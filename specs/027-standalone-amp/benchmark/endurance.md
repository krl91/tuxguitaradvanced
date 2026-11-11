# GuitarFX endurance (spec 027, SC-006, T063)

- macos aarch64, release build: true
- 60 min at 48000 Hz, blocks of 128 frames, the ready-made Clean echo (six active elements); the first 60 s are a warm-up, not counted.
- Output thread: promoted (time constraint, period 2.67 ms)

| Dropped blocks | Underruns | Late blocks | Worst gap between output callbacks (ms) | Block time (ms) | State at the end |
|---|---|---|---|---|---|
| 0 | 0 | 0 | 4.93 | 2.67 | On |

Run of 2026-11-11 (13:39 to 14:39) on the owner's MacBook Pro, on the owner's go. The test printed this report, then failed to write it: `GUITARFX_ENDURANCE_REPORT` was given relative to `guitarfx/`, but `cargo test` runs in the crate's folder (`guitarfx/guitarfx-app/`); the report was copied here from the output. The assertions come after the write and did not run; the figures above meet them (0 dropped, 0 underruns, state On).
