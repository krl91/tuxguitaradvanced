# GuitarFX against the Java effects (spec 027, T037, SC-003)

The Rust port of the spec 022 effects compared with the Java effects on the same input signals. Java writes them in the build
(`GuitarFxReferenceTest` of `TuxGuitar-audio-engine`, into `target/guitarfx-reference/`); the Rust tests read them
(`guitarfx-dsp/tests/java_reference.rs` for every element, `comparison_by_sound.rs` for the chains sound by sound).

- **Run**: 2026-11-10, macOS on Apple Silicon, from the references of the phase 3 gate (`mvn -o -e verify -P native-modules,guitarfx`).
- **Runs**: 2,544 in all, blocks of 128 frames, at 44.1, 48 and 96 kHz.
  - The signals: silence, a step, a quiet, a medium and a loud sine, noise and a guitar recording (363 runs each), and a howl for the
    feedback guard (3 runs).
  - Each run is one element (or one chain) with its settings, most with a change of settings part way through (`change-at`).
- **Limit**: the largest difference on any sample must stay under −90 dBFS. Every run is under it.
- `-inf` means identical to the bit.

## Each element (largest difference over all of its runs, dBFS)

| Element | 44.1 kHz | 48 kHz | 96 kHz |
|---|---|---|---|
| Noise gate | -inf | -inf | -inf |
| Compressor | -inf | -inf | -inf |
| Overdrive | -inf | -inf | -inf |
| Distortion | -inf | -inf | -inf |
| Amp, clean | -252.9 | -210.7 | -inf |
| Amp, crunch | -341.4 | -341.9 | -204.7 |
| Amp, high gain | -162.6 | -186.6 | -168.6 |
| Cabinet | -inf | -inf | -inf |
| Chorus | -150.5 | -inf | -216.7 |
| Echo | -inf | -inf | -inf |
| Reverb | -inf | -inf | -inf |
| Output stage | -inf | -inf | -inf |
| Feedback guard (its decisions) | -inf | -inf | -inf |
| Biquad filter | -inf | -inf | -inf |
| Oversampler | -inf | -inf | -inf |
| The whole chain | -192.7 | -196.4 | -180.6 |

## Each ready-made sound, and the changes from one to the next (dBFS)

| Chain | 44.1 kHz | 48 kHz | 96 kHz |
|---|---|---|---|
| Clean | -inf | -inf | -inf |
| Crunch | -270.9 | -234.8 | -270.9 |
| Lead | -234.8 | -196.4 | -180.6 |
| Clean echo | -inf | -inf | -inf |
| Clean → Crunch | -inf | -301.0 | -inf |
| Crunch → Lead | -240.8 | -234.8 | -240.8 |
| Lead → Clean echo | -192.7 | -204.7 | -198.7 |
| Clean echo → Clean | -inf | -inf | -inf |

## Reading

- Most elements give exactly Java's output: the port keeps Java's `double` arithmetic and its order of operations.
- The small differences come from the mathematical library: Rust's `tanh`, `sin` and `exp` and Java's `Math` do not always round the
  last bit the same way.
  - These functions shape the amp's clipping and the chorus's sway.
  - The worst case, the chorus at 44.1 kHz, is −150.5 dBFS: 60 dB under the limit, far under anything audible or measurable with a
    24-bit interface (about −144 dBFS).
