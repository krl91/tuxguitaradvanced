//! The FFTs of the analysis (spec 023, research R3): `rustfft` plans made once, with their buffers, so a transform never allocates. The
//! magnitudes follow the layout of the audio engine's `RealFft.java`: bins 0 to size/2 of a real input, times a scale.

use rustfft::num_complex::Complex;
use rustfft::{Fft, FftPlanner};
use std::sync::Arc;

pub struct RealFft {
    size: usize,
    forward: Arc<dyn Fft<f64>>,
    inverse: Arc<dyn Fft<f64>>,
    buffer: Vec<Complex<f64>>,
    scratch: Vec<Complex<f64>>,
}

impl RealFft {
    pub fn new(size: usize) -> RealFft {
        assert!(size >= 2 && size.is_power_of_two(), "FFT size must be a power of two: {size}");
        let mut planner = FftPlanner::<f64>::new();
        let forward = planner.plan_fft_forward(size);
        let inverse = planner.plan_fft_inverse(size);
        let scratch_len = forward.get_inplace_scratch_len().max(inverse.get_inplace_scratch_len());
        RealFft { size, forward, inverse, buffer: vec![Complex::new(0.0, 0.0); size], scratch: vec![Complex::new(0.0, 0.0); scratch_len] }
    }

    pub fn size(&self) -> usize {
        self.size
    }

    /// The magnitudes of bins 0 to size/2 of `input` into `magnitudes` (length at least size/2 + 1), times `scale`.
    pub fn magnitudes(&mut self, input: &[f64], magnitudes: &mut [f64], scale: f64) {
        for (slot, value) in self.buffer.iter_mut().zip(input.iter()) {
            *slot = Complex::new(*value, 0.0);
        }
        self.forward.process_with_scratch(&mut self.buffer, &mut self.scratch);
        for k in 0..=self.size / 2 {
            let c = self.buffer[k];
            magnitudes[k] = scale * (c.re * c.re + c.im * c.im).sqrt();
        }
    }

    /// The complex spectrum of a real input, in place in `spectrum` (length `size`).
    pub fn forward(&mut self, spectrum: &mut [Complex<f64>]) {
        self.forward.process_with_scratch(spectrum, &mut self.scratch);
    }

    /// The inverse transform of `spectrum` in place, divided by the size (as the front end's own inverse).
    pub fn inverse(&mut self, spectrum: &mut [Complex<f64>]) {
        self.inverse.process_with_scratch(spectrum, &mut self.scratch);
        let n = self.size as f64;
        for c in spectrum.iter_mut() {
            c.re /= n;
            c.im /= n;
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn a_sine_lands_in_its_bin_with_the_java_scale() {
        let size = 1024;
        let mut fft = RealFft::new(size);
        let bin = 37.0;
        let input: Vec<f64> = (0..size).map(|i| (2.0 * std::f64::consts::PI * bin * i as f64 / size as f64).sin()).collect();
        let mut magnitudes = vec![0.0; size / 2 + 1];
        fft.magnitudes(&input, &mut magnitudes, 2.0 / size as f64);
        let peak = (0..magnitudes.len()).max_by(|a, b| magnitudes[*a].partial_cmp(&magnitudes[*b]).unwrap()).unwrap();
        assert_eq!(peak, 37);
        assert!((magnitudes[37] - 1.0).abs() < 1e-9, "a unit sine scaled by 2/N is 1: {}", magnitudes[37]);
        assert!(magnitudes[36].abs() < 1e-9 && magnitudes[0].abs() < 1e-9);
    }

    #[test]
    fn the_phase_follows_the_java_sign_convention() {
        // RealFft.java's twiddles are cos/sin(-2 pi i / N): X[k] = sum x[n] e^(-2 pi i k n / N), as rustfft's forward transform
        let size = 64;
        let mut fft = RealFft::new(size);
        let input: Vec<f64> = (0..size).map(|i| ((i * 37) % 11) as f64 / 11.0 - 0.4).collect();
        let mut spectrum: Vec<Complex<f64>> = input.iter().map(|v| Complex::new(*v, 0.0)).collect();
        fft.forward(&mut spectrum);
        for k in 0..=size / 2 {
            let mut re = 0.0;
            let mut im = 0.0;
            for (n, x) in input.iter().enumerate() {
                let angle = -2.0 * std::f64::consts::PI * (k * n) as f64 / size as f64;
                re += x * angle.cos();
                im += x * angle.sin();
            }
            assert!((spectrum[k].re - re).abs() < 1e-9 && (spectrum[k].im - im).abs() < 1e-9, "bin {k}");
        }
    }

    #[test]
    fn the_inverse_gives_the_input_back() {
        let size = 512;
        let mut fft = RealFft::new(size);
        let input: Vec<f64> = (0..size).map(|i| ((i * 7919) % 101) as f64 / 101.0 - 0.5).collect();
        let mut spectrum: Vec<Complex<f64>> = input.iter().map(|v| Complex::new(*v, 0.0)).collect();
        fft.forward(&mut spectrum);
        fft.inverse(&mut spectrum);
        for (a, b) in input.iter().zip(spectrum.iter()) {
            assert!((a - b.re).abs() < 1e-6 && b.im.abs() < 1e-6);
        }
    }
}
