#!/bin/bash
# Self-test of misc/release-identity.sh (spec 015, FR-001, FR-009, FR-011): the one release version is derived once from the tag, every notation from it,
# and a tag that is not a version is refused. Run by the first job of the Release workflow.
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ID="$ROOT/misc/release-identity.sh"
SHA=fc50a2f43aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
fail=0
get() { echo "$1" | grep "^$2=" | head -1 | cut -d= -f2-; }
eq() { if [ "$2" = "$3" ]; then echo "ok: $1"; else echo "FAILED: $1 (read '$2', expected '$3')"; fail=1; fi; }
out="$("$ID" v0.6.0-beta.1 "$SHA" 2>&1)"; code=$?
eq "a labelled tag: exit 0" "$code" 0
eq "version has no leading v" "$(get "$out" version)" "0.6.0-beta.1"
eq "numeric" "$(get "$out" numeric)" "0.6.0.0"
eq "debversion uses a tilde" "$(get "$out" debversion)" "0.6.0~beta.1"
eq "commit" "$(get "$out" commit)" "$SHA"
eq "prerelease for a label" "$(get "$out" prerelease)" "true"
out="$("$ID" v1.2.3 "$SHA" 2>&1)"
eq "a final tag: version" "$(get "$out" version)" "1.2.3"
eq "a final tag: debversion has no tilde" "$(get "$out" debversion)" "1.2.3"
eq "a final tag is not a prerelease" "$(get "$out" prerelease)" "false"
out="$("$ID" v0.6.0-test-1.rc "$SHA" 2>&1)"
eq "a free label with a hyphen: debversion" "$(get "$out" debversion)" "0.6.0~test.1.rc"
eq "a free label: numeric" "$(get "$out" numeric)" "0.6.0.0"
for bad in "0.6.0" "v0.6" "v0.6.0-" "v0.6.0-be ta" "vv0.6.0" "" "latest"; do
  "$ID" "$bad" "$SHA" >/dev/null 2>&1; code=$?
  [ "$code" != 0 ] && echo "ok: refused tag '$bad'" || { echo "FAILED: tag '$bad' was accepted"; fail=1; }
done
"$ID" v0.6.0 "fc50a2f" >/dev/null 2>&1; code=$?
[ "$code" != 0 ] && echo "ok: a short commit is refused" || { echo "FAILED: a short commit was accepted"; fail=1; }
exit $fail
