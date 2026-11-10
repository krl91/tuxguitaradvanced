//! The compressor, ported from spec 022's `Compressor.java`: a feed-forward RMS detector, a soft knee and make-up gain. `sustain` sets
//! the amount; the gain follows the target at the `attack` time going down and at the release time going up, and the make-up is part of
//! that gain, so a change of sustain glides instead of jumping. `level` is the output, 5 being unity.

use crate::knobs::index::compressor;
use crate::params::{Id, Params};
use crate::pedals::{self, flush};
use crate::settings::PedalSettings;

/// Keeps the logarithm of silence finite.
const SILENCE: f64 = 1e-12;

pub struct Compressor {
    sample_rate: f32,
    knee_db: f64,
    ratio_step: f64,
    threshold_base: f64,
    threshold_step: f64,
    makeup: f64,
    level_step: f64,
    rms_coefficient: f64,
    mean_square: f64,
    threshold_db: f64,
    ratio: f64,
    makeup_db: f64,
    attack_coefficient: f64,
    release_coefficient: f64,
    gain_db: f64,
    level: f64,
    target_level: f64,
    first: bool,
}

impl Compressor {
    pub fn new(sample_rate: f32, params: &Params) -> Self {
        let rate = f64::from(sample_rate);
        let release_coefficient = 1.0 - (-1.0 / (params.get(Id::CompressorRelease) * rate)).exp();
        Self {
            sample_rate,
            knee_db: params.get(Id::CompressorKnee),
            ratio_step: params.get(Id::CompressorRatioStep),
            threshold_base: params.get(Id::CompressorThreshold),
            threshold_step: params.get(Id::CompressorThresholdStep),
            makeup: params.get(Id::CompressorMakeup),
            level_step: params.get(Id::PedalsLevelStep),
            rms_coefficient: 1.0 - (-1.0 / (params.get(Id::CompressorRms) * rate)).exp(),
            mean_square: 0.0,
            threshold_db: params.get(Id::CompressorThreshold),
            ratio: 1.0,
            makeup_db: 0.0,
            attack_coefficient: release_coefficient,
            release_coefficient,
            gain_db: 0.0,
            level: 1.0,
            target_level: 1.0,
            first: true,
        }
    }

    pub fn set(&mut self, settings: &PedalSettings) {
        let sustain = settings.value(compressor::SUSTAIN);
        self.ratio = 1.0 + self.ratio_step * sustain;
        self.threshold_db = self.threshold_base + self.threshold_step * sustain;
        self.makeup_db = -self.threshold_db * (1.0 - 1.0 / self.ratio) * self.makeup;
        self.attack_coefficient = 1.0 - (-1.0 / (settings.value(compressor::ATTACK) / 1000.0 * f64::from(self.sample_rate))).exp();
        self.target_level = pedals::level(settings.value(compressor::LEVEL), self.level_step);
        if self.first {
            self.first = false;
            self.level = self.target_level;
        }
    }

    fn reduction_db(&self, level_db: f64) -> f64 {
        let over = level_db - self.threshold_db;
        let slope = 1.0 - 1.0 / self.ratio;
        if over <= -self.knee_db / 2.0 {
            return 0.0;
        }
        if over >= self.knee_db / 2.0 {
            return -over * slope;
        }
        let in_knee = over + self.knee_db / 2.0;
        -slope * in_knee * in_knee / (2.0 * self.knee_db)
    }

    pub fn process(&mut self, buffer: &mut [f32]) {
        let from = self.level;
        let n = buffer.len() as f64;
        for (i, sample) in buffer.iter_mut().enumerate() {
            let x = f64::from(*sample);
            self.mean_square = flush(self.mean_square + (x * x - self.mean_square) * self.rms_coefficient);
            let level_db = 10.0 * (self.mean_square + SILENCE).log10();
            let target = self.reduction_db(level_db) + self.makeup_db;
            let coefficient = if target < self.gain_db {
                self.attack_coefficient
            } else {
                self.release_coefficient
            };
            self.gain_db += (target - self.gain_db) * coefficient;
            let t = (i + 1) as f64 / n;
            *sample = (x * 10f64.powf(self.gain_db / 20.0) * (from + (self.target_level - from) * t)) as f32;
        }
        self.level = self.target_level;
    }

    pub fn reset(&mut self) {
        self.mean_square = 0.0;
        self.gain_db = 0.0;
    }
}
