# Inventory: spec 026

Written by task T003 from the scan of research R1 (`AE`, `PR/session`, `PR/arcade/core`, `CRATE/analysis`). One row per value: a **parameter** (its key in `RecognitionParameters.ALL`) or a **fixed** constant (its kind and reason, repeated in section 6 of `docs/architecture/recognition-engines.md`). The inventory tests (T029–T031) match a row by its file and constant name.

Totals: 192 parameters in 35 sections (at most 7 each), 184 fixed constants, 116 inline numbers that stay inline.

**Takes effect**: *listening restart* for a value an engine stage reads, Java or native (a change while listening rebuilds the Java analysis and is pushed to the native one with `configureTuning` or `configure`); *next run* for a value of the session, the techniques, the calibration or the clock.

**Grid**: every default, minimum and maximum is a whole number of slider steps (data-model "slider grid"); shown values are those of the window.

## Parameters

### `ONSET`

| Key | Unit | Default | Min | Max | Shown default | Takes effect | Replaces | Native |
|---|---|---|---|---|---|---|---|---|
| `recognition.onset.flux-factor` | × | 1.5 | 0.5 | 6 | 1.5 × | listening restart | `ArticulationDetector.java` `DEFAULT_FLUX_MEDIAN_FACTOR` | float[21] ONSET_FLUX_FACTOR |
| `recognition.onset.flux-offset` | — | 5.0 | 1 | 20 | 5  | listening restart | `ArticulationDetector.java` `DEFAULT_FLUX_OFFSET` | float[21] ONSET_FLUX_OFFSET |
| `recognition.onset.refractory` | hops | 12 | 3 | 48 | 12 hops | listening restart | `ArticulationDetector.java` `DEFAULT_REFRACTORY_HOPS` | float[21] ONSET_REFRACTORY |
| `recognition.onset.pre-first` | hops | 19 | 2 | 19 | 19 hops | listening restart | `ArticulationDetector.java` constructor 19; `NativeParameters.java` `NORMAL_PRE_FIRST` | float[21] ONSET_PRE_FIRST |
| `recognition.onset.pre-last` | hops | 2 | 1 | 19 | 2 hops | listening restart | `ArticulationDetector.java` constructor 2; `NativeParameters.java` `NORMAL_PRE_LAST` | float[21] ONSET_PRE_LAST |
| `recognition.onset.post-first` | hops | 4 | 1 | 13 | 4 hops | listening restart | `ArticulationDetector.java` constructor 4; `NativeParameters.java` `NORMAL_POST_FIRST` | float[21] ONSET_POST_FIRST |
| `recognition.onset.post-last` | hops | 13 | 4 | 13 | 13 hops | listening restart | `ArticulationDetector.java` constructor post window 13 | float[21] ONSET_POST_LAST |

### `ONSET_FAST`

| Key | Unit | Default | Min | Max | Shown default | Takes effect | Replaces | Native |
|---|---|---|---|---|---|---|---|---|
| `recognition.onset-fast.flux-factor` | × | 1.5 | 0.5 | 6 | 1.5 × | listening restart | `ArticulationDetector.java` `DEFAULT_FLUX_MEDIAN_FACTOR` | articulation fast (withFastOnset) |
| `recognition.onset-fast.flux-offset` | — | 5.0 | 1 | 20 | 5  | listening restart | `ArticulationDetector.java` `DEFAULT_FLUX_OFFSET` | articulation fast (withFastOnset) |
| `recognition.onset-fast.refractory` | hops | 6 | 2 | 24 | 6 hops | listening restart | `ArticulationDetector.java` `FAST_REFRACTORY_HOPS` | articulation fast (withFastOnset) |
| `recognition.onset-fast.pre-first` | hops | 8 | 2 | 19 | 8 hops | listening restart | `ArticulationDetector.java` fast() 8; `NativeParameters.java` `FAST_PRE_FIRST` | articulation fast (withFastOnset) |
| `recognition.onset-fast.pre-last` | hops | 2 | 1 | 8 | 2 hops | listening restart | `ArticulationDetector.java` fast() 2; `NativeParameters.java` `FAST_PRE_LAST` | articulation fast (withFastOnset) |
| `recognition.onset-fast.post-first` | hops | 3 | 1 | 8 | 3 hops | listening restart | `ArticulationDetector.java` fast() 3; `NativeParameters.java` `FAST_POST_FIRST` | articulation fast (withFastOnset) |
| `recognition.onset-fast.post-last` | hops | 8 | 3 | 8 | 8 hops | listening restart | `ArticulationDetector.java` fast() post window 8 | articulation fast (withFastOnset) |

### `PITCH`

| Key | Unit | Default | Min | Max | Shown default | Takes effect | Replaces | Native |
|---|---|---|---|---|---|---|---|---|
| `recognition.pitch.yin-threshold` | % | 0.20 | 0.05 | 0.5 | 20 % | listening restart | `TarsosFastYinEstimator.java` (FastYin default 0.20); `NativeParameters.java` `DEFAULT_YIN_THRESHOLD` | float[21] YIN_THRESHOLD; yin.rs DEFAULT_THRESHOLD |
| `recognition.pitch.target-search` | cents | 20.0 | 5 | 50 | 20 cents | listening restart | `ExpectedPitchVerifier.java` `SEARCH_CENTS` | target.rs SEARCH_CENTS (tuning) |
| `recognition.pitch.target-periods` | — | 4 | 2 | 8 | 4  | listening restart | `ExpectedPitchVerifier.java` `PERIODS` | target.rs PERIODS (tuning) |
| `recognition.pitch.max-sharp` | cents | 49.0 | 20 | 49 | 49 cents | next run | `TolerancePolicy.java` `MAX_SHARP_CENTS` | - |
| `recognition.pitch.attack-candidates-low` | MIDI | 28 | 24 | 40 | 28 MIDI | next run | `AttackCandidates.java` `LOWEST` | - |
| `recognition.pitch.attack-candidates-high` | MIDI | 100 | 80 | 112 | 100 MIDI | next run | `AttackCandidates.java` `HIGHEST` | - |

### `POLYPHONIC_1`

| Key | Unit | Default | Min | Max | Shown default | Takes effect | Replaces | Native |
|---|---|---|---|---|---|---|---|---|
| `recognition.polyphonic.candidate-low` | MIDI | 33 | 24 | 60 | 33 MIDI | listening restart | `PolyphonicAnalyzer.java` `Parameters.minCandidateMidi` | polyphonic.rs min_candidate_midi |
| `recognition.polyphonic.candidate-high` | MIDI | 88 | 64 | 112 | 88 MIDI | listening restart | `PolyphonicAnalyzer.java` `Parameters.maxCandidateMidi` | polyphonic.rs max_candidate_midi |
| `recognition.polyphonic.harmonics` | — | 12 | 4 | 24 | 12  | listening restart | `PolyphonicAnalyzer.java` `Parameters.harmonics` | polyphonic.rs harmonics |
| `recognition.polyphonic.max-harmonic` | Hz | 5000.0 | 1000 | 12000 | 5000 Hz | listening restart | `PolyphonicAnalyzer.java` `Parameters.maxHarmonicHz` | polyphonic.rs max_harmonic_hz |
| `recognition.polyphonic.tolerance` | cents | 25.0 | 5 | 50 | 25 cents | listening restart | `PolyphonicAnalyzer.java` `Parameters.harmonicToleranceCents` | polyphonic.rs harmonic_tolerance_cents |
| `recognition.polyphonic.candidates` | — | 6 | 1 | 12 | 6  | listening restart | `PolyphonicAnalyzer.java` `Parameters.maxCandidates` | polyphonic.rs max_candidates |

### `POLYPHONIC_2`

| Key | Unit | Default | Min | Max | Shown default | Takes effect | Replaces | Native |
|---|---|---|---|---|---|---|---|---|
| `recognition.polyphonic.min-relative` | % | 0.10 | 0.01 | 0.5 | 10 % | listening restart | `PolyphonicAnalyzer.java` `Parameters.minRelativeSalience` | polyphonic.rs min_relative_salience |
| `recognition.polyphonic.floor` | dB | -70.0 | -100 | -40 | -70 dB | listening restart | `PolyphonicAnalyzer.java` `Parameters.floorDb` | polyphonic.rs floor_db |
| `recognition.polyphonic.subharmonic` | % | 0.30 | 0.05 | 0.9 | 30 % | listening restart | `PolyphonicAnalyzer.java` `Parameters.subharmonicRatio` | polyphonic.rs subharmonic_ratio |
| `recognition.polyphonic.floor-width` | Hz | 25.0 | 5 | 100 | 25 Hz | listening restart | `PolyphonicAnalyzer.java` `FLOOR_HALF_WIDTH_HZ` | polyphonic.rs FLOOR_HALF_WIDTH_HZ |
| `recognition.polyphonic.floor-factor` | × | 2.0 | 1 | 6 | 2 × | listening restart | `PolyphonicAnalyzer.java` `FLOOR_FACTOR` | polyphonic.rs FLOOR_FACTOR |

### `ATTACK_1`

| Key | Unit | Default | Min | Max | Shown default | Takes effect | Replaces | Native |
|---|---|---|---|---|---|---|---|---|
| `recognition.attack.lag` | hops | 22 | 4 | 64 | 22 hops | listening restart | `AttackAnalyzer.java` `Parameters.lagHops` | attack.rs lag_hops |
| `recognition.attack.max-frequency` | Hz | 5000.0 | 1000 | 12000 | 5000 Hz | listening restart | `AttackAnalyzer.java` `Parameters.maxHz` | attack.rs max_hz |
| `recognition.attack.partials` | — | 14 | 4 | 24 | 14  | listening restart | `AttackAnalyzer.java` `Parameters.partials` | attack.rs partials |
| `recognition.attack.beta` | — | 0.3 | 0 | 2 | 0.3  | listening restart | `AttackAnalyzer.java` `Parameters.beta` | attack.rs beta |
| `recognition.attack.sigma` | — | 1.2 | 0.3 | 4 | 1.2  | listening restart | `AttackAnalyzer.java` `Parameters.sigmaBins` | attack.rs sigma_bins |

### `ATTACK_2`

