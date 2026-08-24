#!/usr/bin/env python3
"""Integration tests for local ONNX model packs (lite-v1).

Exercises the same graphs the Android LiteEngine loads:
  garment_seg.onnx @ 320×320
  human_parse.onnx @ 512×512 (ATR parser)

Tests bundled debug assets AND files downloaded from the live HF manifest.

Usage:
  python3 scripts/integration-local-models.py [--skip-hf-download]

Exit 0 when all required checks pass.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import sys
import tempfile
import urllib.request
from pathlib import Path

import numpy as np
import onnxruntime as ort

ROOT = Path(__file__).resolve().parents[1]
BUNDLED_LITE = ROOT / "composeApp/src/debug/assets/packs/lite-v1"
MANIFEST_URL = (
    "https://huggingface.co/datasets/Iamzakirzr/vestra-packs/resolve/main/manifest.json"
)


def sha256_of(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def fetch(url: str) -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": "lookbook-integration/1.0"})
    with urllib.request.urlopen(req, timeout=120) as resp:
        return resp.read()


def smoke_onnx(model_path: Path, height: int, width: int, label: str) -> None:
    session = ort.InferenceSession(
        str(model_path),
        providers=["CPUExecutionProvider"],
    )
    inp = session.get_inputs()[0]
    name = inp.name
    chw = np.random.rand(1, 3, height, width).astype(np.float32)
    outputs = session.run(None, {name: chw})
    assert outputs and outputs[0] is not None, f"{label}: empty output"
    out_shape = outputs[0].shape
    print(f"  OK  {label}: {model_path.name} in=(1,3,{height},{width}) out={out_shape}")


def test_bundled_assets() -> None:
    print("== Bundled debug lite-v1 assets ==")
    for name in ("garment_seg.onnx", "human_parse.onnx"):
        path = BUNDLED_LITE / name
        if not path.exists():
            raise SystemExit(f"MISSING bundled asset: {path}")
    smoke_onnx(BUNDLED_LITE / "garment_seg.onnx", 320, 320, "bundled garment_seg")
    smoke_onnx(BUNDLED_LITE / "human_parse.onnx", 512, 512, "bundled human_parse")


def smoke_realesrgan(model_path: Path) -> None:
    """James040 FP16 graph: input NCHW float16 + denoise_strength float16."""
    session = ort.InferenceSession(str(model_path), providers=["CPUExecutionProvider"])
    names = {i.name for i in session.get_inputs()}
    assert "input" in names and "denoise_strength" in names, f"unexpected inputs: {names}"
    h = w = 64
    feeds = {
        "input": np.random.rand(1, 3, h, w).astype(np.float16),
        "denoise_strength": np.array([0], dtype=np.float16),
    }
    outputs = session.run(None, feeds)
    assert outputs and outputs[0] is not None
    out = outputs[0]
    assert out.dtype == np.float16, f"expected float16 out, got {out.dtype}"
    assert out.shape[2] >= h and out.shape[3] >= w, f"upscale too small: {out.shape}"
    print(f"  OK  realesrgan smoke: {model_path.name} out={out.shape}")


def smoke_birefnet(model_path: Path) -> None:
    session = ort.InferenceSession(str(model_path), providers=["CPUExecutionProvider"])
    inp = session.get_inputs()[0]
    assert inp.name == "input_image", f"unexpected BiRefNet input: {inp.name}"
    chw = np.random.randn(1, 3, 1024, 1024).astype(np.float32)
    outputs = session.run(None, {inp.name: chw})
    assert outputs and outputs[0] is not None
    out = outputs[0]
    assert out.shape == (1, 1, 1024, 1024), f"unexpected BiRefNet out: {out.shape}"
    print(f"  OK  birefnet smoke: {model_path.name} out={out.shape}")


def smoke_quality_packs_from_hf(packs: dict) -> None:
    """Download + run quality packs when present on the live manifest."""
    print("\n== Quality packs (BiRefNet / Real-ESRGAN) ==")
    for pack_id, smoker in (
        ("realesrgan-v1", smoke_realesrgan),
        ("birefnet-v1", smoke_birefnet),
    ):
        if pack_id not in packs:
            print(f"  … {pack_id} not on manifest")
            continue
        pack = packs[pack_id]
        onnx_files = [f for f in pack["files"] if f["path"].endswith(".onnx")]
        if not onnx_files:
            raise SystemExit(f"{pack_id}: no ONNX in manifest")
        entry = onnx_files[0]
        print(f"  … downloading {pack_id}/{entry['path']} ({entry['bytes'] / 1e6:.1f} MB)")
        data = fetch(entry["url"])
        if len(data) != entry["bytes"]:
            raise SystemExit(f"{pack_id} size mismatch")
        if sha256_of(data) != entry["sha256"]:
            raise SystemExit(f"{pack_id} sha256 mismatch")
        with tempfile.TemporaryDirectory(prefix=f"{pack_id}-") as tmp:
            dest = Path(tmp) / entry["path"]
            dest.write_bytes(data)
            smoker(dest)


def test_hf_manifest_and_download() -> None:
    print("\n== Live HF manifest + download ==")
    manifest = json.loads(fetch(MANIFEST_URL))
    packs = {p["id"]: p for p in manifest["packs"]}

    for required in ("lite-v1", "pro-v1"):
        if required not in packs:
            raise SystemExit(f"manifest missing pack: {required}")
        print(f"  OK  manifest lists {required} v{packs[required]['version']}")

    lite = packs["lite-v1"]
    files = {f["path"]: f for f in lite["files"]}

    with tempfile.TemporaryDirectory(prefix="lite-integ-") as tmp:
        tmp_path = Path(tmp)
        for onnx_name, (h, w) in (
            ("garment_seg.onnx", (320, 320)),
            ("human_parse.onnx", (512, 512)),
        ):
            entry = files[onnx_name]
            print(f"  … downloading {onnx_name} ({entry['bytes'] / 1e6:.1f} MB)")
            data = fetch(entry["url"])
            if len(data) != entry["bytes"]:
                raise SystemExit(
                    f"{onnx_name} size mismatch: got {len(data)}, expected {entry['bytes']}"
                )
            if sha256_of(data) != entry["sha256"]:
                raise SystemExit(f"{onnx_name} sha256 mismatch")
            dest = tmp_path / onnx_name
            dest.write_bytes(data)
            smoke_onnx(dest, h, w, f"HF {onnx_name}")

    # pro-v2-int8: optional until published to HF manifest
    if "pro-v2-int8" in packs:
        print(f"  OK  manifest lists pro-v2-int8 v{packs['pro-v2-int8']['version']}")
    else:
        print("  … pro-v2-int8 not on manifest yet (pro-v1 preferred in app)")

    for optional in ("birefnet-v1", "realesrgan-v1", "lite-v2"):
        if optional in packs:
            print(f"  OK  optional pack {optional} listed")

    smoke_quality_packs_from_hf(packs)

    pro = packs["pro-v1"]
    config = next((f for f in pro["files"] if f["path"] == "config.json"), None)
    if config is None:
        raise SystemExit("pro-v1 missing config.json in manifest")
    cfg_bytes = fetch(config["url"])
    cfg = json.loads(cfg_bytes)
    print(f"  OK  pro-v1 config.json ({len(cfg_bytes)} bytes) keys={list(cfg.keys())[:6]}…")
    unet = next((f for f in pro["files"] if f["path"] == "unet.onnx"), None)
    if unet:
        unet_data = next((f for f in pro["files"] if f["path"] == "unet.onnx.data"), None)
        data_gb = (unet_data["bytes"] / 1e9) if unet_data else 0.0
        combined_gb = (unet["bytes"] + (unet_data["bytes"] if unet_data else 0)) / 1e9
        # HEAD-style: fetch only first byte via Range to prove host serves the file.
        req = urllib.request.Request(
            unet["url"],
            headers={"User-Agent": "lookbook-integration/1.0", "Range": "bytes=0-0"},
        )
        with urllib.request.urlopen(req, timeout=60) as resp:
            if resp.status not in (200, 206):
                raise SystemExit(f"pro-v1 unet.onnx unreachable: HTTP {resp.status}")
        label = f"pro-v1/unet.onnx reachable"
        if unet_data:
            label += f" + unet.onnx.data ({data_gb:.2f} GB external) → {combined_gb:.2f} GB total"
        else:
            label += f" ({unet['bytes'] / 1e9:.2f} GB)"
        print(f"  OK  {label}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--skip-hf-download",
        action="store_true",
        help="Only test bundled assets (faster, offline)",
    )
    args = parser.parse_args()

    test_bundled_assets()
    if not args.skip_hf_download:
        test_hf_manifest_and_download()

    print("\nLocal model integration: PASS")
    return 0


if __name__ == "__main__":
    sys.exit(main())
