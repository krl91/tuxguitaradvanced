//! The analysis of one stream, hop by hop (spec 023, research R3): the stages in the order of the audio engine's `OfflineAnalysis` and
//! `AnalysisService` (window 2048, hop 256): the spectrum and attack histories are fed every hop, then, once the window is full, the level,
//! the pitch, the onset, the spectrum, the target and the chord evidence of the window, written as one frame record. Built once; a hop
//! never allocates.

use super::articulation::{ArticulationDetector, ArticulationParameters};
use super::attack::{Attack, AttackAnalyzer, AttackParameters};
use super::chord::{ChordEvaluator, ChordEvidence, ExpectedChord};
use super::frame::{self, Writer};
use super::level::LevelAnalyzer;
use super::polyphonic::{PolyphonicAnalyzer, Spectrum};
use super::target::{ExpectedPitchVerifier, Target, MAX_HYPOTHESES};
use super::yin::Yin;
use super::{Parameters, Tuning};

pub const WINDOW: usize = 2048;
pub const HOP: usize = 256;

/// What the consumers asked for (the optional parts of the records).
pub struct Request<'a> {
    pub spectrum: bool,
    pub attack_candidates: &'a [i32],
    pub expected_pitches: &'a [i32],
    pub chord_required: &'a [i32],
    pub chord_tolerated: &'a [i32],
}

/// The result of one hop.
#[derive(Clone, Copy, Debug, PartialEq)]
pub enum HopResult {
    /// The window is not full yet: no frame.
    NotYet,
    /// A record of this many floats was written.
    Record(usize),
    /// The record did not fit in the buffer given: refused, never cut.
    TooLarge,
}

pub struct Pipeline {
    rate: i32,
    /// The attack analyser's values of the tuning, for each new set of candidates (spec 026).
    attack_parameters: AttackParameters,
    /// The front end's values of the tuning: the live loop gives them to its front end when it swaps this pipeline in (spec 026).
    front_parameters: super::front_end::FrontEndParameters,
    level: LevelAnalyzer,
    yin: Yin,
    articulation: ArticulationDetector,
    polyphonic: PolyphonicAnalyzer,
    spectrum: Spectrum,
    spectrum_on: bool,
    attack: Option<AttackAnalyzer>,
    attack_out: Attack,
    attack_ready: bool,
    verifier: ExpectedPitchVerifier,
    target: Target,
    expected: [i32; MAX_HYPOTHESES],
    expected_count: usize,
    chord: ChordEvaluator,
    chord_out: ChordEvidence,
    expected_chord: Option<ExpectedChord>,
    window: Vec<f32>,
    filled: usize,
    processed: i64,
}

impl Pipeline {
    pub fn new(rate: i32, p: Parameters) -> Pipeline {
        Pipeline::new_tuned(rate, p, Tuning::defaults())
    }

    /// With the tuning array (spec 026); each engine family reads its values from it.
    pub fn new_tuned(rate: i32, p: Parameters, tuning: Tuning) -> Pipeline {
        let polyphonic_parameters = tuning.polyphonic;
        let polyphonic = PolyphonicAnalyzer::new(polyphonic_parameters, rate as f64);
        let bin_hz = polyphonic.bin_hz();
        Pipeline {
            rate,
            attack_parameters: tuning.attack,
            front_parameters: tuning.front,
            level: level_of(&p),
            yin: Yin::new(rate as f64, WINDOW, p.yin_threshold),
            articulation: ArticulationDetector::new(articulation_of(&p)),
            spectrum: Spectrum::new(&polyphonic_parameters),
            polyphonic,
            spectrum_on: false,
            attack: None,
            attack_out: Attack { pitches: Vec::new(), amplitudes: Vec::new(), attack_strength: 0.0, growth: Vec::new(), bin_hz: 0.0 },
            attack_ready: false,
            verifier: ExpectedPitchVerifier::new_tuned(rate as f64, tuning.target_search_cents, tuning.target_periods),
            target: Target::default(),
            expected: [0; MAX_HYPOTHESES],
            expected_count: 0,
            chord: ChordEvaluator::new(polyphonic_parameters, bin_hz),
            chord_out: ChordEvidence::default(),
            expected_chord: None,
            window: vec![0.0; WINDOW],
            filled: 0,
            processed: 0,
        }
    }

    pub fn front_parameters(&self) -> super::front_end::FrontEndParameters {
        self.front_parameters
    }

    pub fn sample_rate(&self) -> i32 {
        self.rate
    }

