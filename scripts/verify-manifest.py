#!/usr/bin/env python3
"""Fetch the live packs manifest and verify structure + pack integrity.

Exit 0 when OK; non-zero on degradation. Used in CI after publish.

Usage:
  python3 scripts/verify-manifest.py [--manifest-url URL]
"""
from __future__ import annotations

import argparse
import hashlib
import json
import sys
import urllib.request

DEFAULT_URL = (
    "https://huggingface.co/datasets/Iamzakirzr/vestra-packs/resolve/main/manifest.json"
)
REQUIRED_PACKS = ("lite-v1", "pro-v1", "local-gemma-4-e2b-v1", "local-functiongemma-v1")

# Fully-conditioned production Pro (convert_pro_pack.py / Colab notebook).
# Absence of any of these means a CatVTON 3-file stub overwrote pro-v1.
PRO_CONDITIONING_FILES = (
    "unet.onnx",
    "unet.onnx.data",
    "text_encoder.onnx",
    "text_encoder.onnx.data",
    "controlnet.onnx",
    "controlnet.onnx.data",
    "depth.onnx",
    "vae_encoder.onnx",
    "vae_decoder.onnx",
    "ip_image_encoder.onnx",
)
# External UNet weights for the live pack are ~1.8 GB.
PRO_UNET_DATA_MIN_BYTES = 1_000_000_000


def sha256_of(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def fetch(url: str) -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": "lookbook-verify/1.0"})
    with urllib.request.urlopen(req, timeout=60) as resp:
        return resp.read()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest-url", default=DEFAULT_URL)
    args = parser.parse_args()

    errors: list[str] = []
    body = fetch(args.manifest_url)
    manifest = json.loads(body)
    packs = {p["id"]: p for p in manifest.get("packs", [])}

    for pack_id in REQUIRED_PACKS:
        if pack_id not in packs:
            errors.append(f"MISSING required pack: {pack_id}")

    lite = packs.get("lite-v1")
    if lite:
        expected = {f["path"]: f for f in lite["files"]}
        for name in ("garment_seg.onnx", "human_parse.onnx"):
            if name not in expected:
                errors.append(f"lite-v1 missing file entry: {name}")
        # Spot-check ONNX files download and match sha256.
        for name in ("garment_seg.onnx", "human_parse.onnx"):
            if name not in expected:
                continue
            entry = expected[name]
            try:
                data = fetch(entry["url"])
            except Exception as exc:
                errors.append(f"lite-v1 {name} download failed: {exc}")
                continue
            if len(data) != entry["bytes"]:
                errors.append(
                    f"lite-v1 {name} size mismatch: got {len(data)}, expected {entry['bytes']}"
                )
            digest = sha256_of(data)
            if digest != entry["sha256"]:
                errors.append(f"lite-v1 {name} sha256 mismatch: {digest[:16]}…")
            else:
                print(f"OK: lite-v1/{name} ({len(data)} bytes, sha256 match)")

    pro = packs.get("pro-v1")
    if pro:
        if not pro.get("files"):
            errors.append("pro-v1 has no files")
        else:
            files = {f["path"]: f for f in pro["files"]}
            for name in PRO_CONDITIONING_FILES:
                if name not in files:
                    errors.append(
                        f"pro-v1 missing conditioning file: {name} "
                        "(pack must be fully-conditioned, not CatVTON 3-file stub)"
                    )
                else:
                    print(f"OK: pro-v1/{name} ({files[name]['bytes']} bytes listed)")
            unet_data = files.get("unet.onnx.data")
            if unet_data and unet_data["bytes"] < PRO_UNET_DATA_MIN_BYTES:
                errors.append(
                    f"pro-v1 unet.onnx.data too small ({unet_data['bytes']}); "
                    f"expected ≥ {PRO_UNET_DATA_MIN_BYTES} (fully-conditioned pack)"
                )
            # CatVTON stub only ships 3 ONNX graphs — fully-conditioned has many more.
            if len(files) < 10:
                errors.append(
                    f"pro-v1 only has {len(files)} files — looks like a stub, not conditioned pack"
                )

    for pack_id, pack in sorted(packs.items()):
        total = sum(f["bytes"] for f in pack.get("files", []))
        if total != pack.get("totalBytes"):
            errors.append(f"{pack_id}: totalBytes {pack['totalBytes']} != sum(files) {total}")
        print(f"OK: {pack_id} v{pack['version']} — {len(pack.get('files', []))} files")

    if errors:
        print("\nFAILURES:", file=sys.stderr)
        for err in errors:
            print(f"  - {err}", file=sys.stderr)
        sys.exit(1)

    print(f"\nManifest OK — {len(packs)} pack(s); pro-v1 fully-conditioned")


if __name__ == "__main__":
    main()
