#!/bin/bash
# Spec 026: compares the dataset reports of one engine with the ones taken before any change (T002).
# Usage: compare.sh <java|native> <folder of the new reports, e.g. desktop/TuxGuitar-practice/target/dataset-benchmark>
# Dates, clock times, run durations and the code revision are masked, and the lines are sorted: the dataset tests run in parallel, so the rows
# of a report come in a different order from one run to the next (seen at the phase 2 gate). The large .tsv probes are kept gzipped in before-<engine>/.
# Exit 0 when every report is identical, 1 otherwise (the differences are printed).
set -u
here=$(cd "$(dirname "$0")" && pwd)
engine=${1:?java or native}; new=${2:?folder of the new reports}
before="$here/before-$engine"
work=$(mktemp -d); trap 'rm -rf "$work"' EXIT
mask() { sed -E -e 's/20[0-9]{2}-[0-9]{2}-[0-9]{2}([ T][0-9]{2}:[0-9]{2}(:[0-9]{2})?)?/<date>/g' \
                -e 's/([Ee]lapsed|[Tt]ook|[Dd]uration)[^0-9]*[0-9]+([.,][0-9]+)? ?(ms|s|min)/\1 <duration>/g' \
                -e 's/(Code revision: ).*/\1<revision>/' | LC_ALL=C sort; }
copy() { # $1 source folder, $2 destination
  mkdir -p "$2"
  for f in "$1"/*; do
    [ -f "$f" ] || continue
    case $f in
      *.gz) gunzip -c "$f" | mask > "$2/$(basename "${f%.gz}")" ;;
      *) mask < "$f" > "$2/$(basename "$f")" ;;
    esac
  done
}
copy "$before" "$work/before"; copy "$new" "$work/after"
# a report of the other engine in the new folder is not compared
for f in "$work/after"/*; do case $(basename "$f") in *-java.*|*-native.*) case $(basename "$f") in *-"$engine".*) ;; *) rm -f "$f" ;; esac ;; esac; done
if diff -r "$work/before" "$work/after"; then echo "identical ($engine)"; exit 0; else exit 1; fi
