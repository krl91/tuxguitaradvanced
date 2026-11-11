#!/bin/bash
# Installs a guitarfx.yml job's Ubuntu packages from the release ci-packages of this repository (spec 027, T058): on 2026-11-11 no
# Ubuntu host resolved on the runners while GitHub did. The job's tarball is checked against SHA256SUMS before anything is installed;
# apt installs the packages from the local files only, as its archive cache. The release's README says where each package and its source come from.
# Usage: misc/ci-debs-install.sh linux|windows    (GH_TOKEN and GITHUB_REPOSITORY as GitHub Actions sets them)
# CI_APT_SUDO is for the self-test.
set -uo pipefail
case "${1:-}" in
	linux|windows) JOB="$1" ;;
	*) echo "usage: $0 linux|windows" >&2; exit 2 ;;
esac
SUDO="${CI_APT_SUDO:-sudo}"
DIR="$(mktemp -d "${RUNNER_TEMP:-/tmp}/ci-packages.XXXXXX")"
TAR="ci-packages-$JOB.tar"
gh release download ci-packages -R "$GITHUB_REPOSITORY" -p "$TAR" -p SHA256SUMS -D "$DIR" || { echo "ci-packages: $TAR not downloaded" >&2; exit 1; }
sum() { if command -v sha256sum >/dev/null; then sha256sum "$@"; else shasum -a 256 "$@"; fi; }
expected="$(grep " $TAR\$" "$DIR/SHA256SUMS" | cut -d' ' -f1)"
actual="$(sum "$DIR/$TAR" | cut -d' ' -f1)"
if [ -z "$expected" ] || [ "$expected" != "$actual" ]; then
	echo "ci-packages: $TAR does not match SHA256SUMS" >&2
	exit 1
fi
mkdir -p "$DIR/debs/partial" && tar -xf "$DIR/$TAR" -C "$DIR/debs" || exit 1
# given the files only, apt took the same versions from Ubuntu's indexes and tried to download them (eighth run); the folder is
# made its archive cache, where the tarball keeps apt's own names (epoch as %3a), so that it finds them there
$SUDO apt-get -o Dir::Cache::Archives="$DIR/debs/" install -y --no-download "$DIR"/debs/*.deb
