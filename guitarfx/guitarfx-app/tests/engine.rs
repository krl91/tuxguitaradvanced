//! The engine on a fake device, with no hardware (spec 027, T026, FR-014, FR-015): the input callback pushes the chosen channel, the output
//! callback runs the chain block by block and writes stereo; the output equals the chain's on the same input; changes go through the
//! mailbox; the ring keeps at most its blocks; overload lightens the chain step by step and then mutes; nothing is allocated or freed in
//! either callback.

use guitarfx_app::engine::{self, Clock, Config, State};
use guitarfx_dsp::chain::Chain;
use guitarfx_dsp::feedback_guard::FeedbackGuard;
use guitarfx_dsp::output_stage::OutputStage;
use guitarfx_dsp::params::{Id, Params};
use guitarfx_dsp::ready_made;
use guitarfx_dsp::settings::ChainParams;
use std::alloc::{GlobalAlloc, Layout, System};
use std::cell::Cell;
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::Arc;

struct Counting;

thread_local! {
    static CALLS: Cell<u64> = const { Cell::new(0) };
}

unsafe impl GlobalAlloc for Counting {
    unsafe fn alloc(&self, layout: Layout) -> *mut u8 {
        CALLS.with(|a| a.set(a.get() + 1));
        unsafe { System.alloc(layout) }
    }

    unsafe fn dealloc(&self, ptr: *mut u8, layout: Layout) {
        CALLS.with(|a| a.set(a.get() + 1));
        unsafe { System.dealloc(ptr, layout) }
    }

    unsafe fn realloc(&self, ptr: *mut u8, layout: Layout, new_size: usize) -> *mut u8 {
        CALLS.with(|a| a.set(a.get() + 1));
        unsafe { System.realloc(ptr, layout, new_size) }
    }
}

#[global_allocator]
static COUNTING: Counting = Counting;

fn calls() -> u64 {
    CALLS.with(|a| a.get())
}

/// A clock that moves by `step` nanoseconds each time it is read; the test sets the step.
#[derive(Clone, Default)]
struct FakeClock {
    now: Arc<AtomicU64>,
    step: Arc<AtomicU64>,
}

impl Clock for FakeClock {
    fn nanos(&self) -> u64 {
        self.now.fetch_add(self.step.load(Ordering::Relaxed), Ordering::Relaxed)
    }
}

struct Noise(u64);

impl Noise {
    fn next(&mut self) -> f32 {
        self.0 ^= self.0 << 13;
        self.0 ^= self.0 >> 7;
        self.0 ^= self.0 << 17;
        ((self.0 >> 11) as f64 / (1u64 << 53) as f64 * 2.0 - 1.0) as f32
    }
}

const RATE: f32 = 48000.0;
const BLOCK: usize = 128;

fn config(input_rate: f32, output_rate: f32, resample: bool) -> Config {
    Config {
        input_rate,
        output_rate,
        input_channels: 2,
        channel: 1,
        output_channels: 2,
        resample,
    }
}

fn sound(name: &str) -> ChainParams {
    ready_made::chain(name).unwrap()
}

/// The chain, the output stage and the guard run directly, as the engine runs them.
struct Reference {
    chain: Chain,
    stage: OutputStage,
    guard: FeedbackGuard,
}

impl Reference {
    fn new(params: &Params, chain: &ChainParams, volume: f64) -> Self {
        let mut c = Chain::new(RATE, BLOCK, params);
        c.apply(Some(chain));
        let mut stage = OutputStage::new(RATE, params, volume);
        stage.start();
        Self {
            chain: c,
            stage,
            guard: FeedbackGuard::new(RATE, BLOCK, params),
        }
    }

    fn block(&mut self, block: &mut [f32], volume: f64) {
        self.chain.process(block);
        self.stage.set_volume(volume);
        self.stage.process(block);
        if self.stage.is_faded_in() {
            self.guard.process(block);
        }
    }
}

