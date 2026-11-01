# Research: the guitar heard through an amp and effect pedals (spec 022)

All findings come from the repository as it stands on 2026-11-01 (commit `1d7df8326`), unless marked "to measure".

## What exists today

- **Input only.** `desktop/TuxGuitar-audio-engine` captures the practice input and never plays a sound.
  - `input/CaptureService` opens a Java Sound `TargetDataLine` on the chosen mixer, preferably 48 kHz, 16-bit (`chooseFormat`).
  - The line buffer is `FRAMES_PER_READ * 4` = 4096 frames (85 ms at 48 kHz).
  - A worker thread (`TGThreadManager`, `TGThreadPriority.HIGH`) reads blocks of `FRAMES_PER_READ = 1024` frames (21.3 ms). It converts them (`PcmSampleConverter`), keeps one channel (`ChannelDeinterleaver`) and writes it to `SampleRingBuffer`. `CaptureClock` stamps the arrival times.
  - The analysis (`analysis/*`, `frontend/*`) reads the ring buffer by hops.
- **The score's sound** is TuxGuitar's MIDI player (`common/TuxGuitar-lib/.../player/base/MidiPlayer`), through the chosen synthesizer (Java Sound or the native AudioUnit module on macOS). Its master volume is `MidiPlayer.getVolume()` / `setVolume(int)`, from 0 to 10.
- **Native audio modules** already exist under `desktop/build-scripts/native-modules/`: `tuxguitar-audiounit-macos`, `tuxguitar-alsa-linux`, `tuxguitar-jack-linux`, `tuxguitar-winmm-windows-x86_64`, and others. They serve MIDI synthesis, not the input.
- **Settings pattern.** `practice/session/ArcadeChoices.Persistence` (a get/set of strings over `TGConfigManager`, or a map in tests) is used by `FireTuningStore` (spec 019).
- **Score metadata.** `TGSong.getName()` and `getArtist()` give the title and the artist (FR-019).
- **Test inputs.** `FakeAudioInput` and the recorded-hop fixtures replay inputs without hardware (constitution VII).

## R1 — Can Java Sound reach the delay? (to measure first)

**Unknown.** The delay heard is the input buffering, plus one processing block, plus the output buffering, plus the devices' own delays. Today the input alone waits up to 21 ms for a block, so the current read size cannot be kept for the sound.

**Decision:** Phase 1 is a probe and builds nothing for the product. A benchmark-tagged test (`@Tag("benchmark")`, excluded by default, like the real-guitar reports) does three things:
1. It opens a `TargetDataLine` and a `SourceDataLine` at 48 kHz with blocks of 64, 128 and 256 frames and line buffers of 2 to 4 blocks.
2. It plays a click and records it through the built-in microphone (speaker to microphone, about 30 cm, so about 1 ms of air), 20 times per setting.
3. It reports the median and the 95th percentile of the round trip, and the underruns over 60 s of pass-through.

The owner then repeats the same probe with the Rocksmith cable, using a short cable from the headphone output to an instrument input if one is at hand, or by ear otherwise.

