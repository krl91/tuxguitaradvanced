# GuitarFX delay probe (spec 027, research R8)

- GuitarFX 0.1.0, macos aarch64
- Input: Samson Q2U Microphone (channel 1); output: Haut-parleurs MacBook Pro
- 20 clicks of 1 ms, 3 s apart, per setting, through GuitarFX's engine playing the first ready-made sound at volume 0.5; the input is never played back.
- Delay = the click's frame in the input's recording minus the frame where it went into the engine: the input's block and ring, the chain, the output, both devices and the air.
- Estimate: the window's (both callbacks' blocks, both declared latencies, the resampler's fill).

| Block (frames) | Rate (Hz) | Clicks found | Median (ms) | Min (ms) | Max (ms) | Estimate (ms) | Median − estimate (ms) | Dropped blocks | Underruns |
|---|---|---|---|---|---|---|---|---|---|
| 64 | 48000 | 20 / 20 | 27.8 | 27.8 | 28.0 | 26.2 | 1.6 | 14 | 1 |
| 128 | 48000 | 20 / 20 | 32.5 | 32.4 | 32.6 | 31.6 | 0.9 | 0 | 4 |
| 256 | 48000 | 20 / 20 | 41.8 | 41.8 | 41.9 | 42.5 | -0.7 | 0 | 5 |

## Next to TuxGuitarAdvanced (spec 022, same devices, same probe method)

Run of 2026-11-11 on the owner's MacBook Pro, the Samson Q2U before the built-in speakers (quickstart §5, sheet F17 S1, set-up A).
TuxGuitarAdvanced's figures are `specs/022-guitar-amp-effects/benchmark/native-probe-usb.md` (2026-11-02, real-time thread on).

| Block (frames) | TuxGuitarAdvanced median (ms) | GuitarFX median (ms) | Difference (ms) |
|---|---|---|---|
| 64 | 26.4 | 27.8 | +1.4 |
| **128 (default)** | **32.5** | **32.5** | **0.0** |
| 256 | 43.5 | 41.8 | −1.7 |

- **SC-002** (128 frames): GuitarFX's delay is not longer than TuxGuitarAdvanced's: equal, 32.5 ms. Not shorter: the aim was not reached.
- **Stop condition D** (SC-002's setting, 128 frames): not met.
- At 64 frames GuitarFX is 1.4 ms longer, and drops blocks as TuxGuitarAdvanced did (14 here, 11 there): 64 frames is not a usable setting on this Mac for either program.
- The microphone is not the Rocksmith cable: the 30 ms ceiling with the cable (spec 022) is not measured here.
