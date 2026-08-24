#!/usr/bin/env bash
# Device E2E matrix checklist (generation-stability D2 / follow-up D2).
# Runs deep-link navigation + optional generation smoke when adb device present.
set -euo pipefail
SERIAL="${1:-}"
if [[ -z "$SERIAL" ]]; then
  SERIAL=$(adb devices | awk 'NR>1 && $2=="device" {print $1; exit}')
fi
if [[ -z "$SERIAL" ]]; then
  echo "WARN: no adb device — printing matrix only"
  cat <<'EOF'
E2E matrix:
  [ ] studio/tryon hero
  [ ] studio/image generate (or offline fail <5s)
  [ ] studio/video composer
  [ ] studio/code composer
  [ ] studio/news chat
  [ ] settings hub subsections
  [ ] diagnostics export share sheet
  [ ] pack download permission rationale
EOF
  exit 0
fi

OUT="${2:-/tmp/lookbook-e2e}"
bash "$(dirname "$0")/visual-verify.sh" "$SERIAL" "$OUT"
echo "E2E screenshots in $OUT — review manually for functional pass"
