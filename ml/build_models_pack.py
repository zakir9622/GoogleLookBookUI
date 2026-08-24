#!/usr/bin/env python3
"""Build a studio-models pack from the owner's base-model photos.

The Lookbook's casting gallery is populated from a MODELS pack. This packages a
folder of model photos (the person the garment is rendered onto) into a pack the
app downloads, with per-model metadata (display name + tags for filtering, e.g.
"hijab", "seated", "plus"). Until such a pack is installed the app falls back to
the four bundled silhouettes.

Input layout:
  models_src/
    <model_id>.jpg              # full-body, neutral pose, clean background best
    <model_id>.json  (optional) # {"displayName": "...", "tags": ["hijab", ...]}

Usage:
  python build_models_pack.py models_src/ --out exports/studio-models-v1 \
      [--size 900x1200]

Then: python manifest_gen.py exports/   (emits the MODELS pack into manifest.json)

IMPORTANT: only include images you have the rights to use commercially. For a
modest-wear catalog, include hijab-wearing models and a range of body types and
skin tones so the casting gallery represents the customer base.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("src", type=Path)
    parser.add_argument("--out", type=Path, default=Path("exports/studio-models-v1"))
    parser.add_argument("--size", default="900x1200")
    args = parser.parse_args()

    from PIL import Image, ImageOps

    width, height = (int(v) for v in args.size.split("x"))
    args.out.mkdir(parents=True, exist_ok=True)

    studio_models = []
    for image_path in sorted(p for p in args.src.iterdir() if p.suffix.lower() in {".jpg", ".jpeg", ".png"}):
        model_id = image_path.stem
        sidecar = image_path.with_suffix(".json")
        meta = json.loads(sidecar.read_text()) if sidecar.exists() else {}

        img = ImageOps.fit(Image.open(image_path).convert("RGB"), (width, height))
        out_file = f"{model_id}.jpg"
        img.save(args.out / out_file, quality=92)

        studio_models.append(
            {
                "id": model_id,
                "displayName": meta.get("displayName", model_id.replace("_", " ").title()),
                "file": out_file,
                "tags": meta.get("tags", []),
            }
        )

    if not studio_models:
        raise SystemExit(f"No images found under {args.src}")

    (args.out / "pack.json").write_text(
        json.dumps(
            {
                "version": 1,
                "tier": "LITE",  # unused for MODELS packs; kept for schema compatibility
                "kind": "MODELS",
                "displayName": "Studio models",
                "description": "Photorealistic base models for the casting gallery.",
                "minSpec": {"minRamMb": 0, "requiresNpu": False, "minSdk": 35},
                "studioModels": studio_models,
            },
            indent=2,
        )
    )
    total = sum(f.stat().st_size for f in args.out.rglob("*") if f.is_file())
    print(f"Studio-models pack: {len(studio_models)} models, {total / 1e6:.1f} MB at {args.out}")
    print("Next: python manifest_gen.py exports/ , then upload to the packs repo.")


if __name__ == "__main__":
    main()
