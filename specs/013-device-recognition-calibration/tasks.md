---
description: "Tasks of spec 013: per-device calibration of the note and chord recognition"
---

# Tasks: Per-device calibration of the recognition

**Input**: `specs/013-device-recognition-calibration/` (spec.md with its Clarifications of 2026-10-15, plan.md P0 to P6, research.md R1-R11, data-model.md, contracts/profile-library.md, calibration-flow.md, scoring-search.md, quickstart.md)

**Prerequisites**: spec.md (US1 to US5, FR-001 to FR-018, SC-001 to SC-009). **No recognition algorithm changes; with no profile every figure is as before; no audio is ever written to disk; the bass is out of scope.**

**Tests**: written **before** the code they test and seen failing (a compile error counts; say so).

**Conventions**: `AE` = `desktop/TuxGuitar-audio-engine/src/main/java/app/tuxguitar/audio/engine`, `AET` = `desktop/TuxGuitar-audio-engine/src/test/java/app/tuxguitar/audio/engine`, `SRC` = `desktop/TuxGuitar-practice/src/app/tuxguitar/app/tools/custom/practice`, `TST` = `desktop/TuxGuitar-practice/test/app/tuxguitar/app/tools/custom/practice`, `SPEC` = `specs/013-device-recognition-calibration`, `BUILD` = `desktop/build-scripts/tuxguitar-macosx-swt-cocoa` (with `JAVA_HOME` on JDK 21).

**Rules for every phase**: commit locally at the end of the phase with no co-author trailer and a message saying which tests failed first; stage explicit paths (never `git add specs`); never push (the owner asks); keep the build green; **run the whole default practice suite after a clean compile** (`mvn clean test -pl ../../TuxGuitar-practice -am` from BUILD) and compare it to `specs/012-adjustable-timing-window/benchmark/after.txt` (937 tests); report exactly what ran and what was skipped; never commit the owner's recordings.

## Phase 1: Baseline and unverified points (P0)

- [ ] T001 Record in `SPEC/benchmark/baseline.txt`: the test counts of audio-engine and practice (from `specs/012-adjustable-timing-window/benchmark/after.txt`, re-run to confirm), the recognition reports of `specs/010-pre-run-check/benchmark/after/` as the diff base, and the git hash.
- [ ] T002 Count the **events** of `tests/basic_test.tg` (notes and chords; a chord is one event) by reading it with the practice sequence builder in a throw-away test, write the count, the number of notes and of chords in `SPEC/research.md` R10 (replace "about 13"). Copy the score to `desktop/TuxGuitar-practice/share/calibration/guitar.tg` and check how the plugin reads its share path (cite the class); if it cannot, say so and stop for a decision.
- [ ] T003 Verify in the source and write in `SPEC/research.md` R3: whether `AudioInputDescriptor.getId()` differs for two identical adapters (else add the catalog position to `DeviceKey`), and in R4: whether the front end can be read as neutral (gain 0 dB, noise reduction off) from `RecognitionSettings`/`EngineGainBackend`. No code.
- [ ] T004 [HUMAN] The owner records `basic_test.tg` once on the built-in microphone with a small tool kept outside git (`~/tuxguitar-owner-takes-backup/calibration/`), quiet room; the file is **not committed** (ignored by `.gitignore`: add `tests/calibration-takes/` if needed).
- [ ] T005 Judge the 22 built-in-microphone notes of `desktop/TuxGuitar-audio-engine/src/test/resources/benchmark/builtin-mic/standard` with the default settings and write the rate in `SPEC/benchmark/baseline.txt` (the reference of SC-006). Commit phase 1 locally.

## Phase 2: User Stories 2 and 4 - the profile layer and the library (P1)

**Goal**: a profile of values merged over the default through the store, saved in its own library; with no profile everything is equal. **Independent test**: `-Dtest='RecognitionSettingsStoreProfileTest,ProfileLibraryTest,ProfileStateTest,DeviceKeyTest'`.

