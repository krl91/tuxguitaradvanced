#!/bin/bash
# Self-test of misc/stamp-macos-plist.sh (spec 015, FR-003, FR-009, FR-007): the Info.plist of the macOS package says the version of build-info.properties,
# in the notation the system accepts, and never 9.99-SNAPSHOT. Needs /usr/libexec/PlistBuddy (macOS): skipped elsewhere.
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
if [ ! -x /usr/libexec/PlistBuddy ]; then echo "SKIPPED: PlistBuddy is not available on this machine"; exit 0; fi
TMP="$(mktemp -d /tmp/test-stamp-macos-plist.XXXXXX)"; trap 'rm -rf "$TMP"' EXIT
SHA=fc50a2f43aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
fail=0
get() { python3 -c 'import plistlib,sys; print(plistlib.load(open(sys.argv[1],"rb")).get(sys.argv[2],"<absent>"))' "$1" "$2"; }
eq() { if [ "$2" = "$3" ]; then echo "ok: $1"; else echo "FAILED: $1 (read '$2', expected '$3')"; fail=1; fi; }
fresh() { cp "$ROOT/desktop/build-scripts/common-resources/common-macosx/Contents/Info.plist" "$TMP/Info.plist"; }

# a release with a label
fresh; mkdir -p "$TMP/s1"; "$ROOT/misc/stamp-build-info.sh" "$TMP/s1" --release 0.6.0-beta.1 "$SHA" >/dev/null
"$ROOT/misc/stamp-macos-plist.sh" "$TMP/Info.plist" "$TMP/s1/build-info.properties" >/dev/null 2>&1; code=$?
eq "a release: exit 0" "$code" 0
eq "a release: CFBundleShortVersionString is the numeric part X.Y.Z" "$(get "$TMP/Info.plist" CFBundleShortVersionString)" "0.6.0"
eq "a release: CFBundleVersion is X.Y.Z.0" "$(get "$TMP/Info.plist" CFBundleVersion)" "0.6.0.0"
eq "a release: CFBundleGetInfoString" "$(get "$TMP/Info.plist" CFBundleGetInfoString)" "TuxGuitarAdvanced 0.6.0-beta.1 (fc50a2f)"
eq "a release: TGBuildVersion" "$(get "$TMP/Info.plist" TGBuildVersion)" "0.6.0-beta.1"
eq "a release: TGBuildCommit is the full commit" "$(get "$TMP/Info.plist" TGBuildCommit)" "$SHA"

# running it twice on the same plist gives the same result (the keys are set, not added twice)
"$ROOT/misc/stamp-macos-plist.sh" "$TMP/Info.plist" "$TMP/s1/build-info.properties" >/dev/null 2>&1; code=$?
eq "a second run: exit 0" "$code" 0
eq "a second run: same version" "$(get "$TMP/Info.plist" TGBuildVersion)" "0.6.0-beta.1"

# a development build never shows 9.99-SNAPSHOT nor a release number
fresh; mkdir -p "$TMP/s2"; STAMP_REPO="$TMP/none" "$ROOT/misc/stamp-build-info.sh" "$TMP/s2" --dev >/dev/null
"$ROOT/misc/stamp-macos-plist.sh" "$TMP/Info.plist" "$TMP/s2/build-info.properties" >/dev/null 2>&1
eq "dev: CFBundleShortVersionString" "$(get "$TMP/Info.plist" CFBundleShortVersionString)" "0.0.0"
eq "dev: CFBundleVersion" "$(get "$TMP/Info.plist" CFBundleVersion)" "0.0.0.0"
eq "dev: CFBundleGetInfoString" "$(get "$TMP/Info.plist" CFBundleGetInfoString)" "TuxGuitarAdvanced dev (unknown)"
eq "dev: TGBuildVersion" "$(get "$TMP/Info.plist" TGBuildVersion)" "dev"
if grep -q "SNAPSHOT" "$TMP/Info.plist"; then echo "FAILED: dev: 9.99-SNAPSHOT is still in the plist"; fail=1; else echo "ok: dev: no SNAPSHOT left in the plist"; fi

# a missing or invalid build-info file is refused and the plist is not touched
fresh; cp "$TMP/Info.plist" "$TMP/Info.before"
"$ROOT/misc/stamp-macos-plist.sh" "$TMP/Info.plist" "$TMP/missing.properties" >/dev/null 2>&1; code=$?
if [ "$code" != 0 ] && cmp -s "$TMP/Info.plist" "$TMP/Info.before"; then echo "ok: a missing build-info is refused, the plist is unchanged"; else echo "FAILED: a missing build-info (exit $code)"; fail=1; fi
printf 'version=oops\nnumeric=x\ncommit=y\nmodified=false\n' > "$TMP/bad.properties"
"$ROOT/misc/stamp-macos-plist.sh" "$TMP/Info.plist" "$TMP/bad.properties" >/dev/null 2>&1; code=$?
if [ "$code" != 0 ] && cmp -s "$TMP/Info.plist" "$TMP/Info.before"; then echo "ok: an invalid build-info is refused, the plist is unchanged"; else echo "FAILED: an invalid build-info (exit $code)"; fail=1; fi
exit $fail
