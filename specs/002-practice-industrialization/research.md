# Research: Interactive Practice Industrialization

Conventions: each item gives **Decision / Rationale / Alternatives**, cites the inspected source, and
labels what is **verified from source** versus **unverified (spike)**. No experiment listed here as
a spike has been run yet. File paths are relative to the repository root.

## R1. Toolbar button next to Play (Phase A)

**Inspected**: `desktop/TuxGuitar/src/app/tuxguitar/app/view/toolbar/main/`
- `TGMainToolBar.layout()` creates `new TGMainToolBarConfigMap()`; all items (including
  `transport.start`, bound to `TGTransportPlayPauseAction.NAME`) are registered inside that class's constructor.
- `TGMainToolBarConfig` holds the default layout as static arrays; the centre area is
  `transport.first, transport.previous, transport.start, transport.stop, transport.next, transport.last, …`.
- `TGMainToolBarConfigManager` adds user layouts from `TGUserFileUtils.PATH_USER_TOOLBAR`; the default config `""` is always code-built.
- `TGMainToolBarSectionToolItems` supports `ACTION_ITEM` and `CHECKABLE_ITEM`; `TGMainToolBarItemUpdater` gives enabled/checked/icon/text per state.
- The Tools menu item comes from `TGToolItemPlugin` (action id `practice.open`, `PracticePlugin.OPEN_ACTION_ID`).
- Icons resolve through `TGIconManager.loadIcon` → `TGFileUtils.loadImage` → `TGResourceManager.getResourceAsStream("skins/<skin>/<name>")`; a missing icon logs an error and yields a blank 16×16 image.

