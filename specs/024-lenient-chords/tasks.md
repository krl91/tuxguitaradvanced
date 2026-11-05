# Tasks: the owner's chords accepted at least 9 times in 10, with a chord leniency the learner can set

**Input**: design documents from `specs/024-lenient-chords/`

**Prerequisites**: plan.md, spec.md, research.md (R0–R7), data-model.md, contracts/chord-leniency.md, quickstart.md

**Tests**: required. The owner's rules apply:
- tests come first and must be seen red;
- never modify an existing test, only add new ones;
- if a committed test fails, fix the code under the bug protocol, at most 3 attempts, then stop and report.

**Stop conditions (research R2, R3, R4)**: stop and report to the owner, with the figures, when:
- *Normal* needs a different default for `CHORD_EVIDENCE` or `CHORD_CONFIRM` (committed tests pin them);
- a lever needs evidence the engine does not carry (a JNI change);
- no configuration meets at least 38 accepted and at most 13 wrong (per series within the committed baseline) on both engines.

**Paths**:
- `AE` = `desktop/TuxGuitar-audio-engine/src/main/java/app/tuxguitar/audio/engine`
- `AET` = `desktop/TuxGuitar-audio-engine/src/test/java/app/tuxguitar/audio/engine`
- `PR` = `desktop/TuxGuitar-practice/src/app/tuxguitar/app/tools/custom/practice`
- `PT` = `desktop/TuxGuitar-practice/test/app/tuxguitar/app/tools/custom/practice`
- `LANG` = `desktop/TuxGuitar-practice/share/lang`
- `BUILD` = `desktop/build-scripts/tuxguitar-macosx-swt-cocoa`
- `SPEC` = `specs/024-lenient-chords`
- `LIB` = `desktop/build-scripts/native-modules/tuxguitar-monitor-native/target`, the native library as built (spec 023 quickstart §3)

**Standing rules for every phase**:
- **Build:**
  - JDK 21 pinned.
  - After each Maven build, run `git checkout -- specs/003-arcade-mode/benchmark/`.
  - Never run two builds at once.
- **Benchmark engines:** each benchmark runs with `-Dtuxguitar.recognition.engine=java` and `=native`.
- **Gate:**
  - `mvn -o -e clean verify -P native-modules` from `BUILD` is green;
  - plus `-P swt-gui-tests` when a window changed;
  - plus `git diff --name-status <phase start> -- '*test*'` shows only added files.
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

## Phase 3: User Story 1 - The chords I play are accepted (P1)

**Goal**: with the defaults (*Normal*), at least 38 of 42 accepted and at most 13 wrong, with both engines, and no guard line lower (FR-001 to FR-004, FR-006, FR-009, FR-011).

**Independent Test**: quickstart §3 and §4; `OwnerChordsBenchmarkTest` green on both engines; `SPEC/benchmark/comparison.md` with every guard line passing.

Implement only the levers T004 kept, in the order of the refusals they recover. A lever not kept: mark its tasks "not needed (diagnostic)".

- [ ] T006 [P] [US1] Test first, `PT/session/ChordMatcherCompletenessTest.java`, for lever L1:
  - with `all-but-one`, a chord of 3 or more verifiable pitches is complete when one verifiable pitch is missing but has at least one clear unique partial;
  - it is not complete when that pitch has none, or when two are missing;
  - with 2 verifiable pitches, nothing changes;
  - unverifiable pitches are classified exactly as with `all-verifiable`;
  - the old constructor of `ChordMatcher.Parameters` keeps `all-verifiable`.

  Seen red.
- [ ] T007 [US1] `PR/session/ChordMatcher.java`: `Parameters` gets the completeness, with a new constructor, the old one kept. `Result` knows the completeness and the clear partials of a missing pitch. `isComplete` applies L1. T006 green, and the committed chord tests green unchanged.
- [ ] T008 [P] [US1] Test first, `PT/session/ClippedChordFrameTest.java`, for lever L4 if kept:
  - with the flag set, `onChordFrame` judges a `CLIPPING` frame and a clipped strum of the chord is accepted;
  - with the flag off, it is refused as today;
  - the input warnings and the frame's level state are unchanged.

  Seen red.
