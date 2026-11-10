//! Endurance (spec 027, SC-006, T063): the engine for one hour on a fake pair of devices paced by the clock, a chain of six active
//! elements (the ready-made Clean echo: compressor, amp, cabinet, chorus, echo, reverb), both devices' threads promoted to real-time audio
//! threads, as the devices' callbacks are. After a one-minute warm-up (spec 022's), it counts the dropped blocks, the underruns and the late
//! blocks, and the worst gap between two output callbacks; the goal is none dropped (SC-006).
//!
//! Ignored by default: it takes an hour. Run it on the owner's request, in release:
//! `cargo test --release -p guitarfx-app --test endurance -- --ignored --nocapture`
//! `GUITARFX_ENDURANCE_MINUTES` shortens it; `GUITARFX_ENDURANCE_REPORT` names the report file (else it is only printed).

use guitarfx_app::engine::{self, Config, SystemClock};
use guitarfx_dsp::params::{Id, Params};
use guitarfx_dsp::ready_made;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::thread;
use std::time::{Duration, Instant};

const RATE: f32 = 48000.0;
const MINUTES: u64 = 60;
const WARM_UP: Duration = Duration::from_secs(60);
const SOUND: &str = "Clean echo";

/// Sleeps until `deadline`; the last half millisecond is spun, as a device's clock is exact.
fn wait_until(deadline: Instant) {
    let spin = Duration::from_micros(500);
    let now = Instant::now();
    if deadline > now + spin {
        thread::sleep(deadline - now - spin);
    }
    while Instant::now() < deadline {
        std::hint::spin_loop();
    }
}

/// A guitar-like input: a decaying note every half second.
fn note(frame: u64) -> f32 {
    let rate = f64::from(RATE);
    let t = (frame % (rate as u64 / 2)) as f64 / rate;
    let pitch = 110.0 * (1 + (frame / (rate as u64 / 2)) % 4) as f64;
    (0.3 * (-4.0 * t).exp() * (std::f64::consts::TAU * pitch * t).sin()) as f32
}

#[test]
#[ignore = "one hour; run on the owner's request (SC-006)"]
fn one_hour_with_six_elements_drops_no_block() {
    let minutes = std::env::var("GUITARFX_ENDURANCE_MINUTES")
        .ok()
        .and_then(|m| m.parse().ok())
        .unwrap_or(MINUTES);
    let length = Duration::from_secs(minutes * 60);
    // a shortened run keeps three quarters of itself measured
    let warm_up = WARM_UP.min(length / 4);
    let params = Params::defaults();
    let block = params.get(Id::DevicesBlock) as usize;
    let chain = ready_made::chain(SOUND).expect("a ready-made sound");
    // four pedals, the amp and the cabinet
    assert_eq!(chain.before().len() + chain.after().len() + 2, 6, "six active elements");
    let config = Config {
        input_rate: RATE,
        output_rate: RATE,
        input_channels: 1,
        channel: 0,
        output_channels: 2,
        resample: false,
    };
    let (mut input, mut output, control) = engine::build(&config, &params, chain, 1.0, SystemClock::default());
    let block_time = Duration::from_secs_f64(block as f64 / f64::from(RATE));
    let start = Instant::now() + Duration::from_millis(100);
    let done = Arc::new(AtomicBool::new(false));

    // the input device: one block per block time, on a real-time thread as a device's input callback is
    let input_done = done.clone();
    let input_thread = thread::spawn(move || {
        let _ = tuxguitar_monitor_native::realtime::promote_current_thread(block, RATE);
        let mut samples = vec![0.0f32; block];
        let mut frame = 0u64;
        let mut k = 0u32;
        while !input_done.load(Ordering::Acquire) {
            wait_until(start + block_time * k);
            for sample in samples.iter_mut() {
                *sample = note(frame);
                frame += 1;
            }
            input.on_input(&samples, |s| s);
            k += 1;
        }
    });

    // the output device, half a block later: promoted, as the application's output thread
    let output_thread = thread::spawn(move || {
        let promoted = tuxguitar_monitor_native::realtime::promote_current_thread(block, RATE);
        let mut data = vec![0.0f32; block * 2];
        let mut worst = Duration::ZERO;
        let mut late = 0u64;
        let mut last: Option<Instant> = None;
        let mut warm = None;
        let mut k = 0u32;
        loop {
            let deadline = start + block_time / 2 + block_time * k;
            if deadline > start + length {
                break;
            }
            wait_until(deadline);
            output.on_output(&mut data, |s| s);
            let now = Instant::now();
            let measured = now >= start + warm_up;
            if measured && warm.is_none() {
                warm = Some((control.dropped_blocks(), control.underruns()));
            }
            if measured {
                if let Some(last) = last {
                    worst = worst.max(now - last);
                }
                if now > deadline + block_time {
                    late += 1;
                }
            }
            last = Some(now);
            k += 1;
        }
        let (dropped_before, underruns_before) = warm.unwrap_or((0, 0));
        (
            promoted,
            control.dropped_blocks() - dropped_before,
            control.underruns() - underruns_before,
            late,
            worst,
            control.state(),
        )
    });
    let (promoted, dropped, underruns, late, worst, state) = output_thread.join().expect("the output thread");
    done.store(true, Ordering::Release);
    input_thread.join().expect("the input thread");

    let report = format!(
        "# GuitarFX endurance (spec 027, SC-006, T063)\n\n\
         - {} {}, release build: {}\n\
         - {minutes} min at {RATE} Hz, blocks of {block} frames, the ready-made {SOUND} (six active elements); the first {} s are a warm-up, not counted.\n\
         - Output thread: {}\n\n\
         | Dropped blocks | Underruns | Late blocks | Worst gap between output callbacks (ms) | Block time (ms) | State at the end |\n\
         |---|---|---|---|---|---|\n\
         | {dropped} | {underruns} | {late} | {:.2} | {:.2} | {state:?} |\n",
        std::env::consts::OS,
        std::env::consts::ARCH,
        !cfg!(debug_assertions),
        warm_up.as_secs(),
        match &promoted {
            Ok(policy) => format!("promoted ({policy})"),
            Err(why) => format!("not promoted ({why})"),
        },
        worst.as_secs_f64() * 1000.0,
        block_time.as_secs_f64() * 1000.0,
    );
    println!("{report}");
    if let Ok(path) = std::env::var("GUITARFX_ENDURANCE_REPORT") {
        std::fs::write(&path, &report).expect("the report written");
    }
    assert_eq!(dropped, 0, "dropped blocks");
    assert_eq!(underruns, 0, "underruns");
    assert_eq!(state, engine::State::On, "the sound stayed on");
}
