# Tasks: Interactive Practice MVP

**Input**: Design documents from `specs/001-interactive-practice-mvp/` (spec.md, plan.md, research.md,
data-model.md, contracts/, quickstart.md) and `.specify/memory/constitution.md` (v1.1.0).

**Tests**: included. Constitution XI requires tests for new unit-testable logic, and SC-003 requires a
reproducible detection benchmark. Hardware behaviour is verified manually (SC-009), never claimed as
automated.

**Organization**: grouped by user story (spec.md). Stories US1–US3 are P1, US4–US5 P2, US6 P3.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an unfinished task)
- **[Story]**: US1…US6 = spec.md user stories 1…6

## Path conventions (plan.md "Project Structure")

- Engine library: `desktop/TuxGuitar-audio-engine/` (standard Maven layout; package `app.tuxguitar.audio.engine`)
- Evolved tuner plugin: `desktop/TuxGuitar-tuner-next/` (`src/`, `share/`, `test/`; package `app.tuxguitar.app.tools.custom.tunernext`)
- Practice plugin: `desktop/TuxGuitar-practice/` (`src/`, `share/`, `test/`, `test-resources/`; package `app.tuxguitar.app.tools.custom.practice`)
- Core tablature hook: `desktop/TuxGuitar/src/app/tuxguitar/app/view/component/tab/`
- macOS SWT packaging: `desktop/build-scripts/tuxguitar-macosx-swt-cocoa/`
- Legacy tuner `desktop/TuxGuitar-tuner/` and the other eight build-script POMs MUST NOT change (P9).

Build/verify command for every phase (quickstart.md):
`cd desktop/build-scripts/tuxguitar-macosx-swt-cocoa && mvn -e clean verify -P native-modules`
(with `JAVA_HOME=$(/usr/libexec/java_home -v 21)`).

---

## Phase 1: Setup (shared infrastructure)

**Purpose**: create the three new modules and wire the engine and practice plugin into the macOS SWT build only. Invariant for the whole implementation: every packaged application contains exactly one usable tuner — the legacy tuner stays packaged until tuner-next is functional and is swapped atomically in US4 (T005).

- [x] T001 Create `desktop/TuxGuitar-audio-engine/pom.xml`: parent `tuxguitar-pom` (`<relativePath>../</relativePath>`), artifactId `tuxguitar-audio-engine`, standard `src/main/java` / `src/test/java` layout; dependencies `tuxguitar-lib` `${project.version}` (provided), `be.tarsos.dsp:core:2.5` with repository `https://mvn.0110.be/releases` (not Maven Central), `org.junit.jupiter:junit-jupiter-engine:5.9.2` (test); override `maven-compiler-plugin` `<release>11</release>` in this POM only (TarsosDSP 2.5 is Java 11 bytecode, so the module needs a Java 11+ runtime; research.md "Java release of the new modules"); configure surefire to exclude JUnit tag `benchmark` by default. Verify with `mvn help:effective-pom` that only this module has release 11, that no existing module declares a dependency on it, and that the build accepts the TarsosDSP classes
- [x] T002 [P] Create `desktop/TuxGuitar-tuner-next/` skeleton: `pom.xml` (parent `tuxguitar-pom`, artifactId `tuxguitar-tuner-next`, `<sourceDirectory>src</sourceDirectory>`, resources `share`, `<testSourceDirectory>test</testSourceDirectory>`, `<release>11</release>`, provided deps `tuxguitar-lib`, `tuxguitar`, `tuxguitar-ui-toolkit`, `tuxguitar-audio-engine` `${project.version}` with TarsosDSP excluded so only the engine API is visible, junit 5.9.2 test), `share/META-INF/services/app.tuxguitar.util.plugin.TGPlugin` naming a lifecycle-only `src/app/tuxguitar/app/tools/custom/tunernext/TunerNextPlugin.java` (`MODULE_ID` `tuxguitar-tuner`, `ACTION_ID` `tuner.tuner`; T064 turns it into the tool-item plugin), `share/META-INF/tuxguitar-tuner.info` (same file name and fields as `desktop/TuxGuitar-tuner/share/META-INF/tuxguitar-tuner.info`), `share/lang/messages.properties`
- [x] T003 [P] Create `desktop/TuxGuitar-practice/` skeleton: `pom.xml` (same conventions as T002 plus provided `tuxguitar-editor-utils`; artifactId `tuxguitar-practice`; test sources `test/` and test resources `test-resources/` declared, created with the first tests), `share/META-INF/services/app.tuxguitar.util.plugin.TGPlugin` naming a lifecycle-only `src/app/tuxguitar/app/tools/custom/practice/ui/PracticePlugin.java` (`MODULE_ID` `tuxguitar-practice`; T027 turns it into the tool-item plugin), `share/META-INF/tuxguitar-practice.info`, `share/lang/messages.properties`
- [x] T004 Wire the engine and practice plugin into `desktop/build-scripts/tuxguitar-macosx-swt-cocoa/pom.xml` only: add modules `../../TuxGuitar-audio-engine` and `../../TuxGuitar-practice`; copy `tuxguitar-audio-engine.jar` and the TarsosDSP 2.5 jar (once, `Contents/MacOS/lib` only; declare the TarsosDSP repository so the build script can resolve it) to `Contents/MacOS/lib` and `tuxguitar-practice.jar` to `Contents/MacOS/share/plugins`; keep module `../../TuxGuitar-tuner` and its `tuxguitar-tuner.jar` copy unchanged; do **not** add or package `TuxGuitar-tuner-next` (swap in T005). Run the full macOS SWT build, inspect the `.app` (engine, TarsosDSP, practice present; legacy tuner present; tuner-next absent; no duplicate TarsosDSP), and launch it to confirm normal startup, the existing tuner, and no errors from the practice plugin
- [x] T006 Run the Phase 1 closing verification: run the full macOS SWT build (`cd desktop/build-scripts/tuxguitar-macosx-swt-cocoa && mvn -e clean verify -P native-modules` with Temurin 21); compare `git diff --stat master...HEAD` for the protected paths and confirm no changes to the other twelve `desktop/build-scripts/*/pom.xml` (the eight other tuner-bundling variants plus four installer/deb packaging POMs), `desktop/pom.xml`, or `desktop/TuxGuitar-tuner/`; confirm the macOS SWT package contains exactly one usable tuner, the legacy tuner; confirm tuner-next is neither built nor packaged; confirm the audio engine, TarsosDSP (exactly once) and the practice plugin are packaged; confirm the working tree is clean after verification