    /// Sets what the records carry. Building the attack analyser and the chord allocates: this is never called from the hop loop.
    pub fn set_request(&mut self, request: Request) {
        self.spectrum_on = request.spectrum;
        self.set_attack_candidates(request.attack_candidates);
        self.set_expected_pitches(request.expected_pitches);
        self.set_expected_chord(if request.chord_required.is_empty() { None } else { Some(ExpectedChord::new(request.chord_required, request.chord_tolerated)) });
    }

    /// Switched on, the spectrum starts from an empty history, as `AnalysisService` builds a new analyser then.
    pub fn set_spectrum(&mut self, on: bool) {
        if on && !self.spectrum_on {
            self.polyphonic.reset();
        }
        self.spectrum_on = on;
    }

    pub fn set_attack_candidates(&mut self, candidates: &[i32]) {
        if candidates.is_empty() {
            self.attack = None;
            self.attack_ready = false;
        } else {
            let analyzer = AttackAnalyzer::new(self.attack_parameters, self.rate as f64, candidates);
            self.attack_out = analyzer.new_evidence();
            self.attack = Some(analyzer);
            self.attack_ready = false;
        }
    }

    pub fn set_expected_pitches(&mut self, pitches: &[i32]) {
        self.expected_count = pitches.len().min(MAX_HYPOTHESES);
        self.expected[..self.expected_count].copy_from_slice(&pitches[..self.expected_count]);
    }

    pub fn set_expected_chord(&mut self, chord: Option<ExpectedChord>) {
        self.expected_chord = chord;
    }

    /// The live analysis's way to change the attack candidates without allocating: the analyser and its evidence, made by the caller, are
    /// swapped with the current ones, which the caller frees.
    pub fn swap_attack(&mut self, analyzer: &mut Option<AttackAnalyzer>, evidence: &mut Attack) {
        std::mem::swap(&mut self.attack, analyzer);
        std::mem::swap(&mut self.attack_out, evidence);
        self.attack_ready = false;
    }

    /// As `swap_attack`, for the expected chord.
    pub fn swap_expected_chord(&mut self, chord: &mut Option<ExpectedChord>) {
        std::mem::swap(&mut self.expected_chord, chord);
    }

    fn polyphonic_needed(&self) -> bool {
        self.spectrum_on || self.expected_chord.is_some()
    }

    /// One hop of `HOP` samples: a record is written into `out` once the window is full.
    pub fn hop(&mut self, hop: &[f32], capture_sample_end: i64, arrival_nanos: i64, out: &mut [f32]) -> HopResult {
        let timestamp = (self.processed + hop.len().min(HOP) as i64) * 1_000_000_000 / self.rate as i64;
        self.hop_dated(hop, timestamp, capture_sample_end, arrival_nanos, out)
    }

