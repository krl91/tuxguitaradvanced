# Data model: spec 026

## Engine parameter

A `RecognitionParameter` (`Number`, `Flag` or `Choice`) in `RecognitionParameters.ALL`, as the 34 that exist today (research R2).

| Field | Rule |
|---|---|
| key | `recognition.<family>.<name>`, lower case and hyphens; unique; never renamed once released |
| section | one of the new `Section` values below (at most 7 parameters each) |
| unit | as shown: `ms`, `hops`, `ticks`, `dB`, `dBFS`, `cents`, `Hz`, `MIDI`, `%`, `times` (shown "×"), or none. A unit that is new (`hops`, `ticks`, `Hz`, `times`) gets its `recognition.unit.<unit>` text in the three message files, as the committed `RecognitionControlsTest.everyTextOfTheRubricIsInTheTranslationFile` requires for every unit |
| default | the literal the code uses today, exactly (research R3) |
| bounds | minimum and maximum; whole values for counts and hops. Chosen from the meaning (for example a share in 0–1, cents within ±100); when the meaning gives none, from about a quarter to about four times the default, rounded outward to the slider grid, and at least 1 for a count. An upper bound that sizes a buffer is the size allocated (research R6) |
| slider grid | the window's slider moves in steps of 1 / (display factor × resolution) (`RecognitionControls`: resolution 10 for a fractional value with display factor 1, else 1). The minimum, the maximum **and the default** are each a whole number of steps, so that the committed `RecognitionControlsTest.everySliderRunsFromZeroAndCoversExactlyTheValidRange` holds (both ends exact) and the recommended value is shown exactly. A small value takes a display factor that makes it whole: for example the YIN threshold 0.15 is shown in % (factor 100), a learn rate 0.005 in ‰ or with factor 1000 and no unit |
| takes effect | `next run` (practice session, calibration, technique values), or `listening restart` for the values an engine stage reads, Java or native (the Java analysis is rebuilt, the native one receives `configure` or `configureTuning`), as the level values today |
| documented | a row of `docs/architecture/recognition-engines.md` with name, meaning, unit, default, bounds, section and when it takes effect (FR-008) |

Validation: as every recognition setting (`RecognitionSettings.fromStrings`): missing, empty or unreadable → default; out of bounds → nearest bound. Pairs that must stay ordered are repaired as `recognition.note.accept` ≥ `recognition.tuner.in-tune` is today, in `RecognitionSettings.normalized`, so on reading (`fromStrings`, the repair reported) and on every change (`with`, `withDefaults`, the window's sliders). Because the window then moves the partner, the bounds of a pair are chosen so that, from the defaults, moving either value to its minimum or its maximum never crosses the other's default: the committed `RecognitionSettingsPanelTest.everySliderChangesOnlyItsOwnValueAndTextAtItsMinimumMiddleAndMaximum` requires that no other control moves. Each family's `…ParametersTest` asserts it for its pairs. The pairs:
- `noise-gate.open` > `noise-gate.close`;
- the onset windows: pre-first ≥ pre-last, post-first ≤ post-last (normal and fast);
- the bend window: min ≤ max;
- `polyphonic.candidate-low` < `polyphonic.candidate-high`, `attack-candidates.low` < `attack-candidates.high`;
- the calibration levels: calm below < loud above;
- each leniency row: very permissive at least as lenient as permissive.

## Families and sections (the categories of the window)

Counts are those of the scan of 2026-11-06 (research R1); task T003 writes the exact list in `inventory.md`. A family above 7 values is split into numbered sections.

| Section (`Section` value) | Family key | Where the values are today | About |
|---|---|---|---|
| `ONSET` | `onset` | `ArticulationDetector`, `NativeParameters` (normal windows) | 7 |
| `ONSET_FAST` | `onset-fast` | `ArticulationDetector` fast values, `PracticeParameters.WEAK_FAST_DBFS` | 6 |
| `PITCH` | `pitch` | YIN threshold, `ExpectedPitchVerifier` (search, periods), `TolerancePolicy.MAX_SHARP_CENTS`, `AttackCandidates` range | 6 |
| `POLYPHONIC_1`, `POLYPHONIC_2` | `polyphonic` | `PolyphonicAnalyzer.Parameters` (except FFT and band range), floor width and factor | 10 |
| `ATTACK_1`, `ATTACK_2` | `attack` | `AttackAnalyzer.Parameters` (except FFT), `AttackEvidence` | 8 |
| `CHORD_EVIDENCE` | `chord-evidence` | `ChordEvidence` / `chord.rs`, `StrumClipMeter` / `strum_clip.rs` | 6 |
| `FRONT_END` | `front-end` | `HighPass`, `NoiseEstimator` | 5 |
| `NOISE_GATE` | `noise-gate` | `NoiseGate`, `SpectralSubtractor` (alpha, beta, learn rate) | 7 |
| `GAIN` | `gain` | `InputGain` range, `GainSuggester` | 6 |
| `ACCEPTANCE_1`, `ACCEPTANCE_2` | `acceptance` | `PolyphonicAcceptance.Parameters.defaults()` and its constants | 14 |
| `WAIT_1`, `WAIT_2` | `wait` | `PracticeSession` (verdict hold, neighbour veto, technique and bend windows, speed window), `PracticeParameters` early stability | 12 |
| `FOLLOW_1`… `FOLLOW_3` | `follow` | `FollowSession` timings, technique and bend windows | 15 |
| `CLOCK` | `clock` | `TickInterpolator` (fit window, minimum points and span, stall, extrapolation, seek margin), `LatencyCompensation.LIMIT_MILLIS` | 7 |
| `TIMING` | `timing` | `TimingTolerance` reference beat, `PlayTimeWindow` advance and gap, `ClockUncertainty` | 4 |
| `LENIENCY_1`… `LENIENCY_4` | `follow-leniency`, `arcade-leniency` | `FollowLeniency` and `ArcadeLeniency` rows × positions | 22 |
| `TECHNIQUE_1`… `TECHNIQUE_4` | `technique` | `BendDetector`, `SlideDetector`, `VibratoDetector`, `LegatoDetector` parameters, `PitchMotion` | 27 |
| `CALIBRATION_1`… `CALIBRATION_3` | `calibration` | `CalibrationRun` thresholds, `CalibrationTaps` outliers, `RecognitionRate`, `RecordingQuality` levels, `SoundDetection` refractory and lead | 17 |
| `INPUT_QUALITY` | `input-quality` | `InputQuality` | 3 |

About 182 parameters (the sum of the counts above) and 32 new sections. The existing 8 sections are unchanged.

## Fixed constant

A value kept in the code, listed in section 6 of the documentation with its kind and reason (research R1 table): structural size, buffer or layout, numerical safety, plumbing, clock, score or display, calibration protocol.

## Parameter set of a run

Built once when a run or an offline analysis opens, from the effective `RecognitionSettings` (store, device profile overlay, Follow or Arcade leniency):
- the Java stages' value objects (research R4);
- `NativeParameters.of(settings, …)` (today's `float[21]`) and `NativeParameters.tuning(settings)` (the `double[]`) for the native engine (contracts/native-parameters.md);
- `PracticeParameters` for the session.

Never changed during a run, except the values pushed with `configure` (level, attack, front end), as today.
