#!/bin/bash
# Self-test of misc/verify-macos-bundle.sh (spec 020, FR-012, SC-004): the verifier must pass a right bundle and refuse each wrong one by the check meant
# to refuse it. The bundles are small fakes built here with clang (a launcher, a two-slice AudioUnit library, a JRE-like library, an Info.plist), sealed ad hoc
# or with a throwaway certificate in a throwaway keychain that is never trusted and is deleted on exit. macOS only.
set -uo pipefail
[ "$(uname)" = Darwin ] || { echo "test-verify-macos-bundle: macOS only (it needs clang, codesign and security)" >&2; exit 2; }
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VERIFY="$ROOT/misc/verify-macos-bundle.sh"
SEAL="$ROOT/misc/seal-macos-app.sh"
TMP="$(mktemp -d /tmp/test-verify-macos-bundle.XXXXXX)"
KC=""
cleanup() {
  [ -n "$KC" ] && security delete-keychain "$KC" >/dev/null 2>&1
  chmod -R u+w "$TMP" 2>/dev/null; rm -rf "$TMP"
}
trap cleanup EXIT
fail=0
die() { echo "test-verify-macos-bundle: $*" >&2; exit 2; }

printf 'int main(void){return 0;}\n' > "$TMP/main.c"
printf 'int f(void){return 1;}\n' > "$TMP/lib.c"

macho() { # file minos exe|bundle|dylib [arch...]: a tiny Mach-O for each arch (arm64 by default), joined with lipo when there are several
  local file="$1" min="$2" kind="$3"; shift 3
  [ $# -eq 0 ] && set -- arm64
  local src=lib.c flag; case "$kind" in exe) src=main.c; flag="";; bundle) flag=-bundle;; dylib) flag=-dynamiclib;; esac
  local slices=() arch
  for arch in "$@"; do
    /usr/bin/clang -arch "$arch" -mmacosx-version-min="$min" $flag -o "$file.$arch" "$TMP/$src" || die "cannot build $file ($arch, $min)"
    slices+=("$file.$arch")
  done
  if [ ${#slices[@]} -eq 1 ]; then mv "${slices[0]}" "$file"; else lipo -create "${slices[@]}" -output "$file" && rm -f "${slices[@]}" || die "cannot join $file"; fi
}

nominos() { # file: a Mach-O with no minimum, a bare 64-bit arm64 MH_DYLIB header with no load command
  python3 -c 'import struct,sys; open(sys.argv[1],"wb").write(struct.pack("<IiiIIIII",0xfeedfacf,0x0100000c,0,6,0,0,0,0))' "$1" || die "cannot write $1"
}

bundle() { # dir plist-min|none launcher-min au-x86-min|missing au-arm-min [jre-min|none|nominos]: prints the path of <dir>/T.app
  local dir="$1" plist="$2" launcher="$3" aux="$4" auarm="$5" jre="${6:-none}"
  local app="$dir/T.app" res="$dir/T.app/Contents/Resources/tuxguitar"
  mkdir -p "$app/Contents/MacOS" "$res/lib"
  macho "$app/Contents/MacOS/tuxguitar-launcher" "$launcher" exe
  if [ "$aux" != missing ]; then
    macho "$res/lib/x86" "$aux" bundle x86_64; macho "$res/lib/arm" "$auarm" bundle arm64
    lipo -create "$res/lib/x86" "$res/lib/arm" -output "$res/lib/libtuxguitar-audiounit-jni.jnilib" && rm -f "$res/lib/x86" "$res/lib/arm" || die "cannot join the AudioUnit library"
  fi
  case "$jre" in
    none) ;;
    nominos) mkdir -p "$res/jre/lib"; nominos "$res/jre/lib/libjava.dylib";;
    *) mkdir -p "$res/jre/lib"; macho "$res/jre/lib/libjava.dylib" "$jre" dylib;;
  esac
  python3 - "$app/Contents/Info.plist" "$plist" <<'PY' || die "cannot write the Info.plist"
