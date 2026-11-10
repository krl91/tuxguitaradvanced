//! The reverb, ported from spec 022's `Reverb.java`: the public Schroeder–Moorer structure known as "Freeverb", rewritten. Eight comb
//! filters in parallel, each with a one-pole low-pass in its loop, then four all-pass filters in series; the lengths are the structure's
//! published ones, scaled to the sample rate. `size` sets the combs' feedback, `damping` the low-pass in their loops and `mix` the share of
//! the reverb (0 is the dry signal exactly). Every knob ramps over a block.

use crate::knobs::index::reverb;
use crate::params::{Id, Params};
use crate::pedals::flush;
use crate::settings::PedalSettings;

/// The comb lengths in samples at 44.1 kHz (the classic public-domain Freeverb tuning).
const COMBS: &[usize] = &[1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617];
/// The all-pass lengths.
const ALL_PASSES: &[usize] = &[556, 441, 341, 225];
/// The rate at which those lengths are given.
const LENGTHS_RATE: f64 = 44100.0;

pub struct Reverb {
    input_gain: f64,
    wet_gain: f64,
    feedback_base: f64,
    size_step: f64,
    damping_step: f64,
    all_pass_coefficient: f64,
    combs: Vec<Vec<f32>>,
    comb_index: Vec<usize>,
    comb_low_pass: Vec<f64>,
    all_passes: Vec<Vec<f32>>,
    all_pass_index: Vec<usize>,
    feedback: f64,
    target_feedback: f64,
    damping: f64,
    target_damping: f64,
    mix: f64,
    target_mix: f64,
    first: bool,
    opening: f64,
    opening_step: f64,
}

impl Reverb {
    pub fn new(sample_rate: f32, params: &Params) -> Self {
        let rate = f64::from(sample_rate);
        let scale = rate / LENGTHS_RATE;
        let line = |length: usize| vec![0f32; 1usize.max((length as f64 * scale).round() as usize)];
        Self {
            input_gain: params.get(Id::ReverbInput),
            wet_gain: params.get(Id::ReverbWet),
            feedback_base: params.get(Id::ReverbFeedback),
            size_step: params.get(Id::ReverbSizeStep),
            damping_step: params.get(Id::ReverbDampingStep),
            all_pass_coefficient: params.get(Id::ReverbAllPass),
            combs: COMBS.iter().map(|l| line(*l)).collect(),
            comb_index: vec![0; COMBS.len()],
            comb_low_pass: vec![0.0; COMBS.len()],
            all_passes: ALL_PASSES.iter().map(|l| line(*l)).collect(),
            all_pass_index: vec![0; ALL_PASSES.len()],
            feedback: 0.0,
            target_feedback: 0.0,
            damping: 0.0,
            target_damping: 0.0,
            mix: 0.0,
            target_mix: 0.0,
            first: true,
            opening: 1.0,
            opening_step: 1.0 / (params.get(Id::SmoothOpening) * rate),
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
        self.target_feedback = self.feedback_base + self.size_step * settings.value(reverb::SIZE);
        self.target_damping = self.damping_step * settings.value(reverb::DAMPING);
        self.target_mix = settings.value(reverb::MIX) / 100.0;
        if self.first {
            self.first = false;
            self.feedback = self.target_feedback;
            self.damping = self.target_damping;
            self.mix = self.target_mix;
        }
    }

    pub fn process(&mut self, buffer: &mut [f32]) {
        let n = buffer.len() as f64;
        let from_feedback = self.feedback;
        let from_damping = self.damping;
        let from_mix = self.mix;
        for (i, sample) in buffer.iter_mut().enumerate() {
            let t = (i + 1) as f64 / n;
            let feedback_now = from_feedback + (self.target_feedback - from_feedback) * t;
            let damping_now = from_damping + (self.target_damping - from_damping) * t;
            let mix_now = from_mix + (self.target_mix - from_mix) * t;
            let x = f64::from(*sample);
            let input = x * self.input_gain * self.opening();
            let mut sum = 0.0;
            for c in 0..self.combs.len() {
                let comb = &mut self.combs[c];
                let index = self.comb_index[c];
                let delayed = f64::from(comb[index]);
                self.comb_low_pass[c] = flush(delayed * (1.0 - damping_now) + self.comb_low_pass[c] * damping_now);
                comb[index] = flush(input + self.comb_low_pass[c] * feedback_now) as f32;
                self.comb_index[c] = if index + 1 == comb.len() { 0 } else { index + 1 };
                sum += delayed;
            }
            for a in 0..self.all_passes.len() {
                let all_pass = &mut self.all_passes[a];
                let index = self.all_pass_index[a];
                let delayed = f64::from(all_pass[index]);
                all_pass[index] = flush(sum + delayed * self.all_pass_coefficient) as f32;
                sum = delayed - sum;
                self.all_pass_index[a] = if index + 1 == all_pass.len() { 0 } else { index + 1 };
            }
            *sample = (x * (1.0 - mix_now) + sum * self.wet_gain * mix_now) as f32;
        }
        self.feedback = self.target_feedback;
        self.damping = self.target_damping;
        self.mix = self.target_mix;
    }

    pub fn reset(&mut self) {
        for (comb, low_pass) in self.combs.iter_mut().zip(&mut self.comb_low_pass) {
            comb.fill(0.0);
            *low_pass = 0.0;
        }
        for all_pass in &mut self.all_passes {
            all_pass.fill(0.0);
        }
        self.opening = 0.0;
    }
}