| Key | Unit | Default | Min | Max | Shown default | Takes effect | Replaces | Native |
|---|---|---|---|---|---|---|---|---|
| `recognition.attack.evidence-partials` | — | 12 | 4 | 24 | 12  | next run | `AttackEvidence.java` `PARTIALS` | - |
| `recognition.attack.evidence-search` | cents | 60.0 | 10 | 100 | 60 cents | next run | `AttackEvidence.java` `SEARCH_CENTS` | - |
| `recognition.attack.evidence-peak` | % | 0.03 | 0.01 | 0.2 | 3 % | next run | `AttackEvidence.java` `MIN_PEAK_SHARE` | - |

### `CHORD_EVIDENCE`

| Key | Unit | Default | Min | Max | Shown default | Takes effect | Replaces | Native |
|---|---|---|---|---|---|---|---|---|
| `recognition.chord-evidence.partial-presence` | % | 0.05 | 0.01 | 0.3 | 5 % | listening restart | `ChordEvidence.java` `PARTIAL_PRESENCE_RATIO` | chord.rs PARTIAL_PRESENCE_RATIO |
| `recognition.chord-evidence.neighbour-margin` | × | 1.5 | 1 | 4 | 1.5 × | listening restart | `ChordEvidence.java` `NEIGHBOUR_MARGIN` | chord.rs NEIGHBOUR_MARGIN |
| `recognition.chord-evidence.strum-window` | ms | 300 | 100 | 1000 | 300 ms | listening restart | `StrumClipMeter.java` Parameters 300_000_000L | strum_clip.rs WINDOW_NANOS |
| `recognition.chord-evidence.strum-quiet` | hops | 60 | 10 | 200 | 60 hops | listening restart | `StrumClipMeter.java` Parameters 60 | strum_clip.rs ARM_QUIET_HOPS |
| `recognition.chord-evidence.strum-rise` | dB | 12.0 | 3 | 30 | 12 dB | listening restart | `StrumClipMeter.java` Parameters 12.0 | strum_clip.rs ATTACK_RISE_DB |
| `recognition.chord-evidence.strum-clip-share` | ‰ | 0.005 | 0.001 | 0.05 | 5 ‰ | listening restart | `StrumClipMeter.java` Parameters 0.005 | strum_clip.rs CLIP_SHARE |

### `FRONT_END`

| Key | Unit | Default | Min | Max | Shown default | Takes effect | Replaces | Native |
|---|---|---|---|---|---|---|---|---|
| `recognition.front-end.high-pass` | Hz | 40.0 | 10 | 120 | 40 Hz | listening restart | `HighPass.java` `CORNER_HZ` | front_end.rs CORNER_HZ |
| `recognition.front-end.noise-rise` | dB/s | 0.5 | 0.1 | 5 | 0.5 dB/s | listening restart | `NoiseEstimator.java` `RISE_DB_PER_SECOND` | front_end.rs RISE_DB_PER_SECOND |
| `recognition.front-end.noise-fall` | — | 0.2 | 0.1 | 1 | 0.2  | listening restart | `NoiseEstimator.java` `FALL_RATE` | front_end.rs FALL_RATE |
| `recognition.front-end.noise-trusted` | hops | 188 | 20 | 1000 | 188 hops | listening restart | `NoiseEstimator.java` `TRUSTED_AFTER_HOPS` | front_end.rs TRUSTED_AFTER_HOPS |

### `NOISE_GATE`

| Key | Unit | Default | Min | Max | Shown default | Takes effect | Replaces | Native |
|---|---|---|---|---|---|---|---|---|
| `recognition.noise-gate.open` | dB | 9.0 | 6 | 30 | 9 dB | listening restart | `NoiseGate.java` `OPEN_MARGIN_DB` | front_end.rs OPEN_MARGIN_DB |
| `recognition.noise-gate.close` | dB | 5.0 | 0 | 8 | 5 dB | listening restart | `NoiseGate.java` `CLOSE_MARGIN_DB` | front_end.rs CLOSE_MARGIN_DB |
| `recognition.noise-gate.reduction` | dB | 20.0 | 0 | 60 | 20 dB | listening restart | `NoiseGate.java` `REDUCTION_DB` | front_end.rs REDUCTION_DB |
| `recognition.noise-gate.hold` | hops | 19 | 1 | 100 | 19 hops | listening restart | `NoiseGate.java` `HOLD_HOPS` | front_end.rs HOLD_HOPS |
| `recognition.noise-gate.subtraction` | × | 1.5 | 0.5 | 4 | 1.5 × | listening restart | `SpectralSubtractor.java` `ALPHA` | front_end.rs ALPHA |
| `recognition.noise-gate.subtraction-floor` | % | 0.05 | 0 | 0.3 | 5 % | listening restart | `SpectralSubtractor.java` `BETA` | front_end.rs BETA |
| `recognition.noise-gate.subtraction-learn` | % | 0.1 | 0.01 | 1 | 10 % | listening restart | `SpectralSubtractor.java` `LEARN_RATE` | front_end.rs LEARN_RATE |

### `GAIN`

| Key | Unit | Default | Min | Max | Shown default | Takes effect | Replaces | Native |
|---|---|---|---|---|---|---|---|---|
| `recognition.gain.target` | dBFS | -22.0 | -40 | -10 | -22 dBFS | next run | `GainSuggester.java` `TARGET_PLAYING_RMS_DBFS` | - |
| `recognition.gain.max-peak` | dBFS | -3.0 | -12 | -1 | -3 dBFS | next run | `GainSuggester.java` `MAX_PEAK_DBFS` | - |
| `recognition.gain.no-change` | dB | 1.5 | 0.5 | 6 | 1.5 dB | next run | `GainSuggester.java` `NO_CHANGE_DB` | - |
| `recognition.gain.min-snr` | dB | 20.0 | 6 | 40 | 20 dB | next run | `GainSuggester.java` `MIN_SIGNAL_TO_NOISE_DB` | - |

### `ACCEPTANCE_1`

| Key | Unit | Default | Min | Max | Shown default | Takes effect | Replaces | Native |
|---|---|---|---|---|---|---|---|---|
| `recognition.acceptance.min-share` | % | 0.30 | 0.05 | 0.9 | 30 % | next run | `PolyphonicAcceptance.java` `Parameters.minShare` | - |
| `recognition.acceptance.min-balance` | % | 0.10 | 0 | 0.5 | 10 % | next run | `PolyphonicAcceptance.java` `Parameters.minBalance` | - |
| `recognition.acceptance.min-peak` | % | 0.7 | 0.1 | 1 | 70 % | next run | `PolyphonicAcceptance.java` `Parameters.minPeakRatio` | - |
| `recognition.acceptance.hops` | hops | 6 | 1 | 30 | 6 hops | next run | `PolyphonicAcceptance.java` `Parameters.hops` | - |
| `recognition.acceptance.neighbour` | × | 0.0 | 0 | 4 | 0 × | next run | `PolyphonicAcceptance.java` `Parameters.neighbourRatio` | - |
| `recognition.acceptance.attack-strength` | — | 0.0 | 0 | 5 | 0  | next run | `PolyphonicAcceptance.java` `Parameters.minAttackStrength` | - |
| `recognition.acceptance.rearm` | ms | 220 | 50 | 1000 | 220 ms | next run | `PolyphonicAcceptance.java` `Parameters.rearmNanos` | - |

### `ACCEPTANCE_2`

| Key | Unit | Default | Min | Max | Shown default | Takes effect | Replaces | Native |
|---|---|---|---|---|---|---|---|---|
| `recognition.acceptance.offset-margin` | cents | 15.0 | 0 | 50 | 15 cents | next run | `PolyphonicAcceptance.java` `OFFSET_MARGIN` | - |
| `recognition.acceptance.decay` | hops | 1 | 0 | 5 | 1 hops | next run | `PolyphonicAcceptance.java` `DECAY` | - |
| `recognition.acceptance.guard` | ms | 60 | 0 | 200 | 60 ms | next run | `PolyphonicAcceptance.java` `GUARD_NANOS` | - |
| `recognition.acceptance.min-total` | ‰ | 0.01 | 0.001 | 0.1 | 10 ‰ | next run | `PolyphonicAcceptance.java` `MIN_TOTAL` | - |
| `recognition.acceptance.floor-hops` | hops | 10 | 1 | 50 | 10 hops | next run | `PolyphonicAcceptance.java` `FLOOR_HOPS` | - |
| `recognition.acceptance.silence` | % | 0.6 | 0.1 | 1 | 60 % | next run | `PolyphonicAcceptance.java` `SILENCE_STRENGTH` | - |
| `recognition.acceptance.min-onset` | ‰ | 0.002 | 0.001 | 0.05 | 2 ‰ | next run | `PolyphonicAcceptance.java` `MIN_ONSET_TOTAL` | - |

### `ACCEPTANCE_3`

| Key | Unit | Default | Min | Max | Shown default | Takes effect | Replaces | Native |
|---|---|---|---|---|---|---|---|---|
| `recognition.acceptance.onset-floor` | × | 3.0 | 1 | 10 | 3 × | next run | `PolyphonicAcceptance.java` inline 3.0 * floor (onset above the floor) | - |
| `recognition.acceptance.release` | % | 0.25 | 0.05 | 0.9 | 25 % | next run | `PolyphonicAcceptance.java` inline 0.25 * attackPeak (end of the attack) | - |

### `WAIT_1`

| Key | Unit | Default | Min | Max | Shown default | Takes effect | Replaces | Native |
|---|---|---|---|---|---|---|---|---|
| `recognition.wait.verdict-hold` | ms | 1000 | 200 | 3000 | 1000 ms | next run | `PracticeSession.java` `VERDICT_HOLD_NANOS` | - |
| `recognition.wait.neighbour-veto` | ms | 120 | 0 | 500 | 120 ms | next run | `PracticeSession.java` `NEIGHBOUR_VETO_NANOS` | - |
| `recognition.wait.neighbour-confidence` | % | 0.8 | 0.5 | 1 | 80 % | next run | `PracticeSession.java` `NEIGHBOUR_CONFIDENCE` | - |
| `recognition.wait.technique-window` | ms | 1500 | 500 | 4000 | 1500 ms | next run | `PracticeSession.java` `TECHNIQUE_WINDOW_NANOS` | - |
| `recognition.wait.bend-window-min` | ms | 1000 | 250 | 2500 | 1000 ms | next run | `PracticeSession.java` `BEND_WINDOW_MIN_NANOS` | - |
| `recognition.wait.bend-window-max` | ms | 2500 | 1000 | 4000 | 2500 ms | next run | `PracticeSession.java` `BEND_WINDOW_MAX_NANOS`; `PracticeSession.java` `BEND_FRAMES_MAX` | - |
| `recognition.wait.bend-margin` | semitones | 0.5 | 0.1 | 2 | 0.5 semitones | next run | `PracticeSession.java` `BEND_PATH_MARGIN_SEMITONES` | - |

