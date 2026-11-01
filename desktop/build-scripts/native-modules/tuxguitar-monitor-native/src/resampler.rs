//! From the input's rate (the chain's) to the output's (spec 022, research R1 addendum). A cubic Hermite interpolator over stereo frames
//! popped from the output ring. Its ratio is the two nominal rates' ratio, nudged by how full the ring is: when Java writes a little
//! faster than the output plays (or slower), the ratio follows, so the ring's fill, and with it the delay, stays where it should even
//! though two devices' clocks never run at exactly the same pace. Nothing is allocated after `new`.

use crate::ring::Ring;

/// How far the ratio may move from the nominal one: 0.5 %, far more than any clock drift, too little to hear as a pitch change.
const MAX_ADJUST: f64 = 0.005;
/// How strongly the fill error moves the ratio, per frame of the smoothed fill's distance from the target (100 ppm for 5 frames).
const GAIN: f64 = 0.00002;
/// The smoothing of the measured fill (per output callback).
const SMOOTHING: f64 = 0.02;

pub struct Resampler {
    nominal: f64,
    target_fill: f64,
    smoothed_fill: f64,
    position: f64,
    // the last four frames (oldest first), left and right
    left: [f32; 4],
    right: [f32; 4],
    primed: bool,
    underruns: u64,
}

impl Resampler {
    /// `block` is the frames of one output callback. The fill is measured as a callback starts, before it takes about one block: the
    /// target is two blocks, so a callback never runs the ring dry while the writer's block is still on its way.
    pub fn new(input_rate: f64, output_rate: f64, block: usize) -> Resampler {
        let target_fill = 2.0 * block.max(1) as f64;
        Resampler {
            nominal: input_rate / output_rate,
            target_fill,
            smoothed_fill: target_fill,
            position: 0.0,
            left: [0.0; 4],
            right: [0.0; 4],
            primed: false,
            underruns: 0,
        }
    }

    /// The ratio in use (input frames consumed per output frame).
    pub fn ratio(&self) -> f64 {
        self.nominal * (1.0 + (GAIN * (self.smoothed_fill - self.target_fill)).clamp(-MAX_ADJUST, MAX_ADJUST))
    }

    /// The output callbacks that found the ring empty.
    pub fn underruns(&self) -> u64 {
        self.underruns
    }