    /// As `hop`, the frame dated `timestamp_nanos` by the caller (the live analysis counts the dropped samples in it, as `AnalysisService`).
    pub fn hop_dated(&mut self, hop: &[f32], timestamp_nanos: i64, capture_sample_end: i64, arrival_nanos: i64, out: &mut [f32]) -> HopResult {
        let n = hop.len().min(HOP);
        self.window.copy_within(n.., 0);
        self.window[WINDOW - n..].copy_from_slice(&hop[..n]);
        self.filled = (self.filled + n).min(WINDOW);
        self.processed += n as i64;
        let polyphonic = self.polyphonic_needed();
        if polyphonic {
            self.polyphonic.push(&hop[..n]);
        }
        if let Some(attack) = self.attack.as_mut() {
            attack.push(&hop[..n]);
            self.attack_ready = attack.analyze(&mut self.attack_out);
        }
        if self.filled < WINDOW {
            return HopResult::NotYet;
        }
        let level = self.level.analyze(&self.window);
        let pitch = self.yin.estimate(&self.window);
        let onset = self.articulation.process(&self.window);
        if polyphonic {
            self.polyphonic.analyze(&mut self.spectrum);
        }
        if self.expected_count > 0 {
            self.verifier.verify(&self.window, &self.expected[..self.expected_count], &mut self.target);
        }
        if let Some(chord) = self.expected_chord.as_ref() {
            self.chord.evaluate(&self.spectrum, chord, &mut self.chord_out);
        }

        let mut w = Writer::new(out);
        w.set_long(frame::TIMESTAMP_NANOS, timestamp_nanos);
        w.set_long(frame::CAPTURE_SAMPLE_END, capture_sample_end);
        w.set_long(frame::ARRIVAL_NANOS, arrival_nanos);
        w.set_double(frame::RMS_DBFS, level.rms_dbfs);
        w.set_double(frame::PEAK_DBFS, level.peak_dbfs);
        w.set_double(frame::CLIPPED_RATIO, level.clipped_ratio);
        w.set_int(frame::LEVEL_STATE, level.state);
        w.set_double(frame::F0_HZ, if pitch.pitched { pitch.f0_hz } else { f64::NAN });
        w.set_double(frame::CONFIDENCE, pitch.probability);
        w.set_int(frame::ONSET, onset as i32);
        let mut parts = 0;
        if self.spectrum_on {
            parts |= frame::PART_SPECTRUM;
            let p = &self.polyphonic.p;
            let s = &self.spectrum;
            w.double(s.bin_hz);
            w.int(p.min_band_midi);
            w.int(s.band_db.len() as i32);
            for &v in &s.band_db {
                w.float(v);
            }
            w.int(p.min_candidate_midi);
            w.int(s.salience.len() as i32);
            for &v in &s.salience {
                w.float(v);
            }
            w.int(s.candidate_count as i32);
            for c in &s.candidates[..s.candidate_count] {
                w.int(c.midi);
                w.double(c.salience);
                w.double(c.relative_salience);
                w.double(c.level_db);
            }
            w.int(s.peak_count as i32);
            for &v in &s.peaks[..s.peak_count] {
                w.float(v);
            }
        }
        if self.expected_count > 0 {
            parts |= frame::PART_TARGET;
            w.double(self.target.window_millis);
            w.int(self.target.count as i32);
            for h in &self.target.hypotheses[..self.target.count] {
                w.int(h.midi);
                w.double(h.score);
            }
        }
        if self.attack.is_some() && self.attack_ready {
            parts |= frame::PART_ATTACK;
            let a = &self.attack_out;
            w.int(a.pitches.len() as i32);
            for &m in &a.pitches {
                w.int(m);
            }
            for &v in &a.amplitudes {
                w.double(v);
            }
            w.double(a.attack_strength);
            w.double(a.bin_hz);
            w.int(a.growth.len() as i32);
            for &v in &a.growth {
                w.double(v);
            }
        }
        if self.expected_chord.is_some() {
            parts |= frame::PART_CHORD;
            let c = &self.chord_out;
            w.int(c.pitch_count as i32);
            for p in &c.pitches[..c.pitch_count] {
                w.int(p.midi);
                w.double(p.salience);
                w.double(p.independent_salience);
                w.int(p.harmonic_of_lower as i32);
                w.double(p.mean_amplitude);
                w.double(p.unique_amplitude);
                w.int(p.unique_partials);
                w.double(p.unique_present_fraction);
                w.int(p.unique_clear_partials);
            }
            w.int(c.residual_count as i32);
            for r in &c.residual[..c.residual_count] {
                w.int(r.midi);
                w.double(r.salience);
                w.double(r.relative_salience);
                w.double(r.level_db);
            }
        }
        w.set_int(frame::PARTS, parts as i32);
        match w.finish() {
            Some(length) => HopResult::Record(length),
            None => HopResult::TooLarge,
        }
    }

    /// Forgets the window and every stage's history (a reconfiguration, as `AnalysisService.reconfigure`).
    pub fn reset(&mut self) {
        self.window.iter_mut().for_each(|s| *s = 0.0);
        self.filled = 0;
        self.processed = 0;
        self.articulation.reset();
        self.polyphonic.reset();
        if let Some(attack) = self.attack.as_mut() {
            attack.reset();
        }
        self.attack_ready = false;
    }
}

fn level_of(p: &Parameters) -> LevelAnalyzer {
    LevelAnalyzer {
        too_low_rms_dbfs: p.level_too_low_dbfs,
        too_high_rms_dbfs: p.level_too_high_dbfs,
        clipped_sample_level: p.level_clip_sample,
        clipping_ratio_threshold: p.level_clip_ratio,
    }
}

fn articulation_of(p: &Parameters) -> ArticulationParameters {
    ArticulationParameters {
        flux_median_factor: p.onset_flux_factor,
        flux_offset: p.onset_flux_offset,
        min_level_db: p.onset_min_level_db,
        refractory_hops: p.onset_refractory_hops as i64,
        gate_db: p.onset_gate_db,
        pre_first: p.onset_pre_first as i64,
        pre_last: p.onset_pre_last as i64,
        post_first: p.onset_post_first as i64,
        post_last: p.onset_post_last as i64,
    }
}

