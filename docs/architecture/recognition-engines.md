# Recognition engines

This document describes the two audio-recognition engines of the Interactive Practice modules, the interface between them and the practice session, every parameter they read, the constants still fixed in the code, and the rule for later changes. It was written for spec 024 (FR-014, research R10) from the source as it stands in the working tree.

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
- Its values come from `RecognitionSettings` through `AudioEngine` (level analyser, articulation detector, strum meter) and from the fixed defaults of each analyser (section 6).

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

`desktop/TuxGuitar-audio-engine/src/main/java/app/tuxguitar/audio/engine/nativeengine/NativeParameters.java` builds a `float[21]`; the crate's `src/analysis/mod.rs` reads the same indices into `Parameters` (`from_slice` refuses a shorter array). It is sent at `open` and with `configure` whenever the settings or the live front end change (`AudioEngine.pushNativeParameters`). The native side does not validate bounds: the values come already validated from `RecognitionSettings`.

| Index | Name | Source | Default | Unit |
|---|---|---|---|---|
| 0 | `LEVEL_TOO_LOW` | `recognition.input.level.min` | −40 | dBFS |
| 1 | `LEVEL_TOO_HIGH` | `recognition.input.level.max` | −6 | dBFS |
| 2 | `LEVEL_CLIP_SAMPLE` | `recognition.advanced.level.clip-sample` | 0.99 | share of full scale |
| 3 | `LEVEL_CLIP_RATIO` | `recognition.advanced.level.clip-share` | 0.10 | share of samples |
| 4 | `ONSET_FLUX_FACTOR` | fixed: `ArticulationDetector.DEFAULT_FLUX_MEDIAN_FACTOR` | 1.5 | × median flux |
| 5 | `ONSET_FLUX_OFFSET` | fixed: `ArticulationDetector.DEFAULT_FLUX_OFFSET` | 5.0 | flux units |
| 6 | `ONSET_MIN_LEVEL` | `recognition.advanced.attack.min-level` | −50 | dBFS |
| 7 | `ONSET_REFRACTORY` | fixed: `ArticulationDetector.DEFAULT_REFRACTORY_HOPS` (fast: 6) | 12 | hops |
| 8 | `ONSET_GATE` | `recognition.note.attack` | 4.5 | dB |
| 9 | `ONSET_PRE_FIRST` | fixed in `NativeParameters` (fast: 8) | 19 | hops before the transient |
| 10 | `ONSET_PRE_LAST` | fixed (fast: 2) | 2 | hops |
| 11 | `ONSET_POST_FIRST` | fixed (fast: 3) | 4 | hops after the transient |
| 12 | `ONSET_POST_LAST` | fixed: `ArticulationDetector.DECISION_DELAY_HOPS` (fast: 8) | 13 | hops |
| 13 | `FRONT_GAIN` | live input gain (in memory only, `InputGain` −6 to +18 dB) | 1.0 | multiplier |
| 14 | `FRONT_NOISE_REDUCTION` | `recognition.input.noise-reduction` (live value) | 0 | 0 or 1 |
| 15 | `FRONT_SUBTRACTION` | live front end | 0 | 0 or 1 |
| 16 | `STRUM_ARM_FLOOR` | `recognition.input.level.min` | −40 | dBFS |
| 17 | `STRUM_CLIP_SAMPLE` | `recognition.advanced.level.clip-sample` | 0.99 | share of full scale |
| 18 | `STRUM_WEAK` | `recognition.input.level.min` | −40 | dBFS |
| 19 | `STRUM_LOUD` | `recognition.input.level.max` | −6 | dBFS |
| 20 | `YIN_THRESHOLD` | fixed: `NativeParameters.DEFAULT_YIN_THRESHOLD` | 0.20 | YIN absolute threshold |

Spec 024 changed none of these: the chord profile rule runs in Java on the spectrum both engines already give.

## 4. Recognition settings (`RecognitionParameters`)

Source: `desktop/TuxGuitar-audio-engine/src/main/java/app/tuxguitar/audio/engine/settings/RecognitionParameters.java` (34 parameters). Each key is `"recognition."` followed by the part given in the code; the tables show the full stored key.

