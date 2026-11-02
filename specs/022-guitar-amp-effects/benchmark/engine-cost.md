# Engine cost per block (spec 022, task T072)

- Java 21.0.13, Mac OS X aarch64
- The heaviest chain (every pedal on, high-gain amp at 4x oversampling, cabinet on), then the output stage and the feedback guard (a 2048-point FFT every 10 ms); 60 s after a 60 s warm-up.

| Rate (Hz) | Block (frames) | Whole, mean (% of block) | Whole, 99th percentile | Guard alone, mean | Guard alone, 99th percentile |
|---|---|---|---|---|---|
| 48000 | 128 | 4.7 | 5.9 | 0.3 | 1.3 |
