#!/bin/bash
# Self-test of misc/verify-branding.sh (spec 008, T039): the verifier must pass on a complete package and must fail, saying MISSING or DIFFERENT, on a package
# that lacks a declared icon or has a different one. Run before the verifier in the Release workflow.
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MANIFEST="$ROOT/specs/008-brand-identity/branding-manifest.json"
TMP="$(mktemp -d /tmp/test-verify-branding.XXXXXX)"; trap 'rm -rf "$TMP"' EXIT
PKG="$TMP/pkg"; mkdir -p "$PKG"
fail=0
# a complete macOS-like package: every declared file, at its package location
while IFS=$'\t' read -r path suffix; do
  mkdir -p "$PKG/$(dirname "$suffix")"; cp "$ROOT/$path" "$PKG/$suffix"
done < <(python3 - "$MANIFEST" <<'PY'
import json,sys
for s in json.load(open(sys.argv[1]))["surfaces"]:
    if "macos" in s.get("packages",[]): print(s["path"]+"\t"+s["packageSuffix"])
PY
)
check() { # name expected-exit-code expected-word
  local out code
  out="$("$ROOT/misc/verify-branding.sh" "$PKG" macos 2>&1)"; code=$?
  if { [ "$2" = 0 ] && [ "$code" = 0 ]; } || { [ "$2" != 0 ] && [ "$code" != 0 ] && echo "$out" | grep -q "$3"; }; then
    echo "ok: $1"
  else
    echo "FAILED: $1 (exit $code)"; echo "$out" | head -3; fail=1
  fi
}
check "a complete package passes" 0 ""
# one declared icon removed -> MISSING
ICON="$PKG/Contents/Resources/icon.icns"
mv "$ICON" "$TMP/icon.icns.saved"
check "a package without the application icon fails with MISSING" 1 MISSING
# the icon back, but a different one -> DIFFERENT
printf 'x' >> "$TMP/icon.icns.saved"; cp "$TMP/icon.icns.saved" "$ICON"
check "a package with a different application icon fails with DIFFERENT" 1 DIFFERENT
# an empty package -> nothing verified, fails
rm -rf "$PKG"; mkdir -p "$PKG"
check "an empty package fails" 1 "nothing was verified\|MISSING"
exit $fail