**Where they live.** `RecognitionSettingsStore` saves them in the TuxGuitar configuration module `tuxguitar-audio-recognition`, with `recognition.version` = 1 (`RecognitionParameters.VERSION_KEY`, `VERSION`). On reading, `RecognitionSettings.fromStrings` repairs every value: a missing, empty or unreadable value gives the default, a number out of bounds is clamped. Cross-rule: `recognition.note.accept` is never below `recognition.tuner.in-tune`. Unknown `recognition.*` keys are dropped at the next save.

**Where they are set.** In the Recognition settings window (TuxGuitar Configuration › *Audio recognition*, `AudioRecognitionOption` and `RecognitionSettingsPanel`), one category per `RecognitionParameter.Section`, each with a *Restore* button. Other places that change a value are listed in the last column. A device profile (spec 013) may overlay only the parameters of `ProfileParameters.LIST` (marked "profile").

**Stored values.** A value shown in % is stored as a fraction (for example `recognition.chord.profile.match=0.79` shows 79 %). Defaults and bounds below are the stored values.

**When they take effect.** Tuner values at once; practice values at the next Start (`PracticeParameters` is built once per run); level and attack values restart the listening briefly and are pushed to the native engine with `configure`.

### 4.1 Audio input (`Section.INPUT`)

| Key | Meaning | Unit | Default | Bounds | Also set by |
|---|---|---|---|---|---|
| `recognition.input.level.min` | Quieter sound is "too low" and never accepted; also the strum meter's arm floor and weak level | dBFS | −40 | −60 to −25 | profile |
| `recognition.input.level.max` | Louder sound is "too high"; also the strum meter's loud level | dBFS | −6 | −20 to −1 | |
| `recognition.input.noise-reduction` | Noise gate and noise subtraction in the front end | on/off | off | | |
| `recognition.input.gain-suggestion` | Offers a gain after the level check (never applied without confirmation) | on/off | on | | |

### 4.2 Tuner (`Section.TUNER`)

| Key | Meaning | Unit | Default | Bounds | Also set by |
|---|---|---|---|---|---|
| `recognition.tuner.in-tune` | Distance from the target shown as "in tune" | cents | 5 | 1 to 20 | |
| `recognition.tuner.hold` | The tuner keeps the last reliable note in grey | on/off | on | | |

### 4.3 Practice: notes (`Section.NOTES`)

| Key | Meaning | Unit | Default | Bounds | Also set by |
|---|---|---|---|---|---|
| `recognition.note.accept` | Accepted distance from the written pitch | cents | 35 | 10 to 45 (never under the in-tune range) | profile; Follow relaxation |
| `recognition.note.confidence` | Minimum pitch confidence before a note is judged | % (fraction) | 0.80 | 0.50 to 0.95 | profile; Follow and Arcade relaxation |
| `recognition.note.stability` | Time the right (or wrong) note must sound before it is judged | ms (turned into hops) | 107 | 50 to 300 | profile; Follow and Arcade relaxation |
| `recognition.note.attack` | Level gate of a new attack (`ArticulationDetector` gate, native `ONSET_GATE`) | dB | 4.5 | 2.0 to 10.0 | profile |
| `recognition.note.legato` | Legato rule; one option only, `strict` (every note needs its own attack) | choice | `strict` | `strict` | |
| `recognition.note.technique` | Evaluate the written playing techniques; off shows them as not verifiable | on/off | on | | |
| `recognition.tempo.window` | Half-width of the on-time window in Arcade, at 120 BPM (scales with tempo) | ms | 100 | 40 to 200 | the Arcade slider of the *Mode* tab |

### 4.4 Practice: chords (`Section.CHORDS`)