/// Interleaves the guitar on channel 1 (the second) of a two-channel input, with noise on the other channel.
fn interleaved(guitar: &[f32], other: &mut Noise) -> Vec<f32> {
    guitar.iter().flat_map(|s| [other.next(), *s]).collect()
}

#[test]
fn the_output_is_the_chains_on_the_chosen_channel_in_both_output_channels() {
    let params = Params::defaults();
    let crunch = sound("Crunch");
    let (mut input, mut output, control) = engine::build(&config(RATE, RATE, false), &params, crunch, 0.6, FakeClock::default());
    let mut reference = Reference::new(&params, &crunch, 0.6);
    let mut guitar = Noise(7);
    let mut other = Noise(99);
    let mut out = vec![0f32; 2 * BLOCK];
    for _ in 0..(3 * 48000 / BLOCK) {
        let mut block: Vec<f32> = (0..BLOCK).map(|_| guitar.next() * 0.1).collect();
        input.on_input(&interleaved(&block, &mut other), |s| s);
        output.on_output(&mut out, |s| s);
        reference.block(&mut block, 0.6);
        for (i, expected) in block.iter().enumerate() {
            assert_eq!(out[2 * i], *expected, "left");
            assert_eq!(out[2 * i + 1], *expected, "right");
        }
    }
    assert_eq!(control.state(), State::On, "faded in");
    assert_eq!(control.underruns(), 0);
    assert_eq!(control.dropped_blocks(), 0);
}

#[test]
fn a_mono_output_and_a_wider_output_are_written_too() {
    let params = Params::defaults();
    let clean = sound("Clean");
    for channels in [1, 4] {
        let mut config = config(RATE, RATE, false);
        config.output_channels = channels;
        let (mut input, mut output, _control) = engine::build(&config, &params, clean, 1.0, FakeClock::default());
        let mut reference = Reference::new(&params, &clean, 1.0);
        let mut guitar = Noise(3);
        let mut other = Noise(4);
        let mut out = vec![0f32; channels * BLOCK];
        for _ in 0..200 {
            let mut block: Vec<f32> = (0..BLOCK).map(|_| guitar.next() * 0.2).collect();
            input.on_input(&interleaved(&block, &mut other), |s| s);
            output.on_output(&mut out, |s| s);
            reference.block(&mut block, 1.0);
            for (i, expected) in block.iter().enumerate() {
                let frame = &out[i * channels..(i + 1) * channels];
                assert_eq!(frame[0], *expected);
                if channels > 1 {
                    assert_eq!(frame[1], *expected);
                    assert!(frame[2..].iter().all(|s| *s == 0.0), "the other channels are silent");
                }
            }
        }
    }
}

#[test]
fn callbacks_of_any_size_give_the_same_output() {
    let params = Params::defaults();
    let lead = sound("Lead");
    let (mut input, mut output, _control) = engine::build(&config(RATE, RATE, false), &params, lead, 0.8, FakeClock::default());
    let mut reference = Reference::new(&params, &lead, 0.8);
    let mut guitar = Noise(11);
    let mut other = Noise(12);
    // two blocks in, then the output asks for them in callbacks of 100 and 156 frames
    let mut produced = Vec::new();
    let mut expected = Vec::new();
    for _ in 0..300 {
        let mut a: Vec<f32> = (0..BLOCK).map(|_| guitar.next() * 0.1).collect();
        let mut b: Vec<f32> = (0..BLOCK).map(|_| guitar.next() * 0.1).collect();
        input.on_input(&interleaved(&a, &mut other), |s| s);
        input.on_input(&interleaved(&b, &mut other), |s| s);
        for frames in [100, 156] {
            let mut out = vec![0f32; 2 * frames];
            output.on_output(&mut out, |s| s);
            produced.extend(out.chunks(2).map(|f| f[0]));
        }
        reference.block(&mut a, 0.8);
        reference.block(&mut b, 0.8);
        expected.extend(a);
        expected.extend(b);
    }
    assert_eq!(produced, expected);
}