import plistlib,sys
f,minimum=sys.argv[1:3]
d={"CFBundleIdentifier":"app.tuxguitaradvanced","CFBundleExecutable":"tuxguitar-launcher","CFBundleName":"TuxGuitarAdvanced"}
if minimum!="none": d["LSMinimumSystemVersion"]=minimum
plistlib.dump(d,open(f,"wb"))
PY
  echo "$app"
}

newdir() { mktemp -d "$TMP/case.XXXXXX"; }

adhoc() { # app: sealed ad hoc, as the release does without the signing secrets
  "$SEAL" "$1" >/dev/null 2>&1 || die "cannot seal $1 ad hoc"
}

throwaway_identity() { # a self-signed code-signing certificate in a throwaway keychain, never trusted: sets KC and SHA1
  local pw=throwaway
  cat > "$TMP/cs.cnf" <<'CNF'
[req]
distinguished_name=dn
x509_extensions=ext
prompt=no
[dn]
CN=Throwaway Test Signing
[ext]
basicConstraints=critical,CA:false
keyUsage=critical,digitalSignature
extendedKeyUsage=critical,codeSigning
CNF
  openssl req -x509 -newkey rsa:2048 -nodes -keyout "$TMP/key.pem" -out "$TMP/cert.pem" -days 2 -config "$TMP/cs.cnf" >/dev/null 2>&1 || die "cannot make the throwaway certificate"
  # OpenSSL 3 and later encrypt a .p12 in a way security import may refuse: -legacy, unless this openssl does not know it (LibreSSL)
  openssl pkcs12 -export -inkey "$TMP/key.pem" -in "$TMP/cert.pem" -out "$TMP/cert.p12" -passout "pass:$pw" -legacy >/dev/null 2>&1 \
    || openssl pkcs12 -export -inkey "$TMP/key.pem" -in "$TMP/cert.pem" -out "$TMP/cert.p12" -passout "pass:$pw" >/dev/null 2>&1 \
    || die "cannot export the throwaway certificate"
  KC="$TMP/throwaway.keychain-db"
  security create-keychain -p "$pw" "$KC" && security unlock-keychain -p "$pw" "$KC" \
    && security import "$TMP/cert.p12" -k "$KC" -P "$pw" -T /usr/bin/codesign >/dev/null \
    && security set-key-partition-list -S apple-tool:,apple: -s -k "$pw" "$KC" >/dev/null \
    || die "cannot set up the throwaway keychain"
  SHA1="$(security find-identity -p codesigning "$KC" | awk '/^ *1\)/ {print $2; exit}')"
  [ -n "$SHA1" ] || die "no identity in the throwaway keychain"
}

case_() { # name pass|refuse:<n> command...: pass = exit 0 and no WRONG line; refuse:<n> = exit 1 and a line "WRONG   [<n>]" (that check refused it)
  local name="$1" expect="$2"; shift 2
  local out code ok=""
  out="$("$@" 2>&1)"; code=$?
  local wrong; wrong="$(printf '%s\n' "$out" | grep '^WRONG' | tail -1)"
  if [ "$expect" = pass ]; then
    [ "$code" = 0 ] && [ -z "$wrong" ] && ok=1
  else
    [ "$code" = 1 ] && printf '%s\n' "$out" | grep -q "^WRONG   \[${expect#refuse:}\]" && ok=1
  fi
  if [ -n "$ok" ]; then echo "PASS $name"; else echo "FAIL $name (expected $expect, got exit $code: ${wrong:-no WRONG line})"; fail=1; fi
}

# the right bundle
APP="$(bundle "$(newdir)" 15.0 15.0 15.0 15.0 11.0)"; adhoc "$APP"
case_ "right bundle, ad hoc" pass "$VERIFY" "$APP"

