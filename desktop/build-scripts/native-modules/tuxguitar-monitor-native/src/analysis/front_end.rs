//! The input front end applied to every hop before the analysis (port of the audio engine's `frontend` package, spec 023 research R3):
//! the input gain, then, with the noise reduction on, the 40 Hz high-pass, the noise gate and optionally the spectral subtraction. With a
//! neutral gain and the noise reduction off a hop is left bit for bit as it came.

use super::fft::RealFft;
use rustfft::num_complex::Complex;

pub const NEUTRAL_GAIN: f64 = 1.0;

struct HighPass {
    b0: f64,
    b1: f64,
    b2: f64,
    a1: f64,
    a2: f64,
    x1: f64,
    x2: f64,
    y1: f64,
    y2: f64,
}

const CORNER_HZ: f64 = 40.0;

impl HighPass {
    fn new(sample_rate: f64, corner_hz: f64) -> HighPass {
        let w0 = 2.0 * std::f64::consts::PI * corner_hz / sample_rate;
        let q = 1.0 / 2.0f64.sqrt();
        let alpha = w0.sin() / (2.0 * q);
        let cos = w0.cos();
        let a0 = 1.0 + alpha;
        let b0 = ((1.0 + cos) / 2.0) / a0;
        HighPass { b0, b1: (-(1.0 + cos)) / a0, b2: b0, a1: (-2.0 * cos) / a0, a2: (1.0 - alpha) / a0, x1: 0.0, x2: 0.0, y1: 0.0, y2: 0.0 }
    }

    fn process(&mut self, hop: &mut [f32]) {
        for s in hop.iter_mut() {
            let x = *s as f64;
            let y = self.b0 * x + self.b1 * self.x1 + self.b2 * self.x2 - self.a1 * self.y1 - self.a2 * self.y2;
            self.x2 = self.x1;
            self.x1 = x;
            self.y2 = self.y1;
            self.y1 = y;
            *s = y as f32;
        }
    }

    fn reset(&mut self) {
        self.x1 = 0.0;
        self.x2 = 0.0;
        self.y1 = 0.0;
        self.y2 = 0.0;
    }
}

const MINIMUM_RMS: f64 = 1.5e-5;
const RISE_DB_PER_SECOND: f64 = 0.5;
const FALL_RATE: f64 = 0.2;
const TRUSTED_AFTER_HOPS: i64 = 188;

#[derive(Clone, Copy, PartialEq)]
enum Source {
    None,
    LevelCheck,
    QuietTracking,
}

struct NoiseEstimator {
    rise_factor_per_hop: f64,
    fall_rate: f64,
    trusted_after_hops: i64,
    floor_rms: f64,
    hops: i64,
    source: Source,
}

impl NoiseEstimator {
    fn new(sample_rate: f64, hop_size: usize, p: &FrontEndParameters) -> NoiseEstimator {
        let seconds_per_hop = hop_size as f64 / sample_rate;
        NoiseEstimator {
            rise_factor_per_hop: 10.0f64.powf(p.noise_rise_db_per_second * seconds_per_hop / 20.0),
            fall_rate: p.noise_fall_rate,
            trusted_after_hops: p.noise_trusted_hops,
            floor_rms: 0.0,
            hops: 0,
            source: Source::None,
        }
    }

    fn update(&mut self, hop_rms: f64) {
        let level = MINIMUM_RMS.max(hop_rms);
        if self.hops == 0 || self.floor_rms <= 0.0 {
            self.floor_rms = level;
        } else if level < self.floor_rms {
            self.floor_rms += self.fall_rate * (level - self.floor_rms);
        } else {
            self.floor_rms = level.min(self.floor_rms * self.rise_factor_per_hop);
        }
        self.hops += 1;
        if self.source != Source::LevelCheck {
            self.source = Source::QuietTracking;
        }
    }

