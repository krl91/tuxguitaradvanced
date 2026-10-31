#!/bin/bash
# Signs a whole macOS .app so that its signature is valid (spec 016 fix: a bundle whose signature does not verify is called "damaged" by macOS, with no
# "Open Anyway"). Run after relocate-macos-payload.sh. With a self-signed identity macOS shows "Open Anyway" in System Settings > Privacy & Security.
# With a requirement (spec 020), it becomes the designated requirement of the bundle: the release forces one that names the stable certificate as root,
# so that macOS keeps the microphone permission from one version to the next.
# usage: misc/seal-macos-app.sh <app> [identity (default: ad hoc "-")] [keychain] [requirement (the whole "designated => ..." text)]
set -uo pipefail
APP="${1:?usage: seal-macos-app.sh <app> [identity] [keychain] [requirement]}"; IDENTITY="${2:--}"; KEYCHAIN="${3:-}"; REQUIREMENT="${4:-}"
die() { echo "seal-macos-app: $*" >&2; exit 1; }
[ -d "$APP/Contents/MacOS" ] || die "not an app: $APP"
if find "$APP/Contents/MacOS" -mindepth 1 -type d | grep -q .; then die "Contents/MacOS holds folders: run misc/relocate-macos-payload.sh first"; fi
# the files of the embedded JRE are read-only
chmod -R u+w "$APP" || die "cannot make $APP writable"
# codesign takes a folder whose name has an extension (the JDK's legal/java.base, legal/jdk.jfr...) for a nested bundle and refuses it: keep the licences in one archive
LEGAL="$APP/Contents/Resources/tuxguitar/jre/legal"
if [ -d "$LEGAL" ]; then
  tar -czf "$APP/Contents/Resources/tuxguitar/jre-legal.tar.gz" -C "$APP/Contents/Resources/tuxguitar/jre" legal && rm -rf "$LEGAL" || die "cannot archive the JRE licences"
fi
xattr -cr "$APP" 2>/dev/null || true
ARGS=(--force --sign "$IDENTITY" --identifier app.tuxguitaradvanced)
[ -n "$KEYCHAIN" ] && ARGS+=(--keychain "$KEYCHAIN")
[ -n "$REQUIREMENT" ] && ARGS+=(-r="$REQUIREMENT")
codesign "${ARGS[@]}" "$APP" || die "codesign failed"
codesign --verify --deep --strict --verbose=2 "$APP" || die "the signature of the bundle is not valid"
codesign -dvv "$APP" 2>&1 | grep -E 'Identifier|Authority|Signature|Sealed' | head -5
echo "sealed $(basename "$APP")"
