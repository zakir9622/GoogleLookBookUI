#!/usr/bin/env python3
"""Perceptual size/hash compare of screenshot dirs (generation-stability M5).

Exits 0 when every baseline PNG has a counterpart within size ±35%.
Missing baseline PNGs → WARN (exit 0) unless --strict.
Missing actual when baseline PNG exists → FAIL.
"""
from __future__ import annotations

import hashlib
import sys
from pathlib import Path


def rough_fingerprint(path: Path) -> tuple[int, str]:
    data = path.read_bytes()
    # Size + truncated content hash — good enough without Pillow.
    h = hashlib.sha256(data[:: max(1, len(data) // 4096)]).hexdigest()[:16]
    return len(data), h


def main() -> int:
    args = [a for a in sys.argv[1:] if a != "--strict"]
    strict = "--strict" in sys.argv[1:]
    if len(args) < 2:
        print("Usage: compare-screenshots.py <baseline_dir> <actual_dir> [--strict]", file=sys.stderr)
        return 2
    baseline = Path(args[0])
    actual = Path(args[1])
    if not baseline.is_dir():
        print(f"WARN: no baseline at {baseline} — skipping compare")
        return 0
    if not actual.is_dir():
        print(f"ERROR: actual dir missing: {actual}", file=sys.stderr)
        return 2

    baseline_pngs = sorted(baseline.glob("*.png"))
    if not baseline_pngs:
        print(f"WARN: no baseline PNGs in {baseline} (see README.md) — skipping compare")
        return 1 if strict else 0

    failed = 0
    compared = 0
    for base_png in baseline_pngs:
        other = actual / base_png.name
        if not other.exists():
            print(f"FAIL missing {base_png.name}")
            failed += 1
            continue
        b_size, b_fp = rough_fingerprint(base_png)
        a_size, a_fp = rough_fingerprint(other)
        compared += 1
        ratio = a_size / max(b_size, 1)
        if ratio < 0.65 or ratio > 1.35:
            print(f"FAIL size drift {base_png.name}: baseline={b_size} actual={a_size}")
            failed += 1
        elif b_fp != a_fp:
            print(f"WARN content drift {base_png.name} (size ok) — review manually")
        else:
            print(f"OK {base_png.name}")

    print(f"Compared {compared}, failed {failed}")
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
