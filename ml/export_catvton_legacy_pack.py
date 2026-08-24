#!/usr/bin/env python3
"""Build a CatVTON-style 3-file try-on pack (exports/catvton-legacy/).

⚠ This is NOT the production pro-v1 pack. Production pro-v1 is fully
conditioned (text + ControlNet-Depth + IP-Adapter) via convert_pro_pack.py.
This exporter intentionally defaults to exports/catvton-legacy/ so it cannot
overwrite the live HF pro-v1 tree.

  vae_encoder.onnx   image  -> 4ch latent
  vae_decoder.onnx   latent -> image
  unet.onnx          9ch inpaint UNet

⚠ LICENSING (read ml/MODEL_LICENSES.md): CatVTON weights are CC BY-NC-SA 4.0 —
personal / eval only, not redistributable in a commercial app.

Usage:
  python export_catvton_legacy_pack.py --weights /path/to/catvton-merged \
      [--out exports/catvton-legacy] [--width 384] [--height 512] [--fp16]
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path


def export(weights: Path, out: Path, width: int, height: int, fp16: bool) -> None:
    # Imports here so `--help` works without the heavy deps installed.
    import torch
    from diffusers import AutoencoderKL, UNet2DConditionModel

    out.mkdir(parents=True, exist_ok=True)
    latent_w, latent_h = width // 8, height // 8
    dtype = torch.float16 if fp16 else torch.float32

    print("Loading VAE…")
    vae = AutoencoderKL.from_pretrained(weights / "vae", torch_dtype=dtype).eval()

    class VaeEncoder(torch.nn.Module):
        def __init__(self, vae):  # noqa: ANN001
            super().__init__()
            self.vae = vae

        def forward(self, image):  # noqa: ANN001
            # Deterministic: use the posterior mean (mode), not a sample.
            return self.vae.encode(image).latent_dist.mode()

    class VaeDecoder(torch.nn.Module):
        def __init__(self, vae):  # noqa: ANN001
            super().__init__()
            self.vae = vae

        def forward(self, latent):  # noqa: ANN001
            return self.vae.decode(latent).sample

    print("Exporting VAE encoder/decoder…")
    torch.onnx.export(
        VaeEncoder(vae),
        torch.zeros(1, 3, height, width, dtype=dtype),
        str(out / "vae_encoder.onnx"),
        input_names=["image"],
        output_names=["latent"],
        opset_version=17,
    )
    torch.onnx.export(
        VaeDecoder(vae),
        torch.zeros(1, 4, latent_h, latent_w, dtype=dtype),
        str(out / "vae_decoder.onnx"),
        input_names=["latent"],
        output_names=["image"],
        opset_version=17,
    )

    print("Loading UNet…")
    unet = UNet2DConditionModel.from_pretrained(weights / "unet", torch_dtype=dtype).eval()

    class TryOnUnet(torch.nn.Module):
        """Wraps the UNet with a fixed zero text context (CatVTON is text-free)."""

        def __init__(self, unet):  # noqa: ANN001
            super().__init__()
            self.unet = unet
            dim = unet.config.cross_attention_dim
            self.register_buffer("context", torch.zeros(1, 1, dim, dtype=dtype))

        def forward(self, sample, timestep):  # noqa: ANN001
            return self.unet(sample, timestep, encoder_hidden_states=self.context).sample

    print("Exporting UNet (this is the slow one)…")
    torch.onnx.export(
        TryOnUnet(unet),
        (
            torch.zeros(1, 9, latent_h, latent_w * 2, dtype=dtype),
            torch.tensor([500], dtype=torch.long),
        ),
        str(out / "unet.onnx"),
        input_names=["sample", "timestep"],
        output_names=["noise_pred"],
        opset_version=17,
    )

    if not fp16:
        print("INT8-quantizing UNet weights…")
        from onnxruntime.quantization import QuantType, quantize_dynamic

        quantize_dynamic(
            str(out / "unet.onnx"),
            str(out / "unet.onnx"),
            weight_type=QuantType.QUInt8,
        )

    (out / "config.json").write_text(
        json.dumps(
            {
                "latentWidth": latent_w,
                "latentHeight": latent_h,
                "imageWidth": width,
                "imageHeight": height,
                "inferenceSteps": 12,
                "guidanceScale": 2.5,
                "vaeScale": 0.18215,
                "concatAxis": "width",
            },
            indent=2,
        )
    )
    (out / "pack.json").write_text(
        json.dumps(
            {
                "version": 1,
                "tier": "PRO",
                "kind": "ENGINE",
                "displayName": "CatVTON legacy (dev only)",
                "description": "3-file CatVTON stub — not the production fully-conditioned pro-v1.",
                "devOnly": True,
                "minSpec": {"minRamMb": 7168, "requiresNpu": True, "minSdk": 35},
            },
            indent=2,
        )
    )
    total = sum(f.stat().st_size for f in out.rglob("*") if f.is_file())
    print(f"CatVTON legacy pack ready at {out} ({total / 1e9:.2f} GB).")
    print("Do NOT publish this as pro-v1. Use convert_pro_pack.py for production.")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--weights", type=Path, required=True)
    parser.add_argument("--out", type=Path, default=Path("exports/catvton-legacy"))
    parser.add_argument("--width", type=int, default=384)
    parser.add_argument("--height", type=int, default=512)
    parser.add_argument("--fp16", action="store_true")
    args = parser.parse_args()
    if args.out.name == "pro-v1" or args.out.as_posix().endswith("/pro-v1"):
        raise SystemExit(
            "Refusing to write CatVTON legacy into pro-v1/. "
            "Use convert_pro_pack.py for production pro-v1, "
            "or pick a different --out (default: exports/catvton-legacy)."
        )
    export(args.weights, args.out, args.width, args.height, args.fp16)


if __name__ == "__main__":
    main()
