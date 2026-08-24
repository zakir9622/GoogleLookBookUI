#!/usr/bin/env python3
"""Benchmark local ONNX packs — cold vs cached session timing."""
from __future__ import annotations

import json
import sys
import time
from pathlib import Path

import numpy as np
import onnxruntime as ort

ROOT = Path(__file__).resolve().parents[1]
BUNDLED = ROOT / "composeApp/src/debug/assets/packs/lite-v1"
OUT = Path("/tmp/lookbook-benchmark-local.json")


def bench(path: Path, h: int, w: int, runs: int = 3) -> dict:
    times = []
    for i in range(runs):
        t0 = time.perf_counter()
        session = ort.InferenceSession(str(path), providers=["CPUExecutionProvider"])
        inp = session.get_inputs()[0].name
        chw = np.random.rand(1, 3, h, w).astype(np.float32)
        session.run(None, {inp: chw})
        times.append((time.perf_counter() - t0) * 1000)
    return {
        "model": path.name,
        "runs_ms": [round(t, 1) for t in times],
        "avg_ms": round(sum(times) / len(times), 1),
        "input": f"{h}x{w}",
    }


def main() -> int:
    results = []
    for name, (h, w) in (("garment_seg.onnx", (320, 320)), ("human_parse.onnx", (512, 512))):
        path = BUNDLED / name
        if not path.exists():
            print(f"MISSING {path}", file=sys.stderr)
            return 1
        results.append(bench(path, h, w))
    payload = {"lite_v1": results, "timestamp": time.time()}
    OUT.write_text(json.dumps(payload, indent=2))
    print(json.dumps(payload, indent=2))
    print(f"\nWrote {OUT}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
