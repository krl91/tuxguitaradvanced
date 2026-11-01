# Native delay probe (spec 022, task T063, quickstart §1b)

- Java 21.0.13, Mac OS X 27.2 aarch64
- Input: the system's default input; output: the system's default output
- 20 clicks of 1 ms, 3 s apart, per setting; the input is never played back.
- Estimate: both devices' declared latencies, the input and output buffers and the output ring (one block each), as the window shows it.

| Block (frames) | Rate (Hz) | Clicks found | Median (ms) | Min (ms) | Max (ms) | Estimate (ms) | Median − estimate (ms) | Dropped blocks | Silent reads |
|---|---|---|---|---|---|---|---|---|---|
| 64 | 44100 | 20 / 20 | 54.3 | 54.3 | 56.6 | 53.2 | 1.1 | 19 | 0 |
| 128 | 44100 | 20 / 20 | 60.6 | 58.0 | 63.2 | 57.6 | 3.0 | 0 | 27 |
| 256 | 44100 | 20 / 20 | 71.4 | 71.4 | 72.3 | 66.3 | 5.1 | 0 | 49 |

Run of 2026-11-01 on the owner's MacBook Pro (built-in microphone and speakers, both 44.1 kHz), after two fixes found by this probe:
- a late read no longer writes a block of silence (the first run, where it did, gave 79.3 ms median at 128 frames with a spread of 59 to 126 ms);
- the estimate counts the output ring's two blocks (4 blocks in all): with it, the medians above are within 1.1 ms (64), 0.1 ms (128) and −0.7 ms (256) of the estimate (the Estimate column above still shows the 3-block estimate of this run).