**Checkpoint**: macOS SWT build green with the engine and an empty practice plugin packaged, the legacy tuner still packaged, tuner-next built nowhere; nothing else changed.

---

## Phase 2: Foundational (blocking prerequisites)

**Purpose**: audio engine core, macOS microphone launcher, practice plugin shell. No user story can be
validated before this phase is complete.

### Engine core (contracts/audio-pitch-engine.md)

- [x] T007 [P] Create `desktop/TuxGuitar-audio-engine/src/main/java/app/tuxguitar/audio/engine/input/AudioInputDescriptor.java` with fields `id` ("mixer name + index + channel"), `deviceName`, `channelIndex` ("0-based channel within the device stream; absent for mono devices"), `channelCount` ("1 for mono"), `displayName` ("device name, plus '— Input k' for multi-channel devices"), `kind` ∈ {`BUILT_IN_MICROPHONE`, `MICROPHONE`, `INSTRUMENT`, `UNKNOWN`}, `isSystemDefault`
- [x] T008 [P] Implement a preallocated lock-free single-producer/single-consumer float ring buffer in `desktop/TuxGuitar-audio-engine/src/main/java/app/tuxguitar/audio/engine/input/SampleRingBuffer.java`: producer and consumer operations are non-blocking, use no locks or `synchronized`, do no logging, and do not allocate in the steady-state read/write path (verified by source inspection and, if useful later, a focused performance test — not claimed from ordinary unit tests). **Agreed policy: when full, accept only available capacity, reject the newest excess samples without blocking and increment a cumulative dropped-sample count; when empty, return zero samples immediately without waiting.** Rejection is an emergency guard only, not acceptable nominal behaviour: buffer sizing and consumption (T085) must prevent overruns in normal use, and the cumulative dropped-sample count is the means of detecting them. Completing T008 requires `desktop/TuxGuitar-audio-engine/src/test/java/app/tuxguitar/audio/engine/input/SampleRingBufferTest.java` covering at least: FIFO ordering; reads from an empty buffer; normal writes and reads; wrap-around; the exact-capacity boundary; overflow behaviour per the agreed policy; recovery after an overflow/full condition; reuse after a complete drain; and block/bulk operations if the implementation exposes them
- [x] T009 [P] Create immutable `desktop/TuxGuitar-audio-engine/src/main/java/app/tuxguitar/audio/engine/analysis/AnalysisFrame.java` with `timestampNanos`, `rmsDbfs`, `peakDbfs`, `clippedRatio`, `levelState` ∈ {`TOO_LOW`, `SUITABLE`, `TOO_HIGH`, `CLIPPING`}, `f0Hz` (NaN when none), `confidence` 0–1, `onset` boolean
- [x] T010 [P] Create the estimator abstraction `desktop/TuxGuitar-audio-engine/src/main/java/app/tuxguitar/audio/engine/analysis/PitchEstimator.java` and `PitchEstimate.java` (f0Hz, probability, pitched)
- [x] T011 Implement `desktop/TuxGuitar-audio-engine/src/main/java/app/tuxguitar/audio/engine/analysis/TarsosFastYinEstimator.java` wrapping TarsosDSP `FastYin` at 48 kHz with a 2048-sample window; it is the only class importing `be.tarsos.*`; window/hop configurable, defaults 2048/256 (research "S3 results")
- [x] T012 [P] Implement `desktop/TuxGuitar-audio-engine/src/main/java/app/tuxguitar/audio/engine/pitch/NoteInterpreter.java` and `PitchReading.java`: `midi` = nearest equal-tempered note with A4 = 440 Hz, `noteName`/`octave` in sounding pitch (E2 = MIDI 40), `cents` "deviation from that note's centre, −50…+50", `reliable` = confidence above a provisional parameter **and** input not unusable
- [x] T013 [P] Implement `desktop/TuxGuitar-audio-engine/src/main/java/app/tuxguitar/audio/engine/pitch/TolerancePolicy.java`: constructor "fails unless `practiceAcceptCents ≥ tunerInTuneCents` and `practiceAcceptCents < 50`"; `tunerState(reading)` → `FLAT`/`IN_TUNE`/`SHARP`/`NO_READING`; `matches(reading, expectedMidi)` true only if reliable, same MIDI note (octave included) and `|cents| ≤ practiceAccept`; both values are constructor parameters (no product constants)
- [x] T014 [P] Implement `desktop/TuxGuitar-audio-engine/src/main/java/app/tuxguitar/audio/engine/analysis/LevelAnalyzer.java`: per-hop RMS/peak dBFS, clipped-sample ratio, exact-zero ratio (all-zero window = "no signal received — check microphone access and the input", never asserted as a denial); classification thresholds are parameters
- [x] T015 Implement `desktop/TuxGuitar-audio-engine/src/main/java/app/tuxguitar/audio/engine/input/InputDeviceCatalog.java`: enumerate Java Sound mixers supporting `TargetDataLine`, read advertised 16-bit formats for channel count, emit one `AudioInputDescriptor` per mono device and one per usable channel of multi-channel devices, system default first; classify only the built-in microphone positively, everything else `UNKNOWN` unless evidence exists (research R11)
- [x] T016 Implement `desktop/TuxGuitar-audio-engine/src/main/java/app/tuxguitar/audio/engine/input/CaptureService.java`: open the device at its native channel count (never request stereo from a mono device), prefer 48 kHz/16-bit, accept 24-bit converted in Java, de-interleave only the selected channel into `SampleRingBuffer`, run on a dedicated high-priority thread started via `TGThreadManager`, never open on the UI thread (cold open measured up to ~4 s while a permission dialog is shown), `acquire(consumerId)`/`release(consumerId)` close the line at zero consumers (FR-067), report `LineUnavailableException`/format errors/device loss as events, never throw into UI code, never write samples anywhere (FR-068)
- [x] T017 Implement per-`TGContext` singleton `desktop/TuxGuitar-audio-engine/src/main/java/app/tuxguitar/audio/engine/input/CurrentInputSelection.java`: exactly one device/channel, `revision` increments on every change, assessment reset to `UNCHECKED` on change (FR-039), listeners notified off audio threads
- [x] T018 Implement `desktop/TuxGuitar-audio-engine/src/main/java/app/tuxguitar/audio/engine/analysis/AnalysisService.java`: analysis thread consuming hops (default 256) over a 2048 window from the ring buffer with preallocated arrays, producing `AnalysisFrame`s via `LevelAnalyzer` + `PitchEstimator`, `subscribe(listener)`; deterministic for identical input
- [x] T085 Compose the shared engine pipeline in `desktop/TuxGuitar-audio-engine/src/main/java/app/tuxguitar/audio/engine/AudioEngine.java` (contract "Input service (per `TGContext`)"), as approved by the product owner: one shared engine per `TGContext`, created lazily via `TGSingletonUtil`, holding one `SampleRingBuffer`, `CaptureService` and `AnalysisService` over the shared `CurrentInputSelection`; capture is active only through consumers' `acquire`/`release`; analysis is active only while at least one subscriber exists; the ring-buffer capacity defaults to 48000 mono samples and is configurable; the cumulative dropped-sample count is a diagnostic for tests and measurements only (not shown to the learner); at each capture opening, analysis is rebuilt for the sample rate actually negotiated (no resampling), with a single analysis consumer at any time; provisional configurable level defaults: too low below −40 dBFS RMS, too high above −6 dBFS, clipped sample at 0.99 of full scale, clipping state above 10 % clipped samples (refined in T076). Add `desktop/TuxGuitar-audio-engine/src/test/java/app/tuxguitar/audio/engine/AudioEngineTest.java`: samples fed at the nominal rate produce zero dropped samples; a forced overrun increases the count and analysis recovers
- [x] T019 [P] Create deterministic synthetic fixtures `desktop/TuxGuitar-audio-engine/src/test/java/app/tuxguitar/audio/engine/testsupport/SyntheticSignals.java` (sine + harmonics, Karplus–Strong plucks with fixed seeds, ±cents detune, noise, clipping, silence, repeated plucks, 2-channel interleaving)
- [x] T020 [P] Add `desktop/TuxGuitar-audio-engine/src/test/java/app/tuxguitar/audio/engine/pitch/NoteInterpreterTest.java` and `TolerancePolicyTest.java` (A4 = 440, E2 = 40, cents sign, constructor invariant, adjacent semitone never matches, wrong octave never matches)
- [x] T021 [P] Add `desktop/TuxGuitar-audio-engine/src/test/java/app/tuxguitar/audio/engine/analysis/TarsosFastYinEstimatorTest.java` on synthetic signals: E2…B5 and Drop D D2 correct note/cents, strong-2nd/3rd-harmonic octave cases, silence/noise not reliable
- [x] T022 [P] Add `desktop/TuxGuitar-audio-engine/src/test/java/app/tuxguitar/audio/engine/analysis/LevelAnalyzerTest.java` and `input/DeinterleaveTest.java` (selected channel only, no mixing, silent channel reports no signal and never switches)
- [x] T023 Add the accepted builtin-mic recordings as **clean, high-quality real-guitar reference fixtures** (approved by the product owner) and a real-audio regression test. They complement, never replace, the synthetic tests (T019–T022) and are not assumed to represent all guitars, microphones, noisy rooms or playing styles. (a) Copy only the 22 accepted, verified WAVs from the scratch staging directory into `desktop/TuxGuitar-audio-engine/src/test/resources/benchmark/builtin-mic/standard/` byte-for-byte (no resampling, trimming or normalisation; check SHA-256 against the source); never copy `discarded/` clips or `setup-*.properties`. (b) Write `benchmark/manifest.csv` from the staging manifest plus `expected_note`, `played_note`, `truth_source` (`recording-plan` for clips played as instructed; `unverified` for clips 18–21), `fixture_role` (`reference`, `silence`, `known-issue`, `unverified`) and `sha256`; mark 07 (E4 open) and 15 (E5) `known-issue` (other strings ringing −4/−1 dB, research "S3"). Clips 18–21 (requested wrong semitones) are kept unchanged but `unverified`: the owner reported during recording that the expected notes may have been played instead, and a later pitch analysis is not independent ground truth for testing pitch detection; leave `played_note` empty. (c) Copy `pairings.csv` with a `status` column; wrong-note pairings are only asserted when every clip involved has an independently confirmed `played_note` (manual listening against reference pitches, an analysis method independent of FastYin, or another trustworthy source), so all current pairings (18–21) are `disabled`. (d) Add `benchmark/LICENSE` with the full CC0 1.0 legal code. (e) Add `desktop/TuxGuitar-audio-engine/src/test/java/app/tuxguitar/audio/engine/benchmark/RealGuitarRegressionTest.java`, driven only by the manifest, running FastYin through the engine's `PitchEstimator` (T011) and `NoteInterpreter` (T012) and asserting per `reference` clip: the dominant and first stable note equal `played_note`; no persistent octave error (octave-off frames bounded over the sustain); time from onset to stable detection bounded; and for the silence clip, no stable guitar-range note. Confidence gate, stability window, octave-frame and latency bounds are **test parameters** whose initial values and evidence are recorded in `research.md` — do not encode the live-demo 0.90 / 5-frame values as product requirements. `known-issue` clips are run and reported but not asserted until T077; `unverified` clips are run for information only, with no expected-vs-wrong-note assertions. The repeated-note clip (22) validates stable pitch only; articulation/re-pluck stays in `ArticulationDetectorTest` (T035). The regression test runs in the default build if it stays fast (S3 measured 0.046 ms per 2048 frame); otherwise tag it `benchmark` and run it explicitly with `-Dgroups=benchmark -DexcludedGroups=`, suitable for CI. Also add `BenchmarkRunnerTest.java` (tagged `benchmark`) reporting per input type to `specs/001-interactive-practice-mvp/benchmark/` for T076

