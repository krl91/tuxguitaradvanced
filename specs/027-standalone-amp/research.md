# Research: GuitarFX (spec 027)

Written on 2026-11-06 for `/speckit-plan`. Each decision cites the source inspected (constitution III). Web facts were checked on 2026-11-06 and are re-checked by the tasks that depend on them (the licence check of R3 runs in CI).

## R1. What exists today and what GuitarFX reuses

**Inspected**:
- `desktop/TuxGuitar-audio-engine/src/main/java/app/tuxguitar/audio/engine/monitor/` holds the spec 022 effects, 3,178 lines in all:
  - in `fx/`: `NoiseGate`, `Compressor`, `Overdrive`, `Distortion`, `Amp`, `Cabinet`, `Chorus`, `Echo`, `Reverb`, `OutputStage`, `Oversampler`, `Biquad`, `Pedals`;
  - beside them: `Chain` (crossfades, lightening on overload), `FeedbackGuard`, `MonitorEngine` (block of 128 frames, clipping, notices), `EffectKind` and `Knob` (knob names, ranges, units, defaults), `ChainSettings` (the chain's compact text), `ReadyMadeChains` (the four sounds).
  - The arithmetic is in `double`; for example `Biquad.java` line 21 holds `b0…a2` and `x1…y2` as `double`. The `fx/` and engine classes use `Math.tanh/exp/sin/cos/pow/log` about 40 times.
- The crate `desktop/build-scripts/native-modules/tuxguitar-monitor-native` (cpal 0.15.3, LGPL-2.1-or-later). Its public parts:
  - `ring::Ring` (lock-free, nothing allocated after `new`);
  - `resampler::Resampler` (cubic Hermite over stereo frames, ratio nudged by the ring's fill);
  - `realtime::promote_current_thread`;
  - `workgroup`;
  - `recognition::Mailbox<T>` (`AtomicPtr` hand-over, `post` off the real-time thread);
  - `device::Duplex`.
  - `latency::declared` is private (`mod latency` in `lib.rs`).
- `Duplex` is a pull design: the input callback fills a ring, **Java** reads blocks, processes them and writes stereo blocks into the output ring, and the output callback plays them through the resampler (`device.rs`, module comment).

**Decision**:
- GuitarFX processes in the **output callback**. The input callback pushes the chosen channel into a `Ring`; the output callback pops a block, runs the chain, and writes the frames. This removes the round trip through another thread and its ring, which is where the shorter delay of SC-002 can come from.
- When the input and output rates differ, the input is resampled to the output's rate with the same Hermite method, in a mono variant written in GuitarFX. The existing `Resampler` is stereo and output-side.
- GuitarFX depends on the crate as a path dependency, an `rlib` it already declares (`crate-type = ["cdylib", "rlib"]`), for `Ring`, `realtime`, `workgroup`, `Mailbox` and `latency`.
- The only change to the crate is `mod latency` → `pub mod latency` (justified in the plan). Every other part is used as is.

**Alternatives rejected**:
- Reuse `Duplex` as is: it keeps the extra thread and ring that GuitarFX exists to remove.
- Copy the device code: two copies of the device would drift.
- Move the shared code into a new crate: that restructures TuxGuitarAdvanced's crate, which is unrelated refactoring (constitution X); it is left to the later spec that unifies the effects.

## R2. Porting the effects so the output stays within −90 dBFS of Java's (SC-003)

**Decision**:
- Port every class of `monitor/fx` and the parts of `Chain`, `FeedbackGuard` and `MonitorEngine` that shape the sound, line for line, keeping:
  - `f64` wherever Java uses `double`, `f32` wherever it uses `float`;
  - the same order of operations;
  - the same smoothing and gliding per block of 128 frames.
- Rust's `f64::tanh/exp/sin…` call the platform's libm, and Java's `Math` may use intrinsics. They can differ in the last bit, which is far below −90 dBFS (about 3·10⁻⁵ of full scale); the comparison measures it.

**How it is checked**: a new Java test in the audio engine, `monitor/fx/GuitarFxReferenceTest` (in the package of the package-private `Signals`), never changing a committed test.
- It runs each element and each ready-made sound on `Signals.sine` and `Signals.noise`, a silence and a step built in the test, and a guitar recording of `benchmark/MonitorFixtureSet` (the recordings of spec 022's tests), at 44.1, 48 and 96 kHz.
- It writes the inputs and outputs as raw little-endian `f32` files with a small index into `desktop/TuxGuitar-audio-engine/target/guitarfx-reference/`.
- The GuitarFX Maven module then runs `cargo test` with `GUITARFX_REFERENCE_DIR` set. Under Maven a missing directory is a failure; under a plain `cargo test` the comparison is skipped with a message.
- Nothing large is committed.

**Alternatives rejected**:
- Committed golden files: several megabytes, and stale as soon as the Java changes.
- Calling Java from Rust: it brings the JVM into the tool's tests.

## R3. Plugin formats and licences (FR-020, FR-024)

**Facts found on 2026-11-06**:
- The VST3 SDK is under the MIT licence since VST 3.8, announced by Steinberg on 2025-10-29 ([press release](https://ocl-steinberg-live.steinberg.net/_storage/asset/819253/storage/master/Press%20Release%20-%202025-10-29%20-%20VST%203.8%20-%20EN.pdf), [Sonicstate](https://sonicstate.com/news/2025/10/30/vst-3-now-available-under-mit-license)). The ASIO SDK is GPLv3 and is not used.
- CLAP is MIT.
- `nih-plug` ([repo](https://github.com/robbert-vdh/nih-plug)) is ISC. **Its VST3 export binds GPLv3 code** (its README: "the VST3 bindings used by `nih_export_vst3!()` are licensed under the GPLv3 license"). It is in maintenance mode, and a community fork exists.
- `clap-wrapper` (free-audio, MIT) turns a CLAP plugin into VST3 and AUv2. Its Rust crate `clap-wrapper` ([docs.rs](https://docs.rs/crate/clap-wrapper/0.3.1/source/README.md)):
  - is Apache-2.0 or MIT;
  - embeds the VST3 SDK (MIT) and the AUv2 SDK;
  - builds with the `cc` crate, without CMake;
  - adds `export_vst3!()` and `export_auv2!()` over the crate's `clap_entry`;
  - is tested on Ubuntu 22.04, macOS 13.7 and Windows 10.
- Apple's AudioUnitSDK is Apache-2.0. The task re-checks this against the embedded copy.

**Decision**:
- The plugin is written with `nih-plug` and exported **as CLAP only** (`nih_export_clap!`). `nih_export_vst3!` and its feature are never enabled.
- VST3 and Audio Unit (AUv2, macOS) come from `clap-wrapper` over that CLAP entry. No GPL code is linked.
- A licence gate in CI enforces it: `cargo deny check licenses` with an allow list of MIT, Apache-2.0, ISC, BSD-2/3-Clause, Zlib, Unicode-3.0 and LGPL-2.1-or-later (the TuxGuitarAdvanced crate itself). GPL, AGPL and unknown licences are refused.
- **Other non-GPL licences expected in the planned tree** (2026-11-07, `/speckit-analyze`; from the crates' published metadata, not yet checked by `cargo deny list` because the crates are not fetched): MPL-2.0 (`option-ext`, through `directories` → `dirs-sys`), OFL-1.1 and LicenseRef-UFL-1.0 (`epaint_default_fonts`, egui's built-in fonts), BSL-1.0 (`clipboard-win` and `error-code`, egui's clipboard on Windows), CC0-1.0 (possible in small crates). None is GPL, all allow distribution with GuitarFX (FR-020). They are allowed only for the crates that need them (`exceptions` of `deny.toml`), and T029 and T051 confirm the list with `cargo deny list` and record it here.
- **Checked with the interface in** (T029, 2026-11-10, `cargo deny --locked list` with eframe 0.33.3, cpal 0.15.3 and `directories` 6.0.0): besides MIT, Apache-2.0, BSD, ISC, Zlib, Unicode-3.0 and 0BSD (`adler2`, also MIT or Apache-2.0), the tree holds MPL-2.0 (`option-ext`), OFL-1.1 and **Ubuntu-font-1.0** (`epaint_default_fonts`; its metadata names the Ubuntu Font Licence, not `LicenseRef-UFL-1.0` as expected, so the exception was corrected) and BSL-1.0 (`clipboard-win`, `error-code`). No crate is CC0-1.0. `cargo deny check licenses` is green.
- **Checked with the file dialogs in** (T043, 2026-11-10): `rfd` 0.15.4 (MIT) and its tree (on Linux the desktop portal through `ashpd` and `zbus`, no GTK) add no licence outside the list; `cargo deny --locked check licenses` is green. `cargo deny check advisories`, not part of the gate, reports two crates as unmaintained with no fix available (`ttf-parser` through eframe, `zbus_names` through accesskit and the portal); no vulnerability. `sys-locale` 0.3.2 (MIT or Apache-2.0, T046) adds no licence either.
- `nih-plug` is not on crates.io, so it is a git dependency pinned to one commit in `Cargo.lock`. Its maintenance mode is accepted: the pinned commit is all GuitarFX needs. If it blocks a task, the Codeberg fork is a drop-in replacement, recorded as a deviation.

**Alternatives rejected**:
- `nih_export_vst3!`: GPLv3.
- `clack` alone: lower level, no editor glue.
- Writing the VST3 and AU wrappers by hand: large, and `clap-wrapper` is the maintained reference.

**Risk to check in the first plugin task**:
- Windows is cross-compiled with MinGW in the release workflow (`release.yml`, job `build-windows`, `x86_64-pc-windows-gnu`). The VST3 wrapper's C++ may not build with MinGW.
- Fallback: build GuitarFX's Windows package on a `windows-2022` runner (MSVC target). The plan allows this; it changes no TuxGuitarAdvanced job.

## R4. User interface

**Decision**: `egui` (MIT/Apache-2.0).
- **The application**: `eframe` with the `glow` back end.
- **The plugin**: `nih_plug_egui` (ISC + egui).
- **Shared widgets**: the chain rows, the sound list, the knobs as sliders with value and unit, and the *Advanced* categories live in one crate, `guitarfx-ui`, so the application and the plugin show the same controls.
- **Window size**: the application's main window and its *Advanced* window fit 1024×768 (FR-025), as the TuxGuitarAdvanced recognition window does (spec 026).

**Alternatives rejected**:
- iced or vizia: also supported by nih-plug, but egui's immediate mode is the simplest to share between a window of its own and a plugin editor.
- A native toolkit per system: three UIs, against parity.

## R5. Texts in three languages (FR-018)

**Decision**:
- The texts are in `guitarfx/lang/messages{,_fr,_es}.properties`, the same format as TuxGuitarAdvanced's language files, compiled into the binaries with `include_str!`.
- The language comes from the system (`sys-locale`, MIT/Apache-2.0), English otherwise.
- A test checks that the three files have the same keys and that French and Spanish differ from English except for a short list of words that are the same.

**Alternative rejected**: gettext or Fluent, heavier for about 300 strings.

## R6. Settings, sounds and the sound file (FR-009, FR-016, FR-023, FR-025)

**Decision**:
- **Where**: one folder per user, found with the `directories` crate (MIT/Apache-2.0):
  - macOS: `~/Library/Application Support/GuitarFX`;
  - Windows: `%APPDATA%\GuitarFX`;
  - Linux: `~/.config/guitarfx`.
  - It holds `settings.properties` (the devices, the volume, the current sound, every external parameter) and `sounds.properties` (the changed ready-made sounds and the guitarist's own).
  - The plugin reads `settings.properties` read-only at load (FR-025), and saves its own chain with the host's project.
- **The chain is the text of `ChainSettings.toText()`**, for example `gate:on,-60,150|amp:crunch,5,5,5,5,6|cab:on|echo:on,380,3,5,30`. The parser is ported, with the same rules: each kind once, a pedal in its own section, a malformed text gives a ready-made sound.
- **The sound file** (`.guitarfx`): a UTF-8 properties file with `format=1`, `name=…` and `chain=…`. Its grammar is in `contracts/sound-file.md`.
  - TuxGuitarAdvanced reads and writes it with a new small class, `SoundFile`, in the audio engine's `monitor` package, through `ChainSettings.parse` and `toText`.
  - Its *Amp and effects* window gets *Export…* and *Import…* in the ⋯ menu of `AmpWindow` (`desktop/TuxGuitar-practice/.../ui/AmpWindow.java`, the `menu` built at line 300 and opened by `openMenu`, line 334).
  - A file with `format` above 1 or a malformed chain is refused whole.
- **Writes are atomic**: write to a temporary file, then rename. A damaged file is kept as `*.damaged-<date>` before the defaults are written (spec edge case).

**Alternatives rejected**:
- TOML or JSON: a new format, unlike the chain text that both programs already share.
- Sharing TuxGuitarAdvanced's store automatically: refused by the owner (Q2 B).

## R7. External parameters (FR-016, SC-008)

**Inspected**: `grep "static final"` finds 53 constants in `monitor/` and `monitor/fx`. Some examples:
- `OutputStage.CEILING` (−1 dBFS), `FADE_IN_SECONDS` 0.5, `FADE_OUT_SECONDS` 0.020, `RELEASE_SECONDS` 0.050;
- `Chain.FADE_SECONDS` 0.010;
- `FeedbackGuard.SIZE` 2048, `ABOVE_MEDIAN_DB` 20, `NARROW_DB` 10, `NARROW_BINS` 4, `LOUD_DB` −12, `HOLD_SECONDS` 0.300, `RISE_DB` 15, `FALL_TOLERANCE_DB` 1, `LOW_HZ` 60, `HIGH_HZ` 12000;
- `MonitorEngine.BLOCK_FRAMES` 128, `CLIPPING_LEVEL` −0.1 dBFS, `OVERLOAD_BLOCKS` 20.

**Decision**:
- The first GuitarFX task writes `specs/027-standalone-amp/inventory.md` with the method of spec 026's inventory (`specs/026-engine-external-parameters/inventory.md`): every constant and every inline number of the ported code that changes what is heard, each one either a **parameter** or **fixed** with its kind and reason (a mathematical constant, a buffer size, a format).
- Each parameter has a key `guitarfx.<category>.<name>`, a unit, a default equal to the Java value, bounds, a description and a category of the *Advanced* window.
- The values are read once when the sound is turned on and passed to the chain's constructor, so the real-time path never reads settings.
- A test scans the Rust sources the way spec 026's inventory test does (numbers outside an allow list and outside the inventory fail), which is SC-008's automated check.

**Alternative rejected**: making the Java constants external too. FR-022 says TuxGuitarAdvanced keeps its effects as they are, and the comparison runs at the defaults.

## R8. Delay (SC-002) and the probe

**Decision**:
- `guitarfx-probe` is a small binary of the workspace, kept out of the packages. It plays 20 clicks of 1 ms, 3 s apart, through GuitarFX's own engine, finds them at the input with the threshold of spec 022's probe (`MonitorLatencyProbeTest`), and writes the same table as `specs/022-guitar-amp-effects/benchmark/native-probe-usb.md`: block, rate, clicks found, median, min, max, estimate, dropped blocks.
- It is **audible**: it runs only when the owner asks, and the owner is warned before (owner's rule).
- The report goes to `specs/027-standalone-amp/benchmark/latency.md`, next to the spec 022 figures for the same devices.

**Estimate shown** (FR-017): input block + output block + the devices' declared latencies (`latency::declared`) + resampler fill, in ms, named as an estimate.

## R9. Installers (FR-026)

**Decision**:

| System | Package | Application | Plugins | Built on |
|---|---|---|---|---|
| macOS | `GuitarFX-<version>-macos-arm64.pkg` (`pkgbuild` + `productbuild`, Apple's own tools) | `/Applications/GuitarFX.app` (Info.plist with `NSMicrophoneUsageDescription`) | `/Library/Audio/Plug-Ins/VST3/GuitarFX.vst3`, `/Library/Audio/Plug-Ins/CLAP/GuitarFX.clap`, `/Library/Audio/Plug-Ins/Components/GuitarFX.component` | `macos-15` (the release job's runner) |
| Windows | `GuitarFX-<version>-windows-x86_64-setup.exe` (NSIS; its stub is zlib-licensed, nothing of NSIS is linked into GuitarFX) | `C:\Program Files\GuitarFX\` and a Start menu entry | `C:\Program Files\Common Files\VST3\GuitarFX.vst3`, `…\Common Files\CLAP\GuitarFX.clap` | `ubuntu-24.04` with MinGW, or `windows-2022` (R3 risk) |
| Linux | `guitarfx_<version>_amd64.deb` and `GuitarFX-<version>-linux-x86_64.tar.gz` with `install.sh` | `/usr/bin/guitarfx` and a `.desktop` entry (deb), `~/.local` (tar.gz) | `/usr/lib/vst3/`, `/usr/lib/clap/` (deb); `~/.vst3/`, `~/.clap/` (tar.gz) | `ubuntu-24.04` |

- Each installer has an uninstaller:
  - macOS: a `guitarfx-uninstall` script installed with the application, since a `.pkg` has no uninstaller of its own;
  - Windows: the NSIS uninstaller;
  - Linux: `apt remove`, or `install.sh --uninstall`.
- The settings folder of R6 is never touched by an install or an uninstall unless the guitarist asks (FR-026).
- **Signing**: the same as TuxGuitarAdvanced (`release.yml` lines 26–29): the stable self-signed certificate when the secrets exist, ad hoc otherwise, not notarized, so the first launch asks for *Open Anyway*. An ad-hoc-signed Audio Unit loads on Apple Silicon after `auval` passes; `auval -v aufx GFX1 TGAd` is run by CI on macOS.

**Alternative rejected**: a `.dmg` with drag and drop. It cannot place the plugins (Q5 A).

## R10. Real-time safety (FR-015)

**Decision**:
- **All elements always exist.** Each of the 9 elements (7 pedals, amp, cabinet) is allocated when the sound is turned on, at the largest block size and the device's rate. The chain's order and switches are a fixed array of indices and flags.
- **Changes travel without blocking.** A change from the UI is a `ChainParams` value (plain numbers, no heap) posted through `recognition::Mailbox` and picked up at the start of the next block. Crossfades, ramps and glides work as in Java.
- **Turning the sound on with new external parameters builds a new chain off the audio thread** and posts it whole.
- **The check of "no allocation in the callback"** uses a counting global allocator in the tests, as the crate's `no_allocation.rs` does.
- In the plugin, the host calls `process`, and the same rules hold.

## R11. Where GuitarFX lives and how it is built

**Decision**:
- **Layout**: a Cargo workspace at `guitarfx/`, at the repository root, separate from TuxGuitarAdvanced's modules. It holds:
  - `guitarfx-dsp`: the effects, the chain, the safety, the chain text; no third-party dependency;
  - `guitarfx-settings`: the files, the parameter registry, the texts;
  - `guitarfx-ui`: egui;
  - `guitarfx-app`: eframe and cpal;
  - `guitarfx-plugin`: nih-plug and clap-wrapper;
  - `guitarfx-probe`.
- **Maven**: `guitarfx/pom.xml` is the module of a new `guitarfx` profile of the macOS SWT build script, after `TuxGuitar-audio-engine`, so the Java references exist when `cargo test` runs; the owner's gate is `-P native-modules,guitarfx`. It is not in `native-modules`: TuxGuitarAdvanced's CI and release jobs build that profile with `-DskipTests` and without `cargo-deny`, and its Windows job cross-compiles with MinGW (`release.yml`), so GuitarFX there would break TuxGuitarAdvanced's releases (corrected 2026-11-07, `/speckit-analyze`). GuitarFX is never copied into TuxGuitarAdvanced's packages.
- **CI**:
  - A new workflow `.github/workflows/guitarfx.yml` builds and tests on `macos-15`, `ubuntu-24.04` and Windows: the licence gate, the comparison, the no-allocation test, the bundle, `auval`, and a start-up check of the application in headless mode.
  - `release.yml` gets three jobs that build the installers and attach them to the same draft release, plus a check of the draft that names the GuitarFX files.
- **Rust**: the toolchain of spec 022 (1.93 on the owner's Mac, `dtolnay/rust-toolchain` in CI).
