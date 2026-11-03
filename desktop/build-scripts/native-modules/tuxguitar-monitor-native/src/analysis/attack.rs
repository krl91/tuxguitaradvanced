//! Which pitches were just struck (port of the audio engine's `AttackAnalyzer` and `NnlsSolver`, spec 023 research R3): the growth of the
//! spectrum over `lag_hops` hops, explained as a non-negative combination of harmonic templates of the candidate pitches (Lawson-Hanson on
//! the normal equations). Streaming, one `analyze` per hop, preallocated.

use super::fft::RealFft;

#[derive(Clone, Copy, Debug, PartialEq)]
pub struct AttackParameters {
    pub fft_size: usize,
    pub lag_hops: usize,
    pub max_hz: f64,
    pub partials: i32,
    pub beta: f64,
    pub sigma_bins: f64,
}

impl AttackParameters {
    /// `AttackAnalyzer.Parameters.defaults()`.
    pub fn defaults() -> AttackParameters {
        AttackParameters { fft_size: 4096, lag_hops: 22, max_hz: 5000.0, partials: 14, beta: 0.3, sigma_bins: 1.2 }
    }
}

/// The evidence of one hop (the fields of `AttackEvidence`).
pub struct Attack {
    pub pitches: Vec<i32>,
    pub amplitudes: Vec<f64>,
    pub attack_strength: f64,
    pub growth: Vec<f64>,
    pub bin_hz: f64,
}

const EPSILON: f64 = 1e-12;
const RIDGE: f64 = 1e-9;

struct NnlsSolver {
    size: usize,
    gram: Vec<Vec<f64>>,
    x: Vec<f64>,
    passive: Vec<bool>,
    index: Vec<usize>,
    system: Vec<Vec<f64>>,
    z: Vec<f64>,
}

impl NnlsSolver {
    fn new(gram: Vec<Vec<f64>>) -> NnlsSolver {
        let size = gram.len();
        NnlsSolver {
            size,
            gram,
            x: vec![0.0; size],
            passive: vec![false; size],
            index: vec![0; size],
            system: vec![vec![0.0; size + 1]; size],
            z: vec![0.0; size],
        }
    }

    fn solve(&mut self, b: &[f64]) -> &[f64] {
        let n_all = self.size;
        self.x.iter_mut().for_each(|v| *v = 0.0);
        self.passive.iter_mut().for_each(|v| *v = false);
        let mut scale = 0.0f64;
        for value in b {
            scale = scale.max(value.abs());
        }
        let tolerance = 1e-9 * scale.max(1e-12);
        for _outer in 0..3 * n_all {
            let mut best: isize = -1;
            let mut best_gradient = tolerance;
            for i in 0..n_all {
                let mut g = b[i];
                for j in 0..n_all {
                    g -= self.gram[i][j] * self.x[j];
                }
                if !self.passive[i] && g > best_gradient {
                    best_gradient = g;
                    best = i as isize;
                }
            }
            if best < 0 {
                break;
            }
            self.passive[best as usize] = true;
            for _inner in 0..n_all {
                let mut n = 0;
                for i in 0..n_all {
                    if self.passive[i] {
                        self.index[n] = i;
                        n += 1;
                    }
                }
                for r in 0..n {
                    for c in 0..n {
                        self.system[r][c] = self.gram[self.index[r]][self.index[c]] + if r == c { RIDGE } else { 0.0 };
                    }
                    self.system[r][n] = b[self.index[r]];
                }
                self.gauss(n);
                let mut all_positive = true;
                for r in 0..n {
                    if self.z[r] <= EPSILON {
                        all_positive = false;
                    }
                }
                if all_positive {
                    self.x.iter_mut().for_each(|v| *v = 0.0);
                    for r in 0..n {
                        self.x[self.index[r]] = self.z[r];
                    }
                    break;
                }
                let mut alpha = f64::MAX;
                for r in 0..n {
                    if self.z[r] <= EPSILON {
                        let current = self.x[self.index[r]];
                        let step = current - self.z[r];
                        if step > 1e-15 {
                            alpha = alpha.min(current / step);
                        }
                    }
                }
                if alpha == f64::MAX {
                    alpha = 0.0;
                }
                for r in 0..n {
                    let i = self.index[r];
                    self.x[i] += alpha * (self.z[r] - self.x[i]);
                }
                for i in 0..n_all {
                    if self.passive[i] && self.x[i] <= EPSILON {
                        self.passive[i] = false;
                        self.x[i] = 0.0;
                    }
                }
            }
        }
        &self.x
    }

