# Inventory: spec 027

Written by task T001 from the Java effects of spec 022 (`MON/fx/*.java`, `MON/Chain.java`, `MON/FeedbackGuard.java`, `MON/MonitorEngine.java`) and the parts of the native crate GuitarFX ports (`CRATE/src/resampler.rs`), with the method of `specs/026-engine-external-parameters/inventory.md`. One row per value: a **parameter** (a key `guitarfx.<category>.<name>` of the registry `DSP/src/params.rs`, shown in the *Advanced* window) or a **fixed** value (its kind and reason).

Totals: 103 parameters in 19 categories (at most 8 each); the 53 `static final` of the sources, each in a row; every inline number in a row or in the allow list.

**Defaults**: each default is the Java literal it replaces, compared with `==` by T008. Where Java computes from two literals (`6 + 2.4 * gain`), each literal is its own parameter and the port keeps the same operations in the same order (research R2).

**Takes effect**: every parameter is read when the sound is turned on (the application) or when the plugin is loaded (FR-025); the real-time path never reads settings.

## How the scan reads this file (T009, SC-008)

The scan takes every numeric literal of `DSP/src/**/*.rs`, `APP/src/**/*.rs` and `PLUG/src/**/*.rs`, outside comments, string contents and `#[cfg(test)]` code, and compares it by **value** (`0.010`, `1e-2` and `0.01_f64` are one value):

1. **Allow list** (anywhere): `0`, `1`, `2`, `0.5`, `10`, `20`, `100`, `1000`.
   - `0`, `1`, `2`: counts, indices, halves and doubles, unity gain, `2π`.
   - `0.5`: halves, the raised cosine `0.5 − 0.5·cos(π·x)`, the Butterworth Q `√0.5`.
   - `10`, `20`: decibels (`10^(dB/20)` for amplitude, `10^(dB/10)` for power).
   - `100`: a percent knob as a fraction. `1000`: milliseconds to seconds.
2. **Parameter tables** (below): their Default, Min and Max are allowed **only in `DSP/src/params.rs`**. Anywhere else, a parameter's value comes from `Params`, never from a literal.
3. **Fixed tables** (below): each number of the Value cell is allowed **only in the Rust file of its row**.
4. **Knob table**: its numbers are allowed only in `DSP/src/knobs.rs`.

A number the port needs and this file lacks is added here **in the same commit**, as a fixed row with its kind and reason, or as a parameter. It is never left as a silent literal.

**Starting state not ported**: Java fields that `prepare` or `set` overwrite before the first sample are not carried into the port. These are `sampleRate = 48000f`, `maxBlock = 128`, `rampLength = 128`, the knob values held in fields (`thresholdDb = -60`, `releaseMillis = 150`, `thresholdDb = -10`, `ratio = 1`) and the gains at `1`. The Rust elements are built by `prepare(rate, block, &Params)` and `set(knobs)`. The two exceptions are fixed rows below, because their effect reaches the first block: the overdrive and distortion tone filters' first frequency.

## Parameters

### `output`: Output and safety

| Key | Unit | Default | Min | Max | Replaces | Description |
|---|---|---|---|---|---|---|
| `guitarfx.output.ceiling` | dBFS | -1 | -12 | 0 | `OutputStage.CEILING` (`10^(−1/20)`) | The loudest the output may be; the limiter holds every sample under it. |
| `guitarfx.output.fade-in` | s | 0.5 | 0.05 | 3 | `OutputStage.FADE_IN_SECONDS` | How long the sound takes to rise when it is turned on. |
| `guitarfx.output.fade-out` | s | 0.020 | 0.005 | 0.5 | `OutputStage.FADE_OUT_SECONDS` | How long the sound takes to fall when it is turned off or muted. |
| `guitarfx.output.release` | s | 0.050 | 0.005 | 1 | `OutputStage.RELEASE_SECONDS` | How fast the limiter lets the level come back after a peak. |

### `feedback`: Feedback guard

