//! Runs the elements of the chain in order: the pedals before the amp, the amp, the cabinet, the pedals after it, ported from spec 022's
//! `Chain.java`. With no settings the chain is dry. Every element is made once, one pedal per kind (research R10), so applying settings
//! never allocates:
//! - a pedal switched on or off, added or removed crossfades with its dry signal;
//! - a knob ramps inside its pedal;
//! - a change of order among the pedals that sound dips the chain's output (down, the new order, up), as the amp does for a change of
//!   character: one instance per kind cannot run the old and the new order side by side.
//!
//! The first settings, and the first after a dry chain, apply at once. When the computer cannot keep up, the chain is made lighter step by
//! step ([`Chain::lighten`]): the reverb off, the chorus off, the amp's oversampling at 2× then 1×, then every pedal bypassed; new settings
//! keep it.

use crate::amp::Amp;
use crate::cabinet::Cabinet;
use crate::knobs::{Kind, KINDS};
use crate::params::{Id, Params};
use crate::pedals::Pedal;
use crate::settings::ChainParams;
use std::f64::consts::PI;

/// The lightening steps, in spec 022's order. They are an order, not a level.
const REVERB_OFF: usize = 1;
const CHORUS_OFF: usize = 2;
const AMP_AT_TWO: usize = 3;
const AMP_AT_ONE: usize = 4;
const PEDALS_OFF: usize = 5;

/// The order of one section, and the order waiting for the bottom of a dip. Arrays made once.
#[derive(Clone, Copy)]
struct Section {
    order: [Kind; KINDS],
    count: usize,
    next: [Kind; KINDS],
    next_count: usize,
    merged: [Kind; KINDS],
    merged_count: usize,
}

impl Section {
    const EMPTY: Section = Section {
        order: [Kind::Gate; KINDS],
        count: 0,
        next: [Kind::Gate; KINDS],
        next_count: 0,
        merged: [Kind::Gate; KINDS],
        merged_count: 0,
    };

    fn set_next(&mut self, kinds: &[Kind]) {
        self.next_count = 0;
        for kind in kinds {
            self.next[self.next_count] = *kind;
            self.next_count += 1;
        }
    }

    fn take(&mut self) {
        self.order[..self.merged_count].copy_from_slice(&self.merged[..self.merged_count]);
        self.count = self.merged_count;
    }

    fn install(&mut self) {
        self.order[..self.next_count].copy_from_slice(&self.next[..self.next_count]);
        self.count = self.next_count;
    }
}

fn index_of(kinds: &[Kind], kind: Kind) -> Option<usize> {
    kinds.iter().position(|k| *k == kind)
}

pub struct Chain {
    amp: Amp,
    cabinet: Cabinet,
    pedals: Vec<Pedal>,
    mix: [f64; KINDS],
    target: [f64; KINDS],
    before: Section,
    after: Section,
    dry: Vec<f32>,
    mix_step: f64,
    dip_step: f64,
    dip: f64,
    dipping: bool,
    settings: Option<ChainParams>,
    first: bool,
    lightening: usize,
}

impl Chain {
    /// Every element made and prepared for that rate and largest block.
    pub fn new(sample_rate: f32, max_block: usize, params: &Params) -> Self {
        let rate = f64::from(sample_rate);
        Self {
            amp: Amp::new(sample_rate, max_block, params),
            cabinet: Cabinet::new(sample_rate, max_block, params),
            pedals: Kind::ALL.iter().map(|kind| Pedal::new(*kind, sample_rate, max_block, params)).collect(),
            mix: [0.0; KINDS],
            target: [0.0; KINDS],
            before: Section::EMPTY,
            after: Section::EMPTY,
            dry: vec![0.0; max_block],
            mix_step: 1.0 / (params.get(Id::SmoothCrossfade) * rate),
            dip_step: 1.0 / (params.get(Id::SmoothDip) * rate),
            dip: 1.0,
            dipping: false,
            settings: None,
            first: true,
            lightening: 0,
        }
    }

    /// New settings, or none for a dry chain.
    pub fn apply(&mut self, value: Option<&ChainParams>) {
        self.settings = value.copied();
        let Some(value) = value else {
            self.first = true;
            return;
        };
        for kind in Kind::ALL {
            if let Some(pedal) = value.pedal(kind) {
                self.pedals[kind as usize].set(pedal);
            }
        }
        self.targets(value);
        self.amp.set(&value.amp);
        self.cabinet.set_enabled(value.amp.cabinet);
        self.before.set_next(value.before());
        self.after.set_next(value.after());
        if self.first {
            self.first = false;
            self.install();
            return;
        }
        let keeps_order = self.merge_before() && self.merge_after();
        if keeps_order && !self.dipping {
            self.before.take();
            self.after.take();
        } else {
            self.dipping = true;
        }
    }

    /// Each pedal's switch, unless the lightening has turned it off.
    fn targets(&mut self, value: &ChainParams) {
        for kind in Kind::ALL {
            let on = value.pedal(kind).is_some_and(|p| p.enabled) && !self.lightened_off(kind);
            self.target[kind as usize] = if on { 1.0 } else { 0.0 };
        }
    }

    fn lightened_off(&self, kind: Kind) -> bool {
        self.lightening >= PEDALS_OFF
            || (self.lightening >= REVERB_OFF && kind == Kind::Reverb)
            || (self.lightening >= CHORUS_OFF && kind == Kind::Chorus)
    }

