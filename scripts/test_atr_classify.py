#!/usr/bin/env python3
"""Real-input ATR classification harness (R2.0b).

Mirrors `AtrTaxonomy` in Kotlin.

Fixture formats (scripts/fixtures/atr/*.json):
  - `fractions`: person-normalized ATR histogram (worn-photo shapes)
  - `counts`: absolute per-class pixel counts (complete person map — Engine path)

Both are classified via the same decision table. Optional `--onnx` smokes the
real `human_parse.onnx` graph when present (not a CI gate).

Usage:
  python3 scripts/test_atr_classify.py
  python3 scripts/test_atr_classify.py --onnx path/to/human_parse.onnx
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

CLASS_COUNT = 18
BACKGROUND = 0
HAT = 1
HAIR = 2
UPPER = 4
SKIRT = 5
PANTS = 6
DRESS = 7
FACE = 11
LEFT_LEG = 12
RIGHT_LEG = 13
LEFT_ARM = 14
RIGHT_ARM = 15
SCARF = 17


def classify_histogram(h: list[float]) -> str:
    def f(i: int) -> float:
        return h[i] if i < len(h) else 0.0

    scarf = f(SCARF)
    hat = f(HAT)
    face = f(FACE)
    hair = f(HAIR)
    upper = f(UPPER)
    dress = f(DRESS)
    pants = f(PANTS)
    skirt = f(SKIRT)
    legs = f(LEFT_LEG) + f(RIGHT_LEG)
    arms = f(LEFT_ARM) + f(RIGHT_ARM)
    head_cover = scarf + hat
    lower = pants + skirt
    torso = upper + dress

    if scarf > 0.08 and face < 0.035 and head_cover > 0.10:
        return "NIQAB"
    if head_cover > 0.10 and torso < 0.14 and lower < 0.08:
        return "HIJAB" if scarf >= hat else "HEADSCARF"
    if scarf > 0.07 and torso > 0.12 and lower < 0.12:
        return "DUPATTA"

    if torso + lower >= 0.32:
        if dress > 0.18 and skirt > 0.08 and pants < 0.06:
            return "LEHENGA"
        if dress > 0.20 and lower < 0.12:
            return "DRESS"
        if torso > 0.26 and (arms + legs) > 0.10 and face < 0.09 and lower > 0.08:
            return "ABAYA"
        if upper > 0.12 and pants > 0.10:
            return "SHALWAR_KAMEEZ"
        if torso > 0.22 and arms > 0.12 and lower > 0.06:
            return "JILBAB"
        if torso > 0.18 and arms > 0.08 and lower > 0.05:
            return "KAFTAN"

    if dress > 0.18:
        return "LEHENGA" if skirt > pants and skirt > 0.07 else "DRESS"
    if lower > torso and lower > 0.16:
        return "LOWER_BODY"
    if upper > 0.16 and pants > 0.09:
        return "SHALWAR_KAMEEZ"
    if upper > 0.14 and lower < 0.10:
        return "KURTA" if arms > 0.07 else "UPPER_BODY"
    if skirt > 0.14:
        return "LEHENGA"
    if head_cover > 0.06 or (hair > 0.12 and scarf > 0.04):
        return "HIJAB"
    return "ABAYA"


def histogram_from_counts(counts: list[int]) -> list[float]:
    person = sum(counts) - (counts[BACKGROUND] if counts else 0)
    out = [0.0] * CLASS_COUNT
    if person <= 0:
        return out
    for i in range(min(CLASS_COUNT, len(counts))):
        if i != BACKGROUND:
            out[i] = counts[i] / person
    return out


def load_fixture(path: Path) -> tuple[str, list[float], str]:
    """Returns (expected, histogram, mode)."""
    data = json.loads(path.read_text())
    expected = data["expected"]
    if "counts" in data:
        counts = [0] * CLASS_COUNT
        for key, val in data["counts"].items():
            counts[int(key)] = int(val)
        return expected, histogram_from_counts(counts), "counts"
    hist = data.get("histogram")
    if hist is None and "fractions" in data:
        hist = [0.0] * CLASS_COUNT
        for key, val in data["fractions"].items():
            hist[int(key)] = float(val)
    if hist is None:
        raise ValueError(f"{path}: need fractions, histogram, or counts")
    if len(hist) < CLASS_COUNT:
        hist = list(hist) + [0.0] * (CLASS_COUNT - len(hist))
    return expected, hist[:CLASS_COUNT], "fractions"


def smoke_onnx(onnx_path: Path) -> None:
    import numpy as np
    import onnxruntime as ort

    session = ort.InferenceSession(str(onnx_path), providers=["CPUExecutionProvider"])
    inp = session.get_inputs()[0]
    rgb = np.zeros((1, 3, 512, 512), dtype=np.float32)
    rgb[:, 0, 40:140, 180:330] = 0.72
    rgb[:, 1, 40:140, 180:330] = 0.52
    rgb[:, 2, 40:140, 180:330] = 0.42
    rgb[:, :, 140:480, 140:370] = 0.12
    out = session.run(None, {inp.name: rgb})[0]
    logits = np.asarray(out)
    if logits.ndim != 4:
        raise SystemExit(f"unexpected human_parse out shape: {logits.shape}")
    class_map = logits[0].argmax(axis=0).reshape(-1).astype(int)
    counts = [0] * CLASS_COUNT
    for label in class_map.tolist():
        if 0 <= label < CLASS_COUNT:
            counts[label] += 1
    cat = classify_histogram(histogram_from_counts(counts))
    print(f"  ONNX smoke  {onnx_path.name}: person_px={sum(counts)-counts[0]} → {cat}")


def main() -> int:
    parser = argparse.ArgumentParser(description="ATR classify fixture harness")
    parser.add_argument(
        "--fixtures",
        type=Path,
        default=Path(__file__).resolve().parent / "fixtures" / "atr",
    )
    parser.add_argument("--onnx", type=Path, default=None)
    args = parser.parse_args()
    files = sorted(args.fixtures.glob("*.json"))
    if not files:
        print(f"No fixtures in {args.fixtures}", file=sys.stderr)
        return 2

    failed = 0
    for path in files:
        expected, hist, mode = load_fixture(path)
        got = classify_histogram(hist)
        ok = got == expected
        status = "PASS" if ok else "FAIL"
        print(f"{status}  {path.name} [{mode}]: expected={expected} got={got}")
        if not ok:
            failed += 1

    print(f"\n{len(files) - failed}/{len(files)} passed")

    if args.onnx is not None:
        if not args.onnx.is_file():
            print(f"ONNX missing: {args.onnx}", file=sys.stderr)
            return 2
        print("\n== Real human_parse.onnx smoke ==")
        smoke_onnx(args.onnx)

    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
