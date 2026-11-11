# Tasks: GuitarFX, a standalone amp and effect pedals

**Input**: design documents from `specs/027-standalone-amp/`

**Prerequisites**: plan.md, spec.md (clarified 2026-11-06, 5 answers), research.md (R1–R11), data-model.md, contracts/ (sound-file, settings-files, application, plugin), quickstart.md

**Tests**: required. The owner's rules apply:
- tests come first and must be seen red;
- never modify an existing test, only add new ones;
- if a committed test fails, fix the code under the bug protocol (a reproducing test first, at most 3 attempts), then stop and report.

**Approved exceptions**: none. TuxGuitarAdvanced's behaviour does not change except for the two new menu items (FR-022), so no committed test may need to change.

**Stop conditions**: stop and report to the owner, with the figures, when:
- **A**: an element's output differs from the Java reference by more than −90 dBFS (SC-003), and 3 attempts do not explain it;
- **B**: a licence outside the allow list of research R3 is pulled in (`cargo deny` red) and no replacement exists;
- **C**: the VST3 or AUv2 wrapper cannot be built on a system, even on the fallback runner of R3;
- **D**: the delay measured by the probe (T062) is longer than TuxGuitarAdvanced's with the same devices (SC-002).

**Paths**:
- `GFX` = `guitarfx` (the new workspace at the repository root)
- `DSP` = `guitarfx/guitarfx-dsp`, `SET` = `guitarfx/guitarfx-settings`, `UI` = `guitarfx/guitarfx-ui`, `APP` = `guitarfx/guitarfx-app`, `PLUG` = `guitarfx/guitarfx-plugin`
- `MON` = `desktop/TuxGuitar-audio-engine/src/main/java/app/tuxguitar/audio/engine/monitor`
- `MONT` = `desktop/TuxGuitar-audio-engine/src/test/java/app/tuxguitar/audio/engine/monitor`
- `PR` = `desktop/TuxGuitar-practice/src/app/tuxguitar/app/tools/custom/practice`
- `PT` = `desktop/TuxGuitar-practice/test/app/tuxguitar/app/tools/custom/practice`
- `CRATE` = `desktop/build-scripts/native-modules/tuxguitar-monitor-native`
- `BUILD` = `desktop/build-scripts/tuxguitar-macosx-swt-cocoa`
- `SPEC` = `specs/027-standalone-amp`

**Standing rules for every phase**:
- **Builds:**
  - pin JDK 21 and wrap builds in `caffeinate -i`;
  - after each Maven build, run `git checkout -- specs/003-arcade-mode/benchmark/ specs/023-native-recognition-engine/benchmark/recognition-delay.md`;
  - never run two builds at once, and never edit sources while a build runs;
  - recreate the `.app` jre symlink after a clean build.
