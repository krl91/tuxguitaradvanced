#!/bin/bash
# Self-test of GuitarFX's macOS uninstaller (spec 027, FR-026): the CLAP and VST3 folders the installer created are removed when the
# uninstall leaves them empty (guitarfx.yml's fourth run found them left), and kept when another plugin is in them. Nothing is installed.
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
TMP="$(mktemp -d "${TMPDIR:-/tmp}/test-guitarfx-uninstall.XXXXXX")"; trap 'rm -rf "$TMP"' EXIT
fail=0
ok() { if eval "$2"; then echo "ok: $1"; else echo "FAILED: $1"; fail=1; fi; }
R="$TMP/alone"
mkdir -p "$R/Applications/GuitarFX.app" "$R/Library/Audio/Plug-Ins/VST3/GuitarFX.vst3" "$R/Library/Audio/Plug-Ins/CLAP/GuitarFX.clap" \
	"$R/Library/Audio/Plug-Ins/Components/GuitarFX.component"
GUITARFX_UNINSTALL_ROOT="$R" "$HERE/macos/guitarfx-uninstall" >/dev/null; code=$?
ok "the uninstaller ends well" "[ $code -eq 0 ]"
ok "an empty CLAP folder is removed" "[ ! -e '$R/Library/Audio/Plug-Ins/CLAP' ]"
ok "an empty VST3 folder is removed" "[ ! -e '$R/Library/Audio/Plug-Ins/VST3' ]"
ok "the Components folder is kept (macOS ships it)" "[ -d '$R/Library/Audio/Plug-Ins/Components' ]"
R="$TMP/shared"
mkdir -p "$R/Library/Audio/Plug-Ins/VST3/GuitarFX.vst3" "$R/Library/Audio/Plug-Ins/VST3/Other.vst3" \
	"$R/Library/Audio/Plug-Ins/CLAP/GuitarFX.clap" "$R/Library/Audio/Plug-Ins/CLAP/Other.clap"
GUITARFX_UNINSTALL_ROOT="$R" "$HERE/macos/guitarfx-uninstall" >/dev/null
ok "a VST3 folder holding another plugin is kept" "[ -d '$R/Library/Audio/Plug-Ins/VST3/Other.vst3' ]"
ok "a CLAP folder holding another plugin is kept" "[ -d '$R/Library/Audio/Plug-Ins/CLAP/Other.clap' ]"
exit $fail
