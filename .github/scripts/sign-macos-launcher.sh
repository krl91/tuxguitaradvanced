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
  sudo security remove-trusted-cert -d "$WORK/cert.pem" >/dev/null 2>&1 || true
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
# codesign only uses identities the system trusts for code signing
sudo security add-trusted-cert -d -r trustRoot -p codeSign -k "$KEYCHAIN" "$WORK/cert.pem"

IDENTITY="$(security find-identity -v -p codesigning "$KEYCHAIN" | awk 'NR==1 {print $2}')"
[ -n "$IDENTITY" ] || { echo "no valid code-signing identity in the keychain" >&2; exit 1; }

codesign --force --sign "$IDENTITY" --keychain "$KEYCHAIN" --identifier app.tuxguitar "$LAUNCHER"
codesign --verify --strict -v "$LAUNCHER"
codesign -dvv "$LAUNCHER" 2>&1 | grep -E 'Identifier|Authority|Signature'
codesign -d -r- "$LAUNCHER" 2>&1 | grep designated
