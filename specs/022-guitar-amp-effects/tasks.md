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

- [X] T001 Write `specs/022-guitar-amp-effects/baseline.txt`:
  - the commit;
  - the counts of the full gate (expected: practice 1401 with 12 skipped, audio-engine 378, the other modules as in `specs/019-score-demo-fire/baseline.txt`);
  - `swt-gui-tests` 50;
  - the list of existing tests that must stay unchanged and green, especially every test under `AET/input/` and `AET/analysis/`, `FrontEndTest`, `AudioEngineTest` and the practice verdict tests.
- [X] T002 Create `AET/monitor/MonitorLatencyProbeTest.java`, `@Tag("benchmark")`. It is excluded by default and only measures.
  - For blocks of 64, 128 and 256 frames at 48 kHz, and line buffers of 2, 3 and 4 blocks, it opens a `SourceDataLine` (default output) and a `TargetDataLine` (built-in microphone).
  - It plays a 1 ms click 20 times, 500 ms apart, finds each click in the capture by threshold, and reports the round-trip median and 95th percentile.
  - It runs 60 s of pass-through per setting and counts underruns (the `available()` of the output reaching the full buffer) and short reads.
  - It writes `specs/022-guitar-amp-effects/benchmark/latency-probe.md`.
  - Done: run 1 showed the delay growing by itself; the clicks were then spaced 3 s over the whole minute and the input backlog capped as a queue would (research R1 "Measured").
  - It never fails on the numbers: it fails only if no line can be opened, with the reason.
