# Implementation Plan: Recognise notes and chords natively, without Java, at least as well and with less delay

**Branch**: `feature/interactive-practice` (spec directory `023-native-recognition-engine`) | **Date**: 2026-11-03 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/023-native-recognition-engine/spec.md`

## Summary

Today every sample the practice hears goes through Java:
- `CaptureService` reads Java Sound in reads of 1024 frames, with a 4-read buffer and bursts of up to 130 ms of lag;
- `AnalysisService` then runs the front end, level, FastYin pitch, onset, polyphonic spectrum and attack and target evidence on a Java thread, and the practice computes the chord evidence from the spectrum on the frame thread;
- it publishes `AnalysisFrame`s to the practice, the techniques and the tuner (research R2).

The plan moves **everything that reads samples** into the native crate of spec 022 (Rust). The capture runs in blocks of 128 frames, each dated at its callback. The analysis is a faithful port of the fork's Java analysers, with YIN written from the paper. The native engine hands Java the **same frames**, so the practice rules, the techniques and the tuner work unchanged. The standalone tuner follows the selected engine too, since it reads its frames through `AudioEngine`.

1. **Measure first (R1).** A live probe measures the Java engine's delay from a tone to its frame on the owner's Mac. That is SC-002's baseline, and the Java dataset figures are copied as "before" (R8).
2. **Faithful native analysis (R3), offline first.** Every stage is ported with a parity test against the Java stage on recordings. `OfflineAnalysis` dispatches on the engine of the settings, so every dataset benchmark and replay can run either engine (R8).
3. **Native capture and live engine (R4).** The engine is reached through a `RecognitionBackend` seam in `AudioEngine`, with a Java backend (today's code, moved, not changed) and a native backend. The choice applies from the next run, and if the native engine does not start it falls back to Java with a notice (R5).
4. **The menu (US2).** The engine catalog gets the native engine, and *Automatic* is the default (native if it loads). A hand-made Java choice is kept. Texts are in three languages.
5. **Arcade at the screen's rate (R7).** It is measured at 120 Hz on the owner's Mac, the frame period follows the display if needed, and the recognition never holds a paint.
6. **Chords better (US3).** These improvements come after parity, in the native engine (and in shared Java rules when measured on both), kept only when every SC-001 line holds.
7. **Compare and report.** Both engines are run on every dataset, compared line by line against SC-001 and SC-004, and the live delay is reported against SC-002 and SC-006.

## Technical Context

**Language/Version**:
- Rust 1.93.0 (CI pin), for the native engine, in the existing crate `desktop/build-scripts/native-modules/tuxguitar-monitor-native` (edition 2021).
- Java release 11, for `TuxGuitar-audio-engine` and `TuxGuitar-practice`; JDK 21 locally.

**Primary Dependencies**:
- Already in the crate: `cpal` 0.15.3 and `jni` 0.21.1.
- New: `rustfft` 6.2.0 (MIT/Apache-2.0, pure Rust, three systems). It is used for the FFTs of YIN, the onset flux and the spectrum.
- Java: unchanged. TarsosDSP stays for the Java engine only.

**Storage**: the existing recognition settings (`RecognitionSettingsStore`, key `recognition.engine.pitch`), with a new value `native`. There is no new file.

**Testing**:
- JUnit 5:
  - parity tests per stage, Java against native;
  - backend and fallback tests with a fake native library;
  - settings and catalog tests;
  - SWT tests for the menu.
- `cargo test` for the crate's stages and rings, with no allocation in the loops.
- `@Tag("benchmark")` tests, run on the owner's Mac:
  - the datasets, for each engine;
  - the live probes: speakers to microphone, and both engines at once on the Rocksmith cable (R1);
  - the cost;
  - the endurance run;
  - Arcade at the display's rate.

**Target Platform**: macOS 15+ (the owner's MacBook, Apple Silicon, ProMotion), Linux (ubuntu-24.04 CI, PipeWire/PulseAudio desktops) and Windows (WASAPI shared mode).

**Project Type**: desktop application (fork modules plus the native crate).

**Performance Goals**:
- **SC-002:** the live median from strike to frame is ≥ 20 ms lower than Java's, the 90th percentile is not higher, and the offline delay is not higher.
- **SC-006:**
  - Arcade has 99 % of its frames on time at the display's rate (120 on the owner's Mac);
  - no frame waits on the recognition;
  - the native analysis of one hop stays under 10 % of a hop (0.53 ms at 48 kHz, a hop of 256 samples being 5.3 ms) at the 99th percentile.

**Constraints**:
- SC-001: every dataset line is at least Java's.
- The device's rate is never changed (cpal opens at the current rate).
- No GPL code goes in the crate.
- No allocation or lock in the callback or the analysis loop.
- Committed tests are not changed.

**Scale/Scope**:
- About 3,700 lines of Java analysis are ported: `AE/analysis` (including `ChordEvidence`), `AE/frontend`, `AE/recognition`, and the measure of `StrumClipMeter`.
- About 1,000 lines of frame consumers stay in Java (`AE/technique`, `AE/pitch`).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status |
|---|---|
| I. Preserve compatibility | **Pass.** Additive. The Java engine stays and is selectable. The settings file gains one value, and `fastyin` keeps its meaning. No song format is touched. |
| II. Extend before modifying core | **Pass.** No change in `common/` or `desktop/TuxGuitar`. All changes are in the fork's modules (`TuxGuitar-audio-engine`, `TuxGuitar-practice`) and the fork's crate. |
| III. Evidence-based | **Pass.** Every decision cites inspected files (research R1–R10). |
| IV. Spec-driven | **Pass.** The spec is clarified (6 answers, 2026-11-03). This plan comes before any code. |
| V. Incremental delivery | **Pass.** The phases are: measure, offline parity, live backend, menu, Arcade, chords, report. Each one builds and is tested on its own, and the Java engine stays the working path until the native one passes its gates. |
| VI. Real-time audio | **Pass by design.** The callback copies only. The analysis thread has no allocation or lock, which is tested. The Java frame thread is the same as today's publish. Delays are stated (SC-002). Device loss and permission problems degrade into a notice and a fall back to Java. |
| VII. Separable, testable audio logic | **Pass.** Each native stage is a pure function over buffers, tested by `cargo test` and by Java parity tests offline. Only `capture.rs` touches hardware. |
| VIII. Deterministic signal processing | **Pass.** It is a port of the deterministic analysers, with YIN from the paper. US3 improvements stay deterministic, and no machine learning is used. |
| IX. Cross-platform | **Pass.** One Rust code for three systems, built in CI. The platforms validated live are stated (macOS by the owner; Linux and Windows by testers, sheet F13). |
| X. No unrelated refactoring | **Pass, with one justified move.** `CaptureService` and `AnalysisService` move behind `RecognitionBackend` unchanged. The move is needed to choose the engine, and the classes' code is not edited. |
| XI. Build and test integrity | **Pass.** Committed tests are not changed. They run with the native engine by default and with Java through the property, and both must pass. |
| XII. Truthful reporting | **Pass.** The before and after reports per engine are kept in `benchmark/`, and SC-004 is reported as reached or not. |
| XIII. Honest audio semantics | **Pass.** The same properties are established from audio as today. Nothing new is claimed. |
| XIV. Learner-centred feedback / licences / strings | **Pass.** The fallback notice says why and that practice goes on. Strings are in three languages. `rustfft` is MIT/Apache-2.0, compatible with LGPL. TarsosDSP (GPL-3.0) is not ported (YIN from the paper). |

There are no unjustified violations.

**Re-check after Phase 1 design:** still passes. The contract keeps the `AnalysisFrame` fields, so no consumer changes. The seam is in the fork's module.

## Project Structure

### Documentation (this feature)

```text
specs/023-native-recognition-engine/
├── spec.md
├── plan.md              # this file
├── research.md          # R1–R10
├── data-model.md
├── quickstart.md
├── contracts/
│   └── native-recognition.md
├── checklists/requirements.md
└── benchmark/           # before/, after-java/, after-native/, comparison.md, recognition-delay.md, arcade-refresh.md, cost.md
```

### Source Code (repository root)

```text
desktop/build-scripts/native-modules/tuxguitar-monitor-native/
├── Cargo.toml                    # + rustfft = "=6.2.0"
└── src/
    ├── lib.rs                    # + mod capture, analysis, recognition, jni_recognition
    ├── capture.rs                # input-only cpal stream, blocks + arrival into a ring (R4)
    ├── recognition.rs            # analysis thread, configuration slot, frame and hop rings
    ├── jni_recognition.rs        # open/configure/next_frames/next_hops/close/analyze
    └── analysis/
        ├── mod.rs, fft.rs        # RealFft layout over rustfft
        ├── front_end.rs          # FrontEnd: gain, high-pass, gate, spectral subtraction
        ├── level.rs              # LevelAnalyzer
        ├── yin.rs                # YIN from the paper (R3)
        ├── articulation.rs       # ArticulationDetector
        ├── polyphonic.rs         # PolyphonicAnalyzer, HarmonicComb, NnlsSolver
        ├── attack.rs             # AttackAnalyzer → AttackEvidence
        ├── target.rs             # ExpectedPitchVerifier → TargetEvidence
        ├── chord.rs              # ChordEvidence.evaluate for the expected chord (R2)
        ├── strum_clip.rs         # StrumClipMeter's measure
        ├── frame.rs              # the packed frame record (contract)
        └── frame_layout.txt      # the record's field offsets, read by frame.rs's tests and by FrameRecordsTest

