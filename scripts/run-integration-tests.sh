#!/usr/bin/env bash
# Full integration gate: local ONNX packs, HF manifest, unit tests, cloud probes.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

REPORT="${INTEGRATION_REPORT:-/tmp/lookbook-integration-report.txt}"
: > "$REPORT"

log() { echo "$@" | tee -a "$REPORT"; }

log "=== Lookbook integration tests $(date -u +%Y-%m-%dT%H:%M:%SZ) ==="
log ""

FAILED=0

run_step() {
  local name="$1"
  shift
  log "--- $name ---"
  if "$@" >> "$REPORT" 2>&1; then
    log "PASS: $name"
  else
    log "FAIL: $name (exit $?)"
    FAILED=$((FAILED + 1))
  fi
  log ""
}

run_step "Gradle unit tests" ./gradlew :shared:testDebugUnitTest :composeApp:testSideloadDebugUnitTest --quiet
run_step "Lite pack assets (verify-lite-pack.sh)" bash scripts/verify-lite-pack.sh
run_step "HF manifest integrity" python3 scripts/verify-manifest.py
run_step "Local ONNX integration (bundled + HF download)" python3 scripts/integration-local-models.py
run_step "Edge-case integration (truncation, input geometry, pro weights)" python3 scripts/integration-edge-cases.py
run_step "Cloud probe (quick)" python3 scripts/probe-models.py --quick --json /tmp/lookbook-probe-quick.json
run_step "Cloud probe (full spaces)" python3 scripts/probe-models.py --json /tmp/lookbook-probe-full.json

run_step "Local ONNX benchmark" python3 scripts/benchmark-local.py
run_step "Cloud benchmark (quick)" python3 scripts/benchmark-cloud.py --quick

if command -v adb >/dev/null 2>&1 && adb devices 2>/dev/null | rg -q 'device$'; then
  run_step "Connected Android instrumentation" ./gradlew :composeApp:connectedSideloadDebugAndroidTest --quiet
else
  log "SKIP: Connected Android instrumentation (no adb device)"
  log ""
fi

log "=== Summary ==="
if [[ "$FAILED" -eq 0 ]]; then
  log "ALL REQUIRED INTEGRATION STEPS PASSED"
  log "Report: $REPORT"
  exit 0
else
  log "$FAILED step(s) FAILED — see $REPORT"
  exit 1
fi
