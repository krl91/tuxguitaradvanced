//! The plugin's process, driven directly (spec 027, T052, FR-024, SC-003): at 44.1, 48 and 96 kHz and with host blocks of 64, 128, 512 and
//! 1024 frames, the output is the chain's, the output stage's and the guard's run in pieces of `guitarfx.devices.block` frames on the same
//! signal; a stereo input is the mean of its two channels; an offline render, which reads no clock, equals a real-time one; slow pieces
//! lighten the chain and then mute it, as in the application; nothing is allocated or freed in `process`.

use guitarfx_dsp::chain::Chain;
use guitarfx_dsp::chain_text;
use guitarfx_dsp::feedback_guard::FeedbackGuard;
use guitarfx_dsp::output_stage::OutputStage;
use guitarfx_dsp::params::{Id, Params};
use guitarfx_dsp::ready_made;
use guitarfx_dsp::settings::ChainParams;
use guitarfx_plugin::processor::{Clock, Processor, State};
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

const RATES: [f32; 3] = [44100.0, 48000.0, 96000.0];
const HOST_BLOCKS: [usize; 4] = [64, 128, 512, 1024];
/// SC-003's limit.
const LIMIT_DB: f64 = -90.0;

/// The ready-made sounds and a chain with every pedal.
fn sounds() -> Vec<(String, ChainParams)> {
    let mut all: Vec<(String, ChainParams)> = ready_made::ALL
        .iter()
        .map(|(name, _)| (name.to_string(), ready_made::chain(name).unwrap()))
        .collect();
    let every = "gate:on,-60,150|compressor:on,5,10,5|overdrive:on,5,5,5|distortion:on,5,5,5|amp:crunch,5,5,5,5,6|cab:on|\
                 chorus:on,0.8,4,40|echo:on,450,4,4,35|reverb:on,4,5,20";
    all.push(("every pedal".to_string(), chain_text::parse(every).unwrap()));
    all
}

/// Two plucked notes with harmonics and a little noise, on two channels (the second at half the first), with silence between.
fn signal(rate: f32, seconds: f64) -> (Vec<f32>, Vec<f32>) {
    let frames = (f64::from(rate) * seconds) as usize;
    let mut noise = Noise(11);
    let mut left = Vec::with_capacity(frames);
    let mut right = Vec::with_capacity(frames);
    for i in 0..frames {
        let t = i as f64 / f64::from(rate);
        let note = |start: f64, hz: f64| {
            if t < start {
                return 0.0;
            }
            let u = t - start;
            (-3.0 * u).exp() * (0.5 * (2.0 * std::f64::consts::PI * hz * u).sin() + 0.2 * (4.0 * std::f64::consts::PI * hz * u).sin())
        };
        let value = 0.4 * (note(0.05, 110.0) + note(seconds * 0.6, 196.0)) + 0.001 * f64::from(noise.next());
        left.push(value as f32);
        right.push((value * 0.5) as f32);
    }
    (left, right)
}

/// The chain, the output stage at full volume and the guard, run in pieces of `guitarfx.devices.block` frames, as the application runs them.
fn reference(rate: f32, params: &Params, chain: &ChainParams, mono: &[f32]) -> Vec<f32> {
    let block = params.get(Id::DevicesBlock) as usize;
    let mut elements = Chain::new(rate, block, params);
    elements.apply(Some(chain));
    let mut stage = OutputStage::new(rate, params, 1.0);
    stage.start();
    let mut guard = FeedbackGuard::new(rate, block, params);
    let mut out = mono.to_vec();
    for piece in out.chunks_mut(block) {
        elements.process(piece);
        stage.set_volume(1.0);
        stage.process(piece);
        if stage.is_faded_in() {
            assert!(!guard.process(piece), "no howl in the reference signals");
        }
    }
    out
}

/// Runs the processor in host blocks of `host` frames; returns the left and right outputs.
fn render<C: Clock>(processor: &mut Processor<C>, inputs: &[&[f32]], host: usize) -> (Vec<f32>, Vec<f32>) {
    let frames = inputs[0].len();
    let mut left = vec![0f32; frames];
    let mut right = vec![0f32; frames];
    let mut allocated = 0;
    let mut offset = 0;
    while offset < frames {
        let n = host.min(frames - offset);
        // the host's buffer is in place: the input in the first channels, the output written over it
        left[offset..offset + n].copy_from_slice(&inputs[0][offset..offset + n]);
        if let Some(second) = inputs.get(1) {
            right[offset..offset + n].copy_from_slice(&second[offset..offset + n]);
        } else {
            right[offset..offset + n].fill(0.0);
        }
        let mut channels: [&mut [f32]; 2] = [&mut left[offset..offset + n], &mut right[offset..offset + n]];
        let before = calls();
        processor.process(&mut channels, inputs.len());
        allocated += calls() - before;
        offset += n;
    }
    assert_eq!(allocated, 0, "process allocated or freed");
    (left, right)
}

