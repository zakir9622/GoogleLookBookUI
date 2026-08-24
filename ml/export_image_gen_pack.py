#!/usr/bin/env python3
"""Export local-sdturbo-v1 ONNX pack for offline Create Studio.

Produces pack layout for AndroidTxt2ImgEngine:
  text_encoder.onnx, unet.onnx, vae_decoder.onnx, vocab.json, merges.txt, config.json, pack.json

Full weight export needs a GPU host / Colab — this script writes the contract
scaffold and can copy public CLIP tokenizer files. See ml/colab_export_sdturbo_pack.ipynb
(when present) or:
  diffusers SD-Turbo / LCM-SD1.5 → ONNX (opset 17) → copy into exports/local-sdturbo-v1/

Usage:
  python ml/export_image_gen_pack.py --out exports/local-sdturbo-v1
  python ml/export_image_gen_pack.py --out exports/local-sdturbo-v1 --copy-tokenizer
"""
from __future__ import annotations

import argparse
import json
import urllib.request
from pathlib import Path

TOKENIZER_BASE = (
    "https://huggingface.co/runwayml/stable-diffusion-v1-5/resolve/main/tokenizer"
)


def copy_tokenizer(out: Path) -> None:
    for name in ("vocab.json", "merges.txt"):
        dest = out / name
        if dest.is_file():
            print(f"  skip {name} (already present)")
            continue
        url = f"{TOKENIZER_BASE}/{name}"
        print(f"  fetch {url}")
        with urllib.request.urlopen(url, timeout=120) as resp:
            dest.write_bytes(resp.read())


def main() -> None:
    parser = argparse.ArgumentParser(description="Export local SD-Turbo image-gen pack scaffold")
    parser.add_argument("--out", type=Path, default=Path("exports/local-sdturbo-v1"))
    parser.add_argument(
        "--copy-tokenizer",
        action="store_true",
        help="Download vocab.json + merges.txt from public SD1.5 tokenizer",
    )
    args = parser.parse_args()
    out = args.out
    out.mkdir(parents=True, exist_ok=True)

    config = {
        "version": 1,
        "displayName": "SD-Turbo local",
        "description": "Offline Create Studio — 512×512, 1–4 steps (AndroidTxt2ImgEngine)",
        "minSpec": {"minRamMb": 6144, "requiresNpu": False, "minSdk": 35},
        "lcmDistilled": True,
        "graphs": {
            "text_encoder": "text_encoder.onnx",
            "unet": "unet.onnx",
            "vae_decoder": "vae_decoder.onnx",
        },
        "scheduler": {"type": "lcm", "steps": 4, "guidance": 1.0},
        "resolution": 512,
    }
    (out / "config.json").write_text(json.dumps(config, indent=2) + "\n")

    pack_meta = {
        "version": 1,
        "tier": "LITE",
        "displayName": "SD-Turbo local",
        "description": "Offline Create Studio — SD-Turbo / LCM via ORT (local-sdturbo-v1)",
        "minSpec": {"minRamMb": 6144, "requiresNpu": False, "minSdk": 35},
    }
    (out / "pack.json").write_text(json.dumps(pack_meta, indent=2) + "\n")

    if args.copy_tokenizer:
        print("Copying CLIP tokenizer files…")
        copy_tokenizer(out)

    (out / "README.md").write_text(
        "# local-sdturbo-v1\n\n"
        "Required files (each ONNX ≥ 1 MB for the app to treat graphs as real):\n\n"
        "- `text_encoder.onnx` — CLIP text encoder\n"
        "- `unet.onnx` — 4-channel SD-Turbo / LCM UNet (not Pro 9-ch inpaint)\n"
        "- `vae_decoder.onnx`\n"
        "- `vocab.json` + `merges.txt` — CLIP BPE (`--copy-tokenizer` or SD1.5 export)\n"
        "- `pack.json` — manifest metadata for `ml/manifest_gen.py`\n\n"
        "App engine: `AndroidTxt2ImgEngine` (SAMPLER_WIRED=true). "
        "Validate layout: `python scripts/verify-local-sdturbo-pack.py <dir>`. "
        "Publish via `scripts/publish-packs.py` / HF `Iamzakirzr/vestra-packs`.\n"
    )
    print(f"Wrote scaffold to {out}")
    print("TODO: export real ONNX weights on GPU/Colab, then run manifest_gen.py")


if __name__ == "__main__":
    main()
