# Recognition engines

This document describes the two audio-recognition engines of the Interactive Practice modules, the interface between them and the practice session, every parameter they read, the constants that stay fixed in the code, and the rule for later changes. It was written for spec 024 (FR-014, research R10) and completed by spec 026 (FR-008), from the source as it stands in the working tree.

Both engines take **external parameters read at run time**: the recognition settings are stored in a configuration file and read when a run starts (or, for the level and attack values, pushed to a running engine). Tuning a value needs no rebuild of the application or of the native library.

## 1. Overview

```
guitar input
  ├─ Java engine:   CaptureService (Java Sound) → SampleRingBuffer → AnalysisService → AnalysisFrame
  └─ native engine: capture.rs (cpal) → recognition.rs → analysis/pipeline.rs → frame record → NativeBackend → AnalysisFrame
                                                                                                   │
practice session (TuxGuitar-practice, one Java code path for both engines)                         ▼
  PracticeSession (Wait) / FollowSession (Follow, Arcade)
     ├─ notes:  NoteInterpreter, TolerancePolicy, PolyphonicAcceptance (attack path)
     └─ chords: ChordProfile + ChordMatcher (profile rule) or ChordEvidence + ChordMatcher (per-string rule),
                PolyphonicAcceptance (attack path)
```

- `AudioEngine` (`desktop/TuxGuitar-audio-engine/src/main/java/app/tuxguitar/audio/engine/AudioEngine.java`) chooses the backend through `RecognitionBackendFactory`: `JavaBackend` or `NativeBackend` (`.../engine/backend/`).
- The choice comes from the setting `recognition.engine.pitch` and the system property `tuxguitar.recognition.engine` (`PitchEngineCatalog`). When the native library does not load or cannot open the input, the run falls back to the Java engine and a notice is shown (`RecognitionEngineNotice`).
- Both engines produce the same `AnalysisFrame` (`.../engine/analysis/AnalysisFrame.java`). The decision is made in Java, in the practice session, on that frame. The same frames give the same decision with either engine.

## 2. How each engine works

### 2.1 Common analysis stages

Both engines run the same stages, hop by hop: window 2048 samples, hop 256 samples (about 5.3 ms at 48 kHz). The native stages are ports of the Java ones and are checked against them by parity tests.

| Stage | Java class (`.../engine/`) | Native module (`tuxguitar-monitor-native/src/`) | Output |
|---|---|---|---|
| Front end: high-pass, noise gate, spectral subtraction, input gain | `frontend/FrontEnd`, `HighPass`, `NoiseGate`, `NoiseEstimator`, `SpectralSubtractor`, `InputGain` | `analysis/front_end.rs` | cleaned samples |
| Level | `analysis/LevelAnalyzer` | `analysis/level.rs` | RMS, peak, clipped share, level state (too low, suitable, too high, clipping) |
| Pitch | `analysis/TarsosFastYinEstimator` (TarsosDSP FastYin) | `analysis/yin.rs` | f0 and confidence |
| Onset (articulation) | `analysis/ArticulationDetector` | `analysis/articulation.rs` | onset flag: spectral-flux transient and a level gate, reported 13 hops after the transient |
| Spectrum (only while asked) | `analysis/PolyphonicAnalyzer` → `SpectrumFrame` | `analysis/polyphonic.rs` | semitone band levels (MIDI 24 to 112, dB), harmonic salience, ranked candidates, peaks |
| Attack evidence (only with attack candidates) | `analysis/AttackAnalyzer` → `AttackEvidence` | `analysis/attack.rs` | per candidate pitch, the amplitude of what the newest hop added (non-negative least squares on harmonic templates) |
| Target evidence (only with expected pitches) | `recognition/ExpectedPitchVerifier` → `TargetEvidence` | `analysis/target.rs` | how well the expected pitch, its neighbours and its octave explain the audio |
| Chord evidence (per-string rule) | `analysis/ChordEvidence` (computed in the session from the spectrum) | `analysis/chord.rs` (carried in the record) | per expected pitch: unique partials, clear partials, present fraction; residual pitches |
| Strum clip meter (input check) | `analysis/StrumClipMeter` | `analysis/strum_clip.rs` | verdict of the first strum's level: clipping, too loud, too weak, good |

### 2.2 The Java engine

- `CaptureService` reads the selected input with Java Sound (`TargetDataLine`) into a `SampleRingBuffer`.
- `AnalysisService` is the single analysis worker. For each hop it runs the front end, then the stages above, and builds one `AnalysisFrame`. The Java frame carries no chord evidence: `ChordMatcher` computes it from the spectrum when the per-string rule needs it.
- Its values come from `RecognitionSettings` through `AudioEngine`: each stage gets its value object built from the settings when the analysis is built (level analyser, articulation detector, strum meter, and since spec 026 the front end, pitch, polyphonic and attack analysers, §4.11). The sizes and safety values that stay fixed are in section 6.

### 2.3 The native engine

- Crate `desktop/build-scripts/native-modules/tuxguitar-monitor-native` (Rust, `cdylib` `tuxguitar_monitor_native`), built with `-P native-modules`.
- `capture.rs` opens the practice input with `cpal` at the device's current rate and cuts it into blocks of 128 frames, pushed into a lock-free ring. The callback never allocates, locks or waits.
- `recognition.rs` runs the analysis thread: front end, strum meter, then `analysis/pipeline.rs` hop by hop. Each frame is written as a packed `float` record (`analysis/frame.rs`, layout in `analysis/frame_layout.txt`) into a ring that Java reads. Changes from Java (parameters, attack candidates, expected pitches and chord) go through a mailbox the loop takes at the top of its next iteration.
- On the Java side, `NativeBackend` runs a frame thread that turns each record into an `AnalysisFrame` (`nativeengine/FrameRecords`) and feeds the capture clock from the record's sample end and arrival time. The native frame carries the chord evidence of the expected chord; `ChordMatcher.check(AnalysisFrame, …)` uses it when it is for the chord matched, otherwise it computes it from the spectrum.

### 2.4 The decision in the practice session

Code: `desktop/TuxGuitar-practice/src/app/tuxguitar/app/tools/custom/practice/session/`. `PracticeParameters.of(settings)` turns the settings into the session's values once per Start, so a running session never sees a partial change.

**Arming.** A strum or pluck is armed by an onset frame (`frame.isOnset()`). The transient time is the onset time minus the articulation delay (`ArticulationDetector.DECISION_DELAY_HOPS` hops). In Wait, `PracticeSession` arms one attempt at a time. In Follow and Arcade, `FollowSession.registerOnset` creates a `PendingOnset` for each onset, with the score events it may match as candidates.

**Two acceptance paths** run side by side for notes and chords:

1. **The attack path** (`PolyphonicAcceptance`): the attack evidence must explain at least `minShare` of what the attack added, every note of a chord must reach `minBalance` of the strongest, and this for `hops` hops in a row. Evidence of a note already accepted is ignored for `rearmNanos`. For chords it runs only while `recognition.chord.attack` is on.
2. **The steady path**: for a note, the pitch must stay inside the accepted range for the stability time (`recognition.note.stability`). For a chord, the chord rule below.

**Chord rule `all-verifiable` (spec 023, per string).** `ChordMatcher.judge` reads the chord evidence: a pitch with fewer than `recognition.advanced.chord.min-partials` unique partials is not verifiable; a verifiable pitch is heard when enough of its unique partials clearly sound (`recognition.chord.evidence`). A frame is complete when every verifiable pitch is heard.

**Chord rule `profile` (spec 024, pitch-class profile, the default).** Class `ChordProfile`, used by `ChordMatcher.check(ChordProfile, long)`:

- **Reset at each strum.** The profile is set to the expected chord: the template is the names (pitch classes) of the required pitches. The tolerated pitches (notes still sounding from an earlier beat) that are not required names are ignored: their bands are not summed.
  - Wait (`PracticeSession.sumProfile`): one profile per session, reset at the arming (the first frame after a new transient, or when the expected event changes).
  - Follow and Arcade (`FollowSession.registerOnset`): one profile per chord candidate of each `PendingOnset`, reset at the transient of that onset.
- **Summing.** Each usable frame (level suitable or too high, spectrum present) adds its bands: for every MIDI pitch from one semitone under the lowest required pitch (never under the lowest band, MIDI 24) up to `top` (`recognition.chord.detail.top`), `sums[midi % 12] += 10^(bandDb/20)`. Linear magnitudes, no noise floor.
- **Match.** The cosine between the 12 sums and the template of required names (0 for an empty profile).
- **Weak names.** A required name is weak when its sum is under `share` (`recognition.chord.profile.names`) times the sum of the strongest required name.
- **Complete frame.** `match >= recognition.chord.profile.match`, at most one weak name, and at least `recognition.chord.detail.settle` elapsed since the reset. Two weak names or more refuse the frame whatever the match.
- **Acceptance.** `stableHops` complete frames in a row (`recognition.advanced.chord.confirm` turned into hops), within `recognition.chord.window` of the reference (the arming in Wait, the onset in Follow and Arcade). A frame at an unusable level resets the run of complete frames; the profile keeps its sums.
- **Refusal.** When the window ends first: in Wait, feedback `INCOMPLETE` with the expected pitches of the weakest names (`ChordProfile.weakest`), and a new strum is needed; in Follow and Arcade, a wrong attempt (`FollowSession.wrongAttempt`) with the same missing pitches.
- **Attack path guard.** Under the profile rule the attack path may accept a chord only when `ChordMatcher.attackMayAccept` holds: the attack path is on and the profile has at most one weak name. The profile used is the one summed since the arming; with no strum armed, or when the armed profile summed nothing yet, the profile of the attack's own frame, and a frame with no spectrum does not stop the attack. Single notes are never affected.
- **Follow and Arcade** relax some chord values (`FollowLeniency`, `ArcadeLeniency`: `recognition.chord.evidence`, `recognition.advanced.chord.min-partials`, `recognition.advanced.chord.confirm`). They never relax the profile threshold, the weak-name share or the rule.

**Known limits of the profile rule** (spec 024, `specs/024-lenient-chords/benchmark/stop-t027/`):

- An incomplete chord (one note name fewer than expected) has only one weak name and is judged by its match alone, so it may be accepted. The dataset guard on incomplete chords was set to the measured floor, 20 of 44 refused.
- Low-register chords (lowest note around G2) leak into neighbouring semitone bands, which lowers their match: the IDMT minor triad G2 B♭2 D3 reaches 0.73 at 200 ms against a threshold of 0.79 and is refused.
- A name that is not played can reach 0.61 to 0.85 of the strongest expected name on real spectra (leak), so only silent missing names are certain to be weak (`benchmark/probe.md`).

## 3. Interface between the engines and the application

### 3.1 What the application gives

Through `AudioEngine` and the `RecognitionBackend` methods: the input and its channel, the parameters (`NativeParameters` for the native engine), whether the spectrum is wanted (on while the score has chords, `PracticeWindow`), the expected pitches (target evidence), the expected chord (required and tolerated pitches), and the attack candidates.

### 3.2 The frame record

