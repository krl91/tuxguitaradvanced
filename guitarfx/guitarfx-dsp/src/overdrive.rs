//! The overdrive, ported from spec 022's `Overdrive.java`: a mid bump and the `tone` low-pass before a `tanh` soft clipping run at 2×
//! oversampling. `drive` raises the gain into the clipping; `level` is the output, 5 being unity. Gains ramp over a block and the filters
//! glide.

use crate::biquad::{butterworth, Biquad};
use crate::knobs::index::overdrive;
use crate::oversampler::Oversampler;
use crate::params::{Id, Params};
use crate::pedals;
use crate::settings::PedalSettings;

/// The tone filter's first frequency: it glides to the knob's frequency within the first block.
const FIRST_TONE_HZ: f64 = 4000.0;

pub struct Overdrive {
    bump: Biquad,
    tone: Biquad,
    oversampler: Oversampler,
    high: Vec<f32>,
    drive_step: f64,
    tone_base: f64,
    tone_step: f64,
    level_step: f64,
    drive: f64,
    target_drive: f64,
    level: f64,
    target_level: f64,
    first: bool,
}

impl Overdrive {
    pub fn new(sample_rate: f32, max_block: usize, params: &Params) -> Self {
        let mut bump = Biquad::with_params(sample_rate, max_block, params);
        let mut tone = Biquad::with_params(sample_rate, max_block, params);
        bump.set_peak(
            params.get(Id::OverdriveBump),
            params.get(Id::OverdriveBumpQ),
            params.get(Id::OverdriveBumpGain),
        );
        tone.set_low_pass(FIRST_TONE_HZ, butterworth());
        Self {
            bump,
            tone,
            oversampler: Oversampler::new(2, max_block),
            high: vec![0.0; 2 * max_block],
            drive_step: params.get(Id::OverdriveDriveStep),
            tone_base: params.get(Id::OverdriveTone),
            tone_step: params.get(Id::OverdriveToneStep),
            level_step: params.get(Id::PedalsLevelStep),
            drive: 1.0,
            target_drive: 1.0,
            level: 1.0,
            target_level: 1.0,
            first: true,
        }
    }

    pub fn set(&mut self, settings: &PedalSettings) {
        self.target_drive = 10f64.powf(settings.value(overdrive::DRIVE) * self.drive_step / 20.0);
        self.target_level = pedals::level(settings.value(overdrive::LEVEL), self.level_step);
        self.tone.set_low_pass(
            self.tone_base * 2f64.powf(settings.value(overdrive::TONE) * self.tone_step),
            butterworth(),
        );
        if self.first {
            self.first = false;
            self.drive = self.target_drive;
            self.level = self.target_level;
        }
    }

    pub fn process(&mut self, buffer: &mut [f32]) {
        let n = buffer.len();
        let from_drive = self.drive;
        let from_level = self.level;
        for (i, sample) in buffer.iter_mut().enumerate() {
            let t = (i + 1) as f64 / n as f64;
            *sample = (self.tone.tick(self.bump.tick(f64::from(*sample))) * (from_drive + (self.target_drive - from_drive) * t)) as f32;
        }
        self.drive = self.target_drive;
        let count = self.oversampler.up(buffer, &mut self.high);
        for value in &mut self.high[..count] {
            *value = f64::from(*value).tanh() as f32;
        }
        self.oversampler.down(&self.high[..count], buffer);
        for (i, sample) in buffer.iter_mut().enumerate() {
            let t = (i + 1) as f64 / n as f64;
            *sample = (f64::from(*sample) * (from_level + (self.target_level - from_level) * t)) as f32;
        }
        self.level = self.target_level;
    }

    pub fn reset(&mut self) {
        self.bump.reset();
        self.tone.reset();
        self.oversampler.reset();
    }
}
