#!/bin/bash
# The release identity, derived ONCE from the tag asked for and the commit built (spec 015, FR-001, FR-009, FR-011). Prints key=value lines:
#   version, numeric, debversion, commit, prerelease      usage: misc/release-identity.sh <tag> <commit>     (tag: vMAJOR.MINOR.PATCH[-label], any label)
# Exit 1, with a message, for a tag that is not a version or a commit that is not 40 lowercase hexadecimal characters.
set -uo pipefail
TAG="${1-}"; COMMIT="${2-}"
die() { echo "release-identity: $*" >&2; exit 1; }
[[ "$TAG" =~ ^v[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.-]+)?$ ]] || die "the tag '$TAG' is not a version: expected vMAJOR.MINOR.PATCH or vMAJOR.MINOR.PATCH-label (letters, digits, dots, hyphens)"
[[ "$COMMIT" =~ ^[0-9a-f]{40}$ ]] || die "the commit '$COMMIT' is not a full commit (40 lowercase hexadecimal characters)"
VERSION="${TAG#v}"; BASE="${VERSION%%-*}"
if [ "$VERSION" = "$BASE" ]; then DEB="$BASE"; PRE=false; else DEB="$BASE~$(echo "${VERSION#*-}" | tr - .)"; PRE=true; fi
echo "version=$VERSION"
echo "numeric=$BASE.0"
echo "debversion=$DEB"
echo "commit=$COMMIT"
echo "prerelease=$PRE"