- [ ] T009 [US1] `PR/session/PracticeSession.java` (`onChordFrame`, usable level), for lever L4 if kept: a clipped frame is judged when the parameters say so. The attack path is unchanged. T008 green.
- [ ] T010 [US1] `AE/settings/RecognitionParameters.java`:
  - option `all-but-one` of `CHORD_COMPLETENESS`;
  - the L4 flag `CHORD_CLIPPED` (key `recognition.chord.clipped`, `Section.CHORDS`) if kept;
  - the option names in `LANG`;
  - `PR/session/PracticeParameters.java` `chordParameters()` passes them on.

  The defaults are not changed yet. Test first in `PT/session/PracticeParametersChordCompletenessTest.java`, seen red, then green. The same test also covers Follow and Arcade (analysis C1, FR-009): settings relaxed by `FollowLeniency.apply` and by `ArcadeLeniency` still carry the completeness and the flag into `chordParameters()`.
- [ ] T011 [US1] New test `PT/dataset/ChordLeniencyBenchmarkTest.java` (tag `dataset`):
  - the judge of T003, with given settings;
  - it sweeps the kept levers' options and `CHORD_EVIDENCE` from 0.20 to 0.50 in steps of 0.05 (and `CHORD_CONFIRM` 30, 43 and 60 ms);
  - it gives the accepted and wrong figures per series and in total;
  - it writes `target/dataset-benchmark/chord-leniency-<engine>.txt`.

  Run it with both engines. Choose *Normal* by the rule of research R4:
  - `CHORD_EVIDENCE` and `CHORD_CONFIRM` stay at their defaults;
  - at least 38 accepted;
  - at most 13 wrong, and per series at most 2 and 11 with at least 8 and 18 accepted;
  - the fewest wrong.

  Write the sweep and the choice in `SPEC/benchmark/levels.md`. **Stop condition** if no configuration meets it, or if it needs another evidence or confirmation default.
- [ ] T012 [US1] Test first, `AET/settings/ChordLeniencyMigrationTest.java`:
  - a file holding the spec 023 chord values (`all-verifiable`, 1/3, 43 ms, no `recognition.chord.clipped`) is read with *Normal*'s completeness and flag;
  - a file with a chord value changed by hand keeps every value it holds;
  - a file written with *Normal* reads back unchanged;
  - the version stays 1.

  Seen red.
- [ ] T013 [US1] Make *Normal* the defaults:
  - `CHORD_COMPLETENESS`'s default (and `CHORD_CLIPPED`'s) become *Normal*'s values in `AE/settings/RecognitionParameters.java`;
  - the migration goes in `AE/settings/RecognitionSettings.fromStrings` (next to the legacy tempo key);
  - T012 green, and `RecognitionSettingsTest`, `RecognitionSettingsStoreTest`, `RecognitionSettingsStoreProfileTest` and `RecognitionControlsTest` green unchanged.
- [ ] T014 [US1] Run the five dataset benchmarks with each engine into `SPEC/benchmark/after-java/` and `after-native/`. `OwnerChordsBenchmarkTest` must be green on both, with the committed baseline. Its "BETTER than its baseline" line is reported, and the baseline file is not edited.
- [ ] T015 [US1] New test `PT/dataset/ChordLeniencyComparisonTest.java` (tag `benchmark`). It reads `SPEC/benchmark/before-*` and `after-*`, and writes `SPEC/benchmark/comparison.md`:
  - each line before, Java after, native after, pass or fail;
  - the owner's 42: at least 38 and at most 13;
  - every single-note and wrong-note line not lower;
  - Audio-Effects whole tone higher refused at least 85 %;
  - GuitarSet complete chords, summed and by microphone, not lower;
  - the bass report unchanged;
  - the incomplete-chord lines reported.

  It fails on any line that fails. Run it: green.
- [ ] T016 [US1] Gate; commit "impl(024): phase 3 (US1)".

## Phase 4: User Story 2 - I set how strict the chord check is (P2)

**Goal**: *Strict*, *Normal* and *Lenient* in the *Mode* tab, derived from the chord settings, *Custom* after a change by hand, kept across restarts (FR-005, FR-007, FR-008, FR-012).

**Independent Test**: quickstart §3 and §5; `ChordLeniencyTest`, `ChordLeniencyPanelSwtTest`.

- [ ] T017 [US2] From the sweep of T011 (both engines), choose *Strict* and *Lenient* by research R4:
  - *Strict*: fewer than 13 wrong and at most *Normal*'s, with the most accepted;
  - *Lenient*: at least *Normal*'s accepted, more if any configuration gives more, with the fewest wrong.

  Record them in `SPEC/benchmark/levels.md`.
- [ ] T018 [P] [US2] Test first, `AET/settings/ChordLeniencyTest.java` (data-model invariants):
  - `of(defaults()) == NORMAL`;
  - `of(apply(L, s)) == L` for each named level;
  - `apply` changes only the level's values, never `CHORD_WINDOW` nor `CHORD_MIN_PARTIALS`;
  - one value changed by hand gives `CUSTOM`;
  - *Strict* is not more lenient than *Normal* on any value, and *Lenient* not stricter.

  Seen red.
