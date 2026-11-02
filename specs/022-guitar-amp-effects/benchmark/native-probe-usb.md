# Native delay probe through the USB microphone (spec 022, task T067; the real-time thread on)

- Java 21.0.13, Mac OS X 27.2 aarch64
- Input: Samson Q2U Microphone; output: the system's default output
- 20 clicks of 1 ms, 3 s apart, per setting; the input is never played back.
- Estimate: both devices' declared latencies, the input and output buffers and the output ring (one block each), as the window shows it.

| Block (frames) | Rate (Hz) | Clicks found | Median (ms) | Min (ms) | Max (ms) | Estimate (ms) | Median − estimate (ms) | Dropped blocks | Silent reads |
|---|---|---|---|---|---|---|---|---|---|
| 64 | 48000 | 20 / 20 | 26.4 | 26.4 | 26.8 | 26.1 | 0.4 | 11 | 5 |
| 128 | 48000 | 20 / 20 | 32.5 | 32.3 | 32.6 | 31.4 | 1.1 | 5 | 10 |
| 256 | 48000 | 20 / 20 | 43.5 | 43.0 | 43.5 | 42.1 | 1.4 | 2 | 28 |
