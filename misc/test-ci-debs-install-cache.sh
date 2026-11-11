#!/bin/bash
# Self-test of misc/ci-debs-install.sh (spec 027, T058, eighth run): given the files only, apt still took the same versions from
# Ubuntu's indexes and tried to download them ("E: Unable to fetch some archives"). It must find them in its archive cache: the
# cache is the folder of the extracted packages, named as apt names them (epoch as %3a), with its partial/ folder. A fake gh serves
# the files, a fake apt-get records its arguments; nothing is installed.
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
TMP="$(mktemp -d "${TMPDIR:-/tmp}/test-ci-debs-cache.XXXXXX")"; trap 'rm -rf "$TMP"' EXIT
fail=0
ok() { if eval "$2"; then echo "ok: $1"; else echo "FAILED: $1"; fail=1; fi; }
sum() { if command -v sha256sum >/dev/null; then sha256sum "$@"; else shasum -a 256 "$@"; fi; }
mkdir -p "$TMP/bin" "$TMP/release" "$TMP/pkg"
echo one > "$TMP/pkg/libx_2%3a1.0-1_amd64.deb"; echo two > "$TMP/pkg/tool_1.0_all.deb"
tar -cf "$TMP/release/ci-packages-linux.tar" -C "$TMP/pkg" .
(cd "$TMP/release" && sum ci-packages-linux.tar > SHA256SUMS)
cat > "$TMP/bin/gh" <<'FAKE'
#!/bin/bash
dir=""; patterns=()
while [ $# -gt 0 ]; do case "$1" in -D) dir="$2"; shift 2 ;; -p) patterns+=("$2"); shift 2 ;; *) shift ;; esac; done
for p in "${patterns[@]}"; do cp "$RELEASE/$p" "$dir/" || exit 1; done
FAKE
cat > "$TMP/bin/apt-get" <<'FAKE'
#!/bin/bash
echo "$*" >> "$APTLOG"
for a in "$@"; do case "$a" in Dir::Cache::Archives=*) d="${a#Dir::Cache::Archives=}"; [ -d "$d/partial" ] && echo partial >> "$APTLOG" ;; esac; done
FAKE
chmod +x "$TMP/bin/gh" "$TMP/bin/apt-get"
: > "$TMP/apt.log"
APTLOG="$TMP/apt.log" RELEASE="$TMP/release" PATH="$TMP/bin:$PATH" CI_APT_SUDO=" " GITHUB_REPOSITORY=owner/repo RUNNER_TEMP="$TMP" \
	"$HERE/ci-debs-install.sh" linux >/dev/null 2>&1; code=$?
cache="$(grep -o 'Dir::Cache::Archives=[^ ]*' "$TMP/apt.log" | head -1 | cut -d= -f2)"
cache="${cache%/}"
ok "it ends well" "[ $code -eq 0 ]"
ok "apt's archive cache is the folder of the extracted packages" "[ -n '$cache' ] && [ -f '$cache/libx_2%3a1.0-1_amd64.deb' ] && [ -f '$cache/tool_1.0_all.deb' ]"
ok "the cache has its partial/ folder" "grep -qx partial '$TMP/apt.log'"
ok "the packages are given from the cache, under apt's names (epoch as %3a)" "[ -n '$cache' ] && grep -q -- \" $cache/libx_2%3a1.0-1_amd64.deb\" '$TMP/apt.log'"
ok "the option comes before install" "grep -q 'Dir::Cache::Archives=.* install -y --no-download' '$TMP/apt.log'"
exit $fail