### `WAIT_2`

| Key | Unit | Default | Min | Max | Shown default | Takes effect | Replaces | Native |
|---|---|---|---|---|---|---|---|---|
| `recognition.wait.technique-lead` | ms | 150 | 0 | 500 | 150 ms | next run | `PracticeSession.java` `TECHNIQUE_LEAD_NANOS` | - |
| `recognition.wait.speed-window` | ms | 200 | 50 | 1000 | 200 ms | next run | `PracticeSession.java` `SPEED_WINDOW_NANOS` | - |
| `recognition.wait.speed-lead` | ms | 20 | 0 | 100 | 20 ms | next run | `PracticeSession.java` inline 20_000_000L (speed check, before the last acceptance) | - |
| `recognition.wait.early-margin` | — | 0.5 | 0.1 | 2 | 0.5  | next run | `PracticeParameters.java` `EARLY_STABILITY_MARGIN` | - |
| `recognition.wait.early-hops` | hops | 12 | 4 | 40 | 12 hops | next run | `PracticeParameters.java` `EARLY_STABILITY_NANOS` | - |
| `recognition.wait.weak-fast` | dBFS | -30.0 | -50 | -10 | -30 dBFS | next run | `PracticeParameters.java` `WEAK_FAST_DBFS` | - |

### `FOLLOW_1`

| Key | Unit | Default | Min | Max | Shown default | Takes effect | Replaces | Native |
|---|---|---|---|---|---|---|---|---|
| `recognition.follow.evidence-horizon` | ms | 400 | 100 | 1500 | 400 ms | next run | `FollowSession.java` `EVIDENCE_HORIZON_NANOS` | - |
| `recognition.follow.silence` | ms | 40 | 10 | 200 | 40 ms | next run | `FollowSession.java` `SILENCE_NANOS` | - |
| `recognition.follow.short-note` | ms | 40 | 10 | 200 | 40 ms | next run | `FollowSession.java` `SHORT_NOTE_NANOS` | - |
| `recognition.follow.same-sound` | ms | 100 | 20 | 400 | 100 ms | next run | `FollowSession.java` `SAME_SOUND_NANOS` | - |
| `recognition.follow.ringing` | ms | 1500 | 300 | 4000 | 1500 ms | next run | `FollowSession.java` `RINGING_NANOS` | - |
| `recognition.follow.scan-back` | — | 8 | 0 | 32 | 8  | next run | `FollowSession.java` inline scanStart() 8 (events looked back) | - |
| `recognition.follow.onset-memory` | ms | 3000 | 500 | 10000 | 3000 ms | next run | `FollowSession.java` inline 3_000_000_000L (recent onsets kept) | - |

### `FOLLOW_2`

| Key | Unit | Default | Min | Max | Shown default | Takes effect | Replaces | Native |
|---|---|---|---|---|---|---|---|---|
| `recognition.follow.refine-before` | ms | 150 | 0 | 500 | 150 ms | next run | `FollowSession.java` `REFINE_BEFORE_NANOS` | - |
| `recognition.follow.refine-after` | ms | 30 | 0 | 200 | 30 ms | next run | `FollowSession.java` `REFINE_AFTER_NANOS` | - |
| `recognition.follow.after-close` | ms | 100 | 0 | 400 | 100 ms | next run | `FollowSession.java` `EVIDENCE_AFTER_CLOSE_NANOS` | - |
| `recognition.follow.tail` | ms | 100 | 0 | 400 | 100 ms | next run | `FollowSession.java` `TAIL_NANOS` | - |
| `recognition.follow.history` | ms | 1200 | 400 | 4000 | 1200 ms | next run | `FollowSession.java` `HISTORY_NANOS` | - |

### `FOLLOW_3`

| Key | Unit | Default | Min | Max | Shown default | Takes effect | Replaces | Native |
|---|---|---|---|---|---|---|---|---|
| `recognition.follow.technique-window` | ms | 1500 | 500 | 4000 | 1500 ms | next run | `FollowSession.java` `TECHNIQUE_WINDOW_NANOS` | - |
| `recognition.follow.bend-window-min` | ms | 1000 | 250 | 2500 | 1000 ms | next run | `FollowSession.java` `BEND_WINDOW_MIN_NANOS` | - |
| `recognition.follow.bend-window-max` | ms | 2500 | 1000 | 4000 | 2500 ms | next run | `FollowSession.java` `BEND_WINDOW_MAX_NANOS` | - |
| `recognition.follow.bend-margin` | semitones | 0.5 | 0.1 | 2 | 0.5 semitones | next run | `FollowSession.java` `BEND_MARGIN_SEMITONES` | - |
| `recognition.follow.evidence-lead` | ms | 400 | 100 | 1500 | 400 ms | next run | `FollowSession.java` `EVIDENCE_LEAD_NANOS` | - |

### `CLOCK`

| Key | Unit | Default | Min | Max | Shown default | Takes effect | Replaces | Native |
|---|---|---|---|---|---|---|---|---|
| `recognition.clock.fit-window` | ms | 600 | 150 | 2000 | 600 ms | next run | `TickInterpolator.java` `FIT_WINDOW_NANOS` | - |
| `recognition.clock.min-points` | — | 8 | 3 | 64 | 8  | next run | `TickInterpolator.java` `MIN_POINTS` | - |
| `recognition.clock.min-span` | ms | 150 | 30 | 600 | 150 ms | next run | `TickInterpolator.java` `MIN_SPAN_NANOS` | - |
| `recognition.clock.stall` | ms | 60 | 20 | 300 | 60 ms | next run | `TickInterpolator.java` `STALL_NANOS` | - |
| `recognition.clock.extrapolation` | ms | 30 | 0 | 200 | 30 ms | next run | `TickInterpolator.java` `EXTRAPOLATION_NANOS` | - |
| `recognition.clock.seek-margin` | ticks | 960 | 240 | 3840 | 960 ticks | next run | `TickInterpolator.java` `SEEK_MARGIN_TICKS` | - |
| `recognition.clock.latency-limit` | ms | 1000.0 | 200 | 3000 | 1000 ms | next run | `LatencyCompensation.java` `LIMIT_MILLIS` | - |

### `TIMING`

| Key | Unit | Default | Min | Max | Shown default | Takes effect | Replaces | Native |
|---|---|---|---|---|---|---|---|---|
| `recognition.timing.reference-beat` | ms | 500.0 | 250 | 1000 | 500 ms | next run | `TimingTolerance.java` `REFERENCE_BEAT_MILLIS` | - |
| `recognition.timing.advance` | ms | 50 | 0 | 200 | 50 ms | next run | `PlayTimeWindow.java` `ADVANCE_NANOS` | - |
| `recognition.timing.gap` | ms | 20 | 0 | 100 | 20 ms | next run | `PlayTimeWindow.java` `GAP_NANOS` | - |
| `recognition.timing.clock-spread` | ms | 80.0 | 20 | 300 | 80 ms | next run | `ClockUncertainty.java` `UNSTABLE_SPREAD_MILLIS` | - |

### `LENIENCY_1`

| Key | Unit | Default | Min | Max | Shown default | Takes effect | Replaces | Native |
|---|---|---|---|---|---|---|---|---|
| `recognition.follow-leniency.permissive.accept` | cents | 35.0 | 10 | 45 | 35 cents | next run | `FollowLeniency.java` `NOTE_ACCEPT` | - |
| `recognition.follow-leniency.permissive.confidence` | % | 0.60 | 0.5 | 0.95 | 60 % | next run | `FollowLeniency.java` `NOTE_CONFIDENCE` | - |
| `recognition.follow-leniency.permissive.stability` | ms | 55.0 | 50 | 300 | 55 ms | next run | `FollowLeniency.java` `NOTE_STABILITY` | - |
| `recognition.follow-leniency.permissive.chord-evidence` | % | 0.22 | 0.2 | 0.8 | 22 % | next run | `FollowLeniency.java` `CHORD_EVIDENCE` | - |
| `recognition.follow-leniency.permissive.chord-partials` | — | 1.0 | 1 | 4 | 1  | next run | `FollowLeniency.java` `CHORD_MIN_PARTIALS` | - |
| `recognition.follow-leniency.permissive.chord-confirm` | ms | 24.0 | 20 | 150 | 24 ms | next run | `FollowLeniency.java` `CHORD_CONFIRM` | - |

### `LENIENCY_2`

| Key | Unit | Default | Min | Max | Shown default | Takes effect | Replaces | Native |
|---|---|---|---|---|---|---|---|---|
| `recognition.follow-leniency.very-permissive.accept` | cents | 35.0 | 10 | 45 | 35 cents | next run | `FollowLeniency.java` `NOTE_ACCEPT` | - |
| `recognition.follow-leniency.very-permissive.confidence` | % | 0.50 | 0.5 | 0.95 | 50 % | next run | `FollowLeniency.java` `NOTE_CONFIDENCE` | - |
| `recognition.follow-leniency.very-permissive.stability` | ms | 50.0 | 50 | 300 | 50 ms | next run | `FollowLeniency.java` `NOTE_STABILITY` | - |
| `recognition.follow-leniency.very-permissive.chord-evidence` | % | 0.20 | 0.2 | 0.8 | 20 % | next run | `FollowLeniency.java` `CHORD_EVIDENCE` | - |
| `recognition.follow-leniency.very-permissive.chord-partials` | — | 1.0 | 1 | 4 | 1  | next run | `FollowLeniency.java` `CHORD_MIN_PARTIALS` | - |
| `recognition.follow-leniency.very-permissive.chord-confirm` | ms | 20.0 | 20 | 150 | 20 ms | next run | `FollowLeniency.java` `CHORD_CONFIRM` | - |

### `LENIENCY_3`

