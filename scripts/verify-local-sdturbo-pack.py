#!/usr/bin/env python3
"""Validate local-sdturbo-v1 pack directory layout (no ONNX inference).

Usage:
  python scripts/verify-local-sdturbo-pack.py exports/local-sdturbo-v1
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

MIN_GRAPH_BYTES = 1_000_000
REQUIRED_ONNX = ("text_encoder.onnx", "unet.onnx", "vae_decoder.onnx")
TOKENIZER = ("vocab.json", "merges.txt")


def verify(pack_dir: Path) -> list[str]:
    errors: list[str] = []
    if not pack_dir.is_dir():
        return [f"Not a directory: {pack_dir}"]

    for name in ("config.json", "pack.json"):
        if not (pack_dir / name).is_file():
            errors.append(f"Missing {name}")

    config_path = pack_dir / "config.json"
    if config_path.is_file():
        config = json.loads(config_path.read_text())
        graphs = config.get("graphs") or {}
        for key in ("text_encoder", "unet", "vae_decoder"):
            onnx_name = graphs.get(key)
            if not onnx_name:
                errors.append(f"config.json graphs.{key} missing")
                continue
            path = pack_dir / onnx_name
            if not path.is_file():
                errors.append(f"Missing ONNX graph {onnx_name}")
            elif path.stat().st_size < MIN_GRAPH_BYTES:
                errors.append(
                    f"{onnx_name} is placeholder-sized "
                    f"({path.stat().st_size} B < {MIN_GRAPH_BYTES} B)"
                )

    for name in REQUIRED_ONNX:
        path = pack_dir / name
        if path.is_file() and path.stat().st_size < MIN_GRAPH_BYTES:
            if f"{name} is placeholder-sized" not in " ".join(errors):
                errors.append(f"{name} is placeholder-sized ({path.stat().st_size} B)")

    for name in TOKENIZER:
        if not (pack_dir / name).is_file():
            errors.append(f"Missing tokenizer file {name}")

    pack_json = pack_dir / "pack.json"
    if pack_json.is_file():
        meta = json.loads(pack_json.read_text())
        if meta.get("tier") not in ("LITE", "PRO", "AUTO", "CLOUD"):
            errors.append(f"pack.json tier invalid: {meta.get('tier')}")

    return errors


def main() -> None:
    if len(sys.argv) != 2:
        print(__doc__.strip())
        sys.exit(2)
    pack_dir = Path(sys.argv[1])
    errors = verify(pack_dir)
    if errors:
        print(f"FAIL {pack_dir}:")
        for err in errors:
            print(f"  - {err}")
        sys.exit(1)
    print(f"OK {pack_dir} — layout matches AndroidTxt2ImgEngine contract")


if __name__ == "__main__":
    main()