    fn seed(&mut self, silent_rms: f64) {
        self.floor_rms = MINIMUM_RMS.max(silent_rms);
        self.hops = self.hops.max(1);
        self.source = Source::LevelCheck;
    }

    fn trusted(&self) -> bool {
        self.source == Source::LevelCheck || self.hops >= self.trusted_after_hops
    }

    fn reset(&mut self) {
        self.floor_rms = 0.0;
        self.hops = 0;
        self.source = Source::None;
    }
}

fn rms(samples: &[f32]) -> f64 {
    let mut sum = 0.0;
    for s in samples {
        sum += (*s as f64) * (*s as f64);
    }
    (sum / samples.len().max(1) as f64).sqrt()
}

const OPEN_MARGIN_DB: f64 = 9.0;
const CLOSE_MARGIN_DB: f64 = 5.0;
const REDUCTION_DB: f64 = 20.0;
const HOLD_HOPS: i32 = 19;

struct NoiseGate {
    open_factor: f64,
    close_factor: f64,
    hold_hops: i32,
    reduction: f64,
    open: bool,
    hold: i32,
    last_gain: f64,
}

impl NoiseGate {
    fn new(p: &FrontEndParameters) -> NoiseGate {
        NoiseGate {
            open_factor: 10.0f64.powf(p.gate_open_db / 20.0),
            close_factor: 10.0f64.powf(p.gate_close_db / 20.0),
            hold_hops: p.gate_hold_hops,
            reduction: 10.0f64.powf(-p.gate_reduction_db / 20.0),
            open: false,
            hold: 0,
            last_gain: 1.0,
        }
    }

    fn process(&mut self, hop: &mut [f32], estimator: &mut NoiseEstimator) {
        let level = rms(hop);
        estimator.update(level);
        let floor = estimator.floor_rms;
        let open_threshold = floor * self.open_factor;
        let close_threshold = floor * self.close_factor;
        if level > open_threshold {
            self.open = true;
            self.hold = self.hold_hops;
        } else if self.open {
            if level < close_threshold {
                self.hold -= 1;
                if self.hold <= 0 {
                    self.open = false;
                }
            } else {
                self.hold = self.hold_hops;
            }
        }
        let target = if self.open || !estimator.trusted() { 1.0 } else { self.reduction };
        let length = hop.len();
        if target < self.last_gain {
            for (i, s) in hop.iter_mut().enumerate() {
                let gain = self.last_gain + (target - self.last_gain) * (i + 1) as f64 / length as f64;
                *s = (*s as f64 * gain) as f32;
            }
        } else if target < 1.0 {
            for s in hop.iter_mut() {
                *s = (*s as f64 * target) as f32;
            }
        }
        self.last_gain = target;
    }

    fn reset(&mut self) {
        self.open = false;
        self.hold = 0;
        self.last_gain = 1.0;
    }
}

const FRAME: usize = 512;
const HOP: usize = FRAME / 2;
const ALPHA: f64 = 1.5;
const BETA: f64 = 0.05;
const LEARN_RATE: f64 = 0.1;

struct SpectralSubtractor {
    alpha: f64,
    beta: f64,
    learn_rate: f64,
    window: Vec<f64>,
    noise: Vec<f64>,
    spectrum: Vec<Complex<f64>>,
    previous: Vec<f64>,
    overlap: Vec<f64>,
    fft: RealFft,
    learned_once: bool,
}

impl SpectralSubtractor {
    fn new() -> SpectralSubtractor {
        SpectralSubtractor {
            alpha: ALPHA,
            beta: BETA,
            learn_rate: LEARN_RATE,
            window: (0..FRAME).map(|i| 0.5 - 0.5 * (2.0 * std::f64::consts::PI * i as f64 / FRAME as f64).cos()).collect(),
            noise: vec![0.0; FRAME / 2 + 1],
            spectrum: vec![Complex::new(0.0, 0.0); FRAME],
            previous: vec![0.0; HOP],
            overlap: vec![0.0; HOP],
            fft: RealFft::new(FRAME),
            learned_once: false,
        }
    }

