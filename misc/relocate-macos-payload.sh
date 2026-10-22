#!/bin/bash
# macOS accepts only executables in Contents/MacOS: everything else there (the JRE, lib/, share/, dist/, doc/, tuxguitar.sh) is moved to
# Contents/Resources/tuxguitar, so that the bundle can be sealed by codesign. The launcher stays and starts Resources/tuxguitar/tuxguitar.sh.
# usage: misc/relocate-macos-payload.sh <path-to-.app>     (safe to run twice)
set -uo pipefail
APP="${1:?usage: relocate-macos-payload.sh <app>}"
MACOS="$APP/Contents/MacOS"; DEST="$APP/Contents/Resources/tuxguitar"
die() { echo "relocate-macos-payload: $*" >&2; exit 1; }
[ -d "$MACOS" ] || die "no such folder: $MACOS"
[ -f "$MACOS/tuxguitar-launcher" ] || die "no launcher in $MACOS"
mkdir -p "$DEST" || die "cannot create $DEST"
shopt -s dotglob nullglob
for entry in "$MACOS"/*; do
  name="$(basename "$entry")"
  [ "$name" = tuxguitar-launcher ] && continue
  if [ -e "$DEST/$name" ] && [ -d "$entry" ] && [ ! -L "$entry" ]; then
    # the destination exists (the folder was filled twice): merge
    cp -Rp "$entry"/. "$DEST/$name"/ && rm -rf "$entry" || die "cannot merge $name"
  else
    mv "$entry" "$DEST/" || die "cannot move $name"
  fi
done
echo "moved the payload of $(basename "$APP") to Contents/Resources/tuxguitar"
