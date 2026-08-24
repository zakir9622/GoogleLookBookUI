#!/usr/bin/env python3
"""Export Real-ESRGAN ONNX pack (realesrgan-v1) for 2× upscale after try-on/create.

Usage:
  # Preferred — download a small public ONNX (no GPU):
  python ml/export_realesrgan_pack.py --from-hub --out exports/realesrgan-v1

  # Or export a bilinear stub for CI smoke (not production quality):
  python ml/export_realesrgan_pack.py --stub --out exports/realesrgan-v1
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path

HUB_REPO = "James040/realesrganfp16-onnx-5mb"
HUB_FILE = "model.onnx"


def write_meta(out: Path, scale: int) -> None:
    meta = {
        "version": 1,
        "tier": "LITE",
        "displayName": f"Real-ESRGAN {scale}×",
        "description": "Upscale try-on and Create outputs for listing-ready stills.",
        "minSpec": {"minRamMb": 2048, "requiresNpu": False, "minSdk": 35},
        "kind": "QUALITY",
        "scale": scale,
        "license": "BSD-3-Clause",
    }
    (out / "pack.json").write_text(json.dumps(meta, indent=2) + "\n")


def from_hub(out: Path, scale: int) -> None:
    from huggingface_hub import hf_hub_download

    out.mkdir(parents=True, exist_ok=True)
    path = hf_hub_download(repo_id=HUB_REPO, filename=HUB_FILE)
    onnx_path = out / f"realesrgan_x{scale}.onnx"
    onnx_path.write_bytes(Path(path).read_bytes())
    write_meta(out, scale)
    print(f"Wrote {onnx_path} ({onnx_path.stat().st_size} bytes) from {HUB_REPO}")


def stub(out: Path, scale: int) -> None:
    try:
        import torch
    except ImportError as exc:
        raise SystemExit("Install torch for --stub, or use --from-hub") from exc

    out.mkdir(parents=True, exist_ok=True)

    class UpscaleStub(torch.nn.Module):
        def forward(self, x: torch.Tensor) -> torch.Tensor:
            return torch.nn.functional.interpolate(x, scale_factor=scale, mode="bilinear")

    model = UpscaleStub().eval()
    dummy = torch.randn(1, 3, 256, 256)
    onnx_path = out / f"realesrgan_x{scale}.onnx"
    torch.onnx.export(
        model,
        dummy,
        str(onnx_path),
        input_names=["input"],
        output_names=["output"],
        dynamic_axes={"input": {0: "batch", 2: "height", 3: "width"}},
        opset_version=17,
    )
    write_meta(out, scale)
    print(f"Wrote stub {onnx_path} ({onnx_path.stat().st_size} bytes)")


def main() -> None:
    parser = argparse.ArgumentParser(description="Export Real-ESRGAN ONNX quality pack")
    parser.add_argument("--out", type=Path, default=Path("exports/realesrgan-v1"))
    parser.add_argument("--scale", type=int, default=2, choices=[2, 4])
    parser.add_argument("--from-hub", action="store_true", help="Download public ONNX from HF")
    parser.add_argument("--stub", action="store_true", help="Export bilinear stub (CI only)")
    args = parser.parse_args()
    if args.from_hub or not args.stub:
        from_hub(args.out, args.scale)
    else:
        stub(args.out, args.scale)


if __name__ == "__main__":
    main()
