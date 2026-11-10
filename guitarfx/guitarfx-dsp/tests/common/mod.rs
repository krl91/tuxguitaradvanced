//! Helpers shared by the DSP's tests.
#![allow(dead_code)]

use guitarfx_dsp::knobs::{Kind, Section};
use guitarfx_dsp::settings::{AmpSettings, ChainParams, PedalSettings};

pub const RATE: f32 = 48000.0;
pub const BLOCK: usize = 128;

/// A small deterministic generator (xorshift), uniform in −1…1.
pub struct Noise(u64);

impl Noise {
    pub fn new(seed: u64) -> Self {
        Self(seed.max(1))
    }

    pub fn next(&mut self) -> f64 {
        self.0 ^= self.0 << 13;
        self.0 ^= self.0 >> 7;
        self.0 ^= self.0 << 17;
        (self.0 >> 11) as f64 / (1u64 << 53) as f64 * 2.0 - 1.0
    }
}

pub fn db(level: f64) -> f64 {
    10f64.powf(level / 20.0)
}

pub fn pedals(section: Section) -> Vec<PedalSettings> {
    Kind::ALL
        .iter()
        .filter(|k| k.section() == section)
        .map(|k| PedalSettings::defaults(*k))
        .collect()
}

/// Every pedal on at its defaults.
pub fn full() -> ChainParams {
    ChainParams::new(&pedals(Section::Before), AmpSettings::default(), &pedals(Section::After)).unwrap()
}

/// The chain's pedals of one section, in order.
pub fn section(chain: &ChainParams, section: Section) -> Vec<PedalSettings> {
    match section {
        Section::Before => chain.before_pedals().copied().collect(),
        Section::After => chain.after_pedals().copied().collect(),
    }
}

pub fn rebuilt(chain: &ChainParams, before: Vec<PedalSettings>, after: Vec<PedalSettings>) -> ChainParams {
    ChainParams::new(&before, chain.amp, &after).unwrap()
}

pub fn without(chain: &ChainParams, kind: Kind) -> ChainParams {
    let keep = |p: &PedalSettings| p.kind != kind;
    rebuilt(
        chain,
        section(chain, Section::Before).into_iter().filter(keep).collect(),
        section(chain, Section::After).into_iter().filter(keep).collect(),
    )
}
