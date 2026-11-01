# Tasks: Hear my guitar through an amp and effect pedals while I practise

**Input**: Design documents from `specs/022-guitar-amp-effects/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/monitor-engine.md, contracts/amp-window.md, quickstart.md

**Tests**: required (the owner's rules: tests first and seen red, never modify an existing test, only add new ones).

**Paths**:
- `AE` = `desktop/TuxGuitar-audio-engine/src/main/java/app/tuxguitar/audio/engine`
- `AET` = `desktop/TuxGuitar-audio-engine/src/test/java/app/tuxguitar/audio/engine`
- `PR` = `desktop/TuxGuitar-practice/src/app/tuxguitar/app/tools/custom/practice`
- `TST` = `desktop/TuxGuitar-practice/test/app/tuxguitar/app/tools/custom/practice`
- `LANG` = `desktop/TuxGuitar-practice/share/lang`
- `BUILD` = `desktop/build-scripts/tuxguitar-macosx-swt-cocoa`

**Standing rules for every phase**:
- JDK 21 pinned.
- After each Maven build, `git checkout -- specs/003-arcade-mode/benchmark/`.
- Gate = `mvn -o -e clean verify -P native-modules` from `BUILD` green, plus `swt-gui-tests` when a window changed, plus `git diff --name-status` on the test folders showing additions only.
- Commit each phase without any co-author trailer.
- One push per round (both refs).

## Format: `[ID] [P?] [Story] Description`

## Phase 1: Setup (baseline and the delay probe, research R1)

- [ ] T001 Write `specs/022-guitar-amp-effects/baseline.txt`:
  - the commit;
  - the counts of the full gate (expected: practice 1401 with 12 skipped, audio-engine 378, the other modules as in `specs/019-score-demo-fire/baseline.txt`);
  - `swt-gui-tests` 50;
  - the list of existing tests that must stay unchanged and green, especially every test under `AET/input/` and `AET/analysis/`, `FrontEndTest`, `AudioEngineTest` and the practice verdict tests.
- [ ] T002 Create `AET/monitor/MonitorLatencyProbeTest.java`, `@Tag("benchmark")`. It is excluded by default and only measures.
  - For blocks of 64, 128 and 256 frames at 48 kHz, and line buffers of 2, 3 and 4 blocks, it opens a `SourceDataLine` (default output) and a `TargetDataLine` (built-in microphone).
  - It plays a 1 ms click 20 times, 500 ms apart, finds each click in the capture by threshold, and reports the round-trip median and 95th percentile.
  - It runs 60 s of pass-through per setting and counts underruns (the `available()` of the output reaching the full buffer) and short reads.
  - It writes `specs/022-guitar-amp-effects/benchmark/latency-probe.md`.
  - It never fails on the numbers: it fails only if no line can be opened, with the reason.
- [ ] T003 Run T002 on the owner's Mac (`quickstart.md` §1) and record the report.
  - **Decision gate (research R1):**
    - Java Sound if a setting has a median of at most 30 ms with no underrun over 60 s; it is the smallest such setting, and its block size becomes `MonitorEngine.BLOCK_FRAMES`;
    - otherwise the native branch (Phase 7) is required.
  - Write the decision and the chosen block size and line buffer in `research.md` R1 ("Measured") and in `baseline.txt`.

**Checkpoint**: block size and device path decided.

## Phase 2: Foundational (blocks every story)

**Purpose**: the settings model, the engine on a fake device, and the capture hand-off that leaves the recognition untouched.

### Tests first (each seen red: it fails to compile before its class exists)

- [ ] T004 [P] Create `AET/monitor/ChainSettingsTest.java` (data-model "ChainSettings", "EffectKind"). It checks:
  - every `EffectKind` has the knobs, ranges, units and defaults of the data-model table;
  - `PedalSettings.with` clamps a value outside the range into it;
  - a chain with a kind twice, or an *after* kind in *before*, is refused (`IllegalArgumentException`);
  - `toText()` then `parse()` gives an equal chain for the four ready-made chains of data-model;
  - malformed texts (`""`, `"amp:loud"`, `"echo:on,abc"`, an unknown kind) give `Optional.empty()`;
  - `equals` and `hashCode` work by value.
- [ ] T005 [P] Create `AET/monitor/BlockQueueTest.java`:
  - one producer and one consumer pass 10 000 blocks in order;
  - `offer` never blocks: on a full queue it drops the oldest and counts it;
  - `poll` on an empty queue returns false and counts a silent block;
  - offering 1000 blocks allocates nothing (all buffers made at construction; checked with a fixed pool).
- [ ] T006 [P] Create `AET/monitor/OutputStageTest.java` (FR-010, SC-004). It checks:
  - a full-scale square wave with every gain at its maximum never gives a sample above 1.0 after the clamp, and peaks are held at −1 dBFS ± 0.1 dB by the limiter;
  - after `start`, the level rises from silence to the set level in 0.5 s ± 0.05 s with no step;
  - `stop` fades to silence in 20 ms ± 2 ms;
  - a volume change ramps within one block.
- [ ] T007 [P] Create `AET/monitor/MonitorEngineTest.java` on a fake `MonitorDevice` that records what is written and can be made late, lost or refused (contracts/monitor-engine.md). It checks:
  - the state transitions OFF → STARTING → ON → STOPPING → OFF;
  - an empty chain passes the input through the output stage (stereo, both channels equal);
  - `setChain` from another thread is picked up at the next block;
  - a device that refuses to open gives FAILED with its reason, and `start` never throws;
  - a device lost while ON gives FAILED and a listener message;
  - `stats()` counts dropped and silent blocks.
- [ ] T008 [P] Create `AET/input/CaptureDeliveryTest.java` (FR-003, SC-002, research R2). Through a new package-private `CaptureService.deliver(float[] samples, int frames)` (the code after the conversion, moved unchanged), it checks:
  - delivering the same 10 s recording in blocks of 1024 with no monitor, and in blocks of `MonitorEngine.BLOCK_FRAMES` with a monitor set, gives an identical `SampleRingBuffer` content and written count;
  - the monitor received every sample, in order;
  - with no monitor, `deliver` calls nothing of the monitor;
  - setting and clearing the monitor between two deliveries (the capture running) takes effect at the next block, and the ring buffer content and written count stay identical to a run without any switch.
- [ ] T009 [P] Create `AET/monitor/RecognitionUntouchedTest.java` (SC-002). It runs the engine of `FakeAudioInput.newEngine` on the recorded fixtures of `desktop/TuxGuitar-audio-engine/src/test/resources/benchmark/builtin-mic/standard/` listed in `manifest.csv` (loaded through `BenchmarkFixtures`, every file whose `fixture_role` is `reference`, plus `01_none_silence.wav`) twice: once as today, and once through `CaptureService.deliver` with a monitor running each ready-made chain on a fake device. It checks identical analysis frames (pitch, level, onset) frame by frame.
  - It is seen red with a deliberate one-run mutation: the monitor's processed block is written back into the ring buffer. That mutation is never committed.

### Implementation

- [ ] T010 Create `AE/monitor/EffectKind.java`, `Knob.java`, `PedalSettings.java`, `AmpSettings.java` and `ChainSettings.java` per data-model:
  - knobs with key, range, unit and default;
  - `AmpSettings.character` is "`CLEAN`, `CRUNCH` or `HIGH_GAIN`" and its knobs go "from 0 to 10";
  - "Each kind appears at most once in a chain";
  - the text format of research R6.
  Make T004 pass.
- [ ] T011 Create `AE/monitor/Effect.java` (contracts/monitor-engine.md), `BlockQueue.java` (preallocated ring of float blocks, single producer and single consumer, drop-oldest) and `fx/OutputStage.java` (volume ramp, 0.5 s fade in, 20 ms fade out, peak limiter at −1 dBFS, clamp at 0 dBFS). Make T005 and T006 pass.
- [ ] T012 Create `AE/monitor/MonitorDevice.java`, `MonitorState.java`, `MonitorStats.java`, `Chain.java` (runs before, amp, cabinet and after; an empty slot is a pass-through) and `MonitorEngine.java`:
  - an output thread through `TGThreadManager` at `TGThreadPriority.HIGH`;
  - `ChainSettings` in an `AtomicReference`;
  - no allocation in the loop;
  - listeners notified through an executor given by the caller.
  `BLOCK_FRAMES` is the value decided by T003. Make T007 pass.
- [ ] T013 In `AE/input/CaptureService.java`, additive only:
  - move the code after `PcmSampleConverter.convert` into `deliver(float[] selected, int frameCount)` without changing it;
  - add `setMonitor(MonitorEngine)`;
  - when a monitor is set, read `BLOCK_FRAMES` frames per read and offer each block to it after the ring buffer write;
  - when none is set, read `FRAMES_PER_READ` as today;
  - a change of the monitor while the capture runs takes effect at the next read, without reopening the line or resetting the ring buffer (contracts/monitor-engine.md).
  Make T008 and T009 pass. Every existing test of `AET/input/` and `AET/analysis/` stays green and unchanged.
- [ ] T014 Gate and commit Phase 2.

**Checkpoint**: an engine that passes the input to a fake output, with the recognition proven untouched.

## Phase 3: User Story 1 - I hear my guitar through an amp (Priority: P1) 🎯 MVP

**Goal**: with *Amp sound* on, the guitar is heard through the amp and cabinet while the practice window is open, mixed with the score; the verdicts are unchanged.

**Independent Test**: plug a guitar, open the practice window and *Amp and effects*, turn the sound on, play: the guitar is heard through *Clean*/*Crunch*/high gain, with the delay shown; a recorded run gives the same verdicts with the sound on and off (T009).

### Tests first

- [ ] T015 [P] [US1] Create `AET/monitor/fx/BiquadTest.java` and `OversamplerTest.java`:
  - low-pass, high-pass, peak and shelf responses at chosen frequencies within ±0.5 dB of the formulas;
  - stable for the extreme knob values;
  - the oversampler round trip keeps a 1 kHz sine within ±0.1 dB;
  - aliasing of a clipped 5 kHz sine is at least 40 dB lower with 4× than with 1×.
- [ ] T016 [P] [US1] Create `AET/monitor/fx/AmpTest.java` and `CabinetTest.java` (FR-004). They check:
  - CLEAN on a −20 dBFS sine gives total harmonic distortion under 3 %, CRUNCH between 3 % and 30 %, HIGH_GAIN over 30 %;
  - bass, middle and treble at 0 and 10 move the response at 100 Hz, 700 Hz and 3 kHz by at least 6 dB each way;
  - the output is finite for every knob combination at full-scale input (no NaN, no denormals after 10 s of silence);
  - determinism: two runs give identical samples;
  - the cabinet's response is down at least 12 dB at 40 Hz and at 10 kHz compared with 1 kHz; switched off, it is a pass-through.
- [ ] T017 [P] [US1] Create `AET/monitor/fx/NoiseGateTest.java` (FR-011): a −70 dBFS noise is attenuated by at least 40 dB with the threshold at −60 dB; a −20 dBFS note passes within 1 ms; the release follows its knob ± 10 %; no click when it closes (no step above the signal's own change); with a −70 dBFS noise as input, the gate at −55 dB in front of the amp at HIGH_GAIN 7/6/6/6/6 with the cabinet on gives less than −60 dBFS at the output at the default guitar volume (FR-011).
- [ ] T018 [P] [US1] Create `AET/monitor/JavaSoundMonitorDeviceTest.java`: the conversion of float stereo blocks to 16-bit little-endian frames (rounding, clamp) and the estimated delay from the format and the buffer. It runs without hardware, through the package-private conversion and estimate methods.
- [ ] T019 [P] [US1] Create `TST/ui/AmpWindowSwtTest.java` (swt-gui-tests, contracts/amp-window.md). It checks:
  - the window shows the *Amp sound* switch (off on a fresh store), the output list, *Guitar volume*, *Score volume*, the delay line and the amp row with its character and five sliders;
  - moving a slider calls `setChain` with the clamped value;
  - every control has a tooltip;
  - a second open brings the same window forward;
  - the palette's amp button opens it too (FR-020);
  - during a run, with no kept bounds, it opens on the side of the main window away from the framed note and the next note (given guard boxes on the left, it opens on the right, and the reverse), and it never takes the focus from the palette;
  - its bounds are kept when it closes and used at the next open when they are on a screen;
  - the delay line reads "about N ms (estimate)" from the engine's estimate (FR-002).
- [ ] T020 [P] [US1] Create `TST/ui/AmpTextsTest.java` (FR-016, SC-008), like `FireTextsTest`: for every key of a list `PR/ui/AmpTexts.KEYS` (each control, knob, element and message of the window; the list grows with each phase), the three `LANG` files have a non-empty label and a tooltip key `<key>.tip`, and every tooltip of a knob names its unit and its default. Seen red before the keys of US1 exist; each later phase adds its keys to the list and to the three files in the same task.

### Implementation

- [ ] T021 [P] [US1] Create `AE/monitor/fx/Biquad.java` (RBJ cookbook forms, coefficient ramps) and `fx/Oversampler.java` (polyphase half-band, 2× and 4×). Make T015 pass.
- [ ] T022 [US1] Create `AE/monitor/fx/Amp.java` (research R4: input gain, stages by character, tone stack, presence, master), `fx/Cabinet.java` and `fx/NoiseGate.java`. Make T016 and T017 pass.
- [ ] T023 [US1] Create `AE/monitor/JavaSoundMonitorDevice.java`: a `SourceDataLine` on the named output mixer (default when empty), 48 kHz, 16-bit stereo, the line buffer chosen by T003; `write` blocks for at most one block; errors are wrapped in `MonitorDeviceException`. Make T018 pass.
- [ ] T024 [US1] Create `PR/ui/AmpWindow.java` and `PR/ui/AmpChainRows.java` with the top block and the amp and cabinet rows only (pedal rows come in US2), owned by the main window. Add an *Amp and effects…* button in `PR/ui/PracticeWindow.java` and an amp button in `PR/ui/PracticePalette.java` (both open the same window). Place it per contracts/amp-window.md: its kept bounds (`window.bounds`), else away from the framed and the next note using the boxes of `PR/ui/PaletteGuard.java`/`ScoreGuardBoxes`, else centred over the practice window, without taking the focus; keep its bounds when it closes. Show the delay as an estimate (FR-002, research R8). Keep one `MonitorEngine` per practice window:
  - it starts when *Amp sound* is on and the window opens, and stops when the practice window closes (FR-001);
  - `CaptureService.setMonitor` is called while it is ON;
  - *Score volume* reads and writes `MidiPlayer.getVolume()`/`setVolume(int)`.
  Make T019 pass.
- [ ] T025 [US1] Create `PR/ui/AmpTexts.java` with the keys of US1, and add the labels and tooltips of US1 (switch, output, volumes, delay, amp knobs, cabinet) to `LANG/messages.properties`, `messages_fr.properties` and `messages_es.properties`: what each changes, its unit and its default (FR-016). Make T020 pass.
- [ ] T026 [US1] Gate and commit. Relaunch the app for a smoke check: guitar on the built-in microphone with headphones, *Clean* heard.
- [ ] T027 [HUMAN] [US1] Listen by ear with the Rocksmith cable and headphones, at T003's setting: does it feel like playing through an amp (SC-001)? The probe measured the built-in microphone, not the cable. The answer is noted in `baseline.txt` and confirmed at the final gate.

**Checkpoint**: the MVP. The guitar is heard through an amp while practising.

## Phase 4: User Story 2 - Effect pedals I can choose and tune (Priority: P1)

**Goal**: the compressor, overdrive, distortion, chorus, echo and reverb before or after the amp, switched, tuned, added, removed and reordered, each change heard at once without a click.

**Independent Test**: add an echo and a distortion, move every knob while playing, switch them: each change is heard within 0.1 s, with no click; the echo's tail dies away.

### Tests first

- [ ] T028 [P] [US2] Create `AET/monitor/fx/CompressorTest.java`, `OverdriveTest.java` and `DistortionTest.java`:
  - the compressor reduces a 20 dB step by at least `sustain`-dependent amounts and makes up its level;
  - the overdrive and distortion harmonic contents grow with drive and gain;
  - `tone` moves the 3 kHz content by at least 6 dB;
  - `level` 0 to 10 spans at least 30 dB;
  - finite and deterministic.
- [ ] T029 [P] [US2] Create `AET/monitor/fx/ChorusTest.java`, `EchoTest.java` and `ReverbTest.java`:
  - the echo of an impulse repeats at `time` ± 1 sample, each repeat lower by the `repeats` factor, darker with a lower `tone`;
  - its tail continues after the input stops (US2/AC2), and a 1.5 s time is allowed;
  - the chorus delay stays within 7–20 ms;
  - the reverb tail's decay time grows with `size`;
  - `mix` 0 is dry;
  - every one is finite after 60 s of white noise at full scale.
- [ ] T030 [P] [US2] Create `AET/monitor/SmoothChangesTest.java` (FR-006, SC-003). For every knob of every kind, a sine runs while the knob jumps from its minimum to its maximum, and then the pedal is switched on and off. The largest sample-to-sample step stays under 1.5× the largest step of the same run with no change. A change is fully applied within 0.1 s.
- [ ] T031 [P] [US2] Create `AET/monitor/ChainCostBenchmarkTest.java` (`@Tag("benchmark")`): the full chain of research R4 (every kind on, high gain, 4× oversampling) over 60 s of signal at `BLOCK_FRAMES`. It reports the mean and the 99th percentile of the processing time per block as a share of the block time. Goal: under 25 %; recorded in `baseline.txt`.
- [ ] T032 [P] [US2] Create `AET/monitor/MonitorEnduranceBenchmarkTest.java` (`@Tag("benchmark")`, SC-006): the full chain of T031 on a fake device paced by the real clock for 10 minutes; it reports dropped and silent blocks (goal: 0) and the heap growth after a one-minute warm-up (goal: under 1 MB); recorded in `baseline.txt`.
- [ ] T033 [P] [US2] Extend the window test with a new file, `TST/ui/AmpPedalRowsSwtTest.java`. It checks:
  - *Add pedal…* lists only kinds not in the chain and places each in its own section;
  - up and down move a pedal only within its section;
  - remove takes it out;
  - each row has its switch, its sliders and its tooltips;
  - every change reaches `setChain`.

### Implementation

- [ ] T034 [P] [US2] Create `AE/monitor/fx/Compressor.java`, `Overdrive.java` (2× oversampled) and `Distortion.java` (4× oversampled). Make T028 pass.
- [ ] T035 [P] [US2] Create `AE/monitor/fx/Chorus.java`, `Echo.java` (preallocated 1.5 s line, interpolated reads, low-pass in the loop) and `Reverb.java` (8 combs and 4 all-passes, rewritten from the public Schroeder–Moorer structure). Make T029 pass.
- [ ] T036 [US2] Wire the pedals into `AE/monitor/Chain.java`, with a 10 ms crossfade bypass and knob ramps. Make T030 pass. Run T031 and T032 and record them.
- [ ] T037 [US2] Add the pedal rows to `PR/ui/AmpChainRows.java` (add, remove, up, down, switch, sliders with values), and their keys in `PR/ui/AmpTexts.java` with their labels and tooltips in the three `LANG` files. Make T033 and T020 pass.
- [ ] T038 [US2] Gate and commit.

## Phase 5: User Story 3 - Sounds I can save, and the sound of each score (Priority: P2)

**Goal**: the four ready-made sounds, the learner's own, save, rename, delete and restore, and the last sound of each score coming back by title and artist.

**Independent Test**: save two sounds, restart, switch between them; pick *Lead* for score A and *Clean* for score B, restart, reopen A and B: each one gets its own sound back.

### Tests first

- [ ] T039 [P] [US3] Create `TST/session/ScoreKeyTest.java` (FR-019, data-model "ScoreKey"). It checks:
  - title and artist are lower-cased, trimmed and joined; the key is the 16-character hex SHA-256 prefix, and no title text appears in it;
  - the file name is used when both are empty; no key when all three are empty;
  - the same title and artist in two files give the same key;
  - renaming or moving the file keeps the key.
- [ ] T040 [P] [US3] Create `TST/session/AmpSoundStoreTest.java` with an in-memory `ArcadeChoices.Persistence`. It checks:
  - a fresh store gives `enabled` false, guitar volume 0.7, current "Clean", and the four ready-made sounds in the order of data-model;
  - `saveAs` keeps a sound across a new store over the same persistence;
  - names are "trimmed, from 1 to 40 characters, and unique regardless of case"; a clash with a ready-made name is refused;
  - rename and delete apply to own sounds only;
  - a changed ready-made sound shows as changed, and `restore` brings back its chain;
  - `remember(A, "Lead")` and `remember(B, "Clean")`, then `soundFor(A)` gives Lead and `soundFor(B)` gives Clean, also after a new store;
  - an unknown score gives `last`;
  - a deleted remembered sound gives `last` (US3/AC6);
  - a malformed stored chain falls back to the ready-made sound of that name, or to Clean;
  - the chosen output and the window bounds are kept across a new store over the same persistence (FR-009).
- [ ] T041 [P] [US3] Create `TST/ui/AmpSoundListSwtTest.java`. It checks:
  - the sound list shows ready-made sounds then own sounds;
  - picking one sends its chain at once;
  - a change shows "(changed)";
  - *Save as…*, *Rename…*, *Delete* and *Restore* are enabled only when they apply.

### Implementation

- [ ] T042 [US3] Create `PR/session/ScoreKey.java`, `Sound.java`, `AmpSoundSettings.java` and `AmpSoundStore.java` (contracts/amp-window.md; module `tuxguitar-practice-amp`, the keys of data-model). Make T039 and T040 pass.
- [ ] T043 [US3] Add the sound list and its buttons to `PR/ui/AmpWindow.java`. In `PR/ui/PracticeWindow.java`:
  - when the practice window shows its preparation (`showPreparation`, which reads the score in front through `host.song()`), and so whenever the score in front has changed, set the current sound to `soundFor(ScoreKey.of(song.getName(), song.getArtist(), scoreName()))`;
  - when the learner picks a sound, call `remember`;
  - a test in `TST/ui/AmpSoundListSwtTest.java` (T041) opens the window on score A then on score B and checks each gets its remembered sound before any run.
  Keys go in `PR/ui/AmpTexts.java`, labels and tooltips in the three `LANG` files. Make T041 and T020 pass.
- [ ] T044 [US3] Gate and commit.

## Phase 6: User Story 4 - Safe by default (Priority: P2)

**Goal**: silent at first, the microphone warning, a howl cut within 0.5 s, a lost device handled, and the overload lightening.

**Independent Test**: a fresh install is silent; turning the sound on with the built-in microphone warns; a synthetic howl is muted within 0.5 s; a lost device stops the sound with a message while the practice goes on.

### Tests first

- [ ] T045 [P] [US4] Create `AET/monitor/FeedbackGuardTest.java` (FR-012, SC-005, research R5). It checks:
  - a synthetic howl (a 2.3 kHz sine rising 6 dB per 100 ms over guitar-like noise) fed through the engine mutes the output within 0.5 s of crossing the rule, state MUTED_FEEDBACK;
  - the four ready-made sounds played on the recorded guitar fixtures (including long sustained notes and the echo's tails) never trigger it;
  - turning the sound on again restarts it.
- [ ] T046 [P] [US4] Create `AET/monitor/OverloadTest.java` (FR-014). A fake clock makes each block take 60 % of the block time. After 20 blocks the reverb goes off with a message, then the chorus, then the oversampling drops to 2× and 1×, then the state is MUTED_OVERLOAD. The capture's `deliver` is never slowed (it returns within a fixed time).
- [ ] T047 [P] [US4] Create `TST/ui/AmpSafetySwtTest.java`. It checks:
  - with the built-in microphone as input, turning the sound on shows the warning, and the sound starts only after *Turn on anyway*;
  - the message line shows the feedback, overload and device-lost messages from engine events;
  - a fresh store opens with the switch off.

### Implementation

- [ ] T048 [US4] Create `AE/monitor/FeedbackGuard.java` (the existing `RealFft` every 10 ms, research R5), and add the overload lightening to `AE/monitor/Chain.java` and `MonitorEngine.java`. Make T045 and T046 pass.
- [ ] T049 [US4] Add the warning dialog and the message line to `PR/ui/AmpWindow.java`, with the input kind from `CurrentInputSelection` (built-in microphone versus other inputs) and the messages in `PR/ui/AmpTexts.java` and the three `LANG` files. Make T047 and T020 pass.
- [ ] T050 [US4] Gate and commit.

## Phase 7: Conditional — native duplex device (only if T003 decided it)

- [ ] T051 Only if T003 found no Java Sound setting within 30 ms:
  - write a short addendum to `research.md` naming the native API per system (CoreAudio, ALSA or JACK, WASAPI) and its module under `desktop/build-scripts/native-modules/`;
  - add `NativeMonitorDevice` behind `MonitorDevice`, with its own probe run;
  - wire the modules into the three build scripts and the release workflow.
  This is planned with the owner before coding (a new native module per system). If T003 chose Java Sound, mark this task "not needed" with T003's numbers.

## Phase 8: Polish & cross-cutting concerns

- [ ] T052 [P] Describe the sound, the *Amp and effects* window, the chain, the sounds and the sound per score, the delay, headphones and feedback, and "the sound never changes the judging" in `docs/PRACTICE.md`, `docs/PRACTICE-FR.md` and `docs/PRACTICE-ES.md` (FR-017), with a screenshot of the window in `docs/images/features/` (taken on the built app).
- [ ] T053 [P] Write the manual sheet `specs/test-sheets/F12-ampli-effets.md` (quickstart §3, plus rows for Linux and Windows testers: the delay shown, steps 2 to 5, a howl) and add it to `specs/test-sheets/README.md`.
- [ ] T054 Full gate: `mvn -o -e clean verify -P native-modules` and `swt-gui-tests`. Record the counts in `baseline.txt` and check the test folders show additions only.
- [ ] T055 Write `specs/022-guitar-amp-effects/final-report.md`:
  - what was built, the R1 decision with its numbers, the tests added, the cost measured;
  - where each algorithm and each ready-made sound comes from (the project's own; public structures such as the Schroeder–Moorer reverb named, rewritten), confirming FR-018;
  - what is unverified (the delay with the cable until T056, Linux and Windows).
  Update the 022 row of `specs/STATUS.md` and the memory `pending-amp-effects-spec`. Commit.
- [ ] T056 [HUMAN] The owner's gate on the relaunched app, per sheet F12, with the Rocksmith cable and headphones:
  - the delay feels like an amp (SC-001, T027);
  - the four sounds in Wait, Follow and Arcade;
  - an hour of practice without a dropout (SC-006);
  - the sound per score after a restart;
  - the microphone warning and a howl.
  Values to retune (ready-made sounds, ranges) are noted, changed and re-tried.

---

## Dependencies & execution order

- Phase 1 (T003's decision) → Phase 2 → US1 (Phase 3, MVP) → US2 → US3 → US4 → Phase 8.
- US3 and US4 depend on the window of US1, but not on each other.
- Phase 7 runs only if T003 asks for it, and then it comes before US1's device task T023.
- Inside a phase, tests come first and are seen red; [P] tasks touch different files.

## Parallel examples

- Phase 2: T004, T005, T006, T007, T008 and T009 together.
- US1: T015, T016, T017, T018, T019 and T020 together, then T021 alongside the window work.
- US2: T028, T029, T030, T031, T032 and T033 together, then T034 and T035 together.
- US3: T039, T040 and T041 together.

## Implementation strategy

- **MVP = Phase 1 + Phase 2 + US1**: the guitar heard through an amp, with the delay measured and the recognition proven untouched.
- Then the pedals (US2), the sounds (US3) and safety (US4), each one gated and committed.
- **No release** before the owner's gate T056.
- **Not before US4 is done:** the sound is off by default from US1 on, and the safe ceiling is in the output stage from Phase 2. The microphone warning and the feedback guard come in US4, so before that phase the owner tries it with headphones only (said in the US1 smoke check).
