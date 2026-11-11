#!/bin/bash
# Drops from a CI runner's apt mirror list the mirrors whose name does not resolve, before apt runs (spec 027, T058): guitarfx.yml's
# sixth run waited 20 s per package on azure.archive.ubuntu.com, which the runner could not resolve, until its 20-minute limit. When no
# mirror is left, Ubuntu's main archive is written. Nothing to do when the runner has no mirror list.
# Usage: misc/ci-apt-mirrors.sh
# CI_APT_SUDO, CI_APT_RESOLVE (a command given a host name), CI_APT_MIRRORS (the list file) are for the self-test.
set -uo pipefail
SUDO="${CI_APT_SUDO:-sudo}"
RESOLVE="${CI_APT_RESOLVE:-}"
MIRRORS="${CI_APT_MIRRORS:-/etc/apt/apt-mirrors.txt}"
[ -f "$MIRRORS" ] || exit 0
resolves() {
	if [ -n "$RESOLVE" ]; then "$RESOLVE" "$1"; else timeout 5 getent hosts "$1" >/dev/null; fi
}
kept=""
while IFS= read -r line; do
	[ -n "$line" ] || continue
	host="$(printf '%s\n' "$line" | sed -E 's#^[a-z]+://([^/[:space:]]+).*#\1#')"
	if resolves "$host"; then
		kept="$kept$line"$'\n'
	else
		echo "dropped from the apt mirrors (does not resolve): $host" >&2
	fi
done < "$MIRRORS"
if [ -z "$kept" ]; then
	echo "no apt mirror resolves: Ubuntu's main archive instead" >&2
	kept=$'http://archive.ubuntu.com/ubuntu/\tpriority:1\nhttp://security.ubuntu.com/ubuntu/\tpriority:2\n'
fi
printf '%s' "$kept" | $SUDO tee "$MIRRORS" >/dev/null
