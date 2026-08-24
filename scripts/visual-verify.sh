#!/usr/bin/env bash
# Capture visual-verify screenshots (device) or list expected routes (CI dry-run).
# Usage:
#   scripts/visual-verify.sh [--list-routes|--dry-run]
#   scripts/visual-verify.sh [serial] [outdir] [--compare]
set -euo pipefail

ROUTES=(
  "studio/tryon:01-atelier-tryon"
  "studio/image:02-image-studio"
  "studio/video:03-video-studio"
  "studio/code:04-code-studio"
  "studio/news:05-news-chat"
  "wardrobe:06-looks-gallery"
  "help:07-help-faq"
  "settings:08-settings"
  "usage:09-cloud-usage"
  "garment:10-garment-capture"
  "settings:11-settings-about"
)

if [[ "${1:-}" == "--list-routes" || "${1:-}" == "--dry-run" ]]; then
  echo "visual-verify routes (lookbook://screen/<route> → filename):"
  for pair in "${ROUTES[@]}"; do
    echo "  ${pair%%:*} → ${pair##*:}.png"
  done
  echo "Baseline dir: docs/screenshots/baseline/"
  echo "Dry-run OK (no adb)."
  exit 0
fi

SERIAL="${1:-emulator-5554}"
OUT="${2:-/opt/cursor/artifacts/screenshots/visual-verify}"
COMPARE=0
for arg in "$@"; do
  [[ "$arg" == "--compare" ]] && COMPARE=1
done
PKG=com.zakir.vestra
POST_NAV_SLEEP="${POST_NAV_SLEEP:-3.5}"
BASELINE_DIR="${BASELINE_DIR:-docs/screenshots/baseline}"
mkdir -p "$OUT"

shot() {
  local name="$1"
  sleep "$POST_NAV_SLEEP"
  adb -s "$SERIAL" exec-out screencap -p > "$OUT/${name}.png"
  echo "✓ $name ($(wc -c < "$OUT/${name}.png") bytes)"
}

open_route() {
  local route="$1"
  adb -s "$SERIAL" shell am start -a android.intent.action.VIEW \
    -d "lookbook://screen/${route}" -n "$PKG/.MainActivity" >/dev/null || true
}

adb -s "$SERIAL" shell settings put global window_animation_scale 0
adb -s "$SERIAL" shell settings put global transition_animation_scale 0
adb -s "$SERIAL" shell settings put global animator_duration_scale 0

adb -s "$SERIAL" shell am force-stop "$PKG"
open_route "studio/tryon"
for i in $(seq 1 40); do
  adb -s "$SERIAL" exec-out screencap -p > "$OUT/_probe.png"
  bytes=$(wc -c < "$OUT/_probe.png")
  if [ "$bytes" -gt 200000 ]; then
    echo "warm after probe $i ($bytes bytes)"
    break
  fi
  sleep 2
done
cp "$OUT/_probe.png" "$OUT/01-atelier-tryon.png"
echo "✓ 01-atelier-tryon ($(wc -c < "$OUT/01-atelier-tryon.png") bytes)"

for pair in \
  "studio/image:02-image-studio" \
  "studio/video:03-video-studio" \
  "studio/code:04-code-studio" \
  "studio/news:05-news-chat" \
  "wardrobe:06-looks-gallery" \
  "help:07-help-faq" \
  "settings:08-settings" \
  "usage:09-cloud-usage" \
  "garment:10-garment-capture"
do
  open_route "${pair%%:*}"
  shot "${pair##*:}"
done

open_route settings
sleep 2
for _ in 1 2 3 4; do
  adb -s "$SERIAL" shell input swipe 540 2000 540 250 350
  sleep 0.35
done
sleep 1
adb -s "$SERIAL" exec-out screencap -p > "$OUT/11-settings-about.png"
echo "✓ 11-settings-about ($(wc -c < "$OUT/11-settings-about.png") bytes)"

rm -f "$OUT/_probe.png"

if [[ "$COMPARE" -eq 1 ]]; then
  echo "Comparing against $BASELINE_DIR …"
  python3 "$(dirname "$0")/compare-screenshots.py" "$BASELINE_DIR" "$OUT" || {
    echo "visual regression detected" >&2
    exit 2
  }
fi

echo "Artifacts in $OUT"
ls -lh "$OUT"
