#!/usr/bin/env python3
"""Assemble local-gemma-4-e2b-v1 from litert-community HuggingFace export.

Downloads gemma-4-E2B-it.litertlm and writes manifest_gen-ready layout under ml/exports/.

Usage:
  HF_TOKEN=… python3 scripts/assemble-local-gemma4-pack.py
  python3 scripts/assemble-local-gemma4-pack.py --functiongemma
  cd ml && python3 manifest_gen.py exports/ && python3 ../scripts/publish-packs.py
"""
from __future__ import annotations

import argparse
import json
import os
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_REPO = "litert-community/gemma-4-E2B-it-litert-lm"
PRIMARY_FILE = "gemma-4-E2B-it.litertlm"
FUNCTION_REPO = "litert-community/functiongemma-mobile-actions_q8_ekv1024.litertlm"
FUNCTION_FILE = "mobile-actions_q8_ekv1024.litertlm"


def download(repo: str, filename: str, dest: Path, token: str | None) -> None:
    from huggingface_hub import hf_hub_download

    path = hf_hub_download(repo_id=repo, filename=filename, token=token)
    dest.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(path, dest)
    print(f"  {dest.name}: {dest.stat().st_size / 1e9:.2f} GB")


def write_gemma4_pack(out: Path) -> None:
    out.mkdir(parents=True, exist_ok=True)
    (out / "config.json").write_text(
        json.dumps(
            {
                "runtime": "litert-lm",
                "primaryFile": PRIMARY_FILE,
                "capability": "code",
                "vision": True,
                "audio": True,
                "tools": False,
                "backendDefault": "cpu",
            },
            indent=2,
        )
        + "\n",
    )
    (out / "pack.json").write_text(
        json.dumps(
            {
                "version": 1,
                "tier": "LITE",
                "displayName": "Gemma 4 E2B (LiteRT-LM)",
                "description": "Gallery-class Gemma 4 for Code, vision assist, and audio transcribe.",
                "minSpec": {"minRamMb": 8192, "requiresNpu": False, "minSdk": 35},
            },
            indent=2,
        )
        + "\n",
    )


def write_functiongemma_pack(out: Path) -> None:
    out.mkdir(parents=True, exist_ok=True)
    (out / "config.json").write_text(
        json.dumps(
            {
                "runtime": "litert-lm",
                "primaryFile": FUNCTION_FILE,
                "capability": "tools",
                "vision": False,
                "audio": False,
                "tools": True,
                "backendDefault": "cpu",
            },
            indent=2,
        )
        + "\n",
    )
    (out / "pack.json").write_text(
        json.dumps(
            {
                "version": 1,
                "tier": "LITE",
                "displayName": "FunctionGemma 270M tools",
                "description": "Experimental local tool calling (Mobile Actions class).",
                "minSpec": {"minRamMb": 4096, "requiresNpu": False, "minSdk": 35},
            },
            indent=2,
        )
        + "\n",
    )


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", type=Path, default=ROOT / "ml" / "exports" / "local-gemma-4-e2b-v1")
    parser.add_argument("--repo", default=DEFAULT_REPO)
    parser.add_argument("--functiongemma", action="store_true")
    args = parser.parse_args()
    token = (
        os.environ.get("HF_TOKEN")
        or os.environ.get("LOOKBOOK_HF_TOKEN")
        or os.environ.get("HUGGING_FACE_HUB_TOKEN")
    )

    if args.functiongemma:
        out = ROOT / "ml" / "exports" / "local-functiongemma-v1"
        write_functiongemma_pack(out)
        dest = out / FUNCTION_FILE
        if not dest.is_file() or dest.stat().st_size < 100_000_000:
            print(f"Downloading {FUNCTION_FILE}…")
            download(FUNCTION_REPO, FUNCTION_FILE, dest, token)
        else:
            print(f"  skip {FUNCTION_FILE} ({dest.stat().st_size / 1e6:.0f} MB)")
        return

    out = args.out
    write_gemma4_pack(out)
    dest = out / PRIMARY_FILE
    if dest.is_file() and dest.stat().st_size > 500_000_000:
        print(f"  skip {PRIMARY_FILE} ({dest.stat().st_size / 1e9:.2f} GB)")
    else:
        print(f"Downloading {PRIMARY_FILE} from {args.repo}…")
        download(args.repo, PRIMARY_FILE, dest, token)
    print(f"Ready: {out}")


if __name__ == "__main__":
    main()