- [ ] T019 [US2] `AE/settings/ChordLeniency.java` (new, pure): `Level { STRICT, NORMAL, LENIENT, CUSTOM }`, `of`, `apply`, `values`. T018 green.
- [ ] T020 [P] [US2] Test, `PT/dataset/ChordLeniencyOrderTest.java` (tag `dataset`), with each engine on the owner's takes:
  - *Lenient* accepted ≥ *Normal* accepted;
  - *Strict* wrong ≤ *Normal* wrong;
  - *Strict* wrong < 13 (SC-004, FR-007).

  It runs with `ChordLeniency.apply` and is green.
- [ ] T021 [P] [US2] Test first, `PT/ui/ChordLeniencyPanelSwtTest.java` (`swt-gui-tests`), on a store with memory persistence:
  - the drop-down lists the three levels and shows *Normal* at first;
  - choosing *Strict* writes its values into the store's default layer and shows its line;
  - a chord value changed in the store shows *Custom* (added to the list) and its line;
  - choosing *Lenient* again removes *Custom*;
  - the values survive a new store on the same persistence.

  Seen red.
- [ ] T022 [US2] `PR/ui/ChordLeniencyPanel.java` (new), per contracts/chord-leniency.md:
  - the label, the drop-down, and the wrapped line of the chosen level;
  - it listens to `RecognitionSettingsStore`;
  - it is removed from the store's listeners on dispose.

  T021 green.
- [ ] T023 [US2] `PR/ui/PracticeWindow.java`: the panel in the *Mode* tab, on the row after `FollowPanel`. It stays enabled during a run, and the change applies from the next run. Committed `PracticeWindow` source tests green unchanged.
- [ ] T024 [US2] Texts in `LANG` (English, French, Spanish):
  - `practice.chord-leniency`, the level names (French *Strict*, *Normal*, *Tolérant*), and each level's line saying what it accepts and what it risks;
  - the *Custom* line;
  - the expert tips of `recognition.chord.evidence`, `.confirm` and `.completeness`, which say that a level sets them and that a change by hand makes it *Custom*.

  Test in `PT/ui/ChordLeniencyTextsTest.java`: every key exists and is non-empty in the three files.
- [ ] T025 [US2] Gate with `-P swt-gui-tests` (Mac session unlocked); commit "impl(024): phase 4 (US2)".

## Phase 5: Polish

- [ ] T026 [P] `docs/PRACTICE.md`, `PRACTICE-FR.md` and `PRACTICE-ES.md`:
  - the chord check setting in the *Mode* tab, with the three levels and *Custom*;
  - the link with the expert chord settings;
  - that the same chord in another voicing (an octave, the same notes) cannot be told apart by ear or by audio.
- [ ] T027 [P] `specs/test-sheets/F15-accords-tolerants.md`, with its row in `specs/test-sheets/README.md`:
  - chords of a score through the cable, in Wait, Follow and Arcade, with *Normal*;
  - a wrong chord refused;
  - each level tried on one chord;
  - *Custom* after a change in Recognition settings;
  - the level kept after a restart;
  - X1 for Linux and Windows.
- [ ] T028 [P] `docs/release-notes/next-chords.md` (draft): the figures before and after, both engines.
- [ ] T029 Full gate (full build, `swt-gui-tests`, test diff only added files). Append the counts to `SPEC/baseline.txt`.
- [ ] T030 Write `SPEC/final-report.md`:
  - the FR/SC table with figures;
  - the diagnostic's summary;
  - the levers kept and dropped;
  - the deviations;
  - what waits for the owner.

  Update `specs/STATUS.md`. Commit "impl(024): phase 5".
- [ ] T031 [HUMAN] The owner's gate: sheet F15 on the relaunched app (SC-005).

## Dependencies

- Phase 1 → Phase 2 (US3) → Phase 3 (US1) → Phase 4 (US2) → Phase 5.
- US3 comes first despite its priority: the spec makes the diagnostic a prerequisite of any rule change (FR-010).
- US2 depends on the sweep of US1 (T011) and on the defaults being *Normal* (T013).
- Within a phase, a test task comes before the code that makes it green. [P] tasks touch different files.

## Parallel opportunities

- T006 and T008 (different test files).
- T018, T020 and T021.
- T026, T027 and T028.

## Implementation strategy

- **MVP:** phases 1 to 3. The defaults accept at least 38 of the owner's 42 chords with no guard lower.
- **Then:** phase 4 gives the learner the setting.
- **Stops:** each stop condition halts the run with a report to the owner. No target or committed test is changed to get past it.
