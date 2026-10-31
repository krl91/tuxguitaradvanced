#!/bin/bash
# Verifies that a macOS .app is a bundle macOS accepts to open after "Open Anyway" instead of calling it damaged, on the macOS it says it needs:
#   1. Contents/MacOS holds only files (no folder: macOS treats everything there as code to seal, so the JRE, the jars and the resources live in Contents/Resources);
#   2. the code signature of the whole bundle is valid (codesign --verify --deep --strict);
#   3. the Info.plist declares LSMinimumSystemVersion $MIN (spec 020: macOS refuses to open it on an older system, with its own message);
#   4. the parts the build makes, the launcher and each slice of the AudioUnit library, declare the minimum $MIN;
#   5. every other Mach-O file (the embedded JRE declares 11.0) declares a minimum, and none above $MIN.
# Each line says OK or WRONG and the number of its check, so that misc/test-verify-macos-bundle.sh can tell which check refused.
# usage: misc/verify-macos-bundle.sh <package>      (package: a .app folder or a .zip)
set -uo pipefail
# the minimum macOS of the package: the same value as macos.minimumVersion in desktop/pom.xml (spec 020)
MIN=15.0
PKG="${1:?package (.app or .zip)}"
WORK="$(mktemp -d /tmp/verify-macos-bundle.XXXXXX)"; trap 'chmod -R u+w "$WORK" 2>/dev/null; rm -rf "$WORK"' EXIT
case "$PKG" in
  *.zip) ditto -x -k "$PKG" "$WORK/x" 2>/dev/null || { echo "cannot open $PKG" >&2; exit 2; }; APP="$(find "$WORK/x" -maxdepth 2 -name '*.app' -type d | head -1)";;
  *) APP="$PKG";;
esac
[ -d "${APP:-}" ] || { echo "no .app in $PKG" >&2; exit 2; }
fail=0
ok() { echo "OK      [$1] $2"; }
wrong() { echo "WRONG   [$1] $2"; fail=1; }

# the minimum macOS of one slice of a Mach-O file, or nothing when it declares none
minos() { # file arch
  otool -arch "$2" -l "$1" 2>/dev/null | awk '
    /cmd LC_BUILD_VERSION/ { b=1; next }
    /cmd LC_VERSION_MIN_MACOSX/ { v=1; next }
    b && $1=="minos" { print $2; exit }
    v && $1=="version" { print $2; exit }
    /cmd / { b=0; v=0 }'
}
# compares two macOS versions major.minor: prints -1, 0 or 1
vcmp() {
  awk -v a="$1" -v b="$2" 'BEGIN { split(a,x,"."); split(b,y,"."); r=0
    for (i=1;i<=2;i++) { if (x[i]+0<y[i]+0) { r=-1; break } if (x[i]+0>y[i]+0) { r=1; break } } print r }'
}

DIRS="$(find "$APP/Contents/MacOS" -mindepth 1 -type d 2>/dev/null | head -3)"
if [ -n "$DIRS" ]; then wrong 1 "Contents/MacOS holds folders (it must hold only the executable): $(echo "$DIRS" | head -1 | sed "s|.*Contents/|Contents/|")"; else ok 1 "Contents/MacOS holds only files"; fi
OUT="$(codesign --verify --deep --strict "$APP" 2>&1)"; code=$?
if [ "$code" = 0 ]; then ok 2 "the code signature of the bundle is valid"; else wrong 2 "the code signature of the bundle is not valid: $(echo "$OUT" | head -1 | sed "s|$APP|the .app|")"; fi

PLISTMIN="$(/usr/libexec/PlistBuddy -c 'Print :LSMinimumSystemVersion' "$APP/Contents/Info.plist" 2>/dev/null)"
if [ "$PLISTMIN" = "$MIN" ]; then ok 3 "the Info.plist declares LSMinimumSystemVersion $MIN"; else wrong 3 "the Info.plist declares LSMinimumSystemVersion ${PLISTMIN:-none} instead of $MIN"; fi

LAUNCHER="Contents/MacOS/tuxguitar-launcher"
AUDIOUNIT="Contents/Resources/tuxguitar/lib/libtuxguitar-audiounit-jni.jnilib"
own=0
for part in "$LAUNCHER" "$AUDIOUNIT"; do
  if [ ! -f "$APP/$part" ]; then wrong 4 "$part is missing"; own=1; continue; fi
  for arch in $(lipo -archs "$APP/$part" 2>/dev/null); do
    m="$(minos "$APP/$part" "$arch")"
    [ "$m" = "$MIN" ] || { wrong 4 "$part ($arch) declares the minimum ${m:-none} instead of $MIN"; own=1; }
  done
done
[ "$own" = 0 ] && ok 4 "the launcher and the AudioUnit library declare the minimum $MIN"

other=0
while IFS= read -r -d '' f; do
  rel="${f#"$APP"/}"
  [ "$rel" = "$LAUNCHER" ] || [ "$rel" = "$AUDIOUNIT" ] && continue
  case "$(file -b "$f")" in Mach-O*) ;; *) continue;; esac
  for arch in $(lipo -archs "$f" 2>/dev/null); do
    m="$(minos "$f" "$arch")"
    if [ -z "$m" ]; then wrong 5 "$rel ($arch) declares no minimum"; other=1; break 2
    elif [ "$(vcmp "$m" "$MIN")" = 1 ]; then wrong 5 "$rel ($arch) declares the minimum $m, above $MIN"; other=1; break 2; fi
  done
done < <(find "$APP/Contents" -type f -print0)
[ "$other" = 0 ] && ok 5 "no other native code declares a minimum above $MIN"

[ "$fail" = 0 ] && echo "bundle verified: $(basename "$APP")"
exit $fail
