//! The level of a window (port of the audio engine's `LevelAnalyzer`, spec 023 research R3): RMS, peak, clipped share and the level state,
//! with the thresholds of the settings.

/// The ordinals of `AnalysisFrame.LevelState`.
pub const TOO_LOW: i32 = 0;
pub const SUITABLE: i32 = 1;
pub const TOO_HIGH: i32 = 2;
pub const CLIPPING: i32 = 3;

#[derive(Clone, Copy, Debug)]
pub struct Level {
    pub rms_dbfs: f64,
    pub peak_dbfs: f64,
    pub clipped_ratio: f64,
    pub state: i32,
}

#[derive(Clone, Copy, Debug)]
pub struct LevelAnalyzer {
    pub too_low_rms_dbfs: f64,
    pub too_high_rms_dbfs: f64,
    pub clipped_sample_level: f64,
    pub clipping_ratio_threshold: f64,
}

impl LevelAnalyzer {
    pub fn analyze(&self, samples: &[f32]) -> Level {
        let mut sum_squares = 0.0f64;
        let mut peak = 0.0f64;
        let mut clipped = 0usize;
        for sample in samples {
            let absolute = (*sample as f64).abs();
            sum_squares += absolute * absolute;
            peak = peak.max(absolute);
            if absolute >= self.clipped_sample_level {
                clipped += 1;
            }
        }
        let length = samples.len() as f64;
        let rms = (sum_squares / length).sqrt();
        let rms_dbfs = to_dbfs(rms);
        let clipped_ratio = clipped as f64 / length;
        let state = if clipped_ratio >= self.clipping_ratio_threshold {
            CLIPPING
        } else if rms_dbfs < self.too_low_rms_dbfs {
            TOO_LOW
        } else if rms_dbfs > self.too_high_rms_dbfs {
            TOO_HIGH
        } else {
            SUITABLE
        };
        Level { rms_dbfs, peak_dbfs: to_dbfs(peak), clipped_ratio, state }
    }
}

fn to_dbfs(linear: f64) -> f64 {
    if linear == 0.0 {
        f64::NEG_INFINITY
    } else {
        20.0 * linear.log10()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn analyzer() -> LevelAnalyzer {
        LevelAnalyzer { too_low_rms_dbfs: -40.0, too_high_rms_dbfs: -6.0, clipped_sample_level: 0.99, clipping_ratio_threshold: 0.10 }
    }

    #[test]
    fn a_sine_at_half_scale_is_suitable_and_silence_too_low() {
        let sine: Vec<f32> = (0..2048).map(|i| (0.5 * (2.0 * std::f64::consts::PI * 440.0 * i as f64 / 48000.0).sin()) as f32).collect();
        let level = analyzer().analyze(&sine);
        assert!((level.rms_dbfs - (-9.03)).abs() < 0.02, "{}", level.rms_dbfs);
        assert!((level.peak_dbfs - (-6.02)).abs() < 0.02);
        assert_eq!(level.state, SUITABLE);
        let silence = vec![0f32; 2048];
        let level = analyzer().analyze(&silence);
        assert_eq!(level.rms_dbfs, f64::NEG_INFINITY);
        assert_eq!(level.state, TOO_LOW);
    }

    #[test]
    fn a_clipped_window_is_clipping() {
        let mut samples = vec![0.3f32; 2048];
        for s in samples.iter_mut().take(300) {
            *s = 1.0;
        }
        let level = analyzer().analyze(&samples);
        assert_eq!(level.state, CLIPPING);
        assert!((level.clipped_ratio - 300.0 / 2048.0).abs() < 1e-12);
    }
}
