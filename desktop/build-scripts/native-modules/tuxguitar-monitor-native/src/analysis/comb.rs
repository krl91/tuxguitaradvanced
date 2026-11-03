//! Harmonic-comb measurements on a floor-removed magnitude spectrum (port of the audio engine's `HarmonicComb`, spec 023 research R3),
//! shared by the candidate search and the chord evidence so both use the same rules.

use super::polyphonic::PolyphonicParameters;

/// Java's `Math.round` for the positive values used here: floor(x + 0.5).
pub fn java_round(x: f64) -> i64 {
    (x + 0.5).floor() as i64
}

pub fn frequency(midi: i32) -> f64 {
    440.0 * 2.0f64.powf((midi - 69) as f64 / 12.0)
}

pub struct HarmonicComb {
    p: PolyphonicParameters,
    bin_hz: f64,
    tolerance_ratio: f64,
    harmonic_amplitudes: Vec<f64>,
    frequencies: [f64; 128],
}

impl HarmonicComb {
    pub fn new(p: PolyphonicParameters, bin_hz: f64) -> HarmonicComb {
        let mut frequencies = [0.0; 128];
        for (midi, f) in frequencies.iter_mut().enumerate() {
            *f = frequency(midi as i32);
        }
        HarmonicComb {
            p,
            bin_hz,
            tolerance_ratio: 2.0f64.powf(p.harmonic_tolerance_cents / 1200.0),
            harmonic_amplitudes: vec![0.0; p.harmonics as usize + 2],
            frequencies,
        }
    }

    pub fn tolerance_ratio(&self) -> f64 {
        self.tolerance_ratio
    }

    pub fn frequency_of(&self, midi: i32) -> f64 {
        self.frequencies[midi as usize]
    }

    /// Harmonic-sum salience (Klapuri weights), skipping partials that are multiples of `skip_multiples_of` (0: none).
    pub fn salience(&self, magnitudes: &[f64], length: usize, midi: i32, skip_multiples_of: i32) -> f64 {
        let f0 = self.frequency_of(midi);
        let mut sum = 0.0;
        for h in 1..=self.p.harmonics {
            let f = f0 * h as f64;
            if f > self.p.max_harmonic_hz || f >= self.bin_hz * (length as f64 - 2.0) {
                break;
            }
            if skip_multiples_of > 0 && h % skip_multiples_of == 0 {
                continue;
            }
            sum += weight(f0, h) * self.peak_around(magnitudes, length, f, self.tolerance_ratio);
        }
        sum
    }

    /// Weighted mean amplitude of the pitch's partials; with `others`, only the partials no other pitch shares. Returns (mean, count).
    pub fn mean_partial_amplitude(&self, magnitudes: &[f64], length: usize, midi: i32, others: Option<&[i32]>) -> (f64, i32) {
        let f0 = self.frequency_of(midi);
        let mut sum = 0.0;
        let mut weights = 0.0;
        let mut count = 0;
        for h in 1..=self.p.harmonics {
            let f = f0 * h as f64;
            if f > self.p.max_harmonic_hz || f >= self.bin_hz * (length as f64 - 2.0) {
                break;
            }
            if let Some(others) = others {
                if self.shared_with_others(f, midi, others) {
                    continue;
                }
            }
            let weight = weight(f0, h);
            sum += weight * self.peak_around(magnitudes, length, f, self.tolerance_ratio);
            weights += weight;
            count += 1;
        }
        (if weights > 0.0 { sum / weights } else { 0.0 }, count)
    }

    /// The peak of each of the pitch's partials (with `others`, only its unique partials), into `out`; returns how many.
    pub fn partial_peaks(&self, magnitudes: &[f64], length: usize, midi: i32, others: Option<&[i32]>, out: &mut [f64]) -> usize {
        let f0 = self.frequency_of(midi);
        let mut count = 0;
        for h in 1..=self.p.harmonics {
            let f = f0 * h as f64;
            if f > self.p.max_harmonic_hz || f >= self.bin_hz * (length as f64 - 2.0) {
                break;
            }
            if let Some(others) = others {
                if self.shared_with_others(f, midi, others) {
                    continue;
                }
            }
            out[count] = self.peak_around(magnitudes, length, f, self.tolerance_ratio);
            count += 1;
        }
        count
    }

    fn shared_with_others(&self, frequency: f64, midi: i32, others: &[i32]) -> bool {
        for &other in others {
            if other == midi {
                continue;
            }
            let f0 = self.frequency_of(other);
            let h = java_round(frequency / f0);
            if h >= 1 && (1200.0 * (frequency / (h as f64 * f0)).ln() / 2.0f64.ln()).abs() <= self.p.harmonic_tolerance_cents {
                return true;
            }
        }
        false
    }