#[test]
fn a_change_goes_through_the_mailbox_and_takes_effect_at_the_next_block() {
    let params = Params::defaults();
    let (clean, crunch, lead) = (sound("Clean"), sound("Crunch"), sound("Lead"));
    let (mut input, mut output, control) = engine::build(&config(RATE, RATE, false), &params, clean, 0.7, FakeClock::default());
    let mut reference = Reference::new(&params, &clean, 0.7);
    let mut guitar = Noise(5);
    let mut other = Noise(6);
    let mut out = vec![0f32; 2 * BLOCK];
    let mut counted = 0;
    for index in 0..2000 {
        let volume = if index < 1000 { 0.7 } else { 0.4 };
        match index {
            300 => {
                control.post_chain(crunch);
                reference.chain.apply(Some(&crunch));
            }
            700 => {
                control.post_chain(lead);
                reference.chain.apply(Some(&lead));
            }
            1000 => control.set_volume(0.4),
            1500 => {
                // two posts before a block: the second replaces the first
                control.post_chain(crunch);
                control.post_chain(clean);
                reference.chain.apply(Some(&clean));
            }
            _ => {}
        }
        let mut block: Vec<f32> = (0..BLOCK).map(|_| guitar.next() * 0.1).collect();
        let data = interleaved(&block, &mut other);
        let before = calls();
        input.on_input(&data, |s| s);
        output.on_output(&mut out, |s| s);
        counted += calls() - before;
        reference.block(&mut block, volume);
        for (i, expected) in block.iter().enumerate() {
            assert_eq!(out[2 * i], *expected, "block {index}");
        }
    }
    assert_eq!(counted, 0, "the callbacks allocated or freed {counted} times");
}

#[test]
fn the_ring_keeps_at_most_its_blocks_and_counts_the_dropped_ones() {
    let params = Params::defaults();
    let (mut input, mut output, control) = engine::build(&config(RATE, RATE, false), &params, sound("Clean"), 0.7, FakeClock::default());
    let mut other = Noise(1);
    for _ in 0..10 {
        input.on_input(&interleaved(&[0.1; BLOCK], &mut other), |s| s);
    }
    let mut out = vec![0f32; 2 * BLOCK];
    output.on_output(&mut out, |s| s);
    let kept = params.get(Id::DevicesRingBlocks) as u64;
    assert_eq!(control.dropped_blocks(), 10 - kept);
    // the input late: the block is played from what there is, the rest silent, and counted
    output.on_output(&mut out, |s| s);
    output.on_output(&mut out, |s| s);
    assert_eq!(control.underruns(), 1, "one late block (the kept blocks were played first)");
}

#[test]
fn the_block_size_and_the_ring_blocks_are_external_parameters() {
    let params = Params::from_values([("guitarfx.devices.block", "64"), ("guitarfx.devices.ring-blocks", "4")]);
    let (mut input, mut output, control) = engine::build(&config(RATE, RATE, false), &params, sound("Clean"), 0.7, FakeClock::default());
    let mut other = Noise(1);
    for _ in 0..10 {
        input.on_input(&interleaved(&[0.1; 64], &mut other), |s| s);
    }
    let mut out = vec![0f32; 2 * 64];
    output.on_output(&mut out, |s| s);
    assert_eq!(control.dropped_blocks(), 6);
    assert_eq!(control.block(), 64);
}

/// The zero crossings' rate, in Hz.
fn frequency(samples: &[f32], rate: f64) -> f64 {
    let mut crossings = Vec::new();
    for i in 1..samples.len() {
        if samples[i - 1] < 0.0 && samples[i] >= 0.0 {
            let t = samples[i - 1] / (samples[i - 1] - samples[i]);
            crossings.push((i - 1) as f64 + f64::from(t));
        }
    }
    let span = crossings[crossings.len() - 1] - crossings[0];
    (crossings.len() - 1) as f64 / span * rate
}

