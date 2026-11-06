# Tasks: every chord the owner plays right is accepted, a clearly wrong chord refused, with a chord leniency the learner can set

**Input**: design documents from `specs/024-lenient-chords/` (plan version 2)

**Prerequisites**: plan.md, spec.md (clarified 2026-11-05), research.md (R0–R11), data-model.md, contracts/chord-profile-rule.md, contracts/chord-leniency.md, quickstart.md

**Version 2** (2026-11-05): T001–T005 are the done phases of version 1 (commit `13b3d6bd6`), kept as they were. From T006 on, the tasks follow plan version 2: the chord's pitch-class profile, on the owner's third series (`owner-chords-3`, the reference).

**Tests**: required. The owner's rules apply:
- tests come first and must be seen red;
- never modify an existing test, only add new ones;
- if a committed test fails, fix the code under the bug protocol, at most 3 attempts, then stop and report.

**Approved exceptions** (spec Clarifications, research R7). Only these committed test lines may change:
- the two `wrong.max` lines of `owner-chords-baseline.properties` (its `accepted.min` lines never change);
- if `CHORD_CONFIRM` must move: `RecognitionSettingsTest` lines 48 and 50 and `RecognitionControlsTest` lines 81–84;
- the `guard(all, "incomplete chord", null, 0.90, report)` line of `DatasetBenchmarkTest`: its floor becomes 0.45, the share measured with the profile rule and both engines (20 of 44; spec Clarifications, the owner's option B of 2026-11-05).
- `SyntheticChordGuardTest`: `BASELINE_MISSING_ACCEPTED_AT_MOST` becomes 77, the count measured with the profile rule; `FollowDeterminismTest`: the line requiring a chord with a string missing to be refused at *Strict* is removed, the line requiring *Strict* and *Very permissive* to judge it alike stays (spec Clarifications, the gate of phase 4, 2026-11-05).

**Stop conditions**: stop and report to the owner, with the figures, when:
- **A (research R6)**: no configuration of the probe gives 25/25 right and 0/408 clearly wrong on the reference series with both engines, while keeping at least 8 and 18 accepted on the first two series;
- **B**: the rule needs evidence the spectrum does not carry (a JNI change);
- **C**: a guard of research R8 drops and no configuration of the sweep avoids it.

**Paths**:
- `AE` = `desktop/TuxGuitar-audio-engine/src/main/java/app/tuxguitar/audio/engine`
- `AET` = `desktop/TuxGuitar-audio-engine/src/test/java/app/tuxguitar/audio/engine`
- `PR` = `desktop/TuxGuitar-practice/src/app/tuxguitar/app/tools/custom/practice`
- `PT` = `desktop/TuxGuitar-practice/test/app/tuxguitar/app/tools/custom/practice`
- `LANG` = `desktop/TuxGuitar-practice/share/lang`
- `BUILD` = `desktop/build-scripts/tuxguitar-macosx-swt-cocoa`
- `SPEC` = `specs/024-lenient-chords`
- `LIB` = `desktop/build-scripts/native-modules/tuxguitar-monitor-native/target`, the native library as built
- `OC3` = `specs/004-attack-difference-recognition/benchmark/owner-chords-3`, the reference series (not committed; labels in `SPEC/benchmark/owner-chords-3/`)

**Standing rules for every phase**:
- **Build:**
  - JDK 21 pinned.
  - After each Maven build, run `git checkout -- specs/003-arcade-mode/benchmark/`.
  - Never run two builds at once, and never edit sources while a build runs.
- **Benchmark engines:** each dataset run is done with `-Dtuxguitar.recognition.engine=java` and with `=native` (`-Dtuxguitar.native.library.dir=$LIB`). Reference series runs add `-Dtuxguitar.owner.chords=$OC3`.
- **Gate:**
  - `mvn -o -e clean verify -P native-modules` from `BUILD` is green;
  - plus `-P swt-gui-tests` when a window changed;
  - `git diff --name-status <phase start> -- '*test*'` shows only added files, apart from the approved exceptions.
- **Real time:** nothing is allocated per frame in the session's chord handling.
- **Strings:** every new string goes in `LANG/messages.properties`, `_fr` and `_es`.
- **Commits and pushes:**
  - commit each phase without any co-author trailer;
  - one push per round, both refs together.

## Format: `[ID] [P?] [Story] Description`

## Phase 1: Setup (the figures before)

- [X] T001 Write `SPEC/baseline.txt`:
  - HEAD;
  - the counts of the full gate and of `swt-gui-tests`;
  - the committed tests that read the files this spec changes (`RecognitionSettingsTest`, `RecognitionSettingsStoreTest`, `RecognitionSettingsStoreProfileTest`, `RecognitionControlsTest`, `PracticeParametersTest`, `FollowLeniencyTest`, `ArcadeLeniencyTest`, `ChordEvidenceParityTest`, the `ChordMatcher` and `PracticeSession` chord tests, `OwnerChordsBenchmarkTest` with its baseline file).
- [X] T002 Run the five committed dataset benchmarks (`DatasetBenchmarkTest`, `GuitarSetChordBenchmarkTest`, `TechniqueDatasetBenchmarkTest`, `OwnerChordsBenchmarkTest`, `BassRecognitionBenchmarkTest`) with each engine at the current code, as in quickstart §1. Copy `desktop/TuxGuitar-practice/target/dataset-benchmark/` and the bass report into `SPEC/benchmark/before-java/` and `SPEC/benchmark/before-native/`. Check that the owner's figures are 26 of 42 and 13 of 42 on both.

## Phase 2: User Story 3 - The cause of each refused chord is known (P3, first: every later choice depends on it)

**Goal**: a recorded cause for each of the 16 refusals and the 13 wrong acceptances, for both engines (FR-010, SC-006).

**Independent Test**: quickstart §2; `SPEC/benchmark/diagnostic.md` exists with the causes grouped and counted.

- [X] T003 [US3] New test `PT/dataset/OwnerChordsDiagnosticTest.java` (tag `dataset`):
  - It reads the owner's takes and pairs each with its control, as `OwnerChordsBenchmarkTest` does.
  - It has its own judge that takes the `RecognitionSettings` and the session parameters as arguments. It mirrors `DatasetJudge.accepts`, which stays unchanged.
  - Per take, it records the fields of data-model "Chord diagnostic":
    - the share of `CLIPPING` frames inside the window;
    - per required pitch at its best frame: heard, missing or unverifiable, with unique partials, clear partials and fraction (`ChordMatcher.check`);
    - the longest run of complete frames;
    - the attack path's best share, peak ratio and balance, from `AttackEvidence` (the same arithmetic as `PolyphonicAcceptance`, recomputed in the test);
    - the cause, the first that applies of *clipped*, *one string short*, *weak evidence*, *too short*, *other* (research R1);
    - the counterfactual: which single relaxation flips the outcome, among L1 to L4 of research R3. L1 and L4 are simulated in the test's judge from the `ChordMatcher.Result` and the level state. L2 and L3 change the parameters.
  - It writes `target/dataset-benchmark/owner-chords-diagnostic-<engine>.txt`.
- [X] T004 [US3] Run T003 with each engine and write `SPEC/benchmark/diagnostic.md`:
  - one row per refused take and per wrong acceptance, with its cause and evidence;
  - the causes grouped with their counts;
  - which relaxation recovers how many refusals and adds how many wrong acceptances;
  - which wrong acceptances are the same chord in another voicing (research R1);
  - the two engines compared.

  Check the stop conditions:
  - if no lever of R3 recovers at least 12 refusals, alone or together, stop and report;
  - if the levers need evidence the engine does not carry, stop and report.
  *Done 2026-11-05 (`benchmark/diagnostic.md`): both engines give identical lines; 26/42 accepted, 13/42 wrong, of which 12 are the same chord in another voicing; clipping is not a cause; on 12 of the 16 refusals every string is heard at its own best frame but not together. The best lever, L1, gives at most 34/42 with 19 wrong (17 voicing pairs); evidence and confirmation recover nothing. **The stop condition applies: implementation stops after T005 and waits for the owner.***
- [X] T005 [US3] Gate; commit "impl(024): phase 2 (US3): diagnostic".

## Phase 3: Probe of the method (blocks US1 and US2)

**Goal**: measure the pitch-class profile on the session's own spectra, with both engines, before any production change (research R6), and choose the values of *Normal*, *Strict* and *Lenient*.

**Independent Test**: quickstart §2. `SPEC/benchmark/probe.md` exists, and stop condition A is checked.

- [X] T006 New test `PT/dataset/OwnerChordsProfileProbeTest.java` (tag `dataset`). It reads the folders of `-Dtuxguitar.owner.chords` (default: the first two series), as `OwnerChordsStrumHoldProbeTest` does. For each take, it builds the frames with the engine of `-Dtuxguitar.recognition.engine`, with the spectrum on, and arms the strum as the session does (onset, `articulationLatencyNanos`, `CHORD_WINDOW`), as in `OwnerChordsStrumHoldProbeTest`. In the test, it computes the profile of data-model "Chord profile":
  - the linear magnitude of `getBandDb(midi)` summed per note name, from the expected chord's lowest pitch minus one semitone up to `top`;
  - the bands more than `noise` dB under the frame's strongest summed band (the tolerated names' bands not counted) left out, when a noise floor is set;
  - the cosine with the expected names, and the count of weak names (under `names` × the strongest expected name's sum);
  - complete when the match reaches the threshold, at most one name is weak, and `settle` ms have passed; accepted after `CHORD_CONFIRM` frames in a row within the window.

  For speed, the frames and their bands are computed once per take and engine and reused by every configuration (about 60,000 configurations per arming reference); only the summing and the decision run per configuration, and the match is recorded per frame so that every threshold is read from one pass.

  It sweeps:
  - the threshold: 0.70 to 0.95 by 0.01;
  - settle: 0, 50, 100, 200 ms;
  - the weak-name share: 0.05, 0.10, 0.20, 0.30, 0.40, 0.50;
  - top: 76, 84, 88, 96;
  - the noise floor: none, 40 dB, 30 dB;
  - weight: linear or square root;
  - confirmation: `CHORD_CONFIRM` at 8 frames (its default) and 4;
  - the attack path: with or without it (with it, the session's `PolyphonicAcceptance` run as in the strum hold probe);
  - the arming reference: Wait's (the arming, `articulationLatencyNanos`) and Follow's (the onset, each frame's age since it, as `FollowSession`); a configuration qualifies only when both give the figures of T007.

  For each configuration it counts:
  - on the reference series: right, voicings, one name off and clearly wrong (data model "Judgement categories"), every take against every take's chord;
  - on the first two series: right accepted per series.

  Once per engine, it also reports the leak: in the reference takes judged against their own chord, the highest share (of the strongest required name's sum) that a name not played reaches.

  It writes `target/dataset-benchmark/owner-chords-profile-probe-<engine>.txt` and asserts nothing about the figures.
- [X] T007 Run T006 on the reference series and on the first two series, with each engine. Write `SPEC/benchmark/probe.md`, with:
  - the configurations that give 25/25 right, 0 clearly wrong, and at least 8 and 18 on the first two series, on both engines and both arming references;
  - the two engines compared;
  - the margin: the weakest right match against the strongest clearly wrong one;
  - the leak of T006 (reported; no longer a criterion, owner's choice 1 of 2026-11-05, research R6).

  Then choose the values with the rule of research R6 (FR-006, FR-007):
  - *Normal*: among the qualifying configurations, the fewest one-name-off accepted; ties go to the highest threshold, then the shortest settle;
  - *Strict*: a higher threshold;
  - *Lenient*: a lower threshold;
  - whether the attack path stays for chords;
  - whether Follow and Arcade relax the threshold; if so, the chosen configuration must also give 25/25 and 0 clearly wrong at the relaxed threshold with Follow's arming (research R7), and the probe reports it;
  - whether the weight and the noise floor are kept as settings (kept when the chosen value is not the plain one: linear, no floor).

  **Done** (`SPEC/benchmark/probe.md`): stop condition A was met with the leak criterion; the owner chose to drop it (choice 1). The values: *Normal* top 84, linear, no noise floor, settle 200 ms, share 0.40, `CHORD_CONFIRM` 8, attack path guarded, threshold 0.79; *Strict* the same with the attack path off; *Lenient* threshold 0.70. Follow and Arcade do not relax the threshold. Weight and noise floor are not settings.

  **Check stop condition A** (research R6): if no configuration qualifies, stop here and report to the owner with the best figures and the spec's fallback.
- [X] T008 Gate (the test files added only); commit "impl(024): phase 3: probe of the pitch-class profile".

## Phase 4: User Story 1 - The chords I play are accepted (P1) 🎯 MVP

**Goal**: with the defaults, both engines accept 25/25 of the reference series and 0 of its 408 clearly wrong chords, the first two series keep at least 8 and 18, and no guard drops (FR-001 to FR-004, FR-003a, FR-009, FR-013).

**Independent Test**: quickstart §3 and §4.

### Tests for User Story 1 (first, seen red)

- [X] T009 [P] [US1] New unit test `PT/session/ChordProfileTest.java`, for the invariants of data-model "Chord profile":
  - a frame where only the expected names sound matches 1;
  - an octave moved inside the range does not change the match;
  - two voicings with the same names match alike;
  - a chord sharing at most one name with a three-name template matches at most 1/√3;
  - a chord holding only two names of a four-name template, or three of a five-name one, has two weak names (`weakNames() ≥ 2`) for every weak-name share of the sweep, whatever its match;
  - a chord missing one name of four has one weak name;
  - a tolerated name adds nothing: a right chord with a silent tolerated name matches as without it, and a ringing tolerated name does not lower the match;
  - with the missing names silent, the four- and five-name clearly wrong chords have two weak names at *Normal*'s share (0.40) and at 0.05 (the leak is not guaranteed against, research R6);
  - `reset` empties the sums;
  - `weakest(n)` returns the expected names of lowest share;
  - an empty profile matches 0;
  - no allocation in `add` (one object across 10 000 calls).

  Frames are built with `SpectrumFrame.of`.
- [X] T010 [P] [US1] New unit test `PT/session/ChordMatcherProfileTest.java`:
  - with `CHORD_COMPLETENESS = profile`, a frame is complete when the match reaches the threshold, at most one expected name is weak, and the settle time has passed;
  - a frame with two weak names is not complete even at a match of 1 on the other names (a four-name chord, two names silent, threshold 0.70);
  - with `all-verifiable`, `ChordMatcher` behaves exactly as before (the same `Result` on the same frames);
  - the old `ChordMatcher.Parameters` constructor keeps `all-verifiable`.
- [X] T011 [P] [US1] New unit test `PT/session/PracticeSessionChordProfileTest.java`, which drives `PracticeSession` with synthetic frames:
  - the profile is reset at each arming;
  - a chord is accepted after `stableHops` complete frames inside the window;
  - a chord with a tolerated pitch (a note still sounding from an earlier beat) is accepted whether that note rings or has faded;
  - when the window ends first, the verdict is wrong, the feedback is `INCOMPLETE` with the expected pitches of the weakest names, and unverifiable is empty;
  - a frame at an unusable level resets the run but not the sums;
  - single notes are unchanged;
  - with `CHORD_ATTACK` off, the attack path accepts no chord but still accepts single notes; with it on, it does not accept a chord whose profile has two weak names, and with no strum armed (no onset) it reads the profile of its own frame (research R6);
  - a clipping or too low frame still gives the input warning it gives today (the spec's edge case on input problems).
- [X] T012 [P] [US1] New unit test `PT/session/FollowSessionChordProfileTest.java`, which drives `FollowSession` with synthetic frames (Follow, and Arcade through the same session):
  - a chord candidate is matched with the profile rule after `stableHops` complete frames inside `windowNanos` of its onset;
  - two chord candidates of one onset have their own profiles;
  - a candidate with a tolerated pitch is matched whether that note rings or has faded;
  - a new onset starts new profiles;
  - when the window ends first, the wrong attempt carries the pitches of the weakest names;
  - single-note candidates are unchanged;
  - the attack path for chords follows `CHORD_ATTACK`, and, if on, never accepts a chord whose profile has two weak names (the profile of its own frame when it comes before any onset).
- [X] T013 [P] [US1] New test `AET/settings/ChordProfileSettingsTest.java`:
  - the new parameters exist with the keys, units, defaults (T007's *Normal*), bounds and sections of data-model "Settings": `CHORD_PROFILE_MATCH`, `CHORD_PROFILE_NAMES` and `CHORD_ATTACK` in the chords section, `CHORD_PROFILE_TOP` and `CHORD_PROFILE_SETTLE` in the new chord details section;
  - the chords section holds 7 parameters (its rows and the new section's texts are checked by the committed `RecognitionControlsTest` and `RecognitionSettingsPanelTest`, which iterate every section);
  - `CHORD_COMPLETENESS` has the options `all-verifiable` and `profile`, with the default `profile`;
  - the values round-trip through `RecognitionSettings.toStrings`/`fromStrings`;
  - none of them is a device-profile parameter (`ProfileParameters.LIST`).
- [X] T014 [P] [US1] New test `AET/settings/ChordProfileMigrationTest.java`:
  - a file with the spec 023 chord values and no profile key reads as *Normal*'s values;
  - a file with `CHORD_EVIDENCE` or `CHORD_CONFIRM` changed by hand keeps its values and `all-verifiable`;
  - a file with a profile key is read as it is;
  - with a test history holding an older set tagged *Strict* and one tagged *Lenient*, a file with either old set reads as that level's current values, and a file differing by one value is read as it is (the table-driven migration of research R7);
  - `VERSION` is still "1".
- [X] T015 [P] [US1] New test `PT/session/ChordProfileParametersTest.java`:
  - `PracticeParameters.chordParameters()` passes the rule and the profile values from the settings;
  - `FollowLeniency` and `ArcadeLeniency` keep the rule, the profile threshold and `CHORD_ATTACK` (T007: no relaxation needed; FR-009);
  - they never relax the weak-name share (`CHORD_PROFILE_NAMES`);
  - a value changed in the settings reaches the next run's `ChordMatcher` with no restart (FR-013).
- [X] T016 [P] [US1] New test `PT/dataset/OwnerChordsProfileTest.java` (tag `dataset`; FR-001, FR-002, SC-001, SC-002):
  - with `RecognitionSettings.defaults()` on the reference series, judged as `DatasetJudge` does (the Wait session), 25 of 25 right accepted and 0 clearly wrong accepted (Follow and Arcade are measured by T006's Follow arming and covered by T012);
  - voicings and one name off reported (`target/dataset-benchmark/owner-chords-profile-<engine>.txt`);
  - skipped when the folder is absent.
- [X] T017 [US1] Compile and run T009–T016: they fail for the right reason (missing classes or values, or the old rule's figures). Record the red run in `SPEC/benchmark/red-us1.txt`.

### Implementation for User Story 1

- [X] T018 [US1] New pure class `PR/session/ChordProfile.java` (data-model "Chord profile"):
  - arrays made once;
  - `reset(required, tolerated, top)`, `add(SpectrumFrame)`, `match()`, `weakNames(share)`, `weakest(n)`, `summedNanos`.
- [X] T019 [US1] `AE/settings/RecognitionParameters.java`: add the option `profile` (`COMPLETENESS_PROFILE`) to `CHORD_COMPLETENESS` with the default `profile`. Add `CHORD_PROFILE_MATCH` (79 %), `CHORD_PROFILE_NAMES` (40 %) and `CHORD_ATTACK` (on) in the chords section (7 parameters, 8 rows: the advanced section is full, research R5). Add a new `Section.CHORD_DETAILS` holding `CHORD_PROFILE_TOP` (84) and `CHORD_PROFILE_SETTLE` (200 ms). Keys, units and bounds as data-model "Settings". Register them in `RecognitionParameters.ALL`. The committed `RecognitionControlsTest.everySectionFitsWithoutScrolling` must stay green.
- [X] T020 [US1] `AE/settings/RecognitionSettings.java` `fromStrings`: the table-driven migration of research R7, beside the legacy tempo key. The history of past sets starts here, in the new class `AE/settings/ChordLeniency.java` (only the history in this phase; T033 adds the levels), holding one set: spec 023's values tagged *Normal*.
- [X] T021 [US1] `PR/session/ChordMatcher.java`: `Parameters` gains the rule and the profile values (a new constructor; the old one keeps `all-verifiable`), and `check(ChordProfile, SpectrumFrame, required, tolerated)` decides by profile when the rule says so, by the per-string rule otherwise (contracts/chord-profile-rule.md). `PR/session/PracticeParameters.java` `chordParameters()` passes them.
- [X] T022 [US1] `PR/session/PracticeSession.java` `onChordFrame`:
  - the profile is reset at each arming and summed on each usable frame;
  - the decision follows contracts/chord-profile-rule.md;
  - the refusal feedback names the expected pitches of the weakest names;
  - the attack path for chords follows `CHORD_ATTACK`, with the weak-name guard of research R6 while on (the profile of its own frame when no strum is armed);
  - nothing is allocated per frame.

  `FollowLeniency` and `ArcadeLeniency` stay as they are: T007 found no need to relax the threshold.
- [X] T023 [US1] `PR/session/FollowSession.java` (Follow and Arcade):
  - one `ChordProfile` per chord candidate, made with the `PendingOnset` in `registerOnset`;
  - summed on each usable frame and checked through `ChordMatcher.check(profile, …)` in the chord loop (lines 939–960);
  - the wrong attempt carries the weakest names' pitches;
  - the attack path for chords follows `CHORD_ATTACK`, with the weak-name guard of research R6 while on (the profile of its own frame when no strum is armed);
  - nothing is allocated per frame.
- [X] T024 [US1] Strings for the new values and the new option, in `LANG/messages.properties`, `_fr` and `_es`: name, tip and option label of each value, shown by the existing Recognition settings window (`RecognitionControls`). The completeness name becomes "Chord rule" ("Règle des accords", "Regla de acordes"), with the options "Note names over the strum" and "Every string separately" (research R7); "Chord check" stays the name of the level in the *Mode* tab only. The tips of `CHORD_EVIDENCE` and `CHORD_MIN_PARTIALS` add that they apply to the rule "Every string separately" only. `CHORD_ATTACK` is named "Accept a chord on its attack" ("Accepter un accord sur son attaque", "Aceptar un acorde por su ataque"). The name and tip of the new section `Section.CHORD_DETAILS` ("Chord details", "Détails des accords", "Detalles de acordes").
- [X] T025 [US1] Run T009–T016: all green, with both engines for T016.
- [X] T026 [US1] `PT/dataset/owner-chords-baseline.properties`: change only the two `wrong.max` lines to the counts `OwnerChordsBenchmarkTest` now gives on the first two series, with a comment that cites the approved exception and says that this count includes voicings and is no longer a guard (the counts of the reference series are checked by T016). This is approved exception 1; `accepted.min` stays 8 and 18. Run `OwnerChordsBenchmarkTest` with each engine: green, at least 8 and 18 accepted (FR-003a).
- [X] T027 [US1] The guards after (research R8; FR-003, FR-004, FR-011, SC-003): run the five committed dataset benchmarks with each engine into `SPEC/benchmark/after-java/` and `after-native/`. Then write the new test `PT/dataset/ChordLeniencyComparisonTest.java` (tag `benchmark`): it reads `before-*` and `after-*`, writes `SPEC/benchmark/comparison.md` (each guard line, before and after, both engines, pass or fail) and fails on any guard line lower, except the two lines SC-003 allows lower, which fail only under their floor: incomplete chords refused at least 20 of 44, IDMT chords recognised at least 85 %; the minor triad on G2 is reported as a known limit (research R8). Before it, change the `DatasetBenchmarkTest` floor 0.90 to 0.45 (approved exception, a comment citing spec 024) and run `DatasetBenchmarkTest` with each engine: green. Run the comparison.
  - **Stop condition C** applies if a guard drops and no configuration of `probe.md` avoids it.

  *Stopped 2026-11-05 (stop condition C; the committed `DatasetBenchmarkTest` fails, "incomplete chord null: 20/44 < 0.9"). With both engines, identical figures (`benchmark/after-{java,native}/`): the single-note and wrong-note lines and the chord a whole tone higher (100 %) are unchanged, GuitarSet complete chords rise (33 to 40 summed, 23 to 25 microphone), techniques and bass are unchanged, the owner's reference series gives 25/25 and 0/408, the first two series 8 and 18. But "incomplete chord refused" falls (Audio-Effects poly 35 to 16 of 36, IDMT-SMT-Guitar 8 to 4 of 8): one name missing of three is one weak name and a match of about 0.82, accepted by the rule as designed ("one name off", Clarifications). And Audio-Effects poly "minor triad" falls from 6 to 0 of 6 (cause not yet diagnosed). No probe configuration was measured on IDMT. Waiting for the owner.*

  *Resumed 2026-11-05: the minor triad drop is one low recording (research R8); the probe of `benchmark/stop-t027/` (both engines identical) found no variant that keeps 25/25 and refuses 90 % of the incomplete chords; the owner chose option B (spec Clarifications): the profile rule stays, the incomplete-chord floor becomes 0.45. The probe's temporary code was removed; its reports and `IdmtTriadProfileDiagnosticTest` (tag `dataset`, a report) are kept.*
  - The bass report judges single notes only and must stay unchanged (research R8); a change there is a bug of this phase, handled under the bug protocol.
- [X] T028 [US1] Gate; commit "impl(024): phase 4 (US1): the pitch-class profile chord rule as the default".

  *Gate 2026-11-05 22:43: 1,507 practice tests, 4 failures. Fixed under the bug protocol (first attempt): `RecordedPracticeReplayTest` (7 dyads missed: a strum armed whose profile summed nothing, the analysis having no spectrum, refused the attack; now judged as a frame with no spectrum, with new tests in both sessions) and `RecognitionControlsTest` (the help pages in three languages had no rows for the new settings and section). Stopped: `SyntheticChordGuardTest` (chords with a string missing accepted 77 of 78, baseline at most 43) and `FollowDeterminismTest` line 230 (E major without G♯ accepted at Follow's Strict) fail by the rule the owner chose (one name off may be accepted; a voicing is never wrong); not among the approved exceptions. Waiting for the owner.*

  *Resumed 2026-11-05: the owner approved both as exceptions (spec Clarifications, `d5a96c696`; listed above and in research R7); applied with a comment citing spec 024; the gate runs again.*

  *Gate again 2026-11-05 23:24: build success, 1,509 practice tests, 0 failures (12 skipped, as before).*

## Phase 5: User Story 2 - I set how strict the chord check is (P2)

*Order changed at the owner's request (2026-11-05 23:15: the owner is away overnight and cannot unlock the Mac): the native SWT runs (`-P swt-gui-tests`) of T036, T037 and T043 are grouped at the end, run once on the whole branch when the owner is back, before the push of T044. Overnight, the gates run without that profile; T032 records `ChordLeniencyPanelSwtTest` red by its compilation (the panel does not exist yet). Nothing is pushed before the SWT runs are green.*

**Goal**: three levels in the *Mode* tab, derived from the chord settings; a change by hand shows *Custom* (FR-005 to FR-008, FR-012).

**Independent Test**: quickstart §5 and §7.

### Tests for User Story 2 (first, seen red)

- [X] T029 [P] [US2] New test `AET/settings/ChordLeniencyTest.java`, for the invariants of data-model "Chord leniency level":
  - `of(defaults()) == NORMAL`;
  - `of(apply(L, s)) == L`;
  - `apply` changes no other value;
  - `CUSTOM` cannot be applied;
  - a value changed by hand gives `CUSTOM`.
- [X] T030 [P] [US2] New test `PT/dataset/ChordLeniencyLevelsTest.java` (tag `dataset`; FR-007, SC-004). On the reference series with each level's values, with each engine, it checks:
  - every level accepts 0 clearly wrong;
  - *Lenient* accepts at least *Normal*'s right chords;
  - *Strict* accepts at most *Normal*'s wrong acceptances (SC-004).

  It writes `target/dataset-benchmark/chord-leniency-levels-<engine>.txt`.
  It also reports, without asserting, the IDMT incomplete chords accepted by each level (expected one note more than played, as `DatasetBenchmarkTest` builds them, 44 cases), so the spec's edge case "*Strict* refuses it more often" is measured; T036 writes the figures in `levels.md`. If *Strict* does not refuse more of them than *Normal*, the edge case is corrected through `/speckit-clarify` (constitution XII).
- [X] T031 [P] [US2] New SWT test `PT/ui/ChordLeniencyPanelSwtTest.java` (`swt-gui-tests`):
  - the *Mode* tab shows "Chord check" after `FollowPanel`, with *Strict*, *Normal* and *Lenient*;
  - choosing a level writes its values into the store's default layer;
  - a value changed in the store shows *Custom* and its line;
  - choosing a level again removes *Custom*;
  - the control stays enabled during a run.
- [X] T032 [US2] Run T029–T031: red for the right reason. Record it in `SPEC/benchmark/red-us2.txt`.

### Implementation for User Story 2

- [X] T033 [US2] `AE/settings/ChordLeniency.java` (made by T020 with the history): add the levels, their values from T007, `of`, `apply` and `values` (data-model "Chord leniency level").
- [X] T034 [US2] New `PR/ui/ChordLeniencyPanel.java` (contracts/chord-leniency.md): a label, a `UIDropDownSelect` and a wrapped line, listening to the store. Add it to the *Mode* tab in `PR/ui/PracticeWindow.java`, on the row after `FollowPanel`.
- [X] T035 [US2] Strings: `practice.chord-leniency`, `practice.chord-leniency.{strict,normal,lenient,custom}` and `….line`, in three languages; the French names are *Strict*, *Normal*, *Tolérant* and *Personnalisé* (label *Vérification des accords*), the Spanish *Estricto*, *Normal*, *Tolerante* and *Personalizado* (label *Comprobación de acordes*), the English *Strict*, *Normal*, *Lenient* and *Custom* (label *Chord check*), as the guide, sheet F15 and help pages use them. Also add to the `.tip` of the level's values in the Recognition settings window: "Choosing a chord check level in the practice window sets this value; changing it here makes the level Custom".
- [X] T036 [US2] Run T029–T031 with each engine for T030, and `-P swt-gui-tests` with the Mac session unlocked: green. Write `SPEC/benchmark/levels.md` from T030's reports.
  *Done 2026-11-05 without the SWT run (Phase 5 note): T029 green (7 tests), T030 green with each engine, identical figures (`benchmark/levels.md`): 0 of 408 clearly wrong at every level, Strict 23, Normal 25, Lenient 25 of 25 right; one name off 0, 6 and 69 of 138. Incomplete chords of the datasets: Strict 24, Normal 24, Lenient 32 of 44 accepted, so the edge case "Strict refuses it more often" is not met: to be corrected through `/speckit-clarify` by the owner. `ChordLeniencyPanelSourceTest` green; `ChordLeniencyPanelSwtTest` waits for the owner.*
- [X] T037 [US2] Gate with `swt-gui-tests`; commit "impl(024): phase 5 (US2): chord leniency levels in the Mode tab".
  *Gate 2026-11-06 00:24 without `-P swt-gui-tests` (Phase 5 note): build success, 1,512 practice tests and 519 engine tests, 0 failures. The SWT run waits for the owner.*

## Phase 6: Polish & cross-cutting concerns

- [X] T038 A new test `AET/settings/EngineDocumentationTest.java` checks that every key of `RecognitionParameters` appears in `docs/architecture/recognition-engines.md`, so the documentation stays complete (FR-014). Run it: red, since the document does not exist yet.
  *Order kept as far as possible (2026-11-05): T039 was written first, in parallel, by an agent. To see T038 red for the right reason, run it once with `docs/architecture/recognition-engines.md` set aside in the scratchpad (absent document), record it in `SPEC/benchmark/red-docs.txt`, put the document back and run it again: green.*
- [X] T039 New `docs/architecture/recognition-engines.md` (FR-014, research R10), linked from `docs/architecture/README.md`. It covers:
  1. how each engine works, from the frame to the session's decision;
  2. their interface: the frame record, `frame_layout.txt`, the JNI functions of `jni_recognition.rs`, `NativeParameters`;
  3. every parameter of `RecognitionParameters` and `NativeParameters`: key, meaning, unit, default, bounds, where it is set;
  4. the constants still fixed, by file, marked as spec 026's;
  5. the rule for later updates: a change of a level's values or of the defaults adds the old set to the `ChordLeniency` history (research R7).

  Then run T038: green.
- [X] T040 [P] The practice guide in three languages: the *Chord check* levels, and what a refused chord's message means now.
- [X] T041 [P] New sheet `specs/test-sheets/F15-accords-tolerants.md` (SC-005): chords of a score through the cable in Wait, Follow and Arcade with *Normal*, then one chord with each level, then one threshold changed in Recognition settings and seen at the next run (FR-013), and one chord played right after a held note (tied or let ring), accepted whether the held note still rings or has faded.
- [X] T042 Draft the release notes (macOS, Linux and Windows alike).
  *T039–T042 were written in parallel on 2026-11-05 by agents (each on its own files, no build, no commit); they are checked off after review in this phase, T039 after T038 is seen red then green.*
  *Reviewed 2026-11-06: T038 seen red with the document set aside, then green (`benchmark/red-docs.txt`). Corrected in review: the guide in three languages and the release notes said that a level sets every chord setting (it sets three: the rule, the match, the attack); the release notes said Strict refuses incomplete chords more often (measured: no, `benchmark/levels.md`); `recognition-engines.md` §4.10 still described phase 4; sheet F15 step 1 asked to hover the levels (the line under the list shows the chosen level's).*
- [X] T043 Full gate: `clean verify -P native-modules`, then `-P swt-gui-tests`, and the dataset tests of this spec with each engine.
  *Done 2026-11-06 01:00 without `-P swt-gui-tests` (Phase 5 note): build success, 1,512 practice and 520 engine tests, 0 failures; `OwnerChordsProfileTest`, `ChordLeniencyLevelsTest` and `DatasetBenchmarkTest` green with each engine. The SWT run waits for the owner.*
- [X] T044 Write `SPEC/final-report.md` (each FR and SC with its figure and evidence, both engines, the stop conditions met or not) and update `specs/STATUS.md`. Commit "impl(024): phase 6: documentation, sheet F15, final report"; push both refs in one push.
  *Done 2026-11-06 without the push: the owner asked for nothing to be pushed before the SWT runs, done on the owner's return; the push of both refs follows them.*
- [ ] T045 [HUMAN] The owner's gate: sheet F15 on the built application (SC-005).

## Dependencies & execution order

- **Phases 1–2:** done.
- **Phase 3** (probe) blocks everything after it: its values are *Normal*, *Strict* and *Lenient*. Stop condition A is checked at T007.
- **Phase 4 (US1)** depends on T007. Its tests T009–T016 can be written in parallel ([P]); T017 runs them red; T018–T024 follow in order (shared files); then T025–T028.
- **Phase 5 (US2)** depends on phase 4 (the values and the rule). T029–T031 in parallel, then T032–T037.
- **Phase 6:** T038 (red) then T039 (T039 written first by an agent on 2026-11-05: T038 is seen red with the document set aside); T040 and T041 in parallel with them; then T042–T044; T045 last, by the owner.

## Parallel example (US1)

```text
T009 ChordProfileTest   T010 ChordMatcherProfileTest   T011 PracticeSessionChordProfileTest   T012 FollowSessionChordProfileTest
T013 ChordProfileSettingsTest   T014 ChordProfileMigrationTest   T015 ChordProfileParametersTest   T016 OwnerChordsProfileTest
```

## Implementation strategy

- **MVP:** phases 3 and 4. The default rule accepts every right chord of the reference series and refuses every clearly wrong one, with both engines and its values external.
- **Then:** phase 5 (the levels and the setting) and phase 6 (documentation, sheet, report).
- **Each phase** ends green and committed; one push per round.
