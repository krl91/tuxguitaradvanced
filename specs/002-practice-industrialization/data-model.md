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
`preset` (STRICT/NORMAL/LENIENT), `earlyFractionOfBeat`, `lateFractionOfBeat`, `floorMillis`, `latencyOffsetMillis` (audio-chain offset, learner-adjustable or calibrated). Persisted: the preset only; the offset is per session unless spike S-T1 shows it is stable (then decided with the owner).

### **FollowTimeline**
Ordered list of (`eventIndex`, `targetTimeNanos`) for a tempo percentage; built from the `PracticeSequence` and the song's tempo map. Immutable per session start; rebuilt when the percentage changes.

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
