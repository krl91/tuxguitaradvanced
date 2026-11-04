//! The evidence for an expected chord in one spectrum (port of the audio engine's `ChordEvidence.evaluate`, spec 023 research R2 and R3):
//! how strongly each expected pitch is present, and which other pitches remain once the expected and tolerated ones are explained. It
//! decides nothing; the practice's rules do. Preallocated: evaluating a chord never allocates.

use super::comb::HarmonicComb;
use super::polyphonic::{Candidate, PolyphonicParameters, Spectrum};

pub const PARTIAL_PRESENCE_RATIO: f64 = 0.05;
pub const MAX_PITCHES: usize = 16;
pub const MAX_RESIDUAL: usize = 3;
/// The chord a whole tone away explains the partials this many times better than the expected one, so the expected one is not heard.
pub const NEIGHBOUR_MARGIN: f64 = 1.5;

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

    /// The harmonic sum of the expected pitches against that of the same chord a whole tone lower and higher. The chord a whole tone
    /// away shares a partial of each pitch with the expected one (the ninth of the lower note is the eighth of the higher), which is enough for the unique
    /// partials when a distortion gives every partial; its harmonic sum tells it, as its fundamentals and low partials are the ones that sound (spec 023,
    /// experiment A, `benchmark/chord-experiments.md`).
    fn a_whole_tone_away_explains_better(&self, distinct: &[i32], length: usize) -> bool {
        let own: f64 = distinct.iter().map(|&midi| self.comb.salience(&self.peaks, length, midi, 0)).sum();
        for shift in [-2, 2] {
            if distinct.iter().any(|&midi| midi + shift < self.p.min_candidate_midi || midi + shift > self.p.max_candidate_midi) {
                continue;
            }
            let neighbour: f64 = distinct.iter().map(|&midi| self.comb.salience(&self.peaks, length, midi + shift, 0)).sum();
            if neighbour > NEIGHBOUR_MARGIN * own {
                return true;
            }
        }
        false
    }

    /// A low string through a microphone gives little of its fundamental, while its second and third partials sound. The pitch is heard when its
    /// fundamental is under the presence ratio and both those partials are above it, neither of them a partial of another pitch that sounds (expected
    /// or tolerated; spec 023, experiment C).
    fn heard_by_its_second_and_third_partials(&self, sounding: &[i32], midi: i32, length: usize, strongest_partial: f64) -> bool {
        if strongest_partial <= 0.0 {
            return false;
        }
        let f0 = self.comb.frequency_of(midi);
        let ratio = self.comb.tolerance_ratio();
        let present = |h: f64| self.comb.peak_around(&self.peaks, length, f0 * h, ratio) >= PARTIAL_PRESENCE_RATIO * strongest_partial;
        if present(1.0) || !present(2.0) || !present(3.0) {
            return false;
        }
        for h in [2.0, 3.0] {
            let f = f0 * h;
            for &other in sounding.iter().filter(|&&other| other != midi) {
                let g = self.comb.frequency_of(other);
                let k = super::comb::java_round(f / g);
                if k >= 1 && (1200.0 * (f / (k as f64 * g)).ln() / 2.0f64.ln()).abs() <= self.p.harmonic_tolerance_cents {
                    return false;
                }
            }
        }
        true
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

        for i in 0..out.pitch_count {
            if self.heard_by_its_second_and_third_partials(sounding, out.pitches[i].midi, length, strongest_partial) {
                let pitch = &mut out.pitches[i];
                pitch.unique_clear_partials = pitch.unique_clear_partials.max(2);
                pitch.unique_present_fraction = pitch.unique_present_fraction.max(1.0 / 3.0);
            }
        }

        if self.a_whole_tone_away_explains_better(distinct, length) {
            for pitch in out.pitches[..out.pitch_count].iter_mut() {
                pitch.unique_present_fraction = 0.0;
                pitch.unique_clear_partials = 0;
            }
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

    /// A chord through a distortion: eight partials decaying as 1/h, then a soft clip of gain `drive`, which adds the products of a real distortion.
    fn distorted_spectrum_of(pitches: &[i32], drive: f64) -> (PolyphonicParameters, Spectrum) {
        let rate = 48000.0;
        let p = PolyphonicParameters::defaults();
        let mut analyzer = PolyphonicAnalyzer::new(p, rate);
        let samples: Vec<f32> = (0..8192)
            .map(|i| {
                let t = i as f64 / rate;
                let mut v = 0.0;
                for &m in pitches {
                    for h in 1..=8 {
                        v += 0.05 / h as f64 * (2.0 * std::f64::consts::PI * frequency(m) * h as f64 * t).sin();
                    }
                }
                (0.5 * (drive * v).tanh() / drive.tanh()) as f32
            })
            .collect();
        analyzer.push(&samples);
        let mut out = Spectrum::new(&p);
        analyzer.analyze(&mut out);
        (p, out)
    }

    /// The rule of ChordMatcher: a verifiable pitch (two unique partials) is missing unless two of them, and a third of them, clearly sound.
    fn missing(evidence: &ChordEvidence) -> usize {
        evidence.pitches[..evidence.pitch_count]
            .iter()
            .filter(|p| p.unique_partials >= 2 && !(p.unique_clear_partials >= 2 && p.unique_present_fraction >= 1.0 / 3.0))
            .count()
    }

    /// Spec 023, experiment A: a chord played a whole tone lower than the one expected is not evidence of the expected one, while the chord played is. A
    /// distorted G power chord is the case that fails without it: the ninth partial of each note is the eighth of the note a whole tone higher (9/8 is
    /// 2.04 semitones), and the distortion gives that chord every partial it needs.
    #[test]
    fn a_chord_a_whole_tone_lower_than_expected_is_not_its_evidence() {
        for (played, label, drive) in [(vec![43, 50, 55], "G power chord", 16.0), (vec![45, 52, 57, 61, 64], "A major", 16.0),
            (vec![48, 52, 55], "C major", 1.0)] {
            let (p, frame) = distorted_spectrum_of(&played, drive);
            let mut evaluator = ChordEvaluator::new(p, frame.bin_hz);
            let mut evidence = ChordEvidence::default();
            evaluator.evaluate(&frame, &ExpectedChord::new(&played, &[]), &mut evidence);
            assert_eq!(missing(&evidence), 0, "{label} (drive {drive}) played and expected: no pitch missing");
            let higher: Vec<i32> = played.iter().map(|m| m + 2).collect();
            evaluator.evaluate(&frame, &ExpectedChord::new(&higher, &[]), &mut evidence);
            assert!(missing(&evidence) > 0, "{label} (drive {drive}) played, a whole tone higher expected: a pitch missing");
        }
    }

    /// A spectrum of the given pitches, each with its own partial amplitudes (index 0 is the fundamental).
    fn spectrum_with_partials(notes: &[(i32, &[f64])]) -> (PolyphonicParameters, Spectrum) {
        let rate = 48000.0;
        let p = PolyphonicParameters::defaults();
        let mut analyzer = PolyphonicAnalyzer::new(p, rate);
        let samples: Vec<f32> = (0..8192)
            .map(|i| {
                let t = i as f64 / rate;
                let mut v = 0.0;
                for &(m, partials) in notes {
                    for (k, &a) in partials.iter().enumerate() {
                        v += a * (2.0 * std::f64::consts::PI * frequency(m) * (k + 1) as f64 * t).sin();
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

    /// A low string through a microphone gives little of its fundamental and of its high partials, but its second and third partials
    /// sound. With a loud treble note in the chord, they are the only ones above the presence ratio: the low pitch is heard.
    #[test]
    fn a_low_string_with_a_weak_fundamental_is_heard_by_its_second_and_third_partials() {
        let low: &[f64] = &[0.002, 0.08, 0.06, 0.002, 0.002, 0.002, 0.002, 0.002];
        let high: &[f64] = &[0.5, 0.15, 0.08, 0.04];
        let (p, frame) = spectrum_with_partials(&[(40, low), (55, high)]);
        let mut evaluator = ChordEvaluator::new(p, frame.bin_hz);
        let mut evidence = ChordEvidence::default();
        evaluator.evaluate(&frame, &ExpectedChord::new(&[40, 55], &[]), &mut evidence);
        assert_eq!(missing(&evidence), 0, "E2 is heard: {:?}", &evidence.pitches[..evidence.pitch_count]);
    }

    /// Spec 023, experiment C: a second partial that a lower expected pitch explains is not evidence of the pitch above it.
    #[test]
    fn partials_a_lower_expected_pitch_explains_are_not_evidence() {
        let low: &[f64] = &[0.5, 0.2, 0.12, 0.08, 0.05, 0.03];
        let (p, frame) = spectrum_with_partials(&[(40, low)]);
        let mut evaluator = ChordEvaluator::new(p, frame.bin_hz);
        let mut evidence = ChordEvidence::default();
        evaluator.evaluate(&frame, &ExpectedChord::new(&[40, 47], &[]), &mut evidence);
        assert_eq!(missing(&evidence), 1, "B2 is not played: {:?}", &evidence.pitches[..evidence.pitch_count]);
    }

    /// Spec 023, experiment C: the second and third partials of a missing low string that other expected strings play (E3 and B3 for E2 in E major) are
    /// not evidence of it.
    #[test]
    fn partials_another_expected_pitch_plays_are_not_evidence() {
        let (p, frame) = spectrum_of(&[47, 52, 56, 59, 64]);
        let mut evaluator = ChordEvaluator::new(p, frame.bin_hz);
        let mut evidence = ChordEvidence::default();
        evaluator.evaluate(&frame, &ExpectedChord::new(&[40, 47, 52, 56, 59, 64], &[]), &mut evidence);
        assert!(missing(&evidence) >= 1, "E2 is not played: {:?}", &evidence.pitches[..evidence.pitch_count]);
    }
}
