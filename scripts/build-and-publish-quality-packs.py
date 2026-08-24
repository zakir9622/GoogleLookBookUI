#!/usr/bin/env python3
"""Build + publish birefnet-v1 and realesrgan-v1 to Iamzakirzr/vestra-packs.

Downloads public ONNX weights (no GPU needed), writes pack.json + manifest,
then uploads with LOOKBOOK_HF_TOKEN / HF_TOKEN / local.properties lookbook.hf.token.

Usage:
  LOOKBOOK_HF_TOKEN=hf_… python3 scripts/build-and-publish-quality-packs.py
  python3 scripts/build-and-publish-quality-packs.py --dry-run
"""
from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
EXPORTS = ROOT / "ml" / "exports"
REPO_ID = "Iamzakirzr/vestra-packs"

BIREFNET_URL = (
    "https://github.com/ZhengPeng7/BiRefNet/releases/download/v1/"
    "BiRefNet-general-bb_swin_v1_tiny-epoch_232.onnx"
)
REALESRGAN_REPO = "James040/realesrganfp16-onnx-5mb"
REALESRGAN_FILE = "model.onnx"


def load_token() -> str:
    token = os.environ.get("LOOKBOOK_HF_TOKEN") or os.environ.get("HF_TOKEN")
    if token:
        return token.strip()
    props = ROOT / "local.properties"
    if props.exists():
        for line in props.read_text().splitlines():
            if line.startswith("lookbook.hf.token="):
                return line.split("=", 1)[1].strip()
    sys.exit(
        "No HF write token. Set LOOKBOOK_HF_TOKEN or add lookbook.hf.token=… to local.properties"
    )


def download(url: str, dest: Path) -> None:
    dest.parent.mkdir(parents=True, exist_ok=True)
    if dest.exists() and dest.stat().st_size > 1_000_000:
        print(f"OK cached {dest} ({dest.stat().st_size} bytes)")
        return
    print(f"Downloading {url} → {dest}")
    req = urllib.request.Request(url, headers={"User-Agent": "lookbook-pack-builder"})
    with urllib.request.urlopen(req, timeout=600) as resp, dest.open("wb") as out:
        while True:
            chunk = resp.read(1 << 20)
            if not chunk:
                break
            out.write(chunk)
    print(f"Wrote {dest} ({dest.stat().st_size} bytes)")


def build_birefnet() -> None:
    out = EXPORTS / "birefnet-v1"
    download(BIREFNET_URL, out / "birefnet.onnx")
    (out / "pack.json").write_text(
        json.dumps(
            {
                "version": 1,
                "tier": "LITE",
                "displayName": "BiRefNet matting",
                "description": "Cleaner garment/person mattes for Lite and Pro try-on (Swin-Tiny ONNX).",
                "minSpec": {"minRamMb": 4096, "requiresNpu": False, "minSdk": 35},
                "kind": "QUALITY",
                "license": "MIT",
                "source": "ZhengPeng7/BiRefNet release v1 tiny ONNX",
            },
            indent=2,
        )
        + "\n"
    )


def build_realesrgan() -> None:
    out = EXPORTS / "realesrgan-v1"
    out.mkdir(parents=True, exist_ok=True)
    dest = out / "realesrgan_x2.onnx"
    if not (dest.exists() and dest.stat().st_size > 1_000_000):
        from huggingface_hub import hf_hub_download

        path = hf_hub_download(repo_id=REALESRGAN_REPO, filename=REALESRGAN_FILE)
        dest.write_bytes(Path(path).read_bytes())
        print(f"Wrote {dest} ({dest.stat().st_size} bytes)")
    else:
        print(f"OK cached {dest}")
    (out / "pack.json").write_text(
        json.dumps(
            {
                "version": 1,
                "tier": "LITE",
                "displayName": "Real-ESRGAN upscale",
                "description": "Upscale try-on and Create outputs for listing-ready stills.",
                "minSpec": {"minRamMb": 2048, "requiresNpu": False, "minSdk": 35},
                "kind": "QUALITY",
                "scale": 2,
                "license": "BSD-3-Clause",
                "source": f"{REALESRGAN_REPO}/{REALESRGAN_FILE}",
            },
            indent=2,
        )
        + "\n"
    )


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--skip-build", action="store_true")
    args = parser.parse_args()

    if not args.skip_build:
        build_birefnet()
        build_realesrgan()

    subprocess.check_call(
        [sys.executable, str(ROOT / "ml" / "manifest_gen.py"), str(EXPORTS)],
        cwd=str(ROOT),
    )

    # Restrict local manifest to quality packs only before merge/upload
    # (publish-packs merges with remote so pro/lite stay).
    local_ids = {"birefnet-v1", "realesrgan-v1"}
    manifest_path = EXPORTS / "manifest.json"
    full = json.loads(manifest_path.read_text())
    local_only = {
        "schemaVersion": full.get("schemaVersion", 1),
        "packs": [p for p in full["packs"] if p["id"] in local_ids],
    }
    manifest_path.write_text(json.dumps(local_only, indent=2) + "\n")

    if args.dry_run:
        print("Dry run — packs ready under ml/exports/; skipping upload")
        return

    token = load_token()
    os.environ["LOOKBOOK_HF_TOKEN"] = token
    subprocess.check_call([sys.executable, str(ROOT / "scripts" / "publish-packs.py")], cwd=str(ROOT))
    print("Verify: python3 scripts/verify-manifest.py")


if __name__ == "__main__":
    main()
