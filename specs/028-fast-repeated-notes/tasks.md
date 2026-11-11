# Tasks: fast repeated notes recognised, early or late, and a suggestion to retune

**Input**: design documents from `specs/028-fast-repeated-notes/`

**Prerequisites**: plan.md, spec.md (clarified 2026-11-11: 5 answers, then 5 more after /speckit-analyze), research.md (R0–R7), data-model.md, contracts/reattack-rule.md, contracts/retune-suggestion.md, contracts/owner-offset-test.md, quickstart.md

**Version 2** (2026-11-11, after /speckit-analyze): Wait mode added, the settings land with their documentation, help and strings, SWT at the end, the release workflow checks the table, the Arcade text and level wait for the owner's answers.

**Version 3** (2026-11-11, after the second /speckit-clarify): Arcade at every level 1 to 5; in Arcade the suggestion pauses the run on a window (Yes: the tuner, then resume; No; More tolerant: +10 % for this run, capped at 45 cents); a tenth setting `tuning.arcade-boost`.

**Tests**: required. The owner's rules apply:
- tests come first and must be seen red;
- never modify an existing test, only add new ones;
- if a committed test fails, fix the code under the bug protocol, at most 3 attempts, then stop and report.

**Stop conditions**: stop and report to the owner, with the figures, when:
- **A (research R3)**: no setting of the probe gives 100/100 from −70 to +70 ms on the DI recording (Follow, Arcade at each level 1 to 5; Wait at offset 0), with both engines, without a new accept on the guard cases;
- **B**: a guard figure (research R6) is lower than its `before` value, and no setting of the sweep avoids it;
- **C**: FR-007a (lean towards accepting) and a committed refusal test disagree;
- **D**: the rule needs evidence the frames do not carry (a JNI or native change).

