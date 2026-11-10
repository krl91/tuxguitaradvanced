//! The echo, ported from spec 022's `Echo.java`: a line made once for the longest `time`, read with linear interpolation at `time`,
//! through a one-pole low-pass set by `tone`, fed back by `repeats`, the tail going on after the input stops. `mix` is the share of the
//! repeats (0 is the dry signal exactly). A new time crossfades from the old reading point to the new one rather than sliding, which would
//! bend the pitch; repeats and mix ramp over a block.

use crate::knobs::echo_longest_seconds;
use crate::knobs::index::echo;
use crate::params::{Id, Params};
use crate::pedals::{flush, one_pole};
use crate::settings::PedalSettings;

/// A margin for the interpolated read.
const MARGIN: usize = 4;

pub struct Echo {
    sample_rate: f32,
    repeats_step: f64,
    tone_base: f64,
    tone_step: f64,
    line: Vec<f32>,
    write: usize,
    time: f64,
    next_time: f64,
    pending_time: f64,
    fade: f64,
    fade_step: f64,
    low_pass: f64,
    low_pass_coefficient: f64,
    feedback: f64,
    target_feedback: f64,
    mix: f64,
    target_mix: f64,
    first: bool,
    opening: f64,
    opening_step: f64,
}

impl Echo {
    pub fn new(sample_rate: f32, max_block: usize, params: &Params) -> Self {
        let rate = f64::from(sample_rate);
        let fade_step = 1.0 / (params.get(Id::SmoothEchoTime) * rate);
        Self {
            sample_rate,
            repeats_step: params.get(Id::EchoRepeatsStep),
            tone_base: params.get(Id::EchoTone),
            tone_step: params.get(Id::EchoToneStep),
            line: vec![0.0; (echo_longest_seconds() * rate).ceil() as usize + max_block + MARGIN],
            write: 0,
            time: 0.0,
            next_time: 0.0,
            pending_time: -1.0,
            fade: 1.0,
            fade_step,
            low_pass: 0.0,
            low_pass_coefficient: 1.0,
            feedback: 0.0,
            target_feedback: 0.0,
            mix: 0.0,
            target_mix: 0.0,
            first: true,
            opening: 1.0,
            opening_step: fade_step,
        }
    }

    /// After a reset the input enters the line gradually, so that its first repeat does not start with a click.
    fn opening(&mut self) -> f64 {
        if self.opening < 1.0 {
            self.opening = 1f64.min(self.opening + self.opening_step);
        }
        self.opening
    }

    pub fn set(&mut self, settings: &PedalSettings) {
        let samples = settings.value(echo::TIME) / 1000.0 * f64::from(self.sample_rate);
        self.target_feedback = self.repeats_step * settings.value(echo::REPEATS);
        self.target_mix = settings.value(echo::MIX) / 100.0;
        self.low_pass_coefficient = one_pole(self.tone_base * 2f64.powf(settings.value(echo::TONE) * self.tone_step), self.sample_rate);
        if self.first {
            self.first = false;
            self.time = samples;
            self.next_time = samples;
            self.feedback = self.target_feedback;
            self.mix = self.target_mix;
        } else if self.fade >= 1.0 {
            if samples != self.time {
                self.next_time = samples;
                self.fade = 0.0;
            }
            self.pending_time = -1.0;
        } else {
            self.pending_time = samples;
        }
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
        let from_feedback = self.feedback;
        let from_mix = self.mix;
        for (i, sample) in buffer.iter_mut().enumerate() {
            let t = (i + 1) as f64 / n;
            let delayed;
            if self.fade < 1.0 {
                self.fade = 1f64.min(self.fade + self.fade_step);
                delayed = self.read(self.time) * (1.0 - self.fade) + self.read(self.next_time) * self.fade;
                if self.fade >= 1.0 {
                    self.time = self.next_time;
                    if self.pending_time >= 0.0 && self.pending_time != self.time {
                        self.next_time = self.pending_time;
                        self.fade = 0.0;
                    }
                    self.pending_time = -1.0;
                }
            } else {
                delayed = self.read(self.time);
            }
            self.low_pass = flush(self.low_pass + (delayed - self.low_pass) * self.low_pass_coefficient);
            let x = f64::from(*sample);
            let feedback_now = from_feedback + (self.target_feedback - from_feedback) * t;
            let opening = self.opening();
            self.line[self.write] = (x * opening + feedback_now * self.low_pass) as f32;
            self.write = (self.write + 1) % self.line.len();
            let mix_now = from_mix + (self.target_mix - from_mix) * t;
            *sample = (x * (1.0 - mix_now) + self.low_pass * mix_now) as f32;
        }
        self.feedback = self.target_feedback;
        self.mix = self.target_mix;
    }

    pub fn reset(&mut self) {
        self.line.fill(0.0);
        self.low_pass = 0.0;
        self.opening = 0.0;
    }
}
