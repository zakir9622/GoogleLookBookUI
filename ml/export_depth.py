#!/usr/bin/env python3
"""Export a lightweight depth estimator to ONNX for the on-device Stage A
(structural conditioning). Produces `depth.onnx` for the Pro pack.

Depth (not OpenPose) is deliberate: a depth map preserves the *volume* of
flowing modest-wear (abaya, kaftan, dupatta drape) as well as fitted western
wear, which pose skeletons flatten. MediaPipe ships no general monocular-depth
model, so we use Depth-Anything-V2-Small (Apache-2.0), which is small and
mobile-friendly.

Output: depth.onnx  [1,3,518,518] → [1,1,518,518] (normalized inverse depth),
which the app resizes to the ControlNet conditioning resolution (512).

Usage: python export_depth.py --out exports/pro-v1/depth.onnx
Deps: pip install torch transformers onnx
"""

from __future__ import annotations

import argparse
from pathlib import Path

SIZE = 518  # Depth-Anything-V2 native input


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", type=Path, default=Path("exports/pro-v1/depth.onnx"))
    parser.add_argument("--opset", type=int, default=17)
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    if args.dry_run:
        print(f"dry-run: would export Depth-Anything-V2-Small → {args.out}")
        return

    import torch
    from transformers import AutoModelForDepthEstimation

    args.out.parent.mkdir(parents=True, exist_ok=True)
    model = AutoModelForDepthEstimation.from_pretrained(
        "depth-anything/Depth-Anything-V2-Small-hf"
    ).eval()

    class Depth(torch.nn.Module):
        def __init__(self, m):  # noqa: ANN001
            super().__init__(); self.m = m
        def forward(self, x):  # noqa: ANN001
            d = self.m(pixel_values=x).predicted_depth  # [1,H,W]
            d = d.unsqueeze(1)
            lo = d.amin((2, 3), keepdim=True)
            hi = d.amax((2, 3), keepdim=True)
            return (d - lo) / (hi - lo + 1e-6)  # 0..1 inverse depth

    torch.onnx.export(
        Depth(model), (torch.zeros(1, 3, SIZE, SIZE),), str(args.out),
        input_names=["image"], output_names=["depth"], opset_version=args.opset,
        dynamic_axes={"image": {0: "b"}, "depth": {0: "b"}},
    )
    print(f"Wrote {args.out}")


if __name__ == "__main__":
    main()
