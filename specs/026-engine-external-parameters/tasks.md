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
- **Each family task** (an engine family puts its native values in the tuning array, or in the `float[21]` for the five of today's indices that were fixed):
  1. a new test `…ParametersTest` asserts the family's keys, sections (at most 7 per section), units, defaults (equal to the code's literal, compared with `==`), bounds and the ordered pairs; seen red;
  2. the parameters in `AE/settings/RecognitionParameters.java` (`ALL`, in display order), with their name and tip in `LANG/messages{,_fr,_es}.properties` (contracts/settings-window.md);
  3. the stage reads them from its value object built once when the run opens (research R4); its public constants stay, as the parameter's default;
  4. a new "takes effect" test: a non-default value changes the stage's output, with the Java engine and, for an engine stage, with the native engine; seen red.
- **Gate** (end of each phase):
  - `mvn -o -e clean verify -P native-modules` from `BUILD` is green;
  - the dataset reports with each engine are identical to `SPEC/benchmark/before-<engine>/` (quickstart §2);
  - `git diff --name-status <phase start> -- '*test*'` shows only added files;
  - no SWT run overnight or with the Mac locked: the SWT runs are grouped in T040.
- **Real time:** nothing new is read, allocated, locked or awaited per frame or per hop.
- **Commits and pushes:**
  - commit each phase without any co-author trailer;
  - one push per round, both refs together, after the SWT run of T040.

## Format: `[ID] [P?] [Story] Description`

## Phase 1: Setup (the figures before, the inventory)

- [ ] T001 Write `SPEC/baseline.txt`:
  - HEAD;
  - the counts of the full gate;
  - the committed tests that read the classes this spec changes (`ArticulationDetector*`, `PolyphonicAnalyzer*`, `AttackAnalyzer*`, `ChordEvidence*`, `StrumClipMeter*`, the front-end tests, `NativeParameters*`, the parity tests, `RecognitionSettings*`, `RecognitionControlsTest`, `RecognitionSettingsPanelTest`, `RecognitionSettingsSwtTest`, `PolyphonicAcceptance*`, `PracticeParametersTest`, `FollowLeniencyTest`, `ArcadeLeniencyTest`, the technique, calibration and timing tests).
- [ ] T002 Run every dataset and benchmark report with each engine at the current code (quickstart §1, research R10): `DatasetBenchmarkTest`, `GuitarSetChordBenchmarkTest`, `TechniqueDatasetBenchmarkTest`, `OwnerChordsBenchmarkTest`, `OwnerChordsProfileTest`, `ChordLeniencyLevelsTest`, `BassRecognitionBenchmarkTest`, `SyntheticChordGuardTest`, `RecordedPracticeReplayTest` (if its local files are there), and the `benchmark` tag. Copy the reports into `SPEC/benchmark/before-java/` and `before-native/`. Write `SPEC/benchmark/compare.sh`: it masks dates and durations, then runs `diff -r`.
- [ ] T003 Write `SPEC/inventory.md` from the scan of research R1 (Java engine, session, Arcade core, crate). It has one row per value:
  - **parameter**: key, section, unit, default (the literal), bounds, takes effect, file and constant, Java and crate counterparts;
  - **fixed**: kind and reason, from the table in research R1.

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
- [ ] T008 [P] New test `AET/settings/RecognitionSectionsTest.java`:
  - the 8 existing sections keep their order and content;
  - every section holds 1 to 7 parameters;
  - no parameter of a new section is in `ProfileParameters.LIST` (a device profile never overlays them; research R8);
  - the new ordered-pair rule (`AE/settings/OrderedPair.java`): built on any two `Number` parameters, it brings the lower one of an inverted pair back within the upper one, and `RecognitionSettings.fromStrings` applies every pair of `RecognitionParameters.ORDERED_PAIRS` (empty until the families fill it).

  Seen red (the class does not exist).