- [ ] T006 [US4] Write first (red) in `AET/settings/RecognitionSettingsStoreProfileTest.java`, from `contracts/profile-library.md`: (1) no active profile: `get()` equals `getDefault()` for **every** parameter of `RecognitionParameters.ALL`; (2) an active profile changes only the profile parameters (`NOTE_ACCEPT`, `NOTE_FRET_SHARP`, `NOTE_CONFIDENCE`, `NOTE_STABILITY`, `NOTE_ATTACK`, `CHORD_EVIDENCE`, `CHORD_WINDOW`, `CHORD_MIN_PARTIALS`, `CHORD_CONFIRM`, `ATTACK_MIN_LEVEL`, `INPUT_LEVEL_MIN`), never `ENGINE_PITCH`, the tuner, `TEMPO_WINDOW`, the flames, the gain, the passages; (3) `update` through `getDefault()` while a profile is active leaves the profile parameters of the default as they were; (4) listeners are told when `setActiveProfile` changes `get()`.
- [ ] T007 [US4] Implement `AE/settings/ProfileParameters.java` (the list of profile parameters, the merge and the check "within bounds") and in `AE/settings/RecognitionSettingsStore.java`: `getDefault()`, `setActiveProfile`, effective `get()`. Green T006.
- [ ] T008 [US4] Find **every writer** (`grep -rn "store.update(\|\.update(" desktop/TuxGuitar-practice desktop/TuxGuitar-tuner-next desktop/TuxGuitar-audio-engine`): `FollowSettings.setWindowMillis`, `PassageSettings`, `RecognitionSettingsPanel`, `EngineGainBackend`, the tuner; each reads `getDefault()` before writing (`store.update(store.getDefault().with(...))`). Write first (red) one test over the callers found, asserting that a write with a profile active leaves the default's profile parameters unchanged (invariant 3). List the callers in the commit message.
- [ ] T009 [P] [US2] Write first (red) `AET/calibration/DeviceKeyTest.java` and `RecognitionProfileTest.java`: the key is `id#channelIndex`; two devices of the same name, one device with two channels, a different sample rate never give the same key or apply the profile; a profile holds only the profile parameters within their bounds, its sample rate, date, rates (default and found) and origin; a profile applies only when the capture rate equals its rate or is unknown.
- [ ] T010 [P] [US2] Write first (red) `AET/calibration/ProfileStateTest.java`: `ProfileState.of(profile, rate)` is orange for no profile, for origin `DEFAULT_CHOSEN` and for a rate mismatch; green for a searched profile with total rate >= 60 %; red for a searched profile below 60 %.
- [ ] T011 [US2] Implement `AE/calibration/DeviceKey.java`, `RecognitionProfile.java`, `ProfileState.java`. Green T009 and T010.
- [ ] T012 [US2] Write first (red) `AET/calibration/ProfileLibraryTest.java`: with an in-memory `ProfilePersistence`, save two profiles, build a **new** library over the same persistence ("restart"): both come back equal; a corrupt or unknown-version entry is skipped (orange, no exception); `save` writes only the library; a failed save leaves the old profile; the file written is not the recognition store's file.
- [ ] T013 [US2] Implement `AE/calibration/ProfileLibrary.java` and `ProfilePersistence.java` (a `TGConfigManager` module `tuxguitar-recognition-profiles`, keys `profile.<deviceKey>.<param>` plus metadata). Green T012.
- [ ] T014 [US4] Run the whole default suite after a clean compile; the recognition reports equal to the base; commit phase 2 locally (say which tests failed first). **Protects FR-010, FR-011, FR-013, SC-002, SC-004, SC-008.**

## Phase 3: User Story 1 - scoring and search, offline (P1)

**Goal**: given a recording and the events of the piece, find the best profile. **Independent test**: `-Dtest='EventJudgeTest,SoundSegmenterTest,CalibrationScorerTest,ProfileSearchTest'`.

