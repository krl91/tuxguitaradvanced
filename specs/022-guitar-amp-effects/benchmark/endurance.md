# Endurance (spec 022, task T035, SC-006)

- Java 21.0.13, Mac OS X aarch64
- The full chain of T034 on a device paced by the clock; counts after a one-minute warm-up.

| Minutes | Dropped blocks | Silent reads | Over-budget blocks | Heap growth (MB) | Worst lateness of a read (ms) | Worst time between reads (ms) |
|---|---|---|---|---|---|---|
| 2 | 0 | 0 | 6 | -0.07 | 16.3 | 18.1 |