**Gate:**
- **Round trip within 20 ms:** Java Sound is used.
- **Between 20 and 30 ms:** Java Sound is used, and the delay is shown (owner's answer).
- **Above 30 ms at every setting:** the plan adds a small native duplex module per system, isolated behind an interface `MonitorDevice` (constitution IX). On macOS it would be CoreAudio, following `tuxguitar-audiounit-macos`. On Linux it would be ALSA or JACK, following the existing modules. On Windows it would be WASAPI. That branch is planned in `tasks.md` as a conditional phase, as spec 019 did for its core hook.

**Alternatives considered:**
- JACK only: not installed by default on macOS or Windows.
- PortAudio through JNI: a new native dependency on three systems, kept as the fallback of the fallback.
- Delaying the decision to the implementation: the constitution (VI) asks for the delay to be verified, not assumed.

**Measured (T003, 2026-11-01, the owner's MacBook Pro, Java 21, built-in microphone and speakers, both running at 44.1 kHz behind 48 kHz lines):**
- Java Sound hands the microphone over in bursts of 512 device frames (557 to 558 frames at 48 kHz, every 11.6 ms), and the output line is drained in bursts of the same size, whatever buffer is asked (measured apart, by polling `available()`).
- Run 1 (one loop, no drop): 64 × 2 gave a median of 37.6 ms and a minimum of 37.3 ms, then the delay grew by itself up to 298 ms (95th percentile 221 ms): a line buffer smaller than one output burst is not emptied at the input's pace. Stopped after this row.
- Run 2 (the input backlog capped at 1024 frames plus four blocks, `benchmark/latency-probe.md`): every one of the nine settings has a median between 101 and 126 ms; only 256 × 3 and 256 × 4 (line buffers of 768 and 1024 frames, larger than a burst) ran without dropping, at 107.8 and 104.2 ms, and they had 1 and 3 underruns.
- Run 3 (the input capped at one burst and one block): 64 × 2 still gave a median of 86.3 ms; stopped after this row at the owner's request (the clicks are audible). The output line alone keeps about 55 to 60 ms.
- No Java Sound setting reaches 30 ms.
- Native pre-check (owner's choice, 2026-11-01): a throwaway CoreAudio program (two AUHAL units, the default input and output at their own 44.1 kHz, IO buffer of 64, 128 or 256 frames, 20 clicks timed by host time) gave medians of 50.9, 52.3 and 53.0 ms. The devices themselves declare it: the built-in microphone's stream latency is 1328 frames (30 ms, its voice processing) plus a safety offset of 50, the speakers 65 + 639 frames plus 74 (17.6 ms); with two 64-frame buffers that is 51.8 ms, as measured.
- Rust pre-check (owner's request, same day): the same probe written in Rust with `cpal` 0.15.3 gave 48.7, 50.1 and 53.0 ms at 64, 128 and 256 frames, the same as C: a native path adds almost nothing to what the devices declare.
- USB microphone (owner's Samson Q2U, declared input 74 + 74 frames at 48 kHz = 3.1 ms; its headphone output 2.1 ms), same Rust program, the Mac's speakers as output: 23.3, 24.8 and 27.7 ms at 64, 128 and 256 frames (95th percentile equal to the median): again the devices' declared latencies plus both buffers. With headphones on the Q2U's own output instead of the speakers (17.6 ms), about 8 ms is expected.
- **So the built-in microphone with the built-in speakers cannot reach 30 ms through any programming interface: about 49 ms is in the hardware. Java Sound adds about 35 ms more (86 ms against 51 ms at 64 frames). The 30 ms gate has to be judged on the setup the learner plays with (the Rocksmith cable or another instrument input, with headphones); the decision is taken with the owner.** Whatever device is used, the engine's queue must hold at least one input burst plus one block (`QUEUE_BLOCKS` × `BLOCK_FRAMES` = 512 frames is smaller than a 557-frame burst and would drop part of every burst).

## R1 addendum — The native device (T054, owner's decisions of 2026-11-01, to validate before coding)

**Owner's decisions:** the native device is written in **Rust with `cpal`**, one code for the three systems; it opens **its own input**, separate from the recognition's capture. The owner assumes the Rocksmith cable behaves like the built-in microphone did (a native path close to the devices' own delay); the cable is tested later.

**Checked before writing this (throwaway programs in the session's scratchpad, nothing in the product):**
- `cpal` 0.15.3 on the owner's Mac: 48.7 ms at 64 frames, the devices' own 49 ms (R1 "Measured").
- While a Java Sound `TargetDataLine` held the built-in microphone (as `CaptureService` does), the Rust program opened the same microphone and measured 48.5 ms, and the Java capture kept getting its bursts every 11.6 ms with no gap: two clients of one input work on macOS (CoreAudio shares inputs). This lifts the objection of R2's first alternative, which was about a second Java Sound line.

**Decision:**
1. **Module.** A Rust crate `desktop/build-scripts/native-modules/tuxguitar-monitor-native/` (`Cargo.toml` with `Cargo.lock` committed; `cpal` 0.15.3 for CoreAudio, ALSA and WASAPI; `jni` 0.21 for the bridge; licences Apache-2.0/MIT, compatible with LGPL). Its `pom.xml` runs `cargo build --release --target <triple>` through `maven-antrun-plugin`, as `tuxguitar-audiounit-macos/pom.xml` runs `make`, and it is listed in the `native-modules` profile of the three SWT build scripts (macOS, Linux, Windows). The library is copied next to the other native libraries of each build and loaded the way the AudioUnit plugin loads its own.
2. **What the native side does, and only that.** It opens the input (the practice input's device, matched by name, else the system default; the practice input's channel, else the first) and the output (the chosen output, else the default), with a fixed buffer of `BLOCK_FRAMES`, and moves samples between the devices' callbacks and two lock-free single-producer, single-consumer rings. No effect runs in Rust: the chain stays the plain Java of R4 (constitution VIII, the tests of US1 to US4 unchanged).
   - **Each device runs at its current rate, never changed.** Read in `cpal` 0.15.3's CoreAudio code: asking a device for another rate changes the device's nominal rate (a system setting). So each stream is opened at its device's current rate, the chain runs at the input's rate, and a small adaptive resampler in the crate maps it to the output's rate; its ratio is nudged by the output ring's fill, which also absorbs the slow drift between two devices' clocks (two devices at the same nominal rate still drift), so the delay does not creep over an hour (SC-006). The ring aims at two output blocks as a callback starts (about one and a half on average): with one, a callback taking its block often ran the ring dry before Java's next block came (108 dry callbacks in 4000 in the crate's test). A dry ring plays silence, counted.
   - **The estimated delay** uses the latencies the devices declare where the system gives them (CoreAudio, as measured above), plus both buffers and the resampler's ring; elsewhere, the buffers only. It is shown as an estimate (FR-002, R8).
3. **Java side.** `MonitorDevice` becomes duplex: `open(inputName, inputChannel, outputName, blockFrames)` gives an `Opened` with `int read(float[] in, int n)` (waits for at most one block time; returns the frames read, 0 on a gap, counted as silence), `write(left, right, n)`, `sampleRate()` and `estimatedLatencyFrames()` (the devices' declared latencies plus both buffers, R8). `NativeMonitorDevice` implements it over JNI. The output thread of R3 loops read → chain → output stage → write. If the native library cannot be loaded, the sound fails with a clear message (FR-013); there is no Java Sound fallback, since it would be about 35 ms slower than the devices.
4. **The capture is not touched.** `CaptureService` stays exactly as it is: no `setMonitor`, no change of read size. FR-003 holds by construction. The identity test (T009) is kept: the analysis frames are identical with the sound running on a fake duplex device. **Owner's request (2026-11-01): the note and chord recognition is also validated on the recorded datasets** already used by the benchmark reports (`~/tuxguitar-datasets`, never committed: GuitarSet, IDMT-SMT-GUITAR_V2; the practice module's `dataset/` benchmarks such as `DatasetBenchmarkTest`, `GuitarSetChordBenchmarkTest`, `TechniqueDatasetBenchmarkTest`, `OwnerChordsBenchmarkTest`). They run twice, sound off and with a `MonitorEngine` running each ready-made chain on a fake duplex device fed with the same recording in the same process, and their outcome files in `target/dataset-benchmark` must be identical line for line. It is benchmark-tagged (excluded by default, like the reports), run at the gate of US1 and at the final gate, and its result goes in the final report.
5. **Block size.** `BLOCK_FRAMES = 128` (2.9 ms at 44.1 kHz): 1.4 ms slower than 64 in the pre-check, with more margin for the Java thread. The native rings hold at most two blocks of input waiting; beyond that the oldest is dropped and counted (R3).
6. **Builds and CI.** macOS on `macos-15` (aarch64-apple-darwin); Linux on `ubuntu-24.04` (x86_64-unknown-linux-gnu, with `libasound2-dev`); Windows cross-compiled on Linux as today (x86_64-pc-windows-gnu with the Mingw-w64 already installed). The workflows install a pinned Rust toolchain and the targets. Local builds need `rustup` (present on the owner's Mac: Rust 1.93).
7. **Its own probe run.** A benchmark-tagged `NativeMonitorProbeTest` measures the round trip through `NativeMonitorDevice` with the same clicks as T002, on the built-in microphone; the cable is measured later by the owner.

**What changes in the other documents (once validated):** R2, R3 and R9 here; `plan.md` (structure and phases); `contracts/monitor-engine.md` (`MonitorDevice` duplex, no `offer` from the capture, the `CaptureService` change removed); `tasks.md` (T008 and T013 not needed, T009 on the duplex fake device, a dataset identity task (sound off and on, outcome files identical), T018 and T024 replaced by the native device's tasks, T054 detailed); `quickstart.md` §1.

**Alternatives considered:**
- C, one module per system: three codes, no new toolchain (not chosen by the owner).
- One native input that also feeds the recognition: more of the capture touched, FR-003 then rests on the identity test only (not chosen).
- Rust with `cpal` for the output only: the input would still come in Java Sound bursts of 11.6 ms.

## R2 — Where does the guitar's signal come from without disturbing the recognition?

**Superseded on 2026-11-01 by the R1 addendum** (the owner chose a separate native input): the native device opens the input itself, beside the recognition's capture, and `CaptureService` is not touched, so FR-003 holds by construction whether the sound is on or off. The identity checks remain (the recorded inputs, T009, and the datasets). The text below is the first decision, kept for the record; its Java Sound bursts of 11.6 ms are what ruled it out.

**First decision (not used):** one capture, two consumers.
- When the sound is **off**, `CaptureService` runs exactly as today: same read size and same code path. The new code is not called, so FR-003 holds by construction.
- When the sound is **on**, the capture reads small blocks (the size chosen by R1). For each block, it writes the same samples to `SampleRingBuffer` as today, and also hands a copy to the `MonitorPath` (lock-free, single producer and single consumer).
  - The ring buffer receives the same samples in the same order. Only the size of each write changes.
  - The analysis reads by hops, independently of the write size.
  - `CaptureClock` gets more, smaller stamps. It already estimates the clock from many reads, so it becomes more precise, not less.
- A test feeds the same recorded input with the sound off and on (any chain) and checks identical analysis frames (pitch, level, onset), hence identical verdicts and summaries (SC-002).

The monitor gets the raw input **before** the front end. The front end's gain and high-pass are for the analysis, not for the sound. The sound has its own input level (the amp's input gain).

**Alternatives considered:**
- A second `TargetDataLine` on the same device: Java Sound on macOS may refuse it or give a different clock, so it is not dependable.
- Feeding the monitor from the ring buffer by hops: it adds a hop of delay (at least 10 ms).

## R3 — Processing thread and output

**Decision:**
- A dedicated thread (`TGThreadPriority.HIGH`) reads one block from the native device's input ring, runs the chain and writes it to the native output ring (R1 addendum). The device's buffer is `BLOCK_FRAMES` frames.
- The device's callbacks never wait for Java. When the input ring holds more than two blocks (Java is late), the oldest block is dropped and counted, rather than letting the delay grow. When the output ring is empty at a callback, silence is played and counted.
- Every buffer is allocated when the sound starts. Nothing is allocated, locked or logged in the loop (constitution VI).
- The parameters are published as an immutable `ChainSettings` snapshot through an `AtomicReference`. The thread reads it once per block, and each knob ramps to its new value over the block (FR-006: no click).
- The mono guitar signal is written to both channels of a stereo output. The echo, the chorus and the reverb may widen it.

**Alternatives considered:**
- Processing in the capture thread: the output's blocking write would delay the capture, and so the recognition.
- One thread per effect: needless.

## R4 — The effects (constitution VIII: deterministic, explainable)

All the effects are written in plain Java in the audio engine, with no new library and no recorded sound of commercial gear (FR-018). Each one is a pure class `process(float[] in, float[] out, int n)` with its own state, tested on synthetic signals.

| Element | Method | Knobs (default) |
|---|---|---|
| Noise gate | envelope follower, threshold with hysteresis, attack 1 ms, release 50–500 ms | threshold (−60 dB), release (150 ms) |
| Compressor | feed-forward RMS detector, soft knee, make-up gain | sustain (amount), attack, level |
| Overdrive | 2× oversampled `tanh` soft clipping, a tone low-pass before it and a mid bump | drive, tone, level |
| Distortion | 4× oversampled asymmetric hard clipping with a pre-emphasis and a post low-pass | gain, tone, level |
| Amp | input gain; 1 to 3 waveshaper stages by character (clean: 1 light stage; crunch: 2; high gain: 3, 4× oversampled); a tone stack of 3 biquads (bass 100 Hz shelf, middle 700 Hz peak, treble 3 kHz shelf); presence; master | character (clean / crunch / high gain), gain, bass, middle, treble, volume |
| Cabinet | fixed EQ of biquads: high-pass 80 Hz, a resonance near 120 Hz, a broad mid presence, a steep low-pass near 5 kHz (a 4×12-like curve, the project's own); can be switched off | on / off |
| Chorus | two modulated delays of 7–20 ms, sine LFO | rate, depth, mix |
| Echo | delay line up to 1.5 s, feedback with a low-pass in the loop, interpolated reads | time, repeats (feedback), tone, mix |
| Reverb | Schroeder–Moorer reverb (8 comb filters with damping and 4 all-pass filters, the public "Freeverb" structure, rewritten) | size, damping, mix |
| Output stage (always on, not a pedal) | guitar volume, start ramp of 0.5 s, safety limiter at −1 dBFS (a look-ahead-free peak limiter) and a hard clamp at 0 dBFS | — |

- **Oversampling** uses polyphase half-band filters (fixed coefficients), only in the clipping stages.
- **Cost budget:** the full chain of R4, at 48 kHz, in under 25 % of the block time on the owner's Mac. It is measured by a benchmark test. Above that, FR-014's simplification first drops the reverb, then the chorus, then the oversampling factor, then keeps the amp alone (every pedal bypassed), with a message each time, and mutes if that is still too much (spec edge case).

## R5 — Feedback guard (FR-012, SC-005)

**Decision:** an always-on detector on the processed output.
- A block FFT (the existing `RealFft`) every 10 ms.
- A howl is one narrow peak, more than 20 dB above the median of the spectrum, whose level keeps rising or stays above −12 dBFS for 300 ms.
- When a howl is found, the output ramps to silence within 20 ms, the window says why, and the sound stays off until the learner turns it on again.

With the built-in microphone chosen as input, turning the sound on shows the headphones warning first.

## R6 — Sounds, the sound per score, and storage

- **Sounds** are stored with `TGConfigManager` in a new module `tuxguitar-practice-amp`:
  - `sound.<n>.name` and `sound.<n>.chain`, the chain being a compact text such as `gate:on,-60,150|amp:crunch,5,5,5,5,6|cab:on|echo:on,380,3,5,30`;
  - `sound.current`, `enabled`, `output`, `guitar.volume`, `window.bounds` (the score volume is TuxGuitar's own, not stored here).
  - Ready-made sounds have fixed names and can be restored.
  - Text keeps the existing settings mechanism (no new file format); a malformed entry falls back to the ready-made sound and is logged once.
- **Sound per score**, by title and artist (owner's answer):
  - the key is `score.<key>` where `<key>` is the lower-case title and artist joined by ` — ` and passed through a stable hash;
  - when both are empty, the key is the file name;
  - the value is the name of the sound;
  - the last used sound is `sound.last`.
- **Score volume**: the balance slider sets `MidiPlayer.setVolume` (0 to 10), the same value as TuxGuitar's volume control. The guitar's volume is the output stage gain.

## R7 — The *Amp and effects* window (FR-020)

- It is a `UIWindow` of the toolkit, built like the other practice windows (`PreRunWindow`, `CalibrationWindow`), owned by the main window.
- Its first block holds *Amp sound* on/off, the sound list (pick, save as, rename, delete, restore), the output device, the guitar volume, the score volume and the estimated delay (R8).
- Below come the chain rows: pedals before the amp, the amp, the cabinet, then the pedals after it. Each row has a switch, sliders with values and tooltips, and up and down buttons to reorder the pedals inside their section. An *Add pedal* list offers the kinds not yet in the chain.
- The window is opened by a button of the practice window and by a button of the palette (`PracticePalette`).
  - During a run it opens at its last place, or else beside the score away from the framed note. It uses the boxes of `PaletteDodge`/`ScoreGuardBoxes` (specs 017 and 019) to choose a side.
  - It never takes the focus from the run's keys.
- It closes with the practice window, and its bounds are kept.

## R8 — Delay shown (FR-002)

The window shows the estimated delay: input block + output line buffer + processing block, from the line formats. It is named as an estimate. The end-to-end delay measured by the R1 probe is a development check (SC-001) and is not shown in the application (FR-002). Bluetooth outputs are named in the tooltip as adding their own delay.

## R9 — Parity and CI (FR-015)

- The pure DSP and the queue are plain Java and run in every build's tests.
- R1 forced the native device: one Rust crate with `cpal` (CoreAudio, ALSA, WASAPI) is built by the three build scripts and by the CI on each system's job (Windows cross-compiled on Linux as today). Linux and Windows delays are reported by testers, through the release notes and the test sheet (SC-009).
