#!/bin/bash
# Self-test of misc/ci-apt-mirrors.sh (spec 027, T058): before apt runs, the mirrors of the runner's list whose name does not resolve
# are dropped (guitarfx.yml's sixth run waited 20 s per package on azure.archive.ubuntu.com until its 20-minute limit); when none is
# left, Ubuntu's main archive is written. A fake resolver answers; nothing is installed.
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
TMP="$(mktemp -d "${TMPDIR:-/tmp}/test-ci-apt-mirrors.XXXXXX")"; trap 'rm -rf "$TMP"' EXIT
fail=0
ok() { if eval "$2"; then echo "ok: $1"; else echo "FAILED: $1"; fail=1; fi; }
cat > "$TMP/resolve" <<'FAKE'
#!/bin/bash
# resolves the names listed in $GOOD only
grep -qx "$1" "$GOOD"
FAKE
chmod +x "$TMP/resolve"
list() { printf 'http://azure.archive.ubuntu.com/ubuntu/\tpriority:1\nhttps://archive.ubuntu.com/ubuntu/\tpriority:2\nhttps://security.ubuntu.com/ubuntu/\tpriority:3\n' > "$TMP/mirrors"; }
run() { GOOD="$TMP/good" CI_APT_SUDO=" " CI_APT_RESOLVE="$TMP/resolve" CI_APT_MIRRORS="$TMP/mirrors" "$HERE/ci-apt-mirrors.sh" >/dev/null 2>&1; }
list; printf 'azure.archive.ubuntu.com\narchive.ubuntu.com\nsecurity.ubuntu.com\n' > "$TMP/good"; run; code=$?
ok "every mirror resolves: ends well" "[ $code -eq 0 ]"
ok "every mirror resolves: the list is kept" "[ \$(wc -l < '$TMP/mirrors') -eq 3 ] && grep -q azure '$TMP/mirrors'"
list; printf 'archive.ubuntu.com\nsecurity.ubuntu.com\n' > "$TMP/good"; run; code=$?
ok "the Azure mirror does not resolve: it is dropped" "[ $code -eq 0 ] && ! grep -q azure '$TMP/mirrors' && grep -q '^https://archive.ubuntu.com/ubuntu/' '$TMP/mirrors'"
ok "the others keep their order" "head -1 '$TMP/mirrors' | grep -q archive.ubuntu.com && tail -1 '$TMP/mirrors' | grep -q security.ubuntu.com"
list; : > "$TMP/good"; run; code=$?
ok "none resolves: Ubuntu's main archive is written" "[ $code -eq 0 ] && grep -q 'http://archive.ubuntu.com/ubuntu/' '$TMP/mirrors' && ! grep -q azure '$TMP/mirrors'"
rm -f "$TMP/mirrors"; run; code=$?
ok "no mirror list (sources without it): nothing to do" "[ $code -eq 0 ] && [ ! -e '$TMP/mirrors' ]"
exit $fail
