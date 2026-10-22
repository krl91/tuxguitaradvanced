#!/bin/bash
# Verifies that a package says the version and the commit that were asked for (spec 015, FR-003, FR-006, contracts/verify-version.md).
# usage: misc/verify-version.sh <package> <platform> <version> <commit>
#   package: a .app folder, a folder, a .zip, a .tar.gz or a .deb;  platform: macos | windows | linux;  version: 0.6.0-beta.1 (no leading v);  commit: 40 hex
# Each place read prints one line  OK | WRONG | MISSING  <place>: <read> (expected <value>); the exit code is 0 only if every place is OK.
# A check that needs a tool this machine lacks (dpkg-deb) fails with exit 2, unless VERIFY_ALLOW_SKIP=1 (the self-test, which lists what it skipped).
set -uo pipefail
PKG="${1:?package}"; PLATFORM="${2:?platform (macos|windows|linux)}"; VERSION="${3:?version}"; COMMIT="${4:?commit}"
[[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.-]+)?$ ]] || { echo "not a version: $VERSION" >&2; exit 2; }
[[ "$COMMIT" =~ ^[0-9a-f]{40}$ ]] || { echo "not a full commit: $COMMIT" >&2; exit 2; }
BASE="${VERSION%%-*}"; NUMERIC="$BASE.0"; SHORT="${COMMIT:0:7}"
if [ "$VERSION" = "$BASE" ]; then DEBVERSION="$BASE"; else DEBVERSION="$BASE~$(echo "${VERSION#*-}" | tr - .)"; fi
WORK="$(mktemp -d /tmp/verify-version.XXXXXX)"; trap 'rm -rf "$WORK"' EXIT
fail=0; seen=0
ok()   { echo "OK      $1: $2"; seen=$((seen+1)); }
wrong(){ echo "WRONG   $1: read '$2' (expected '$3')"; fail=1; seen=$((seen+1)); }
miss() { echo "MISSING $1: $2"; fail=1; seen=$((seen+1)); }
cmp_() { if [ "$2" = "$3" ]; then ok "$1" "$2"; else wrong "$1" "$2" "$3"; fi; }

NAME="$(basename "$PKG")"
case "$PKG" in
  *.app|*/) ROOTDIR="$PKG" ;;
  *.zip) mkdir "$WORK/x" && unzip -q "$PKG" -d "$WORK/x" && ROOTDIR="$WORK/x" ;;
  *.tar.gz|*.tgz) mkdir "$WORK/x" && tar -xzf "$PKG" -C "$WORK/x" && ROOTDIR="$WORK/x" ;;
  *.deb)
    if ! command -v dpkg-deb >/dev/null 2>&1; then
      echo "CANNOT  dpkg-deb is not installed, the .deb is not verified" >&2
      [ "${VERIFY_ALLOW_SKIP:-0}" = 1 ] && exit 0 || exit 2
    fi
    mkdir "$WORK/x" && dpkg-deb -x "$PKG" "$WORK/x" && ROOTDIR="$WORK/x" ;;
  *) [ -d "$PKG" ] && ROOTDIR="$PKG" || { echo "unknown package type: $PKG" >&2; exit 2; } ;;
esac
[ -n "${ROOTDIR:-}" ] || { echo "cannot open $PKG" >&2; exit 2; }

# the file name (only for a file: a folder has no release name)
if [ -f "$PKG" ]; then
  case "$PLATFORM:$NAME" in
    macos:*.zip) EXPECTED_NAME="TuxGuitarAdvanced-v$VERSION-macos-arm64.zip" ;;
    windows:*.zip) EXPECTED_NAME="TuxGuitarAdvanced-v$VERSION-windows-x86_64.zip" ;;
    linux:*.tar.gz) EXPECTED_NAME="TuxGuitarAdvanced-v$VERSION-linux-x86_64.tar.gz" ;;
    linux:*.deb) EXPECTED_NAME="TuxGuitarAdvanced-v$VERSION-linux-amd64.deb" ;;
    *) EXPECTED_NAME="" ;;
  esac
  [ -z "$EXPECTED_NAME" ] || cmp_ "file name" "$NAME" "$EXPECTED_NAME"
fi