| Key | Unit | Default | Min | Max | Replaces | Description |
|---|---|---|---|---|---|---|
| `guitarfx.feedback.above-median` | dB | 20 | 6 | 40 | `FeedbackGuard.ABOVE_MEDIAN_DB` | How far a peak must stand above the spectrum's median to be a howl. Higher: fewer false alarms, a later catch. |
| `guitarfx.feedback.narrow` | dB | 10 | 3 | 30 | `FeedbackGuard.NARROW_DB` | How far the peak must stand above its neighbours on both sides (a howl is a single narrow tone). |
| `guitarfx.feedback.narrow-bins` | bins | 4 | 1 | 16 | `FeedbackGuard.NARROW_BINS` | How far, in spectrum bins, those neighbours are. |
| `guitarfx.feedback.loud` | dBFS | -12 | -40 | 0 | `FeedbackGuard.LOUD_DB` | A peak held above this level is a howl even if it does not grow. |
| `guitarfx.feedback.hold` | s | 0.300 | 0.050 | 2 | `FeedbackGuard.HOLD_SECONDS` | How long a peak must last before it counts. |
| `guitarfx.feedback.rise` | dB | 15 | 3 | 40 | `FeedbackGuard.RISE_DB` | How much a peak must grow during that time to be a howl. |
| `guitarfx.feedback.fall-tolerance` | dB | 1 | 0 | 10 | `FeedbackGuard.FALL_TOLERANCE_DB` | How much a growing peak may dip between two looks and still count as growing. |
| `guitarfx.feedback.drift` | bins | 1 | 0 | 8 | `FeedbackGuard` inline `> 1` | How far the peak may move between two looks and still be the same tone. |

### `feedback-analysis`: Feedback guard, analysis

| Key | Unit | Default | Min | Max | Replaces | Description |
|---|---|---|---|---|---|---|
| `guitarfx.feedback-analysis.low` | Hz | 60 | 20 | 500 | `FeedbackGuard.LOW_HZ` | The lowest frequency watched. |
| `guitarfx.feedback-analysis.high` | Hz | 12000 | 2000 | 20000 | `FeedbackGuard.HIGH_HZ` | The highest frequency watched. |
| `guitarfx.feedback-analysis.hop` | s | 0.010 | 0.002 | 0.100 | `FeedbackGuard` inline `0.010f` | How often the spectrum is looked at. Java multiplies in `float`: the port casts it to `f32` first. |

### `devices`: Devices

| Key | Unit | Default | Min | Max | Replaces | Description |
|---|---|---|---|---|---|---|
| `guitarfx.devices.block` | frames | 128 | 16 | 1024 | `MonitorEngine.BLOCK_FRAMES` | The size of the blocks the chain processes. Smaller: less delay, more work. |
| `guitarfx.devices.ring-blocks` | blocks | 2 | 1 | 16 | new (T026): the input ring the CRATE's capture uses | How many input blocks may wait; older ones are dropped and counted. |
| `guitarfx.devices.clipping` | dBFS | -0.1 | -6 | 0 | `MonitorEngine.CLIPPING_LEVEL` (`10^(−0.1/20)`) | An input peak above this level shows the clipping message. |
| `guitarfx.devices.resample-fill` | blocks | 2 | 1 | 8 | `resampler.rs` inline `2.0 * block` | How full the resampler keeps its buffer when input and output rates differ. |
| `guitarfx.devices.resample-adjust` | × | 0.005 | 0.0005 | 0.05 | `resampler.rs` `MAX_ADJUST` | The largest change of the resampling ratio, as a fraction. |
| `guitarfx.devices.resample-gain` | ×/frame | 0.00002 | 0.000001 | 0.001 | `resampler.rs` `GAIN` | How strongly the ratio follows the buffer's fill. |
| `guitarfx.devices.resample-smoothing` | × | 0.02 | 0.001 | 0.5 | `resampler.rs` `SMOOTHING` | How much the measured fill is smoothed. |

### `smooth`: Smooth changes

