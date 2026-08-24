#!/usr/bin/env bash
# A0 — run on-device ORT benchmark and materialize docs/BENCHMARKS.md
#
# Requires: adb + a connected device/emulator with the sideloadDebug APK's
# instrumentation tests (bundled lite-v1 assets).
#
# Usage:
#   bash scripts/benchmark-on-device.sh
#   LOOKBOOK_BENCH_DEVICE=serial bash scripts/benchmark-on-device.sh
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

export ANDROID_HOME="${ANDROID_HOME:-/home/ubuntu/android-sdk}"
export PATH="${ANDROID_HOME}/platform-tools:${ANDROID_HOME}/emulator:${PATH:-}"

ADB=(adb)
if [[ -n "${LOOKBOOK_BENCH_DEVICE:-}" ]]; then
  ADB=(adb -s "$LOOKBOOK_BENCH_DEVICE")
fi

echo "=== Lookbook on-device ORT benchmark ==="
"${ADB[@]}" devices -l
DEVICE_LINE="$("${ADB[@]}" devices | awk 'NR>1 && $2=="device" {print $1; exit}')"
if [[ -z "$DEVICE_LINE" ]]; then
  echo "ERROR: no adb device. Connect a Pixel 9 (or start the emulator) and retry." >&2
  exit 1
fi
echo "Using device: $DEVICE_LINE"

echo "--- Assemble + instrumented test ---"
./gradlew :composeApp:connectedSideloadDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.zakir.vestra.OnDeviceOrtBenchmarkTest \
  --info 2>&1 | tee /tmp/lookbook-bench-gradle.log | tail -80

PKG="com.zakir.vestra.debug"
# Prefer external files (world-readable on debug), then run-as internal.
PULL_JSON="/tmp/lookbook-on-device-ort.json"
rm -f "$PULL_JSON"
EXT_PATH="$("${ADB[@]}" shell "run-as $PKG ls files/benchmarks/on-device-ort.json 2>/dev/null" | tr -d '\r' || true)"
if "${ADB[@]}" shell "run-as $PKG cat files/benchmarks/on-device-ort.json" >"$PULL_JSON" 2>/dev/null; then
  echo "Pulled via run-as"
else
  # External files path varies by API — scrape logcat for WROTE line or JSON dump.
  echo "run-as failed — harvesting LookbookBench logcat JSON…"
  "${ADB[@]}" logcat -d -s LookbookBench:I LookbookOrtEp:I | tee /tmp/lookbook-bench-logcat.txt >/dev/null
  # Reconstruct JSON from log lines between first { and last }
  python3 - <<'PY'
import re, pathlib
text = pathlib.Path("/tmp/lookbook-bench-logcat.txt").read_text(errors="replace")
lines = []
for line in text.splitlines():
    m = re.search(r"LookbookBench\s*:\s*(.*)$", line)
    if m:
        lines.append(m.group(1))
body = "\n".join(lines)
start = body.find("{")
end = body.rfind("}")
if start < 0 or end < 0:
    raise SystemExit("Could not find JSON in LookbookBench logcat")
pathlib.Path("/tmp/lookbook-on-device-ort.json").write_text(body[start:end+1])
print("Rebuilt JSON from logcat")
PY
fi

python3 - <<'PY'
import json, pathlib, datetime
from pathlib import Path
root = Path("/workspace") if Path("/workspace").exists() else Path(".")
raw = Path("/tmp/lookbook-on-device-ort.json").read_text()
data = json.loads(raw)
out_json = root / "docs" / "benchmarks" / "on-device-ort-latest.json"
out_json.parent.mkdir(parents=True, exist_ok=True)
out_json.write_text(json.dumps(data, indent=2) + "\n")

device = data.get("device", {})
model = f"{device.get('manufacturer','?')} {device.get('model','?')}".strip()
sdk = device.get("sdkInt")
captured = data.get("capturedAt", datetime.datetime.utcnow().strftime("%Y-%m-%d"))
eps = data.get("executionProviders", {})
available = ", ".join(eps.get("available") or []) or "(none)"
intent = eps.get("productionProIntent", "")

