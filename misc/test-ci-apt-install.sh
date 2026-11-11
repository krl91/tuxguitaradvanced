#!/bin/bash
# Self-test of misc/ci-apt-install.sh (spec 027, T058): apt is retried with a wait between attempts, the runner's mirror list is
# replaced by Ubuntu's main archive after three failures, and the script fails once every attempt has failed. A fake apt-get counts
# its calls; nothing is installed.
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
TMP="$(mktemp -d "${TMPDIR:-/tmp}/test-ci-apt.XXXXXX")"; trap 'rm -rf "$TMP"' EXIT
fail=0
ok() { if eval "$2"; then echo "ok: $1"; else echo "FAILED: $1"; fail=1; fi; }
mkdir -p "$TMP/bin"
# the fake fails its first FAILS install calls, then succeeds
cat > "$TMP/bin/apt-get" <<'FAKE'
#!/bin/bash
echo "$*" >> "$COUNT"
if [ "$1" = install ]; then
	n=$(grep -c '^install' "$COUNT")
	[ "$n" -gt "$FAILS" ] && exit 0
	exit 100
fi
exit 0
FAKE
chmod +x "$TMP/bin/apt-get"
run() { # fails, then the script's arguments
	local fails="$1"; shift
	: > "$TMP/count"; printf 'http://azure.archive.ubuntu.com/ubuntu/\tpriority:1\n' > "$TMP/mirrors"
	COUNT="$TMP/count" FAILS="$fails" PATH="$TMP/bin:$PATH" CI_APT_SUDO=" " CI_APT_WAIT=0 CI_APT_MIRRORS="$TMP/mirrors" \
		"$HERE/ci-apt-install.sh" "$@" >/dev/null 2>&1
}
run 0 gcc nsis; code=$?
ok "a first success ends well" "[ $code -eq 0 ]"
ok "it installs what it is given" "grep -q '^install -y gcc nsis' '$TMP/count'"
ok "the mirror list is kept when apt works" "grep -q azure '$TMP/mirrors'"
run 2 gcc; code=$?
ok "two failures, then a success: ends well" "[ $code -eq 0 ] && [ \$(grep -c '^install' '$TMP/count') -eq 3 ]"
ok "each attempt updates first" "[ \$(grep -c '^update' '$TMP/count') -eq 3 ]"
run 4 gcc; code=$?
ok "after three failures the main archive replaces the mirror list" "[ $code -eq 0 ] && grep -q 'http://archive.ubuntu.com/ubuntu/' '$TMP/mirrors' && ! grep -q azure '$TMP/mirrors'"
run 99 gcc; code=$?
ok "every attempt failing: the script fails" "[ $code -ne 0 ] && [ \$(grep -c '^install' '$TMP/count') -eq 6 ]"
"$HERE/ci-apt-install.sh" >/dev/null 2>&1; code=$?
ok "no package: refused" "[ $code -eq 2 ]"
exit $fail
