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

## R2 — Where does the guitar's signal come from without disturbing the recognition?

**Decision:** one capture, two consumers.
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
- A dedicated output thread (`TGThreadPriority.HIGH`) takes the blocks from the single-producer, single-consumer queue, runs the chain and writes them to a `SourceDataLine` on the chosen output mixer. The probe's output line buffer is the smallest that gave no underrun in R1.
- The capture thread never waits for the output. When the queue is full (the output is late), the oldest block is dropped and counted, rather than letting the delay grow. When it is empty, silence is written and counted.
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
- The Java Sound path is the same on the three systems.
- If R1 forces native modules, each system gets its own, and the CI builds each one. Linux and Windows delays are reported by testers, through the release notes and the test sheet (SC-009).
