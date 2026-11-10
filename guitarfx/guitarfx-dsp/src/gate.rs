//! The noise gate, ported from spec 022's `NoiseGate.java`. An RMS detector opens it above the threshold and, after a hold, lets it close
//! a few dB under the threshold. Closing lowers the gain in decibels at a steady rate down to the floor, so it never clicks.

use crate::knobs::index::gate;
use crate::knobs::Kind;
use crate::params::{Id, Params};
use crate::settings::PedalSettings;

/// Under this the mean square is flushed to zero.
const FLUSH: f64 = 1e-30;

pub struct NoiseGate {
    sample_rate: f32,
    floor_db: f64,
    hysteresis_db: f64,
    release_range_db: f64,
    threshold_db: f64,
    release_millis: f64,
    mean_square: f64,
    rms_coefficient: f64,
    gain_db: f64,
    open: bool,
    hold_left: i64,
    hold_samples: i64,
    open_step: f64,
    close_step: f64,
}

impl NoiseGate {
    pub fn new(sample_rate: f32, params: &Params) -> Self {
        let rate = f64::from(sample_rate);
        let floor_db = params.get(Id::GateFloor);
        let mut gate = Self {
            sample_rate,
            floor_db,
            hysteresis_db: params.get(Id::GateHysteresis),
            release_range_db: params.get(Id::GateReleaseRange),
            threshold_db: Kind::Gate.knobs()[gate::THRESHOLD].default,
            release_millis: Kind::Gate.knobs()[gate::RELEASE].default,
            mean_square: 0.0,
            rms_coefficient: 1.0 - (-1.0 / (params.get(Id::GateRms) * rate)).exp(),
            gain_db: floor_db,
            open: false,
            hold_left: 0,
            hold_samples: (params.get(Id::GateHold) * rate).round() as i64,
            open_step: -floor_db / (params.get(Id::GateOpen) * rate),
            close_step: 0.0,
        };
        gate.update_close();
        gate
    }

    pub fn set(&mut self, settings: &PedalSettings) {
        self.threshold_db = settings.value(gate::THRESHOLD);
        self.release_millis = settings.value(gate::RELEASE);
        self.update_close();
    }

    fn update_close(&mut self) {
        self.close_step = self.release_range_db / (self.release_millis / 1000.0 * f64::from(self.sample_rate));
    }

    /// The gain applied now, linear (for the tests).
    pub fn gain(&self) -> f64 {
        10f64.powf(self.gain_db / 20.0)
    }

    pub fn process(&mut self, buffer: &mut [f32]) {
        let open_level = 10f64.powf(self.threshold_db / 10.0);
        let close_level = 10f64.powf((self.threshold_db - self.hysteresis_db) / 10.0);
        for sample in buffer.iter_mut() {
            let x = f64::from(*sample);
            self.mean_square += (x * x - self.mean_square) * self.rms_coefficient;
            if self.mean_square < FLUSH {
                self.mean_square = 0.0;
            }
            if self.mean_square > open_level {
                self.open = true;
                self.hold_left = self.hold_samples;
            } else if self.open && self.mean_square < close_level {
                if self.hold_left > 0 {
                    self.hold_left -= 1;
                } else {
                    self.open = false;
                }
            }
            if self.open {
                self.gain_db = 0f64.min(self.gain_db + self.open_step);
            } else {
                self.gain_db = self.floor_db.max(self.gain_db - self.close_step);
            }
            *sample = (x * 10f64.powf(self.gain_db / 20.0)) as f32;
        }
    }

    pub fn reset(&mut self) {
        self.mean_square = 0.0;
        self.gain_db = self.floor_db;
        self.open = false;
        self.hold_left = 0;
    }
}