| Key | Meaning | Unit | Default | Bounds | Also set by |
|---|---|---|---|---|---|
| `recognition.chord.evidence` | Per-string rule: share of a string's unique partials that must clearly sound | % (fraction) | 1/3 (0.333…) | 0.20 to 0.80 | profile; Follow and Arcade relaxation |
| `recognition.chord.window` | Time after the strum within which the chord must be complete | ms | 600 | 300 to 1500 | profile |
| `recognition.chord.completeness` | Chord rule: `profile` (note names over the strum) or `all-verifiable` (every string separately) | choice | `profile` | `all-verifiable`, `profile` | chord check level |
| `recognition.chord.foreign` | Extra wrong strings; one option only, `ignore` (not checked yet) | choice | `ignore` | `ignore` | |
| `recognition.chord.profile.match` | Profile rule: the match (cosine) from which a frame is complete | % (fraction) | 0.79 | 0.50 to 0.99 | chord check level |
| `recognition.chord.profile.names` | Profile rule: a required name summed under this share of the strongest required name is weak | % (fraction) | 0.40 | 0.05 to 0.60 | |
| `recognition.chord.attack` | A chord may be accepted on its attack (guarded by the weak names under the profile rule) | on/off | on | | chord check level |

### 4.5 Chord details (`Section.CHORD_DETAILS`)

| Key | Meaning | Unit | Default | Bounds | Also set by |
|---|---|---|---|---|---|
| `recognition.chord.detail.top` | Profile rule: the highest pitch summed into the profile (84 = C6) | MIDI | 84 | 60 to 112 | |
| `recognition.chord.detail.settle` | Profile rule: time from the reset before a chord can be complete | ms | 200 | 0 to 500 | |

### 4.6 Passages (`Section.PASSAGES`)

| Key | Meaning | Unit | Default | Bounds | Also set by |
|---|---|---|---|---|---|
| `recognition.passage.measures` | Passage length for repeats; `0` plays straight through | choice (measures) | `2` | `0`, `1`, `2`, `4` | the practice window, for the current run |
| `recognition.passage.threshold` | Error share above which a passage is played again | choice (%) | `50` | `25`, `50`, `75` | |
| `recognition.passage.retries` | Most repeats of one passage | count | 2 | 0 to 5 | |
| `recognition.follow.play-time` | In Follow, how long after its time a note may still be played | ms | 600 | 100 to 1200 | the Follow slider of the *Mode* tab |

### 4.7 Recognition engine (`Section.ENGINE`)

| Key | Meaning | Unit | Default | Bounds | Also set by |
|---|---|---|---|---|---|
| `recognition.engine.pitch` | Engine choice: `automatic` (shown); a stored `native` or `fastyin` is kept and listed by the menu | choice | `automatic` | `automatic`, `native`, `fastyin` | system property `tuxguitar.recognition.engine` (`java` or `native`) for Automatic only |

### 4.8 Advanced (`Section.ADVANCED`)

| Key | Meaning | Unit | Default | Bounds | Also set by |
|---|---|---|---|---|---|
| `recognition.note.fret-sharp` | Extra sharp allowance per fret (sharp side only); 0 switches it off | cents per fret | 0.7 | 0 to 2 | profile |
| `recognition.advanced.level.clip-sample` | A sample at or above this share of full scale counts as clipped | % (fraction) | 0.99 | 0.90 to 1.00 | |
| `recognition.advanced.level.clip-share` | Share of clipped samples above which the frame is clipping | % (fraction) | 0.10 | 0.01 to 0.50 | |
| `recognition.advanced.tuner.confidence` | Minimum confidence before the tuner shows a note | % (fraction) | 0.80 | 0.50 to 0.95 | |
| `recognition.advanced.attack.min-level` | Sound quieter than this is never a new attack | dBFS | −50 | −70 to −30 | profile |
| `recognition.advanced.chord.min-partials` | Per-string rule: unique partials a string needs to be verifiable and heard | count | 2 | 1 to 4 | profile; Follow and Arcade relaxation |
| `recognition.advanced.chord.confirm` | Time every chord condition must hold (`stableHops`, both rules) | ms (turned into hops: 43 ms = 8) | 43 | 20 to 150 | profile; Follow and Arcade relaxation |

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

## 5. The chord rule's migration and the rule for later updates

`ChordLeniency` (`desktop/TuxGuitar-audio-engine/src/main/java/app/tuxguitar/audio/engine/settings/ChordLeniency.java`) keeps `HISTORY`, the past value sets of the levels, each tagged with its level. `RecognitionSettings.fromStrings` reads a stored file whose chord values equal a past set as that level's current values (`ChordLeniency.migrate`). A file changed by hand matches no set and is read as it is.

