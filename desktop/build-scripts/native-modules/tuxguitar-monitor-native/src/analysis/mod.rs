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
