//! The polyphonic spectrum (port of the audio engine's `PolyphonicAnalyzer`, spec 023 research R3): a Hann-windowed FFT over the latest
//! 8192 samples, per-semitone band levels, harmonic-sum salience and ranked pitch candidates, from a floor-removed spectrum. No allocation
//! per hop: the result is written into a preallocated `Spectrum`.

use super::comb::{frequency, HarmonicComb};
use super::fft::RealFft;

#[derive(Clone, Copy, Debug, PartialEq)]
pub struct PolyphonicParameters {
    pub fft_size: usize,
    pub min_band_midi: i32,
    pub max_band_midi: i32,
    pub min_candidate_midi: i32,
    pub max_candidate_midi: i32,
    pub harmonics: i32,
    pub max_harmonic_hz: f64,
    pub harmonic_tolerance_cents: f64,
    pub max_candidates: usize,
    pub min_relative_salience: f64,
    pub floor_db: f64,
    pub subharmonic_ratio: f64,
}

impl PolyphonicParameters {
    /// `PolyphonicAnalyzer.Parameters.defaults()`.
    pub fn defaults() -> PolyphonicParameters {
        PolyphonicParameters {
            fft_size: 8192,
            min_band_midi: 24,
            max_band_midi: 112,
            min_candidate_midi: 33,
            max_candidate_midi: 88,
            harmonics: 12,
            max_harmonic_hz: 5000.0,
            harmonic_tolerance_cents: 25.0,
            max_candidates: 6,
            min_relative_salience: 0.10,
            floor_db: -70.0,
            subharmonic_ratio: 0.30,
        }
    }
}

const FLOOR_HALF_WIDTH_HZ: f64 = 25.0;
const FLOOR_FACTOR: f64 = 2.0;

#[derive(Clone, Copy, Debug, Default, PartialEq)]
pub struct Candidate {
    pub midi: i32,
    pub salience: f64,
    pub relative_salience: f64,
    pub level_db: f64,
}

/// One frame's polyphonic view (the fields of `SpectrumFrame`).
pub struct Spectrum {
    pub bin_hz: f64,
    pub band_db: Vec<f32>,
    pub salience: Vec<f32>,
    pub candidates: Vec<Candidate>,
    pub candidate_count: usize,
    pub peaks: Vec<f32>,
    pub peak_count: usize,
}

impl Spectrum {
    pub fn new(p: &PolyphonicParameters) -> Spectrum {
        Spectrum {
            bin_hz: 0.0,
            band_db: vec![0.0; (p.max_band_midi - p.min_band_midi + 1) as usize],
            salience: vec![0.0; (p.max_candidate_midi - p.min_candidate_midi + 1) as usize],
            candidates: vec![Candidate::default(); p.max_candidates],
            candidate_count: 0,
            peaks: vec![0.0; p.fft_size / 2 + 1],
            peak_count: 0,
        }
    }

    /// The band level of a pitch, or minus infinity outside the bands (`SpectrumFrame.getBandDb`).
    pub fn band_db_of(&self, p: &PolyphonicParameters, midi: i32) -> f64 {
        let index = midi - p.min_band_midi;
        if index >= 0 && (index as usize) < self.band_db.len() {
            self.band_db[index as usize] as f64
        } else {
            f64::NEG_INFINITY
        }
    }
}

pub struct PolyphonicAnalyzer {
    pub p: PolyphonicParameters,
    fft: RealFft,
    hann: Vec<f64>,
    history: Vec<f32>,
    input: Vec<f64>,
    spectrum: Vec<f64>,
    peaks: Vec<f64>,
    working: Vec<f64>,
    prefix: Vec<f64>,
    picked: Vec<bool>,
    excluded: [bool; 128],
    band_ratio: f64,
    comb: HarmonicComb,
    peak_length: usize,
    history_position: usize,
    bin_hz: f64,
}

impl PolyphonicAnalyzer {
    pub fn new(p: PolyphonicParameters, sample_rate: f64) -> PolyphonicAnalyzer {
        let n = p.fft_size;
        let hann = (0..n).map(|i| 0.5 - 0.5 * (2.0 * std::f64::consts::PI * i as f64 / (n - 1) as f64).cos()).collect();
        let bin_hz = sample_rate / n as f64;
        let mut analyzer = PolyphonicAnalyzer {
            p,
            fft: RealFft::new(n),
            hann,
            history: vec![0.0; n],
            input: vec![0.0; n],
            spectrum: vec![0.0; n / 2 + 1],
            peaks: vec![0.0; n / 2 + 1],
            working: vec![0.0; n / 2 + 1],
            prefix: vec![0.0; n / 2 + 2],
            picked: vec![false; (p.max_candidate_midi - p.min_candidate_midi + 1) as usize],
            excluded: [false; 128],
            band_ratio: 2.0f64.powf(50.0 / 1200.0),
            comb: HarmonicComb::new(p, bin_hz),
            peak_length: 0,
            history_position: 0,
            bin_hz,
        };
        analyzer.peak_length = (n / 2 + 1).min((p.max_harmonic_hz * 1.1 / bin_hz).ceil() as usize + 3);
        analyzer
    }

    pub fn bin_hz(&self) -> f64 {
        self.bin_hz
    }

    pub fn reset(&mut self) {
        self.history.iter_mut().for_each(|s| *s = 0.0);
        self.history_position = 0;
    }

