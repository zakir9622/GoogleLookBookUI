#!/usr/bin/env python3
"""Upload model packs + manifest.json to the Hugging Face vestra-packs dataset.

Usage:
  python3 scripts/publish-packs.py [--dry-run]

Reads HF token from LOOKBOOK_HF_TOKEN env or local.properties lookbook.hf.token.
Expects ml/exports/manifest.json and pack directories under ml/exports/.
"""
from __future__ import annotations

import argparse
import json
import os
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
EXPORTS = ROOT / "ml" / "exports"
MANIFEST = EXPORTS / "manifest.json"
REPO_ID = "Iamzakirzr/vestra-packs"
BASE_URL = f"https://huggingface.co/datasets/{REPO_ID}/resolve/main"


def load_token() -> str:
    token = os.environ.get("LOOKBOOK_HF_TOKEN") or os.environ.get("HF_TOKEN")
    if token:
        return token.strip()
    props = ROOT / "local.properties"
    if props.exists():
        for line in props.read_text().splitlines():
            if line.startswith("lookbook.hf.token="):
                return line.split("=", 1)[1].strip()
    sys.exit("No HF token — set LOOKBOOK_HF_TOKEN or lookbook.hf.token in local.properties")


def merge_remote_manifest(local: dict, token: str) -> dict:
    """Keep remote packs not rebuilt locally (e.g. pro-v1)."""
    try:
        from huggingface_hub import hf_hub_download

        remote_path = hf_hub_download(
            repo_id=REPO_ID,
            repo_type="dataset",
            filename="manifest.json",
            token=token,
        )
        remote = json.loads(Path(remote_path).read_text())
    except Exception as exc:
        print(f"WARN: could not fetch remote manifest ({exc}); uploading local only")
        return local

    local_ids = {p["id"] for p in local.get("packs", [])}
    merged = list(local.get("packs", []))
    for pack in remote.get("packs", []):
        if pack["id"] not in local_ids:
            merged.append(pack)
    merged.sort(key=lambda p: p["id"])
    return {"schemaVersion": local.get("schemaVersion", 1), "packs": merged}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    if not MANIFEST.exists():
        sys.exit(f"Missing {MANIFEST} — run: cd ml && python3 manifest_gen.py exports/")

    token = load_token()
    local = json.loads(MANIFEST.read_text())
    manifest = merge_remote_manifest(local, token)
    MANIFEST.write_text(json.dumps(manifest, indent=2) + "\n")

    print(f"Manifest: {len(manifest['packs'])} pack(s)")
    for pack in manifest["packs"]:
        print(f"  {pack['id']} v{pack['version']} — {pack['totalBytes'] / 1e6:.1f} MB")

    if args.dry_run:
        print("Dry run — no upload")
        return

    from huggingface_hub import HfApi

    api = HfApi(token=token)

    # Upload only packs present locally (don't re-upload multi-GB pro-v1).
    for pack in local.get("packs", []):
        pack_dir = EXPORTS / pack["id"]
        if not pack_dir.is_dir():
            print(f"SKIP {pack['id']}: directory missing")
            continue
        print(f"Uploading {pack['id']}…")
        api.upload_folder(
            folder_path=str(pack_dir),
            path_in_repo=pack["id"],
            repo_id=REPO_ID,
            repo_type="dataset",
            commit_message=f"Publish {pack['id']} v{pack['version']}",
        )

    print("Uploading manifest.json…")
    api.upload_file(
        path_or_fileobj=MANIFEST.read_bytes(),
        path_in_repo="manifest.json",
        repo_id=REPO_ID,
        repo_type="dataset",
        commit_message="Update manifest.json",
    )
    print(f"Done: {BASE_URL}/manifest.json")


if __name__ == "__main__":
    main()
