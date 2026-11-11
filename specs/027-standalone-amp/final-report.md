# Spec 027: final report

**GuitarFX: the amp and the pedals of spec 022, ported to Rust and checked against Java, in a standalone application and a plugin (CLAP, VST3, Audio Unit), without TuxGuitarAdvanced and without Java, with installers for macOS, Windows and Linux.** Written on 2026-11-10 (T065).

## What was built

- **The workspace `guitarfx/`** (Rust 1.93): `guitarfx-dsp` (the nine elements, the output stage, the feedback guard, the chain, the chain text, the four ready-made sounds, the registry of 103 parameters), `guitarfx-settings` (the settings and sounds files, the `.guitarfx` file, the texts in three languages), `guitarfx-ui` (egui windows shared by the application and the plugin), `guitarfx-app` (the devices through cpal, the engine in the output callback, the main and *Advanced* windows, `--check`), `guitarfx-plugin` (nih-plug CLAP, clap-wrapper VST3 and AUv2), `guitarfx-probe` (the delay probe) and `xtask` (the parameters page). Built by the profile `guitarfx` of the macOS build; TuxGuitarAdvanced's own builds and packages never contain it.
- **The same sound as TuxGuitarAdvanced**: `GuitarFxReferenceTest` writes 2,544 runs of the Java effects (each element, each knob at its bounds, each ready-made sound, the guard and the output stage, at 44.1, 48 and 96 kHz); the Rust port matches every one (worst −150.5 dBFS, the chains −180.6 dBFS, most identical to the bit) (`benchmark/comparison.md`).
- **The application**: devices and channel by exact name, the sound off at every start and faded in, the chain in three tabs, live changes through a mailbox, the estimated delay with the Bluetooth note, the built-in-microphone warning, the feedback guard, the overload lightening then mute, the clipping message, one instance at a time, the settings and sounds files written atomically, newer and damaged files handled.
- **Sounds shared with TuxGuitarAdvanced**: the `.guitarfx` file, written byte for byte the same by both programs; *Export…* and *Import…* in both, with the same import rule.
- **External parameters**: 103 values in 19 categories, each with a default equal to the Java literal it replaces; the *Advanced* window; `inventory_scan.rs` fails on any number in the DSP, the application or the plugin that is neither a parameter nor a listed fixed value.
- **The plugin**: the same chain, output stage and guard, in pieces of the engine's block, equal to the application's at every common rate and host block; the project keeps the chain; its internal values come from the computer's `settings.properties`; a reported latency of 45 samples.
- **Installers and CI**: a `.pkg` (macOS), an NSIS setup (Windows), a `.deb` and a `tar.gz` with `install.sh` (Linux); each uninstall keeps the settings unless asked. `guitarfx.yml` (on request) tests, installs, checks without Java, validates the plugins and uninstalls on the three systems; `release.yml` builds, signs and attaches the four packages, and the draft check lists them.
- **Documentation**: `docs/guitarfx/guide{,_fr,_es}.md`, `docs/guitarfx/parameters.md` (generated, checked by a test), sheet F17, draft release notes `docs/release-notes/next-guitarfx.md`.

## Each requirement

