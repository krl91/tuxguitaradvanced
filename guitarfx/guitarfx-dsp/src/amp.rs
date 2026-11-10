//! The amp, ported from spec 022's `Amp.java`: a high-pass that tightens the low end, the input gain, a soft clipping stage run
//! oversampled (one stage for clean and crunch, two for high gain, with a small offset for even harmonics), a DC blocker, a three-band tone
//! stack (bass shelf, middle peak, treble shelf), a low-pass that tames the fizz, and the master volume.
//!
//! Knob changes ramp over a block. A change of character dips the level around the switch instead of jumping.

use crate::biquad::{butterworth, Biquad};
use crate::knobs::{Character, CENTRE};
use crate::oversampler::{Oversampler, LARGEST_FACTOR};
use crate::params::{Id, Params};
use crate::settings::AmpSettings;
use std::f64::consts::PI;

/// The DC blocker removes the asymmetric shapes' offset, under the audible band.
const DC_BLOCK_HZ: f64 = 10.0;

/// One character's values (inventory `amp-clean`, `amp-crunch`, `amp-high-gain`).
struct Voicing {
    input: f64,
    input_step: f64,
    output_cut: f64,
    output_cut_step: f64,
    output: f64,
    tighten: f64,
    fizz: f64,
    bias: f64,
    offset: f64,
    second_stage: f64,
}

pub struct Amp {
    tighten: Biquad,
    dc_block: Biquad,
    bass: Biquad,
    middle: Biquad,
    treble: Biquad,
    fizz: Biquad,
    oversampler_one: Oversampler,
    oversampler_two: Oversampler,
    oversampler_four: Oversampler,
    high: Vec<f32>,
    oversampling: usize,
    clean: Voicing,
    crunch: Voicing,
    high_gain: Voicing,
    volume_unity: f64,
    volume_step: f64,
    eq_step: f64,
    bass_hz: f64,
    middle_hz: f64,
    middle_q: f64,
    treble_hz: f64,
    settings: AmpSettings,
    character: Character,
    pending_character: Option<Character>,
    input_gain: f64,
    target_input_gain: f64,
    output_gain: f64,
    target_output_gain: f64,
    change: f64,
    change_step: f64,
    first: bool,
}

fn shape(character: Character, crunch: &Voicing, high_gain: &Voicing, x: f64) -> f64 {
    match character {
        Character::Clean => x.tanh(),
        Character::Crunch => (x + crunch.bias).tanh() - crunch.offset,
        Character::HighGain => {
            let first = (x + high_gain.bias).tanh() - high_gain.offset;
            (high_gain.second_stage * first).tanh()
        }
    }
}

fn db(value: f64) -> f64 {
    10f64.powf(value / 20.0)
}

impl Amp {
    pub fn new(sample_rate: f32, max_block: usize, params: &Params) -> Self {
        let filter = || Biquad::with_params(sample_rate, max_block, params);
        let crunch_bias = params.get(Id::AmpCrunchBias);
        let high_gain_bias = params.get(Id::AmpHighGainBias);
        let mut amp = Self {
            tighten: filter(),
            dc_block: filter(),
            bass: filter(),
            middle: filter(),
            treble: filter(),
            fizz: filter(),
            oversampler_one: Oversampler::new(1, max_block),
            oversampler_two: Oversampler::new(2, max_block),
            oversampler_four: Oversampler::new(LARGEST_FACTOR, max_block),
            high: vec![0.0; max_block * LARGEST_FACTOR],
            oversampling: LARGEST_FACTOR,
            clean: Voicing {
                input: 0.0,
                input_step: params.get(Id::AmpCleanInputStep),
                output_cut: 0.0,
                output_cut_step: 0.0,
                output: 0.0,
                tighten: params.get(Id::AmpCleanTighten),
                fizz: params.get(Id::AmpCleanFizz),
                bias: 0.0,
                offset: 0.0,
                second_stage: 1.0,
            },
            crunch: Voicing {
                input: params.get(Id::AmpCrunchInput),
                input_step: params.get(Id::AmpCrunchInputStep),
                output_cut: params.get(Id::AmpCrunchOutputCut),
                output_cut_step: params.get(Id::AmpCrunchOutputCutStep),
                output: 0.0,
                tighten: params.get(Id::AmpCrunchTighten),
                fizz: params.get(Id::AmpCrunchFizz),
                bias: crunch_bias,
                offset: crunch_bias.tanh(),
                second_stage: 1.0,
            },
            high_gain: Voicing {
                input: params.get(Id::AmpHighGainInput),
                input_step: params.get(Id::AmpHighGainInputStep),
                output_cut: 0.0,
                output_cut_step: 0.0,
                output: params.get(Id::AmpHighGainOutput),
                tighten: params.get(Id::AmpHighGainTighten),
                fizz: params.get(Id::AmpHighGainFizz),
                bias: high_gain_bias,
                offset: high_gain_bias.tanh(),
                second_stage: params.get(Id::AmpHighGainSecondStage),
            },
            volume_unity: params.get(Id::AmpToneVolumeUnity),
            volume_step: params.get(Id::AmpToneVolumeStep),
            eq_step: params.get(Id::AmpToneEqStep),
            bass_hz: params.get(Id::AmpToneBass),
            middle_hz: params.get(Id::AmpToneMiddle),
            middle_q: params.get(Id::AmpToneMiddleQ),
            treble_hz: params.get(Id::AmpToneTreble),
            settings: AmpSettings::default(),
            character: Character::Clean,
            pending_character: None,
            input_gain: 1.0,
            target_input_gain: 1.0,
            output_gain: 1.0,
            target_output_gain: 1.0,
            change: 1.0,
            change_step: 1.0 / (params.get(Id::SmoothCharacter) / 2.0 * f64::from(sample_rate)),
            first: true,
        };
        amp.dc_block.set_high_pass(DC_BLOCK_HZ, butterworth());
        amp.apply(&AmpSettings::default(), true);
        amp
    }

