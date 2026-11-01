# Delay probe (spec 022, research R1, task T002)

- Java 21.0.13, Mac OS X 27.2 aarch64
- Input: Micro MacBook Pro, 48 kHz, 16-bit mono
- Output: the system's default output, 48 kHz, 16-bit stereo
- The input line buffer is 4096 frames, as `CaptureService` opens it; only the read size changes.
- 20 clicks of 1 ms, 3 s apart, over 60 s per setting. The block written is silence apart from the clicks: the microphone is never played back.
- Delay = the click's index in the input minus the first frame of the input block read in the same iteration (that block would be written where the click was): input block + output buffer + devices + about 1 ms of air.
- As the monitor's queue does, no more than 4 blocks beyond one input burst of 1024 frames wait in the input (Java Sound hands the microphone over in bursts of 512 device frames, 11.6 ms): the oldest are dropped (Dropped column). Without it the delay grew by itself, since both devices run at 44.1 kHz behind the 48 kHz lines and the two conversions do not keep the same pace.
- Underrun: the output line's `available()` equals its whole buffer before a write (nothing left to play). Short read: `read` returned less than a block.

| Block (frames) | Line buffer asked (blocks) | Line buffer got (frames) | Clicks found | Median (ms) | 95th percentile (ms) | Min (ms) | Max (ms) | Underruns | Short reads | Dropped blocks |
|---|---|---|---|---|---|---|---|---|---|---|
| 64 | 2 | 128 | 19 / 20 | 101.6 | 110.2 | 100.9 | 110.2 | 0 | 0 | 34661 |
| 64 | 3 | 192 | 19 / 20 | 111.4 | 112.4 | 99.6 | 112.4 | 0 | 0 | 29490 |
| 64 | 4 | 256 | 19 / 20 | 101.3 | 107.5 | 69.1 | 107.5 | 0 | 0 | 24325 |
| 128 | 2 | 256 | 19 / 20 | 115.8 | 122.8 | 100.0 | 122.8 | 0 | 0 | 12161 |
| 128 | 3 | 384 | 19 / 20 | 112.8 | 117.3 | 112.1 | 117.3 | 0 | 0 | 6989 |
| 128 | 4 | 512 | 19 / 20 | 108.4 | 110.2 | 107.5 | 110.2 | 1 | 0 | 1824 |
| 256 | 2 | 512 | 19 / 20 | 126.2 | 129.7 | 123.6 | 129.7 | 1 | 0 | 910 |
| 256 | 3 | 768 | 19 / 20 | 107.8 | 107.8 | 107.7 | 107.8 | 1 | 0 | 0 |
| 256 | 4 | 1024 | 19 / 20 | 104.2 | 104.5 | 104.2 | 104.5 | 3 | 0 | 0 |
