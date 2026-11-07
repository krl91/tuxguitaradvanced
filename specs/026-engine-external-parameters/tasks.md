# Tasks: every recognition engine value is an external parameter

**Input**: design documents from `specs/026-engine-external-parameters/`

**Prerequisites**: plan.md, spec.md (clarified 2026-11-06), research.md (R1–R11), data-model.md, contracts/native-parameters.md, contracts/settings-window.md, quickstart.md

**Tests**: required. The owner's rules apply:
- tests come first and must be seen red;
- never modify an existing test, only add new ones;
- if a committed test fails, fix the code under the bug protocol (a reproducing test first, at most 3 attempts), then stop and report.

**Approved exceptions**: none. This spec changes no behaviour, so no committed test may need to change. If one does, stop and report.

**Stop conditions**: stop and report to the owner, with the figures, when:
- **A**: a before/after report differs with the defaults (SC-002) and 3 attempts do not explain it;
- **B**: a value cannot be read without an allocation, lock or wait in the analysis loop or the cpal callback;
- **C**: a family's categories cannot fit the 768-pixel screen at 7 values each.

**Paths**:
- `AE` = `desktop/TuxGuitar-audio-engine/src/main/java/app/tuxguitar/audio/engine`
- `AET` = `desktop/TuxGuitar-audio-engine/src/test/java/app/tuxguitar/audio/engine`
- `PR` = `desktop/TuxGuitar-practice/src/app/tuxguitar/app/tools/custom/practice`
- `PT` = `desktop/TuxGuitar-practice/test/app/tuxguitar/app/tools/custom/practice`
- `CRATE` = `desktop/build-scripts/native-modules/tuxguitar-monitor-native/src`
- `LANG` = `desktop/TuxGuitar-practice/share/lang`
- `BUILD` = `desktop/build-scripts/tuxguitar-macosx-swt-cocoa`
- `SPEC` = `specs/026-engine-external-parameters`
- `DOC` = `docs/architecture/recognition-engines.md`
- `LIB` = the native library as built; `OC3` = the owner's third series (as in spec 024)

**Standing rules for every phase**:
- **Build:**
  - pin JDK 21; wrap builds in `caffeinate -i`;
  - after each Maven build, run `git checkout -- specs/003-arcade-mode/benchmark/ specs/023-native-recognition-engine/benchmark/recognition-delay.md`;
  - never run two builds at once, and never edit sources while a build runs.
- **Each family task** (an engine family puts its native values in the tuning array, or in the `float[21]` for the five of today's indices that were fixed, and adds its keys to `AudioEngine.engineValuesDiffer`; every family adds one help row per parameter, under a new heading per section, to the three help pages `desktop/TuxGuitar/share/help/tools_audio_recognition{,_fr,_es}.html`, in the format the committed `RecognitionControlsTest.theHelpPageDocumentsEverySettingWithItsCurrentRangeAndRecommendedValue` reads: an `<h2>` with the category name, then name, current range, recommended value; and one row per parameter, its key in backticks, to `DOC` §4, since the committed `EngineDocumentationTest` requires every key of `ALL` in `DOC` at every gate; T033 then completes and checks those rows):
  1. a new test `…ParametersTest` asserts the family's keys, sections (at most 7 per section), units, defaults (equal to the code's literal, compared with `==`), bounds and the ordered pairs; that the minimum, maximum and default are whole slider steps and the default is shown exactly (data-model "slider grid"); and, for each pair, that from the defaults moving either value to either end leaves the other unchanged (data-model "Validation"); seen red;
  2. the parameters in `AE/settings/RecognitionParameters.java` (`ALL`, in display order), with their name and tip in `LANG/messages{,_fr,_es}.properties`, a new unit's `recognition.unit.*` text, and the section names (contracts/settings-window.md: no number with a unit in a tip, French always different from English);
  3. the stage reads them from its value object built once when the run opens (research R4); its public constants stay, as the parameter's default;
  4. a new "takes effect" test: a non-default value changes the stage's output, with the Java engine and, for an engine stage, with the native engine; seen red.
- **Gate** (end of each phase):
  - `mvn -o -e clean verify -P native-modules` from `BUILD` is green;
  - the dataset reports with each engine are identical to `SPEC/benchmark/before-<engine>/` (quickstart §2);
  - `git diff --name-status <phase start> -- '*test*'` shows only added files;
  - no SWT run overnight or with the Mac locked: the SWT runs are grouped in T042.
