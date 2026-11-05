# Quickstart: checking spec 024

Run from `desktop/build-scripts/tuxguitar-macosx-swt-cocoa` with JDK 21:

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 21); export PATH="$JAVA_HOME/bin:$PATH"
```

After every Maven build, run `git checkout -- ../../../specs/003-arcade-mode/benchmark/`.

## 1. Figures before (spec 023's code)

Run the five committed dataset benchmarks with each engine, as in spec 023 quickstart §3, and copy `TuxGuitar-practice/target/dataset-benchmark/` into `specs/024-lenient-chords/benchmark/before-{java,native}/`. The datasets are in `~/tuxguitar-datasets`, and the owner's takes in `specs/004-attack-difference-recognition/benchmark/owner-chords{,-2}`.

## 2. The diagnostic (US3, before any rule changes)

```sh
for e in java native; do
  mvn -o test -pl ../../TuxGuitar-practice -am -Dtuxguitar.recognition.engine=$e -Dtuxguitar.native.library.dir=$LIB \
    -Dgroups=dataset -DexcludedGroups= -Dtest=OwnerChordsDiagnosticTest -Dsurefire.failIfNoSpecifiedTests=false
done
```

Expected: `specs/024-lenient-chords/benchmark/diagnostic.md`, where each of the 16 refusals and 13 wrong acceptances has a cause, and the causes are grouped (SC-006).

## 3. The levels (US1, US2)

- `ChordLeniencyBenchmarkTest`, with each engine, prints the sweep and the figures of *Strict*, *Normal* and *Lenient* (research R4).
- *Normal*: at least 38 of 42 accepted, at most 13 wrong, per series within `owner-chords-baseline.properties` (SC-001, SC-002).
- *Strict*: fewer than 13 wrong. *Lenient*: at least *Normal*'s accepted (SC-004).

## 4. The guards (after)

- Run section 1 again into `after-{java,native}/`.
- Then run `ChordLeniencyComparisonTest`, which writes `benchmark/comparison.md` and fails on any guard line lower (SC-003).

## 5. The gate

- `mvn -o -e clean verify -P native-modules`, then `mvn -o test -P swt-gui-tests -pl ../../TuxGuitar-practice -am`, with the Mac session unlocked.
- The *Mode* tab shows **Chord check** with the three levels.
- Choosing *Strict* then opening Recognition settings shows its values. Changing one makes the level *Custom*.

## 6. The owner (SC-005)

Sheet `specs/test-sheets/F15-accords-tolerants.md`: chords of a score through the cable in Wait, Follow and Arcade with *Normal*, then one chord with each level.
