#!/usr/bin/env bash
# Matrix report for every catalog model (generation-stability M5).
# Local packs: offline smoke via integration-local-models.py when available.
# Cloud models: probe-models.py --json (quota = WARN).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${1:-/tmp/lookbook-verify-all-models.json}"
mkdir -p "$(dirname "$OUT")"

echo "== Local catalog runnable matrix =="
CATALOG_JSON="/tmp/lookbook-catalog-matrix.json"
python3 "$ROOT/scripts/catalog-matrix.py" "$CATALOG_JSON"

echo "== Local model smoke =="
if [[ -f "$ROOT/scripts/integration-local-models.py" ]]; then
  python3 "$ROOT/scripts/integration-local-models.py" --skip-hf-download 2>&1 | tee /tmp/lookbook-local-matrix.txt || true
else
  echo "WARN: integration-local-models.py missing"
fi

echo "== Cloud probe matrix =="
PROBE_JSON="/tmp/lookbook-probe-matrix.json"
if [[ -f "$ROOT/scripts/probe-models.py" ]]; then
  python3 "$ROOT/scripts/probe-models.py" --quick --json "$PROBE_JSON" 2>&1 | tee /tmp/lookbook-cloud-matrix.txt || true
else
  echo "WARN: probe-models.py missing"
  echo '{}' > "$PROBE_JSON"
fi

python3 - <<PY
import json, time
from pathlib import Path
probe = {}
p = Path("$PROBE_JSON")
if p.exists():
    try:
        probe = json.loads(p.read_text())
    except Exception as e:
        probe = {"error": str(e)}
catalog = {}
c = Path("$CATALOG_JSON")
if c.exists():
    try:
        catalog = json.loads(c.read_text())
    except Exception as e:
        catalog = {"error": str(e)}
report = {
    "generatedAt": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
    "localCatalog": catalog,
    "localLog": "/tmp/lookbook-local-matrix.txt",
    "cloudProbe": probe,
    "note": "Quota exhausted / ZeroGPU empty = WARN, not FAIL. Catalog runnable=false = NOT_PUBLISHED.",
}
Path("$OUT").write_text(json.dumps(report, indent=2))
print("Wrote $OUT")
print(
    "Catalog:",
    catalog.get("runnableCount", "?"),
    "runnable /",
    catalog.get("notPublishedCount", "?"),
    "not published",
)
PY