- [X] T003 Run T002 on the owner's Mac (`quickstart.md` §1) and record the report.
  - **Decision gate (research R1):**
    - Java Sound if a setting has a median of at most 30 ms with no underrun over 60 s; it is the smallest such setting, and its block size becomes `MonitorEngine.BLOCK_FRAMES`;
    - otherwise the native branch (Phase 7) is required.
  - Write the decision and the chosen block size and line buffer in `research.md` R1 ("Measured") and in `baseline.txt`.
  - **Decided on 2026-11-01: no Java Sound setting within 30 ms (37 ms at best, 86 to 126 ms with a capped backlog) → the native branch, in Rust with `cpal`, with its own input; `BLOCK_FRAMES = 128` (research R1 "Measured" and addendum, owner's decisions).**

**Checkpoint**: block size and device path decided (native device, 128 frames).

## Phase 2: Foundational (blocks every story)

**Purpose**: the settings model, the engine on a fake device, and the capture hand-off that leaves the recognition untouched.

### Tests first (each seen red: it fails to compile before its class exists)

- [X] T004 [P] Create `AET/monitor/ChainSettingsTest.java` (data-model "ChainSettings", "EffectKind"). It checks:
  - every `EffectKind` has the knobs, ranges, units and defaults of the data-model table;
  - `PedalSettings.with` clamps a value outside the range into it;
  - a chain with a kind twice, or an *after* kind in *before*, is refused (`IllegalArgumentException`);
  - `toText()` then `parse()` gives an equal chain for the four chains of `ReadyMadeChains`, which match the data-model table;
  - malformed texts (`""`, `"amp:loud"`, `"echo:on,abc"`, an unknown kind) give `Optional.empty()`;
  - `equals` and `hashCode` work by value.
- [X] T005 **Not needed (R1 addendum): the rings between the devices and Java are in the native crate, tested by `cargo test` (T054).** First text, kept for the record: create `AET/monitor/BlockQueueTest.java`:
  - one producer and one consumer pass 10 000 blocks in order;
  - `offer` never blocks: on a full queue it drops the oldest and counts it;
  - `poll` on an empty queue returns false and counts a silent block;
  - offering 1000 blocks allocates nothing (all buffers made at construction; checked with a fixed pool).
- [X] T006 [P] Create `AET/monitor/OutputStageTest.java` (FR-010, SC-004). It checks:
  - a full-scale square wave with every gain at its maximum never gives a sample above 1.0 after the clamp, and peaks are held at −1 dBFS ± 0.1 dB by the limiter;
  - after `start`, the level rises from silence to the set level in 0.5 s ± 0.05 s with no step;
  - `stop` fades to silence in 20 ms ± 2 ms;
  - a volume change ramps within one block.
- [X] T007 [P] Create `AET/monitor/MonitorEngineTest.java` on a fake duplex `MonitorDevice` that is fed an input, records what is written and can be made late, lost or refused (contracts/monitor-engine.md, R1 addendum). It checks:
  - the state transitions OFF → STARTING → ON → STOPPING → OFF;
  - an empty chain passes the input through the output stage (stereo, both channels equal);
  - `setChain` from another thread is picked up at the next block;
  - a device that refuses to open gives FAILED with its reason, and `start` never throws;
  - a device lost while ON gives FAILED and a listener message;
  - after FAILED, `start` with an output again gives STARTING then ON (spec edge case "the output device changes or disappears");
  - `stats()` counts the device's dropped blocks and the silent blocks (a read with no input);
  - an input block with a sample at or above −0.1 dBFS counts a clipped input block and notifies the listener once per second at most (spec edge case "the input level is too high").
- [X] T008 **Not needed (R1 addendum): `CaptureService` is not touched.** First text, kept for the record: create `AET/input/CaptureDeliveryTest.java` (FR-003, SC-002, research R2). Through a new package-private `CaptureService.deliver(float[] samples, int frames)` (the code after the conversion, moved unchanged), it checks:
  - delivering the same 10 s recording in blocks of 1024 with no monitor, and in blocks of `MonitorEngine.BLOCK_FRAMES` with a monitor set, gives an identical `SampleRingBuffer` content and written count;
  - the monitor received every sample, in order;
  - with no monitor, `deliver` calls nothing of the monitor;
  - setting and clearing the monitor between two deliveries (the capture running) takes effect at the next block, and the ring buffer content and written count stay identical to a run without any switch.
- [X] T009 [P] Create `AET/monitor/RecognitionUntouchedTest.java` (SC-002). It runs the engine of `FakeAudioInput.newEngine` on the recorded fixtures of `desktop/TuxGuitar-audio-engine/src/test/resources/benchmark/builtin-mic/standard/` listed in `manifest.csv` (loaded through `BenchmarkFixtures`, every file whose `fixture_role` is `reference`, plus `01_none_silence.wav`) twice: once as today, and once while a `MonitorEngine` runs each ready-made chain on a fake duplex device fed with the same recording, in the same process. It checks identical analysis frames (pitch, level, onset) frame by frame.
  - It is seen red with a deliberate one-run mutation: the engine's processed output is fed to the analysis instead of the recording. That mutation is never committed.

### Implementation

- [X] T010 Create `AE/monitor/EffectKind.java`, `Knob.java`, `PedalSettings.java`, `AmpSettings.java` and `ChainSettings.java` per data-model:
  - knobs with key, range, unit and default;
  - `AmpSettings.character` is "`CLEAN`, `CRUNCH` or `HIGH_GAIN`" and its knobs go "from 0 to 10";
  - "Each kind appears at most once in a chain";
  - the text format of research R6;
  - `ReadyMadeChains.java`, the single home of the four ready-made chains of data-model (Clean, Crunch, Lead, Clean echo), used by the engine tests and later by `PR/session/Sound` and `AmpSoundStore`.
  Make T004 pass.
- [X] T011 Create `AE/monitor/Effect.java` (contracts/monitor-engine.md) and `fx/OutputStage.java` (volume ramp, 0.5 s fade in, 20 ms fade out, peak limiter at −1 dBFS, clamp at 0 dBFS). Make T006 pass. (No `BlockQueue`: T005 not needed.)
- [X] T012 Create `AE/monitor/MonitorDevice.java`, `MonitorState.java`, `MonitorStats.java`, `Chain.java` (runs before, amp, cabinet and after; an empty slot is a pass-through) and `MonitorEngine.java`:
  - a thread through `TGThreadManager` at `TGThreadPriority.HIGH` that loops: read one block from the device, chain, output stage, write;
  - `ChainSettings` in an `AtomicReference`;
  - no allocation in the loop;
  - listeners notified through an executor given by the caller;
  - `MonitorStats` counts clipped input blocks.
  `BLOCK_FRAMES = 128`, decided by T003. Make T007 pass.
- [X] T013 **Not needed (R1 addendum): `CaptureService` is not touched; T009 passes without it.** First text, kept for the record: in `AE/input/CaptureService.java`, additive only:
  - move the code after `PcmSampleConverter.convert` into `deliver(float[] selected, int frameCount)` without changing it;
  - add `setMonitor(MonitorEngine)`;
  - when a monitor is set, read `BLOCK_FRAMES` frames per read and offer each block to it after the ring buffer write;
  - when none is set, read `FRAMES_PER_READ` as today;
  - a change of the monitor while the capture runs takes effect at the next read, without reopening the line or resetting the ring buffer (contracts/monitor-engine.md).
  Make T008 and T009 pass. Every existing test of `AET/input/` and `AET/analysis/` stays green and unchanged. (T009 is made to pass by T012.)
- [X] T014 Gate and commit Phase 2.

**Checkpoint**: an engine that passes the input of a fake duplex device to its output, with the recognition proven untouched.

Then Phase 7 (the native device) comes before US1's device task T024.

## Phase 3: User Story 1 - I hear my guitar through an amp (Priority: P1) 🎯 MVP

**Goal**: with *Amp sound* on, the guitar is heard through the amp and cabinet while the practice window is open, mixed with the score; the verdicts are unchanged.

**Independent Test**: plug a guitar, open the practice window and *Amp and effects*, turn the sound on, play: the guitar is heard through *Clean*/*Crunch*/high gain, with the delay shown; a recorded run gives the same verdicts with the sound on and off (T009).

### Tests first

- [X] T015 [P] [US1] Create `AET/monitor/fx/BiquadTest.java` and `OversamplerTest.java`:
  - low-pass, high-pass, peak and shelf responses at chosen frequencies within ±0.5 dB of the formulas;
  - stable for the extreme knob values;
  - the oversampler round trip keeps a 1 kHz sine within ±0.1 dB;
  - aliasing of a clipped 5 kHz sine is at least 40 dB lower with 4× than with 1×.
- [X] T016 [P] [US1] Create `AET/monitor/fx/AmpTest.java` and `CabinetTest.java` (FR-004). They check:
  - CLEAN on a −20 dBFS sine gives total harmonic distortion under 3 %, CRUNCH between 3 % and 30 %, HIGH_GAIN over 30 %;
  - bass, middle and treble at 0 and 10 move the response at 100 Hz, 700 Hz and 3 kHz by at least 6 dB each way;
  - the output is finite for every knob combination at full-scale input (no NaN, no denormals after 10 s of silence);
  - determinism: two runs give identical samples;
  - the cabinet's response is down at least 12 dB at 40 Hz and at 10 kHz compared with 1 kHz; switched off, it is a pass-through.
- [X] T017 [P] [US1] Create `AET/monitor/fx/NoiseGateTest.java` (FR-011): a −70 dBFS noise is attenuated by at least 40 dB with the threshold at −60 dB; a −20 dBFS note passes within 1 ms; the release follows its knob ± 10 %; no click when it closes (no step above the signal's own change); with a −70 dBFS noise as input, the gate at −55 dB in front of the amp at HIGH_GAIN 7/6/6/6/6 with the cabinet on gives less than −60 dBFS at the output at the default guitar volume (FR-011).
- [X] T018 [P] [US1] Create `AET/monitor/NativeMonitorDeviceTest.java` (R1 addendum). Without hardware it checks: the estimated delay from the devices' declared latencies and both buffers (package-private estimate method); when the native library cannot be loaded, `open` throws `MonitorDeviceException` with a reason naming the library (FR-013), and the engine goes to FAILED with that reason.
- [X] T019 [P] [US1] Create `TST/ui/AmpWindowSwtTest.java` (swt-gui-tests, contracts/amp-window.md). It checks:
  - the window shows the *Amp sound* switch (off on a fresh store), the output list, *Guitar volume*, *Score volume*, the delay line and the amp row with its character and five sliders;
  - moving a slider calls `setChain` with the clamped value;
  - every control has a tooltip;
  - a second open brings the same window forward;
  - the palette's amp button opens it too (FR-020);
  - during a run, with no kept bounds, it opens on the side of the main window away from the framed note and the next note (given guard boxes on the left, it opens on the right, and the reverse), and it never takes the focus from the palette;
  - its bounds are kept when it closes and used at the next open when they are on a screen;
  - it closes when the practice window closes (FR-020);
  - the delay line reads "about N ms (estimate)" from the engine's estimate (FR-002).
- [X] T020 [P] [US1] Create `TST/session/AmpSoundSettingsTest.java` (FR-009, SC-007) with an in-memory `ArcadeChoices.Persistence`. It checks:
  - a fresh store gives `enabled` false, the system's default output, guitar volume 0.7, the current chain equal to `ReadyMadeChains` Clean, and no window bounds;
  - each value written (`enabled`, `output`, `guitar.volume`, `chain.current`, `window.bounds`) is found again by a new store over the same persistence;
  - a malformed stored current chain falls back to Clean.
- [X] T021 [P] [US1] Create `TST/ui/AmpTextsTest.java` (FR-016, SC-008), like `FireTextsTest`: for every key of a list `PR/ui/AmpTexts.KEYS` (each control, knob, element and message of the window; the list grows with each phase), the three `LANG` files have a non-empty label and a tooltip key `<key>.tip`, and every tooltip of a knob names its unit and its default. Seen red before the keys of US1 exist; each later phase adds its keys to the list and to the three files in the same task.

- [X] T061 [P] [US1] Create `TST/dataset/DatasetRecognitionIdentityTest.java` (`@Tag("benchmark")`, owner's request of 2026-11-01, quickstart §2): it runs the dataset benchmarks of `TST/dataset/` that write outcome files (`DatasetBenchmarkTest`, `GuitarSetChordBenchmarkTest`, `TechniqueDatasetBenchmarkTest`, `OwnerChordsBenchmarkTest`, through their existing entry points, unchanged) twice, sound off and with a `MonitorEngine` running each ready-made chain on a fake duplex device fed with the same recordings in the same process, and checks the outcome files of `target/dataset-benchmark` are identical line for line. It needs `~/tuxguitar-datasets` (or `-Dtuxguitar.datasets`) and is skipped with a message when the data are absent. Run at T028 and T058; its result goes in the final report.

### Implementation

- [X] T022 [P] [US1] Create `AE/monitor/fx/Biquad.java` (RBJ cookbook forms, coefficient ramps) and `fx/Oversampler.java` (polyphase half-band, 2× and 4×). Make T015 pass.
- [X] T023 [US1] Create `AE/monitor/fx/Amp.java` (research R4: input gain, stages by character, tone stack, presence, master), `fx/Cabinet.java` and `fx/NoiseGate.java`. Make T016 and T017 pass.
- [X] T024 [US1] Create `AE/monitor/NativeMonitorDevice.java` over the crate of T054 (JNI): `open(inputName, inputChannel, outputName, BLOCK_FRAMES)` ("" = the system's default, channel −1 = the first), `read` waits for at most one block time, `write` never blocks longer, the native errors are wrapped in `MonitorDeviceException`, and the library is loaded the way the AudioUnit plugin loads its own. Make T018 pass.
- [X] T025 [US1] Create `PR/session/AmpSoundSettings.java` and the settings part of `PR/session/AmpSoundStore.java` (contracts/amp-window.md: `of(TGContext)` over the module `tuxguitar-practice-amp`, `settings()`, `save()`, `currentChain()`, `saveCurrentChain()`; the keys `enabled`, `output`, `guitar.volume`, `chain.current` and `window.bounds` of data-model). The named sounds and the sound per score come in US3. Make T020 pass.
- [X] T026 [US1] First create `PR/ui/AmpTexts.java` with the text keys of US1 (the list T021 reads), then create `PR/ui/AmpWindow.java` and `PR/ui/AmpChainRows.java` with the top block and the amp and cabinet rows only (pedal rows come in US2), owned by the main window. Add an *Amp and effects…* button in `PR/ui/PracticeWindow.java` and an amp button in `PR/ui/PracticePalette.java` (both open the same window). Place it per contracts/amp-window.md: its kept bounds (`window.bounds`), else away from the framed and the next note using the boxes of `PR/ui/PaletteGuard.java`/`ScoreGuardBoxes`, else centred over the practice window, without taking the focus; keep its bounds when it closes. Show the delay as an estimate (FR-002, research R8). Keep one `MonitorEngine` per practice window:
  - it starts when *Amp sound* is on and the window opens, and stops when the practice window closes (FR-001);
  - it opens the practice input (the device of `CurrentInputSelection`, by name) and the chosen output through `NativeMonitorDevice`; `CaptureService` is not touched;
  - the switch, the output, *Guitar volume*, the amp settings and the bounds are read from and written to `AmpSoundStore` (T025); every change of the chain is kept with `saveCurrentChain`;
  - *Score volume* reads and writes `MidiPlayer.getVolume()`/`setVolume(int)`;
  - the window closes with the practice window.
  Make T019 pass.
- [X] T027 [US1] Add the labels and tooltips of US1 (switch, output, volumes, delay, amp knobs, cabinet) to `LANG/messages.properties`, `messages_fr.properties` and `messages_es.properties`: what each changes, its unit and its default (FR-016); the delay's tooltip says that Bluetooth headphones add their own delay (research R8). Make T021 pass.
- [ ] T028 [US1] Gate (with T061 run once) and commit. Relaunch the app for a smoke check: the owner's USB microphone (or the built-in one, about 49 ms by itself) with headphones, *Clean* heard.
- [ ] T029 [HUMAN] [US1] **Non-blocking: the implementation goes on with US2 without waiting; the owner does it whenever convenient, and it is confirmed at T060.** Listen by ear with the Rocksmith cable and headphones, at `BLOCK_FRAMES` = 128: does it feel like playing through an amp (SC-001)? The probes measured the built-in and a USB microphone, not the cable. The answer is noted in `baseline.txt` and confirmed at the final gate.

**Checkpoint**: the MVP. The guitar is heard through an amp while practising.

## Phase 4: User Story 2 - Effect pedals I can choose and tune (Priority: P1)

**Goal**: the compressor, overdrive, distortion, chorus, echo and reverb before or after the amp, switched, tuned, added, removed and reordered, each change heard at once without a click.

**Independent Test**: add an echo and a distortion, move every knob while playing, switch them: each change is heard within 0.1 s, with no click; the echo's tail dies away.

### Tests first

- [X] T030 [P] [US2] Create `AET/monitor/fx/CompressorTest.java`, `OverdriveTest.java` and `DistortionTest.java`:
  - the compressor reduces a 20 dB step by at least `sustain`-dependent amounts and makes up its level;
  - the overdrive and distortion harmonic contents grow with drive and gain;
  - `tone` moves the 3 kHz content by at least 6 dB;
  - `level` 0 to 10 spans at least 30 dB;
  - finite and deterministic.
  - *Done as one file, `AET/monitor/fx/DrivePedalsTest.java`, with the same checks.*
- [X] T031 [P] [US2] Create `AET/monitor/fx/ChorusTest.java`, `EchoTest.java` and `ReverbTest.java`:
  - the echo of an impulse repeats at `time` ± 1 sample, each repeat lower by the `repeats` factor, darker with a lower `tone`;
  - its tail continues after the input stops (US2/AC2), and a 1.5 s time is allowed;
  - the chorus delay stays within 7–20 ms;
  - the reverb tail's decay time grows with `size`;
  - `mix` 0 is dry;
  - every one is finite after 60 s of white noise at full scale.
  - *Done as one file, `AET/monitor/fx/SpacePedalsTest.java`, with the same checks.*
- [X] T032 [P] [US2] Create `AET/monitor/SmoothChangesTest.java` (FR-006, SC-003). A sine runs through the chain while:
  - every knob of every kind jumps from its minimum to its maximum;
  - each pedal is switched on and off;
  - a pedal is added, removed and moved within its section;
  - the chain changes from each ready-made chain to each other one (a sound picked).
  In every case the largest sample-to-sample step stays under 1.5× the largest step of the same run with no change, and the change is fully applied within 0.1 s.
- [X] T033 [P] [US2] Create `AET/monitor/ReadyMadeSilenceTest.java` (FR-011, US1/AC4): each of the four chains of `ReadyMadeChains`, fed with a −70 dBFS noise, through the output stage at the default guitar volume (0.7), gives less than −60 dBFS RMS at the output after 1 s.
- [X] T034 [P] [US2] Create `AET/monitor/ChainCostBenchmarkTest.java` (`@Tag("benchmark")`): the full chain of research R4 (every kind on, high gain, 4× oversampling) over 60 s of signal at `BLOCK_FRAMES`. It reports the mean and the 99th percentile of the processing time per block as a share of the block time. Goal: under 25 %; recorded in `baseline.txt`.
- [X] T035 [P] [US2] Create `AET/monitor/MonitorEnduranceBenchmarkTest.java` (`@Tag("benchmark")`, SC-006): the full chain of T034 on a fake device paced by the real clock for 10 minutes; it reports dropped and silent blocks (goal: 0) and the heap growth after a one-minute warm-up (goal: under 1 MB); recorded in `baseline.txt`.
  - *Result (2026-11-02): the goal is not met: 20 to 30 dropped blocks in 10 minutes on the owner's Mac, heap growth none. The chain allocates nothing (ChainAllocationTest) and no collection runs during the measure (GC log); the remaining drops are the system scheduling the Java thread more than two blocks late. See `baseline.txt` T035; the owner chose a real-time audio thread: T064-T068, after which T066 dropped none.*
- [X] T036 [P] [US2] Extend the window test with a new file, `TST/ui/AmpPedalRowsSwtTest.java`. It checks:
  - *Add pedal…* lists only kinds not in the chain and places each in its own section;
  - up and down move a pedal only within its section;
  - remove takes it out;
  - each row has its switch, its sliders and its tooltips;
  - every change reaches `setChain`;
  - a new window over the same persistence shows the same chain and settings (US2/AC3).

### Implementation

- [X] T037 [P] [US2] Create `AE/monitor/fx/Compressor.java`, `Overdrive.java` (2× oversampled) and `Distortion.java` (4× oversampled). Make T030 pass.
- [X] T038 [P] [US2] Create `AE/monitor/fx/Chorus.java`, `Echo.java` (preallocated 1.5 s line, interpolated reads, low-pass in the loop) and `Reverb.java` (8 combs and 4 all-passes, rewritten from the public Schroeder–Moorer structure). Make T031 pass.
- [X] T039 [US2] Wire the pedals into `AE/monitor/Chain.java`:
  - one preallocated instance per kind, so a change never allocates in the loop;
  - a 10 ms crossfade bypass and knob ramps;
  - a structural change (a pedal added, removed or moved, or a new chain) crossfades from the old order to the new one over 10 ms.
  - *As built:* a pedal added or removed crossfades with its dry signal over 10 ms (it fades out where it was). A change of order among the pedals that sound dips the chain's output for 10 ms (down in 5 ms, the new order, up in 5 ms), as the amp does for a change of character: with one instance per kind, the old and the new order cannot run side by side. After a reset, the chorus keeps the dry signal until its line is full, and the echo and reverb fade their input in over 10 ms, so a delay pedal switched on mid-note does not click when its delayed signal arrives. T032 checks all of it.
  Make T032 and T033 pass; if a ready-made chain fails T033, retune it in `ReadyMadeChains` and in the data-model table (for example a noise gate in *Clean echo*). Run T034 and T035 and record them.
- [X] T040 [US2] Add the pedal rows to `PR/ui/AmpChainRows.java` (add, remove, up, down, switch, sliders with values), and their keys in `PR/ui/AmpTexts.java` with their labels and tooltips in the three `LANG` files. Make T036 and T021 pass.
- [X] T041 [US2] Gate and commit.

## Phase 5: User Story 3 - Sounds I can save, and the sound of each score (Priority: P2)

**Goal**: the four ready-made sounds, the learner's own, save, rename, delete and restore, and the last sound of each score coming back by title and artist.

**Independent Test**: save two sounds, restart, switch between them; pick *Lead* for score A and *Clean* for score B, restart, reopen A and B: each one gets its own sound back.

### Tests first

- [X] T042 [P] [US3] Create `TST/session/ScoreKeyTest.java` (FR-019, data-model "ScoreKey"). It checks:
  - title and artist are lower-cased, trimmed and joined; the key is the 16-character hex SHA-256 prefix, and no title text appears in it;
  - the file name is used when both are empty; no key when all three are empty;
  - the same title and artist in two files give the same key;
  - renaming or moving the file keeps the key.
- [X] T043 [P] [US3] Create `TST/session/AmpSoundStoreTest.java` with an in-memory `ArcadeChoices.Persistence` (the settings part is covered by T020). It checks:
  - a fresh store gives current "Clean" and the four ready-made sounds in the order of data-model, their chains equal to `ReadyMadeChains`;
  - `saveAs` keeps a sound across a new store over the same persistence;
  - names are "trimmed, from 1 to 40 characters, and unique regardless of case"; a clash with a ready-made name is refused;
  - rename and delete apply to own sounds only;
  - a changed ready-made sound shows as changed, and `restore` brings back its chain;
  - `remember(A, "Lead")` and `remember(B, "Clean")`, then `soundFor(A)` gives Lead and `soundFor(B)` gives Clean, also after a new store;
  - an unknown score gives `last`;
  - a deleted remembered sound gives `last` (US3/AC6);
  - a malformed stored chain falls back to the ready-made sound of that name, or to Clean;
  - picking a sound sets `chain.current` to its chain, and a changed current chain kept at closing shows the sound as "(changed)" in a new store.
- [X] T044 [P] [US3] Create `TST/ui/AmpSoundListSwtTest.java`. It checks:
  - the sound list shows ready-made sounds then own sounds;
  - picking one sends its chain at once;
  - a change shows "(changed)";
  - *Save as…*, *Rename…*, *Delete* and *Restore* are enabled only when they apply;
  - with a sound remembered for score A and another for score B, opening the practice window on A then on B gives each its remembered sound before any run (FR-019, US3/AC4).

### Implementation

- [X] T045 [US3] Create `PR/session/ScoreKey.java` and `Sound.java` (the ready-made ones from `ReadyMadeChains`), and add the sounds part to `PR/session/AmpSoundStore.java` (contracts/amp-window.md; the `sound.*`, `ready.*` and `score.*` keys of data-model). Make T042 and T043 pass; T020 stays green.
- [X] T046 [US3] Add the sound list and its buttons to `PR/ui/AmpWindow.java`. In `PR/ui/PracticeWindow.java`:
  - when the practice window shows its preparation (`showPreparation`, which reads the score in front through `host.song()`), and so whenever the score in front has changed, set the current sound to `soundFor(ScoreKey.of(song.getName(), song.getArtist(), scoreName()))`;
  - when the learner picks a sound, call `remember`;
  Keys go in `PR/ui/AmpTexts.java`, labels and tooltips in the three `LANG` files. Make T044 (including its score A and score B case) and T021 pass.
- [X] T047 [US3] Gate and commit.

## Phase 6: User Story 4 - Safe by default (Priority: P2)

**Goal**: silent at first, the microphone warning, a howl cut within 0.5 s, a lost device handled, and the overload lightening.

**Independent Test**: a fresh install is silent; turning the sound on with the built-in microphone warns; a synthetic howl is muted within 0.5 s; a lost device stops the sound with a message while the practice goes on.

### Tests first

- [ ] T048 [P] [US4] Create `AET/monitor/FeedbackGuardTest.java` (FR-012, SC-005, research R5). It checks:
  - a synthetic howl (a 2.3 kHz sine rising 6 dB per 100 ms over guitar-like noise) fed through the engine mutes the output within 0.5 s of crossing the rule, state MUTED_FEEDBACK;
  - the four ready-made sounds played on the recorded guitar fixtures (including long sustained notes and the echo's tails) never trigger it;
  - turning the sound on again restarts it.
- [ ] T049 [P] [US4] Create `AET/monitor/OverloadTest.java` (FR-014). A fake clock makes each block take 60 % of the block time. After 20 blocks the reverb goes off with a message, then the chorus, then the oversampling drops to 2× and 1×, then only the amp and cabinet are kept (every pedal bypassed), each step with a message, then the state is MUTED_OVERLOAD. The capture's `deliver` is never slowed (it returns within a fixed time).
- [ ] T050 [P] [US4] Create `TST/ui/AmpSafetySwtTest.java`. It checks:
  - with the built-in microphone as input, turning the sound on shows the warning, and the sound starts only after *Turn on anyway*;
  - the message line shows the feedback, overload, device-lost and input-clipping messages from engine events (the clipping message gives the advice of the gain control);
  - a fresh store opens with the switch off.

### Implementation

- [ ] T051 [US4] Create `AE/monitor/FeedbackGuard.java` (the existing `RealFft` every 10 ms, research R5), and add the overload lightening to `AE/monitor/Chain.java` and `MonitorEngine.java`. Make T048 and T049 pass.
- [ ] T052 [US4] Add the warning dialog and the message line (feedback, overload, device lost, input clipping) to `PR/ui/AmpWindow.java`, with the input kind from `CurrentInputSelection` (built-in microphone versus other inputs) and the messages in `PR/ui/AmpTexts.java` and the three `LANG` files. Make T050 and T021 pass.
- [ ] T053 [US4] Gate and commit.

## Phase 7: The native duplex device (required by T003; done right after T014, before T024)

- [X] T054 Create the crate `desktop/build-scripts/native-modules/tuxguitar-monitor-native/` (research R1 addendum, planned with the owner on 2026-11-01):
  - `Cargo.toml` (`cpal` 0.15.3, `jni` 0.21, `crate-type = ["cdylib"]`) and `Cargo.lock` committed;
  - `src/lib.rs`: open the input (matched by name, else the default; the given channel, else the first) and the output (likewise), each at its device's current rate (never changed), with a fixed buffer of the given frames; an adaptive resampler from the input's rate to the output's, nudged by the output ring's fill (rate difference and clock drift); two lock-free single-producer, single-consumer rings between the callbacks and Java; at most two input blocks waiting (the oldest dropped and counted), silence played and counted when the output ring is empty; the devices' declared latencies reported; no effect in Rust;
  - `cargo test` for the rings (order, drop-oldest, silence counted, no allocation after open) and the resampler (a sine through 48 to 44.1 kHz keeps its frequency within 0.1 %, the ring's fill stays bounded over a simulated hour with a 100 ppm clock difference);
  - `pom.xml` running `cargo test` then `cargo build --release --target <triple>` through `maven-antrun-plugin`, as `tuxguitar-audiounit-macos/pom.xml` runs `make`;
  - listed in the `native-modules` profile of `desktop/build-scripts/tuxguitar-macosx-swt-cocoa`, `tuxguitar-linux-swt` (and its `-deb`) and `tuxguitar-windows-swt-x86_64`, the library copied next to the other native libraries of each build.
- [X] T062 Install a pinned Rust toolchain and its targets in `ubuntu-maven.yml` and `release.yml` (`macos-maven.yml` builds without `-P native-modules`, so it needs none; the toolchain is in `.github/actions/install-rust`, Rust 1.93.0) (aarch64-apple-darwin on `macos-15`; x86_64-unknown-linux-gnu with `libasound2-dev` on `ubuntu-24.04`; x86_64-pc-windows-gnu cross-compiled on Linux with the Mingw-w64 already installed), and check each package contains the library (as the workflows check the other native files).
- [X] T063 Create `AET/monitor/NativeMonitorProbeTest.java` (`@Tag("benchmark")`, quickstart §1b): the round trip through `NativeMonitorDevice` at 64, 128 and 256 frames with 20 clicks per setting, the input chosen by `-Dprobe.input` (else the default), reported with the devices' declared latencies and the dropped and silent blocks in `specs/022-guitar-amp-effects/benchmark/native-probe.md`. Run it on the owner's Mac (built-in microphone; the USB microphone if plugged in) and check each median is within 3 ms of the declared latencies plus both buffers.

### The real-time audio thread (owner's decision of 2026-11-02 after T035, research R3 addendum)

- [X] T064 Add a test first in the crate, `src/realtime.rs` (`cargo test`): `promote_current_thread(block_frames, sample_rate)` on macOS gives the calling thread the time-constraint policy (read back with `thread_policy_get`: period one block, computation half of it, constraint one block); on Linux and Windows it returns either the policy taken or the reason it was refused, never a panic; on a fresh thread each time.
- [X] T065 Implement `src/realtime.rs` with `libc` (macOS, Linux) and `avrt` declared by hand (Windows), and its JNI entry `NativeMonitorLibrary.promoteThread(int blockFrames, float sampleRate)` (the policy taken, or why not, as text; tied to no device, so the benchmark can use it). `NativeMonitorDevice.open`, which runs on the engine's thread, calls it once the rate is known and logs the result once. Make T064 pass.
- [X] T066 Create `AET/monitor/RealtimeEnduranceBenchmarkTest.java` (`@Tag("benchmark")`; the committed T035 test is not changed): the run of T035, its paced device and its full chain, with the engine's thread promoted through the crate's library (loaded as `NativeMonitorProbeTest` does) at its first read; the report (`benchmark/endurance-realtime.md`) says which policy was taken. Run it for 10 minutes; goal 0 dropped blocks; record it beside T035 in `baseline.txt`.
- [X] T067 Run the native probe (T063) again with the owner's USB microphone (warn the owner: audible clicks), to check the real-time thread does not change the delay; record it.
- [X] T068 Gate and commit.

## Phase 8: Polish & cross-cutting concerns

- [ ] T055 [P] Describe the sound, the *Amp and effects* window, the chain, the sounds and the sound per score, the delay, headphones and feedback, and "the sound never changes the judging" in `docs/PRACTICE.md`, `docs/PRACTICE-FR.md` and `docs/PRACTICE-ES.md` (FR-017), with a screenshot of the window in `docs/images/features/` (taken on the built app).
- [ ] T056 [P] Write the manual sheet `specs/test-sheets/F12-ampli-effets.md` (quickstart §3, plus rows for Linux and Windows testers: the delay shown, steps 2 to 5, a howl) and add it to `specs/test-sheets/README.md`.
- [ ] T057 [P] Draft the release notes of the next version in `docs/release-notes/` (FR-015, SC-009, quickstart §4): the sound, the window, the sounds, headphones and feedback, the delay reached on the owner's Mac, and the call to Linux and Windows testers to report the delay shown and a howl through sheet F12. The version number is chosen at release time, and nothing is released before T060.
- [ ] T058 Full gate: `mvn -o -e clean verify -P native-modules`, `swt-gui-tests` and T061 (the datasets' identity). Record the counts in `baseline.txt` and check the test folders show additions only.
- [ ] T059 Write `specs/022-guitar-amp-effects/final-report.md`:
  - what was built, the R1 decision with its numbers, the tests added, the cost measured;
  - where each algorithm and each ready-made sound comes from (the project's own; public structures such as the Schroeder–Moorer reverb named, rewritten), confirming FR-018;
  - what is unverified (the delay with the cable until T060, Linux and Windows).
  Update the 022 row of `specs/STATUS.md` and the memory `pending-amp-effects-spec`. Commit.
- [ ] T060 [HUMAN] The owner's gate on the relaunched app, per sheet F12, with the Rocksmith cable and headphones:
  - the delay feels like an amp (SC-001, T029);
  - the four sounds in Wait, Follow and Arcade;
  - an hour of practice without a dropout (SC-006);
  - the sound per score after a restart;
  - the microphone warning and a howl.
  Values to retune (ready-made sounds, ranges) are noted, changed and re-tried.

---

## Dependencies & execution order

- Phase 1 (T003's decision) → Phase 2 → Phase 7 (the native device) → US1 (Phase 3, MVP) → US2 → US3 → US4 → Phase 8.
- US3 and US4 depend on the window of US1, but not on each other.
- T003 required Phase 7; it comes before US1's device task T024. T062 can run alongside US1.
- Inside a phase, tests come first and are seen red; [P] tasks touch different files.

## Parallel examples

- Phase 2: T004, T006, T007 and T009 together.
- Phase 7: T062 once T054 is done; T063 needs `NativeMonitorDevice`, so it runs right after US1's T024.
- US1: T015, T016, T017, T018, T019, T020, T021 and T061 together, then T022 and T025 alongside the window work.
- US2: T030, T031, T032, T033, T034, T035 and T036 together, then T037 and T038 together.
- US3: T042, T043 and T044 together.
- US4: T048, T049 and T050 together.
- Phase 8: T055, T056 and T057 together.

## Implementation strategy

- **MVP = Phase 1 + Phase 2 + Phase 7 + US1**: the guitar heard through an amp, with the delay measured and the recognition proven untouched (recorded inputs and datasets).
- Then the pedals (US2), the sounds (US3) and safety (US4), each one gated and committed.
- **No release** before the owner's gate T060.
- **Not before US4 is done:** the sound is off by default from US1 on, and the safe ceiling is in the output stage from Phase 2. The microphone warning and the feedback guard come in US4, so before that phase the owner tries it with headphones only (said in the US1 smoke check).
