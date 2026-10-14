# Quickstart: running and checking spec 011

From `desktop/build-scripts/tuxguitar-macosx-swt-cocoa` with `export JAVA_HOME=$(/usr/libexec/java_home -v 21)`.

## 1. The new journeys (default build, under 2 minutes added)

```sh
mvn test -pl ../../TuxGuitar-practice -am -Dtest='*JourneyTest,JourneyMatrixTest' -Dsurefire.failIfNoSpecifiedTests=false
```
Expected: every journey of `contracts/journeys.md` marked **new** passes; `JourneyMatrixTest` finds no row without a test or a gap.

## 2. The whole default suite (equal to before, plus the new tests)

```sh
mvn test -pl ../../TuxGuitar-practice -am
```
Expected: the counts of `specs/010-pre-run-check/benchmark/before-after.txt` (practice 911) plus the new tests, 0 failures, the same 4 skipped; the build time is `build-times.txt`'s before plus **under 2 minutes**.

## 3. The layouts on the real toolkit (tagged, opens short windows on your screen)

```sh
mvn test -P swt-gui-tests -pl ../../TuxGuitar-practice -am -Dtest='LayoutSwtJourneyTest,PracticeWindowSwtTest' -Dsurefire.failIfNoSpecifiedTests=false
```

## 4. Stability: the whole set 20 times in a row (tagged)

```sh
mvn test -pl ../../TuxGuitar-practice -am -Dgroups=stability -DexcludedGroups= -Dsurefire.failIfNoSpecifiedTests=false
```
Expected: 20 of 20 passes, no undeclared skip.

## 5. Coverage (profile, on request)

```sh
mvn test -P coverage -pl ../../TuxGuitar-practice -am
```
Expected: `target/site/jacoco/index.html` and the per-package CSV; the figures go to `benchmark/coverage-after.txt` next to `coverage-before.txt`; no package lower.

## 6. The tests can fail (break-on-purpose)

Follow `contracts/break-on-purpose.md`: for each entry, apply the change on a scratch state, run the named test, see it fail, restore. Expected: 24 of 24 entries fail the test named, recorded in `benchmark/break-on-purpose-results.md`.

## 7. Nothing changed for the learner (manual, owner)

Open the built application on `tests/basic_test.tg` and press the transport Practice button: the start window asks the tuning question alone; *Yes* shows the strum part with its meter; a bad level blocks; *No* opens the tuner and brings the question back. Same as the gate G of spec 010.