    /// 1, 2 or 4 (4 by default; the overload lightening lowers it).
    pub fn set_oversampling(&mut self, factor: usize) {
        self.oversampling = if factor >= LARGEST_FACTOR {
            LARGEST_FACTOR
        } else if factor >= 2 {
            2
        } else {
            1
        };
    }

    pub fn oversampling(&self) -> usize {
        self.oversampling
    }

    pub fn set(&mut self, value: &AmpSettings) {
        let first = self.first;
        self.apply(value, first);
    }

    fn apply(&mut self, value: &AmpSettings, immediately: bool) {
        self.settings = *value;
        if immediately {
            self.first = false;
            self.character = value.character;
            self.pending_character = None;
            self.change = 1.0;
        } else if value.character != self.character {
            self.pending_character = Some(value.character);
        }
        let active = if self.pending_character.is_some() {
            self.character
        } else {
            value.character
        };
        self.configure(active, value, immediately);
    }

    fn configure(&mut self, active: Character, value: &AmpSettings, immediately: bool) {
        let gain = value.gain;
        let (input_db, output_db, voicing) = match active {
            Character::Clean => {
                let input_db = self.clean.input_step * gain;
                (input_db, -input_db, &self.clean)
            }
            Character::Crunch => {
                let v = &self.crunch;
                (v.input + v.input_step * gain, -(v.output_cut + v.output_cut_step * gain), v)
            }
            Character::HighGain => {
                let v = &self.high_gain;
                (v.input + v.input_step * gain, v.output, v)
            }
        };
        let (tighten_hz, fizz_hz) = (voicing.tighten, voicing.fizz);
        let volume = value.volume;
        let volume_gain = if volume <= 0.0 {
            0.0
        } else {
            db((volume - self.volume_unity) * self.volume_step)
        };
        self.target_input_gain = db(input_db);
        self.target_output_gain = db(output_db) * volume_gain;
        self.tighten.set_high_pass(tighten_hz, butterworth());
        self.fizz.set_low_pass(fizz_hz, butterworth());
        self.bass.set_low_shelf(self.bass_hz, (value.bass - CENTRE) * self.eq_step);
        self.middle
            .set_peak(self.middle_hz, self.middle_q, (value.middle - CENTRE) * self.eq_step);
        self.treble.set_high_shelf(self.treble_hz, (value.treble - CENTRE) * self.eq_step);
        if immediately {
            self.input_gain = self.target_input_gain;
            self.output_gain = self.target_output_gain;
        }
    }

    pub fn process(&mut self, buffer: &mut [f32]) {
        let n = buffer.len();
        let from_input = self.input_gain;
        let from_output = self.output_gain;
        for (i, sample) in buffer.iter_mut().enumerate() {
            let t = (i + 1) as f64 / n as f64;
            *sample = (self.tighten.tick(f64::from(*sample)) * (from_input + (self.target_input_gain - from_input) * t)) as f32;
        }
        self.input_gain = self.target_input_gain;
        let oversampler = if self.oversampling == LARGEST_FACTOR {
            &mut self.oversampler_four
        } else if self.oversampling == 2 {
            &mut self.oversampler_two
        } else {
            &mut self.oversampler_one
        };
        let count = oversampler.up(buffer, &mut self.high);
        let active = self.character;
        for value in &mut self.high[..count] {
            *value = shape(active, &self.crunch, &self.high_gain, f64::from(*value)) as f32;
        }
        oversampler.down(&self.high[..count], buffer);
        for (i, sample) in buffer.iter_mut().enumerate() {
            let mut y = self.dc_block.tick(f64::from(*sample));
            y = self.bass.tick(y);
            y = self.middle.tick(y);
            y = self.treble.tick(y);
            y = self.fizz.tick(y);
            let t = (i + 1) as f64 / n as f64;
            y *= from_output + (self.target_output_gain - from_output) * t;
            y *= self.dip();
            *sample = y as f32;
        }
        self.output_gain = self.target_output_gain;
    }

    /// True while a change of character is still being made.
    pub fn changing(&self) -> bool {
        self.pending_character.is_some() || self.change < 1.0
    }

    /// The level around a change of character: down to silence, the switch, back up.
    fn dip(&mut self) -> f64 {
        if let Some(pending) = self.pending_character {
            self.change -= self.change_step;
            if self.change <= 0.0 {
                self.change = 0.0;
                self.character = pending;
                self.pending_character = None;
                let settings = self.settings;
                self.configure(pending, &settings, false);
            }
        } else if self.change < 1.0 {
            self.change = 1f64.min(self.change + self.change_step);
        }
        0.5 - 0.5 * (PI * self.change).cos()
    }

    pub fn reset(&mut self) {
        for filter in [
            &mut self.tighten,
            &mut self.dc_block,
            &mut self.bass,
            &mut self.middle,
            &mut self.treble,
            &mut self.fizz,
        ] {
            filter.reset();
        }
        self.oversampler_one.reset();
        self.oversampler_two.reset();
        self.oversampler_four.reset();
    }
}
