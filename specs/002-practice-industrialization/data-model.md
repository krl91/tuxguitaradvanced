# Data Model: Interactive Practice Industrialization

Existing types referenced (inspected): `PracticeEvent`, `PracticeSequence`, `PracticeSnapshot`, `Feedback`,
`AnalysisFrame`, `SpectrumFrame`, `RecognitionParameters`/`RecognitionSettings`, `CurrentInputSelection`,
`InputAssessment`. New types are in **bold**. All are plain Java, no UI toolkit or hardware dependency.

## Engine module (`tuxguitar-audio-engine`)

### **InputGain** (frontend)
- `multiplier` (double, 1.0 neutral, bounded; range and step fixed from trials), `volatile`.
- Rules: reset to 1.0 when the `CurrentInputSelection` input revision changes and at engine construction; never persisted.

### **NoiseEstimate** (frontend)
- `floorDbfs` (per-band optional), `confidence`, `source` (LEVEL_CHECK, QUIET_TRACKING).
- Updated from the silent part of the level check and during quiet hops; reset when the input changes.

### **FrontEndSettings** (persisted via `RecognitionParameters`)
- `noiseReduction` (Flag, default per spike S-B1), `gainSuggestion` (Flag, default TBD). The gain value itself is not persisted.

### **AnalysisFrame** (existing, additive)
- New optional members, null unless enabled (same pattern as `spectrum`): `TargetEvidence` (expected-pitch verifier, Phase C) and `TechniqueFeatures` (Phases D/F).

### **TargetEvidence** (recognition)
- Per hypothesis (expected, ±1 semitone, octave): `midi`, `score` (0–1 periodicity evidence), `marginOverBest`; `windowMillis` used.

### **TechniqueFeatures** (per hop)
- `f0Hz`, `f0Cents` relative to a reference, `levelDb`, `levelSlopeDbPerMs`, `spectralCentroidHz`, `highFreqRatio`, `partialCount`/`flatness`, `flux`, `provisionalOnset` (flag with transient time).

### **TechniqueEvidence** (per note segment, output of the detectors)
- `technique` (**Technique**), `verdict` (**TechniqueVerdict**), `confidence` (0–1), `measurements` (named doubles, e.g. `glideCents`, `vibratoDepthCents`, `vibratoRateHz`, `decayDbPerMs`), `explanationKey` (i18n key for the learner-facing text).

## Practice module (`tuxguitar-practice`)

### **Technique** (supersedes the display-only `PracticeEvent.Technique`)
`HAMMER_ON`, `PULL_OFF`, `SLIDE`, `BEND`, `PRE_BEND`, `RELEASE`, `VIBRATO`, `PALM_MUTE`, `TREMOLO_PICKING`, `NATURAL_HARMONIC`, `ARTIFICIAL_HARMONIC`, `PINCH_HARMONIC`, `TAPPING`, plus the existing display-only `ACCENT`, `LET_RING`, `STACCATO` (never judged). Extensible (FR-028): a new value needs a detector or defaults to `NOT_VERIFIABLE`.

### **TechniqueVerdict**
`DETECTED`, `NOT_DETECTED`, `UNCERTAIN`, `NOT_VERIFIABLE`. A detector may only emit the first three; `NOT_VERIFIABLE` is set by the judge when no detector exists, when technique detection is switched off (FR-032), or when the benchmark-gated "verified" flag for that technique is off (SC-006).

### **TechniqueTarget** (attached to `PracticeEvent`)
- `technique`, optional `targetMidi` or **PitchPath** (bend: ordered (relativeTime, semitones) points from `TGEffectBend`), optional `soundingMidi` (harmonics), `partnerEventIndex` (destination of a hammer-on, pull-off or slide).

### **PracticeEvent** (existing, changed)
- `expectedMidi` stays the validated pitch (harmonics: the sounding pitch; bends: the target pitch, with the path in `TechniqueTarget`).
- `Kind`/`AutoPassReason`: `BEND` and `HARMONIC` no longer auto-pass (research R5); `TREMOLO_BAR` and `TRILL` still do.
- New: `techniqueTargets : List<TechniqueTarget>`. Event times are not stored here; the follow timeline computes them.

