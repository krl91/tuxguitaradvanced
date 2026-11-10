//! Nothing is allocated or freed once the chain, the output stage and the feedback guard are made (spec 027, T020, FR-015): ten seconds of
//! blocks with every knob moved, every element switched and moved, the sound changed and the chain lightened, as the audio callback runs
//! them.

mod common;

use common::{full, section, without, Noise, BLOCK, RATE};
use guitarfx_dsp::chain::Chain;
use guitarfx_dsp::feedback_guard::FeedbackGuard;
use guitarfx_dsp::knobs::{Character, Kind, Section, AMP_KNOBS};
use guitarfx_dsp::output_stage::OutputStage;
use guitarfx_dsp::params::Params;
use guitarfx_dsp::ready_made;
use guitarfx_dsp::settings::ChainParams;
use std::alloc::{GlobalAlloc, Layout, System};
use std::cell::Cell;

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

/// Every change the guitarist can make, made before the run (they are plain values, posted to the audio thread).
fn changes() -> Vec<ChainParams> {
    let base = full();
    let mut changes = vec![base];
    for kind in Kind::ALL {
        let pedal = *base.pedal(kind).unwrap();
        for (i, knob) in kind.knobs().iter().enumerate() {
            changes.push(base.with_pedal(pedal.with(i, knob.min)));
            changes.push(base.with_pedal(pedal.with(i, knob.max)));
        }
        changes.push(base.with_pedal(pedal.with_enabled(false)));
        changes.push(without(&base, kind));
        changes.push(base);
    }
    for i in 0..AMP_KNOBS.len() {
        changes.push(base.with_amp(base.amp.with_knob(i, 0.0)));
        changes.push(base.with_amp(base.amp.with_knob(i, 10.0)));
    }
    for character in Character::ALL {
        changes.push(base.with_amp(guitarfx_dsp::settings::AmpSettings { character, ..base.amp }));
    }
    changes.push(base.with_amp(guitarfx_dsp::settings::AmpSettings { cabinet: false, ..base.amp }));
    let mut before = section(&base, Section::Before);
    before.reverse();
    let mut after = section(&base, Section::After);
    after.reverse();
    changes.push(ChainParams::new(&before, base.amp, &after).unwrap());
    for (name, _) in ready_made::ALL {
        changes.push(ready_made::chain(name).unwrap());
    }
    changes
}

#[test]
fn the_audio_path_never_allocates_after_it_is_made() {
    let params = Params::defaults();
    let changes = changes();
    let mut chain = Chain::new(RATE, BLOCK, &params);
    let mut stage = OutputStage::new(RATE, &params, 0.7);
    let mut guard = FeedbackGuard::new(RATE, BLOCK, &params);
    let mut noise = Noise::new(7);
    let mut block = vec![0f32; BLOCK];
    let blocks = 10 * RATE as usize / BLOCK;
    let before = calls();
    chain.apply(Some(&changes[0]));
    stage.start();
    for b in 0..blocks {
        if b % 8 == 0 {
            chain.apply(Some(&changes[(b / 8) % changes.len()]));
        }
        if b % 200 == 100 {
            chain.lighten((b / 200) % (Chain::lightest() + 1));
        }
        if b == blocks / 2 {
            chain.apply(None);
        }
        if b == blocks / 2 + 1 {
            chain.reset();
            chain.apply(Some(&changes[1]));
        }
        stage.set_volume((b % 10) as f64 / 10.0);
        for (i, sample) in block.iter_mut().enumerate() {
            *sample = (0.3 * (2.0 * std::f64::consts::PI * 220.0 * (b * BLOCK + i) as f64 / f64::from(RATE)).sin() + 0.05 * noise.next()) as f32;
        }
        chain.process(&mut block);
        stage.process(&mut block);
        guard.process(&block);
        let last = block.len() / 2;
        chain.process(&mut block[..last]);
    }
    stage.stop();
    stage.process(&mut block);
    guard.reset();
    assert_eq!(calls() - before, 0, "allocations or frees in the audio path");
}

#[test]
fn the_counter_sees_an_allocation_and_a_free() {
    let before = calls();
    let boxed = std::hint::black_box(Box::new([0u8; 64]));
    drop(boxed);
    assert_eq!(calls() - before, 2);
}
