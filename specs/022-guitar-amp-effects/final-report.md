# Spec 022 — final report: the guitar heard through an amp and pedals

Date: 2026-11-02. Branch `feature/interactive-practice`. Tasks: 68 of 72 done (T069-T072 added by /speckit-converge and done). The 4 left need the owner or the built application: T028 (its smoke check), T029 and T060 (the owner's gate, sheet F12), and T055 (its text is written; the window's screenshot waits for the built application).

## What was built

- **The sound** (US1): while the practice window is open, the guitar's own input is played through the chain and an output stage, mixed with the score (FR-001). It is off by default and starts with a half-second fade in. The *Amp and effects* window has the switch, the output, the two volumes, the delay line, then the chain: pedals before the amp, the amp (*Clean*, *Crunch*, *High gain*; gain, bass, middle, treble, volume), the cabinet, and pedals after it. Every control has a tooltip in English, French and Spanish.
- **The pedals** (US2): noise gate, compressor, overdrive, distortion, chorus, echo and reverb, added, removed, switched and reordered while playing, without clicks: 10 ms crossfades, 10 ms dips on reorder, ramped knobs, gliding filters.
- **The sounds** (US3): four ready-made sounds and the learner's own, saved, renamed and deleted. A changed ready-made sound keeps its changes until *Restore* (owner's answer of 2026-11-02). The sound of each score is remembered by a hash of its title and artist (else its file name), and comes back before any run.
- **Safety** (US4): the headphones warning with the built-in microphone, a feedback guard that cuts a howl within half a second, a lost device that stops the sound while the practice goes on, the overload lightening (reverb, chorus, oversampling 2× then 1×, pedals bypassed, then muted), and an output never above −1 dBFS.
- **The native duplex device** (Phase 7): a Rust crate on cpal 0.15.3, with its own input and output each at the device's current rate (never changed), an adaptive resampler, lock-free rings, the devices' declared latencies, and JNI. It is built by Maven on the three systems, with Rust 1.93.0 in CI.
- **The real-time audio thread** (owner's decision after T035): Mach time constraint on macOS, MMCSS "Pro Audio" on Windows, SCHED_FIFO on Linux when allowed. The capture of the recognition (`CaptureService`) is untouched: the sound has its own input.

## The R1 decision and its numbers

- **The delay probe (T003):** Java Sound gave 37 ms at best and 86 to 126 ms in practice, above the owner's 30 ms ceiling. So the native device was required, with 128-frame blocks (owner's decisions of 2026-11-01).
- **The native probe** (clicks through the real devices, 128 frames, real-time thread):
  - USB microphone (Samson Q2U, 48 kHz) into the built-in speakers (44.1 kHz): 32.5 ms (26.4 at 64 frames, 43.5 at 256);
  - built-in microphone and speakers: 60.6 ms, mostly the microphone's own hardware (49 ms by itself).
  - The Rocksmith cable with headphones is not measured yet (T029/T060).
- **Endurance:** 10 minutes of the heaviest chain on a normal thread dropped 20 to 30 blocks (OS scheduling). With the real-time thread: 0 dropped blocks, worst gap between two reads 1.1 ms, heap −0.07 MB (SC-006 on macOS).
- **Cost:** the full chain (every pedal, high gain at 4×, cabinet) takes 4.3 % of a 128-frame block at 48 kHz (99th percentile 4.5 %), well under the 25 % goal. With the output stage and the feedback guard (T072, benchmark/engine-cost.md): 4.7 % (99th percentile 5.9 %), the guard alone 0.3 %.

## Tests added

- **Audio engine:** the chain settings and their text, the amp, the cabinet, the gate, the drive and space pedals, smooth changes, allocation in the loop, the ready-made sounds' silence, the engine on a fake duplex device (start, stop, a lost device, a late input), the feedback guard (a synthetic howl muted within 0.5 s; the recorded guitars and long notes through the four sounds never trigger it), the overload lightening, the native device, and `RecognitionUntouchedTest`.
- **Benchmarks** (tag `benchmark`): the chain's cost, the endurance (on a normal thread and on the real-time thread), the native probe, and the datasets' identity (T061).
- **Practice:** the settings, `ScoreKey`, the sounds store, the texts in three languages, and on the real SWT toolkit the window, the pedal rows, the sound list (including the sound of score A and of score B) and the safety.
- **Crate:** the rings, the resampler and the real-time promotion (`cargo test`).
- **Final gate (T058),** `mvn -o -e clean verify -P native-modules`: BUILD SUCCESS, audio engine 438, practice 1420 (12 skipped), cargo 9; `swt-gui-tests` practice 75.
- **The datasets' identity (T061),** sound off and with the four ready-made sounds playing real recordings beside them: outcomes.csv 943 lines, report.txt 64, guitarset-chords-outcomes.csv 301, technique-report.txt 29, owner-chords.txt 46, all identical (compared sorted: the benchmarks analyse in parallel).
- No existing test was modified: the test folders show additions only at every gate.

## Where each algorithm and each ready-made sound comes from (FR-018)

Everything is the project's own, written in plain Java in the audio engine with no new library and no recorded sound of commercial gear:

- **Textbook building blocks:**
  - envelope followers (gate, compressor);
  - `tanh` and asymmetric clipping (overdrive, distortion, amp);
  - polyphase half-band oversampling;
  - RBJ-style biquads (tone stacks, cabinet curve);
  - modulated delay lines (chorus, echo);
  - the radix-2 FFT already in the engine (feedback guard).
- **The reverb** follows the public Schroeder–Moorer structure known as "Freeverb" (8 damped comb filters and 4 all-passes), rewritten.
- **The cabinet's curve** is the project's own (a high-pass, a low resonance, a mid presence and a steep low-pass), with no impulse response of a real cabinet.
- **The four ready-made sounds** (*Clean*, *Crunch*, *Lead*, *Clean echo*) and their values are the project's own starting points, tuned by tests (silence with a quiet input, no howl detected on the recorded guitars). No name, picture, preset or sound was taken from Rocksmith or from a maker; Rocksmith was an experience reference only.

## What is unverified

- **The delay with the Rocksmith cable and headphones,** and the feel of the sound: the owner's gate (T029, T060, sheet F12).
- **An hour of practice** with the sound on (SC-006 by ear), the four sounds in Wait, Follow and Arcade, the sound per score after a restart, the warning and a real howl: T060.
- **Linux and Windows:** the real-time code is compiled by CI (Ubuntu run 41508924416 green; Windows only at a Release), but no package was run. The delay, clicks and a howl are asked of testers in the release notes (sheet F12, rows X1 to X4).
- **The feedback guard's margin:** the recorded guitars rise at most 11.9 dB in 300 ms, against a 15 dB rule; a real howl rising slower than that is caught only once it stays above −12 dBFS for 300 ms.
- **The window's screenshot** for the user guide (T055) waits for the built application at the owner's session.