/// The whole recording offline (contract §1 `analyze`): every record, one after another.
pub fn analyze(mono: &[f32], rate: i32, p: Parameters, request: Request) -> Vec<f32> {
    analyze_tuned(mono, rate, p, Tuning::defaults(), request)
}

/// As `analyze`, with the tuning array (spec 026, `analyzeTuned`).
pub fn analyze_tuned(mono: &[f32], rate: i32, p: Parameters, tuning: Tuning, request: Request) -> Vec<f32> {
    let mut pipeline = Pipeline::new_tuned(rate, p, tuning);
    pipeline.set_request(request);
    let mut records = Vec::new();
    let mut record = vec![0f32; frame::CAPACITY];
    let mut offset = 0;
    while offset + HOP <= mono.len() {
        if let HopResult::Record(length) = pipeline.hop(&mono[offset..offset + HOP], -1, 0, &mut record) {
            records.extend_from_slice(&record[..length]);
        }
        offset += HOP;
    }
    records
}

#[cfg(test)]
mod cost {
    use super::*;
    use std::time::Instant;

    fn hop_cost_micros(spectrum: bool, attack: &[i32], rate: i32) -> f64 {
        let samples: Vec<f32> = (0..rate as usize * 4).map(|i| (0.2 * (2.0 * std::f64::consts::PI * 110.0 * i as f64 / rate as f64).sin()) as f32).collect();
        hop_cost_micros_of(&samples, spectrum, attack, rate)
    }

    /// Room noise, as a microphone hears it between notes: a few thousandths of full scale, deterministic.
    fn noise(rate: i32) -> Vec<f32> {
        let mut state = 0x2545F4914F6CDD1Du64;
        (0..rate as usize * 4)
            .map(|_| {
                state ^= state << 13;
                state ^= state >> 7;
                state ^= state << 17;
                ((state >> 11) as f64 / (1u64 << 53) as f64 * 2.0 - 1.0) as f32 * 0.003
            })
            .collect()
    }

    fn hop_cost_micros_of(samples: &[f32], spectrum: bool, attack: &[i32], rate: i32) -> f64 {
        let p = Parameters::defaults();
        let mut pipeline = Pipeline::new(rate, p);
        pipeline.set_request(Request { spectrum, attack_candidates: attack, expected_pitches: &[], chord_required: &[], chord_tolerated: &[] });
        let mut out = vec![0f32; frame::CAPACITY];
        let hops: Vec<&[f32]> = samples.chunks_exact(HOP).collect();
        for h in &hops[..40] {
            pipeline.hop(h, -1, 0, &mut out);
        }
        let mut costs: Vec<f64> = hops[40..]
            .iter()
            .map(|h| {
                let t = Instant::now();
                pipeline.hop(h, -1, 0, &mut out);
                t.elapsed().as_nanos() as f64 / 1000.0
            })
            .collect();
        costs.sort_by(|a, b| a.partial_cmp(b).unwrap());
        costs[(costs.len() as f64 * 0.99) as usize]
    }

    /// The live engine's budget (research R9): the 99th percentile of one hop's analysis under 10 % of a hop, spectrum on and candidates set (T057).
    #[test]
    #[ignore = "timing: run with --ignored on an idle machine"]
    fn one_hop_costs_under_a_tenth_of_a_hop() {
        let rate = 44100;
        let candidates = [40, 45, 47, 50, 52, 55, 57, 59, 64];
        let limit = HOP as f64 * 1e6 / rate as f64 / 10.0;
        let base = hop_cost_micros(false, &[], rate);
        let spectrum = hop_cost_micros(true, &[], rate);
        let attack = hop_cost_micros(false, &candidates, rate);
        let all = hop_cost_micros(true, &candidates, rate);
        println!("p99 µs: level+yin+onset {base:.0}, +spectrum {spectrum:.0}, +attack {attack:.0}, all {all:.0}");
        let quiet = noise(rate);
        let n_base = hop_cost_micros_of(&quiet, false, &[], rate);
        let n_spectrum = hop_cost_micros_of(&quiet, true, &[], rate);
        let n_attack = hop_cost_micros_of(&quiet, false, &candidates, rate);
        let n_all = hop_cost_micros_of(&quiet, true, &candidates, rate);
        println!("noise p99 µs: level+yin+onset {n_base:.0}, +spectrum {n_spectrum:.0}, +attack {n_attack:.0}, all {n_all:.0}");
        assert!(n_all < limit, "noise: 99th percentile {n_all:.0} µs, limit {limit:.0} µs");
        assert!(all < limit, "99th percentile {all:.0} µs, limit {limit:.0} µs");
    }
}