- [ ] T015 [US1] Write first (red) `AET/calibration/SoundSegmenterTest.java`: a synthetic signal of N plucks gives N segments cut at the onsets, with half a second of silence in front of each; fewer or more onsets than the events refuses with a reason; a silent or clipped signal is refused.
- [ ] T016 [US1] Implement `AE/calibration/SoundSegmenter.java`. Green.
- [ ] T017 [US1] Write first (red) `TST/calibration/EventJudgeTest.java`: the logic of `DatasetJudge.accepts` in production form, `EventJudge.accepts(segment, event, settings)` (offline analysis and the practice parameters both from the settings, a Wait-mode `PracticeSession` with `PolyphonicAcceptance`): a right note accepted, a wrong note refused, a chord with all its notes accepted, with one missing refused. Cross-check on the 22-note corpus: same verdicts as `DatasetJudge`.
- [ ] T018 [US1] Implement `SRC/calibration/EventJudge.java` (moving the logic, not rewriting: say in the commit what was moved; `DatasetJudge` keeps working, now calling it if that is trivial, else left as it is). Green.
- [ ] T019 [US1] Write first (red) `TST/calibration/CalibrationScorerTest.java` from `contracts/scoring-search.md`: `correct`, `wrong` (the control against the next event), objective = correct - 2 x wrong, rate = correct / N, notes and chords separately, total rate for the 60 % threshold; a setting that accepts everything has a bad objective.
- [ ] T020 [US1] Implement `SRC/calibration/CalibrationPiece.java` (reads the shipped score into events) and `CalibrationScorer.java`. Green.
- [ ] T021 [US1] Write first (red) `TST/calibration/ProfileSearchTest.java`: deterministic (same input, same profile); the default is the first candidate and the result never has a lower objective; leaving the default needs a gain of at least one event; the budget (120 s, 2000 candidates) and the cancel (ends within one candidate, nothing returned); the report has one line per parameter; progress is reported.
- [ ] T022 [US1] Implement `SRC/calibration/ProfileSearch.java` (coordinate descent, five values per parameter, ties nearest the default). Green.
- [ ] T023 [US1] Write `TST/calibration/BuiltinMicProfileTest.java` (tag `benchmark`, skip when the owner's calibration recording is absent): on the owner's recording of `basic_test.tg` the search runs, the rates with the default and with the profile are printed; on the 22 built-in-microphone notes the profile found elsewhere is judged against the default (SC-005, SC-006). Record the figures in `SPEC/benchmark/search-results.txt` **whatever they are**; no figure is promised.
- [ ] T024 Run the whole default suite after a clean compile; commit phase 3 locally. **Protects FR-006, FR-014, FR-016, SC-005, SC-006.**

## Phase 4: User Story 1 - the listening mode (P2)

**Goal**: record in memory, count sounds without saying which, stop by itself. **Independent test**: `-Dtest='SoundCounterTest,HopRecorderTest,RecordingQualityTest'`.

- [ ] T025 [US1] Write first (red) `AET/calibration/HopRecorderTest.java`: a `HopListener` copying hops into a preallocated array (cap 120 s), **no allocation per hop** (a test that counts allocations or at least asserts the array is never replaced), refuses when the front end is not neutral (gain not 0 dB or noise reduction on), and nothing is ever written to a file (the test lists the temp directory before and after).
- [ ] T026 [US1] Write first (red) `AET/calibration/SoundCounterTest.java`: onset plus level counts one sound; 250 ms refractory; stop 1 s after the count reaches the number of events; timeouts (20 s of silence, 3 min in all); cancel.
- [ ] T027 [US1] Write first (red) `AET/calibration/RecordingQualityTest.java`: silent, too short, too noisy, clipped each refused with its reason (reuse `LevelAnalyzer`/`StrumClipMeter` of spec 010).
- [ ] T028 [US1] Implement `AE/calibration/HopRecorder.java`, `SoundCounter.java`, and the quality check. Green T025 to T027. Test once with the device-less engine (`FakeAudioInput.newEngine`) that a played file reaches the recorder through the real `AnalysisService`.
- [ ] T029 Run the whole default suite after a clean compile; commit phase 4 locally. **Protects FR-004, FR-005, FR-017.**

## Phase 5: Users Stories 1, 3 and 5 - the flow and the window (P2)

**Goal**: the flow of `contracts/calibration-flow.md` is a total pure table; one window shows it. **Independent test**: `-Dtest='CalibrationFlowTest,CalibrationWindowTest'`.

- [ ] T030 [US1] Write first (red) `TST/calibration/CalibrationFlowTest.java`: every row of the table of `contracts/calibration-flow.md`, a pair not in the table changes nothing, the four invariants (nothing saved before `DONE`, `TUNER_OPEN` never reaches `PIECE`, the recording dropped in `DONE` and `CANCELLED`, a result not better than the default never saved without the choice).
- [ ] T031 [US1] Implement `SRC/calibration/CalibrationFlow.java`. Green.
- [ ] T032 [US1] [US3] [US5] Write first (red) `TST/calibration/CalibrationControllerTest.java` (fake engine, fake clock, in-memory library): the whole path with a played recording gives a saved profile; *No* at the quality question saves nothing; a cancel at **each** state leaves the existing profile unchanged (SC-007); the device lost while listening cancels; *Use the default* saves the default with origin `DEFAULT_CHOSEN` and records nothing; the three-choice message appears when the result is not better than the default or below 60 % and each choice does what the contract says.
- [ ] T033 [US1] Implement `SRC/calibration/CalibrationController.java` (live listening on the engine's hop listener, the search on its own thread, progress and cancel). Green.
- [ ] T034 [US1] Write first (red) `TST/ui/CalibrationWindowTest.java` (headless contract toolkit, like `PreRunWindowTest`): each state shows its text, the buttons, a progress bar while `ANALYSING`; the tuning question reuses the `practice.prerun.tuned.*` texts and `PracticeTunerOpener`; texts present in en/fr/es.
- [ ] T035 [US1] Implement `SRC/ui/CalibrationWindow.java` (one window whose content changes per step; one window decided by the owner in the spec (FR-003), to be judged in his trial T044; keep the content per step in separate view classes so that going back to separate windows stays cheap) and the texts in `desktop/TuxGuitar-practice/share/lang/messages{,_fr,_es}.properties`. Green.
- [ ] T036 Run the whole default suite after a clean compile; commit phase 5 locally. **Protects FR-002 to FR-005, FR-009, FR-012, FR-014, SC-001, SC-007, SC-009.**

## Phase 6: User Stories 2 and 4 - the dot, the button and the reuse (P2)

**Goal**: the Input tab shows the state and the engine uses the profile of the selected device. **Independent test**: `-Dtest='ProfileDotTest,InputSelectorPanelCalibrationTest,ProfileReuseTest'`.

- [ ] T037 [US2] Write first (red) `TST/ui/ProfileDotTest.java`: the dot is green, orange, red with its **text** and colour, recomputed on every selection change (a selection listener test), correct for the three states (SC-003).
- [ ] T038 [US2] Write first (red) `TST/ui/ProfileReuseTest.java`: a profile saved for device A is the active profile when A is selected after a "restart" (new library, new store), and the effective settings differ from the default only by the profile parameters; selecting B (no profile) restores the default; two devices of the same name do not share (SC-002, SC-008).
- [ ] T039 [US2] Implement `SRC/ui/ProfileDot.java` and in `SRC/ui/InputSelectorPanel.java` the button *Calibrate the recognition* and the dot; the engine selection listener sets the active profile (`setActiveProfile`) at each input change; the offer *Calibrate again / Use the default* when the device has a profile. Green T037 and T038.
- [ ] T040 Run the whole default suite after a clean compile and the native test of the Input tab if one exists (`swt-gui-tests`); commit phase 6 locally. **Protects FR-001, FR-007, FR-008, FR-010.**

## Phase 7: Validation, documentation, owner trial (P6)

- [ ] T041 Document: `desktop/TuxGuitar/share/help/tools_audio_recognition{,_fr,_es}.html` (the button, the dot, the three choices, what is measured, that no audio is kept), `docs/` page, `STATUS.md`, `README` mention. `RecognitionControlsTest` still green.
- [ ] T042 Independent measure: run quickstart items 3 and 4, write `SPEC/benchmark/validation.txt`: rate with the default and with the profile on the 22 built-in notes and on the owner's recording; **say plainly if there is no gain** (SC-006 may fail: report it, do not tune the search to the validation data).
- [ ] T043 Full gate: `mvn -e clean verify -P native-modules` from BUILD; `git diff` on the recognition code (analysers, `PolyphonicAcceptance`) is empty; the recognition reports equal to the base (SC-004). Build the app and relaunch it.
- [ ] T044 [HUMAN] The owner's trial on the built-in microphone: judge **the one window** (is each step clear, does it feel right, else go back to separate windows); calibrate with `basic_test.tg`, look at the dot, restart, check the dot, try another device, try the three-choice message; report what does not feel right.
- [ ] T045 Write `SPEC/final-report.md` (what ran, what was skipped, the measured gain or its absence, the deviation of one window, the open points) and commit phase 7 locally.

## Dependencies

P1 then P2; P3 needs P2 (profile and settings); P4 is independent of P3 but needed by P5; P5 needs P3 and P4; P6 needs P2 and P5; P7 last. MVP = P1 to P3 (a profile that can be searched offline from a file); the user-visible feature needs P6.

## Coverage

FR-001 T039; FR-002 T034; FR-003 T034; FR-004 T026, T028; FR-005 T032; FR-006 T022; FR-007 T013; FR-008 T037; FR-009 T032, T039; FR-010 T006, T038; FR-011 T006, T014, T043; FR-012 T032; FR-013 T012; FR-014 T021, T032; FR-015 T035, T041; FR-016 T002, T020; FR-017 T025, T030; FR-018 (no bass: nothing to build). SC-001 T044; SC-002 T012, T038; SC-003 T037; SC-004 T014, T043; SC-005 T023; SC-006 T023, T042; SC-007 T032; SC-008 T009, T038; SC-009 T034.
