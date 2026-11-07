# Implementation Plan: Every recognition engine value is an external parameter

**Branch**: `feature/interactive-practice` (spec directory `specs/026-engine-external-parameters`) | **Date**: 2026-11-06 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/026-engine-external-parameters/spec.md`

## Summary

192 values (inventory.md) that change what is heard or accepted, fixed today in the Java engine, the native engine and the practice session, become recognition settings:
- stored and repaired like the 34 existing ones;
- read once when a run opens;
- passed to the native library in a second `double[]` (the tuning array) beside today's unchanged `float[21]`, with a layout check;
- shown in the Recognition settings window in 35 new "Advanced: …" categories of at most 7 values;
- documented in `docs/architecture/recognition-engines.md`.

No default changes. Every dataset report is compared byte for byte before and after, with each engine. The structural sizes, buffers, numerical margins, plumbing, clocks, score and display stay fixed and are documented with their reason (clarifications of 2026-11-06).

## Technical Context

**Language/Version**: Java, release 11 for the three fork modules (`TuxGuitar-audio-engine`, `TuxGuitar-practice`, `TuxGuitar-tuner-next`); Rust (crate `tuxguitar-monitor-native`); JDK 21 locally.

**Primary Dependencies**: existing only (TarsosDSP inside the Java engine; `jni`, `cpal` in the crate). No new dependency.

**Storage**: TuxGuitar configuration module `tuxguitar-audio-recognition` (`RecognitionSettingsStore`), `recognition.version` stays 1.

**Testing**: JUnit 5 (`TuxGuitar-audio-engine/src/test/java`, `TuxGuitar-practice/test`), SWT GUI tests (`-P swt-gui-tests`, Mac unlocked), `cargo test` through the native-modules build, the dataset reports (`-Dgroups=dataset`) with each engine.

**Target Platform**: macOS (validated locally, tests and SWT), Linux (CI build with tests), Windows (only the release workflow's cross-build, without tests, verified at the next release, not before); all three packages carry the same parameters (FR-011).

**Project Type**: desktop application, plugin modules plus a native library.

**Performance Goals**: none new; the analysis loop's cost is unchanged (values are fields read like the constants were).

**Constraints**:
- no allocation, lock or wait in the cpal callback or the analysis loop (FR-010);
- every figure identical with the defaults (SC-002);
- no GPL code in the crate;
- the window fits a 768-pixel-high screen (SC-007).

**Scale/Scope**:
- 192 parameters, 35 categories, about 1,150 new strings (a name and a tip for each, in 3 languages);
- the source of about 40 Java classes and 10 crate files.

## Constitution Check

| Principle | Status |
|---|---|
| I. Compatibility | Pass: additive keys; an older configuration file reads every new key as its default (FR-007); no song format touched. |
| II. Extend before core | Pass: the code changes only in the fork modules and the crate; nothing in `common/`. In `desktop/TuxGuitar`, only the three help pages `share/help/tools_audio_recognition{,_fr,_es}.html` change, as spec 024 did: they already document the 34 settings, and the committed `RecognitionControlsTest` requires a row for each. The strings go in the practice plugin's message files (`desktop/TuxGuitar-practice/share/lang`). |
| III. Evidence | Pass: research R1–R11 cite the inspected files. |
| IV. Spec-driven | Pass: spec, two clarifications and the owner's three answers before this plan. |
| V. Incremental | Pass: one family group per phase, each with its before/after comparison (tasks). |
| VI. Real-time | Pass: values built before the run, sizes allocated at the bound at `open` (R4, R6). |
| VII. Separable logic | Pass: stages keep their value objects; tests build them without UI. |
| VIII. Deterministic DSP | Pass: no algorithm changes. |
| IX. Cross-platform | Pass: the crate and Java are built for the three platforms; validated on macOS, Linux/Windows by CI (stated as such). |
| X. No unrelated refactoring | Pass: each constant becomes a field of its stage's value object; nothing else moves. |
| XI. Build and test integrity | Pass, with one point to watch: committed tests that build a stage with a constant name (for example `ArticulationDetector.DEFAULT_FLUX_MEDIAN_FACTOR`) keep compiling: the public constants stay, as the default of their parameter. No committed test is edited. |
| XII. Truthful reporting | Pass: before/after reports kept in `benchmark/`. |
| XIII–XIV. Guitar semantics, learner feedback | Not affected: no behaviour changes; the tips say these are expert settings. |

Post-design re-check: pass. The design adds no core change and no new dependency. The only interface change is additive: the tuning `double[]`, new JNI functions and `tuningLayout()`, with today's functions unchanged. The owner approved it.

## Project Structure

### Documentation (this feature)

```text
specs/026-engine-external-parameters/
├── spec.md, plan.md, research.md, data-model.md, quickstart.md
├── contracts/native-parameters.md, contracts/settings-window.md
├── inventory.md            (task T002: one row per parameter and per fixed constant)
├── benchmark/              (before/after reports per engine)
└── tasks.md                (/speckit-tasks)
```

### Source Code (repository root)

```text
desktop/TuxGuitar-audio-engine/src/main/java/app/tuxguitar/audio/engine/
├── settings/RecognitionParameter.java      (new Section values)
├── settings/RecognitionParameters.java     (new parameters, ALL, repair of ordered pairs in RecognitionSettings)
├── analysis/  (ArticulationDetector, PolyphonicAnalyzer, AttackAnalyzer, AttackEvidence, ChordEvidence, StrumClipMeter, TarsosFastYinEstimator users)
├── recognition/ExpectedPitchVerifier.java, pitch/TolerancePolicy.java
├── frontend/  (HighPass, NoiseEstimator, NoiseGate, SpectralSubtractor, InputGain, GainSuggester)
├── technique/ (BendDetector, SlideDetector, VibratoDetector, LegatoDetector, PitchMotion)
├── calibration/ (RecognitionRate, RecordingQuality, SoundDetection)
├── nativeengine/NativeParameters.java, NativeRecognitionLibrary.java   (tuning double[], new JNI functions, tuningLayout)
└── AudioEngine.java                        (builds the stages from the settings)
desktop/TuxGuitar-audio-engine/src/test/java/…  (new tests only)

