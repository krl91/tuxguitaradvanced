#!/bin/bash
# GuitarFX's Linux packages (spec 027, T059, research R9, FR-026), from the release build of guitarfx-app and guitarfx-plugin:
#   guitarfx_<debversion>_amd64.deb: /usr/bin/guitarfx, /usr/lib/vst3/GuitarFX.vst3, /usr/lib/clap/GuitarFX.clap,
#                                    /usr/share/applications/guitarfx.desktop (removed with apt remove; the settings are the user's)
#   GuitarFX-<label>-linux-x86_64.tar.gz: the folder GuitarFX/ with install.sh (for this user, ~/.local, ~/.vst3, ~/.clap)
# Usage: build-packages.sh <guitarfx binary> <libguitarfx_plugin.so> <Debian version> <file label> <output folder>
set -euo pipefail
if [ $# -ne 5 ]; then echo "usage: $0 <guitarfx> <libguitarfx_plugin.so> <debversion> <label> <output folder>" >&2; exit 2; fi
APP="$1"; PLUGIN="$2"; DEBVERSION="$3"; LABEL="$4"; OUT="$5"
HERE="$(cd "$(dirname "$0")" && pwd)"
for file in "$APP" "$PLUGIN"; do [ -f "$file" ] || { echo "missing: $file" >&2; exit 1; }; done
mkdir -p "$OUT"
WORK="$(mktemp -d "${TMPDIR:-/tmp}/guitarfx-linux.XXXXXX")"; trap 'rm -rf "$WORK"' EXIT

# the plugin bundles: a CLAP is the library itself; a VST3 is a folder with the library under its architecture
plugins() {
	mkdir -p "$1/GuitarFX.vst3/Contents/x86_64-linux"
	install -m 755 "$PLUGIN" "$1/GuitarFX.vst3/Contents/x86_64-linux/GuitarFX.so"
	install -m 755 "$PLUGIN" "$1/GuitarFX.clap"
}

# the .deb
DEB="$WORK/deb"
install -D -m 755 "$APP" "$DEB/usr/bin/guitarfx"
install -D -m 644 "$HERE/guitarfx.desktop" "$DEB/usr/share/applications/guitarfx.desktop"
mkdir -p "$DEB/usr/lib/vst3" "$DEB/usr/lib/clap"
plugins "$WORK/plugins"
cp -R "$WORK/plugins/GuitarFX.vst3" "$DEB/usr/lib/vst3/"
cp "$WORK/plugins/GuitarFX.clap" "$DEB/usr/lib/clap/"
mkdir -p "$DEB/DEBIAN"
SIZE=$(du -sk "$DEB/usr" | cut -f1)
sed -e "s/@VERSION@/$DEBVERSION/" -e "s/@SIZE@/$SIZE/" "$HERE/control" > "$DEB/DEBIAN/control"
dpkg-deb --root-owner-group --build "$DEB" "$OUT/guitarfx_${DEBVERSION}_amd64.deb" >/dev/null

# the tar.gz
TAR="$WORK/tar/GuitarFX"
install -D -m 755 "$APP" "$TAR/bin/guitarfx"
install -D -m 644 "$HERE/guitarfx.desktop" "$TAR/share/guitarfx.desktop"
install -D -m 755 "$HERE/install.sh" "$TAR/install.sh"
mkdir -p "$TAR/plugins"
cp -R "$WORK/plugins/." "$TAR/plugins/"
tar -C "$WORK/tar" -czf "$OUT/GuitarFX-${LABEL}-linux-x86_64.tar.gz" GuitarFX
ls "$OUT"/guitarfx_"${DEBVERSION}"_amd64.deb "$OUT"/GuitarFX-"${LABEL}"-linux-x86_64.tar.gz
