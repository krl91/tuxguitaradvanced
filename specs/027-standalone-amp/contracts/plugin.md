# Contract: the GuitarFX plugin (FR-024, US5)

## Identity

| Item | Value |
|---|---|
| Name shown by hosts | GuitarFX |
| Vendor | TuxGuitarAdvanced |
| CLAP id | `app.tuxguitar.guitarfx` |
| VST3 class id | fixed 16 bytes, generated once and kept in `guitarfx-plugin/src/lib.rs` |
| AUv2 | type `aufx`, subtype `GFX1`, manufacturer `TGAd` |
| Category | audio effect, guitar / distortion |
| Audio ports | input: mono or stereo (a stereo input is summed to mono, as the application's channel choice gives mono); output: stereo |
| Version | the release version |

## Files

| Format | Bundle | Systems |
|---|---|---|
| CLAP | `GuitarFX.clap` | macOS, Windows, Linux |
| VST3 | `GuitarFX.vst3` (bundle layout of the VST3 spec) | macOS, Windows, Linux |
| AUv2 | `GuitarFX.component` | macOS only (the format's limit) |

All three are built from one Rust library: nih-plug's `nih_export_clap!`, plus `clap-wrapper`'s `export_vst3!` and `export_auv2!` (research R3). `nih_export_vst3!` is never used.

## Behaviour

- **Rate and block**: the plugin follows the host's sample rate and maximum block. It prepares everything in `initialize` (allocation allowed there) and processes any block size up to that maximum, cut into pieces of at most `guitarfx.devices.block` frames (default 128), so the glides and crossfades are the application's. When a piece takes longer than its share of time, the overload rule of the application lightens the chain (FR-014).
- **Real time**: `process` never allocates, locks or waits (FR-015). Editor changes reach it through the same mailbox as in the application.
- **Offline rendering**: the output is the same as in real time; the plugin never reads a clock.
- **Latency reported to the host**: 0 samples. The oversampler's delay is reported if the inventory shows one; the comparison test checks the value.
- **State**: `state.format=1` and `chain=<text>`, saved and restored by the host with its project (US5, scenario 2). A newer state format gives the default sound and a line in the editor.
- **External parameters**: read from GuitarFX's `settings.properties` when the plugin is loaded, defaults when the file is missing (FR-025); not stored in the project. A host session may therefore sound different on a computer whose *Advanced* values differ; `docs/guitarfx/guide.md` says so.
- **Host automation**: none in this spec. The knobs are the editor's, the chain is the state. (Automating the knobs is a later wish, not asked.)

## Editor

- The same widgets as the application (`guitarfx-ui`): the sound list with *Save…*, ⋯ (*Rename…*, *Delete*, *Restore*, *Export…*, *Import…*), and the three tabs of the chain.
- No device list, no volume, no *Sound on*, no *Advanced* window.
- The safety elements (ceiling, feedback guard, fade-in) are active as in the application.
- The plugin's sounds are the same `sounds.properties` as the application's, read when the editor opens and written when a sound is saved from the editor.

## Checks

| Check | Where |
|---|---|
| `auval -v aufx GFX1 TGAd` passes | CI on macOS |
| `clap-validator validate GuitarFX.clap` passes (MIT licence, a test tool only) | CI on the three systems |
| the plugin's render of the reference signals equals the application's chain (SC-003), at 44.1, 48 and 96 kHz and with blocks of 64, 128, 512 and 1024 | `cargo test` |
| it loads, plays and restores its state in SoundCraft on each system, and in GarageBand or Logic on macOS | [HUMAN], SC-012 |
