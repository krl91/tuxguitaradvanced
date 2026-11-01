# Implementation Plan: Hear my guitar through an amp and effect pedals while I practise

**Branch**: `feature/interactive-practice` (spec directory `022-guitar-amp-effects`) | **Date**: 2026-11-01 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/022-guitar-amp-effects/spec.md`

## Summary

Today the audio engine only listens. `CaptureService` reads the practice input in blocks of 1024 frames (21 ms) into `SampleRingBuffer` for the analysis, and nothing is ever played back. The plan adds a **monitor path** next to the analysis, in five parts:

1. **Measure first (research R1).**
   - A probe measured the round trip that Java Sound reaches with small blocks on the owner's Mac: no setting within 30 ms (T003, R1 "Measured").
   - So the native duplex device is required (R1 addendum, owner's decisions of 2026-11-01): one Rust crate with `cpal` for the three systems, behind `MonitorDevice`.
2. **The sound's own input (R2).**
   - The native device opens the practice input itself, beside the recognition's capture, and the output.
   - `CaptureService` is not touched: the recognition sees exactly what it sees today (FR-003, SC-002), checked on the recorded inputs and on the datasets.
3. **A real-time thread (R3)** reads the native input, runs the chain and writes to the chosen output. It never allocates, locks or logs, the knobs ramp inside each block, and the output stage fades, limits and clamps (FR-006, FR-010).
4. **The effects (R4).** A noise gate, compressor, overdrive, distortion, amp (clean, crunch, high gain, with a 3-band tone stack), cabinet curve, chorus, echo and reverb. All are plain deterministic Java, the project's own, and tested on synthetic signals.
5. **The practice side.**
   - The *Amp and effects* window, opened from the practice window and the palette (R7).
   - The sounds, and the sound per score by title and artist, in `AmpSoundStore` (R6).
   - The feedback guard (R5), the device failures and the overload lightening.
   - Tooltips in EN/FR/ES and the guides.

Details: [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/).

## Technical Context

**Language/Version**: Java release 11 (`TuxGuitar-audio-engine`, `TuxGuitar-practice`), JDK 21 locally. Rust (stable toolchain, 1.93 on the owner's Mac) for the native device only (R1 addendum).

**Primary Dependencies**:
- the native device: `cpal` 0.15.3 (CoreAudio, ALSA, WASAPI) and `jni` 0.21, licences Apache-2.0/MIT, in the crate `tuxguitar-monitor-native` (R1 addendum);
- the existing `RealFft` (feedback guard), `TGThreadManager`, `TGConfigManager` and `MidiPlayer.setVolume`;
- the UI toolkit (`UIWindow`, scales, drop-downs, buttons, tooltips).

No new Java library.

**Storage**: `TGConfigManager` module `tuxguitar-practice-amp` (data-model `AmpSoundSettings`).

**Testing**:
- JUnit 5:
  - `desktop/TuxGuitar-audio-engine/src/test/java`: effects, chain, engine on a fake device, identity of the recognition;
  - `desktop/TuxGuitar-practice/test`: store, score key, window logic.
- `-P swt-gui-tests` for the window.
- `@Tag("benchmark")` for the delay probes (Java Sound, then the native device), the cost of the chain, the endurance run and the identity of the recognition on the datasets (`~/tuxguitar-datasets`) (excluded by default).
- `cargo test` for the native rings, run by the native module's build.
- The owner's gate with the guitar.

**Target Platform**: macOS Apple Silicon validated by the owner; Linux and Windows built by CI and checked by testers (FR-015, memory `all-os-feature-parity`).

**Project Type**: desktop application, plugin modules.

**Performance Goals**:
- delay from string to ear: target 20 ms, shipping ceiling 30 ms (SC-001) with an instrument input; the built-in microphone and speakers alone declare about 49 ms, which no interface removes (R1 "Measured");
- a change heard within 0.1 s (SC-003);
- the full chain in under 25 % of a block's time on the owner's Mac (R4);
- no dropout over an hour (SC-006).

**Constraints**:
- the recognition, the tuner and the calibration unchanged (FR-003);
- no existing test modified (owner's rule);
- the capture path unchanged (`CaptureService` not touched);
- off by default;
- no copied material (FR-018).

**Scale/Scope**: one input, one stereo output, chains of up to 9 elements (7 pedal kinds, the amp and the cabinet), 4 ready-made sounds plus the learner's own.

## Constitution Check

| Principle | Status |
|---|---|
| I. Preserve compatibility | **Pass.** Off by default; the capture is not touched. The score's playback is untouched except its master volume, which the learner moves on purpose (the same value as TuxGuitar's volume control). No file format change. |
| II. Extend before modifying core | **Pass.** All code goes in the fork's modules `TuxGuitar-audio-engine` and `TuxGuitar-practice`. No core change; `MidiPlayer.setVolume` is an existing public API. |
| III. Evidence-based | **Pass.** Every claim cites inspected classes (research "What exists today"). The delay is measured before the device choice (R1). |
| IV. Spec-driven | **Pass.** The spec is clarified, with six owner answers. |
| V. Incremental delivery | **Pass.** Phases: probe → monitor with the amp (US1) → pedals (US2) → sounds and per score (US3) → safety (US4) → polish. Each one builds and is testable. |
| VI. Real-time audio | **Pass by design, verified by tests and the probe.** No allocation, lock or I/O in the loop; a non-blocking queue; the delay stated (SC-001) and measured; device failures degrade with a message (FR-013). |
| VII. Separable audio logic | **Pass.** The effects, chain and engine are pure Java, exercised with synthetic and recorded input and a fake device. Only `NativeMonitorDevice` (and its Rust crate) touches the hardware. |
| VIII. Deterministic DSP | **Pass.** Classic waveshaping, biquads, delay lines and a Schroeder–Moorer reverb. No ML, no impulse responses of commercial gear. |
| IX. Cross-platform | **Pass.** One Rust crate with `cpal` for the three systems, isolated behind `MonitorDevice`, built by the three build scripts and the CI. Linux and Windows reported as untested until testers answer. |
| X. No unrelated refactoring | **Pass.** `CaptureService` is not touched; nothing else moves. |
| XI. Build and test integrity | **Pass.** Full gate each phase; only new tests. |
| XII. Truthful reporting | **Pass.** The delay is shown to the learner as an estimate; the end-to-end delay is measured by the probe, whose report report is committed; the owner judges the feel (SC-001). |
| XIII. Honest guitar-audio semantics | **Pass.** The sound is a rendering of the mono input; it claims nothing about what was played and never feeds the judging. |
| XIV. Learner-centred feedback | **Pass.** The messages say what happened and what to do (headphones, a lost device, too much processing). |

Post-design re-check: unchanged. R1's native branch was taken (T003); it is tracked in Complexity Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/022-guitar-amp-effects/
├── spec.md, plan.md, research.md, data-model.md, quickstart.md
├── contracts/monitor-engine.md, contracts/amp-window.md
├── benchmark/latency-probe.md      (Phase 1 report)
├── checklists/requirements.md
└── tasks.md                         (/speckit-tasks)
```

