//! The onset of a new pluck (port of the audio engine's `ArticulationDetector`, spec 023 research R3): a spectral-flux transient and a
//! level gate, decided `post_last` hops after the transient. Streaming, one call per hop, preallocated.

use super::fft::RealFft;
use rustfft::num_complex::Complex;

const FFT_SIZE: usize = 1024;
const MEDIAN_HOPS: usize = 10;

/// The detector's numbers: those of `ArticulationDetector`'s constructor.
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct ArticulationParameters {
    pub flux_median_factor: f64,
    pub flux_offset: f64,
    pub min_level_db: f64,
    pub refractory_hops: i64,
    pub gate_db: f64,
    pub pre_first: i64,
    pub pre_last: i64,
    pub post_first: i64,
    pub post_last: i64,
}

impl ArticulationParameters {
    /// The default detector (13 hops of decision delay), with the gate and floor of the settings.
    pub fn normal(min_level_db: f64, gate_db: f64) -> ArticulationParameters {
        ArticulationParameters {
            flux_median_factor: 1.5,
            flux_offset: 5.0,
            min_level_db,
            refractory_hops: 12,
            gate_db,
            pre_first: 19,
            pre_last: 2,
            post_first: 4,
            post_last: 13,
        }
    }

    /// The fast-run detector (8 hops).
    pub fn fast(min_level_db: f64, gate_db: f64) -> ArticulationParameters {
        ArticulationParameters {
            flux_median_factor: 1.5,
            flux_offset: 5.0,
            min_level_db,
            refractory_hops: 6,
            gate_db,
            pre_first: 8,
            pre_last: 2,
            post_first: 3,
            post_last: 8,
        }
    }
}

pub struct ArticulationDetector {
    p: ArticulationParameters,
    history: usize,
    fft: RealFft,
    hann: Vec<f64>,
    spectrum: Vec<Complex<f64>>,
    previous_magnitude: Vec<f64>,
    level_db: Vec<f64>,
    flux: Vec<f64>,
    median_scratch: [f64; MEDIAN_HOPS],
    hop: i64,
    last_onset_hop: i64,
}

impl ArticulationDetector {
    pub fn new(p: ArticulationParameters) -> ArticulationDetector {
        let history = (p.pre_first + p.post_last + 2) as usize;
        let hann = (0..FFT_SIZE).map(|i| 0.5 - 0.5 * (2.0 * std::f64::consts::PI * i as f64 / FFT_SIZE as f64).cos()).collect();
        let mut detector = ArticulationDetector {
            p,
            history,
            fft: RealFft::new(FFT_SIZE),
            hann,
            spectrum: vec![Complex::new(0.0, 0.0); FFT_SIZE],
            previous_magnitude: vec![0.0; FFT_SIZE / 2],
            level_db: vec![0.0; history],
            flux: vec![0.0; history],
            median_scratch: [0.0; MEDIAN_HOPS],
            hop: 0,
            last_onset_hop: 0,
        };
        detector.reset();
        detector
    }

    pub fn parameters(&self) -> ArticulationParameters {
        self.p
    }

    pub fn reset(&mut self) {
        self.hop = 0;
        self.last_onset_hop = i64::MIN / 2;
        self.previous_magnitude.iter_mut().for_each(|m| *m = 0.0);
    }

    /// The newest hop: true when an articulation is confirmed whose transient was `post_last` hops before this one.
    pub fn process(&mut self, window: &[f32]) -> bool {
        let offset = window.len() - FFT_SIZE;
        let mut energy = 0.0f64;
        for i in 0..FFT_SIZE {
            let sample = window[offset + i] as f64;
            energy += sample * sample;
            self.spectrum[i] = Complex::new(sample * self.hann[i], 0.0);
        }
        self.fft.forward(&mut self.spectrum);
        let mut current_flux = 0.0;
        for k in 1..FFT_SIZE / 2 {
            let c = self.spectrum[k];
            let magnitude = (100.0 * c.re.hypot(c.im)).ln_1p();
            let rise = magnitude - self.previous_magnitude[k];
            if rise > 0.0 {
                current_flux += rise;
            }
            self.previous_magnitude[k] = magnitude;
        }
        let slot = self.slot(self.hop);
        self.level_db[slot] = 10.0 * (energy / FFT_SIZE as f64 + 1e-12).log10();
        self.flux[slot] = if self.hop == 0 { 0.0 } else { current_flux };
        let onset = self.decide(self.hop - self.p.post_last);
        self.hop += 1;
        onset
    }

