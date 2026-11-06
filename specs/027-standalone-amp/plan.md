# Implementation Plan: GuitarFX, a standalone amp and effect pedals

**Branch**: `feature/interactive-practice` (no branch of its own) | **Date**: 2026-11-06 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/027-standalone-amp/spec.md`

## Summary

GuitarFX turns a computer into a guitar amp and effect pedals without TuxGuitarAdvanced or Java. It comes as an application and as a plugin (VST3, CLAP, and Audio Unit on macOS).

The spec 022 effects (`desktop/TuxGuitar-audio-engine/.../monitor/fx`) are ported line for line to Rust, in `f64` like the Java, and run inside the audio callback. A Java reference test proves the two give the same output within −90 dBFS.

The application reuses the device pieces of TuxGuitarAdvanced's crate:
- `Ring`, `Mailbox`, `realtime`, `workgroup` and `latency`, through its `rlib`;
- the only change to the crate is making `latency` public.

The plugin is a `nih-plug` CLAP, turned into VST3 and AUv2 by `clap-wrapper`, so no GPL code is linked.

Sounds use the existing chain text. They are exchanged with TuxGuitarAdvanced as `.guitarfx` files; *Export…* and *Import…* are added to its *Amp and effects* window.

Every internal value is an external parameter, shown in an *Advanced* window.

One installer per system is published with the TuxGuitarAdvanced release.

## Technical Context

**Language/Version**:
- Rust stable: 1.93 on the owner's Mac; the same toolchain action as spec 022 in CI.
- Java release 11 for the two small TuxGuitarAdvanced additions (the reference test and `SoundFile`) and the practice plugin's two menu items. JDK 21 locally.

**Primary Dependencies** (licences checked by `cargo deny` in CI, research R3):

| Dependency | Role | Licence |
|---|---|---|
| `cpal` 0.15.3 | the devices | Apache-2.0 |
| the path dependency `tuxguitar-monitor-native` | the device pieces | LGPL-2.1-or-later, the project's own |
| `nih-plug` | the plugin, CLAP export only, git pinned | ISC |
| `nih_plug_egui` | the plugin's editor | ISC |
| `clap-wrapper` | VST3 and AUv2 | MIT/Apache-2.0; embeds the VST3 SDK (MIT) and the AudioUnitSDK (Apache-2.0) |
| `eframe`/`egui` | the windows | MIT/Apache-2.0 |
| `directories` | the settings folder | MIT/Apache-2.0 |
| `sys-locale` | the system's language | MIT/Apache-2.0 |

No new Java library.

**Storage**: two UTF-8 properties files in the user's settings folder (R6), and `.guitarfx` sound files.

**Testing**:
- `cargo test` covers the effects against the Java references, the chain text, the files, the parameter registry, the texts, no allocation in the callback, and the safety (ceiling, fade-in, feedback guard, silence).
- JUnit 5 covers the new `GuitarFxReferenceTest` (it writes the references) and `SoundFileTest`.
- CI also runs `auval` on macOS and a headless start of the application on the three systems.
- The probe of R8 (audible) and the one-hour endurance are run by hand, on the owner's request.

**Target Platform**:
- macOS 15+ on Apple Silicon: validated on the owner's Mac.
- Linux x86_64 and Windows x86_64: built and checked in CI; reported by testers or stated as unchecked (FR-021).

**Project Type**: a desktop application and an audio plugin, in a Cargo workspace beside TuxGuitarAdvanced.

**Performance Goals**:
- A delay no longer than TuxGuitarAdvanced's with the same devices and probe (SC-002; 32.5 ms measured in spec 022 with the USB microphone at 128 frames). Shorter is the aim.
- No dropped block in one hour with six active elements (SC-006).

**Constraints**:
- No allocation, lock or wait in the audio callback or in the plugin's `process` (FR-015).
- No GPL code (FR-020).
- No value that changes the sound fixed in the code (FR-016).
- The windows fit 1024×768.

**Scale/Scope**:
- 9 elements: 7 pedals, the amp and the cabinet.
- 4 ready-made sounds.
- About 2,000 lines of Java DSP ported.
- An estimated 60 to 80 external parameters; the exact count comes from the inventory task.
- About 300 texts × 3 languages.
- 3 plugin formats and 3 installers.

## Constitution Check

*GATE: checked before Phase 0, re-checked after Phase 1.*

| Principle | Status |
|---|---|
| I. Preserve TuxGuitar compatibility | **Pass.** TuxGuitarAdvanced keeps its effects and its sound (FR-022). It gains two menu items and a file format it reads and writes. Its packages do not change: GuitarFX is never copied into them. |
| II. Extend before modifying core | **Pass.** No core module is touched. The additions are in the audio engine (`SoundFile`, the reference test) and in the practice plugin (`AmpWindow`'s ⋯ menu). The one change to the native crate is `mod latency` → `pub mod latency` in `lib.rs`. **Justification:** GuitarFX needs the devices' declared latencies for FR-017; the function exists (`latency.rs` line 6) and is private. Copying it would make a second copy of platform code. |
| III. Evidence-based architecture | **Pass.** Every reuse cites the inspected file (research R1). The web facts on licences are dated and re-checked by the licence gate (R3). |
| IV. Spec-driven | **Pass.** spec → clarify (5 answers, 2026-11-06) → this plan → tasks. |
| V. Incremental delivery | **Pass.** The phases below each build and test on their own. The application (US1–US4) comes before the plugin (US5). |
| VI. Real-time audio | **Pass.** Everything is preallocated when the sound is turned on, changes go through `Mailbox`, and a counting allocator checks it (R10). The delay is stated in SC-002. A lost device, a denied permission or a bad format stop the sound with a message (FR-013). |
| VII. Separable, testable audio logic | **Pass.** `guitarfx-dsp` has no I/O and no UI. It is tested on synthetic and recorded signals and against the Java references. |
| VIII. Deterministic DSP | **Pass.** It is a port of spec 022's waveshaping, biquads, delay lines and Schroeder–Moorer reverb. No ML and no impulse responses. |
| IX. Cross-platform | **Pass.** One code base for three systems. Audio Unit exists only on macOS: that is the format's limit, not a missing feature (FR-024). Platform code stays inside cpal, `realtime`, `latency` and the installers. Linux and Windows are reported as checked by CI only, until testers answer. |
| X. No unrelated refactoring | **Pass.** The crate's shared parts are used where they are. Moving them into a common crate is left to the later spec that unifies the effects. |
| XI. Build and test integrity | **Pass.** Every phase ends with the full macOS build (`clean verify -P native-modules`) green. No committed test is changed. |
| XII. Truthful reporting | **Pass.** The delay is reported as measured, and Linux and Windows as unchecked until tested. |
| XIII. Honest guitar-audio semantics | **Not applicable**: GuitarFX judges nothing. |
| XIV. Learner-centred feedback | **Pass.** Every message says what happened and what to do: feedback, a lost device, permission, overload. |
| Licences (Technical constraints) | **Pass.** Every dependency is MIT, Apache-2.0, ISC, BSD, Zlib or the project's LGPL crate. `nih_export_vst3!` (GPLv3) is never used. `cargo deny` refuses GPL in CI. |
| Translation files | **Deviation, justified.** GuitarFX is not a TuxGuitarAdvanced module, so its texts are in its own `guitarfx/lang/` files in TuxGuitarAdvanced's format, English first. The two new TuxGuitarAdvanced menu items go in the practice plugin's `share/lang/`, as the rule asks. |

Re-check after Phase 1 (the data model and the contracts): no new violation.

## Project Structure

### Documentation (this feature)

```text
specs/027-standalone-amp/
├── spec.md, plan.md, research.md, data-model.md, quickstart.md
├── contracts/
│   ├── sound-file.md        (the .guitarfx file, shared with TuxGuitarAdvanced)
│   ├── settings-files.md    (settings.properties, sounds.properties, the parameter keys)
│   ├── application.md       (the main window, the Advanced window, the messages)
│   └── plugin.md            (formats, identifiers, state, the host's rate and block)
├── inventory.md             (first implementation task: every constant, a parameter or fixed)
├── benchmark/               (latency.md, endurance.md, comparison.md)
└── tasks.md                 (/speckit-tasks)
```

### Source Code (repository root)

```text
guitarfx/                                   (new Cargo workspace; built by the native-modules profile, never packaged in TuxGuitarAdvanced)
├── Cargo.toml, Cargo.lock, deny.toml, pom.xml, README.md
├── lang/messages.properties, messages_fr.properties, messages_es.properties
├── guitarfx-dsp/src/        biquad, oversampler, gate, compressor, overdrive, distortion, amp, cabinet, chorus, echo, reverb,
│                            output_stage, feedback_guard, chain, chain_text, ready_made, params (the parameter registry)
├── guitarfx-dsp/tests/      java_reference.rs (SC-003), silence, ceiling, smooth_changes, no_allocation, feedback, inventory_scan
├── guitarfx-settings/src/   folder, properties, settings, sounds, sound_file, texts
├── guitarfx-ui/src/         chain_rows, sounds, knobs, advanced, messages
├── guitarfx-app/src/        main, engine (cpal callbacks, mono resampler, mailbox), devices, delay_estimate
├── guitarfx-plugin/src/     lib (nih-plug Plugin, CLAP export, export_vst3!, export_auv2!), editor, state
├── guitarfx-probe/src/      main (the latency probe, R8; not packaged)
└── packaging/               macos (Info.plist, pkg scripts, uninstall), windows (guitarfx.nsi), linux (debian/, install.sh, guitarfx.desktop)

desktop/build-scripts/native-modules/tuxguitar-monitor-native/src/lib.rs   (`mod latency` → `pub mod latency`, the only change)
desktop/TuxGuitar-audio-engine/src/main/java/app/tuxguitar/audio/engine/monitor/SoundFile.java      (new: read and write .guitarfx)
desktop/TuxGuitar-audio-engine/src/test/java/app/tuxguitar/audio/engine/monitor/SoundFileTest.java  (new)
desktop/TuxGuitar-audio-engine/src/test/java/app/tuxguitar/audio/engine/monitor/GuitarFxReferenceTest.java (new: writes target/guitarfx-reference/)
desktop/TuxGuitar-practice/src/app/tuxguitar/app/tools/custom/practice/ui/AmpWindow.java  (⋯ menu: Export…, Import…)
desktop/TuxGuitar-practice/share/lang/messages{,_fr,_es}.properties                       (the two items and their messages)
desktop/build-scripts/tuxguitar-{macosx-swt-cocoa,linux-swt,windows-swt-x86_64}/pom.xml   (native-modules profile: the guitarfx module)
.github/workflows/guitarfx.yml (new), .github/workflows/release.yml (three installer jobs, the draft check)
docs/guitarfx/guide{,_fr,_es}.md, docs/guitarfx/parameters.md
```

**Structure Decision**:
- GuitarFX is a product of its own, so it gets a top-level workspace. TuxGuitarAdvanced's tree changes only where the spec asks: the sound file, its two menu items and the reference test. Its build only runs GuitarFX's tests.
- The DSP crate has no dependency, so the application, the plugin and a later unification spec can all use it.

### Phases (each one ends with the full macOS build green, then a commit)

1. **Inventory and references**:
   - `inventory.md` from the Java effects (R7);
   - `GuitarFxReferenceTest` writes the references;
   - the empty workspace builds under Maven;
   - the licence gate.
2. **The DSP** (US2 core): the elements, then the chain, ported in the order of spec 022's dependencies (Biquad, Oversampler, the pedals, the amp and cabinet, the output stage, the feedback guard, the chain). Each one is red against the Java reference before it is written. Then the chain text and the ready-made sounds, and the parameter registry with the inventory scan.
3. **The application's sound** (US1, US4):
   - devices, engine, mailbox, delay estimate, messages, settings files;
   - the main window;
   - no-allocation and safety tests.
4. **Sounds and files** (US3):
   - the sound list, restore, save, rename, delete;
   - `.guitarfx` export and import in GuitarFX;
   - `SoundFile` and the two menu items in TuxGuitarAdvanced;
   - the round-trip test SC-011.
5. **The *Advanced* window and the texts** (US4, FR-018, FR-025): the categories from the registry, *Defaults*, the three languages and their tests.
6. **The plugin** (US5):
   - the CLAP plugin, its editor and its state;
   - VST3 and AUv2 through `clap-wrapper`;
   - `auval`;
   - the Windows build check of R3.
7. **Installers and CI**: `guitarfx.yml`, the three installers, the release jobs, the uninstall.
8. **Documentation and measures**:
   - the guides in three languages and `parameters.md`;
   - the probe and the endurance run (owner, audible; warned first);
   - the [HUMAN] checks: the owner's play test, SoundCraft and GarageBand or Logic, Linux and Windows testers.

## Complexity Tracking

| Item | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| A C++ wrapper compiled into the plugin (`clap-wrapper`, through `cc`) | VST3 and AUv2 without GPL bindings (Q1 C) | `nih_export_vst3!` binds GPLv3; hand-written wrappers are far larger |
| A possible `windows-2022` runner for GuitarFX only | the VST3 wrapper's C++ may not build with MinGW (R3) | the MinGW cross-build is kept if it works; the runner is used only if it fails |
| Two copies of the effects (Java and Rust) until a later spec | the owner's Q3 A | the comparison of SC-003 runs in every build and catches any drift |
