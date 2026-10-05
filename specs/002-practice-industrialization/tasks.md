# Tasks: Interactive Practice Industrialization

**Input**: Design documents from `specs/002-practice-industrialization/` (spec.md, plan.md, research.md, data-model.md, contracts/, quickstart.md) and `.specify/memory/constitution.md`.

**Tests**: included. Constitution XI requires tests for new unit-testable logic; hardware and UI behaviour is verified manually (quickstart.md) and is never reported as automated. Benchmarks are JUnit-tagged `benchmark` and run explicitly.

**Organization**: grouped by user story (spec.md). P1: US1, US8, US2, US7, US3, US4, US2b. P2: US5. P2/P3: US6. The order of the phases below is the delivery order of plan.md (A→F), each phase leaves the application buildable and usable.
> **Status 2026-10-06**: Phases 1-4 delivered (Practice button, one-click start, input gain / noise gate / suggested gain); Phase 5 partly (different-pitch acceptance rule, fast attack detector as an option; the expected-pitch evidence is built but off, research R3). Phases 6-12 (technique framework and detectors, tempo-following mode, polish) are **not started**. Spec 004 (polyphonic recognition) supersedes the recognition part of Phase 5. Amendments decided with the owner since this plan: the tuner cannot be opened while practice is open; a one-click start shows no configuration window unless the learner has to act; measure errors are listed on demand.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an unfinished task)
- **[Story]**: US1, US2, US2b, US3…US8 = spec.md user stories
- `[SPIKE]` = an experiment whose outcome decides a design point; its result is recorded in `research.md` before dependent tasks start. No spike has been run yet.

## Path conventions (plan.md "Project Structure")