desktop/TuxGuitar-audio-engine/src/main/java/app/tuxguitar/audio/engine/
├── AudioEngine.java              # uses RecognitionBackend; engine chosen at acquire; fallback notice
├── OfflineAnalysis.java          # dispatches on the engine (R4, R8)
├── backend/
│   ├── RecognitionBackend.java   # the seam (contract)
│   ├── JavaBackend.java          # CaptureService + AnalysisService, unchanged
│   ├── NativeBackend.java        # over NativeRecognitionLibrary
│   └── RecognitionEngineNotice.java
├── nativeengine/
│   ├── NativeRecognitionLibrary.java   # native methods; loading shared with NativeMonitorLibrary
│   ├── NativeRecognitionException.java
│   ├── FrameRecords.java         # record ⇄ AnalysisFrame
│   └── NativeParameters.java     # RecognitionSettings → float[] parameters
└── settings/PitchEngineCatalog.java    # + NATIVE, kind(), recommended = native when it loads

desktop/TuxGuitar-audio-engine/src/test/java/...  # parity, backend, fallback, catalog, offline dispatch, cost
desktop/TuxGuitar-practice/
├── src/.../ui/RecognitionSettingsPanel.java   # options and Automatic's name (existing panel)
├── src/.../ui/PracticeWindow.java             # shows the fallback notice once
├── src/.../session/ChordMatcher.java          # uses the frame's ChordEvidence when present (R2)
├── src/.../arcade/ui/ArcadeCanvas.java         # frame and tick periods derived from the display's rate, 60 Hz when unknown (R7)
├── share/lang/messages{,_fr,_es}.properties    # new keys
└── test/...                                    # comparison report, live probe, Arcade refresh, SWT menu
.github/workflows/*.yml                         # macOS job: audio-engine, practice and tuner-next tests once more with -Dtuxguitar.recognition.engine=java
specs/test-sheets/F13-moteur-natif.md
```

**Structure Decision:**
- The native engine lives in the spec 022 crate, so there is one native library, already packaged on three systems.
- The Java side is in `TuxGuitar-audio-engine`, the module that owns capture and analysis.
- The practice module only gets the menu, the notice and Arcade's frame period.

## Phases

1. **Measure:** R1's Java probe and the "before" copy of the datasets.
2. **Offline native analysis:**
   - the stages one by one, each with its Rust test and its parity test;
   - then `OfflineAnalysis` dispatch;
   - then the datasets with the native engine (SC-001 gate).
3. **Live:**
   - the capture, the analysis thread and the JNI;
   - `RecognitionBackend`, `NativeBackend` and the fallback;
   - the live probe (SC-002) and the endurance run (SC-005);
   - the amp on and off (FR-010).
4. **Menu:** the catalog, Automatic, the texts and the notice (US2).
5. **Arcade:** the measurement at 120 Hz, the frame period if needed, and the no-wait test (SC-006).
6. **Chords (US3):** measured improvements, kept or dropped per SC-001.
7. **Polish:**
   - the comparison report, the CI's second pass with Java, sheet F13 and the user guide in three languages;
   - the release notes with the testing request for Linux and Windows;
   - the final report.

## Complexity Tracking

| Choice | Why needed | Simpler alternative rejected because |
|---|---|---|
| Port about 3,700 lines of analysis to Rust | The owner's goal: no Java between the input and the frame (spec Input). | Porting only the pitch leaves the Java capture, where the delay is (R1). |
| A `RecognitionBackend` seam in `AudioEngine` | To choose the engine at the next run and fall back to Java. | Two `AudioEngine` classes would duplicate the tuner and practice wiring. |
| YIN rewritten from the paper | Keeps GPL-3.0 code out of the LGPL crate. | Translating TarsosDSP would put GPL code in the crate. |
| A second CI pass with the Java engine (macOS) | To keep the Java engine tested once *Automatic* makes the suite run the native one. | Without it, the Java engine would go untested. |