desktop/TuxGuitar-practice/src/app/tuxguitar/app/tools/custom/practice/
├── session/ (PolyphonicAcceptance, PracticeParameters, PracticeSession, FollowSession, FollowLeniency, InputQuality,
│             PlayTimeWindow, TimingTolerance, ClockUncertainty, AttackCandidates, CalibrationRun, CalibrationTaps)
├── arcade/core/ArcadeLeniency.java
└── ui/RecognitionControls.java, RecognitionSettingsPanel.java   (only if a new section needs it; the panel already lists Section.values())
desktop/TuxGuitar-practice/test/…            (new tests only)

desktop/build-scripts/native-modules/tuxguitar-monitor-native/src/
├── analysis/mod.rs (Parameters unchanged; new Tuning, TUNING_LENGTH, exact length)
├── analysis/{articulation,polyphonic,attack,chord,strum_clip,front_end,target,yin}.rs
└── jni_recognition.rs (openTuned, openAnalysisTuned, configureTuning, analyzeTuned, tuningLayout)

desktop/TuxGuitar-practice/share/lang/messages.properties, messages_fr.properties, messages_es.properties   (names, tips, section names)
desktop/TuxGuitar/share/help/tools_audio_recognition{,_fr,_es}.html   (one row per parameter, one heading per section)
docs/architecture/recognition-engines.md    (sections 3.4, 4 and 6)
```

**Structure Decision**: the existing modules only. Every parameter joins `RecognitionParameters` (one list, one store, one window). Every stage keeps its value-object pattern.

## Phases (for /speckit-tasks)

1. **Baseline and inventory**:
   - the before reports with each engine (R10);
   - `inventory.md`;
   - the inventory tests, seen red (they list every constant not yet a parameter).
2. **Foundation**:
   - the new `Section` values, the ordered-pair repair, and the window's 768-pixel SWT test;
   - the native tuning `double[]`, its JNI functions and `tuningLayout()`, with an empty-but-checked layout first.
   - Gate: identical reports.
3. **US1, native-engine stages**: onset, onset fast, pitch, polyphonic, attack, chord evidence, strum, front end, noise gate, gain. In Java and the crate together, family by family, each with its "takes effect" test with each engine. Gate: identical reports.
4. **US1, session**: acceptance, Wait, Follow, player clock, timing, leniency rows, techniques, calibration, input quality. Gate: identical reports.
5. **US3, documentation**:
   - section 6 holds only the fixed constants with their reasons;
   - every parameter is documented with its six facts;
   - the inventory tests turn green.
6. **Polish**:
   - the strings in three languages;
   - the old-configuration test (SC-005);
   - the full gate with SWT (Mac unlocked);
   - the final report and the release notes draft (all OS alike).

US2 ("nothing changes") is the gate of every phase, not a phase of its own.

## Complexity Tracking

| Item | Why needed | Simpler alternative rejected because |
|---|---|---|
| 35 new categories in the drop-down | 192 values at most 7 per category (the row budget that keeps the window on a 768-pixel screen) | one scrolled category: the owner asked for more Advanced categories rather than overflow |
| A second array (tuning `double[]`) and new JNI functions beside today's | committed tests use the `float[21]` interface and the `Api` methods directly; the new values must keep their exact `double` (SC-002) | changing the `float[]` breaks committed tests (owner's rule); growing it loses precision and an older library would accept it silently |
