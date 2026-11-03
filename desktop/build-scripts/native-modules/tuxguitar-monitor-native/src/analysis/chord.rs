//! The evidence for an expected chord in one spectrum (port of the audio engine's `ChordEvidence.evaluate`, spec 023 research R2 and R3):
//! how strongly each expected pitch is present, and which other pitches remain once the expected and tolerated ones are explained. It
//! decides nothing; the practice's rules do. Preallocated: evaluating a chord never allocates.

use super::comb::HarmonicComb;
use super::polyphonic::{Candidate, PolyphonicParameters, Spectrum};

pub const PARTIAL_PRESENCE_RATIO: f64 = 0.05;
pub const MAX_PITCHES: usize = 16;
pub const MAX_RESIDUAL: usize = 3;

#[derive(Clone, Copy, Debug, Default, PartialEq)]
pub struct ChordPitch {
    pub midi: i32,
    pub salience: f64,
    pub independent_salience: f64,
    pub harmonic_of_lower: bool,
    pub mean_amplitude: f64,
    pub unique_amplitude: f64,
    pub unique_partials: i32,
    pub unique_present_fraction: f64,
    pub unique_clear_partials: i32,
}

pub struct ChordEvidence {
    pub pitches: [ChordPitch; MAX_PITCHES],
    pub pitch_count: usize,
    pub residual: [Candidate; MAX_RESIDUAL],
    pub residual_count: usize,
}

impl Default for ChordEvidence {
    fn default() -> ChordEvidence {
        ChordEvidence { pitches: [ChordPitch::default(); MAX_PITCHES], pitch_count: 0, residual: [Candidate::default(); MAX_RESIDUAL], residual_count: 0 }
    }
}

/// The expected chord: its distinct required pitches, ascending, and the tolerated ones (in the order given).
pub struct ExpectedChord {
    pub distinct: Vec<i32>,
    pub tolerated: Vec<i32>,
    pub sounding: Vec<i32>,
}

impl ExpectedChord {
    pub fn new(required: &[i32], tolerated: &[i32]) -> ExpectedChord {
        let mut distinct: Vec<i32> = required.to_vec();
        distinct.sort_unstable();
        distinct.dedup();
        distinct.truncate(MAX_PITCHES);
        let mut sounding = distinct.clone();
        sounding.extend_from_slice(tolerated);
        ExpectedChord { distinct, tolerated: tolerated.to_vec(), sounding }
    }
}

pub struct ChordEvaluator {
    p: PolyphonicParameters,
    comb: HarmonicComb,
    bin_hz: f64,
    peaks: Vec<f64>,
    working: Vec<f64>,
    partials: Vec<f64>,
}

impl ChordEvaluator {
    pub fn new(p: PolyphonicParameters, bin_hz: f64) -> ChordEvaluator {
        ChordEvaluator {
            p,
            comb: HarmonicComb::new(p, bin_hz),
            bin_hz,
            peaks: vec![0.0; p.fft_size / 2 + 1],
            working: vec![0.0; p.fft_size / 2 + 1],
            partials: vec![0.0; p.harmonics as usize],
        }
    }

    pub fn bin_hz(&self) -> f64 {
        self.bin_hz
    }