- [ ] T009 Add `OrderedPair` and `RecognitionParameters.ORDERED_PAIRS`, applied in `AE/settings/RecognitionSettings.java` beside the existing `note.accept` ≥ `tuner.in-tune` rule. T008 green.
- [ ] T010 [P] New SWT test `PT/ui/RecognitionSettingsFitSwtTest.java` (`swt-gui-tests`): with the usable height forced to 768, every category's every control and its *Restore* button lie inside the window without scrolling, as `PracticeWindowFitsSwtTest` (spec 025) does. Written now, run in T040.
- [ ] T011 Gate (with the reports identical); commit "impl(026): phase 2: the native tuning array with its layout check, the ordered-pair repair".

## Phase 3: User Story 1, the engine stages (Priority: P1) 🎯 MVP

**Goal**: every value of the engine stages is a parameter read identically by both engines.

**Independent test**: each family's "takes effect" test, with each engine; the reports identical.

- [ ] T012 [US1] Family `onset` and `onset-fast` (`AE/analysis/ArticulationDetector.java`, `NativeParameters` windows, `PracticeParameters.WEAK_FAST_DBFS`, `CRATE/analysis/articulation.rs`): sections `ONSET`, `ONSET_FAST`; new tests `AET/settings/OnsetParametersTest.java` and `AET/analysis/OnsetTakesEffectTest.java` (Java and native).
- [ ] T013 [US1] Family `pitch` (YIN threshold, `AE/recognition/ExpectedPitchVerifier.java` search and periods with `CRATE/analysis/target.rs`, `AE/pitch/TolerancePolicy.java`, `PR/session/AttackCandidates.java` range): section `PITCH`; tests `PitchParametersTest`, `PitchTakesEffectTest`.
- [ ] T014 [US1] Family `polyphonic` (`AE/analysis/PolyphonicAnalyzer.java` except the FFT size and the band range, `CRATE/analysis/polyphonic.rs`): sections `POLYPHONIC_1`, `POLYPHONIC_2`. The candidate count and the harmonics are allocated at their upper bound (research R6). Tests `PolyphonicParametersTest`, `PolyphonicTakesEffectTest`. A new crate test with the existing `CRATE/no_allocation.rs` harness: `configureTuning` with every value of the family at its upper bound, then hops analysed, allocates nothing (FR-010, research R6).
- [ ] T015 [US1] Family `attack` (`AE/analysis/AttackAnalyzer.java` except the FFT size, `AE/analysis/AttackEvidence.java`, `CRATE/analysis/attack.rs`): sections `ATTACK_1`, `ATTACK_2`; the lag ring is allocated at its bound. Tests `AttackParametersTest`, `AttackTakesEffectTest`. A new crate test with the existing `CRATE/no_allocation.rs` harness: `configureTuning` with every value of the family at its upper bound, then hops analysed, allocates nothing (FR-010, research R6).
- [ ] T016 [US1] Family `chord-evidence` (`AE/analysis/ChordEvidence.java`, `CRATE/analysis/chord.rs`, `AE/analysis/StrumClipMeter.java`, `CRATE/analysis/strum_clip.rs`): section `CHORD_EVIDENCE`; tests `ChordEvidenceParametersTest`, `ChordEvidenceTakesEffectTest`.
- [ ] T017 [US1] Families `front-end`, `noise-gate`, `gain` (`AE/frontend/*`, `CRATE/analysis/front_end.rs`): sections `FRONT_END`, `NOISE_GATE`, `GAIN`. Ordered pair: `noise-gate.open` > `noise-gate.close`. Tests `FrontEndParametersTest`, `FrontEndTakesEffectTest`. A new crate test with the existing `CRATE/no_allocation.rs` harness: `configureTuning` with every value of the family at its upper bound, then hops analysed, allocates nothing (FR-010, research R6).
- [ ] T018 [US1] Gate (reports identical with each engine); commit "impl(026): phase 3 (US1): the engine stages' values are parameters, read alike by both engines".

## Phase 4: User Story 1, the practice session (Priority: P1)

**Goal**: every value of the session, the techniques and the calibration that changes what is accepted is a parameter.

