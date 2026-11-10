#!/bin/bash
# Self-test of misc/check-guitarfx-size.sh (spec 027, T060, SC-009): with a fake `gh`, it passes when each GuitarFX package is at most a
# tenth of TuxGuitarAdvanced's package of the same system, and fails, naming the package, when one is above or missing.
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CHECK="$ROOT/misc/check-guitarfx-size.sh"
TMP="$(mktemp -d /tmp/test-check-guitarfx-size.XXXXXX)"; trap 'rm -rf "$TMP"' EXIT
TAG=v0.9.0-beta.1; VERSION=0.9.0-beta.1
fail=0
mkdir -p "$TMP/bin"
cat > "$TMP/bin/gh" <<'GH'
#!/bin/bash
case "$1 $2" in
  "release view") printf '%s' "$FAKE_RELEASE";;
  *) echo "fake gh: unexpected $*" >&2; exit 9;;
esac
GH
chmod +x "$TMP/bin/gh"
# name=size pairs
release() { python3 -c 'import json,sys; print(json.dumps({"assets":[{"name":a.split("=")[0],"size":int(a.split("=")[1])} for a in sys.argv[1:]]}))' "$@"; }
check() { # name expected-exit expected-word
  local out code; out="$(PATH="$TMP/bin:$PATH" GITHUB_REPOSITORY=krl91/tuxguitaradvanced "$CHECK" "$TAG" "$VERSION" 2>&1)"; code=$?
  if { [ "$2" = 0 ] && [ "$code" = 0 ] && echo "$out" | grep -q "$3"; } || { [ "$2" != 0 ] && [ "$code" != 0 ] && echo "$out" | grep -q "$3"; }; then echo "ok: $1"
  else echo "FAILED: $1 (exit $code)"; echo "$out" | head -8; fail=1; fi
}
TGA=("TuxGuitarAdvanced-$TAG-macos-arm64.zip=300000000" "TuxGuitarAdvanced-$TAG-windows-x86_64.zip=100000000"
     "TuxGuitarAdvanced-$TAG-linux-amd64.deb=90000000" "TuxGuitarAdvanced-$TAG-linux-x86_64.tar.gz=100000000")
export FAKE_RELEASE="$(release "${TGA[@]}" "GuitarFX-$VERSION-macos-arm64.pkg=10000000" "GuitarFX-$VERSION-windows-x86_64-setup.exe=8000000" \
  "guitarfx_${VERSION}_amd64.deb=9000000" "GuitarFX-$VERSION-linux-x86_64.tar.gz=9000000")"
check "packages within a tenth pass, and the sizes are reported" 0 "GuitarFX-$VERSION-macos-arm64.pkg"
check "a package at exactly a tenth passes" 0 "guitarfx_${VERSION}_amd64.deb"
export FAKE_RELEASE="$(release "${TGA[@]}" "GuitarFX-$VERSION-macos-arm64.pkg=10000000" "GuitarFX-$VERSION-windows-x86_64-setup.exe=10000001" \
  "guitarfx_${VERSION}_amd64.deb=9000000" "GuitarFX-$VERSION-linux-x86_64.tar.gz=9000000")"
check "a package above a tenth fails, named" 1 "TOO BIG.*GuitarFX-$VERSION-windows-x86_64-setup.exe"
export FAKE_RELEASE="$(release "${TGA[@]}" "GuitarFX-$VERSION-macos-arm64.pkg=10000000" "GuitarFX-$VERSION-windows-x86_64-setup.exe=8000000" \
  "GuitarFX-$VERSION-linux-x86_64.tar.gz=9000000")"
check "a missing GuitarFX package fails" 1 "MISSING.*guitarfx_${VERSION}_amd64.deb"
export FAKE_RELEASE="$(release "${TGA[@]:1}" "GuitarFX-$VERSION-macos-arm64.pkg=10000000" "GuitarFX-$VERSION-windows-x86_64-setup.exe=8000000" \
  "guitarfx_${VERSION}_amd64.deb=9000000" "GuitarFX-$VERSION-linux-x86_64.tar.gz=9000000")"
check "a missing TuxGuitarAdvanced package fails" 1 "MISSING.*TuxGuitarAdvanced-$TAG-macos-arm64.zip"
exit $fail