### Source Code

```text
desktop/TuxGuitar-audio-engine/src/main/java/app/tuxguitar/audio/engine/
└── monitor/                                (new; input/CaptureService.java is not touched)
    ├── Effect.java, EffectKind.java, Knob.java
    ├── PedalSettings.java, AmpSettings.java, ChainSettings.java, ReadyMadeChains.java (the four ready-made chains)
    ├── fx/NoiseGate, Compressor, Overdrive, Distortion, Amp, Cabinet, Chorus, Echo, Reverb, OutputStage, Oversampler, Biquad
    ├── Chain.java                          (runs the elements, lightening on overload)
    ├── BlockQueue.java                     (single producer, single consumer)
    ├── FeedbackGuard.java
    ├── MonitorEngine.java, MonitorState.java, MonitorStats.java
    └── MonitorDevice.java (duplex), NativeMonitorDevice.java (JNI)
desktop/build-scripts/native-modules/tuxguitar-monitor-native/   (new: Cargo.toml, Cargo.lock, src/lib.rs, pom.xml; listed in the native-modules profile of the three SWT build scripts)
desktop/TuxGuitar-audio-engine/src/test/java/.../monitor/   (new tests; probes, cost and 10-minute endurance tagged benchmark)
desktop/TuxGuitar-practice/test/.../dataset/                 (new: the datasets' identity, sound off and on, tagged benchmark)

desktop/TuxGuitar-practice/src/app/tuxguitar/app/tools/custom/practice/
├── session/AmpSoundStore.java, Sound.java, ScoreKey.java, AmpSoundSettings.java   (new)
├── ui/AmpWindow.java, AmpChainRows.java, AmpTexts.java (the text keys)          (new)
├── ui/PracticeWindow.java       (button, start and stop with the window, the sound per score when the practice window opens on a score)
└── ui/PracticePalette.java      (amp button)
desktop/TuxGuitar-practice/test/...                                               (new tests)
desktop/TuxGuitar-practice/share/lang/messages*.properties   (EN/FR/ES labels and tooltips, beside the practice texts)
docs/PRACTICE*.md, specs/test-sheets/F12-ampli-effets.md
```

