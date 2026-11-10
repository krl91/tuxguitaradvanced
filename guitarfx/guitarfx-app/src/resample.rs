//! From the input's rate to the output's (spec 027, research R1): the cubic Hermite interpolator of the native crate's `resampler.rs`, over
//! mono frames popped from the input ring. Its ratio is the two nominal rates' ratio, nudged by how full the ring is, so that the ring's
//! fill, and with it the delay, stays where it should even though two devices' clocks never run at exactly the same pace. Its four values
//! are external parameters (`guitarfx.devices.resample-*`). Nothing is allocated after `new`.

use guitarfx_dsp::params::{Id, Params};
use tuxguitar_monitor_native::ring::Ring;

pub struct Resampler {
    nominal: f64,
    max_adjust: f64,
    gain: f64,
    smoothing: f64,
    target_fill: f64,
    smoothed_fill: f64,
    position: f64,
    // the last four samples, oldest first
    last: [f32; 4],
    primed: bool,
    underruns: u64,
}

impl Resampler {
    /// `block` is the frames of one output block. The fill is measured as a block starts, before it takes about one block.
    pub fn new(input_rate: f64, output_rate: f64, block: usize, params: &Params) -> Resampler {
        let target_fill = params.get(Id::DevicesResampleFill) * block.max(1) as f64;
        Resampler {
            nominal: input_rate / output_rate,
            max_adjust: params.get(Id::DevicesResampleAdjust),
            gain: params.get(Id::DevicesResampleGain),
            smoothing: params.get(Id::DevicesResampleSmoothing),
            target_fill,
            smoothed_fill: target_fill,
            position: 0.0,
            last: [0.0; 4],
            primed: false,
            underruns: 0,
        }
    }

    /// The fill aimed at, in input frames: part of the delay.
    pub fn target_fill(&self) -> f64 {
        self.target_fill
    }

    /// The ratio in use (input frames consumed per output frame).
    pub fn ratio(&self) -> f64 {
        self.nominal * (1.0 + (self.gain * (self.smoothed_fill - self.target_fill)).clamp(-self.max_adjust, self.max_adjust))
    }

    /// The blocks that found the ring empty.
    pub fn underruns(&self) -> u64 {
        self.underruns
    }

    /// Fills `out` with output frames popped from `ring`.
    pub fn render(&mut self, ring: &Ring, out: &mut [f32]) {
        let fill = ring.len() as f64;
        self.smoothed_fill += (fill - self.smoothed_fill) * self.smoothing;
        if !self.primed {
            // wait until the ring holds its target before playing, so that the first blocks do not run dry
            if fill < self.target_fill {
                out.fill(0.0);
                return;
            }
            self.primed = true;
            self.smoothed_fill = fill;
        }
        let ratio = self.ratio();
        let mut dry = false;
        let mut sample = [0f32; 1];
        for value in out.iter_mut() {
            while self.position >= 1.0 {
                self.position -= 1.0;
                if ring.pop(&mut sample) < 1 {
                    // no sample: silence (the interpolator fades into it over its few samples), counted once per block
                    dry = true;
                    sample[0] = 0.0;
                }
                self.last.rotate_left(1);
                self.last[3] = sample[0];
            }
            *value = hermite(&self.last, self.position as f32);
            self.position += ratio;
        }
        if dry {
            self.underruns += 1;
        }
    }
}

/// The cubic Hermite (Catmull-Rom) value between the second and third of four points.
fn hermite(p: &[f32; 4], t: f32) -> f32 {
    let c0 = p[1];
    let c1 = 0.5 * (p[2] - p[0]);
    let c2 = p[0] - 2.5 * p[1] + 2.0 * p[2] - 0.5 * p[3];
    let c3 = 0.5 * (p[3] - p[0]) + 1.5 * (p[1] - p[2]);
    ((c3 * t + c2) * t + c1) * t + c0
}