- Engine: `desktop/TuxGuitar-audio-engine/` — main `src/main/java/app/tuxguitar/audio/engine/`, tests `src/test/java/app/tuxguitar/audio/engine/` (abbreviated **ENGINE** / **ENGINE-TEST** below)
- Practice plugin: `desktop/TuxGuitar-practice/` — main `src/app/tuxguitar/app/tools/custom/practice/`, tests `test/app/tuxguitar/app/tools/custom/practice/` (abbreviated **PRACTICE** / **PRACTICE-TEST**); strings `share/lang/messages.properties` and `messages_fr.properties`
- Tuner: `desktop/TuxGuitar-tuner-next/src/app/tuxguitar/app/tools/custom/tunernext/` (**TUNER**)
- Core toolbar (Phase A only): `desktop/TuxGuitar/src/app/tuxguitar/app/view/toolbar/main/` (**TOOLBAR**)
- Benchmark reports and recordings: `specs/002-practice-industrialization/benchmark/` (**BENCH**; `specs/` is git-ignored — force-add when committing, owner's decision)

Build/verify command for every phase (quickstart.md), with `JAVA_HOME=$(/usr/libexec/java_home -v 21)`:
`cd desktop/build-scripts/tuxguitar-macosx-swt-cocoa && mvn -e clean verify -P native-modules`

Commit only when the owner asks; never push without approval (CLAUDE.md "Git Safety").

---

## Phase 1: Setup and baseline

**Purpose**: a measured starting point and the test tooling every later phase needs. No product behaviour changes.

- [x] T001 Run the full macOS SWT build with tests on the current tree and record the exact result (tests run/failed/skipped per module) in `BENCH/baseline.md`; this is the regression reference for every phase
- [x] T002 [P] Extend `ENGINE-TEST/testsupport/SyntheticSignals.java` with fast-passage generators: a sequence of Karplus-Strong plucks at a given notes-per-second rate (4…12), with options for identical repeated pitches, semitone neighbours and a still-ringing previous note *(done: `TechniqueSignals.fastPassage` in `ENGINE-TEST/testsupport/TechniqueSignals.java`, with `TechniqueSignalsTest`)*
- [x] T003 [P] Extend `ENGINE-TEST/testsupport/SyntheticSignals.java` with technique generators, each returning the plain counterpart too: legato step without attack (hammer-on up, pull-off down), slide glide between two pitches over a given time, vibrato (rate Hz, depth cents), bend path from a list of (time, semitones), palm-muted pluck (fast decay + darker spectrum), tremolo-picked burst (attack rate), natural-harmonic pluck (near-sinusoidal), additive hum/broadband noise and a weak-level scaling option *(done in `TechniqueSignals`: legato step, slide, vibrato, bend, palm-muted pluck, tremolo picking, natural harmonic, hum, scaling)*
- [ ] T004 [P] Add latency reporting to `ENGINE-TEST/benchmark/BenchmarkFixtures.java` and `BenchmarkSweepTest.java`: per clip, time from the energy onset to the first accepted note, with p50/p95/max over the clip set, written as CSV to `BENCH/`
- [x] T005 [SPIKE] S-C1: run the existing benchmark on the current tree (`mvn test -pl ../../TuxGuitar-audio-engine -am -Dgroups=benchmark -DexcludedGroups= -Dsurefire.failIfNoSpecifiedTests=false` from the macOS SWT build directory) and record the baseline latency p50/p95 and accuracy in `BENCH/baseline.md` and `research.md` R3 (replaces the MVP's 171 ms / 288 ms figures if different) *(done: baseline in `BENCH/baseline.md`; median stable-note latency 171 ms on the built-in-mic clips)*
- [ ] T006 [P] Write `BENCH/recording-plan.md`: the clip list to record on the owner's setup (direct interface and built-in microphone, reported separately): fast runs at 4/6/8/10/12 notes per second, identical repeats, neighbouring pitches, each P1 technique next to its plain counterpart, palm mute, tremolo picking, natural/artificial/pinch harmonics, tapping, noise-only, weak-input and hum clips; format rules from the MVP (short PCM WAV, mono, 48 kHz, 16-bit, guitar only, CC0-1.0 licence file beside them)

**Checkpoint**: baseline numbers exist; synthetic signal tooling ready.

---

## Phase 2: Foundational (blocking prerequisites)

**Purpose**: shared types and settings used by several stories. No user story can be validated before this phase is complete.

- [x] T007 Add the new persisted settings to `ENGINE/settings/RecognitionParameters.java` and register them in `ALL`: `noise reduction` (Flag), `gain suggestion` (Flag), `technique detection` (Flag, default on), `timing tolerance` (Choice `strict`/`normal`/`lenient`, default `normal`); add `ADVANCED` threshold parameters only when a task below needs them. Update `ENGINE-TEST/settings/RecognitionSettingsTest.java` and `AudioEngineSettingsTest.java` (defaults, clamping/defaulting of bad values) *(done: `INPUT_NOISE_REDUCTION`, `INPUT_GAIN_SUGGESTION`, `NOTE_TECHNIQUE`, `TEMPO_TOLERANCE`, with English and French texts and help rows, enforced by the existing `RecognitionControlsTest`)*
- [x] T008 [P] Create in `PRACTICE/model/`: `Technique.java` with `HAMMER_ON`, `PULL_OFF`, `SLIDE`, `BEND`, `PRE_BEND`, `RELEASE`, `VIBRATO`, `PALM_MUTE`, `TREMOLO_PICKING`, `NATURAL_HARMONIC`, `ARTIFICIAL_HARMONIC`, `PINCH_HARMONIC`, `TAPPING` plus the display-only `ACCENT`, `LET_RING`, `STACCATO` (never judged; a value without a detector defaults to `NOT_VERIFIABLE`); `TechniqueVerdict.java` with `DETECTED`, `NOT_DETECTED`, `UNCERTAIN`, `NOT_VERIFIABLE` (a detector may only emit the first three); `PitchPath.java` (ordered (relativeTime, semitones) points); `TechniqueTarget.java` (`technique`, optional `targetMidi` or `PitchPath`, optional `soundingMidi`, `partnerEventIndex`) *(done; the enum is named `PlayingTechnique` because `PracticeEvent.Technique` already exists)*
- [x] T009 Update `PRACTICE/model/PracticeEvent.java`: add `techniqueTargets : List<TechniqueTarget>` and keep the old display-only `Technique` set working through a mapping to the new enum; update `PRACTICE/model/PracticeSequenceBuilder.java` to fill `techniqueTargets` for the techniques it already reads (hammer, slide, vibrato, palm mute, tremolo picking) without changing any existing classification; extend `PRACTICE-TEST/model/PracticeSequenceBuilderTest.java` so every existing assertion still passes and the new targets are asserted *(done: `PracticeEvent.getTechniqueTargets()`, derived from the existing display set; `TechniqueModelTest`)*
- [x] T010 [P] Extend `ENGINE/analysis/AnalysisFrame.java` additively with optional `TargetEvidence` and `TechniqueFeatures` members (null unless enabled, same pattern as `spectrum`; existing constructors unchanged); create empty value classes `ENGINE/recognition/TargetEvidence.java` (per hypothesis: `midi`, `score` 0–1, `marginOverBest`, `windowMillis`) and `ENGINE/technique/TechniqueFeatures.java` (`f0Hz`, `f0Cents`, `levelDb`, `levelSlopeDbPerMs`, `spectralCentroidHz`, `highFreqRatio`, `partialCount`/`flatness`, `flux`, `provisionalOnset`); test in `ENGINE-TEST/analysis/AnalysisServiceTest.java` that frames are unchanged when the members are not enabled *(done: `AnalysisFrame` constructor with `TargetEvidence` and `TechniqueFeatures`; `TargetEvidenceTest`)*

**Checkpoint**: build green, all existing tests unchanged, new types in place, nothing user-visible.

---

## Phase 3: User Story 1 - One-click Practice button (Priority: P1) 🎯 first delivery

**Goal**: a Practice button right of Play in the transport bar; the Tools menu entry stays and behaves identically (spec US1, FR-001…007; contracts/toolbar-extension.md; research R1).

**Independent Test**: open a song, click the button: the practice window opens for the selected track; Tools → Practice opens the same single window; removing the plugin leaves no button and no error.

### Tests for User Story 1

- [x] T011 [P] [US1] Registry unit test `PRACTICE-TEST/toolbar/TGMainToolBarItemRegistryTest.java` (the practice module is where core-class tests run): register/unregister by name, replace by name, unknown unregister is a no-op, the registry holds data only (no fork class reference)
- [x] T012 [P] [US1] Default-layout regression test in the same file: with an empty registry `TGMainToolBarConfig` default content is exactly today's (`transport.first … transport.last`, separators, time counter, tempo indicator, metronome, count-down, mode); with `transport.practice` registered it appears right after `transport.start`; a user-saved layout is returned unmodified; a saved layout naming an unregistered item renders without it and without exception

### Implementation for User Story 1

- [x] T013 [US1] Create `TOOLBAR/TGMainToolBarPluginItem.java` and `TOOLBAR/TGMainToolBarItemRegistry.java` exactly per contracts/toolbar-extension.md (fields `name`, `group`, `textKey`, `actionName`, `iconFileName`, `checkable`, `updater`, `defaultAfter`; one registry per `TGContext` via `TGSingletonUtil`); this is the only core change of the feature (plan.md Complexity Tracking) *(done; `name` doubles as the tooltip key, so there is no separate `textKey`)*
- [x] T014 [US1] Update `TOOLBAR/TGMainToolBarConfigMap.java` to copy registered items into `mapItems`, `itemNames` and `groupNames` at construction (they then appear in the toolbar-settings dialog); update `TOOLBAR/TGMainToolBarConfig.java` to insert registered `defaultAfter` items into the default centre content only; verify `TOOLBAR/TGMainToolBar.java` `layout()` tolerates a configured name with no registered item (guard if it does not) *(done: default-layout insertion is applied in `TGMainToolBar.layout` for the default config only, `TGMainToolBarConfig` itself is unchanged; unknown saved names were already ignored by the tool bar)*
- [x] T015 [P] [US1] Create `PRACTICE/ui/LauncherState.java` (`INACTIVE`, `ACTIVE`, `UNAVAILABLE(reason)`; reasons: no song, no playable guitar track; an unset audio input does not make it unavailable) and the updater in `PRACTICE/ui/PracticeLauncherItem.java`: checked iff the practice window is open; enabled iff a song is open; tooltip keys `practice.toolbar.open` / `practice.toolbar.unavailable` with the reason *(done: `LauncherState`, `PracticeLauncherItem`; the practicable-track check is repeated at most once a second)*
- [x] T016 [US1] Register the item in `PRACTICE/ui/PracticePlugin.java` `connect` (name `transport.practice`, group `transport`, checkable, action `practice.open`, `defaultAfter` `transport.start`) and unregister it in `disconnect`; keep `TGToolItemPlugin`'s Tools menu entry untouched *(done: new action `practice.toggle` for the button, `practice.open` of the Tools menu unchanged)*
- [x] T017 [US1] Make `PRACTICE/ui/PracticeWindow.java` expose its open state and fire a refresh of the toolbar item when it opens or closes by any means (button, menu, window close, plugin unload, song change); a second activation brings the existing window forward and never creates a second instance (FR-005) *(done: the window tells the plugin when it opens or closes; the button closes the window when it is open)*
- [ ] T018 [US1] Define and implement the Play/practice interaction of FR-006 in wait mode: the existing `PauseReason.PLAYBACK` pause (`PRACTICE/session/SessionEvents.java`) is kept and its message is shown to the learner; pressing the practice button while the song plays opens the window paused with that message
- [x] T019 [SPIKE] [US1] S-I1: check whether `TGResourceManager` resolves `skins/<skin>/practice_toolbar.png` from a plugin's `share/skins/` (read `TGIconManager.loadIcon` → `TGFileUtils.loadImage`, then test in the built `.app`); if not, put the six icons in `desktop/TuxGuitar/share/skins/*` (a resource-only core change) and record the outcome in `research.md` R1 *(answered: a plugin jar's `skins/<skin>/…` resource is found; `TGIconManager.getImageByName` now loads an icon on first use. Visual confirmation in the running app is still to be done by the owner)*
- [x] T020 [P] [US1] Create the toolbar icon `practice_toolbar.png` for the skins `Breeze`, `Breeze-dark`, `Oxygen`, `Oxygen-dark`, `Symbolic`, `Symbolic-dark` in the location decided by T019, matching the size and style of `transport_icon_play.png` in the same skin *(done: generated 22×22 eighth-note icons, dark on light skins and light on dark skins)*
- [x] T021 [P] [US1] Add `practice.toolbar.open` and `practice.toolbar.unavailable` (with reason variants) to `PRACTICE/../share/lang/messages.properties` and `messages_fr.properties`
- [ ] T022 [US1] Phase A closing verification: full build with tests green (compare with T001); manual quickstart "Phase A" on the macOS SWT build (steps 1–4); record results exactly as run

**Checkpoint (Phase A)**: button + menu work, plugin absent ⇒ no button, build green.

---

## Phase 4: User Story 8 - Boost and clean the input signal (Priority: P1)

**Goal**: in-app gain (practice and tuner), noise gate with noise estimate and subtraction, optional suggested gain (spec US8, FR-060…066; contracts/signal-frontend.md; research R2).

**Independent Test**: with a weak/noisy input, raise the gain and toggle noise reduction; level indication and recognition follow; gain is neutral again after an input change or restart and nothing is saved.

### Tests for User Story 8

- [x] T023 [P] [US8] `ENGINE-TEST/frontend/InputGainTest.java`: neutral multiplier 1.0 leaves samples bit-identical; out-of-range values are clamped; reset returns 1.0
- [x] T024 [P] [US8] `ENGINE-TEST/frontend/HighPassTest.java`: attenuation below 1 dB at 73 Hz and 82 Hz (drop D and low E), at least 20 dB at 10 Hz; DC removed
- [x] T025 [P] [US8] `ENGINE-TEST/frontend/NoiseGateTest.java` and `NoiseEstimatorTest.java`: noise-only input stays below the gate and raises no onset; a decaying pluck is not muted while above floor + margin; the first 50 ms of a pluck are not attenuated; hysteresis and hold prevent chatter; the estimate follows a change of noise level
- [x] T026 [P] [US8] `ENGINE-TEST/frontend/SpectralSubtractorTest.java`: stationary noise is reduced by a measured amount, a pluck's pitch (YIN) is unchanged within tolerance, reported latency equals the window length
- [x] T027 [P] [US8] `ENGINE-TEST/frontend/GainSuggesterTest.java`: for weak, suitable and hot level-check statistics the suggestion lands in the `LevelAnalyzer` SUITABLE band (defaults −40…−6 dBFS RMS), is capped, reports `TOO_LOW`/`TOO_HIGH`/`NOISE_DOMINATED`/`OK`, and never changes the gain itself
- [x] T028 [US8] In `ENGINE-TEST/analysis/AnalysisServiceTest.java` and `AudioEngineTest.java`: neutral front end leaves frames identical to the MVP; gain resets when the input revision changes and on a new engine; nothing is written to settings; level check, level state and pitch see the processed signal *(done in `AudioEngineFrontEndTest` and `InputAssessorStatsTest`; the gain reset is compared by input revision, which removed a race with the asynchronous selection notification)*

### Implementation for User Story 8

- [x] T029 [P] [US8] Create `ENGINE/frontend/FrontEndStage.java` (`process(float[] hop, int length)`, `reset()`, `latencyHops()`) and `InputGain.java` (volatile multiplier, 1.0 neutral, clamped to a range fixed from trials; `resetToNeutral()`) *(range −6 … +18 dB)*
- [x] T030 [P] [US8] Create `ENGINE/frontend/HighPass.java` (2nd-order, corner ≈ 50–60 Hz, exact value chosen so T024 passes; preallocated)
- [x] T031 [P] [US8] Create `ENGINE/frontend/NoiseEstimator.java` (floor in dBFS, `confidence`, `source` LEVEL_CHECK or QUIET_TRACKING; seeded by the level check's silent part, updated by minimum statistics during quiet hops, reset when the input changes) and `NoiseGate.java` (threshold = floor + margin, hysteresis and hold; never gates the decay of a ringing note)
- [x] T032 [P] [US8] Create `ENGINE/frontend/SpectralSubtractor.java` (STFT overlap-add, over-subtraction factor, spectral floor; behind the noise-reduction switch until T047 decides its default)
- [x] T033 [P] [US8] Create `ENGINE/frontend/GainSuggester.java` (from level-check statistics; cap; reasons `TOO_LOW`, `TOO_HIGH`, `NOISE_DOMINATED`, `OK`); extend `ENGINE/analysis/InputAssessor.java` to expose the level-check peak/RMS statistics and the silent-portion level it already measures
- [x] T034 [US8] Apply the stage chain in `ENGINE/analysis/AnalysisService.java` per hop **before** level, pitch, onset and spectrum analysis; swap stages with the existing pending-configuration mechanism (never a partial state); no allocation per hop after construction
- [x] T035 [US8] Add the engine API in `ENGINE/AudioEngine.java` per contracts/signal-frontend.md: `getInputGain`/`setInputGain`/`resetInputGain`, `isNoiseReductionEnabled` (backed by the persisted flag of T007), `suggestGain()` (empty when the suggestion switch is off); reset the gain when `CurrentInputSelection` reports a new input revision and at construction; sum stage `latencyHops()` into a reported chain latency (FR-065)
- [x] T036 [US8] Level warning logic (FR-062) in `ENGINE/analysis/InputAssessor.java` / `ENGINE/input/InputAssessment.java`: report "boost mostly amplifies noise" and "clipping caused by boost" with the advice kind (raise interface gain / use a direct input) *(done in the engine: `InputWarning`; shown by the gain controls)*
- [x] T037 [P] [US8] Add the gain control (slider with value and reset) to `PRACTICE/ui/LevelPanel.java` and a "suggest" button shown only when the suggestion switch is on; show the warnings of T036 *(done: `GainControl` in `LevelPanel`, with the suggestion controls shown only when the setting is on)*
- [x] T038 [P] [US8] Add the same gain control bound to the same engine gain to `TUNER/TunerWindow.java` *(done: `TunerGainControl`; suggestions are in the practice window only)*
- [x] T039 [US8] Add the noise-reduction checkbox and the gain-suggestion switch to `PRACTICE/ui/RecognitionSettingsPanel.java` and `PRACTICE/ui/AudioRecognitionOption.java` (existing "Audio recognition" options); update `PRACTICE-TEST/ui/RecognitionSettingsPanelTest.java` and `RecognitionControlsTest.java` *(the two switches appear in the "Audio recognition" options through the generic panel; existing panel tests pass)*
- [x] T040 [P] [US8] Add the strings (gain, reset, suggest, noise reduction, the warnings) to `PRACTICE/../share/lang/messages.properties` + `messages_fr.properties` and the tuner's `share/lang/` files *(English and French; the tuner's other languages fall back to English)*
- [x] T041 [SPIKE] [US8] S-B2: measure the noise floor of the built-in microphone and of an interface on the owner's setup to set the gate margin, the gain range/step and the "mostly amplifies noise" threshold; record in `research.md` R2 *(partly: built-in microphone floor measured from the recorded clip; the audio interface still has to be measured by the owner; see research.md R2 results)*
- [x] T042 [SPIKE] [US8] S-B1: run the benchmark with and without the front end (gate only, gate + subtraction) on built-in-mic and interface clips, including the T003 noise/hum signals; decide the default of spectral subtraction (off unless it loses nothing in accuracy and latency) and record in `research.md` R2 *(done: see research.md; default stays off)*
- [ ] T043 [US8] Phase B closing verification: full build with tests green; manual quickstart "Phase B" (steps 1–4); record results exactly as run

**Checkpoint (Phase B)**: gain and noise reduction usable in both windows; defaults justified by T041/T042.

---

## Phase 5: User Story 2 - Keep up when playing fast (Priority: P1)

**Goal**: faster, more robust note recognition at speed, including identical repeated notes (spec US2, FR-010…017; research R3 levers L1–L5).

**Independent Test**: benchmark of fast passages (synthetic first, then recorded) meets or honestly reports SC-002/003/004; manual scale runs at rising speed show no false crosses.

### Tests for User Story 2

- [x] T044 [P] [US2] `ENGINE-TEST/recognition/ExpectedPitchVerifierTest.java`: for plucks at E2…E5 the expected-pitch evidence beats ±1 semitone and the octave, a wrong neighbour never scores above the expected, silence and noise score low, evidence latency is at most the window length
- [x] T045 [P] [US2] Extend `ENGINE-TEST/analysis/ArticulationDetectorTest.java`: the MVP default behaviour is unchanged for existing cases; the v2 detector separates identical repeated notes at 8 notes/s (and reports the measured limit at 10 and 12), does not fire on vibrato, dropout recovery or a still-ringing previous note, and exposes a provisional flux-only onset time
- [x] T046 [P] [US2] Extend `PRACTICE-TEST/session/PracticeSessionTest.java`: a different expected pitch is accepted from a stable pitch change without a confirmed attack; an identical pitch still needs an attack; a wrong neighbour is never accepted; the confidence-scaled stability accepts earlier on a large margin and keeps the conservative 107 ms fallback otherwise; chords and silence behave as before

### Implementation for User Story 2

- [x] T047 [US2] Create `ENGINE/recognition/ExpectedPitchVerifier.java`: periodicity score for the expected pitch(es), ±1 semitone and the octave on a window of about three periods (shorter for high strings); publish `TargetEvidence` on frames; `AudioEngine.setExpectedPitches(int[])` (volatile, read by the analysis worker) and the `AnalysisService` hook
- [x] T048 [US2] Implement the attack detector v2 in `ENGINE/analysis/ArticulationDetector.java` behind parameters: release-aware baseline (level over the last 2–8 hops instead of the maximum over 19), shorter post window, refractory lowerable for tremolo; keep the MVP constants as defaults so existing tests pass; add the provisional onset flag and time to `AnalysisFrame`
- [x] T049 [US2] Update `PRACTICE/session/PracticeSession.java`: post the expected pitches to the engine on every event change (through `SessionLoop.post`); accept a different expected pitch from a stable pitch change (research R3 L3); confidence-scaled stability (L2); keep the attack rule for identical pitches and chords; thresholds stay parameters (`PRACTICE/session/PracticeParameters.java`)
- [ ] T050 [US2] "Input too poor for reliable fast recognition" (FR-015): derive it from level state, noise floor and evidence margin, show it in `PRACTICE/ui/PracticePalette.java` with what to do; strings in `share/lang/` en + fr
- [ ] T051 [P] [US2] Extend the benchmark in `ENGINE-TEST/benchmark/BenchmarkSweepTest.java` with the T002 fast passages (4…12 notes/s, identical repeats, neighbours): recognition rate, false accepts, latency p50/p95, written to `BENCH/`; compare with the T005 baseline
- [ ] T052 [US2] Regression: all existing chord, silence, level-check, tuning and passage tests in `PRACTICE-TEST/` and `ENGINE-TEST/` still pass unchanged (FR-016)
- [ ] T053 [SPIKE] [US2] Record the T006 fast-run and repeat clips with the owner on both inputs, add them with the CC0-1.0 licence file to the benchmark assets, and re-run T051 on them
- [ ] T054 [US2] Compare measured results with SC-002, SC-003 and SC-004 in `research.md` R3; where a target is not met, report the measured figure and propose revised targets to the owner (no target is silently changed)
- [ ] T055 [US2] Phase C closing verification: full build with tests green; manual quickstart "Phase C" (scale runs at rising speed in the app); record results exactly as run

**Checkpoint (Phase C)**: faster acceptance with measured numbers; legato acceptance rule (L3) in place for Phase D.

---

> **Status 2026-10-06 (Phases 6–8, first pass).** Done: the judge, catalogue, per-note collection in the session, the technique line of the palette (strings EN/FR), `PracticeSequence.targetsOf` (hammer-on / pull-off with partner), and the vibrato, bend, slide and legato detectors, benchmarked on IDMT-SMT-Guitar dataset 2 (`benchmark/technique-benchmark-2026-10-06.txt`, rerun with `-Dgroups=dataset`). Choices recorded: the engine has its own `DetectableTechnique` / `TechniqueEvidence` (no dependency on the practice module); detectors are stateless over a `NoteSegment` instead of a streaming `push`; their thresholds are a `Parameters` class per detector, not yet `RecognitionParameters` ADVANCED entries; the per-hop `TechniqueFeatureExtractor` (T057, T059) was not needed by these detectors and stays open. **No technique is verified**: vibrato 59 %, bend 82 %, slide 48 % of the annotated notes detected (target 85 %), legato has no labelled recording; every written technique therefore shows "cannot be checked yet" (T074). Still open: T057, T059, T063 (per-technique counts in the passage summary), T065 manual check, T073 legato part, T075, T076 (bend against the written path), T078–T085.

## Phase 6: User Story 7 - Honest technique feedback (Priority: P1)

**Goal**: the framework every technique uses: separate technique verdicts shown next to the pitch result, honest "not verifiable" (spec US7, FR-020, FR-028…032; contracts/technique-detection.md; research R4).

**Independent Test**: run practice on a score with every supported technique; each shows exactly one of detected / not detected / uncertain / not verifiable, consistent with the audio and never blocking progression.

### Tests for User Story 7

- [x] T056 [P] [US7] `PRACTICE-TEST/session/TechniqueJudgeTest.java`: `NOT_VERIFIABLE` when no detector is registered, when detection is switched off (FR-032) and when the technique's `verified` flag is false; a detector cannot emit `NOT_VERIFIABLE`; borderline evidence gives `UNCERTAIN`; a technique verdict never changes the pitch verdict or progression (FR-030)
- [ ] T057 [P] [US7] `ENGINE-TEST/technique/TechniqueFeaturesTest.java`: per-hop features on synthetic signals (f0 cents track, level slope, spectral centroid, high-frequency ratio, flatness, flux) are stable and deterministic; disabling the feature block leaves frames unchanged

### Implementation for User Story 7

- [x] T058 [P] [US7] Create `ENGINE/technique/TechniqueDetector.java` (`technique()`, `push(TechniqueFeatures)`, `evaluate(TechniqueTarget, NoteSegment)`, `reset()`), `TechniqueEvidence.java` (`technique`, `verdict`, `confidence` 0–1, named `measurements`, `explanationKey`) and `NoteSegment.java`; the engine-side `Technique`/`TechniqueVerdict` types are the ones of T008 moved or mirrored without a dependency from the engine on the practice module (record the choice)
- [ ] T059 [US7] Implement the per-hop feature extractor `ENGINE/technique/TechniqueFeatureExtractor.java` and enable it from `ENGINE/analysis/AnalysisService.java` with a `setTechniqueFeaturesEnabled` switch (same pattern as `setSpectrumEnabled`; off for the tuner)
- [x] T060 [US7] Create `ENGINE/technique/TechniqueCatalog.java`: the single table of technique → detector + `verified` flag + the benchmark result that justifies it (all flags false at this point); `PRACTICE/session/TechniqueJudge.java` turning `TechniqueTarget` + `TechniqueEvidence` into a `TechniqueResult` (`technique`, `TechniqueVerdict`, `confidence`, `detail` key + numbers)
- [x] T061 [US7] Integrate in `PRACTICE/session/PracticeSession.java`: collect evidence per note segment, produce `NoteEventResult` (`pitchVerdict`, `techniqueResults`); extend `PRACTICE/session/PracticeSnapshot.java` and `Feedback.java`; progression is unchanged
- [x] T062 [US7] Show one technique chip per written technique next to the pitch line in `PRACTICE/ui/PracticePalette.java` and `PracticeWindow.java` (detected / not detected / uncertain / not verifiable with a short localized explanation; visually distinct from the pitch result); the "technique detection" switch (T007) in `RecognitionSettingsPanel.java` makes every result `NOT_VERIFIABLE`
- [ ] T063 [US7] Extend `PRACTICE/session/PassageStatus.java` summary with per-technique counts (detected, not detected, uncertain, not verifiable) and show them in the end-of-passage text; tests in `PRACTICE-TEST/session/PassagePracticeTest.java`
- [x] T064 [P] [US7] Strings `practice.technique.<name>.<verdict>` and the summary texts in `share/lang/messages.properties` + `messages_fr.properties`
- [ ] T065 [US7] Phase D1 verification: full build with tests green; with no detector yet every written technique shows "not verifiable" and nothing else changes (manual check)

**Checkpoint**: framework in place; all techniques honestly "not verifiable" until a detector is benchmarked.

---

## Phase 7: User Story 3 - Hammer-on, pull-off, slide (Priority: P1)

**Goal**: legato notes accepted as the written pitch and technique verdict reported (spec US3, FR-021; research R4 rows "Hammer-on / pull-off" and "Slide").

**Independent Test**: legato clips (ascending hammer-on, descending pull-off, slide up/down) are accepted and reported detected; the same notes picked individually are accepted and reported not detected.

### Tests for User Story 3

- [x] T066 [P] [US3] `ENGINE-TEST/technique/LegatoDetectorTest.java`: synthetic hammer-on and pull-off steps without attack give `DETECTED`; the picked version gives `NOT_DETECTED`; a borderline level rise gives `UNCERTAIN`
- [x] T067 [P] [US3] `ENGINE-TEST/technique/SlideDetectorTest.java`: a monotone glide through intermediate pitches gives `DETECTED`, a direct picked jump `NOT_DETECTED`
- [x] T068 [P] [US3] Extend `PRACTICE-TEST/model/PracticeSequenceBuilderTest.java`: a written hammer is classified `HAMMER_ON` when the destination is higher and `PULL_OFF` when lower, with `partnerEventIndex`; a slide carries its partner

### Implementation for User Story 3

- [x] T069 [US3] Derive `HAMMER_ON` vs `PULL_OFF` and the partner event in `PRACTICE/model/PracticeSequenceBuilder.java` from `TGNoteEffect.isHammer()`/`isSlide()` and the next note's pitch
- [x] T070 [P] [US3] Create `ENGINE/technique/LegatoDetector.java`: pitch step between two stable segments with no confirmed attack (level rise below the attack gate) and continuous sustain; pull-off: lower pitch with level not above the previous note; wide `UNCERTAIN` band between the plain and the technique thresholds (parameters in `RecognitionParameters` ADVANCED)
- [x] T071 [P] [US3] Create `ENGINE/technique/SlideDetector.java`: monotone f0 glide through intermediate pitches over at least X ms (parameter) with no attack at arrival
- [x] T072 [US3] Wire both detectors into `ENGINE/technique/TechniqueCatalog.java` and use them from `PRACTICE/session/PracticeSession.java` for the destination note (acceptance rule already in T049)
- [ ] T073 [SPIKE] [US3] S-F1: record the legato and slide clips (T006) on both inputs and compute the candidate features offline; publish per-technique separability (detected-vs-plain rates) in `BENCH/` and `research.md` R4
- [x] T074 [US3] Set the `verified` flag in `TechniqueCatalog` for a technique only if the T073 result meets SC-005 (≥ 85% correct verdict, < 10% false "detected" on plain notes, provisional); otherwise leave it false and record why
- [ ] T075 [US3] Phase D2 verification: full build with tests green; manual legato/slide play-through per quickstart "Phases D/F"

---

## Phase 8: User Story 4 - Bend and vibrato (Priority: P1)

**Goal**: bends judged against the written path, vibrato recognised, no false wrong-note feedback (spec US4, FR-022/FR-023; research R4, R5).

**Independent Test**: bend clips (half, whole, release, pre-bend) report target reached/under/over and vibrato clips report presence and width, with no false wrong-note result.

### Tests for User Story 4

- [ ] T076 [P] [US4] `ENGINE-TEST/technique/BendDetectorTest.java`: a bend reaching +1 semitone reports on pitch; +0.5 reports under; +1.5 over; release and pre-bend follow the path; the static-note tolerance is not applied while the pitch moves toward a written target
- [x] T077 [P] [US4] `ENGINE-TEST/technique/VibratoDetectorTest.java`: modulation at 4–8 Hz with depth ≥ about 10 cents over at least about 400 ms is `DETECTED`; a steady pitch is `NOT_DETECTED`; the sustained pitch is still accepted as the written note
- [ ] T078 [P] [US4] Extend `PRACTICE-TEST/model/PracticeSequenceBuilderTest.java`: a bend note is an `EXPECTED_NOTE` (no longer `AutoPassReason.BEND`) with a `PitchPath` built from `TGEffectBend` points; tremolo-bar and trill stay auto-pass

### Implementation for User Story 4

- [ ] T079 [SPIKE] [US4] Confirm from source the unit of `TGEffectBend` point values (`SEMITONE_LENGTH`, `MAX_POSITION_LENGTH`, `MAX_VALUE_LENGTH` in `common/TuxGuitar-lib/src/main/java/app/tuxguitar/song/models/effects/TGEffectBend.java`) and how the player turns them into pitch; record the exact value→semitone rule in `research.md` R5 before T080
- [ ] T080 [US4] Change `PRACTICE/model/PracticeSequenceBuilder.java`: `BEND` no longer auto-passes; build the event as `EXPECTED_NOTE` with the target pitch and a `PitchPath` (start, target, release, pre-bend) using the rule of T079
- [ ] T081 [US4] Path-aware pitch acceptance: add `ENGINE/pitch/PathTolerance.java` (or extend `TolerancePolicy.java`) with a widened cents tolerance along the `PitchPath`; use it in `PRACTICE/session/PracticeSession.java` so a moving pitch toward the written target is neither accepted early nor judged wrong
- [ ] T082 [P] [US4] Create `ENGINE/technique/BendDetector.java` (trajectory versus path; under/over/on-pitch in `measurements`) and `VibratoDetector.java` (periodic f0 modulation rate and depth; `UNCERTAIN` by depth and periodicity)
- [ ] T083 [US4] Wire both into `ENGINE/technique/TechniqueCatalog.java` and show reached/target semitones and vibrato width in the technique chip detail (`PRACTICE/ui/PracticePalette.java`, strings en + fr)
- [ ] T084 [SPIKE] [US4] S-F1 for bend and vibrato: record clips on both inputs, publish separability and bend-accuracy in `BENCH/` and `research.md` R4
- [ ] T085 [US4] Set `verified` flags in `TechniqueCatalog` per T084 against SC-005, otherwise leave false and record why
- [ ] T086 [US4] Phase D3 verification: full build with tests green; manual bend/vibrato play-through; record results exactly as run

**Checkpoint (Phase D)**: P1 techniques recognised or honestly "not verifiable".

---

## Phase 9: User Story 2b - Play at the tempo of the score (Priority: P1)

**Goal**: tempo-following practice mode with timing tolerance (spec US2b, FR-050…056; contracts/tempo-following.md; research R6, R6b, R10).

**Independent Test**: play a passage at the score tempo on benchmark clips with known timing offsets: notes inside the tolerance are matched, others flagged early/late/missed, and the position is never lost after a mistake.

### Tests for User Story 2b

- [ ] T087 [P] [US2b] `PRACTICE-TEST/session/FollowTimelineTest.java`: event times from measure/beat start and the tempo map at 50/70/100%, repeats and alternate endings in `MidiRepeatController` order, tempo changes
- [ ] T088 [P] [US2b] `PRACTICE-TEST/session/TimingToleranceTest.java`: `preset` STRICT/NORMAL/LENIENT with `earlyFractionOfBeat`, `lateFractionOfBeat`, `floorMillis` and `latencyOffsetMillis` scale with the local beat (same preset at 60 and 180 BPM); an exactly-on-the-beat note is not judged late after the offset
- [ ] T089 [P] [US2b] `PRACTICE-TEST/session/FollowSessionTest.java`: ON_TIME/EARLY/LATE/MISSED/EXTRA verdicts; property test over random error injections proving later events are still matched correctly (resynchronisation, FR-053); timing uses the onset transient time, not the decision time; states `PREPARING → COUNT_IN → FOLLOWING → PAUSED/COMPLETE/STOPPED` with no state waiting on a wrong or missed note
- [ ] T090 [P] [US2b] Extend `PRACTICE-TEST/session/ChordPracticeTest.java` / `SessionLoopTest.java`: chords and technique judges work inside follow mode without changing the timing verdict

### Implementation for User Story 2b

- [ ] T091 [SPIKE] [US2b] S-T1: measure (a) the round trip from the player's metronome click to the audio input (interface loop-back or acoustic), (b) the jitter of `MidiPlayer.getTickPosition()` against wall time, (c) the stream-time ↔ `System.nanoTime` mapping error of `CaptureService` (1024-frame reads ≈ 21 ms); decide player-clock vs own clock and whether latency auto-calibration can replace manual offset; record in `research.md` R6
- [ ] T092 [SPIKE] [US2b] S-T2: confirm how to mute the practice track player-side only (`MidiPlayer` sequencer mute) without touching the song model or the undo stack; record in `research.md` R6
- [ ] T093 [US2b] Create `PRACTICE/session/TempoClock.java` (`nowNanos()`, `songPositionFor(nanos)`) with the implementation chosen by T091, and a `CaptureClock` mapping in `ENGINE/input/CaptureService.java`/`AudioEngine.java` from stream time to wall time (non-blocking, Principle VI)
- [ ] T094 [US2b] Create `PRACTICE/session/FollowTimeline.java` and `TimingTolerance.java` per data-model.md (presets, fractions of the beat with a millisecond floor, `latencyOffsetMillis`)
- [ ] T095 [US2b] Create `PRACTICE/session/FollowSession.java` (contracts/tempo-following.md): count-in, onset-to-event matching (nearest unmatched event in its window), MISSED on window close, EXTRA for unmatched onsets, greedy matching with resynchronisation; reuse `ChordMatcher` and the technique judges; state machine per data-model.md
- [ ] T096 [US2b] Player integration: start/stop the player as clock and backing track (practice track muted per T092), tempo percentage from the player mode, count-in from `MidiPlayerCountDown`; in follow mode `SessionEvents.java` must not pause on `NOTIFY_STARTED`; a manual Play while following stops or restarts the session with a message (FR-006, R10)
- [ ] T097 [US2b] Latency offset: a manual adjustment and, if T091 supports it, a click-along auto-calibration (median offset of N taps) in `PRACTICE/ui/` with strings en + fr (FR-054)
- [ ] T098 [US2b] UI: mode selector (wait / follow) and tempo percentage in `PRACTICE/ui/PracticeWindow.java`; tolerance preset in `RecognitionSettingsPanel.java`/`AudioRecognitionOption.java` (persisted `timing tolerance` of T007); per-event timing feedback (on time / early / late / missed / extra) in `PracticePalette.java`
- [ ] T099 [US2b] End-of-run summary: counts ON_TIME/EARLY/LATE/MISSED/EXTRA plus the technique counts of T063, memory only (FR-056); extend `PRACTICE/session/PassageStatus.java`
- [ ] T100 [P] [US2b] Strings for follow mode, tolerance presets, calibration and the timing verdicts in `share/lang/messages.properties` + `messages_fr.properties`
- [ ] T101 [US2b] Benchmark in `ENGINE-TEST/benchmark/` / `PRACTICE-TEST/`: synthetic and recorded runs at the score tempo up to 8 notes/s with deliberate mistakes; report match rate and resynchronisation rate against SC-009 (provisional) in `BENCH/` and `research.md`
- [ ] T102 [US2b] Phase E closing verification: full build with tests green; manual quickstart "Phase E" (70% then 100%, deliberate mistake, calibration, built-in mic, back to wait mode unchanged); record results exactly as run

**Checkpoint (Phase E)**: tempo-following mode usable; wait mode unchanged.

---

## Phase 10: User Story 5 - Palm mute and tremolo picking (Priority: P2)

**Goal**: muted notes and tremolo-picked notes recognised (spec US5, FR-024/FR-025; research R4).

**Independent Test**: palm-muted versus open notes and tremolo-picked versus single picked notes get the right verdicts.

- [ ] T103 [P] [US5] `ENGINE-TEST/technique/PalmMuteDetectorTest.java`: a fast-decaying darker pluck is `DETECTED` relative to the learner's own open notes in the session, an open note is `NOT_DETECTED`, close cases are `UNCERTAIN`, verdict is relative (baseline) and never absolute
- [ ] T104 [P] [US5] `ENGINE-TEST/technique/TremoloPickingDetectorTest.java`: a burst above the rate threshold is `DETECTED` and counted as one event; a single pluck is `NOT_DETECTED`; the lowered refractory of T048 does not create false onsets on sustain
- [ ] T105 [P] [US5] Create `ENGINE/technique/PalmMuteDetector.java` (decay rate and spectral centroid / high-frequency ratio against a session baseline of unmuted notes)
- [ ] T106 [P] [US5] Create `ENGINE/technique/TremoloPickingDetector.java` (onset density on one pitch); make `PRACTICE/session/PracticeSession.java` treat the written tremolo-picked note as one event with attacks counted inside it (no repeated advance, no repeated error)
- [ ] T107 [US5] Wire both in `ENGINE/technique/TechniqueCatalog.java`; strings en + fr
- [ ] T108 [SPIKE] [US5] S-F2: record palm-mute and tremolo clips on both inputs, publish separability in `BENCH/` and `research.md` R4; set `verified` flags only per SC-006, otherwise "not verifiable"
- [ ] T109 [US5] Phase F1 verification: full build with tests green; manual check; record results exactly as run

---

## Phase 11: User Story 6 - Harmonics and tapping (Priority: P2/P3)

**Goal**: harmonics validated at the sounding pitch with honest technique feedback; tapping recognised or "not verifiable" (spec US6, FR-026/FR-027; research R4, R5).

**Independent Test**: natural, artificial and pinch harmonics and tapped passages are accepted at the written sounding pitch and the technique reported with its confidence, or "not verifiable".

- [ ] T110 [SPIKE] [US6] Confirm the sounding-pitch rule of harmonics from source: `TGEffectHarmonic` (`NATURAL_FREQUENCIES`, `getData()` offsets for artificial/pinch/tapped) and how the player computes it (`common/TuxGuitar-lib`, player/`MidiPlayer` note-on path); record the exact rule in `research.md` R5 before T113
- [ ] T111 [P] [US6] Extend `PRACTICE-TEST/model/PracticeSequenceBuilderTest.java`: natural, artificial, pinch and tapped harmonics become `EXPECTED_NOTE` with `soundingMidi` per the T110 rule (no longer `AutoPassReason.HARMONIC`)
- [ ] T112 [P] [US6] `ENGINE-TEST/technique/HarmonicDetectorTest.java`: a near-sinusoidal natural-harmonic pluck is `DETECTED`/`UNCERTAIN` by spectral shape and a normally fretted note at the same pitch `NOT_DETECTED`; artificial/pinch are at most `UNCERTAIN` on synthetic signals
- [ ] T113 [US6] Change `PRACTICE/model/PracticeSequenceBuilder.java`: harmonics no longer auto-pass; set `expectedMidi` to the sounding pitch and add `TechniqueTarget` with `soundingMidi`
- [ ] T114 [US6] Check the pitch front end handles harmonic octave/subharmonic readings (`ENGINE/analysis/HarmonicComb.java`, `ExpectedPitchVerifier`) so a harmonic at the written sounding pitch is accepted; add tests
- [ ] T115 [P] [US6] Create `ENGINE/technique/HarmonicDetector.java` (partial count / flatness / high-partial emphasis; natural first, artificial and pinch conservative) and, only if the S-F2 spike finds a reliable cue, `TappingDetector.java`; otherwise tapping stays without a detector and shows "not verifiable"
- [ ] T116 [SPIKE] [US6] S-F2 for harmonics and tapping: record clips on both inputs, publish separability in `BENCH/` and `research.md` R4; `verified` flags per SC-006
- [ ] T117 [US6] Wire in `ENGINE/technique/TechniqueCatalog.java`; strings en + fr; the screen says "not verifiable" (never "verified") where no benchmark backs the verdict
- [ ] T118 [US6] Phase F2 verification: full build with tests green; manual check; record results exactly as run

---

## Phase 12: Polish and cross-cutting

**Purpose**: documentation, completeness and final verification.

- [ ] T119 [P] Update the help pages `desktop/TuxGuitar/share/help/tools_audio_recognition.html` and `tools_audio_recognition_fr.html` for the button, gain, noise reduction, fast recognition, technique results and tempo mode
- [ ] T120 [P] Update the "Audio recognition" options text and any settings descriptions in `PRACTICE/../share/lang/` so every new setting has English and French strings (constitution: new strings through the translation files, English provided)
- [ ] T121 Verify no audio is recorded or persisted and the gain is never saved (FR-042, FR-063): review the code paths and add a test asserting the settings store holds no gain value
- [ ] T122 Final constitution re-check of plan.md (each principle, deviations) and update plan.md/research.md with the measured results and any revised target agreed with the owner
- [ ] T123 Run the complete quickstart.md end to end on the macOS SWT build (both inputs) and record results exactly as run, stating which checks were manual and which skipped
- [ ] T124 Final full build with tests (`mvn -e clean verify -P native-modules`) and the benchmark run; compare with T001 and T005; list any regression
- [ ] T125 Prepare the release note text (what changed, known limits: techniques shown "not verifiable", macOS validated only); the owner decides when to tag and publish

---

## Phase 13: Automated tests: stubs, real guitar samples, functional and GUI tests

**Purpose**: owner request (2026-09-30): automatic unit, functional and GUI tests, with stubs and with real guitar sound examples from the internet. Each task below is executed **together with the story phase it names** (it is listed here to keep the numbering stable); unit tests stay inside their story phases above. Nothing here is hardware-dependent: a stub feeds audio to the engine instead of Java Sound.

### Test stubs and tooling

- [ ] T126 [P] Create `ENGINE-TEST/testsupport/WavSource.java`: loads a PCM WAV (mono, 48 kHz, 16-bit; other rates rejected with a clear message) into `float[]` for tests, using `javax.sound.sampled` only
- [ ] T127 [P] Create the audio-input stub `ENGINE-TEST/testsupport/FakeAudioInput.java`: writes samples into the engine's `SampleRingBuffer` either at real-time pace or as fast as possible, replacing `CaptureService`/Java Sound; add a test factory for `AudioEngine` (the package-private constructor taking `InputDeviceCatalog`, `CurrentInputSelection`, ring-buffer capacity, `LevelAnalyzer` and estimator factory already exists; expose a test-support entry point rather than widening the public API)
- [ ] T128 [P] Create `PRACTICE-TEST/testsupport/FakeTempoClock.java` and `FakeMidiPlayer.java` (stubs for player start/stop events, tick position and count-in) used by the tempo-mode tests
- [ ] T129 [P] Extend `PRACTICE-TEST/model/SongFixture.java` with builders for songs with a legato pair, a bend with path, a natural harmonic, a chord strum, a tempo change and a repeat, so functional tests do not depend on files
- [x] T130 [SPIKE] Find freely redistributable real guitar recordings on the internet (e.g. freesound.org with the CC0 filter, public-domain or CC0 sample packs; check the licence of research datasets such as GuitarSet or IDMT-SMT-Guitar before any use) and record for each candidate its URL, author, licence and whether redistribution in this repository is allowed in `BENCH/SOURCES.md`; rule inherited from the MVP: only CC0-1.0 or public-domain clips of known authorship are committed, anything with another licence stays out of the repository or needs the owner's written approval **Done 2026-10-06:** research datasets found and downloaded locally (not redistributable, never committed); see spec 004 and research R9.
- [ ] T131 [P] Download and cut the accepted clips (single notes E2…E5, open strings, chord strums, hammer-on / pull-off, slide, bend, vibrato, palm mute, natural harmonic, noise-only) to short mono 48 kHz 16-bit WAVs (at most about 3 s each) in `desktop/TuxGuitar-audio-engine/src/test/resources/samples/` with a licence file and a provenance table beside them; no network access at test time

### Functional tests (engine → session, headless, with the stubs)

- [x] T132 [US2] `ENGINE-TEST/functional/NotePracticeFunctionalTest.java`: real single-note clips through `FakeAudioInput` → `AudioEngine` → `PracticeSession` on a `SongFixture` score: the right note is accepted (✓), a wrong neighbour gives ✗, silence and noise never advance; record latency to `BENCH/` **Done 2026-10-06:** covered by `RecordedPracticeReplayTest`, `MonophonicRegressionReplayTest` and `DatasetBenchmarkTest` (spec 004).
- [ ] T133 [US2] Fast-run functional test with the T002 synthetic runs and, when available, real run clips: recognition rate and false accepts at 4…12 notes per second reported and compared with SC-002/SC-004
- [x] T134 [US2] Chord functional test with real strum clips: complete chord accepted, missing note reported, "not verifiable" pitches never claimed heard **Done 2026-10-06:** covered by `DatasetBenchmarkTest` (chords, incomplete chords refused 96-98 %); the "not verifiable" labels shown to the learner are still open (spec 004 T008).
- [ ] T135 [US8] Functional test of the front end: weak and noisy clips with and without gain, gate and subtraction; results reported as a with/without table in `BENCH/`
- [ ] T136 [US7] Functional technique tests, one per technique family (legato, slide, bend, vibrato, palm mute, tremolo picking, harmonics): a clip plus the written technique gives the expected verdict; plain clips never give a false "detected" beyond the agreed rate
- [ ] T137 [US2b] Functional tempo test: `FakeTempoClock` plus clips with known onset times give the expected ON_TIME/EARLY/LATE/MISSED/EXTRA verdicts and the session stays synchronised after an injected mistake

### GUI tests (SWT, using the existing contract-test toolkit)

Base: `PRACTICE-TEST/ui/SwtContractToolkit.java` and `RecognitionSettingsSwtTest.java` show how the existing GUI tests create widgets; reuse that mechanism (check how it satisfies SWT's main-thread requirement on macOS and record it in `research.md`).

- [x] T138 [US1] GUI test: with the plugin connected the transport bar shows the Practice item after `transport.start`; clicking opens the practice window once (no second instance); the item's checked state follows open/close; the Tools entry produces the same window *(done as a native SWT test of the button, icon and state in `PracticeToolbarSwtTest`, profile `swt-gui-tests`; clicking the real tool bar item in the full application is covered by the manual check of T022)*
- [x] T139 [US8] GUI test: the gain control exists in both `LevelPanel` and `TunerWindow` and shows the same value; reset returns to neutral; the "suggest" button is visible only when the suggestion switch is on *(done: `GainControlSwtTest` against a stub backend, profile `swt-gui-tests`)*
- [ ] T140 [US7] GUI test: the technique chips show detected / not detected / uncertain / not verifiable with their localized text; with technique detection off every chip says "not verifiable"
- [ ] T141 [US2b] GUI test: mode selector, tolerance preset and tempo percentage change the session parameters; per-event timing feedback is shown
- [ ] T142 [SPIKE] Optional GUI visual check: capture a screenshot of `PracticeWindow` and of the transport bar in a test (SWT `GC` on the widget) and compare with a stored baseline within a tolerance; keep it only if it is stable across runs on the macOS build
- [ ] T143 Make sure the default `mvn verify` runs the unit, functional and GUI tests (only the `benchmark` tag stays excluded) and add a CI job `.github/workflows/` that runs the fork modules' tests on a macOS runner (the current release workflow uses `-DskipTests`); report what actually ran

### Guitar Pro import issues (found 2026-09-30; outside spec 002, tracked here so they are not lost)

Constitution I and the owner's rule "keep upstream TuxGuitar changes separate" apply: fixes here go in their own commits.

- [ ] T144 [SPIKE] Reproduce the reported playback error ("division par zéro", from memory) on `Metallica - To Live Is To Die (6).gp4`: my headless checks did **not** reproduce it (all 457 readable Metallica GP3/GP4 files generate their MIDI sequence without exception through `MidiSequenceParser` and `MidiSequenceHandlerImpl`); launch the app from a terminal with stdout/stderr written to a log, open the file, press Play, and analyse the stack trace; record the cause in `research.md`
- [ ] T145 Regression tests in `common/TuxGuitar-lib/src/test/java` (or `TuxGuitar-gtp` if it has a test source set): small GP3/GP4 fixtures written by our own `GP4OutputStream`/`GP3` writer covering a valid song, a triplet/quintuplet measure, too-long and too-short measures, a tied note without a previous note, and a bend with two equal points; each is read, passed through `TGSongManager.updatePreciseStart`, checked with `getMeasureErrors`, and its MIDI sequence generated without exception
- [ ] T146 Optional corpus smoke test: a test that reads every `.gp3`/`.gp4` of the directory named by the environment variable `TG_GP_CORPUS` and is skipped when it is not set; it reports counts (read, read failures, files with measure errors) without asserting them; the owner's folder of Metallica files is copyright material and is never committed
- [x] T147 Decide with the owner how imported files with measure-duration errors should behave: measured on the 460 Metallica files, 269 of the 457 readable ones open the "measure errors" dialog (898 too-long and 41 415 too-short voices, 1 298 tied-note errors); first determine whether these are defects of the source files or reader mismatches (e.g. compare a measure with Guitar Pro's own rendering), then propose options (keep as is, offer automatic fix, relax the check for imports) **Done 2026-10-06:** after-open dialog removed, `Edit > Display invalid measures` lists every problem on demand (`TGUpdateReadSongController`).
- [ ] T148 Check the file-format detection used by the app for files whose extension does not match their content: three files named `.gp3` are Guitar Pro 4 files (`FICHIER GUITAR PRO v4.00/v4.06`); confirm the app opens them by content (my test forced the reader by extension and reported them as unsupported)

---

## Dependencies and order

- Phase 1 → Phase 2 → everything else. Phase 2 (T007–T010) blocks all stories.
- **US1 (Phase 3)** is independent of the audio stories and can ship first. T013 → T014 → T016/T017; T019 → T020.
- **US8 (Phase 4)** is independent of US1; T029–T033 are parallel, T034 depends on them, T035 on T034, UI tasks (T037–T039) on T035. T041/T042 spikes can run beside implementation; T042 decides the subtraction default.
- **US2 (Phase 5)** depends on Phase 2 (T010) and benefits from US8 (cleaner signal); T047/T048 parallel, T049 depends on both. T053/T054 depend on recordings.
- **US7 (Phase 6)** depends on Phase 2 (T008–T010); it must precede US3, US4, US5, US6 (they register detectors in its catalog).
- **US3 (Phase 7)** depends on US7 and on T049 (acceptance rule). **US4 (Phase 8)** depends on US7; T079 before T080; T081 on T080.
- **US2b (Phase 9)** depends on US2 (recognition speed) and on the spikes T091/T092; it reuses US7's judges. It can start its timeline/tolerance/session tests (T087–T090, T094, T095) before the clock decision because the contract is clock-independent.
- **US5 (Phase 10)** depends on US7 and T048 (refractory parameter). **US6 (Phase 11)** depends on US7, T110 before T113, and on T114.
- Polish (Phase 12) last. Phase 13 tasks run with the phase of the story they name: T126–T131 in Phase 1/2 (before the first functional test), T132–T134 with Phase 5, T135 with Phase 4, T136 with Phases 6–8, T137 with Phase 9, T138 with Phase 3, T139 with Phase 4, T140 with Phase 6, T141 with Phase 9; T143 as soon as the first GUI test exists; T144–T148 are independent of spec 002.

## Parallel opportunities

- Phase 1: T002, T003, T004, T006 together.
- Phase 3: T011 and T012, then T015, T020, T021 together after T013.
- Phase 4: T023–T027 (tests) together; T029–T033 (stages) together; T037, T038, T040 together.
- Phase 5: T044, T045, T046 together; T047 and T048 together.
- Phase 6: T056, T057 together; T064 beside T062.
- Phases 7, 8, 10, 11 detector tasks (T070/T071, T082, T105/T106, T115) are independent files once US7 is done; different people or sessions can take US3, US4, US5 in parallel.
- Phase 9: T087–T090 together.

## Implementation strategy

1. **Deliver US1 first** (Phases 1–3): small, visible, low risk; leaves the build green.
2. **Then US8 and US2** (Phases 4–5): they raise recognition quality and produce the measured numbers that decide whether the provisional targets stand.
3. **Then the technique framework and P1 techniques** (Phases 6–8), each technique with its verified flag gated on the benchmark.
4. **Then tempo following** (Phase 9) once recognition speed is proven and the clock spike is answered.
5. **P2/P3 techniques last** (Phases 10–11); any technique that cannot be shown to be separable ships as "not verifiable".
6. Stop and report after each phase (constitution V); do not implement later phases opportunistically; commit only when asked.

**MVP scope**: Phase 3 (US1) alone is a shippable increment; Phases 4–5 are the next useful increment; the tempo mode (Phase 9) is the headline feature and is deliberately after the recognition work it depends on.
