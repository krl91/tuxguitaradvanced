#!/bin/bash
# Self-test of misc/verify-version.sh (spec 015, FR-006, SC-004): the verifier must pass on a correct package of each platform and must fail, naming the
# place, when ONE value is wrong. The Debian cases need dpkg-deb: they run where it exists (the Release workflow) and are listed as skipped otherwise.
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VERIFY="$ROOT/misc/verify-version.sh"
TMP="$(mktemp -d /tmp/test-verify-version.XXXXXX)"; trap 'rm -rf "$TMP"' EXIT
VERSION=0.6.0-beta.1; NUMERIC=0.6.0.0; BASE=0.6.0
COMMIT=fc50a2f43aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa; OTHER=0123456789abcdef0123456789abcdef01234567
fail=0; skipped=()

info() { # dir version numeric commit modified
  mkdir -p "$1"; printf 'version=%s\nnumeric=%s\ncommit=%s\nmodified=%s\n' "$2" "$3" "$4" "${5:-false}" > "$1/build-info.properties"
}
plist() { # file shortversion bundleversion getinfo tgversion tgcommit
  python3 - "$@" <<'PY'
import plistlib,sys
f,short,bundle,getinfo,tgv,tgc=sys.argv[1:7]
plistlib.dump({"CFBundleShortVersionString":short,"CFBundleVersion":bundle,"CFBundleGetInfoString":getinfo,"TGBuildVersion":tgv,"TGBuildCommit":tgc,"CFBundleName":"TuxGuitarAdvanced"},open(f,"wb"))
PY
}
fakeexe() { # file numeric(a.b.c.d) textversion
  python3 - "$@" <<'PY'
import struct,sys
f,num,txt=sys.argv[1:4]
a,b,c,d=[int(x) for x in num.split(".")]
def u(s): return s.encode("utf-16-le")+b"\0\0"
body=b"MZ"+b"\0"*60
body+=struct.pack("<IIIIIIIII",0xFEEF04BD,0x10000,(a<<16)|b,(c<<16)|d,(a<<16)|b,(c<<16)|d,0x3F,4,1)
for key in ("FileVersion","ProductVersion"):
    body+=u(key)+b"\0\0"+u(txt)
open(f,"wb").write(body)
PY
}
# the correct packages
macos_pkg() { # dir
  local app="$1/TuxGuitarAdvanced.app"; mkdir -p "$app/Contents/MacOS/share"
  info "$app/Contents/MacOS/share" "$VERSION" "$NUMERIC" "$COMMIT"
  plist "$app/Contents/Info.plist" "$BASE" "$NUMERIC" "TuxGuitarAdvanced $VERSION (${COMMIT:0:7})" "$VERSION" "$COMMIT"
}
windows_pkg() { # dir
  local d="$1/TuxGuitarAdvanced"; mkdir -p "$d/share"
  info "$d/share" "$VERSION" "$NUMERIC" "$COMMIT"; fakeexe "$d/tuxguitar.exe" "$NUMERIC" "$VERSION"
}
linux_pkg() { # dir
  local d="$1/TuxGuitarAdvanced"; mkdir -p "$d/share"; info "$d/share" "$VERSION" "$NUMERIC" "$COMMIT"
}
zipit() { (cd "$1" && zip -qr "$2" "$3"); }

check() { # name expected-exit(0|1) expected-word package platform [version] [commit]
  local out code
  out="$(VERIFY_ALLOW_SKIP=1 "$VERIFY" "$4" "$5" "${6:-$VERSION}" "${7:-$COMMIT}" 2>&1)"; code=$?
  if { [ "$2" = 0 ] && [ "$code" = 0 ]; } || { [ "$2" != 0 ] && [ "$code" != 0 ] && echo "$out" | grep -q "$3"; }; then echo "ok: $1"
  else echo "FAILED: $1 (exit $code)"; echo "$out" | head -4; fail=1; fi
}

