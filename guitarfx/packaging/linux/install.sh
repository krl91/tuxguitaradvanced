#!/bin/sh
# Installs GuitarFX from its tar.gz for this user only (spec 027, T059, research R9, FR-026), with nothing asked of the system:
#   ~/.local/bin/guitarfx, ~/.local/share/applications/guitarfx.desktop, ~/.vst3/GuitarFX.vst3, ~/.clap/GuitarFX.clap
# Usage, from the unpacked folder:  ./install.sh                 install, or install over an older version (the settings are kept)
#                                   ./install.sh --uninstall     remove what it installed, keep the sounds and settings
#                                   ./install.sh --uninstall --remove-settings
set -eu
HERE="$(cd "$(dirname "$0")" && pwd)"
BIN="$HOME/.local/bin"
APPLICATIONS="$HOME/.local/share/applications"
SETTINGS="${XDG_CONFIG_HOME:-$HOME/.config}/guitarfx"
UNINSTALL=false
REMOVE_SETTINGS=false
for argument in "$@"; do
	case "$argument" in
		--uninstall) UNINSTALL=true ;;
		--remove-settings) REMOVE_SETTINGS=true ;;
		*) echo "usage: $0 [--uninstall [--remove-settings]]" >&2; exit 2 ;;
	esac
done
if $REMOVE_SETTINGS && ! $UNINSTALL; then echo "--remove-settings goes with --uninstall" >&2; exit 2; fi
remove() {
	for path in "$BIN/guitarfx" "$APPLICATIONS/guitarfx.desktop" "$HOME/.vst3/GuitarFX.vst3" "$HOME/.clap/GuitarFX.clap"; do
		if [ -e "$path" ]; then rm -rf "$path"; echo "removed $path"; fi
	done
}
if $UNINSTALL; then
	remove
	if $REMOVE_SETTINGS; then
		rm -rf "$SETTINGS"; echo "removed $SETTINGS"
	elif [ -d "$SETTINGS" ]; then
		echo "kept your sounds and settings: $SETTINGS"
	fi
	exit 0
fi
# over an older version: the old files go, the settings stay
remove >/dev/null
mkdir -p "$BIN" "$APPLICATIONS" "$HOME/.vst3" "$HOME/.clap"
cp "$HERE/bin/guitarfx" "$BIN/guitarfx"
chmod 755 "$BIN/guitarfx"
cp "$HERE/share/guitarfx.desktop" "$APPLICATIONS/guitarfx.desktop"
cp -R "$HERE/plugins/GuitarFX.vst3" "$HOME/.vst3/GuitarFX.vst3"
cp "$HERE/plugins/GuitarFX.clap" "$HOME/.clap/GuitarFX.clap"
echo "GuitarFX is installed: run guitarfx (in $BIN), or load it in your music software."
