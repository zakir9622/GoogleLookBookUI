#!/usr/bin/env python3
"""Build the PRIVATE dev Pro pack from published research try-on weights.

⚠ LICENSE: CatVTON and Mobile-VTON weights are CC BY-NC(-SA) — NON-COMMERCIAL.
This pack is for the developer's own device testing only. It must be uploaded
(if at all) to a PRIVATE repo and served through a dev manifest, never the
production one. manifest_gen.py propagates the devOnly flag; the app shows a
DEV ONLY warning on such packs.

This wraps export_diffusion_pack.py: it fetches CatVTON's trainable attention
weights, merges them over the OpenRAIL SD-1.5-inpainting base the way CatVTON's
inference code does, then reuses the standard export.

Usage:
  python export_dev_pack.py [--out exports-dev/pro-dev-v1] [--width 384 --height 512]

Requires: pip install torch diffusers transformers safetensors onnx onnxruntime
Weights fetched: zheng-chong/CatVTON (attention modules) +
                 runwayml/stable-diffusion-inpainting (base).
Disk: ~8 GB during export. Run on a machine with ≥16 GB RAM.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path


def merge_catvton(work: Path) -> Path:
    import torch
    from diffusers import AutoencoderKL, UNet2DConditionModel
    from huggingface_hub import snapshot_download

    base_dir = Path(
        snapshot_download("runwayml/stable-diffusion-inpainting", allow_patterns=["unet/*", "vae/*"])
    )
    catvton_dir = Path(snapshot_download("zhengchong/CatVTON", allow_patterns=["*.safetensors", "**/*.safetensors"]))

    print("Loading base inpainting UNet…")
    unet = UNet2DConditionModel.from_pretrained(base_dir / "unet", torch_dtype=torch.float32)

    print("Merging CatVTON attention weights…")
    from safetensors.torch import load_file

    merged = 0
    state = unet.state_dict()
    for shard in sorted(catvton_dir.rglob("*.safetensors")):
        for key, tensor in load_file(shard).items():
            # CatVTON publishes only the self-attention parameters it trains;
            # their keys align with the diffusers UNet state dict.
            if key in state and state[key].shape == tensor.shape:
                state[key] = tensor
                merged += 1
    if merged == 0:
        raise SystemExit(
            "No CatVTON keys matched the base UNet — the published layout changed. "
            "Check zhengchong/CatVTON's repo structure and adjust the key mapping."
        )
    unet.load_state_dict(state)
    print(f"  merged {merged} tensors")

    out = work / "merged"
    (out / "unet").mkdir(parents=True, exist_ok=True)
    unet.save_pretrained(out / "unet")
    AutoencoderKL.from_pretrained(base_dir / "vae").save_pretrained(out / "vae")
    return out


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", type=Path, default=Path("exports-dev/pro-dev-v1"))
    parser.add_argument("--width", type=int, default=384)
    parser.add_argument("--height", type=int, default=512)
    parser.add_argument("--work", type=Path, default=Path("exports-dev/.work"))
    args = parser.parse_args()

    merged = merge_catvton(args.work)

    from export_diffusion_pack import export

    export(merged, args.out, args.width, args.height, fp16=False)

    # Overwrite pack.json with the dev-only variant.
    (args.out / "pack.json").write_text(
        json.dumps(
            {
                "version": 1,
                "tier": "PRO",
                "displayName": "Pro engine (DEV)",
                "description": "CatVTON research weights for private testing. Not for distribution.",
                "minSpec": {"minRamMb": 7168, "requiresNpu": True, "minSdk": 35},
                "devOnly": True,
                "licenseNotice": "CC BY-NC-SA 4.0 (CatVTON) — non-commercial, private testing only.",
            },
            indent=2,
        )
    )
    print(f"\nDEV pack at {args.out}. Serve via a PRIVATE manifest only.")


if __name__ == "__main__":
    main()
