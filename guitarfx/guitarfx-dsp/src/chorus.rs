//! The chorus, ported from spec 022's `Chorus.java`: two delays swept by a sine a quarter of a turn apart, read with linear interpolation.
//! `rate` is the sweep's frequency, `depth` its width and `mix` the share of the delayed voices (0 is the dry signal exactly). Depth and mix
//! ramp over a block; the sweep's phase never jumps. After a reset the line is empty: until it holds the longest delay the voices are
//! replaced by the dry signal, then they come in gradually, so that a chorus switched on in the middle of a note does not click.

use crate::knobs::index::chorus;
use crate::knobs::STEPS;
use crate::params::{Id, Params};
use crate::settings::PedalSettings;
use std::f64::consts::PI;

/// A margin for the interpolated read.
const MARGIN: usize = 4;

pub struct Chorus {
    sample_rate: f32,
    centre_seconds: f64,
    swing_step: f64,
    line: Vec<f32>,
    write: usize,
    phase: f64,
    phase_step: f64,
    swing: f64,
    target_swing: f64,
    mix: f64,
    target_mix: f64,
    delay_samples: f64,
    first: bool,
    filled: usize,
    longest: usize,
    warm: f64,
    warm_step: f64,
}

impl Chorus {
    pub fn new(sample_rate: f32, max_block: usize, params: &Params) -> Self {
        let rate = f64::from(sample_rate);
        // sized for the longest delay any value of the parameters gives, so no read falls outside it
        let widest = Params::entry(Id::ChorusCentre).max + f64::from(STEPS) * Params::entry(Id::ChorusSwingStep).max;
        let centre_seconds = params.get(Id::ChorusCentre);
        Self {
            sample_rate,
            centre_seconds,
            swing_step: params.get(Id::ChorusSwingStep),
            line: vec![0.0; (widest * rate).ceil() as usize + max_block + MARGIN],
            write: 0,
            phase: 0.0,
            phase_step: 0.0,
            swing: 0.0,
            target_swing: 0.0,
            mix: 0.0,
            target_mix: 0.0,
            delay_samples: centre_seconds * rate,
            first: true,
            filled: 0,
            longest: (params.get(Id::ChorusWarmWait) * rate).ceil() as usize + 2,
            warm: 0.0,
            warm_step: 1.0 / (params.get(Id::SmoothOpening) * rate),
        }
    }

    pub fn set(&mut self, settings: &PedalSettings) {
        let rate = f64::from(self.sample_rate);
        self.phase_step = 2.0 * PI * settings.value(chorus::RATE) / rate;
        self.target_swing = settings.value(chorus::DEPTH) * self.swing_step * rate;
        self.target_mix = settings.value(chorus::MIX) / 100.0;
        if self.first {
            self.first = false;
            self.swing = self.target_swing;
            self.mix = self.target_mix;
        }
    }

    /// The first voice's delay at the end of the last block, in milliseconds.
    pub fn delay_millis(&self) -> f64 {
        self.delay_samples * 1000.0 / f64::from(self.sample_rate)
    }

    fn read(&self, delay: f64) -> f64 {
        let length = self.line.len();
        let mut position = self.write as f64 - delay;
        while position < 0.0 {
            position += length as f64;
        }
        let index = position as usize;
        let fraction = position - index as f64;
        let a = self.line[index % length];
        let b = self.line[(index + 1) % length];
        f64::from(a) + f64::from(b - a) * fraction
    }

    pub fn process(&mut self, buffer: &mut [f32]) {
        let n = buffer.len() as f64;
        let from_swing = self.swing;
        let from_mix = self.mix;
        let centre = self.centre_seconds * f64::from(self.sample_rate);
        for (i, sample) in buffer.iter_mut().enumerate() {
            let t = (i + 1) as f64 / n;
            let swing_now = from_swing + (self.target_swing - from_swing) * t;
            let mix_now = from_mix + (self.target_mix - from_mix) * t;
            let x = f64::from(*sample);
            self.line[self.write] = x as f32;
            let first = centre + swing_now * self.phase.sin();
            let second = centre + swing_now * self.phase.cos();
            let mut wet = 0.5 * (self.read(first) + self.read(second));
            if self.warm < 1.0 {
                if self.filled < self.longest {
                    self.filled += 1;
                } else {
                    self.warm = 1f64.min(self.warm + self.warm_step);
                }
                wet = x + (wet - x) * self.warm;
            }
            self.delay_samples = first;
            self.write = (self.write + 1) % self.line.len();
            self.phase += self.phase_step;
            if self.phase > 2.0 * PI {
                self.phase -= 2.0 * PI;
            }
            *sample = (x * (1.0 - mix_now) + wet * mix_now) as f32;
        }
        self.swing = self.target_swing;
        self.mix = self.target_mix;
    }

    pub fn reset(&mut self) {
        self.line.fill(0.0);
        self.phase = 0.0;
        self.filled = 0;
        self.warm = 0.0;
    }
}