    /// From 0 (the whole chain) to 5 (the amp and the cabinet alone); the pedals turned off crossfade out.
    pub fn lighten(&mut self, level: usize) {
        self.lightening = level;
        self.amp.set_oversampling(if level >= AMP_AT_ONE {
            1
        } else if level >= AMP_AT_TWO {
            2
        } else {
            crate::oversampler::LARGEST_FACTOR
        });
        if let Some(settings) = self.settings {
            self.targets(&settings);
        }
    }

    /// The highest lightening step.
    pub fn lightest() -> usize {
        PEDALS_OFF
    }

    /// Whether a pedal is to sound: its switch on and not turned off by the lightening.
    pub fn wanted(&self, kind: Kind) -> bool {
        self.target[kind as usize] == 1.0
    }

    pub fn oversampling(&self) -> usize {
        self.amp.oversampling()
    }

    pub fn settings(&self) -> Option<&ChainParams> {
        self.settings.as_ref()
    }

    fn merge_before(&mut self) -> bool {
        let mut section = self.before;
        let keeps = self.merge(&mut section);
        self.before = section;
        keeps
    }

    fn merge_after(&mut self) -> bool {
        let mut section = self.after;
        let keeps = self.merge(&mut section);
        self.after = section;
        keeps
    }

    /// The new order with the pedals that leave it while still sounding kept at their old places (after the pedal that came before them),
    /// so that they fade out where they are. False when the pedals that stay are in another order: then the chain dips.
    fn merge(&self, section: &mut Section) -> bool {
        section.merged_count = 0;
        for i in 0..section.next_count {
            section.merged[section.merged_count] = section.next[i];
            section.merged_count += 1;
        }
        for i in 0..section.count {
            let kind = section.order[i];
            if index_of(&section.next[..section.next_count], kind).is_some() || self.mix[kind as usize] == 0.0 {
                continue;
            }
            let mut at = 0;
            if i > 0 {
                // Java's indexOf gives -1 when the previous pedal is not there: it then goes first
                at = index_of(&section.merged[..section.merged_count], section.order[i - 1]).map_or(0, |previous| previous + 1);
            }
            section.merged.copy_within(at..section.merged_count, at + 1);
            section.merged[at] = kind;
            section.merged_count += 1;
        }
        let mut last: Option<usize> = None;
        for i in 0..section.count {
            let Some(at) = index_of(&section.merged[..section.merged_count], section.order[i]) else {
                continue;
            };
            if last.is_some_and(|l| at < l) {
                return false;
            }
            last = Some(at);
        }
        true
    }

    /// The new order at once, every pedal at its switch's position (the first settings, or the bottom of a dip).
    fn install(&mut self) {
        self.before.install();
        self.after.install();
        for k in 0..KINDS {
            if self.mix[k] == 0.0 && self.target[k] == 1.0 {
                self.pedals[k].reset();
            }
            self.mix[k] = self.target[k];
        }
    }

    /// True while a pedal is still crossfading, the order is changing or the amp or the cabinet is still switching.
    pub fn changing(&self) -> bool {
        if self.dipping || self.dip < 1.0 || self.amp.changing() || self.cabinet.changing() {
            return true;
        }
        (0..KINDS).any(|k| self.mix[k] != self.target[k])
    }

    /// In place; a block no longer than the largest block.
    pub fn process(&mut self, buffer: &mut [f32]) {
        if self.settings.is_none() {
            return;
        }
        let before = self.before;
        self.run(&before, buffer);
        self.amp.process(buffer);
        self.cabinet.process(buffer);
        let after = self.after;
        self.run(&after, buffer);
        if self.dipping || self.dip < 1.0 {
            self.dip(buffer);
        }
    }

    fn run(&mut self, section: &Section, buffer: &mut [f32]) {
        let n = buffer.len();
        for i in 0..section.count {
            let k = section.order[i] as usize;
            let mut m = self.mix[k];
            let t = self.target[k];
            if m == 0.0 && t == 0.0 {
                continue;
            }
            if m == 1.0 && t == 1.0 {
                self.pedals[k].process(buffer);
                continue;
            }
            if m == 0.0 {
                // it starts from silence: no old tail
                self.pedals[k].reset();
            }
            self.dry[..n].copy_from_slice(buffer);
            self.pedals[k].process(buffer);
            for (sample, dry) in buffer.iter_mut().zip(&self.dry) {
                m = if t > m { t.min(m + self.mix_step) } else { t.max(m - self.mix_step) };
                *sample = (f64::from(*dry) + f64::from(*sample - *dry) * m) as f32;
            }
            self.mix[k] = m;
        }
    }

    /// Down; at the bottom the rest of the block stays silent and the new order comes in; then up.
    fn dip(&mut self, buffer: &mut [f32]) {
        for sample in buffer.iter_mut() {
            if self.dipping {
                self.dip = 0f64.max(self.dip - self.dip_step);
            } else {
                self.dip = 1f64.min(self.dip + self.dip_step);
            }
            *sample = (f64::from(*sample) * (0.5 - 0.5 * (PI * self.dip).cos())) as f32;
        }
        if self.dipping && self.dip == 0.0 {
            self.dipping = false;
            self.install();
        }
    }

    pub fn reset(&mut self) {
        for pedal in &mut self.pedals {
            pedal.reset();
        }
        self.amp.reset();
        self.cabinet.reset();
    }
}
