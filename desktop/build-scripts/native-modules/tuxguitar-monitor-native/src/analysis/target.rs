//! "Is the expected pitch playing?" (port of the audio engine's `ExpectedPitchVerifier`, spec 023 research R3): for each expected pitch,
//! its semitone neighbours and its octave, the periodicity of the newest audio at that pitch's period, penalised when its half period is
//! also periodic. No state; preallocated hypotheses.

pub const SEARCH_CENTS: f64 = 20.0;
pub const PERIODS: i64 = 4;
pub const MIN_WINDOW: i64 = 512;
pub const MAX_HYPOTHESES: usize = 64;

#[derive(Clone, Copy, Debug, Default, PartialEq)]
pub struct Hypothesis {
    pub midi: i32,
    pub score: f64,
}

pub struct Target {
    pub hypotheses: [Hypothesis; MAX_HYPOTHESES],
    pub count: usize,
    pub window_millis: f64,
}

impl Default for Target {
    fn default() -> Target {
        Target { hypotheses: [Hypothesis::default(); MAX_HYPOTHESES], count: 0, window_millis: 0.0 }
    }
}

pub struct ExpectedPitchVerifier {
    sample_rate: f64,
    search_cents: f64,
    periods: i64,
}

impl ExpectedPitchVerifier {
    pub fn new(sample_rate: f64) -> ExpectedPitchVerifier {
        ExpectedPitchVerifier::new_tuned(sample_rate, SEARCH_CENTS, PERIODS)
    }

    /// With the search and the periods of the tuning array (spec 026, `pitch.target-search`, `pitch.target-periods`).
    pub fn new_tuned(sample_rate: f64, search_cents: f64, periods: i64) -> ExpectedPitchVerifier {
        ExpectedPitchVerifier { sample_rate, search_cents, periods }
    }

    pub fn verify(&self, audio: &[f32], expected: &[i32], out: &mut Target) {
        out.count = 0;
        let mut longest = 0;
        for &e in expected {
            for midi in [e, e - 1, e + 1, e + 12] {
                if out.count < MAX_HYPOTHESES && !out.hypotheses[..out.count].iter().any(|h| h.midi == midi) {
                    let used = self.window_for(midi, audio.len() as i64);
                    longest = longest.max(used);
                    out.hypotheses[out.count] = Hypothesis { midi, score: self.score(audio, midi, used) };
                    out.count += 1;
                }
            }
        }
        out.window_millis = 1000.0 * longest as f64 / self.sample_rate;
    }

    fn window_for(&self, midi: i32, available: i64) -> i64 {
        let period = (self.sample_rate / frequency(midi)).ceil() as i64;
        available.min(MIN_WINDOW.max(self.periods * period + period))
    }

    fn score(&self, audio: &[f32], midi: i32, window: i64) -> f64 {
        let period = self.sample_rate / frequency(midi);
        let own = periodicity(audio, window, period, self.search_cents, self.periods);
        let half = periodicity(audio, window, period / 2.0, self.search_cents, self.periods);
        let penalty = half.max(0.0);
        (own * (1.0 - penalty * penalty)).clamp(0.0, 1.0)
    }
}

fn periodicity(audio: &[f32], window: i64, period: f64, search_cents: f64, periods: i64) -> f64 {
    let low = (period * 2.0f64.powf(-search_cents / 1200.0)).floor() as i64;
    let high = (period * 2.0f64.powf(search_cents / 1200.0)).ceil() as i64;
    let mut best = 0.0f64;
    let mut lag = low.max(2);
    while lag <= high {
        let length = (periods * super::comb::java_round(period)).min(window - lag);
        if length >= 32 {
            let start = audio.len() as i64 - length;
            let mut cross = 0.0;
            let mut energy_now = 0.0;
            let mut energy_before = 0.0;
            for i in start as usize..audio.len() {
                let now = audio[i] as f64;
                let before = audio[i - lag as usize] as f64;
                cross += now * before;
                energy_now += now * now;
                energy_before += before * before;
            }
            let norm = (energy_now * energy_before).sqrt();
            if norm > 1e-9 {
                best = best.max(cross / norm);
            }
        }
        lag += 1;
    }
    best
}

fn frequency(midi: i32) -> f64 {
    440.0 * 2.0f64.powf((midi - 69) as f64 / 12.0)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn the_played_pitch_beats_its_neighbours_and_octave() {
        let rate = 48000.0;
        let audio: Vec<f32> = (0..2048).map(|i| (0.5 * (2.0 * std::f64::consts::PI * frequency(57) * i as f64 / rate).sin()) as f32).collect();
        let mut out = Target::default();
        ExpectedPitchVerifier::new(rate).verify(&audio, &[57], &mut out);
        assert_eq!(out.count, 4);
        assert_eq!(out.hypotheses[0].midi, 57);
        let own = out.hypotheses[0].score;
        assert!(own > 0.9, "{own}");
        for h in &out.hypotheses[1..4] {
            assert!(h.score < own, "{h:?}");
        }
        assert!(out.window_millis > 0.0 && out.window_millis <= 1000.0 * 2048.0 / rate);
    }
}