    pub fn push(&mut self, samples: &[f32]) {
        let n = self.history.len();
        for s in samples {
            self.history[self.history_position] = *s;
            self.history_position = (self.history_position + 1) % n;
        }
    }

    pub fn analyze(&mut self, out: &mut Spectrum) {
        let size = self.p.fft_size;
        for i in 0..size {
            self.input[i] = self.history[(self.history_position + i) % size] as f64 * self.hann[i];
        }
        self.fft.magnitudes(&self.input, &mut self.spectrum, 4.0 / size as f64);
        for midi in self.p.min_band_midi..=self.p.max_band_midi {
            let peak = self.comb.peak_around(&self.spectrum, self.spectrum.len(), frequency(midi), self.band_ratio);
            out.band_db[(midi - self.p.min_band_midi) as usize] = to_db(peak) as f32;
        }
        remove_floor(&self.spectrum, &mut self.peaks, &mut self.prefix, self.bin_hz);
        for midi in self.p.min_candidate_midi..=self.p.max_candidate_midi {
            out.salience[(midi - self.p.min_candidate_midi) as usize] = self.comb.salience(&self.peaks, self.peak_length, midi, 0) as f32;
        }
        self.working[..self.peak_length].copy_from_slice(&self.peaks[..self.peak_length]);
        self.picked.iter_mut().for_each(|b| *b = false);
        self.excluded = [false; 128];
        let floor = 10.0f64.powf(self.p.floor_db / 20.0);
        let mut strongest = 0.0;
        out.candidate_count = 0;
        while out.candidate_count < self.p.max_candidates {
            let mut best = -1;
            let mut best_salience = 0.0;
            for midi in self.p.min_candidate_midi..=self.p.max_candidate_midi {
                if !self.picked[(midi - self.p.min_candidate_midi) as usize] {
                    let value = self.comb.salience(&self.working, self.peak_length, midi, 0);
                    if value > best_salience {
                        best_salience = value;
                        best = midi;
                    }
                }
            }
            if best < 0 || best_salience < floor || (strongest > 0.0 && best_salience < strongest * self.p.min_relative_salience) {
                break;
            }
            best = self.comb.lowest_explaining_subharmonic(
                &self.working,
                self.peak_length,
                best,
                best_salience,
                self.p.subharmonic_ratio,
                &self.excluded,
                self.p.min_candidate_midi,
            );
            best_salience = best_salience.max(self.comb.salience(&self.working, self.peak_length, best, 0));
            if strongest == 0.0 {
                strongest = best_salience;
            }
            let level = self.comb.peak_around(&self.spectrum, self.spectrum.len(), frequency(best), self.comb.tolerance_ratio());
            out.candidates[out.candidate_count] = Candidate { midi: best, salience: best_salience, relative_salience: best_salience / strongest, level_db: to_db(level) };
            out.candidate_count += 1;
            self.picked[(best - self.p.min_candidate_midi) as usize] = true;
            self.excluded[best as usize] = true;
            let length = self.peak_length;
            self.comb.suppress(&mut self.working, length, best);
        }
        for k in 0..self.peak_length {
            out.peaks[k] = self.peaks[k] as f32;
        }
        out.peak_count = self.peak_length;
        out.bin_hz = self.bin_hz;
    }
}

/// Keeps only what rises above twice the local mean (over ±25 Hz).
fn remove_floor(magnitudes: &[f64], result: &mut [f64], prefix: &mut [f64], bin_hz: f64) {
    prefix[0] = 0.0;
    for k in 0..magnitudes.len() {
        prefix[k + 1] = prefix[k] + magnitudes[k];
    }
    let half_width = 2i64.max(super::comb::java_round(FLOOR_HALF_WIDTH_HZ / bin_hz)) as usize;
    for k in 0..magnitudes.len() {
        let low = k.saturating_sub(half_width);
        let high = (magnitudes.len() - 1).min(k + half_width);
        let mean = (prefix[high + 1] - prefix[low]) / (high - low + 1) as f64;
        result[k] = (magnitudes[k] - FLOOR_FACTOR * mean).max(0.0);
    }
}

pub fn to_db(amplitude: f64) -> f64 {
    if amplitude > 0.0 {
        20.0 * amplitude.log10()
    } else {
        f64::NEG_INFINITY
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn a_chord_gives_its_three_pitches_as_candidates() {
        let rate = 48000.0;
        let p = PolyphonicParameters::defaults();
        let mut analyzer = PolyphonicAnalyzer::new(p, rate);
        // C major: C3, E3, G3, each with four partials
        let pitches = [48, 52, 55];
        let samples: Vec<f32> = (0..8192)
            .map(|i| {
                let t = i as f64 / rate;
                let mut v = 0.0;
                for m in pitches {
                    let f = frequency(m);
                    for h in 1..=4 {
                        v += 0.08 / h as f64 * (2.0 * std::f64::consts::PI * f * h as f64 * t).sin();
                    }
                }
                v as f32
            })
            .collect();
        analyzer.push(&samples);
        let mut out = Spectrum::new(&p);
        analyzer.analyze(&mut out);
        let found: Vec<i32> = out.candidates[..out.candidate_count].iter().map(|c| c.midi).collect();
        for m in pitches {
            assert!(found.contains(&m), "{m} in {found:?}");
        }
        assert!((out.candidates[0].relative_salience - 1.0).abs() < 1e-12);
        assert!(out.band_db_of(&p, 48) > -30.0 && out.band_db_of(&p, 49) < out.band_db_of(&p, 48));
    }
}