- **Real time:** nothing new is read, allocated, locked or awaited per frame or per hop.
- **Commits and pushes:**
  - commit each phase without any co-author trailer;
  - one push per round, both refs together, after the SWT run of T042.

## Format: `[ID] [P?] [Story] Description`

## Phase 1: Setup (the figures before, the inventory)

- [X] T001 Write `SPEC/baseline.txt`:
  - HEAD;
  - the counts of the full gate;
  - the committed tests that read the classes this spec changes (`ArticulationDetector*`, `PolyphonicAnalyzer*`, `AttackAnalyzer*`, `ChordEvidence*`, `StrumClipMeter*`, the front-end tests, `NativeParameters*`, the parity tests, `RecognitionSettings*`, `RecognitionControlsTest`, `RecognitionSettingsPanelTest`, `RecognitionSettingsSwtTest`, `PolyphonicAcceptance*`, `PracticeParametersTest`, `FollowLeniencyTest`, `ArcadeLeniencyTest`, the technique, calibration and timing tests, the clock tests `TickInterpolator*`, `LatencyCompensation*` and `PlayerTempoClockTest`, `AudioEngineBackendTest`, the tests that read the help pages (`RecognitionControlsTest`'s help check), and the committed tests that loop over every parameter or section: `EngineDocumentationTest`, `RecognitionSettingsTest`, `RecognitionSettingsStoreProfileTest`, `RecognitionProfileTest`, `ChordLeniencyTest`, `RecognitionSettingsPanelTest`, `RecognitionControlsTest`, `RecognitionSettingsSwtTest`).
- [X] T002 Run every dataset and benchmark report with each engine at the current code (quickstart §1, research R10): `DatasetBenchmarkTest`, `GuitarSetChordBenchmarkTest`, `TechniqueDatasetBenchmarkTest`, `OwnerChordsBenchmarkTest`, `OwnerChordsProfileTest`, `ChordLeniencyLevelsTest`, `BassRecognitionBenchmarkTest`, `SyntheticChordGuardTest`, `RecordedPracticeReplayTest` (if its local files are there), and the `benchmark` tag. Copy the reports into `SPEC/benchmark/before-java/` and `before-native/`. Write `SPEC/benchmark/compare.sh`: it masks dates and durations, then runs `diff -r`.
- [X] T003 Write `SPEC/inventory.md` from the scan of research R1 (Java engine, session, Arcade core, crate). It has one row per value:
  - **parameter**: key, section, unit, default (the literal), bounds, takes effect, file and constant, Java and crate counterparts;
  - **fixed**: kind and reason, from the table in research R1.

  Each parameter row says when the value takes effect (data-model.md):
  - **listening restart**, for a value an engine stage reads, Java or native: a change made while listening restarts the analysis (Java) and is pushed with `configureTuning` or `configure` (native), as the level values are today;
  - **next run**, for a value of the session, the techniques, the calibration or the clock: it is read when the next run starts.

  Each "takes effect" test checks the moment its row gives.

  The scan also reads the numbers written inline in method bodies and constructor calls (research R1): each one outside the mathematical and unit allow-list gets a row, by file and text, as a future parameter (named as a constant by its family task) or as fixed with its reason.

  Section sizes follow data-model.md, at most 7 values each. The counts of data-model.md are approximate; `inventory.md` fixes them, and data-model.md is updated to the exact counts in the same task.

## Phase 2: Foundational (blocks every story)

- [ ] T004 [P] New test `AET/nativeengine/NativeTuningTest.java`:
  - `NativeParameters.tuning(RecognitionSettings.defaults())` has `NativeParameters.TUNING_LENGTH` values;
  - `NativeParameters.of(…)` (`float[21]`) is unchanged for the defaults (contracts/native-parameters.md);
  - the interface's default `open(…, parameters, tuning)` calls today's `open(…, parameters)`, and its default `configureTuning` does nothing, checked with a fake that implements only today's methods.

  Seen red.
- [ ] T005 [P] New test `AET/nativeengine/NativeLayoutCheckTest.java`: the layout check of the `JNI` implementation refuses a library whose `tuningLayout()` is missing (`UnsatisfiedLinkError`) or returns another length. The refusal has the reason `LIBRARY_MISSING` and the detail of the contract, and the backend falls back to the Java engine with the notice. The check is a small function taking the layout supplier, so it can be tested without a library. Seen red.
- [ ] T006 [P] New crate tests in `CRATE/analysis/mod.rs`: `Tuning::from_slice` refuses a shorter and a longer slice; `Tuning::defaults()` equals today's crate literals; `tuning_layout()` equals `TUNING_LENGTH`. Seen red.
- [ ] T007 Add the tuning array (`AE/nativeengine/NativeParameters.java`, `NativeRecognitionLibrary.java` and its `Api`, `AE/backend/NativeBackend.java`, `AE/AudioEngine.java`, `CRATE/jni_recognition.rs`, `CRATE/analysis/mod.rs`, `CRATE/recognition.rs`):
  - it starts with no family, `TUNING_LENGTH` 0, and the layout check in place;
  - each family of phase 3 adds its indices;
  - today's JNI functions and the `float[21]` are untouched.

  T004–T006 green; every committed native test still green.
  Every engine-stage parameter added later must also be in the list that `AudioEngine.engineValuesDiffer` compares, so that the Java analysis is rebuilt when it changes during listening. A new test `AET/AudioEngineEngineValuesTest.java` requires every key whose `inventory.md` row says "listening restart" to be in that list. It is green while the list is empty of new keys and goes red as soon as a family adds a key without listing it.
  Also a new test `AET/AudioEngineTuningPushTest.java` (on `FakeRecognitionApi`'s pattern, a new fake that records `configureTuning`): a settings change during listening pushes the new tuning, as `pushNativeParameters` pushes the `float[21]` today. Seen red before the push is wired.
- [ ] T008 [P] New test `AET/settings/RecognitionSectionsTest.java`:
  - the 8 existing sections keep their order and content;
  - every section holds 1 to 7 parameters;
  - no parameter of a new section is in `ProfileParameters.LIST` (a device profile never overlays them; research R8);
  - the new ordered-pair rule (`AE/settings/OrderedPair.java`): built on any two `Number` parameters, it brings the lower one of an inverted pair back within the upper one, and `RecognitionSettings.fromStrings` and `with` apply every pair of `RecognitionParameters.ORDERED_PAIRS` (empty until the families fill it; the test uses a pair built on two existing parameters).

  Seen red (the class does not exist).
- [ ] T009 Add `OrderedPair` and `RecognitionParameters.ORDERED_PAIRS`, applied in `RecognitionSettings.normalized` (`AE/settings/RecognitionSettings.java`) beside the existing `note.accept` ≥ `tuner.in-tune` rule, so on reading and on every change, the repair reported on reading. T008 green.
- [ ] T010 [P] New SWT test `PT/ui/RecognitionSettingsFitSwtTest.java` (`swt-gui-tests`): with the usable height forced to 768, every category's every control and its *Restore* button lie inside the window without scrolling, as `PracticeWindowFitsSwtTest` (spec 025) does. It also has a case that builds a test panel of 9 value rows in the same window and expects it to overflow, so the check is seen red on a real overflow. Written now, run in T042 (the Mac unlocked).
- [ ] T011 [P] New test `PT/ui/RecognitionStringsAllLanguagesTest.java`: every key of `RecognitionParameters.ALL` and every `Section` has a non-empty name and tip in `LANG/messages.properties`, `messages_fr.properties` and `messages_es.properties`; the French and Spanish texts differ from the English one; only Spanish texts may be in a short exception list written in the test itself (names such as YIN, units), since the committed `RecognitionSettingsPanelTest` already requires every French name, tip and category name to differ. Seen red with one French tip removed for the run (not committed), then green once it is put back.
- [ ] T012 Gate (with the reports identical); commit "impl(026): phase 2: the native tuning array with its layout check, the ordered-pair repair".

## Phase 3: User Story 1, the engine stages (Priority: P1) 🎯 MVP

**Goal**: every value of the engine stages is a parameter read identically by both engines.

**Independent test**: each family's "takes effect" test, with each engine; the reports identical.

- [ ] T013 [US1] Family `onset` and `onset-fast` (`AE/analysis/ArticulationDetector.java`, `NativeParameters` windows, `PracticeParameters.WEAK_FAST_DBFS`, `CRATE/analysis/articulation.rs`): sections `ONSET`, `ONSET_FAST`. The decision delays (`DECISION_DELAY_HOPS` 13, `FAST_DECISION_DELAY_HOPS` 8) stay fixed (the look-ahead the detector buffers: a buffer size, FR-002) and bound the post windows; pre-first is bounded by the 19 hops kept (data-model). Ordered pairs: pre-first ≥ pre-last and post-first ≤ post-last, normal and fast; new tests `AET/settings/OnsetParametersTest.java` and `AET/analysis/OnsetTakesEffectTest.java` (Java and native).
- [ ] T014 [US1] Family `pitch` (YIN threshold, one parameter for both engines, default 0.20: the Java engine passes it to TarsosDSP's `new FastYin(rate, size, threshold)` in `AE/analysis/TarsosFastYinEstimator.java`, which today uses the library's default 0.20, and the native engine reads index `YIN_THRESHOLD` instead of `NativeParameters.DEFAULT_YIN_THRESHOLD`; `AE/recognition/ExpectedPitchVerifier.java` search and periods with `CRATE/analysis/target.rs`, `AE/pitch/TolerancePolicy.java`, `PR/session/AttackCandidates.java` range): section `PITCH`. Ordered pair: `attack-candidates.low` < `attack-candidates.high`. Tests `PitchParametersTest`, `PitchTakesEffectTest`.
- [ ] T015 [US1] Family `polyphonic` (`AE/analysis/PolyphonicAnalyzer.java` except the FFT size and the band range, `CRATE/analysis/polyphonic.rs`, `CRATE/analysis/comb.rs`): sections `POLYPHONIC_1`, `POLYPHONIC_2`. Ordered pair: `polyphonic.candidate-low` < `polyphonic.candidate-high`. The candidate count and the harmonics are allocated at their upper bound (research R6). Tests `PolyphonicParametersTest`, `PolyphonicTakesEffectTest`. A new crate test with the existing `CRATE/no_allocation.rs` harness: `configureTuning` with every value of the family at its upper bound, then hops analysed, allocates nothing (FR-010, research R6).
- [ ] T016 [US1] Family `attack` (`AE/analysis/AttackAnalyzer.java` except the FFT size, `AE/analysis/AttackEvidence.java`, `CRATE/analysis/attack.rs`): sections `ATTACK_1`, `ATTACK_2`; the lag ring is allocated at its bound. Tests `AttackParametersTest`, `AttackTakesEffectTest`. A new crate test with the existing `CRATE/no_allocation.rs` harness: `configureTuning` with every value of the family at its upper bound, then hops analysed, allocates nothing (FR-010, research R6).
- [ ] T017 [US1] Family `chord-evidence` (`AE/analysis/ChordEvidence.java`, `CRATE/analysis/chord.rs`, `AE/analysis/StrumClipMeter.java`, `CRATE/analysis/strum_clip.rs`): section `CHORD_EVIDENCE`; tests `ChordEvidenceParametersTest`, `ChordEvidenceTakesEffectTest`.
- [ ] T018 [US1] Families `front-end`, `noise-gate`, `gain` (`AE/frontend/*`, `CRATE/analysis/front_end.rs`): sections `FRONT_END`, `NOISE_GATE`, `GAIN`. Ordered pair: `noise-gate.open` > `noise-gate.close`. Tests `FrontEndParametersTest`, `FrontEndTakesEffectTest`. A new crate test with the existing `CRATE/no_allocation.rs` harness: `configureTuning` with every value of the family at its upper bound, then hops analysed, allocates nothing (FR-010, research R6).
- [ ] T019 [US1] Gate (reports identical with each engine); commit "impl(026): phase 3 (US1): the engine stages' values are parameters, read alike by both engines".

## Phase 4: User Story 1, the practice session (Priority: P1)

**Goal**: every value of the session, the techniques and the calibration that changes what is accepted is a parameter.

**Independent test**: each family's "takes effect" test; the reports identical.

- [ ] T020 [US1] Family `acceptance` (`PR/session/PolyphonicAcceptance.java`): sections `ACCEPTANCE_1`…`ACCEPTANCE_3` (with the onset floor and the release found inline, inventory.md); tests `PT/session/AcceptanceParametersTest.java`, `AcceptanceTakesEffectTest.java`.
- [ ] T021 [US1] Family `wait` (`PR/session/PracticeSession.java` verdict hold, neighbour veto and confidence, technique and bend windows, technique lead, speed window; `PracticeParameters` early stability): sections `WAIT_1`, `WAIT_2`. Ordered pair: bend window min ≤ max. Tests `WaitParametersTest`, `WaitTakesEffectTest`.
- [ ] T022 [US1] Family `follow` (`PR/session/FollowSession.java` timings, technique and bend windows): sections `FOLLOW_1`…`FOLLOW_3`. Ordered pair: bend window min ≤ max. Tests `FollowParametersTest`, `FollowTakesEffectTest`.
- [ ] T023 [US1] Family `clock` (clarification 3: `PR/session/TickInterpolator.java` fit window, minimum points, minimum span, stall, extrapolation, seek margin; `PR/session/LatencyCompensation.java` limit; `CAPACITY` and `PlayerTempoClock.NOMINAL_TICKS_PER_SECOND` stay fixed): section `CLOCK`; tests `ClockParametersTest`, `ClockTakesEffectTest` (on the interpolator with a scripted tick source, not the real sequencer).
- [ ] T024 [US1] Family `timing` (`PR/session/TimingTolerance.java` reference beat, `PlayTimeWindow` advance and gap, `ClockUncertainty`): section `TIMING`; tests `TimingParametersTest`, `TimingTakesEffectTest`.
- [ ] T025 [US1] Families `follow-leniency`, `arcade-leniency` (`PR/session/FollowLeniency.java`, `PR/arcade/core/ArcadeLeniency.java`, one parameter per row and position, and Arcade's tempo window factors found inline). No ordered pair (inventory.md, "Ordered pairs"). Sections `LENIENCY_1`…`LENIENCY_4`; tests `LeniencyParametersTest`, `LeniencyTakesEffectTest`.
- [ ] T026 [US1] Family `technique` (`AE/technique/BendDetector.java`, `SlideDetector`, `VibratoDetector`, `LegatoDetector`, `PitchMotion`): sections `TECHNIQUE_1`…`TECHNIQUE_5` (with the frame confidence of Wait and Follow, inventory.md); tests `AET/settings/TechniqueParametersTest.java`, `AET/technique/TechniqueTakesEffectTest.java`.
- [ ] T027 [US1] Families `calibration`, `input-quality` (`PR/session/CalibrationRun.java` thresholds, `CalibrationTaps` outliers, `AE/calibration/RecognitionRate`, `RecordingQuality` levels, `SoundDetection` refractory and lead; `PR/session/InputQuality`; the level check `AE/analysis/InputAssessor.Parameters`, found inline, inventory.md). Ordered pair: calm below < loud above. Sections `CALIBRATION_1`…`CALIBRATION_3`, `LEVEL_CHECK`, `INPUT_QUALITY`; tests `CalibrationParametersTest`, `CalibrationTakesEffectTest`.
- [ ] T028 [US1] Gate (reports identical with each engine); commit "impl(026): phase 4 (US1): the practice session's, techniques' and calibration's values are parameters".

## Phase 5: User Story 3, every parameter documented (Priority: P2)

- [ ] T029 [P] [US3] New test `AET/settings/EngineConstantInventoryTest.java`. It finds every numeric `static final`, `Parameters.defaults()` literal and public numeric default field in `AE`, and requires each one to be in one of two places:
  - a **parameter** row of `SPEC/inventory.md` whose key is in `RecognitionParameters.ALL` and whose default equals the literal: the constant then serves only as that parameter's default;
  - or a **fixed** row of `DOC` section 6, with its kind and reason.

  A row is matched by its file and constant name, as written in the inventory and the documentation (research R9). It also fails on an inline number in a method body or constructor call outside the test's mathematical and unit allow-list, unless `inventory.md` lists it as fixed by file and text. Seen red against the current section 6.
- [ ] T030 [P] [US3] New test `PT/SessionConstantInventoryTest.java`: the same rule and matching as T029, inline numbers included, for `PR/session` and `PR/arcade/core`. Seen red.
- [ ] T031 [P] [US3] New crate test (`CRATE/analysis/mod.rs`, `#[cfg(test)]`): every `const` of `CRATE/analysis` is one of three things: an index of `Parameters` or `Tuning`, a default of `Tuning::defaults()` named in a parameter row of `SPEC/inventory.md`, or a fixed row of `DOC` section 6. It is matched by its file and name, as in T029, and inline numbers outside the allow-list are checked as in T029. Seen red.
- [ ] T032 [P] [US3] New test `AET/settings/EngineDocumentationFactsTest.java`: each key of `ALL` has a row in `DOC` with its unit, default, bounds, section and when it takes effect, matching the code. Seen red.
- [ ] T033 [US3] Rewrite `DOC`:
  - §3.4 (the `float[21]`, the tuning `double[]`, the new JNI functions and `tuningLayout`);
  - §4, one table per new section (the rows added by each family, completed with every fact T032 checks);
  - §6, only the fixed constants, grouped by kind with the reason.

  T029–T032 green, and `EngineDocumentationTest` still green.
- [ ] T034 [US3] Gate; commit "impl(026): phase 5 (US3): the engines' documentation lists every parameter and every fixed constant with its reason".

## Phase 6: Polish and cross-cutting concerns

- [ ] T035 [P] New test `AET/settings/RecognitionSettingsMigrationTest.java`: a configuration file written by v0.8.0-beta.7 and one written at the end of spec 024 are read with every stored value kept and every new key at its default (SC-005). Seen red with the reading of one stored key broken for the run (not committed), then green once it is put back.
- [ ] T036 [P] Review the strings and the three help pages (`desktop/TuxGuitar/share/help/tools_audio_recognition{,_fr,_es}.html`) in three languages; add the row the Spanish page lacks today (33 rows for 34 settings) and check the three pages have one row per parameter; every name and tip says what the value does and which way is more lenient or more sensitive, and ends with the expert sentence of the contract; the section names follow contracts/settings-window.md.
- [ ] T037 [P] Practice guide `docs/PRACTICE{,-FR,-ES}.md`: one paragraph on the Advanced categories. They are expert settings, *Restore* brings back the measured values, and the full list is in `DOC`.
- [ ] T038 [P] New sheet `specs/test-sheets/F16-parametres-experts.md` (SC-001, SC-007):
  - open each Advanced category and check that nothing overflows the screen;
  - change the onset sensitivity, start a Wait run and see fewer attacks;
  - *Restore*;
  - repeat with the native engine.
- [ ] T039 Draft the release notes `docs/release-notes/next-expert-parameters.md` (macOS, Linux and Windows alike; an older native library is never mixed in, since packages carry their own).
- [ ] T040 Full gate: `clean verify -P native-modules`, the reports identical with each engine, the CI on Ubuntu and macOS dispatched on the branch (the three wall-clock timing tests are left out of the CI runs by `.github/ci-timing-excludes.txt`, owner's decision of 2026-11-06, option A; they still run in every local gate, and the report says so). Windows has no CI build outside the release workflow (its Windows job cross-compiles on Linux, without tests): the final report states that Windows is verified only by that job at the next release, not before (constitution IX), and the release run must show the Windows job green before the release is announced.
- [ ] T041 Write `SPEC/final-report.md` (each FR and SC with its evidence, both engines, the reports' comparison) and update `specs/STATUS.md`. Commit "impl(026): phase 6: migration guard, strings, guide, sheet F16, final report".
- [ ] T042 With the Mac unlocked: `-P swt-gui-tests` on the whole branch, including `RecognitionSettingsFitSwtTest` and the existing settings window tests over the 35 new categories. Fix any failure under the bug protocol, commit the result, then one push of both refs.
- [ ] T043 [HUMAN] The owner's gate: sheet F16 on the built application.

## Dependencies & execution order

- Phase 1 → Phase 2 → Phases 3 and 4 → Phase 5 → Phase 6.
- T007 (the tuning array) blocks every engine family of phase 3; T009 (the pair repair) blocks every family with an ordered pair: T013, T014, T015, T018, T021, T022, T025 and T027.
- Within phase 3, the families touch different stages and could run in parallel, but they share `RecognitionParameters.java`, `NativeParameters.java`, `CRATE/analysis/mod.rs` and the message files, so they run one after the other.
- Phase 4 families touch different session classes; same shared files, same order.
- T029–T032 are [P]: separate new files.
- T042 needs the Mac unlocked; T043 needs the owner.

## Parallel example

```text
Phase 2: T004, T005, T006 (separate test files), then T007.
Phase 5: T029, T030, T031, T032 together, then T033.
Phase 6: T035, T036, T037, T038 together.
```

## Implementation strategy

- **MVP**: phases 1–3. Every engine stage value is tunable without a rebuild, with both engines.
- **Then**: phase 4 (session), phase 5 (documentation), phase 6.
- US2 (nothing changes) is the gate of every phase: the before/after comparison with each engine.
