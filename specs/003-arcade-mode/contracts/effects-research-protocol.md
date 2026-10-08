# Contract: effects research (increment F, research only)

Nothing is implemented in the first Arcade version (FR-032). This is the protocol for the later work, so that the comparison is reproducible (FR-038, FR-039, SC-013).

## Candidate inventory (task T120)

For each free/open-source real-time audio library found (Java, native with JNI/FFM, or portable C/C++), record, citing the source repository, the documentation and the **tested version**:

* licence and compatibility with GPL-3.0 distribution of the combined work (constitution: "Technical & Platform Constraints"); an incompatible or uncertain licence rejects the candidate before any benchmark;
* maintenance (last release/commit, open critical issues);
* macOS (Apple Silicon), Windows, Linux support; packaging cost in `desktop/build-scripts/*`;
* Java or native integration cost (JNI/FFM, build in `native-modules`);
* real-time safety: allocation and blocking on the audio thread (source inspection and measurement);
* determinism of the output for a fixed input and parameters;
* supported sample rates, channel counts, block sizes;
* availability and quality of adjustable **reverb** and **distortion**.

## Benchmark (task T121), identical for every viable candidate

One harness, one input signal set, one monitoring path, the same sample rates and block sizes. Five states: **bypass**, **distortion only**, **reverb only**, **both**, **live parameter changes**. Report: processing time median / p95 / maximum per block, deadline misses, dropouts, CPU load, allocations and garbage-collection activity in steady state, and the **complete physical-loopback latency** under the worst state. A candidate is **rejected** if it misses the deadline, allocates or blocks on the audio thread, has an incompatible licence, or pushes the path over 22 ms in any state, whatever its sound.

## Requirements carried to the later specification

Each effect has its own on/off and adjustable parameters with documented safe ranges, defaults and a reset; both may be combined; a global bypass restores the dry clear sound; changes are click-free and never interrupt the MIDI backing or the judging (FR-037); no effect is retained if the full path exceeds 22 ms. If no library qualifies, none is adopted and a small purpose-built effect or deferral is considered explicitly.
