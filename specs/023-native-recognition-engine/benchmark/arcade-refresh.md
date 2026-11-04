# Arcade at the screen's rate (spec 023, T070, T072, T073)

## 2026-11-04 19:44, before

- Java 21.0.13, Mac OS X 27.2 aarch64, 11 processors.
- The stress score of spec 003 (600 s, 3 notes a second, then chords), the real Arcade window maximized, the MIDI channels silent, the live engine on Micro MacBook Pro.
- On time: a frame within 1.5 display periods of the previous one. Held: a paint longer than one display period. Drift: the song's position against the wall clock over the run.

| Engine | Notice | Display rate, toolkit / measured (Hz) | Frames | On time | Display cycles missed | Held | Paint p50 / p95 / max (ms) | Drift (ms) | Dropped samples | Ended |
|---|---|---|---|---|---|---|---|---|---|---|
| native | none | 120 / 119.8 | 53310 | 98.94 % | 565 | 0 | 2.33 / 2.62 / 4.62 | 0.2 | 0 | PLAYER_STOPPED |
| java | none | 120 / 119.1 | 52052 | 96.81 % | 1663 | 0 | 2.66 / 3.01 / 5.33 | 0.7 | 0 | PLAYER_STOPPED |

- SC-006: native on time 98.94 % (needs 99 %), 0 held (needs 0).
- FR-011: drift native 0.2 ms against Java 0.7 ms (no worse); paint p95 native 2.62 ms against Java 3.01 ms, limit 3.51 ms (no worse).

## 2026-11-04 20:00, after

- Java 21.0.13, Mac OS X 27.2 aarch64, 11 processors.
- The stress score of spec 003 (600 s, 3 notes a second, then chords), the real Arcade window maximized, the MIDI channels silent, the live engine on Micro MacBook Pro.
- On time: a frame within 1.5 display periods of the previous one. Held: a paint longer than one display period. Drift: the song's position against the wall clock over the run.

| Engine | Notice | Display rate, toolkit / measured (Hz) | Frames | On time | Display cycles missed | Held | Paint p50 / p95 / max (ms) | Drift (ms) | Dropped samples | Ended |
|---|---|---|---|---|---|---|---|---|---|---|
| native | none | 120 / 120.1 | 54109 | 99.83 % | 94 | 0 | 2.43 / 2.67 / 6.38 | 1.2 | 0 | PLAYER_STOPPED |
| java | none | 120 / 120.3 | 54139 | 99.81 % | 104 | 0 | 2.64 / 2.91 / 4.98 | -14.5 | 0 | PLAYER_STOPPED |

- SC-006: native on time 99.83 % (needs 99 %), 0 held (needs 0).
- FR-011: drift native 1.2 ms against Java -14.5 ms (no worse); paint p95 native 2.67 ms against Java 2.91 ms, limit 3.40 ms (no worse).

## Before and after T073

The display of the owner's MacBook Pro runs at 120 Hz. Before T073 the canvas ticked every 8.3 ms (half of a 60 Hz frame), so it already asked for frames at about 120 a second, but with no margin: a tick late by a little missed a display cycle. After T073 the frame, the tick (4.2 ms, half a display period) and the quality controller's budget come from the display's rate.

| Engine | On time before → after | Display cycles missed before → after | Held | Paint p95 before → after (ms) |
|---|---|---|---|---|
| native | 98.94 % → 99.83 % | 565 → 94 | 0 → 0 | 2.62 → 2.67 |
| java | 96.81 % → 99.81 % | 1663 → 104 | 0 → 0 | 3.01 → 2.91 |

SC-006 (99 % on time, no frame held) is reached with the native engine after T073; FR-011 (drift and paint no worse than Java) holds in both runs.

## Spec 003's Arcade benchmarks (T075, SC-006)

Run on 2026-11-04 after T073, unchanged; their reports are kept here (`arcade-scene-T075.txt`, `arcade-stress-T075.md`), spec 003's own files stay as committed.

- `ArcadeRenderBenchmarkTest` (scene baseline): passed. Total p99 at most 0.43 ms at every tier and size, against the bound of 4.2 ms; no frame over 16.7 ms.
- The ten-minute stress run (`ArcadeStressTest`, plain then with the renderer): the frames are much better than in spec 003's record (missed deadline slots 2 instead of 109, interval between paints p95 9.05 ms instead of 13.46 ms, paint p99 4.19 ms instead of 5.15 ms), no capture overrun, the MIDI backing complete. Its last check, the identity of the judgements between the two runs, failed by 2 lines of 1129 (one note ON_TIME in one run and EARLY in the other, and the score that follows). Spec 003's record already showed 9 differing lines between two runs without the renderer and 13 with it: the real-time chain is not bit-reproducible over ten minutes. Accepted by the owner on 2026-11-04 as timing variation, not a regression; the test is unchanged.
