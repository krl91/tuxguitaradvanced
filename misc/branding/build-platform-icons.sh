#!/bin/bash
# Builds the platform icons and images from the APPROVED derivatives only and writes them over the files declared in the manifest (spec 008, T017-T021).
# Refuses a derivative whose status is not "approved" in specs/008-brand-identity/branding-manifest.json. Needs: make-derivatives.sh already run (WORK).
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
WORK="${WORK:-/tmp/branding-work}"
MANIFEST="$ROOT/specs/008-brand-identity/branding-manifest.json"
python3 - "$MANIFEST" <<'PY' || exit 1
import json,sys
m=json.load(open(sys.argv[1]))
bad=[d["id"] for d in m["derivatives"] if d["status"]!="approved"]
if bad: print("derivatives not approved by the owner: "+", ".join(bad)); sys.exit(1)
PY
"$HERE/make-derivatives.sh" >/dev/null
CR="$ROOT/desktop/build-scripts/common-resources"
rgba() { magick "$1" -background none -alpha on PNG32:"$2"; }

# the source of a size: D2 (icon-only) below 128 px, D1 (full logo) from 128 px (the manifest rule)
src() { if [ "$1" -lt 128 ]; then echo "$WORK/D2-$1.png"; elif [ "$1" -le 256 ] && [ "$1" -ge 128 ]; then echo "$WORK/D1-$1.png"; else echo "$WORK/D1-$1.png"; fi; }

# S1 icon.icns (16, 32, 64, 128, 256, 512, 1024 with the @2x entries)
SET="$WORK/icon.iconset"; mkdir -p "$SET"
make_entry() { rgba "$(src "$2")" "$SET/$1"; }
make_entry icon_16x16.png 16;      make_entry icon_16x16@2x.png 32
make_entry icon_32x32.png 32;      make_entry icon_32x32@2x.png 64
make_entry icon_128x128.png 128;   make_entry icon_128x128@2x.png 256
make_entry icon_256x256.png 256;   make_entry icon_256x256@2x.png 512
make_entry icon_512x512.png 512;   make_entry icon_512x512@2x.png 1024
iconutil -c icns "$SET" -o "$CR/common-macosx/Contents/Resources/icon.icns"

# S2, S3, S4 ICO: 16, 24, 32, 48, 64, 96, 128, 256
ICO_ARGS=(); for n in 16 24 32 48 64 96 128 256; do ICO_ARGS+=("$(src "$n")"); done
for f in "$CR/common-windows/dist/tuxguitar.ico" "$CR/common-windows-installer-icons/TG_icon_install.ico" "$CR/common-windows-installer-icons/TG_icon_uninstall.ico"; do
  magick "${ICO_ARGS[@]}" -background none "$f"
done

# S5 48x48, S6 panel, S7 splash (Windows installer), S8 Linux pixmap
rgba "$WORK/D2-48.png" "$CR/common-windows-installer-icons/TG_logo_48x48.png"
rgba "$WORK/D4-dark.png" "$CR/common-windows-installer-icons/TG_logo_163x314.png"
rgba "$WORK/D3-dark.png" "$CR/common-windows-installer-icons/TG_splash_500x300.png"
rgba "$WORK/D2-96.png" "$CR/common-linux/share/pixmaps/tuxguitar.png"

# S9 icon.png and S10 splash.png of every skin, byte-equal, in share/skins and src-skins
for dir in $(git -C "$ROOT" ls-files desktop/TuxGuitar/share/skins desktop/TuxGuitar/src-skins | grep -E '/(icon|splash)\.png$' | xargs -n1 dirname | sort -u); do
  rgba "$WORK/D2-96.png" "$ROOT/$dir/icon.png"
  rgba "$WORK/D3-dark.png" "$ROOT/$dir/splash.png"
done
echo "platform icons and images written from the approved derivatives"
