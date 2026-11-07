//! The native recognition's analysis (spec 023): the stages of the audio engine's Java analysis, ported so they give the same frames, and
//! the packed record that carries a frame to Java. Every stage is built once and then never allocates.

pub mod articulation;
pub mod attack;
pub mod chord;
pub mod comb;
pub mod fft;
pub mod frame;
pub mod front_end;
pub mod level;
pub mod pipeline;
pub mod polyphonic;
pub mod strum_clip;
pub mod target;
pub mod yin;

/// The settings sent by Java's `NativeParameters` as a `float[]`, at these indices.
pub const LEVEL_TOO_LOW: usize = 0;
pub const LEVEL_TOO_HIGH: usize = 1;
pub const LEVEL_CLIP_SAMPLE: usize = 2;
pub const LEVEL_CLIP_RATIO: usize = 3;
pub const ONSET_FLUX_FACTOR: usize = 4;
pub const ONSET_FLUX_OFFSET: usize = 5;
pub const ONSET_MIN_LEVEL: usize = 6;
pub const ONSET_REFRACTORY: usize = 7;
pub const ONSET_GATE: usize = 8;
pub const ONSET_PRE_FIRST: usize = 9;
pub const ONSET_PRE_LAST: usize = 10;
pub const ONSET_POST_FIRST: usize = 11;
pub const ONSET_POST_LAST: usize = 12;
pub const FRONT_GAIN: usize = 13;
pub const FRONT_NOISE_REDUCTION: usize = 14;
pub const FRONT_SUBTRACTION: usize = 15;
pub const STRUM_ARM_FLOOR: usize = 16;
pub const STRUM_CLIP_SAMPLE: usize = 17;
pub const STRUM_WEAK: usize = 18;
pub const STRUM_LOUD: usize = 19;
pub const YIN_THRESHOLD: usize = 20;
pub const LENGTH: usize = 21;

/// The values of one `configure`, read from the array; the doubles of Java are carried as floats (their precision is enough for these
/// thresholds, and both engines compare against the same float values in the parity tests).
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct Parameters {
    pub level_too_low_dbfs: f64,
    pub level_too_high_dbfs: f64,
    pub level_clip_sample: f64,
    pub level_clip_ratio: f64,
    pub onset_flux_factor: f64,
    pub onset_flux_offset: f64,
    pub onset_min_level_db: f64,
    pub onset_refractory_hops: i32,
    pub onset_gate_db: f64,
    pub onset_pre_first: i32,
    pub onset_pre_last: i32,
    pub onset_post_first: i32,
    pub onset_post_last: i32,
    pub front_gain: f64,
    pub front_noise_reduction: bool,
    pub front_subtraction: bool,
    pub strum_arm_floor_dbfs: f64,
    pub strum_clip_sample: f64,
    pub strum_weak_dbfs: f64,
    pub strum_loud_dbfs: f64,
    pub yin_threshold: f64,
}

impl Parameters {
    /// The values `NativeParameters.of(RecognitionSettings.defaults())` sends.
    pub fn defaults() -> Parameters {
        let mut values = [0f32; LENGTH];
        values[LEVEL_TOO_LOW] = -40.0;
        values[LEVEL_TOO_HIGH] = -6.0;
        values[LEVEL_CLIP_SAMPLE] = 0.99;
        values[LEVEL_CLIP_RATIO] = 0.10;
        values[ONSET_FLUX_FACTOR] = 1.5;
        values[ONSET_FLUX_OFFSET] = 5.0;
        values[ONSET_MIN_LEVEL] = -50.0;
        values[ONSET_REFRACTORY] = 12.0;
        values[ONSET_GATE] = 4.5;
        values[ONSET_PRE_FIRST] = 19.0;
        values[ONSET_PRE_LAST] = 2.0;
        values[ONSET_POST_FIRST] = 4.0;
        values[ONSET_POST_LAST] = 13.0;
        values[FRONT_GAIN] = 1.0;
        values[STRUM_ARM_FLOOR] = -40.0;
        values[STRUM_CLIP_SAMPLE] = 0.99;
        values[STRUM_WEAK] = -40.0;
        values[STRUM_LOUD] = -6.0;
        values[YIN_THRESHOLD] = 0.20;
        Parameters::from_slice(&values).unwrap()
    }