**Independent test**: each family's "takes effect" test; the reports identical.

- [ ] T019 [US1] Family `acceptance` (`PR/session/PolyphonicAcceptance.java`): sections `ACCEPTANCE_1`, `ACCEPTANCE_2`; tests `PT/session/AcceptanceParametersTest.java`, `AcceptanceTakesEffectTest.java`.
- [ ] T020 [US1] Family `wait` (`PR/session/PracticeSession.java` verdict hold, neighbour veto and confidence, technique and bend windows, technique lead, speed window; `PracticeParameters` early stability): sections `WAIT_1`, `WAIT_2`; tests `WaitParametersTest`, `WaitTakesEffectTest`.
- [ ] T021 [US1] Family `follow` (`PR/session/FollowSession.java` timings, technique and bend windows): sections `FOLLOW_1`…`FOLLOW_3`; tests `FollowParametersTest`, `FollowTakesEffectTest`.
- [ ] T022 [US1] Family `timing` (`PR/session/TimingTolerance.java` reference beat, `PlayTimeWindow` advance and gap, `ClockUncertainty`): section `TIMING`; tests `TimingParametersTest`, `TimingTakesEffectTest`.
- [ ] T023 [US1] Families `follow-leniency`, `arcade-leniency` (`PR/session/FollowLeniency.java`, `PR/arcade/core/ArcadeLeniency.java`, one parameter per row and position). Ordered pair: very permissive at least as lenient as permissive. Sections `LENIENCY_1`…`LENIENCY_4`; tests `LeniencyParametersTest`, `LeniencyTakesEffectTest`.
- [ ] T024 [US1] Family `technique` (`AE/technique/BendDetector.java`, `SlideDetector`, `VibratoDetector`, `LegatoDetector`, `PitchMotion`): sections `TECHNIQUE_1`…`TECHNIQUE_4`; tests `AET/settings/TechniqueParametersTest.java`, `AET/technique/TechniqueTakesEffectTest.java`.
- [ ] T025 [US1] Families `calibration`, `input-quality` (`PR/session/CalibrationRun.java` thresholds, `CalibrationTaps` outliers, `AE/calibration/RecognitionRate`, `RecordingQuality` levels, `SoundDetection` refractory and lead; `PR/session/InputQuality`). Ordered pair: calm below < loud above. Sections `CALIBRATION_1`…`CALIBRATION_3`, `INPUT_QUALITY`; tests `CalibrationParametersTest`, `CalibrationTakesEffectTest`.
- [ ] T026 [US1] Gate (reports identical with each engine); commit "impl(026): phase 4 (US1): the practice session's, techniques' and calibration's values are parameters".

## Phase 5: User Story 3, every parameter documented (Priority: P2)

- [ ] T027 [P] [US3] New test `AET/settings/EngineConstantInventoryTest.java`. It finds every numeric `static final`, `Parameters.defaults()` literal and public numeric default field in `AE`, and requires each one to be in one of two places:
  - a **parameter** row of `SPEC/inventory.md` whose key is in `RecognitionParameters.ALL` and whose default equals the literal: the constant then serves only as that parameter's default;
  - or a **fixed** row of `DOC` section 6, with its kind and reason.

  A row is matched by its file and constant name, as written in the inventory and the documentation (research R9). Seen red against the current section 6.
- [ ] T028 [P] [US3] New test `PT/SessionConstantInventoryTest.java`: the same rule and matching as T027, for `PR/session` and `PR/arcade/core`. Seen red.
- [ ] T029 [P] [US3] New crate test (`CRATE/analysis/mod.rs`, `#[cfg(test)]`): every `const` of `CRATE/analysis` is one of three things: an index of `Parameters` or `Tuning`, a default of `Tuning::defaults()` named in a parameter row of `SPEC/inventory.md`, or a fixed row of `DOC` section 6. It is matched by its file and name, as in T027. Seen red.
- [ ] T030 [P] [US3] New test `AET/settings/EngineDocumentationFactsTest.java`: each key of `ALL` has a row in `DOC` with its unit, default, bounds, section and when it takes effect, matching the code. Seen red.
- [ ] T031 [US3] Rewrite `DOC`:
  - §3.4 (the `float[21]`, the tuning `double[]`, the new JNI functions and `tuningLayout`);
  - §4, one table per new section;
  - §6, only the fixed constants, grouped by kind with the reason.

  T027–T030 green, and `EngineDocumentationTest` still green.
