//! The estimated delay shown beside *Sound on* (FR-017, data-model "Estimated delay"): the input block, the output block, the latencies the
//! two devices declare (`latency::declared`) and the resampler's target fill. Each part is counted at its own device's rate (the input's
//! frames at the input's, the output's at the output's), which is the output's rate when the two are the same. It is an estimate: the
//! real delay is measured by `guitarfx-probe`, and Bluetooth headphones add their own.

use std::time::Duration;

/// The display's steps in a millisecond (a tenth each).
const STEPS: f64 = 10.0;

#[derive(Clone, Copy, Debug, PartialEq)]
pub struct Parts {
    pub input_rate: f32,
    pub output_rate: f32,
    /// Frames of one input callback.
    pub input_block: u32,
    /// Frames of one output callback.
    pub output_block: u32,
    /// Frames, at the input's rate.
    pub input_declared: u32,
    /// Frames, at the output's rate.
    pub output_declared: u32,
    /// Input frames the resampler keeps in the ring; 0 when the input is not resampled.
    pub resample_fill: f64,
}

/// Milliseconds to a tenth; NaN when a rate is missing.
pub fn milliseconds(parts: &Parts) -> f64 {
    let input_rate = f64::from(parts.input_rate);
    let output_rate = f64::from(parts.output_rate);
    if !(input_rate > 0.0 && output_rate > 0.0) {
        return f64::NAN;
    }
    let input = f64::from(parts.input_block) + f64::from(parts.input_declared) + parts.resample_fill;
    let output = f64::from(parts.output_block) + f64::from(parts.output_declared);
    let ms = Duration::from_secs(1).as_millis() as f64 * (input / input_rate + output / output_rate);
    (ms * STEPS).round() / STEPS
}