| Key | Unit | Default | Min | Max | Shown default | Takes effect | Replaces | Native |
|---|---|---|---|---|---|---|---|---|
| `recognition.arcade-leniency.fun.confidence` | % | 0.70 | 0.5 | 0.95 | 70 % | next run | `ArcadeLeniency.java` `NOTE_CONFIDENCE` | - |
| `recognition.arcade-leniency.fun.stability` | ms | 75.0 | 50 | 300 | 75 ms | next run | `ArcadeLeniency.java` `NOTE_STABILITY` | - |
| `recognition.arcade-leniency.fun.chord-evidence` | % | 0.28 | 0.2 | 0.8 | 28 % | next run | `ArcadeLeniency.java` `CHORD_EVIDENCE` | - |
| `recognition.arcade-leniency.fun.chord-partials` | — | 2.0 | 1 | 4 | 2  | next run | `ArcadeLeniency.java` `CHORD_MIN_PARTIALS` | - |
| `recognition.arcade-leniency.fun.chord-confirm` | ms | 32.0 | 20 | 150 | 32 ms | next run | `ArcadeLeniency.java` `CHORD_CONFIRM` | - |
| `recognition.arcade-leniency.fun.tempo-window` | % | 1.25 | 1 | 2 | 125 % | next run | `ArcadeLeniency.java` windowMillis() factor 1.25 (fun) | - |

### `LENIENCY_4`

| Key | Unit | Default | Min | Max | Shown default | Takes effect | Replaces | Native |
|---|---|---|---|---|---|---|---|---|
| `recognition.arcade-leniency.very-permissive.confidence` | % | 0.60 | 0.5 | 0.95 | 60 % | next run | `ArcadeLeniency.java` `NOTE_CONFIDENCE` | - |
| `recognition.arcade-leniency.very-permissive.stability` | ms | 55.0 | 50 | 300 | 55 ms | next run | `ArcadeLeniency.java` `NOTE_STABILITY` | - |
| `recognition.arcade-leniency.very-permissive.chord-evidence` | % | 0.22 | 0.2 | 0.8 | 22 % | next run | `ArcadeLeniency.java` `CHORD_EVIDENCE` | - |
| `recognition.arcade-leniency.very-permissive.chord-partials` | — | 1.0 | 1 | 4 | 1  | next run | `ArcadeLeniency.java` `CHORD_MIN_PARTIALS` | - |
| `recognition.arcade-leniency.very-permissive.chord-confirm` | ms | 24.0 | 20 | 150 | 24 ms | next run | `ArcadeLeniency.java` `CHORD_CONFIRM` | - |
| `recognition.arcade-leniency.very-permissive.tempo-window` | % | 1.5 | 1 | 2 | 150 % | next run | `ArcadeLeniency.java` windowMillis() factor 1.5 (very-permissive) | - |

### `TECHNIQUE_1`

| Key | Unit | Default | Min | Max | Shown default | Takes effect | Replaces | Native |
|---|---|---|---|---|---|---|---|---|
| `recognition.technique.bend.min-range` | cents | 60.0 | 20 | 200 | 60 cents | next run | `BendDetector.java` `Parameters.minRangeCents` | - |
| `recognition.technique.bend.plain-range` | cents | 30.0 | 5 | 100 | 30 cents | next run | `BendDetector.java` `Parameters.plainRangeCents` | - |
| `recognition.technique.bend.min-duration` | ms | 0.2 | 0.05 | 1 | 200 ms | next run | `BendDetector.java` `Parameters.minDurationSeconds` | - |
| `recognition.technique.bend.voiced` | % | 0.6 | 0.2 | 1 | 60 % | next run | `BendDetector.java` `Parameters.minVoicedShare` | - |
| `recognition.technique.bend.written-move` | cents | 25.0 | 5 | 100 | 25 cents | next run | `BendDetector.java` `Parameters.minWrittenMoveCents` | - |

### `TECHNIQUE_2`

| Key | Unit | Default | Min | Max | Shown default | Takes effect | Replaces | Native |
|---|---|---|---|---|---|---|---|---|
| `recognition.technique.bend.on-tolerance` | cents | 30.0 | 5 | 100 | 30 cents | next run | `BendDetector.java` `Parameters.onToleranceCents` | - |
| `recognition.technique.bend.on-share` | % | 0.15 | 0 | 0.5 | 15 % | next run | `BendDetector.java` `Parameters.onToleranceShare` | - |
| `recognition.technique.bend.moved-share` | % | 0.5 | 0.1 | 1 | 50 % | next run | `BendDetector.java` `Parameters.movedShare` | - |
| `recognition.technique.bend.start-tolerance` | cents | 40.0 | 5 | 150 | 40 cents | next run | `BendDetector.java` `Parameters.startToleranceCents` | - |
| `recognition.technique.bend.edge` | hops | 8 | 0 | 40 | 8 hops | next run | `BendDetector.java` `Parameters.edgeHops` | - |

### `TECHNIQUE_3`

| Key | Unit | Default | Min | Max | Shown default | Takes effect | Replaces | Native |
|---|---|---|---|---|---|---|---|---|
| `recognition.technique.slide.min-range` | cents | 100.0 | 30 | 300 | 100 cents | next run | `SlideDetector.java` `Parameters.minRangeCents` | - |
| `recognition.technique.slide.plain-range` | cents | 40.0 | 5 | 150 | 40 cents | next run | `SlideDetector.java` `Parameters.plainRangeCents` | - |
| `recognition.technique.slide.monotony` | % | 0.75 | 0.3 | 1 | 75 % | next run | `SlideDetector.java` `Parameters.minMonotony` | - |
| `recognition.technique.slide.steps` | hops | 6 | 1 | 30 | 6 hops | next run | `SlideDetector.java` `Parameters.minMovingSteps` | - |
| `recognition.technique.slide.min-duration` | ms | 0.15 | 0.05 | 1 | 150 ms | next run | `SlideDetector.java` `Parameters.minDurationSeconds` | - |
| `recognition.technique.slide.voiced` | % | 0.5 | 0.2 | 1 | 50 % | next run | `SlideDetector.java` `Parameters.minVoicedShare` | - |

### `TECHNIQUE_4`

| Key | Unit | Default | Min | Max | Shown default | Takes effect | Replaces | Native |
|---|---|---|---|---|---|---|---|---|
| `recognition.technique.vibrato.depth` | cents | 25.0 | 5 | 100 | 25 cents | next run | `VibratoDetector.java` `Parameters.minDepthCents` | - |
| `recognition.technique.vibrato.plain-depth` | cents | 10.0 | 1 | 50 | 10 cents | next run | `VibratoDetector.java` `Parameters.plainDepthCents` | - |
| `recognition.technique.vibrato.min-rate` | Hz | 2.5 | 0.5 | 6 | 2.5 Hz | next run | `VibratoDetector.java` `Parameters.minRateHz` | - |
| `recognition.technique.vibrato.max-rate` | Hz | 9.0 | 6.5 | 20 | 9 Hz | next run | `VibratoDetector.java` `Parameters.maxRateHz` | - |
| `recognition.technique.vibrato.min-duration` | ms | 0.35 | 0.1 | 2 | 350 ms | next run | `VibratoDetector.java` `Parameters.minDurationSeconds` | - |
| `recognition.technique.vibrato.voiced` | % | 0.7 | 0.2 | 1 | 70 % | next run | `VibratoDetector.java` `Parameters.minVoicedShare` | - |

### `TECHNIQUE_5`

| Key | Unit | Default | Min | Max | Shown default | Takes effect | Replaces | Native |
|---|---|---|---|---|---|---|---|---|
| `recognition.technique.legato.start-window` | ms | 0.1 | 0.03 | 0.5 | 100 ms | next run | `LegatoDetector.java` `Parameters.startWindowSeconds` | - |
| `recognition.technique.legato.picked-rise` | dB | 6.0 | 2 | 20 | 6 dB | next run | `LegatoDetector.java` `Parameters.pickedRiseDb` | - |
| `recognition.technique.legato.rise` | dB | 3.0 | 0 | 10 | 3 dB | next run | `LegatoDetector.java` `Parameters.legatoRiseDb` | - |
| `recognition.technique.frame-confidence` | % | 0.8 | 0.5 | 1 | 80 % | next run | `PracticeSession.java` inline 0.8 (collectTechniqueFrame); `FollowSession.java` inline 0.8 (process) | - |
| `recognition.technique.motion.outlier` | cents | 400.0 | 100 | 1200 | 400 cents | next run | `PitchMotion.java` `OUTLIER_CENTS` | - |
| `recognition.technique.motion.min-voiced` | hops | 8 | 2 | 40 | 8 hops | next run | `PitchMotion.java` `MIN_VOICED` | - |

### `CALIBRATION_1`

| Key | Unit | Default | Min | Max | Shown default | Takes effect | Replaces | Native |
|---|---|---|---|---|---|---|---|---|
| `recognition.calibration.early-window` | ms | 250 | 50 | 1000 | 250 ms | next run | `CalibrationRun.java` `EARLY_WINDOW_NANOS` | - |
| `recognition.calibration.late-window` | ms | 700 | 200 | 2000 | 700 ms | next run | `CalibrationRun.java` `LATE_WINDOW_NANOS` | - |
| `recognition.calibration.pitch-patience` | ms | 25 | 5 | 200 | 25 ms | next run | `CalibrationRun.java` `PITCH_PATIENCE_NANOS` | - |
| `recognition.calibration.pitch-before` | ms | 30 | 0 | 200 | 30 ms | next run | `CalibrationRun.java` `PITCH_BEFORE_ATTACK_NANOS` | - |
| `recognition.calibration.same-pitch` | semitones | 0.5 | 0.1 | 2 | 0.5 semitones | next run | `CalibrationRun.java` `SAME_PITCH_SEMITONES` | - |
| `recognition.calibration.min-confidence` | % | 0.8 | 0.5 | 1 | 80 % | next run | `CalibrationRun.java` `MIN_CONFIDENCE` | - |
| `recognition.calibration.min-frequency` | Hz | 70.0 | 40 | 200 | 70 Hz | next run | `CalibrationRun.java` `MIN_HZ` | - |

### `CALIBRATION_2`

| Key | Unit | Default | Min | Max | Shown default | Takes effect | Replaces | Native |
|---|---|---|---|---|---|---|---|---|
| `recognition.calibration.max-frequency` | Hz | 1400.0 | 500 | 4000 | 1400 Hz | next run | `CalibrationRun.java` `MAX_HZ` | - |
| `recognition.calibration.min-usable` | — | 5 | 2 | 8 | 5  | next run | `CalibrationTaps.java` `MIN_USABLE` | - |
| `recognition.calibration.outlier-deviations` | × | 3.0 | 1 | 6 | 3 × | next run | `CalibrationTaps.java` `OUTLIER_DEVIATIONS` | - |
| `recognition.calibration.outlier-floor` | ms | 40.0 | 5 | 200 | 40 ms | next run | `CalibrationTaps.java` `OUTLIER_FLOOR_MILLIS` | - |
| `recognition.calibration.unstable-spread` | ms | 60.0 | 10 | 300 | 60 ms | next run | `CalibrationTaps.java` `UNSTABLE_SPREAD_MILLIS` | - |
| `recognition.calibration.recognition-rate` | % | 60 | 10 | 100 | 60 % | next run | `RecognitionRate.java` `THRESHOLD_PERCENT` | - |

