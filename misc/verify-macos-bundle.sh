#!/bin/bash
# Verifies that a macOS .app is a bundle macOS accepts to open after "Open Anyway" instead of calling it damaged:
#   1. Contents/MacOS holds only files (no folder: macOS treats everything there as code to seal, so the JRE, the jars and the resources live in Contents/Resources);
#   2. the code signature of the whole bundle is valid (codesign --verify --deep --strict).
# usage: misc/verify-macos-bundle.sh <package>      (package: a .app folder or a .zip)
set -uo pipefail
PKG="${1:?package (.app or .zip)}"
WORK="$(mktemp -d /tmp/verify-macos-bundle.XXXXXX)"; trap 'chmod -R u+w "$WORK" 2>/dev/null; rm -rf "$WORK"' EXIT
case "$PKG" in
  *.zip) ditto -x -k "$PKG" "$WORK/x" 2>/dev/null || { echo "cannot open $PKG" >&2; exit 2; }; APP="$(find "$WORK/x" -maxdepth 2 -name '*.app' -type d | head -1)";;
  *) APP="$PKG";;
esac
[ -d "${APP:-}" ] || { echo "no .app in $PKG" >&2; exit 2; }
fail=0
DIRS="$(find "$APP/Contents/MacOS" -mindepth 1 -type d 2>/dev/null | head -3)"
if [ -n "$DIRS" ]; then echo "WRONG   Contents/MacOS holds folders (it must hold only the executable): $(echo "$DIRS" | head -1 | sed "s|.*Contents/|Contents/|")"; fail=1; else echo "OK      Contents/MacOS holds only files"; fi
OUT="$(codesign --verify --deep --strict "$APP" 2>&1)"; code=$?
if [ "$code" = 0 ]; then echo "OK      the code signature of the bundle is valid"; else echo "WRONG   the code signature of the bundle is not valid: $(echo "$OUT" | head -1 | sed "s|$APP|the .app|")"; fail=1; fi
[ "$fail" = 0 ] && echo "bundle verified: $(basename "$APP")"
exit $fail
