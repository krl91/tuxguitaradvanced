//! Watches the processed output for a howl, ported from spec 022's `FeedbackGuard.java` and the `RealFft` it uses: every hop, the spectrum
//! of the last 2048 samples; a howl is one narrow peak far above the median of the spectrum, on the same frequency for the hold time, whose
//! level keeps rising or stays loud. It never allocates after it is made.

use crate::params::{Id, Params};
use std::f64::consts::PI;

/// The analysis frame, a power of two (the FFT).
pub const SIZE: usize = 2048;
/// Keeps the logarithm of silence finite.
const SILENCE: f64 = 1e-12;

/// An in-place radix-2 FFT with tables made once, for magnitude spectra.
struct RealFft {
    bit_reversed: Vec<usize>,
    cos: Vec<f64>,
    sin: Vec<f64>,
    re: Vec<f64>,
    im: Vec<f64>,
}

impl RealFft {
    fn new() -> Self {
        let bits = SIZE.trailing_zeros();
        let bit_reversed = (0..SIZE).map(|i| ((i as u32).reverse_bits() >> (u32::BITS - bits)) as usize).collect();
        let angle = |i: usize| -2.0 * PI * i as f64 / SIZE as f64;
        Self {
            bit_reversed,
            cos: (0..SIZE / 2).map(|i| angle(i).cos()).collect(),
            sin: (0..SIZE / 2).map(|i| angle(i).sin()).collect(),
            re: vec![0.0; SIZE],
            im: vec![0.0; SIZE],
        }
    }

    /// The magnitudes of bins 0…SIZE/2 of `input`.
    fn magnitudes(&mut self, input: &[f64], magnitudes: &mut [f64]) {
        for (i, x) in input.iter().enumerate() {
            self.re[self.bit_reversed[i]] = *x;
            self.im[self.bit_reversed[i]] = 0.0;
        }
        let mut length = 2;
        while length <= SIZE {
            let half = length >> 1;
            let step = SIZE / length;
            let mut start = 0;
            while start < SIZE {
                for k in 0..half {
                    let wr = self.cos[k * step];
                    let wi = self.sin[k * step];
                    let a = start + k;
                    let b = a + half;
                    let tr = wr * self.re[b] - wi * self.im[b];
                    let ti = wr * self.im[b] + wi * self.re[b];
                    self.re[b] = self.re[a] - tr;
                    self.im[b] = self.im[a] - ti;
                    self.re[a] += tr;
                    self.im[a] += ti;
                }
                start += length;
            }
            length <<= 1;
        }
        for (k, magnitude) in magnitudes.iter_mut().enumerate().take(SIZE / 2 + 1) {
            *magnitude = (self.re[k] * self.re[k] + self.im[k] * self.im[k]).sqrt();
        }
    }
}

pub struct FeedbackGuard {
    above_median_db: f64,
    narrow_db: f64,
    narrow_bins: usize,
    loud_db: f64,
    rise_db: f64,
    fall_tolerance_db: f64,
    drift: usize,
    fft: RealFft,
    ring: Vec<f32>,
    frame: Vec<f64>,
    window: Vec<f64>,
    magnitudes: Vec<f64>,
    sorted: Vec<f64>,
    hop: usize,
    low: usize,
    high: usize,
    hold_analyses: usize,
    position: usize,
    since_analysis: usize,
    filled: usize,
    bin: Option<usize>,
    analyses: usize,
    start_db: f64,
    last_db: f64,
    rising: bool,
    loud: bool,
    found: bool,
}

