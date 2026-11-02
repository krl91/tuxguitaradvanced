# Endurance on a real-time thread (spec 022, task T066, SC-006)

- Java 21.0.13, Mac OS X aarch64
- The full chain of T034 on a device paced by the clock, the engine's thread promoted; counts after a one-minute warm-up.

| Minutes | Thread | Dropped blocks | Silent reads | Over-budget blocks | Heap growth (MB) | Worst time between reads (ms) |
|---|---|---|---|---|---|---|
| 10 | real-time: time constraint, period 2.67 ms | 0 | 0 | 0 | -0.07 | 1.1 |