    /// Gaussian elimination with partial pivoting on the n x (n+1) system; the solution goes to z.
    fn gauss(&mut self, n: usize) {
        for i in 0..n {
            let mut pivot = i;
            for r in i + 1..n {
                if self.system[r][i].abs() > self.system[pivot][i].abs() {
                    pivot = r;
                }
            }
            self.system.swap(i, pivot);
            let mut value = self.system[i][i];
            if value.abs() < 1e-12 {
                value = 1e-12;
            }
            for r in i + 1..n {
                let factor = self.system[r][i] / value;
                for c in i..=n {
                    let above = self.system[i][c];
                    self.system[r][c] -= factor * above;
                }
            }
        }
        for i in (0..n).rev() {
            let mut sum = self.system[i][n];
            for c in i + 1..n {
                sum -= self.system[i][c] * self.z[c];
            }
            let mut value = self.system[i][i];
            if value.abs() < 1e-12 {
                value = 1e-12;
            }
            self.z[i] = sum / value;
        }
    }
}

pub struct AttackAnalyzer {
    p: AttackParameters,
    pitches: Vec<i32>,
    fft: RealFft,
    hann: Vec<f64>,
    history: Vec<f32>,
    input: Vec<f64>,
    magnitudes: Vec<f64>,
    length: usize,
    ring: Vec<Vec<f64>>,
    templates: Vec<Vec<f64>>,
    difference: Vec<f64>,
    projection: Vec<f64>,
    solver: NnlsSolver,
    bin_hz: f64,
    history_position: usize,
    hops: usize,
}

impl AttackAnalyzer {
    pub fn new(p: AttackParameters, sample_rate: f64, pitches: &[i32]) -> AttackAnalyzer {
        let mut sorted = pitches.to_vec();
        sorted.sort_unstable();
        sorted.dedup();
        let size = p.fft_size;
        let hann = (0..size).map(|i| 0.5 - 0.5 * (2.0 * std::f64::consts::PI * i as f64 / size as f64).cos()).collect();
        let bin_hz = sample_rate / size as f64;
        let length = (size / 2 - 1).min((p.max_hz / bin_hz) as usize);
        let templates = build_templates(&p, &sorted, bin_hz, length);
        let gram = gram(&templates);
        AttackAnalyzer {
            p,
            fft: RealFft::new(size),
            hann,
            history: vec![0.0; size],
            input: vec![0.0; size],
            magnitudes: vec![0.0; size / 2 + 1],
            length,
            ring: vec![vec![0.0; length]; p.lag_hops + 1],
            difference: vec![0.0; length],
            projection: vec![0.0; sorted.len()],
            solver: NnlsSolver::new(gram),
            templates,
            bin_hz,
            history_position: 0,
            hops: 0,
            pitches: sorted,
        }
    }

    pub fn pitches(&self) -> &[i32] {
        &self.pitches
    }

    pub fn push(&mut self, samples: &[f32]) {
        let n = self.history.len();
        for s in samples {
            self.history[self.history_position] = *s;
            self.history_position = (self.history_position + 1) % n;
        }
    }

    /// The evidence of the newest hop into `out` (its vectors sized by `Attack::for_analyzer`); false until `lag_hops + 1` hops were seen.
    pub fn analyze(&mut self, out: &mut Attack) -> bool {
        let size = self.p.fft_size;
        for i in 0..size {
            self.input[i] = self.history[(self.history_position + i) % size] as f64 * self.hann[i];
        }
        self.fft.magnitudes(&self.input, &mut self.magnitudes, 4.0 / size as f64);
        let slots = self.ring.len();
        let now_slot = self.hops % slots;
        self.ring[now_slot].copy_from_slice(&self.magnitudes[..self.length]);
        self.hops += 1;
        if self.hops <= self.p.lag_hops {
            return false;
        }
        let before_slot = (self.hops - 1 - self.p.lag_hops) % slots;
        let mut grown = 0.0;
        let mut total = 0.0;
        for k in 0..self.length {
            let now = self.ring[now_slot][k];
            let d = now - self.ring[before_slot][k];
            self.difference[k] = if d > 0.0 { d } else { 0.0 };
            grown += self.difference[k];
            total += now;
        }
        for u in 0..self.pitches.len() {
            let mut sum = 0.0;
            for k in 0..self.length {
                sum += self.templates[u][k] * self.difference[k];
            }
            self.projection[u] = sum;
        }
        let solution = self.solver.solve(&self.projection);
        out.pitches.copy_from_slice(&self.pitches);
        out.amplitudes.copy_from_slice(solution);
        out.attack_strength = if total > 0.0 { grown / total } else { 0.0 };
        out.growth.copy_from_slice(&self.difference);
        out.bin_hz = self.bin_hz;
        true
    }

