//! The output is safe (spec 027, T021): it never exceeds the ceiling (SC-007, FR-010), it fades in over half a second (FR-010), each
//! ready-made sound is quiet when the guitar is not played (FR-011), and a howl is muted within half a second (SC-005).

mod common;

use common::{db, full, Noise, BLOCK, RATE};
use guitarfx_dsp::chain::Chain;
use guitarfx_dsp::feedback_guard::FeedbackGuard;
use guitarfx_dsp::knobs::{Character, Kind, AMP_KNOBS};
use guitarfx_dsp::output_stage::OutputStage;
use guitarfx_dsp::params::Params;
use guitarfx_dsp::ready_made;
use guitarfx_dsp::settings::{AmpSettings, ChainParams};

/// Every knob at its loudest: levels, drives and mixes at their maximum.
fn loudest(character: Character) -> ChainParams {
    let mut chain = full();
    for kind in Kind::ALL {
        let mut pedal = *chain.pedal(kind).unwrap();
        for (i, knob) in kind.knobs().iter().enumerate() {
            pedal = pedal.with(i, knob.max);
        }
        chain = chain.with_pedal(pedal);
    }
    let mut amp = AmpSettings {
        character,
        ..AmpSettings::default()
    };
    for i in 0..AMP_KNOBS.len() {
        amp = amp.with_knob(i, 10.0);
    }
    chain.with_amp(amp)
}

#[test]
fn every_knob_at_its_loudest_never_exceeds_the_ceiling() {
    let params = Params::defaults();
    let mut noise = Noise::new(11);
    for character in Character::ALL {
        let mut chain = Chain::new(RATE, BLOCK, &params);
        chain.apply(Some(&loudest(character)));
        let mut stage = OutputStage::new(RATE, &params, 1.0);
        stage.start();
        let ceiling = stage.ceiling();
        let mut block = vec![0f32; BLOCK];
        let mut largest = 0f64;
        for b in 0..3 * RATE as usize / BLOCK {
            for (i, sample) in block.iter_mut().enumerate() {
                let t = (b * BLOCK + i) as f64 / f64::from(RATE);
                *sample = (0.9 * (2.0 * std::f64::consts::PI * 110.0 * t).sin() + 0.1 * noise.next()) as f32;
            }
            chain.process(&mut block);
            stage.process(&mut block);
            largest = block.iter().fold(largest, |m, s| m.max(f64::from(s.abs())));
        }
        // the limiter is spec 022's, ported as it is (SC-003): it checks a peak against the gain before that sample's release step, so a
        // peak can pass the ceiling by a fraction of a decibel; spec 022's OutputStageTest holds the peaks at −1 dBFS within 0.1 dB
        let over_db = 20.0 * (largest / ceiling).log10();
        assert!(over_db <= 0.1, "{character:?}: {largest} is {over_db:.3} dB over the ceiling {ceiling}");
        assert!(largest <= 1.0, "{character:?}: over 0 dBFS");
        assert!(largest > ceiling * 0.9, "{character:?}: the limiter is reached ({largest})");
    }
}

#[test]
fn the_sound_fades_in_over_half_a_second() {
    let params = Params::defaults();
    let mut stage = OutputStage::new(RATE, &params, 0.7);
    stage.start();
    let fade = (params.get_key("guitarfx.output.fade-in").unwrap() * f64::from(RATE)) as usize;
    let mut out = Vec::new();
    let mut block = vec![0f32; BLOCK];
    while out.len() < fade + 2 * BLOCK {
        block.fill(0.1);
        stage.process(&mut block);
        out.extend_from_slice(&block);
    }
    assert!(out[0].abs() < 1e-6, "it starts from silence");
    assert!(out[fade / 2] > 0.02 && out[fade / 2] < 0.05, "half way: {}", out[fade / 2]);
    assert!(
        (f64::from(out[fade + BLOCK]) - 0.07).abs() < 1e-6,
        "the set level after 0.5 s: {}",
        out[fade + BLOCK]
    );
    assert!(stage.is_faded_in());
    for pair in out.windows(2) {
        assert!(pair[1] >= pair[0], "it only rises");
    }
}

