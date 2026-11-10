//! The knobs the guitarist turns (spec 022, `EffectKind`, `Knob`, `AmpSettings`; spec 027 data-model "Knob"). They keep spec 022's
//! ranges, units and defaults: they are shared with TuxGuitarAdvanced's sound files, so they are not internal values (inventory "Knob
//! ranges").

/// One knob of a pedal.
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct Knob {
    pub key: &'static str,
    pub min: f64,
    pub max: f64,
    /// "dB", "ms", "Hz", "%" or "" for a plain 0 to 10 scale.
    pub unit: &'static str,
    pub default: f64,
}

impl Knob {
    const fn new(key: &'static str, min: f64, max: f64, unit: &'static str, default: f64) -> Self {
        Self {
            key,
            min,
            max,
            unit,
            default,
        }
    }

    /// The value brought back into the range; a value that is not a number becomes the minimum.
    pub fn clamp(&self, value: f64) -> f64 {
        if value.is_nan() {
            return self.min;
        }
        self.min.max(self.max.min(value))
    }
}

/// Where a pedal sits: before the amp or after it. A pedal moves only within its section.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Section {
    Before,
    After,
}

/// The seven pedals, in `EffectKind`'s order.
#[derive(Clone, Copy, Debug, PartialEq, Eq, PartialOrd, Ord, Hash)]
pub enum Kind {
    Gate,
    Compressor,
    Overdrive,
    Distortion,
    Chorus,
    Echo,
    Reverb,
}

/// The number of pedal kinds.
pub const KINDS: usize = Kind::Reverb as usize + 1;

/// The knobs of the kind that has the most.
pub const MAX_KNOBS: usize = ECHO.len();

/// A 0 to 10 knob's steps (the plain scale of `Knob`).
pub const STEPS: u8 = 10;

/// The middle of a 0 to 10 knob, where a level or a band is flat.
pub const CENTRE: f64 = 5.0;

const GATE: &[Knob] = &[
    Knob::new("threshold", -90.0, -20.0, "dB", -60.0),
    Knob::new("release", 20.0, 1000.0, "ms", 150.0),
];
const COMPRESSOR: &[Knob] = &[
    Knob::new("sustain", 0.0, 10.0, "", 5.0),
    Knob::new("attack", 1.0, 100.0, "ms", 10.0),
    Knob::new("level", 0.0, 10.0, "", 5.0),
];
const OVERDRIVE: &[Knob] = &[
    Knob::new("drive", 0.0, 10.0, "", 5.0),
    Knob::new("tone", 0.0, 10.0, "", 5.0),
    Knob::new("level", 0.0, 10.0, "", 5.0),
];
const DISTORTION: &[Knob] = &[
    Knob::new("gain", 0.0, 10.0, "", 6.0),
    Knob::new("tone", 0.0, 10.0, "", 5.0),
    Knob::new("level", 0.0, 10.0, "", 5.0),
];
const CHORUS: &[Knob] = &[
    Knob::new("rate", 0.1, 5.0, "Hz", 0.8),
    Knob::new("depth", 0.0, 10.0, "", 5.0),
    Knob::new("mix", 0.0, 100.0, "%", 50.0),
];
const ECHO: &[Knob] = &[
    Knob::new("time", 50.0, 1500.0, "ms", 380.0),
    Knob::new("repeats", 0.0, 10.0, "", 3.0),
    Knob::new("tone", 0.0, 10.0, "", 5.0),
    Knob::new("mix", 0.0, 100.0, "%", 30.0),
];
const REVERB: &[Knob] = &[
    Knob::new("size", 0.0, 10.0, "", 5.0),
    Knob::new("damping", 0.0, 10.0, "", 5.0),
    Knob::new("mix", 0.0, 100.0, "%", 25.0),
];

impl Kind {
    pub const ALL: [Kind; KINDS] = [
        Kind::Gate,
        Kind::Compressor,
        Kind::Overdrive,
        Kind::Distortion,
        Kind::Chorus,
        Kind::Echo,
        Kind::Reverb,
    ];

