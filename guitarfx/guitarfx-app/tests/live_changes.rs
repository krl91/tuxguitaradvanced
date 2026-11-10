//! A change made while playing, through the engine on the fake device (spec 027, T036, FR-006, SC-004): it is heard from the next block, the
//! output reaches the new setting's within 0.1 s, and no step is larger than T022's limit (1.5 times the largest step with the old setting
//! alone or the new one alone).

use guitarfx_app::engine::{self, Clock, Config};
use guitarfx_dsp::knobs::{index, Character, Kind};
use guitarfx_dsp::params::Params;
use guitarfx_dsp::ready_made;
use guitarfx_dsp::settings::{ChainParams, PedalSettings};

struct Still;

impl Clock for Still {
    fn nanos(&self) -> u64 {
        0
    }
}

const RATE: f32 = 48000.0;
const BLOCK: usize = 128;
const BLOCKS: usize = 3 * RATE as usize / (2 * BLOCK);
/// After the half-second fade in.
const CHANGE: usize = BLOCKS / 2;
/// The sine's level: low enough that the output stage's limiter never acts, since its clamping of a peak and its slow release are not the
/// change's doing (the limiter is checked by T021); asserted below.
const LEVEL: f64 = 0.02;

/// The left channel of the run: a 220 Hz sine, the chain `first`, and `second` posted before block CHANGE.
fn run(first: &ChainParams, second: Option<&ChainParams>) -> Vec<f32> {
    let config = Config {
        input_rate: RATE,
        output_rate: RATE,
        input_channels: 1,
        channel: 0,
        output_channels: 2,
        resample: false,
    };
    let (mut input, mut output, control) = engine::build(&config, &Params::defaults(), *first, 1.0, Still);
    let mut out = vec![0f32; 2 * BLOCK];
    let mut left = Vec::with_capacity(BLOCKS * BLOCK);
    for b in 0..BLOCKS {
        if b == CHANGE {
            if let Some(second) = second {
                control.post_chain(*second);
            }
        }
        let block: Vec<f32> = (0..BLOCK)
            .map(|i| (LEVEL * (2.0 * std::f64::consts::PI * 220.0 * (b * BLOCK + i) as f64 / f64::from(RATE)).sin()) as f32)
            .collect();
        input.on_input(&block, |s| s);
        output.on_output(&mut out, |s| s);
        left.extend(out.chunks(2).map(|f| f[0]));
    }
    left
}

fn largest_step(samples: &[f32]) -> f64 {
    samples[CHANGE * BLOCK / 2..]
        .windows(2)
        .map(|w| f64::from((w[1] - w[0]).abs()))
        .fold(0.0, f64::max)
}

fn check(what: &str, first: &ChainParams, second: &ChainParams, failures: &mut Vec<String>) {
    let old = run(first, None);
    let new = run(second, None);
    let changed = run(first, Some(second));
    let at = CHANGE * BLOCK;
    if changed[at..at + BLOCK] == old[at..at + BLOCK] {
        failures.push(format!("{what}: not heard in the next block"));
    }
    let settled = at + RATE as usize / 10;
    let gap = changed[settled..]
        .iter()
        .zip(&new[settled..])
        .map(|(a, b)| f64::from((a - b).abs()))
        .fold(0.0, f64::max);
    // −60 dBFS: the old setting's traces are gone
    if gap > 0.001 {
        failures.push(format!("{what}: still {gap} from the new setting's output 0.1 s later"));
    }
    let loudest = [&old, &new, &changed].iter().flat_map(|r| r.iter()).fold(0f32, |m, s| m.max(s.abs()));
    if loudest >= 0.89 {
        failures.push(format!("{what}: {loudest} reaches the limiter; lower LEVEL"));
    }
    let reference = largest_step(&old).max(largest_step(&new));
    let step = largest_step(&changed);
    #[allow(clippy::neg_cmp_op_on_partial_ord)]
    if !(step < 1.5 * reference) {
        failures.push(format!("{what}: a step of {step} against {reference}"));
    }
}

fn crunch() -> ChainParams {
    ready_made::chain("Crunch").unwrap()
}

#[test]
fn knobs_turned_while_playing_are_heard_at_once_and_smoothly() {
    let base = crunch();
    let with_drive = |drive: f64| {
        let mut before: Vec<PedalSettings> = base.before_pedals().copied().collect();
        before.push(PedalSettings::defaults(Kind::Overdrive).with(index::overdrive::DRIVE, drive));
        ChainParams::new(&before, base.amp, &base.after_pedals().copied().collect::<Vec<_>>()).unwrap()
    };
    let mut failures = Vec::new();
    check("overdrive drive 0 → 10", &with_drive(0.0), &with_drive(10.0), &mut failures);
    check("overdrive drive 10 → 2", &with_drive(10.0), &with_drive(2.0), &mut failures);
    for (index, _) in guitarfx_dsp::knobs::AMP_KNOBS.iter().enumerate() {
        let low = base.with_amp(base.amp.with_knob(index, 0.0));
        let high = base.with_amp(base.amp.with_knob(index, 10.0));
        check(&format!("amp knob {index} 0 → 10"), &low, &high, &mut failures);
    }
    let mut high_gain = base.amp;
    high_gain.character = Character::HighGain;
    check("amp crunch → high gain", &base, &base.with_amp(high_gain), &mut failures);
    assert!(failures.is_empty(), "{}", failures.join("\n"));
}
