//! The pitch of a window by YIN, written from the paper: A. de Cheveigné and H. Kawahara, "YIN, a fundamental frequency estimator for
//! speech and music", J. Acoust. Soc. Am. 111(4), 2002 (spec 023, research R3). TarsosDSP was not used, nor read, to write it: the crate
//! stays free of GPL code. The parity test compares its results with the Java engine's estimator from the outside only.
//!
//! Steps of the paper: (2) the difference function, its cross term computed with an FFT; (3) the cumulative mean normalised difference;
//! (4) the absolute threshold, then the local minimum that follows; (5) parabolic interpolation. The integration window is half the
//! analysis window, so the longest period is half of it too.

use super::fft::RealFft;
use rustfft::num_complex::Complex;

/// The threshold of step 4 used by the Java engine's estimator (its default).
pub const DEFAULT_THRESHOLD: f64 = 0.20;

#[derive(Clone, Copy, Debug)]
pub struct Pitch {
    /// NaN when unpitched.
    pub f0_hz: f64,
    pub probability: f64,
    pub pitched: bool,
}

pub struct Yin {
    sample_rate: f64,
    size: usize,
    half: usize,
    threshold: f64,
    fft: RealFft,
    signal: Vec<Complex<f64>>,
    kernel: Vec<Complex<f64>>,
    difference: Vec<f64>,
}

impl Yin {
    pub fn new(sample_rate: f64, window: usize, threshold: f64) -> Yin {
        let half = window / 2;
        Yin {
            sample_rate,
            size: window,
            half,
            threshold,
            fft: RealFft::new(window),
            signal: vec![Complex::new(0.0, 0.0); window],
            kernel: vec![Complex::new(0.0, 0.0); window],
            difference: vec![0.0; half],
        }
    }

    pub fn estimate(&mut self, window: &[f32]) -> Pitch {
        self.difference_function(window);
        self.cumulative_mean_normalised();
        match self.absolute_threshold() {
            Some(tau) => {
                // the Java engine's estimator gives both as floats: rounded the same way, so the two engines give the same frames
                let probability = ((1.0 - self.difference[tau]) as f32) as f64;
                let refined = self.parabolic(tau);
                Pitch { f0_hz: ((self.sample_rate / refined) as f32) as f64, probability, pitched: true }
            }
            None => Pitch { f0_hz: f64::NAN, probability: 0.0, pitched: false },
        }
    }

    /// Step 2: d(τ) = Σ_{j<W} (x_j − x_{j+τ})² = e(0) + e(τ) − 2 r(τ), with r the cross correlation of the first W samples with the window.
    fn difference_function(&mut self, x: &[f32]) {
        let w = self.half;
        for i in 0..self.size {
            self.signal[i] = Complex::new(x[i] as f64, 0.0);
            self.kernel[i] = Complex::new(if i < w { x[i] as f64 } else { 0.0 }, 0.0);
        }
        self.fft.forward(&mut self.signal);
        self.fft.forward(&mut self.kernel);
        for i in 0..self.size {
            let k = self.kernel[i];
            self.signal[i] = self.signal[i] * k.conj();
        }
        self.fft.inverse(&mut self.signal);
        // e(τ), slid one sample at a time. The Java engine's estimator lets in sample τ+W where the paper's sum lets in τ+W−1 (found by
        // comparing the two engines' outputs only, spec 023 T018, owner's decision of 2026-11-03): this engine does the same, so that both
        // give the same pitches and confidences.
        let mut energy_0 = 0.0;
        for j in 0..w {
            energy_0 += (x[j] as f64) * (x[j] as f64);
        }
        let mut energy_tau = energy_0;
        for tau in 0..w {
            if tau > 0 {
                let out = x[tau - 1] as f64;
                let enter = x[tau + w] as f64;
                energy_tau += enter * enter - out * out;
            }
            self.difference[tau] = (energy_0 + energy_tau - 2.0 * self.signal[tau].re).max(0.0);
        }
    }

    /// Step 3: d'(0) = 1, d'(τ) = d(τ) / ((1/τ) Σ_{j=1..τ} d(j)).
    fn cumulative_mean_normalised(&mut self) {
        self.difference[0] = 1.0;
        let mut running = 0.0;
        for tau in 1..self.half {
            running += self.difference[tau];
            self.difference[tau] = if running > 0.0 { self.difference[tau] * tau as f64 / running } else { 1.0 };
        }
    }

    /// Step 4: the smallest τ whose d' is below the threshold, moved on to the bottom of that dip.
    fn absolute_threshold(&self) -> Option<usize> {
        let mut tau = 2;
        while tau < self.half {
            if self.difference[tau] < self.threshold {
                while tau + 1 < self.half && self.difference[tau + 1] < self.difference[tau] {
                    tau += 1;
                }
                return Some(tau);
            }
            tau += 1;
        }
        None
    }

    /// Step 5: the vertex of the parabola through d' at τ−1, τ, τ+1.
    fn parabolic(&self, tau: usize) -> f64 {
        if tau < 1 || tau + 1 >= self.half {
            return tau as f64;
        }
        let s0 = self.difference[tau - 1];
        let s1 = self.difference[tau];
        let s2 = self.difference[tau + 1];
        let denominator = 2.0 * (2.0 * s1 - s2 - s0);
        if denominator == 0.0 {
            return tau as f64;
        }
        tau as f64 + (s2 - s0) / denominator
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn tone(hz: f64, rate: f64, n: usize) -> Vec<f32> {
        (0..n)
            .map(|i| {
                let t = i as f64 / rate;
                (0.4 * (2.0 * std::f64::consts::PI * hz * t).sin() + 0.2 * (4.0 * std::f64::consts::PI * hz * t).sin()) as f32
            })
            .collect()
    }

    fn cents(a: f64, b: f64) -> f64 {
        1200.0 * (a / b).log2()
    }

    #[test]
    fn guitar_and_bass_pitches_at_every_rate() {
        // the lowest strings of the practice's tunings (5-string bass B0, bass E1, drop C, drop D, standard E2) and higher notes
        let pitches = [30.87, 41.20, 65.41, 73.42, 82.41, 110.0, 196.0, 329.63, 659.26, 1318.5];
        for rate in [44100.0, 48000.0, 88200.0, 96000.0] {
            // the window holds at least two periods of the lowest pitch at this rate, as the engine's window does at 48 kHz for its range
            let window = if rate > 50000.0 { 4096 } else { 2048 };
            let mut yin = Yin::new(rate, window, DEFAULT_THRESHOLD);
            for hz in pitches {
                if rate / hz > (window / 2) as f64 - 2.0 {
                    continue;
                }
                let p = yin.estimate(&tone(hz, rate, window));
                assert!(p.pitched, "{hz} Hz at {rate} Hz is pitched");
                assert!(cents(p.f0_hz, hz).abs() < 1.0, "{hz} Hz at {rate}: {} Hz", p.f0_hz);
                assert!(p.probability > 0.9);
            }
        }
    }

    #[test]
    fn noise_and_silence_are_unpitched() {
        let mut yin = Yin::new(48000.0, 2048, DEFAULT_THRESHOLD);
        let silence = vec![0f32; 2048];
        assert!(!yin.estimate(&silence).pitched);
        let mut seed = 12345u64;
        let noise: Vec<f32> = (0..2048)
            .map(|_| {
                seed = seed.wrapping_mul(6364136223846793005).wrapping_add(1442695040888963407);
                ((seed >> 33) as f64 / (1u64 << 31) as f64 - 0.5) as f32
            })
            .collect();
        assert!(!yin.estimate(&noise).pitched);
    }
}