    /// Fills `left` and `right` (the same length) with output frames, popping interleaved stereo frames from `ring`.
    pub fn render(&mut self, ring: &Ring, left: &mut [f32], right: &mut [f32]) {
        let fill = (ring.len() / 2) as f64;
        self.smoothed_fill += (fill - self.smoothed_fill) * SMOOTHING;
        if !self.primed {
            // wait until the ring holds its target before playing, so that the first callbacks do not run dry
            if fill < self.target_fill {
                left.iter_mut().for_each(|s| *s = 0.0);
                right.iter_mut().for_each(|s| *s = 0.0);
                return;
            }
            self.primed = true;
            self.smoothed_fill = fill;
        }
        let ratio = self.ratio();
        let mut dry = false;
        let mut frame = [0f32; 2];
        for i in 0..left.len() {
            while self.position >= 1.0 {
                self.position -= 1.0;
                if ring.pop(&mut frame) < 2 {
                    // no frame: silence (the interpolator fades into it over its few frames), counted once per callback
                    dry = true;
                    frame = [0.0, 0.0];
                }
                self.left.rotate_left(1);
                self.right.rotate_left(1);
                self.left[3] = frame[0];
                self.right[3] = frame[1];
            }
            let t = self.position as f32;
            left[i] = hermite(&self.left, t);
            right[i] = hermite(&self.right, t);
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

#[cfg(test)]
mod tests {
    use super::*;

    /// The zero crossings' rate of a signal, in Hz.
    fn frequency(samples: &[f32], rate: f64) -> f64 {
        let mut crossings = Vec::new();
        for i in 1..samples.len() {
            if samples[i - 1] < 0.0 && samples[i] >= 0.0 {
                let t = samples[i - 1] / (samples[i - 1] - samples[i]);
                crossings.push((i - 1) as f64 + t as f64);
            }
        }
        let span = crossings[crossings.len() - 1] - crossings[0];
        (crossings.len() - 1) as f64 / span * rate
    }

    #[test]
    fn a_sine_keeps_its_frequency_from_48_to_44_1_khz() {
        let ring = Ring::new(1 << 16);
        let mut resampler = Resampler::new(48000.0, 44100.0, 128);
        let mut phase = 0.0f64;
        let mut out = Vec::new();
        let (mut l, mut r) = (vec![0f32; 128], vec![0f32; 128]);
        let mut block = vec![0f32; 2 * 128];
        // each side at its own clock: Java writes 128 frames every 128 / 48000 s, the output asks for 128 frames every 128 / 44100 s
        let mut written_time = 0.0f64;
        for c in 0..4000 {
            let now = (c + 1) as f64 * 128.0 / 44100.0;
            while written_time < now {
                written_time += 128.0 / 48000.0;
                for f in 0..128 {
                    let v = (0.5 * (2.0 * std::f64::consts::PI * phase).sin()) as f32;
                    phase += 1000.0 / 48000.0;
                    block[2 * f] = v;
                    block[2 * f + 1] = v;
                }
                ring.push(&block);
            }
            resampler.render(&ring, &mut l, &mut r);
            out.extend_from_slice(&l);
        }
        let measured = frequency(&out[5 * 44100..], 44100.0);
        assert!((measured - 1000.0).abs() < 1.0, "1000 Hz came out at {measured} Hz");
    }

    #[test]
    fn the_fill_stays_bounded_over_an_hour_with_a_100_ppm_clock_difference() {
        // the input clock runs 100 ppm fast: Java writes a little more than the output plays
        let ring = Ring::new(1 << 14);
        let block = 128usize;
        let mut resampler = Resampler::new(48000.0, 48000.0, block);
        let (mut l, mut r) = (vec![0f32; block], vec![0f32; block]);
        let frames = vec![0.25f32; 2 * block];
        let mut owed = 0.0f64;
        let callbacks = 3600 * 48000 / block;
        let mut max_fill = 0usize;
        let mut min_fill = usize::MAX;
        for c in 0..callbacks {
            owed += block as f64 * 1.0001;
            while owed >= block as f64 {
                ring.push(&frames);
                owed -= block as f64;
            }
            resampler.render(&ring, &mut l, &mut r);
            if c > 48000 / block * 10 {
                let fill = ring.len() / 2;
                max_fill = max_fill.max(fill);
                min_fill = min_fill.min(fill);
            }
        }
        assert!(max_fill <= 4 * block, "the fill grew to {max_fill} frames");
        assert_eq!(resampler.underruns(), 0, "{} underruns (min fill {min_fill})", resampler.underruns());
    }

    #[test]
    fn an_empty_ring_plays_silence_and_is_counted() {
        let ring = Ring::new(4096);
        let mut resampler = Resampler::new(48000.0, 48000.0, 128);
        let (mut l, mut r) = (vec![0f32; 128], vec![0f32; 128]);
        ring.push(&vec![0.5f32; 2 * 256]);
        resampler.render(&ring, &mut l, &mut r);
        resampler.render(&ring, &mut l, &mut r);
        assert_eq!(resampler.underruns(), 0);
        // the ring is empty now: after the interpolator's few frames of tail, silence
        resampler.render(&ring, &mut l, &mut r);
        resampler.render(&ring, &mut l, &mut r);
        assert_eq!(resampler.underruns(), 2, "each dry callback is counted");
        assert!(l.iter().chain(r.iter()).all(|s| *s == 0.0), "a dry ring plays silence, not the last frame held");
    }
}
