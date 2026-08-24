#!/usr/bin/env python3
"""Build the Lite engine model pack (exports/lite-v1/).

The Lite pipeline on-device is:
  1. garment_seg.onnx   — U²-Net-P salient-object segmentation (garment cutout)
  2. human_parse.onnx   — human parsing (body-region masks incl. torso/arms/legs)
  3. TPS warp + color-transfer harmonization run in Kotlin (no model needed)

Both models are fetched from Hugging Face, INT8-dynamic-quantized for mobile,
and smoke-tested with ONNX Runtime before being written to the pack directory.

Usage: python export_lite_pack.py [--out exports/lite-v1]
"""

from __future__ import annotations

import argparse
import json
import shutil
from pathlib import Path

import numpy as np
import onnxruntime as ort
from huggingface_hub import hf_hub_download
from onnxruntime.quantization import QuantType, quantize_dynamic

# u2netp: 4.7 MB fp32 salient-object model, the standard garment/product cutout
# workhorse (same weights rembg ships). ATR-trained human parser gives the
# clothing/skin region masks the warp stage needs.
SEG_REPO, SEG_FILE = "tomjackson2023/rembg", "u2netp.onnx"
PARSE_REPO, PARSE_FILE = "Longcat2957/humanparsing-onnx", "parsing_atr.onnx"


def fetch(repo: str, filename: str, dest: Path) -> Path:
    cached = hf_hub_download(repo_id=repo, filename=filename)
    dest.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy(cached, dest)
    return dest


def quantize(src: Path, dst: Path) -> None:
    quantize_dynamic(str(src), str(dst), weight_type=QuantType.QUInt8)
    src.unlink()


def smoke_test(model: Path, input_hw: int) -> None:
    session = ort.InferenceSession(str(model), providers=["CPUExecutionProvider"])
    inp = session.get_inputs()[0]
    shape = [d if isinstance(d, int) else 1 for d in inp.shape]
    # Most vision models here are NCHW with a dynamic batch dim.
    if len(shape) == 4:
        shape = [1, shape[1] if shape[1] in (1, 3) else 3, input_hw, input_hw]
    outputs = session.run(None, {inp.name: np.random.rand(*shape).astype(np.float32)})
    assert outputs and outputs[0] is not None
    print(f"  ✓ {model.name}: in={shape} out={[o.shape for o in outputs]}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", type=Path, default=Path("exports/lite-v1"))
    parser.add_argument(
        "--pack-id",
        choices=("lite-v1", "lite-v2"),
        default="lite-v1",
        help="Pack directory name (lite-v2 reserved for improved seg export)",
    )
    args = parser.parse_args()
    out: Path = args.out
    if args.pack_id == "lite-v2" and str(out) == "exports/lite-v1":
        out = Path("exports/lite-v2")
    out.mkdir(parents=True, exist_ok=True)

    print("Fetching + quantizing garment segmentation (u2netp)…")
    raw = fetch(SEG_REPO, SEG_FILE, out / "garment_seg_fp32.onnx")
    quantize(raw, out / "garment_seg.onnx")
    smoke_test(out / "garment_seg.onnx", 320)

    print("Fetching + quantizing human parsing (ATR)…")
    raw = fetch(PARSE_REPO, PARSE_FILE, out / "human_parse_fp32.onnx")
    quantize(raw, out / "human_parse.onnx")
    smoke_test(out / "human_parse.onnx", 512)

    (out / "pack.json").write_text(
        json.dumps(
            {
                "version": 1,
                "tier": "LITE",
                "displayName": "Lite engine",
                "description": "Fast offline try-on: garment cutout, body analysis, and fitting on every supported phone.",
                "minSpec": {"minRamMb": 0, "requiresNpu": False, "minSdk": 35},
            },
            indent=2,
        )
    )
    total = sum(f.stat().st_size for f in out.rglob("*") if f.is_file())
    print(f"Lite pack ready at {out} ({total / 1e6:.1f} MB). Run manifest_gen.py next.")


if __name__ == "__main__":
    main()
