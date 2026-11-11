#!/bin/bash
# Installs Ubuntu packages on a CI runner (spec 027, T058): guitarfx.yml's fourth and fifth runs failed when the runner could not reach
# its Azure mirror ("Temporary failure resolving 'azure.archive.ubuntu.com'") while Ubuntu's own archive answered. Each attempt
# updates then installs; a failed attempt waits before the next; after three failures the runner's mirror list is replaced by Ubuntu's
# main archive. Fails once every attempt has failed.
# Usage: misc/ci-apt-install.sh <package>...
# CI_APT_SUDO, CI_APT_WAIT (seconds), CI_APT_MIRRORS (the mirror list file) are for the self-test.
set -uo pipefail
[ $# -gt 0 ] || { echo "usage: $0 <package>..." >&2; exit 2; }
SUDO="${CI_APT_SUDO:-sudo}"
WAIT="${CI_APT_WAIT:-30}"
MIRRORS="${CI_APT_MIRRORS:-/etc/apt/apt-mirrors.txt}"
ATTEMPTS=6
for attempt in $(seq 1 $ATTEMPTS); do
	if $SUDO apt-get update && $SUDO apt-get install -y "$@"; then
		exit 0
	fi
	echo "apt attempt $attempt of $ATTEMPTS failed" >&2
	if [ "$attempt" -eq 3 ] && [ -f "$MIRRORS" ]; then
		echo "the runner's mirrors fail: Ubuntu's main archive instead" >&2
		printf 'http://archive.ubuntu.com/ubuntu/\tpriority:1\nhttp://security.ubuntu.com/ubuntu/\tpriority:2\n' | $SUDO tee "$MIRRORS" >/dev/null
	fi
	[ "$attempt" -lt $ATTEMPTS ] && sleep "$WAIT"
done
exit 1