    /// Removes the pitch's harmonics, each at most down to the mean of its neighbours (spectral smoothness).
    pub fn suppress(&mut self, magnitudes: &mut [f64], length: usize, midi: i32) {
        let f0 = self.frequency_of(midi);
        let mut count = 0;
        let mut h = 1;
        while h <= self.p.harmonics && f0 * h as f64 <= self.p.max_harmonic_hz && f0 * (h as f64) < self.bin_hz * (length as f64 - 2.0) {
            self.harmonic_amplitudes[h as usize] = self.peak_around(magnitudes, length, f0 * h as f64, self.tolerance_ratio);
            count = h;
            h += 1;
        }
        for h in 1..=count {
            let amplitude = self.harmonic_amplitudes[h as usize];
            if amplitude <= 0.0 {
                continue;
            }
            let mut removable = amplitude;
            if h > 1 {
                let neighbours = if h < count {
                    (self.harmonic_amplitudes[h as usize - 1] + self.harmonic_amplitudes[h as usize + 1]) / 2.0
                } else {
                    self.harmonic_amplitudes[h as usize - 1]
                };
                removable = amplitude.min(neighbours);
            }
            let factor = (1.0 - removable / amplitude).max(0.0);
            let high = self.high_bin(length, f0 * h as f64, self.tolerance_ratio, 2);
            let low = self.low_bin(length, f0 * h as f64, self.tolerance_ratio, 2);
            for k in low..=high {
                magnitudes[k] *= factor;
            }
        }
    }

    /// The lowest subharmonic 1/k (k = 2 … 8) whose own partials reach `ratio` of the pitch's salience, else the pitch itself.
    pub fn lowest_explaining_subharmonic(
        &self,
        magnitudes: &[f64],
        length: usize,
        midi: i32,
        midi_salience: f64,
        ratio: f64,
        excluded: &[bool; 128],
        min_midi: i32,
    ) -> i32 {
        let mut lowest = midi;
        for k in 2..=8 {
            let semitones = 12.0 * (k as f64).ln() / 2.0f64.ln();
            let candidate = midi - java_round(semitones) as i32;
            let error_cents = ((midi - candidate) as f64 - semitones).abs() * 100.0;
            if candidate < min_midi || error_cents > self.p.harmonic_tolerance_cents || excluded[candidate as usize] {
                continue;
            }
            if self.salience(magnitudes, length, candidate, k) >= ratio * midi_salience && candidate < lowest {
                lowest = candidate;
            }
        }
        lowest
    }

    /// True when the pitch lies, within the tolerance, on harmonic 2 … harmonics of the lower pitch.
    pub fn is_harmonic_of(&self, midi: i32, lower_midi: i32) -> bool {
        if lower_midi >= midi {
            return false;
        }
        let ratio = self.frequency_of(midi) / self.frequency_of(lower_midi);
        let h = java_round(ratio);
        h >= 2 && h <= self.p.harmonics as i64 && (1200.0 * (ratio / h as f64).ln() / 2.0f64.ln()).abs() <= self.p.harmonic_tolerance_cents
    }

    /// The strongest magnitude within a frequency ratio around the frequency (at least the nearest bin).
    pub fn peak_around(&self, magnitudes: &[f64], length: usize, frequency: f64, ratio: f64) -> f64 {
        let mut peak = 0.0f64;
        let high = self.high_bin(length, frequency, ratio, 0);
        let low = self.low_bin(length, frequency, ratio, 0);
        for k in low..=high {
            peak = peak.max(magnitudes[k]);
        }
        peak
    }

    fn low_bin(&self, length: usize, frequency: f64, ratio: f64, extra: i64) -> usize {
        let nearest = java_round(frequency / self.bin_hz);
        clamp_bin(length, nearest.min((frequency / ratio / self.bin_hz).ceil() as i64) - extra)
    }

    fn high_bin(&self, length: usize, frequency: f64, ratio: f64, extra: i64) -> usize {
        let nearest = java_round(frequency / self.bin_hz);
        clamp_bin(length, nearest.max((frequency * ratio / self.bin_hz).floor() as i64) + extra)
    }
}

fn clamp_bin(length: usize, bin: i64) -> usize {
    bin.min(length as i64 - 1).max(1) as usize
}

fn weight(f0: f64, h: i32) -> f64 {
    (f0 + 27.0) / (h as f64 * f0 + 320.0)
}