### `CALIBRATION_3`

| Key | Unit | Default | Min | Max | Shown default | Takes effect | Replaces | Native |
|---|---|---|---|---|---|---|---|---|
| `recognition.calibration.calm-below` | dBFS | -60.0 | -90 | -46 | -60 dBFS | next run | `RecordingQuality.java` `CALM_BELOW_DBFS` | - |
| `recognition.calibration.loud-above` | dBFS | -45.0 | -59 | -20 | -45 dBFS | next run | `RecordingQuality.java` `LOUD_ABOVE_DBFS` | - |
| `recognition.calibration.sound-refractory` | ms | 250 | 50 | 1000 | 250 ms | next run | `SoundDetection.java` `REFRACTORY_MILLIS` | - |
| `recognition.calibration.sound-lead` | ms | 150 | 0 | 500 | 150 ms | next run | `SoundDetection.java` `SEGMENT_LEAD_MILLIS` | - |

### `LEVEL_CHECK`

| Key | Unit | Default | Min | Max | Shown default | Takes effect | Replaces | Native |
|---|---|---|---|---|---|---|---|---|
| `recognition.level-check.playing-confidence` | % | 0.80 | 0.5 | 1 | 80 % | next run | `InputAssessor.java` `Parameters.playingConfidence` | - |
| `recognition.level-check.required-playing` | ms | 500 | 100 | 3000 | 500 ms | next run | `InputAssessor.java` `Parameters.requiredPlayingNanos` | - |
| `recognition.level-check.suboptimal-share` | % | 0.10 | 0.01 | 0.5 | 10 % | next run | `InputAssessor.java` `Parameters.suboptimalShare` | - |

### `INPUT_QUALITY`

| Key | Unit | Default | Min | Max | Shown default | Takes effect | Replaces | Native |
|---|---|---|---|---|---|---|---|---|
| `recognition.input-quality.attacks` | — | 3 | 1 | 10 | 3  | next run | `InputQuality.java` `ATTACKS` | - |
| `recognition.input-quality.remember` | ms | 8000 | 1000 | 30000 | 8000 ms | next run | `InputQuality.java` `REMEMBER_NANOS` | - |
| `recognition.input-quality.attack-window` | ms | 200 | 50 | 1000 | 200 ms | next run | `InputQuality.java` `ATTACK_WINDOW_NANOS` | - |

## Ordered pairs

Repaired in `RecognitionSettings.normalized` (the lower one is brought back to the upper one). From the defaults, moving either value to either end never crosses the other's default.

- `recognition.onset.pre-last` ≤ `recognition.onset.pre-first`
- `recognition.onset.post-first` ≤ `recognition.onset.post-last`
- `recognition.onset-fast.pre-last` ≤ `recognition.onset-fast.pre-first`
- `recognition.onset-fast.post-first` ≤ `recognition.onset-fast.post-last`
- `recognition.pitch.attack-candidates-low` ≤ `recognition.pitch.attack-candidates-high`
- `recognition.polyphonic.candidate-low` ≤ `recognition.polyphonic.candidate-high`
- `recognition.noise-gate.close` ≤ `recognition.noise-gate.open`
- `recognition.wait.bend-window-min` ≤ `recognition.wait.bend-window-max`
- `recognition.follow.bend-window-min` ≤ `recognition.follow.bend-window-max`
- `recognition.calibration.calm-below` ≤ `recognition.calibration.loud-above`

The leniency rows have no ordered pair: `recognition.follow-leniency.very-permissive.chord-partials` and its permissive row both default to 1, so a pair would leave the very permissive row a single value (minimum = maximum), which the window cannot show (`RecognitionControlsTest` requires a slider of at least one step). Each position is applied on its own, as today.

## Fixed constants

