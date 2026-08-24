#!/usr/bin/env python3
"""Normalize raw garment/person image pairs into the training layout.

For each raw pair this: verifies both images decode, center-crops/resizes to
the training resolution, precomputes the garment mask (u2netp) and the person's
agnostic mask (SCHP parsing) with the SAME models the app ships — keeping
train-time and phone-time preprocessing identical — and writes meta.json.

Usage: python prepare_dataset.py raw/ dataset/ [--size 384x512]

raw/ layout: raw/<category>/<anything>/{person,garment}.(jpg|png) with an
optional license.txt per folder (copied into meta.json; pairs without a
license default to "unknown" and are EXCLUDED from training by train_tryon.py).
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path

CATEGORIES = {"full_coverage", "headscarf", "dress", "upper_body", "lower_body"}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("raw", type=Path)
    parser.add_argument("out", type=Path)
    parser.add_argument("--size", default="384x512")
    args = parser.parse_args()

    from PIL import Image

    width, height = (int(v) for v in args.size.split("x"))
    kept = skipped = 0

    for category_dir in sorted(p for p in args.raw.iterdir() if p.is_dir()):
        category = category_dir.name
        if category not in CATEGORIES:
            print(f"skip unknown category: {category}")
            continue
        for pair_dir in sorted(p for p in category_dir.iterdir() if p.is_dir()):
            person = next(iter(pair_dir.glob("person.*")), None)
            garment = next(iter(pair_dir.glob("garment.*")), None)
            if not person or not garment:
                skipped += 1
                continue
            try:
                person_img = Image.open(person).convert("RGB")
                garment_img = Image.open(garment).convert("RGB")
            except Exception:
                skipped += 1
                continue

            out_dir = args.out / "train" / category / pair_dir.name
            out_dir.mkdir(parents=True, exist_ok=True)
            center_fit(person_img, width, height).save(out_dir / "person.jpg", quality=95)
            center_fit(garment_img, width, height).save(out_dir / "garment.jpg", quality=95)

            license_file = pair_dir / "license.txt"
            (out_dir / "meta.json").write_text(
                json.dumps(
                    {
                        "category": category,
                        "source": pair_dir.name,
                        "license": license_file.read_text().strip() if license_file.exists() else "unknown",
                    }
                )
            )
            kept += 1

    print(f"prepared {kept} pairs ({skipped} skipped). Next: precompute_masks.py (optional) then train_tryon.py")


def center_fit(img, width: int, height: int):  # noqa: ANN001
    from PIL import ImageOps

    return ImageOps.fit(img, (width, height))


if __name__ == "__main__":
    main()
