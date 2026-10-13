#!/bin/bash
# Makes the derivatives D1 to D4 of the official logo from the master only (spec 008). Output: $WORK (default /tmp/branding-work).
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
MASTER="$ROOT/specs/008-brand-identity/assets/tuxguitaradvanced-logo-primary.png"
WORK="${WORK:-/tmp/branding-work}"
. "$HERE/crop.conf"
. "$HERE/background.conf"
EXPECTED=a9d369a2574396cd3208b0665648c57f398690499efb137d6781478991baf9e0
[ "$(shasum -a 256 "$MASTER" | cut -d' ' -f1)" = "$EXPECTED" ] || { echo "the master is not the official logo (SHA-256 differs)" >&2; exit 1; }
rm -rf "$WORK"; mkdir -p "$WORK"
RS="-filter Lanczos -define filter:blur=1 -colorspace sRGB"

# D1 full logo: uniform downscale, alpha kept
for n in 128 256 512 1024; do
  magick "$MASTER" -filter Lanczos -resize "${n}x${n}" -background none "$WORK/D1-$n.png"
done

# D2 icon-only: a rectangular crop with the fixed coordinates of crop.conf, centred on a transparent square (no stretching), then a uniform downscale
magick "$MASTER" -crop "${CROP_W}x${CROP_H}+${CROP_X}+${CROP_Y}" +repage -background none -gravity center -extent "$(( CROP_W > CROP_H ? CROP_W : CROP_H ))x$(( CROP_W > CROP_H ? CROP_W : CROP_H ))" "$WORK/D2-full.png"
for n in 16 24 32 48 64 96 128 256; do
  magick "$WORK/D2-full.png" -filter Lanczos -resize "${n}x${n}" -background none "$WORK/D2-$n.png"
done

# D3 splash 500 x 300 and D4 panel 163 x 314: the unmodified full logo scaled uniformly to fit inside the margins, centred, on a plain background
compose() { # name width height margin background
  local inner_w=$(( $2 - 2 * $4 )) inner_h=$(( $3 - 2 * $4 ))
  magick -size "$2x$3" "xc:$5" \( "$MASTER" -filter Lanczos -resize "${inner_w}x${inner_h}" \) -gravity center -composite "$WORK/$1.png"
}
compose D3-dark 500 300 12 "$BACKGROUND_DARK"
compose D3-light 500 300 12 "$BACKGROUND_LIGHT"
compose D4-dark 163 314 8 "$BACKGROUND_DARK"
compose D4-light 163 314 8 "$BACKGROUND_LIGHT"
echo "derivatives written in $WORK:"; ls "$WORK"
