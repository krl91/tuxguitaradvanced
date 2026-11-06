# Quickstart: validating GuitarFX (spec 027)

## Prerequisites

- JDK 21:
  ```sh
  export JAVA_HOME=$(/usr/libexec/java_home -v 21)
  export PATH="$JAVA_HOME/bin:$PATH"
  ```
- Rust stable (1.93 on the owner's Mac).
- `cargo install cargo-deny clap-validator` for the licence and plugin checks.

## 1. Full build and tests (every phase)

```sh
cd desktop/build-scripts/tuxguitar-macosx-swt-cocoa
mvn -e clean verify -P native-modules
```

Expected:
- **BUILD SUCCESS.**
- `TuxGuitar-audio-engine` writes `target/guitarfx-reference/` (`GuitarFxReferenceTest`).
- The `guitarfx` module runs `cargo deny check licenses` and `cargo test --workspace` with `GUITARFX_REFERENCE_DIR` set; this covers SC-003, the no-allocation test, silence, ceiling, smooth changes and the inventory scan.
- No committed test is changed.

Afterwards, run `git checkout -- specs/003-arcade-mode/benchmark/ specs/023-native-recognition-engine/benchmark/recognition-delay.md`, as after every build.

## 2. Only GuitarFX

```sh
cd guitarfx
cargo test --workspace            # the Java comparison is skipped with a message unless GUITARFX_REFERENCE_DIR is set
cargo run -p guitarfx-app -- --check   # prints "ok"
cargo run -p guitarfx-app --release    # opens the window
```

## 3. Play (owner, the Mac unlocked; audible)

1. Plug the Rocksmith cable and put on headphones.
2. Open GuitarFX, choose the cable as input and the headphones as output, and turn the sound on. The sound fades in; play.
3. Pick each of the four ready-made sounds, add and move pedals, and move every knob: no click, and each change is heard at once (SC-004).
4. Quit, reopen: the devices, sound and chain are the same, and the sound is off (SC-007).
5. Choose the built-in microphone: the warning appears (FR-012).

## 4. Sounds with TuxGuitarAdvanced (SC-011)

1. In GuitarFX: ⋯ → *Export…* on a sound using every pedal.
2. In TuxGuitarAdvanced, *Amp and effects* → ⋯ → *Import…* the file: it is listed and sounds the same.
3. Do the reverse.
4. The automated round trip is part of step 1's build.

## 5. Delay (SC-002; owner's request only, audible)

```sh
cd guitarfx && cargo run -p guitarfx-probe --release -- --input "<device>" --output "<device>" --block 128
```

The report goes to `specs/027-standalone-amp/benchmark/latency.md`. Compare with `specs/022-guitar-amp-effects/benchmark/native-probe-usb.md` for the same devices.

## 6. The plugin (SC-012)

```sh
cd guitarfx && cargo xtask bundle   # GuitarFX.clap, GuitarFX.vst3, GuitarFX.component (macOS)
clap-validator validate target/bundled/GuitarFX.clap
auval -v aufx GFX1 TGAd              # macOS, after copying the component to ~/Library/Audio/Plug-Ins/Components
```

Then, as the owner:
1. Load GuitarFX on a track in SoundCraft, in each format.
2. Load it in GarageBand or Logic (Audio Unit).
3. Pick a sound, play, save the project, reopen it: same sound.

## 7. Installers (FR-026)

- macOS: install `GuitarFX-<version>-macos-arm64.pkg`.
  - `/Applications/GuitarFX.app` and the three plugins are in `/Library/Audio/Plug-Ins/`.
  - Run `guitarfx --check`.
  - Uninstall with `/Applications/GuitarFX.app/Contents/Resources/guitarfx-uninstall`: no GuitarFX file is left but the settings folder.
- Windows and Linux: the same steps run in CI (`guitarfx.yml`), then by testers.

## 8. Endurance (SC-006; owner's request)

Play for one hour through a chain of six active elements. `benchmark/endurance.md` records the dropped blocks (expected 0) and the worst gap between callbacks.