### **NoteEventResult** (per expected event)
- `pitchVerdict` (existing `Feedback.Kind` / `Verdict` semantics), `techniqueResults : List<TechniqueResult>`, `timing : TimingVerdict` (tempo mode only).
- **TechniqueResult**: `technique`, `TechniqueVerdict`, `confidence`, `detail` (learner-facing key plus numbers, e.g. "bend reached 0.8 of 1 semitone").

### **TimingVerdict** (tempo mode)
`ON_TIME`, `EARLY`, `LATE`, `MISSED`, `EXTRA` (an onset matching no event), with `offsetMillis` for early/late.

### **TimingTolerance**
`preset` (STRICT/NORMAL/LENIENT) giving, as a fraction of the local beat with a floor and a ceiling in milliseconds, the half-width of the tight window (ON_TIME) and of the outer window (EARLY / LATE); the outer window is cut at the middle of the gap to the neighbouring events. Persisted: the preset only. **It no longer holds the latency offset** (decided 2026-10-07): that is `LatencyCompensation`.

### **LatencyCompensation**
`offsetMillis` (audio-chain offset of the route, positive or negative, within +-1000 ms) and `source` (NONE / MANUAL / CALIBRATED). Subtracted from the observed time of an onset; never added to a window. NONE means 0 and "not calibrated". Kept per input + output + audio format/profile (FR-058); it is the offset of one route, not a property of a device name.

### **ClockUncertainty**
`captureLagSpreadMillis` from the capture clock: a route whose arrival lag spreads over 80 ms or more (provisional) is called unstable. Never widens a window.

### **TimingContext**
`TimingTolerance` + `LatencyCompensation` + `ClockUncertainty` + whether the capture clock and the player clock are ready. `mayJudge()` (both clocks ready: before that **no** EARLY, LATE, MISSED, EXTRA or WRONG), `precision()` (NOT_READY / REDUCED / NORMAL) and its reasons (CLOCKS_NOT_READY, NOT_CALIBRATED, UNSTABLE_ROUTE), reported separately.

### **FollowTimeline**
Ordered list of (`eventIndex`, `targetTimeNanos`, `targetTick`, local beat) for a tempo percentage; built from the song (its tempo per measure, repeats and alternate endings in `MidiRepeatController` order) and the `PracticeSequence`. The tick is the expanded position the player will reach (the player's own repeats are expanded), which the player clock turns into wall time. Immutable per session start; rebuilt when the percentage changes.

### **FollowSession** (state machine)
States: `PREPARING`, `COUNT_IN`, `FOLLOWING`, `PAUSED`, `COMPLETE`, `STOPPED`.
Transitions: `PREPARING → COUNT_IN` on Start (input suitable and track accepted, as `canStart` in wait mode); `COUNT_IN → FOLLOWING` at the first beat; `FOLLOWING → PAUSED` on input loss, tuner open or manual pause; `FOLLOWING → COMPLETE` when the last event window closes. No state ever waits on a wrong or missed note (FR-053).

### **PassageSummary** (extends the existing `PassageStatus.Summary`, memory only)
Counts: correct, early, late, wrong, missed, extra; per technique: detected, not detected, uncertain, not verifiable.

### **LauncherState** (Phase A)
`INACTIVE`, `ACTIVE` (window open), `UNAVAILABLE(reason)`. Reasons: no song, no playable guitar track (an unset audio input still lets the window open so it can be set up). Drives the toolbar item's enabled/checked/tooltip through the registered updater.

## Relationships

`PracticeSequence 1—* PracticeEvent 1—* TechniqueTarget`;
`AnalysisFrame 0..1—1 TechniqueFeatures`; detectors turn `TechniqueFeatures` over a note segment into `TechniqueEvidence`;
`TechniqueJudge` joins `TechniqueTarget` + `TechniqueEvidence` into a `TechniqueResult`; `FollowTimeline` + onsets give a `TimingVerdict`;
all of these feed `NoteEventResult` and `PassageSummary`.

## Validation rules (from the spec)

- A technique verdict is never `DETECTED` from pitch alone (FR-029, constitution XIII).
- Technique outcomes never change progression in wait mode (FR-030) and never change the timing verdict in tempo mode.
- Gain resets on input change and at launch (FR-063); noise processing can be switched off (FR-064).
- No audio is stored (FR-042): only derived numbers live in memory.
