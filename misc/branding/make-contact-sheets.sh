#!/bin/bash
# Contact sheets of the derivatives at 16, 32, 64, 128, 256, 512 and 1024 px on a light and a dark background (spec 008, FR-009, SC-004).
set -euo pipefail
WORK="${WORK:-/tmp/branding-work}"
OUT="${OUT:-/tmp/branding-review}"
rm -rf "$OUT"; mkdir -p "$OUT"
LIGHT='#FFFFFF'; DARK='#1E1E1E'
SIZES="16 32 64 128 256 512 1024"
source_for() { # derivative size -> file or empty
  case "$1" in
    D1) [ -f "$WORK/D1-$2.png" ] && echo "$WORK/D1-$2.png" || { [ "$2" -lt 128 ] || echo ""; } ;;
    D2) [ -f "$WORK/D2-$2.png" ] && echo "$WORK/D2-$2.png" || echo "" ;;
  esac
}
sheet() { # derivative background name
  local d="$1" bg="$2" name="$3" args=() skipped=""
  for s in $SIZES; do
    f="$(source_for "$d" "$s")"
    if [ -n "$f" ]; then
      magick "$f" -background "$bg" -alpha remove -alpha off -gravity center -extent "$(( s + 16 ))x$(( s + 16 ))" "$OUT/.cell-$d-$s.png"
      args+=("$OUT/.cell-$d-$s.png")
    else
      skipped="$skipped $s"
    fi
  done
  # sizes are shown at their true pixel size on one row, bottom-aligned
  magick "${args[@]}" -background "$bg" -gravity south +append "$OUT/$name.png"
  rm -f "$OUT"/.cell-*.png
  [ -n "$skipped" ] && echo "$d: no $skipped px (this derivative does not exist at those sizes)"
  return 0
}
for d in D1 D2; do
  if ls "$WORK"/$d-*.png >/dev/null 2>&1; then
    sheet $d "$LIGHT" "$d-light"; sheet $d "$DARK" "$d-dark"
  fi
done
# D3 and D4 are single images: shown as they are, with their own background
for f in "$WORK"/D3-*.png "$WORK"/D4-*.png; do [ -f "$f" ] && cp "$f" "$OUT/$(basename "$f")"; done
echo "contact sheets in $OUT:"; ls "$OUT"
