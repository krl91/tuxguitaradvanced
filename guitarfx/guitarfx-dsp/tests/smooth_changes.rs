//! Every change of the chain is heard at once and without a click (spec 027, T022, SC-004), with the rule of spec 022's
//! `SmoothChangesTest`: a sine runs through the chain and the settings change half way. The largest step from one sample to the next stays
//! under 1.5 times the largest step of the same window with the old settings alone and with the new settings alone, and the chain is done
//! changing within 0.1 s.

mod common;

use common::{full, section, without, BLOCK, RATE};
use guitarfx_dsp::chain::Chain;
use guitarfx_dsp::knobs::{Character, Kind, Section, AMP_KNOBS};
use guitarfx_dsp::params::Params;
use guitarfx_dsp::ready_made;
use guitarfx_dsp::settings::{AmpSettings, ChainParams};

const BLOCKS: usize = RATE as usize / BLOCK;
const CHANGE: usize = BLOCKS / 2;
const FROM: usize = BLOCKS / 4;

struct Run {
    samples: Vec<f32>,
    changing_samples: usize,
}

fn run(first: &ChainParams, second: Option<&ChainParams>) -> Run {
    let mut chain = Chain::new(RATE, BLOCK, &Params::defaults());
    chain.apply(Some(first));
    let mut run = Run {
        samples: Vec::with_capacity(BLOCKS * BLOCK),
        changing_samples: 0,
    };
    let mut block = [0f32; BLOCK];
    let mut counting = false;
    for b in 0..BLOCKS {
        if b == CHANGE {
            if let Some(second) = second {
                chain.apply(Some(second));
                counting = true;
            }
        }
        for (i, sample) in block.iter_mut().enumerate() {
            *sample = (0.25 * (2.0 * std::f64::consts::PI * 220.0 * (b * BLOCK + i) as f64 / f64::from(RATE)).sin()) as f32;
        }
        chain.process(&mut block);
        if counting {
            if chain.changing() {
                run.changing_samples += BLOCK;
            } else {
                counting = false;
            }
        }
        run.samples.extend_from_slice(&block);
    }
    run
}

fn largest_step(samples: &[f32]) -> f64 {
    samples[FROM * BLOCK..]
        .windows(2)
        .map(|w| f64::from((w[1] - w[0]).abs()))
        .fold(0.0, f64::max)
}

fn check(what: &str, first: &ChainParams, second: &ChainParams, failures: &mut Vec<String>) {
    let reference = largest_step(&run(first, None).samples).max(largest_step(&run(second, None).samples));
    let changed = run(first, Some(second));
    let step = largest_step(&changed.samples);
    // as spec 022 writes it: a step that is not a number fails too
    #[allow(clippy::neg_cmp_op_on_partial_ord)]
    if !(step < 1.5 * reference) {
        failures.push(format!("{what}: a step of {step} against {reference}"));
    }
    if changed.changing_samples > RATE as usize / 10 {
        failures.push(format!(
            "{what}: still changing after {} ms",
            changed.changing_samples * 1000 / RATE as usize
        ));
    }
}

fn assert_none(failures: Vec<String>) {
    assert!(failures.is_empty(), "{}", failures.join("\n"));
}

#[test]
fn every_pedal_knob_jumps_from_its_minimum_to_its_maximum() {
    let mut failures = Vec::new();
    let base = full();
    for kind in Kind::ALL {
        let pedal = *base.pedal(kind).unwrap();
        for (i, knob) in kind.knobs().iter().enumerate() {
            check(
                &format!("{kind:?} {}", knob.key),
                &base.with_pedal(pedal.with(i, knob.min)),
                &base.with_pedal(pedal.with(i, knob.max)),
                &mut failures,
            );
        }
    }
    assert_none(failures);
}

#[test]
fn every_amp_knob_jumps_from_0_to_10_and_the_character_and_cabinet_change() {
    let mut failures = Vec::new();
    for character in Character::ALL {
        let base = full().with_amp(AmpSettings {
            character,
            ..AmpSettings::default()
        });
        for (i, name) in AMP_KNOBS.iter().enumerate() {
            check(
                &format!("{character:?} {name}"),
                &base.with_amp(base.amp.with_knob(i, 0.0)),
                &base.with_amp(base.amp.with_knob(i, 10.0)),
                &mut failures,
            );
        }
        for other in Character::ALL.into_iter().filter(|c| *c != character) {
            check(
                &format!("{character:?} to {other:?}"),
                &base,
                &base.with_amp(AmpSettings {
                    character: other,
                    ..base.amp
                }),
                &mut failures,
            );
        }
        let off = base.with_amp(AmpSettings { cabinet: false, ..base.amp });
        check(&format!("{character:?} cabinet off"), &base, &off, &mut failures);
        check(&format!("{character:?} cabinet on"), &off, &base, &mut failures);
    }
    assert_none(failures);
}

#[test]
fn every_pedal_is_switched_on_and_off() {
    let mut failures = Vec::new();
    let base = full();
    for kind in Kind::ALL {
        let off = base.with_pedal(base.pedal(kind).unwrap().with_enabled(false));
        check(&format!("{kind:?} off"), &base, &off, &mut failures);
        check(&format!("{kind:?} on"), &off, &base, &mut failures);
    }
    assert_none(failures);
}

#[test]
fn a_pedal_is_added_removed_and_moved() {
    let mut failures = Vec::new();
    let base = full();
    for kind in Kind::ALL {
        check(&format!("{kind:?} removed"), &base, &without(&base, kind), &mut failures);
        check(&format!("{kind:?} added"), &without(&base, kind), &base, &mut failures);
    }
    let after = section(&base, Section::After);
    let mut before = section(&base, Section::Before);
    before.swap(0, 1);
    check(
        "compressor before the gate",
        &base,
        &ChainParams::new(&before, base.amp, &after).unwrap(),
        &mut failures,
    );
    let before = section(&base, Section::Before);
    let mut swapped = after.clone();
    swapped.swap(1, 2);
    check(
        "reverb before the echo",
        &base,
        &ChainParams::new(&before, base.amp, &swapped).unwrap(),
        &mut failures,
    );
    let mut reversed = after.clone();
    reversed.reverse();
    check(
        "the after pedals reversed",
        &base,
        &ChainParams::new(&before, base.amp, &reversed).unwrap(),
        &mut failures,
    );
    assert_none(failures);
}

#[test]
fn each_ready_made_sound_is_picked_after_each_other() {
    let mut failures = Vec::new();
    for (from, _) in ready_made::ALL {
        for (to, _) in ready_made::ALL {
            if from != to {
                check(
                    &format!("{from} to {to}"),
                    &ready_made::chain(from).unwrap(),
                    &ready_made::chain(to).unwrap(),
                    &mut failures,
                );
            }
        }
    }
    let dry = ChainParams::new(&[], AmpSettings::default(), &[]).unwrap();
    check("dry to Lead", &dry, &ready_made::chain("Lead").unwrap(), &mut failures);
    assert_none(failures);
}
