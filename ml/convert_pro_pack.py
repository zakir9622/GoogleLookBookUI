#!/usr/bin/env python3
"""Convert the SD1.5 + ControlNet-Depth + IP-Adapter source weights into the
ONNX FP16 Pro pack the Android app loads.

This is the mandatory bridge: .safetensors are PyTorch tensors and cannot be
executed by ONNX Runtime Mobile. Each component is exported separately, then
FP16-packed, so the app runs the multi-conditioning chain (SdControlNetPipeline):

  text_encoder.onnx      CLIP-L text encoder      input_ids[1,77] -> [1,77,768]
  vae_encoder.onnx       image -> latent          [1,3,512,512]  -> [1,4,64,64]
  vae_decoder.onnx       latent -> image          [1,4,64,64]    -> [1,3,512,512]
  controlnet.onnx        (sample,t,text,depth)    -> 12 down + 1 mid residuals
  ip_image_encoder.onnx  CLIP-H garment[1,3,224,224] -> penultimate [1,257,1280]
  unet.onnx              (sample,t,text[1,77,768], image_embeds[1,1,257,1280],
                          down_0..down_11, mid)  -> noise[1,4,64,64]

IMPORTANT contract notes (verified end-to-end on CPU via onnxruntime):
  * There is NO separate ip_proj: the IP-Adapter Plus resampler + attention are
    baked into unet.onnx. The image encoder emits raw CLIP-H penultimate hidden
    states; the UNet consumes them via added_cond_kwargs["image_embeds"].
  * image_embeds is 4-D [batch, num_images, seq, dim].
  * opset 18 (torch's exporter emits >=18; forcing 17 breaks Split/Resize).

No GPU required — export traces on CPU in fp32, then onnxconverter_common packs
each graph to fp16 (graph-level). Runs on ~15 GB RAM. On a GPU box it's faster.

Usage:
  ./download_pro_models.sh pro_src
  python convert_pro_pack.py --src pro_src --out exports/pro-v1
  python export_depth.py --out exports/pro-v1/depth.onnx   # Stage-A depth model
  python manifest_gen.py exports/ --base-url <hf-dataset-resolve-url>

Deps: pip install torch diffusers==0.31.0 transformers==4.44.2 onnx \
      onnxruntime onnxconverter-common onnxscript safetensors accelerate
"""

from __future__ import annotations

import argparse
import gc
import json
import shutil
from pathlib import Path

IMG, LAT, OPSET = 512, 64, 18
# Pixel 9 (12 GB RAM) and similar flagships.
MIN_SPEC = {"minRamMb": 10000, "requiresNpu": False, "minSdk": 35}


def log(*a):
    print(*a, flush=True)