### macOS microphone launcher (Q-L option A)

- [x] T024 [P] Write `desktop/build-scripts/tuxguitar-macosx-swt-cocoa/launcher/tuxguitar-launcher.c`: resolve its own directory, `posix_spawn("/bin/sh", "<Contents/MacOS>/tuxguitar.sh", argv…)`, wait, return the script's exit status; no other logic (launch/classpath stay in `tuxguitar.sh`)
- [x] T025 Extend `desktop/build-scripts/tuxguitar-macosx-swt-cocoa/pom.xml` to compile the stub with clang for the build machine architecture **in every SWT build** (not only `-P native-modules`), ad-hoc sign it with identifier `app.tuxguitar`, place it in `Contents/MacOS/`, and after copying `common-macosx` set the packaged `Info.plist` `CFBundleExecutable` to `tuxguitar-launcher` and add `NSMicrophoneUsageDescription` (the shared `common-resources/common-macosx/Contents/Info.plist` stays unchanged)
- [x] T026 Validate the packaged app manually: launch via `open`, confirm the permission dialog, confirm in `log show --predicate 'process == "tccd"'` that the responsible client is `app.tuxguitar`, rebuild and relaunch to see whether the grant persists; record results in `specs/001-interactive-practice-mvp/research.md`

### Practice plugin shell