def fmt_row(item):
    status = item.get("status", "?")
    iid = item.get("id", "?")
    if status != "OK":
        return f"| `{iid}` | {status} | — | — | {item.get('note') or item.get('error','')[:80]} |"
    cold = item.get("coldLoadMs", item.get("firstOpenMs", "—"))
    warm = item.get("warmAvgMs")
    if warm is None and "warmRunMs" in item:
        w = item["warmRunMs"]
        warm = w if isinstance(w, (int, float)) else (sum(w)/len(w) if w else "—")
    if isinstance(warm, float):
        warm = round(warm, 1)
    return f"| `{iid}` | OK | {cold} | {warm} | `{item.get('sessionFactory','')}` / {item.get('epIntent','')[:40]} |"

md = []
md.append("# Lookbook on-device ORT benchmarks")
md.append("")
md.append(f"**Captured:** {captured}  ")
md.append(f"**Device:** {model} (`{device.get('device')}`, SDK {sdk}, product `{device.get('product')}`)  ")
md.append(f"**Plan:** generation-transparency A0  ")
md.append(f"**Raw JSON:** [`docs/benchmarks/on-device-ort-latest.json`](benchmarks/on-device-ort-latest.json)")
md.append("")
md.append("## Execution providers")
md.append("")
md.append(f"- **Available (ORT binary):** {available}")
md.append(f"- **Production Pro session intent:** `{intent}`")
md.append("- **Registration probe:**")
md.append("")
md.append("| EP | Registered | Error |")
md.append("|----|------------|-------|")
for a in eps.get("registrationProbe") or []:
    err = a.get("error") or ""
    md.append(f"| {a.get('name')} | {a.get('registered')} | {err} |")
md.append("")
md.append("## Lite (bundled `lite-v1`)")
md.append("")
md.append("| Graph | Status | Cold load (ms) | Warm run avg (ms) | Factory / EP |")
md.append("|-------|--------|----------------|-------------------|--------------|")
for item in data.get("lite") or []:
    md.append(fmt_row(item))
md.append("")
md.append("## Pro (`pro-v1`) — session open via `OrtGraph`/`ProOrtSessions`")
md.append("")
md.append("| Graph | Status | Cold load (ms) | Warm run avg (ms) | Factory / EP |")
md.append("|-------|--------|----------------|-------------------|--------------|")
for item in data.get("pro") or []:
    md.append(fmt_row(item))
md.append("")
md.append("## Local image (`local-sdturbo-v1`)")
md.append("")
md.append("| Graph | Status | Cold load (ms) | Warm run avg (ms) | Factory / EP |")
md.append("|-------|--------|----------------|-------------------|--------------|")
for item in data.get("localImage") or []:
    md.append(fmt_row(item))
md.append("")
md.append("## Local audio / video")
md.append("")
audio = data.get("localAudio") or {}
video = data.get("localVideo") or {}
md.append(f"- **System TTS init:** {audio.get('status')} in {audio.get('initMs')} ms")
md.append(f"- **Video still-clip:** {video.get('status')} — {video.get('note','')}")
md.append("")
md.append("## Notes")
md.append("")
md.append("- Production Pro path uses **CPU + `NO_OPT`**; QNN is never enabled (FP16 rewrite hazard).")
md.append("- NNAPI is opt-in via `OrtEpPolicy.preferNnapi` (default false after Pixel 9 SIGSEGV).")
md.append("- Warm-run for Pro graphs is load-only in this harness when shapes are multi-input;")
md.append("  end-to-end Pro try-on soak remains a device UI matrix item.")
md.append("- Re-run: `bash scripts/benchmark-on-device.sh`")
md.append("")

Path(root / "docs" / "BENCHMARKS.md").write_text("\n".join(md) + "\n")
print("Wrote", out_json)
print("Wrote", root / "docs" / "BENCHMARKS.md")
print("--- summary ---")
print("device:", model, "sdk", sdk)
print("EPs:", available)
for section in ("lite", "pro", "localImage"):
    for item in data.get(section) or []:
        print(f"  {item.get('id')}: {item.get('status')} cold={item.get('coldLoadMs', item.get('firstOpenMs'))} warm={item.get('warmAvgMs', item.get('warmRunMs'))}")
PY

echo "=== Done ==="
