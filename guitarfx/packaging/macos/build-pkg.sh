#!/bin/bash
# GuitarFX's macOS installer (spec 027, T059, research R9, FR-026): one .pkg, made with Apple's pkgbuild and productbuild, that places
#   /Applications/GuitarFX.app (with guitarfx-uninstall in its Resources)
#   /Library/Audio/Plug-Ins/VST3/GuitarFX.vst3, /Library/Audio/Plug-Ins/CLAP/GuitarFX.clap, /Library/Audio/Plug-Ins/Components/GuitarFX.component
# The settings folder (~/Library/Application Support/GuitarFX) is never in the payload, so an install over an older version keeps it.
# Usage: build-pkg.sh <GuitarFX.app> <folder with the three plugin bundles> <version X.Y.Z> <output .pkg>
# The bundles are signed before (guitarfx/pom.xml, or the release job); the .pkg itself is not signed (as TuxGuitarAdvanced's zip).
set -euo pipefail
if [ $# -ne 4 ]; then echo "usage: $0 <GuitarFX.app> <plugins folder> <version> <output.pkg>" >&2; exit 2; fi
APP="$1"; PLUGINS="$2"; VERSION="$3"; OUT="$4"
HERE="$(cd "$(dirname "$0")" && pwd)"
IDENTIFIER=app.tuxguitar.guitarfx.pkg
for bundle in "$APP" "$PLUGINS/GuitarFX.vst3" "$PLUGINS/GuitarFX.clap" "$PLUGINS/GuitarFX.component"; do
	[ -d "$bundle" ] || { echo "missing: $bundle" >&2; exit 1; }
done
WORK="$(mktemp -d "${TMPDIR:-/tmp}/guitarfx-pkg.XXXXXX")"; trap 'rm -rf "$WORK"' EXIT
ROOT="$WORK/root"
mkdir -p "$ROOT/Applications" "$ROOT/Library/Audio/Plug-Ins/VST3" "$ROOT/Library/Audio/Plug-Ins/CLAP" "$ROOT/Library/Audio/Plug-Ins/Components"
ditto --norsrc --noextattr --noqtn "$APP" "$ROOT/Applications/GuitarFX.app"
ditto --norsrc --noextattr --noqtn "$PLUGINS/GuitarFX.vst3" "$ROOT/Library/Audio/Plug-Ins/VST3/GuitarFX.vst3"
ditto --norsrc --noextattr --noqtn "$PLUGINS/GuitarFX.clap" "$ROOT/Library/Audio/Plug-Ins/CLAP/GuitarFX.clap"
ditto --norsrc --noextattr --noqtn "$PLUGINS/GuitarFX.component" "$ROOT/Library/Audio/Plug-Ins/Components/GuitarFX.component"
# the uninstaller rides in the application's Resources, which the signature covers: the application is signed again after (ad hoc keeps
# an ad-hoc bundle valid; the release job signs everything again with its certificate before calling this script with SIGN_IDENTITY set)
install -m 755 "$HERE/guitarfx-uninstall" "$ROOT/Applications/GuitarFX.app/Contents/Resources/guitarfx-uninstall"
# SIGN_REQUIREMENT, when given, is the designated requirement the release forces (the stable certificate as root, as TuxGuitarAdvanced's)
SIGN_ARGS=(--force --sign "${SIGN_IDENTITY:--}" --identifier app.tuxguitar.guitarfx)
[ -n "${SIGN_REQUIREMENT:-}" ] && SIGN_ARGS+=(-r="$SIGN_REQUIREMENT")
codesign "${SIGN_ARGS[@]}" "$ROOT/Applications/GuitarFX.app"
# no extended attribute the bundles do not need (the system's own, such as com.apple.provenance, cannot be removed and are harmless)
xattr -cr "$ROOT" 2>/dev/null || true
codesign --verify --deep --strict "$ROOT/Applications/GuitarFX.app"
# the bundles always go where they are listed: never moved to a copy found elsewhere on the disk
pkgbuild --analyze --root "$ROOT" "$WORK/components.plist" >/dev/null
i=0
while /usr/libexec/PlistBuddy -c "Print :$i" "$WORK/components.plist" >/dev/null 2>&1; do
	/usr/libexec/PlistBuddy -c "Delete :$i:BundleIsRelocatable" "$WORK/components.plist" >/dev/null 2>&1 || true
	/usr/libexec/PlistBuddy -c "Add :$i:BundleIsRelocatable bool false" "$WORK/components.plist"
	i=$((i + 1))
done
pkgbuild --root "$ROOT" --component-plist "$WORK/components.plist" --identifier "$IDENTIFIER" --version "$VERSION" \
	--install-location / "$WORK/GuitarFX.pkg" >/dev/null
productbuild --package "$WORK/GuitarFX.pkg" "$OUT" >/dev/null
echo "$OUT"
