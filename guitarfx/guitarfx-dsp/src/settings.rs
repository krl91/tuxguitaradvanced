//! The chain the guitarist sets, ported from spec 022's `PedalSettings`, `AmpSettings` and `ChainSettings`. Unlike Java's lists, a
//! [`ChainParams`] is plain numbers with no heap, so it can be posted to the audio thread and copied there (spec 027, data-model "Chain",
//! research R10).

use crate::knobs::{self, Character, Kind, Section, KINDS, MAX_KNOBS};

/// One pedal: its kind, its switch and its knob values, each in its range.
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct PedalSettings {
    pub kind: Kind,
    pub enabled: bool,
    values: [f64; MAX_KNOBS],
}

impl PedalSettings {
    /// The pedal switched on with every knob at its default.
    pub fn defaults(kind: Kind) -> Self {
        let mut values = [0.0; MAX_KNOBS];
        for (value, knob) in values.iter_mut().zip(kind.knobs()) {
            *value = knob.default;
        }
        Self { kind, enabled: true, values }
    }

    /// The values in the order of [`Kind::knobs`], each brought back into its range. Missing values take the knob's default.
    pub fn of(kind: Kind, enabled: bool, values: &[f64]) -> Self {
        let mut pedal = Self::defaults(kind);
        pedal.enabled = enabled;
        for (i, knob) in kind.knobs().iter().enumerate() {
            if let Some(value) = values.get(i) {
                pedal.values[i] = knob.clamp(*value);
            }
        }
        pedal
    }

    /// The value of the knob at that position of [`Kind::knobs`].
    pub fn value(&self, index: usize) -> f64 {
        self.values[index]
    }

    /// The knob values, as many as the kind has.
    pub fn values(&self) -> &[f64] {
        &self.values[..self.kind.knobs().len()]
    }

    /// A copy with that knob changed, brought back into its range.
    pub fn with(mut self, index: usize, value: f64) -> Self {
        self.values[index] = self.kind.knobs()[index].clamp(value);
        self
    }

    pub fn with_enabled(mut self, enabled: bool) -> Self {
        self.enabled = enabled;
        self
    }
}

/// The amp and its cabinet; every knob goes from 0 to 10.
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct AmpSettings {
    pub character: Character,
    pub gain: f64,
    pub bass: f64,
    pub middle: f64,
    pub treble: f64,
    pub volume: f64,
    pub cabinet: bool,
}

fn clamp_amp(value: f64) -> f64 {
    if value.is_nan() {
        return knobs::AMP_MIN;
    }
    knobs::AMP_MIN.max(knobs::AMP_MAX.min(value))
}

impl Default for AmpSettings {
    /// A clean amp, every knob at 5, the cabinet on.
    fn default() -> Self {
        let d = knobs::AMP_DEFAULT;
        Self::new(Character::Clean, d, d, d, d, d, true)
    }
}

impl AmpSettings {
    pub fn new(character: Character, gain: f64, bass: f64, middle: f64, treble: f64, volume: f64, cabinet: bool) -> Self {
        Self {
            character,
            gain: clamp_amp(gain),
            bass: clamp_amp(bass),
            middle: clamp_amp(middle),
            treble: clamp_amp(treble),
            volume: clamp_amp(volume),
            cabinet,
        }
    }

    /// The five knobs in the chain text's order.
    pub fn knobs(&self) -> [f64; knobs::AMP_KNOBS.len()] {
        [self.gain, self.bass, self.middle, self.treble, self.volume]
    }

    /// A copy with the knob at that position of [`knobs::AMP_KNOBS`] changed.
    pub fn with_knob(self, index: usize, value: f64) -> Self {
        let mut knobs = self.knobs();
        knobs[index] = value;
        let [gain, bass, middle, treble, volume] = knobs;
        Self::new(self.character, gain, bass, middle, treble, volume, self.cabinet)
    }
}

/// The ordered pedals of one section.
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct Order {
    kinds: [Kind; KINDS],
    count: usize,
}

impl Order {
    pub const EMPTY: Order = Order {
        kinds: [Kind::Gate; KINDS],
        count: 0,
    };

    pub fn kinds(&self) -> &[Kind] {
        &self.kinds[..self.count]
    }

    fn push(&mut self, kind: Kind) {
        self.kinds[self.count] = kind;
        self.count += 1;
    }
}

/// The chain: the pedals before the amp, the amp with its cabinet, the pedals after it. Each kind at most once, and only in its section.
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct ChainParams {
    pedals: [Option<PedalSettings>; KINDS],
    before: Order,
    after: Order,
    pub amp: AmpSettings,
}

impl ChainParams {
    /// None when a kind is there twice or sits in the other section.
    pub fn new(before: &[PedalSettings], amp: AmpSettings, after: &[PedalSettings]) -> Option<Self> {
        let mut chain = Self {
            pedals: [None; KINDS],
            before: Order::EMPTY,
            after: Order::EMPTY,
            amp,
        };
        for (pedals, section) in [(before, Section::Before), (after, Section::After)] {
            for pedal in pedals {
                if pedal.kind.section() != section || chain.pedals[pedal.kind as usize].is_some() {
                    return None;
                }
                chain.pedals[pedal.kind as usize] = Some(*pedal);
                match section {
                    Section::Before => chain.before.push(pedal.kind),
                    Section::After => chain.after.push(pedal.kind),
                }
            }
        }
        Some(chain)
    }

    /// The pedal of that kind, or none when it is not in the chain.
    pub fn pedal(&self, kind: Kind) -> Option<&PedalSettings> {
        self.pedals[kind as usize].as_ref()
    }

    pub fn before(&self) -> &[Kind] {
        self.before.kinds()
    }

    pub fn after(&self) -> &[Kind] {
        self.after.kinds()
    }

    /// The pedals before the amp, in order.
    pub fn before_pedals(&self) -> impl Iterator<Item = &PedalSettings> {
        self.before().iter().filter_map(|kind| self.pedal(*kind))
    }

    /// The pedals after the amp, in order.
    pub fn after_pedals(&self) -> impl Iterator<Item = &PedalSettings> {
        self.after().iter().filter_map(|kind| self.pedal(*kind))
    }

    /// A copy with that pedal's settings replaced (it stays where it is), when the pedal is in the chain.
    pub fn with_pedal(mut self, pedal: PedalSettings) -> Self {
        if self.pedals[pedal.kind as usize].is_some() {
            self.pedals[pedal.kind as usize] = Some(pedal);
        }
        self
    }

    pub fn with_amp(mut self, amp: AmpSettings) -> Self {
        self.amp = amp;
        self
    }
}
