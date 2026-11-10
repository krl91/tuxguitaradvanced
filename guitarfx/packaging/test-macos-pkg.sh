#!/bin/bash
# Self-test of GuitarFX's macOS installer (spec 027, T059, FR-026): the .pkg places the application with its uninstaller and the three
# plugins in the system's folders, never the settings folder, and does not move a bundle to a copy found elsewhere; the uninstaller removes
# them and keeps the settings unless asked. Needs pkgbuild (macOS): skipped elsewhere. Nothing is installed on this machine.
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
if ! command -v pkgbuild >/dev/null; then echo "SKIPPED: pkgbuild is not available on this machine"; exit 0; fi
TMP="$(mktemp -d "${TMPDIR:-/tmp}/test-guitarfx-pkg.XXXXXX")"; trap 'rm -rf "$TMP"' EXIT
fail=0
ok() { if eval "$2"; then echo "ok: $1"; else echo "FAILED: $1"; fail=1; fi; }
bundle() { # a small bundle with an executable, signed ad hoc
	mkdir -p "$1/Contents/MacOS" "$1/Contents/Resources"
	cp /usr/bin/true "$1/Contents/MacOS/$2"
	printf '<?xml version="1.0" encoding="UTF-8"?>\n<plist version="1.0"><dict><key>CFBundleExecutable</key><string>%s</string><key>CFBundleIdentifier</key><string>%s</string><key>CFBundlePackageType</key><string>%s</string></dict></plist>\n' "$2" "$3" "$4" > "$1/Contents/Info.plist"
	codesign --force --sign - "$1" 2>/dev/null
}
bundle "$TMP/in/GuitarFX.app" guitarfx app.tuxguitar.guitarfx APPL
for f in clap vst3 component; do bundle "$TMP/in/plugins/GuitarFX.$f" GuitarFX "app.tuxguitar.guitarfx.$f" BNDL; done

"$HERE/macos/build-pkg.sh" "$TMP/in/GuitarFX.app" "$TMP/in/plugins" 1.2.3 "$TMP/GuitarFX.pkg" >/dev/null 2>"$TMP/err"; code=$?
ok "the package is built" "[ $code -eq 0 ] && [ -f '$TMP/GuitarFX.pkg' ]"
[ $code -eq 0 ] || cat "$TMP/err"
pkgutil --expand-full "$TMP/GuitarFX.pkg" "$TMP/expanded" >/dev/null 2>&1
PAYLOAD="$(find "$TMP/expanded" -type d -name Payload | head -1)"
for path in Applications/GuitarFX.app/Contents/MacOS/guitarfx Applications/GuitarFX.app/Contents/Resources/guitarfx-uninstall \
	Library/Audio/Plug-Ins/VST3/GuitarFX.vst3/Contents/MacOS/GuitarFX Library/Audio/Plug-Ins/CLAP/GuitarFX.clap/Contents/MacOS/GuitarFX \
	Library/Audio/Plug-Ins/Components/GuitarFX.component/Contents/MacOS/GuitarFX; do
	ok "the payload has $path" "[ -e '$PAYLOAD/$path' ]"
done
ok "the uninstaller can be run" "[ -x '$PAYLOAD/Applications/GuitarFX.app/Contents/Resources/guitarfx-uninstall' ]"
ok "the application's signature covers the uninstaller" "codesign --verify --deep --strict '$PAYLOAD/Applications/GuitarFX.app' 2>/dev/null"
ok "no settings folder in the payload" "[ -d '$PAYLOAD' ] && ! find '$PAYLOAD' -path '*Application Support*' | grep -q ."
# an extended attribute on a source file is carried as an attribute, never extracted as a "._" file on the guitarist's disk
xattr -w com.apple.test guitarfx "$TMP/in/plugins/GuitarFX.clap/Contents/MacOS/GuitarFX" 2>/dev/null
"$HERE/macos/build-pkg.sh" "$TMP/in/GuitarFX.app" "$TMP/in/plugins" 1.2.3 "$TMP/attributes.pkg" >/dev/null 2>&1
pkgutil --expand-full "$TMP/attributes.pkg" "$TMP/attributes" >/dev/null 2>&1
ok "no AppleDouble file once extracted" "[ -d '$TMP/attributes' ] && ! find '$TMP/attributes' -name '._*' | grep -q ."
INFO="$(find "$TMP/expanded" -name PackageInfo | head -1)"
ok "the version is the one given" "grep -q 'version=\"1.2.3\"' '$INFO'"
ok "the identifier is GuitarFX's" "grep -q 'identifier=\"app.tuxguitar.guitarfx.pkg\"' '$INFO'"
ok "no bundle is relocated to another copy" "[ -f '$INFO' ] && ! grep -q '<relocate>' '$INFO'"

# the uninstaller, in a folder of its own
R="$TMP/root"; S="$R/home/Library/Application Support/GuitarFX"
install_fake() {
	mkdir -p "$R/Applications/GuitarFX.app" "$R/Library/Audio/Plug-Ins/VST3/GuitarFX.vst3" "$R/Library/Audio/Plug-Ins/CLAP/GuitarFX.clap" \
		"$R/Library/Audio/Plug-Ins/Components/GuitarFX.component" "$R/Library/Audio/Plug-Ins/VST3/Other.vst3" "$S"
	echo "sound.0.name=Mine" > "$S/sounds.properties"
}
install_fake
GUITARFX_UNINSTALL_ROOT="$R" "$HERE/macos/guitarfx-uninstall" >/dev/null; code=$?
ok "the uninstaller ends well" "[ $code -eq 0 ]"
ok "the application is removed" "[ ! -e '$R/Applications/GuitarFX.app' ]"
ok "the plugins are removed" "[ ! -e '$R/Library/Audio/Plug-Ins/VST3/GuitarFX.vst3' ] && [ ! -e '$R/Library/Audio/Plug-Ins/CLAP/GuitarFX.clap' ] && [ ! -e '$R/Library/Audio/Plug-Ins/Components/GuitarFX.component' ]"
ok "another plugin is kept" "[ -d '$R/Library/Audio/Plug-Ins/VST3/Other.vst3' ]"
ok "the sounds and settings are kept" "[ -f '$S/sounds.properties' ]"
install_fake
GUITARFX_UNINSTALL_ROOT="$R" "$HERE/macos/guitarfx-uninstall" --remove-settings >/dev/null
ok "--remove-settings removes them" "[ ! -e '$S' ]"
GUITARFX_UNINSTALL_ROOT="$R" "$HERE/macos/guitarfx-uninstall" --what >/dev/null 2>&1; code=$?
ok "an unknown option is refused" "[ $code -eq 2 ]"
GUITARFX_UNINSTALL_ROOT="$R" "$HERE/macos/guitarfx-uninstall" >/dev/null; code=$?
ok "a second run finds nothing and ends well" "[ $code -eq 0 ]"
exit $fail
