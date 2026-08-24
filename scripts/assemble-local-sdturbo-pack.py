#!/usr/bin/env python3
"""Assemble local-sdturbo-v1 from a public tiny-SD ONNX export.

Default source: RanaLLC/tiny-sd-onnx-fp16 (single-file ONNX graphs, ~1 GB).
Writes the AndroidTxt2ImgEngine layout under ml/exports/local-sdturbo-v1/.

Usage:
  HF_TOKEN=… python3 scripts/assemble-local-sdturbo-pack.py
  python3 scripts/verify-local-sdturbo-pack.py ml/exports/local-sdturbo-v1
"""
from __future__ import annotations

import argparse
import json
import os
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_OUT = ROOT / "ml" / "exports" / "local-sdturbo-v1"
DEFAULT_REPO = "RanaLLC/tiny-sd-onnx-fp16"

FILES = {
    "text_encoder.onnx": "text_encoder/model.onnx",
    "unet.onnx": "unet/model.onnx",
    "vae_decoder.onnx": "vae_decoder/model.onnx",
    "vocab.json": "tokenizer/vocab.json",
    "merges.txt": "tokenizer/merges.txt",
}


def download(repo: str, remote: str, dest: Path, token: str | None) -> None:
    from huggingface_hub import hf_hub_download

    path = hf_hub_download(
        repo_id=repo,
        filename=remote,
        token=token,
    )
    dest.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(path, dest)
    print(f"  {dest.name}: {dest.stat().st_size / 1e6:.1f} MB")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT)
    parser.add_argument("--repo", default=DEFAULT_REPO)
    args = parser.parse_args()
    token = (
        os.environ.get("HF_TOKEN")
        or os.environ.get("LOOKBOOK_HF_TOKEN")
        or os.environ.get("HUGGING_FACE_HUB_TOKEN")
    )
    out: Path = args.out
    out.mkdir(parents=True, exist_ok=True)

    print(f"Assembling {out} from {args.repo}…")
    for local_name, remote in FILES.items():
        dest = out / local_name
        if dest.is_file() and dest.stat().st_size > 1_000_000:
            print(f"  skip {local_name} ({dest.stat().st_size / 1e6:.1f} MB)")
            continue
        download(args.repo, remote, dest, token)

    config = {
        "version": 1,
        "displayName": "SD-Turbo local (tiny-SD LCM)",
        "description": "Offline Create Studio — 512×512, 1–4 LCM steps (AndroidTxt2ImgEngine)",
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
        "displayName": "Local image gen (tiny-SD)",
        "description": "Offline Create Studio via ORT — assembled from public tiny-SD ONNX FP16",
        "minSpec": {"minRamMb": 6144, "requiresNpu": False, "minSdk": 35},
    }
    (out / "pack.json").write_text(json.dumps(pack_meta, indent=2) + "\n")
    print(f"Done → {out}")
    print("Next: python3 scripts/verify-local-sdturbo-pack.py", out)


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:  # noqa: BLE001
        print(f"FAIL: {exc}", file=sys.stderr)
        sys.exit(1)