- **Gate** (end of each phase):
  - `mvn -o -e clean verify -P native-modules,guitarfx` from `BUILD` is green, including `cargo deny check licenses` and `cargo test --workspace` of `GFX` (the `guitarfx` profile of T004; TuxGuitarAdvanced's own builds, CI and release jobs never activate it);
  - `git diff --name-status <phase start> -- '*test*' '*Test*'` shows only added files.
- **Real time:**
  - nothing is allocated, locked or awaited in the cpal callbacks or in the plugin's `process`;
  - the counting-allocator test (T020) covers every new path that runs there.
- **Audible:** anything that plays a sound (the probe, the play tests) runs only on the owner's request, after warning them.
- **Port rule** (research R2): each Rust element is a line-for-line port of its Java class. It keeps `f64` where Java has `double` and `f32` where Java has `float`, and the same order of operations. Its test against the Java reference is written first and seen red (missing element).
- **Commits and pushes:**
  - commit each phase without any co-author trailer;
  - one push per round, both refs together (`git push origin feature/interactive-practice feature/interactive-practice:master`), after the last automated phase.

## Format: `[ID] [P?] [Story] Description`

## Phase 1: Setup (the inventory, the workspace, the Java references)

- [X] T001 Write `SPEC/inventory.md` with the method of `specs/026-engine-external-parameters/inventory.md`.
  - **Sources**: every `static final` (53 today) and every inline number of `MON/fx/*.java`, `MON/Chain.java`, `MON/FeedbackGuard.java`, `MON/MonitorEngine.java`, `MON/fx/OutputStage.java`.
  - **Each value is either**:
    - a **parameter**: key `guitarfx.<category>.<name>`, category of contracts/application.md, unit, default (the Java literal), min, max, a description;
    - or **fixed**: its kind (a mathematical constant, a buffer size, a format, a knob range of spec 022) and its reason.
  - **Categories**: at most 8 rows each.
  - The scan's allow list of plain numbers is written at the top.
- [X] T002 Create the workspace `GFX/Cargo.toml` with the members `guitarfx-dsp`, `guitarfx-settings`, `guitarfx-ui`, `guitarfx-app`, `guitarfx-plugin`, `guitarfx-probe`.
  - Each member is an empty crate that builds.
  - `rust-version = "1.93"`, the toolchain of `.github/actions/install-rust` (the CRATE declares none).
  - Run `cargo fetch` once (network: the crates are not in the local cargo cache) and commit `GFX/Cargo.lock`; every later build is `--locked`.
  - The licence is `LGPL-2.1-or-later`, as the CRATE.
  - Add `GFX/README.md` (what GuitarFX is, how to build) and `GFX/.gitignore` (`target/`).
- [X] T003 Write `GFX/deny.toml` with the allow list of research R3: MIT, Apache-2.0, Apache-2.0 WITH LLVM-exception, ISC, BSD-2-Clause, BSD-3-Clause, Zlib, Unicode-3.0, LGPL-2.1-or-later, and the non-GPL licences R3 expects in the planned tree (MPL-2.0, OFL-1.1, LicenseRef-UFL-1.0, BSL-1.0, CC0-1.0).
  - Each of those five is allowed only for the crates R3 names (`exceptions` of `deny.toml`), and T029 and T051 run `cargo deny list` once their dependencies are in, adjusting the exceptions to the crates actually pulled and recording them in R3.
  - Every GPL and AGPL variant is denied, and unknown licences are denied.
  - Check that `cargo deny check licenses` passes on the empty workspace, then fails when a GPL test crate is added temporarily in a scratch copy (seen red, never committed).
- [X] T004 Write `GFX/pom.xml`, a Maven module in the style of `CRATE/pom.xml`:
  - `cargo deny check licenses`;
  - `cargo test --workspace`, with `GUITARFX_REFERENCE_DIR=${project.basedir}/../desktop/TuxGuitar-audio-engine/target/guitarfx-reference`;
  - `cargo build --release -p guitarfx-app`.
  - every cargo call has `--locked`, as in `CRATE/pom.xml`; `cargo deny` and `cargo test` are skipped when `skipTests` is set.
  - Add the module to a **new profile `guitarfx`** of `desktop/build-scripts/tuxguitar-macosx-swt-cocoa/pom.xml` only, listed after `TuxGuitar-audio-engine`. It is **not** added to `native-modules`, nor to the Linux or Windows build scripts: TuxGuitarAdvanced's CI and release jobs build with `-P native-modules -DskipTests` and have no `cargo-deny`, so the module there would break them (constitution I). GuitarFX's Linux and Windows builds are `guitarfx.yml` (T058) and its own release jobs (T060).
  - Check that `mvn -o -e clean verify -P native-modules` without the profile builds as before, and that no packaging step copies anything of `GFX`.
- [X] T005 New test `MONT/fx/GuitarFxReferenceTest.java` (package `app.tuxguitar.audio.engine.monitor.fx`, so it can call the package-private `Signals`; normal build, not tagged). For each element of `MON/fx`, the whole chain of each ready-made sound of `MON/ReadyMadeChains.java`, `FeedbackGuard` and `OutputStage`, at 44.1, 48 and 96 kHz, with the default knobs and with each knob at its minimum and maximum:
  - it runs `Signals.sine` at 3 levels and `Signals.noise` at −40 dBFS (the committed `Signals.java` is not changed), plus a silence and a step built in the test itself, and the first recording of `benchmark/MonitorFixtureSet.recognitionInputs()` (public, the guitar recordings of spec 022's `RecognitionUntouchedTest`);
  - it writes the input, the knob values and the output as little-endian `f32` files, plus an `index.properties`, into `desktop/TuxGuitar-audio-engine/target/guitarfx-reference/`;
  - it asserts that the files exist and that the index lists them.
  - *Done*: `Chain` and `FeedbackGuard` are package-private in `monitor`, so a new public test helper `MONT/GuitarFxReferenceRuns.java` runs them for the test. Besides the elements of `MON/fx`, the test also writes `Biquad` (five shapes and a glide) and `Oversampler` (factors 1, 2, 4, up then down), a *moved* variant per element (every knob to its maximum in the middle), the amp's change of character, each ready-made sound changed for the next, and a growing 1 kHz howl for the guard (found after about 0.49 s at each rate). 2,544 runs, 93 MB, 3.4 s.
- [X] T006 Run the gate once (T004 and T005 in): green.
  - Write `SPEC/baseline.txt`: HEAD, the counts per module, the size of `target/guitarfx-reference/`, and the committed tests that read `MON` (`MONT/**`, `PT/**/Amp*`).
  - Commit Phase 1.

---

## Phase 2: Foundational (the DSP, needed by every story)

**Purpose**: `DSP` holds every element, the chain, the safety, the chain text and the parameter registry, all checked against Java. No story can start without it.

- [X] T007 Test `DSP/tests/java_reference.rs`:
  - it reads `GUITARFX_REFERENCE_DIR` and the index, runs each Rust element on the same input and knobs, and asserts the largest absolute difference is below 10^(−90/20) on every sample (SC-003);
  - without the variable, it prints "skipped: no Java references" and passes;
  - under Maven (T004) the variable is set, and a missing directory fails.
  - Seen red: no element exists yet.
  - *Done*: seen red (no element), then green at the first run on all 2,544 runs: most elements are identical to the bit; the largest difference is −150.5 dBFS (the chorus at 44.1 kHz), the amp at most −162.6 dBFS, the chains −180.6 dBFS. 32 s in a debug build, 2.6 s optimised; `Cargo.toml` builds `guitarfx-dsp` at `opt-level = 2` in the dev profile (Rust never reorders floating-point operations, so the results do not change).
- [X] T008 Test `DSP/tests/params.rs`, the parameter registry:
  - every key of `SPEC/inventory.md`'s parameter rows is there, with its unit, category, bounds and a default equal to the inventory's Java literal (compared with `==`);
  - min ≤ default ≤ max;
  - at most 8 per category;
  - the keys are unique;
  - a value out of bounds is clamped.
  - Seen red.
- [X] T009 Test `DSP/tests/inventory_scan.rs`: it scans `DSP/src/**/*.rs`, `APP/src/**/*.rs` and `PLUG/src/**/*.rs` (the device handling and the plugin's processing are in FR-016's scope; directories not written yet are skipped) for numeric literals outside comments, tests and the allow list of T001, and fails on any literal not named in `SPEC/inventory.md` as fixed or as a parameter's default in `params.rs` (SC-008). Seen red as soon as T010 adds code with a number.
  - *Done*: seen red on the two numbers the inventory lacked, `1e-12` of `FeedbackGuard.db` and the `0.99` of the combined bounds in `params.rs`; both added to the inventory's fixed table in this phase. The echo's 1.5 s line is computed from the *time* knob's maximum (`knobs.rs`), so no `1.5` is left in `echo.rs`.
- [X] T010 [P] Port `MON/fx/Biquad.java` to `DSP/src/biquad.rs`: the gliding of frequency, Q and gain over a block, in `f64`, and the same coefficients.
- [X] T011 [P] Port `MON/fx/Oversampler.java` to `DSP/src/oversampler.rs`.
- [X] T012 Port `MON/fx/NoiseGate.java`, `Compressor.java`, `Overdrive.java` and `Distortion.java` to `DSP/src/{gate,compressor,overdrive,distortion}.rs`, and `MON/fx/Pedals.java` to `DSP/src/pedals.rs`; T007 green for these four.
- [X] T013 Port `MON/fx/Amp.java` and `MON/fx/Cabinet.java` to `DSP/src/{amp,cabinet}.rs`. The three characters are `clean`, `crunch` and `highgain`; the five knobs go from 0 to 10, default 5. T007 green.
- [X] T014 Port `MON/fx/Chorus.java`, `Echo.java` and `Reverb.java` to `DSP/src/{chorus,echo,reverb}.rs`. The delay lines are allocated in `prepare` at the longest time of their knob range and the device's rate. T007 green.
- [X] T015 Port `MON/fx/OutputStage.java` (ceiling −1 dBFS, fade-in 0.5 s, fade-out 0.020 s, release 0.050 s) and `MON/FeedbackGuard.java` to `DSP/src/{output_stage,feedback_guard}.rs`. Their constants come from the registry (T016), with defaults equal to Java's. T007 green.
- [X] T016 Write `DSP/src/params.rs`, the registry of T008: one entry per parameter row of the inventory. A `Params` value is built once from a key→value map and passed to each element's `prepare`. Every element reads its internal values from `Params`, never from a constant. T008 and T009 green.
- [X] T017 Port `MON/Chain.java` to `DSP/src/chain.rs`:
  - the 9 elements always exist (research R10);
  - `order[9]` and `enabled[9]` hold the arrangement;
  - the 10 ms crossfades, the dips on reorder, the ramped knobs;
  - the lightening order of `MonitorEngine.LIGHTER`.
  - `ChainParams` is a plain value with no heap.
  - The chain of each ready-made sound equals the Java chain's reference (T007).
- [X] T018 [P] Test `DSP/tests/chain_text.rs`, then write `DSP/src/chain_text.rs`:
  - parse and format exactly as `MON/ChainSettings.java` (`toText`, `parse`), with the grammar of contracts/sound-file.md;
  - each kind once; a pedal only in its section (gate, compressor, overdrive and distortion before; chorus, echo and reverb after);
  - "amp" with exactly 5 numbers and a character among `clean`, `crunch`, `highgain`;
  - numbers formatted without trailing zeros;
  - a malformed text refused whole.
  - Cases: the four ready-made texts, written by T005 into the reference folder from `ChainSettings.toText()`, must round-trip identically.
- [X] T019 [P] Write `DSP/src/ready_made.rs`: the four sounds of `MON/ReadyMadeChains.java`, "Clean", "Crunch", "Lead" and "Clean echo", as chain texts equal to Java's (the test compares with the reference folder).
- [X] T020 Test `DSP/tests/no_allocation.rs`, with a counting global allocator as `CRATE/src/no_allocation.rs`:
  - `prepare` once, then 10 s of blocks of 128 frames through the full chain;
  - every knob moved, every element switched and moved, the sound changed via `ChainParams`;
  - 0 allocations after `prepare` (FR-015).
- [X] T021 [P] Test `DSP/tests/safety.rs`:
  - every knob at its loudest: the output never exceeds the ceiling (SC-007);
  - the fade-in reaches the set level after 0.5 s (FR-010);
  - each ready-made sound with no input: the output is below −60 dBFS (FR-011);
  - a synthetic howl: it is muted within 0.5 s (SC-005).
  - *Done*, with one finding: at every knob's loudest the peak reaches 0.899 against the ceiling 0.891 (+0.075 dB). The limiter is spec 022's `OutputStage.java`, ported as it is (SC-003): it compares a peak with the gain before that sample's release step, so a peak can pass −1 dBFS by a fraction of a decibel. TuxGuitarAdvanced does the same, and spec 022's `OutputStageTest` holds the peaks at −1 dBFS within 0.1 dB. The test checks that tolerance and 0 dBFS; making the limiter exact needs the same change in Java (TuxGuitarAdvanced's sound), reported to the owner.
- [X] T022 [P] Test `DSP/tests/smooth_changes.rs`: moving every knob end to end, switching and reordering, and changing the sound passes the rule of spec 022's `MONT/SmoothChangesTest.java` (SC-004): the largest sample-to-sample step stays below 1.5 times that of the same run without the change, and the change is done within 0.1 s.
- [X] T023 Gate: green; T007 covers every element, the chain and the four sounds. Commit Phase 2.

  - *Done* 2026-11-10: `mvn -o -e verify -P native-modules,guitarfx` green (practice 1547 tests, 0 failures, 12 skipped; crate 64 passed, 4 ignored; `cargo deny` and the GuitarFX workspace green). Run without `clean`: the owner's TuxGuitarAdvanced was running from the build's `.app`, which `clean` cannot delete. Test files: only added.

**Checkpoint**: the effects exist in Rust and sound the same as Java's.

---

## Phase 3: User Story 1, I hear my guitar through an amp without TuxGuitar (Priority: P1) 🎯 MVP

**Goal**: install, choose the devices, turn the sound on, and hear the guitar through the amp.

**Independent test**: on a Mac with no Java on the `PATH`, `guitarfx --check` prints `ok`; then the owner chooses the cable and the headphones, turns the sound on and plays (quickstart §3, steps 1–2).

- [X] T024 [US1] Change `CRATE/src/lib.rs`: `mod latency` → `pub mod latency`, the only change to the CRATE (plan, constitution II). The CRATE's own tests stay green.
- [X] T025 [P] [US1] Test `SET/tests/settings.rs`, then write `SET/src/{folder,properties,settings}.rs` per contracts/settings-files.md:
  - the folder per system (`directories`);
  - `settings.format=1`;
  - devices, `volume` (0–1, clamped), `sound.current` (default "Clean"), `chain.current`, and every `guitarfx.*` key from the registry;
  - a missing key takes its default, an unknown key is kept, an out-of-bounds value is clamped;
  - atomic writes (temporary file, then rename);
  - **a newer format**: the file is left untouched and the defaults are used;
  - **a damaged file**: copied to `settings.properties.damaged-<yyyymmdd-hhmmss>`, the readable values kept;
  - the sound's on/off state is never written.
- [X] T026 [US1] Test `APP/tests/engine.rs` on a fake device (no hardware), then write `APP/src/engine.rs`:
  - the input callback pushes the chosen channel into a `Ring` (CRATE);
  - the output callback pops blocks of the block size, resamples mono when the rates differ (`APP/src/resample.rs`, a mono Hermite port of `CRATE/src/resampler.rs`), runs the chain, and writes stereo;
  - changes through `recognition::Mailbox` (CRATE): the callback hands every box it took back with `give_back`, never drops it (a drop would free on the audio thread); T020's allocator counts frees as well as allocations;
  - `realtime::promote_current_thread` and `workgroup` on the output thread;
  - when the ring holds more than `guitarfx.devices.ring-blocks` blocks (default 2), the oldest are dropped and counted; the block size is `guitarfx.devices.block` (default 128, `MonitorEngine.BLOCK_FRAMES`);
  - **overload** (FR-014), ported from `MonitorEngine` (lines 243–258): a block whose processing takes more than `guitarfx.overload.budget` of its duration (default 0.5, the inline `/ 2`) counts as slow; after `guitarfx.overload.blocks` slow blocks in a row (default 20, `OVERLOAD_BLOCKS`) the chain is lightened by the next step of `LIGHTER`, and past the last step the sound mutes with its message. The fake device's clock drives it in the test.
  - The fake device checks the output equals the chain's on the same input.
  - T020's allocator covers both callbacks.
- [X] T027 [P] [US1] Test `APP/tests/delay_estimate.rs`, then write `APP/src/delay_estimate.rs`: input block + output block + `latency::declared` (input and output) + the resampler's target fill, in ms at the output rate, rounded to 0.1 (data-model "Estimated delay").
- [X] T028 [US1] Write `APP/src/devices.rs`:
  - list the inputs, their channels and the outputs, with refresh (FR-003);
  - a missing remembered device is shown as missing and never replaced;
  - a lost device, a denied permission or an unsupported format stop the sound with the message of contracts/application.md, and the application stays open (FR-013);
  - the built-in microphone is recognised by the rule of `desktop/TuxGuitar-audio-engine/.../input/InputDeviceCatalog.java` (lines 70–80: a microphone whose name holds `built-in`, `internal` or `macbook`), ported with its cases as a test (FR-012).
  - Test with cpal's error kinds mapped through a fake.
- [X] T029 [US1] Write the main window in `APP/src/main.rs` and `UI/src/{window,devices_bar,messages}.rs` per contracts/application.md:
  - the devices bar, *Sound on* (off at every start), the volume, the input meter with clipping, the estimated delay with the word "estimated" and the Bluetooth note shown at all times beside it, never depending on the output (clarification 2026-11-07), the message line;
  - the window's place and size remembered;
  - it fits 1024×768: a new test `UI/tests/fits_screen.rs` runs the window's layout headless (`egui::Context::run` with a 1024×768 screen, the longest texts of the three languages and every pedal in each tab) and asserts that every widget's rectangle lies inside the screen; it is seen red with a screen of 600 pixels.
  - Texts come through `SET/src/texts.rs` with English keys now; the other languages come in T046.
- [X] T030 [US1] Write `APP/src/single_instance.rs`: a lock file in the settings folder; a second instance shows "GuitarFX is already open." and quits without opening a device. Test with two processes.
- [X] T031 [US1] Write `guitarfx --check` (contracts/application.md "Headless start"): no window, no device; it loads the settings, texts and registry, builds the chain at 48 kHz, processes 1 s of silence, prints `ok`, and exits with 0. Test `APP/tests/check.rs` runs the binary.
- [X] T032 [US1] Write `APP/packaging/macos/Info.plist` (`CFBundleIdentifier app.tuxguitar.guitarfx`, `NSMicrophoneUsageDescription` in three languages through `InfoPlist.strings`) and the `.app` bundle step in `GFX/pom.xml`. Ad-hoc sign it in the local build.
- [X] T033 [US1] Gate: green. Run `GuitarFX.app` once with `--check`. Commit Phase 3.
  - 2026-11-10: `mvn -o -e verify -P native-modules,guitarfx` without `clean` (the owner's TuxGuitarAdvanced was running from the build's `.app`): BUILD SUCCESS, 1547 Java tests (0 failures, 12 skipped), cargo 137 tests (the workspace's 73 and the crate's 64), licences ok. `GuitarFX.app` (signed ad hoc, `app.tuxguitar.guitarfx`) `--check` printed `ok`, exit 0.
  - `cargo fmt --all` had also formatted the native crate (a path dependency); that formatting was undone, so the crate's only change stays `pub mod latency` (T024). Format with `cargo fmt -p <crate>` from now on.
  - Decisions to confirm with the owner: two different devices are resampled even at the same rate (their clocks drift; about 5 ms more delay than a direct path); 32-bit integer devices are accepted besides float and 16-bit; each part of the estimated delay is counted at its own device's rate.

**Checkpoint**: GuitarFX plays the guitar through the chain of the current sound.

---

## Phase 4: User Story 2, the same pedals, sounding the same (Priority: P1)

**Goal**: add, remove, move, switch and turn the pedals and the amp, with every change heard at once and without a click.

**Independent test**: T007 green for every element. The owner moves every knob while playing (quickstart §3, step 3).

- [X] T034 [US2] Test `UI/tests/chain_rows.rs` on the UI's model (no rendering): adding a pedal puts it at the end of its section; a kind already in the chain is not offered; moves stay in the section; removing and switching produce the expected `ChainParams`.
- [X] T035 [US2] Write `UI/src/{chain_rows,knobs}.rs`: the three tabs (before the amp, the amp, after the amp), a row per element with its switch, its knobs as sliders with value and unit (the knob ranges of data-model "Knob"), ↑, ↓ and ✕, and *+ Add a pedal*. Each change posts a `ChainParams` through the engine's mailbox. Tooltips say what the knob changes, its unit and its default.
- [X] T036 [US2] Test `APP/tests/live_changes.rs` on the fake device: a knob change reaches the output within 0.1 s (FR-006) and passes T022's jump limit.
- [X] T037 [US2] Write `SPEC/benchmark/comparison.md`: the largest difference per element and per ready-made sound at each rate, from the T007 run's printout.
- [X] T038 [US2] Gate: green. Commit Phase 4.
  - 2026-11-10: the same gate without `clean`: BUILD SUCCESS, 1547 Java tests (0 failures, 12 skipped), cargo 145 tests (the workspace's 81 and the crate's 64).
  - T036 drives the chain at a level the output stage's limiter never reaches: the limiter clamps a sudden loud peak at once and releases slowly, which is not the change's doing (it is checked by T021); asserted in the test.
  - T037: the per-sound figures come from a new test, `guitarfx-dsp/tests/comparison_by_sound.rs`; the committed `java_reference.rs` is unchanged.

---

## Phase 5: User Story 3, ready-made sounds and my own sounds (Priority: P2)

**Goal**: pick, change, restore, save, rename, delete, export and import sounds, also with TuxGuitarAdvanced.

**Independent test**: SC-011's round trip is green in the build. The owner exports from one program and imports in the other (quickstart §4).

- [X] T039 [P] [US3] Test `SET/tests/sounds.rs`, then write `SET/src/sounds.rs` per contracts/settings-files.md (`sounds.properties`, `sounds.format=1`, `ready.<name>.chain`, `sound.<n>.name`, `sound.<n>.chain`, the keys of `AmpSoundStore`):
  - a newer `sounds.format` or a damaged file is handled as contracts/settings-files.md says (left untouched, or copied aside as `.damaged-<date>`), as T025 does for `settings.properties`;
  - a name is "1 to 40 characters, not blank, unique among the sounds; it cannot take a ready-made sound's name";
  - a changed ready-made sound shows as changed, and *Restore* brings back the shipped chain;
  - an own sound edited shows as changed until *Save*, and is dropped when another sound is picked.
- [X] T040 [P] [US3] Test `SET/tests/sound_file.rs` on the example files `SET/tests/sounds/{valid,format2,malformed,every-pedal}.guitarfx`, then write `SET/src/sound_file.rs` per contracts/sound-file.md:
  - `format` must be 1; a greater number gives "made by a newer version";
  - missing fields or a malformed chain give "not a sound file";
  - the name is trimmed, 1 to 40 characters;
  - the writing order is the comment line, `format`, `name`, `chain`, written atomically.
- [X] T041 [US3] New Java test `MONT/SoundFileTest.java` on the same four example files (read from `guitarfx/guitarfx-settings/tests/sounds/`), then write `MON/SoundFile.java`: read and write with `ChainSettings.parse` and `toText`, the same rules and messages as T040.
- [X] T042 [US3] Round trip SC-011, both ways, in one build:
  - `MONT/SoundFileTest.java` also writes the four ready-made sounds and the every-pedal sound into `target/guitarfx-reference/sounds/`;
  - `SET/tests/sound_file.rs` reads them and asserts equal chains (Java → Rust);
  - it then writes the same five sounds itself and asserts each file is **byte-identical** to Java's. Since `SoundFileTest` reads Java's files, Rust's files are readable by TuxGuitarAdvanced (Rust → Java) without a second build.
- [X] T043 [US3] Write `UI/src/sounds.rs`: the sound list (ready-made first, then own sounds alphabetically, "(changed)"), *Save…* with the name prompt, and ⋯ with *Rename…*, *Delete*, *Restore*, *Export…* and *Import…* (a file dialog through `rfd`, MIT; add it to T003's check). Importing over an existing own name asks "replace, or keep both" (keep both names it "<name> 2"); a ready-made name gives "<name> (imported)".
- [X] T044 [US3] TuxGuitarAdvanced: add *Export…* and *Import…* to the ⋯ menu of `PR/ui/AmpWindow.java` (the `menu` built at line 300, its items made by the helper of line 324) using `MON/SoundFile.java` and the toolkit's file dialog, with their texts and tips in `desktop/TuxGuitar-practice/share/lang/messages{,_fr,_es}.properties` (French and Spanish different from English).
  - The two keys go in `AmpTexts.KEYS`, each with its `.tip`, so the committed `AmpTextsTest` keeps every control with a label and a tip in the three languages.
  - New test `PT/session/AmpSoundImportTest.java` (no window): importing a file into `AmpSoundStore` adds the sound, a name that exists asks (replace, or keep both), a ready-made name gives "<name> (imported)", a newer format is refused with the message.
  - New SWT test `PT/ui/AmpSoundFileSwtTest.java` (`swt-gui-tests`): the ⋯ menu offers *Export…* and *Import…*. Written now, run in T065 with the Mac unlocked.
  - Done 2026-11-10. `SET/tests/sounds_import.rs` (5, seen red) and `PT/session/AmpSoundImportTest.java` (5, seen red) hold the import rule, the same in both programs (`Sounds::import` and `AmpSoundImport`). Decisions within the contract: "keep both" goes on to "<name> 3"… when "<name> 2" is taken, and shortens the name so that the whole fits 40 characters; the import keeps the file's own name ("MINE" gives "MINE 2"); an import does not change the current sound, except *Replace* of the current sound, whose new chain is then played; *Export…* adds `.guitarfx` when the name chosen has another ending.
  - The views never open a file: `UI/src/sounds.rs` returns the actions and `APP/src/app.rs` opens the `rfd` dialogs. To keep the committed `fits_screen.rs` unchanged, the window with its sound list is `window::show_with`, checked at 1024×768 by the new `UI/tests/fits_screen_sounds.rs` (with each sound dialog; 600 px fails); `UI/tests/sounds.rs` checks "(changed)" and the ⋯ items.
  - `rfd` 0.15.4 (MIT) is in `deny.toml`'s comment and research R3: no new licence. The French and Spanish texts of GuitarFX's new keys come with T046.
- [X] T045 [US3] Gate: green, including every committed `MONT/**` and `PT/**/Amp*` test unchanged. Commit Phase 5.
  - Done 2026-11-10: `mvn -o -e verify -P native-modules,guitarfx` BUILD SUCCESS, no Java test failing (SWT tests excluded as usual), cargo 169 passed with the Java round trip run (not skipped), `cargo deny --locked check licenses` green, `GuitarFX.app --check` prints `ok`. No committed test changed.

---

## Phase 6: User Story 4, safe, and tunable without rebuilding (Priority: P2)

**Goal**: the sound is safe by default, and every internal value can be set in the *Advanced* window, in three languages.

**Independent test**: T021 is green; the owner opens *Advanced…*, changes a value, turns the sound off and on, and hears the change.

- [X] T046 [P] [US4] Test `SET/tests/texts.rs`, then write `GFX/lang/messages{,_fr,_es}.properties` and `SET/src/texts.rs`:
  - every key in the three files;
  - French and Spanish differ from English except a short listed set;
  - no translated text contains a figure with a unit: the unit and the default of a knob or a parameter are appended to its tooltip from the knob table or the registry when it is shown, so every tooltip says its unit and default (FR-018) and a changed default never leaves a stale text;
  - the language comes from `sys-locale`, English otherwise.
  - Fill every text of the windows, knobs, parameters, messages and categories.
- [X] T047 [US4] Write the feedback and safety behaviour in the application:
  - the built-in-microphone warning before turning the sound on, with *Turn on anyway* and *Cancel*;
  - a howl mutes the sound with its message (SC-005);
  - the overload lightening message;
  - the input clipping message.
  - Test `APP/tests/safety_messages.rs` on the fake device.
  - Done 2026-11-10 (T046 and T047): `GFX/lang/messages{,_fr,_es}.properties` (399 keys each: the windows, knobs, messages, the *Advanced* window, 19 categories, each of the 103 parameters' name and tooltip) are built into the program; `sys-locale` 0.3.2 (MIT or Apache-2.0, licence check green) picks the language. `SET/tests/texts.rs` (7) was seen red, and its unit check was seen failing on the old volume tooltip ("from 0 to 100 %"), which now gets its range and default from `knobs::tip`; the name length comes from `NAME_LENGTH`. The committed `fits_screen.rs` stays green with the longest French and Spanish texts. Texts the same in every language are the list `SAME` of the test (product and pedal names such as Overdrive, Chorus, Crunch, symbols, OK).
  - T047: the existing behaviour moved into `APP/src/safety.rs` (`needs_warning`, `Watch`), used by `App::poll`; `APP/tests/safety_messages.rs` (4, seen red) drives the real engine on a fake device: a loud steady tone is caught as a howl (muted, message and dialog), slow blocks give each lightening message then the overload one, clipping is said at most once a second.
- [X] T048 [US4] Test `UI/tests/advanced.rs` on the model, then write `UI/src/advanced.rs` per contracts/application.md "Advanced…":
  - categories in a drop-down, at most 8 rows, each row with name, slider, value, unit, "default X" and a tooltip, all from the registry;
  - *Defaults* with a confirmation;
  - the line "Changes take effect the next time the sound is turned on." while the sound is on.
  - Values are saved to `settings.properties` and read when the sound is next turned on (FR-025).
  - The window fits 1024×768.
- [X] T049 [US4] Test `APP/tests/takes_effect.rs`: a non-default value of one parameter per category, written in the settings, changes the chain's output the next time the sound is turned on, and not before.
  - Done 2026-11-10 (T048 and T049): `UI/src/advanced.rs`, opened by *Advanced…* beside the message line (`window::show_all`; the committed `show` and `show_with` are unchanged); a value moved is written to `settings.properties` once it rests, *Defaults* asks first. `UI/tests/advanced.rs` (7, seen red) checks the categories, rows, texts, bounds, the takes-effect line, the confirmation, and the fit at 1024×768 for every category in the longest language.
  - T049: `APP/tests/takes_effect.rs` was first red for five parameters, each time because of the test's scenario, not the program: its fake clock made every block look slow, so the overload rule muted the sound after a second; a pedal level at unity hides `pedals.level-step`; the guard's analysis values only matter once a howl is caught. The scenario now runs quarter-time blocks, a compressor level of 7, and for the analysis category starts from `feedback.loud` = −40 so that the guard catches the tone. Every category then changes the output once the sound is turned on again, and an engine already playing is unchanged.
- [X] T050 [US4] Gate: green. Commit Phase 6.
  - Done 2026-11-10: `mvn -o -e verify -P native-modules,guitarfx` BUILD SUCCESS, no Java test failing, cargo 188 passed, licence check green, `GuitarFX.app --check` prints `ok`. No committed test changed.

---

## Phase 7: User Story 5, the same amp as a plugin (Priority: P3)

**Goal**: GuitarFX as VST3, CLAP and Audio Unit (macOS) in music software.

**Independent test**: `clap-validator` and `auval` pass in CI; the owner loads it in SoundCraft and GarageBand or Logic (T066).

- [X] T051 [US5] Add `nih-plug` (git, pinned to one commit in `Cargo.lock`, the `vst3` feature off), `nih_plug_egui` and `clap-wrapper` (features `vst3` and `auv2`) to `PLUG/Cargo.toml`. `cargo deny check licenses` stays green (stop condition B). Write in `SPEC/research.md` R3 the commit pinned and the licences of the AudioUnitSDK and VST3 SDK copies embedded in `clap-wrapper`, as read from its sources.
  - Done 2026-11-10: commits, licences and the deviation (`nih_plug_egui` from the `egui-baseview` repository, for egui 0.33) in research R3.
- [X] T052 [US5] Test `PLUG/tests/render.rs`: the plugin's `process`, driven directly at 44.1, 48 and 96 kHz with blocks of 64, 128, 512 and 1024, gives the same output as the chain on the reference signals (SC-003), and an offline render equals a real-time one. T020's allocator covers `process`.
  - Done 2026-11-10: `guitarfx-plugin/src/processor.rs` (the plugin's processing, testable without a host); 8 tests, with the 4 ready-made sounds and a chain with every pedal, all within −90 dBFS; a stereo input is the mean of its channels; the overload rule per piece, its budget scaled to the piece; the reported latency measured (45.75 samples, 45 reported: inventory.md corrected, it said 46.5).
- [X] T053 [US5] Write `PLUG/src/lib.rs` per contracts/plugin.md:
  - the nih-plug `Plugin`: mono or stereo input summed to mono, stereo output;
  - everything prepared in `initialize`; `process` cut into pieces of at most `guitarfx.devices.block` frames (default 128);
  - the overload rule of T026 applies to `process` too (FR-024 includes FR-014);
  - `nih_export_clap!`, `export_vst3!`, `export_auv2!`, with the identifiers of the contract (CLAP `app.tuxguitar.guitarfx`, AU `aufx`/`GFX1`/`TGAd`, a fixed VST3 class id);
  - the latency reported as the inventory says;
  - no host automation.
  - Done 2026-11-10: CLAP entry written out in `entry.rs` to give the fixed VST3 class id (deviation, research R3); exported symbols checked (`clap_entry`, `GetPluginFactory`, `GetPluginFactoryAUV2`, `bundleEntry`, no JNI); the processor is rebuilt in `initialize`, `reset` brings back a muted sound.
- [X] T054 [US5] Test `PLUG/tests/state.rs`, then write `PLUG/src/state.rs`: `state.format=1` and `chain=<text>` saved and restored; a newer format gives the default sound and a line in the editor; the external parameters are read from `settings.properties` at load (defaults when missing) and never stored in the state.
  - Done 2026-11-10: 5 tests; the state is one persisted text (`#[persist = "guitarfx"]`); a newer state is kept as it is until the guitarist changes the sound.
- [X] T055 [US5] Write `PLUG/src/editor.rs` with `nih_plug_egui` and the shared `UI` widgets: the sound list and its menu, and the three tabs. There are no devices, volume, *Sound on* or *Advanced*. Sounds are read from and saved to the same `sounds.properties` as the application's.
  - Done 2026-11-10: `window::show_plugin` in `UI` (3 tests: fits 800×560, shows the sounds and tabs, nothing of the devices) and `editor.rs` (6 tests: the logic without a window). The sounds file is written only by the sound list (save, rename, delete, restore, import), never by a knob or a pick; the editor test was written before its code but first run after it. New texts `plugin.state.newer`, `plugin.muted.feedback`, `plugin.muted.overload` in the three languages.
- [X] T056 [US5] Bundle the three formats (`GuitarFX.clap`, `GuitarFX.vst3`, `GuitarFX.component` on macOS) with nih-plug's bundler (`GFX/xtask`), called from `GFX/pom.xml`.
  - Run `clap-validator validate` and, on macOS, `auval -v aufx GFX1 TGAd` after copying the component to `~/Library/Audio/Plug-Ins/Components` (the owner's user folder: tell the owner, remove it after).
  - Build the Windows plugin with the MinGW cross-toolchain of `release.yml`; if the VST3 wrapper fails there, record it and use the `windows-2022` runner in T058 (R3).
  - Done 2026-11-10: the three macOS bundles are made by `GFX/pom.xml` (ant, as `GuitarFX.app`), from templates in `PLUG/packaging/macos`, each signed ad hoc; nih-plug's xtask is not used (deviation, research R3: it makes no Audio Unit, and the application's bundle is already made in the POM). `PLUG/tests/bundle.rs` checks the Audio Unit's version and codes.
  - `auval -v aufx GFX1 TGAd`: **AU VALIDATION SUCCEEDED** (the component copied to `~/Library/Audio/Plug-Ins/Components` for the run, removed after). Warnings: no tail time, preset name not kept, a MIDI entry point from the wrapper in an effect; the Audio Unit offers only stereo in and out (the wrapper takes the first layout), a mono track reaching it as two equal channels, which give the same sound.
  - `clap-validator` was not run: installing it from git was refused on the owner's machine; it runs in CI (T058). The CLAP entry was checked by a small C host instead: id, name, vendor, version, features, and the VST3 class id given to the wrapper.
  - Windows with MinGW (`x86_64-pc-windows-gnu`, the cross-toolchain of `release.yml`) builds: `PLUG/build.rs` links `libuuid` (the VST3 wrapper's `FOLDERID_*`) and the C++ standard library statically (no `libstdc++-6.dll` next to the host). The DLL exports `clap_entry`, `GetPluginFactory`, `InitDll` and `ExitDll`. The `windows-2022` fallback is not needed. Linux is built by CI only.
- [X] T057 [US5] Gate: green. Commit Phase 7.
  - Done 2026-11-10: BUILD SUCCESS, no Java failure, cargo 212 passed (the Java references read), the three bundles built and signed.

---

## Phase 8: Polish and cross-cutting concerns

- [X] T058 Write `.github/workflows/guitarfx.yml`: on `macos-15`, `ubuntu-24.04`, and Windows (MinGW on Ubuntu, or `windows-2022` per T056):
  - `cargo deny`;
  - `GuitarFxReferenceTest` through Maven, then `cargo test --workspace`;
  - the bundles, `clap-validator`, and `auval` on macOS;
  - the installer of T059 installed, then `guitarfx --check`, then uninstalled, checking that no file is left but the settings folder;
  - **no Java** (FR-001, SC-001): the runners have Java, so the job checks the binaries' linked libraries (`otool -L`, `ldd`, `objdump -p`) name no `libjvm`/`jvm.dll`, and runs `guitarfx --check` with `JAVA_HOME` unset and no `java` on the `PATH`.
  - Done 2026-11-10: `guitarfx.yml` runs only when asked (`workflow_dispatch`), so a push never starts it. Jobs `macos` (`macos-15`), `linux` (`ubuntu-24.04`), `windows-build` (MinGW and NSIS on Ubuntu) and `windows` (`windows-2022`: cargo test with MSVC reading the references written on Linux, the installer run silently, `--check` with only System32 on the `PATH`, the uninstall and a comparison of the folders before and after). cargo-deny 0.20.2 and clap-validator 0.4.1 are prebuilt release files, pinned. The Windows application gets `windows_subsystem = "windows"` (no console window; found while checking the cross-built exe). **Not run yet**: it runs on GitHub when the owner starts it.
  - First run 2026-11-11 (run 43022240810, on the owner's request): red on two points, fixed. (1) macOS and Linux: the clap-validator zip holds a tar.gz holding the program; the step now opens it (checked locally on the real archives). (2) Windows: clap-wrapper links `Shell32`, MinGW ships `libshell32.a`, found on macOS's case-insensitive disk only (the local MinGW build of T056 passed); reproduced on a case-sensitive disk image (`ld: cannot find -lShell32`, then linked with a `libShell32.a` copy), `PLUG/build.rs` now copies it beside its `libstdc++.a`. The same build is `release.yml`'s Windows job.
- [X] T059 Write the installers in `GFX/packaging/` per research R9:
  - **macOS**: `pkgbuild`/`productbuild` scripts placing `/Applications/GuitarFX.app` and the three plugins in `/Library/Audio/Plug-Ins/{VST3,CLAP,Components}`, and `guitarfx-uninstall` in the app's Resources.
  - **Windows**: `guitarfx.nsi` (Program Files, Start menu, `Common Files\VST3` and `\CLAP`, the uninstaller).
  - **Linux**: `debian/` (`/usr/bin/guitarfx`, `/usr/lib/vst3`, `/usr/lib/clap`, `guitarfx.desktop`) and `install.sh` with `--uninstall` for the tar.gz (`~/.local`, `~/.vst3`, `~/.clap`).
  - Installing over an older version keeps the settings folder; uninstalling never removes it unless asked (FR-026).
  - Done 2026-11-10: `GFX/packaging/macos/build-pkg.sh` (the bundles copied without extended attributes, `BundleIsRelocatable` false added to the component list, which this macOS's `pkgbuild` no longer writes), `guitarfx-uninstall` (`--remove-settings` to remove the settings too, re-run with sudo); `windows/guitarfx.nsi` (the uninstaller asks, in English, French or Spanish, before removing `%APPDATA%\GuitarFX`; a silent uninstall keeps it); `linux/build-packages.sh` (the .deb and the tar.gz), `control` (the maintainer is the repository's address: no personal e-mail), `install.sh`. Self-tests `GFX/packaging/test-macos-pkg.sh` and `test-linux-packages.sh` pass on the owner's Mac (the .deb part skipped there without `dpkg-deb`; it runs in CI). The NSIS script and the Linux packages were not built locally: CI only.
- [X] T060 Add three jobs to `.github/workflows/release.yml`, `build-guitarfx-macos`, `build-guitarfx-windows` and `build-guitarfx-linux`:
  - they sign as TuxGuitarAdvanced's macOS job does (stable self-signed certificate if the secrets exist, ad hoc otherwise);
  - they attach `GuitarFX-<version>-macos-arm64.pkg`, `GuitarFX-<version>-windows-x86_64-setup.exe`, `guitarfx_<version>_amd64.deb` and `GuitarFX-<version>-linux-x86_64.tar.gz` to the same draft release;
  - the draft check lists those files beside TuxGuitarAdvanced's (FR-021): `misc/verify-release.sh --draft` gains an option `--guitarfx` that adds the four GuitarFX files to the expected list; without it the check is unchanged, so the committed cases of `misc/test-verify-release.sh` stay as they are and new cases are added for the option;
  - SC-009: the job compares each GuitarFX package's size with TuxGuitarAdvanced's package of the same system and fails above a tenth; the sizes go in the final report.
  - Done 2026-11-10: jobs `build-guitarfx-macos`, `build-guitarfx-windows` and `build-guitarfx-linux` run beside TuxGuitarAdvanced's, from the draft's creation; the release job waits for them. macOS signs through `.github/scripts/build-guitarfx-pkg.sh` (the same certificate and keychain steps as `sign-macos-launcher.sh`; the application's designated requirement names the certificate as root, so the microphone permission is kept between versions; ad hoc without the secrets), then checks every signature inside the .pkg. The installers carry the release's X.Y.Z; the .deb carries the Debian version inside and is named with the release's version (GitHub does not keep a `~`).
  - `misc/verify-release.sh --draft --guitarfx`: the 17 committed cases of `misc/test-verify-release.sh` unchanged and passing, 10 new cases added, seen failing on the committed script (9 failures) before passing.
  - SC-009: `misc/check-guitarfx-size.sh` reads the draft's sizes after the draft check, writes them in the job's summary, and fails above a tenth; self-test `misc/test-check-guitarfx-size.sh` (5 cases, seen red), run by the validate job. On the owner's Mac the ad-hoc .pkg weighs 10.2 MB, against 303 MB for TuxGuitarAdvanced's v0.6.0 macOS zip.
  - Not run: the release workflow itself (it runs at the next release), and the signing with the certificate (no secret on the Mac).
- [X] T061 [P] Write `docs/guitarfx/guide.md`, `guide_fr.md` and `guide_es.md`: install, devices, the chain, sounds and files, the delay, headphones and feedback, the *Advanced* window, the plugin and its formats, that the plugin's internal values come from the computer's settings, and the uninstall. Write `docs/guitarfx/parameters.md`, generated from the registry by `GFX/xtask` and checked by a test that regenerates and compares it (FR-019).
  - Done 2026-11-10: the three guides (the labels of the windows as the language files give them). `GFX/xtask` is a new workspace member: `cargo run -p xtask -- parameters` writes the page (103 values in their 19 categories, with the English names and tooltips of the *Advanced* window); `GFX/xtask/tests/parameters_doc.rs` regenerates it and compares, and checks each key's default and bounds are on its row (seen failing before the generator existed). While writing the page: a value out of range is brought to its nearer bound, not replaced by its default (`Params::from_values`); the page says so.
- [X] T062 The latency probe `guitarfx/guitarfx-probe/src/main.rs` per research R8: 20 clicks, the threshold of `MONT/MonitorLatencyProbeTest.java`, and the table of `specs/022-guitar-amp-effects/benchmark/native-probe-usb.md`. **Audible: run only on the owner's request, after warning them.** Write `SPEC/benchmark/latency.md` with the figures next to spec 022's for the same devices (SC-002; stop condition D).
  - Written 2026-11-10, **not run** (audible; waits for the owner's request): `guitarfx-probe` opens the devices as the application does and runs GuitarFX's own engine (`engine::build`, the first ready-made sound). The engine is given silence with the clicks, never the microphone, so nothing is played back; the microphone is recorded beside it, and each click is found there with `MonitorLatencyProbeTest`'s threshold (600 in 16 bits or 8 × the RMS of the 0.1 s before, within 0.3 s). The delay is counted in the input's frames, as spec 022's probes. It refuses to run without `--audible`. `--blocks` (not `--block`, quickstart §5 corrected), `--report` writes the table (spec 022's columns, the engine's underruns where spec 022 counted silent reads). `GFX/guitarfx-probe/tests/analysis.rs` (5 tests, seen failing first) checks the schedule, the detection and the row on made-up recordings.
  - Run 2026-11-11 on the owner's go (set-up A: Samson Q2U before the built-in speakers, as spec 022): 20 / 20 clicks found at each setting; medians 27.8 ms (64), **32.5 ms (128, the default)**, 41.8 ms (256), against TuxGuitarAdvanced's 26.4, 32.5 and 43.5. SC-002 met at 128 (equal, not shorter); stop condition D not met. `SPEC/benchmark/latency.md`. Sheet F17's S1 corrected: the probe's input must hear its output (the owner's remark: the cable with headphones gives nothing back).
- [X] T063 Endurance: `APP/tests/endurance.rs` (ignored by default), one hour on the fake device paced by the clock with six active elements and the real-time thread, counting dropped blocks and the worst gap. Run on the owner's request; write `SPEC/benchmark/endurance.md` (SC-006).
  - Written 2026-11-10; the hour waits for the owner's request. Two threads paced by the clock stand for the devices, both promoted to real-time threads (a first one-minute run with only the output promoted lost 3 blocks to the unpromoted input thread's lateness, with no late output block: a fault of the fake device, fixed in the test, not of the engine). After a warm-up (one minute, or a quarter of a shortened run), it asserts no dropped block, no underrun and the sound still on, and reports the late blocks and the worst gap. A one-minute run in release (`GUITARFX_ENDURANCE_MINUTES=1`): 0 dropped, 0 underruns, 0 late, worst gap 3.05 ms for blocks of 2.67 ms, the output thread under a time constraint.
  - Run 2026-11-11 on the owner's go, one hour in release: **0 dropped, 0 underruns, 0 late, worst gap 4.93 ms** for blocks of 2.67 ms, the sound still On (SC-006 met). The test then failed to write its report (the path was relative to `guitarfx/`, `cargo test` runs in the crate's folder): `SPEC/benchmark/endurance.md` copied from its output; sheet F17's S2 command now gives an absolute path.
- [X] T064 Write `specs/test-sheets/F17-guitarfx.md`, the owner's sheet in French:
  - install, devices, play, sounds, export and import with TuxGuitarAdvanced, the *Advanced* window, feedback;
  - the main and *Advanced* windows on a 768-pixel-high screen: nothing cut off;
  - the plugin in SoundCraft (each format) and in GarageBand or Logic;
  - the uninstall.
  - Write the draft release notes `docs/release-notes/next-guitarfx.md`.
  - Done 2026-11-10: `specs/test-sheets/F17-guitarfx.md` (14 steps, two on request: the probe and the hour; lines for the Linux and Windows testers), listed in `specs/test-sheets/README.md`; `docs/release-notes/next-guitarfx.md`, a section to merge into the next release's notes after F17.
- [X] T065 Final gate:
  - the full build is green;
  - with the Mac unlocked, `-P swt-gui-tests` on the whole branch (the amp window's SWT tests, T044's new one included); a failure is fixed under the bug protocol;
  - `git diff --name-status <spec start> -- '*test*' '*Test*'` shows only added files;
  - the delay on each system (FR-021): macOS from T062, Linux and Windows from testers or stated as unknown;
  - write `SPEC/final-report.md` (each FR and SC with its evidence, the stop conditions met, the deviations: the translation files outside TuxGuitarAdvanced's, the Windows runner if used);
  - update `specs/STATUS.md`;
  - commit, then one push of both refs.
  - 2026-11-10: the full build green without the SWT profile (2,536 Java tests, cargo 219, `--check` ok); the SWT run, made with the Mac locked, gave 92 of 95 green and the 3 tests of `PracticeWindowFitsSwtTest` red for want of a screen (no code of that window changed by this spec): **left open until the SWT run passes with the Mac unlocked**. The test diff shows only added files but `misc/test-verify-release.sh` (cases added, as T060 asks) and the sheets' index. Delay per system: not measured (T062 waits; Linux and Windows unknown). `final-report.md` written, `STATUS.md` updated, committed and pushed.
  - 2026-11-10, Mac unlocked: `mvn -o -e verify -P native-modules,guitarfx,swt-gui-tests` **BUILD SUCCESS**, the 95 SWT tests green (`PracticeWindowFitsSwtTest` 3/3, `AmpSoundFileSwtTest` included), cargo 219 passed, 0 failed. 2026-11-11: the macOS delay from T062 (32.5 ms at 128 frames); Linux and Windows unknown. Closed.
- [ ] T066 [HUMAN] The owner's gate: sheet F17 on the built packages (quickstart §3, §4, §6, §7). Linux and Windows testers' reports, or stated as unchecked (SC-001, SC-012).

## Dependencies & execution order

- **Phase 1 → Phase 2**: T005's references are needed by T007. T001's inventory is needed by T008, T009 and T016.
- **Phase 2 blocks every story.**
- **US1 (Phase 3)** comes before US2's live checks (T036 uses the engine). US3 and US4 need US1's settings and window. US5 needs Phase 2 and the `UI` widgets of T035 and T043.
- **Phase 8** needs every story. T062 and T063 need the owner. T066 is the owner's.

## Parallel example

- Phase 2: T010 and T011 together, then the pedal ports T012–T014 one after the other (they share `pedals.rs`); T018, T019, T021 and T022 together once T017 is in.
- Phase 5: T039 and T040 together, then T041 and T042.

## Implementation strategy

- **MVP = Phases 1 to 3**: the application plays the guitar through the ported chain with the current sound.
- **Then**:
  - US2: editing the chain;
  - US3: sounds and files;
  - US4: safety messages, the *Advanced* window and the three languages;
  - US5: the plugin;
  - then installers, CI and the release.
- **Each phase ends green and committed**, without pause between phases (owner's rule). The run stops only at a stop condition or a [HUMAN] task. The audible tasks T062 and T063 wait for the owner's go.