# ---- macOS
rm -rf "$TMP/m"; macos_pkg "$TMP/m"; zipit "$TMP/m" "$TMP/TuxGuitarAdvanced-v$VERSION-macos-arm64.zip" TuxGuitarAdvanced.app
check "macos: a correct package passes" 0 "" "$TMP/TuxGuitarAdvanced-v$VERSION-macos-arm64.zip" macos
check "macos: another version asked fails" 1 "WRONG" "$TMP/TuxGuitarAdvanced-v$VERSION-macos-arm64.zip" macos 0.6.0-beta.2
check "macos: another commit asked fails" 1 "WRONG" "$TMP/TuxGuitarAdvanced-v$VERSION-macos-arm64.zip" macos "$VERSION" "$OTHER"
cp "$TMP/TuxGuitarAdvanced-v$VERSION-macos-arm64.zip" "$TMP/TuxGuitarAdvanced-v0.6.0-macos-arm64.zip"
check "macos: a file name with another version fails" 1 "file name" "$TMP/TuxGuitarAdvanced-v0.6.0-macos-arm64.zip" macos
mw() { # name  mutate-command...   (a macOS package with ONE value changed, zipped under the right name)
  local name="$1"; shift; rm -rf "$TMP/mw" "$TMP/mwz"; macos_pkg "$TMP/mw"; "$@"; mkdir -p "$TMP/mwz"
  zipit "$TMP/mw" "$TMP/mwz/TuxGuitarAdvanced-v$VERSION-macos-arm64.zip" TuxGuitarAdvanced.app
  check "macos: $name" 1 "WRONG" "$TMP/mwz/TuxGuitarAdvanced-v$VERSION-macos-arm64.zip" macos
}
APP="$TMP/mw/TuxGuitarAdvanced.app"
mw "a wrong short version fails" plist "$APP/Contents/Info.plist" 0.5.0 "$NUMERIC" "TuxGuitarAdvanced $VERSION (${COMMIT:0:7})" "$VERSION" "$COMMIT"
mw "a wrong bundle version fails" plist "$APP/Contents/Info.plist" "$BASE" 9.99 "TuxGuitarAdvanced $VERSION (${COMMIT:0:7})" "$VERSION" "$COMMIT"
mw "a wrong text version fails" plist "$APP/Contents/Info.plist" "$BASE" "$NUMERIC" "TuxGuitarAdvanced 9.99-SNAPSHOT (${COMMIT:0:7})" "$VERSION" "$COMMIT"
mw "a short commit in place of the full one fails" plist "$APP/Contents/Info.plist" "$BASE" "$NUMERIC" "TuxGuitarAdvanced $VERSION (${COMMIT:0:7})" "$VERSION" "${COMMIT:0:7}"
mw "a modified build fails" info "$APP/Contents/MacOS/share" "$VERSION" "$NUMERIC" "$COMMIT" true
mw "a build-info with another commit fails" info "$APP/Contents/MacOS/share" "$VERSION" "$NUMERIC" "$OTHER"

# ---- Windows
rm -rf "$TMP/w"; windows_pkg "$TMP/w"; WZ="$TMP/TuxGuitarAdvanced-v$VERSION-windows-x86_64.zip"; zipit "$TMP/w" "$WZ" TuxGuitarAdvanced
check "windows: a correct package passes" 0 "" "$WZ" windows
rm -rf "$TMP/w2"; windows_pkg "$TMP/w2"; fakeexe "$TMP/w2/TuxGuitarAdvanced/tuxguitar.exe" 9.99.0.0 "$VERSION"; mkdir -p "$TMP/w2z"; zipit "$TMP/w2" "$TMP/w2z/TuxGuitarAdvanced-v$VERSION-windows-x86_64.zip" TuxGuitarAdvanced
check "windows: a wrong numeric version in the exe fails" 1 "WRONG" "$TMP/w2z/TuxGuitarAdvanced-v$VERSION-windows-x86_64.zip" windows
rm -rf "$TMP/w3"; windows_pkg "$TMP/w3"; fakeexe "$TMP/w3/TuxGuitarAdvanced/tuxguitar.exe" "$NUMERIC" 9.99-SNAPSHOT; mkdir -p "$TMP/w3z"; zipit "$TMP/w3" "$TMP/w3z/TuxGuitarAdvanced-v$VERSION-windows-x86_64.zip" TuxGuitarAdvanced
check "windows: a wrong text version in the exe fails" 1 "WRONG" "$TMP/w3z/TuxGuitarAdvanced-v$VERSION-windows-x86_64.zip" windows
rm -rf "$TMP/w4"; windows_pkg "$TMP/w4"; rm "$TMP/w4/TuxGuitarAdvanced/tuxguitar.exe"; mkdir -p "$TMP/w4z"; zipit "$TMP/w4" "$TMP/w4z/TuxGuitarAdvanced-v$VERSION-windows-x86_64.zip" TuxGuitarAdvanced
check "windows: a package without the exe fails" 1 "MISSING" "$TMP/w4z/TuxGuitarAdvanced-v$VERSION-windows-x86_64.zip" windows
rm -rf "$TMP/w5"; windows_pkg "$TMP/w5"; info "$TMP/w5/TuxGuitarAdvanced/share" "$VERSION" "$NUMERIC" "$OTHER"; mkdir -p "$TMP/w5z"; zipit "$TMP/w5" "$TMP/w5z/TuxGuitarAdvanced-v$VERSION-windows-x86_64.zip" TuxGuitarAdvanced
check "windows: a build-info with another commit fails" 1 "WRONG" "$TMP/w5z/TuxGuitarAdvanced-v$VERSION-windows-x86_64.zip" windows

