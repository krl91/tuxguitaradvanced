#!/bin/bash
# Self-test of the line endings GuitarFX's tests rely on (spec 027, T058, tenth run): the Windows runners check out with
# core.autocrlf, so the files that the Rust tests compare byte for byte, or with "\n", got CRLF and three tests failed there
# (bundle, sound_file, parameters_doc). Those files must be checked out with LF everywhere (.gitattributes, eol=lf).
set -uo pipefail
cd "$(dirname "$0")/.." || exit 1
fail=0
for f in guitarfx/guitarfx-plugin/packaging/macos/component.Info.plist docs/guitarfx/parameters.md $(git ls-files 'guitarfx/*.guitarfx'); do
	if [ "$(git check-attr eol -- "$f" | sed 's/.*: //')" = lf ]; then echo "ok: $f is checked out with LF"; else echo "FAILED: $f is not eol=lf"; fail=1; fi
done
exit $fail
