# Tasks: Recognise notes and chords natively, without Java, at least as well and with less delay

**Input**: Design documents from `specs/023-native-recognition-engine/`
**Prerequisites**: plan.md, spec.md, research.md (R1–R10), data-model.md, contracts/native-recognition.md, quickstart.md

**Tests**: required (the owner's rules: tests first and seen red, never modify an existing test, only add new ones; if a committed test fails, fix the code under the bug protocol, at most 3 attempts, then stop and report).

**Paths**:
- `AE` = `desktop/TuxGuitar-audio-engine/src/main/java/app/tuxguitar/audio/engine`
- `AET` = `desktop/TuxGuitar-audio-engine/src/test/java/app/tuxguitar/audio/engine`
- `PR` = `desktop/TuxGuitar-practice/src/app/tuxguitar/app/tools/custom/practice`
- `TST` = `desktop/TuxGuitar-practice/test/app/tuxguitar/app/tools/custom/practice`
- `LANG` = `desktop/TuxGuitar-practice/share/lang`
- `CRATE` = `desktop/build-scripts/native-modules/tuxguitar-monitor-native`
- `BUILD` = `desktop/build-scripts/tuxguitar-macosx-swt-cocoa`
- `SPEC` = `specs/023-native-recognition-engine`

**Standing rules for every phase**:
- JDK 21 pinned; Rust 1.93.0.
- After each Maven build, `git checkout -- specs/003-arcade-mode/benchmark/`.
- Gate = `(cd CRATE && cargo test --release)` green, `mvn -o -e clean verify -P native-modules` from `BUILD` green, plus `swt-gui-tests` when a window changed, plus `git diff --name-status` on the test folders showing additions only.
- From T036 on, the gate also runs the audio-engine, practice and `TuxGuitar-tuner-next` tests (the standalone tuner reads its frames through `AudioEngine`, so it follows the engine) with `-Dtuxguitar.recognition.engine=native`, and from T064 on (Automatic = native) also with `-Dtuxguitar.recognition.engine=java`.
- No GPL code in `CRATE`: YIN is written from de Cheveigné & Kawahara (2002), TarsosDSP is not read while writing it.
- No allocation, lock or wait in the cpal callback or the native analysis loop (checked by `CRATE/src/no_allocation.rs`).
- Every device is opened at its current rate, never another one.
- Audible probes (tones through the speakers): warn the owner in the session just before running one, and keep it short.
- Commit each phase without any co-author trailer. One push per round, both refs together.
- Do not run the Release workflow.

## Format: `[ID] [P?] [Story] Description`

## Phase 1: Setup (measure first, research R1 and R8)

- [X] T001 Write `SPEC/baseline.txt`:
  - the current commit (HEAD) and the counts of the full gate, run now (expected as in `specs/022-guitar-amp-effects/baseline.txt` after its convergence), plus `swt-gui-tests`;
  - the list of existing tests that must stay unchanged and green, especially every test under `AET/analysis/`, `AET/frontend/`, `AET/recognition/`, `AET/input/`, `AET/settings/`, `AudioEngineTest`, `LiveEngineEquivalenceTest`, `AET/monitor/RecognitionUntouchedTest`, the practice verdict tests and the dataset benchmarks.
- [X] T002 Copy the Java dataset reports into `SPEC/benchmark/before/`:
  - from `desktop/TuxGuitar-practice/target/dataset-benchmark/` if it holds the run of commit 0ffd99c89;
  - otherwise run `DatasetBenchmarkTest`, `GuitarSetChordBenchmarkTest`, `TechniqueDatasetBenchmarkTest`, `OwnerChordsBenchmarkTest` and `BassRecognitionBenchmarkTest` now (quickstart §3 command, no engine property) and copy them, writing in `SPEC/benchmark/before/README.md` that no analysis code changed between 0ffd99c89 and the run's commit (`git diff --stat 0ffd99c89 -- desktop/TuxGuitar-audio-engine desktop/TuxGuitar-practice/src`).
  - Check that each SC-001 figure of the spec is found in these files; write any difference in the README and stop and report if one is lower.
- [X] T003 Create `TST/benchmark/RecognitionDelayProbeTest.java`, `@Tag("benchmark")`, excluded by default; it only measures (research R1):
  - it plays, through the default output (Java Sound), 20 short plucked tones (A2, D3, G3, B3, E4 in turn, 120 ms, 1.5 s apart) and runs the live `AudioEngine` on the built-in microphone, then on the USB microphone when one is listed;
  - for each tone, it measures from the tone's first sample (the output line's frame position, as spec 022's `MonitorLatencyProbeTest`) to (a) the first `AnalysisFrame` pitched within ±50 cents of the tone and (b) the first onset frame after the tone, both in the frames' `timestampNanos` base corrected by `CaptureClock`;
  - it writes, per engine (from `-Dtuxguitar.recognition.engine`, `java` by default) and per input, the tones played, the tones found, the median and 90th percentile of (a) and (b), appending a dated section to `SPEC/benchmark/recognition-delay.md`;
  - it fails only if no line or device can be opened, with the reason.
- [X] T004 Warn the owner (audible tones), then run T003 with `-Dtuxguitar.recognition.engine=java` (quickstart §1). Record the Java median and 90th percentile per input in `recognition-delay.md` and `baseline.txt`: this is SC-002's baseline.

**Checkpoint**: Java figures known, offline and live.

## Phase 2: Foundational (blocks every story)

**Purpose**: the record format, the parameters, the library loading and the engine resolution that both the offline and the live paths use.

### Tests first (each seen red: it fails to compile or fails before its code exists)

- [X] T005 [P] Create `AET/nativeengine/FrameRecordsTest.java` (data-model "Frame record"): a round trip `AnalysisFrame` → record → `AnalysisFrame` keeps every field (`timestampNanos`, `captureSampleEnd`, `arrivalNanos`, `rmsDbfs`, `peakDbfs`, `clippedRatio`, `levelState`, `f0Hz` NaN when unpitched, `confidence`, `onset`, `spectrum`, `targetEvidence`, `attackEvidence`, `chordEvidence`), each optional part present or absent, `techniqueFeatures` null; the largest record fits "a buffer of 4096 floats"; a record longer than its buffer is refused with an exception, never truncated silently.
- [X] T006 [P] Create `AET/nativeengine/NativeParametersTest.java`: every `RecognitionSettings` value read by `FrontEnd`, `LevelAnalyzer`, `ArticulationDetector`, `PolyphonicAnalyzer`, `AttackAnalyzer`, `ExpectedPitchVerifier`, `StrumClipMeter.Parameters.of` and the pitch estimator lands at its documented index of the `float[]`; the defaults and a changed profile give the expected arrays; the array length is the constant `NativeParameters.LENGTH`.
- [X] T007 [P] Create `AET/settings/PitchEngineKindTest.java` (contract §3, data-model "Recognition engine"):
  - `fastyin` → JAVA; `native` → NATIVE; `automatic` or any other value → the kind of the recommended recognition engine (Java until T064);
  - `-Dtuxguitar.recognition.engine=java|native` overrides *Automatic* only, never a hand-made `fastyin` or `native`; an empty or blank property counts as unset;
  - `native` asked while the library does not load → JAVA, with one log line per process.
- [X] T008 [P] Add the Rust tests (`#[cfg(test)]` modules) for `CRATE/src/analysis/fft.rs` (a sine lands in its bin with the magnitude and phase layout of `AE/analysis/RealFft.java`; the inverse gives the input back within 1e-6; no allocation after construction) and `CRATE/src/analysis/frame.rs` (the field offsets equal the Java ones listed in a shared table `CRATE/src/analysis/frame_layout.txt`, also read by `FrameRecordsTest`).

### Implementation

- [X] T009 Add `rustfft = "=6.2.0"` to `CRATE/Cargo.toml` and `mod analysis;` to `CRATE/src/lib.rs`; create `CRATE/src/analysis/mod.rs`, `fft.rs` (the `RealFft` layout over `rustfft`, plans built once) and `frame.rs` (the packed record writer) and `frame_layout.txt` (each field's name and offset, one per line); `cargo test` green on the three CI systems (no new system dependency).
- [X] T010 Create `AE/nativeengine/FrameRecords.java` (record ⇄ `AnalysisFrame`, using `frame_layout.txt`'s offsets as constants) and add `getChordEvidence()` to `AE/analysis/AnalysisFrame` (null by default, set through a new constructor overload; the existing constructors unchanged) so T005 passes.
- [X] T011 Create `AE/nativeengine/NativeParameters.java` (`RecognitionSettings` → `float[]`) and the matching `Parameters` struct in `CRATE/src/analysis/mod.rs`, so T006 passes.
- [X] T012 Create `AE/nativeengine/NativeRecognitionLibrary.java` and `NativeRecognitionException.java` (contract §1): the native method declarations, `load()` returning null or the failure text, the loading shared with `AE/monitor/NativeMonitorLibrary` so the library is loaded once; the exception's `reason` is one of `LIBRARY_MISSING`, `DEVICE_NOT_FOUND`, `PERMISSION_DENIED`, `FORMAT_UNSUPPORTED`, `STREAM_FAILED`, plus a `detail` for the log. Create `CRATE/src/jni_recognition.rs` with the symbols present and returning "not implemented" errors until US1.
- [X] T013 In `AE/settings/PitchEngineCatalog.java`, add `NATIVE = "native"`, `Engine.kind()` (JAVA or NATIVE) and `resolveKind(RecognitionSettings)` with the system property, so T007 passes. `getEngines()`, `getRecommended()` and `resolve()` stay exactly as today, now and after US2: the committed `RecognitionSettingsStoreTest` (lines 158–161) checks one engine, `fastyin` recommended and `resolve(AUTOMATIC)` equal to it. *Automatic* still means Java here.
- [X] T014 Pass `tuxguitar.recognition.engine` through surefire (`systemPropertyVariables` from the Maven property of the same name, empty by default) in `desktop/TuxGuitar-audio-engine/pom.xml` and `desktop/TuxGuitar-practice/pom.xml`, and give the tests the native library path as the spec 022 monitor tests already get it.

**Checkpoint**: gate green; nothing changes for the learner yet.

## Phase 3: User Story 1, part A - the native analysis, offline, at parity (Priority: P1) 🎯 MVP

**Goal**: every stage that reads samples runs in Rust and gives the Java figures (research R3); `OfflineAnalysis` can run either engine, so every dataset measures both.

**Independent test**: the parity tests within R3's tolerances, then the dataset benchmarks with `-Dtuxguitar.recognition.engine=native` against `benchmark/before/`.

- [X] T015 [US1] Declare in `AE/nativeengine/NativeRecognitionLibrary.java` and stub in `CRATE/src/jni_recognition.rs` the three entries of contract §1 added at analysis: `frontEnd` (parity test only), `strumClip`/`resetStrumClip` and `stats`. Nothing else is added to the contract during the implementation without the owner.

### Tests first (Rust tests in each module, and one Java parity test per stage)

Parity recordings: the audio-engine test resources already used by `LiveEngineEquivalenceTest`, plus `~/tuxguitar-datasets` (IDMT, GuitarSet, the owner's chords) when present, skipped by assumption otherwise as the dataset tests do. Shared helper: `AET/nativeengine/ParityRecordings.java`.

- [X] T016 [P] [US1] Create `AET/nativeengine/FrontEndParityTest.java` and the Rust tests of `CRATE/src/analysis/front_end.rs`: gain, high-pass, noise gate and spectral subtraction, hop by hop, within 1e-4 relative of `AE/frontend/FrontEnd`, with the default profile and a profile with every stage on.
- [X] T017 [P] [US1] Create `AET/nativeengine/LevelParityTest.java` and the Rust tests of `level.rs`: `rmsDbfs`, `peakDbfs` within 0.01 dB, `clippedRatio` within 1e-4, the same `levelState` on every frame.
- [X] T018 [P] [US1] Create `AET/nativeengine/PitchParityTest.java` and the Rust tests of `yin.rs` (difference function by FFT, cumulative mean normalised difference, absolute threshold, parabolic interpolation, from the paper): within 1 cent of `TarsosFastYinEstimator` when both are pitched; the same pitched/unpitched decision on ≥ 99.9 % of the frames; the same confidence within 1e-4. Synthetic tests for guitar and bass ranges and every tuning of the practice (lowest string of each tuning, 5-string bass B0), at 44.1, 48, 88.2 and 96 kHz.
- [X] T019 [P] [US1] Create `AET/nativeengine/OnsetParityTest.java` and the Rust tests of `articulation.rs`: the same onset hops as `ArticulationDetector`, in normal (`DECISION_DELAY_HOPS = 13`) and fast (8) mode.
- [X] T020 [P] [US1] Create `AET/nativeengine/SpectrumParityTest.java` and the Rust tests of `polyphonic.rs` (`PolyphonicAnalyzer`, `HarmonicComb`, `NnlsSolver`): band dB, salience, candidates and peaks within 1e-4 relative, the same candidate set on every frame.
- [X] T021 [P] [US1] Create `AET/nativeengine/AttackEvidenceParityTest.java` and the Rust tests of `attack.rs`: every `AttackEvidence` field within 1e-4 relative, for the candidate sets used by the practice's chord and note judges.
- [X] T022 [P] [US1] Create `AET/nativeengine/TargetEvidenceParityTest.java` and the Rust tests of `target.rs`: the hypotheses (midi, score) within 1e-4 and the same window in ms as `ExpectedPitchVerifier`.
- [X] T023 [P] [US1] Create `AET/nativeengine/ChordEvidenceParityTest.java` and the Rust tests of `chord.rs`: for the chords of the owner's set and GuitarSet, every field of `ChordEvidence.evaluate(spectrum, required, tolerated)` (pitches with salience, independent salience, harmonic-of-lower, mean and unique amplitude, unique partials, residual candidates) within 1e-4 relative.
- [X] T024 [P] [US1] Create `AET/nativeengine/StrumClipParityTest.java` and the Rust tests of `strum_clip.rs`: the same verdict, clipped share and mean level as `StrumClipMeter` on the parity recordings and on a synthetic clipped strum.
- [X] T025 [P] [US1] Extend the allocation check with a new Rust test in `CRATE/src/no_allocation.rs`'s test module: one second of hops through every stage (spectrum on, candidates set, pitches and chord expected) allocates nothing after warm-up.
- [X] T026 [P] [US1] Create `AET/OfflineAnalysisEngineDispatchTest.java` (contract §3): with `-Dtuxguitar.recognition.engine=java` (or `fastyin`) the frames are identical, field by field, to today's loop; with `native` they come from `NativeRecognitionLibrary.analyze`; a `HopListener` gets the same hops in both; `native` with the library missing gives Java and one log line; the existing signatures are unchanged.

### Implementation (in this order: each stage feeds the next)

- [X] T027 [US1] Write `CRATE/src/analysis/front_end.rs` and the `frontEnd` JNI entry in `jni_recognition.rs` (T016 green).
- [X] T028 [US1] Write `level.rs` (T017 green).
- [X] T029 [US1] Write `yin.rs` from the paper, with the threshold and window of the current setting (T018 green). Write in its header the paper reference and that TarsosDSP was not used.
- [X] T030 [US1] Write `articulation.rs` (T019 green).
- [X] T031 [US1] Write `polyphonic.rs` (T020 green).
- [X] T032 [US1] Write `attack.rs` and `target.rs` (T021, T022 green).
- [X] T033 [US1] Write `chord.rs` (T023 green).
- [X] T034 [US1] Write `strum_clip.rs` (T024 green).
- [X] T035 [US1] Write the offline pipeline and `analyze(...)` in `jni_recognition.rs`: the stages in the order of `AE/OfflineAnalysis` (window 2048, hop 256), deterministic, records through `frame.rs`; T025 green.
- [X] T036 [US1] Make `AE/OfflineAnalysis.analyze` dispatch on `PitchEngineCatalog.resolveKind` (contract §3): NATIVE → `NativeRecognitionLibrary.analyze` then `FrameRecords`; JAVA → today's loop unchanged (T026 green).

### Dataset gate (SC-001, FR-002, FR-007, FR-013)

- [X] T037 [US1] Create `TST/dataset/EngineComparisonReportTest.java`, `@Tag("benchmark")`: it reads `SPEC/benchmark/before/`, `after-java/` and `after-native/` and writes `SPEC/benchmark/comparison.md` with every SC-001 line (before, Java after, native after, pass/fail), the 942 tests' OK→KO list, the SC-002 offline delay lines and the SC-004 lines ("reached" / "not reached"). It fails on any SC-001 line lower with the native engine, on any SC-002 offline line not met (native GuitarSet chord delay median > 103 ms summed or > 149 ms microphone, or higher than the Java engine's on the same run), on any OK→KO, and if a Java outcome file differs from `before/` (FR-007); the files compared are `outcomes.csv` and `guitarset-chords-outcomes.csv` of `target/dataset-benchmark/`, copied into each folder. Seen red: `after-*` missing.
- [X] T038 [US1] Run the five dataset benchmarks with `-Dtuxguitar.recognition.engine=java` and then `native` (quickstart §3), copy each `target/dataset-benchmark/` into `SPEC/benchmark/after-java/` and `after-native/`, run T037. Any failing line: bug protocol on the native stage concerned (a test reproducing the difference first, at most 3 attempts), then report.
- [X] T039 [US1] Run the full gate twice: as is (Automatic = Java) and with `-Dtuxguitar.recognition.engine=native`. Every committed test must pass both ways, unchanged.

**Checkpoint**: the native analysis matches Java on every recording and every dataset; commit.

## Phase 4: User Story 1, part B - the native capture and the live engine (Priority: P1)

**Goal**: no Java Sound line and no Java analysis in a run with the native engine; the same frames reach the same listeners, sooner (research R4, R2).

**Independent test**: the live probe with both engines (SC-002), the endurance run (SC-005) and the amp on and off (FR-010).

### Tests first

- [X] T040 [P] [US1] Rust tests for `CRATE/src/capture.rs` and `CRATE/src/recognition.rs` (fed from a test ring, no device): blocks of 128 frames with their arrival copied in order, at 44.1, 48, 88.2 and 96 kHz; a full ring counts dropped blocks, never blocks the callback; the configuration slot is swapped at the top of the loop, the window is discarded and the state reset, as `AnalysisService.reconfigure`; the frame records produced live are identical to offline `analyze` on the same samples; `next_frames` returns the records, 0 on timeout, −1 after a stream error, −2 after close; hop blocks only while hops are enabled, a configure marker first after a reconfiguration; the loop allocates nothing (via `no_allocation.rs`).
- [X] T041 [P] [US1] Create `AET/backend/JavaBackendTest.java`: `JavaBackend` over today's `CaptureService` and `AnalysisService` (with `FakeAudioInput`) gives the frames today's `AudioEngine` gives, for the same input; `setExpectedChord` is a no-op.
- [X] T042 [P] [US1] Create `AET/backend/NativeBackendTest.java` with a fake `NativeRecognitionLibrary.Api` (an interface whose default implementation is the JNI one): frames from the records are delivered on one thread, never the UI thread, in order, `timestampNanos` strictly increasing; `CaptureClock` is fed from the records' sample end and arrival; −1 becomes the `CaptureService.Event` input-lost the practice already handles; dropped blocks are reported as the Java capture reports them; hop listeners get the hop blocks only while registered; `close` is idempotent and stops the thread within 500 ms.
- [X] T043 [P] [US1] Create `AET/AudioEngineBackendTest.java` (data-model "Backend"): the backend is chosen at the first `acquire` from `resolveKind`; a changed setting applies at the next `acquire` when no consumer holds the capture, never during a run; what consumers set (subscribers, hop listeners, capture listeners, spectrum flag, expected pitches, expected chord, attack candidates, front-end settings) is re-applied to a backend that opens later; the calibration, the input meter and the pre-run check go through the same backend (FR-008); only `AudioEngine.getInstance` chooses between the backends, and the package constructors used by `AudioEngineTest` and the other committed tests keep the Java backend unless a backend factory is passed, so a fake input is never bypassed; the device name and channel chosen in the practice settings reach the backend's `open` unchanged (`""` and −1 for the defaults, FR-009); with the native engine, no `CaptureService` is ever opened, not even for the input meter or the pre-run check (US1 AC1).
- [X] T044 [P] [US1] Create `TST/session/ChordMatcherFrameEvidenceTest.java` (contract §4): with a frame carrying the `ChordEvidence` of the chord being matched, `ChordMatcher` uses it and does not call `ChordEvidence.evaluate`; without it (Java engine, or another chord), it computes it as today; the accept/refuse result is the same in both cases on the recorded chords.
- [X] T045 [P] [US1] Create `AET/backend/NativeStrumClipTest.java`: on the native backend, the pre-run check's strum reading comes from `strumClip`/`resetStrumClip` with the same verdicts as `StrumClipMeter` on the same samples.

### Implementation

- [X] T046 [US1] Write `CRATE/src/capture.rs`: an input-only cpal stream on the practice input (`""` = default, channel −1 = first) at the device's current rate, blocks of 128 frames, the callback copying the block and its `Instant` into the lock-free ring, nothing else; errors mapped to the five reasons.
- [X] T047 [US1] Write `CRATE/src/recognition.rs`: the analysis thread (promoted through `realtime.rs` as in spec 022), the configuration slot, the frame and hop rings, the condition variable waited on by `next_frames`/`next_hops` only, the clock `javaNanosAtOpen + (arrival − openInstant)`, the dropped-block counter, the cost histogram, `strum_clip`. T040 green.
- [X] T048 [US1] Complete `CRATE/src/jni_recognition.rs`: `open`, `sampleRate`, `configure`, `setSpectrumEnabled`, `setExpectedPitches`, `setExpectedChord`, `setAttackCandidates`, `nextFrames`, `nextHops`, `setHopsEnabled`, `droppedBlocks`, `stats`, `strumClip`, `resetStrumClip`, `close` (contract §1, as amended in T015).
- [X] T049 [US1] Create `AE/backend/RecognitionBackend.java` (contract §2) and `AE/backend/JavaBackend.java`, which moves the use of `CaptureService` and `AnalysisService` out of `AudioEngine` without editing those two classes (T041 green).
- [X] T050 [US1] Create the interface `NativeRecognitionLibrary.Api` (the entries of contract §1, its default implementation calling the JNI methods) and `AE/backend/NativeBackend.java` over it, with its frame thread and hop thread (T042, T045 green).
- [X] T051 [US1] Make `AE/AudioEngine.java` use the seam and choose the backend at `acquire`, the choice wired in `getInstance` only, the package constructors keeping the Java backend unless given a backend factory (T043 green); route the pre-run check's strum meter to the native reading on the native backend; the tuner keeps working through `AudioEngine` unchanged; the public API used by the committed tests stays as it is (`getInstance` a singleton, `getLevelAnalyzer()`, `getArticulationDetector()` and `getDroppedSampleCount()` reflecting the settings and the drops with either backend), and `getInstance` opens no device before the first `acquire`.
- [X] T052 [US1] Make `PR/session/ChordMatcher.java` use the frame's evidence when present, and have the session give the expected chord to `AudioEngine.setExpectedChord` beside the expected pitches (T044 green).
- [X] T053 [US1] Gate, both ways (as is and `-Dtuxguitar.recognition.engine=native`), including `LiveEngineEquivalenceTest` and `AET/monitor/RecognitionUntouchedTest` unchanged.

### Live measurements (the owner's Mac)

- [X] T054 [US1] Warn the owner (audible tones), then run T003 with `-Dtuxguitar.recognition.engine=native`. SC-002: the median at least 20 ms below Java's (T004) on each input, the 90th percentile not higher. Record the device's nominal rate before the open and after the close: they must be equal (the rate is never changed). If not met: find where the delay goes (capture lag envelope, ring wait, decision hops) with a reproducing measurement, at most 3 attempts, then report with the figures.
- [ ] T055 [US1] Create `TST/benchmark/DualEngineCableProbeTest.java`, `@Tag("benchmark")` (research R1, SC-002 for the Rocksmith cable, which cannot hear the speakers): it opens the practice input with both engines at once, a Java backend and a native backend; it waits for 20 notes played by the owner (an onset with a pitched frame in each engine within 300 ms of each other); for each note it takes the gap between the Java frame and the native frame of the same onset, both in the `System.nanoTime` base after `CaptureClock`; it writes the median and 90th percentile of the gap, and of each engine's onset-to-pitched delay, to `recognition-delay.md`; it fails only if an engine cannot open the input, and stops after 2 minutes with the notes found.
- [ ] T056 [HUMAN] [US1] **Non-blocking: the implementation goes on; the figure is confirmed at T087.** The owner plugs in the Rocksmith cable and plays 20 single notes, about one a second, while T055 runs (`quickstart.md` §5). SC-002 for the cable: the native frame at least 20 ms ahead of the Java frame at the median, and the native 90th percentile not higher than Java's.
- [X] T057 [P] [US1] Create `AET/benchmark/NativeRecognitionEnduranceBenchmarkTest.java`, `@Tag("benchmark")`, on the pattern of `AET/monitor/RealtimeEnduranceBenchmarkTest`: 10 minutes of live native recognition on the built-in microphone, spectrum on, candidates set; it reports dropped blocks (0 expected, SC-005) and the per-hop cost median and 99th percentile from `stats` (under 10 % of a hop, 0.53 ms at 48 kHz, at the 99th percentile, R9) into `SPEC/benchmark/endurance.md` and `SPEC/benchmark/cost.md`, and fails above those limits. Run it (silent: capture only).
- [X] T058 [US1] Run the dataset identity of spec 022 (T061 there) and `RecognitionUntouchedTest` with `-Dtuxguitar.recognition.engine=native`, amp sound on and off (FR-010, SC-005); record in `SPEC/benchmark/amp-identity.md`. On a system where the second open fails, the amp says so (R6): note it for sheet F13.

**Checkpoint**: US1 works live with the property; commit.

## Phase 5: User Story 2 - I choose the recognition engine in the menu (Priority: P1)

**Goal**: *Automatic* (native if it starts), *Native (Rust)* and *Java (FastYin)* in the settings; a failed native start falls back to Java with a notice (FR-004 to FR-006).

**Independent test**: switch the setting, restart, check the engine used; make the native open fail and check the notice and Java.

### Tests first

- [X] T059 [P] [US2] Create `AET/settings/PitchEngineCatalogNativeTest.java`: `getEngines()`, `getRecommended()` and `resolve()` are unchanged (one Java engine, `fastyin`); the new `getRecognitionEngines()` lists the native engine first when the library loads, then Java; only Java when it does not; `getRecommendedRecognitionEngine()` is the first listed; the names in that list are "Native (Rust)" and "Java (FastYin)"; a stored `fastyin` stays Java; any unknown value reads as `automatic`; the stored key stays `recognition.engine.pitch`.
- [X] T060 [P] [US2] Create `AET/backend/RecognitionFallbackTest.java` with the fake library failing with LIBRARY_MISSING, DEVICE_NOT_FOUND, FORMAT_UNSUPPORTED and STREAM_FAILED, and with a library that does not load: the Java backend runs; exactly one `RecognitionEngineNotice(reason, detail)` per run is sent; the run's first frame comes within 2 s (SC-003); a native `open` that never returns is abandoned after 1.5 s and does not hang `acquire`; no exception reaches the caller. With PERMISSION_DENIED, no fallback is tried: today's microphone-permission event reaches the practice, as with the Java capture, and no notice is sent.
- [X] T061 [P] [US2] Create `TST/ui/RecognitionEngineSettingSwtTest.java` (swt-gui-tests): the choice lists *Automatic (Native)* or *Automatic (Java)*, then *Native (Rust)* when it loads, then *Java (FastYin)*, each with its tip; a choice is stored and read back after the store is reopened (restart); a change applies from the next run without a restart.
- [X] T062 [P] [US2] Create `TST/ui/RecognitionEngineNoticeSwtTest.java` (swt-gui-tests): the practice window shows the notice once per reason per session, says the practice goes on with Java, and never blocks the run.
- [X] T063 [P] [US2] Create `TST/ui/RecognitionEngineMessagesTest.java`: the keys `recognition.engine.pitch.option.native`, `recognition.engine.pitch.option.fastyin`, `recognition.engine.pitch.tip.v2` and `recognition.engine.notice.<reason>` for the four reasons that fall back exist and are not empty in `LANG/messages.properties`, `messages_fr.properties` and `messages_es.properties`; the old keys are still there.

### Implementation

- [X] T064 [US2] Add `getRecognitionEngines()` and `getRecommendedRecognitionEngine()` to `AE/settings/PitchEngineCatalog.java` and make `resolveKind` use them, leaving `getEngines()`, `getRecommended()` and `resolve()` unchanged (T059 green, `RecognitionSettingsStoreTest` unchanged and green). From here *Automatic* means native wherever the library loads, so the whole suite runs native by default: run the gate both ways (as is, and `-Dtuxguitar.recognition.engine=java`) before going on.
- [X] T065 [US2] Create `AE/backend/RecognitionEngineNotice.java` and the fallback in `AudioEngine`, with no fallback for PERMISSION_DENIED (T060 green).
- [X] T066 [US2] Add the new keys in the three `LANG/messages*.properties` (English, French, Spanish), keeping the old ones (T063 green).
- [X] T067 [US2] Update `PR/ui/RecognitionSettingsPanel.java` (the options from `getRecognitionEngines()`, Automatic's name, the tips; the setting keeps its name *Pitch detection engine*) and show the notice in `PR/ui/PracticeWindow.java` through `AppPracticeHost`, so `PracticeWindow.java` gains none of the calls `PracticeHostSourceTest` forbids (`AudioEngine.getInstance(`, `new Thread(`, `Thread.sleep(`, `System.nanoTime(`) (T061, T062 green); gate with `swt-gui-tests`.

**Checkpoint**: the engine is chosen in the menu, Automatic is native, the fallback works; commit.

## Phase 6: User Story 1, part C - Arcade at the screen's rate (Priority: P1, FR-015, SC-006)

**Goal**: Arcade holds the display's rate (120 on the owner's Mac) with the native engine live, no frame waits on the recognition, and the hit shows sooner than with Java (research R7).

### Tests first

- [X] T068 [P] [US1] Create `TST/arcade/ArcadeFrameSourceNoWaitTest.java`: while the recognition thread is blocked in `nextFrames` (fake library), Arcade's frame source returns each frame within 1 ms, reading the latest published verdicts.
- [X] T069 [P] [US1] Create `TST/arcade/ArcadeFramePeriodTest.java`: for a display rate of 60 and of 120 Hz, the frame period is 1/rate, the tick is half of it and `QualityController`'s budget follows; with no rate known, today's 60 Hz values.
- [X] T070 [P] [US1] Create `TST/benchmark/ArcadeRefreshRateBenchmarkTest.java`, `@Tag("benchmark")`: 10 minutes of Arcade on a demo score with the native engine live on the built-in microphone; it reads the display rate through the toolkit or measures it from the paint intervals, and writes the share of frames on time, the frames held and the paint 95th percentile to `SPEC/benchmark/arcade-refresh.md`; fails under 99 % on time or with any frame held. FR-011: the run is repeated with the Java engine, and the song's position drift against the wall clock at the end and the paint 95th percentile must be no worse with the native engine.
- [X] T071 [P] [US1] Create `TST/benchmark/ArcadeHitDelayProbeTest.java`, `@Tag("benchmark")`: T003's tones played into an Arcade run; it measures from the tone to the hit painted (`ArcadeCanvas`'s `FrameObserver`), per engine, and appends it to `recognition-delay.md`; it fails if the native median exceeds T054's native median plus one display frame (FR-015), or if the native median or 90th percentile is higher than the Java engine's (SC-006).

### Implementation

- [X] T072 [US1] Run T070 on the owner's Mac (silent) before any change; record it.
- [X] T073 [US1] In every case, derive the frame and tick periods and `QualityController`'s budget from the display rate in `PR/arcade/ui/ArcadeCanvas.java`, with today's 60 Hz values when no rate is known (T069 green). Then run T070 again; if T072 was under 99 % and T070 still is, find where the frames go late with a reproducing measurement, at most 3 attempts, then report. Write the before and after figures in `arcade-refresh.md`.
- [X] T074 [US1] Make the frame source read a volatile snapshot of the verdicts (T068 green), if it does not already.
- [X] T075 [US1] Run spec 003's Arcade benchmarks (`ArcadeRenderBenchmarkTest` and the stress run) unchanged: within their current limits (SC-006). Warn the owner, then run T071 with both engines: the hit shown sooner with the native engine, at the median and the 90th percentile.

**Checkpoint**: Arcade in real time; commit.

## Phase 7: User Story 3 - Chords recognised better (Priority: P2, FR-012, SC-004)

**Goal**: more of the owner's chords accepted, fewer neighbours wrongly accepted, more GuitarSet chords found, a whole tone higher refused more often, with every SC-001 line still met. The targets are aimed at, not blocking.

**Protocol for each experiment**: a Rust test of the new behaviour first; the experiment behind a native parameter (off = parity); the chord benchmarks and the comparison (T037) run with it on; kept only if every SC-001 line holds and at least one SC-004 line moves toward its target; recorded in `SPEC/benchmark/chord-experiments.md` (what, why, figures, kept or dropped). Deterministic methods only (constitution VIII). A change to a shared Java rule (`PolyphonicAcceptance`, `ChordMatcher`'s rule) is measured on both engines and recorded (FR-007).

- [X] T076 [US3] Create `SPEC/benchmark/chord-experiments.md` with the protocol above and the starting figures (from `comparison.md`): owner's chords 26/42 and 13/42, GuitarSet 33/56 and 23/56, a whole tone higher 66.7 %.
- [X] T077 [US3] Experiment A, the neighbour shifted by a whole tone (`CRATE/src/analysis/chord.rs`): refuse when the shifted chord explains the spectrum's partials better than the expected one by a margin; Rust test on synthetic chords first.
- [X] T078 [US3] Experiment B, the strum spread (`attack.rs`, `chord.rs`): accumulate the chord evidence over the hops of one strum (the string attacks spread over up to 80 ms) instead of the single best frame, for the microphone input; Rust test first.
- [X] T079 [US3] Experiment C, the missing partials of low strings (`polyphonic.rs`, `chord.rs`): accept a required pitch whose fundamental is weak when its second and third partials are present and not explained by a lower required pitch; Rust test first.
- [X] T080 [US3] Keep the experiments that passed (their parameter on by default in the native engine), run both engines on every dataset once more, regenerate `after-native/`, `after-java/` and `comparison.md`, and write for each SC-004 target whether it was reached.

**Checkpoint**: chords measured and improved where possible; commit.

## Phase 8: Polish & cross-cutting concerns

- [X] T081 [P] In `.github/workflows/macos-maven.yml`, add a second test pass of `TuxGuitar-audio-engine`, `TuxGuitar-practice` and `TuxGuitar-tuner-next` with `-Dtuxguitar.recognition.engine=java`; check that `ubuntu-maven.yml` and the Windows build of `release.yml` build the crate with `rustfft` (no edit expected; edit only if a build step needs it). Do not run the Release workflow.
- [X] T082 [P] Write the manual sheet `specs/test-sheets/F13-moteur-natif.md` (quickstart §6, plus rows for Linux and Windows testers: the engine shown by *Automatic*, the felt delay, the fallback with the library renamed, the amp on together with the recognition, a raw ALSA `hw:` device) and add it to `specs/test-sheets/README.md`.
- [X] T083 [P] Describe the recognition engine (Automatic, Native, Java, the notice, "the same judging, sooner") in `docs/PRACTICE.md`, `docs/PRACTICE-FR.md` and `docs/PRACTICE-ES.md`.
- [X] T084 [P] Draft the release notes of the next TuxGuitarAdvanced version in `docs/release-notes/`: the native engine on macOS, Linux and Windows, the delay gained on the owner's Mac, the chords figures, Arcade at 120 Hz, and the call to Linux and Windows testers through sheet F13 (delay felt, fallback, amp together). The version is chosen at release time; nothing is released before T087.
- [X] T085 Full gate: `cargo test --release`, `mvn -o -e clean verify -P native-modules` (Automatic = native), the same tests with `-Dtuxguitar.recognition.engine=java`, `swt-gui-tests`; record the counts in `baseline.txt`; the test folders show additions only.
- [X] T086 Write `SPEC/final-report.md`: each SC (001 to 006) and FR with its figure and status, the parity tolerances reached per stage, the chord experiments kept and dropped, the risks left (ALSA `hw:`), and what waits for the owner and the testers.
- [ ] T087 [HUMAN] The owner's gate on the relaunched app, per sheet F13, with the Rocksmith cable and headphones: the engine choice and its restart, Wait, Follow and Arcade felt quicker than with Java, the fallback with the library renamed (within 2 s), the amp sound on with the recognition unchanged. The cable delay measured with the owner at T056 (both engines at once) is confirmed here, and the owner's answer (felt quicker or not) is written in `final-report.md`.

## Dependencies & execution order

- Phase 1 (the Java baseline, T004) → Phase 2 → US1 part A (offline parity, Phase 3) → US1 part B (live, Phase 4) → US2 (Phase 5) → US1 part C (Arcade, Phase 6) → US3 (Phase 7) → Phase 8.
- US2's catalog (T064) switches *Automatic* to native: it comes after the live path passed its gates (T053 to T055, T057, T058; T056, the owner's cable session, does not block and is confirmed at T087), so the default never points to an untested engine.
- Arcade (Phase 6) needs the live native engine (Phase 4) and benefits from US2's default; US3 needs the offline parity and the comparison (T037).
- In Phase 3 the stage implementations run in order T027 → T035 (each stage feeds the next), the parity tests before them can be written together.
- Inside a phase, tests come first and are seen red; [P] tasks touch different files.

## Parallel examples

- Phase 2: T005, T006, T007 and T008 together.
- US1 part A: T016 to T026 together (one parity test per stage), then T027 to T036 in order.
- US1 part B: T040 to T045 together, then T046 → T048 (Rust) alongside T049 (Java); T057 once T048 is done.
- US2: T059 to T063 together.
- Arcade: T068 to T071 together.
- Phase 8: T081, T082, T083 and T084 together.

## Implementation strategy

- **MVP = Phase 1 + Phase 2 + US1 parts A and B**: the native engine recognises live without Java, with the Java figures on every dataset and the delay measured against Java. It is reachable through the property only, so nothing changes for the learner until US2.
- Then the menu and *Automatic* (US2), Arcade at the screen's rate, and the chords (US3), each one gated and committed.
- The Java engine stays the working path until the native one passes its dataset and live gates.
- **No release** before the owner's gate T087.
