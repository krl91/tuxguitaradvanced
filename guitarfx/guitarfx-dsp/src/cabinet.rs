//! The speaker cabinet, ported from spec 022's `Cabinet.java`: a curve, not a recorded impulse response. Two high-passes, a low resonance,
//! a presence bump and two low-passes. Switching it on or off crossfades; off from the start, it is an exact pass-through.

use crate::biquad::{butterworth, Biquad};
use crate::params::{Id, Params};

pub struct Cabinet {
    filters: Vec<Biquad>,
    wet: Vec<f32>,
    enabled: bool,
    mix: f64,
    mix_step: f64,
    started: bool,
}

impl Cabinet {
    pub fn new(sample_rate: f32, max_block: usize, params: &Params) -> Self {
        let filter = || Biquad::with_params(sample_rate, max_block, params);
        let mut low_cut = filter();
        low_cut.set_high_pass(params.get(Id::CabinetLowCut), butterworth());
        let mut low_peak = filter();
        low_peak.set_peak(
            params.get(Id::CabinetLowPeak),
            params.get(Id::CabinetLowPeakQ),
            params.get(Id::CabinetLowPeakGain),
        );
        let mut high_peak = filter();
        high_peak.set_peak(
            params.get(Id::CabinetHighPeak),
            params.get(Id::CabinetHighPeakQ),
            params.get(Id::CabinetHighPeakGain),
        );
        let mut high_cut = filter();
        high_cut.set_low_pass(params.get(Id::CabinetHighCut), butterworth());
        Self {
            filters: vec![low_cut.clone(), low_cut, low_peak, high_peak, high_cut.clone(), high_cut],
            wet: vec![0.0; max_block],
            enabled: true,
            mix: 1.0,
            mix_step: 1.0 / (params.get(Id::SmoothCabinet) * f64::from(sample_rate)),
            started: false,
        }
    }

    /// Switched on or off with a crossfade; before any sound it is set at once.
    pub fn set_enabled(&mut self, value: bool) {
        self.enabled = value;
        if !self.started {
            self.mix = if value { 1.0 } else { 0.0 };
        }
    }

    /// True while switching on or off is still crossfading.
    pub fn changing(&self) -> bool {
        self.mix != if self.enabled { 1.0 } else { 0.0 }
    }

    pub fn process(&mut self, buffer: &mut [f32]) {
        self.started = true;
        let target = if self.enabled { 1.0 } else { 0.0 };
        if self.mix == 0.0 && target == 0.0 {
            return;
        }
        for (wet, x) in self.wet.iter_mut().zip(buffer.iter()) {
            let mut y = f64::from(*x);
            for filter in &mut self.filters {
                y = filter.tick(y);
            }
            *wet = y as f32;
        }
        for (sample, wet) in buffer.iter_mut().zip(&self.wet) {
            if self.mix < target {
                self.mix = target.min(self.mix + self.mix_step);
            } else if self.mix > target {
                self.mix = target.max(self.mix - self.mix_step);
            }
            *sample = (self.mix * f64::from(*wet) + (1.0 - self.mix) * f64::from(*sample)) as f32;
        }
    }

    pub fn reset(&mut self) {
        self.started = false;
        for filter in &mut self.filters {
            filter.reset();
        }
    }
}