#[test]
fn a_different_input_rate_is_resampled_to_the_outputs_and_the_pitch_kept() {
    let params = Params::defaults();
    for (input_rate, output_rate) in [(44100.0f32, 48000.0f32), (48000.0, 44100.0), (48000.0, 48000.0)] {
        let (mut input, mut output, control) =
            engine::build(&config(input_rate, output_rate, true), &params, sound("Clean"), 1.0, FakeClock::default());
        let mut other = Noise(2);
        let mut phase = 0.0f64;
        let mut produced = Vec::new();
        let mut out = vec![0f32; 2 * BLOCK];
        // each side at its own clock: the input gives 128 frames every 128 / input rate, the output asks for 128 every 128 / output rate
        let mut input_time = 0.0f64;
        for callback in 0..(6 * output_rate as usize / BLOCK) {
            let now = (callback + 1) as f64 * BLOCK as f64 / f64::from(output_rate);
            while input_time < now {
                input_time += BLOCK as f64 / f64::from(input_rate);
                let block: Vec<f32> = (0..BLOCK)
                    .map(|_| {
                        phase += 440.0 / f64::from(input_rate);
                        (0.2 * (2.0 * std::f64::consts::PI * phase).sin()) as f32
                    })
                    .collect();
                input.on_input(&interleaved(&block, &mut other), |s| s);
            }
            output.on_output(&mut out, |s| s);
            produced.extend(out.chunks(2).map(|f| f[0]));
        }
        let measured = frequency(&produced[2 * output_rate as usize..], f64::from(output_rate));
        assert!(
            (measured - 440.0).abs() < 0.5,
            "{input_rate} → {output_rate}: 440 Hz came out at {measured} Hz"
        );
        assert_eq!(control.underruns(), 0, "{input_rate} → {output_rate}");
        assert_eq!(control.dropped_blocks(), 0, "{input_rate} → {output_rate}");
    }
}

fn block_nanos() -> u64 {
    (BLOCK as f64 / f64::from(RATE) * 1e9).round() as u64
}

#[test]
fn slow_blocks_lighten_the_chain_step_by_step_then_mute() {
    let params = Params::defaults();
    let clock = FakeClock::default();
    let (mut input, mut output, control) = engine::build(&config(RATE, RATE, false), &params, sound("Lead"), 0.7, clock.clone());
    let mut other = Noise(8);
    let mut out = vec![0f32; 2 * BLOCK];
    let budget = params.get(Id::OverloadBudget);
    let blocks = params.get(Id::OverloadBlocks) as usize;
    let mut run = |count: usize| {
        for _ in 0..count {
            input.on_input(&interleaved(&[0.05; BLOCK], &mut other), |s| s);
            output.on_output(&mut out, |s| s);
        }
    };
    // faded in at full speed first
    run(48000 / BLOCK);
    assert_eq!(control.state(), State::On);
    // the clock is read as a block starts and as it ends: each block takes a little more than the budget
    clock.step.store((budget * block_nanos() as f64) as u64 + 1, Ordering::Relaxed);
    run(blocks - 1);
    assert_eq!(control.lightening(), 0);
    for step in 1..=Chain::lightest() {
        run(1);
        assert_eq!(control.lightening(), step, "after {} slow blocks", step * blocks);
        run(blocks - 1);
    }
    assert_eq!(control.state(), State::On, "{:?}", control.state());
    run(1);
    assert_eq!(control.state(), State::Stopping, "past the last step the sound fades out");
    run(48000 / BLOCK);
    assert_eq!(control.state(), State::MutedOverload);
    let mut out = vec![0f32; 2 * BLOCK];
    output.on_output(&mut out, |s| s);
    assert!(out.iter().all(|s| *s == 0.0));
    assert!(control.slow_blocks() >= (Chain::lightest() as u64 + 1) * blocks as u64);
}