# ---- Linux tar.gz
rm -rf "$TMP/l"; linux_pkg "$TMP/l"; LT="$TMP/TuxGuitarAdvanced-v$VERSION-linux-x86_64.tar.gz"; (cd "$TMP/l" && tar -czf "$LT" TuxGuitarAdvanced)
check "linux: a correct tar.gz passes" 0 "" "$LT" linux
rm -rf "$TMP/l2"; linux_pkg "$TMP/l2"; info "$TMP/l2/TuxGuitarAdvanced/share" 9.99-SNAPSHOT 9.99.0.0 "$COMMIT"; mkdir -p "$TMP/l2z"; (cd "$TMP/l2" && tar -czf "$TMP/l2z/TuxGuitarAdvanced-v$VERSION-linux-x86_64.tar.gz" TuxGuitarAdvanced)
check "linux: a wrong version in build-info fails" 1 "WRONG" "$TMP/l2z/TuxGuitarAdvanced-v$VERSION-linux-x86_64.tar.gz" linux
rm -rf "$TMP/l3"; mkdir -p "$TMP/l3/TuxGuitarAdvanced"; mkdir -p "$TMP/l3z"; (cd "$TMP/l3" && tar -czf "$TMP/l3z/TuxGuitarAdvanced-v$VERSION-linux-x86_64.tar.gz" TuxGuitarAdvanced)
check "linux: a package without build-info fails" 1 "MISSING" "$TMP/l3z/TuxGuitarAdvanced-v$VERSION-linux-x86_64.tar.gz" linux
rm -rf "$TMP/l4"; linux_pkg "$TMP/l4"; info "$TMP/l4/TuxGuitarAdvanced/share" "$VERSION" "$NUMERIC" "$COMMIT" true; mkdir -p "$TMP/l4z"; (cd "$TMP/l4" && tar -czf "$TMP/l4z/TuxGuitarAdvanced-v$VERSION-linux-x86_64.tar.gz" TuxGuitarAdvanced)
check "linux: modified=true fails" 1 "WRONG" "$TMP/l4z/TuxGuitarAdvanced-v$VERSION-linux-x86_64.tar.gz" linux

# ---- Debian (needs dpkg-deb)
if command -v dpkg-deb >/dev/null 2>&1; then
  mkdeb() { # dir version-field description out
    rm -rf "$1"; mkdir -p "$1/pkg/DEBIAN" "$1/pkg/opt/tuxguitar/share"; info "$1/pkg/opt/tuxguitar/share" "$VERSION" "$NUMERIC" "$COMMIT"
    printf 'Package: tuxguitar\nVersion: %s\nArchitecture: amd64\nMaintainer: x\nDescription: %s\n' "$2" "$3" > "$1/pkg/DEBIAN/control"
    mkdir -p "$(dirname "$4")"; dpkg-deb --build "$1/pkg" "$4" >/dev/null
  }
  mkdeb "$TMP/d1" "0.6.0~beta.1" "TuxGuitarAdvanced $VERSION ($COMMIT)" "$TMP/d1o/TuxGuitarAdvanced-v$VERSION-linux-amd64.deb"
  check "deb: a correct package passes" 0 "" "$TMP/d1o/TuxGuitarAdvanced-v$VERSION-linux-amd64.deb" linux
  mkdeb "$TMP/d2" "0.6.0-beta.1" "TuxGuitarAdvanced $VERSION ($COMMIT)" "$TMP/d2o/TuxGuitarAdvanced-v$VERSION-linux-amd64.deb"
  check "deb: a hyphen in place of the tilde fails" 1 "WRONG" "$TMP/d2o/TuxGuitarAdvanced-v$VERSION-linux-amd64.deb" linux
  mkdeb "$TMP/d3" "0.6.0~beta.1" "TuxGuitarAdvanced 9.99 ($COMMIT)" "$TMP/d3o/TuxGuitarAdvanced-v$VERSION-linux-amd64.deb"
  check "deb: a wrong text version in the description fails" 1 "WRONG" "$TMP/d3o/TuxGuitarAdvanced-v$VERSION-linux-amd64.deb" linux
  mkdeb "$TMP/d4" "0.6.0~beta.1" "TuxGuitarAdvanced $VERSION ($OTHER)" "$TMP/d4o/TuxGuitarAdvanced-v$VERSION-linux-amd64.deb"
  check "deb: a wrong commit in the description fails" 1 "WRONG" "$TMP/d4o/TuxGuitarAdvanced-v$VERSION-linux-amd64.deb" linux
else
  skipped+=("deb: four cases (a correct package, a hyphen for the tilde, a wrong version text, a wrong commit) need dpkg-deb")
fi

# an unknown platform and an empty package
check "an empty package fails" 1 "MISSING" "$TMP/l3z/TuxGuitarAdvanced-v$VERSION-linux-x86_64.tar.gz" linux
if [ "${#skipped[@]}" -gt 0 ]; then echo "SKIPPED on this machine (run in CI):"; printf '  - %s\n' "${skipped[@]}"; fi
exit $fail
