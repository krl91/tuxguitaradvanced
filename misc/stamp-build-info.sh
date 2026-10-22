#!/bin/bash
# Writes <share-dir>/build-info.properties, the build identity the application reads (spec 015, contracts/build-info.md).
# usage: misc/stamp-build-info.sh <share-dir> --release <version> <commit>     (a release: the version asked for and the commit built)
#        misc/stamp-build-info.sh <share-dir> --dev                           (a local build: version "dev", the commit and the state of the working copy, from git)
# A release is never "modified". STAMP_REPO overrides the repository read in --dev mode (used by the self-test).
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
die() { echo "stamp-build-info: $*" >&2; exit 1; }
[ $# -ge 2 ] || die "usage: $0 <share-dir> (--release <version> <commit> | --dev)"
SHARE="$1"; MODE="$2"
case "$MODE" in
  --release)
    [ $# -eq 4 ] || die "--release needs a version and a commit"
    VERSION="$3"; COMMIT="$4"
    [[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.-]+)?$ ]] || die "not a version: '$VERSION' (expected MAJOR.MINOR.PATCH[-label])"
    [[ "$COMMIT" =~ ^[0-9a-f]{40}$ ]] || die "not a full commit: '$COMMIT' (expected 40 lowercase hexadecimal characters)"
    MODIFIED=false
    ;;
  --dev)
    [ $# -eq 2 ] || die "--dev takes no other argument"
    VERSION=dev
    REPO="${STAMP_REPO:-$ROOT}"
    COMMIT="$(git -C "$REPO" rev-parse HEAD 2>/dev/null || true)"
    if [[ "$COMMIT" =~ ^[0-9a-f]{40}$ ]]; then
      if [ -n "$(git -C "$REPO" status --porcelain --untracked-files=no 2>/dev/null)" ]; then MODIFIED=true; else MODIFIED=false; fi
    else
      COMMIT=unknown; MODIFIED=false
    fi
    ;;
  *) die "unknown mode '$MODE'";;
esac
BASE="${VERSION%%-*}"
if [ "$VERSION" = dev ]; then NUMERIC=0.0.0.0; else NUMERIC="$BASE.0"; fi
mkdir -p "$SHARE" || die "cannot create $SHARE"
{
  echo "version=$VERSION"
  echo "numeric=$NUMERIC"
  echo "commit=$COMMIT"
  echo "modified=$MODIFIED"
} > "$SHARE/build-info.properties" || die "cannot write $SHARE/build-info.properties"
echo "stamped $SHARE/build-info.properties: $VERSION $COMMIT modified=$MODIFIED"
