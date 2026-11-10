//! The last stage before the output, ported from spec 022's `OutputStage.java`: the guitar's volume (ramped over a block), a fade in after
//! [`OutputStage::start`], a fade out after [`OutputStage::stop`], a peak limiter holding the peaks under the ceiling, and a clamp at
//! 0 dBFS. Mono, in place.

use crate::params::{Id, Params};
use std::f64::consts::PI;

pub struct OutputStage {
    ceiling: f64,
    fade_in_step: f64,
    fade_out_step: f64,
    release: f64,
    target_volume: f64,
    volume: f64,
    fade_position: f64,
    fading_out: bool,
    fade_out_level: f64,
    limiter_gain: f64,
}

impl OutputStage {
    /// `volume` is the guitar's volume, from 0 to 1 (the `volume` setting).
    pub fn new(sample_rate: f32, params: &Params, volume: f64) -> Self {
        let rate = f64::from(sample_rate);
        let volume = 0f64.max(1f64.min(volume));
        Self {
            ceiling: 10f64.powf(params.get(Id::OutputCeiling) / 20.0),
            fade_in_step: 1.0 / (params.get(Id::OutputFadeIn) * rate),
            fade_out_step: 1.0 / (params.get(Id::OutputFadeOut) * rate),
            release: 1.0 - (-1.0 / (params.get(Id::OutputRelease) * rate)).exp(),
            target_volume: volume,
            volume,
            fade_position: 0.0,
            fading_out: false,
            fade_out_level: 0.0,
            limiter_gain: 1.0,
        }
    }

    /// The loudest a sample may be, linear.
    pub fn ceiling(&self) -> f64 {
        self.ceiling
    }

    /// From 0 to 1; reached at the end of the next block.
    pub fn set_volume(&mut self, value: f64) {
        self.target_volume = 0f64.max(1f64.min(value));
    }

    /// Starts the fade in from silence.
    pub fn start(&mut self) {
        self.fade_position = 0.0;
        self.fading_out = false;
        self.limiter_gain = 1.0;
        self.volume = self.target_volume;
    }

    /// Starts the fade out.
    pub fn stop(&mut self) {
        if !self.fading_out {
            self.fade_out_level = self.fade_in();
            self.fading_out = true;
        }
    }

    /// True once the fade in is over.
    pub fn is_faded_in(&self) -> bool {
        !self.fading_out && self.fade_position >= 1.0
    }

    /// True once the fade out reached silence.
    pub fn is_silent(&self) -> bool {
        self.fading_out && self.fade_out_level <= 0.0
    }

    fn fade_in(&self) -> f64 {
        // a raised cosine: no step at the start or at the end
        let position = 1f64.min(self.fade_position);
        0.5 - 0.5 * (PI * position).cos()
    }

    pub fn process(&mut self, buffer: &mut [f32]) {
        let n = buffer.len() as f64;
        let from = self.volume;
        let to = self.target_volume;
        for (i, sample) in buffer.iter_mut().enumerate() {
            let gain = from + (to - from) * (i + 1) as f64 / n;
            let fade;
            if self.fading_out {
                self.fade_out_level = 0f64.max(self.fade_out_level - self.fade_out_step);
                fade = self.fade_out_level;
            } else {
                self.fade_position += self.fade_in_step;
                fade = self.fade_in();
            }
            let x = f64::from(*sample) * gain * fade;
            let magnitude = x.abs();
            if magnitude * self.limiter_gain > self.ceiling {
                self.limiter_gain = self.ceiling / magnitude;
            } else {
                self.limiter_gain += (1.0 - self.limiter_gain) * self.release;
            }
            *sample = (x * self.limiter_gain).clamp(-1.0, 1.0) as f32;
        }
        self.volume = to;
    }
}