    fn process(&mut self, hop: &mut [f32], quiet: bool) {
        for i in 0..HOP {
            self.spectrum[i] = Complex::new(self.previous[i] * self.window[i], 0.0);
            self.spectrum[HOP + i] = Complex::new(hop[i] as f64 * self.window[HOP + i], 0.0);
            self.previous[i] = hop[i] as f64;
        }
        self.fft.forward(&mut self.spectrum);
        for k in 0..=FRAME / 2 {
            let c = self.spectrum[k];
            let magnitude = c.re.hypot(c.im);
            if quiet {
                self.noise[k] = if self.learned_once { (1.0 - self.learn_rate) * self.noise[k] + self.learn_rate * magnitude } else { magnitude };
            }
            let kept = (magnitude - self.alpha * self.noise[k]).max(self.beta * magnitude);
            let scale = if magnitude > 0.0 { kept / magnitude } else { 0.0 };
            self.spectrum[k] = Complex::new(c.re * scale, c.im * scale);
            if k > 0 && k < FRAME / 2 {
                self.spectrum[FRAME - k] = Complex::new(self.spectrum[k].re, -self.spectrum[k].im);
            }
        }
        if quiet {
            self.learned_once = true;
        }
        self.fft.inverse(&mut self.spectrum);
        for i in 0..HOP {
            hop[i] = (self.overlap[i] + self.spectrum[i].re) as f32;
            self.overlap[i] = self.spectrum[HOP + i].re;
        }
    }

    fn set_values(&mut self, p: &FrontEndParameters) {
        self.alpha = p.subtraction_alpha;
        self.beta = p.subtraction_beta;
        self.learn_rate = p.subtraction_learn_rate;
    }

    fn reset(&mut self) {
        self.previous.iter_mut().for_each(|v| *v = 0.0);
        self.overlap.iter_mut().for_each(|v| *v = 0.0);
        self.noise.iter_mut().for_each(|v| *v = 0.0);
        self.learned_once = false;
    }
}

/// The values of the front end's stages (spec 026, sections `FRONT_END` and `NOISE_GATE`): read from the tuning array, as Java's
/// `FrontEnd.Parameters`; the defaults are the former constants.
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct FrontEndParameters {
    pub high_pass_hz: f64,
    pub noise_rise_db_per_second: f64,
    pub noise_fall_rate: f64,
    pub noise_trusted_hops: i64,
    pub gate_open_db: f64,
    pub gate_close_db: f64,
    pub gate_reduction_db: f64,
    pub gate_hold_hops: i32,
    pub subtraction_alpha: f64,
    pub subtraction_beta: f64,
    pub subtraction_learn_rate: f64,
}

impl FrontEndParameters {
    pub fn defaults() -> FrontEndParameters {
        FrontEndParameters {
            high_pass_hz: CORNER_HZ,
            noise_rise_db_per_second: RISE_DB_PER_SECOND,
            noise_fall_rate: FALL_RATE,
            noise_trusted_hops: TRUSTED_AFTER_HOPS,
            gate_open_db: OPEN_MARGIN_DB,
            gate_close_db: CLOSE_MARGIN_DB,
            gate_reduction_db: REDUCTION_DB,
            gate_hold_hops: HOLD_HOPS,
            subtraction_alpha: ALPHA,
            subtraction_beta: BETA,
            subtraction_learn_rate: LEARN_RATE,
        }
    }
}

/// The values Java sets on its front end: the gain and the two switches.
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct FrontEndSettings {
    pub gain: f64,
    pub noise_reduction: bool,
    pub subtraction: bool,
}

pub struct FrontEnd {
    settings: FrontEndSettings,
    sample_rate: f64,
    hop_size: usize,
    parameters: FrontEndParameters,
    high_pass: HighPass,
    estimator: NoiseEstimator,
    gate: NoiseGate,
    subtractor: SpectralSubtractor,
    reset_requested: bool,
}