- [x] T027 Implement `desktop/TuxGuitar-practice/src/app/tuxguitar/app/tools/custom/practice/ui/PracticePlugin.java` (`TGToolItemPlugin`, item `practice.open`) and register `action.practice.start`, `action.practice.skip`, `action.practice.resume`, `action.practice.stop` via `TuxGuitar.getInstance().getActionManager().mapAction`, `TGActionAdapterManager.getKeyBindingActionIds().addActionId` and `getSyncThreadInterceptor().addActionId`; unregister in `disconnect` (contracts/actions-and-shortcuts.md)
- [x] T028 Add default bindings for the four practice actions to `desktop/TuxGuitar/dist/tuxguitar-shortcuts.xml` using combinations absent from that file (check digits, `Space`, `F9`, `Ctrl t`, …), and editor labels in `desktop/TuxGuitar-practice/share/lang/messages.properties`. Approved defaults (2026-09-26): `F2` for `practice.open`, `Ctrl Shift` + `s`/`k`/`r`/`x` for start/skip/resume/stop; macOS builds replace the shared file with `desktop/build-scripts/common-resources/common-macosx/Contents/MacOS/dist/tuxguitar-shortcuts.xml`, which gets the same entries with `Command Shift`
- [x] T029 Create the non-modal practice window shell `desktop/TuxGuitar-practice/src/app/tuxguitar/app/tools/custom/practice/ui/PracticeWindow.java` using only `app.tuxguitar.ui.*`, calling `KeyBindingActionManager.appendListenersTo` on its controls
- [x] T030 Create the session thread and command queue `desktop/TuxGuitar-practice/src/app/tuxguitar/app/tools/custom/practice/session/SessionLoop.java` (single writer of session state; consumes `AnalysisFrame`s and learner commands; UI gets coalesced snapshots ≤ ~30/s via `TGSynchronizer.executeLater`)

**Checkpoint**: engine tests green; packaged app prompts for the microphone and captures; practice menu item and actions registered; build green.

---

## Phase 3: User Story 1 — Practise a melody note by note (P1) 🎯 MVP

**Goal**: wait-for-correct-note practice on one learner-accepted Practice Track with the practice highlight.

**Independent test**: open a song whose selected track contains only single pitched notes, choose Interactive
Practice, go through preparation with a usable input, start, play the first notes correctly: each is accepted in
turn; silence, uncertain input, wrong notes and wrong octaves never advance (spec US1).

### Tests for User Story 1

