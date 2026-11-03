# Research: native recognition engine (spec 023)

Inspected source (constitution III):
- the audio engine: `desktop/TuxGuitar-audio-engine/src/main/java/app/tuxguitar/audio/engine/`, written below as `AE/`;
- the native crate of spec 022: `desktop/build-scripts/native-modules/tuxguitar-monitor-native/`;
- the practice module: `desktop/TuxGuitar-practice/`.

## R1 — Where the delay of the Java path comes from, and how it is measured

**Facts from the source.**
- `AE/input/CaptureService.java` reads Java Sound in reads of `FRAMES_PER_READ = 1024` frames (21 ms at 48 kHz). It opens the line with a buffer of 4 reads (about 85 ms) and copies the samples into `SampleRingBuffer`.
- `AE/input/CaptureClock.java` measured, on the built-in microphone, reads arriving in bursts, with an arrival jitter of up to 65 ms and a lag of up to 130 ms. Its javadoc says so, and so does spec 002 R6c. The clock dates a sample by its count plus the *lowest* lag seen over 10 s.
- `AE/analysis/AnalysisService.java#run` polls the ring and sleeps 1 ms when it is empty (`pauseBriefly`). Its window is 2048 samples (42.7 ms at 48 kHz) with a hop of 256 (5.3 ms), from `TarsosFastYinEstimator.DEFAULT_WINDOW_SIZE` / `DEFAULT_HOP_SIZE`.
- `AE/analysis/ArticulationDetector` decides an onset after `DECISION_DELAY_HOPS = 13` hops, or `FAST_DECISION_DELAY_HOPS = 8`.
- Spec 022's probe (`specs/022-guitar-amp-effects/benchmark/`) measured:
  - Java Sound: 37 ms at best and 86 to 126 ms in practice, for a round trip;
  - the native cpal device: 32.5 ms for the round trip (USB microphone, 128 frames);
  - the built-in microphone's hardware: 31 ms on its own.

**Consequence.** The analysis itself (window, onset decision) costs the same in either engine as long as the algorithms are the same. The gain of the native path comes from three places:
1. the capture: blocks of 128 frames instead of reads of 1024 with a 4-read buffer and bursts;
2. a dated arrival for every block, giving a much tighter lag envelope;
3. no polling sleep.

The 20 ms target of SC-002 has to come from these. R1's measurement is what proves it.

**Decision: measure, then build.**
- A live probe (`RecognitionDelayProbeTest`, `@Tag("benchmark")`) plays a short plucked tone through the Mac's speakers and captures it with the built-in microphone, and with the USB microphone when one is plugged in.
- It measures, for each engine, the time from the tone's start (the output's sample clock, as in spec 022's probe) to two moments:
  - the first `AnalysisFrame` with that pitch (`pitched` and within ±50 cents);
  - the onset flag.
- It runs 20 tones per input. The result is a median, a 90th percentile and the share of tones found, in `benchmark/recognition-delay.md`.
- **The Rocksmith cable** cannot hear the speakers, so its gain is measured another way: `DualEngineCableProbeTest` runs both engines at once on the cable (two clients of one input, R6), the owner plays 20 notes, and the probe takes, for each note, the gap between the Java frame and the native frame of the same onset, in the same `System.nanoTime` base. Its median is SC-002's figure for the cable.
- The Java figures of this probe are the baseline of SC-002. The probe's tones are audible, so the owner is warned before each run, which is kept short.
- **Offline**, the delay of the GuitarSet chord benchmark (median 103 ms summed, 149 ms microphone) is kept as the dataset-side figure. It depends only on the analysis, so both engines are expected to give the same figure there. SC-002's offline line is therefore "not higher". The live line carries the gain.

**Alternatives considered.**
- *Lower the window or the hop to gain delay:* both change what the analysis sees and what the practice rules count in hops (`ArticulationDetector`, `PolyphonicAcceptance` hops 6). They are left for US3, measured, and kept only if every SC-001 line holds.
- *Measure with the Rocksmith cable only:* it is not always plugged in. The built-in and USB microphones are always available, and the cable is added to sheet F13 for the owner.

## R2 — What leaves Java, and what stays

**Facts.** In the live engine (`AE/AudioEngine.java`), one chain does everything:
- `CaptureService` puts the samples in `SampleRingBuffer`;
- `AnalysisService` runs, per hop:
  - `FrontEnd`: gain, high-pass, noise gate, spectral subtraction (`AE/frontend/`);
  - the hop listeners;
  - `PolyphonicAnalyzer` (spectrum, `HarmonicComb`, `NnlsSolver`), when enabled;
  - `AttackAnalyzer` → `AttackEvidence`, when candidates are set;
