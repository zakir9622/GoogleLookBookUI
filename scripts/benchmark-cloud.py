#!/usr/bin/env python3
"""Quick cloud model latency probe — writes JSON for CI / diagnostics."""
from __future__ import annotations

import argparse
import json
import os
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = Path("/tmp/lookbook-benchmark-cloud.json")


def main() -> int:
    parser = argparse.ArgumentParser(description="Benchmark cloud probes")
    parser.add_argument("--json", type=Path, default=OUT)
    parser.add_argument("--quick", action="store_true")
    args = parser.parse_args()

    probe = ROOT / "scripts" / "probe-models.py"
    if not probe.exists():
        print(f"MISSING {probe}", file=sys.stderr)
        return 1

    t0 = time.perf_counter()
    import subprocess

    cmd = [sys.executable, str(probe), "--json", str(args.json)]
    if args.quick:
        cmd.append("--quick")
    env = os.environ.copy()
    result = subprocess.run(cmd, capture_output=True, text=True, env=env)
    elapsed_ms = round((time.perf_counter() - t0) * 1000, 1)

    payload: dict = {"probe_elapsed_ms": elapsed_ms, "exit_code": result.returncode}
    if args.json.exists():
        try:
            payload["probe"] = json.loads(args.json.read_text())
        except json.JSONDecodeError:
            payload["probe_raw"] = args.json.read_text()[:2000]
    else:
        payload["stderr"] = result.stderr[-500:] if result.stderr else None

    summary_path = args.json.with_name("lookbook-benchmark-cloud-summary.json")
    summary_path.write_text(json.dumps(payload, indent=2))
    print(json.dumps(payload, indent=2))
    print(f"\nWrote {summary_path}")
    return result.returncode


if __name__ == "__main__":
    sys.exit(main())
