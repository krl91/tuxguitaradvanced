#!/bin/bash
# SC-009 (spec 027, T060): each GuitarFX package of a draft release is at most a tenth of TuxGuitarAdvanced's package of the same system.
# usage: GITHUB_REPOSITORY=owner/repo misc/check-guitarfx-size.sh <tag> <version>
# Prints a table of the sizes (also to $GITHUB_STEP_SUMMARY when set); one line per pair  OK | TOO BIG | MISSING; exit 0 only if every pair is OK.
set -uo pipefail
TAG="${1:?tag}"; VERSION="${2:?version}"
REPO="${GITHUB_REPOSITORY:?GITHUB_REPOSITORY (owner/repo) is required}"
JSON="$(gh release view "$TAG" --repo "$REPO" --json assets 2>/dev/null)" || { echo "MISSING release: no release $TAG in $REPO"; exit 1; }
size() { python3 -c 'import json,sys; print(next((str(a["size"]) for a in json.load(sys.stdin).get("assets",[]) if a["name"]==sys.argv[1]), ""))' "$1" <<< "$JSON"; }
fail=0
TABLE="| System | GuitarFX | Size | TuxGuitarAdvanced | Size | Ratio |
|---|---|---:|---|---:|---:|"
while read -r system gfx tga; do
  g="$(size "$gfx")"; t="$(size "$tga")"
  if [ -z "$g" ]; then echo "MISSING $gfx"; fail=1; continue; fi
  if [ -z "$t" ]; then echo "MISSING $tga"; fail=1; continue; fi
  ratio="$(python3 -c 'import sys; print(f"{int(sys.argv[1]) / int(sys.argv[2]):.3f}")' "$g" "$t")"
  # a tenth at most: 10 x GuitarFX <= TuxGuitarAdvanced, in whole bytes
  if [ $((g * 10)) -le "$t" ]; then echo "OK      $gfx: $g bytes, $ratio of $tga ($t bytes)"
  else echo "TOO BIG $gfx: $g bytes, $ratio of $tga ($t bytes), above a tenth"; fail=1; fi
  TABLE="$TABLE
| $system | $gfx | $g | $tga | $t | $ratio |"
done <<LIST
macOS GuitarFX-$VERSION-macos-arm64.pkg TuxGuitarAdvanced-$TAG-macos-arm64.zip
Windows GuitarFX-$VERSION-windows-x86_64-setup.exe TuxGuitarAdvanced-$TAG-windows-x86_64.zip
Linux-deb guitarfx_${VERSION}_amd64.deb TuxGuitarAdvanced-$TAG-linux-amd64.deb
Linux-tar.gz GuitarFX-$VERSION-linux-x86_64.tar.gz TuxGuitarAdvanced-$TAG-linux-x86_64.tar.gz
LIST
echo "$TABLE"
[ -n "${GITHUB_STEP_SUMMARY:-}" ] && printf '### GuitarFX package sizes (SC-009)\n\n%s\n' "$TABLE" >> "$GITHUB_STEP_SUMMARY"
exit $fail