- [x] T031 [P] [US1] Add `.tg` fixtures (repeats + alternate endings, two-voice overlap, ties, grace with/without pitch, dead, dead-only chord, harmonic, bend, offsets 0/+2/−1/−8) in `desktop/TuxGuitar-practice/test-resources/` and `desktop/TuxGuitar-practice/test/app/tuxguitar/app/tools/custom/practice/model/PracticeSequenceBuilderTest.java` checking order via `MidiRepeatController(song,-1,-1)`, classification, and "`sequenceIndex` strictly increasing; every `EXPECTED_NOTE` has `expectedMidi` in 0–127; `MULTI_NOTE` never carries `expectedMidi`". Implemented with songs built in code (`SongFixture`) plus a .tg write/read round trip instead of committed binary .tg files
- [x] T032 [P] [US1] Add `desktop/TuxGuitar-practice/test/app/tuxguitar/app/tools/custom/practice/model/PracticeTrackCandidatesTest.java`: excluded `PERCUSSION`, `NOT_SIX_STRINGS`, `UNDEFINED_STRING_PITCH`, `NO_PRACTICE_EVENTS`; preselection caret track first, else GM program 24–31 hint; acceptance always required; confirmation memory per song document + track
- [x] T033 [P] [US1] Add `desktop/TuxGuitar-practice/test/app/tuxguitar/app/tools/custom/practice/model/TrackTuningTest.java`: `expectedMidi` = offset + open string + fret; `physicalTargetMidi[i]` = open string if offset ≥ 0 else open string + offset; `unusualSetup` only for negative offsets below per-string minima of the built-in Guitar › 6-String presets
- [x] T034 [P] [US1] Add `desktop/TuxGuitar-practice/test/app/tuxguitar/app/tools/custom/practice/session/PracticeSessionTest.java` with scripted `AnalysisFrame`s: silence/uncertain/incorrect never advance (FR-007), correct advances, same-pitch next note needs an articulation event after the previous acceptance (FR-022), completion and Restart to the original start (FR-032/033)
- [x] T035 [P] [US1] Add `desktop/TuxGuitar-audio-engine/src/test/java/app/tuxguitar/audio/engine/analysis/ArticulationDetectorTest.java` with the S4 synthetic cases (re-plucks, weak re-pluck, fast repeats, vibrato, beating, clicks, dropout, plain sustain) asserting no false articulation on sustain/vibrato/click/dropout

### Implementation for User Story 1

- [x] T036 [P] [US1] Implement `desktop/TuxGuitar-practice/src/app/tuxguitar/app/tools/custom/practice/model/PracticeEvent.java` per data-model.md (`kind` ∈ {`EXPECTED_NOTE`, `MULTI_NOTE`, `AUTO_PASS`}, `autoPassReason`, `sequenceIndex`, `playIndex`, `beat`, `measureIndex`, `voiceIndex`, `stringNumber`, `fret`, `expectedMidi`, `isGrace`, display-only `techniques`, `sameAsPrevious`)
- [x] T037 [P] [US1] Implement `desktop/TuxGuitar-practice/src/app/tuxguitar/app/tools/custom/practice/model/TrackTuning.java` (`openStringMidi[6]`, `offset`, `realization` `NONE`/`CAPO_EQUIVALENT(k)`/`TUNE_DOWN(k)`, `physicalTargetMidi[6]`, `unusualSetup` from `TuningManager.getTgTuningsGroup()` read at runtime, user custom tunings excluded)
- [x] T038 [US1] Implement `desktop/TuxGuitar-practice/src/app/tuxguitar/app/tools/custom/practice/model/PracticeSequenceBuilder.java`: walk `MidiRepeatController(song, -1, -1)`, classify per data-model rules (rests/ties none; simultaneous notes → `MULTI_NOTE` or `DEAD_ONLY_CHORD`; dead/bend/harmonic/tremolo bar/trill → `AUTO_PASS`; grace → preceding event), `expectedMidi = offset + stringValue + fret` (player transpose ignored), start index parameter (default first event). An expected pitch outside 0–127 throws (not reachable with standard offsets and frets)
- [x] T039 [US1] Implement `desktop/TuxGuitar-practice/src/app/tuxguitar/app/tools/custom/practice/model/PracticeTrackCandidates.java`, `PracticeTrackSelection.java` (`accepted` set only by explicit learner action) and `GuitarConfirmationMemory.java` (per `TGContext`, run only, never saved) (FR-003)
- [x] T040 [US1] Implement `desktop/TuxGuitar-audio-engine/src/main/java/app/tuxguitar/audio/engine/analysis/ArticulationDetector.java` (spectral-flux transient AND post-vs-pre-maximum level gate; thresholds are parameters, biased toward false negatives per FR-022) and set `AnalysisFrame.onset` from it in `AnalysisService.java`. Onsets are reported `ArticulationDetector.DECISION_DELAY_HOPS` (13) hops after their transient because the level gate needs the level after it; provisional defaults from S4 (flux 1.5 × median + 5, level > −50 dB, 12-hop refractory, gate +1.5 dB)
- [x] T041 [US1] Implement `desktop/TuxGuitar-practice/src/app/tuxguitar/app/tools/custom/practice/session/PracticeSession.java` state machine (PREPARING → RUNNING → PAUSED → READY_TO_RESUME → RUNNING; COMPLETE; STOPPED) with acceptance rule "reliable, `midi == expectedMidi`, `|cents| ≤ practiceAcceptCents`, stable for the stability window, and (if `sameAsPrevious`) `articulationArmed`"; stability window is a provisional parameter. An onset arms same-pitch acceptance only when its transient (frame time minus the detector delay) is later than the previous acceptance; Skip and unsupported events are left to US5 and detailed feedback to US2; stability window, confidence gate and tolerances are constructor parameters
- [x] T042 [US1] Core hook: in `desktop/TuxGuitar/src/app/tuxguitar/app/view/component/tab/TablatureEditor.java` register for `TGExternalBeatViewerEvent`, store the beat on the `Tablature`, request a redraw on the UI thread (research R18)
- [x] T043 [US1] Core hook: in `desktop/TuxGuitar/src/app/tuxguitar/app/view/component/tab/Tablature.java` add an optional external beat; `isRunning(TGBeat)`/`isRunning(TGMeasure)` also return true for it only while `MidiPlayer` is not running; no behaviour change when none is set. While an external beat is shown and the player is stopped, `isRunning` is true only for that beat and its measure, whatever the caret's track
- [x] T044 [US1] Core hook: in `desktop/TuxGuitar/src/app/tuxguitar/app/view/component/tab/TGControl.java` paint the external beat with `getViewLayout().paintPlayMode(...)` after the full repaint when the player is stopped, and scroll to its measure once when it changes; caret handling untouched. The highlight and the one-time scroll apply only when the beat's track is displayed (all tracks in multitrack view)
- [ ] T045 [US1] Implement `desktop/TuxGuitar-practice/src/app/tuxguitar/app/tools/custom/practice/ui/PracticeHighlight.java` calling `TGExternalBeatViewerManager.showExternalBeat(beat)` / `hideExternalBeat()` (hide whenever the session stops); never move the normal cursor
- [ ] T046 [US1] Build the preparation step in `PracticeWindow.java`: Practice Track selector with explicit acceptance, tuning + raw offset + realization + physical targets + non-blocking "unusual setup" notice (FR-008), Start (disabled until a track is accepted and input is usable)
- [ ] T047 [US1] Build the running view in `PracticeWindow.java`: expected note + octave, string/fret labelled as the tablature's instruction, detected note + cents, feedback state, input health, Skip/Stop/Resume; completion with Restart/Close (FR-028, FR-029, FR-032, FR-033)
- [ ] T048 [US1] Wire events in `desktop/TuxGuitar-practice/src/app/tuxguitar/app/tools/custom/practice/session/SessionEvents.java`: `MidiPlayerEvent.NOTIFY_STARTED` → pause (PLAYBACK) with explicit Resume (FR-065); `TGUpdateEvent` `MEASURE_UPDATED`/`SONG_UPDATED`/`SONG_LOADED` → stop (FR-066); `SELECTION` never stops or retargets (FR-003)
- [ ] T049 [US1] Manually execute the US1 acceptance scenarios and quickstart Phase 1/2/6 rows on the validation target; record results in `specs/001-interactive-practice-mvp/checklists/acceptance.md`

