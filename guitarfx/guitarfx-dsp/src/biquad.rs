//! A second-order filter in the forms of the public "Audio EQ Cookbook" (low-pass, high-pass, peak, low and high shelf), direct form I in
//! `f64`, ported line for line from spec 022's `Biquad.java`. A new setting glides over the next block instead of jumping: the frequency
//! and the Q move geometrically and the gain in decibels, the coefficients worked out again every few samples. The very first setting, or
//! one of another shape, applies at once. Tiny values are flushed to zero so silence never leaves denormals.

use crate::params::{Id, Params};
use std::f64::consts::PI;

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
enum Shape {
    LowPass,
    HighPass,
    Peak,
    LowShelf,
    HighShelf,
}

/// Under this a value is flushed to zero.
const FLUSH: f64 = 1e-20;
/// The smallest Q: a Q of 0 divides by zero.
const SMALLEST_Q: f64 = 0.05;
/// The highest frequency as a share of the rate: at or above Nyquist the filter is unstable.
const HIGHEST: f64 = 0.49;
/// The lowest frequency.
const LOWEST_HZ: f64 = 10.0;

#[derive(Clone, Debug)]
pub struct Biquad {
    sample_rate: f32,
    ramp_length: usize,
    step: usize,
    b0: f64,
    b1: f64,
    b2: f64,
    a1: f64,
    a2: f64,
    x1: f64,
    x2: f64,
    y1: f64,
    y2: f64,
    shape: Option<Shape>,
    frequency: f64,
    q: f64,
    gain_db: f64,
    from_frequency: f64,
    from_q: f64,
    from_gain_db: f64,
    to_frequency: f64,
    to_q: f64,
    to_gain_db: f64,
    remaining: usize,
}

/// The Butterworth Q.
pub fn butterworth() -> f64 {
    0.5f64.sqrt()
}

impl Biquad {
    /// A pass-through until it is set. `step` is the samples between two designs while a setting glides.
    pub fn new(sample_rate: f32, max_block: usize, step: usize) -> Self {
        Self {
            sample_rate,
            ramp_length: max_block.max(1),
            step: step.max(1),
            b0: 1.0,
            b1: 0.0,
            b2: 0.0,
            a1: 0.0,
            a2: 0.0,
            x1: 0.0,
            x2: 0.0,
            y1: 0.0,
            y2: 0.0,
            shape: None,
            frequency: 0.0,
            q: 0.0,
            gain_db: 0.0,
            from_frequency: 0.0,
            from_q: 0.0,
            from_gain_db: 0.0,
            to_frequency: 0.0,
            to_q: 0.0,
            to_gain_db: 0.0,
            remaining: 0,
        }
    }

    /// With the glide step of the parameters.
    pub fn with_params(sample_rate: f32, max_block: usize, params: &Params) -> Self {
        Self::new(sample_rate, max_block, params.get(Id::SmoothGlideStep) as usize)
    }

    pub fn set_low_pass(&mut self, frequency: f64, q: f64) {
        self.target(Shape::LowPass, frequency, q, 0.0);
    }

    pub fn set_high_pass(&mut self, frequency: f64, q: f64) {
        self.target(Shape::HighPass, frequency, q, 0.0);
    }

    pub fn set_peak(&mut self, frequency: f64, q: f64, gain_db: f64) {
        self.target(Shape::Peak, frequency, q, gain_db);
    }

    pub fn set_low_shelf(&mut self, frequency: f64, gain_db: f64) {
        self.target(Shape::LowShelf, frequency, butterworth(), gain_db);
    }

    pub fn set_high_shelf(&mut self, frequency: f64, gain_db: f64) {
        self.target(Shape::HighShelf, frequency, butterworth(), gain_db);
    }

    fn target(&mut self, shape: Shape, frequency: f64, q: f64, gain_db: f64) {
        let limited_q = SMALLEST_Q.max(q);
        if self.shape != Some(shape) {
            // the first setting, or another kind of filter: no glide between two different shapes
            self.shape = Some(shape);
            self.frequency = frequency;
            self.q = limited_q;
            self.gain_db = gain_db;
            self.remaining = 0;
            self.design(shape, frequency, limited_q, gain_db);
            return;
        }
        self.from_frequency = self.frequency;
        self.from_q = self.q;
        self.from_gain_db = self.gain_db;
        self.to_frequency = frequency;
        self.to_q = limited_q;
        self.to_gain_db = gain_db;
        self.remaining = self.ramp_length;
    }

    fn omega(&self, value: f64) -> f64 {
        let rate = f64::from(self.sample_rate);
        let limited = LOWEST_HZ.max((HIGHEST * rate).min(value));
        2.0 * PI * limited / rate
    }

