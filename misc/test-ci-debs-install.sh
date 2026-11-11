#!/bin/bash
# Self-test of misc/ci-debs-install.sh (spec 027, T058): the job's tarball and SHA256SUMS are downloaded from the release ci-packages,
# the tarball is checked before anything is installed, and its packages are installed with apt from the local files only. A fake gh
# serves the files, a fake apt-get records its arguments; nothing is installed.
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
TMP="$(mktemp -d "${TMPDIR:-/tmp}/test-ci-debs.XXXXXX")"; trap 'rm -rf "$TMP"' EXIT
fail=0
ok() { if eval "$2"; then echo "ok: $1"; else echo "FAILED: $1"; fail=1; fi; }
sum() { if command -v sha256sum >/dev/null; then sha256sum "$@"; else shasum -a 256 "$@"; fi; }
mkdir -p "$TMP/bin" "$TMP/release" "$TMP/pkg"
echo one > "$TMP/pkg/a_1_amd64.deb"; echo two > "$TMP/pkg/b_2_all.deb"
tar -cf "$TMP/release/ci-packages-windows.tar" -C "$TMP/pkg" .
(cd "$TMP/release" && sum ci-packages-windows.tar > SHA256SUMS)
cat > "$TMP/bin/gh" <<'FAKE'
#!/bin/bash
# gh release download <tag> -R <repo> -p <pattern>... -D <dir>
echo "$*" >> "$GHLOG"
dir=""; patterns=()
while [ $# -gt 0 ]; do case "$1" in -D) dir="$2"; shift 2 ;; -p) patterns+=("$2"); shift 2 ;; *) shift ;; esac; done
for p in "${patterns[@]}"; do cp "$RELEASE/$p" "$dir/" || exit 1; done
FAKE
cat > "$TMP/bin/apt-get" <<'FAKE'
#!/bin/bash
echo "$*" >> "$APTLOG"
FAKE
chmod +x "$TMP/bin/gh" "$TMP/bin/apt-get"
run() { : > "$TMP/gh.log"; : > "$TMP/apt.log"
	GHLOG="$TMP/gh.log" APTLOG="$TMP/apt.log" RELEASE="$TMP/release" PATH="$TMP/bin:$PATH" CI_APT_SUDO=" " GITHUB_REPOSITORY=owner/repo \
		RUNNER_TEMP="$TMP" "$HERE/ci-debs-install.sh" "$@" >/dev/null 2>&1; }
run windows; code=$?
ok "a good tarball ends well" "[ $code -eq 0 ]"
ok "it asks the release ci-packages of this repository for the job's tarball and SHA256SUMS" "grep -q 'release download ci-packages -R owner/repo' '$TMP/gh.log' && grep -q 'ci-packages-windows.tar' '$TMP/gh.log' && grep -q 'SHA256SUMS' '$TMP/gh.log'"
ok "it installs every package, from the local files only" "grep -q 'install -y --no-download' '$TMP/apt.log' && grep -q 'a_1_amd64.deb' '$TMP/apt.log' && grep -q 'b_2_all.deb' '$TMP/apt.log'"
echo tampered >> "$TMP/release/ci-packages-windows.tar"
run windows; code=$?
ok "a tarball that does not match SHA256SUMS: refused, nothing installed" "[ $code -ne 0 ] && [ ! -s '$TMP/apt.log' ]"
run linux; code=$?
ok "a tarball missing from the release: fails" "[ $code -ne 0 ] && [ ! -s '$TMP/apt.log' ]"
run macos; code=$?
ok "an unknown job: refused" "[ $code -eq 2 ]"
exit $fail
