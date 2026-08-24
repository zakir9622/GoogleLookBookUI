#!/usr/bin/env python3
"""Quality benchmark: score generated shots against the eval set.

Compares each generated image to its ground-truth pair with SSIM and LPIPS,
reported per category so modest-wear regressions are visible immediately —
the eval set MUST include full_coverage and headscarf pairs (see
../train/README.md for the taxonomy and layout).

Usage:
  python compute_metrics.py dataset/eval generated/
      # generated/<category>/<pair_id>.jpg — one output per eval pair

pip install torch torchmetrics lpips pillow
"""

from __future__ import annotations

import argparse
import json
from collections import defaultdict
from pathlib import Path


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("eval_dir", type=Path)
    parser.add_argument("generated_dir", type=Path)
    parser.add_argument("--out", type=Path, default=Path("eval_report.json"))
    args = parser.parse_args()

    import lpips
    import numpy as np
    import torch
    from PIL import Image
    from torchmetrics.functional.image import structural_similarity_index_measure as ssim

    lpips_net = lpips.LPIPS(net="alex")
    scores: dict[str, list[tuple[float, float]]] = defaultdict(list)

    for category_dir in sorted(p for p in args.eval_dir.iterdir() if p.is_dir()):
        for pair_dir in sorted(p for p in category_dir.iterdir() if p.is_dir()):
            truth_path = pair_dir / "person.jpg"
            generated_path = args.generated_dir / category_dir.name / f"{pair_dir.name}.jpg"
            if not truth_path.exists() or not generated_path.exists():
                continue
            truth = to_tensor(Image.open(truth_path))
            generated = to_tensor(Image.open(generated_path).resize(Image.open(truth_path).size))
            ssim_value = float(ssim(generated, truth))
            lpips_value = float(lpips_net(generated * 2 - 1, truth * 2 - 1))
            scores[category_dir.name].append((ssim_value, lpips_value))

    if not scores:
        raise SystemExit("No matching eval/generated pairs found")

    report = {}
    print(f"{'category':<16} {'pairs':>5} {'SSIM↑':>8} {'LPIPS↓':>8}")
    for category, values in sorted(scores.items()):
        mean_ssim = sum(v[0] for v in values) / len(values)
        mean_lpips = sum(v[1] for v in values) / len(values)
        report[category] = {"pairs": len(values), "ssim": mean_ssim, "lpips": mean_lpips}
        print(f"{category:<16} {len(values):>5} {mean_ssim:>8.4f} {mean_lpips:>8.4f}")

    args.out.write_text(json.dumps(report, indent=2))
    print(f"\nreport → {args.out}")


def to_tensor(img):  # noqa: ANN001
    import numpy as np
    import torch

    arr = np.asarray(img.convert("RGB")).astype("float32") / 255.0
    return torch.from_numpy(arr).permute(2, 0, 1)[None]


if __name__ == "__main__":
    main()
