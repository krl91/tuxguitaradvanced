# Quickstart: validating spec 016

From `desktop/build-scripts/tuxguitar-macosx-swt-cocoa`, with `export JAVA_HOME=$(/usr/libexec/java_home -v 21); export PATH="$JAVA_HOME/bin:$PATH"`. Close any running TuxGuitar first, and no Finder window on `target/` (a `.DS_Store` written during `clean` fails the last module).

## Automatic

1. **Reasons** ([contracts/refusal-reasons.md](contracts/refusal-reasons.md)): `mvn test -pl ../../TuxGuitar-practice -am -Dtest='RefusalReasonTest,FollowSummaryReasonsTest' -Dsurefire.failIfNoSpecifiedTests=false`.
2. **Verdicts unchanged by the reasons**: the recorded Follow run (`FollowBenchmarkTest`, tag `benchmark`) gives the same figures as `specs/002-practice-industrialization/benchmark/follow-benchmark-recorded-2026-10-08.txt`, plus a reason for each of its 7 missed events.
3. **Time to play** ([contracts/play-time-window.md](contracts/play-time-window.md)): `-Dtest='PlayTimeWindowTest'`; the old windows: `TimingWindowParityTest` unchanged and green.
4. **Pitch positions** ([contracts/follow-leniency.md](contracts/follow-leniency.md)): `-Dtest='FollowLeniencyTest'`; the measure: `-Dtest=FollowLeniencyReplayTest -Dgroups=dataset -DexcludedGroups= -Dfollow.measure=true` prints, per position, the right notes accepted and the negatives accepted (≤ 4 of 98 at Permissive).
5. **The record and its replay** ([contracts/run-record.md](contracts/run-record.md)): `-Dtest='FollowRunRecordTest'`; with the owner's take: `-Dtest=FollowTakeReplayTest -Dgroups=dataset -DexcludedGroups=` (newest file in `~/tuxguitar-follow-takes/`, or `-Dfollow.take=<file>`).
6. **Calibration offer** ([contracts/calibration-offer.md](contracts/calibration-offer.md)): `-Dtest='PreRunCheckRhythmTest,DeclinedRoutesTest'`; the start path source tests unchanged and green; natively `-P swt-gui-tests -Dtest=PreRunWindowRhythmSwtTest`.
7. **Nothing else changed** (SC-006): `mvn -e clean verify -P native-modules`: practice and engine counts equal to the baseline plus the new tests; recognition reports equal to `specs/010-pre-run-check/benchmark/after/`; Arcade's spec 014 figures equal; `git diff --name-status <start>` shows no M/D/R under any `test` directory.

## By hand (the owner)

- **Gate G1 (after story 1)**: in the Mode tab tick *Record this run* and *Keep a copy of the sound*, play `tests/1/_TEST_verifie.tg` in Follow at 70 % with the Rocksmith (calibrated or not, say which); each refused note shows its reason; the summary counts the reasons; two files appear in `~/tuxguitar-follow-takes/`. Tell the developer the file name.
- **Story 2**: with an input never calibrated, start Follow: the start window offers the calibration; *Go on without*, restart: not asked; quit and relaunch: asked again; *Calibrate now*: one click, 8 clicks heard, the run begins in under 15 seconds (SC-005); Wait mode: never asked.
- **Gate G2 (end, SC-007)**: test sheet `specs/test-sheets/F01-suivre-le-tempo.md` rerun at the default settings (time to play 300 ms, Permissive, tempo 70 %, route calibrated): is a right note played roughly in time accepted?