| Key | Unit | Default | Min | Max | Replaces | Description |
|---|---|---|---|---|---|---|
| `guitarfx.smooth.crossfade` | s | 0.010 | 0.001 | 0.100 | `Chain.FADE_SECONDS` | The crossfade when a pedal is switched on or off. |
| `guitarfx.smooth.dip` | s | 0.005 | 0.001 | 0.050 | `Chain` inline `FADE_SECONDS / 2` | Each half of the short dip when the pedals are reordered. |
| `guitarfx.smooth.glide-step` | samples | 8 | 1 | 64 | `Biquad.STEP` | How often a gliding filter recomputes its coefficients. |
| `guitarfx.smooth.character` | s | 0.010 | 0.002 | 0.100 | `Amp.CHANGE_SECONDS` | The dip when the amp's character changes. |
| `guitarfx.smooth.cabinet` | s | 0.010 | 0.001 | 0.100 | `Cabinet` inline `0.010` | The crossfade when the cabinet is switched. |
| `guitarfx.smooth.echo-time` | s | 0.010 | 0.001 | 0.100 | `Echo.FADE_SECONDS` | The crossfade when the echo's time changes, and the echo's opening after a reset. |
| `guitarfx.smooth.opening` | s | 0.010 | 0.001 | 0.100 | `Chorus` inline `0.010`, `Reverb` inline `0.010` | How fast the chorus and the reverb open after a reset. |

### `overload`: Overload

| Key | Unit | Default | Min | Max | Replaces | Description |
|---|---|---|---|---|---|---|
| `guitarfx.overload.budget` | × | 0.5 | 0.1 | 1 | `MonitorEngine` inline `blockNanos / 2` | A block whose processing takes more than this share of its duration is slow. |
| `guitarfx.overload.blocks` | blocks | 20 | 2 | 200 | `MonitorEngine.OVERLOAD_BLOCKS` | How many slow blocks in a row lighten the sound. |

### `pedals`: All pedals

| Key | Unit | Default | Min | Max | Replaces | Description |
|---|---|---|---|---|---|---|
| `guitarfx.pedals.level-step` | dB/step | 3.6 | 1 | 6 | `Pedals.level` inline `3.6` | What one step of a pedal's *level* knob changes (5 is unity). |

### `gate`: Gate

| Key | Unit | Default | Min | Max | Replaces | Description |
|---|---|---|---|---|---|---|
| `guitarfx.gate.floor` | dB | -80 | -120 | -20 | `NoiseGate.FLOOR_DB` | How far the closed gate lowers the sound. |
| `guitarfx.gate.hysteresis` | dB | 3 | 0 | 12 | `NoiseGate.HYSTERESIS_DB` | How far under the threshold the sound must fall for the gate to close. |
| `guitarfx.gate.hold` | s | 0.010 | 0 | 0.5 | `NoiseGate.HOLD_SECONDS` | How long the gate stays open after the sound falls. |
| `guitarfx.gate.open` | s | 0.00025 | 0.00005 | 0.010 | `NoiseGate.OPEN_SECONDS` | How fast the gate opens on an attack. |
| `guitarfx.gate.rms` | s | 0.005 | 0.001 | 0.050 | `NoiseGate` inline `0.005` | The time over which the level is measured. |
| `guitarfx.gate.release-range` | dB | 60 | 20 | 120 | `NoiseGate` inline `60.0` | How many decibels the gate falls during its *release* time. |

### `compressor`: Compressor

| Key | Unit | Default | Min | Max | Replaces | Description |
|---|---|---|---|---|---|---|
| `guitarfx.compressor.knee` | dB | 6 | 0 | 24 | `Compressor.KNEE_DB` | The width of the soft knee around the threshold. |
| `guitarfx.compressor.release` | s | 0.050 | 0.005 | 1 | `Compressor.RELEASE_SECONDS` | How fast the compressor lets go. |
| `guitarfx.compressor.rms` | s | 0.005 | 0.001 | 0.050 | `Compressor` inline `0.005` | The time over which the level is measured. |
| `guitarfx.compressor.ratio-step` | ×/step | 0.9 | 0.1 | 3 | `Compressor` inline `0.9` | What one step of *sustain* adds to the ratio (ratio = 1 + this × sustain). |
| `guitarfx.compressor.threshold` | dB | -10 | -40 | 0 | `Compressor` inline `-10` | The threshold with *sustain* at 0. |
| `guitarfx.compressor.threshold-step` | dB/step | -4 | -8 | 0 | `Compressor` inline `- 4 *` | What one step of *sustain* moves the threshold. |
| `guitarfx.compressor.makeup` | × | 0.5 | 0 | 1 | `Compressor` inline `* 0.5` | The share of the gain reduction given back. |

