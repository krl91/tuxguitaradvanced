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

Commit only when the owner asks; never push without approval (project rule: no push without approval).

---

## Phase 1: Setup and baseline

**Purpose**: a measured starting point and the test tooling every later phase needs. No product behaviour changes.

- [x] T001 Run the full macOS SWT build with tests on the current tree and record the exact result (tests run/failed/skipped per module) in `BENCH/baseline.md`; this is the regression reference for every phase
- [x] T002 [P] Extend `ENGINE-TEST/testsupport/SyntheticSignals.java` with fast-passage generators: a sequence of Karplus-Strong plucks at a given notes-per-second rate (4…12), with options for identical repeated pitches, semitone neighbours and a still-ringing previous note *(done: `TechniqueSignals.fastPassage` in `ENGINE-TEST/testsupport/TechniqueSignals.java`, with `TechniqueSignalsTest`)*
- [x] T003 [P] Extend `ENGINE-TEST/testsupport/SyntheticSignals.java` with technique generators, each returning the plain counterpart too: legato step without attack (hammer-on up, pull-off down), slide glide between two pitches over a given time, vibrato (rate Hz, depth cents), bend path from a list of (time, semitones), palm-muted pluck (fast decay + darker spectrum), tremolo-picked burst (attack rate), natural-harmonic pluck (near-sinusoidal), additive hum/broadband noise and a weak-level scaling option *(done in `TechniqueSignals`: legato step, slide, vibrato, bend, palm-muted pluck, tremolo picking, natural harmonic, hum, scaling)*
- [x] T004 [P] Add latency reporting to `ENGINE-TEST/benchmark/BenchmarkFixtures.java` and `BenchmarkSweepTest.java`: per clip, time from the energy onset to the first accepted note, with p50/p95/max over the clip set, written as CSV to `BENCH/`
- [x] T005 [SPIKE] S-C1: run the existing benchmark on the current tree (`mvn test -pl ../../TuxGuitar-audio-engine -am -Dgroups=benchmark -DexcludedGroups= -Dsurefire.failIfNoSpecifiedTests=false` from the macOS SWT build directory) and record the baseline latency p50/p95 and accuracy in `BENCH/baseline.md` and `research.md` R3 (replaces the MVP's 171 ms / 288 ms figures if different) *(done: baseline in `BENCH/baseline.md`; median stable-note latency 171 ms on the built-in-mic clips)*
- [x] T006 [P] Write `BENCH/recording-plan.md`: the clip list to record on the owner's setup (direct interface and built-in microphone, reported separately): fast runs at 4/6/8/10/12 notes per second, identical repeats, neighbouring pitches, each P1 technique next to its plain counterpart, palm mute, tremolo picking, natural/artificial/pinch harmonics, tapping, noise-only, weak-input and hum clips; format rules from the MVP (short PCM WAV, mono, 48 kHz, 16-bit, guitar only, CC0-1.0 licence file beside them)

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
- [x] T050 [US2] "Input too poor for reliable fast recognition" (FR-015): derive it from level state, noise floor and evidence margin, show it in `PRACTICE/ui/PracticePalette.java` with what to do; strings in `share/lang/` en + fr **Done 2026-10-06: `InputQuality` (median level of the last three attacks below -30 dBFS, measured in research R3 closing: three-note runs followed at 100 % from -25 dBFS, 95 % at -30, 10 % at -40; noise and evidence margin did not discriminate), shown in the palette with what to do (fr, en, es); `InputQualityTest`, `InputTooWeakSessionTest`, `WeakInputBenchmarkTest`; not seen in the running application (needs live weak audio).**
- [x] T051 [P] [US2] Extend the benchmark in `ENGINE-TEST/benchmark/BenchmarkSweepTest.java` with the T002 fast passages (4…12 notes/s, identical repeats, neighbours): recognition rate, false accepts, latency p50/p95, written to `BENCH/`; compare with the T005 baseline **Done: `FastPassageBenchmarkTest` on real notes through the practice session (not synthetic Karplus-Strong plucks), 4 to 12 notes per second, `benchmark/fast-passages-2026-10-06.txt`.**
- [x] T052 [US2] Regression: all existing chord, silence, level-check, tuning and passage tests in `PRACTICE-TEST/` and `ENGINE-TEST/` still pass unchanged (FR-016) **Full macOS SWT build with tests, 2026-10-06: BUILD SUCCESS, 0 failures, 0 skipped (practice 228, engine 253, tuner 23); no existing test was changed to make it pass.**
- [ ] T053 [SPIKE] [US2] **DEFERRED by the owner (2026-10-06): no new recording is to be requested for now.** Record the T006 fast-run and repeat clips with the owner on both inputs, add them with the CC0-1.0 licence file to the benchmark assets, and re-run T051 on them
- [x] T054 [US2] Compare measured results with SC-002, SC-003 and SC-004 in `research.md` R3; where a target is not met, report the measured figure and propose revised targets to the owner (no target is silently changed) **Done: SC-002 and SC-004 are not met (24.7 % of the notes at 8 per second against 95 %), SC-003 met for the session on real notes, false accepts 0; revised targets proposed to the owner in research R3 closing, none applied.**
- [ ] T055 [US2] **Stays open until a manual validation with the guitar (owner, 2026-10-06); the full build with tests was green on 2026-10-06.** Phase C closing verification: full build with tests green; manual quickstart "Phase C" (scale runs at rising speed in the app); record results exactly as run

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
  > **Status 2026-10-06:** open, blocked by the recordings (T053 is deferred by the owner, no new recording is requested). What exists: slide measured on IDMT-SMT-Guitar dataset 2 (48 % of the annotated notes, `benchmark/technique-benchmark-2026-10-06.txt`); **no real legato clip at all**: neither IDMT, GuitarSet nor the reference recording (`tests/1`, whose score has no hammer-on or slide) annotates hammer-ons or pull-offs. Synthetic baseline in research R14.
- [x] T074 [US3] Set the `verified` flag in `TechniqueCatalog` for a technique only if the T073 result meets SC-005 (≥ 85% correct verdict, < 10% false "detected" on plain notes, provisional); otherwise leave it false and record why
- [ ] T075 [US3] Phase D2 verification: full build with tests green; manual legato/slide play-through per quickstart "Phases D/F"
  > **Status 2026-10-06:** build with tests green (full macOS SWT build 2026-10-06, 4:23, BUILD SUCCESS; `LegatoFunctionalTest` 2/2, `LegatoDetectorTest`, `SlideDetectorTest`, `PracticeSequenceBuilderTest` in it). The manual play-through with a guitar (legato and slide, quickstart "Phases D/F") is **not done** and stays open until the owner has played it.

---

## Phase 8: User Story 4 - Bend and vibrato (Priority: P1)

**Goal**: bends judged against the written path, vibrato recognised, no false wrong-note feedback (spec US4, FR-022/FR-023; research R4, R5).

**Independent Test**: bend clips (half, whole, release, pre-bend) report target reached/under/over and vibrato clips report presence and width, with no false wrong-note result.

### Tests for User Story 4

- [x] T076 [P] [US4] `ENGINE-TEST/technique/BendDetectorTest.java`: a bend reaching +1 semitone reports on pitch; +0.5 reports under; +1.5 over; release and pre-bend follow the path; the static-note tolerance is not applied while the pitch moves toward a written target **Done 2026-10-06 (engine side): `BendDetectorTest` (9 tests: on pitch, under, over, never happens, not made before the target, bend and release, pre-bend, quarter tone, too few hops), with `BendPath` and `BendDetector.evaluate(segment, baseMidi, path)`; the static-note tolerance part is the session's (T081).**
- [x] T077 [P] [US4] `ENGINE-TEST/technique/VibratoDetectorTest.java`: modulation at 4–8 Hz with depth ≥ about 10 cents over at least about 400 ms is `DETECTED`; a steady pitch is `NOT_DETECTED`; the sustained pitch is still accepted as the written note
- [x] T078 [P] [US4] Extend `PRACTICE-TEST/model/PracticeSequenceBuilderTest.java`: a bend note is an `EXPECTED_NOTE` (no longer `AutoPassReason.BEND`) with a `PitchPath` built from `TGEffectBend` points; tremolo-bar and trill stay auto-pass **Done 2026-10-06: 8 new tests in `PracticeSequenceBuilderTest` (path, units, bend+release, pre-bend, quarter tone, written duration, same pitch as previous, no bend); chord with a bent note stays MULTI_NOTE, tremolo bar and trill stay auto-pass; `SongFixture.bend` now writes quarter-tone values (a semitone is 2).**

### Implementation for User Story 4

- [x] T079 [SPIKE] [US4] Confirm from source the unit of `TGEffectBend` point values (`SEMITONE_LENGTH`, `MAX_POSITION_LENGTH`, `MAX_VALUE_LENGTH` in `common/TuxGuitar-lib/src/main/java/app/tuxguitar/song/models/effects/TGEffectBend.java`) and how the player turns them into pitch; record the exact value→semitone rule in `research.md` R5 before T080 **Done 2026-10-06: one unit is 0.5 semitone (a quarter of a whole tone), `semitones = value / 2`, position `/ 12` of the note; evidence, path rules and the proposed observation window in `research.md` R5 addendum.**
- [x] T080 [US4] Change `PRACTICE/model/PracticeSequenceBuilder.java`: `BEND` no longer auto-passes; build the event as `EXPECTED_NOTE` with the target pitch and a `PitchPath` (start, target, release, pre-bend) using the rule of T079 **Done 2026-10-06: a single bent note is an `EXPECTED_NOTE` (pitch = fretted pitch, or fretted + first point rounded for a pre-bend) carrying `PitchPath`, base pitch and written seconds (`PracticeEvent.getBendPath/getBendWrittenSeconds`), and `BEND` / `PRE_BEND` / `RELEASE` technique targets (`TechniqueTarget.withBend`); a bend mark without points is a plain note. The session still ignores the path: T081.**
- [x] T081 [US4] Path-aware pitch acceptance: add `ENGINE/pitch/PathTolerance.java` (or extend `TolerancePolicy.java`) with a widened cents tolerance along the `PitchPath`; use it in `PRACTICE/session/PracticeSession.java` so a moving pitch toward the written target is neither accepted early nor judged wrong **Done 2026-10-06: `PracticeSession` follows the written path (corridor with 0.5 semitone of margin): no wrong note along the path, no early acceptance of the next note when it lies on the path (pitch change rule and attack-difference rule), a bend observation window of the written duration clamped to 1.0 to 2.5 s; `BendSessionTest` (12 scripted tests) and `BendFunctionalTest` (7 audio tests); mutations of the three rules each fail tests.** **Correction 2026-10-07: a bent note whose run began on its written pitch goes on along the path (a bend that leaves the fretted pitch at once was never accepted, found with the owner's rendered audio); `BendRecordedReplayTest` replays `tests/2` (skipped when absent): 8 of 8 notes on scores 1 to 3, no wrong note; research R5 second addendum.** **Changed by the owner 2026-10-07: the bend is required for the note to be accepted (Skip is the way out), see research R5 third addendum; the dropout and segment-start defects found with the rendered audio are fixed with tests; the written release is required too (R5 fourth addendum).**
- [x] T082 [P] [US4] Create `ENGINE/technique/BendDetector.java` (trajectory versus path; under/over/on-pitch in `measurements`) and `VibratoDetector.java` (periodic f0 modulation rate and depth; `UNCERTAIN` by depth and periodicity) **Done 2026-10-06: `BendDetector.evaluate(segment, baseMidi, path, part)` (rise, start, fall; under / on pitch / over in `outcome`) and `BendPath`; the vibrato detector existed (first pass).**
- [ ] T083 [US4] Wire both into `ENGINE/technique/TechniqueCatalog.java` and show reached/target semitones and vibrato width in the technique chip detail (`PRACTICE/ui/PracticePalette.java`, strings en + fr)
  > **Status 2026-10-06:** `TechniqueJudge` already judges a bend, a pre-bend and a release each against its part of the path (the catalogue wiring existed). The detail in the chip (reached / target semitones, vibrato width) and its strings are **not done on purpose**: bend and vibrato stay *cannot be checked yet* until T084, so such a detail would be dead text; to do with T085.
- [ ] T084 [SPIKE] [US4] S-F1 for bend and vibrato: record clips on both inputs, publish separability and bend-accuracy in `BENCH/` and `research.md` R4
  > **Status 2026-10-07:** open, blocked by the recordings (T053 deferred, none requested). Available: bend 82 % and vibrato 59 % of the annotated notes on IDMT-SMT-Guitar dataset 2 for presence only, **no measurement of the bend accuracy against a written target** (the dataset gives no written path). The path-aware detector is therefore tested on synthetic trajectories only.
- [ ] T085 [US4] Set `verified` flags in `TechniqueCatalog` per T084 against SC-005, otherwise leave false and record why
  > **Status 2026-10-07:** nothing to change: `bend` and `vibrato` stay `verified = false`, so the interface says *cannot be checked yet* and never *heard* (owner's contract of 2026-10-06).
- [ ] T086 [US4] Phase D3 verification: full build with tests green; manual bend/vibrato play-through; record results exactly as run
  > **Status 2026-10-07:** build with tests green (full macOS SWT build 2026-10-07, 4:26, BUILD SUCCESS: practice 269, engine 264, tuner 23, Gervill 9, lib 67, gtp 10 with 1 skipped); the manual bend and vibrato play-through with a guitar is **not done** and stays open for the owner.
  > **Owner's manual result 2026-10-07 (his words, nothing rounded):** rising bends: *"pour le bend qui monte ça va"* (after the bend was made a condition); descending bends and pre-bend with release: *"pas bon"* before the release was required and the zones were widened, then, on `tests/2/3b-pre-bend-visible.tg` (the pre-bend and release of `3-pre-bend.tg` measure 1 has no arrow in TuxGuitar, which was the cause of the first complaint): *"ça me semble très bien"*. **Not tested by the owner:** vibrato, a bend not played (base note only) on the guitar, hammer-on and slide on the guitar, the other three scores in full. T086 therefore stays open for the vibrato and the cases not played; the bend, the release and the pre-bend with release are validated on the owner's guitar for the scores played. **Confirmed by the owner afterwards: the bends, rising and descending, now work (his message of 2026-10-07).** The vibrato, a bend deliberately not played, the hammer-on and the slide are not yet validated on the guitar: T086 stays open.

**Checkpoint (Phase D)**: P1 techniques recognised or honestly "not verifiable".

---

## Phase 9: User Story 2b - Play at the tempo of the score (Priority: P1)

**Goal**: tempo-following practice mode with timing tolerance (spec US2b, FR-050…056; contracts/tempo-following.md; research R6, R6b, R10).

**Independent Test**: play a passage at the score tempo on benchmark clips with known timing offsets: notes inside the tolerance are matched, others flagged early/late/missed, and the position is never lost after a mistake.

### Tests for User Story 2b

- [x] T087 [P] [US2b] `PRACTICE-TEST/session/FollowTimelineTest.java`: event times from measure/beat start and the tempo map at 50/70/100%, repeats and alternate endings in `MidiRepeatController` order, tempo changes **Done 2026-10-07: `FollowTimelineTest` (8 tests).**
- [x] T088 [P] [US2b] `PRACTICE-TEST/session/TimingToleranceTest.java`: `preset` STRICT/NORMAL/LENIENT with `earlyFractionOfBeat`, `lateFractionOfBeat`, `floorMillis` and `latencyOffsetMillis` scale with the local beat (same preset at 60 and 180 BPM); an exactly-on-the-beat note is not judged late after the offset **Done 2026-10-07: `TimingToleranceTest` (9) and `TimingContextTest` (13), with the three separate things and the rule that nothing is judged before the clocks are ready.**
- [x] T089 [P] [US2b] `PRACTICE-TEST/session/FollowSessionTest.java`: ON_TIME/EARLY/LATE/MISSED/EXTRA verdicts; property test over random error injections proving later events are still matched correctly (resynchronisation, FR-053); timing uses the onset transient time, not the decision time; states `PREPARING → COUNT_IN → FOLLOWING → PAUSED/COMPLETE/STOPPED` with no state waiting on a wrong or missed note **Done 2026-10-08: `FollowSessionTest` (22 tests), fake clocks, no sleep; 22/22 green with the session.**
- [x] T090 [P] [US2b] Extend `PRACTICE-TEST/session/ChordPracticeTest.java` / `SessionLoopTest.java`: chords and technique judges work inside follow mode without changing the timing verdict **Done 2026-10-08: `FollowSessionTechniqueTest` (11 tests, chords and legato on frames from real analysis of synthetic audio, bends on scripted frames); 11/11 green with the session. Known gap: a chord needing 1 hop instead of `stableHops` still passes (mutation survives); a bend still sounding under the next note was not made to work with synthetic audio and is not covered.**

### Implementation for User Story 2b

- [ ] T091 [SPIKE] [US2b] S-T1: measure (a) the round trip from the player's metronome click to the audio input (interface loop-back or acoustic), (b) the jitter of `MidiPlayer.getTickPosition()` against wall time, (c) the stream-time ↔ `System.nanoTime` mapping error of `CaptureService` (1024-frame reads ≈ 21 ms); decide player-clock vs own clock and whether latency auto-calibration can replace manual offset; record in `research.md` R6
  > **Status 2026-10-07:** measured, results in research R6c: the player's tick is an integral of `nanoTime` (10 ms steps, rate error +0.01 / -0.14 / -0.11 % at 60 / 120 / 200 bpm), the capture sample clock is exact but the arrival time of a read jitters by up to 65 ms (spread 130 ms), and the **acoustic round trip on the built-in speakers and microphone is 64 to 66 ms (median, std 3 ms, range 60 to 71 ms)** at 48 and 44.1 kHz, queue of the output path not included. Player clock validated by the owner. A measurement by cable (the owner's USB interface) is left for later and does not block T093; the task stays open for it.
- [x] T092 [SPIKE] [US2b] S-T2: confirm how to mute the practice track player-side only (`MidiPlayer` sequencer mute) without touching the song model or the undo stack; record in `research.md` R6
  > **Status 2026-10-07:** answered by inspection of the source and **done** (commit `1d106b411`): a player-only mute in `MidiPlayer` (`setPlayerOnlyMute`, `clearPlayerOnlyMutes`), kept by `play()` and every update of the tracks, 6 tests in `TestPlayerOnlyMute`; the Android build was not run (no SDK here), see research R6c.
- [x] T093 [US2b] Create `PRACTICE/session/TempoClock.java` (`nowNanos()`, `songPositionFor(nanos)`) with the implementation chosen by T091, and a `CaptureClock` mapping in `ENGINE/input/CaptureService.java`/`AudioEngine.java` from stream time to wall time (non-blocking, Principle VI) **Done 2026-10-07: `CaptureClock` (engine) plugged in `CaptureService` and `AudioEngine`, `AnalysisFrame.getCaptureSampleEnd()`, `TickInterpolator`, `TempoClock`, `PlayerTempoClock` (practice), 17 tests, checked live on the built-in microphone; details and what is left in research R6c.**
  > **Rule added 2026-10-07 (owner, FR-057 to FR-059):** no filtering of inputs or outputs by name, transport or presumed latency (Bluetooth included); the clocks must not assume a device; the capture clock reports the spread of the arrival lag, which feeds the unstable-route warning.
- [x] T094 [US2b] Create `PRACTICE/session/FollowTimeline.java` and `TimingTolerance.java` per data-model.md (presets, fractions of the beat with a millisecond floor, `latencyOffsetMillis`) **Done 2026-10-07: `FollowTimeline`, `TimingTolerance` (provisional values until T101), `LatencyCompensation`, `ClockUncertainty`, `TimingContext`, `TimingOutcome`, `TimingVerdict`; research R6c. T095 must go through `TimingContext.mayJudge()` before any verdict (EARLY, LATE, MISSED, EXTRA, WRONG).**
- [x] T095 [US2b] Create `PRACTICE/session/FollowSession.java` (contracts/tempo-following.md): count-in, onset-to-event matching (nearest unmatched event in its window), MISSED on window close, EXTRA for unmatched onsets, greedy matching with resynchronisation; reuse `ChordMatcher` and the technique judges; state machine per data-model.md **Done 2026-10-08 (session only, not yet wired to the UI or the player): `FollowSession` implemented; windows per event on `wallNanosAt`, `TimingContext.mayJudge()` gate, NOT_JUDGED for windows not seen whole, MISSED kept open to a late-reported attack for one detection latency, chords by `ChordMatcher`, notes by `PolyphonicAcceptance` per open event when configured, legato arrivals from their pitch, techniques judged apart. Mutations checked: gate removed, legato disabled, miss shifting the next window: all caught.**
  > **Constraint from Phase 8 (owner, 2026-10-06):** in follow mode the timeline of the score has priority; a bend, release or pre-bend judged wrongly or late never stops the timeline (research R5 addendum, rule e).
- [ ] T096 [US2b] Player integration: start/stop the player as clock and backing track (practice track muted per T092), tempo percentage from the player mode, count-in from `MidiPlayerCountDown`; in follow mode `SessionEvents.java` must not pause on `NOTIFY_STARTED`; a manual Play while following stops or restarts the session with a message (FR-006, R10) **Implemented 2026-10-08, not ticked: `FollowPlayer` port + `MidiFollowPlayer`, `FollowPlayback` (player-only mute of the practised track, percentage, count-down and loop given back exactly once), `FollowRun`, `FollowEvents` (never pauses because the player started), manual Play ends the run with a localized message and the playback goes on, Stop command / new song / exception / closing end it too; 14 + 15 + 3 tests on fakes and on the real `MidiPlayer` without sound. Not verified here: a real playback with sound and a real count-in (manual Phase E, T102).**
- [ ] T097 [US2b] Latency offset: a manual adjustment and, if T091 supports it, a click-along auto-calibration (median offset of N taps) in `PRACTICE/ui/` with strings en + fr (FR-054) **Implemented 2026-10-08, not ticked: manual delay within +-1000 ms, calibration by 8 clicks and the median of the notes with outliers set aside (`CalibrationTaps`, `CalibrationRun`: announced with its duration, Cancel cuts the clicks at once), kept per input + output + format (`AudioRoute`, `RouteCalibrations`, configuration), "reduced rhythmic precision" never blocking, tuner and wait mode independent; 9 + 11 + 6 + 3 + 15 tests, headless and native SWT. Not verified here: the clicks through a real output and a real microphone (manual Phase E, T102). Known limit: a note less than about 65 ms after the click heard again by the microphone cannot be told from it. T091 (cable measurement) stays open; this is the compensation of spec 002, not the 22 ms monitoring limit of the future spec 003.**
  > **Rule added 2026-10-07 (owner, FR-057 to FR-059):** the calibration is stored per input + output + audio format/profile (sample rate, Bluetooth profile) and invalidated or asked again when any of them changes; a high or unstable latency warns clearly and never blocks; the calibration sounds are announced before they play with an approximate duration and a Cancel button that stops them at once; the offset may be adjusted by hand as well.
- [ ] T098 [US2b] UI: mode selector (wait / follow) and tempo percentage in `PRACTICE/ui/PracticeWindow.java`; tolerance preset in `RecognitionSettingsPanel.java`/`AudioRecognitionOption.java` (persisted `timing tolerance` of T007); per-event timing feedback (on time / early / late / missed / extra) in `PracticePalette.java` **Implemented 2026-10-08, not ticked: mode selector, percentage (20 to 150), tolerance, calibration and precision in the preparation window (`FollowPanel`, `FollowSettings`, `FollowStarter`), palette of the run (`FollowView`, `PracticePalette.renderFollow`), one run at a time. The values shown reach the session (`FollowStarterTest`), the panel is tested headless and natively; the whole window and the palette widgets were only built, not exercised (manual Phase E, T102).**
  > **Rule added 2026-10-07 (owner, FR-057 to FR-059):** every mode that judges rhythm shows "reduced rhythmic precision" until the route is calibrated; the tuner and the wait mode stay usable without timing calibration.
- [x] T099 [US2b] End-of-run summary: counts ON_TIME/EARLY/LATE/MISSED/EXTRA plus the technique counts of T063, memory only (FR-056); extend `PRACTICE/session/PassageStatus.java` **Done 2026-10-08: `FollowSummary` (a new class rather than `PassageStatus`, which is the summary of the wait mode and is left untouched): every verdict apart, techniques apart, events not judged apart, pending events of a stopped run, always summing to the events of the run, memory only, nothing written to the song (6 tests, 4 mutations killed); shown by the palette at the end (`FollowView`). The contract has no "best run", so none was added.**
- [x] T100 [P] [US2b] Strings for follow mode, tolerance presets, calibration and the timing verdicts in `share/lang/messages.properties` + `messages_fr.properties` **Done 2026-10-08: English, French and Spanish texts (the project keeps the three in step), `FollowStringsTest` keeps the keys and placeholders equal and every state, verdict and reason covered.**
- [x] T101 [US2b] Benchmark in `ENGINE-TEST/benchmark/` / `PRACTICE-TEST/`: synthetic and recorded runs at the score tempo up to 8 notes/s with deliberate mistakes; report match rate and resynchronisation rate against SC-009 (provisional) in `BENCH/` and `research.md` **Done 2026-10-08 (the benchmark is made and reported, SC-009 and SC-004 are NOT met above 2 notes a second): `FollowBenchmarkTest` (tag benchmark), reports in `benchmark/follow-benchmark-{synthetic,recorded}-2026-10-08.txt`, research R15. Synthetic: 100 % / 86 % / 44 % / 22 % right matches at 2 / 4 / 6 / 8 notes a second; repeated identical notes 41 % with 56 false matches at 4 a second; real recording laid at 82 % of the written tempo: 35 of 42 events matched. Targets unchanged.** **Follow-up 2026-10-08: the false matches of repeated notes (56 at 4 a second) came from the late time of the attack evidence over an identical ringing note; the time now comes from the reported transient, default 56 -> 36 false and 41 -> 51 % right with nothing lost elsewhere; waiting for the report gives 0 false at the price of right matches on other notes (research R15, a product choice left open). SC-009 and SC-004 still not met.**
- [ ] T102 [US2b] Phase E closing verification: full build with tests green; manual quickstart "Phase E" (70% then 100%, deliberate mistake, calibration, built-in mic, back to wait mode unchanged); record results exactly as run
- [x] T149 [US2b] Bug (owner, 2026-10-29, test sheet `specs/test-sheets/F01` step 2, found while running T102): in the preparation window the start button stayed greyed with no word of why (the input had heard nothing yet: level UNCHECKED; only the one-click start said "play a few notes"). Owner's decision: greyed, but explained. Small bug fix outside the full Spec Kit flow, by the bug-fix protocol: `GreyedStartExplainedTest` written first and seen failing (compilation: `QuickStart.greyedReason` absent), then `QuickStart.greyedReason` and a line under the start position in `PracticeWindow.updateStartButton` (keys `practice.start.greyed.UNCHECKED` / `.UNUSABLE`, en/fr/es); the FAQ of PRACTICE, PRACTICE-FR and PRACTICE-ES says it. Seen on screen: not yet (the owner's rerun of F01).

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

- [x] T126 [P] Create `ENGINE-TEST/testsupport/WavSource.java`: loads a PCM WAV (mono, 48 kHz, 16-bit; other rates rejected with a clear message) into `float[]` for tests, using `javax.sound.sampled` only **Done by what exists (2026-10-06): the test WAV readers are `RecordedAudio` (any PCM rate and depth, mono mix, .m4a through afconvert), `MultiChannelWav` (hexaphonic) in the practice tests and `BenchmarkFixtures.readRecording` in the engine tests; no equivalent class was added.**
- [x] T127 [P] Create the audio-input stub `ENGINE-TEST/testsupport/FakeAudioInput.java`: writes samples into the engine's `SampleRingBuffer` either at real-time pace or as fast as possible, replacing `CaptureService`/Java Sound; add a test factory for `AudioEngine` (the package-private constructor taking `InputDeviceCatalog`, `CurrentInputSelection`, ring-buffer capacity, `LevelAnalyzer` and estimator factory already exists; expose a test-support entry point rather than widening the public API) **Done: `FakeAudioInput` (engine tests, in the engine package because the engine's constructor and ring buffer are package-private) feeds the ring buffer as fast as the analysis consumes or in real time; `LiveEngineEquivalenceTest` shows the live engine and `OfflineAnalysis` give the same frames, so the benchmarks measure what the learner gets; the engine tests are shared with the practice tests through a test-jar.**
- [x] T128 [P] Create `PRACTICE-TEST/testsupport/FakeTempoClock.java` and `FakeMidiPlayer.java` (stubs for player start/stop events, tick position and count-in) used by the tempo-mode tests **DEFERRED with the tempo mode (Phase 9): the stubs follow the design of the clock and the player events, they are not created before it.** **Done 2026-10-08: `FakeTempoClock` (position, ticks, time advance by the test, start, pause, resume, seek, stop, not ready; tempo, tempo changes, repeats and alternative endings through the function of a `FollowTimeline`) and `FakeMidiPlayer` (on the new narrow port `FollowPlayer`: start, count-down, stop, notifications, learner's play, end of the song, player-only mute, percentage, count-down and loop given back, ordered calls), 6 tests of their own against the timeline; no thread, no sleep.**
- [x] T129 [P] Extend `PRACTICE-TEST/model/SongFixture.java` with builders for songs with a legato pair, a bend with path, a natural harmonic, a chord strum, a tempo change and a repeat, so functional tests do not depend on files **Done for legato pair, chord strum, bend with path, natural harmonic, tempo change and repeat (`SongFixture`, `SongFixtureBuildersTest`).**
- [x] T130 [SPIKE] Find freely redistributable real guitar recordings on the internet (e.g. freesound.org with the CC0 filter, public-domain or CC0 sample packs; check the licence of research datasets such as GuitarSet or IDMT-SMT-Guitar before any use) and record for each candidate its URL, author, licence and whether redistribution in this repository is allowed in `BENCH/SOURCES.md`; rule inherited from the MVP: only CC0-1.0 or public-domain clips of known authorship are committed, anything with another licence stays out of the repository or needs the owner's written approval **Done 2026-10-06:** research datasets found and downloaded locally (not redistributable, never committed); see spec 004 and research R9.
- [ ] T131 [P] Download and cut the accepted clips (single notes E2…E5, open strings, chord strums, hammer-on / pull-off, slide, bend, vibrato, palm mute, natural harmonic, noise-only) to short mono 48 kHz 16-bit WAVs (at most about 3 s each) in `desktop/TuxGuitar-audio-engine/src/test/resources/samples/` with a licence file and a provenance table beside them; no network access at test time **DEFERRED (owner, 2026-10-06: no recordings; no clip of unclear licence): nothing is downloaded or cut until a CC0 or public-domain source of known authorship is chosen and recorded in the provenance table.**

### Functional tests (engine → session, headless, with the stubs)

- [x] T132 [US2] `ENGINE-TEST/functional/NotePracticeFunctionalTest.java`: real single-note clips through `FakeAudioInput` → `AudioEngine` → `PracticeSession` on a `SongFixture` score: the right note is accepted (✓), a wrong neighbour gives ✗, silence and noise never advance; record latency to `BENCH/` **Done 2026-10-06:** covered by `RecordedPracticeReplayTest`, `MonophonicRegressionReplayTest` and `DatasetBenchmarkTest` (spec 004).
- [x] T133 [US2] Fast-run functional test with the T002 synthetic runs and, when available, real run clips: recognition rate and false accepts at 4…12 notes per second reported and compared with SC-002/SC-004 **Done: `FastRunFunctionalTest` (default suite, synthetic plucks, 3 to 12 notes per second; no wrong note accepted, floor at 3 and 4 per second) and `FastPassageBenchmarkTest` (real notes); compared with SC-002 and SC-004 in research R3 closing.**
- [x] T134 [US2] Chord functional test with real strum clips: complete chord accepted, missing note reported, "not verifiable" pitches never claimed heard **Done 2026-10-06:** covered by `DatasetBenchmarkTest` (chords, incomplete chords refused 96-98 %); the "not verifiable" labels shown to the learner are still open (spec 004 T008).
- [x] T135 [US8] Functional test of the front end: weak and noisy clips with and without gain, gate and subtraction; results reported as a with/without table in `BENCH/` **Done: noisy clips were in `FrontEndBenchmarkTest` (`frontend-with-without.csv`); weak clips with and without gain, gate and subtraction added (`WeakInputBenchmarkTest`, research R12): gain +6 dB rescues notes at -45 dBFS, gate and subtraction do not help and can remove the notes.**
- [x] T136 [US7] Functional technique tests, one per technique family (legato, slide, bend, vibrato, palm mute, tremolo picking, harmonics): a clip plus the written technique gives the expected verdict; plain clips never give a false "detected" beyond the agreed rate **Done for legato, slide, vibrato, palm mute, tremolo picking, bend and natural harmonic (`TechniqueFamiliesFunctionalTest`, `LegatoFunctionalTest`): the plain counterpart is accepted, no technique is ever reported as heard (none is verified); legato destination without attack is accepted at 105-150 ms on synthetic steps and a legato run is followed in full from 150 ms spacing (2 of 4 at 90-110 ms): the baseline for the hammer-on / pull-off work.**
- [x] T137 [US2b] Functional tempo test: `FakeTempoClock` plus clips with known onset times give the expected ON_TIME/EARLY/LATE/MISSED/EXTRA verdicts and the session stays synchronised after an injected mistake **DEFERRED with the tempo mode (Phase 9), after T128.** **Done 2026-10-08: `FollowFunctionalTest` (10 tests on `FakePlayerRun`: ON_TIME/EARLY/LATE/MISSED/EXTRA, a mistake that moves nothing, 70 and 100 %, tempo change + repeat + alternative ending, chord, technique, pause and resume, end of the session and of the song, stop of the learner). It found a real defect on its first run (the segment of a technique was not cut where the next sound begins), fixed.**

### GUI tests (SWT, using the existing contract-test toolkit)

Base: `PRACTICE-TEST/ui/SwtContractToolkit.java` and `RecognitionSettingsSwtTest.java` show how the existing GUI tests create widgets; reuse that mechanism (check how it satisfies SWT's main-thread requirement on macOS and record it in `research.md`).

- [x] T138 [US1] GUI test: with the plugin connected the transport bar shows the Practice item after `transport.start`; clicking opens the practice window once (no second instance); the item's checked state follows open/close; the Tools entry produces the same window *(done as a native SWT test of the button, icon and state in `PracticeToolbarSwtTest`, profile `swt-gui-tests`; clicking the real tool bar item in the full application is covered by the manual check of T022)*
- [x] T139 [US8] GUI test: the gain control exists in both `LevelPanel` and `TunerWindow` and shows the same value; reset returns to neutral; the "suggest" button is visible only when the suggestion switch is on *(done: `GainControlSwtTest` against a stub backend, profile `swt-gui-tests`)*
- [x] T140 [US7] GUI test: the technique chips show detected / not detected / uncertain / not verifiable with their localized text; with technique detection off every chip says "not verifiable" **Done at the level of the text (2026-10-06): the technique line (`TechniqueLine`, shown as a line of text, not chips) is tested in English, French and Spanish for every technique and verdict, and with detection off every written technique says it cannot be checked yet (`TechniqueLineTest`); the palette widget itself needs the application singletons and was checked by hand only (preparation window; the technique line needs a played note).**
- [ ] T141 [US2b] GUI test: mode selector, tolerance preset and tempo percentage change the session parameters; per-event timing feedback is shown **DEFERRED with the tempo mode (Phase 9).** **Mostly done 2026-10-08, not ticked: the choices reach the settings and the session (`FollowPanelTest`, `FollowPanelSwtTest` run locally with the profile swt-gui-tests, `FollowStarterTest`), the feedback per event is tested as text (`FollowViewTest`); the palette widgets themselves are not tested natively (they need the TuxGuitar window singletons). The GitHub workflow T143 was not run (quota).**
- [ ] T142 [SPIKE] Optional GUI visual check: capture a screenshot of `PracticeWindow` and of the transport bar in a test (SWT `GC` on the widget) and compare with a stored baseline within a tolerance; keep it only if it is stable across runs on the macOS build **Not done (optional): stable screenshot comparison is not worth its cost; the windows were captured by hand for the guides (docs/images/features).**
- [ ] T143 Make sure the default `mvn verify` runs the unit, functional and GUI tests (only the `benchmark` tag stays excluded) and add a CI job `.github/workflows/` that runs the fork modules' tests on a macOS runner (the current release workflow uses `-DskipTests`); report what actually ran **Partly done 2026-10-06: the default `mvn verify` runs the unit and functional tests of the fork modules (only `benchmark`, `dataset` and `swt-gui` are excluded) and the macOS workflow `macos-maven.yml` already runs it on macOS 14 and 15 (CI run of 2026-10-06: engine 253, tuner 23, practice 197 at the time, 3 skipped for the missing private recordings); a manual workflow `practice-gui-tests.yml` runs the SWT tests on a macOS runner, **not yet run on the hosted runner**, so the GUI tests in CI are not claimed.**

### Guitar Pro import issues (found 2026-09-30; outside spec 002, tracked here so they are not lost)

Constitution I and the owner's rule "keep upstream TuxGuitar changes separate" apply: fixes here go in their own commits.

- [x] T144 [SPIKE] Normal reference file and negative case for playback. **`Metallica - To Live Is To Die (4).gp4`** (owner's MySongBook Database folder, outside the repository) is the reference for a normal musical read: open, render, MIDI sequence generation and playback without exception. **`Metallica - To Live Is To Die (6).gp4` is a defective file** and is no longer a reference for normal playback: it is kept only as a separate **negative case**: a defective file must never crash TuxGuitar or cause a division by zero; it may be refused with a clear message or opened in a degraded mode, and its musical content is not required to be correct. **Done 2026-10-06:** the cause of the original report is a tempo of 0 in some Guitar Pro files, fixed (commit `0c3255a20`, `GTPInputStream.applyTempo`, `TestGTPTempo`). `TestGPCorpusSmoke` (env `TG_GP_CORPUS`, details printed for up to 5 files): (4) read, 8 tracks, 258 measures, 1 measure error, MIDI sequence of 9085 notes, no exception; (6) read in degraded mode, 3 tracks, 42 measures, 8 measure errors, MIDI sequence of 792 notes, no exception, no division by zero. In the running application, (4) opened (title shown, score rendered), Play then Stop pressed, application alive afterwards. Findings in `research.md` R13. (6) was not opened in the application. No protected file is in the repository (the test reads them through `TG_GP_CORPUS`).**
- [x] T145 Regression tests in `common/TuxGuitar-lib/src/test/java` (or `TuxGuitar-gtp` if it has a test source set): small GP3/GP4 fixtures written by our own `GP4OutputStream`/`GP3` writer covering a valid song, a triplet/quintuplet measure, too-long and too-short measures, a tied note without a previous note, and a bend with two equal points; each is read, passed through `TGSongManager.updatePreciseStart`, checked with `getMeasureErrors`, and its MIDI sequence generated without exception **Done: `TestGTPFiles` writes with our GP3 and GP4 writers a valid song, a triplet and a quintuplet measure, too long and too short measures, a tied note without a previous note and a bend with two equal points; each is read back by content, normalised, checked with `getMeasureErrors` and its MIDI sequence generated: no exception, the errors are reported.**
- [x] T146 Optional corpus smoke test: a test that reads every `.gp3`/`.gp4` of the directory named by the environment variable `TG_GP_CORPUS` and is skipped when it is not set; it reports counts (read, read failures, files with measure errors) without asserting them; the owner's folder of Metallica files is copyright material and is never committed **Done: `TestGPCorpusSmoke` (skipped without `TG_GP_CORPUS`, `TG_GP_CORPUS_LIMIT` caps it) reports counts and asserts nothing; on the Metallica folder: 460 files, 460 read, 0 not read, 313 with measure errors, 0 sequences that could not be generated. No file is committed.**
- [x] T147 Decide with the owner how imported files with measure-duration errors should behave: measured on the 460 Metallica files, 269 of the 457 readable ones open the "measure errors" dialog (898 too-long and 41 415 too-short voices, 1 298 tied-note errors); first determine whether these are defects of the source files or reader mismatches (e.g. compare a measure with Guitar Pro's own rendering), then propose options (keep as is, offer automatic fix, relax the check for imports) **Done 2026-10-06:** after-open dialog removed, `Edit > Display invalid measures` lists every problem on demand (`TGUpdateReadSongController`).
- [x] T148 Check the file-format detection used by the app for files whose extension does not match their content: three files named `.gp3` are Guitar Pro 4 files (`FICHIER GUITAR PRO v4.00/v4.06`); confirm the app opens them by content (my test forced the reader by extension and reported them as unsupported) **Done: the application reads a file by its content first (`TGSongReaderHelper.detectFileFormat`, a detector per Guitar Pro version) and falls back to the extension; `TestGTPFiles` reads Guitar Pro 4 bytes named `gp3` and Guitar Pro 3 bytes named `gp4`; on the Metallica folder 460 of 460 files are read (457 by extension): the three mis-named files open.**

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
