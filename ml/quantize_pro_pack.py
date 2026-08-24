#!/usr/bin/env python3
"""INT8-quantize a Pro FP16 pack for smaller download and faster Pixel-class inference.

Usage:
  python quantize_pro_pack.py --src exports/pro-v1 --out exports/pro-v2-int8

Quantizes unet.onnx and controlnet.onnx (static, per-channel). Encoders stay FP16.
Re-validates each graph on CPU, then writes pack.json + config.json for pro-v2-int8.
"""

from __future__ import annotations

import argparse
import gc
import json
import shutil
from pathlib import Path

MIN_SPEC = {"minRamMb": 10000, "requiresNpu": False, "minSdk": 35}
QUANTIZE_TARGETS = ("unet.onnx", "controlnet.onnx")


def log(*a):
    print(*a, flush=True)


def quantize_model(src: Path, dst: Path) -> None:
    import numpy as np
    import onnx
    from onnxruntime.quantization import QuantType, quantize_dynamic

    log(f"  quantize {src.name} → {dst.name}")
    quantize_dynamic(
        str(src),
        str(dst),
        weight_type=QuantType.QInt8,
        per_channel=True,
        reduce_range=False,
    )
    import onnxruntime as ort

    sess = ort.InferenceSession(str(dst), providers=["CPUExecutionProvider"])
    log(f"  ✓ {dst.name}: inputs={[i.name for i in sess.get_inputs()]}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--src", type=Path, required=True, help="Source FP16 pack directory")
    parser.add_argument("--out", type=Path, default=Path("exports/pro-v2-int8"))
    args = parser.parse_args()

    if not args.src.is_dir():
        raise SystemExit(f"Source pack not found: {args.src}")

    args.out.mkdir(parents=True, exist_ok=True)

    for f in sorted(args.src.iterdir()):
        if f.suffix == ".onnx":
            if f.name in QUANTIZE_TARGETS:
                quantize_model(f, args.out / f.name)
            else:
                shutil.copy2(f, args.out / f.name)
                data = f.with_suffix(".onnx.data")
                if data.exists():
                    shutil.copy2(data, args.out / data.name)
        elif f.name in ("config.json", "vocab.json", "merges.txt", "tokenizer.json"):
            shutil.copy2(f, args.out / f.name)

    config = json.loads((args.src / "config.json").read_text())
    (args.out / "config.json").write_text(json.dumps(config, indent=2))

    (args.out / "pack.json").write_text(
        json.dumps(
            {
                "version": 2,
                "tier": "PRO",
                "kind": "ENGINE",
                "displayName": "Pro engine INT8 (SD1.5 + ControlNet + IP-Adapter)",
                "description": "Quantized on-device try-on for 10 GB+ RAM phones (Pixel 9).",
                "minSpec": MIN_SPEC,
            },
            indent=2,
        )
    )
    log(f"INT8 pack written to {args.out}. Run manifest_gen.py to publish.")


if __name__ == "__main__":
    main()