### `overdrive`: Overdrive

| Key | Unit | Default | Min | Max | Replaces | Description |
|---|---|---|---|---|---|---|
| `guitarfx.overdrive.bump` | Hz | 720 | 200 | 2000 | `Overdrive` inline `720` | The middle boost before the clipping. |
| `guitarfx.overdrive.bump-q` | Q | 0.7 | 0.2 | 4 | `Overdrive` inline `0.7` | The width of that boost. |
| `guitarfx.overdrive.bump-gain` | dB | 4 | 0 | 12 | `Overdrive` inline `4` (third argument) | The height of that boost. |
| `guitarfx.overdrive.drive-step` | dB/step | 4 | 1 | 8 | `Overdrive` inline `* 4 / 20` | What one step of *drive* adds. |
| `guitarfx.overdrive.tone` | Hz | 700 | 200 | 4000 | `Overdrive` inline `700 *` | The tone filter with *tone* at 0. |
| `guitarfx.overdrive.tone-step` | oct/step | 0.5 | 0.1 | 1 | `Overdrive` inline `/ 2` | How far one step of *tone* raises it (`tone / 2` and `tone × 0.5` are the same exact value). |

### `distortion`: Distortion

| Key | Unit | Default | Min | Max | Replaces | Description |
|---|---|---|---|---|---|---|
| `guitarfx.distortion.top` | × | 0.5 | 0.1 | 1 | `Distortion.TOP` | Where the positive side clips. |
| `guitarfx.distortion.bottom` | × | -0.35 | -1 | -0.1 | `Distortion.BOTTOM` | Where the negative side clips (unequal sides add warmth). |
| `guitarfx.distortion.tighten` | Hz | 100 | 20 | 400 | `Distortion` inline `100` | The low cut before the clipping. |
| `guitarfx.distortion.emphasis` | Hz | 700 | 200 | 4000 | `Distortion` inline `700` | The treble shelf before the clipping. |
| `guitarfx.distortion.emphasis-gain` | dB | 6 | 0 | 18 | `Distortion` inline `6` | The height of that shelf. |
| `guitarfx.distortion.gain-step` | dB/step | 5 | 1 | 10 | `Distortion` inline `* 5 / 20` | What one step of *gain* adds. |
| `guitarfx.distortion.tone` | Hz | 1500 | 300 | 6000 | `Distortion` inline `1500 *` | The tone filter with *tone* at 0. |
| `guitarfx.distortion.tone-step` | oct/step | 0.4 | 0.1 | 1 | `Distortion` inline `* 0.4` | How far one step of *tone* raises it. |

### `amp-clean`: Amp, clean

| Key | Unit | Default | Min | Max | Replaces | Description |
|---|---|---|---|---|---|---|
| `guitarfx.amp-clean.input-step` | dB/step | 1.2 | 0 | 6 | `Amp` inline `1.2 * gain` (clean) | What one step of *gain* adds before the shape; the output gives it back (`outputDb = −inputDb`). |
| `guitarfx.amp-clean.tighten` | Hz | 70 | 20 | 300 | `Amp` inline `70` | The low cut. |
| `guitarfx.amp-clean.fizz` | Hz | 12000 | 2000 | 20000 | `Amp` inline `12000` | The high cut. |

### `amp-crunch`: Amp, crunch

| Key | Unit | Default | Min | Max | Replaces | Description |
|---|---|---|---|---|---|---|
| `guitarfx.amp-crunch.input` | dB | 6 | 0 | 24 | `Amp` inline `6 +` (input) | The drive with *gain* at 0. |
| `guitarfx.amp-crunch.input-step` | dB/step | 2.4 | 0 | 6 | `Amp` inline `2.4 * gain` | What one step of *gain* adds. |
| `guitarfx.amp-crunch.output-cut` | dB | 6 | 0 | 24 | `Amp` inline `-(6 +` | The level taken back after the shape with *gain* at 0. |
| `guitarfx.amp-crunch.output-cut-step` | dB/step | 1.2 | 0 | 6 | `Amp` inline `1.2 * gain)` (crunch output) | What one step of *gain* takes back. |
| `guitarfx.amp-crunch.tighten` | Hz | 110 | 20 | 300 | `Amp` inline `110` | The low cut. |
| `guitarfx.amp-crunch.fizz` | Hz | 9000 | 2000 | 20000 | `Amp` inline `9000` | The high cut. |
| `guitarfx.amp-crunch.bias` | × | 0.2 | 0 | 0.5 | `Amp.shape` inline `x + 0.2`; the offset `0.197375320224904` is `tanh(0.2)`, computed from it in `prepare` | The shape's asymmetry (even harmonics). |