    pub fn new_evidence(&self) -> Attack {
        Attack { pitches: vec![0; self.pitches.len()], amplitudes: vec![0.0; self.pitches.len()], attack_strength: 0.0, growth: vec![0.0; self.length], bin_hz: self.bin_hz }
    }
}

fn build_templates(p: &AttackParameters, pitches: &[i32], bin_hz: f64, length: usize) -> Vec<Vec<f64>> {
    let mut result = vec![vec![0.0; length]; pitches.len()];
    for (u, &pitch) in pitches.iter().enumerate() {
        let f0 = 440.0 * 2.0f64.powf((pitch - 69) as f64 / 12.0);
        for h in 1..=p.partials {
            let center = f0 * h as f64 / bin_hz;
            if center >= length as f64 - 2.0 {
                break;
            }
            let weight = 1.0 / (h as f64).powf(p.beta);
            let from = 1i64.max((center - 3.0 * p.sigma_bins - 1.0) as i64);
            let to = (length as i64 - 1).min((center + 3.0 * p.sigma_bins + 1.0) as i64);
            for k in from..=to {
                let d = (k as f64 - center) / p.sigma_bins;
                result[u][k as usize] += weight * (-0.5 * d * d).exp();
            }
        }
        let mut norm = 0.0;
        for k in 0..length {
            norm += result[u][k] * result[u][k];
        }
        let norm = norm.max(1e-12).sqrt();
        for k in 0..length {
            result[u][k] /= norm;
        }
    }
    result
}

fn gram(templates: &[Vec<f64>]) -> Vec<Vec<f64>> {
    let n = templates.len();
    let mut g = vec![vec![0.0; n]; n];
    for i in 0..n {
        for j in i..n {
            let mut sum = 0.0;
            for k in 0..templates[i].len() {
                sum += templates[i][k] * templates[j][k];
            }
            g[i][j] = sum;
            g[j][i] = sum;
        }
    }
    g
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn a_new_note_over_a_ringing_one_is_the_one_explained() {
        let rate = 48000.0;
        let hop = 256;
        let mut analyzer = AttackAnalyzer::new(AttackParameters::defaults(), rate, &[45, 52, 57, 52]);
        assert_eq!(analyzer.pitches(), &[45, 52, 57]);
        let mut evidence = analyzer.new_evidence();
        let f = |m: i32| 440.0 * 2.0f64.powf((m - 69) as f64 / 12.0);
        let mut last = false;
        for h in 0..60 {
            let block: Vec<f32> = (0..hop)
                .map(|i| {
                    let t = (h * hop + i) as f64 / rate;
                    let mut v = 0.2 * (2.0 * std::f64::consts::PI * f(45) * t).sin() + 0.1 * (4.0 * std::f64::consts::PI * f(45) * t).sin();
                    if h >= 40 {
                        v += 0.2 * (2.0 * std::f64::consts::PI * f(57) * t).sin() + 0.1 * (4.0 * std::f64::consts::PI * f(57) * t).sin();
                    }
                    v as f32
                })
                .collect();
            analyzer.push(&block);
            last = analyzer.analyze(&mut evidence);
        }
        assert!(last);
        // A2's second harmonic is A3 itself, so A2 keeps part of the new energy: A3 is still the one explained most
        assert!(evidence.amplitudes[2] > 2.0 * evidence.amplitudes[0], "A3 just struck, A2 rings: {:?}", evidence.amplitudes);
        assert!(evidence.attack_strength > 0.1);
    }
}