**Paths**:
- `AE` = `desktop/TuxGuitar-audio-engine/src/main/java/app/tuxguitar/audio/engine`
- `AET` = `desktop/TuxGuitar-audio-engine/src/test/java/app/tuxguitar/audio/engine`
- `PR` = `desktop/TuxGuitar-practice/src/app/tuxguitar/app/tools/custom/practice`
- `PT` = `desktop/TuxGuitar-practice/test/app/tuxguitar/app/tools/custom/practice`
- `LANG` = `desktop/TuxGuitar-practice/share/lang`
- `HELP` = `desktop/TuxGuitar/share/help/tools_audio_recognition.html` and `tools_audio_recognition_fr.html`
- `DOC` = `docs/architecture/recognition-engines.md`
- `BUILD` = `desktop/build-scripts/tuxguitar-macosx-swt-cocoa`
- `SPEC` = `specs/028-fast-repeated-notes`
- `LIB` = `desktop/build-scripts/native-modules/tuxguitar-monitor-native/target`, the native library as built
- `DI` = `tests/tiktok/1` (the owner's DI recording `extrait_offspring_guitare_DI.wav` and `tablature_extrait.gp5`; never committed)

**Standing rules for every phase**:
- **Build:**
  - JDK 21 pinned, `caffeinate -i`.
  - After each Maven build, run `git checkout -- specs/003-arcade-mode/benchmark/ specs/023-native-recognition-engine/benchmark/recognition-delay.md`.
  - Never run two builds at once, and never edit sources while a build runs.
- **Engines:** each dataset run is done with `-Dtuxguitar.recognition.engine=java` and with `=native` (`-Dtuxguitar.native.library.dir=$LIB`).
- **Gate:**
  - `mvn -o -e clean verify -P native-modules` from `BUILD` is green;
  - the SWT tests (`-P swt-gui-tests`) run once, at T044, with the Mac unlocked (the owner's rule);
  - `git diff --name-status <phase start> -- '*test*'` shows only added files.
- **Committed tests that constrain the settings** (research R0) stay green from the phase that adds a setting: `RecognitionSectionsTest`, `EngineDocumentationTest`, `EngineDocumentationFactsTest`, `RecognitionControlsTest`, `RecognitionStringsAllLanguagesTest`, `RecognitionSettingsPanelTest`, `SessionConstantInventoryTest`.
- **Real time:** nothing is allocated per frame in the sessions' re-attack handling.
- **Parameters:** no engine constant hard-coded; every new value is a `RecognitionParameters` entry. There is no numeric `static final` in new code under `PR/session` or `PR/arcade/core`.
- **Strings:** every new string goes in `LANG/messages.properties`, `_fr` and `_es`, translated.
- **Commits and pushes:**
  - commit each phase without any co-author trailer;
  - stage explicit paths, never `tests/`, `.specify/feature.json` or the untracked `OffsetReplayProbeTest.java`;
  - one push per round, both refs together.

## Format: `[ID] [P?] [Story] Description`

## Phase 1: Setup (the figures before)

- [ ] T001 Write `SPEC/baseline.txt`:
  - HEAD;
  - the counts of the full gate and of `swt-gui-tests`;
  - the committed tests that read what this spec touches:
    - the settings: `OnsetParametersTest`, `RecognitionSettingsTest`, `RecognitionSettingsStoreTest`, `RecognitionSectionsTest`, `EngineDocumentationTest`, `EngineDocumentationFactsTest`, `RecognitionControlsTest`, `RecognitionStringsAllLanguagesTest`, `RecognitionSettingsPanelTest`, `RecognitionSettingsSwtTest`, `SessionConstantInventoryTest`, `FollowLeniencyTest`, `ArcadeLeniencyTest`;
    - the sessions and Arcade: `FastRunFunctionalTest`, `FastPassageBenchmarkTest`, `FollowDeterminismTest`, the `FollowSession` and `PracticeSession` tests, `ArcadeRunFunctionalTest`, `ArcadeResultTextTest`, `SceneFixture` and the Arcade painting tests.
- [ ] T002 Run the committed dataset and benchmark suites (`-Dgroups=dataset,benchmark -DexcludedGroups=`) with each engine at the current code (quickstart §3). Copy `desktop/TuxGuitar-practice/target/dataset-benchmark/`, the other reports and the `REPEATED` table printed by `FastRunFunctionalTest` into `SPEC/benchmark/before-java/` and `SPEC/benchmark/before-native/`.
- [ ] T003 Extend the untracked `PT/session/OffsetReplayProbeTest.java`:
  - to Wait (offset 0);
  - to Arcade, at every level 1 to 5 (the `ArcadeStarter` defaults, leniency `ArcadeLeniencyPosition.DEFAULT`);
  - to the offsets −100, −70, −50, −40, −30, −20 ms.

  Write `SPEC/benchmark/before-offset.md` on `DI`, Java and native, from −100 to +100 ms. These are the missing SC-001 figures (Arcade, Wait and early).

## Phase 2: Foundational (the probe, gate of the method, and the settings)

**Purpose**: confirm the cause (R1, R4) and set the values (R3) before any rule changes, then add the settings with everything the committed tests require. Stop condition A applies at T006.

- [ ] T004 New test `PT/dataset/RepeatedNotesProbeTest.java` (tag `dataset`, skipped without `-Dtuxguitar.owner.offset`), from the probe's alignment and run code. For each event of `DI` missed at offsets 0, −70 and +70 (Follow, Arcade, Wait), it records:
  - the overall level rise over the max and over the min of the hops before;
  - the flux;
  - the rise of the expected pitch's band and of its octave (`SpectrumFrame.bandDb`);
  - the share of bands rising in the same frame;
  - the event each attack went to.

  The results go to `SPEC/benchmark/probe.md`.
- [ ] T005 In `RepeatedNotesProbeTest`, compute an offline re-attack (contracts/reattack-rule.md, items 1–6a) in the test, on the frames of each engine. Sweep:
  - `rise` 1.5–6 dB by 0.5;
  - `before` 4, 6, 8, 12 and 19 hops;
  - `spread` 0.4–0.8 by 0.1;
  - `octave` on and off;
  - the nearest-target assignment on and off.

  For each setting, report:
  - the recognised count on `DI` (Wait at 0; Follow and Arcade at every level and every offset from −100 to +100 ms; Java and native);
  - the extra accepts on the guard cases: the `FastPassageBenchmarkTest` takes, the sustained single notes of `DatasetBenchmarkTest`, a synthetic held note, and a synthetic scratch (broadband burst) over a ringing note.

  Append the results to `SPEC/benchmark/probe.md`.
- [ ] T006 Read `SPEC/benchmark/probe.md`:
  - check R1 and R4;
  - choose the defaults: the least sensitive setting that gives 100/100 from −70 to +70 ms where the spec requires it, with no extra accept;
  - write the decision and its figures at the end of `SPEC/research.md` ("R3 closing");
  - if R1 or R4 does not hold, revise the plan section concerned before any code.

  **Stop condition A** if no setting qualifies.
- [ ] T007 [P] New test `AET/settings/ReattackSettingsTest.java`, seen red. It checks:
  - the ten keys of data-model.md, with their unit, type (`Number`, or `Flag` for `reattack.octave` and `reattack.enabled`), default (from T006; `tuning.drift-threshold` 20) and bounds, quoted: `reattack.rise` "1.0–10.0" dB, `reattack.before` "2–19" hops, `reattack.spread` "0.3–1.0", `tuning.drift-threshold` "10–35" cents, `tuning.drift-notes` "3–20", `tuning.drift-agreement` "0.5–1.0", `tuning.drift-quiet` "4–100", `tuning.arcade-boost` "0.05–0.25" (default 0.10; the `%` keys are shares shown ×100, display factor 100.0 as `NOTE_CONFIDENCE`);
  - `Section.REATTACK` holds the five reattack keys and `Section.TUNING_DRIFT` the five drift keys, the threshold first; both come after the existing sections;
  - `ProfileParameters` contains none of them;
  - the round trip through `RecognitionSettingsStore`;
  - a file without the keys reads the defaults.
- [ ] T008 Add the settings so that T007 is green and the committed tests of the standing rules stay green, all in one change:
  - `Section.REATTACK` and `Section.TUNING_DRIFT` at the end of `AE/settings/RecognitionParameter.java`;
  - the ten entries in `AE/settings/RecognitionParameters.java`, registered in its list;
  - their rows in `DOC`, under a heading per new section naming `` `Section.REATTACK` `` and `` `Section.TUNING_DRIFT` ``, with unit, default, bounds and "next run";
  - a row per setting and an `<h2>` per new section in both `HELP` pages;
  - names, tips and section names in `LANG`, in three languages, translated;
  - the values read by `FollowSession.FollowParameters` and `PR/session/PracticeParameters.java`.
- [ ] T009 Gate, and commit "impl(028): phase 1-2: figures before, probe, settings".

## Phase 3: User Story 1 - Every note of a fast riff counts (P1) 🎯 MVP

**Goal**: a note played again with its own attack is recognised in Wait, Follow and Arcade; a held note or a scratch is not counted again (FR-001, FR-001a, FR-002, FR-008, SC-004, SC-005).

**Independent Test**: the new unit tests green; `FastRunFunctionalTest` keeps its floors; `DI` gives 100/100 on time in Follow and Arcade and in Wait, with both engines.

- [ ] T010 [P] [US1] New test `PT/session/FollowSessionReattackTest.java`, seen red. It builds synthetic frames with `SpectrumFrame.of`, the band levels and the pitch given per frame (as `FollowSessionChordProfileTest` does). Cases:
  - two plucks of the same pitch 100 ms apart, both expected: two matches (SC-005);
  - the same at 150 ms: two matches;
  - one pluck held over two expected events of that pitch: one match, the second missed with `NO_ATTACK` (SC-004, FR-002, FR-008);
  - a broadband burst (scratch) over a ringing note expected again: no re-attack (contract item 6a);
  - a wrong pitch at the time of an expected repeat: `WRONG_PITCH`, as before;
  - a repeated dyad: judged by the chord rule (contract item 3);
  - `reattack.enabled` off: every verdict is equal to a run of today's code on the same frames;
  - an event that does not repeat a sounding pitch: no re-attack is ever registered for it (contract item 7).
- [ ] T011 [P] [US1] New test `PT/session/ArcadeReattackTest.java`, seen red. Two plucks of the same pitch through `ArcadeRun` on synthetic frames:
  - at level 5, scored twice;
  - at a level whose filter removes the event between them: the kept event still expects a repeat (contract item 1, the full sequence).
- [ ] T012 [P] [US1] New test `PT/session/PracticeSessionReattackTest.java`, seen red: Wait mode, the same pitch twice in a row, each pluck on synthetic frames; the second note is accepted on its re-attack; a held note is not; `reattack.enabled` off: as before.
- [ ] T013 [US1] In `PR/session/FollowSession.java`, implement contracts/reattack-rule.md items 1, 2, 3, 5, 6 (the re-attack part), 6a, 7 and 8:
  - the expected repeats, computed once from the full written sequence;
  - a ring of band levels per open expected repeat, preallocated and sized from the bound of `reattack.before`;
  - the rise and spread test on each frame in `process`;
  - re-attacks go into the `PendingOnset` path;
  - a re-attack is merged with a detector onset within `ONSET_REFRACTORY` hops.

  T010 and T011 must turn green.
- [ ] T014 [US1] In `PR/session/PracticeSession.java`, the same rule for the awaited event (items 1, 2, 3, 6, 6a, 7, 8), so that T012 is green and `FastRunFunctionalTest` keeps its floors.
- [ ] T015 [US1] New test `PT/dataset/RepeatedPlucksEnginesTest.java` (tag `dataset`). It runs synthetic Karplus-Strong plucks, as `FastRunFunctionalTest` makes them, through each engine (`OfflineAnalysis`, `-Dtuxguitar.recognition.engine`), then through Follow and Wait:
  - two plucks 100 ms apart are two matches (SC-005);
  - a held note expected twice is one match (SC-004).

  See it red before T013–T014 (run at the phase start), green after.
- [ ] T016 [US1] Run `RepeatedNotesProbeTest` on the product path and the offset probe on time: 100/100 in Wait, Follow and Arcade (each level 1 to 5), with both engines. Write the result to `SPEC/benchmark/us1.md`, with the `REPEATED` table of `FastRunFunctionalTest` before and after.
- [ ] T017 [US1] Guards: rerun T002 into `SPEC/benchmark/after-us1-java/` and `-native/`, and compare with `before-*` into `SPEC/benchmark/comparison.md`. **Stop condition B** applies.
- [ ] T018 [US1] Gate, and commit "impl(028): US1: a note played again is recognised".

## Phase 4: User Story 2 - A little late or early is still played (P1)

**Goal**: 100/100 on `DI` from −70 to +70 ms, in Follow and Arcade, with both engines (FR-003, SC-001).

**Independent Test**: the offset table from −70 to +70 ms is at 100 % on every row.

- [ ] T019 [P] [US2] New test `PT/session/FollowSessionNearestTargetTest.java`, seen red, on synthetic frames. A run of eight notes of the same pitch 153 ms apart, every attack moved by −70, −40, +40 and +70 ms:
  - each attack goes to the event with the nearest target (ties: the earlier one), and all eight are matched;
  - a run on time is unchanged;
  - with `reattack.enabled` off, the first open event takes the attack, as today.
- [ ] T020 [US2] In `PR/session/FollowSession.java` (`registerOnset` and the re-attack path), give an attack inside the windows of several open events of the same pitch to the nearest target, under `reattack.enabled` (contract items 4 and 6). If T006 found another cause, implement instead what "R3 closing" in `SPEC/research.md` decided, with its test first.
- [ ] T021 [US2] Write the offset table to `SPEC/benchmark/after-offset.md`: Wait at 0, and Follow and Arcade (every level), Java and native, from −100 to +100 ms. Every row the spec requires, from −70 to +70, must be at 100 / 100.
- [ ] T022 [US2] Guards again (as T017) into `after-us2-*`, and update the comparison. **Stop condition B** applies.
- [ ] T023 [US2] Gate, and commit "impl(028): US2: early or late, the attack goes to its own note".

## Phase 5: User Story 3 - The test is replayed on every version (P2)

**Goal**: the offset test is kept, skipped where the recording is absent, run before each release, and checked by the release workflow (FR-004, FR-005, SC-003).

**Independent Test**: without `-Dtuxguitar.owner.offset` the test is skipped; with `DI` it passes and writes the table; with `-Dtuxguitar.owner.offset.reattack=off` it fails and names the events; the release-notes check refuses missing or failing tables.

- [ ] T024 [US3] New test `PT/dataset/OwnerOffsetTest.java` (tag `owner-offset`, no pom change), per contracts/owner-offset-test.md:
  - folders separated by commas, and `report-only`;
  - the alignment;
  - Wait, Follow and Arcade at each level 1 to 5 (the `Level` column);
  - both engines, all the offsets;
  - the switch `tuxguitar.owner.offset.reattack`;
  - the table and the per-event detail of failing rows;
  - fails below 100 % from −70 to +70 ms on a gated folder;
  - skipped (`Assumptions.assumeTrue`) without the property.
- [ ] T025 [US3] Check T024 three ways and record the three outcomes in `SPEC/benchmark/owner-offset.md`:
  - without the property: skipped;
  - on `DI`: green, with its table;
  - on `DI` with `-Dtuxguitar.owner.offset.reattack=off`: red, with the missed events named (US3 scenario 2).
- [ ] T026 [P] [US3] New script `misc/owner-offset-check.sh <report> <folder>…`:
  - JDK 21, run from `BUILD`, `-Dtest=OwnerOffsetTest`, with the native library;
  - restores the benchmark files after Maven;
  - exits with the test's status.

  Its self-test `misc/test-owner-offset-check.sh` checks the argument errors (no report, no folder, a folder without its files).
- [ ] T027 [P] [US3] New script `misc/verify-offset-table.sh <release-notes>`, which fails when the heading `## Offset test` is missing, or when a row of a gated folder between −70 and +70 ms is under 100 %. Write its self-test `misc/test-verify-offset-table.sh` first and see it red, on sample notes with and without a failing row.

  In `.github/workflows/release.yml`:
  - list both self-tests beside the others (lines 67–71);
  - run `misc/verify-offset-table.sh docs/release-notes/<tag>.md` before anything is published.
- [ ] T028 [P] [US3] In `docs/VERSIONING.md`, add the section "Before a release":
  - run `misc/owner-offset-check.sh <report> tests/tiktok/1` on the owner's machine;
  - copy the table under `## Offset test` in `docs/release-notes/<tag>.md`;
  - the release workflow refuses it otherwise.
- [ ] T029 [US3] Gate, and commit "impl(028): US3: the offset test before every version".

## Phase 6: User Story 4 - The guitar drifts, the practice says so (P3)

**Goal**: a suggestion to retune appears in Wait, Follow and Arcade when the notes drift the same way, from 20 cents by default (user setting). Wait and Follow go on; Arcade pauses on a window with Yes (the tuner, then resume), No, and More tolerant (+10 % for this run, never above 45 cents) (FR-007b, FR-009, SC-006).

**Independent Test**: the new tests green; in Wait and Follow the run and the music go on; in Arcade each of the three choices resumes the run where it paused, and the boost is gone at the next run.

- [ ] T030 [P] [US4] New test `PT/session/TuningDriftTest.java`, seen red, per contracts/retune-suggestion.md:
  - six notes −25 cents on string 3: suggest flat, string 3;
  - six notes −25 on strings 2 and 3: suggest flat, no string;
  - six notes mixed +25 and −25: none;
  - six notes −15 at a threshold of 20: none;
  - a threshold of 10: suggested;
  - after a suggestion: none for `drift-quiet` notes;
  - misses, wrong notes and chords are not inputs.
- [ ] T031 [US4] New pure class `PR/session/TuningDrift.java`, so that T030 is green. It has a preallocated ring sized from the bound of `drift-notes`, its values from the settings, and no numeric `static final`.
- [ ] T032 [US4] New test `PT/session/FollowSessionTuningDriftTest.java`, seen red: a run of drifting single notes gives the suggestion in `FollowSession.Snapshot` (a new accessor; the snapshot is built only in `FollowSession`), and no verdict changes. Then feed `TuningDrift` from `PR/session/FollowSession.java` with each single note judged on its pitch, using its cents offset and `PracticeEvent.getStringNumber()`.
- [ ] T033 [US4] The same for Wait: a new test in `PT/session/PracticeSessionTuningDriftTest.java`, seen red, then `TuningDrift` fed from `PR/session/PracticeSession.java` and exposed in `PracticeSnapshot`.
- [ ] T034 [US4] Write the texts of contracts/retune-suggestion.md in `LANG` (three languages) and a new test `PT/ui/TuningDriftTextTest.java`, seen red first: the text choice (flat or sharp, one string or several) for Wait, Follow and Arcade.

  Show the suggestion:
  - in Follow, through `PracticePalette.showFollowNotice`;
  - in Wait, on the feedback line of `PR/ui/PracticePalette.java`;
  - Arcade's text is the window's (T038).

  Nothing pauses in Wait and Follow.
- [ ] T035 [P] [US4] New test `PT/arcade/core/ArcadeRetuneOfferTest.java`, seen red, per contracts/retune-suggestion.md and data-model.md:
  - a suggestion while `PLAYING` → `OFFERED`; a second suggestion while `OFFERED` or `TUNING` is ignored;
  - Yes → `TUNING`, then the tuner's close → `PLAYING`; No → `PLAYING`; the window closed by its frame = No;
  - More tolerant from a start of 35: 38.5, then 42, then 45, then still 45 (the cap); at a start of 45 or more: unchanged, and the button says so;
  - a new run: steps back to 0, acceptance back to its start;
  - the step is `tuning.arcade-boost` read from the settings (0.05 and 0.25 checked).
- [ ] T036 [US4] New pure class `PR/arcade/core/ArcadeRetuneOffer.java`, so that T035 is green; no numeric `static final` (`SessionConstantInventoryTest` covers `PR/arcade/core`); the cap of 45 cents comes from the bound of `NOTE_ACCEPT` in `RecognitionParameters`, not a literal.
- [ ] T037 [US4] New test `PT/session/FollowSessionAcceptBoostTest.java`, seen red: a note 40 cents off is refused at 35; after `setNoteAcceptCents(42)` the next such note is accepted, the verdict already given is unchanged, and a new run starts from the settings' value. Then add `setNoteAcceptCents` to `PR/session/FollowSession.java` (applied from the next judged frame, no allocation).
- [ ] T038 [US4] New `PR/arcade/ui/ArcadeRetuneWindow.java` (modal, over the Arcade window, on the model of `PR/ui/ConfirmWindow.java`), with the texts of contracts/retune-suggestion.md in `LANG` (three languages; the More tolerant button says what it risks), and its wiring in `PR/ui/PracticeWindow.java`, which owns `ArcadeController`:
  - the suggestion from `FollowSession.Snapshot` while the run is RUNNING → `ArcadeController.pause()` and the window;
  - Yes → `new PracticeTunerOpener(context).open()` (as `PR/ui/PreRunStartGate.java` does) and `toFront()`; its closed listener → `ArcadeController.resume()` only once the track is given back and `TUNER_OPEN` is resolved by `PR/session/FollowEvents.java` (the tuner refuses to open while a practice track is set: `tuner-next` `TunerWindow.open`); the lifted track ends nothing and shows no mismatch (`PracticeWindow.updateMismatch`); if the tuner is not available or did not open, Yes behaves as No and the window says so;
  - the run ended while the window is open (Stop, the Arcade window closed): the window closes with no effect;
  - No → `resume()`; More tolerant → `ArcadeRetuneOffer`, then `FollowSession.setNoteAcceptCents`, then `resume()`;
  - nothing is saved; the next run builds a new `ArcadeRetuneOffer`.

  Before the wiring, a new unit test `PT/ui/ArcadeRetuneFlowTest.java`, seen red, for a new class `PR/ui/ArcadeRetuneFlow.java` (the order of the calls, out of `PracticeWindow`), on fakes of `ArcadeRunPort` and of the tuner opener (`PreRunWindow.TunerOpener`, which `PracticeTunerOpener` implements): Yes pauses, opens, and resumes only after the close and the resolve, with no pause reason left; Yes with no tuner = No; No and More tolerant resume; a run ended while offered closes the offer.

  New test `PT/arcade/ui/ArcadeRetuneWindowSwtTest.java` (`swt-gui`): the three buttons, their texts (the step shown as set), each one resumes; written here and run at T044. The Arcade painting tests stay green.
- [ ] T039 [US4] New test `PT/ui/TuningDriftSettingSwtTest.java` (`swt-gui`): the threshold is the first row of the *Tuning drift* section of the settings panel, with its recommended value. It is written here and run at T044.
- [ ] T040 [US4] New test `PT/dataset/TuningDriftReplayTest.java` (tag `dataset`), both engines:
  - a clean dataset recording resampled to sound 25 cents flat, then 40 cents flat: the suggestion appears, and the run completes with the same number of verdicts;
  - on the dataset recordings whose notes lie within 10 cents on average: it never appears (SC-006).
- [ ] T041 [US4] Gate (without SWT), and commit "impl(028): US4: the suggestion to retune".

## Phase 7: Polish & cross-cutting concerns

- [ ] T042 [P] Documentation:
  - in `DOC`, the method: the re-attack, the nearest target, the drift; the detector's rule and defaults unchanged;
  - in `docs/PRACTICE.md`, `docs/PRACTICE-FR.md` and `docs/PRACTICE-ES.md`: repeated notes in every mode, playing early or late, and the suggestion to retune, its setting, and Arcade's pause with its three choices.
- [ ] T043 [P] New owner's sheet `specs/test-sheets/F18-notes-repetees.md`, in French, covering:
  - the riff of `DI` played live in Wait, Follow and Arcade, on time, early and late;
  - a held note and a scratch;
  - a guitar detuned on purpose (one string, then all);
  - the suggestion that does not stop the play in Wait and Follow;
  - in Arcade, the pause and each of Yes (tuner, then resume where paused), No, and More tolerant (twice, then a new run back to the usual setting);
  - the setting.
- [ ] T044 Full gate (`clean verify -P native-modules`), then once with the Mac unlocked `-P swt-gui-tests` (T038's and T039's tests and the committed SWT tests). Then the guards a last time into `SPEC/benchmark/after-java/` and `-native/`, and `misc/owner-offset-check.sh` on `DI`.
- [ ] T045 Write `SPEC/final-report.md` (each FR and SC with its evidence, the figures before and after, the stop conditions met or not, "no existing setting changed" for FR-007, the boost only per run in Arcade). Update `specs/STATUS.md`. Delete the untracked `PT/session/OffsetReplayProbeTest.java`. Commit "impl(028): polish, report", then push once.
- [ ] T046 [HUMAN] The owner runs sheet F18 and gives the verdict.

## Dependencies & Execution Order

- **Phase 1 → Phase 2**: T006 gates everything after it.
- **US1 (Phase 3)** depends on Phase 2.
- **US2 (Phase 4)** depends on US1: the assignment acts on re-attacks too.
- **US3 (Phase 5)** depends on US2: its test requires 100 % from −70 to +70.
- **US4 (Phase 6)** depends only on Phase 2 (the settings). It is done after US3 so that its changes do not mix with the guards of US1 and US2.
- **Polish** comes after all the stories.

## Parallel Opportunities

- T007 runs beside T004–T005 (different modules).
- T010, T011 and T012 are written together.
- T019 can be written during US1's guard runs.
- T026, T027 and T028 run beside T024–T025.
- T030 runs beside T032's test; T035 beside T037's test.
- T042 and T043 run together.

## Implementation Strategy

- **MVP**: Phases 1–3. The repeats are recognised on time in every mode, with both engines, and with no regression.
- **Then**:
  - US2 (early and late) completes SC-001;
  - US3 locks it for every version and every release;
  - US4 (the retune suggestion, and Arcade's pause window) comes as a separate increment.
- Each phase ends with the gate and a commit. The SWT run is at the end, and so is the single push.