    fn design(&mut self, shape: Shape, f: f64, quality: f64, gain: f64) {
        let w = self.omega(f);
        let cos = w.cos();
        let sin = w.sin();
        let a = 10f64.powf(gain / 40.0);
        match shape {
            Shape::LowPass => {
                let alpha = sin / (2.0 * quality);
                self.coefficients((1.0 - cos) / 2.0, 1.0 - cos, (1.0 - cos) / 2.0, 1.0 + alpha, -2.0 * cos, 1.0 - alpha);
            }
            Shape::HighPass => {
                let alpha = sin / (2.0 * quality);
                self.coefficients((1.0 + cos) / 2.0, -(1.0 + cos), (1.0 + cos) / 2.0, 1.0 + alpha, -2.0 * cos, 1.0 - alpha);
            }
            Shape::Peak => {
                let alpha = sin / (2.0 * quality);
                self.coefficients(1.0 + alpha * a, -2.0 * cos, 1.0 - alpha * a, 1.0 + alpha / a, -2.0 * cos, 1.0 - alpha / a);
            }
            Shape::LowShelf => {
                let root = 2.0 * a.sqrt() * sin / 2.0 * 2f64.sqrt();
                self.coefficients(
                    a * ((a + 1.0) - (a - 1.0) * cos + root),
                    2.0 * a * ((a - 1.0) - (a + 1.0) * cos),
                    a * ((a + 1.0) - (a - 1.0) * cos - root),
                    (a + 1.0) + (a - 1.0) * cos + root,
                    -2.0 * ((a - 1.0) + (a + 1.0) * cos),
                    (a + 1.0) + (a - 1.0) * cos - root,
                );
            }
            Shape::HighShelf => {
                let root = 2.0 * a.sqrt() * sin / 2.0 * 2f64.sqrt();
                self.coefficients(
                    a * ((a + 1.0) + (a - 1.0) * cos + root),
                    -2.0 * a * ((a - 1.0) + (a + 1.0) * cos),
                    a * ((a + 1.0) + (a - 1.0) * cos - root),
                    (a + 1.0) - (a - 1.0) * cos + root,
                    2.0 * ((a - 1.0) - (a + 1.0) * cos),
                    (a + 1.0) - (a - 1.0) * cos - root,
                );
            }
        }
    }

    fn coefficients(&mut self, nb0: f64, nb1: f64, nb2: f64, na0: f64, na1: f64, na2: f64) {
        self.b0 = nb0 / na0;
        self.b1 = nb1 / na0;
        self.b2 = nb2 / na0;
        self.a1 = na1 / na0;
        self.a2 = na2 / na0;
    }

    fn glide(&mut self) {
        self.remaining -= 1;
        if self.remaining == 0 {
            self.frequency = self.to_frequency;
            self.q = self.to_q;
            self.gain_db = self.to_gain_db;
        } else if self.remaining.is_multiple_of(self.step) {
            let t = 1.0 - self.remaining as f64 / self.ramp_length as f64;
            self.frequency = self.from_frequency * (self.to_frequency / self.from_frequency).powf(t);
            self.q = self.from_q * (self.to_q / self.from_q).powf(t);
            self.gain_db = self.from_gain_db + (self.to_gain_db - self.from_gain_db) * t;
        } else {
            return;
        }
        if let Some(shape) = self.shape {
            self.design(shape, self.frequency, self.q, self.gain_db);
        }
    }

    /// In place.
    pub fn process(&mut self, buffer: &mut [f32]) {
        for sample in buffer.iter_mut() {
            *sample = self.tick(f64::from(*sample)) as f32;
        }
    }

    /// One sample, for callers that work sample by sample (the amp's stages).
    pub fn tick(&mut self, x: f64) -> f64 {
        if self.remaining > 0 {
            self.glide();
        }
        let mut y = self.b0 * x + self.b1 * self.x1 + self.b2 * self.x2 - self.a1 * self.y1 - self.a2 * self.y2;
        if y.abs() < FLUSH {
            y = 0.0;
        }
        self.x2 = self.x1;
        self.x1 = if x.abs() < FLUSH { 0.0 } else { x };
        self.y2 = self.y1;
        self.y1 = y;
        y
    }

    pub fn reset(&mut self) {
        self.x1 = 0.0;
        self.x2 = 0.0;
        self.y1 = 0.0;
        self.y2 = 0.0;
        if self.remaining > 0 {
            self.remaining = 0;
            self.frequency = self.to_frequency;
            self.q = self.to_q;
            self.gain_db = self.to_gain_db;
            if let Some(shape) = self.shape {
                self.design(shape, self.frequency, self.q, self.gain_db);
            }
        }
    }
}