| File | Constant | Kind | Reason |
|---|---|---|---|
| `AE/OfflineAnalysis.java` | `WINDOW` | structural size | analysis window 2048 and hop 256 (clarification 1) |
| `AE/OfflineAnalysis.java` | `HOP` | structural size | analysis window 2048 and hop 256 (clarification 1) |
| `AE/AudioEngine.java` | `DEFAULT_RING_BUFFER_CAPACITY` | buffer or layout | capture ring size |
| `AE/AudioEngine.java` | `DEFAULT_TOO_LOW_RMS_DBFS` | already a setting | the defaults of recognition.input.level.min, .max, advanced.level.clip-sample and clip-share |
| `AE/AudioEngine.java` | `DEFAULT_TOO_HIGH_RMS_DBFS` | already a setting | the defaults of recognition.input.level.min, .max, advanced.level.clip-sample and clip-share |
| `AE/AudioEngine.java` | `DEFAULT_CLIPPED_SAMPLE_LEVEL` | already a setting | the defaults of recognition.input.level.min, .max, advanced.level.clip-sample and clip-share |
| `AE/AudioEngine.java` | `DEFAULT_CLIPPING_RATIO` | already a setting | the defaults of recognition.input.level.min, .max, advanced.level.clip-sample and clip-share |
| `AE/AudioEngine.java` | `INITIAL_SAMPLE_RATE` | structural size | sample rate 48,000 (clarification 1) |
| `AE/AudioEngine.java` | `NATIVE_OPEN_TIMEOUT_MILLIS` | plumbing | native open timeout (clarification 2) |
| `AE/settings/RecognitionParameters.java` | `VERSION` | buffer or layout | configuration layout version |
| `AE/settings/RecognitionParameters.java` | `HOP_MILLIS` | structural size | one hop, 256 samples at 48 kHz |
| `AE/analysis/ArticulationDetector.java` | `DEFAULT_MIN_LEVEL_DB` | already a setting | the defaults of recognition.advanced.attack.min-level and recognition.note.attack |
| `AE/analysis/ArticulationDetector.java` | `DEFAULT_GATE_DB` | already a setting | the defaults of recognition.advanced.attack.min-level and recognition.note.attack |
| `AE/analysis/ArticulationDetector.java` | `FFT_SIZE` | structural size | articulation FFT 1024 (clarification 1) |
| `AE/analysis/ArticulationDetector.java` | `MEDIAN_HOPS` | buffer or layout | length of the flux history the median is taken over (a buffer the detector keeps) |
| `AE/analysis/ArticulationDetector.java` | `DECISION_DELAY_HOPS` | buffer or layout | look-ahead the detector buffers before deciding; bounds the post windows |
| `AE/analysis/ArticulationDetector.java` | `FAST_DECISION_DELAY_HOPS` | buffer or layout | look-ahead the detector buffers before deciding; bounds the post windows |
| `AE/analysis/AnalysisFrame.java` | `NO_CAPTURE_SAMPLE` | buffer or layout | marker value of the frame record |
| `AE/analysis/NnlsSolver.java` | `EPSILON` | numerical safety | numerical safety of the least-squares solver |
| `AE/analysis/NnlsSolver.java` | `RIDGE` | numerical safety | numerical safety of the least-squares solver |
| `AE/analysis/StrumClipMeter.java` | `SILENCE_DBFS` | numerical safety | level reported for digital silence |
| `AE/frontend/GainScale.java` | `MIN_DB` | buffer or layout | the gain control's scale, derived from InputGain's range |
| `AE/frontend/GainScale.java` | `MAX_DB` | buffer or layout | the gain control's scale, derived from InputGain's range |
| `AE/frontend/GainScale.java` | `POSITIONS` | buffer or layout | the gain control's scale, derived from InputGain's range |
| `AE/frontend/GainScale.java` | `NEUTRAL_POSITION` | buffer or layout | the gain control's scale, derived from InputGain's range |
| `AE/frontend/InputGain.java` | `MIN_MULTIPLIER` | buffer or layout | the range of the input gain control (-6 to +18 dB), set by the learner or the calibration |
| `AE/frontend/InputGain.java` | `MAX_MULTIPLIER` | buffer or layout | the range of the input gain control (-6 to +18 dB), set by the learner or the calibration |
| `AE/frontend/InputGain.java` | `NEUTRAL` | numerical safety | unit gain |
| `AE/frontend/SpectralSubtractor.java` | `FRAME` | structural size | subtraction frame 512 and hop 256 (clarification 1) |
| `AE/frontend/SpectralSubtractor.java` | `HOP` | structural size | subtraction frame 512 and hop 256 (clarification 1) |
| `AE/frontend/NoiseEstimator.java` | `MINIMUM_RMS` | numerical safety | floor of the noise estimate, avoids a zero level |
| `AE/analysis/TarsosFastYinEstimator.java` | `DEFAULT_SAMPLE_RATE` | structural size | sample rate, window and hop (clarification 1) |
| `AE/analysis/TarsosFastYinEstimator.java` | `DEFAULT_WINDOW_SIZE` | structural size | sample rate, window and hop (clarification 1) |
| `AE/analysis/TarsosFastYinEstimator.java` | `DEFAULT_HOP_SIZE` | structural size | sample rate, window and hop (clarification 1) |
| `AE/calibration/FrontEndState.java` | `GAIN_TOLERANCE` | numerical safety | equality tolerance of two stored gains |
| `AE/calibration/RecognitionProfile.java` | `LAYOUT` | buffer or layout | profile file layout version |
| `AE/input/CaptureClock.java` | `NOT_READY` | buffer or layout | marker value |
| `AE/input/CaptureClock.java` | `WINDOW_NANOS` | clock | capture clock drift estimate (clarification 2: clock drift correction) |
| `AE/input/CaptureClock.java` | `MIN_READS` | clock | capture clock drift estimate (clarification 2: clock drift correction) |
| `AE/input/CaptureClock.java` | `CAPACITY` | buffer or layout | ring of clock readings |
| `AE/backend/NativeBackend.java` | `BLOCK_FRAMES` | buffer or layout | native capture block and hop block sizes |
| `AE/backend/NativeBackend.java` | `HOP_BLOCK` | buffer or layout | native capture block and hop block sizes |
| `AE/backend/NativeBackend.java` | `HOP_SIZE` | structural size | hop 256 (clarification 1) |
| `AE/backend/NativeBackend.java` | `WAIT_MILLIS` | plumbing | thread waits (clarification 2) |
| `AE/backend/NativeBackend.java` | `JOIN_MILLIS` | plumbing | thread waits (clarification 2) |
| `AE/input/CaptureService.java` | `FRAMES_PER_READ` | buffer or layout | capture read size |
| `AE/calibration/RecordingQuality.java` | `BLOCK` | buffer or layout | block the quality is measured on |
| `AE/calibration/RecordingQuality.java` | `FLOOR_DBFS` | numerical safety | level reported for digital silence |
| `AE/calibration/SoundDetection.java` | `STOP_AFTER_LAST_MILLIS` | plumbing | calibration timeouts (clarification 2) |
| `AE/calibration/SoundDetection.java` | `SILENCE_TIMEOUT_MILLIS` | plumbing | calibration timeouts (clarification 2) |
| `AE/calibration/SoundDetection.java` | `TOTAL_TIMEOUT_MILLIS` | plumbing | calibration timeouts (clarification 2) |
| `AE/recognition/ExpectedPitchVerifier.java` | `MIN_WINDOW` | structural size | shortest analysis window of the target check (clarification 1) |
| `AE/nativeengine/NativeParameters.java` | `LEVEL_TOO_LOW` | buffer or layout | indices and length of the float[21] (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `LEVEL_TOO_HIGH` | buffer or layout | indices and length of the float[21] (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `LEVEL_CLIP_SAMPLE` | buffer or layout | indices and length of the float[21] (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `LEVEL_CLIP_RATIO` | buffer or layout | indices and length of the float[21] (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `ONSET_FLUX_FACTOR` | buffer or layout | indices and length of the float[21] (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `ONSET_FLUX_OFFSET` | buffer or layout | indices and length of the float[21] (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `ONSET_MIN_LEVEL` | buffer or layout | indices and length of the float[21] (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `ONSET_REFRACTORY` | buffer or layout | indices and length of the float[21] (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `ONSET_GATE` | buffer or layout | indices and length of the float[21] (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `ONSET_PRE_FIRST` | buffer or layout | indices and length of the float[21] (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `ONSET_PRE_LAST` | buffer or layout | indices and length of the float[21] (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `ONSET_POST_FIRST` | buffer or layout | indices and length of the float[21] (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `ONSET_POST_LAST` | buffer or layout | indices and length of the float[21] (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `FRONT_GAIN` | buffer or layout | indices and length of the float[21] (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `FRONT_NOISE_REDUCTION` | buffer or layout | indices and length of the float[21] (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `FRONT_SUBTRACTION` | buffer or layout | indices and length of the float[21] (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `STRUM_ARM_FLOOR` | buffer or layout | indices and length of the float[21] (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `STRUM_CLIP_SAMPLE` | buffer or layout | indices and length of the float[21] (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `STRUM_WEAK` | buffer or layout | indices and length of the float[21] (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `STRUM_LOUD` | buffer or layout | indices and length of the float[21] (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `YIN_THRESHOLD` | buffer or layout | indices and length of the float[21] (contracts/native-parameters.md) |
| `AE/nativeengine/NativeParameters.java` | `LENGTH` | buffer or layout | indices and length of the float[21] (contracts/native-parameters.md) |
| `AE/nativeengine/FrameRecords.java` | `LENGTH` | buffer or layout | layout of the native frame record |
| `AE/nativeengine/FrameRecords.java` | `TIMESTAMP_NANOS` | buffer or layout | layout of the native frame record |
| `AE/nativeengine/FrameRecords.java` | `CAPTURE_SAMPLE_END` | buffer or layout | layout of the native frame record |
| `AE/nativeengine/FrameRecords.java` | `ARRIVAL_NANOS` | buffer or layout | layout of the native frame record |
| `AE/nativeengine/FrameRecords.java` | `RMS_DBFS` | buffer or layout | layout of the native frame record |
| `AE/nativeengine/FrameRecords.java` | `PEAK_DBFS` | buffer or layout | layout of the native frame record |
| `AE/nativeengine/FrameRecords.java` | `CLIPPED_RATIO` | buffer or layout | layout of the native frame record |
| `AE/nativeengine/FrameRecords.java` | `LEVEL_STATE` | buffer or layout | layout of the native frame record |
| `AE/nativeengine/FrameRecords.java` | `F0_HZ` | buffer or layout | layout of the native frame record |
| `AE/nativeengine/FrameRecords.java` | `CONFIDENCE` | buffer or layout | layout of the native frame record |
| `AE/nativeengine/FrameRecords.java` | `ONSET` | buffer or layout | layout of the native frame record |
| `AE/nativeengine/FrameRecords.java` | `PARTS` | buffer or layout | layout of the native frame record |
| `AE/nativeengine/FrameRecords.java` | `HEADER` | buffer or layout | layout of the native frame record |
| `AE/nativeengine/FrameRecords.java` | `PART_SPECTRUM` | buffer or layout | layout of the native frame record |
| `AE/nativeengine/FrameRecords.java` | `PART_TARGET` | buffer or layout | layout of the native frame record |
| `AE/nativeengine/FrameRecords.java` | `PART_ATTACK` | buffer or layout | layout of the native frame record |
| `AE/nativeengine/FrameRecords.java` | `PART_CHORD` | buffer or layout | layout of the native frame record |
| `AE/nativeengine/FrameRecords.java` | `CAPACITY` | buffer or layout | layout of the native frame record |
| `PR/session/PracticeParameters.java` | `MEASURED_DELAY_MEDIAN_MILLIS` | score or display | measured figures shown to the learner, not used to decide |
| `PR/session/PracticeParameters.java` | `MEASURED_DELAY_P90_MILLIS` | score or display | measured figures shown to the learner, not used to decide |
| `PR/session/PracticeParameters.java` | `MEASURED_SPEED_LIMIT_SECONDS` | score or display | measured figures shown to the learner, not used to decide |
| `PR/session/PracticeParameters.java` | `HOP_NANOS` | structural size | one hop at 48 kHz |
| `PR/session/PracticeSession.java` | `RECENT_FRAMES` | buffer or layout | frame history sizes; BEND_FRAMES_MAX bounds recognition.wait.bend-window-max |
| `PR/session/PracticeSession.java` | `BEND_FRAMES_MAX` | buffer or layout | frame history sizes; BEND_FRAMES_MAX bounds recognition.wait.bend-window-max |
| `PR/session/PracticeSession.java` | `SPEED_SHOW_NANOS` | score or display | display duration (clarification 3) |
| `PR/session/PlayTimeWindow.java` | `MIN_MILLIS` | already a setting | bounds and default of recognition.follow.play-time |
| `PR/session/PlayTimeWindow.java` | `MAX_MILLIS` | already a setting | bounds and default of recognition.follow.play-time |
| `PR/session/PlayTimeWindow.java` | `DEFAULT_MILLIS` | already a setting | bounds and default of recognition.follow.play-time |
| `PR/session/MidiClickPlayer.java` | `KEY` | not recognition | the click sound |
| `PR/session/MidiClickPlayer.java` | `VELOCITY` | not recognition | the click sound |
| `PR/session/MidiClickPlayer.java` | `CLICK_MILLIS` | not recognition | the click sound |
| `PR/session/SessionLoop.java` | `MIN_UI_INTERVAL_NANOS` | score or display | screen refresh interval |
| `PR/session/FollowSettings.java` | `DEFAULT_WINDOW_MILLIS` | already a setting | the Follow window's own display settings (clarification 2) |
| `PR/session/FollowSettings.java` | `MIN_PERCENT` | already a setting | the Follow window's own display settings (clarification 2) |
| `PR/session/FollowSettings.java` | `MAX_PERCENT` | already a setting | the Follow window's own display settings (clarification 2) |
| `PR/session/FollowSettings.java` | `DEFAULT_PERCENT` | already a setting | the Follow window's own display settings (clarification 2) |
| `PR/session/FollowSettings.java` | `MIN_MARKER_PERCENT` | already a setting | the Follow window's own display settings (clarification 2) |
| `PR/session/FollowSettings.java` | `MAX_MARKER_PERCENT` | already a setting | the Follow window's own display settings (clarification 2) |
| `PR/session/FollowSettings.java` | `DEFAULT_MARKER_PERCENT` | already a setting | the Follow window's own display settings (clarification 2) |
| `PR/session/FollowSettings.java` | `MIN_FAR_SCALE_PERCENT` | already a setting | the Follow window's own display settings (clarification 2) |
| `PR/session/FollowSettings.java` | `MAX_FAR_SCALE_PERCENT` | already a setting | the Follow window's own display settings (clarification 2) |
| `PR/session/FollowSettings.java` | `DEFAULT_FAR_SCALE_PERCENT` | already a setting | the Follow window's own display settings (clarification 2) |
| `PR/session/FollowSettings.java` | `MIN_HORIZON_PERCENT` | already a setting | the Follow window's own display settings (clarification 2) |
| `PR/session/FollowSettings.java` | `MAX_HORIZON_PERCENT` | already a setting | the Follow window's own display settings (clarification 2) |
| `PR/session/FollowSettings.java` | `DEFAULT_HORIZON_PERCENT` | already a setting | the Follow window's own display settings (clarification 2) |
| `PR/session/RemovedSoundsPlayer.java` | `LATE_TOLERANCE_NANOS` | not recognition | playback of the removed sounds |
| `PR/session/RemovedSoundsPlayer.java` | `POLL_MILLIS` | not recognition | playback of the removed sounds |
| `PR/session/RemovedSoundsPlayer.java` | `DEFAULT_VELOCITY` | not recognition | playback of the removed sounds |
| `PR/session/TickInterpolator.java` | `CAPACITY` | buffer or layout | ring of tick readings; bounds recognition.clock.min-points (clarification 3) |
| `PR/session/AmpSoundStore.java` | `NAME_LENGTH` | not recognition | amp sound names |
| `PR/session/AmpSoundStore.java` | `RENAMES` | not recognition | amp sound names |
| `PR/session/AmpSoundSettings.java` | `DEFAULT_GUITAR_VOLUME` | not recognition | amp volume |
| `PR/session/FollowStarter.java` | `FIRST_TICK` | buffer or layout | where the timeline of a run starts (one beat of lead-in) |
| `PR/session/MidiFollowPlayer.java` | `STOP_SETTLE_MILLIS` | plumbing | sequencer stop wait |
| `PR/session/TimingTolerance.java` | `MS` | numerical safety | nanoseconds per millisecond |
| `PR/session/TimingTolerance.java` | `MIN_WIDTH_MILLIS` | already a setting | bounds of recognition.tempo.window |
| `PR/session/TimingTolerance.java` | `MAX_WIDTH_MILLIS` | already a setting | bounds of recognition.tempo.window |
| `PR/session/CalibrationRun.java` | `LEAD_NANOS` | calibration protocol | calibration protocol (clarification 3) |
| `PR/session/CalibrationRun.java` | `PERIOD_NANOS` | calibration protocol | calibration protocol (clarification 3) |
| `PR/session/CalibrationTaps.java` | `CLICKS` | calibration protocol | calibration protocol, 8 clicks (clarification 3) |
| `PR/session/FollowRunRecorder.java` | `MAX_MILLIS` | plumbing | recording limits of a Follow run |
| `PR/session/FollowRunRecorder.java` | `MARGIN_MILLIS` | plumbing | recording limits of a Follow run |
| `PR/session/PassagePolicy.java` | `DEFAULT_MEASURES` | already a setting | defaults of the passage settings |
| `PR/session/PassagePolicy.java` | `DEFAULT_MAX_ERROR_RATE` | already a setting | defaults of the passage settings |
| `PR/session/PassagePolicy.java` | `DEFAULT_MAX_RETRIES` | already a setting | defaults of the passage settings |
| `PR/session/PassageSettings.java` | `OFF` | buffer or layout | marker value |
| `PR/session/PlayerTempoClock.java` | `NOMINAL_TICKS_PER_SECOND` | clock | nominal tick rate (clarification 3) |
| `PR/session/FollowSession.java` | `REASON_SPAN_NANOS` | score or display | display duration (clarification 3) |
| `PR/arcade/core/ArcadeScoring.java` | `FIRST_STEP` | score or display | Arcade score (clarification 2) |
| `PR/arcade/core/ArcadeScoring.java` | `SECOND_STEP` | score or display | Arcade score (clarification 2) |
| `CRATE/analysis/articulation.rs` | `FFT_SIZE` | structural size | as ArticulationDetector.FFT_SIZE |
| `CRATE/analysis/articulation.rs` | `MEDIAN_HOPS` | buffer or layout | as ArticulationDetector.MEDIAN_HOPS |
| `CRATE/analysis/attack.rs` | `EPSILON` | numerical safety | as NnlsSolver |
| `CRATE/analysis/attack.rs` | `RIDGE` | numerical safety | as NnlsSolver |
| `CRATE/analysis/chord.rs` | `MAX_PITCHES` | buffer or layout | preallocated evidence sizes |
| `CRATE/analysis/chord.rs` | `MAX_RESIDUAL` | buffer or layout | preallocated evidence sizes |
| `CRATE/analysis/level.rs` | `TOO_LOW` | buffer or layout | level state codes of the record |
| `CRATE/analysis/level.rs` | `SUITABLE` | buffer or layout | level state codes of the record |
| `CRATE/analysis/level.rs` | `TOO_HIGH` | buffer or layout | level state codes of the record |
| `CRATE/analysis/level.rs` | `CLIPPING` | buffer or layout | level state codes of the record |
| `CRATE/analysis/frame.rs` | `LENGTH` | buffer or layout | layout of the frame record |
| `CRATE/analysis/frame.rs` | `TIMESTAMP_NANOS` | buffer or layout | layout of the frame record |
| `CRATE/analysis/frame.rs` | `CAPTURE_SAMPLE_END` | buffer or layout | layout of the frame record |
| `CRATE/analysis/frame.rs` | `ARRIVAL_NANOS` | buffer or layout | layout of the frame record |
| `CRATE/analysis/frame.rs` | `RMS_DBFS` | buffer or layout | layout of the frame record |
| `CRATE/analysis/frame.rs` | `PEAK_DBFS` | buffer or layout | layout of the frame record |
| `CRATE/analysis/frame.rs` | `CLIPPED_RATIO` | buffer or layout | layout of the frame record |
| `CRATE/analysis/frame.rs` | `LEVEL_STATE` | buffer or layout | layout of the frame record |
| `CRATE/analysis/frame.rs` | `F0_HZ` | buffer or layout | layout of the frame record |
| `CRATE/analysis/frame.rs` | `CONFIDENCE` | buffer or layout | layout of the frame record |
| `CRATE/analysis/frame.rs` | `ONSET` | buffer or layout | layout of the frame record |
| `CRATE/analysis/frame.rs` | `PARTS` | buffer or layout | layout of the frame record |
| `CRATE/analysis/frame.rs` | `HEADER` | buffer or layout | layout of the frame record |
| `CRATE/analysis/frame.rs` | `PART_SPECTRUM` | buffer or layout | layout of the frame record |
| `CRATE/analysis/frame.rs` | `PART_TARGET` | buffer or layout | layout of the frame record |
| `CRATE/analysis/frame.rs` | `PART_ATTACK` | buffer or layout | layout of the frame record |
| `CRATE/analysis/frame.rs` | `PART_CHORD` | buffer or layout | layout of the frame record |
| `CRATE/analysis/frame.rs` | `CAPACITY` | buffer or layout | layout of the frame record |
| `CRATE/analysis/front_end.rs` | `MINIMUM_RMS` | numerical safety | as NoiseEstimator.MINIMUM_RMS |
| `CRATE/analysis/front_end.rs` | `FRAME` | structural size | as SpectralSubtractor.FRAME and HOP |
| `CRATE/analysis/front_end.rs` | `HOP` | structural size | as SpectralSubtractor.FRAME and HOP |
| `CRATE/analysis/strum_clip.rs` | `SILENCE_DBFS` | numerical safety | as StrumClipMeter.SILENCE_DBFS |
| `CRATE/analysis/strum_clip.rs` | `SUITABLE` | buffer or layout | strum verdict codes |
| `CRATE/analysis/strum_clip.rs` | `CLIPPING` | buffer or layout | strum verdict codes |
| `CRATE/analysis/strum_clip.rs` | `TOO_LOUD` | buffer or layout | strum verdict codes |
| `CRATE/analysis/strum_clip.rs` | `TOO_WEAK` | buffer or layout | strum verdict codes |
| `CRATE/analysis/pipeline.rs` | `WINDOW` | structural size | window 2048 and hop 256 (clarification 1) |
| `CRATE/analysis/pipeline.rs` | `HOP` | structural size | window 2048 and hop 256 (clarification 1) |
| `CRATE/analysis/target.rs` | `MIN_WINDOW` | structural size | as ExpectedPitchVerifier.MIN_WINDOW |
| `CRATE/analysis/target.rs` | `MAX_HYPOTHESES` | buffer or layout | preallocated hypotheses |

## Inline numbers that stay inline

Numbers written in a method body or a constructor call, outside the mathematical and unit allow-list of the inventory tests (0, 0.5, 1, 2, 4, 10, 12, 20, 60, 69, 100, 440, 1,000, 1,200, 3,600, the time conversions and 1e-9, 1e-12). The literals that become parameters are named as constants by their family task and leave the code.

| File | Literal | Kind | Reason |
|---|---|---|---|
| `AE/pitch/TolerancePolicy.java` | `50.0` | numerical safety | validation: an acceptance never reaches the next semitone's half |
| `AE/pitch/NoteInterpreter.java` | `127` | mathematical | MIDI range |
| `AE/settings/RecognitionSettings.java` | `50.0` | legacy migration | tempo windows that carry the presets of spec 002 over to recognition.tempo.window |
| `AE/settings/RecognitionSettings.java` | `125.0` | legacy migration | tempo windows that carry the presets of spec 002 over to recognition.tempo.window |
| `AE/settings/ChordLeniency.java` | `3.0` | already a setting | 1.0 / 3.0, the default of recognition.chord.evidence |
| `AE/analysis/PolyphonicAnalyzer.java` | `127` | numerical safety | validation bounds of the parameters |
| `AE/analysis/PolyphonicAnalyzer.java` | `50` | numerical safety | validation bounds of the parameters |
| `AE/analysis/PolyphonicAnalyzer.java` | `8192` | already a setting | FFT size and band range (clarification 1), in Parameters.defaults() |
| `AE/analysis/PolyphonicAnalyzer.java` | `24` | already a setting | FFT size and band range (clarification 1), in Parameters.defaults() |
| `AE/analysis/PolyphonicAnalyzer.java` | `112` | already a setting | FFT size and band range (clarification 1), in Parameters.defaults() |
| `AE/analysis/PolyphonicAnalyzer.java` | `128` | mathematical | MIDI range |
| `AE/analysis/PolyphonicAnalyzer.java` | `50.0` | mathematical | half a semitone (50 cents), the edge of a semitone band |
| `AE/analysis/PolyphonicAnalyzer.java` | `1.1` | buffer or layout | peak search length: 10 % and 3 bins past the highest harmonic |
| `AE/analysis/PolyphonicAnalyzer.java` | `3` | buffer or layout | peak search length: 10 % and 3 bins past the highest harmonic |
| `AE/analysis/ArticulationDetector.java` | `19` | buffer or layout | the flux history kept (bounds recognition.onset.pre-first) |
| `AE/analysis/NnlsSolver.java` | `3` | numerical safety | iteration limit and step epsilon of the solver |
| `AE/analysis/NnlsSolver.java` | `1e-15` | numerical safety | iteration limit and step epsilon of the solver |
| `AE/analysis/AttackAnalyzer.java` | `4096` | already a setting | attack FFT 4096 (clarification 1), in Parameters.defaults() |
| `AE/analysis/AttackAnalyzer.java` | `3` | mathematical | a Gaussian kernel cut at three standard deviations |
| `AE/analysis/ChordEvidence.java` | `3.0` | algorithm definition | the second and third partials (harmonic numbers 2 and 3) and the credit they give (1/3, two partials) |
| `AE/analysis/ChordEvidence.java` | `128` | mathematical | MIDI range |
| `AE/analysis/ChordEvidence.java` | `3` | buffer or layout | three residual candidates (MAX_RESIDUAL) |
| `AE/analysis/StrumClipMeter.java` | `40.0` | already a setting | defaults of recognition.input.level.min, .max and advanced.level.clip-sample |
| `AE/analysis/StrumClipMeter.java` | `6.0` | already a setting | defaults of recognition.input.level.min, .max and advanced.level.clip-sample |
| `AE/analysis/StrumClipMeter.java` | `0.99` | already a setting | defaults of recognition.input.level.min, .max and advanced.level.clip-sample |
| `AE/analysis/RealFft.java` | `32` | mathematical | bit reversal of an int |
| `AE/frontend/SpectralSubtractor.java` | `32` | mathematical | bit reversal of an int |
| `AE/analysis/HarmonicComb.java` | `8` | algorithm definition | subharmonics 1/2 to 1/8 searched |
| `AE/analysis/HarmonicComb.java` | `27.0` | algorithm definition | fitted harmonic weight model, not a threshold |
| `AE/analysis/HarmonicComb.java` | `320.0` | algorithm definition | fitted harmonic weight model, not a threshold |
| `AE/analysis/InputAssessor.java` | `10_000_000_000` | plumbing | level check timeout |
| `AE/analysis/InputAssessor.java` | `120.0` | numerical safety | level of digital silence |
| `AE/analysis/InputAssessor.java` | `50_000_000` | plumbing | longest gap counted as playing between two frames |
| `AE/frontend/FrontEnd.java` | `48000` | audio format | sample rate 48,000 (clarification 1) |
| `AE/input/PcmSampleConverter.java` | `8` | audio format | bytes per sample |
| `AE/input/PcmSampleConverter.java` | `3` | audio format | bytes per sample |
| `AE/input/CaptureService.java` | `48000` | audio format | capture formats |
| `AE/input/CaptureService.java` | `16` | audio format | capture formats |
| `AE/input/CaptureService.java` | `24` | audio format | capture formats |
| `AE/input/CaptureService.java` | `8` | audio format | capture formats |
| `AE/input/InputDeviceCatalog.java` | `16` | audio format | capture formats |
| `AE/input/InputDeviceCatalog.java` | `24` | audio format | capture formats |
| `AE/backend/NativeBackend.java` | `16` | audio format | capture format; hop blocks read at once |
| `AE/backend/NativeBackend.java` | `3` | buffer or layout | record count and buffer sizes |
| `AE/backend/NativeBackend.java` | `8` | buffer or layout | record count and buffer sizes |
| `AE/calibration/RecordingQuality.java` | `0.10` | algorithm definition | the noise floor is the 10th percentile of the blocks |
| `AE/calibration/FrontEndState.java` | `31` | mathematical | hash code |
| `AE/calibration/RecognitionRate.java` | `31` | mathematical | hash code |
| `AE/calibration/SoundCounter.java` | `1e-6` | numerical safety | level of digital silence |
| `AE/calibration/SoundCounter.java` | `120.0` | numerical safety | level of digital silence |
| `AE/calibration/ProfileLibrary.java` | `3` | audio format | stored record layout |
| `AE/calibration/DeviceKey.java` | `9` | audio format | encoding of a device key |
| `AE/calibration/DeviceKey.java` | `16` | audio format | encoding of a device key |
| `AE/calibration/DeviceKey.java` | `3` | audio format | encoding of a device key |
| `AE/technique/SlideDetector.java` | `1.5` | score or display | confidence reported with the evidence, decides nothing |
| `AE/technique/SlideDetector.java` | `0.3` | score or display | confidence reported with the evidence, decides nothing |
| `AE/technique/VibratoDetector.java` | `1.5` | score or display | confidence reported with the evidence, decides nothing |
| `AE/technique/VibratoDetector.java` | `0.3` | score or display | confidence reported with the evidence, decides nothing |
| `AE/technique/BendDetector.java` | `1.5` | score or display | confidence reported with the evidence, decides nothing |
| `AE/technique/BendDetector.java` | `0.3` | score or display | confidence reported with the evidence, decides nothing |
| `AE/technique/BendDetector.java` | `0.8` | score or display | confidence reported with the evidence, decides nothing |
| `AE/technique/LegatoDetector.java` | `0.8` | score or display | confidence reported with the evidence, decides nothing |
| `AE/technique/LegatoDetector.java` | `0.6` | score or display | confidence reported with the evidence, decides nothing |
| `AE/technique/LegatoDetector.java` | `0.3` | score or display | confidence reported with the evidence, decides nothing |
| `AE/technique/BendDetector.java` | `3.0` | algorithm definition | search band of three semitones around the bend path |
| `AE/technique/BendDetector.java` | `0.95` | algorithm definition | peak taken as the 95th percentile |
| `AE/technique/PitchMotion.java` | `3` | algorithm definition | smoothing windows and percentiles of the pitch motion statistics |
| `AE/technique/PitchMotion.java` | `0.25` | algorithm definition | smoothing windows and percentiles of the pitch motion statistics |
| `AE/technique/PitchMotion.java` | `5` | algorithm definition | smoothing windows and percentiles of the pitch motion statistics |
| `AE/technique/PitchMotion.java` | `0.95` | algorithm definition | smoothing windows and percentiles of the pitch motion statistics |
| `AE/technique/PitchMotion.java` | `0.05` | algorithm definition | smoothing windows and percentiles of the pitch motion statistics |
| `AE/nativeengine/FrameRecords.java` | `32` | mathematical | two ints in a long |
| `AE/recognition/ExpectedPitchVerifier.java` | `32` | numerical safety | shortest lag examined |
| `PR/session/PracticeSession.java` | `3` | buffer or layout | index in the hop record |
| `PR/session/FollowSession.java` | `3` | buffer or layout | index in the hop record |
| `PR/session/ChordMatcher.java` | `0.79` | already a setting | defaults of the chord profile settings in the spec 023 constructor |
| `PR/session/ChordMatcher.java` | `0.40` | already a setting | defaults of the chord profile settings in the spec 023 constructor |
| `PR/session/ChordMatcher.java` | `84` | already a setting | defaults of the chord profile settings in the spec 023 constructor |
| `PR/session/ChordMatcher.java` | `200_000_000` | already a setting | defaults of the chord profile settings in the spec 023 constructor |
| `PR/session/ChordMatcher.java` | `3.0` | already a setting | defaults of recognition.chord.evidence, advanced.chord.confirm and chord.window |
| `PR/session/ChordMatcher.java` | `8` | already a setting | defaults of recognition.chord.evidence, advanced.chord.confirm and chord.window |
| `PR/session/ChordMatcher.java` | `600_000_000` | already a setting | defaults of recognition.chord.evidence, advanced.chord.confirm and chord.window |
| `PR/session/FollowTimeline.java` | `60e9` | mathematical | nanoseconds per minute |
| `PR/session/FireTuningStore.java` | `7` | audio format | colour text |
| `PR/session/FireTuningStore.java` | `16` | audio format | colour text |
| `PR/session/AmpSoundStore.java` | `3` | not recognition | window bounds of the amp sounds |
| `PR/session/AudioRoute.java` | `3` | audio format | stored route layout |
| `PR/session/TimingTolerance.java` | `0.10` | legacy migration | the presets of spec 002, kept to read older configuration files |
| `PR/session/TimingTolerance.java` | `40` | legacy migration | the presets of spec 002, kept to read older configuration files |
| `PR/session/TimingTolerance.java` | `0.25` | legacy migration | the presets of spec 002, kept to read older configuration files |
| `PR/session/TimingTolerance.java` | `300` | legacy migration | the presets of spec 002, kept to read older configuration files |
| `PR/session/TimingTolerance.java` | `0.15` | legacy migration | the presets of spec 002, kept to read older configuration files |
| `PR/session/TimingTolerance.java` | `150` | legacy migration | the presets of spec 002, kept to read older configuration files |
| `PR/session/TimingTolerance.java` | `0.40` | legacy migration | the presets of spec 002, kept to read older configuration files |
| `PR/session/TimingTolerance.java` | `450` | legacy migration | the presets of spec 002, kept to read older configuration files |
| `PR/session/TimingTolerance.java` | `90` | legacy migration | the presets of spec 002, kept to read older configuration files |
| `PR/session/TimingTolerance.java` | `250` | legacy migration | the presets of spec 002, kept to read older configuration files |
| `PR/session/TimingTolerance.java` | `0.60` | legacy migration | the presets of spec 002, kept to read older configuration files |
| `PR/session/TimingTolerance.java` | `220` | legacy migration | the presets of spec 002, kept to read older configuration files |
| `PR/session/TimingTolerance.java` | `700` | legacy migration | the presets of spec 002, kept to read older configuration files |
| `PR/session/TimingTolerance.java` | `8.0` | algorithm definition | the shape of the on-time window from recognition.tempo.window: the ratios of the old Normal, written as exact divisions so that 75 ms gives the old literals bit for bit |
| `PR/session/TimingTolerance.java` | `1500.0` | algorithm definition | the shape of the on-time window from recognition.tempo.window: the ratios of the old Normal, written as exact divisions so that 75 ms gives the old literals bit for bit |
| `PR/session/TimingTolerance.java` | `0.8` | algorithm definition | the shape of the on-time window from recognition.tempo.window: the ratios of the old Normal, written as exact divisions so that 75 ms gives the old literals bit for bit |
| `PR/session/TimingTolerance.java` | `6` | algorithm definition | the shape of the on-time window from recognition.tempo.window: the ratios of the old Normal, written as exact divisions so that 75 ms gives the old literals bit for bit |
| `PR/session/CalibrationRun.java` | `200_000_000` | plumbing | calibration timeout margin and rounding up to a second |
| `PR/session/CalibrationRun.java` | `999_999_999` | plumbing | calibration timeout margin and rounding up to a second |
| `PR/session/CalibrationTaps.java` | `1.4826` | mathematical | median absolute deviation to standard deviation |
| `PR/session/AmpSoundSettings.java` | `31` | mathematical | hash code |
| `PR/session/ScoreKey.java` | `8` | audio format | key length |
| `PR/arcade/core/ArcadeLevel.java` | `30` | score or display | the Arcade levels (how many notes are shown), not recognition |
| `PR/arcade/core/ArcadeLevel.java` | `3` | score or display | the Arcade levels (how many notes are shown), not recognition |
| `PR/arcade/core/ArcadeLevel.java` | `50` | score or display | the Arcade levels (how many notes are shown), not recognition |
| `PR/arcade/core/ArcadeLevel.java` | `75` | score or display | the Arcade levels (how many notes are shown), not recognition |
| `PR/arcade/core/ArcadeLevel.java` | `5` | score or display | the Arcade levels (how many notes are shown), not recognition |
| `PR/arcade/core/ArcadeLevelFilter.java` | `50` | mathematical | rounding of a percentage |
| `PR/arcade/core/ArcadeScoring.java` | `1.5` | score or display | Arcade score multiplier (clarification 2) |

`AE/monitor` (the amplifier and effects of spec 022) is out of the scan: it changes what the learner hears from the amplifier, never what is recognised.
