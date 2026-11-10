//! The pedal of each kind, and the helpers they share, ported from spec 022's `Pedals.java` and `Pedal.java`.

use crate::chorus::Chorus;
use crate::compressor::Compressor;
use crate::distortion::Distortion;
use crate::echo::Echo;
use crate::gate::NoiseGate;
use crate::knobs::{Kind, CENTRE};
use crate::overdrive::Overdrive;
use crate::params::Params;
use crate::reverb::Reverb;
use crate::settings::PedalSettings;
use std::f64::consts::PI;

/// Under this a state is set to zero, so that no denormal number slows the loop down.
const FLUSH: f64 = 1e-20;
/// A one-pole filter's highest cut-off as a share of the rate, under Nyquist.
const ONE_POLE_HIGHEST: f64 = 0.45;

/// A pedal's level knob, 0 to 10, as a gain: 5 is unity, each step `level_step` dB (`guitarfx.pedals.level-step`).
pub fn level(knob: f64, level_step: f64) -> f64 {
    10f64.powf((knob - CENTRE) * level_step / 20.0)
}

/// A one-pole low-pass coefficient for that cut-off, kept under the Nyquist frequency.
pub fn one_pole(frequency: f64, sample_rate: f32) -> f64 {
    let rate = f64::from(sample_rate);
    1.0 - (-2.0 * PI * frequency.min(ONE_POLE_HIGHEST * rate) / rate).exp()
}

/// Zero under 1e-20.
pub fn flush(value: f64) -> f64 {
    if value.abs() < FLUSH {
        0.0
    } else {
        value
    }
}

/// A pedal of the chain. `set` only stores targets (real-time safe, no allocation); `process` works in place. The chain makes one of each
/// kind once, so the variants' sizes do not matter, and boxing them would only add an allocation.
#[allow(clippy::large_enum_variant)]
pub enum Pedal {
    Gate(NoiseGate),
    Compressor(Compressor),
    Overdrive(Overdrive),
    Distortion(Distortion),
    Chorus(Chorus),
    Echo(Echo),
    Reverb(Reverb),
}

impl Pedal {
    /// The pedal of that kind, with every buffer made for that rate and largest block.
    pub fn new(kind: Kind, sample_rate: f32, max_block: usize, params: &Params) -> Self {
        match kind {
            Kind::Gate => Pedal::Gate(NoiseGate::new(sample_rate, params)),
            Kind::Compressor => Pedal::Compressor(Compressor::new(sample_rate, params)),
            Kind::Overdrive => Pedal::Overdrive(Overdrive::new(sample_rate, max_block, params)),
            Kind::Distortion => Pedal::Distortion(Distortion::new(sample_rate, max_block, params)),
            Kind::Chorus => Pedal::Chorus(Chorus::new(sample_rate, max_block, params)),
            Kind::Echo => Pedal::Echo(Echo::new(sample_rate, max_block, params)),
            Kind::Reverb => Pedal::Reverb(Reverb::new(sample_rate, params)),
        }
    }

    pub fn set(&mut self, settings: &PedalSettings) {
        match self {
            Pedal::Gate(p) => p.set(settings),
            Pedal::Compressor(p) => p.set(settings),
            Pedal::Overdrive(p) => p.set(settings),
            Pedal::Distortion(p) => p.set(settings),
            Pedal::Chorus(p) => p.set(settings),
            Pedal::Echo(p) => p.set(settings),
            Pedal::Reverb(p) => p.set(settings),
        }
    }

    pub fn process(&mut self, buffer: &mut [f32]) {
        match self {
            Pedal::Gate(p) => p.process(buffer),
            Pedal::Compressor(p) => p.process(buffer),
            Pedal::Overdrive(p) => p.process(buffer),
            Pedal::Distortion(p) => p.process(buffer),
            Pedal::Chorus(p) => p.process(buffer),
            Pedal::Echo(p) => p.process(buffer),
            Pedal::Reverb(p) => p.process(buffer),
        }
    }

    /// Clears delay lines and envelopes.
    pub fn reset(&mut self) {
        match self {
            Pedal::Gate(p) => p.reset(),
            Pedal::Compressor(p) => p.reset(),
            Pedal::Overdrive(p) => p.reset(),
            Pedal::Distortion(p) => p.reset(),
            Pedal::Chorus(p) => p.reset(),
            Pedal::Echo(p) => p.reset(),
            Pedal::Reverb(p) => p.reset(),
        }
    }
}
