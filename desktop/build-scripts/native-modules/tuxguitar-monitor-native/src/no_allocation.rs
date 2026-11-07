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

#[test]
fn the_live_recognition_loop_allocates_nothing_even_when_java_changes_its_requests() {
    use crate::analysis::chord::ExpectedChord;
    use crate::analysis::Parameters;
    use crate::capture::{CaptureShared, BLOCK};
    use crate::recognition::{front_of, pipeline_for, strum_of, AttackSlot, Config, Requests, Shared, Step, Worker};
    use std::sync::Arc;
    let rate = 48000;
    let p = Parameters::defaults();
    let shared = Arc::new(Shared::new(Arc::new(CaptureShared::new(64)), rate, 0));
    let mut worker = Worker::new(shared.clone(), p, front_of(&p), strum_of(&p));
    shared.set_spectrum(true);
    shared.set_hops_enabled(true);
    let block: Vec<f32> = (0..BLOCK).map(|i| (0.2 * (2.0 * std::f64::consts::PI * 110.0 * i as f64 / rate as f64).sin()) as f32).collect();
    let block: [f32; BLOCK] = block.try_into().unwrap();
    let mut out = vec![0f32; 1 << 18];
    let drain = |shared: &Shared, out: &mut [f32]| {
        shared.next_frames(out, std::time::Duration::ZERO);
        shared.next_hops(out, std::time::Duration::ZERO);
    };
    for _ in 0..80 {
        shared.capture.blocks.push(&block, 0);
        while worker.step() == Step::Block {}
        drain(&shared, &mut out);
    }
    // what Java hands over is built before counting, on this thread; posting it and the loop's taking it must not allocate
    let mut changes: Vec<(Box<Config>, Box<AttackSlot>, Box<Vec<i32>>, Box<Option<ExpectedChord>>)> = (0..4)
        .map(|k| {
            let requests = Requests { spectrum: true, attack: vec![40, 45 + k], expected: vec![45], chord: Some((vec![40, 47, 52], vec![45])) };
            (
                Box::new(Config { pipeline: if k % 2 == 0 { Some(pipeline_for(rate, p, &requests)) } else { None }, front: front_of(&p), strum: strum_of(&p) }),
                Box::new(AttackSlot::of(rate, &requests.attack)),
                Box::new(vec![45, 52]),
                Box::new(Some(ExpectedChord::new(&[40, 47, 52], &[45]))),
            )
        })
        .collect();
    changes.reserve(0);
    let before = allocations();
    for round in 0..400 {
        if round % 100 == 50 {
            let (config, attack, expected, chord) = changes.pop().unwrap();
            shared.config.post(config);
            shared.attack.post(attack);
            shared.expected.post(expected);
            shared.chord.post(chord);
        }
        if round == 300 {
            // a gap: more blocks than the ring holds, the analysis restarts
            for _ in 0..80 {
                shared.capture.blocks.push(&block, 0);
            }
        }
        shared.capture.blocks.push(&block, 0);
        while worker.step() == Step::Block {}
        drain(&shared, &mut out);
    }
    assert_eq!(allocations() - before, 0);
    assert!(shared.dropped_blocks() > 0);
}

/// The live loop given a pipeline built with `values` as its tuning array (spec 026, research R6): the pipeline is built on Java's thread, at
/// `configureTuning`; the loop that swaps it in and analyses with it allocates nothing.
fn the_live_loop_allocates_nothing_with_the_tuning(values: &[f64]) {
    the_live_loop_allocates_nothing_with(values, crate::analysis::Parameters::defaults());
}