#[test]
fn each_ready_made_sound_is_silent_with_no_input_and_quiet_with_a_quiet_one() {
    let params = Params::defaults();
    for (name, _) in ready_made::ALL {
        for level in [None, Some(-70.0)] {
            let mut chain = Chain::new(RATE, BLOCK, &params);
            chain.apply(Some(&ready_made::chain(name).unwrap()));
            let mut stage = OutputStage::new(RATE, &params, 0.7);
            stage.start();
            let mut noise = Noise::new(1);
            let mut block = vec![0f32; BLOCK];
            let (mut sum, mut counted) = (0.0, 0usize);
            for b in 0..2 * RATE as usize / BLOCK {
                for sample in block.iter_mut() {
                    *sample = level.map_or(0.0, |l| db(l) * 3f64.sqrt() * noise.next()) as f32;
                }
                chain.process(&mut block);
                stage.process(&mut block);
                if b >= RATE as usize / BLOCK {
                    sum += block.iter().map(|s| f64::from(*s) * f64::from(*s)).sum::<f64>();
                    counted += BLOCK;
                }
            }
            let level_db = 10.0 * (sum / counted as f64).max(1e-30).log10();
            assert!(level_db < -60.0, "{name} with {level:?} dBFS in: {level_db:.1} dBFS out");
        }
    }
}

/// A 2.3 kHz sine rising 6 dB per 100 ms from −50 dBFS, held at −3 dBFS, over a quiet noise (spec 022's FeedbackGuardTest).
fn howl(frames: usize, start: usize) -> Vec<f32> {
    let mut noise = Noise::new(3);
    (0..frames)
        .map(|i| {
            let mut x = db(-50.0) * 3f64.sqrt() * noise.next();
            if i >= start {
                let seconds = (i - start) as f64 / f64::from(RATE);
                x += db((-50.0 + 60.0 * seconds).min(-3.0)) * (2.0 * std::f64::consts::PI * 2300.0 * seconds).sin();
            }
            x as f32
        })
        .collect()
}

#[test]
fn a_howl_is_muted_within_half_a_second() {
    let params = Params::defaults();
    let start = RATE as usize / 2;
    let input = howl(4 * RATE as usize, start);
    let mut stage = OutputStage::new(RATE, &params, 1.0);
    stage.start();
    let mut guard = FeedbackGuard::new(RATE, BLOCK, &params);
    let mut out = Vec::new();
    let mut muted = false;
    for chunk in input.chunks(BLOCK) {
        let mut block = chunk.to_vec();
        stage.process(&mut block);
        if stage.is_faded_in() && !muted && guard.process(&block) {
            muted = true;
            stage.stop();
        }
        out.extend_from_slice(&block);
    }
    assert!(muted, "the howl is found");
    for (i, sample) in out.iter().enumerate().skip(start + RATE as usize / 2) {
        assert!(sample.abs() < 1e-4, "silent half a second after the howl began, at {i}: {sample}");
    }
}

#[test]
fn the_ready_made_sounds_on_a_long_note_never_trigger_the_guard() {
    let params = Params::defaults();
    for (name, _) in ready_made::ALL {
        for frequency in [196.0, 659.3] {
            let mut chain = Chain::new(RATE, BLOCK, &params);
            chain.apply(Some(&ready_made::chain(name).unwrap()));
            let mut stage = OutputStage::new(RATE, &params, 0.7);
            stage.start();
            let mut guard = FeedbackGuard::new(RATE, BLOCK, &params);
            let mut block = vec![0f32; BLOCK];
            let mut found = false;
            for b in 0..6 * RATE as usize / BLOCK {
                for (i, sample) in block.iter_mut().enumerate() {
                    let seconds = (b * BLOCK + i) as f64 / f64::from(RATE);
                    let value: f64 = (1..=10)
                        .map(|k| (2.0 * std::f64::consts::PI * frequency * k as f64 * seconds).sin() / k as f64)
                        .sum();
                    let envelope = if seconds < 4.0 {
                        db(-3.0 - 6.0 * seconds) * (seconds * 200.0).min(1.0)
                    } else {
                        0.0
                    };
                    *sample = (value / 3.0 * envelope) as f32;
                }
                chain.process(&mut block);
                stage.process(&mut block);
                if stage.is_faded_in() {
                    found |= guard.process(&block);
                }
            }
            assert!(!found, "{name} on a long note at {frequency} Hz");
        }
    }
}
