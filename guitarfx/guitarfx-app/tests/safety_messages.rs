//! The safety messages (spec 027, T047, FR-012, SC-005, contracts/application.md "Messages") on the engine with a fake device and a fake
//! clock: the built-in microphone asks first; a howl mutes the sound with its message and a dialog; overload shows each lightening step,
//! then turns the sound off with its message; a clipping input shows its message at most once per interval and lights the meter.

use guitarfx_app::engine::{self, Clock, Config, State};
use guitarfx_app::safety::{self, Reading, Watch};
use guitarfx_dsp::chain::Chain;
use guitarfx_dsp::params::{Id, Params};
use guitarfx_dsp::ready_made;
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::Arc;
use std::time::{Duration, Instant};

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

const RATE: f32 = 48000.0;
const BLOCK: usize = 128;

fn config() -> Config {
    Config {
        input_rate: RATE,
        input_channels: 1,
        channel: 0,
        output_rate: RATE,
        output_channels: 2,
        resample: false,
    }
}

fn reading(control: &engine::Control) -> Reading {
    Reading {
        state: control.state(),
        lightening: control.lightening(),
        clipped: control.clipped_blocks(),
    }
}

#[test]
fn the_built_in_microphone_asks_first() {
    assert!(safety::needs_warning("MacBook Pro Microphone", false));
    assert!(!safety::needs_warning("MacBook Pro Microphone", true), "Turn on anyway");
    assert!(!safety::needs_warning("Rocksmith Guitar Adapter", false));
}

#[test]
fn a_howl_mutes_the_sound_with_its_message_and_a_dialog() {
    let params = Params::defaults();
    let (mut input, mut output, control) = engine::build(&config(), &params, ready_made::chain("Clean").unwrap(), 1.0, FakeClock::default());
    let mut watch = Watch::new();
    let now = Instant::now();
    let mut out = vec![0f32; 2 * BLOCK];
    let mut phase = 0f64;
    let mut seen = None;
    // a loud steady tone, the shape of a howl, for up to five seconds
    for _ in 0..(5 * 48000 / BLOCK) {
        let block: Vec<f32> = (0..BLOCK)
            .map(|_| {
                phase += 2.0 * std::f64::consts::PI * 1000.0 / f64::from(RATE);
                (0.9 * phase.sin()) as f32
            })
            .collect();
        input.on_input(&block, |s| s);
        output.on_output(&mut out, |s| s);
        let outcome = watch.step(&reading(&control), now);
        if outcome.off {
            seen = Some(outcome);
            break;
        }
    }
    let outcome = seen.expect("the howl is caught");
    assert_eq!(control.state(), State::MutedFeedback);
    assert_eq!(outcome.message.as_deref(), Some("message.feedback"));
    assert!(outcome.dialog, "a howl asks for an OK");
}

#[test]
fn overload_shows_each_lightening_step_then_turns_the_sound_off() {
    let params = Params::defaults();
    let clock = FakeClock::default();
    let (mut input, mut output, control) = engine::build(&config(), &params, ready_made::chain("Lead").unwrap(), 0.7, clock.clone());
    let mut watch = Watch::new();
    let now = Instant::now();
    let mut out = vec![0f32; 2 * BLOCK];
    let mut messages = Vec::new();
    let mut run = |count: usize, watch: &mut Watch, messages: &mut Vec<String>| {
        for _ in 0..count {
            input.on_input(&[0.05; BLOCK], |s| s);
            output.on_output(&mut out, |s| s);
            let outcome = watch.step(&reading(&control), now);
            messages.extend(outcome.message);
            if outcome.off {
                return true;
            }
        }
        false
    };
    run(48000 / BLOCK, &mut watch, &mut messages);
    assert!(messages.is_empty(), "{messages:?}");
    let block_nanos = BLOCK as f64 / f64::from(RATE) * 1e9;
    clock
        .step
        .store((params.get(Id::OverloadBudget) * block_nanos) as u64 + 1, Ordering::Relaxed);
    let blocks = params.get(Id::OverloadBlocks) as usize;
    let off = run((Chain::lightest() + 1) * blocks + 48000 / BLOCK, &mut watch, &mut messages);
    assert!(off);
    let mut expected: Vec<String> = (1..=Chain::lightest()).map(|n| format!("message.lighter.{n}")).collect();
    expected.push("message.overload".to_string());
    assert_eq!(messages, expected);
    assert_eq!(control.state(), State::MutedOverload);
}

#[test]
fn clipping_shows_its_message_at_most_once_per_interval() {
    let params = Params::defaults();
    let (mut input, _output, control) = engine::build(&config(), &params, ready_made::chain("Clean").unwrap(), 1.0, FakeClock::default());
    let mut watch = Watch::new();
    let start = Instant::now();
    let mut loud = [0f32; BLOCK];
    loud[0] = 1.0;
    input.on_input(&loud, |s| s);
    let first = watch.step(&reading(&control), start);
    assert_eq!(first.message.as_deref(), Some("message.clipping"));
    assert!(first.clipping && !first.off && !first.dialog);
    input.on_input(&loud, |s| s);
    let soon = watch.step(&reading(&control), start + Duration::from_millis(300));
    assert_eq!(soon.message, None, "not again within the interval");
    assert!(soon.clipping);
    let quiet = watch.step(&reading(&control), start + Duration::from_secs(3));
    assert!(!quiet.clipping, "the light goes out once the input stops clipping");
    input.on_input(&loud, |s| s);
    let later = watch.step(&reading(&control), start + Duration::from_secs(4));
    assert_eq!(later.message.as_deref(), Some("message.clipping"));
}
