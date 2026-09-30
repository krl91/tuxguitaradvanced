#!/bin/bash
# Signs the native launcher of a TuxGuitarAdvanced .app with a stable self-signed
# code-signing certificate (same identity for every release, so macOS keeps the
# microphone permission across updates). This is NOT an Apple Developer ID: Gatekeeper
# still asks the user to approve the first launch (System Settings > Privacy & Security >
# "Open Anyway").
#
# Usage: sign-macos-launcher.sh <path-to-.app>
# Environment (GitHub Actions secrets):
#   MACOS_CERT_P12_BASE64  the .p12 (certificate + private key), base64 encoded
#   MACOS_CERT_PASSWORD    the .p12 password
# Without them the script does nothing and the build keeps its ad-hoc signature.
# Meant for an ephemeral CI runner: it adds the certificate to the admin trust settings.
set -euo pipefail

APP="${1:?usage: sign-macos-launcher.sh <app>}"
LAUNCHER="$APP/Contents/MacOS/tuxguitar-launcher"
[ -f "$LAUNCHER" ] || { echo "launcher not found: $LAUNCHER" >&2; exit 1; }

if [ -z "${MACOS_CERT_P12_BASE64:-}" ] || [ -z "${MACOS_CERT_PASSWORD:-}" ]; then
  echo "No signing certificate provided: keeping the ad-hoc signature"
  exit 0
fi

WORK="$(mktemp -d)"
KEYCHAIN="$WORK/signing.keychain-db"
KEYCHAIN_PASSWORD="$(openssl rand -hex 16)"
cleanup() {
  security delete-keychain "$KEYCHAIN" >/dev/null 2>&1 || true
  # the runner is ephemeral: removing the trust there only risks waiting for an authorisation
  [ -n "${CI:-}" ] || sudo security remove-trusted-cert -d "$WORK/cert.pem" >/dev/null 2>&1 || true
  rm -rf "$WORK"
}
trap cleanup EXIT

echo "$MACOS_CERT_P12_BASE64" | base64 --decode > "$WORK/cert.p12"
openssl pkcs12 -in "$WORK/cert.p12" -passin env:MACOS_CERT_PASSWORD -clcerts -nokeys -out "$WORK/cert.pem"

security create-keychain -p "$KEYCHAIN_PASSWORD" "$KEYCHAIN"
security set-keychain-settings -lut 3600 "$KEYCHAIN"
security unlock-keychain -p "$KEYCHAIN_PASSWORD" "$KEYCHAIN"
security import "$WORK/cert.p12" -k "$KEYCHAIN" -P "$MACOS_CERT_PASSWORD" -T /usr/bin/codesign >/dev/null
security set-key-partition-list -S apple-tool:,apple: -s -k "$KEYCHAIN_PASSWORD" "$KEYCHAIN" >/dev/null
security list-keychains -d user -s "$KEYCHAIN" $(security list-keychains -d user | tr -d '"')
# codesign only uses identities the system trusts for code signing. On a CI runner the trust
# change would otherwise wait for an interactive authorisation, so allow it first.
echo "adding the certificate to the trust settings"
sudo security authorizationdb write com.apple.trust-settings.admin allow >/dev/null
sudo security add-trusted-cert -d -r trustRoot -p codeSign -k "$KEYCHAIN" "$WORK/cert.pem"

echo "looking for the identity"
IDENTITY="$(security find-identity -v -p codesigning "$KEYCHAIN" | awk 'NR==1 {print $2}')"
[ -n "$IDENTITY" ] || { echo "no valid code-signing identity in the keychain" >&2; exit 1; }

# Sign a standalone copy: inside the .app, codesign would treat the launcher as the main
# executable of the bundle and try to seal everything next to it (the embedded JRE, lib/,
# share/ live in Contents/MacOS), which fails. The signature is embedded in the Mach-O file,
# so copying it back keeps it.
cp -p "$LAUNCHER" "$WORK/tuxguitar-launcher"
codesign --force --sign "$IDENTITY" --keychain "$KEYCHAIN" --identifier app.tuxguitar "$WORK/tuxguitar-launcher"
codesign --verify --strict -v "$WORK/tuxguitar-launcher"
cp -p "$WORK/tuxguitar-launcher" "$LAUNCHER"
codesign -dvv "$LAUNCHER" 2>&1 | grep -E 'Identifier|Authority|Signature'
codesign -d -r- "$LAUNCHER" 2>&1 | grep designated