fn the_live_loop_allocates_nothing_with(values: &[f64], p: crate::analysis::Parameters) {
    use crate::analysis::Tuning;
    use crate::capture::{CaptureShared, BLOCK};
    use crate::recognition::{front_of, pipeline_for_tuned, strum_of, Config, Requests, Shared, Step, Worker};
    use std::sync::Arc;
    let rate = 44100;
    let tuning = Tuning::from_slice(values).expect("a tuning of the layout's length");
    let shared = Arc::new(Shared::new(Arc::new(CaptureShared::new(64)), rate, 0));
    let mut worker = Worker::new(shared.clone(), p, front_of(&p), strum_of(&p));
    shared.set_spectrum(true);
    shared.set_hops_enabled(true);
    let block: Vec<f32> = (0..BLOCK)
        .map(|i| {
            let t = i as f64 / rate as f64;
            (0.1 * (2.0 * std::f64::consts::PI * 82.4 * t).sin() + 0.1 * (2.0 * std::f64::consts::PI * 659.3 * t).sin()) as f32
        })
        .collect();
    let block: [f32; BLOCK] = block.try_into().unwrap();
    let mut out = vec![0f32; 1 << 18];
    let drain = |shared: &Shared, out: &mut [f32]| {
        shared.next_frames(out, std::time::Duration::ZERO);
        shared.next_hops(out, std::time::Duration::ZERO);
    };
    let requests = Requests { spectrum: true, attack: vec![40, 45, 52, 64, 76], expected: vec![40, 76], chord: Some((vec![40, 52, 64, 76], vec![45])) };
    let config = Box::new(Config { pipeline: Some(pipeline_for_tuned(rate, p, tuning, &requests)), front: front_of(&p), strum: strum_of(&p) });
    for _ in 0..80 {
        shared.capture.blocks.push(&block, 0);
        while worker.step() == Step::Block {}
        drain(&shared, &mut out);
    }
    let before = allocations();
    shared.config.post(config);
    for _ in 0..400 {
        shared.capture.blocks.push(&block, 0);
        while worker.step() == Step::Block {}
        drain(&shared, &mut out);
    }
    assert_eq!(allocations() - before, 0);
}

#[test]
fn the_polyphonic_values_at_their_upper_bounds_allocate_nothing_in_the_loop() {
    use crate::analysis::*;
    let mut values = Tuning::default_values();
    values[T_POLY_CANDIDATE_LOW] = 60.0;
    values[T_POLY_CANDIDATE_HIGH] = 112.0;
    values[T_POLY_HARMONICS] = 24.0;
    values[T_POLY_MAX_HARMONIC] = 12000.0;
    values[T_POLY_TOLERANCE] = 50.0;
    values[T_POLY_CANDIDATES] = 12.0;
    values[T_POLY_MIN_RELATIVE] = 0.5;
    values[T_POLY_FLOOR] = -40.0;
    values[T_POLY_SUBHARMONIC] = 0.9;
    values[T_POLY_FLOOR_WIDTH] = 100.0;
    values[T_POLY_FLOOR_FACTOR] = 6.0;
    the_live_loop_allocates_nothing_with_the_tuning(&values);
    // and the widest candidate range with the most candidates and harmonics
    let mut values = Tuning::default_values();
    values[T_POLY_CANDIDATE_LOW] = 24.0;
    values[T_POLY_HARMONICS] = 24.0;
    values[T_POLY_CANDIDATES] = 12.0;
    values[T_POLY_MIN_RELATIVE] = 0.01;
    values[T_POLY_FLOOR] = -100.0;
    the_live_loop_allocates_nothing_with_the_tuning(&values);
}

#[test]
fn the_attack_values_at_their_upper_bounds_allocate_nothing_in_the_loop() {
    use crate::analysis::*;
    let mut values = Tuning::default_values();
    values[T_ATTACK_LAG] = 64.0;
    values[T_ATTACK_MAX_FREQUENCY] = 12000.0;
    values[T_ATTACK_PARTIALS] = 24.0;
    values[T_ATTACK_BETA] = 2.0;
    values[T_ATTACK_SIGMA] = 4.0;
    the_live_loop_allocates_nothing_with_the_tuning(&values);
}

#[test]
fn the_front_end_values_at_their_upper_bounds_allocate_nothing_in_the_loop() {
    use crate::analysis::*;
    let mut values = Tuning::default_values();
    values[T_FRONT_HIGH_PASS] = 120.0;
    values[T_FRONT_NOISE_RISE] = 5.0;
    values[T_FRONT_NOISE_FALL] = 1.0;
    values[T_FRONT_NOISE_TRUSTED] = 1000.0;
    values[T_GATE_OPEN] = 30.0;
    values[T_GATE_CLOSE] = 8.0;
    values[T_GATE_REDUCTION] = 60.0;
    values[T_GATE_HOLD] = 100.0;
    values[T_SUBTRACTION] = 4.0;
    values[T_SUBTRACTION_FLOOR] = 0.3;
    values[T_SUBTRACTION_LEARN] = 1.0;
    // the noise reduction and the subtraction on, so every stage of the front end runs with its new values
    let mut p = Parameters::defaults();
    p.front_noise_reduction = true;
    p.front_subtraction = true;
    the_live_loop_allocates_nothing_with(&values, p);
}