# build-info.properties: the file the application reads
INFOS="$(find "$ROOTDIR" -name build-info.properties -type f 2>/dev/null)"
if [ -z "$INFOS" ]; then miss "build-info.properties" "no such file in $NAME"; fi
while IFS= read -r f; do
  [ -n "$f" ] || continue
  g() { grep "^$1=" "$f" | head -1 | cut -d= -f2-; }
  cmp_ "build-info version" "$(g version)" "$VERSION"
  cmp_ "build-info numeric" "$(g numeric)" "$NUMERIC"
  cmp_ "build-info commit" "$(g commit)" "$COMMIT"
  cmp_ "build-info modified" "$(g modified)" "false"
done <<< "$INFOS"

case "$PLATFORM" in
  macos)
    PLIST="$(find "$ROOTDIR" -path '*.app/Contents/Info.plist' -type f 2>/dev/null | head -1)"
    [ -n "$PLIST" ] || PLIST="$(find "$ROOTDIR" -path '*Contents/Info.plist' -type f 2>/dev/null | head -1)"
    if [ -z "$PLIST" ]; then miss "Info.plist" "none in $NAME"; else
      plistget() { python3 -c 'import plistlib,sys; print(plistlib.load(open(sys.argv[1],"rb")).get(sys.argv[2],""))' "$PLIST" "$1"; }
      cmp_ "CFBundleShortVersionString" "$(plistget CFBundleShortVersionString)" "$BASE"
      cmp_ "CFBundleVersion" "$(plistget CFBundleVersion)" "$NUMERIC"
      cmp_ "CFBundleGetInfoString" "$(plistget CFBundleGetInfoString)" "TuxGuitarAdvanced $VERSION ($SHORT)"
      cmp_ "TGBuildVersion" "$(plistget TGBuildVersion)" "$VERSION"
      cmp_ "TGBuildCommit" "$(plistget TGBuildCommit)" "$COMMIT"
    fi ;;
  windows)
    EXE="$(find "$ROOTDIR" -name tuxguitar.exe -type f 2>/dev/null | head -1)"
    if [ -z "$EXE" ]; then miss "tuxguitar.exe" "none in $NAME"; else
      # the version resource of the exe, read with python3 only: the fixed numbers (signature 0xFEEF04BD) and the text entries (UTF-16LE)
      RES="$(python3 - "$EXE" <<'PY'
import struct,sys
d=open(sys.argv[1],"rb").read()
i=d.find(struct.pack("<I",0xFEEF04BD))
if i>=0:
    fvms,fvls,pvms,pvls=struct.unpack_from("<IIII",d,i+8)
    print("fileVersion=%d.%d.%d.%d"%(fvms>>16,fvms&0xFFFF,fvls>>16,fvls&0xFFFF))
    print("productVersion=%d.%d.%d.%d"%(pvms>>16,pvms&0xFFFF,pvls>>16,pvls&0xFFFF))
def text(key):
    k=key.encode("utf-16-le")+b"\0\0"
    j=d.find(k)
    if j<0: return ""
    p=j+len(k)
    while d[p:p+2]==b"\0\0" and p<len(d)-2: p+=2
    out=b""
    while p+1<len(d) and d[p:p+2]!=b"\0\0":
        out+=d[p:p+2]; p+=2
    return out.decode("utf-16-le","replace")
print("txtFileVersion="+text("FileVersion"))
print("txtProductVersion="+text("ProductVersion"))
PY
)"
      rg() { echo "$RES" | grep "^$1=" | head -1 | cut -d= -f2-; }
      cmp_ "exe fileVersion" "$(rg fileVersion)" "$NUMERIC"
      cmp_ "exe productVersion" "$(rg productVersion)" "$NUMERIC"
      cmp_ "exe FileVersion text" "$(rg txtFileVersion)" "$VERSION"
      cmp_ "exe ProductVersion text" "$(rg txtProductVersion)" "$VERSION"
    fi ;;
  linux)
    if [ "${NAME##*.}" = deb ]; then
      cmp_ "deb Version" "$(dpkg-deb --field "$PKG" Version)" "$DEBVERSION"
      DESC="$(dpkg-deb --field "$PKG" Description)"
      case "$DESC" in *"TuxGuitarAdvanced $VERSION ($COMMIT)"*) ok "deb Description" "carries the version and the full commit";; *) wrong "deb Description" "$(echo "$DESC" | head -1)" "TuxGuitarAdvanced $VERSION ($COMMIT)";; esac
    fi ;;
  *) echo "unknown platform: $PLATFORM" >&2; exit 2 ;;
esac
[ "$seen" -gt 0 ] || { echo "nothing was verified in $NAME"; fail=1; }
[ "$fail" = 0 ] && echo "version verified in $NAME ($PLATFORM): $VERSION $SHORT, $seen place(s) OK"
exit $fail