**Checkpoint**: US1 independently testable (MVP core).

---

## Phase 4: User Story 2 — Understand what went wrong (P1)

**Goal**: tuner-style, learner-understandable feedback.

**Independent test**: with a known expected note, play the neighbouring semitone, the same note an octave away,
the note slightly sharp and slightly flat, and a very quiet sound; each gives the matching distinct feedback and
only the slightly sharp/flat expected note is accepted (spec US2).

- [ ] T050 [P] [US2] Add `desktop/TuxGuitar-practice/test/app/tuxguitar/app/tools/custom/practice/session/FeedbackTest.java`: `INCORRECT(detected, octaveOnly)` for wrong octave, detected cents relative to the detected note (never to a different expected semitone), `UNCERTAIN` on unreliable or suboptimal input
- [ ] T051 [US2] Implement `desktop/TuxGuitar-practice/src/app/tuxguitar/app/tools/custom/practice/session/Feedback.java` (`WAITING`, `UNCERTAIN`, `INCORRECT(detected, octaveOnly)`, `CORRECT`) and use it in `PracticeSession.java` (FR-018, FR-019, FR-023, FR-026)
- [ ] T052 [US2] Present feedback in `PracticeWindow.java`: detected note + octave + own-cents sharp/flat, expected note separately, octave hint, text/symbol cues not relying on colour (FR-017, FR-070); no wording implying string, fret or technique verification (FR-027)
- [ ] T053 [P] [US2] Add learner-worded English messages for all feedback states and hints to `desktop/TuxGuitar-practice/share/lang/messages.properties` (FR-069, FR-071)
- [ ] T054 [US2] Manually execute the US2 acceptance scenarios; record in `specs/001-interactive-practice-mvp/checklists/acceptance.md`

---

## Phase 5: User Story 3 — Set up and monitor the audio input (P1)

**Goal**: shared input/channel selection, level check, calibration guidance, headphone advice, failure recovery.

**Independent test**: select interface input then built-in mic; level indication responds; headphone advice for
the built-in mic; level check reports too-low/suitable/too-high/clipping as gain changes; suitable starts,
suboptimal warns, unusable blocks; deny access, unplug, and start with no input each give a clear message
(spec US3).

- [ ] T055 [P] [US3] Add `desktop/TuxGuitar-audio-engine/src/test/java/app/tuxguitar/audio/engine/analysis/InputAssessorTest.java`: `SUITABLE`/`SUBOPTIMAL`/`UNUSABLE` with reasons `NO_SIGNAL`, `TOO_LOW`, `TOO_HIGH`, `CLIPPING`, `DEVICE_LOST`, `ACCESS_DENIED_SUSPECTED` (all-zero window); thresholds are test parameters
- [ ] T056 [US3] Implement `desktop/TuxGuitar-audio-engine/src/main/java/app/tuxguitar/audio/engine/analysis/InputAssessor.java` (quick level check on the selected channel only while the learner plays; `runLevelCheck()`; FR-038)
- [ ] T057 [US3] Implement the shared input/channel selector `desktop/TuxGuitar-practice/src/app/tuxguitar/app/tools/custom/practice/ui/InputSelectorPanel.java` (logical inputs from `InputDeviceCatalog`, selection via `CurrentInputSelection`, new level check on change, "try another input channel" hint, never auto-switch; FR-035, FR-039)
- [ ] T058 [US3] Implement the level meter + assessment display `desktop/TuxGuitar-practice/src/app/tuxguitar/app/tools/custom/practice/ui/LevelPanel.java` (too low/suitable/too high/clipping with text cues; suboptimal warning; Start disabled while unusable; FR-037, FR-038)
- [ ] T059 [US3] Implement calibration/gain guidance in `LevelPanel.java`: explicit recalibrate; manual guidance to the interface gain knob or macOS Sound input settings; no automatic gain adjustment (S2; FR-040–FR-042)
- [ ] T060 [US3] Implement the non-blocking headphone recommendation in `PracticeWindow.java` for the built-in microphone, identified microphones and `UNKNOWN` inputs, not for positively identified instrument inputs (FR-036)
- [ ] T061 [US3] Implement mid-session input handling in `PracticeSession.java`/`SessionEvents.java`: suboptimal → continue with warning and `UNCERTAIN`; unusable/device lost/input changed → pause at the current event, explain, monitor or allow another input, resume only by explicit Resume after a new check (FR-043, FR-044, FR-063)
- [ ] T062 [US3] Manually execute the US3 scenarios and quickstart Phase 3 rows (built-in mic gain sweep, unplug, silent channel, permission behaviour); record in `specs/001-interactive-practice-mvp/checklists/acceptance.md`