| Req. | Result | Evidence |
|---|---|---|
| FR-001 / SC-001 | **Met in code and CI scripts**: no Java in the binaries (checked with `otool`, `ldd`, `objdump`), `--check` with no `java` on the `PATH`. macOS: `--check` of the built `.app` prints `ok`. The install / run / uninstall on each system is in `guitarfx.yml` (not run yet) and the owner's F17 | `APP/tests/check.rs`; `guitarfx.yml`; F17 (T066) |
| FR-002 | **Met**: one DSP library, the application and the plugin | `PLUG/tests/render.rs` (the plugin equals the chain) |
| FR-003 | **Met**: input, channel and output chosen and remembered by exact name, never replaced by another device; refresh | `APP/tests/devices.rs` |
| FR-004 / FR-005 | **Met**: the amp's three characters and five knobs, the cabinet switch; pedals before and after the amp, each kind once | `UI/tests/chain_rows.rs`, `DSP/tests/java_reference.rs` |
| FR-006 / SC-004 | **Met in tests**: a change heard from the next block, settled within 0.1 s, the step rule of spec 022's `SmoothChangesTest`; heard by the owner in F17 | `DSP/tests/smooth_changes.rs`, `APP/tests/live_changes.rs` |
| FR-007 / SC-003 | **Met**: 2,544 runs within −90 dBFS (worst −150.5 dBFS); the plugin within −90 dBFS at 44.1/48/96 kHz, host blocks 64–1024 | `DSP/tests/java_reference.rs`, `PLUG/tests/render.rs`, `benchmark/comparison.md` |
| FR-008 / FR-009 / SC-007 | **Met**: the four ready-made sounds, save, rename, delete, restore, "(changed)"; devices, volume, chain and sounds kept; off at start. **Finding**: at every knob's loudest a peak can pass −1 dBFS by up to 0.075 dB, as spec 022's limiter does (kept equal to Java) | `SET/tests/{settings,sounds}.rs`, `DSP/tests/safety.rs` |
| FR-010 / FR-011 | **Met**: limiter, fade-in 0.5 s; noise under −60 dBFS for each ready-made sound | `DSP/tests/safety.rs` |
| FR-012 / SC-005 | **Met in tests**: the built-in-microphone warning; a synthetic howl muted within 0.5 s, with its message. The real howl is F17 step 9 | `DSP/tests/safety.rs`, `APP/tests/safety_messages.rs` |
| FR-013 | **Met**: device loss, permission, unsupported format and failures stop the sound with a message | `APP/tests/devices.rs`, `APP/tests/safety_messages.rs` |
| FR-014 | **Met**: slow blocks lighten the chain in five steps, then mute, with messages; the plugin follows the same rule | `APP/tests/engine.rs`, `PLUG/tests/render.rs` |
| FR-015 | **Met**: no allocation, lock or wait in the chain, the engine's callbacks or the plugin's `process` (counting allocator) | `DSP/tests/no_allocation.rs`, `APP/tests/engine.rs`, `PLUG/tests/render.rs` |
| FR-016 / FR-025 / SC-008 | **Met**: 103 parameters, the *Advanced* window, a change taking effect at the next start (one per category); the plugin reads them when the host starts it; no unlisted number left in the code | `DSP/tests/{params,inventory_scan}.rs`, `UI/tests/advanced.rs`, `APP/tests/takes_effect.rs`, `PLUG/tests/state.rs` |
| FR-017 | **Met**: the estimate (blocks, declared latencies, resampler fill), named as an estimate, with the Bluetooth note | `APP/tests/delay_estimate.rs` |
| FR-018 / SC-010 | **Met**: 399 keys in English, French and Spanish, the system's language, every tooltip with its range and default | `SET/tests/texts.rs` |
| FR-019 | **Met**: the guides in three languages and the generated parameters page | `xtask/tests/parameters_doc.rs` |
| FR-020 | **Met**: no GPL code (`cargo deny` with the allow list of research R3, green in every gate) | `GFX/deny.toml`, gates |
| FR-021 | **Met in the release jobs**: the same features and four packages on the three systems in the same draft, checked by `verify-release.sh --draft --guitarfx`. **The delay**: macOS 32.5 ms at 128 frames (USB microphone before the speakers); Linux and Windows unknown | `release.yml`, `misc/test-verify-release.sh` |
| FR-022 | **Met**: TuxGuitarAdvanced's amp keeps its own Java effects; the committed `MONT/**` and amp tests unchanged and green | gates |
| FR-023 / SC-011 | **Met**: the `.guitarfx` file read and written alike by both programs; the round trip on the four ready-made sounds and one with every pedal, both ways, in one build | `SET/tests/sound_file.rs`, `MONT/SoundFileTest.java`, round trip test (T042) |
| FR-024 / SC-012 | **Met in code**: CLAP, VST3 (fixed class id), Audio Unit on macOS; `auval` passed on the owner's Mac; `clap-validator` in CI: its third run found two faults of nih-plug (a damaged state aborted the host; a mono-in stereo-out in-place pair), both guarded in our CLAP entry with tests; 43 checks pass locally (`param-conversions` excluded: the validator divides by zero on a plugin with no parameter). Loading in SoundCraft and GarageBand or Logic is F17 | `PLUG/tests/*`, `auval -v aufx GFX1 TGAd`; F17 |
| FR-026 | **Met in scripts**: the installers place the application and plugins in the standard folders; the uninstall keeps the settings unless asked. macOS self-test run locally; Linux and Windows in CI only | `GFX/packaging/test-macos-pkg.sh`, `test-linux-packages.sh`, `guitarfx.yml` |
| SC-002 | **Met** (2026-11-11): 32.5 ms at 128 frames, equal to TuxGuitarAdvanced's 32.5 ms with the same devices (Samson Q2U before the built-in speakers); not shorter. 27.8 ms at 64 (TuxGuitarAdvanced 26.4), 41.8 ms at 256 (43.5). The 30 ms ceiling with the Rocksmith cable is not measured | `SPEC/benchmark/latency.md` |
| SC-006 | **Met** (2026-11-11): one hour in release with six active elements, 0 dropped blocks, 0 underruns, 0 late, worst gap 4.93 ms for 2.67 ms blocks, the sound still on | `SPEC/benchmark/endurance.md`, `APP/tests/endurance.rs` |
| SC-009 | **Checked by the release job** (`check-guitarfx-size.sh`, sizes in the job's summary). Locally: the macOS `.pkg` weighs 10.2 MB against 303 MB for TuxGuitarAdvanced's v0.6.0 macOS zip (3 %). Windows and Linux sizes at the first release | `misc/test-check-guitarfx-size.sh` |

## Stop conditions

- **A** (an element above −90 dBFS): not met; the worst difference is −150.5 dBFS.
- **B** (a licence outside the allow list): not met; `cargo deny` green in every gate.
- **C** (a wrapper that cannot be built): not met; VST3 and AUv2 build on macOS, the Windows plugin builds with MinGW (the `windows-2022` runner is used only to run the installer and the MSVC tests).
- **D** (a delay longer than TuxGuitarAdvanced's): **not met** at SC-002's 128 frames (32.5 ms each). At 64 frames GuitarFX is 1.4 ms longer, a setting that drops blocks in both programs on this Mac.

## Deviations and points for the owner

- **Translation files outside TuxGuitarAdvanced's**: GuitarFX's texts are in `guitarfx/lang/messages{,_fr,_es}.properties`, built into the program, not in `common/resources/lang`.
- **Plugin (research R3)**: the CLAP entry is written out (nih-plug's macro, plus clap-wrapper's VST3 info factory) to give the VST3 entry a fixed class id; the bundles are made by `guitarfx/pom.xml`, not nih-plug's xtask (which makes no Audio Unit); the mailbox is a copy of the native crate's (linking the crate would export its JNI symbols from the plugin). The Windows plugin links `libuuid` and the C++ standard library statically.
- **`xtask`** exists only for the parameters page.
- **Windows runner**: `windows-2022` runs the installer, `--check`, `clap-validator` and the tests with MSVC; the packages are cross-built on Ubuntu with MinGW, as TuxGuitarAdvanced's.
- **Windows console**: the application is a GUI program (`windows_subsystem = "windows"`), found while checking the cross-built exe.
- **To confirm**: resampling between two devices even at the same rate (two clocks drift), 32-bit devices supported, the delay estimate counted at each device's rate, a stereo plugin input averaged to mono.
- **Limiter**: a peak can pass −1 dBFS by up to 0.075 dB, as in TuxGuitarAdvanced (spec 022's `OutputStageTest` allows 0.1 dB); making it exact would change TuxGuitarAdvanced's sound too.
- **Import decisions** (within the contract): "keep both" goes on to "<name> 3" when "<name> 2" is taken; the file's own name is kept; *Export…* adds `.guitarfx`.
- **Plugin editor**: the sounds file is written only by the sound list (save, rename, delete, restore, import); a muted plugin asks to remove and re-add GuitarFX or reopen the project.
- **Tests seen red late**: the settings tests (phase 3) were written after their code; the plugin editor test was not seen red (both noted in `tasks.md`).
- **A committed test file gained cases**: `misc/test-verify-release.sh` received 10 new cases for `--guitarfx` at its end, as T060 asks; its 17 committed cases are unchanged and pass.
- **Not run locally**: `clap-validator` (installing it from git was refused on the owner's machine), the NSIS installer and the Linux packages (no `makensis`, no `dpkg-deb` here), the release signing with the certificate (no secret here). `guitarfx.yml` runs when started from *Actions*: five runs on 2026-11-11 found six faults, all fixed, and a failing package mirror on the runners, now dropped before apt runs when its name does not resolve (tasks.md T058); macOS green on the fifth and sixth; the seventh run is the check.
- **auval run**: the component was copied to `~/Library/Audio/Plug-Ins/Components` for the run, then removed, and `AudioComponentRegistrar` was restarted.

## Gates

- **Phases 1 to 7**: each gate green and committed (see `tasks.md`).
- **Final gate, 2026-11-10** (`mvn -o -e verify -P native-modules,guitarfx`, without `clean`): **BUILD SUCCESS**; 2,536 Java tests, 0 failures, 1 skipped; cargo 219 passed, 0 failed (64 in the native crate, 155 in GuitarFX, the comparison with the Java references run, not skipped), 5 ignored (the endurance hour among them); `cargo deny` green; `GuitarFX.app --check` prints `ok`.
- **SWT tests (`-P swt-gui-tests`)**: first run with the Mac locked (`IOConsoleLocked` true): 92 of 95, the 3 tests of `PracticeWindowFitsSwtTest` red for want of a screen size. Run again the same day with the Mac **unlocked** (`mvn -o -e verify -P native-modules,guitarfx,swt-gui-tests`): **BUILD SUCCESS, 95 of 95 green**, among them the new `AmpSoundFileSwtTest`; cargo 219 passed, 0 failed.
- **Committed tests**: `git diff --name-status ed5dac77f -- '*test*' '*Test*'` shows only added files, except `misc/test-verify-release.sh` (10 cases added at its end, as T060 asks; its 17 cases unchanged) and `specs/test-sheets/README.md` (the index of the sheets, a line for F17).
- **Other checks run**: `misc/test-verify-release.sh` (27 ok), `misc/test-check-guitarfx-size.sh` (5 ok), `guitarfx/packaging/test-macos-pkg.sh` and `test-linux-packages.sh` (ok, the `.deb` part skipped without `dpkg-deb`), the release `.pkg` built ad hoc with every signature inside verified, the endurance test for one minute (0 dropped).

## Open

- T062 and T063 run 2026-11-11.
- T066 [HUMAN]: sheet F17; Linux and Windows testers, or stated as unchecked.
