//! Pushing, popping, dropping and rendering never allocate once the ring and the resampler are made (spec 022, research R1 addendum):
//! the callbacks of the audio devices must not.

use crate::resampler::Resampler;
use crate::ring::Ring;
use std::alloc::{GlobalAlloc, Layout, System};
use std::cell::Cell;

struct Counting;

thread_local! {
    static ALLOCATIONS: Cell<u64> = const { Cell::new(0) };
}

unsafe impl GlobalAlloc for Counting {
    unsafe fn alloc(&self, layout: Layout) -> *mut u8 {
        ALLOCATIONS.with(|a| a.set(a.get() + 1));
        unsafe { System.alloc(layout) }
    }

    unsafe fn dealloc(&self, ptr: *mut u8, layout: Layout) {
        unsafe { System.dealloc(ptr, layout) }
    }
}

#[global_allocator]
static COUNTING: Counting = Counting;

fn allocations() -> u64 {
    ALLOCATIONS.with(|a| a.get())
}

#[test]
fn the_ring_and_the_resampler_never_allocate_after_they_are_made() {
    let ring = Ring::new(4096);
    let mut resampler = Resampler::new(48000.0, 44100.0, 128);
    let block = vec![0.25f32; 2 * 128];
    let mut out = vec![0f32; 128];
    let (mut l, mut r) = (vec![0f32; 128], vec![0f32; 128]);
    let before = allocations();
    for _ in 0..1000 {
        ring.push(&block);
        ring.keep_at_most(3 * 256, 256);
        resampler.render(&ring, &mut l, &mut r);
        ring.pop(&mut out);
        ring.skip(16);
    }
    assert_eq!(allocations() - before, 0);
}

#[test]
fn the_analysis_ffts_never_allocate_after_they_are_made() {
    use crate::analysis::fft::RealFft;
    use rustfft::num_complex::Complex;
    let mut fft = RealFft::new(8192);
    let input = vec![0.25f64; 8192];
    let mut magnitudes = vec![0f64; 8192 / 2 + 1];
    let mut spectrum = vec![Complex::new(0.0, 0.0); 8192];
    let before = allocations();
    for _ in 0..20 {
        fft.magnitudes(&input, &mut magnitudes, 1.0);
        fft.forward(&mut spectrum);
        fft.inverse(&mut spectrum);
    }
    assert_eq!(allocations() - before, 0);
}

#[test]
fn one_second_of_hops_through_every_stage_allocates_nothing_after_warm_up() {
    use crate::analysis::pipeline::{Pipeline, Request};
    use crate::analysis::Parameters;
    let rate = 48000;
    let parameters = Parameters::defaults();
    let mut pipeline = Pipeline::new(rate, parameters);
    let candidates = [40, 45, 47, 52, 56, 59, 64];
    pipeline.set_request(Request { spectrum: true, attack_candidates: &candidates, expected_pitches: &[45, 52], chord_required: &[40, 47, 52, 56, 59, 64], chord_tolerated: &[45] });
    let mut records = vec![0f32; crate::analysis::frame::CAPACITY];
    let hop: Vec<f32> = (0..256).map(|i| (0.2 * (2.0 * std::f64::consts::PI * 110.0 * i as f64 / rate as f64).sin()) as f32).collect();
    for _ in 0..40 {
        pipeline.hop(&hop, -1, 0, &mut records);
    }
    let before = allocations();
    for _ in 0..rate / 256 {
        pipeline.hop(&hop, -1, 0, &mut records);
    }
    assert_eq!(allocations() - before, 0);
}
