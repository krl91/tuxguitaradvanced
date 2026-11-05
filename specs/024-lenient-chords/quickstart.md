# Quickstart: checking spec 024 (version 2)

Run from `desktop/build-scripts/tuxguitar-macosx-swt-cocoa` with JDK 21:

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 21); export PATH="$JAVA_HOME/bin:$PATH"
LIB=$PWD/../native-modules/tuxguitar-monitor-native/target
OC3=$PWD/../../../specs/004-attack-difference-recognition/benchmark/owner-chords-3
```

After every Maven build, run `git checkout -- ../../../specs/003-arcade-mode/benchmark/`.

## 1. Done

- The figures before: `benchmark/before-{java,native}/`.
- The diagnostic: `benchmark/diagnostic.md`.
- The third series and its figures: `benchmark/owner-chords-3/`.

The takes are not committed (`owner-chords-3/README.md`).

## 2. The probe of the method (stop condition A)

```sh
for e in java native; do
  mvn -o test -pl ../../TuxGuitar-practice -am -Dtuxguitar.recognition.engine=$e -Dtuxguitar.native.library.dir=$LIB \
    -Dtuxguitar.owner.chords=$OC3 -Dgroups=dataset -DexcludedGroups= -Dtest=OwnerChordsProfileProbeTest -Dsurefire.failIfNoSpecifiedTests=false
done
```

Run it a second time without `-Dtuxguitar.owner.chords` for the first two series.

Expected: `benchmark/probe.md`, the sweep of research R6, with both engines.
- At least one configuration gives 25/25 right and 0/408 clearly wrong on both engines and with both arming references (Wait's and Follow's), and at least 8 and 18 on the first two series.
- Otherwise the work stops and reports to the owner.

## 3. The rule and *Normal* (US1)

- The same command with `-Dtest=OwnerChordsProfileTest`, with both engines: 25/25 right and 0 clearly wrong with the defaults (SC-001, SC-002). Voicings and one name off are reported.
- `OwnerChordsBenchmarkTest` without the property (first two series): at least 8 and 18 accepted.

## 4. The guards (after)

- Run the five committed dataset benchmarks with each engine into `benchmark/after-{java,native}/`, as for the figures before.
- Then run `ChordLeniencyComparisonTest`, which writes `benchmark/comparison.md` and fails on any guard line lower, except the incomplete chords refused (floor 20 of 44) and the IDMT chords recognised (floor 85 %), allowed lower by SC-003.

## 5. The levels (US2)

`benchmark/levels.md`:
- the figures of *Strict*, *Normal* and *Lenient* on the third series, with both engines;
- every level accepts 0 clearly wrong;
- *Lenient* accepts at least as many right chords as *Normal*, and *Strict* at most as many wrong ones (SC-004).

## 6. External parameters (FR-013)

- With the application built, open Recognition settings and change the profile threshold.
- A new run uses the new value, with no rebuild.
- The same holds when the value is written into the `tuxguitar-audio-recognition` file with the application closed.

## 7. The gate

- `mvn -o -e clean verify -P native-modules`, then `mvn -o test -P swt-gui-tests -pl ../../TuxGuitar-practice -am`, with the Mac session unlocked.
- The *Mode* tab shows **Chord check** with the three levels. Choosing *Strict*, then opening Recognition settings, shows its values; changing one makes the level *Custom*.
- `docs/architecture/recognition-engines.md` lists every parameter of both engines and the constants still fixed (FR-014).

## 8. The owner (SC-005)

Sheet `specs/test-sheets/F15-accords-tolerants.md`: chords of a score through the cable in Wait, Follow and Arcade with *Normal*, then one chord with each level.
