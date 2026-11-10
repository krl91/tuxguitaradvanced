#!/bin/bash
# GuitarFX's macOS installer for a release (spec 027, T060): signs the application and its three plugin bundles as TuxGuitarAdvanced's
# release does (sign-macos-launcher.sh), then makes the .pkg with guitarfx/packaging/macos/build-pkg.sh.
#   - with the secrets: the stable self-signed certificate, and the application's designated requirement names it as root, so that macOS
#     keeps GuitarFX's microphone permission from one version to the next;
#   - without them: the ad-hoc signatures the build made are kept.
# This is NOT an Apple Developer ID: Gatekeeper asks the user to approve the first launch.
#
# Usage: build-guitarfx-pkg.sh <GuitarFX.app> <folder with the three plugin bundles> <version X.Y.Z> <output .pkg>
# Environment (GitHub Actions secrets): MACOS_CERT_P12_BASE64, MACOS_CERT_PASSWORD
set -euo pipefail
APP="${1:?usage: build-guitarfx-pkg.sh <app> <plugins> <version> <out.pkg>}"; PLUGINS="${2:?plugins}"; VERSION="${3:?version}"; OUT="${4:?out.pkg}"
BUILD_PKG="$(cd "$(dirname "$0")/../.." && pwd)/guitarfx/packaging/macos/build-pkg.sh"

if [ -z "${MACOS_CERT_P12_BASE64:-}" ] || [ -z "${MACOS_CERT_PASSWORD:-}" ]; then
  echo "No signing certificate provided: the bundles keep their ad-hoc signatures"
  exec "$BUILD_PKG" "$APP" "$PLUGINS" "$VERSION" "$OUT"
fi

WORK="$(mktemp -d)"
KEYCHAIN="$WORK/signing.keychain-db"
KEYCHAIN_PASSWORD="$(openssl rand -hex 16)"
cleanup() {
  security delete-keychain "$KEYCHAIN" >/dev/null 2>&1 || true
  rm -rf "$WORK"
}
trap cleanup EXIT

echo "$MACOS_CERT_P12_BASE64" | base64 --decode > "$WORK/cert.p12"
security create-keychain -p "$KEYCHAIN_PASSWORD" "$KEYCHAIN"
security set-keychain-settings -lut 3600 "$KEYCHAIN"
security unlock-keychain -p "$KEYCHAIN_PASSWORD" "$KEYCHAIN"
security import "$WORK/cert.p12" -k "$KEYCHAIN" -P "$MACOS_CERT_PASSWORD" -T /usr/bin/codesign >/dev/null
security set-key-partition-list -S apple-tool:,apple: -s -k "$KEYCHAIN_PASSWORD" "$KEYCHAIN" >/dev/null
security list-keychains -d user -s "$KEYCHAIN" $(security list-keychains -d user | tr -d '"')

# not -v: the certificate is not trusted, so it is listed only under "Matching identities"
LISTING="$(security find-identity -p codesigning "$KEYCHAIN")"
IDENTITIES="$(echo "$LISTING" | awk '/Matching identities/ {m=1; next} /Valid identities only/ {m=0} m && /^ *[0-9]+\)/ {print $2}' | sort -u)"
if [ "$(echo "$IDENTITIES" | grep -c .)" != 1 ]; then
  echo "expected exactly one code-signing identity in the keychain:" >&2; echo "$LISTING" >&2; exit 1
fi
IDENTITY="$IDENTITIES"

for format in clap vst3 component; do
  codesign --force --sign "$IDENTITY" --keychain "$KEYCHAIN" "$PLUGINS/GuitarFX.$format"
  codesign --verify --deep --strict "$PLUGINS/GuitarFX.$format"
done
SIGN_IDENTITY="$IDENTITY" \
SIGN_REQUIREMENT="designated => identifier \"app.tuxguitar.guitarfx\" and certificate root = H\"$(echo "$IDENTITY" | tr 'A-F' 'a-f')\"" \
  "$BUILD_PKG" "$APP" "$PLUGINS" "$VERSION" "$OUT"