    /// The values at their indices, or None when the array is too short.
    pub fn from_slice(values: &[f32]) -> Option<Parameters> {
        if values.len() < LENGTH {
            return None;
        }
        let d = |i: usize| values[i] as f64;
        let n = |i: usize| values[i].round() as i32;
        Some(Parameters {
            level_too_low_dbfs: d(LEVEL_TOO_LOW),
            level_too_high_dbfs: d(LEVEL_TOO_HIGH),
            level_clip_sample: d(LEVEL_CLIP_SAMPLE),
            level_clip_ratio: d(LEVEL_CLIP_RATIO),
            onset_flux_factor: d(ONSET_FLUX_FACTOR),
            onset_flux_offset: d(ONSET_FLUX_OFFSET),
            onset_min_level_db: d(ONSET_MIN_LEVEL),
            onset_refractory_hops: n(ONSET_REFRACTORY),
            onset_gate_db: d(ONSET_GATE),
            onset_pre_first: n(ONSET_PRE_FIRST),
            onset_pre_last: n(ONSET_PRE_LAST),
            onset_post_first: n(ONSET_POST_FIRST),
            onset_post_last: n(ONSET_POST_LAST),
            front_gain: d(FRONT_GAIN),
            front_noise_reduction: values[FRONT_NOISE_REDUCTION] != 0.0,
            front_subtraction: values[FRONT_SUBTRACTION] != 0.0,
            strum_arm_floor_dbfs: d(STRUM_ARM_FLOOR),
            strum_clip_sample: d(STRUM_CLIP_SAMPLE),
            strum_weak_dbfs: d(STRUM_WEAK),
            strum_loud_dbfs: d(STRUM_LOUD),
            yin_threshold: d(YIN_THRESHOLD),
        })
    }
}

/// The length of the tuning array sent by Java's `NativeParameters.tuning` as a `double[]` (spec 026, contracts/native-parameters.md): every
/// other value a native stage reads, at the indices `T_…` that each engine family adds, the same names as in Java.
pub const T_TARGET_SEARCH: usize = 0;
pub const T_TARGET_PERIODS: usize = 1;
pub const T_POLY_CANDIDATE_LOW: usize = 2;
pub const T_POLY_CANDIDATE_HIGH: usize = 3;
pub const T_POLY_HARMONICS: usize = 4;
pub const T_POLY_MAX_HARMONIC: usize = 5;
pub const T_POLY_TOLERANCE: usize = 6;
pub const T_POLY_CANDIDATES: usize = 7;
pub const T_POLY_MIN_RELATIVE: usize = 8;
pub const T_POLY_FLOOR: usize = 9;
pub const T_POLY_SUBHARMONIC: usize = 10;
pub const T_POLY_FLOOR_WIDTH: usize = 11;
pub const T_POLY_FLOOR_FACTOR: usize = 12;
pub const T_ATTACK_LAG: usize = 13;
pub const T_ATTACK_MAX_FREQUENCY: usize = 14;
pub const T_ATTACK_PARTIALS: usize = 15;
pub const T_ATTACK_BETA: usize = 16;
pub const T_ATTACK_SIGMA: usize = 17;
pub const T_CHORD_PARTIAL_PRESENCE: usize = 18;
pub const T_CHORD_NEIGHBOUR_MARGIN: usize = 19;
pub const T_STRUM_WINDOW: usize = 20;
pub const T_STRUM_QUIET: usize = 21;
pub const T_STRUM_RISE: usize = 22;
pub const T_STRUM_CLIP_SHARE: usize = 23;
pub const T_FRONT_HIGH_PASS: usize = 24;
pub const T_FRONT_NOISE_RISE: usize = 25;
pub const T_FRONT_NOISE_FALL: usize = 26;
pub const T_FRONT_NOISE_TRUSTED: usize = 27;
pub const T_GATE_OPEN: usize = 28;
pub const T_GATE_CLOSE: usize = 29;
pub const T_GATE_REDUCTION: usize = 30;
pub const T_GATE_HOLD: usize = 31;
pub const T_SUBTRACTION: usize = 32;
pub const T_SUBTRACTION_FLOOR: usize = 33;
pub const T_SUBTRACTION_LEARN: usize = 34;
pub const TUNING_LENGTH: usize = 35;

/// What `tuningLayout()` reports to Java: a library whose layout differs is not used.
pub fn tuning_layout() -> i32 {
    TUNING_LENGTH as i32
}

/// The values of the tuning array, read from it; carried as doubles, as Java holds them.
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct Tuning {
    /// `pitch.target-search`: the cents around each expected period the verifier searches.
    pub target_search_cents: f64,
    /// `pitch.target-periods`: the periods the verifier compares.
    pub target_periods: i64,
    /// The polyphonic analyser's values, the FFT size and the bands aside (`polyphonic.*`).
    pub polyphonic: polyphonic::PolyphonicParameters,
    /// The attack analyser's values, the FFT size aside (`attack.*`).
    pub attack: attack::AttackParameters,
    /// The strum meter's window (`chord-evidence.strum-window`, sent in milliseconds), quiet hops, rise and clip share.
    pub strum_window_nanos: f64,
    pub strum_quiet_hops: usize,
    pub strum_rise_db: f64,
    pub strum_clip_share: f64,
    /// The front end's stages (`front-end.*`, `noise-gate.*`).
    pub front: front_end::FrontEndParameters,
}