**Decision**: add `TGMainToolBarItemRegistry` (core, ~60 lines): plugins register item definitions
(`text key`, `action id`, `icon name`, `kind` action/checkable, `updater`, optional "default placement: after
<item>"). `TGMainToolBarConfigMap` copies registered items into its map at construction; `TGMainToolBarConfig`
inserts registered default items after the named anchor (`transport.start`). `PracticePlugin.connect` registers a
**checkable** item bound to `practice.open` (checked = window open); `disconnect` unregisters. Users with a saved
custom toolbar keep it unchanged and can add the button in the toolbar settings dialog.

**Rationale**: smallest core change that keeps core unaware of the fork plugin (Constitution II); no dead button when
the plugin is absent (nothing registered). Toggle/checked state maps to spec FR-003.

**Alternatives**: hard-code the item in core (rejected: coupling, dead button); put the button in the practice window
only (rejected: not one click); a standalone floating toolbar (rejected: not "next to Play").

**Spikes**: S-I1 — does `TGResourceManager` see `share/skins/<skin>/` files shipped by a plugin jar/`share` dir? If not,
ship the six icons in `desktop/TuxGuitar/share/skins/*` (a resource-only core change). S-I2 — button behaviour
while the player runs (FR-006) depends on R10. Both checked in Phase A.

## R2. Where gain and noise processing go (Phase B)

**Inspected**: `CaptureService.captureSamples` writes raw mono `float[]` to `SampleRingBuffer`;
`AnalysisService.run` reads hops of 256 samples (`TarsosFastYinEstimator.DEFAULT_HOP_SIZE`), keeps a 2048-sample window
(`DEFAULT_WINDOW_SIZE`), then computes level (`LevelAnalyzer`), pitch (`PitchEstimator`), onset (`ArticulationDetector`)
and optionally the polyphonic spectrum. `InputAssessor` is an analysis listener, so the level check sees whatever the
analysis produces. `AudioEngine` is one shared pipeline for tuner and practice; `CurrentInputSelection` carries an
`inputRevision` that changes when the input changes. `LevelPanel` (practice) and `TunerWindow` (tuner) already show the level
state but neither has a gain control (`LevelPanel` javadoc: "Never changes the input gain").

**Decision**: insert a `frontend` stage **per hop inside `AnalysisService`, before level/pitch/onset/spectrum**:
`InputGain` (volatile multiplier, 1.0 default) → `HighPass` (2nd-order, ≈ 50–60 Hz so low E at ≈ 82 Hz and drop-D at ≈ 73 Hz
are not attenuated; exact corner from a test) → `NoiseGate` (hysteresis + hold, threshold = estimated noise floor + margin,
never gating the decay of a ringing note) and, behind a benchmark switch, `SpectralSubtractor` (STFT overlap-add, over-subtraction factor, spectral floor).
The ring buffer stays raw. The noise floor comes from `InputAssessor`'s silence portion and from a minimum-statistics tracker during quiet hops.
Gain lives in memory in the engine and is reset when `inputRevision` changes and at construction (spec FR-063).

**Rationale**: one place serves tuner and practice; level meter, clipping and recognition all see the same processed signal (FR-061);
no change to the real-time capture thread; per-hop cost is tiny (256 samples). Gate first, subtraction second: the gate adds no latency beyond hold;
subtraction adds ≈ one STFT window (≈ 10 ms at 512 samples, to be measured) and risks musical-noise artefacts on YIN.

**Alternatives**: gain in `CaptureService` before the ring buffer (rejected: the capture thread should stay minimal and unaware of settings);
OS-level gain via Java Sound (rejected: MVP research S2 found no usable control on the validated setup); Kalman-type filtering (dropped by the owner 2026-09-30;
noted only as a possible pitch-track smoother).

**Suggested gain (FR-066)**: from a few seconds of the learner's playing during the level check, compute the gain that brings the typical
peak/RMS into the SUITABLE band of `LevelAnalyzer` (`DEFAULT_TOO_LOW_RMS_DBFS` −40, `DEFAULT_TOO_HIGH_RMS_DBFS` −6), never above a cap, never automatic.
The feature switch is a persisted `RecognitionParameters.Flag` (default to be decided from trials; spec says "easily disabled").

**Spikes**: S-B1 — with/without subtraction on the real-guitar clips (built-in mic and interface): recognition accuracy and latency; ship the gate alone if subtraction loses anything.
S-B2 — measure the quiet-noise floor of the built-in microphone versus an interface to set the gate margin and the "boost mostly amplifies noise" warning (FR-062).

## R3. Latency and fast recognition (Phase C)

**Inspected (numbers from source)**: hop 256 samples = 5.33 ms (`RecognitionParameters.HOP_MILLIS`); YIN window 2048 samples = 42.7 ms;
`NOTE_STABILITY` default 107 ms (20 hops); `ArticulationDetector`: `DECISION_DELAY_HOPS` 13 (≈ 69 ms), `DEFAULT_REFRACTORY_HOPS` 12 (≈ 64 ms),
gate compares mean level over hops 4…13 after the transient with the **maximum** level over hops 19…2 before it (`PRE_FIRST` 19, `POST_LAST` 13).
`PracticeSession.onNoteFrame` accepts only when the onset has armed acceptance **and** readings match for the stability window.
From the MVP research (`specs/001-interactive-practice-mvp/research.md`, benchmark section): median stable-detection latency 171 ms, max 288 ms at gate 0.80 / 20 hops.

**Findings (derived, not yet measured on new code)**:
1. Two separate waits stack: attack confirmation (≈ 69 ms) and stability (≈ 107 ms), both running from the note start; acceptance time ≈ max of the two plus YIN warm-up, which matches the 171 ms median. Targeting 150 ms median means removing or shortening both waits without raising false accepts.
2. The detector's post window (hops 4…13 ≈ 21–69 ms after the transient) and pre window (up to 19 hops ≈ 101 ms before) are longer than the spacing of fast notes: at 12 notes/s the spacing is ≈ 83 ms ≈ 15.6 hops, so the next attack falls inside the previous note's post window, and at 8 notes/s (125 ms ≈ 23 hops) the pre-window still reaches the previous attack. Identical repeated notes at speed are therefore not reliably separable with the current gate.
3. The "fresh attack" rule is only needed when the expected pitch equals the pitch still ringing (`PracticeEvent.isSameAsPrevious`). For a different pitch, the arrival of the expected pitch after a different one is itself fresh evidence. This is also exactly what hammer-ons/pull-offs/slides need (spec FR-021) and resolves MVP question Q4-L option (b).

**Decision (levers, each kept only if the benchmark shows a gain without more false accepts)**:
- **L1 Score-informed verification** (`recognition/ExpectedPitchVerifier`): the session tells the engine the expected pitch(es) (volatile array); the worker evaluates only a few hypotheses (expected, ±1 semitone, octave) with a periodicity score on a window of ≈ 3 periods (shorter for high strings) and publishes per-hypothesis evidence alongside the free f0. Deterministic, cheap, less exposed to octave/subharmonic errors (MVP S3: ≈ 16 ms of subharmonic readings at each attack).
- **L2 Confidence-scaled stability**: replace the fixed 107 ms by a rule "accept when evidence margin is large for ≥ N hops", N depends on the expected period and the margin; keep 107 ms as the conservative fallback.
- **L3 Pitch-change acceptance**: accept a *different* expected pitch from a stable pitch change (no confirmed attack required); keep the attack rule for identical repeats and chords.
- **L4 Attack detector v2 for repeats**: release-aware baseline (level over the last 2–8 hops instead of the maximum over 19), shorter post window, refractory parameterised so tremolo picking can lower it; a provisional flux-only onset timestamp (for timing and display) followed by confirmation.
- **L5 Window choice**: smaller YIN window when the expected f0 is high (measured benefit only).

**Rationale**: all four are deterministic, separable, and testable with synthetic signals; L1/L3 are the largest expected wins and also serve techniques.

**Alternatives**: a different pitch library or ML onset detector (rejected: constitution VIII, licensing, no evidence yet that deterministic methods fail); simply lowering `NOTE_STABILITY` (rejected as a blind change: MVP data showed correctness does not depend on the gate but false accepts rise with shorter runs — re-measure instead).

**Spikes / first tasks**: S-C1 — re-run the MVP benchmark on the current tree for a fresh baseline; add latency reporting (p50/p95) per clip; S-C2 — synthetic fast passages (Karplus-Strong, 4–12 notes/s, identical and neighbouring pitches) and a recording plan for real clips (CC0, as in the MVP).

## R4. Technique features from a single mono signal (Phases D, F)

**Inspected in code**: the model already carries the written techniques: `TGNoteEffect` (`isHammer`, `isSlide`, `isVibrato`, `isBend` + `TGEffectBend` points, `isPalmMute`, `isTremoloPicking`, `isHarmonic` + `TGEffectHarmonic` types `TYPE_NATURAL/ARTIFICIAL/TAPPED/PINCH/SEMI`, `isTapping`, `isTrill`). `PracticeEvent.Technique` today holds only display-only values (`HAMMER_ON_PULL_OFF` merged, `SLIDE`, `VIBRATO`, `PALM_MUTE`, `ACCENT`, `LET_RING`, `STACCATO`, `TREMOLO_PICKING`); bends and harmonics are auto-passed (`AutoPassReason.BEND`, `HARMONIC`) in `PracticeSequenceBuilder` and never judged. `TGEffectBend` positions go to `MAX_POSITION_LENGTH` 12 with value length `SEMITONE_LENGTH` × 12 (exact value→semitone scaling to be confirmed from `TGEffectBend` / the player's bend handling before use).
`TGEffectHarmonic.NATURAL_FREQUENCIES` lists natural-harmonic frets; artificial/pinch carry an offset (`getData`, ±24).

**Web survey (2026-09-30, standard search, abstracts only; not a literature review)**: published guitar-technique detectors (Reboursière et al., NIME 2012, "Left and right-hand guitar playing techniques detection"; ISMIR/DAFx tablature-transcription papers; a technique-classifier project and a "TART" tablature-transcription paper, seen by title only) use rules or classifiers on pitch trajectory, energy envelope and spectral features; the most robust use a hexaphonic pickup (one signal per string). No source examined provides validated thresholds for our single-input, non-hexaphonic case. Conclusion: use **transparent rule-based features with wide "uncertain" bands**, calibrated on our own recordings.

**Decision — feature set and rules (hypotheses to validate; all thresholds are provisional parameters)**:

| Technique | Evidence used | Verdict logic |
|---|---|---|
| Hammer-on / pull-off | pitch step between two stable segments with **no confirmed attack** (level rise below the existing gate) and smooth sustain; pull-off: lower pitch, level not above the previous note | detected when step + no attack; not detected when a confirmed attack precedes the second note; uncertain when the level rise is borderline (between "no-attack" and gate thresholds) |
| Slide | monotone f0 glide through intermediate pitches between start and target over ≥ X ms, no attack at the arrival | detected on glide; not detected on a direct jump |
| Bend (incl. pre-bend, release) | f0 trajectory relative to the start pitch vs the written path (target semitones from `TGEffectBend`) | per-path check within a widened cents tolerance; under/over/on-pitch reported; pitch verdict uses the path, not the static note |
| Vibrato | periodic f0 modulation ≈ 4–8 Hz, depth ≥ ~10 cents over ≥ ~400 ms | detected / not detected / uncertain by modulation depth and periodicity |
| Palm mute (P2) | fast level decay, lower spectral centroid / high-frequency ratio than an open note; relative to the learner's own unmuted notes in the session | uncertain unless the ratio is clearly on one side |
| Tremolo picking (P2) | onset density on one pitch above a rate; requires low refractory | one event, technique detected when ≥ rate; otherwise not detected |
| Natural harmonic (P2) | sounding pitch from the node; near-sinusoidal spectrum (few partials, low inharmonic energy) versus a fretted note | pitch validated; technique detected/uncertain by spectral shape |
| Artificial / pinch harmonic (P3) | sounding pitch (offset) + high partial emphasis | mostly "uncertain"; "detected" only if the benchmark supports it |
| Tapping (P3) | no-attack notes at speed with consistent low pick-noise | expected to ship "not verifiable" unless a spike shows a reliable cue |

**Rationale**: matches the spec's honesty rules (FR-029); every rule is explainable to a learner (Constitution VIII/XIV) and testable on synthetic signals first (Karplus-Strong + added glide/modulation/decay), then real clips.

**Alternatives**: a trained classifier on spectral features (rejected unless the deterministic benchmark fails, Constitution VIII: requires a spec-level need and a licensing/size analysis); requiring a hexaphonic pickup (rejected: outside the target setup, but noted as a future input capability).

**Spikes**: S-F1 — record the P1 clips on the owner's setup (direct interface, and built-in mic) and compute candidate features offline to check that each technique is separable from its plain counterpart; publish separability per technique. S-F2 — for P2/P3 repeat with the same protocol; techniques that are not separable ship as "not verifiable".

## R5. Sequence-model changes for bends and harmonics (Phases D, F)

**Decision**: `PracticeSequenceBuilder` stops auto-passing `BEND` and `HARMONIC`: bends become `EXPECTED_NOTE` with a pitch path (start, target, release) derived from `TGEffectBend`; harmonics become `EXPECTED_NOTE` with the **sounding** pitch (natural: from the harmonic frets table; artificial/pinch/tapped: from `TGEffectHarmonic.getData()` offset; the exact sounding-pitch rule must be confirmed against how TuxGuitar's player computes it before implementation). Techniques and targets become a richer `TechniqueTarget` per event (see data-model.md). Tremolo bar (dive) and trill remain auto-pass (not in scope).
**Alternatives**: keep auto-pass with a technique-only note (rejected: bends would stay unjudged and spec FR-022/FR-026 unmet).

## R6. Tempo-following clock (Phase E)

**Inspected**: `MidiPlayer` exposes `getTickPosition`, `setTickPosition`, `getCurrentTempo`, `isMetronomeEnabled`, `getMode()` (with percent settings), `getCountDown()` (count-in: `MidiPlayerCountDown`), player events (`MidiPlayerEvent.NOTIFY_STARTED/STOPPED`), per-track mute through the sequencer. `SessionEvents` currently pauses practice on `NOTIFY_STARTED` with `PauseReason.PLAYBACK`. Analysis frames are timestamped in **stream time** (`AnalysisService`: `baseNanos + processedSamples…`), not wall-clock time; `CaptureService` reads 1024 frames (≈ 21 ms at 48 kHz) per call with a line buffer of 4 reads.

**Decision (recommended, pending spike S-T1)**: use TuxGuitar's player as the **clock and backing track** (practice track muted, other tracks audible, metronome and count-in already available, tempo percent from the player mode), and relate frames to the player position through a measured mapping (stream time ↔ `System.nanoTime` at capture, plus a learner-adjustable/auto-calibrated latency offset). Judging uses the **transient time** of the onset (already computed from the decision delay in `PracticeSession`: `armTransientNanos = now − latency`), so feedback latency does not bias timing.
**Fallback**: a practice-owned scheduler (nanoTime-based) plus its own click, if player tick resolution or jitter is insufficient.

**Spikes**: S-T1 — measure the round trip (metronome click out → input) and the jitter of `getTickPosition` vs wall time, with interface and built-in mic; decide player-clock vs own clock, and whether auto-calibration can replace manual offset. S-T2 — confirm how to mute the practice track without altering the song model (player-side mute only, no undo entry).

## R6b. Tempo-following judging

**Decision**: a `FollowSession` builds a timeline from the same `PracticeSequence` (event time = measure/beat start converted with the tempo map at the chosen percent — conversion routine to be identified in the player/`TGTempo` code during tasks, not assumed here). Each onset with pitch evidence is matched to the nearest unmatched event within `[−earlyTol, +lateTol]`; unmatched events become **missed** when their window closes; unmatched onsets are **extra**; matching is greedy with resynchronisation so one mistake never shifts later events. Tolerance presets (strict/normal/lenient) are expressed as a fraction of the local beat duration with a millisecond floor (so they scale with tempo, FR-052); provisional numbers to be set from S-T1 jitter plus recordings. Chords reuse `ChordMatcher`; techniques reuse the Phase D/F judges.

## R7. UI integration details

**Inspected**: `PracticeWindow`, `LevelPanel`, `TunerWindow`, `RecognitionSettingsPanel`/`AudioRecognitionOption` (Configuration rubric "Audio recognition"), `RecognitionParameters` (types `Number`, `Flag`, `Choice`, sections `INPUT/TUNER/NOTES/CHORDS/PASSAGES/ENGINE/ADVANCED`).
**Decision**: gain slider as a compact control in `LevelPanel` and in `TunerWindow`, both bound to the engine's in-memory gain (not a `RecognitionParameter`, since it is not persisted); the noise-reduction switch, suggestion switch, tolerance preset and technique switch are persisted `RecognitionParameters` shown in the existing "Audio recognition" options (new section `TECHNIQUES`/`TEMPO` or reuse `NOTES`). Strings go to `desktop/TuxGuitar-practice/share/lang/messages*.properties` (English + French), per the constitution.

## R8. Dependencies and licences

No new third-party dependency is planned. TarsosDSP 2.5 (`be.tarsos.dsp:core`, GPL-3.0, from `https://mvn.0110.be/releases`) is already used by the audio engine; unchanged. Any library proposed during tasks (e.g. for filtering) must be recorded here with name, version, licence and source before use; the default is to implement the small filters in-house.

## R9. Benchmark extensions

Extend `BenchmarkFixtures`/`BenchmarkSweepTest` (already tagged `benchmark`) with: latency p50/p95 per clip; fast passages (synthetic first); identical repeated notes; per-technique clip pairs (technique vs plain); with/without front end; built-in mic vs direct interface reported separately. Recording assets follow the MVP rules (CC0-1.0 licence file, short WAV, mono 48 kHz 16-bit, guitar only). Targets in the spec are then confirmed or revised with the owner from the measured baseline (Constitution XII).

## R10. Playback vs practice interaction

Wait mode keeps the MVP rule (player start pauses practice with `PauseReason.PLAYBACK`). In tempo-following mode the player is part of the feature, so its start/stop are driven by the follow session and must not trigger the pause; pressing Play manually while a follow session is open stops or restarts it with a message (FR-006). Exact interaction to be fixed in tasks once S-T2 is answered.

## Open questions carried to tasks

1. S-T1 outcome (clock choice) and latency-offset calibration UX.
2. Whether `TGResourceManager` picks plugin-shipped skin icons (S-I1).
3. Exact bend value→semitone scaling and harmonic sounding-pitch rule (verify against player code).
4. Default state of the gain-suggestion switch and of spectral subtraction (from S-B1/S-B2).
5. Real-clip recording session plan and who records (project owner, as in the MVP).

## Results of the Phase 4 spikes (2026-10-01)

### S-B1: front end with and without (T042)

Test `FrontEndBenchmarkTest` (benchmark tag), report `benchmark/frontend-with-without.csv`. The 21 built-in-microphone clips with a note, each preceded by 2 s of the same microphone's silence clip (so the gate can learn the room noise), run through no front end, the noise gate (rumble filter + gate) and gate + spectral subtraction; then the same with 50 Hz hum and broadband noise added.

| Condition | Clips with the right stable note | Median stable-note time from the start of the clip |
|---|---|---|
| none, clean | 19 / 21 | 736 ms |
| gate, clean | 19 / 21 | 736 ms |
| gate + subtraction, clean | 19 / 21 | 741 ms (+5.3 ms: one hop) |
| none, with hum and noise | 19 / 21 | 736 ms |
| gate, with hum and noise | 19 / 21 | 736 ms |
| gate + subtraction, with hum and noise | 19 / 21 | 741 ms |

(The 2 clips without a stable note are the two known-issue clips E4 open string 1 and E5 fret 12 string 1, in every condition.) The level-based onset time the older report uses is not comparable once noise is added (the added noise alone crosses its threshold), so the table counts from the real start of the clip.

**Reading**: on these clips the front end loses nothing and gains nothing in note recognition; the added noise does not even degrade the pitch estimator. The subtraction costs one hop (5.3 ms). The benefit of the gate is elsewhere (false attacks and false notes between notes) and is not measured by this metric.
**Decision (to be confirmed by the owner)**: noise reduction stays **off by default**, spectral subtraction stays **off** (no measured gain for +5 ms); both remain available. A metric that counts false attacks between notes is still missing and is the next thing to measure before changing a default.

### S-B2: noise floor and playing level (T041)

From the recorded built-in-microphone clips: the room (silence clip) is at about −64 dBFS RMS (peak about −50 dBFS); the loudest 200 ms of the five open-string clips is between −12 and −23 dBFS RMS, so about 40 to 50 dB above the room. A live check from the terminal on the owner's machine on 2026-10-01 gave peaks of about −35 to −41 dBFS in a quiet room for the built-in microphone, which is a higher floor than the recording. **Not measured**: an audio interface (owner's hardware). The gain range −6 … +18 dB and the "boost mostly amplifies noise" threshold (playing less than 20 dB above the background) are provisional until an interface is measured.

## R3 measurements — expected-pitch verifier and fast attack detector (Phase 5, 2026-10-01)

- **ExpectedPitchVerifier** (synthetic Karplus-Strong, 48 ms window): margin of the expected pitch over its neighbours/octave is 0.66-0.99 up to E4 (MIDI 64), but only 0.05-0.26 above (a 20-30 ms window barely resolves a semitone). Not used alone to accept high notes.
- **Fast ArticulationDetector** (`ArticulationDetector.fast`): synthetic equal re-plucks on a ringing string rise only 2-4 dB, so the MVP gate (4.5 dB) finds 1-2 of 6 repeats at 6-12 notes/s. With a 1.5 dB gate the fast detector finds 6/6 at 6/s, 5/6 at 8 and 10/s, 3/6 at 12/s, and keeps rejecting vibrato, beating, clicks and dropouts (synthetic).
- **Real clips** (17 single-pluck clips, `builtin-mic-articulation-fast-sweep.csv`): MVP 0 false attacks at gate 4.5 dB; fast detector 4 false attacks (2 clips) at gates 1.5-4.5 dB, 2 at 6 dB. Decision: the fast detector is NOT the default; MVP stays the default until recorded fast runs (T053) and a better discriminator exist. Consequence: identical fast repeats stay a documented limit; different-pitch runs are covered by the L3 rule (no attack needed).

### R3 — evidence margin on the real clips (T049, 2026-10-01)

`builtin-mic-evidence.csv` (15 real single-pluck clips, built-in mic): the expected-pitch margin never reaches 0.5 (max 0.07-0.41 for the played pitch), and a wrong pitch (semitone, two semitones, octave) reaches up to 0.31, so the evidence does not separate the played note from a wrong one on real guitar (it does on synthetic Karplus-Strong up to E4). Consequence: confidence-scaled stability (L2) is built and unit-tested but switched off (`PracticeParameters.EARLY_STABILITY_ENABLED = false`); expected pitches are not sent to the engine while it is off. Lever L1 (expected-pitch verification) is therefore not validated on real audio; revisit with the T053 recordings or a different measure (harmonic-comb energy instead of single-lag autocorrelation). The L3 rule (different pitch needs no attack) is the only recognition-speed gain shipped in Phase 5 so far.

## R6 — Real polyphonic recording (2026-10-05)

See `benchmark/real-recording-1/README.md`. Findings: (1) the chord matcher's unique-partial evidence cannot separate the right chord from wrong ones on a clean acoustic recording whatever the settings (evidence 20-60 %, presence ratio 5-40 %, window 600-1500 ms, confirm 20-43 ms): the settings that accept the first chord also accept 4-5 of 6 wrong ones; a missing fundamental can look present through another note's partial. (2) Single-note recognition by f0 fails on fingerstyle: a ringing bass keeps YIN on the bass (2/34 notes accepted with the defaults). (3) The attack-difference spectrum (partials that grew between just before and just after the attack) ranks the expected note first 27/34 and the chords first 5/8 (top-2 7/8): the basis of spec 004.

### R7 — The reference recording does not match its score everywhere (2026-10-05)

Spec 004 work, attack-difference ranking on raw audio (event times snapped to the detected attacks; FFT 4096, post window 80 ms, comb of 10 partials within 15 cents): 28/34 notes rank first. The failures: events 4, 5, 6 (triplets 0.10 s apart, windows overlap) and events 17, 26, 31 (the last bass note E2 of measures 3, 5 and 6). For events 26 and 31 the audio has no E2 at all (energy at E2 ≈ 40 against 500-800 for what is played: A2+A3 at 9.3 s, G2+G3 at 11.1 s) while the score asks for E2. Event 17 has a weak E2 under the ringing A2/G2. Event 18 (F2 + G3) matches. Conclusion: 100 % on this score is not reachable by any recogniser for events 26 and 31 unless the score is made to match the recording; reported to the owner.

## R8 — Attack-difference recognition, measured on the reference recording (spec 004, 2026-10-05)

Production code path (OfflineAnalysis -> AttackAnalyzer -> PracticeSession with PolyphonicAcceptance, defaults), replay test `RecordedPracticeReplayTest` on `tests/1/audio_15ps.m4a` + `_TEST_verifie.tg`:
- 38 of 38 judged events recognised (notes and chords), 0 false acceptances on 98 neighbouring-event tests, none recognised before its attack, delay median 107 ms, p90 169 ms. 4 events within 0.15 s of a neighbour (the triplets) are reported as a speed limit, not judged.
- `MonophonicRegressionReplayTest` on the 15 single-pluck clips: 15/15 still recognised, 0 wrong note accepted (before: 15/15, 0). Getting there needed four things measured one by one: the pitch-offset check (partials that just appeared within the tolerance + 15 cents), a veto when the pitch estimate hears a semitone/whole-tone neighbour, a 60 ms guard after a pluck from near silence (the thump and body resonances look like other pitches), and a floor on the growth (0.01) against room noise; the single-pitch and chord rules keep judging in parallel (the safe path for a clean note).
- KNOWN LIMIT (FR-003 not met): 4 of 12 chords with a note missing are still recognised (G2 alone for G2+B3, G3 alone for F2+G3, D4 alone for G2+D4). No balance threshold separates them: the genuine chords show upper notes down to 6 % of the bass, and G2/B3 (and G2/D4: D4 is G2's 3rd partial) share partials. Next steps to try: per-note unique-partial evidence at higher resolution (8192), a longer lag, more recordings. Parameters were tuned on this single recording and the 15 clips: new recordings may need re-tuning.

## R9 — Public datasets benchmark (spec 004, 2026-10-05)

`DatasetBenchmarkTest` (tag `dataset`, data kept in `~/tuxguitar-datasets`, never committed: CC BY-NC-ND / BY-NC) replays IDMT-SMT-Guitar V2 (dataset 1: notes and annotated chords, 2 guitars x settings) and IDMT-SMT-Audio-Effects (guitar notes, intervals and chords, 11 audio effects) through the production chain. Each recording is preceded by half a second of silence (a learner presses Start, then plays; without it the 220 ms re-arm hid the first notes). Sample of 70 recordings per folder or effect (1208 recordings): 6178 tests, 4711 OK, 149 KO, 1318 not run. Full figures: `benchmark/dataset-benchmark-2026-10-05.txt`.
- Notes: 99.8 % recognised on the effects set, 100 % on the clean guitars; 0 wrong note (semitone or whole tone away) accepted in 2 300+ tests.
- Intervals and triads with effects: 87 % recognised; chords of IDMT-SMT-Guitar 94.6 %.
- Incomplete chords refused: 96-98 % (the completeness rule: every separable note of a chord must show growth, minBalance 10 % shared among the notes; octaves and twelfths are not compared). Before: 33 %.
- Weak points: chords a whole tone higher accepted 15 % of the time (all with the lowest note G2: a whole tone is about one 4096-FFT bin there); dominant and minor seventh chords (4 notes) 8-50 % recognised; open A major and E major 55-73 %; the reference recording loses 3 chords with a quiet upper note (events 8, 13, 32) to this completeness rule.
- Not run (reasons): octave tests (not separable by audio, FR-004), subset-of-played chords (tolerated by design), notes not playable on six strings or outside E2-E6, and everything of the datasets not integrated yet (IDMT dataset 2-4 multi-note pieces, GuitarSet, bass, the second guitar series).

## R10 — Complete versus incomplete chords on an independent dataset (spec 004 T007, 2026-10-06)

**Data**: GuitarSet 1.0.1 (Xi, Bittner, Pauwels, Ye, Bello, ISMIR 2018; Zenodo 10.5281/zenodo.1422265; licence **CC BY-NC 4.0**, read from the official record), the 180 "comp" recordings: six hexaphonic string channels (`audio_hex-pickup_debleeded`), the microphone (`audio_mic`) and the per-string note annotations (JAMS). Kept outside the repository; only counts and file names are published (`specs/004-attack-difference-recognition/benchmark/guitarset-chords-2026-10-06.txt`). Provenance manifest: `desktop/TuxGuitar-practice/test-data/datasets/README.md`. Run: `GuitarSetChordBenchmarkTest` (tag `dataset`, not in the default suite), revision `797eec4fb`.

**Method**. A strummed chord is found in the annotation: at least 3 strings struck within 120 ms. It is kept only if the annotation can be trusted; the others are excluded with a reason, never silently: 6,383 strikes had another strike within 0.4 s (comping patterns are dense), 875 had a note shorter than 0.2 s, 770 contained a note outside the instructed chord (annotation disagreement), 73 had a previous strike within 0.15 s, 13 had two strings on the same pitch, 5 had a silent string channel at its annotated note. 702 chords remain (3 to 6 notes: major, minor, dominant seventh and half-diminished). The excerpt runs from 0.35 s before the strum (the previous strike still rings) to 0.40 s after it. Practice, expecting the chord, hears: the sum of the six channels (positive), the same sum with one string removed, one string at a time (a chord with a note missing: 1,937 cases where that note can be told apart from the others by audio, 640 where it cannot: an octave or a harmonic of a lower note, FR-004; reported apart, not counted), and the microphone (positive).

**Result with the default settings** (revision `797eec4fb`):

| Test | Result |
|---|---|
| complete chord, six channels summed, accepted | **418 / 702 (59.5 %)**, delay median 97 ms, p90 172 ms, max 358 ms; 15 accepted before the strum |
| complete chord, microphone, accepted | **326 / 702 (46.4 %)**, delay median 126 ms, p90 230 ms, max 376 ms; 14 accepted before the strum |
| incomplete chord (one separately verifiable note removed), refused | **1,682 / 1,937 (86.8 %); 255 false acceptances (13.2 %)** |
| false acceptances by position of the missing note | bass 201 / 702 (28.6 %), inner 42 / 876 (4.8 %), top 12 / 359 (3.3 %) |
| by chord family | seven 20 / 175, half-diminished 0 / 144, major 145 / 1,196, minor 90 / 422 |
| by style | Rock 23.1 %, SS 12.6 %, Funk 6.8 %, Jazz 6.6 %, BN 5.2 % |
| complete chords, none of their notes ringing / some ringing (six channels) | 61.5 % (353 / 574) / 50.8 % (65 / 128) |

These figures are much below the reference recording (35 of 38 judged recognised, 2 of 12 incomplete chords accepted), and the two recordings do not ask the same thing: GuitarSet comping is a dense rhythmic pattern in which strings are struck again while they ring, the reference recording is a slow exercise. The result is a measurement of the software on independent real material, not a verdict on the reference.

**Where the limit is**. Switching off the criteria one by one on the 702 complete chords: with all of them off 94.3 % are accepted; the balance criterion (the weakest separable note must reach a share of the strongest) costs 24 points (84.3 % → 60 %), the peak ratio 6, the share none. The same criterion is what refuses incomplete chords (85 % → 13.6 % of them accepted). **It is a trade-off, not a tuning slip** (before the correction below, 180 recordings, share 0.30, peak ratio 0.7, 6 hops):

| Setting | complete accepted (six channels / microphone) | incomplete accepted (all / bass missing) |
|---|---|---|
| balance 0.05 | 60.5 % / 46.7 % | 14.7 % / 31.3 % |
| **balance 0.10 (default)** | 59.8 % / 46.4 % | 13.6 % / 29.5 % |
| balance 0.25 | 56.7 % / 45.2 % | 12.1 % / 25.9 % |
| balance 0.50 | 53.3 % / 42.7 % | 9.9 % / 20.7 % |
| balance 1.00 | 46.4 % / 39.2 % | 6.2 % / 11.8 % |
| balance off | 84.3 % / 72.1 % | 85.2 % / 87.7 % |
| all criteria off | 94.3 % / 91.9 % | 90.7 % / 94.4 % |
| neighbour ratio 1.5 + balance 0.10 | 51.4 % / 41.3 % | 10.5 % / 22.4 % |
| neighbour ratio 3 + balance 0.10 | 45.2 % / 36.8 % | 6.8 % / 12.7 % |

The neighbour ratio (the note must dominate its semitone and whole-tone neighbours), already in the code and off by default, moves along the same curve. A different measure of the same evidence does not separate better either: the increase of the NNLS residual when a note is left out ("what this note alone explains") has an area under the ROC curve of 0.69 for complete versus one-note-removed chords, against 0.68 for the balance used today (60 recordings, 203 complete chords, 558 chords with a note removed).

**One correction made** (found by a unit test, `IncompleteChordTest`): the balance of a chord was read against the strongest of its separable notes only, so when every separable note carried nothing but leakage they were all equally weak and passed as balanced (a chord heard as a single octave note was accepted). It is now read against the strongest of all the expected notes. Measured effect: 13.6 % → 13.2 % false acceptances, 59.8 % → 59.5 % complete accepted; the reference recording (35 / 38, 0 / 98, 2 / 12, 105 ms / 169 ms) and the IDMT benchmark are unchanged.

**Decision of the owner (2026-10-06): option 2 is refused (losing almost half of the complete chords is not acceptable); the current setting and its documented figures are kept provisionally; T007 stays open; the target is another method able to tell a complete chord from one with a string missing without strongly degrading the complete chords (option 3).** Options that were put to the owner: No threshold of the current evidence reaches both "complete chords recognised" and "incomplete chords refused": the default favours accepting a complete chord (and lets 29 % of the chords without their bass through), a stricter balance refuses more incomplete chords and also more complete ones. Options: (1) keep the default and publish these figures as the measured limits; (2) choose a stricter setting for the practice mode (for example balance 0.50: 53 % / 10 % on this dataset), accepting that more correct chords wait; (3) research another way to tell a missing string (for example per-string onset evidence), which is a new method, not a threshold. The behaviour contract (FR-003, SC-001) is not met either way until (3).

## R11 — The closest notes Practice can follow (spec 004 T010, 2026-10-06)

**Data and method**: IDMT-SMT-Guitar V2 dataset 1 (CC BY-NC-ND 4.0, outside the repository), 96 triples of plain single notes from 4 guitars, mixed one after the other so that the onsets (read from the annotation) are one spacing apart, each note ringing while the next is struck; Practice expects the three in order. `NoteSpacingBenchmarkTest`, revision `f1460f051` (`specs/004-attack-difference-recognition/benchmark/note-spacing-2026-10-06.txt`).

**Result with the default settings** (re-arm of the acceptance rule 220 ms):

| Spacing | all 3 notes followed in order | notes recognised | wrong third note accepted | delay (median / p90 / max) |
|---|---|---|---|---|
| 50 ms | 0 % | 4.9 % | 0 / 92 | 107 / 111 / 307 ms |
| 100 ms | 0 % | 33.0 % | 0 / 92 | 107 / 113 / 355 ms |
| 150 ms | 0 % | 35.8 % | 0 / 92 | 107 / 113 / 160 ms |
| 200 ms | 1.0 % | 49.0 % | 0 / 92 | 111 / 159 / 209 ms |
| 250 ms | 66.7 % | 81.9 % | 0 / 92 | 157 / 171 / 206 ms |
| 350 ms | 90.6 % | 94.4 % | 0 / 92 | 109 / 113 / 210 ms |

So the real limit of the default is **about 0.3 s between picked notes**, not the provisional 0.15 s: below 0.2 s only the first note of a run is recognised (a third of the notes), the others wait. **At no spacing is a wrong note accepted** (0 of 92): the limit makes Practice wait, never wrong, as the specification requires. Cause: after each acceptance the evidence of that attack is ignored for 220 ms (`PolyphonicAcceptance.Parameters.rearmNanos`) so that one pluck cannot validate two notes; the acceptance itself comes about 107 ms after the onset.

**What a shorter re-arm would change** (same data; not applied, a setting of the acceptance rule):

| Re-arm | all 3 followed at 125 / 150 / 200 / 250 ms | a single pluck accepted as two notes (240 clips): same pitch / octave above / fifth / semitone |
|---|---|---|
| 220 ms (default) | 0 / 0 / 1 / 67 % | 0 / 7 / 0 / 0 |
| 160 ms | 0 / 10 / 74 / 86 % | 1 / 7 / 0 / 0 |
| 120 ms | 51 / 74 / 83 / 89 % | 1 / 7 / 0 / 0 |
| 90 ms | 60 / 88 / 86 / 90 % | 1 / 7 / 0 / 0 |

(Wrong third note accepted: 0 of 92 at every re-arm and every spacing.) A shorter re-arm follows faster playing but lets one clip in 240 count a repeated pitch twice; the octave case (7 of 240 at every re-arm, including the default) is the known FR-004 limit: an octave cannot be told from a partial of the note just played.

**The reference recording**: the four events that are less than 0.15 s from a neighbour (events 4 to 7: B3, C4, B3, A3, spaced 108, 108 and 91 ms) are the written hammer-on / pull-off run; two of them (events 5 and 6) have no pick attack in the recording at all (their times are tempo-fit, not attacks). The attack-difference method cannot see a note that has no attack, whatever the re-arm; legato is the subject of the technique detectors (spec 002, Phase 7). **42 of 42 is therefore not reachable on this recording by this method**, and SC-001 is not changed here.

**Decision of the owner (2026-10-06): the limit of about 0.3 s between picked notes is validated as a provisional measured limit, not as a final goal; the re-arm is not reduced globally until the risk of counting one attack twice is solved; hammer-ons and pull-offs without a new attack belong to the technique-recognition phase.** The statement shown to the learner is: "about 0.3 s between picked notes (about 3 notes per second); faster runs are not followed, Practice waits and never marks them wrong". Optional, to be decided separately: a shorter re-arm of 120 to 160 ms (the limit would become about 0.15 to 0.2 s) at the price of 1 in 240 repeated pitches counted twice.
