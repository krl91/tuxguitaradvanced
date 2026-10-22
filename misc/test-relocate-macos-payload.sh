#!/bin/bash
# Self-test of misc/relocate-macos-payload.sh: everything but the launcher moves from Contents/MacOS to Contents/Resources/tuxguitar.
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TMP="$(mktemp -d /tmp/test-relocate.XXXXXX)"; trap 'rm -rf "$TMP"' EXIT
APP="$TMP/T.app"; fail=0
ok() { echo "ok: $1"; }; ko() { echo "FAILED: $1"; fail=1; }
mkdir -p "$APP/Contents/MacOS/jre/bin" "$APP/Contents/MacOS/lib" "$APP/Contents/MacOS/share/plugins" "$APP/Contents/MacOS/.hidden" "$APP/Contents/Resources"
echo x > "$APP/Contents/MacOS/tuxguitar-launcher"; chmod 755 "$APP/Contents/MacOS/tuxguitar-launcher"
echo '#!/bin/sh' > "$APP/Contents/MacOS/tuxguitar.sh"; chmod 755 "$APP/Contents/MacOS/tuxguitar.sh"
echo j > "$APP/Contents/MacOS/jre/bin/java"; chmod 755 "$APP/Contents/MacOS/jre/bin/java"
ln -s /bin/sh "$APP/Contents/MacOS/jre/sh-link"
echo a > "$APP/Contents/MacOS/lib/a.jar"; echo p > "$APP/Contents/MacOS/share/plugins/p.jar"; echo h > "$APP/Contents/MacOS/.hidden/h"
echo icon > "$APP/Contents/Resources/icon.icns"
"$ROOT/misc/relocate-macos-payload.sh" "$APP" >/dev/null 2>&1; code=$?
[ "$code" = 0 ] && ok "exit 0" || ko "exit $code"
[ "$(ls -A "$APP/Contents/MacOS")" = "tuxguitar-launcher" ] && ok "Contents/MacOS holds only the launcher" || ko "Contents/MacOS holds: $(ls -A "$APP/Contents/MacOS" | tr '\n' ' ')"
for f in tuxguitar.sh jre/bin/java lib/a.jar share/plugins/p.jar .hidden/h; do [ -e "$APP/Contents/Resources/tuxguitar/$f" ] && ok "moved: $f" || ko "missing after the move: $f"; done
[ -x "$APP/Contents/Resources/tuxguitar/tuxguitar.sh" ] && [ -x "$APP/Contents/Resources/tuxguitar/jre/bin/java" ] && ok "executable bits kept" || ko "executable bits lost"
[ -L "$APP/Contents/Resources/tuxguitar/jre/sh-link" ] && ok "a symbolic link stays a link" || ko "a symbolic link was lost"
[ -f "$APP/Contents/Resources/icon.icns" ] && ok "the other resources are untouched" || ko "the icon was touched"
[ -x "$APP/Contents/MacOS/tuxguitar-launcher" ] && ok "the launcher is untouched" || ko "the launcher was touched"
# a second run changes nothing and succeeds
"$ROOT/misc/relocate-macos-payload.sh" "$APP" >/dev/null 2>&1; code=$?
[ "$code" = 0 ] && [ -e "$APP/Contents/Resources/tuxguitar/lib/a.jar" ] && ok "a second run is harmless" || ko "a second run (exit $code)"
# refusals
"$ROOT/misc/relocate-macos-payload.sh" "$TMP/none.app" >/dev/null 2>&1; [ "$?" != 0 ] && ok "a missing app is refused" || ko "a missing app was accepted"
mkdir -p "$TMP/E.app/Contents/MacOS"; "$ROOT/misc/relocate-macos-payload.sh" "$TMP/E.app" >/dev/null 2>&1; [ "$?" != 0 ] && ok "an app without launcher is refused" || ko "an app without launcher was accepted"
exit $fail