impl Tuning {
    /// The values of today's stages, at their indices: what `NativeParameters.tuning(RecognitionSettings.defaults())` sends.
    pub fn default_values() -> [f64; TUNING_LENGTH] {
        let mut values = [0f64; TUNING_LENGTH];
        values[T_TARGET_SEARCH] = target::SEARCH_CENTS;
        values[T_TARGET_PERIODS] = target::PERIODS as f64;
        let p = polyphonic::PolyphonicParameters::defaults();
        values[T_POLY_CANDIDATE_LOW] = p.min_candidate_midi as f64;
        values[T_POLY_CANDIDATE_HIGH] = p.max_candidate_midi as f64;
        values[T_POLY_HARMONICS] = p.harmonics as f64;
        values[T_POLY_MAX_HARMONIC] = p.max_harmonic_hz;
        values[T_POLY_TOLERANCE] = p.harmonic_tolerance_cents;
        values[T_POLY_CANDIDATES] = p.max_candidates as f64;
        values[T_POLY_MIN_RELATIVE] = p.min_relative_salience;
        values[T_POLY_FLOOR] = p.floor_db;
        values[T_POLY_SUBHARMONIC] = p.subharmonic_ratio;
        values[T_POLY_FLOOR_WIDTH] = p.floor_half_width_hz;
        values[T_POLY_FLOOR_FACTOR] = p.floor_factor;
        let a = attack::AttackParameters::defaults();
        values[T_ATTACK_LAG] = a.lag_hops as f64;
        values[T_ATTACK_MAX_FREQUENCY] = a.max_hz;
        values[T_ATTACK_PARTIALS] = a.partials as f64;
        values[T_ATTACK_BETA] = a.beta;
        values[T_ATTACK_SIGMA] = a.sigma_bins;
        values[T_CHORD_PARTIAL_PRESENCE] = p.partial_presence_ratio;
        values[T_CHORD_NEIGHBOUR_MARGIN] = p.neighbour_margin;
        let s = strum_clip::StrumParameters::defaults();
        values[T_STRUM_WINDOW] = s.window_nanos / 1_000_000.0;
        values[T_STRUM_QUIET] = s.arm_quiet_hops as f64;
        values[T_STRUM_RISE] = s.attack_rise_db;
        values[T_STRUM_CLIP_SHARE] = s.clip_share;
        let f = front_end::FrontEndParameters::defaults();
        values[T_FRONT_HIGH_PASS] = f.high_pass_hz;
        values[T_FRONT_NOISE_RISE] = f.noise_rise_db_per_second;
        values[T_FRONT_NOISE_FALL] = f.noise_fall_rate;
        values[T_FRONT_NOISE_TRUSTED] = f.noise_trusted_hops as f64;
        values[T_GATE_OPEN] = f.gate_open_db;
        values[T_GATE_CLOSE] = f.gate_close_db;
        values[T_GATE_REDUCTION] = f.gate_reduction_db;
        values[T_GATE_HOLD] = f.gate_hold_hops as f64;
        values[T_SUBTRACTION] = f.subtraction_alpha;
        values[T_SUBTRACTION_FLOOR] = f.subtraction_beta;
        values[T_SUBTRACTION_LEARN] = f.subtraction_learn_rate;
        values
    }

    pub fn defaults() -> Tuning {
        Tuning::from_slice(&Tuning::default_values()).unwrap()
    }

