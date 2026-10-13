#!/bin/bash
# Verifies that the icons declared in specs/008-brand-identity/branding-manifest.json are inside a package and equal to the repository files (spec 008, FR-014).
# usage: misc/verify-branding.sh <package> <platform>   (platform: macos | windows | linux)
#   package: a .app folder, a .zip, a .tar.gz or a .deb. Checks presence and identity (SHA-256), not looks.
set -uo pipefail
PKG="${1:?package}"; PLATFORM="${2:?platform (macos|windows|linux)}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MANIFEST="$ROOT/specs/008-brand-identity/branding-manifest.json"
WORK="$(mktemp -d /tmp/verify-branding.XXXXXX)"; trap 'rm -rf "$WORK"' EXIT
case "$PKG" in
  *.app|*/) ROOTDIR="$PKG" ;;
  *.zip) unzip -q "$PKG" -d "$WORK/x" && ROOTDIR="$WORK/x" ;;
  *.tar.gz|*.tgz) mkdir "$WORK/x" && tar -xzf "$PKG" -C "$WORK/x" && ROOTDIR="$WORK/x" ;;
  *.deb) mkdir "$WORK/x" && dpkg-deb -x "$PKG" "$WORK/x" && ROOTDIR="$WORK/x" ;;
  *) [ -d "$PKG" ] && ROOTDIR="$PKG" || { echo "unknown package type: $PKG" >&2; exit 2; } ;;
esac
[ -n "${ROOTDIR:-}" ] || { echo "cannot open $PKG" >&2; exit 2; }
fail=0; checked=0
while IFS=$'\t' read -r id path suffix; do
  [ -n "$id" ] || continue
  repo="$ROOT/$path"
  want="$(shasum -a 256 "$repo" | cut -d' ' -f1)"
  found="$(find "$ROOTDIR" -type f -path "*/$suffix" 2>/dev/null | head -50)"
  if [ -z "$found" ]; then echo "MISSING  $id: no file ending in $suffix in $PKG"; fail=1; continue; fi
  while IFS= read -r f; do
    got="$(shasum -a 256 "$f" | cut -d' ' -f1)"
    if [ "$got" != "$want" ]; then echo "DIFFERENT $id: $f is not $path"; fail=1; else checked=$((checked+1)); fi
  done <<< "$found"
done < <(python3 - "$MANIFEST" "$PLATFORM" <<'PY'
import json,sys
m=json.load(open(sys.argv[1]))
for s in m["surfaces"]:
    if sys.argv[2] in s.get("packages",[]):
        print("\t".join([s["id"],s["path"],s["packageSuffix"]]))
PY
)
[ "$checked" -gt 0 ] || { echo "nothing was verified in $PKG ($PLATFORM)"; fail=1; }
[ "$fail" = 0 ] && echo "branding verified in $PKG ($PLATFORM): $checked file(s) present and identical to the repository"
exit $fail