**Checkpoint**: US1–US3 (all P1) together form the MVP.

---

## Phase 6: User Story 4 — Tune the guitar and check detection (P2)

**Goal**: the single evolved tuner (macOS SWT build), reachable standalone and from practice preparation.

**Independent test**: open the tuner without practice and from preparation (same tuner); chromatic mode with no
song; guided Standard E, Drop D and custom; Drop D track offered from the track; too low/clipping shows an input
problem; opening during a session pauses practice; FR-059 capabilities preserved (spec US4).

- [ ] T063 [P] [US4] Add `desktop/TuxGuitar-tuner-next/test/app/tuxguitar/app/tools/custom/tunernext/TunerTuningTest.java`: `effectiveTuning` = explicit choice, else Practice Track (practice context) or guitar-confirmed selected track (standalone), else Standard E (FR-052, FR-055); track targets use physical open-string targets; custom = one pitch per string
- [ ] T064 [US4] Implement `desktop/TuxGuitar-tuner-next/src/app/tuxguitar/app/tools/custom/tunernext/TunerNextPlugin.java` (`TGToolItemPlugin`, item name `tuner.tuner`, `getModuleId()` = `tuxguitar-tuner`)
- [ ] T065 [US4] Implement `TunerTuning.java` and `TunerSessionState.java` in `desktop/TuxGuitar-tuner-next/src/app/tuxguitar/app/tools/custom/tunernext/` (presets from `TuningManager` Guitar › 6-String, at least `E Tuning` and `Dropped D`; custom pitch limits taken from TuxGuitar's tuning model after inspecting its source; run-only retention)
- [ ] T066 [US4] Implement `desktop/TuxGuitar-tuner-next/src/app/tuxguitar/app/tools/custom/tunernext/TunerWindow.java`: chromatic and guided modes, note/octave/cents, flat/in tune/sharp via `TolerancePolicy.tunerState`, Hz readout, fine/rough widgets ported from the display logic of `desktop/TuxGuitar-tuner/src/.../TGTunerFineWidget.java` and `TGTunerRoughWidget.java` (not the FFT engine), clickable string targets, input/channel selector and level status, input problem instead of confident readings (FR-048–FR-056). Product-owner requirement (2026-09-26): an animated needle — left = flat (too low), centre = in tune, right = sharp (too high) — shown together with the detected note and a text state (not colour alone)
- [ ] T067 [US4] Implement `desktop/TuxGuitar-tuner-next/src/app/tuxguitar/app/tools/custom/tunernext/TunerSettingsDialog.java` with device/channel, level status and recalibration only — no FFT/buffer/sample-rate/sample-size/noise-gate controls, no reused old control names (FR-059)
- [ ] T068 [US4] Integrate with practice in `PracticeWindow.java`/`SessionEvents.java`: "Tune guitar" processes action id `tuner.tuner`; opening the tuner pauses the session and closing returns to the paused event (FR-058, FR-044); tuner/track mismatch warning before start (FR-062)
- [ ] T005 [US4] Atomic tuner swap in `desktop/build-scripts/tuxguitar-macosx-swt-cocoa/pom.xml`, only once tuner-next is functional (after T064–T068): in one change, replace module `../../TuxGuitar-tuner` with `../../TuxGuitar-tuner-next` and the `tuxguitar-tuner.jar` copy with `tuxguitar-tuner-next.jar` (before: legacy packaged, tuner-next not; after: tuner-next packaged, legacy not). Add a packaging check that fails the build unless `Contents/MacOS/share/plugins` contains exactly one tuner implementation (never both `tuxguitar-tuner.jar` and `tuxguitar-tuner-next.jar`; research R19: they share module id `tuxguitar-tuner`), and verify that the packaged tuner reports module id `tuxguitar-tuner` and registers action `tuner.tuner`. The other eight builds keep the legacy tuner
- [ ] T069 [US4] Manually execute US4 scenarios, the FR-059 regression checks and the macOS SWT package-content check (quickstart Phase 4 rows); record in `specs/001-interactive-practice-mvp/checklists/acceptance.md`

---

## Phase 7: User Story 5 — Get past events the MVP cannot validate (P2)

**Goal**: multi-note events wait for Skip; dead notes, bends, harmonics, tremolo bar and trills pass
automatically; Skip for any event.

**Independent test**: practise a track with a chord, a bent note, a harmonic and dead notes: auto-passes are
briefly indicated and never shown as correct; the chord waits for Skip; skipped events are marked skipped
(spec US5).

- [ ] T070 [P] [US5] Add `desktop/TuxGuitar-practice/test/app/tuxguitar/app/tools/custom/practice/session/UnsupportedEventsTest.java`: `AUTO_PASS` advances with outcome `AUTO_PASSED`; `MULTI_NOTE` gives no judgement and advances only on Skip; Skip on any event → `SKIPPED`, never `CORRECT` (FR-011–FR-013, FR-025, FR-030, FR-031)
- [ ] T071 [US5] Implement auto-pass and Skip handling in `PracticeSession.java` and brief auto-pass indications plus the multi-note "not supported in this version" message with Skip, and skipped-event marking, in `PracticeWindow.java` (FR-016)
- [ ] T072 [US5] Manually execute US5 scenarios; record in `specs/001-interactive-practice-mvp/checklists/acceptance.md`

---

## Phase 8: User Story 6 — Start from a chosen position (P3)

**Goal**: start from the normal score cursor; no position picker.

**Independent test**: place the cursor mid-track (including inside a repeat), start, and confirm the preparation
step shows that position and practice follows playback order to the end; moving the cursor or selecting another
track during the session changes nothing (spec US6).

- [ ] T073 [P] [US6] Add start-resolution cases to `PracticeSequenceBuilderTest.java`: cursor on an event, between events, on another track, inside a repeated section (first played occurrence, no rewind) (FR-004–FR-006)
- [ ] T074 [US6] Implement cursor-based start resolution in `PracticeSequenceBuilder.java` and show the resolved start in the preparation step of `PracticeWindow.java`
- [ ] T075 [US6] Manually execute US6 scenarios; record in `specs/001-interactive-practice-mvp/checklists/acceptance.md`

---

## Phase 9: Polish & cross-cutting concerns

- [ ] T076 [P] Run the tagged benchmark (`mvn test -pl ../../TuxGuitar-audio-engine -am -Dgroups=benchmark -DexcludedGroups= -Dsurefire.failIfNoSpecifiedTests=false`), then tune the provisional parameters (confidence gate, stability window, practice/tuner tolerances, level classes, articulation thresholds) and record chosen values and evidence in `specs/001-interactive-practice-mvp/research.md` (still provisional until acceptance)
- [ ] T077 [P] Octave-error mitigation and other-string interference: investigate the G3-fretted/F#4 cases, pluck-transient subharmonics, clips 07/15 and low-frequency false readings in silence (e.g. guitar-range limiting) in `AnalysisService.java`/`NoteInterpreter.java`; add regression tests; record in research.md
- [ ] T078 Validate direct USB/interface input with the owner's electric guitar and USB adapter: record interface clips with the scratch recorder workflow, add them as `interface/` fixtures after confirmation, run the benchmark per input type (SC-003, FR-045)
- [ ] T079 Validate multi-channel input (P2) on a real multi-input interface: channel k ↔ physical input k, de-interleaving, no mixing or auto-switching; if not achievable with Java Sound, return the exact limitation to the product owner (no fallback); record in research.md
- [ ] T080 [P] Measure perceived response (SC-002, provisional 300 ms) and a 30-minute session for responsiveness, crashes (SC-007) and a dropped-sample count that stays at zero (T085) on the validation target; record in research.md
- [ ] T081 [P] Confirm on a build without the practice plugin (e.g. Linux SWT or macOS JFX from the same branch) that the shared shortcut entries are inert (no errors, no practice rows, keys do nothing, saved `shortcuts.xml` has no practice ids) and that tablature rendering/playback highlighting are unchanged (research R14/R18)
- [ ] T082 Write and execute the product-owner manual acceptance checklist `specs/001-interactive-practice-mvp/checklists/acceptance.md` covering every acceptance scenario and all SC-009 items (SC-009)
- [ ] T083 Update `CLAUDE.md` with the operational steps the MVP introduces: launcher compilation needs Xcode Command Line Tools; the three fork modules build at Java release 11 while existing modules stay at release 9 (the constraint itself is already recorded in plan.md and research.md)
- [ ] T084 **Release gate (not an implementation blocker — do not start before a release is planned)**: complete GPLv3 distribution compliance for the combined TuxGuitarAdvanced distribution (licence files, source headers, third-party notices incl. TarsosDSP provenance) per constitution v1.1.0; no public release before this is done

---

## Dependencies & execution order

### Phase dependencies

- Setup (T001–T004, T006) → Foundational (T007–T030) → user stories → Polish. T005 (tuner swap) belongs to US4.
- Within Foundational: T007–T014 in parallel; T015–T018 depend on T007–T014; T085 depends on T008, T016–T018 and precedes the first engine consumer (T029 onward; T027 does not use the engine); T019–T023 depend on the classes they test; T024 ∥ engine work; T025 → T026; T027–T030 after T003.
- T023 (approved) depends on T011 and T012; its bounds are test parameters, not product thresholds.

### User story dependencies

- **US1** needs Foundational. It is the MVP core.
- **US2** needs US1's `PracticeSession`/`PracticeWindow` (T041, T047).
- **US3** needs Foundational; its UI panels plug into `PracticeWindow` (T046). US1 can be exercised with the default input before US3 lands.
- **US4** needs Foundational (engine) and, for T068, US1's window/session. T005 (atomic tuner swap + exactly-one-tuner check) runs after T064–T068 and before T069.
- **US5** needs US1's sequence and session (T038, T041).
- **US6** needs US1's builder (T038).

### Within each story

Tests first (they must fail), then models, then session logic, then UI, then manual acceptance.

### Parallel opportunities

- T002 ∥ T003; T007–T014; T019–T022; T024 ∥ engine tasks; T031–T035; T036 ∥ T037; T050 ∥ T053; T063 ∥ practice work; T076, T077, T080, T081.
- After US1, US3/US4/US5/US6 can proceed in parallel by different people (they touch different files, except shared edits to `PracticeWindow.java`, which must be sequenced).

## Parallel example: User Story 1

```text
T031 PracticeSequenceBuilderTest     T032 PracticeTrackCandidatesTest
T033 TrackTuningTest                 T034 PracticeSessionTest
T035 ArticulationDetectorTest
then: T036 PracticeEvent ∥ T037 TrackTuning, followed by T038 → T041 and the core hook T042 → T043 → T044
```

## Implementation strategy

### MVP first

1. Phase 1 → Phase 2 (engine + launcher + plugin shell).
2. US1 → US2 → US3 (all P1): stop and validate on the target Mac.
3. Then US4, US5, US6, then Polish (T076–T083). T084 only when a release is planned.

### Incremental delivery

Each phase ends with the build command green, existing tests passing, and its manual checks recorded — no later-phase
behaviour implemented early (Constitution V, XI).

## Notes

- [P] = different files, no dependency on unfinished tasks.
- Never save or log learner audio (FR-068). Thresholds stay parameters until acceptance.
- Commit after each task or logical group, only when the product owner asks.