impl FeedbackGuard {
    pub fn new(sample_rate: f32, _max_block: usize, params: &Params) -> Self {
        let narrow_bins = params.get(Id::FeedbackNarrowBins) as usize;
        // Java multiplies in float and rounds half up
        let hop = 1usize.max((params.get(Id::FeedbackAnalysisHop) as f32 * sample_rate + 0.5).floor() as usize);
        let bin_hz = f64::from(sample_rate / SIZE as f32);
        let low = (narrow_bins + 1).max((params.get(Id::FeedbackAnalysisLow) / bin_hz).floor() as usize);
        let high = (SIZE / 2 - narrow_bins - 1).min((params.get(Id::FeedbackAnalysisHigh) / bin_hz).ceil() as usize);
        let hold_analyses = (params.get(Id::FeedbackHold) * f64::from(sample_rate) / hop as f64).ceil() as usize;
        let mut window: Vec<f64> = (0..SIZE).map(|i| 0.5 - 0.5 * (2.0 * PI * i as f64 / (SIZE - 1) as f64).cos()).collect();
        let sum: f64 = window.iter().fold(0.0, |s, w| s + w);
        // a full-scale sine reads 0 dB at its peak bin
        let scale = 2.0 / sum;
        for w in &mut window {
            *w *= scale;
        }
        Self {
            above_median_db: params.get(Id::FeedbackAboveMedian),
            narrow_db: params.get(Id::FeedbackNarrow),
            narrow_bins,
            loud_db: params.get(Id::FeedbackLoud),
            rise_db: params.get(Id::FeedbackRise),
            fall_tolerance_db: params.get(Id::FeedbackFallTolerance),
            drift: params.get(Id::FeedbackDrift) as usize,
            fft: RealFft::new(),
            ring: vec![0.0; SIZE],
            frame: vec![0.0; SIZE],
            window,
            magnitudes: vec![0.0; SIZE / 2 + 1],
            sorted: vec![0.0; high - low + 1],
            hop,
            low,
            high,
            hold_analyses,
            position: 0,
            since_analysis: 0,
            filled: 0,
            bin: None,
            analyses: 0,
            start_db: 0.0,
            last_db: 0.0,
            rising: false,
            loud: false,
            found: false,
        }
    }

    /// Takes a block of the output; true once a howl is found (and until [`FeedbackGuard::reset`]).
    pub fn process(&mut self, block: &[f32]) -> bool {
        for x in block {
            self.ring[self.position] = *x;
            self.position = (self.position + 1) % SIZE;
        }
        self.filled = SIZE.min(self.filled + block.len());
        self.since_analysis += block.len();
        if !self.found && self.filled == SIZE && self.since_analysis >= self.hop {
            self.since_analysis = 0;
            self.analyse();
        }
        self.found
    }

    pub fn reset(&mut self) {
        self.filled = 0;
        self.since_analysis = 0;
        self.bin = None;
        self.found = false;
    }

    fn analyse(&mut self) {
        for i in 0..SIZE {
            self.frame[i] = f64::from(self.ring[(self.position + i) % SIZE]) * self.window[i];
        }
        self.fft.magnitudes(&self.frame, &mut self.magnitudes);
        let mut peak = self.low;
        for k in self.low..=self.high {
            self.sorted[k - self.low] = self.magnitudes[k];
            if self.magnitudes[k] > self.magnitudes[peak] {
                peak = k;
            }
        }
        let peak_db = db(self.magnitudes[peak]);
        let median_db = db(self.median());
        let narrow = peak_db - db(self.magnitudes[peak - self.narrow_bins]) >= self.narrow_db
            && peak_db - db(self.magnitudes[peak + self.narrow_bins]) >= self.narrow_db;
        if !narrow || peak_db - median_db <= self.above_median_db {
            self.bin = None;
            return;
        }
        match self.bin {
            Some(bin) if peak.abs_diff(bin) <= self.drift => {}
            _ => {
                self.bin = Some(peak);
                self.analyses = 1;
                self.start_db = peak_db;
                self.last_db = peak_db;
                self.rising = true;
                self.loud = peak_db > self.loud_db;
                return;
            }
        }
        self.bin = Some(peak);
        self.analyses += 1;
        self.rising &= peak_db >= self.last_db - self.fall_tolerance_db;
        self.loud &= peak_db > self.loud_db;
        self.last_db = peak_db;
        if self.analyses >= self.hold_analyses && ((self.rising && peak_db - self.start_db >= self.rise_db) || self.loud) {
            self.found = true;
        }
    }

    /// The median of the watched spectrum (a selection in place, no allocation).
    fn median(&mut self) -> f64 {
        let sorted = &mut self.sorted;
        let count = sorted.len() as i64;
        let k = count / 2;
        let mut left: i64 = 0;
        let mut right: i64 = count - 1;
        while left < right {
            let pivot = sorted[((left + right) >> 1) as usize];
            let mut i = left;
            let mut j = right;
            while i <= j {
                while sorted[i as usize] < pivot {
                    i += 1;
                }
                while sorted[j as usize] > pivot {
                    j -= 1;
                }
                if i <= j {
                    sorted.swap(i as usize, j as usize);
                    i += 1;
                    j -= 1;
                }
            }
            if k <= j {
                right = j;
            } else if k >= i {
                left = i;
            } else {
                break;
            }
        }
        sorted[k as usize]
    }
}

fn db(magnitude: f64) -> f64 {
    20.0 * SILENCE.max(magnitude).log10()
}