    fn decide(&mut self, candidate: i64) -> bool {
        if candidate < MEDIAN_HOPS as i64 || candidate - self.last_onset_hop <= self.p.refractory_hops {
            return false;
        }
        let value = self.flux[self.slot(candidate)];
        if value < self.flux[self.slot(candidate - 1)] || value < self.flux[self.slot(candidate + 1)] {
            return false;
        }
        if self.level_db[self.slot(candidate)] <= self.p.min_level_db || value <= self.p.flux_median_factor * self.median_before(candidate) + self.p.flux_offset {
            return false;
        }
        if candidate >= self.p.pre_first && !self.passes_gate(candidate) {
            return false;
        }
        self.last_onset_hop = candidate;
        true
    }

    fn passes_gate(&self, candidate: i64) -> bool {
        let mut pre = -f64::MAX;
        for k in candidate - self.p.pre_first..=candidate - self.p.pre_last {
            pre = pre.max(self.level_db[self.slot(k)]);
        }
        let mut post = 0.0;
        for k in candidate + self.p.post_first..=candidate + self.p.post_last {
            post += self.level_db[self.slot(k)];
        }
        post /= (self.p.post_last - self.p.post_first + 1) as f64;
        post - pre >= self.p.gate_db
    }

    fn median_before(&mut self, candidate: i64) -> f64 {
        for i in 0..MEDIAN_HOPS {
            self.median_scratch[i] = self.flux[self.slot(candidate - MEDIAN_HOPS as i64 + i as i64)];
        }
        // the order of Java's Arrays.sort (no NaN can occur here); an unstable sort never allocates
        self.median_scratch.sort_unstable_by(|a, b| a.partial_cmp(b).unwrap_or(std::cmp::Ordering::Equal));
        self.median_scratch[MEDIAN_HOPS / 2]
    }

    fn slot(&self, hop: i64) -> usize {
        hop.rem_euclid(self.history as i64) as usize
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    /// Silence, then a plucked tone: one onset, `post_last` hops after the pluck's hop.
    fn pluck_onsets(p: ArticulationParameters) -> Vec<i64> {
        let rate = 48000.0;
        let hop = 256;
        let total = 200 * hop;
        let start = 60 * hop + 100;
        let signal: Vec<f32> = (0..total)
            .map(|i| {
                if i < start {
                    0.0005 * (((i * 7919) % 97) as f32 / 97.0 - 0.5)
                } else {
                    let t = (i - start) as f64 / rate;
                    (0.5 * (-t / 0.4).exp() * (2.0 * std::f64::consts::PI * 196.0 * t).sin()) as f32
                }
            })
            .collect();
        let mut detector = ArticulationDetector::new(p);
        let mut window = vec![0f32; 2048];
        let mut onsets = Vec::new();
        for h in 0..total / hop {
            window.copy_within(hop.., 0);
            window[2048 - hop..].copy_from_slice(&signal[h * hop..(h + 1) * hop]);
            if detector.process(&window) {
                onsets.push(h as i64);
            }
        }
        onsets
    }

    #[test]
    fn one_pluck_is_one_onset_after_the_decision_delay() {
        let normal = pluck_onsets(ArticulationParameters::normal(-50.0, 4.5));
        assert_eq!(normal.len(), 1, "{normal:?}");
        let fast = pluck_onsets(ArticulationParameters::fast(-50.0, 4.5));
        assert_eq!(fast.len(), 1, "{fast:?}");
        assert_eq!(normal[0] - fast[0], 13 - 8, "the fast detector decides 5 hops sooner");
    }
}