def validate(path: Path, feeds: dict):
    """Run the exported graph once on CPU so a broken export fails loudly here."""
    import numpy as np  # noqa: F401  (feeds are numpy arrays)
    import onnxruntime as ort

    sess = ort.InferenceSession(str(path), providers=["CPUExecutionProvider"])
    outs = sess.run(None, feeds)
    log(f"  ✓ {path.name}: {[o.shape for o in outs]}")
    return outs


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--src", type=Path, default=Path("pro_src"))
    parser.add_argument("--out", type=Path, default=Path("exports/pro-v1"))
    parser.add_argument("--opset", type=int, default=OPSET)
    parser.add_argument("--dry-run", action="store_true", help="check paths, export nothing")
    args = parser.parse_args()

    base = args.src / "realistic_vision" / "Realistic_Vision_V5.1_fp16-no-ema.safetensors"
    controlnet = args.src / "controlnet_depth" / "control_v11f1p_sd15_depth_fp16.safetensors"
    ip_dir = args.src / "ip_adapter" / "models"
    ip_encoder = ip_dir / "image_encoder"

    log("Planned exports →", args.out)
    for label, path in [("base", base), ("controlnet", controlnet),
                        ("ip-adapter", ip_dir / "ip-adapter-plus_sd15.safetensors"),
                        ("ip image encoder", ip_encoder)]:
        log(f"  {label:<18} {'ok' if path.exists() else 'MISSING'}  {path}")
    if args.dry_run:
        log("dry-run: no export performed.")
        return

    import numpy as np
    import torch
    from diffusers import ControlNetModel, StableDiffusionControlNetPipeline

    fp32 = args.out
    fp32.mkdir(parents=True, exist_ok=True)
    dt = torch.float32  # CPU export must be fp32; fp16 packing happens after
    op = args.opset

    log("loading base + controlnet + ip-adapter…")
    pipe = StableDiffusionControlNetPipeline.from_single_file(
        str(base),
        controlnet=ControlNetModel.from_single_file(str(controlnet), torch_dtype=dt),
        torch_dtype=dt, safety_checker=None)
    pipe.load_ip_adapter(str(ip_dir), subfolder="",
                         weight_name="ip-adapter-plus_sd15.safetensors",
                         image_encoder_folder=str(ip_encoder))
    pipe.set_ip_adapter_scale(0.7)

    # ── text encoder ──
    log("export text_encoder…")
    torch.onnx.export(pipe.text_encoder, (torch.ones(1, 77, dtype=torch.int64),),
        str(fp32 / "text_encoder.onnx"), input_names=["input_ids"],
        output_names=["embeddings"], opset_version=op)
    validate(fp32 / "text_encoder.onnx", {"input_ids": np.ones((1, 77), np.int64)})

    # ── VAE ──
    log("export vae…")

    class Enc(torch.nn.Module):
        def __init__(s, v): super().__init__(); s.v = v
        def forward(s, x): return s.v.encode(x).latent_dist.mode() * s.v.config.scaling_factor

    class Dec(torch.nn.Module):
        def __init__(s, v): super().__init__(); s.v = v
        def forward(s, z): return s.v.decode(z / s.v.config.scaling_factor).sample

    torch.onnx.export(Enc(pipe.vae), (torch.zeros(1, 3, IMG, IMG),),
        str(fp32 / "vae_encoder.onnx"), input_names=["image"], output_names=["latent"], opset_version=op)
    torch.onnx.export(Dec(pipe.vae), (torch.zeros(1, 4, LAT, LAT),),
        str(fp32 / "vae_decoder.onnx"), input_names=["latent"], output_names=["image"], opset_version=op)
    validate(fp32 / "vae_encoder.onnx", {"image": np.zeros((1, 3, IMG, IMG), np.float32)})
    validate(fp32 / "vae_decoder.onnx", {"latent": np.zeros((1, 4, LAT, LAT), np.float32)})

    # ── ControlNet-Depth ──
    log("export controlnet…")
    ctrl_names = [f"down_{i}" for i in range(12)] + ["mid"]
    torch.onnx.export(pipe.controlnet,
        (torch.zeros(1, 4, LAT, LAT), torch.tensor([1], dtype=torch.int64),
         torch.zeros(1, 77, 768), torch.zeros(1, 3, IMG, IMG)),
        str(fp32 / "controlnet.onnx"),
        input_names=["sample", "timestep", "encoder_hidden_states", "controlnet_cond"],
        output_names=ctrl_names, opset_version=op)
    ctrl_outs = validate(fp32 / "controlnet.onnx", {
        "sample": np.zeros((1, 4, LAT, LAT), np.float32), "timestep": np.array([1], np.int64),
        "encoder_hidden_states": np.zeros((1, 77, 768), np.float32),
        "controlnet_cond": np.zeros((1, 3, IMG, IMG), np.float32)})
    res_shapes = [list(o.shape) for o in ctrl_outs]

    # ── IP image encoder (penultimate hidden states) ──
    log("export ip_image_encoder…")

    class ImgEnc(torch.nn.Module):
        def __init__(s, m): super().__init__(); s.m = m
        def forward(s, x): return s.m(x, output_hidden_states=True).hidden_states[-2]

    torch.onnx.export(ImgEnc(pipe.image_encoder), (torch.zeros(1, 3, 224, 224),),
        str(fp32 / "ip_image_encoder.onnx"), input_names=["image"],
        output_names=["image_embeds"], opset_version=op)
    ie = validate(fp32 / "ip_image_encoder.onnx", {"image": np.zeros((1, 3, 224, 224), np.float32)})
    ip_seq, ip_dim = int(ie[0].shape[1]), int(ie[0].shape[2])

    # ── fused UNet (ControlNet residuals + baked IP-Adapter projection/attn) ──
    log("export unet…")
    with torch.no_grad():
        dres, mres = pipe.controlnet(torch.zeros(1, 4, LAT, LAT), torch.tensor([1], dtype=torch.int64),
            encoder_hidden_states=torch.zeros(1, 77, 768),
            controlnet_cond=torch.zeros(1, 3, IMG, IMG), return_dict=False)
    down_names = [f"down_{i}" for i in range(len(dres))]

    class UNetFused(torch.nn.Module):
        def __init__(s, u): super().__init__(); s.u = u
        def forward(s, sample, timestep, ehs, image_embeds, *res):
            return s.u(sample, timestep, ehs,
                down_block_additional_residuals=list(res[:-1]),
                mid_block_additional_residual=res[-1],
                added_cond_kwargs={"image_embeds": [image_embeds]}, return_dict=False)[0]

    # image_embeds must be 4-D [batch, num_images, seq, dim].
    uargs = (torch.zeros(1, 4, LAT, LAT), torch.tensor([1], dtype=torch.int64),
             torch.zeros(1, 77, 768), torch.zeros(1, 1, ip_seq, ip_dim), *dres, mres)
    torch.onnx.export(UNetFused(pipe.unet), uargs, str(fp32 / "unet.onnx"),
        input_names=["sample", "timestep", "encoder_hidden_states", "image_embeds", *down_names, "mid"],
        output_names=["noise_pred"], opset_version=op)
    del pipe
    gc.collect()
    uf = {"sample": np.zeros((1, 4, LAT, LAT), np.float32), "timestep": np.array([1], np.int64),
          "encoder_hidden_states": np.zeros((1, 77, 768), np.float32),
          "image_embeds": np.zeros((1, 1, ip_seq, ip_dim), np.float32)}
    for i, sh in enumerate(res_shapes[:-1]):
        uf[f"down_{i}"] = np.zeros(sh, np.float32)
    uf["mid"] = np.zeros(res_shapes[-1], np.float32)
    validate(fp32 / "unet.onnx", uf)

    log("all components exported + validated (fp32). packing fp16…")
    copy_tokenizer(args.src, fp32)
    pack_fp16(fp32, args.out, res_shapes, ip_seq, ip_dim)