# check 3: the minimum the Info.plist declares
APP="$(bundle "$(newdir)" none 15.0 15.0 15.0 11.0)"; adhoc "$APP"
case_ "no LSMinimumSystemVersion" refuse:3 "$VERIFY" "$APP"
APP="$(bundle "$(newdir)" 14.0 15.0 15.0 15.0 11.0)"; adhoc "$APP"
case_ "LSMinimumSystemVersion 14.0" refuse:3 "$VERIFY" "$APP"

# check 4: the minimum of the parts the build makes
APP="$(bundle "$(newdir)" 15.0 14.0 15.0 15.0 11.0)"; adhoc "$APP"
case_ "launcher 14.0" refuse:4 "$VERIFY" "$APP"
APP="$(bundle "$(newdir)" 15.0 26.0 15.0 15.0 11.0)"; adhoc "$APP"
case_ "launcher 26.0" refuse:4 "$VERIFY" "$APP"
APP="$(bundle "$(newdir)" 15.0 15.0 27.0 15.0 11.0)"; adhoc "$APP"
case_ "AudioUnit x86_64 slice 27.0" refuse:4 "$VERIFY" "$APP"
APP="$(bundle "$(newdir)" 15.0 15.0 missing missing 11.0)"; adhoc "$APP"
case_ "AudioUnit library missing" refuse:4 "$VERIFY" "$APP"

# check 5: the other native code, such as the embedded JRE (11.0 today), at most 15.0
APP="$(bundle "$(newdir)" 15.0 15.0 15.0 15.0 26.0)"; adhoc "$APP"
case_ "JRE library 26.0" refuse:5 "$VERIFY" "$APP"
APP="$(bundle "$(newdir)" 15.0 15.0 15.0 15.0 nominos)"; adhoc "$APP"
case_ "JRE library with no minimum" refuse:5 "$VERIFY" "$APP"
APP="$(bundle "$(newdir)" 15.0 15.0 15.0 15.0 11.0)"; adhoc "$APP"
case_ "JRE library 11.0" pass "$VERIFY" "$APP"

# check 6, with --signed-by: the designated requirement names the stable certificate as root (spec 020, FR-005..FR-008)
throwaway_identity
sha1="$(echo "$SHA1" | tr 'A-F' 'a-f')"
signed() { # app [requirement]: signed with the throwaway identity, with that designated requirement (codesign's default when none)
  local req=()
  [ $# -ge 2 ] && req=(-r="$2")
  codesign --force --deep --keychain "$KC" -s "$SHA1" --identifier app.tuxguitaradvanced ${req[@]+"${req[@]}"} "$1" >/dev/null 2>&1 || die "cannot sign $1 with the throwaway identity"
}
ROOTREQ="designated => identifier \"app.tuxguitaradvanced\" and certificate root = H\"$sha1\""
APP="$(bundle "$(newdir)" 15.0 15.0 15.0 15.0 11.0)"; "$SEAL" "$APP" "$SHA1" "$KC" "$ROOTREQ" >/dev/null 2>&1 || die "cannot seal $APP with the throwaway identity"
case_ "throwaway certificate, forced root requirement, --signed-by its SHA1" pass "$VERIFY" "$APP" --signed-by "$SHA1"
case_ "throwaway certificate, --signed-by another SHA1" refuse:6 "$VERIFY" "$APP" --signed-by 0123456789abcdef0123456789abcdef01234567
APP="$(bundle "$(newdir)" 15.0 15.0 15.0 15.0 11.0)"; adhoc "$APP"
case_ "ad hoc, --signed-by SHA1" refuse:6 "$VERIFY" "$APP" --signed-by "$SHA1"
APP="$(bundle "$(newdir)" 15.0 15.0 15.0 15.0 11.0)"; signed "$APP"
case_ "throwaway certificate, default requirement (certificate leaf), --signed-by SHA1" refuse:6 "$VERIFY" "$APP" --signed-by "$SHA1"

[ "$fail" = 0 ] && echo "all cases as expected"
exit $fail
