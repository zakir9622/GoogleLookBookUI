#!/usr/bin/env bash
# Verify debug Lite pack assets exist and ONNX Runtime can load them (CI gate).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PACK="$ROOT/composeApp/src/debug/assets/packs/lite-v1"
required=(garment_seg.onnx human_parse.onnx)
missing=0
for f in "${required[@]}"; do
  if [[ ! -f "$PACK/$f" ]]; then
    echo "MISSING: $PACK/$f"
    missing=$((missing + 1))
  fi
done
if [[ $missing -gt 0 ]]; then
  echo "Run: python3 ml/export_lite_pack.py (or copy from CI cache)"
  exit 1
fi
echo "OK: lite-v1 debug assets present ($(du -sh "$PACK" | cut -f1))"

python3 - <<'PY' "$PACK/garment_seg.onnx" "$PACK/human_parse.onnx"
import sys
try:
    import onnxruntime as ort
except ImportError:
    print("SKIP: onnxruntime not installed — file presence check only")
    sys.exit(0)

for path in sys.argv[1:]:
    sess = ort.InferenceSession(path, providers=["CPUExecutionProvider"])
    name = sess.get_inputs()[0].name
    print(f"OK: {path.split('/')[-1]} loads ({name})")
PY

exit 0