def copy_tokenizer(src: Path, out: Path) -> None:
    """Copy CLIP tokenizer files from the SD1.5 source tree into the pack."""
    from transformers import CLIPTokenizer

    candidates = [
        src / "realistic_vision" / "tokenizer",
        src / "tokenizer",
    ]
    for tok_dir in candidates:
        if (tok_dir / "vocab.json").exists():
            for name in ("vocab.json", "merges.txt", "tokenizer.json"):
                f = tok_dir / name
                if f.exists():
                    shutil.copy2(f, out / name)
            log(f"  tokenizer copied from {tok_dir}")
            return
    # Fallback: export from transformers hub layout
    try:
        tok = CLIPTokenizer.from_pretrained(str(src / "realistic_vision"), subfolder="tokenizer")
    except Exception:
        tok = CLIPTokenizer.from_pretrained("openai/clip-vit-large-patch14")
    tok.save_pretrained(str(out))
    log("  tokenizer exported via transformers")


def pack_fp16(fp32: Path, out: Path, res_shapes, ip_seq, ip_dim) -> None:
    """FP16-pack each graph. >2 GB graphs skip the shape-inference pass (its
    in-memory serialize hits protobuf's 2 GB limit); smaller graphs keep it."""
    import onnx
    from onnxconverter_common import float16

    for f in sorted(fp32.glob("*.onnx")):
        big = f.with_suffix(".onnx.data").stat().st_size > 1_900_000_000 \
            if f.with_suffix(".onnx.data").exists() else False
        log(f"  fp16 {f.name} (disable_shape_infer={big})")
        m = onnx.load(str(f), load_external_data=True)
        m16 = float16.convert_float_to_float16(m, keep_io_types=True, disable_shape_infer=big)
        # Write to a fresh .data name so an existing file is never appended to.
        onnx.save(m16, str(out / f.name), save_as_external_data=True,
                  all_tensors_to_one_file=True, location=f.name + ".data")
        del m, m16
        gc.collect()

    (out / "config.json").write_text(json.dumps({
        "latentWidth": LAT, "latentHeight": LAT, "imageWidth": IMG, "imageHeight": IMG,
        "resolution": IMG, "inferenceSteps": 22, "guidanceScale": 7.0, "vaeScale": 0.18215,
        "unet": "unet.onnx", "textEncoder": "text_encoder.onnx", "vaeEncoder": "vae_encoder.onnx",
        "vaeDecoder": "vae_decoder.onnx", "controlNet": "controlnet.onnx", "depthModel": "depth.onnx",
        "imageEncoder": "ip_image_encoder.onnx", "ipEmbedSeq": ip_seq, "ipEmbedDim": ip_dim,
        "residualShapes": res_shapes,
    }, indent=2))
    (out / "pack.json").write_text(json.dumps({
        "version": 1, "tier": "PRO", "kind": "ENGINE",
        "displayName": "Pro engine (SD1.5 + ControlNet-Depth + IP-Adapter)",
        "description": "Photorealistic on-device try-on for Pixel 9 class phones (10 GB+ RAM).",
        "minSpec": MIN_SPEC,
    }, indent=2))
    for name in ("vocab.json", "merges.txt", "tokenizer.json"):
        src = fp32 / name
        if src.exists():
            shutil.copy2(src, out / name)
    log(f"Pro pack written to {out}. Add depth.onnx (export_depth.py), then manifest_gen.py.")


if __name__ == "__main__":
    main()