### `amp-high-gain`: Amp, high gain

| Key | Unit | Default | Min | Max | Replaces | Description |
|---|---|---|---|---|---|---|
| `guitarfx.amp-high-gain.input` | dB | 12 | 0 | 36 | `Amp` inline `12 +` | The drive with *gain* at 0. |
| `guitarfx.amp-high-gain.input-step` | dB/step | 3 | 0 | 6 | `Amp` inline `3 * gain` | What one step of *gain* adds. |
| `guitarfx.amp-high-gain.output` | dB | -14 | -36 | 0 | `Amp` inline `-14` | The level after the shape. |
| `guitarfx.amp-high-gain.tighten` | Hz | 140 | 20 | 400 | `Amp` inline `140` | The low cut. |
| `guitarfx.amp-high-gain.fizz` | Hz | 7000 | 2000 | 20000 | `Amp` inline `7000` | The high cut. |
| `guitarfx.amp-high-gain.bias` | × | 0.1 | 0 | 0.5 | `Amp.shape` inline `x + 0.1`; the offset `0.0996679946249558` is `tanh(0.1)`, computed from it in `prepare` | The first stage's asymmetry. |
| `guitarfx.amp-high-gain.second-stage` | × | 6 | 1 | 20 | `Amp.shape` inline `tanh(6 * first)` | The second stage's drive. |

### `amp-tone`: Amp, tone and volume

| Key | Unit | Default | Min | Max | Replaces | Description |
|---|---|---|---|---|---|---|
| `guitarfx.amp-tone.volume-unity` | step | 6 | 0 | 10 | `Amp` inline `volume - 6` | The *volume* step that gives 0 dB. |
| `guitarfx.amp-tone.volume-step` | dB/step | 3 | 1 | 6 | `Amp` inline `* 3)` (volume) | What one step of *volume* changes. |
| `guitarfx.amp-tone.eq-step` | dB/step | 2.4 | 0.5 | 6 | `Amp` inline `* 2.4` (bass, middle, treble) | What one step of *bass*, *middle* or *treble* changes (5 is flat). |
| `guitarfx.amp-tone.bass` | Hz | 200 | 50 | 600 | `Amp` inline `200` | The bass shelf's frequency. |
| `guitarfx.amp-tone.middle` | Hz | 700 | 200 | 3000 | `Amp` inline `700` | The middle band's frequency. |
| `guitarfx.amp-tone.middle-q` | Q | 0.8 | 0.2 | 4 | `Amp` inline `0.8` | The middle band's width. |
| `guitarfx.amp-tone.treble` | Hz | 1800 | 600 | 8000 | `Amp` inline `1800` | The treble shelf's frequency. |

### `cabinet`: Cabinet

| Key | Unit | Default | Min | Max | Replaces | Description |
|---|---|---|---|---|---|---|
| `guitarfx.cabinet.low-cut` | Hz | 80 | 20 | 300 | `Cabinet` inline `80` (two filters) | The low cut, two filters in a row. |
| `guitarfx.cabinet.low-peak` | Hz | 120 | 50 | 400 | `Cabinet` inline `120` | The low resonance. |
| `guitarfx.cabinet.low-peak-q` | Q | 1.2 | 0.2 | 6 | `Cabinet` inline `1.2` | Its width. |
| `guitarfx.cabinet.low-peak-gain` | dB | 3 | -12 | 12 | `Cabinet` inline `3` | Its height. |
| `guitarfx.cabinet.high-peak` | Hz | 2500 | 800 | 6000 | `Cabinet` inline `2500` | The presence resonance. |
| `guitarfx.cabinet.high-peak-q` | Q | 1.5 | 0.2 | 6 | `Cabinet` inline `1.5` | Its width. |
| `guitarfx.cabinet.high-peak-gain` | dB | 2 | -12 | 12 | `Cabinet` inline `2` | Its height. |
| `guitarfx.cabinet.high-cut` | Hz | 5000 | 2000 | 12000 | `Cabinet` inline `5000` (two filters) | The high cut, two filters in a row. |