- [ ] T032 [US3] Gate; commit "impl(026): phase 5 (US3): the engines' documentation lists every parameter and every fixed constant with its reason".

## Phase 6: Polish and cross-cutting concerns

- [ ] T033 [P] New test `AET/settings/RecognitionSettingsMigrationTest.java`: a configuration file written by v0.8.0-beta.7 and one written at the end of spec 024 are read with every stored value kept and every new key at its default (SC-005). Seen red only if a value is lost; otherwise record it green at once, as a guard.
- [ ] T034 [P] Review the strings in three languages: every name and tip says what the value does and which way is more lenient or more sensitive, and ends with the expert sentence of the contract; the section names follow contracts/settings-window.md.
- [ ] T035 [P] Practice guide `docs/PRACTICE{,-FR,-ES}.md`: one paragraph on the Advanced categories. They are expert settings, *Restore* brings back the measured values, and the full list is in `DOC`.
- [ ] T036 [P] New sheet `specs/test-sheets/F16-parametres-experts.md` (SC-001, SC-007):
  - open each Advanced category and check that nothing overflows the screen;
  - change the onset sensitivity, start a Wait run and see fewer attacks;
  - *Restore*;
  - repeat with the native engine.
- [ ] T037 Draft the release notes `docs/release-notes/next-expert-parameters.md` (macOS, Linux and Windows alike; an older native library is never mixed in, since packages carry their own).
- [ ] T038 Full gate: `clean verify -P native-modules`, the reports identical with each engine, the CI on Ubuntu and macOS dispatched on the branch (its timing tests reported apart, as in memory `ci-timing-tests-flaky`). Windows has no CI build outside the release workflow (its Windows job cross-compiles on Linux, without tests): the final report states that Windows is verified only by that job at the next release, not before (constitution IX), and the release run must show the Windows job green before the release is announced.
- [ ] T039 Write `SPEC/final-report.md` (each FR and SC with its evidence, both engines, the reports' comparison) and update `specs/STATUS.md`. Commit "impl(026): phase 6: migration guard, strings, guide, sheet F16, final report".
- [ ] T040 With the Mac unlocked: `-P swt-gui-tests` on the whole branch, including `RecognitionSettingsFitSwtTest` and the existing settings window tests over the 31 new categories. Fix any failure under the bug protocol, commit the result, then one push of both refs.
- [ ] T041 [HUMAN] The owner's gate: sheet F16 on the built application.

## Dependencies & execution order

- Phase 1 → Phase 2 → Phases 3 and 4 → Phase 5 → Phase 6.
- T007 (the tuning array) blocks every engine family of phase 3; T009 (the pair repair) blocks T017, T023 and T025.
- Within phase 3, the families touch different stages and could run in parallel, but they share `RecognitionParameters.java`, `NativeParameters.java`, `CRATE/analysis/mod.rs` and the message files, so they run one after the other.
- Phase 4 families touch different session classes; same shared files, same order.
- T027–T030 are [P]: separate new files.
- T040 needs the Mac unlocked; T041 needs the owner.

## Parallel example

```text
Phase 2: T004, T005, T006 (separate test files), then T007.
Phase 5: T027, T028, T029, T030 together, then T031.
Phase 6: T033, T034, T035, T036 together.
```

## Implementation strategy

- **MVP**: phases 1–3. Every engine stage value is tunable without a rebuild, with both engines.
- **Then**: phase 4 (session), phase 5 (documentation), phase 6.
- US2 (nothing changes) is the gate of every phase: the before/after comparison with each engine.