impl FrontEnd {
    pub fn new(sample_rate: f64, hop_size: usize) -> FrontEnd {
        FrontEnd::new_tuned(sample_rate, hop_size, FrontEndParameters::defaults())
    }

    /// With the stages' values of the tuning array (spec 026).
    pub fn new_tuned(sample_rate: f64, hop_size: usize, parameters: FrontEndParameters) -> FrontEnd {
        let mut subtractor = SpectralSubtractor::new();
        subtractor.set_values(&parameters);
        FrontEnd {
            settings: FrontEndSettings { gain: NEUTRAL_GAIN, noise_reduction: false, subtraction: false },
            sample_rate,
            hop_size,
            parameters,
            high_pass: HighPass::new(sample_rate, parameters.high_pass_hz),
            estimator: NoiseEstimator::new(sample_rate, hop_size, &parameters),
            gate: NoiseGate::new(&parameters),
            subtractor,
            reset_requested: false,
        }
    }

    /// New stage values (a new tuning): every stage is rebuilt and its history starts again, as Java's `FrontEnd.configure`. Allocates
    /// nothing, so the analysis loop may call it.
    pub fn set_parameters(&mut self, parameters: FrontEndParameters) {
        self.parameters = parameters;
        self.high_pass = HighPass::new(self.sample_rate, parameters.high_pass_hz);
        self.estimator = NoiseEstimator::new(self.sample_rate, self.hop_size, &parameters);
        self.gate = NoiseGate::new(&parameters);
        self.subtractor.set_values(&parameters);
        self.subtractor.reset();
    }

    pub fn parameters(&self) -> FrontEndParameters {
        self.parameters
    }

    /// New values from Java; switching a stage on or off starts its history again, as Java's setters do.
    pub fn set(&mut self, settings: FrontEndSettings) {
        if settings.noise_reduction != self.settings.noise_reduction || settings.subtraction != self.settings.subtraction {
            self.reset_requested = true;
        }
        self.settings = settings;
    }

    pub fn settings(&self) -> FrontEndSettings {
        self.settings
    }

    /// Seeds the noise estimate with the silent level of the level check.
    pub fn seed(&mut self, silent_rms: f64) {
        self.estimator.seed(silent_rms);
    }

    pub fn process(&mut self, hop: &mut [f32]) {
        let gain = self.settings.gain;
        if gain != NEUTRAL_GAIN {
            for s in hop.iter_mut() {
                *s = (*s as f64 * gain) as f32;
            }
        }
        if !self.settings.noise_reduction {
            return;
        }
        if self.reset_requested {
            self.reset_requested = false;
            self.reset();
        }
        self.high_pass.process(hop);
        self.gate.process(hop, &mut self.estimator);
        if self.settings.subtraction && hop.len() == HOP && self.hop_size == HOP {
            let quiet = !self.gate.open;
            self.subtractor.process(hop, quiet);
        }
    }

    pub fn reset(&mut self) {
        self.high_pass.reset();
        self.gate.reset();
        self.estimator.reset();
        self.subtractor.reset();
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn a_neutral_front_end_leaves_every_bit() {
        let mut front = FrontEnd::new(48000.0, 256);
        let original: Vec<f32> = (0..256).map(|i| (i as f32 * 0.37).sin() * 0.3).collect();
        let mut hop = original.clone();
        front.process(&mut hop);
        assert_eq!(hop, original);
    }

    #[test]
    fn the_high_pass_removes_a_constant_and_the_gain_scales() {
        let mut front = FrontEnd::new(48000.0, 256);
        front.set(FrontEndSettings { gain: 2.0, noise_reduction: true, subtraction: true });
        let mut last = 1.0f32;
        for _ in 0..400 {
            let mut hop = vec![0.25f32; 256];
            front.process(&mut hop);
            last = hop[255];
        }
        assert!(last.abs() < 1e-3, "DC is removed: {last}");
    }
}
