//! The distortion, ported from spec 022's `Distortion.java`: a high-pass and a pre-emphasis, then an asymmetric hard clipping run at 4×
//! oversampling, a DC blocker and the `tone` low-pass. `gain` raises the level into the clipping; `level` is the output, 5 being unity.
//! Gains ramp over a block and the filters glide.

use crate::biquad::{butterworth, Biquad};
use crate::knobs::index::distortion;
use crate::oversampler::{Oversampler, LARGEST_FACTOR};
use crate::params::{Id, Params};
use crate::pedals;
use crate::settings::PedalSettings;

/// The tone filter's first frequency: it glides to the knob's frequency within the first block.
const FIRST_TONE_HZ: f64 = 6000.0;
/// The DC blocker removes the asymmetric clipping's offset, under the audible band.
const DC_BLOCK_HZ: f64 = 10.0;

pub struct Distortion {
    tighten: Biquad,
    emphasis: Biquad,
    dc_block: Biquad,
    tone: Biquad,
    oversampler: Oversampler,
    high: Vec<f32>,
    top: f64,
    bottom: f64,
    gain_step: f64,
    tone_base: f64,
    tone_step: f64,
    level_step: f64,
    gain: f64,
    target_gain: f64,
    level: f64,
    target_level: f64,
    first: bool,
}

impl Distortion {
    pub fn new(sample_rate: f32, max_block: usize, params: &Params) -> Self {
        let filter = || Biquad::with_params(sample_rate, max_block, params);
        let (mut tighten, mut emphasis, mut dc_block, mut tone) = (filter(), filter(), filter(), filter());
        tighten.set_high_pass(params.get(Id::DistortionTighten), butterworth());
        emphasis.set_high_shelf(params.get(Id::DistortionEmphasis), params.get(Id::DistortionEmphasisGain));
        dc_block.set_high_pass(DC_BLOCK_HZ, butterworth());
        tone.set_low_pass(FIRST_TONE_HZ, butterworth());
        Self {
            tighten,
            emphasis,
            dc_block,
            tone,
            oversampler: Oversampler::new(LARGEST_FACTOR, max_block),
            high: vec![0.0; LARGEST_FACTOR * max_block],
            top: params.get(Id::DistortionTop),
            bottom: params.get(Id::DistortionBottom),
            gain_step: params.get(Id::DistortionGainStep),
            tone_base: params.get(Id::DistortionTone),
            tone_step: params.get(Id::DistortionToneStep),
            level_step: params.get(Id::PedalsLevelStep),
            gain: 1.0,
            target_gain: 1.0,
            level: 1.0,
            target_level: 1.0,
            first: true,
        }
    }

    pub fn set(&mut self, settings: &PedalSettings) {
        self.target_gain = 10f64.powf(settings.value(distortion::GAIN) * self.gain_step / 20.0);
        self.target_level = pedals::level(settings.value(distortion::LEVEL), self.level_step);
        self.tone.set_low_pass(
            self.tone_base * 2f64.powf(settings.value(distortion::TONE) * self.tone_step),
            butterworth(),
        );
        if self.first {
            self.first = false;
            self.gain = self.target_gain;
            self.level = self.target_level;
        }
    }

    pub fn process(&mut self, buffer: &mut [f32]) {
        let n = buffer.len();
        let from_gain = self.gain;
        let from_level = self.level;
        for (i, sample) in buffer.iter_mut().enumerate() {
            let t = (i + 1) as f64 / n as f64;
            *sample = (self.emphasis.tick(self.tighten.tick(f64::from(*sample))) * (from_gain + (self.target_gain - from_gain) * t)) as f32;
        }
        self.gain = self.target_gain;
        let count = self.oversampler.up(buffer, &mut self.high);
        for value in &mut self.high[..count] {
            *value = self.bottom.max(self.top.min(f64::from(*value))) as f32;
        }
        self.oversampler.down(&self.high[..count], buffer);
        for (i, sample) in buffer.iter_mut().enumerate() {
            let t = (i + 1) as f64 / n as f64;
            *sample = (self.tone.tick(self.dc_block.tick(f64::from(*sample))) * (from_level + (self.target_level - from_level) * t)) as f32;
        }
        self.level = self.target_level;
    }

    pub fn reset(&mut self) {
        self.tighten.reset();
        self.emphasis.reset();
        self.dc_block.reset();
        self.tone.reset();
        self.oversampler.reset();
    }
}
