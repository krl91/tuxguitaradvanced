#!/bin/bash
# Signs a whole TuxGuitarAdvanced .app (launcher and sealed resources) with a stable self-signed
# code-signing certificate (same identity for every release, so macOS keeps the
# microphone permission across updates). This is NOT an Apple Developer ID: Gatekeeper
# still asks the user to approve the first launch (System Settings > Privacy & Security >
# "Open Anyway").
#
# Usage: sign-macos-launcher.sh <path-to-.app>
# Environment (GitHub Actions secrets):
#   MACOS_CERT_P12_BASE64  the .p12 (certificate + private key), base64 encoded
#   MACOS_CERT_PASSWORD    the .p12 password
# Without them the bundle is sealed with an ad-hoc signature (valid, but not stable across releases), as before.
# No trust step (spec 020, research R3): codesign signs with the untrusted certificate given by its SHA-1, and the signature verifies; macOS 15
# machines refuse to add it to the trust settings anyway (-60005). The designated requirement is forced to name the certificate as root, the one
# every release since spec 016 carries, so that macOS keeps the microphone permission from one version to the next.
set -euo pipefail

APP="${1:?usage: sign-macos-launcher.sh <app>}"
LAUNCHER="$APP/Contents/MacOS/tuxguitar-launcher"
[ -f "$LAUNCHER" ] || { echo "launcher not found: $LAUNCHER" >&2; exit 1; }

if [ -z "${MACOS_CERT_P12_BASE64:-}" ] || [ -z "${MACOS_CERT_PASSWORD:-}" ]; then
  echo "No signing certificate provided: sealing the bundle with an ad-hoc signature"
  exec "$(dirname "$0")/../../misc/seal-macos-app.sh" "$APP"
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

echo "looking for the identity"
# not -v: the certificate is not trusted, so it is listed only under "Matching identities" (a trusted one would be listed again under "Valid identities only")
LISTING="$(security find-identity -p codesigning "$KEYCHAIN")"
IDENTITIES="$(echo "$LISTING" | awk '/Matching identities/ {m=1; next} /Valid identities only/ {m=0} m && /^ *[0-9]+\)/ {print $2}' | sort -u)"
if [ "$(echo "$IDENTITIES" | grep -c .)" != 1 ]; then
  echo "expected exactly one code-signing identity in the keychain:" >&2; echo "$LISTING" >&2; exit 1
fi
IDENTITY="$IDENTITIES"
REQUIREMENT="designated => identifier \"app.tuxguitaradvanced\" and certificate root = H\"$(echo "$IDENTITY" | tr 'A-F' 'a-f')\""

# Seal the whole bundle (the application was moved to Contents/Resources/tuxguitar by the build): a bundle whose signature does not verify is called
# "damaged" by macOS when it is downloaded, with no "Open Anyway"; a valid self-signed one gets "Open Anyway" in System Settings > Privacy & Security.
"$(dirname "$0")/../../misc/seal-macos-app.sh" "$APP" "$IDENTITY" "$KEYCHAIN" "$REQUIREMENT"
codesign -d -r- "$APP" 2>&1 | grep designated
