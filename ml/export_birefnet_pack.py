#!/usr/bin/env python3
"""Export BiRefNet ONNX pack (birefnet-v1) for Lite/Pro matte quality.

Usage:
  # Preferred — download public tiny ONNX (no GPU):
  python ml/export_birefnet_pack.py --from-release --out exports/birefnet-v1

  # Or convert from Hugging Face weights (needs torch + GPU/Colab):
  python ml/export_birefnet_pack.py --out exports/birefnet-v1
"""
from __future__ import annotations

import argparse
import json
import urllib.request
from pathlib import Path

RELEASE_ONNX = (
    "https://github.com/ZhengPeng7/BiRefNet/releases/download/v1/"
    "BiRefNet-general-bb_swin_v1_tiny-epoch_232.onnx"
)


def write_meta(out: Path) -> None:
    meta = {
        "version": 1,
        "tier": "LITE",
        "displayName": "BiRefNet matting",
        "description": "Cleaner garment/person mattes for Lite and Pro try-on.",
        "minSpec": {"minRamMb": 4096, "requiresNpu": False, "minSdk": 35},
        "kind": "QUALITY",
        "license": "MIT",
    }
    (out / "pack.json").write_text(json.dumps(meta, indent=2) + "\n")


def from_release(out: Path) -> None:
    out.mkdir(parents=True, exist_ok=True)
    onnx_path = out / "birefnet.onnx"
    if not (onnx_path.exists() and onnx_path.stat().st_size > 1_000_000):
        print(f"Downloading {RELEASE_ONNX}")
        urllib.request.urlretrieve(RELEASE_ONNX, onnx_path)
    write_meta(out)
    print(f"Wrote {onnx_path} ({onnx_path.stat().st_size} bytes)")


def from_torch(out: Path, model_id: str) -> None:
    try:
        import torch
        from transformers import AutoModelForImageSegmentation
    except ImportError as exc:
        raise SystemExit(
            "Install torch + transformers, or pass --from-release. "
            "Use ml/colab_convert_pro_pack.ipynb or a GPU machine."
        ) from exc

    out.mkdir(parents=True, exist_ok=True)
    model = AutoModelForImageSegmentation.from_pretrained(model_id, trust_remote_code=True)
    model.eval()
    dummy = torch.randn(1, 3, 512, 512)
    onnx_path = out / "birefnet.onnx"
    torch.onnx.export(
        model,
        dummy,
        str(onnx_path),
        input_names=["input"],
        output_names=["output"],
        dynamic_axes={"input": {0: "batch", 2: "height", 3: "width"}},
        opset_version=17,
    )
    write_meta(out)
    print(f"Wrote {onnx_path} ({onnx_path.stat().st_size} bytes)")


def main() -> None:
    parser = argparse.ArgumentParser(description="Export BiRefNet ONNX quality pack")
    parser.add_argument("--out", type=Path, default=Path("exports/birefnet-v1"))
    parser.add_argument("--model", default="ZhengPeng7/BiRefNet")
    parser.add_argument(
        "--from-release",
        action="store_true",
        help="Download public Swin-Tiny ONNX from ZhengPeng7/BiRefNet releases",
    )
    args = parser.parse_args()
    if args.from_release:
        from_release(args.out)
    else:
        from_torch(args.out, args.model)


if __name__ == "__main__":
    main()