Today `HISTORY` holds one set, spec 023's values tagged *Normal*: `recognition.chord.completeness=all-verifiable`, `recognition.chord.evidence` = 1/3, `recognition.advanced.chord.confirm=43`, and no `recognition.chord.profile.match` key.

**Rule.** A later change of a chord leniency level's values, or of the default of any value a level holds, must add the old set to `ChordLeniency.HISTORY`, tagged with its level, so that a learner's stored level is migrated to the new values. `RecognitionParameters.VERSION` stays 1.

## 6. Constants still fixed in the code (left for spec 026)

These values are not settings yet: changing them needs a rebuild. Spec 026 is to make them external parameters. Java and native values are the same where a stage was ported.

**Java engine** (`desktop/TuxGuitar-audio-engine/src/main/java/app/tuxguitar/audio/engine/`):

| File | Constants |
|---|---|
| `analysis/TarsosFastYinEstimator.java` | `DEFAULT_WINDOW_SIZE` 2048, `DEFAULT_HOP_SIZE` 256, `DEFAULT_SAMPLE_RATE` 48000 |
| `analysis/ArticulationDetector.java` | `DEFAULT_FLUX_MEDIAN_FACTOR` 1.5, `DEFAULT_FLUX_OFFSET` 5.0, `DEFAULT_REFRACTORY_HOPS` 12, `DECISION_DELAY_HOPS` 13, gate windows 19/2/4/13, `FAST_REFRACTORY_HOPS` 6, `FAST_DECISION_DELAY_HOPS` 8, fast windows 8/2/3/8, `FFT_SIZE` 1024, `MEDIAN_HOPS` 10 |
| `analysis/PolyphonicAnalyzer.java` | `Parameters.defaults()`: FFT 8192, bands MIDI 24–112, candidates MIDI 33–88, 12 harmonics, below 5000 Hz, ±25 cents, 6 candidates, relative salience 0.10, floor −70 dBFS, subharmonic ratio 0.30; `FLOOR_HALF_WIDTH_HZ` 25, `FLOOR_FACTOR` 2 |
| `analysis/AttackAnalyzer.java` | `Parameters.defaults()`: FFT 4096, lag 22 hops, 5000 Hz, 14 partials, beta 0.3, sigma 1.2 bins |
| `analysis/ChordEvidence.java` | `PARTIAL_PRESENCE_RATIO` 0.05, `NEIGHBOUR_MARGIN` 1.5 |
| `analysis/StrumClipMeter.java` | window 300 ms, 60 quiet hops, attack rise 12 dB, clip share 0.005 |
| `recognition/ExpectedPitchVerifier.java` | `SEARCH_CENTS` 20, `PERIODS` 4, `MIN_WINDOW` 512 |
| `pitch/TolerancePolicy.java` | `MAX_SHARP_CENTS` 49 |
| `nativeengine/NativeParameters.java` | `DEFAULT_YIN_THRESHOLD` 0.20, onset windows (normal 19/2/4, fast 8/2/3) |
| `frontend/HighPass.java` | `CORNER_HZ` 40 |
| `frontend/NoiseEstimator.java` | `MINIMUM_RMS` 1.5e-5, `RISE_DB_PER_SECOND` 0.5, `FALL_RATE` 0.2, `TRUSTED_AFTER_HOPS` 188 |
| `frontend/NoiseGate.java` | `OPEN_MARGIN_DB` 9, `CLOSE_MARGIN_DB` 5, `REDUCTION_DB` 20, `HOLD_HOPS` 19 |
| `frontend/SpectralSubtractor.java` | `FRAME` 512, `HOP` 256, `ALPHA` 1.5, `BETA` 0.05, `LEARN_RATE` 0.1 |
| `frontend/InputGain.java` | gain range −6 to +18 dB |
| `frontend/GainSuggester.java` | `TARGET_PLAYING_RMS_DBFS` −22, `MAX_PEAK_DBFS` −3, `NO_CHANGE_DB` 1.5, `MIN_SIGNAL_TO_NOISE_DB` 20 |

**Native engine** (`desktop/build-scripts/native-modules/tuxguitar-monitor-native/src/`):

