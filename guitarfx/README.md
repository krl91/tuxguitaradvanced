# GuitarFX

GuitarFX turns the computer into a guitar amp with effect pedals, without TuxGuitar and without Java (spec 027, `specs/027-standalone-amp`). It holds the effects of TuxGuitarAdvanced's *Amp and effects* window (spec 022), ported to Rust and checked against the Java ones: the noise gate, compressor, overdrive, distortion, the amp (clean, crunch, high gain, with a three-band equaliser), the cabinet, chorus, echo and reverb.

It comes as a standalone application and as a plugin (CLAP, VST3, and Audio Unit on macOS), on macOS, Linux and Windows.

## Crates

| Crate | What it holds |
|---|---|
| `guitarfx-dsp` | the elements, the chain, the output safety, the feedback guard, the chain text and the parameter registry |
| `guitarfx-settings` | the settings and sounds files, the `.guitarfx` sound files, the texts in three languages |
| `guitarfx-ui` | the windows (egui), shared by the application and the plugin's editor |
| `guitarfx-app` | the application: the devices, the real-time engine, the main and *Advanced* windows |
| `guitarfx-plugin` | the plugin |
| `guitarfx-probe` | the latency probe (it plays clicks: run it only on purpose) |
| `xtask` | the project's tasks: `cargo run -p xtask -- parameters` writes `docs/guitarfx/parameters.md` from the registry |

## Build

Rust 1.93 or later, and `cargo-deny` for the licence check (`cargo install cargo-deny`).

```sh
cd guitarfx
cargo deny check licenses
cargo test --workspace --locked
cargo build --release --locked -p guitarfx-app
```

The comparison with the Java effects needs their reference outputs, written by `GuitarFxReferenceTest` of `desktop/TuxGuitar-audio-engine`. The macOS build of TuxGuitarAdvanced runs both with the profile `guitarfx`:

```sh
cd desktop/build-scripts/tuxguitar-macosx-swt-cocoa
mvn -o -e clean verify -P native-modules,guitarfx
```

Without `GUITARFX_REFERENCE_DIR`, the comparison test prints "skipped: no Java references" and passes.

## Licence

LGPL-2.1-or-later. No GPL code: `deny.toml` lists the licences allowed.
