#!/bin/bash
# Writes the version of a build into the Info.plist of the macOS package (spec 015, FR-003, FR-009), from the build-info.properties of the same package.
# usage: misc/stamp-macos-plist.sh <Info.plist> <build-info.properties>
# CFBundleShortVersionString = X.Y.Z, CFBundleVersion = X.Y.Z.0 (macOS accepts only numbers there; 0.0.0 / 0.0.0.0 for a development build),
# CFBundleGetInfoString = "TuxGuitarAdvanced <version> (<short commit>)", and the keys TGBuildVersion and TGBuildCommit with the full text and the full commit.
# Nothing is written if the build-info is missing or invalid. Needs /usr/libexec/PlistBuddy.
set -uo pipefail
PLIST="${1:?Info.plist}"; INFO="${2:?build-info.properties}"
PB=/usr/libexec/PlistBuddy
die() { echo "stamp-macos-plist: $*" >&2; exit 1; }
[ -f "$PLIST" ] || die "no such file: $PLIST"
[ -f "$INFO" ] || die "no such file: $INFO"
[ -x "$PB" ] || die "PlistBuddy is not available"
g() { grep "^$1=" "$INFO" | head -1 | cut -d= -f2-; }
VERSION="$(g version)"; NUMERIC="$(g numeric)"; COMMIT="$(g commit)"
if [ "$VERSION" = dev ]; then
  [[ "$NUMERIC" == 0.0.0.0 ]] || die "invalid numeric version for dev: '$NUMERIC'"
else
  [[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.-]+)?$ ]] || die "invalid version: '$VERSION'"
  [[ "$NUMERIC" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.0$ ]] || die "invalid numeric version: '$NUMERIC'"
fi
if [ "$COMMIT" = unknown ]; then SHORT=unknown; else
  [[ "$COMMIT" =~ ^[0-9a-f]{40}$ ]] || die "invalid commit: '$COMMIT'"
  SHORT="${COMMIT:0:7}"
fi
SHORTVERSION="${NUMERIC%.0}"
setkey() { # key value : Set, or Add when the key does not exist yet
  "$PB" -c "Set :$1 $2" "$PLIST" >/dev/null 2>&1 || "$PB" -c "Add :$1 string $2" "$PLIST" >/dev/null || die "cannot write $1"
}
setkey CFBundleShortVersionString "$SHORTVERSION"
setkey CFBundleVersion "$NUMERIC"
setkey CFBundleGetInfoString "TuxGuitarAdvanced $VERSION ($SHORT)"
setkey TGBuildVersion "$VERSION"
setkey TGBuildCommit "$COMMIT"
echo "stamped $PLIST: $VERSION ($SHORT), short version $SHORTVERSION, bundle version $NUMERIC"
