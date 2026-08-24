#!/usr/bin/env python3
"""Publish the local-qwen3-06b-v1 pack to the vestra-packs dataset.

Qwen3 0.6B INT4 is 331 MB against Gemma 4 E2B's 2.47 GB, so its cold load is a
fraction as long on the CPU backend the app defaults to. That makes it the
fastest offline route for Code Studio and the News chat tab.

The 331 MB weights are NOT re-hosted: the manifest entry points straight at
litert-community (upstream of Google's AI Edge Gallery), so this script only
uploads two small metadata files and rewrites manifest.json. That keeps the
dataset repo small and the weights canonical.

Usage:
  python3 scripts/publish-qwen3-pack.py [--dry-run]

Reads the HF token from LOOKBOOK_HF_TOKEN / HF_TOKEN env, or from
local.properties `lookbook.hf.token` (same contract as publish-packs.py).
The token needs write access to the dataset repo.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import sys
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PACK_DIR = ROOT / "ml" / "packs" / "local-qwen3-06b-v1"
REPO_ID = "Iamzakirzr/vestra-packs"
BASE_URL = f"https://huggingface.co/datasets/{REPO_ID}/resolve/main"
PACK_ID = "local-qwen3-06b-v1"

# Upstream weights — litert-community, the source Google's AI Edge Gallery draws on.
WEIGHTS_NAME = "qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm"
WEIGHTS_URL = (
    "https://huggingface.co/litert-community/Qwen3-0.6B-int4/resolve/main/" + WEIGHTS_NAME
)
# LFS oid from the HF paths-info API — LFS oids are sha256.
WEIGHTS_SHA256 = "2df6821ec12702dafd33915e7a1a1adc7c4b053f3672fd9555dfaf3a114c4139"
WEIGHTS_BYTES = 347_251_840

# Insert after this pack so manifest ordering stays stable.
INSERT_AFTER = "local-gemma-v1"


def load_token() -> str:
    token = os.environ.get("LOOKBOOK_HF_TOKEN") or os.environ.get("HF_TOKEN")
    if token:
        return token.strip()
    props = ROOT / "local.properties"
    if props.is_file():
        for line in props.read_text().splitlines():
            key, _, value = line.partition("=")
            if key.strip() == "lookbook.hf.token" and value.strip():
                return value.strip()
    sys.exit(
        "No HF token. Set LOOKBOOK_HF_TOKEN (or HF_TOKEN), or add "
        "lookbook.hf.token=... to local.properties."
    )


def sha256_of(path: Path) -> str:
    digest = hashlib.sha256()
    digest.update(path.read_bytes())
    return digest.hexdigest()


def fetch_manifest() -> dict:
    url = f"{BASE_URL}/manifest.json"
    with urllib.request.urlopen(url) as response:
        return json.loads(response.read().decode())


def build_entry() -> dict:
    files = []
    for name in ("config.json", "pack.json"):
        path = PACK_DIR / name
        if not path.is_file():
            sys.exit(f"Missing {path} — pack metadata is checked into the repo.")
        files.append(
            {
                "path": name,
                "url": f"{BASE_URL}/{PACK_ID}/{name}",
                "sha256": sha256_of(path),
                "bytes": path.stat().st_size,
            }
        )
    files.append(
        {
            "path": WEIGHTS_NAME,
            "url": WEIGHTS_URL,
            "sha256": WEIGHTS_SHA256,
            "bytes": WEIGHTS_BYTES,
        }
    )
    meta = json.loads((PACK_DIR / "pack.json").read_text())
    return {
        "id": PACK_ID,
        "version": meta["version"],
        "tier": meta["tier"],
        "displayName": meta["displayName"],
        "description": meta["description"],
        "totalBytes": sum(f["bytes"] for f in files),
        "files": files,
        "minSpec": meta["minSpec"],
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--dry-run", action="store_true", help="print the manifest, upload nothing")
    args = parser.parse_args()

    manifest = fetch_manifest()
    ids = [p["id"] for p in manifest["packs"]]
    entry = build_entry()

    if PACK_ID in ids:
        manifest["packs"][ids.index(PACK_ID)] = entry
        print(f"Replacing existing {PACK_ID} entry.")
    else:
        index = ids.index(INSERT_AFTER) + 1 if INSERT_AFTER in ids else len(manifest["packs"])
        manifest["packs"].insert(index, entry)
        print(f"Inserting {PACK_ID} at position {index}.")

    rendered = json.dumps(manifest, indent=2) + "\n"

    if args.dry_run:
        print(json.dumps(entry, indent=2))
        print(f"\n[dry-run] manifest would be {len(rendered)} bytes, "
              f"{len(manifest['packs'])} packs. Nothing uploaded.")
        return

    try:
        from huggingface_hub import HfApi
    except ImportError:
        sys.exit("pip install huggingface_hub")

    api = HfApi(token=load_token())
    for name in ("config.json", "pack.json"):
        api.upload_file(
            path_or_fileobj=str(PACK_DIR / name),
            path_in_repo=f"{PACK_ID}/{name}",
            repo_id=REPO_ID,
            repo_type="dataset",
            commit_message=f"Add {PACK_ID}/{name}",
        )
        print(f"Uploaded {PACK_ID}/{name}")

    api.upload_file(
        path_or_fileobj=rendered.encode(),
        path_in_repo="manifest.json",
        repo_id=REPO_ID,
        repo_type="dataset",
        commit_message=f"Add {PACK_ID} to manifest (weights hosted upstream)",
    )
    print("Uploaded manifest.json")
    print(f"\nDone. Verify with: python3 scripts/verify-manifest.py")


if __name__ == "__main__":
    main()