    pub fn evaluate(&mut self, frame: &Spectrum, chord: &ExpectedChord, out: &mut ChordEvidence) {
        let length = frame.peak_count;
        for k in 0..length {
            self.peaks[k] = frame.peaks[k] as f64;
        }
        let distinct = &chord.distinct;
        let sounding = &chord.sounding[..];
        out.pitch_count = 0;
        for i in 0..distinct.len() {
            let midi = distinct[i];
            self.working[..length].copy_from_slice(&self.peaks[..length]);
            let mut harmonic_of_lower = false;
            for &lower in &distinct[..i] {
                self.comb.suppress(&mut self.working, length, lower);
                harmonic_of_lower |= self.comb.is_harmonic_of(midi, lower);
            }
            let mean = self.comb.mean_partial_amplitude(&self.peaks, length, midi, None);
            let unique = self.comb.mean_partial_amplitude(&self.peaks, length, midi, Some(sounding));
            out.pitches[i] = ChordPitch {
                midi,
                salience: self.comb.salience(&self.peaks, length, midi, 0),
                independent_salience: self.comb.salience(&self.working, length, midi, 0),
                harmonic_of_lower,
                mean_amplitude: mean.0,
                unique_amplitude: unique.0,
                unique_partials: unique.1,
                unique_present_fraction: 0.0,
                unique_clear_partials: 0,
            };
            out.pitch_count += 1;
        }
        let mut strongest_partial = 0.0f64;
        for &midi in distinct.iter() {
            let n = self.comb.partial_peaks(&self.peaks, length, midi, None, &mut self.partials);
            for &peak in &self.partials[..n] {
                strongest_partial = strongest_partial.max(peak);
            }
        }
        for i in 0..out.pitch_count {
            let n = self.comb.partial_peaks(&self.peaks, length, out.pitches[i].midi, Some(sounding), &mut self.partials);
            let mut clear = 0;
            for &peak in &self.partials[..n] {
                if strongest_partial > 0.0 && peak >= PARTIAL_PRESENCE_RATIO * strongest_partial {
                    clear += 1;
                }
            }
            out.pitches[i].unique_present_fraction = if n > 0 { clear as f64 / n as f64 } else { 0.0 };
            out.pitches[i].unique_clear_partials = clear;
        }

        self.working[..length].copy_from_slice(&self.peaks[..length]);
        let mut excluded = [false; 128];
        for &midi in distinct.iter() {
            self.comb.suppress(&mut self.working, length, midi);
            excluded[midi as usize] = true;
        }
        for &midi in chord.tolerated.iter() {
            self.comb.suppress(&mut self.working, length, midi);
            excluded[midi as usize] = true;
        }
        let floor = 10.0f64.powf(self.p.floor_db / 20.0);
        let mut strongest = 0.0;
        out.residual_count = 0;
        while out.residual_count < MAX_RESIDUAL {
            let mut best = -1;
            let mut best_salience = 0.0;
            for midi in self.p.min_candidate_midi..=self.p.max_candidate_midi {
                if !excluded[midi as usize] {
                    let value = self.comb.salience(&self.working, length, midi, 0);
                    if value > best_salience {
                        best_salience = value;
                        best = midi;
                    }
                }
            }
            if best < 0 || best_salience < floor {
                break;
            }
            best = self.comb.lowest_explaining_subharmonic(&self.working, length, best, best_salience, self.p.subharmonic_ratio, &excluded, self.p.min_candidate_midi);
            best_salience = best_salience.max(self.comb.salience(&self.working, length, best, 0));
            if strongest == 0.0 {
                strongest = best_salience;
            }
            out.residual[out.residual_count] =
                Candidate { midi: best, salience: best_salience, relative_salience: best_salience / strongest, level_db: frame.band_db_of(&self.p, best) };
            out.residual_count += 1;
            excluded[best as usize] = true;
            self.comb.suppress(&mut self.working, length, best);
        }
    }
}

#[cfg(test)]
mod tests {
    use super::super::comb::frequency;
    use super::super::polyphonic::PolyphonicAnalyzer;
    use super::*;

    fn spectrum_of(pitches: &[i32]) -> (PolyphonicParameters, Spectrum) {
        let rate = 48000.0;
        let p = PolyphonicParameters::defaults();
        let mut analyzer = PolyphonicAnalyzer::new(p, rate);
        let samples: Vec<f32> = (0..8192)
            .map(|i| {
                let t = i as f64 / rate;
                let mut v = 0.0;
                for &m in pitches {
                    for h in 1..=6 {
                        v += 0.06 / h as f64 * (2.0 * std::f64::consts::PI * frequency(m) * h as f64 * t).sin();
                    }
                }
                v as f32
            })
            .collect();
        analyzer.push(&samples);
        let mut out = Spectrum::new(&p);
        analyzer.analyze(&mut out);
        (p, out)
    }

    #[test]
    fn a_played_chord_has_its_pitches_and_a_missing_one_shows() {
        let (p, frame) = spectrum_of(&[48, 52, 55]);
        let mut evaluator = ChordEvaluator::new(p, frame.bin_hz);
        let mut evidence = ChordEvidence::default();
        evaluator.evaluate(&frame, &ExpectedChord::new(&[55, 48, 52, 48], &[]), &mut evidence);
        assert_eq!(evidence.pitch_count, 3, "duplicates ignored");
        assert_eq!(evidence.pitches[0].midi, 48, "ascending");
        for pitch in &evidence.pitches[..3] {
            assert!(pitch.unique_amplitude > 0.0 && pitch.unique_present_fraction >= 0.5, "{pitch:?}");
        }
        evaluator.evaluate(&frame, &ExpectedChord::new(&[48, 52, 55, 59], &[]), &mut evidence);
        let missing = evidence.pitches[3];
        assert_eq!(missing.midi, 59);
        assert!(missing.unique_present_fraction < 0.5, "B3 does not sound: {missing:?}");
    }
}
