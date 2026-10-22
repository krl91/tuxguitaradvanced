#!/bin/bash
# Self-test of misc/stamp-build-info.sh (spec 015, T010): the four keys, the refusals, the development forms. Run before the stamp in the Release workflow.
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
STAMP="$ROOT/misc/stamp-build-info.sh"
TMP="$(mktemp -d /tmp/test-stamp-build-info.XXXXXX)"; trap 'rm -rf "$TMP"' EXIT
SHA=fc50a2f43aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
fail=0
ok() { echo "ok: $1"; }
ko() { echo "FAILED: $1"; fail=1; }
val() { grep "^$2=" "$1/build-info.properties" 2>/dev/null | head -1 | cut -d= -f2-; }

# a release: the four keys
mkdir -p "$TMP/r1"
if "$STAMP" "$TMP/r1" --release 0.6.0-beta.1 "$SHA" >/dev/null 2>&1 \
   && [ "$(val "$TMP/r1" version)" = "0.6.0-beta.1" ] && [ "$(val "$TMP/r1" numeric)" = "0.6.0.0" ] \
   && [ "$(val "$TMP/r1" commit)" = "$SHA" ] && [ "$(val "$TMP/r1" modified)" = "false" ]; then ok "a release writes version, numeric, commit, modified=false"; else ko "a release writes the four keys"; fi

# any label, and no label
mkdir -p "$TMP/r2"; "$STAMP" "$TMP/r2" --release 1.2.3 "$SHA" >/dev/null 2>&1
[ "$(val "$TMP/r2" version)" = "1.2.3" ] && [ "$(val "$TMP/r2" numeric)" = "1.2.3.0" ] && ok "a version without label" || ko "a version without label"
mkdir -p "$TMP/r3"; "$STAMP" "$TMP/r3" --release 0.6.0-test "$SHA" >/dev/null 2>&1
[ "$(val "$TMP/r3" numeric)" = "0.6.0.0" ] && ok "a free label gives the same numeric version" || ko "a free label gives the same numeric version"

# refusals: nothing is written, exit 1
refuse() { # name args...
  local name="$1"; shift; rm -rf "$TMP/x"; mkdir -p "$TMP/x"
  "$STAMP" "$TMP/x" "$@" >/dev/null 2>&1; local code=$?
  if [ "$code" = 1 ] && [ ! -e "$TMP/x/build-info.properties" ]; then ok "refused: $name"; else ko "refused: $name (exit $code)"; fi
}
refuse "a version without patch" --release 0.6 "$SHA"
refuse "a version with a space" --release "0.6.0-be ta" "$SHA"
refuse "a version with a leading v" --release v0.6.0 "$SHA"
refuse "a short commit" --release 0.6.0 fc50a2f
refuse "a commit that is not hexadecimal" --release 0.6.0 "g${SHA:1}"
refuse "no mode" 
refuse "a release without commit" --release 0.6.0

# development builds, against a throwaway git repository (STAMP_REPO)
REPO="$TMP/repo"; mkdir -p "$REPO"
( cd "$REPO" && git init -q && git config user.email t@t && git config user.name t && echo a > f && git add f && git commit -q -m one ) >/dev/null 2>&1
HEAD_SHA="$(git -C "$REPO" rev-parse HEAD)"
mkdir -p "$TMP/d1"; STAMP_REPO="$REPO" "$STAMP" "$TMP/d1" --dev >/dev/null 2>&1
if [ "$(val "$TMP/d1" version)" = "dev" ] && [ "$(val "$TMP/d1" commit)" = "$HEAD_SHA" ] && [ "$(val "$TMP/d1" modified)" = "false" ]; then ok "dev on a clean tree"; else ko "dev on a clean tree"; fi
echo b >> "$REPO/f"
mkdir -p "$TMP/d2"; STAMP_REPO="$REPO" "$STAMP" "$TMP/d2" --dev >/dev/null 2>&1
[ "$(val "$TMP/d2" modified)" = "true" ] && ok "dev on a modified tree is modified=true" || ko "dev on a modified tree"
( cd "$REPO" && git checkout -q f && echo u > untracked ) >/dev/null 2>&1
mkdir -p "$TMP/d3"; STAMP_REPO="$REPO" "$STAMP" "$TMP/d3" --dev >/dev/null 2>&1
[ "$(val "$TMP/d3" modified)" = "false" ] && ok "an untracked file does not make a build modified" || ko "an untracked file does not make a build modified"
mkdir -p "$TMP/nogit"; mkdir -p "$TMP/d4"; STAMP_REPO="$TMP/nogit" "$STAMP" "$TMP/d4" --dev >/dev/null 2>&1; code=$?
if [ "$code" = 0 ] && [ "$(val "$TMP/d4" version)" = "dev" ] && [ "$(val "$TMP/d4" commit)" = "unknown" ] && [ "$(val "$TMP/d4" modified)" = "false" ]; then ok "dev outside git: commit=unknown, no error"; else ko "dev outside git (exit $code)"; fi

# a release is never modified, even in a dirty repository
echo b >> "$REPO/f"
mkdir -p "$TMP/r4"; STAMP_REPO="$REPO" "$STAMP" "$TMP/r4" --release 0.6.0 "$SHA" >/dev/null 2>&1
[ "$(val "$TMP/r4" modified)" = "false" ] && ok "a release is never modified" || ko "a release is never modified"

# the share directory is created when it does not exist
STAMP_REPO="$REPO" "$STAMP" "$TMP/new/share" --dev >/dev/null 2>&1
[ -f "$TMP/new/share/build-info.properties" ] && ok "the share directory is created" || ko "the share directory is created"
exit $fail