| File | Constants |
|---|---|
| `analysis/pipeline.rs` | `WINDOW` 2048, `HOP` 256 |
| `analysis/polyphonic.rs` | `PolyphonicParameters::defaults()` (same values as `PolyphonicAnalyzer`), `FLOOR_HALF_WIDTH_HZ` 25, `FLOOR_FACTOR` 2 |
| `analysis/chord.rs` | `PARTIAL_PRESENCE_RATIO` 0.05, `NEIGHBOUR_MARGIN` 1.5, `MAX_PITCHES` 16 |
| `analysis/articulation.rs` | `FFT_SIZE` 1024, `MEDIAN_HOPS` 10 |
| `analysis/attack.rs` | `EPSILON` 1e-12, `RIDGE` 1e-9 (and the attack defaults ported from `AttackAnalyzer`) |
| `analysis/target.rs` | `PERIODS` 4, `MAX_HYPOTHESES` 64 |
| `analysis/strum_clip.rs` | `WINDOW_NANOS` 300 ms, `ARM_QUIET_HOPS` 60, `ATTACK_RISE_DB` 12, `CLIP_SHARE` 0.005 |
| `analysis/front_end.rs` | `CORNER_HZ` 40, `MINIMUM_RMS` 1.5e-5, `RISE_DB_PER_SECOND` 0.5, `FALL_RATE` 0.2, `TRUSTED_AFTER_HOPS` 188, `OPEN_MARGIN_DB` 9, `CLOSE_MARGIN_DB` 5, `REDUCTION_DB` 20, `HOLD_HOPS` 19, `FRAME` 512, `ALPHA` 1.5, `BETA` 0.05, `LEARN_RATE` 0.1 |
| `jni_recognition.rs`, `recognition.rs` | `CAPTURE_BLOCKS` 1024, `FRAME_RING` 2^18, `HOP_RING`, `COST_BINS` 10 000 (buffers, not tuning values) |

**Practice session** (`desktop/TuxGuitar-practice/src/app/tuxguitar/app/tools/custom/practice/session/`):

| File | Constants |
|---|---|
| `PolyphonicAcceptance.java` | `Parameters.defaults()`: `minShare` 0.30, `minBalance` 0.10, `minPeakRatio` 0.7, `hops` 6, `neighbourRatio` 0, `minAttackStrength` 0, `rearmNanos` 220 ms; `GUARD_NANOS` 60 ms, `MIN_TOTAL` 0.01, `FLOOR_HOPS` 10, `SILENCE_STRENGTH` 0.6, `MIN_ONSET_TOTAL` 0.002, `OFFSET_MARGIN` 15 cents, `DECAY` 1 |
| `ChordMatcher.java` | `Parameters.defaults()` and the four-argument constructor (used outside the settings, e.g. tests): 2 partials, 1/3, 8 hops, 600 ms, profile values 0.79 / 0.40 / 84 / 200 ms |
| `PracticeParameters.java` | `HOP_NANOS` (256 samples at 48 kHz), `WEAK_FAST_DBFS` −30, `EARLY_STABILITY_ENABLED` false, `EARLY_STABILITY_MARGIN` 0.5, `EARLY_STABILITY_NANOS` 12 hops |
| `PracticeSession.java` | `VERDICT_HOLD_NANOS` 1 s, `NEIGHBOUR_VETO_NANOS` 120 ms, `NEIGHBOUR_CONFIDENCE` 0.8, `SPEED_WINDOW_NANOS` 200 ms, `SPEED_SHOW_NANOS` 600 ms, technique and bend windows |
| `FollowSession.java` | `EVIDENCE_HORIZON_NANOS` 400 ms, `SILENCE_NANOS` 40 ms, `SHORT_NOTE_NANOS` 40 ms, `SAME_SOUND_NANOS` 100 ms, `RINGING_NANOS` 1.5 s, `REFINE_BEFORE_NANOS` 150 ms, `REFINE_AFTER_NANOS` 30 ms, `EVIDENCE_AFTER_CLOSE_NANOS` 100 ms, `TAIL_NANOS` 100 ms, `HISTORY_NANOS` 1.2 s, `EVIDENCE_LEAD_NANOS` 400 ms, technique and bend windows |
| `FollowLeniency.java`, `../arcade/core/ArcadeLeniency.java` | The relaxed values of the note and chord settings in Follow and Arcade |