    /// The name used in the chain's text and in the text keys.
    pub fn text_name(self) -> &'static str {
        match self {
            Kind::Gate => "gate",
            Kind::Compressor => "compressor",
            Kind::Overdrive => "overdrive",
            Kind::Distortion => "distortion",
            Kind::Chorus => "chorus",
            Kind::Echo => "echo",
            Kind::Reverb => "reverb",
        }
    }

    pub fn from_text(name: &str) -> Option<Kind> {
        Kind::ALL.into_iter().find(|kind| kind.text_name() == name)
    }

    pub fn section(self) -> Section {
        match self {
            Kind::Gate | Kind::Compressor | Kind::Overdrive | Kind::Distortion => Section::Before,
            Kind::Chorus | Kind::Echo | Kind::Reverb => Section::After,
        }
    }

    pub fn knobs(self) -> &'static [Knob] {
        match self {
            Kind::Gate => GATE,
            Kind::Compressor => COMPRESSOR,
            Kind::Overdrive => OVERDRIVE,
            Kind::Distortion => DISTORTION,
            Kind::Chorus => CHORUS,
            Kind::Echo => ECHO,
            Kind::Reverb => REVERB,
        }
    }

    /// The position of that knob in [`Kind::knobs`], or none.
    pub fn knob_index(self, key: &str) -> Option<usize> {
        self.knobs().iter().position(|knob| knob.key == key)
    }
}

/// The amp's character.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Character {
    Clean,
    Crunch,
    HighGain,
}

impl Character {
    pub const ALL: [Character; 3] = [Character::Clean, Character::Crunch, Character::HighGain];

    pub fn text_name(self) -> &'static str {
        match self {
            Character::Clean => "clean",
            Character::Crunch => "crunch",
            Character::HighGain => "highgain",
        }
    }

    pub fn from_text(name: &str) -> Option<Character> {
        Character::ALL.into_iter().find(|character| character.text_name() == name)
    }
}

/// The amp's five knobs, in the chain text's order; each goes from 0 to 10.
pub const AMP_KNOBS: [&str; 5] = ["gain", "bass", "middle", "treble", "volume"];

/// The amp's knob range and default.
pub const AMP_MIN: f64 = 0.0;
pub const AMP_MAX: f64 = 10.0;
pub const AMP_DEFAULT: f64 = 5.0;

/// The positions of each pedal's knobs.
pub mod index {
    pub mod gate {
        pub const THRESHOLD: usize = 0;
        pub const RELEASE: usize = 1;
    }
    pub mod compressor {
        pub const SUSTAIN: usize = 0;
        pub const ATTACK: usize = 1;
        pub const LEVEL: usize = 2;
    }
    pub mod overdrive {
        pub const DRIVE: usize = 0;
        pub const TONE: usize = 1;
        pub const LEVEL: usize = 2;
    }
    pub mod distortion {
        pub const GAIN: usize = 0;
        pub const TONE: usize = 1;
        pub const LEVEL: usize = 2;
    }
    pub mod chorus {
        pub const RATE: usize = 0;
        pub const DEPTH: usize = 1;
        pub const MIX: usize = 2;
    }
    pub mod echo {
        pub const TIME: usize = 0;
        pub const REPEATS: usize = 1;
        pub const TONE: usize = 2;
        pub const MIX: usize = 3;
    }
    pub mod reverb {
        pub const SIZE: usize = 0;
        pub const DAMPING: usize = 1;
        pub const MIX: usize = 2;
    }
}

/// The longest echo time of its knob, in seconds (the line is sized for it).
pub fn echo_longest_seconds() -> f64 {
    ECHO[index::echo::TIME].max / 1000.0
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn the_indices_name_the_knobs() {
        assert_eq!(Kind::Gate.knob_index("release"), Some(index::gate::RELEASE));
        assert_eq!(Kind::Compressor.knob_index("level"), Some(index::compressor::LEVEL));
        assert_eq!(Kind::Overdrive.knob_index("tone"), Some(index::overdrive::TONE));
        assert_eq!(Kind::Distortion.knob_index("level"), Some(index::distortion::LEVEL));
        assert_eq!(Kind::Chorus.knob_index("mix"), Some(index::chorus::MIX));
        assert_eq!(Kind::Echo.knob_index("mix"), Some(index::echo::MIX));
        assert_eq!(Kind::Reverb.knob_index("damping"), Some(index::reverb::DAMPING));
        assert_eq!(MAX_KNOBS, 4);
    }
}
