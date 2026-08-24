#!/usr/bin/env python3
"""Generate the model-pack manifest.json the app downloads from Hugging Face Hub.

Usage: python manifest_gen.py exports/ [--base-url URL] [--out exports/manifest.json]

Scans exports/<pack_id>/ directories. Each pack directory must contain a
pack.json describing the pack (tier, names, device gate); this script fills in
per-file sha256/bytes and download URLs, bumping the version when file hashes
changed relative to an existing manifest.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
from pathlib import Path

SCHEMA_VERSION = 1
DEFAULT_BASE_URL = "https://huggingface.co/datasets/Iamzakirzr/vestra-packs/resolve/main"


def sha256_of(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def build_pack(pack_dir: Path, base_url: str, previous: dict | None) -> dict:
    meta = json.loads((pack_dir / "pack.json").read_text())
    files = []
    for file_path in sorted(pack_dir.rglob("*")):
        if not file_path.is_file() or file_path.name == "pack.json":
            continue
        rel = file_path.relative_to(pack_dir).as_posix()
        files.append(
            {
                "path": rel,
                "url": f"{base_url}/{pack_dir.name}/{rel}",
                "sha256": sha256_of(file_path),
                "bytes": file_path.stat().st_size,
            }
        )
    if not files:
        raise SystemExit(f"{pack_dir}: no model files found")

    version = int(meta.get("version", 1))
    if previous is not None:
        prev_hashes = {f["path"]: f["sha256"] for f in previous["files"]}
        new_hashes = {f["path"]: f["sha256"] for f in files}
        if prev_hashes != new_hashes:
            version = previous["version"] + 1
        else:
            version = previous["version"]

    pack = {
        "id": pack_dir.name,
        "version": version,
        "tier": meta["tier"],
        "displayName": meta["displayName"],
        "description": meta["description"],
        "totalBytes": sum(f["bytes"] for f in files),
        "files": files,
        "minSpec": meta.get(
            "minSpec", {"minRamMb": 0, "requiresNpu": False, "minSdk": 35}
        ),
    }
    # Dev packs (non-commercial research weights) carry their license notice and
    # must only ever be published to a private dev manifest — never production.
    if meta.get("devOnly"):
        pack["devOnly"] = True
        pack["licenseNotice"] = meta.get("licenseNotice", "Research license — private testing only")
    # MODELS packs supply the studio-model gallery instead of engine weights.
    if meta.get("kind") == "MODELS":
        pack["kind"] = "MODELS"
        pack["studioModels"] = meta.get("studioModels", [])
    return pack


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("exports_dir", type=Path)
    parser.add_argument("--base-url", default=DEFAULT_BASE_URL)
    parser.add_argument("--out", type=Path, default=None)
    args = parser.parse_args()

    out_path = args.out or args.exports_dir / "manifest.json"
    previous_packs: dict[str, dict] = {}
    if out_path.exists():
        previous_packs = {
            p["id"]: p for p in json.loads(out_path.read_text())["packs"]
        }

    packs = []
    for pack_dir in sorted(args.exports_dir.iterdir()):
        if pack_dir.is_dir() and (pack_dir / "pack.json").exists():
            packs.append(
                build_pack(pack_dir, args.base_url, previous_packs.get(pack_dir.name))
            )

    if not packs:
        sys.exit(f"No pack directories with pack.json found under {args.exports_dir}")

    out_path.write_text(
        json.dumps({"schemaVersion": SCHEMA_VERSION, "packs": packs}, indent=2)
    )
    print(f"Wrote {out_path} with {len(packs)} pack(s):")
    for pack in packs:
        print(f"  {pack['id']} v{pack['version']} — {pack['totalBytes'] / 1e6:.1f} MB")


if __name__ == "__main__":
    main()
