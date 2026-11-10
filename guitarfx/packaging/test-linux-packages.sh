#!/bin/bash
# Self-test of GuitarFX's Linux packages (spec 027, T059, FR-026): install.sh places the application, its menu entry and the two plugins
# for the user, installs over an older version keeping the settings, and --uninstall removes what it placed, the settings only when asked;
# the .deb and the tar.gz hold the expected paths (only where dpkg-deb exists). Nothing is installed outside a temporary folder.
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
TMP="$(mktemp -d "${TMPDIR:-/tmp}/test-guitarfx-linux.XXXXXX")"; trap 'rm -rf "$TMP"' EXIT
fail=0
ok() { if eval "$2"; then echo "ok: $1"; else echo "FAILED: $1"; fail=1; fi; }

# an unpacked tar.gz, as build-packages.sh makes it
PKG="$TMP/GuitarFX"
mkdir -p "$PKG/bin" "$PKG/share" "$PKG/plugins/GuitarFX.vst3/Contents/x86_64-linux"
printf '#!/bin/sh\necho new\n' > "$PKG/bin/guitarfx"; chmod 755 "$PKG/bin/guitarfx"
cp "$HERE/linux/guitarfx.desktop" "$PKG/share/"
echo library > "$PKG/plugins/GuitarFX.vst3/Contents/x86_64-linux/GuitarFX.so"
echo library > "$PKG/plugins/GuitarFX.clap"
cp "$HERE/linux/install.sh" "$PKG/install.sh"
H="$TMP/home"; mkdir -p "$H/.config/guitarfx" "$H/.vst3/Other.vst3" "$H/.local/bin"
echo "sound.0.name=Mine" > "$H/.config/guitarfx/sounds.properties"
printf '#!/bin/sh\necho old\n' > "$H/.local/bin/guitarfx"
mkdir -p "$H/.vst3/GuitarFX.vst3/Contents/old"

HOME="$H" XDG_CONFIG_HOME= sh "$PKG/install.sh" >/dev/null; code=$?
ok "install ends well" "[ $code -eq 0 ]"
ok "the application is in ~/.local/bin" "[ -x '$H/.local/bin/guitarfx' ] && [ \"\$('$H/.local/bin/guitarfx')\" = new ]"
ok "the menu entry is in ~/.local/share/applications" "[ -f '$H/.local/share/applications/guitarfx.desktop' ]"
ok "the VST3 is in ~/.vst3" "[ -f '$H/.vst3/GuitarFX.vst3/Contents/x86_64-linux/GuitarFX.so' ]"
ok "the older VST3's files are gone" "[ ! -e '$H/.vst3/GuitarFX.vst3/Contents/old' ]"
ok "the CLAP is in ~/.clap" "[ -f '$H/.clap/GuitarFX.clap' ]"
ok "the settings are kept over an older version" "[ -f '$H/.config/guitarfx/sounds.properties' ]"
HOME="$H" XDG_CONFIG_HOME= sh "$PKG/install.sh" --uninstall >/dev/null; code=$?
ok "uninstall ends well" "[ $code -eq 0 ]"
ok "everything it placed is removed" "[ ! -e '$H/.local/bin/guitarfx' ] && [ ! -e '$H/.local/share/applications/guitarfx.desktop' ] && [ ! -e '$H/.vst3/GuitarFX.vst3' ] && [ ! -e '$H/.clap/GuitarFX.clap' ]"
ok "another plugin is kept" "[ -d '$H/.vst3/Other.vst3' ]"
ok "the sounds and settings are kept" "[ -f '$H/.config/guitarfx/sounds.properties' ]"
HOME="$H" XDG_CONFIG_HOME= sh "$PKG/install.sh" >/dev/null
HOME="$H" XDG_CONFIG_HOME= sh "$PKG/install.sh" --uninstall --remove-settings >/dev/null
ok "--remove-settings removes them" "[ ! -e '$H/.config/guitarfx' ]"
HOME="$H" sh "$PKG/install.sh" --remove-settings >/dev/null 2>&1; code=$?
ok "--remove-settings alone is refused" "[ $code -eq 2 ]"
mkdir -p "$TMP/xdg/guitarfx"
HOME="$H" XDG_CONFIG_HOME="$TMP/xdg" sh "$PKG/install.sh" --uninstall --remove-settings >/dev/null
ok "the settings folder follows XDG_CONFIG_HOME" "[ ! -e '$TMP/xdg/guitarfx' ]"

if command -v dpkg-deb >/dev/null; then
	printf '\177ELF fake' > "$TMP/guitarfx"; printf '\177ELF fake' > "$TMP/libguitarfx_plugin.so"
	"$HERE/linux/build-packages.sh" "$TMP/guitarfx" "$TMP/libguitarfx_plugin.so" 1.2.3~beta.1 v1.2.3-beta.1 "$TMP/out" >/dev/null; code=$?
	ok "the packages are built" "[ $code -eq 0 ]"
	DEB="$TMP/out/guitarfx_1.2.3~beta.1_amd64.deb"
	for path in ./usr/bin/guitarfx ./usr/lib/vst3/GuitarFX.vst3/Contents/x86_64-linux/GuitarFX.so ./usr/lib/clap/GuitarFX.clap ./usr/share/applications/guitarfx.desktop; do
		ok "the .deb has $path" "dpkg-deb -c '$DEB' | grep -q ' $path\$'"
	done
	ok "the .deb's version" "[ \"\$(dpkg-deb -f '$DEB' Version)\" = 1.2.3~beta.1 ]"
	for path in GuitarFX/bin/guitarfx GuitarFX/install.sh GuitarFX/share/guitarfx.desktop GuitarFX/plugins/GuitarFX.clap GuitarFX/plugins/GuitarFX.vst3/Contents/x86_64-linux/GuitarFX.so; do
		ok "the tar.gz has $path" "tar -tzf '$TMP/out/GuitarFX-v1.2.3-beta.1-linux-x86_64.tar.gz' | grep -qx '$path'"
	done
else
	echo "SKIPPED: the .deb and tar.gz checks (no dpkg-deb on this machine)"
fi
exit $fail