### `chorus`: Chorus

| Key | Unit | Default | Min | Max | Replaces | Description |
|---|---|---|---|---|---|---|
| `guitarfx.chorus.centre` | s | 0.0135 | 0.003 | 0.030 | `Chorus.CENTRE_SECONDS` | The voices' middle delay. |
| `guitarfx.chorus.swing-step` | s/step | 0.0006 | 0.0001 | 0.002 | `Chorus.SWING_SECONDS_PER_STEP` | What one step of *depth* adds to the swing. |
| `guitarfx.chorus.warm-wait` | s | 0.020 | 0.001 | 0.060 | `Chorus` inline `0.020` (`longest`) | How long the line fills after a reset before the voices fade in. |

### `echo`: Echo

| Key | Unit | Default | Min | Max | Replaces | Description |
|---|---|---|---|---|---|---|
| `guitarfx.echo.repeats-step` | ×/step | 0.09 | 0.01 | 0.099 | `Echo` inline `0.09 *` | The feedback one step of *repeats* adds (at the maximum, 10 steps stay under 1). |
| `guitarfx.echo.tone` | Hz | 1000 | 200 | 4000 | `Echo` inline `1000 *` | The repeats' high cut with *tone* at 0. |
| `guitarfx.echo.tone-step` | oct/step | 0.45 | 0.1 | 1 | `Echo` inline `* 0.45` | How far one step of *tone* raises it. |

### `reverb`: Reverb

| Key | Unit | Default | Min | Max | Replaces | Description |
|---|---|---|---|---|---|---|
| `guitarfx.reverb.input` | × | 0.015 | 0.001 | 0.1 | `Reverb.INPUT_GAIN` | How much of the guitar enters the reverb. |
| `guitarfx.reverb.wet` | × | 3.0 | 0.5 | 10 | `Reverb.WET_GAIN` | The level of the reverb's sound. |
| `guitarfx.reverb.feedback` | × | 0.70 | 0.3 | 0.9 | `Reverb` inline `0.70 +` | The combs' feedback with *size* at 0. |
| `guitarfx.reverb.size-step` | ×/step | 0.028 | 0.001 | 0.029 | `Reverb` inline `0.028 *` | What one step of *size* adds to the feedback (a longer tail). |
| `guitarfx.reverb.damping-step` | ×/step | 0.04 | 0.01 | 0.09 | `Reverb` inline `0.04 *` | How much one step of *damping* dulls the tail. |
| `guitarfx.reverb.all-pass` | × | 0.5 | 0.1 | 0.9 | `Reverb` inline `delayed * 0.5` | The diffusers' coefficient. |

### Notes on the bounds

- **Combined bounds**: a few parameters work together, and a maximum alone does not keep the sum safe.
  - `reverb.feedback` + 10 × `reverb.size-step` must stay under 1, or the reverb never decays.
  - `echo.repeats-step` × 10 must stay under 1.
  - `params.rs` **clamps the sum when it builds `Params`**, as spec 026's ordered pairs do: the step is lowered so that the sum is at most 0.99. The defaults (sums 0.98 and 0.9) are untouched.
- **`chorus.centre` and `chorus.swing-step`**: the Rust line is sized from these two parameters' **maxima**. It holds `ceil((centre_max + 10 × swing_max) × rate) + block + 4` frames, so no value of the window can read outside it. Java's line (`0.025 s`) is shorter, but the output does not depend on the line's length (the reads are modulo, and every delay fits).

## Fixed

