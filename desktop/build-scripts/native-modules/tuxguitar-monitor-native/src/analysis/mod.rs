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
}
