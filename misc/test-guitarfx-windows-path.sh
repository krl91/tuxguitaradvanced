#!/bin/bash
# Self-test of guitarfx.yml's windows job (spec 027, T058, ninth run): in Git Bash $HOME is a POSIX path (/c/Users/...), which the
# runner does not read in GITHUB_PATH, so cargo-deny, unpacked in $HOME/tools, was not found ("no such command: `deny`"). Every
# line of that job adding to GITHUB_PATH must give a Windows path (cygpath -w).
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
WF="${1:-$HERE/../.github/workflows/guitarfx.yml}"
fail=0
ok() { if eval "$2"; then echo "ok: $1"; else echo "FAILED: $1"; fail=1; fi; }
job="$(awk '/^  windows:/{f=1; print; next} f&&/^  [a-z][a-z0-9-]*:/{f=0} f' "$WF")"
lines="$(printf '%s\n' "$job" | grep -v '^[[:space:]]*#' | grep 'GITHUB_PATH' || true)"
ok "the windows job adds its tools to GITHUB_PATH" "[ -n \"\$lines\" ]"
ok "each GITHUB_PATH line of the windows job gives a Windows path" "! printf '%s\n' \"\$lines\" | grep -v -q 'cygpath -w'"
exit $fail