| Value | Rust file | Java source | Kind | Reason |
|---|---|---|---|---|
| 40 | `biquad.rs` | `Biquad.design` `gain / 40` | mathematics | The peak and shelf amplitude is `10^(dB/40)` (the square root of the amplitude ratio; RBJ cookbook). |
| 0.05 | `biquad.rs` | `Biquad.target` `Math.max(0.05, newQ)` | numerical safety | A Q of 0 divides by zero. |
| 0.49 | `biquad.rs` | `Biquad.radians` `0.49 * sampleRate` | numerical safety | A filter at or above Nyquist is unstable; 10 Hz (allow list) is the floor. |
| 1e-20 | `biquad.rs`, `pedals.rs` | `Biquad.process`, `Pedals.flush` | numerical safety | Flushes denormals, which slow the processor down by a hundred times. |
| 63 | `oversampler.rs` | `Oversampler.TAPS` | filter design | The half-band filter's length. A half-band design needs `4k + 3` taps so that every even offset from the middle is zero; a change needs the filter re-derived. |
| 8 | `oversampler.rs` | `Oversampler.KAISER_BETA` | filter design | The Kaiser window's β, chosen with the 63 taps for about 80 dB of image rejection. |
| 40 | `oversampler.rs` | `Oversampler.bessel` `k < 40` | mathematics | The terms of the Bessel series `I₀`; the 40th term is far under `f64`'s precision for β = 8. |
| 1e-12 | `oversampler.rs` | `Oversampler` static block | numerical safety | A tap under this is a zero of the half-band design, skipped. |
| 1e-25 | `oversampler.rs` | `Oversampler.Stage.filter` | numerical safety | Flushes denormals. |
| 4 | `oversampler.rs`, `amp.rs`, `distortion.rs`, `chain.rs` | `Oversampler(4, …)`, `Amp.oversampling = 4`, `new float[4 * maxBlock]`, `Chain.lighten` | structure | The factors are 1, 2 and 4 (two half-band stages). The amp and the distortion run at 4, the overdrive at 2 (spec 022). |
| 3, 5 | `chain.rs` | `Chain.lighten`, `Chain.lightenedOff` | product (spec 022) | The lightening steps, in `MonitorEngine.LIGHTER`'s order: 1 the reverb off, 2 the chorus off, 3 the amp's oversampling at 2, 4 at 1, 5 every pedal off. They are an order, not a level. |
| 9 | `chain.rs` | `Chain.KINDS`, `EffectKind.values()` | structure | The nine elements (seven pedals, the amp, the cabinet), always present (research R10). |
| 1e-30 | `gate.rs` | `NoiseGate.process` | numerical safety | Flushes the mean square's denormals. |
| 1e-12 | `compressor.rs` | `Compressor.process` `meanSquare + 1e-12` | numerical safety | `log10(0)` is −∞. |
| 1e-12 | `feedback_guard.rs` | `FeedbackGuard.db` `Math.max(1e-12, magnitude)` | numerical safety | `log10(0)` is −∞. |
| 0.99 | `params.rs` | none ("Combined bounds" above) | numerical safety | The highest sum of a feedback and its steps: under 1 the reverb's and the echo's tails always decay. |
| 4000 | `overdrive.rs` | `Overdrive.prepare` `setLowPass(4000, …)` | starting state | The tone filter's first frequency. It glides to the knob's frequency within the first block, so it is never heard as a setting, but it shapes that block; it is kept for the comparison with Java (SC-003). |
| 6000 | `distortion.rs` | `Distortion.prepare` `setLowPass(6000, …)` | starting state | The same, for the distortion's tone filter. |
| 10 | `amp.rs`, `distortion.rs` | `Amp.prepare`, `Distortion.prepare` `setHighPass(10, …)` | DC blocker | Removes the offset of the asymmetric shapes. At 10 Hz it is under the audible band and changes nothing heard. |
| 0.45 | `pedals.rs` | `Pedals.onePole` `0.45 * sampleRate` | numerical safety | Keeps a one-pole filter under Nyquist. |
| 5 | `pedals.rs`, `amp.rs` | `Pedals.level` `knob - 5`, `Amp.update` `value.bass() - 5` … | knob range (spec 022) | The middle of a 0–10 knob, where a level or a band is flat. |
| 1.5 | `echo.rs` | `Echo.LONGEST_SECONDS` | buffer size | The line holds the longest *time* of the knob (1500 ms, data-model "Knob"). The port computes it from that knob's maximum in `knobs.rs` (`1500 / 1000`), so the literal is not in the code. |
| 4 | `echo.rs`, `chorus.rs` | `Echo.prepare`, `Chorus.prepare` `+ maxBlock + 4` | buffer size | A margin for the interpolated read. |
| 1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617 | `reverb.rs` | `Reverb.COMBS` | buffer size | The comb lengths in samples at 44.1 kHz (the classic public-domain Freeverb tuning). They are mutually prime as a set; changing one alone colours the tail. |
| 556, 441, 341, 225 | `reverb.rs` | `Reverb.ALL_PASSES` | buffer size | The all-pass lengths, as the combs. |
| 44100 | `reverb.rs` | `Reverb.prepare` `rate / 44100.0` | format | The rate at which those lengths are given. |
| 2048 | `feedback_guard.rs` | `FeedbackGuard.SIZE` | buffer size | The analysis frame. It must be a power of two (the FFT), and it sets the bin width the other values are measured in (23 Hz at 48 kHz). |
| 0.1 | `delay_estimate.rs` (APP) | none (data-model "Estimated delay") | display | The delay is shown to a tenth of a millisecond. |
| 1000000000 | `engine.rs` (APP) | `MonitorEngine` `1_000_000_000.0`, `NOTICE_INTERVAL_NANOS` | mathematics, display | Nanoseconds in a second; a repeated message is shown at most once a second (not heard). |
| 48000 | `check.rs` (APP) | none (contracts/application.md "Headless start") | test format | `guitarfx --check` builds the chain at 48 kHz on one second of silence. |
| 1.5, 2.5, 3, 4 | `resample.rs` (APP) | `CRATE/src/resampler.rs` `hermite`, `[f32; 4]` | mathematics | The four-point Hermite interpolation and its coefficients. |