#[test]
fn a_fast_block_starts_the_count_again() {
    let params = Params::from_values([("guitarfx.overload.blocks", "5"), ("guitarfx.overload.budget", "0.25")]);
    let clock = FakeClock::default();
    let (mut input, mut output, control) = engine::build(&config(RATE, RATE, false), &params, sound("Lead"), 0.7, clock.clone());
    let mut other = Noise(8);
    let mut out = vec![0f32; 2 * BLOCK];
    let slow = (0.25 * block_nanos() as f64) as u64 + 1;
    let fast = (0.25 * block_nanos() as f64) as u64 - 1;
    for _ in 0..50 {
        for step in [slow, slow, slow, slow, fast] {
            clock.step.store(step, Ordering::Relaxed);
            input.on_input(&interleaved(&[0.05; BLOCK], &mut other), |s| s);
            output.on_output(&mut out, |s| s);
        }
    }
    assert_eq!(control.lightening(), 0, "never 5 slow blocks in a row");
    assert_eq!(control.slow_blocks(), 200);
}

#[test]
fn stop_fades_out_then_plays_silence() {
    let params = Params::defaults();
    let (mut input, mut output, control) = engine::build(&config(RATE, RATE, false), &params, sound("Clean"), 1.0, FakeClock::default());
    assert_eq!(control.state(), State::Starting);
    let mut other = Noise(8);
    let mut out = vec![0f32; 2 * BLOCK];
    let mut guitar = Noise(9);
    let mut run = |count: usize| {
        for _ in 0..count {
            let block: Vec<f32> = (0..BLOCK).map(|_| guitar.next() * 0.2).collect();
            input.on_input(&interleaved(&block, &mut other), |s| s);
            output.on_output(&mut out, |s| s);
        }
    };
    run(48000 / BLOCK);
    assert_eq!(control.state(), State::On);
    control.stop();
    run(1);
    assert_eq!(control.state(), State::Stopping);
    run(48000 / BLOCK);
    assert_eq!(control.state(), State::Off);
    output.on_output(&mut out, |s| s);
    assert!(out.iter().all(|s| *s == 0.0));
}

#[test]
fn the_input_meter_holds_the_peak_and_counts_clipping() {
    let params = Params::defaults();
    let (mut input, _output, control) = engine::build(&config(RATE, RATE, false), &params, sound("Clean"), 1.0, FakeClock::default());
    let mut quiet = vec![0.0f32; BLOCK];
    quiet[10] = -0.25;
    input.on_input(&interleaved(&quiet, &mut Noise(1)), |s| s);
    assert_eq!(control.take_peak(), 0.25, "the chosen channel's peak, not the other channel's noise");
    assert_eq!(control.take_peak(), 0.0, "taken");
    assert_eq!(control.clipped_blocks(), 0);
    let mut loud = vec![0.0f32; BLOCK];
    loud[3] = 0.999;
    input.on_input(&interleaved(&loud, &mut Noise(1)), |s| s);
    assert_eq!(control.clipped_blocks(), 1, "at or over guitarfx.devices.clipping (−0.1 dBFS)");
}

#[test]
fn sixteen_bit_devices_are_converted() {
    let params = Params::defaults();
    let (mut input, mut output, control) = engine::build(&config(RATE, RATE, false), &params, sound("Clean"), 1.0, FakeClock::default());
    let data: Vec<i16> = (0..BLOCK).flat_map(|_| [0i16, 16384]).collect();
    input.on_input(&data, engine::from_i16);
    assert_eq!(control.take_peak(), 0.5);
    let mut out = vec![0i16; 2 * BLOCK];
    output.on_output(&mut out, engine::to_i16);
    assert!(out.chunks(2).all(|f| f[0] == f[1]));
}