fn worst_db(a: &[f32], b: &[f32]) -> f64 {
    let worst = a.iter().zip(b).map(|(x, y)| (f64::from(*x) - f64::from(*y)).abs()).fold(0.0, f64::max);
    20.0 * worst.max(1e-300).log10()
}

#[test]
fn the_output_is_the_chains_at_every_rate_and_host_block() {
    let params = Params::defaults();
    for rate in RATES {
        let (left, right) = signal(rate, 1.5);
        let mono: Vec<f32> = left.iter().zip(&right).map(|(l, r)| (l + r) / 2.0).collect();
        for (name, chain) in sounds() {
            let expected = reference(rate, &params, &chain, &mono);
            for host in HOST_BLOCKS {
                let mut processor = Processor::new(rate, host, &params, chain, Some(FakeClock::default()));
                let (out_left, out_right) = render(&mut processor, &[&left, &right], host);
                let db = worst_db(&out_left, &expected);
                assert!(db <= LIMIT_DB, "{name} at {rate} Hz, host block {host}: {db:.1} dBFS");
                assert_eq!(out_left, out_right, "the same sound in both output channels");
                assert_eq!(processor.shared().state(), State::On, "{name} at {rate} Hz, host block {host}");
            }
        }
    }
}

#[test]
fn a_mono_input_is_played_as_it_is() {
    let params = Params::defaults();
    let (left, _) = signal(48000.0, 1.0);
    let chain = ready_made::chain("Crunch").unwrap();
    let expected = reference(48000.0, &params, &chain, &left);
    let mut processor = Processor::new(48000.0, 512, &params, chain, Some(FakeClock::default()));
    let (out_left, out_right) = render(&mut processor, &[&left], 512);
    assert!(worst_db(&out_left, &expected) <= LIMIT_DB);
    assert_eq!(out_left, out_right);
}

#[test]
fn an_offline_render_equals_a_real_time_one() {
    let params = Params::defaults();
    for rate in RATES {
        let (left, right) = signal(rate, 1.0);
        for (name, chain) in sounds() {
            let mut real_time = Processor::new(rate, 1024, &params, chain, Some(FakeClock::default()));
            let mut offline = Processor::<FakeClock>::new(rate, 1024, &params, chain, None);
            let a = render(&mut real_time, &[&left, &right], 1024);
            let b = render(&mut offline, &[&left, &right], 1024);
            assert_eq!(a, b, "{name} at {rate} Hz");
        }
    }
}

#[test]
fn the_piece_is_the_external_block_size() {
    let params = Params::from_values([("guitarfx.devices.block", "256")]);
    let (left, right) = signal(48000.0, 1.0);
    let mono: Vec<f32> = left.iter().zip(&right).map(|(l, r)| (l + r) / 2.0).collect();
    let chain = ready_made::chain("Lead").unwrap();
    let expected = reference(48000.0, &params, &chain, &mono);
    let mut processor = Processor::new(48000.0, 1024, &params, chain, Some(FakeClock::default()));
    let (out, _) = render(&mut processor, &[&left, &right], 1024);
    assert!(worst_db(&out, &expected) <= LIMIT_DB);
}

#[test]
fn a_change_goes_through_the_mailbox() {
    let params = Params::defaults();
    let (left, right) = signal(48000.0, 1.0);
    let clean = ready_made::chain("Clean").unwrap();
    let lead = ready_made::chain("Lead").unwrap();
    let mut processor = Processor::new(48000.0, 512, &params, clean, Some(FakeClock::default()));
    let shared = processor.shared();
    let half = left.len() / 2;
    render(&mut processor, &[&left[..half], &right[..half]], 512);
    shared.post_chain(lead);
    assert_eq!(processor.applied(), clean);
    render(&mut processor, &[&left[half..], &right[half..]], 512);
    assert_eq!(processor.applied(), lead);
    // a second change: the box taken before is handed back, not freed in process
    shared.post_chain(clean);
    render(&mut processor, &[&left[..512], &right[..512]], 512);
    assert_eq!(processor.applied(), clean);
}