The output's clamp at ±1 (`OutputStage.process`) and the volume's default 0.7 (`OutputStage`, `MonitorEngine.guitarVolume`) are not inventory values. The first is the format's full scale (allow list). The second is the default of the `volume` setting of contracts/settings-files.md, in `SET`, outside the scan.

**Reported latency (plugin, T053)**: derived, with no literal. Each half-band filter delays by `(TAPS − 1) / 2` samples at its own rate. The plugin reports the delay of the amp's oversampling at factor 4, which is always present: `(31 / 2 + 31 / 4) × 2 = 46.5` samples, rounded down to 46, computed from `TAPS` in `oversampler.rs`. The drives' oversamplers are not reported, because a host cannot follow a latency that changes with the chain.

## Knob ranges (spec 022, fixed; `DSP/src/knobs.rs`)

The knobs keep spec 022's ranges, units and defaults (data-model "Knob", `EffectKind`, `AmpSettings.defaults()`). They are what the guitarist turns, shared with TuxGuitarAdvanced's sound files, so they are not internal values.

| Kind | Knob | Min | Max | Default | Unit |
|---|---|---|---|---|---|
| gate | threshold | -90 | -20 | -60 | dB |
| gate | release | 20 | 1000 | 150 | ms |
| compressor | sustain | 0 | 10 | 5 | — |
| compressor | attack | 1 | 100 | 10 | ms |
| compressor | level | 0 | 10 | 5 | — |
| overdrive | drive | 0 | 10 | 5 | — |
| overdrive | tone | 0 | 10 | 5 | — |
| overdrive | level | 0 | 10 | 5 | — |
| distortion | gain | 0 | 10 | 6 | — |
| distortion | tone | 0 | 10 | 5 | — |
| distortion | level | 0 | 10 | 5 | — |
| chorus | rate | 0.1 | 5 | 0.8 | Hz |
| chorus | depth | 0 | 10 | 5 | — |
| chorus | mix | 0 | 100 | 50 | % |
| echo | time | 50 | 1500 | 380 | ms |
| echo | repeats | 0 | 10 | 3 | — |
| echo | tone | 0 | 10 | 5 | — |
| echo | mix | 0 | 100 | 30 | % |
| reverb | size | 0 | 10 | 5 | — |
| reverb | damping | 0 | 10 | 5 | — |
| reverb | mix | 0 | 100 | 25 | % |
| amp | gain, bass, middle, treble, volume | 0 | 10 | 5 | — |
