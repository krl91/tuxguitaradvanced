//! Runs a clipping stage at 2 or 4 times the sample rate so that its harmonics do not fold back as aliasing, ported from spec 022's
//! `Oversampler.java`. Each factor of 2 is a half-band FIR (a Kaiser-windowed sinc, every other tap zero) used to interpolate on the way up
//! and to filter before dropping every other sample on the way down. Factor 1 is a plain copy. Every buffer is made at construction.

use std::f64::consts::PI;
use std::sync::OnceLock;

/// The half-band filter's length: `4k + 3` taps, so that every even offset from the middle is zero.
pub const TAPS: usize = 63;
/// The Kaiser window's β, chosen with the 63 taps for about 80 dB of image rejection.
const KAISER_BETA: f64 = 8.0;
/// The terms of the Bessel series.
const BESSEL_TERMS: usize = 40;
/// A tap under this is a zero of the half-band design.
const ZERO_TAP: f64 = 1e-12;
/// Under this a filtered value is flushed to zero.
const FLUSH: f64 = 1e-25;
/// The factors: 1, 2 and 4 (two half-band stages).
pub const LARGEST_FACTOR: usize = 4;

/// The delay of one half-band stage, in samples at its own rate.
pub fn stage_delay() -> f64 {
    (TAPS - 1) as f64 / 2.0
}

struct Taps {
    values: Vec<f64>,
    index: Vec<usize>,
}

fn bessel(x: f64) -> f64 {
    let mut sum = 1.0;
    let mut term = 1.0;
    for k in 1..BESSEL_TERMS {
        term *= (x / (2.0 * k as f64)) * (x / (2.0 * k as f64));
        sum += term;
    }
    sum
}

/// The non-zero taps, worked out once.
fn taps() -> &'static Taps {
    static TAPS_ONCE: OnceLock<Taps> = OnceLock::new();
    TAPS_ONCE.get_or_init(|| {
        let middle = (TAPS - 1) / 2;
        let mut taps = [0.0; TAPS];
        let mut sum = 0.0;
        for (n, tap) in taps.iter_mut().enumerate() {
            let offset = n as f64 - middle as f64;
            let x = offset / 2.0;
            let sinc = if x == 0.0 { 1.0 } else { (PI * x).sin() / (PI * x) };
            let r = offset / middle as f64;
            *tap = 0.5 * sinc * bessel(KAISER_BETA * 0f64.max(1.0 - r * r).sqrt()) / bessel(KAISER_BETA);
            sum += *tap;
        }
        let mut values = Vec::new();
        let mut index = Vec::new();
        for (n, tap) in taps.iter_mut().enumerate() {
            *tap /= sum;
            if tap.abs() > ZERO_TAP {
                values.push(*tap);
                index.push(n);
            }
        }
        Taps { values, index }
    })
}

/// One half-band stage with its own history.
struct HalfBand {
    history: [f64; 2 * TAPS],
    position: usize,
    taps: &'static Taps,
}

impl HalfBand {
    fn new() -> Self {
        Self {
            history: [0.0; 2 * TAPS],
            position: 0,
            taps: taps(),
        }
    }

    fn push(&mut self, x: f64) {
        self.position = (self.position + 1) % TAPS;
        self.history[self.position] = x;
        self.history[self.position + TAPS] = x;
    }

    fn filter(&self) -> f64 {
        // history[position + TAPS - n] is the sample pushed n steps ago
        let mut sum = 0.0;
        let base = self.position + TAPS;
        for (tap, n) in self.taps.values.iter().zip(&self.taps.index) {
            sum += tap * self.history[base - n];
        }
        if sum.abs() < FLUSH {
            0.0
        } else {
            sum
        }
    }

    fn up(&mut self, input: &[f32], out: &mut [f32]) {
        for (i, x) in input.iter().enumerate() {
            self.push(2.0 * f64::from(*x));
            out[2 * i] = self.filter() as f32;
            self.push(0.0);
            out[2 * i + 1] = self.filter() as f32;
        }
    }

    fn down(&mut self, input: &[f32], out: &mut [f32]) {
        for i in 0..input.len() / 2 {
            self.push(f64::from(input[2 * i]));
            self.push(f64::from(input[2 * i + 1]));
            out[i] = self.filter() as f32;
        }
    }

    fn reset(&mut self) {
        self.history.fill(0.0);
        self.position = 0;
    }
}

pub struct Oversampler {
    factor: usize,
    up_first: HalfBand,
    up_second: HalfBand,
    down_first: HalfBand,
    down_second: HalfBand,
    middle: Vec<f32>,
}

impl Oversampler {
    /// `factor` is 1, 2 or 4 (any other value is brought to the nearest of them below, at least 1).
    pub fn new(factor: usize, max_block: usize) -> Self {
        let factor = if factor >= LARGEST_FACTOR {
            LARGEST_FACTOR
        } else if factor >= 2 {
            2
        } else {
            1
        };
        Self {
            factor,
            up_first: HalfBand::new(),
            up_second: HalfBand::new(),
            down_first: HalfBand::new(),
            down_second: HalfBand::new(),
            middle: vec![0.0; max_block * 2],
        }
    }

    pub fn factor(&self) -> usize {
        self.factor
    }

    /// Returns the frames written to `out`: `input.len()` times the factor.
    pub fn up(&mut self, input: &[f32], out: &mut [f32]) -> usize {
        let n = input.len();
        if self.factor == 1 {
            out[..n].copy_from_slice(input);
        } else if self.factor == 2 {
            self.up_first.up(input, out);
        } else {
            self.up_first.up(input, &mut self.middle[..2 * n]);
            self.up_second.up(&self.middle[..2 * n], out);
        }
        n * self.factor
    }

    /// `input` holds the oversampled frames, a multiple of the factor; writes `input.len() / factor` frames.
    pub fn down(&mut self, input: &[f32], out: &mut [f32]) {
        let count = input.len();
        if self.factor == 1 {
            out[..count].copy_from_slice(input);
        } else if self.factor == 2 {
            self.down_first.down(input, out);
        } else {
            self.down_second.down(input, &mut self.middle[..count / 2]);
            self.down_first.down(&self.middle[..count / 2], out);
        }
    }

    pub fn reset(&mut self) {
        self.up_first.reset();
        self.up_second.reset();
        self.down_first.reset();
        self.down_second.reset();
    }
}