    /// The values at their indices, or None when the array has another length than the layout's.
    pub fn from_slice(values: &[f64]) -> Option<Tuning> {
        if values.len() != TUNING_LENGTH {
            return None;
        }
        let polyphonic = polyphonic::PolyphonicParameters {
            min_candidate_midi: values[T_POLY_CANDIDATE_LOW].round() as i32,
            max_candidate_midi: values[T_POLY_CANDIDATE_HIGH].round() as i32,
            harmonics: values[T_POLY_HARMONICS].round() as i32,
            max_harmonic_hz: values[T_POLY_MAX_HARMONIC],
            harmonic_tolerance_cents: values[T_POLY_TOLERANCE],
            max_candidates: values[T_POLY_CANDIDATES].round() as usize,
            min_relative_salience: values[T_POLY_MIN_RELATIVE],
            floor_db: values[T_POLY_FLOOR],
            subharmonic_ratio: values[T_POLY_SUBHARMONIC],
            floor_half_width_hz: values[T_POLY_FLOOR_WIDTH],
            floor_factor: values[T_POLY_FLOOR_FACTOR],
            partial_presence_ratio: values[T_CHORD_PARTIAL_PRESENCE],
            neighbour_margin: values[T_CHORD_NEIGHBOUR_MARGIN],
            ..polyphonic::PolyphonicParameters::defaults()
        };
        let attack = attack::AttackParameters {
            lag_hops: values[T_ATTACK_LAG].round() as usize,
            max_hz: values[T_ATTACK_MAX_FREQUENCY],
            partials: values[T_ATTACK_PARTIALS].round() as i32,
            beta: values[T_ATTACK_BETA],
            sigma_bins: values[T_ATTACK_SIGMA],
            ..attack::AttackParameters::defaults()
        };
        Some(Tuning { target_search_cents: values[T_TARGET_SEARCH], target_periods: values[T_TARGET_PERIODS].round() as i64, polyphonic,
            attack,
            strum_window_nanos: values[T_STRUM_WINDOW] * 1_000_000.0,
            strum_quiet_hops: values[T_STRUM_QUIET].round() as usize,
            strum_rise_db: values[T_STRUM_RISE],
            strum_clip_share: values[T_STRUM_CLIP_SHARE],
            front: front_end::FrontEndParameters {
                high_pass_hz: values[T_FRONT_HIGH_PASS],
                noise_rise_db_per_second: values[T_FRONT_NOISE_RISE],
                noise_fall_rate: values[T_FRONT_NOISE_FALL],
                noise_trusted_hops: values[T_FRONT_NOISE_TRUSTED].round() as i64,
                gate_open_db: values[T_GATE_OPEN],
                gate_close_db: values[T_GATE_CLOSE],
                gate_reduction_db: values[T_GATE_REDUCTION],
                gate_hold_hops: values[T_GATE_HOLD].round() as i32,
                subtraction_alpha: values[T_SUBTRACTION],
                subtraction_beta: values[T_SUBTRACTION_FLOOR],
                subtraction_learn_rate: values[T_SUBTRACTION_LEARN],
            },
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn the_values_are_read_at_their_indices() {
        let mut values = [0f32; LENGTH];
        for (i, v) in values.iter_mut().enumerate() {
            *v = i as f32 + 0.5;
        }
        values[FRONT_NOISE_REDUCTION] = 1.0;
        values[FRONT_SUBTRACTION] = 0.0;
        let p = Parameters::from_slice(&values).unwrap();
        assert_eq!(p.level_too_low_dbfs, 0.5);
        assert_eq!(p.level_clip_ratio, 3.5);
        assert_eq!(p.onset_gate_db, 8.5);
        assert_eq!(p.onset_post_last, 13);
        assert_eq!(p.front_gain, 13.5);
        assert!(p.front_noise_reduction && !p.front_subtraction);
        assert_eq!(p.strum_loud_dbfs, 19.5);
        assert_eq!(p.yin_threshold, 20.5);
        assert!(Parameters::from_slice(&values[..LENGTH - 1]).is_none());
    }

    // spec 026, T006: the tuning array has exactly its layout's length, and its defaults are today's values

    #[test]
    fn a_tuning_of_another_length_is_refused() {
        let longer = vec![0f64; TUNING_LENGTH + 1];
        assert!(Tuning::from_slice(&longer).is_none());
        if TUNING_LENGTH > 0 {
            let shorter = vec![0f64; TUNING_LENGTH - 1];
            assert!(Tuning::from_slice(&shorter).is_none());
        }
        assert!(Tuning::from_slice(&Tuning::default_values()).is_some());
    }

    #[test]
    fn the_default_tuning_is_today_s_literals() {
        assert_eq!(Some(Tuning::defaults()), Tuning::from_slice(&Tuning::default_values()));
        assert_eq!(TUNING_LENGTH, Tuning::default_values().len());
    }

    #[test]
    fn the_pitch_values_are_read_at_their_indices() {
        let mut values = Tuning::default_values();
        values[T_TARGET_SEARCH] = 35.0;
        values[T_TARGET_PERIODS] = 6.0;
        let t = Tuning::from_slice(&values).unwrap();
        assert_eq!(35.0, t.target_search_cents);
        assert_eq!(6, t.target_periods);
        assert_eq!(20.0, Tuning::defaults().target_search_cents);
        assert_eq!(4, Tuning::defaults().target_periods);
    }

    #[test]
    fn the_polyphonic_values_are_read_at_their_indices() {
        let mut values = Tuning::default_values();
        let defaults = polyphonic::PolyphonicParameters::defaults();
        assert_eq!(defaults, Tuning::defaults().polyphonic, "the defaults are the analyser's");
        values[T_POLY_CANDIDATE_LOW] = 40.0;
        values[T_POLY_CANDIDATE_HIGH] = 100.0;
        values[T_POLY_HARMONICS] = 8.0;
        values[T_POLY_MAX_HARMONIC] = 3000.0;
        values[T_POLY_TOLERANCE] = 15.0;
        values[T_POLY_CANDIDATES] = 3.0;
        values[T_POLY_MIN_RELATIVE] = 0.2;
        values[T_POLY_FLOOR] = -60.0;
        values[T_POLY_SUBHARMONIC] = 0.5;
        values[T_POLY_FLOOR_WIDTH] = 40.0;
        values[T_POLY_FLOOR_FACTOR] = 3.0;
        let p = Tuning::from_slice(&values).unwrap().polyphonic;
        assert_eq!((p.min_candidate_midi, p.max_candidate_midi, p.harmonics, p.max_candidates), (40, 100, 8, 3));
        assert_eq!((p.max_harmonic_hz, p.harmonic_tolerance_cents, p.min_relative_salience), (3000.0, 15.0, 0.2));
        assert_eq!((p.floor_db, p.subharmonic_ratio, p.floor_half_width_hz, p.floor_factor), (-60.0, 0.5, 40.0, 3.0));
        assert_eq!((p.fft_size, p.min_band_midi, p.max_band_midi), (defaults.fft_size, defaults.min_band_midi, defaults.max_band_midi), "the FFT and the bands stay");
    }

    #[test]
    fn the_attack_values_are_read_at_their_indices() {
        let defaults = attack::AttackParameters::defaults();
        assert_eq!(defaults, Tuning::defaults().attack, "the defaults are the analyser's");
        let mut values = Tuning::default_values();
        values[T_ATTACK_LAG] = 30.0;
        values[T_ATTACK_MAX_FREQUENCY] = 7000.0;
        values[T_ATTACK_PARTIALS] = 9.0;
        values[T_ATTACK_BETA] = 0.8;
        values[T_ATTACK_SIGMA] = 2.0;
        let a = Tuning::from_slice(&values).unwrap().attack;
        assert_eq!((a.fft_size, a.lag_hops, a.max_hz, a.partials, a.beta, a.sigma_bins), (defaults.fft_size, 30, 7000.0, 9, 0.8, 2.0));
    }

    #[test]
    fn the_chord_evidence_and_strum_values_are_read_at_their_indices() {
        let t = Tuning::defaults();
        assert_eq!((t.polyphonic.partial_presence_ratio, t.polyphonic.neighbour_margin), (chord::PARTIAL_PRESENCE_RATIO, chord::NEIGHBOUR_MARGIN));
        assert_eq!((t.strum_window_nanos, t.strum_quiet_hops, t.strum_rise_db, t.strum_clip_share), (300_000_000.0, 60, 12.0, 0.005));
        let mut values = Tuning::default_values();
        values[T_CHORD_PARTIAL_PRESENCE] = 0.1;
        values[T_CHORD_NEIGHBOUR_MARGIN] = 2.5;
        values[T_STRUM_WINDOW] = 500.0;
        values[T_STRUM_QUIET] = 100.0;
        values[T_STRUM_RISE] = 6.0;
        values[T_STRUM_CLIP_SHARE] = 0.01;
        let t = Tuning::from_slice(&values).unwrap();
        assert_eq!((t.polyphonic.partial_presence_ratio, t.polyphonic.neighbour_margin), (0.1, 2.5));
        assert_eq!((t.strum_window_nanos, t.strum_quiet_hops, t.strum_rise_db, t.strum_clip_share), (500_000_000.0, 100, 6.0, 0.01));
    }

    #[test]
    fn the_front_end_values_are_read_at_their_indices() {
        assert_eq!(front_end::FrontEndParameters::defaults(), Tuning::defaults().front, "the defaults are the front end's");
        let mut values = Tuning::default_values();
        values[T_FRONT_HIGH_PASS] = 80.0;
        values[T_FRONT_NOISE_RISE] = 2.0;
        values[T_FRONT_NOISE_FALL] = 0.5;
        values[T_FRONT_NOISE_TRUSTED] = 50.0;
        values[T_GATE_OPEN] = 12.0;
        values[T_GATE_CLOSE] = 3.0;
        values[T_GATE_REDUCTION] = 30.0;
        values[T_GATE_HOLD] = 40.0;
        values[T_SUBTRACTION] = 2.5;
        values[T_SUBTRACTION_FLOOR] = 0.1;
        values[T_SUBTRACTION_LEARN] = 0.3;
        let f = Tuning::from_slice(&values).unwrap().front;
        assert_eq!((f.high_pass_hz, f.noise_rise_db_per_second, f.noise_fall_rate, f.noise_trusted_hops), (80.0, 2.0, 0.5, 50));
        assert_eq!((f.gate_open_db, f.gate_close_db, f.gate_reduction_db, f.gate_hold_hops), (12.0, 3.0, 30.0, 40));
        assert_eq!((f.subtraction_alpha, f.subtraction_beta, f.subtraction_learn_rate), (2.5, 0.1, 0.3));
    }

    #[test]
    fn the_layout_is_the_tuning_length() {
        assert_eq!(TUNING_LENGTH as i32, tuning_layout());
    }
}

/// Every number of the native analysis is accounted for (spec 026, T031, FR-002, research R9), by the rule of the Java inventory tests
/// (`EngineConstantInventoryTest`): a `const` is an index of `Parameters` or `Tuning` (or their length), the default of a parameter whose row of
/// `specs/026-engine-external-parameters/inventory.md` names it in its "Native" column, or a fixed constant of section 6 of
/// `docs/architecture/recognition-engines.md`; a number written inline is mathematical, the default of such a parameter in a `defaults()`
/// function, or listed in the inventory's "Inline numbers that stay inline". The `#[cfg(test)]` modules are not read.
#[cfg(test)]
mod constant_inventory {
    use std::collections::HashSet;
    use std::path::{Path, PathBuf};

    const ALLOWED: [f64; 19] =
        [0.0, 0.5, 1.0, 2.0, 4.0, 10.0, 12.0, 20.0, 60.0, 69.0, 100.0, 440.0, 1000.0, 1200.0, 3600.0, 1e6, 1e9, 1e-9, 1e-12];
    const UNIT_FACTORS: [f64; 5] = [1.0, 1e3, 1e6, 1e9, 1e-3];
    const NUMERIC_TYPES: [&str; 12] = ["i8", "i16", "i32", "i64", "u8", "u16", "u32", "u64", "usize", "isize", "f32", "f64"];

    struct Row {
        default: f64,
        native: String,
    }

    struct Inventory {
        rows: Vec<Row>,
        inline: Vec<(String, f64)>,
        fixed: HashSet<String>,
    }

    fn root() -> PathBuf {
        Path::new(env!("CARGO_MANIFEST_DIR")).join("../../../..")
    }

    fn between<'a>(text: &'a str, start: &str, end: Option<&str>) -> &'a str {
        let from = text.find(&format!("\n{}", start)).unwrap_or_else(|| panic!("no heading {}", start));
        let to = end.and_then(|e| text[from + 1..].find(&format!("\n{}", e)).map(|i| i + from + 1)).unwrap_or(text.len());
        &text[from..to]
    }

    fn cells(line: &str) -> Vec<String> {
        let inner = line.trim().trim_start_matches('|').trim_end_matches('|');
        inner.split('|').map(|c| c.trim().to_string()).collect()
    }

    /// The first two backticked cells of a table row: a file and a name or a literal.
    fn pair(line: &str) -> Option<(String, String)> {
        let c = cells(line);
        if !line.starts_with("| `") || c.len() < 2 {
            return None;
        }
        let a = c[0].strip_prefix('`')?.strip_suffix('`')?;
        let b = c[1].strip_prefix('`')?.strip_suffix('`')?;
        Some((a.to_string(), b.to_string()))
    }

    fn read_inventory() -> Inventory {
        let inventory = std::fs::read_to_string(root().join("specs/026-engine-external-parameters/inventory.md")).expect("the inventory");
        let document = std::fs::read_to_string(root().join("docs/architecture/recognition-engines.md")).expect("the engines' documentation");
        let mut rows = Vec::new();
        for line in between(&inventory, "## Parameters", Some("## Ordered pairs")).lines() {
            if line.starts_with("| `recognition.") {
                let c = cells(line);
                rows.push(Row { default: c[2].parse().expect("a default"), native: c[8].clone() });
            }
        }
        let mut inline = Vec::new();
        for line in between(&inventory, "## Inline numbers that stay inline", None).lines() {
            if let Some((file, literal)) = pair(line) {
                inline.push((file, literal_value(&literal)));
            }
        }
        let mut fixed = HashSet::new();
        for line in between(&document, "## 6. ", None).lines() {
            if let Some((file, name)) = pair(line) {
                fixed.insert(format!("{} {}", file, name));
            }
        }
        Inventory { rows, inline, fixed }
    }

    fn same_in_unit(value: f64, default: f64) -> bool {
        UNIT_FACTORS.iter().any(|f| (value - default * f).abs() <= 1e-9 * (default * f).abs().max(1.0))
    }

    /// The value of a Rust literal: underscores, a type suffix and a hexadecimal prefix allowed.
    fn literal_value(literal: &str) -> f64 {
        let text: String = literal.chars().filter(|c| *c != '_').collect();
        if let Some(hex) = text.strip_prefix("0x") {
            let digits: String = hex.chars().take_while(|c| c.is_ascii_hexdigit()).collect();
            return i64::from_str_radix(&digits, 16).expect("a hexadecimal literal") as f64;
        }
        let mut number = text.as_str();
        for suffix in NUMERIC_TYPES.iter() {
            if let Some(rest) = number.strip_suffix(suffix) {
                number = rest;
                break;
            }
        }
        number.trim_end_matches(['f', 'F', 'd', 'D', 'l', 'L']).trim_end_matches('.').parse().expect("a numeric literal")
    }

    /// The source without comments, strings and test modules, positions kept.
    fn stripped(source: &str) -> String {
        let source = match source.find("#[cfg(test)]") {
            Some(i) => &source[..i],
            None => source,
        };
        let bytes = source.as_bytes();
        let mut out = String::with_capacity(source.len());
        let mut i = 0;
        while i < bytes.len() {
            let end = if source[i..].starts_with("//") {
                source[i..].find('\n').map(|k| i + k).unwrap_or(bytes.len())
            } else if source[i..].starts_with("/*") {
                source[i + 2..].find("*/").map(|k| i + 2 + k + 2).unwrap_or(bytes.len())
            } else if bytes[i] == b'"' {
                let mut k = i + 1;
                while k < bytes.len() && bytes[k] != b'"' {
                    k += if bytes[k] == b'\\' { 2 } else { 1 };
                }
                (k + 1).min(bytes.len())
            } else if source[i..].starts_with("#[") {
                source[i..].find(']').map(|k| i + k + 1).unwrap_or(bytes.len())
            } else {
                let c = source[i..].chars().next().unwrap();
                out.push(c);
                i += c.len_utf8();
                continue;
            };
            for c in source[i..end].chars() {
                out.push(if c == '\n' { '\n' } else { ' ' });
            }
            i = end;
        }
        out
    }

    fn is_ident(c: u8) -> bool {
        c.is_ascii_alphanumeric() || c == b'_'
    }

    /// The numeric literals of the code: their start, end and text.
    fn literals(code: &str) -> Vec<(usize, usize, String)> {
        let b = code.as_bytes();
        let mut found = Vec::new();
        let mut i = 0;
        while i < b.len() {
            if b[i].is_ascii_digit() && (i == 0 || !(is_ident(b[i - 1]) || b[i - 1] == b'.')) {
                let start = i;
                if code[i..].starts_with("0x") {
                    i += 2;
                    while i < b.len() && (b[i].is_ascii_hexdigit() || b[i] == b'_') {
                        i += 1;
                    }
                } else {
                    while i < b.len() && (b[i].is_ascii_digit() || b[i] == b'_') {
                        i += 1;
                    }
                    if i + 1 < b.len() && b[i] == b'.' && b[i + 1].is_ascii_digit() {
                        i += 1;
                        while i < b.len() && (b[i].is_ascii_digit() || b[i] == b'_') {
                            i += 1;
                        }
                    } else if i < b.len() && b[i] == b'.' && (i + 1 == b.len() || !(b[i + 1] == b'.' || is_ident(b[i + 1]))) {
                        i += 1;
                    }
                    if i < b.len() && (b[i] == b'e' || b[i] == b'E') && i + 1 < b.len()
                        && (b[i + 1].is_ascii_digit() || ((b[i + 1] == b'-' || b[i + 1] == b'+') && i + 2 < b.len() && b[i + 2].is_ascii_digit()))
                    {
                        i += 2;
                        while i < b.len() && b[i].is_ascii_digit() {
                            i += 1;
                        }
                    }
                }
                while i < b.len() && is_ident(b[i]) {
                    i += 1;
                }
                found.push((start, i, code[start..i].to_string()));
            } else {
                i += 1;
            }
        }
        found
    }

    /// A literal right after a unary minus is negative.
    fn signed(code: &str, at: usize, value: f64) -> f64 {
        let before = code[..at].trim_end();
        if let Some(rest) = before.strip_suffix('-') {
            let previous = rest.trim_end().bytes().last().unwrap_or(b'(');
            if !(is_ident(previous) || previous == b')' || previous == b']' || previous == b'.') {
                return -value;
            }
        }
        value
    }

    /// The `const NAME: TYPE = INIT;` declarations of numeric type: name, type, initializer and span.
    fn constants(code: &str) -> Vec<(String, String, String, usize, usize)> {
        let b = code.as_bytes();
        let mut found = Vec::new();
        let mut from = 0;
        while let Some(k) = code[from..].find("const ") {
            let at = from + k;
            from = at + 6;
            if at > 0 && is_ident(b[at - 1]) {
                continue;
            }
            let Some(colon) = code[from..].find(':').map(|c| from + c) else { break };
            let name = code[from..colon].trim();
            if name.is_empty() || !name.bytes().all(is_ident) {
                continue;
            }
            let Some(equals) = code[colon..].find('=').map(|e| colon + e) else { break };
            let kind = code[colon + 1..equals].trim();
            let Some(semicolon) = code[equals..].find(';').map(|s| equals + s) else { break };
            if NUMERIC_TYPES.contains(&kind) {
                found.push((name.to_string(), kind.to_string(), code[equals + 1..semicolon].trim().to_string(), at, semicolon + 1));
            }
        }
        found
    }

    /// The bodies of the `fn defaults()` functions.
    fn defaults_bodies(code: &str) -> Vec<(usize, usize)> {
        let mut bodies = Vec::new();
        let mut from = 0;
        while let Some(k) = code[from..].find("fn defaults()") {
            let open = from + k + code[from + k..].find('{').expect("a body");
            let mut depth = 0;
            let mut close = code.len();
            for (j, c) in code[open..].char_indices() {
                if c == '{' {
                    depth += 1;
                } else if c == '}' {
                    depth -= 1;
                    if depth == 0 {
                        close = open + j;
                        break;
                    }
                }
            }
            bodies.push((open, close));
            from = close;
        }
        bodies
    }

    fn names_in_native(row: &Row, file: &str, name: &str) -> bool {
        let needle = format!("{} {}", file, name);
        row.native.match_indices(&needle).any(|(i, _)| {
            let after = row.native.as_bytes().get(i + needle.len()).copied().unwrap_or(b' ');
            let before = if i == 0 { b' ' } else { row.native.as_bytes()[i - 1] };
            !is_ident(after) && !is_ident(before)
        })
    }

    fn unaccounted(inventory: &Inventory) -> Vec<String> {
        let folder = Path::new(env!("CARGO_MANIFEST_DIR")).join("src/analysis");
        let mut files: Vec<PathBuf> =
            std::fs::read_dir(&folder).unwrap().map(|e| e.unwrap().path()).filter(|p| p.extension().map_or(false, |x| x == "rs")).collect();
        files.sort();
        let mod_code = stripped(&std::fs::read_to_string(folder.join("mod.rs")).unwrap());
        let mut problems = Vec::new();
        for path in files {
            let file = path.file_name().unwrap().to_str().unwrap().to_string();
            let listed = format!("CRATE/analysis/{}", file);
            let code = stripped(&std::fs::read_to_string(&path).unwrap());
            let declared = constants(&code);
            for (name, kind, init, _, _) in declared.iter() {
                let index = file == "mod.rs" && kind == "usize"
                    && (name == "LENGTH" || name == "TUNING_LENGTH" || mod_code.contains(&format!("[{}]", name)));
                let parameter = inventory.rows.iter().any(|r| names_in_native(r, &file, name));
                if !(index || parameter || inventory.fixed.contains(&format!("{} {}", listed, name))) {
                    problems.push(format!("{} {} = {}: neither an index, a parameter's native default nor a fixed constant of section 6", listed, name, init));
                }
            }
            let bodies = defaults_bodies(&code);
            for (start, _, text) in literals(&code) {
                if declared.iter().any(|d| d.3 <= start && start < d.4) {
                    continue;
                }
                let value = signed(&code, start, literal_value(&text));
                if ALLOWED.contains(&value.abs()) || inventory.inline.iter().any(|(f, v)| *f == listed && *v == value.abs()) {
                    continue;
                }
                let in_defaults = bodies.iter().any(|(a, b)| *a <= start && start <= *b);
                if in_defaults && inventory.rows.iter().any(|r| r.native.contains(&file) && same_in_unit(value, r.default)) {
                    continue;
                }
                let line = 1 + code[..start].matches('\n').count();
                problems.push(format!("{} line {}: inline {} is not in the allow-list nor in inventory.md \"Inline numbers that stay inline\"", listed, line, text));
            }
        }
        problems
    }

    #[test]
    fn the_scan_reads_rust_literals() {
        assert_eq!(1e-15, literal_value("1e-15"));
        assert_eq!(300_000_000.0, literal_value("300_000_000.0"));
        assert_eq!(0.0, literal_value("0f32"));
        assert_eq!(255.0, literal_value("0xff"));
        let code = stripped("let a = [0f32; N]; // 7\nlet b = x.0 + 1..=3; let c = f(-70.0);\n#[cfg(test)]\nmod tests { 9 }");
        let texts: Vec<String> = literals(&code).into_iter().map(|l| l.2).collect();
        assert_eq!(vec!["0f32", "1", "3", "70.0"], texts);
        let at = code.find("70.0").unwrap();
        assert_eq!(-70.0, signed(&code, at, 70.0));
        assert_eq!(vec![("A".to_string(), "usize".to_string(), "4".to_string())],
            constants("pub const A: usize = 4; const B: &str = \"x\";").into_iter().map(|c| (c.0, c.1, c.2)).collect::<Vec<_>>());
    }

    #[test]
    fn every_native_number_is_a_parameter_or_a_fixed_constant() {
        let inventory = read_inventory();
        assert!(!inventory.rows.is_empty(), "the inventory's parameter rows are read");
        let problems = unaccounted(&inventory);
        assert!(problems.is_empty(), "numbers of the native analysis the inventory and section 6 do not account for:\n{}", problems.join("\n"));
    }
}