One record per analysis window, as a `float[]` (Java) or `[f32]` (Rust). Defined in `desktop/build-scripts/native-modules/tuxguitar-monitor-native/src/analysis/frame_layout.txt`; the constants of `analysis/frame.rs` and of `nativeengine/FrameRecords.java` must equal it (checked by `frame.rs`'s tests and `FrameRecordsTest`).

- A `long` or `double` takes two slots (high then low 32 bits, as raw float bits). An int or a count is a float value.
- Header (offset): `LENGTH` 0, `TIMESTAMP_NANOS` 1, `CAPTURE_SAMPLE_END` 3, `ARRIVAL_NANOS` 5, `RMS_DBFS` 7, `PEAK_DBFS` 9, `CLIPPED_RATIO` 11, `LEVEL_STATE` 13, `F0_HZ` 14, `CONFIDENCE` 16, `ONSET` 18, `PARTS` 19; the header is 20 floats.
- `PARTS` is a bit set of the optional parts that follow, in this order: `PART_SPECTRUM` 1, `PART_TARGET` 2, `PART_ATTACK` 4, `PART_CHORD` 8.
  - spectrum: `binHz`, `minBandMidi`, `bandCount`, `bandDb[]`, `minSalienceMidi`, `salienceCount`, `salience[]`, candidates (midi, salience, relative salience, level), `peakCount`, `peaks[]`;
  - target: `windowMillis`, count × (midi, score);
  - attack: count, pitches, amplitudes, `attackStrength`, `binHz`, growth (or −1);
  - chord: count × (midi, salience, independent salience, harmonic of lower, mean amplitude, unique amplitude, unique partials, unique present fraction, unique clear partials), then the residual pitches.
- `CAPACITY` 4096: the largest record fits in this many floats. A record that does not fit is refused, never cut.

### 3.3 The JNI functions (`src/jni_recognition.rs`)

The static native methods of `app.tuxguitar.audio.engine.nativeengine.NativeRecognitionLibrary`. Errors become `NativeRecognitionException` with the message "REASON: detail".

Live recognition (a handle per open input):

| Function | Role |
|---|---|
| `open(inputName, inputChannel, blockFrames, javaNanosAtOpen, float[] parameters)` | Opens the input at the device's rate and starts the analysis thread; returns the handle |
| `sampleRate(handle)` | The rate opened |
| `configure(handle, float[] parameters)` | New `NativeParameters` for a running analysis |
| `setSpectrumEnabled(handle, on)` | Spectrum part on or off |
| `setExpectedPitches(handle, int[] midi)` | Target evidence (null or empty: none) |
| `setExpectedChord(handle, int[] required, int[] tolerated)` | Chord evidence (null required: none) |
| `setAttackCandidates(handle, int[] pitches)` | Attack evidence (null or empty: off) |
| `nextFrames(handle, float[] records, timeoutMillis)` | Copies whole records; 0 on timeout, −1 once the stream stopped, −2 after close |
| `nextHops(handle, float[] hops, timeoutMillis)` / `setHopsEnabled(handle, on)` | Hop blocks for the hop listeners |
| `droppedBlocks(handle)` | Capture blocks dropped |
| `strumClip(handle)` / `resetStrumClip(handle)` | Strum clip meter reading (verdict, clipped share, mean level) and restart |
| `stats(handle)` | Dropped blocks, median and 99th percentile of one hop's cost |
| `close(handle)` | Idempotent |

Offline and per-hop analysis (tests, benchmarks, Java-captured streams):

| Function | Role |
|---|---|
| `analyze(mono, rate, parameters, spectrum, attackCandidates, expectedPitches, chordRequired, chordTolerated)` | Records of a whole recording, no front end, deterministic |
| `frontEnd(mono, rate, parameters)` | Test only: the hops after the native front end |
| `strumClipOffline(mono, rate, parameters)` | Test only: the final strum clip reading |
| `openAnalysis(rate, parameters)` / `closeAnalysis(handle)` | A per-hop analysis |
| `setAnalysisSpectrum`, `setAnalysisAttackCandidates`, `setAnalysisExpectedPitches` | Its requests |
| `analyzeHop(handle, hop, record)` | One hop of 256 samples; returns the record length, 0 while the window is not full, −1 when it does not fit |
| `resetAnalysis(handle)` | Forgets the window and every history |

### 3.4 `NativeParameters`

`desktop/TuxGuitar-audio-engine/src/main/java/app/tuxguitar/audio/engine/nativeengine/NativeParameters.java` builds two arrays, both read by the crate's `src/analysis/mod.rs` at the same indices (the same names on both sides):

- a `float[21]` (`NativeParameters.of`), read into `Parameters` (`from_slice` refuses a shorter array): the level, onset, front-end, strum and YIN values;
- since spec 026, a `double[35]`, the **tuning array** (`NativeParameters.tuning`), read into `Tuning` (`Tuning::from_slice` refuses another length): every other value a native stage reads. It is a `double[]` so that the values reach the crate exactly as Java holds them.

Both are sent at `open` and whenever the settings or the live front end change (`AudioEngine.pushNativeParameters`: `configure` for the `float[21]`, `configureTuning` for the tuning array). The native side does not validate bounds: the values come already validated from `RecognitionSettings`. In Follow's fast runs, `NativeParameters.withFastOnset` puts the `recognition.onset-fast.*` values at the onset indices 4, 5, 7 and 9 to 12.

**The `float[21]`**

| Index | Name | Source | Default | Unit |
|---|---|---|---|---|
| 0 | `LEVEL_TOO_LOW` | `recognition.input.level.min` | −40 | dBFS |
| 1 | `LEVEL_TOO_HIGH` | `recognition.input.level.max` | −6 | dBFS |
| 2 | `LEVEL_CLIP_SAMPLE` | `recognition.advanced.level.clip-sample` | 0.99 | share of full scale |
| 3 | `LEVEL_CLIP_RATIO` | `recognition.advanced.level.clip-share` | 0.10 | share of samples |
| 4 | `ONSET_FLUX_FACTOR` | `recognition.onset.flux-factor` (fast: `recognition.onset-fast.flux-factor`) | 1.5 | × median flux |
| 5 | `ONSET_FLUX_OFFSET` | `recognition.onset.flux-offset` (fast: `recognition.onset-fast.flux-offset`) | 5.0 | flux units |
| 6 | `ONSET_MIN_LEVEL` | `recognition.advanced.attack.min-level` | −50 | dBFS |
| 7 | `ONSET_REFRACTORY` | `recognition.onset.refractory` (fast: `recognition.onset-fast.refractory`, 6) | 12 | hops |
| 8 | `ONSET_GATE` | `recognition.note.attack` | 4.5 | dB |
| 9 | `ONSET_PRE_FIRST` | `recognition.onset.pre-first` (fast: 8) | 19 | hops before the transient |
| 10 | `ONSET_PRE_LAST` | `recognition.onset.pre-last` (fast: 2) | 2 | hops |
| 11 | `ONSET_POST_FIRST` | `recognition.onset.post-first` (fast: 3) | 4 | hops after the transient |
| 12 | `ONSET_POST_LAST` | `recognition.onset.post-last` (fast: 8) | 13 | hops |
| 13 | `FRONT_GAIN` | live input gain (in memory only, `InputGain` −6 to +18 dB) | 1.0 | multiplier |
| 14 | `FRONT_NOISE_REDUCTION` | `recognition.input.noise-reduction` (live value) | 0 | 0 or 1 |
| 15 | `FRONT_SUBTRACTION` | live front end | 0 | 0 or 1 |
| 16 | `STRUM_ARM_FLOOR` | `recognition.input.level.min` | −40 | dBFS |
| 17 | `STRUM_CLIP_SAMPLE` | `recognition.advanced.level.clip-sample` | 0.99 | share of full scale |
| 18 | `STRUM_WEAK` | `recognition.input.level.min` | −40 | dBFS |
| 19 | `STRUM_LOUD` | `recognition.input.level.max` | −6 | dBFS |
| 20 | `YIN_THRESHOLD` | `recognition.pitch.yin-threshold` | 0.20 | YIN absolute threshold |

**The tuning array** (`T_…` indices, `NativeParameters.TUNING_LENGTH` = 35; contracts: `specs/026-engine-external-parameters/contracts/native-parameters.md`). The strum window is sent in milliseconds, as stored.

| Index | Name | Source | Default | Unit |
|---|---|---|---|---|
| 0 | `T_TARGET_SEARCH` | `recognition.pitch.target-search` | 20.0 | cents |
| 1 | `T_TARGET_PERIODS` | `recognition.pitch.target-periods` | 4 | — |
| 2 | `T_POLY_CANDIDATE_LOW` | `recognition.polyphonic.candidate-low` | 33 | MIDI |
| 3 | `T_POLY_CANDIDATE_HIGH` | `recognition.polyphonic.candidate-high` | 88 | MIDI |
| 4 | `T_POLY_HARMONICS` | `recognition.polyphonic.harmonics` | 12 | — |
| 5 | `T_POLY_MAX_HARMONIC` | `recognition.polyphonic.max-harmonic` | 5000.0 | Hz |
| 6 | `T_POLY_TOLERANCE` | `recognition.polyphonic.tolerance` | 25.0 | cents |
| 7 | `T_POLY_CANDIDATES` | `recognition.polyphonic.candidates` | 6 | — |
| 8 | `T_POLY_MIN_RELATIVE` | `recognition.polyphonic.min-relative` | 0.10 | % |
| 9 | `T_POLY_FLOOR` | `recognition.polyphonic.floor` | -70.0 | dB |
| 10 | `T_POLY_SUBHARMONIC` | `recognition.polyphonic.subharmonic` | 0.30 | % |
| 11 | `T_POLY_FLOOR_WIDTH` | `recognition.polyphonic.floor-width` | 25.0 | Hz |
| 12 | `T_POLY_FLOOR_FACTOR` | `recognition.polyphonic.floor-factor` | 2.0 | × |
| 13 | `T_ATTACK_LAG` | `recognition.attack.lag` | 22 | hops |
| 14 | `T_ATTACK_MAX_FREQUENCY` | `recognition.attack.max-frequency` | 5000.0 | Hz |
| 15 | `T_ATTACK_PARTIALS` | `recognition.attack.partials` | 14 | — |
| 16 | `T_ATTACK_BETA` | `recognition.attack.beta` | 0.3 | — |
| 17 | `T_ATTACK_SIGMA` | `recognition.attack.sigma` | 1.2 | — |
| 18 | `T_CHORD_PARTIAL_PRESENCE` | `recognition.chord-evidence.partial-presence` | 0.05 | % |
| 19 | `T_CHORD_NEIGHBOUR_MARGIN` | `recognition.chord-evidence.neighbour-margin` | 1.5 | × |
| 20 | `T_STRUM_WINDOW` | `recognition.chord-evidence.strum-window` | 300 | ms |
| 21 | `T_STRUM_QUIET` | `recognition.chord-evidence.strum-quiet` | 60 | hops |
| 22 | `T_STRUM_RISE` | `recognition.chord-evidence.strum-rise` | 12.0 | dB |
| 23 | `T_STRUM_CLIP_SHARE` | `recognition.chord-evidence.strum-clip-share` | 0.005 | ‰ |
| 24 | `T_FRONT_HIGH_PASS` | `recognition.front-end.high-pass` | 40.0 | Hz |
| 25 | `T_FRONT_NOISE_RISE` | `recognition.front-end.noise-rise` | 0.5 | dB/s |
| 26 | `T_FRONT_NOISE_FALL` | `recognition.front-end.noise-fall` | 0.2 | — |
| 27 | `T_FRONT_NOISE_TRUSTED` | `recognition.front-end.noise-trusted` | 188 | hops |
| 28 | `T_GATE_OPEN` | `recognition.noise-gate.open` | 9.0 | dB |
| 29 | `T_GATE_CLOSE` | `recognition.noise-gate.close` | 5.0 | dB |
| 30 | `T_GATE_REDUCTION` | `recognition.noise-gate.reduction` | 20.0 | dB |
| 31 | `T_GATE_HOLD` | `recognition.noise-gate.hold` | 19 | hops |
| 32 | `T_SUBTRACTION` | `recognition.noise-gate.subtraction` | 1.5 | × |
| 33 | `T_SUBTRACTION_FLOOR` | `recognition.noise-gate.subtraction-floor` | 0.05 | % |
| 34 | `T_SUBTRACTION_LEARN` | `recognition.noise-gate.subtraction-learn` | 0.1 | % |

**The layout check.** `tuningLayout()` returns the crate's `TUNING_LENGTH`. `NativeRecognitionLibrary` compares it with `NativeParameters.TUNING_LENGTH` when the library loads: a library without the function, or with another length, is "too old" and is not used (the Java engine runs instead). A tuning array of another length is refused by `Tuning::from_slice`.

**The JNI functions of spec 026** (in `src/jni_recognition.rs`, beside those of §3.3, which keep the tuning defaults):

| Function | Role |
|---|---|
| `tuningLayout()` | The length of the crate's tuning array |
| `openTuned(inputName, inputChannel, blockFrames, javaNanosAtOpen, float[] parameters, double[] tuning)` | `open` with the tuning array |
| `configureTuning(handle, double[] tuning)` | New tuning values for a running analysis |
| `analyzeTuned(mono, rate, parameters, tuning, …)`, `frontEndTuned(mono, rate, parameters, tuning)`, `strumClipOfflineTuned(mono, rate, parameters, tuning)`, `openAnalysisTuned(rate, parameters, tuning)` | The offline and per-hop functions with the tuning array |

## 4. Recognition settings (`RecognitionParameters`)

Source: `desktop/TuxGuitar-audio-engine/src/main/java/app/tuxguitar/audio/engine/settings/RecognitionParameters.java` (34 parameters before spec 026, which adds the 192 expert values of §4.11: 226 in all). Each key is `"recognition."` followed by the part given in the code; the tables show the full stored key.

**Where they live.** `RecognitionSettingsStore` saves them in the TuxGuitar configuration module `tuxguitar-audio-recognition`, with `recognition.version` = 1 (`RecognitionParameters.VERSION_KEY`, `VERSION`). On reading, `RecognitionSettings.fromStrings` repairs every value: a missing, empty or unreadable value gives the default, a number out of bounds is clamped. Cross-rule: `recognition.note.accept` is never below `recognition.tuner.in-tune`. Unknown `recognition.*` keys are dropped at the next save.

**Where they are set.** In the Recognition settings window (TuxGuitar Configuration › *Audio recognition*, `AudioRecognitionOption` and `RecognitionSettingsPanel`), one category per `RecognitionParameter.Section`, each with a *Restore* button. Other places that change a value are listed in the last column. A device profile (spec 013) may overlay only the parameters of `ProfileParameters.LIST` (marked "profile").

**Stored values.** A value shown in % is stored as a fraction (for example `recognition.chord.profile.match=0.79` shows 79 %). Defaults and bounds below are the stored values.

**When they take effect** (column *Takes effect*): *at once* for the tuner and the front end's noise reduction; *next run* for the practice values (`PracticeParameters` is built once per run, at the next Start) and the calibration; *listening restart* for the values an engine stage reads (`AudioEngine.engineValuesDiffer`): the listening restarts briefly, the Java analysis is rebuilt and the native engine receives them with `configure` and `configureTuning`. `EngineDocumentationFactsTest` checks every row against the code: unit, default, bounds, section and when it takes effect.

### 4.1 Audio input (`Section.INPUT`)

| Key | Meaning | Unit | Default | Bounds | Takes effect | Also set by |
|---|---|---|---|---|---|---|
| `recognition.input.level.min` | Quieter sound is "too low" and never accepted; also the strum meter's arm floor and weak level | dBFS | −40 | −60 to −25 | listening restart | profile |
| `recognition.input.level.max` | Louder sound is "too high"; also the strum meter's loud level | dBFS | −6 | −20 to −1 | listening restart | |
| `recognition.input.noise-reduction` | Noise gate and noise subtraction in the front end | on/off | off | | at once | |
| `recognition.input.gain-suggestion` | Offers a gain after the level check (never applied without confirmation) | on/off | on | | at once | |

### 4.2 Tuner (`Section.TUNER`)

| Key | Meaning | Unit | Default | Bounds | Takes effect | Also set by |
|---|---|---|---|---|---|---|
| `recognition.tuner.in-tune` | Distance from the target shown as "in tune" | cents | 5 | 1 to 20 | at once | |
| `recognition.tuner.hold` | The tuner keeps the last reliable note in grey | on/off | on | | at once | |

### 4.3 Practice: notes (`Section.NOTES`)

| Key | Meaning | Unit | Default | Bounds | Takes effect | Also set by |
|---|---|---|---|---|---|---|
| `recognition.note.accept` | Accepted distance from the written pitch | cents | 35 | 10 to 45 (never under the in-tune range) | next run | profile; Follow relaxation |
| `recognition.note.confidence` | Minimum pitch confidence before a note is judged | % (fraction) | 0.80 | 0.50 to 0.95 | next run | profile; Follow and Arcade relaxation |
| `recognition.note.stability` | Time the right (or wrong) note must sound before it is judged | ms (turned into hops) | 107 | 50 to 300 | next run | profile; Follow and Arcade relaxation |
| `recognition.note.attack` | Level gate of a new attack (`ArticulationDetector` gate, native `ONSET_GATE`) | dB | 4.5 | 2.0 to 10.0 | listening restart | profile |
| `recognition.note.legato` | Legato rule; one option only, `strict` (every note needs its own attack) | choice | `strict` | `strict` | next run | |
| `recognition.note.technique` | Evaluate the written playing techniques; off shows them as not verifiable | on/off | on | | next run | |
| `recognition.tempo.window` | Half-width of the on-time window in Arcade, at 120 BPM (scales with tempo) | ms | 100 | 40 to 200 | next run | the Arcade slider of the *Mode* tab |

### 4.4 Practice: chords (`Section.CHORDS`)

| Key | Meaning | Unit | Default | Bounds | Takes effect | Also set by |
|---|---|---|---|---|---|---|
| `recognition.chord.evidence` | Per-string rule: share of a string's unique partials that must clearly sound | % (fraction) | 1/3 (0.333…) | 0.20 to 0.80 | next run | profile; Follow and Arcade relaxation |
| `recognition.chord.window` | Time after the strum within which the chord must be complete | ms | 600 | 300 to 1500 | next run | profile |
| `recognition.chord.completeness` | Chord rule: `profile` (note names over the strum) or `all-verifiable` (every string separately) | choice | `profile` | `all-verifiable`, `profile` | next run | chord check level |
| `recognition.chord.foreign` | Extra wrong strings; one option only, `ignore` (not checked yet) | choice | `ignore` | `ignore` | next run | |
| `recognition.chord.profile.match` | Profile rule: the match (cosine) from which a frame is complete | % (fraction) | 0.79 | 0.50 to 0.99 | next run | chord check level |
| `recognition.chord.profile.names` | Profile rule: a required name summed under this share of the strongest required name is weak | % (fraction) | 0.40 | 0.05 to 0.60 | next run | |
| `recognition.chord.attack` | A chord may be accepted on its attack (guarded by the weak names under the profile rule) | on/off | on | | next run | chord check level |

### 4.5 Chord details (`Section.CHORD_DETAILS`)

| Key | Meaning | Unit | Default | Bounds | Takes effect | Also set by |
|---|---|---|---|---|---|---|
| `recognition.chord.detail.top` | Profile rule: the highest pitch summed into the profile (84 = C6) | MIDI | 84 | 60 to 112 | next run | |
| `recognition.chord.detail.settle` | Profile rule: time from the reset before a chord can be complete | ms | 200 | 0 to 500 | next run | |

### 4.6 Passages (`Section.PASSAGES`)

| Key | Meaning | Unit | Default | Bounds | Takes effect | Also set by |
|---|---|---|---|---|---|---|
| `recognition.passage.measures` | Passage length for repeats; `0` plays straight through | choice (measures) | `2` | `0`, `1`, `2`, `4` | next run | the practice window, for the current run |
| `recognition.passage.threshold` | Error share above which a passage is played again | choice (%) | `50` | `25`, `50`, `75` | next run | |
| `recognition.passage.retries` | Most repeats of one passage | count | 2 | 0 to 5 | next run | |
| `recognition.follow.play-time` | In Follow, how long after its time a note may still be played | ms | 600 | 100 to 1200 | next run | the Follow slider of the *Mode* tab |

### 4.7 Recognition engine (`Section.ENGINE`)

| Key | Meaning | Unit | Default | Bounds | Takes effect | Also set by |
|---|---|---|---|---|---|---|
| `recognition.engine.pitch` | Engine choice: `automatic` (shown); a stored `native` or `fastyin` is kept and listed by the menu | choice | `automatic` | `automatic`, `native`, `fastyin` | listening restart | system property `tuxguitar.recognition.engine` (`java` or `native`) for Automatic only |

### 4.8 Advanced (`Section.ADVANCED`)

| Key | Meaning | Unit | Default | Bounds | Takes effect | Also set by |
|---|---|---|---|---|---|---|
| `recognition.note.fret-sharp` | Extra sharp allowance per fret (sharp side only); 0 switches it off | cents per fret | 0.7 | 0 to 2 | next run | profile |
| `recognition.advanced.level.clip-sample` | A sample at or above this share of full scale counts as clipped | % (fraction) | 0.99 | 0.90 to 1.00 | listening restart | |
| `recognition.advanced.level.clip-share` | Share of clipped samples above which the frame is clipping | % (fraction) | 0.10 | 0.01 to 0.50 | listening restart | |
| `recognition.advanced.tuner.confidence` | Minimum confidence before the tuner shows a note | % (fraction) | 0.80 | 0.50 to 0.95 | at once | |
| `recognition.advanced.attack.min-level` | Sound quieter than this is never a new attack | dBFS | −50 | −70 to −30 | listening restart | profile |
| `recognition.advanced.chord.min-partials` | Per-string rule: unique partials a string needs to be verifiable and heard | count | 2 | 1 to 4 | next run | profile; Follow and Arcade relaxation |
| `recognition.advanced.chord.confirm` | Time every chord condition must hold (`stableHops`, both rules) | ms (turned into hops: 43 ms = 8) | 43 | 20 to 150 | next run | profile; Follow and Arcade relaxation |

`recognition.note.fret-sharp` is in the advanced category although its key has no `advanced.` part.

### 4.9 Keys that are not parameters

| Key | Role |
|---|---|
| `recognition.version` | Layout version of the file (`VERSION` = 1) |
| `recognition.tempo.tolerance` | Legacy key before spec 012 (`LEGACY_TEMPO_TOLERANCE_KEY`): read once to carry `strict` (50 ms) or `lenient` (125 ms) over to `recognition.tempo.window`, never written |

### 4.10 Chord check level (spec 024)

The *Chord check* level is not stored. `ChordLeniency.of` derives it from the values of `recognition.chord.completeness`, `recognition.chord.profile.match` and `recognition.chord.attack`; any other set is *Custom*. Choosing a level writes its values into the default layer of the store.

| Level | `recognition.chord.completeness` | `recognition.chord.profile.match` | `recognition.chord.attack` |
|---|---|---|---|
| *Strict* | `profile` | 0.79 | off |
| *Normal* (= the defaults) | `profile` | 0.79 | on |
| *Lenient* | `profile` | 0.70 | on |

Values from `specs/024-lenient-chords/data-model.md`, held by `ChordLeniency.values`; `ChordLeniency.apply` writes them and changes no other value. The *Mode* tab of the practice window shows the level (`ChordLeniencyPanel`) and lists *Custom* only while the settings hold no level.

<!-- spec026-sections:start -->
### 4.11 Engine stages and session values (spec 026)

Expert settings, one category per section in the Advanced part of the settings window. Each replaces a constant that was fixed in the code (last column); its default is that constant, so nothing changes until a value is changed. "Native" values are sent to the native engine in the tuning array (`NativeParameters.tuning`, §3.4). Rows from `specs/026-engine-external-parameters/inventory.md`.

#### Advanced: Onsets (`Section.ONSET`)

| Key | Meaning | Unit | Default | Bounds | Takes effect | Replaces |
|---|---|---|---|---|---|---|
| `recognition.onset.flux-factor` | Onset: flux factor | × | 1.5 | 0.5 to 6 | listening restart | `ArticulationDetector.java` `DEFAULT_FLUX_MEDIAN_FACTOR`; native: float[21] ONSET_FLUX_FACTOR |
| `recognition.onset.flux-offset` | Onset: flux margin | — | 5.0 | 1 to 20 | listening restart | `ArticulationDetector.java` `DEFAULT_FLUX_OFFSET`; native: float[21] ONSET_FLUX_OFFSET |
| `recognition.onset.refractory` | Onset: rest after an attack | hops | 12 | 3 to 48 | listening restart | `ArticulationDetector.java` `DEFAULT_REFRACTORY_HOPS`; native: float[21] ONSET_REFRACTORY |
| `recognition.onset.pre-first` | Onset: level before, from | hops | 19 | 2 to 19 | listening restart | `ArticulationDetector.java` constructor 19; `NativeParameters.java` `NORMAL_PRE_FIRST`; native: float[21] ONSET_PRE_FIRST |
| `recognition.onset.pre-last` | Onset: level before, to | hops | 2 | 1 to 19 | listening restart | `ArticulationDetector.java` constructor 2; `NativeParameters.java` `NORMAL_PRE_LAST`; native: float[21] ONSET_PRE_LAST |
| `recognition.onset.post-first` | Onset: level after, from | hops | 4 | 1 to 13 | listening restart | `ArticulationDetector.java` constructor 4; `NativeParameters.java` `NORMAL_POST_FIRST`; native: float[21] ONSET_POST_FIRST |
| `recognition.onset.post-last` | Onset: level after, to | hops | 13 | 4 to 13 | listening restart | `ArticulationDetector.java` constructor post window 13; native: float[21] ONSET_POST_LAST |

#### Advanced: Fast-run onsets (`Section.ONSET_FAST`)

| Key | Meaning | Unit | Default | Bounds | Takes effect | Replaces |
|---|---|---|---|---|---|---|
| `recognition.onset-fast.flux-factor` | Fast run: flux factor | × | 1.5 | 0.5 to 6 | listening restart | `ArticulationDetector.java` `DEFAULT_FLUX_MEDIAN_FACTOR`; native: articulation fast (withFastOnset) |
| `recognition.onset-fast.flux-offset` | Fast run: flux margin | — | 5.0 | 1 to 20 | listening restart | `ArticulationDetector.java` `DEFAULT_FLUX_OFFSET`; native: articulation fast (withFastOnset) |
| `recognition.onset-fast.refractory` | Fast run: rest after an attack | hops | 6 | 2 to 24 | listening restart | `ArticulationDetector.java` `FAST_REFRACTORY_HOPS`; native: articulation fast (withFastOnset) |
| `recognition.onset-fast.pre-first` | Fast run: level before, from | hops | 8 | 2 to 19 | listening restart | `ArticulationDetector.java` fast() 8; `NativeParameters.java` `FAST_PRE_FIRST`; native: articulation fast (withFastOnset) |
| `recognition.onset-fast.pre-last` | Fast run: level before, to | hops | 2 | 1 to 8 | listening restart | `ArticulationDetector.java` fast() 2; `NativeParameters.java` `FAST_PRE_LAST`; native: articulation fast (withFastOnset) |
| `recognition.onset-fast.post-first` | Fast run: level after, from | hops | 3 | 1 to 8 | listening restart | `ArticulationDetector.java` fast() 3; `NativeParameters.java` `FAST_POST_FIRST`; native: articulation fast (withFastOnset) |
| `recognition.onset-fast.post-last` | Fast run: level after, to | hops | 8 | 3 to 8 | listening restart | `ArticulationDetector.java` fast() post window 8; native: articulation fast (withFastOnset) |

#### Advanced: Pitch (`Section.PITCH`)

| Key | Meaning | Unit | Default | Bounds | Takes effect | Replaces |
|---|---|---|---|---|---|---|
| `recognition.pitch.yin-threshold` | Pitch: YIN threshold | % | 0.20 | 0.05 to 0.5 | listening restart | `TarsosFastYinEstimator.java` `DEFAULT_THRESHOLD`; `NativeParameters.java` `DEFAULT_YIN_THRESHOLD`; native: float[21] YIN_THRESHOLD; yin.rs DEFAULT_THRESHOLD |
| `recognition.pitch.target-search` | Pitch: expected-note search | cents | 20.0 | 5 to 50 | listening restart | `ExpectedPitchVerifier.java` `SEARCH_CENTS`; native: target.rs SEARCH_CENTS (tuning) |
| `recognition.pitch.target-periods` | Pitch: expected-note periods | — | 4 | 2 to 8 | listening restart | `ExpectedPitchVerifier.java` `PERIODS`; native: target.rs PERIODS (tuning) |
| `recognition.pitch.max-sharp` | Pitch: maximum sharp side | cents | 49.0 | 20 to 49 | next run | `TolerancePolicy.java` `DEFAULT_MAX_SHARP_CENTS` |
| `recognition.pitch.attack-candidates-low` | Pitch: lowest attack candidate | MIDI | 28 | 24 to 40 | next run | `AttackCandidates.java` `LOWEST` |
| `recognition.pitch.attack-candidates-high` | Pitch: highest attack candidate | MIDI | 100 | 80 to 112 | next run | `AttackCandidates.java` `HIGHEST` |

#### Advanced: Chord spectrum, candidates (`Section.POLYPHONIC_1`)

| Key | Meaning | Unit | Default | Bounds | Takes effect | Replaces |
|---|---|---|---|---|---|---|
| `recognition.polyphonic.candidate-low` | Spectrum: lowest candidate | MIDI | 33 | 24 to 60 | listening restart | `PolyphonicAnalyzer.java` `Parameters.minCandidateMidi`; native: polyphonic.rs min_candidate_midi |
| `recognition.polyphonic.candidate-high` | Spectrum: highest candidate | MIDI | 88 | 64 to 112 | listening restart | `PolyphonicAnalyzer.java` `Parameters.maxCandidateMidi`; native: polyphonic.rs max_candidate_midi |
| `recognition.polyphonic.harmonics` | Spectrum: partials per note | — | 12 | 4 to 24 | listening restart | `PolyphonicAnalyzer.java` `Parameters.harmonics`; native: polyphonic.rs harmonics |
| `recognition.polyphonic.max-harmonic` | Spectrum: highest partial | Hz | 5000.0 | 1000 to 12000 | listening restart | `PolyphonicAnalyzer.java` `Parameters.maxHarmonicHz`; native: polyphonic.rs max_harmonic_hz |
| `recognition.polyphonic.tolerance` | Spectrum: partial tolerance | cents | 25.0 | 5 to 50 | listening restart | `PolyphonicAnalyzer.java` `Parameters.harmonicToleranceCents`; native: polyphonic.rs harmonic_tolerance_cents |
| `recognition.polyphonic.candidates` | Spectrum: pitches per frame | — | 6 | 1 to 12 | listening restart | `PolyphonicAnalyzer.java` `Parameters.maxCandidates`; native: polyphonic.rs max_candidates |

#### Advanced: Chord spectrum, floor (`Section.POLYPHONIC_2`)

| Key | Meaning | Unit | Default | Bounds | Takes effect | Replaces |
|---|---|---|---|---|---|---|
| `recognition.polyphonic.min-relative` | Spectrum: weakest pitch kept | % | 0.10 | 0.01 to 0.5 | listening restart | `PolyphonicAnalyzer.java` `Parameters.minRelativeSalience`; native: polyphonic.rs min_relative_salience |
| `recognition.polyphonic.floor` | Spectrum: level floor | dB | -70.0 | -100 to -40 | listening restart | `PolyphonicAnalyzer.java` `Parameters.floorDb`; native: polyphonic.rs floor_db |
| `recognition.polyphonic.subharmonic` | Spectrum: lower note check | % | 0.30 | 0.05 to 0.9 | listening restart | `PolyphonicAnalyzer.java` `Parameters.subharmonicRatio`; native: polyphonic.rs subharmonic_ratio |
| `recognition.polyphonic.floor-width` | Spectrum: floor width | Hz | 25.0 | 5 to 100 | listening restart | `PolyphonicAnalyzer.java` `DEFAULT_FLOOR_HALF_WIDTH_HZ`; native: polyphonic.rs FLOOR_HALF_WIDTH_HZ |
| `recognition.polyphonic.floor-factor` | Spectrum: floor factor | × | 2.0 | 1 to 6 | listening restart | `PolyphonicAnalyzer.java` `DEFAULT_FLOOR_FACTOR`; native: polyphonic.rs FLOOR_FACTOR |

#### Advanced: Struck notes, growth (`Section.ATTACK_1`)

| Key | Meaning | Unit | Default | Bounds | Takes effect | Replaces |
|---|---|---|---|---|---|---|
| `recognition.attack.lag` | Struck: growth span | hops | 22 | 4 to 64 | listening restart | `AttackAnalyzer.java` `Parameters.lagHops`; native: attack.rs lag_hops |
| `recognition.attack.max-frequency` | Struck: highest frequency | Hz | 5000.0 | 1000 to 12000 | listening restart | `AttackAnalyzer.java` `Parameters.maxHz`; native: attack.rs max_hz |
| `recognition.attack.partials` | Struck: partials per note | — | 14 | 4 to 24 | listening restart | `AttackAnalyzer.java` `Parameters.partials`; native: attack.rs partials |
| `recognition.attack.beta` | Struck: weight of high partials | — | 0.3 | 0 to 2 | listening restart | `AttackAnalyzer.java` `Parameters.beta`; native: attack.rs beta |
| `recognition.attack.sigma` | Struck: partial width | — | 1.2 | 0.3 to 4 | listening restart | `AttackAnalyzer.java` `Parameters.sigmaBins`; native: attack.rs sigma_bins |

#### Advanced: Struck notes, tuning (`Section.ATTACK_2`)

| Key | Meaning | Unit | Default | Bounds | Takes effect | Replaces |
|---|---|---|---|---|---|---|
| `recognition.attack.evidence-partials` | Struck tuning: partials measured | — | 12 | 4 to 24 | next run | `AttackEvidence.java` `DEFAULT_PARTIALS` |
| `recognition.attack.evidence-search` | Struck tuning: search | cents | 60.0 | 10 to 100 | next run | `AttackEvidence.java` `DEFAULT_SEARCH_CENTS` |
| `recognition.attack.evidence-peak` | Struck tuning: smallest peak | % | 0.03 | 0.01 to 0.2 | next run | `AttackEvidence.java` `DEFAULT_MIN_PEAK_SHARE` |

#### Advanced: Chord evidence and strum check (`Section.CHORD_EVIDENCE`)

| Key | Meaning | Unit | Default | Bounds | Takes effect | Replaces |
|---|---|---|---|---|---|---|
| `recognition.chord-evidence.partial-presence` | Chord: partial present from | % | 0.05 | 0.01 to 0.3 | listening restart | `ChordEvidence.java` `PARTIAL_PRESENCE_RATIO`; native: chord.rs PARTIAL_PRESENCE_RATIO |
| `recognition.chord-evidence.neighbour-margin` | Chord: whole-tone check | × | 1.5 | 1 to 4 | listening restart | `ChordEvidence.java` `NEIGHBOUR_MARGIN`; native: chord.rs NEIGHBOUR_MARGIN |
| `recognition.chord-evidence.strum-window` | Strum check: window | ms | 300 | 100 to 1000 | listening restart | `StrumClipMeter.java` Parameters 300_000_000L; native: strum_clip.rs WINDOW_NANOS |
| `recognition.chord-evidence.strum-quiet` | Strum check: quiet before | hops | 60 | 10 to 200 | listening restart | `StrumClipMeter.java` Parameters 60; native: strum_clip.rs ARM_QUIET_HOPS |
| `recognition.chord-evidence.strum-rise` | Strum check: rise | dB | 12.0 | 3 to 30 | listening restart | `StrumClipMeter.java` Parameters 12.0; native: strum_clip.rs ATTACK_RISE_DB |
| `recognition.chord-evidence.strum-clip-share` | Strum check: clipping share | ‰ | 0.005 | 0.001 to 0.05 | listening restart | `StrumClipMeter.java` Parameters 0.005; native: strum_clip.rs CLIP_SHARE |

#### Advanced: Input clean-up (`Section.FRONT_END`)

| Key | Meaning | Unit | Default | Bounds | Takes effect | Replaces |
|---|---|---|---|---|---|---|
| `recognition.front-end.high-pass` | Rumble filter corner | Hz | 40.0 | 10 to 120 | listening restart | `HighPass.java` `CORNER_HZ`; native: front_end.rs CORNER_HZ |
| `recognition.front-end.noise-rise` | Noise level: rise speed | dB/s | 0.5 | 0.1 to 5 | listening restart | `NoiseEstimator.java` `DEFAULT_RISE_DB_PER_SECOND`; native: front_end.rs RISE_DB_PER_SECOND |
| `recognition.front-end.noise-fall` | Noise level: fall speed | — | 0.2 | 0.1 to 1 | listening restart | `NoiseEstimator.java` `DEFAULT_FALL_RATE`; native: front_end.rs FALL_RATE |
| `recognition.front-end.noise-trusted` | Noise level: trusted after | hops | 188 | 20 to 1000 | listening restart | `NoiseEstimator.java` `TRUSTED_AFTER_HOPS`; native: front_end.rs TRUSTED_AFTER_HOPS |

#### Advanced: Noise gate (`Section.NOISE_GATE`)

| Key | Meaning | Unit | Default | Bounds | Takes effect | Replaces |
|---|---|---|---|---|---|---|
| `recognition.noise-gate.open` | Gate: opens above the noise by | dB | 9.0 | 6 to 30 | listening restart | `NoiseGate.java` `OPEN_MARGIN_DB`; native: front_end.rs OPEN_MARGIN_DB |
| `recognition.noise-gate.close` | Gate: closes under the noise plus | dB | 5.0 | 0 to 8 | listening restart | `NoiseGate.java` `CLOSE_MARGIN_DB`; native: front_end.rs CLOSE_MARGIN_DB |
| `recognition.noise-gate.reduction` | Gate: reduction | dB | 20.0 | 0 to 60 | listening restart | `NoiseGate.java` `REDUCTION_DB`; native: front_end.rs REDUCTION_DB |
| `recognition.noise-gate.hold` | Gate: hold | hops | 19 | 1 to 100 | listening restart | `NoiseGate.java` `HOLD_HOPS`; native: front_end.rs HOLD_HOPS |
| `recognition.noise-gate.subtraction` | Noise subtraction: strength | × | 1.5 | 0.5 to 4 | listening restart | `SpectralSubtractor.java` `ALPHA`; native: front_end.rs ALPHA |
| `recognition.noise-gate.subtraction-floor` | Noise subtraction: floor | % | 0.05 | 0 to 0.3 | listening restart | `SpectralSubtractor.java` `BETA`; native: front_end.rs BETA |
| `recognition.noise-gate.subtraction-learn` | Noise subtraction: learning speed | % | 0.1 | 0.01 to 1 | listening restart | `SpectralSubtractor.java` `DEFAULT_LEARN_RATE`; native: front_end.rs LEARN_RATE |

#### Advanced: Gain suggestion (`Section.GAIN`)

| Key | Meaning | Unit | Default | Bounds | Takes effect | Replaces |
|---|---|---|---|---|---|---|
| `recognition.gain.target` | Gain suggestion: playing level aimed at | dBFS | -22.0 | -40 to -10 | next run | `GainSuggester.java` `TARGET_PLAYING_RMS_DBFS` |
| `recognition.gain.max-peak` | Gain suggestion: highest peak | dBFS | -3.0 | -12 to -1 | next run | `GainSuggester.java` `MAX_PEAK_DBFS` |
| `recognition.gain.no-change` | Gain suggestion: nothing to change within | dB | 1.5 | 0.5 to 6 | next run | `GainSuggester.java` `NO_CHANGE_DB` |
| `recognition.gain.min-snr` | Gain suggestion: playing above the noise by | dB | 20.0 | 6 to 40 | next run | `GainSuggester.java` `MIN_SIGNAL_TO_NOISE_DB` |

#### Advanced: Struck notes, decision (`Section.ACCEPTANCE_1`)

| Key | Meaning | Unit | Default | Bounds | Takes effect | Replaces |
|---|---|---|---|---|---|---|
| `recognition.acceptance.min-share` | Struck notes: share explained | % | 0.30 | 0.05 to 0.9 | next run | `PolyphonicAcceptance.java` `Parameters.minShare` |
| `recognition.acceptance.min-balance` | Struck notes: chord balance | % | 0.10 | 0 to 0.5 | next run | `PolyphonicAcceptance.java` `Parameters.minBalance` |
| `recognition.acceptance.min-peak` | Struck notes: strongest pitch | % | 0.7 | 0.1 to 1 | next run | `PolyphonicAcceptance.java` `Parameters.minPeakRatio` |
| `recognition.acceptance.hops` | Struck notes: confirmed for | hops | 6 | 1 to 30 | next run | `PolyphonicAcceptance.java` `Parameters.hops` |
| `recognition.acceptance.neighbour` | Struck notes: over the neighbours | × | 0.0 | 0 to 4 | next run | `PolyphonicAcceptance.java` `Parameters.neighbourRatio` |
| `recognition.acceptance.attack-strength` | Struck notes: attack strength | — | 0.0 | 0 to 5 | next run | `PolyphonicAcceptance.java` `Parameters.minAttackStrength` |
| `recognition.acceptance.rearm` | Struck notes: ignored after a note | ms | 220 | 50 to 1000 | next run | `PolyphonicAcceptance.java` `Parameters.rearmNanos` |

#### Advanced: Struck notes, attack (`Section.ACCEPTANCE_2`)

| Key | Meaning | Unit | Default | Bounds | Takes effect | Replaces |
|---|---|---|---|---|---|---|
| `recognition.acceptance.offset-margin` | Struck notes: tuning margin | cents | 15.0 | 0 to 50 | next run | `PolyphonicAcceptance.java` `DEFAULT_OFFSET_MARGIN` |
| `recognition.acceptance.decay` | Struck notes: forgiveness | hops | 1 | 0 to 5 | next run | `PolyphonicAcceptance.java` `DEFAULT_DECAY` |
| `recognition.acceptance.guard` | Struck notes: thump skipped | ms | 60 | 0 to 200 | next run | `PolyphonicAcceptance.java` `DEFAULT_GUARD_NANOS` |
| `recognition.acceptance.min-total` | Struck notes: quietest note | ‰ | 0.01 | 0.001 to 0.1 | next run | `PolyphonicAcceptance.java` `DEFAULT_MIN_TOTAL` |
| `recognition.acceptance.floor-hops` | Struck notes: quiet level over | hops | 10 | 1 to 50 | next run | `PolyphonicAcceptance.java` `DEFAULT_FLOOR_HOPS` |
| `recognition.acceptance.silence` | Struck notes: from silence | % | 0.6 | 0.1 to 1 | next run | `PolyphonicAcceptance.java` `DEFAULT_SILENCE_STRENGTH` |
| `recognition.acceptance.min-onset` | Struck notes: smallest onset | ‰ | 0.002 | 0.001 to 0.05 | next run | `PolyphonicAcceptance.java` `DEFAULT_MIN_ONSET_TOTAL` |

#### Advanced: Struck notes, onset (`Section.ACCEPTANCE_3`)

| Key | Meaning | Unit | Default | Bounds | Takes effect | Replaces |
|---|---|---|---|---|---|---|
| `recognition.acceptance.onset-floor` | Struck notes: onset over the quiet level | × | 3.0 | 1 to 10 | next run | `PolyphonicAcceptance.java` `DEFAULT_ONSET_FLOOR` (was inline 3.0 * floor) |
| `recognition.acceptance.release` | Struck notes: end of the attack | % | 0.25 | 0.05 to 0.9 | next run | `PolyphonicAcceptance.java` `DEFAULT_RELEASE` (was inline 0.25 * attackPeak) |

#### Advanced: Wait mode, verdicts and techniques (`Section.WAIT_1`)

| Key | Meaning | Unit | Default | Bounds | Takes effect | Replaces |
|---|---|---|---|---|---|---|
| `recognition.wait.verdict-hold` | Wait: verdict shown for | ms | 1000 | 200 to 3000 | next run | `PracticeSession.java` `VERDICT_HOLD_NANOS` |
| `recognition.wait.neighbour-veto` | Wait: neighbour hold-back | ms | 120 | 0 to 500 | next run | `PracticeSession.java` `NEIGHBOUR_VETO_NANOS` |
| `recognition.wait.neighbour-confidence` | Wait: neighbour confidence | % | 0.8 | 0.5 to 1 | next run | `PracticeSession.java` `NEIGHBOUR_CONFIDENCE` |
| `recognition.wait.technique-window` | Wait: technique observed for | ms | 1500 | 500 to 4000 | next run | `PracticeSession.java` `TECHNIQUE_WINDOW_NANOS` |
| `recognition.wait.bend-window-min` | Wait: bend observed at least | ms | 1000 | 250 to 2500 | next run | `PracticeSession.java` `BEND_WINDOW_MIN_NANOS` |
| `recognition.wait.bend-window-max` | Wait: bend observed at most | ms | 2500 | 1000 to 4000 | next run | `PracticeSession.java` `BEND_WINDOW_MAX_NANOS`; `PracticeSession.java` `BEND_FRAMES_MAX` |
| `recognition.wait.bend-margin` | Wait: bend margin | semitones | 0.5 | 0.1 to 2 | next run | `PracticeSession.java` `BEND_PATH_MARGIN_SEMITONES` |

#### Advanced: Wait mode, speed and level (`Section.WAIT_2`)

| Key | Meaning | Unit | Default | Bounds | Takes effect | Replaces |
|---|---|---|---|---|---|---|
| `recognition.wait.technique-lead` | Wait: technique from before | ms | 150 | 0 to 500 | next run | `PracticeSession.java` `TECHNIQUE_LEAD_NANOS` |
| `recognition.wait.speed-window` | Wait: too fast within | ms | 200 | 50 to 1000 | next run | `PracticeSession.java` `SPEED_WINDOW_NANOS` |
| `recognition.wait.speed-lead` | Wait: too fast from before | ms | 20 | 0 to 100 | next run | `PracticeSession.java` inline 20_000_000L (speed check, before the last acceptance) |
| `recognition.wait.early-margin` | Wait: early acceptance margin | — | 0.5 | 0.1 to 2 | next run | `PracticeParameters.java` `EARLY_STABILITY_MARGIN` |
| `recognition.wait.early-hops` | Wait: early acceptance after | hops | 12 | 4 to 40 | next run | `PracticeParameters.java` `EARLY_STABILITY_NANOS` |
| `recognition.wait.weak-fast` | Wait: too weak for speed | dBFS | -30.0 | -50 to -10 | next run | `PracticeParameters.java` `WEAK_FAST_DBFS` |

#### Advanced: Follow mode, attacks (`Section.FOLLOW_1`)

| Key | Meaning | Unit | Default | Bounds | Takes effect | Replaces |
|---|---|---|---|---|---|---|
| `recognition.follow.evidence-horizon` | Follow: pitch awaited for | ms | 400 | 100 to 1500 | next run | `FollowSession.java` `EVIDENCE_HORIZON_NANOS` |
| `recognition.follow.silence` | Follow: end of a sound | ms | 40 | 10 to 200 | next run | `FollowSession.java` `SILENCE_NANOS` |
| `recognition.follow.short-note` | Follow: shortest note | ms | 40 | 10 to 200 | next run | `FollowSession.java` `SHORT_NOTE_NANOS` |
| `recognition.follow.same-sound` | Follow: same sound within | ms | 100 | 20 to 400 | next run | `FollowSession.java` `SAME_SOUND_NANOS` |
| `recognition.follow.ringing` | Follow: string rings for | ms | 1500 | 300 to 4000 | next run | `FollowSession.java` `RINGING_NANOS` |
| `recognition.follow.scan-back` | Follow: notes looked back | — | 8 | 0 to 32 | next run | `FollowSession.java` inline scanStart() 8 (events looked back) |
| `recognition.follow.onset-memory` | Follow: attacks kept for | ms | 3000 | 20 to 10000 | next run | `FollowSession.java` inline 3_000_000_000L (recent onsets kept) |

#### Advanced: Follow mode, attack times (`Section.FOLLOW_2`)

| Key | Meaning | Unit | Default | Bounds | Takes effect | Replaces |
|---|---|---|---|---|---|---|
| `recognition.follow.refine-before` | Follow: attack before the evidence | ms | 150 | 0 to 500 | next run | `FollowSession.java` `REFINE_BEFORE_NANOS` |
| `recognition.follow.refine-after` | Follow: attack after the evidence | ms | 30 | 0 to 200 | next run | `FollowSession.java` `REFINE_AFTER_NANOS` |
| `recognition.follow.after-close` | Follow: open after its window | ms | 100 | 0 to 400 | next run | `FollowSession.java` `EVIDENCE_AFTER_CLOSE_NANOS` |
| `recognition.follow.tail` | Follow: end of the run | ms | 100 | 0 to 400 | next run | `FollowSession.java` `TAIL_NANOS` |
| `recognition.follow.history` | Follow: sound kept for | ms | 1200 | 100 to 4000 | next run | `FollowSession.java` `HISTORY_NANOS` |

#### Advanced: Follow mode, techniques (`Section.FOLLOW_3`)

| Key | Meaning | Unit | Default | Bounds | Takes effect | Replaces |
|---|---|---|---|---|---|---|
| `recognition.follow.technique-window` | Follow: technique observed for | ms | 1500 | 500 to 4000 | next run | `FollowSession.java` `TECHNIQUE_WINDOW_NANOS` |
| `recognition.follow.bend-window-min` | Follow: bend observed at least | ms | 1000 | 250 to 2500 | next run | `FollowSession.java` `BEND_WINDOW_MIN_NANOS` |
| `recognition.follow.bend-window-max` | Follow: bend observed at most | ms | 2500 | 1000 to 4000 | next run | `FollowSession.java` `BEND_WINDOW_MAX_NANOS` |
| `recognition.follow.bend-margin` | Follow: bend margin | semitones | 0.5 | 0.1 to 2 | next run | `FollowSession.java` `BEND_MARGIN_SEMITONES` |
| `recognition.follow.evidence-lead` | Follow: evidence watched from | ms | 400 | 100 to 1500 | next run | `FollowSession.java` `EVIDENCE_LEAD_NANOS` |

#### Advanced: Playback clock and latency (`Section.CLOCK`)

| Key | Meaning | Unit | Default | Bounds | Takes effect | Replaces |
|---|---|---|---|---|---|---|
| `recognition.clock.fit-window` | Clock: position measured over | ms | 600 | 150 to 2000 | next run | `TickInterpolator.java` `FIT_WINDOW_NANOS` |
| `recognition.clock.min-points` | Clock: readings needed | — | 8 | 3 to 64 | next run | `TickInterpolator.java` `MIN_POINTS` |
| `recognition.clock.min-span` | Clock: readings spread over | ms | 150 | 30 to 600 | next run | `TickInterpolator.java` `MIN_SPAN_NANOS` |
| `recognition.clock.stall` | Clock: player stopped after | ms | 60 | 20 to 300 | next run | `TickInterpolator.java` `STALL_NANOS` |
| `recognition.clock.extrapolation` | Clock: ahead of the last reading | ms | 30 | 0 to 200 | next run | `TickInterpolator.java` `EXTRAPOLATION_NANOS` |
| `recognition.clock.seek-margin` | Clock: jump taken as a seek | ticks | 960 | 240 to 3840 | next run | `TickInterpolator.java` `SEEK_MARGIN_TICKS` |
| `recognition.clock.latency-limit` | Clock: largest latency | ms | 1000.0 | 200 to 3000 | next run | `LatencyCompensation.java` `LIMIT_MILLIS` |

#### Advanced: Timing windows (`Section.TIMING`)

| Key | Meaning | Unit | Default | Bounds | Takes effect | Replaces |
|---|---|---|---|---|---|---|
| `recognition.timing.reference-beat` | Timing: width read at the beat | ms | 500.0 | 250 to 1000 | next run | `TimingTolerance.java` `REFERENCE_BEAT_MILLIS` |
| `recognition.timing.advance` | Timing: play before the red | ms | 50 | 0 to 200 | next run | `PlayTimeWindow.java` `ADVANCE_NANOS` |
| `recognition.timing.gap` | Timing: gap before the next note | ms | 20 | 0 to 100 | next run | `PlayTimeWindow.java` `GAP_NANOS` |
| `recognition.timing.clock-spread` | Timing: unstable route from | ms | 80.0 | 20 to 300 | next run | `ClockUncertainty.java` `UNSTABLE_SPREAD_MILLIS` |

#### Advanced: Follow mode, permissive (`Section.LENIENCY_1`)

| Key | Meaning | Unit | Default | Bounds | Takes effect | Replaces |
|---|---|---|---|---|---|---|
| `recognition.follow-leniency.permissive.accept` | Follow permissive: note accepted within | cents | 35.0 | 10 to 45 | next run | `FollowLeniency.java` `NOTE_ACCEPT` |
| `recognition.follow-leniency.permissive.confidence` | Follow permissive: pitch confidence | % | 0.60 | 0.5 to 0.95 | next run | `FollowLeniency.java` `NOTE_CONFIDENCE` |
| `recognition.follow-leniency.permissive.stability` | Follow permissive: note held for | ms | 55.0 | 50 to 300 | next run | `FollowLeniency.java` `NOTE_STABILITY` |
| `recognition.follow-leniency.permissive.chord-evidence` | Follow permissive: chord evidence | % | 0.22 | 0.2 to 0.8 | next run | `FollowLeniency.java` `CHORD_EVIDENCE` |
| `recognition.follow-leniency.permissive.chord-partials` | Follow permissive: chord partials needed | — | 1.0 | 1 to 4 | next run | `FollowLeniency.java` `CHORD_MIN_PARTIALS` |
| `recognition.follow-leniency.permissive.chord-confirm` | Follow permissive: chord confirmed after | ms | 24.0 | 20 to 150 | next run | `FollowLeniency.java` `CHORD_CONFIRM` |

#### Advanced: Follow mode, very permissive (`Section.LENIENCY_2`)

| Key | Meaning | Unit | Default | Bounds | Takes effect | Replaces |
|---|---|---|---|---|---|---|
| `recognition.follow-leniency.very-permissive.accept` | Follow very permissive: note accepted within | cents | 35.0 | 10 to 45 | next run | `FollowLeniency.java` `NOTE_ACCEPT` |
| `recognition.follow-leniency.very-permissive.confidence` | Follow very permissive: pitch confidence | % | 0.50 | 0.5 to 0.95 | next run | `FollowLeniency.java` `NOTE_CONFIDENCE` |
| `recognition.follow-leniency.very-permissive.stability` | Follow very permissive: note held for | ms | 50.0 | 50 to 300 | next run | `FollowLeniency.java` `NOTE_STABILITY` |
| `recognition.follow-leniency.very-permissive.chord-evidence` | Follow very permissive: chord evidence | % | 0.20 | 0.2 to 0.8 | next run | `FollowLeniency.java` `CHORD_EVIDENCE` |
| `recognition.follow-leniency.very-permissive.chord-partials` | Follow very permissive: chord partials needed | — | 1.0 | 1 to 4 | next run | `FollowLeniency.java` `CHORD_MIN_PARTIALS` |
| `recognition.follow-leniency.very-permissive.chord-confirm` | Follow very permissive: chord confirmed after | ms | 20.0 | 20 to 150 | next run | `FollowLeniency.java` `CHORD_CONFIRM` |

#### Advanced: Arcade, fun (`Section.LENIENCY_3`)

| Key | Meaning | Unit | Default | Bounds | Takes effect | Replaces |
|---|---|---|---|---|---|---|
| `recognition.arcade-leniency.fun.confidence` | Arcade fun: pitch confidence | % | 0.70 | 0.5 to 0.95 | next run | `ArcadeLeniency.java` `NOTE_CONFIDENCE` |
| `recognition.arcade-leniency.fun.stability` | Arcade fun: note held for | ms | 75.0 | 50 to 300 | next run | `ArcadeLeniency.java` `NOTE_STABILITY` |
| `recognition.arcade-leniency.fun.chord-evidence` | Arcade fun: chord evidence | % | 0.28 | 0.2 to 0.8 | next run | `ArcadeLeniency.java` `CHORD_EVIDENCE` |
| `recognition.arcade-leniency.fun.chord-partials` | Arcade fun: chord partials needed | — | 2.0 | 1 to 4 | next run | `ArcadeLeniency.java` `CHORD_MIN_PARTIALS` |
| `recognition.arcade-leniency.fun.chord-confirm` | Arcade fun: chord confirmed after | ms | 32.0 | 20 to 150 | next run | `ArcadeLeniency.java` `CHORD_CONFIRM` |
| `recognition.arcade-leniency.fun.tempo-window` | Arcade fun: timing window widened | % | 1.25 | 1 to 2 | next run | `ArcadeLeniency.java` windowMillis() factor 1.25 (fun) |

#### Advanced: Arcade, very permissive (`Section.LENIENCY_4`)

| Key | Meaning | Unit | Default | Bounds | Takes effect | Replaces |
|---|---|---|---|---|---|---|
| `recognition.arcade-leniency.very-permissive.confidence` | Arcade very permissive: pitch confidence | % | 0.60 | 0.5 to 0.95 | next run | `ArcadeLeniency.java` `NOTE_CONFIDENCE` |
| `recognition.arcade-leniency.very-permissive.stability` | Arcade very permissive: note held for | ms | 55.0 | 50 to 300 | next run | `ArcadeLeniency.java` `NOTE_STABILITY` |
| `recognition.arcade-leniency.very-permissive.chord-evidence` | Arcade very permissive: chord evidence | % | 0.22 | 0.2 to 0.8 | next run | `ArcadeLeniency.java` `CHORD_EVIDENCE` |
| `recognition.arcade-leniency.very-permissive.chord-partials` | Arcade very permissive: chord partials needed | — | 1.0 | 1 to 4 | next run | `ArcadeLeniency.java` `CHORD_MIN_PARTIALS` |
| `recognition.arcade-leniency.very-permissive.chord-confirm` | Arcade very permissive: chord confirmed after | ms | 24.0 | 20 to 150 | next run | `ArcadeLeniency.java` `CHORD_CONFIRM` |
| `recognition.arcade-leniency.very-permissive.tempo-window` | Arcade very permissive: timing window widened | % | 1.5 | 1 to 2 | next run | `ArcadeLeniency.java` windowMillis() factor 1.5 (very-permissive) |

#### Advanced: Techniques, bend (`Section.TECHNIQUE_1`)

| Key | Meaning | Unit | Default | Bounds | Takes effect | Replaces |
|---|---|---|---|---|---|---|
| `recognition.technique.bend.min-range` | Bend: detected from | cents | 60.0 | 20 to 200 | next run | `BendDetector.java` `Parameters.minRangeCents` |
| `recognition.technique.bend.plain-range` | Bend: plain note under | cents | 30.0 | 5 to 100 | next run | `BendDetector.java` `Parameters.plainRangeCents` |
| `recognition.technique.bend.min-duration` | Bend: shortest note | ms | 0.2 | 0.05 to 1 | next run | `BendDetector.java` `Parameters.minDurationSeconds` |
| `recognition.technique.bend.voiced` | Bend: pitch heard for | % | 0.6 | 0.2 to 1 | next run | `BendDetector.java` `Parameters.minVoicedShare` |
| `recognition.technique.bend.written-move` | Bend: smallest written move | cents | 25.0 | 5 to 100 | next run | `BendDetector.java` `Parameters.minWrittenMoveCents` |

#### Advanced: Techniques, written bend (`Section.TECHNIQUE_2`)

| Key | Meaning | Unit | Default | Bounds | Takes effect | Replaces |
|---|---|---|---|---|---|---|
| `recognition.technique.bend.on-tolerance` | Bend: on target within | cents | 30.0 | 5 to 100 | next run | `BendDetector.java` `Parameters.onToleranceCents` |
| `recognition.technique.bend.on-share` | Bend: on target within a share | % | 0.15 | 0 to 0.5 | next run | `BendDetector.java` `Parameters.onToleranceShare` |
| `recognition.technique.bend.moved-share` | Bend: under target, uncertain from | % | 0.5 | 0.1 to 1 | next run | `BendDetector.java` `Parameters.movedShare` |
| `recognition.technique.bend.start-tolerance` | Bend: pre-bend start within | cents | 40.0 | 5 to 150 | next run | `BendDetector.java` `Parameters.startToleranceCents` |
| `recognition.technique.bend.edge` | Bend: start and end read over | hops | 8 | 1 to 40 | next run | `BendDetector.java` `Parameters.edgeHops` |

#### Advanced: Techniques, slide (`Section.TECHNIQUE_3`)

| Key | Meaning | Unit | Default | Bounds | Takes effect | Replaces |
|---|---|---|---|---|---|---|
| `recognition.technique.slide.min-range` | Slide: detected from | cents | 100.0 | 30 to 300 | next run | `SlideDetector.java` `Parameters.minRangeCents` |
| `recognition.technique.slide.plain-range` | Slide: plain note under | cents | 40.0 | 5 to 150 | next run | `SlideDetector.java` `Parameters.plainRangeCents` |
| `recognition.technique.slide.monotony` | Slide: one direction for | % | 0.75 | 0.3 to 1 | next run | `SlideDetector.java` `Parameters.minMonotony` |
| `recognition.technique.slide.steps` | Slide: moving steps needed | hops | 6 | 1 to 30 | next run | `SlideDetector.java` `Parameters.minMovingSteps` |
| `recognition.technique.slide.min-duration` | Slide: shortest note | ms | 0.15 | 0.05 to 1 | next run | `SlideDetector.java` `Parameters.minDurationSeconds` |
| `recognition.technique.slide.voiced` | Slide: pitch heard for | % | 0.5 | 0.2 to 1 | next run | `SlideDetector.java` `Parameters.minVoicedShare` |

#### Advanced: Techniques, vibrato (`Section.TECHNIQUE_4`)

| Key | Meaning | Unit | Default | Bounds | Takes effect | Replaces |
|---|---|---|---|---|---|---|
| `recognition.technique.vibrato.depth` | Vibrato: detected from depth | cents | 25.0 | 5 to 100 | next run | `VibratoDetector.java` `Parameters.minDepthCents` |
| `recognition.technique.vibrato.plain-depth` | Vibrato: plain note under | cents | 10.0 | 1 to 50 | next run | `VibratoDetector.java` `Parameters.plainDepthCents` |
| `recognition.technique.vibrato.min-rate` | Vibrato: slowest rate | Hz | 2.5 | 0.5 to 6 | next run | `VibratoDetector.java` `Parameters.minRateHz` |
| `recognition.technique.vibrato.max-rate` | Vibrato: fastest rate | Hz | 9.0 | 6.5 to 20 | next run | `VibratoDetector.java` `Parameters.maxRateHz` |
| `recognition.technique.vibrato.min-duration` | Vibrato: shortest note | ms | 0.35 | 0.1 to 2 | next run | `VibratoDetector.java` `Parameters.minDurationSeconds` |
| `recognition.technique.vibrato.voiced` | Vibrato: pitch heard for | % | 0.7 | 0.2 to 1 | next run | `VibratoDetector.java` `Parameters.minVoicedShare` |

#### Advanced: Techniques, legato and pitch (`Section.TECHNIQUE_5`)

| Key | Meaning | Unit | Default | Bounds | Takes effect | Replaces |
|---|---|---|---|---|---|---|
| `recognition.technique.legato.start-window` | Legato: start looked at | ms | 0.1 | 0.03 to 0.5 | next run | `LegatoDetector.java` `Parameters.startWindowSeconds` |
| `recognition.technique.legato.picked-rise` | Legato: picked above a rise of | dB | 6.0 | 2 to 20 | next run | `LegatoDetector.java` `Parameters.pickedRiseDb` |
| `recognition.technique.legato.rise` | Legato: legato under a rise of | dB | 3.0 | 0 to 10 | next run | `LegatoDetector.java` `Parameters.legatoRiseDb` |
| `recognition.technique.frame-confidence` | Techniques: pitch confidence | % | 0.8 | 0.5 to 1 | next run | `PracticeSession.java` inline 0.8 (collectTechniqueFrame); `FollowSession.java` inline 0.8 (process) |
| `recognition.technique.motion.outlier` | Techniques: stray pitch beyond | cents | 400.0 | 100 to 1200 | next run | `PitchMotion.java` `OUTLIER_CENTS` |
| `recognition.technique.motion.min-voiced` | Techniques: readings needed | hops | 8 | 2 to 40 | next run | `PitchMotion.java` `MIN_VOICED` |

#### Advanced: Delay calibration, notes (`Section.CALIBRATION_1`)

| Key | Meaning | Unit | Default | Bounds | Takes effect | Replaces |
|---|---|---|---|---|---|---|
| `recognition.calibration.early-window` | Calibration: note before its click | ms | 250 | 50 to 1000 | next run | `CalibrationRun.java` `EARLY_WINDOW_NANOS` |
| `recognition.calibration.late-window` | Calibration: note after its click | ms | 700 | 200 to 2000 | next run | `CalibrationRun.java` `LATE_WINDOW_NANOS` |
| `recognition.calibration.pitch-patience` | Calibration: pitch awaited for | ms | 25 | 5 to 200 | next run | `CalibrationRun.java` `PITCH_PATIENCE_NANOS` |
| `recognition.calibration.pitch-before` | Calibration: pitch begun before | ms | 30 | 0 to 200 | next run | `CalibrationRun.java` `PITCH_BEFORE_ATTACK_NANOS` |
| `recognition.calibration.same-pitch` | Calibration: same pitch within | semitones | 0.5 | 0.1 to 2 | next run | `CalibrationRun.java` `SAME_PITCH_SEMITONES` |
| `recognition.calibration.min-confidence` | Calibration: pitch confidence | % | 0.8 | 0.5 to 1 | next run | `CalibrationRun.java` `MIN_CONFIDENCE` |
| `recognition.calibration.min-frequency` | Calibration: lowest pitch | Hz | 70.0 | 40 to 200 | next run | `CalibrationRun.java` `MIN_HZ` |

#### Advanced: Delay calibration, result (`Section.CALIBRATION_2`)

| Key | Meaning | Unit | Default | Bounds | Takes effect | Replaces |
|---|---|---|---|---|---|---|
| `recognition.calibration.max-frequency` | Calibration: highest pitch | Hz | 1400.0 | 500 to 4000 | next run | `CalibrationRun.java` `MAX_HZ` |
| `recognition.calibration.min-usable` | Calibration: notes needed | — | 5 | 2 to 8 | next run | `CalibrationTaps.java` `MIN_USABLE` |
| `recognition.calibration.outlier-deviations` | Calibration: stray note beyond | × | 3.0 | 1 to 6 | next run | `CalibrationTaps.java` `OUTLIER_DEVIATIONS` |
| `recognition.calibration.outlier-floor` | Calibration: never set aside within | ms | 40.0 | 5 to 200 | next run | `CalibrationTaps.java` `OUTLIER_FLOOR_MILLIS` |
| `recognition.calibration.unstable-spread` | Calibration: unstable above | ms | 60.0 | 10 to 300 | next run | `CalibrationTaps.java` `UNSTABLE_SPREAD_MILLIS` |
| `recognition.calibration.recognition-rate` | Recognition rate kept | % | 60 | 10 to 100 | next run | `RecognitionRate.java` `THRESHOLD_PERCENT` |

#### Advanced: Input calibration (`Section.CALIBRATION_3`)

| Key | Meaning | Unit | Default | Bounds | Takes effect | Replaces |
|---|---|---|---|---|---|---|
| `recognition.calibration.calm-below` | Calibration: calm below | dBFS | -60.0 | -90 to -46 | next run | `RecordingQuality.java` `CALM_BELOW_DBFS` |
| `recognition.calibration.loud-above` | Calibration: loud above | dBFS | -45.0 | -59 to -20 | next run | `RecordingQuality.java` `LOUD_ABOVE_DBFS` |
| `recognition.calibration.sound-refractory` | Calibration: one sound within | ms | 250 | 50 to 1000 | next run | `SoundDetection.java` `REFRACTORY_MILLIS` |
| `recognition.calibration.sound-lead` | Calibration: sound cut before | ms | 150 | 0 to 500 | next run | `SoundDetection.java` `SEGMENT_LEAD_MILLIS` |

#### Advanced: Level check (`Section.LEVEL_CHECK`)

| Key | Meaning | Unit | Default | Bounds | Takes effect | Replaces |
|---|---|---|---|---|---|---|
| `recognition.level-check.playing-confidence` | Level check: playing confidence | % | 0.80 | 0.5 to 1 | next run | `InputAssessor.java` `Parameters.playingConfidence` |
| `recognition.level-check.required-playing` | Level check: playing needed | ms | 500 | 100 to 3000 | next run | `InputAssessor.java` `Parameters.requiredPlayingNanos` |
| `recognition.level-check.suboptimal-share` | Level check: too loud from | % | 0.10 | 0.01 to 0.5 | next run | `InputAssessor.java` `Parameters.suboptimalShare` |

#### Advanced: Weak input (`Section.INPUT_QUALITY`)

| Key | Meaning | Unit | Default | Bounds | Takes effect | Replaces |
|---|---|---|---|---|---|---|
| `recognition.input-quality.attacks` | Weak input: attacks looked at | — | 3 | 1 to 10 | next run | `InputQuality.java` `ATTACKS` |
| `recognition.input-quality.remember` | Weak input: attacks kept for | ms | 8000 | 1000 to 30000 | next run | `InputQuality.java` `REMEMBER_NANOS` |
| `recognition.input-quality.attack-window` | Weak input: attack level over | ms | 200 | 50 to 1000 | next run | `InputQuality.java` `ATTACK_WINDOW_NANOS` |

<!-- spec026-sections:end -->

## 5. The chord rule's migration and the rule for later updates

`ChordLeniency` (`desktop/TuxGuitar-audio-engine/src/main/java/app/tuxguitar/audio/engine/settings/ChordLeniency.java`) keeps `HISTORY`, the past value sets of the levels, each tagged with its level. `RecognitionSettings.fromStrings` reads a stored file whose chord values equal a past set as that level's current values (`ChordLeniency.migrate`). A file changed by hand matches no set and is read as it is.

Today `HISTORY` holds one set, spec 023's values tagged *Normal*: `recognition.chord.completeness=all-verifiable`, `recognition.chord.evidence` = 1/3, `recognition.advanced.chord.confirm=43`, and no `recognition.chord.profile.match` key.

**Rule.** A later change of a chord leniency level's values, or of the default of any value a level holds, must add the old set to `ChordLeniency.HISTORY`, tagged with its level, so that a learner's stored level is migrated to the new values. `RecognitionParameters.VERSION` stays 1.

## 6. Constants that stay fixed in the code

Every other number of the engines is a parameter of §4.11. The values below stay fixed by the owner's decisions of 2026-11-06 (spec 026, clarifications 1 to 3); changing one needs a rebuild. Paths: `AE` = `desktop/TuxGuitar-audio-engine/src/main/java/app/tuxguitar/audio/engine`, `PR` = `desktop/TuxGuitar-practice/src/app/tuxguitar/app/tools/custom/practice`, `CRATE` = `desktop/build-scripts/native-modules/tuxguitar-monitor-native/src`. `AE/monitor` (the amplifier and effects of spec 022) is not recognition and is not listed.

The numbers written inline in the code (a Hann window's 0.5, MIDI 69 and 440 Hz, masks, the presets kept to read older files, …) are listed with their reason in `specs/026-engine-external-parameters/inventory.md`, "Inline numbers that stay inline". `EngineConstantInventoryTest`, `SessionConstantInventoryTest` and the crate test `analysis::constant_inventory` fail on any constant that is neither a parameter's default nor listed below, and on any inline number neither mathematical nor listed.

### 6.1 Structural sizes

Why fixed: the sample rate, the analysis window and hop, the FFT sizes and the spectrum band range: the layout of the analysis and of the frame record, not tuning values (owner, clarification 1 of 2026-11-06).

| File | Constant | Reason |
|---|---|---|
| `AE/OfflineAnalysis.java` | `WINDOW` | analysis window 2048 and hop 256 (clarification 1) |
| `AE/OfflineAnalysis.java` | `HOP` | analysis window 2048 and hop 256 (clarification 1) |
| `AE/AudioEngine.java` | `INITIAL_SAMPLE_RATE` | sample rate 48,000 (clarification 1) |
| `AE/settings/RecognitionParameters.java` | `HOP_MILLIS` | one hop, 256 samples at 48 kHz |
| `AE/analysis/ArticulationDetector.java` | `FFT_SIZE` | articulation FFT 1024 (clarification 1) |
| `AE/frontend/SpectralSubtractor.java` | `FRAME` | subtraction frame 512 and hop 256 (clarification 1) |
| `AE/frontend/SpectralSubtractor.java` | `HOP` | subtraction frame 512 and hop 256 (clarification 1) |
| `AE/analysis/TarsosFastYinEstimator.java` | `DEFAULT_SAMPLE_RATE` | sample rate, window and hop (clarification 1) |
| `AE/analysis/TarsosFastYinEstimator.java` | `DEFAULT_WINDOW_SIZE` | sample rate, window and hop (clarification 1) |
| `AE/analysis/TarsosFastYinEstimator.java` | `DEFAULT_HOP_SIZE` | sample rate, window and hop (clarification 1) |
| `AE/backend/NativeBackend.java` | `HOP_SIZE` | hop 256 (clarification 1) |
| `AE/recognition/ExpectedPitchVerifier.java` | `MIN_WINDOW` | shortest analysis window of the target check (clarification 1) |
| `PR/session/PracticeParameters.java` | `HOP_NANOS` | one hop at 48 kHz |
| `CRATE/analysis/articulation.rs` | `FFT_SIZE` | as ArticulationDetector.FFT_SIZE |
| `CRATE/analysis/front_end.rs` | `FRAME` | as SpectralSubtractor.FRAME and HOP |
| `CRATE/analysis/front_end.rs` | `HOP` | as SpectralSubtractor.FRAME and HOP |
| `CRATE/analysis/pipeline.rs` | `WINDOW` | window 2048 and hop 256 (clarification 1) |
| `CRATE/analysis/pipeline.rs` | `HOP` | window 2048 and hop 256 (clarification 1) |
| `CRATE/analysis/target.rs` | `MIN_WINDOW` | as ExpectedPitchVerifier.MIN_WINDOW |

### 6.2 Buffers and layouts

Why fixed: ring and block sizes, record and array indices, layout versions and marker values: they size or address memory and decide nothing.

| File | Constant | Reason |
|---|---|---|
| `AE/AudioEngine.java` | `DEFAULT_RING_BUFFER_CAPACITY` | capture ring size |
| `AE/settings/RecognitionParameters.java` | `VERSION` | configuration layout version |
| `AE/analysis/ArticulationDetector.java` | `MEDIAN_HOPS` | length of the flux history the median is taken over (a buffer the detector keeps) |
| `AE/analysis/ArticulationDetector.java` | `DECISION_DELAY_HOPS` | look-ahead the detector buffers before deciding; bounds the post windows |
| `AE/analysis/ArticulationDetector.java` | `FAST_DECISION_DELAY_HOPS` | look-ahead the detector buffers before deciding; bounds the post windows |
| `AE/analysis/AnalysisFrame.java` | `NO_CAPTURE_SAMPLE` | marker value of the frame record |
| `AE/frontend/GainScale.java` | `MIN_DB` | the gain control's scale, derived from InputGain's range |
| `AE/frontend/GainScale.java` | `MAX_DB` | the gain control's scale, derived from InputGain's range |
| `AE/frontend/GainScale.java` | `POSITIONS` | the gain control's scale, derived from InputGain's range |
| `AE/frontend/GainScale.java` | `NEUTRAL_POSITION` | the gain control's scale, derived from InputGain's range |
| `AE/frontend/InputGain.java` | `MIN_MULTIPLIER` | the range of the input gain control (-6 to +18 dB), set by the learner or the calibration |
| `AE/frontend/InputGain.java` | `MAX_MULTIPLIER` | the range of the input gain control (-6 to +18 dB), set by the learner or the calibration |
| `AE/calibration/RecognitionProfile.java` | `LAYOUT` | profile file layout version |
| `AE/input/CaptureClock.java` | `NOT_READY` | marker value |
| `AE/input/CaptureClock.java` | `CAPACITY` | ring of clock readings |
| `AE/backend/NativeBackend.java` | `BLOCK_FRAMES` | native capture block and hop block sizes |
| `AE/backend/NativeBackend.java` | `HOP_BLOCK` | native capture block and hop block sizes |
| `AE/input/CaptureService.java` | `FRAMES_PER_READ` | capture read size |
| `AE/calibration/RecordingQuality.java` | `BLOCK` | block the quality is measured on |
| `AE/nativeengine/NativeParameters.java` | `LEVEL_TOO_LOW` | indices and length of the float[21] (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `LEVEL_TOO_HIGH` | indices and length of the float[21] (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `LEVEL_CLIP_SAMPLE` | indices and length of the float[21] (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `LEVEL_CLIP_RATIO` | indices and length of the float[21] (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `ONSET_FLUX_FACTOR` | indices and length of the float[21] (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `ONSET_FLUX_OFFSET` | indices and length of the float[21] (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `ONSET_MIN_LEVEL` | indices and length of the float[21] (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `ONSET_REFRACTORY` | indices and length of the float[21] (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `ONSET_GATE` | indices and length of the float[21] (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `ONSET_PRE_FIRST` | indices and length of the float[21] (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `ONSET_PRE_LAST` | indices and length of the float[21] (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `ONSET_POST_FIRST` | indices and length of the float[21] (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `ONSET_POST_LAST` | indices and length of the float[21] (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `FRONT_GAIN` | indices and length of the float[21] (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `FRONT_NOISE_REDUCTION` | indices and length of the float[21] (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `FRONT_SUBTRACTION` | indices and length of the float[21] (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `STRUM_ARM_FLOOR` | indices and length of the float[21] (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `STRUM_CLIP_SAMPLE` | indices and length of the float[21] (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `STRUM_WEAK` | indices and length of the float[21] (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `STRUM_LOUD` | indices and length of the float[21] (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `YIN_THRESHOLD` | indices and length of the float[21] (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `LENGTH` | indices and length of the float[21] (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `T_TARGET_SEARCH` | indices and length of the tuning array (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `T_TARGET_PERIODS` | indices and length of the tuning array (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `T_POLY_CANDIDATE_LOW` | indices and length of the tuning array (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `T_POLY_CANDIDATE_HIGH` | indices and length of the tuning array (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `T_POLY_HARMONICS` | indices and length of the tuning array (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `T_POLY_MAX_HARMONIC` | indices and length of the tuning array (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `T_POLY_TOLERANCE` | indices and length of the tuning array (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `T_POLY_CANDIDATES` | indices and length of the tuning array (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `T_POLY_MIN_RELATIVE` | indices and length of the tuning array (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `T_POLY_FLOOR` | indices and length of the tuning array (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `T_POLY_SUBHARMONIC` | indices and length of the tuning array (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `T_POLY_FLOOR_WIDTH` | indices and length of the tuning array (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `T_POLY_FLOOR_FACTOR` | indices and length of the tuning array (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `T_ATTACK_LAG` | indices and length of the tuning array (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `T_ATTACK_MAX_FREQUENCY` | indices and length of the tuning array (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `T_ATTACK_PARTIALS` | indices and length of the tuning array (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `T_ATTACK_BETA` | indices and length of the tuning array (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `T_ATTACK_SIGMA` | indices and length of the tuning array (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `T_CHORD_PARTIAL_PRESENCE` | indices and length of the tuning array (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `T_CHORD_NEIGHBOUR_MARGIN` | indices and length of the tuning array (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `T_STRUM_WINDOW` | indices and length of the tuning array (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `T_STRUM_QUIET` | indices and length of the tuning array (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `T_STRUM_RISE` | indices and length of the tuning array (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `T_STRUM_CLIP_SHARE` | indices and length of the tuning array (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `T_FRONT_HIGH_PASS` | indices and length of the tuning array (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `T_FRONT_NOISE_RISE` | indices and length of the tuning array (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `T_FRONT_NOISE_FALL` | indices and length of the tuning array (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `T_FRONT_NOISE_TRUSTED` | indices and length of the tuning array (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `T_GATE_OPEN` | indices and length of the tuning array (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `T_GATE_CLOSE` | indices and length of the tuning array (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `T_GATE_REDUCTION` | indices and length of the tuning array (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `T_GATE_HOLD` | indices and length of the tuning array (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `T_SUBTRACTION` | indices and length of the tuning array (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `T_SUBTRACTION_FLOOR` | indices and length of the tuning array (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `T_SUBTRACTION_LEARN` | indices and length of the tuning array (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `TUNING_LENGTH` | indices and length of the tuning array (contracts/native-parameters.md) |
| `AE/nativeengine/FrameRecords.java` | `LENGTH` | layout of the native frame record |
| `AE/nativeengine/FrameRecords.java` | `TIMESTAMP_NANOS` | layout of the native frame record |
| `AE/nativeengine/FrameRecords.java` | `CAPTURE_SAMPLE_END` | layout of the native frame record |
| `AE/nativeengine/FrameRecords.java` | `ARRIVAL_NANOS` | layout of the native frame record |
| `AE/nativeengine/FrameRecords.java` | `RMS_DBFS` | layout of the native frame record |
| `AE/nativeengine/FrameRecords.java` | `PEAK_DBFS` | layout of the native frame record |
| `AE/nativeengine/FrameRecords.java` | `CLIPPED_RATIO` | layout of the native frame record |
| `AE/nativeengine/FrameRecords.java` | `LEVEL_STATE` | layout of the native frame record |
| `AE/nativeengine/FrameRecords.java` | `F0_HZ` | layout of the native frame record |
| `AE/nativeengine/FrameRecords.java` | `CONFIDENCE` | layout of the native frame record |
| `AE/nativeengine/FrameRecords.java` | `ONSET` | layout of the native frame record |
| `AE/nativeengine/FrameRecords.java` | `PARTS` | layout of the native frame record |
| `AE/nativeengine/FrameRecords.java` | `HEADER` | layout of the native frame record |
| `AE/nativeengine/FrameRecords.java` | `PART_SPECTRUM` | layout of the native frame record |
| `AE/nativeengine/FrameRecords.java` | `PART_TARGET` | layout of the native frame record |
| `AE/nativeengine/FrameRecords.java` | `PART_ATTACK` | layout of the native frame record |
| `AE/nativeengine/FrameRecords.java` | `PART_CHORD` | layout of the native frame record |
| `AE/nativeengine/FrameRecords.java` | `CAPACITY` | layout of the native frame record |
| `PR/session/PracticeSession.java` | `RECENT_FRAMES` | frame history size |
| `PR/session/TickInterpolator.java` | `CAPACITY` | ring of tick readings; bounds recognition.clock.min-points (clarification 3) |
| `PR/session/FollowStarter.java` | `FIRST_TICK` | where the timeline of a run starts (one beat of lead-in) |
| `PR/session/PassageSettings.java` | `OFF` | marker value |
| `CRATE/analysis/articulation.rs` | `MEDIAN_HOPS` | as ArticulationDetector.MEDIAN_HOPS |
| `CRATE/analysis/chord.rs` | `MAX_PITCHES` | preallocated evidence sizes |
| `CRATE/analysis/chord.rs` | `MAX_RESIDUAL` | preallocated evidence sizes |
| `CRATE/analysis/level.rs` | `TOO_LOW` | level state codes of the record |
| `CRATE/analysis/level.rs` | `SUITABLE` | level state codes of the record |
| `CRATE/analysis/level.rs` | `TOO_HIGH` | level state codes of the record |
| `CRATE/analysis/level.rs` | `CLIPPING` | level state codes of the record |
| `CRATE/analysis/frame.rs` | `LENGTH` | layout of the frame record |
| `CRATE/analysis/frame.rs` | `TIMESTAMP_NANOS` | layout of the frame record |
| `CRATE/analysis/frame.rs` | `CAPTURE_SAMPLE_END` | layout of the frame record |
| `CRATE/analysis/frame.rs` | `ARRIVAL_NANOS` | layout of the frame record |
| `CRATE/analysis/frame.rs` | `RMS_DBFS` | layout of the frame record |
| `CRATE/analysis/frame.rs` | `PEAK_DBFS` | layout of the frame record |
| `CRATE/analysis/frame.rs` | `CLIPPED_RATIO` | layout of the frame record |
| `CRATE/analysis/frame.rs` | `LEVEL_STATE` | layout of the frame record |
| `CRATE/analysis/frame.rs` | `F0_HZ` | layout of the frame record |
| `CRATE/analysis/frame.rs` | `CONFIDENCE` | layout of the frame record |
| `CRATE/analysis/frame.rs` | `ONSET` | layout of the frame record |
| `CRATE/analysis/frame.rs` | `PARTS` | layout of the frame record |
| `CRATE/analysis/frame.rs` | `HEADER` | layout of the frame record |
| `CRATE/analysis/frame.rs` | `PART_SPECTRUM` | layout of the frame record |
| `CRATE/analysis/frame.rs` | `PART_TARGET` | layout of the frame record |
| `CRATE/analysis/frame.rs` | `PART_ATTACK` | layout of the frame record |
| `CRATE/analysis/frame.rs` | `PART_CHORD` | layout of the frame record |
| `CRATE/analysis/frame.rs` | `CAPACITY` | layout of the frame record |
| `CRATE/analysis/strum_clip.rs` | `NO_STRUM` | strum verdict codes |
| `CRATE/analysis/strum_clip.rs` | `GOOD` | strum verdict codes |
| `CRATE/analysis/strum_clip.rs` | `MAX_ARM_QUIET_HOPS` | the level history of the arm check; bounds recognition.chord-evidence.strum-quiet (at most 200) |
| `CRATE/analysis/strum_clip.rs` | `CLIPPING` | strum verdict codes |
| `CRATE/analysis/strum_clip.rs` | `TOO_LOUD` | strum verdict codes |
| `CRATE/analysis/strum_clip.rs` | `TOO_WEAK` | strum verdict codes |
| `CRATE/analysis/target.rs` | `MAX_HYPOTHESES` | preallocated hypotheses |

### 6.3 Numerical safety

Why fixed: epsilons, ridges, silence floors and tolerances that keep a computation finite; not tuning values.

| File | Constant | Reason |
|---|---|---|
| `AE/analysis/NnlsSolver.java` | `EPSILON` | numerical safety of the least-squares solver |
| `AE/analysis/NnlsSolver.java` | `RIDGE` | numerical safety of the least-squares solver |
| `AE/analysis/StrumClipMeter.java` | `SILENCE_DBFS` | level reported for digital silence |
| `AE/frontend/InputGain.java` | `NEUTRAL` | unit gain |
| `AE/frontend/NoiseEstimator.java` | `MINIMUM_RMS` | floor of the noise estimate, avoids a zero level |
| `AE/calibration/FrontEndState.java` | `GAIN_TOLERANCE` | equality tolerance of two stored gains |
| `AE/calibration/RecordingQuality.java` | `FLOOR_DBFS` | level reported for digital silence |
| `PR/session/TimingTolerance.java` | `MS` | nanoseconds per millisecond |
| `CRATE/analysis/attack.rs` | `EPSILON` | as NnlsSolver |
| `CRATE/analysis/attack.rs` | `RIDGE` | as NnlsSolver |
| `CRATE/analysis/front_end.rs` | `NEUTRAL_GAIN` | unit gain, as InputGain.NEUTRAL |
| `CRATE/analysis/front_end.rs` | `MINIMUM_RMS` | as NoiseEstimator.MINIMUM_RMS |
| `CRATE/analysis/strum_clip.rs` | `SILENCE_DBFS` | as StrumClipMeter.SILENCE_DBFS |

### 6.4 Plumbing

Why fixed: timeouts and thread waits (owner, clarification 2).

| File | Constant | Reason |
|---|---|---|
| `AE/AudioEngine.java` | `NATIVE_OPEN_TIMEOUT_MILLIS` | native open timeout (clarification 2) |
| `AE/backend/NativeBackend.java` | `WAIT_MILLIS` | thread waits (clarification 2) |
| `AE/backend/NativeBackend.java` | `JOIN_MILLIS` | thread waits (clarification 2) |
| `AE/calibration/SoundDetection.java` | `STOP_AFTER_LAST_MILLIS` | calibration timeouts (clarification 2) |
| `AE/calibration/SoundDetection.java` | `SILENCE_TIMEOUT_MILLIS` | calibration timeouts (clarification 2) |
| `AE/calibration/SoundDetection.java` | `TOTAL_TIMEOUT_MILLIS` | calibration timeouts (clarification 2) |
| `PR/session/MidiFollowPlayer.java` | `STOP_SETTLE_MILLIS` | sequencer stop wait |
| `PR/session/FollowRunRecorder.java` | `MAX_MILLIS` | recording limits of a Follow run |
| `PR/session/FollowRunRecorder.java` | `MARGIN_MILLIS` | recording limits of a Follow run |

### 6.5 Clock

Why fixed: the capture clock's drift estimate and TuxGuitar's tick rate (owner, clarifications 2 and 3). The native drift correction of `src/resampler.rs` is outside `analysis/` and fixed for the same reason.

| File | Constant | Reason |
|---|---|---|
| `AE/input/CaptureClock.java` | `WINDOW_NANOS` | capture clock drift estimate (clarification 2: clock drift correction) |
| `AE/input/CaptureClock.java` | `MIN_READS` | capture clock drift estimate (clarification 2: clock drift correction) |
| `PR/session/PlayerTempoClock.java` | `NOMINAL_TICKS_PER_SECOND` | nominal tick rate (clarification 3) |

### 6.6 Score and display

Why fixed: figures shown to the learner and the Arcade score: they change no recognition decision (owner, clarifications 2 and 3).

| File | Constant | Reason |
|---|---|---|
| `PR/session/PracticeParameters.java` | `MEASURED_DELAY_MEDIAN_MILLIS` | measured figures shown to the learner, not used to decide |
| `PR/session/PracticeParameters.java` | `MEASURED_DELAY_P90_MILLIS` | measured figures shown to the learner, not used to decide |
| `PR/session/PracticeParameters.java` | `MEASURED_SPEED_LIMIT_SECONDS` | measured figures shown to the learner, not used to decide |
| `PR/session/PracticeSession.java` | `SPEED_SHOW_NANOS` | display duration (clarification 3) |
| `PR/session/SessionLoop.java` | `MIN_UI_INTERVAL_NANOS` | screen refresh interval |
| `PR/session/FollowSession.java` | `REASON_SPAN_NANOS` | display duration (clarification 3) |
| `PR/arcade/core/ArcadeScoring.java` | `FIRST_STEP` | Arcade score (clarification 2) |
| `PR/arcade/core/ArcadeScoring.java` | `SECOND_STEP` | Arcade score (clarification 2) |

### 6.7 Calibration protocol

Why fixed: the exercise played to the learner (8 clicks, their lead and period), not a threshold (owner, clarification 3).

| File | Constant | Reason |
|---|---|---|
| `PR/session/CalibrationRun.java` | `LEAD_NANOS` | calibration protocol (clarification 3) |
| `PR/session/CalibrationRun.java` | `PERIOD_NANOS` | calibration protocol (clarification 3) |
| `PR/session/CalibrationTaps.java` | `CLICKS` | calibration protocol, 8 clicks (clarification 3) |

### 6.8 Already settings

Why fixed: the default, bounds or options of a setting that existed before spec 026 (§4.1–4.8, the Follow window's own settings), or the maximum of a new one.

| File | Constant | Reason |
|---|---|---|
| `AE/AudioEngine.java` | `DEFAULT_TOO_LOW_RMS_DBFS` | the defaults of recognition.input.level.min, .max, advanced.level.clip-sample and clip-share |
| `AE/AudioEngine.java` | `DEFAULT_TOO_HIGH_RMS_DBFS` | the defaults of recognition.input.level.min, .max, advanced.level.clip-sample and clip-share |
| `AE/AudioEngine.java` | `DEFAULT_CLIPPED_SAMPLE_LEVEL` | the defaults of recognition.input.level.min, .max, advanced.level.clip-sample and clip-share |
| `AE/AudioEngine.java` | `DEFAULT_CLIPPING_RATIO` | the defaults of recognition.input.level.min, .max, advanced.level.clip-sample and clip-share |
| `AE/analysis/ArticulationDetector.java` | `DEFAULT_MIN_LEVEL_DB` | the defaults of recognition.advanced.attack.min-level and recognition.note.attack |
| `AE/analysis/ArticulationDetector.java` | `DEFAULT_GATE_DB` | the defaults of recognition.advanced.attack.min-level and recognition.note.attack |
| `PR/session/PlayTimeWindow.java` | `MIN_MILLIS` | bounds and default of recognition.follow.play-time |
| `PR/session/PlayTimeWindow.java` | `MAX_MILLIS` | bounds and default of recognition.follow.play-time |
| `PR/session/PlayTimeWindow.java` | `DEFAULT_MILLIS` | bounds and default of recognition.follow.play-time |
| `PR/session/FollowSettings.java` | `DEFAULT_WINDOW_MILLIS` | the Follow window's own display settings (clarification 2) |
| `PR/session/FollowSettings.java` | `MIN_PERCENT` | the Follow window's own display settings (clarification 2) |
| `PR/session/FollowSettings.java` | `MAX_PERCENT` | the Follow window's own display settings (clarification 2) |
| `PR/session/FollowSettings.java` | `DEFAULT_PERCENT` | the Follow window's own display settings (clarification 2) |
| `PR/session/FollowSettings.java` | `MIN_MARKER_PERCENT` | the Follow window's own display settings (clarification 2) |
| `PR/session/FollowSettings.java` | `MAX_MARKER_PERCENT` | the Follow window's own display settings (clarification 2) |
| `PR/session/FollowSettings.java` | `DEFAULT_MARKER_PERCENT` | the Follow window's own display settings (clarification 2) |
| `PR/session/FollowSettings.java` | `MIN_FAR_SCALE_PERCENT` | the Follow window's own display settings (clarification 2) |
| `PR/session/FollowSettings.java` | `MAX_FAR_SCALE_PERCENT` | the Follow window's own display settings (clarification 2) |
| `PR/session/FollowSettings.java` | `DEFAULT_FAR_SCALE_PERCENT` | the Follow window's own display settings (clarification 2) |
| `PR/session/FollowSettings.java` | `MIN_HORIZON_PERCENT` | the Follow window's own display settings (clarification 2) |
| `PR/session/FollowSettings.java` | `MAX_HORIZON_PERCENT` | the Follow window's own display settings (clarification 2) |
| `PR/session/FollowSettings.java` | `DEFAULT_HORIZON_PERCENT` | the Follow window's own display settings (clarification 2) |
| `PR/session/TimingTolerance.java` | `MIN_WIDTH_MILLIS` | bounds of recognition.tempo.window |
| `PR/session/TimingTolerance.java` | `MAX_WIDTH_MILLIS` | bounds of recognition.tempo.window |
| `PR/session/LatencyCompensation.java` | `MAX_LIMIT_MILLIS` | the maximum of recognition.clock.latency-limit |
| `PR/session/PassagePolicy.java` | `DEFAULT_MEASURES` | defaults of the passage settings |
| `PR/session/PassagePolicy.java` | `DEFAULT_MAX_ERROR_RATE` | defaults of the passage settings |
| `PR/session/PassagePolicy.java` | `DEFAULT_MAX_RETRIES` | defaults of the passage settings |

### 6.9 Not recognition

Why fixed: the click and removed-sound playback, amp sound names and volume, a serialization id.

| File | Constant | Reason |
|---|---|---|
| `AE/nativeengine/NativeRecognitionException.java` | `serialVersionUID` | serialization id of the exception |
| `PR/session/MidiClickPlayer.java` | `KEY` | the click sound |
| `PR/session/MidiClickPlayer.java` | `VELOCITY` | the click sound |
| `PR/session/MidiClickPlayer.java` | `CLICK_MILLIS` | the click sound |
| `PR/session/RemovedSoundsPlayer.java` | `LATE_TOLERANCE_NANOS` | playback of the removed sounds |
| `PR/session/RemovedSoundsPlayer.java` | `POLL_MILLIS` | playback of the removed sounds |
| `PR/session/RemovedSoundsPlayer.java` | `DEFAULT_VELOCITY` | playback of the removed sounds |
| `PR/session/AmpSoundStore.java` | `NAME_LENGTH` | amp sound names |
| `PR/session/AmpSoundStore.java` | `RENAMES` | amp sound names |
| `PR/session/AmpSoundSettings.java` | `DEFAULT_GUITAR_VOLUME` | amp volume |