**Structure Decision**:
- The DSP and the device live in the audio engine, next to the capture they extend: pure, testable, no UI.
- The window and the settings live in the practice plugin, as every practice window does.
- The engine stays independent of the UI toolkit (constitution VII).

## Phases (for `/speckit-tasks`)

1. **Baseline and probe (R1).**
   - Record the counts.
   - Write `MonitorLatencyProbeTest` (benchmark) and the report.
   - **Decision gate:** taken on 2026-11-01: the native branch (R1 "Measured" and addendum). The owner's listening with the cable comes after US1, without blocking the next phases.
2. **Foundations** (including the output stage with the safe ceiling, SC-004):
   - `Effect`, `Knob`, `PedalSettings`, `AmpSettings`, `ChainSettings` (text, validation);
   - `BlockQueue`, `OutputStage`, `MonitorEngine` on a fake duplex device;
   - the identity test of the recognition (SC-002) with the sound running beside it.
3. **US1, the amp** (P1):
   - `Biquad`, `Oversampler`, `Amp`, `Cabinet`, `NoiseGate`;
   - the native device (Phase 7, done before the device task of US1);
   - the settings part of `AmpSoundStore` (switch, output, guitar volume, current chain, window bounds), so that US1 and US2 survive a restart;
   - the minimal *Amp and effects* window (switch, output, volumes, delay, amp row);
   - started and stopped with the practice window; the score volume through `MidiPlayer`.
4. **US2, the pedals** (P1): `Compressor`, `Overdrive`, `Distortion`, `Chorus`, `Echo`, `Reverb`; rows, add, remove, reorder; smooth changes (SC-003); the cost benchmark.
5. **US3, the sounds** (P2): the sounds part of `AmpSoundStore`, ready-made sounds, save, rename, delete, restore, `ScoreKey`, the sound per score when the practice window opens on a score (FR-019).
6. **US4, safety** (P2): off by default, the microphone warning, `FeedbackGuard` (SC-005), device loss (FR-013), overload lightening (FR-014). The safe ceiling (SC-004) is already in the output stage from the foundations.
7. **The native duplex device** (required by Phase 1; done right after Phase 2): the Rust crate with `cpal`, `NativeMonitorDevice`, the three build scripts and the workflows, its own probe run.
8. **Closing:**
   - the three guides (the EN/FR/ES labels and tooltips are added with each phase);
   - sheet F12 (with Linux and Windows rows);
   - full gate and SWT run, and the datasets' identity (sound off and on);
   - **[HUMAN] gate:** the owner plays through the four sounds in Wait, Follow and Arcade (SC-001, SC-006);
   - release notes with the testers' call for the delay.

## Complexity Tracking

| Item | Why needed | Simpler alternative rejected because |
|---|---|---|
| Native duplex device in Rust with `cpal` (R1 addendum), a new toolchain in the builds and the CI | The probe showed Java Sound at 86 ms or more on the owner's Mac (37 ms at best), against the owner's 30 ms ceiling; `cpal` adds almost nothing to the devices' own delay. | Shipping Java Sound contradicts the owner's ceiling. Three C modules (one per system) were not chosen by the owner. PortAudio adds a third-party native library to install on three systems. |
| Oversampling in the clipping stages | Without it, the distortion and high-gain amp alias audibly (harsh, inharmonic). | Plain clipping at 48 kHz was rejected for sound quality; the cost is bounded by R4's budget and the overload lightening. |