- and, per window:
  - `LevelAnalyzer`;
  - the `PitchEstimator` (`TarsosFastYinEstimator`);
  - `ArticulationDetector`;
  - `ExpectedPitchVerifier` → `TargetEvidence`, when pitches are expected;
- it then publishes an `AnalysisFrame` to the listeners.

`OfflineAnalysis` runs the same stages for tests and benchmarks.

The consumers of the frames are of three kinds:
- the practice rules (`PolyphonicAcceptance`, the session classes, the Follow and Arcade judges);
- the technique detectors (`AE/technique/`: bend, slide, vibrato and legato, over the pitch track of the frames);
- the tuner (`TuxGuitar-tuner-next/.../TunerWindow.java`).

The hop listeners work on samples:
- `StrumClipMeter`, for the pre-run check;
- the calibration's `HopRecorder`, `SoundCounter` and `ProfileActivator`.

**Decision.**
- **The native engine does everything that reads samples:**
  - the capture;
  - the front end;
  - the level;
  - the pitch;
  - the onset;
  - the polyphonic spectrum;
  - the attack evidence;
  - the target evidence;
  - the strum clip meter's measure;
  - **the chord evidence.** Today `ChordEvidence.evaluate(spectrum, required, tolerated)` (`AE/analysis/ChordEvidence.java`, 217 lines, using `NnlsSolver`) is called by the practice's `session/ChordMatcher.java` on the frame thread. With the native engine:
    - the practice gives the expected chord (required and tolerated pitches) through `setExpectedChord`, beside `setExpectedPitches`;
    - each frame carries the native `ChordEvidence` for it;
    - `ChordMatcher` uses the frame's evidence when present, and computes it as today when it is absent (Java engine).

    The accept and refuse thresholds (`PolyphonicAcceptance`, `ChordMatcher`'s rule) stay in Java.
- It hands Java the same frames: the same fields as `AnalysisFrame`, so every consumer works unchanged.
- **Java keeps what reads frames:**
  - the practice rules, the scores and the user interface;
  - the technique detectors, which work over the pitch track of the frames and cost microseconds per frame;
  - the tuner.

  So "without Java" (spec Input, FR-001) holds where the delay and the cost are: no sample is captured or analysed by Java.
- **The hop listeners** still get the samples, because the calibration *records* them (spec 013). With the native engine:
  - the samples come from the native capture (after the native front end), copied into a ring that a Java thread empties;
  - they are not on the path of the verdicts.

  `StrumClipMeter` is ported, because its measure is analysis. The calibration's recorder stays a Java consumer of samples, which is the owner's recording, not the recognition.

**Alternatives considered.**
- *Port the technique detectors too:* no gain in delay or cost, since they read frames that are already there. It would double 1027 lines of tested Java for nothing measurable. It can come later if the owner wants zero Java in the chain.
- *Keep the Java front end and port only the pitch:* the capture's bursts would stay, and that is where the delay is (R1).

## R3 — How the analysis is written in Rust (quality at least equal)

**Facts.**
- The analysers in `AE/analysis/`, `AE/frontend/` and `AE/recognition/` are 3,719 lines of the fork's own code (LGPL), written for the specs 001 to 016. They are covered by the unit tests of `TuxGuitar-audio-engine/src/test/java` and by the dataset benchmarks.
- Only the pitch estimator wraps a third-party library: TarsosDSP's `FastYin` (GPL-3.0), which the audio engine is the only module to use (CLAUDE.md).

**Decision.**
1. **Faithful port first.** Each Java analyser gets a Rust counterpart with the same algorithm, the same parameters (read from the same `RecognitionSettings` values), the same float widths and the same order of operations. A parity test per stage compares the native frames with the Java frames on recordings: the datasets plus the recorded takes of `TuxGuitar-practice/test`.
   - level: within 0.01 dB;
   - pitch: within 1 cent when both are pitched, and the same pitched/unpitched decision on ≥ 99.9 % of the frames;
   - onset: the same hops;
   - spectrum and evidences: within 1e-4 relative.

   A faithful port makes SC-001 hold by construction. The dataset benchmarks then confirm it.
2. **YIN written from the paper, not translated from TarsosDSP.** De Cheveigné and Kawahara (2002):
   - the difference function computed with an FFT, as FastYin does;
   - the cumulative mean normalised difference;
   - the absolute threshold;
   - parabolic interpolation.

   It uses the same threshold and window as the current setting. This keeps the crate free of GPL code (constitution, licences). The parity test of item 1 shows the result matches FastYin within 1 cent.
3. **Improvements (US3) only after parity.** They are made in the native engine, behind the parity gate. Each one is kept only if every SC-001 line holds and is measured. The Java engine is not changed (FR-007), except where the change is in a Java *rule* shared by both engines, such as `PolyphonicAcceptance`'s thresholds. Such a change is measured for both engines and recorded.
4. **FFT in the crate:** `rustfft` (MIT/Apache-2.0, pure Rust, three systems), already common in audio crates. `AE/analysis/RealFft.java`'s layout is reproduced on top of it, so the spectra match.

**Alternatives considered.**
- *Translate TarsosDSP's FastYin line by line:* that makes GPL-3.0 code part of the LGPL crate. Not chosen.
- *A new chord method (machine learning, chroma templates):* constitution VIII, and the owner's "at least the same quality" first. It is left for US3, and only deterministic methods are allowed.
- *Call the Java analysers from Rust:* that is still Java.

## R4 — The bridge between Rust and Java

**Facts.**
- The crate is built by Maven antrun on the three systems, with Rust 1.93.0 in CI (`.github/actions/install-rust`).
- `NativeMonitorLibrary` loads it once with `System.loadLibrary("tuxguitar_monitor_native")` and keeps the failure text.
- `src/no_allocation.rs` checks that the loops do not allocate.
- `src/realtime.rs` promotes a thread: Mach time constraint, MMCSS, or SCHED_FIFO.

**Decision.**
- **One crate, renamed in its description only.** The library name stays `tuxguitar_monitor_native`, so the packaging of the three systems does not change.
  - New modules: `capture.rs` (input only, the recognition's own stream), `analysis/` (the ported stages), `recognition.rs` (the analysis thread) and `jni_recognition.rs`.
  - A new Java class, `AE/nativeengine/NativeRecognitionLibrary`, declares the native methods and shares the loading of `NativeMonitorLibrary`, so the library is loaded once.
- **Live:**
  - `open(inputName, inputChannel, blockFrames = 128, javaNanosAtOpen, parameters[])` gives a handle.
  - The cpal input callback only copies the block and its arrival into a lock-free ring. It does not allocate or lock.
  - A native analysis thread, promoted like spec 022's, runs the stages for each hop and writes fixed-size frame records into a second ring.
  - A Java thread calls `nextFrames(handle, float[] records, timeoutMillis)`, which waits on a condition variable for at most the timeout, and builds an `AnalysisFrame` from each record. It then publishes the frames to the same listeners on that thread, as `AnalysisService` does today.
  - Samples for hop listeners go through a third ring, read by `nextHops(handle, float[], timeout)`, only while a hop listener is registered.
- **Settings and expected pitches:**
  - `configure(handle, parameters[])` covers the front end, level, articulation and the engine values;
  - `setExpectedPitches`, `setAttackCandidates` and `setSpectrumEnabled` complete it.

  These are copied by the caller into a slot that the analysis thread swaps at the top of its loop, as `AnalysisService.Configuration` does. The analysis thread never waits.
- **Offline:** `analyze(float[] mono, int rate, parameters[], boolean spectrum, int[] candidates)` returns the packed records for the whole recording.
  - `AE/OfflineAnalysis.analyze` dispatches on the engine of the settings (R5), so every existing caller of the datasets, replays and calibration gets the engine of the settings without being changed.
- **Clock:**
  - Rust records `Instant::now()` at open, and each block's arrival is `javaNanosAtOpen + (arrival − openInstant)`.
  - Java's `CaptureClock` keeps doing its lower-envelope estimate, fed from the frame records (sample index and arrival), so the Follow mode's timing code is unchanged.
  - With 128-frame blocks dated at the callback, the envelope is expected to sit within a few milliseconds of the device's own latency. The probe of R1 checks this.
- **Errors:** they come back as `NativeRecognitionException(reason)`: library missing, device not found, permission denied, format. A lost device or a stopped stream sets a flag that `nextFrames` reports, and that becomes the same `CaptureService.Event` types the practice already handles (input lost).

## R5 — The engine choice, Automatic and the fall back to Java

**Facts.**
- `AE/settings/PitchEngineCatalog` has `AUTOMATIC = "automatic"` and `FASTYIN = "fastyin"`, and lists one engine.
- The setting is `RecognitionParameters.ENGINE_PITCH`, shown in `RecognitionSettingsPanel` with the texts `recognition.engine.pitch.*` (three languages, `TuxGuitar-practice/share/lang/`).
- `AudioEngine.getInstance` builds the Java pipeline.

**Decision.**
- **Catalog:**
  - The catalog gets a second engine, `NATIVE = "native"` ("Native (Rust)").
  - `fastyin` stays the Java engine's identifier, so a hand-made Java choice stored before the update is kept (clarification 3). Its display name becomes "Java (FastYin)".
  - `getEngines()`, `getRecommended()` and `resolve()` stay as they are: they list the Java pitch estimators, and the committed `RecognitionSettingsStoreTest` (lines 158–161) checks one engine, `fastyin` recommended and `resolve(AUTOMATIC)` equal to it. A new list, `getRecognitionEngines()`, holds the engines of the menu, and `getRecommendedRecognitionEngine()` is the native engine when `NativeRecognitionLibrary.load()` succeeds, else the Java engine. So *Automatic* follows FR-005, and the setting's text shows which engine it stands for: "Automatic (Native)" or "Automatic (Java)".
  - The engine no longer only makes a `PitchEstimator`: it has a `kind()`, JAVA or NATIVE, read by `AudioEngine` and `OfflineAnalysis`.
- **`AudioEngine`:**
  - It gets a small `RecognitionBackend` seam with two implementations:
    - `JavaBackend` is the current `CaptureService` + `AnalysisService`, moved behind the seam without changing their code;
    - `NativeBackend` is new.
  - The seam has the same operations the consumers call today: `acquire` / `release`, `subscribe`, `setSpectrumEnabled`, `setExpectedPitches`, `setAttackCandidates`, the hop listeners, the capture listeners and the front end's settings.
  - The backend is chosen at `acquire` when no consumer holds the capture, so a new engine applies from the next run (FR-004, US2 scenario 4).
- **Fall back (FR-006):** if the native open fails, `NativeBackend` returns the reason. `AudioEngine` then:
  - starts the `JavaBackend` instead;
  - sends one `RecognitionEngineNotice(reason)` to its listeners. The practice window shows it once per reason, in three languages.

  The run starts as it does today (SC-003: within 2 s).
- **Permission denied** is the exception: the Java capture would be refused too, so no fallback is tried. The practice shows today's microphone-permission message, and no notice is sent.
- **Only `AudioEngine.getInstance`** (the application) chooses between the backends. The package constructors used by `AudioEngineTest` and the other committed tests keep the Java backend unless a backend factory is passed to them, so their fake inputs are never bypassed.
- **Texts:** the setting keeps its name, *Pitch detection engine* (`recognition.engine.pitch.name`, FR-004), and a new tip (`recognition.engine.pitch.tip.v2`) says what each engine does. These are new keys in the three files: the old texts are kept and new keys added only for the new options and the notice.

## R6 — The amp sound (spec 022) and the native recognition on one input

**Facts.**
- Spec 022's research R1 addendum found that, on macOS, two clients of one input work: the Java capture and the Rust device opened the built-in microphone together without gaps.
- The amp has its own native duplex stream (`NativeMonitorDevice`).
- `RecognitionUntouchedTest` and T061 check that the recognition is identical with the sound on and off.

**Decision.**
- **Two native input streams on the same device.** One is the recognition's (input only), the other the amp's (duplex), as today with Java plus native. Each runs at the device's current rate, never changed.
  - On Linux, cpal's default host is ALSA through the "default" device. That is PipeWire or PulseAudio on current desktops, which share inputs.
  - On Windows, WASAPI's shared mode shares them.
- FR-010 holds by construction, since the streams do not touch each other. The dataset identity of T061 is run again with the native engine (sound on and off).
- **Risk:** a raw ALSA `hw:` device on Linux opens only once. In that case the second open fails with a message: the amp's, since the recognition opens first when a run starts. This is reported to the Linux testers in sheet F13 and in the release notes.

**Alternatives considered.** One shared native input feeding both: the 022 engine would change, and the owner chose separate inputs in 022. It is kept as a fallback if the testers find a system that does not share.

## R7 — Arcade at the screen's refresh rate (FR-015, SC-006)

**Facts.**
- `TuxGuitar-practice/src/.../arcade/ui/ArcadeCanvas.java` has `FRAME_NANOS = 16_666_667L` (60 frames a second) and a timer at `TICK_NANOS = FRAME_NANOS / 2`.
- Its javadoc says that two ticks a frame let the painting follow the display.
- `arcade/view/QualityController` is built with `FRAME_NANOS` as its budget.
- The Arcade benchmarks of spec 003 (`ArcadeRenderBenchmarkTest` and the stress run) measure the paint cost against 4.17 ms (95th percentile), which the convergence gate of 022 passed (one busy-machine miss recorded).
- At 120 Hz a frame lasts 8.33 ms, and the ticks every 8.33 ms then equal the frame. The javadoc's own analysis says this can halve the rate.

**Decision.**
- **Measure first, change if needed:**
  - `ArcadeRefreshRateBenchmarkTest` runs Arcade for 10 minutes on the owner's Mac (ProMotion), with the native engine live on the built-in microphone, and reports the share of frames painted on time at the display's rate.
  - The display rate is read through the toolkit, or else measured from the paint intervals.
  - If fewer than 99 % of the frames are on time at 120 Hz, the frame and tick periods are derived from the measured display rate (tick = half a display frame), and `QualityController`'s budget follows it.
- **The recognition never holds a frame.**
  - The Arcade's frame source reads the latest verdicts that the recognition thread has published, through a volatile snapshot; the paint never waits on the recognition.
  - A test checks that the frame source does not block while `nextFrames` is waiting.
- The delay from strike to the hit shown is measured by the probe of R1 extended to the Arcade's frame source. The probe's tone and the verdict's paint time come from the `FrameObserver` of `ArcadeCanvas`.

## R8 — Datasets, before and after, for each engine

**Facts.**
- The benchmarks in `TuxGuitar-practice/test/.../dataset/` (`DatasetBenchmarkTest`, `GuitarSetChordBenchmarkTest`, `TechniqueDatasetBenchmarkTest`, `OwnerChordsBenchmarkTest`), plus `functional/BassRecognitionBenchmarkTest`, reach the analysis through `OfflineAnalysis` and `DatasetJudge`. They write their reports to `desktop/TuxGuitar-practice/target/dataset-benchmark/`.

**Decision.**
- The engine of the settings decides the engine of `OfflineAnalysis` (R4). The committed tests are not changed: they run with the settings' defaults, so *Automatic*, so the native engine when it loads.
- A system property, `-Dtuxguitar.recognition.engine=java|native`, overrides *Automatic* for a run of the benchmarks. This is how the two engines are measured on the same recordings without changing a test.
- **Before:** the Java figures of 2026-11-02 (commit 0ffd99c89) are copied into `benchmark/before/` before any change.
- **After:** both engines are run into `benchmark/after-java/` and `benchmark/after-native/`. A new comparison test (`EngineComparisonReportTest`) writes `benchmark/comparison.md` line by line against SC-001 and SC-004, and fails on any SC-001 regression.
- **Consequence for the default test suite:** every non-benchmark test that uses `OfflineAnalysis` (replays, functional, calibration) runs the native engine whenever the library is built, which is the case on the three CI systems. These tests passing unchanged is part of the quality proof.
  - If a committed test fails with the native engine, the native code is fixed (the owner's rule: never change a committed test), within the bug protocol's three attempts, then reported.
  - A run with `-Dtuxguitar.recognition.engine=java` keeps the Java engine tested too (CI runs the suite once each way on macOS).

## R9 — Real time and cost

**Decision.**
- The cpal callback only copies into the ring.
- The analysis thread allocates nothing in its loop, checked by the `no_allocation.rs` counting allocator, extended to the analysis stages.
- Its cost per hop is measured by `NativeAnalysisCostBenchmarkTest`: the median and 99th percentile of one hop's analysis, spectrum on and attack candidates on, against the hop time (5.3 ms). The target is under 10 % of a hop at the 99th percentile on the owner's Mac.
- The Java frame thread does what the analysis thread's publish does today: the listeners.
- A 10-minute live run without a dropped block (SC-005) reuses the endurance pattern of spec 022 (`RealtimeEnduranceBenchmarkTest`).

## R10 — Platforms and CI

**Decision.**
- The crate is already built and tested (`cargo test`) on macOS, Linux and Windows in CI. The new modules add `rustfft` (pure Rust) and nothing else.
- The Java-side tests run on the three CI systems. The live probes (R1, R7, R9 endurance) run on the owner's Mac only.
- Linux and Windows live checks go to the testers through sheet F13 and the release notes (the owner's rule: same features, the testing request in the notes).
