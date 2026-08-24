#!/usr/bin/env python3
"""Emit local catalog runnable matrix (generation-stability M5) without HF/device.

Parses LocalModelCatalog.kt for id / packId / runnable / approxSizeLabel.
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / "shared/src/commonMain/kotlin/com/zakir/vestra/shared/local/LocalModelCatalog.kt"


def parse_catalog(text: str) -> list[dict]:
    entries: list[dict] = []
    # Split on LocalModelEntry( blocks
    for block in re.split(r"LocalModelEntry\(", text)[1:]:
        def field(name: str) -> str | None:
            m = re.search(rf'{name}\s*=\s*"([^"]*)"', block)
            if m:
                return m.group(1)
            m = re.search(rf"{name}\s*=\s*(true|false|null)", block)
            return m.group(1) if m else None

        eid = field("id")
        if not eid:
            continue
        runnable_raw = field("runnable")
        pack_id = field("packId")
        if pack_id == "null":
            pack_id = None
        entries.append(
            {
                "id": eid,
                "displayName": field("displayName"),
                "packId": pack_id,
                "runnable": runnable_raw == "true",
                "approxSizeLabel": field("approxSizeLabel"),
                "testingNote": field("testingNote"),
                "status": (
                    "READY_WHEN_INSTALLED"
                    if runnable_raw == "true"
                    else "NOT_PUBLISHED"
                    if pack_id
                    else "PLANNED"
                ),
            }
        )
    return entries


def main() -> int:
    if not CATALOG.is_file():
        print(f"ERROR: missing {CATALOG}", file=sys.stderr)
        return 2
    entries = parse_catalog(CATALOG.read_text())
    runnable = [e for e in entries if e["runnable"]]
    blocked = [e for e in entries if not e["runnable"]]
    report = {
        "source": str(CATALOG.relative_to(ROOT)),
        "count": len(entries),
        "runnableCount": len(runnable),
        "notPublishedCount": len(blocked),
        "entries": entries,
    }
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else None
    text = json.dumps(report, indent=2)
    if out:
        out.write_text(text)
        print(f"Wrote {out}")
    else:
        print(text)
    # Non-zero only if parse failed to find anything
    return 0 if entries else 1


if __name__ == "__main__":
    raise SystemExit(main())