#[test]
fn slow_pieces_lighten_the_chain_step_by_step_then_mute() {
    let params = Params::defaults();
    let rate = 48000.0;
    let block = params.get(Id::DevicesBlock) as usize;
    let block_nanos = block as f64 / f64::from(rate) * 1e9;
    let budget = params.get(Id::OverloadBudget);
    let blocks = params.get(Id::OverloadBlocks) as usize;
    let clock = FakeClock::default();
    let mut processor = Processor::new(rate, block, &params, ready_made::chain("Lead").unwrap(), Some(clock.clone()));
    let shared = processor.shared();
    let quiet = vec![0.05f32; block];
    let run = |processor: &mut Processor<FakeClock>, count: usize| {
        for _ in 0..count {
            render(processor, &[&quiet], block);
        }
    };
    run(&mut processor, 48000 / block);
    assert_eq!(shared.state(), State::On);
    // the clock is read as a piece starts and as it ends: each piece takes a little more than the budget
    clock.step.store((budget * block_nanos) as u64 + 1, Ordering::Relaxed);
    run(&mut processor, blocks - 1);
    assert_eq!(shared.lightening(), 0);
    for step in 1..=Chain::lightest() {
        run(&mut processor, 1);
        assert_eq!(shared.lightening(), step, "after {} slow pieces", step * blocks);
        run(&mut processor, blocks - 1);
    }
    run(&mut processor, 1);
    assert_eq!(shared.state(), State::Stopping, "past the last step the sound fades out");
    run(&mut processor, 48000 / block);
    assert_eq!(shared.state(), State::MutedOverload);
    let (out, _) = render(&mut processor, &[&quiet], block);
    assert!(out.iter().all(|s| *s == 0.0));
    // the host starts the plugin again: the sound comes back, the chain whole
    processor.reset();
    clock.step.store(0, Ordering::Relaxed);
    run(&mut processor, 48000 / block);
    assert_eq!(shared.state(), State::On);
    assert_eq!(shared.lightening(), 0);
}

#[test]
fn a_short_piece_has_a_shorter_budget() {
    // a host block of 64 frames gives 64-frame pieces: their budget is half a 128-frame block's
    let params = Params::defaults();
    let rate = 48000.0;
    let budget = params.get(Id::OverloadBudget);
    let blocks = params.get(Id::OverloadBlocks) as usize;
    let piece_nanos = 64.0 / f64::from(rate) * 1e9;
    let clock = FakeClock::default();
    let mut processor = Processor::new(rate, 64, &params, ready_made::chain("Lead").unwrap(), Some(clock.clone()));
    let quiet = vec![0.05f32; 64];
    for _ in 0..(48000 / 64) {
        render(&mut processor, &[&quiet], 64);
    }
    clock.step.store((budget * piece_nanos) as u64 + 1, Ordering::Relaxed);
    for _ in 0..blocks {
        render(&mut processor, &[&quiet], 64);
    }
    assert_eq!(processor.shared().lightening(), 1);
}

#[test]
fn the_reported_latency_is_the_amps_oversampling_delay() {
    // inventory.md "Reported latency": the amp's oversampling at its largest factor, up and down, rounded down
    use guitarfx_dsp::oversampler::{Oversampler, LARGEST_FACTOR};
    let rate = 48000.0;
    // low enough that the delay is under one period
    let hz = 500.0;
    let frames = 4800;
    let omega = 2.0 * std::f64::consts::PI * hz / rate;
    let input: Vec<f32> = (0..frames).map(|n| (omega * n as f64).sin() as f32 * 0.5).collect();
    let mut oversampler = Oversampler::new(LARGEST_FACTOR, frames);
    let mut up = vec![0f32; frames * LARGEST_FACTOR];
    let mut output = vec![0f32; frames];
    oversampler.up(&input, &mut up);
    oversampler.down(&up, &mut output);
    // the phase of the output against the input, over whole periods once the filters are full: the delay at that frequency
    let (mut in_phase, mut quadrature) = (0.0, 0.0);
    for (n, value) in output.iter().enumerate().skip(480) {
        in_phase += f64::from(*value) * (omega * n as f64).sin();
        quadrature += f64::from(*value) * (omega * n as f64).cos();
    }
    let period = rate / hz;
    let delay = ((-quadrature).atan2(in_phase) / omega).rem_euclid(period);
    // up: 31 samples at each stage's rate; down: 30 (inventory.md)
    assert!((delay - 45.75).abs() < 0.01, "{delay}");
    assert_eq!(guitarfx_plugin::processor::latency(), delay.floor() as u32);
}
