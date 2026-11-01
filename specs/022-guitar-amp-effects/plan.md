# Implementation Plan: Hear my guitar through an amp and effect pedals while I practise

**Branch**: `feature/interactive-practice` (spec directory `022-guitar-amp-effects`) | **Date**: 2026-11-01 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/022-guitar-amp-effects/spec.md`

## Summary

Today the audio engine only listens. `CaptureService` reads the practice input in blocks of 1024 frames (21 ms) into `SampleRingBuffer` for the analysis, and nothing is ever played back. The plan adds a **monitor path** next to the analysis, in five parts:

1. **Measure first (research R1).**
   - A probe measures the round trip that Java Sound reaches with small blocks on the owner's Mac.
   - If it is within 30 ms, the whole feature uses Java Sound on the three systems.
   - If not, a conditional phase adds a small native duplex device per system behind `MonitorDevice`.
2. **One capture, two consumers (R2).**
   - With the sound off, the capture is exactly as today.
   - With it on, the capture reads small blocks. It writes the same samples to the ring buffer and offers a copy to the `MonitorEngine` through a non-blocking queue. The recognition sees the same samples (FR-003, SC-002).
3. **A real-time output thread (R3)** runs the chain and writes to the chosen output. It never allocates, locks or logs, the knobs ramp inside each block, and the output stage fades, limits and clamps (FR-006, FR-010).
4. **The effects (R4).** A noise gate, compressor, overdrive, distortion, amp (clean, crunch, high gain, with a 3-band tone stack), cabinet curve, chorus, echo and reverb. All are plain deterministic Java, the project's own, and tested on synthetic signals.
5. **The practice side.**
   - The *Amp and effects* window, opened from the practice window and the palette (R7).
   - The sounds, and the sound per score by title and artist, in `AmpSoundStore` (R6).
   - The feedback guard (R5), the device failures and the overload lightening.
   - Tooltips in EN/FR/ES and the guides.

Details: [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/).

## Technical Context

**Language/Version**: Java release 11 (`TuxGuitar-audio-engine`, `TuxGuitar-practice`), JDK 21 locally. C only in the conditional native branch of R1.

**Primary Dependencies**:
- Java Sound (`javax.sound.sampled`: `TargetDataLine` already used, `SourceDataLine` new);
- the existing `RealFft` (feedback guard), `TGThreadManager`, `TGConfigManager` and `MidiPlayer.setVolume`;
- the UI toolkit (`UIWindow`, scales, drop-downs, buttons, tooltips).

No new library.

**Storage**: `TGConfigManager` module `tuxguitar-practice-amp` (data-model `AmpSoundSettings`).

**Testing**:
- JUnit 5:
  - `desktop/TuxGuitar-audio-engine/src/test/java`: effects, chain, engine on a fake device, identity of the recognition;
  - `desktop/TuxGuitar-practice/test`: store, score key, window logic.
- `-P swt-gui-tests` for the window.
- `@Tag("benchmark")` for the delay probe and the cost of the chain (excluded by default).
- The owner's gate with the guitar.

**Target Platform**: macOS Apple Silicon validated by the owner; Linux and Windows built by CI and checked by testers (FR-015, memory `all-os-feature-parity`).

**Project Type**: desktop application, plugin modules.

**Performance Goals**:
- delay from string to ear: target 20 ms, shipping ceiling 30 ms (SC-001), measured by the probe;
- a change heard within 0.1 s (SC-003);
- the full chain in under 25 % of a block's time on the owner's Mac (R4);
- no dropout over an hour (SC-006).

**Constraints**:
- the recognition, the tuner and the calibration unchanged (FR-003);
- no existing test modified (owner's rule);
- the capture path unchanged when the sound is off;
- off by default;
- no copied material (FR-018).

**Scale/Scope**: one input, one stereo output, chains of up to 9 elements (7 pedal kinds, the amp and the cabinet), 4 ready-made sounds plus the learner's own.

## Constitution Check

| Principle | Status |
|---|---|
| I. Preserve compatibility | **Pass.** Off by default; with it off, the capture runs the unchanged path. The score's playback is untouched except its master volume, which the learner moves on purpose (the same value as TuxGuitar's volume control). No file format change. |
| II. Extend before modifying core | **Pass.** All code goes in the fork's modules `TuxGuitar-audio-engine` and `TuxGuitar-practice`. No core change; `MidiPlayer.setVolume` is an existing public API. |
| III. Evidence-based | **Pass.** Every claim cites inspected classes (research "What exists today"). The delay is measured before the device choice (R1). |
| IV. Spec-driven | **Pass.** The spec is clarified, with six owner answers. |
| V. Incremental delivery | **Pass.** Phases: probe → monitor with the amp (US1) → pedals (US2) → sounds and per score (US3) → safety (US4) → polish. Each one builds and is testable. |
| VI. Real-time audio | **Pass by design, verified by tests and the probe.** No allocation, lock or I/O in the loop; a non-blocking queue; the delay stated (SC-001) and measured; device failures degrade with a message (FR-013). |
| VII. Separable audio logic | **Pass.** The effects, chain and engine are pure Java, exercised with synthetic and recorded input and a fake device. Only `JavaSoundMonitorDevice` touches the hardware. |
| VIII. Deterministic DSP | **Pass.** Classic waveshaping, biquads, delay lines and a Schroeder–Moorer reverb. No ML, no impulse responses of commercial gear. |
| IX. Cross-platform | **Pass.** Java Sound on all three; any native fallback isolated behind `MonitorDevice`, one module per system. Linux and Windows reported as untested until testers answer. |
| X. No unrelated refactoring | **Pass.** `CaptureService` gets one additive setter; nothing else moves. |
| XI. Build and test integrity | **Pass.** Full gate each phase; only new tests. |
| XII. Truthful reporting | **Pass.** The delay is shown as estimated or measured; the probe report is committed; the owner judges the feel (SC-001). |
| XIII. Honest guitar-audio semantics | **Pass.** The sound is a rendering of the mono input; it claims nothing about what was played and never feeds the judging. |
| XIV. Learner-centred feedback | **Pass.** The messages say what happened and what to do (headphones, a lost device, too much processing). |

Post-design re-check: unchanged. The only conditional risk is R1's native branch, tracked in Complexity Tracking.

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
├── input/CaptureService.java               (+ setMonitor, additive)
└── monitor/                                (new)
    ├── Effect.java, EffectKind.java, Knob.java
    ├── PedalSettings.java, AmpSettings.java, ChainSettings.java
    ├── fx/NoiseGate, Compressor, Overdrive, Distortion, Amp, Cabinet, Chorus, Echo, Reverb, OutputStage, Oversampler, Biquad
    ├── Chain.java                          (runs the elements, lightening on overload)
    ├── BlockQueue.java                     (single producer, single consumer)
    ├── FeedbackGuard.java
    ├── MonitorEngine.java, MonitorState.java, MonitorStats.java
    └── MonitorDevice.java, JavaSoundMonitorDevice.java
desktop/TuxGuitar-audio-engine/src/test/java/.../monitor/   (new tests; probe and cost tagged benchmark)

desktop/TuxGuitar-practice/src/app/tuxguitar/app/tools/custom/practice/
├── session/AmpSoundStore.java, Sound.java, ScoreKey.java, AmpSoundSettings.java   (new)
├── ui/AmpWindow.java, AmpChainRows.java                                          (new)
├── ui/PracticeWindow.java       (button, start and stop with the window, the sound per score at run start)
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
   - **Decision gate:** Java Sound, or the native branch. The owner listens by ear with the cable.
2. **Foundations:**
   - `Effect`, `Knob`, `PedalSettings`, `AmpSettings`, `ChainSettings` (text, validation);
   - `BlockQueue`, `OutputStage`, `MonitorEngine` on a fake device;
   - `CaptureService.setMonitor`;
   - the identity test of the recognition (SC-002), seen red first with a deliberate mutation.
3. **US1, the amp** (P1):
   - `Biquad`, `Oversampler`, `Amp`, `Cabinet`, `NoiseGate`;
   - `JavaSoundMonitorDevice`;
   - the minimal *Amp and effects* window (switch, output, volumes, delay, amp row);
   - started and stopped with the practice window; the score volume through `MidiPlayer`.
4. **US2, the pedals** (P1): `Compressor`, `Overdrive`, `Distortion`, `Chorus`, `Echo`, `Reverb`; rows, add, remove, reorder; smooth changes (SC-003); the cost benchmark.
5. **US3, the sounds** (P2): `AmpSoundStore`, ready-made sounds, save, rename, delete, restore, `ScoreKey`, the sound per score at run start.
6. **US4, safety** (P2): off by default, the microphone warning, `FeedbackGuard` (SC-005), device loss (FR-013), overload lightening (FR-014), the safe ceiling (SC-004).
7. **Conditional — the native duplex device**, only if Phase 1 decided it (one module per system, built by CI).
8. **Closing:**
   - EN/FR/ES texts and the three guides;
   - sheet F12 (with Linux and Windows rows);
   - full gate and SWT run;
   - **[HUMAN] gate:** the owner plays through the four sounds in Wait, Follow and Arcade (SC-001, SC-006);
   - release notes with the testers' call for the delay.

## Complexity Tracking

| Item | Why needed | Simpler alternative rejected because |
|---|---|---|
| Conditional native duplex device per system (R1), only if the probe shows Java Sound above 30 ms | The owner's ceiling is 30 ms; Java Sound's buffering on some systems may not reach it. | Shipping above 30 ms contradicts the owner's answer. A single cross-platform native library (PortAudio) adds a third-party native dependency on three systems. |
| Oversampling in the clipping stages | Without it, the distortion and high-gain amp alias audibly (harsh, inharmonic). | Plain clipping at 48 kHz was rejected for sound quality; the cost is bounded by R4's budget and the overload lightening. |
