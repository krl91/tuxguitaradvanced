//! The Rust elements against the Java references (spec 027, T007, SC-003). `GuitarFxReferenceTest` (TuxGuitarAdvanced's audio engine) writes
//! every run's input and output into the folder named by `GUITARFX_REFERENCE_DIR`; this test runs the same Rust element on the same input
//! with the same knobs, in blocks of the same size, and checks that no sample differs by more than −90 dBFS.

use guitarfx_dsp::amp::Amp;
use guitarfx_dsp::biquad::Biquad;
use guitarfx_dsp::cabinet::Cabinet;
use guitarfx_dsp::chain::Chain;
use guitarfx_dsp::chain_text;
use guitarfx_dsp::feedback_guard::FeedbackGuard;
use guitarfx_dsp::knobs::{Character, Kind};
use guitarfx_dsp::output_stage::OutputStage;
use guitarfx_dsp::oversampler::Oversampler;
use guitarfx_dsp::params::Params;
use guitarfx_dsp::pedals::Pedal;
use guitarfx_dsp::settings::{AmpSettings, ChainParams, PedalSettings};
use std::collections::BTreeMap;
use std::path::{Path, PathBuf};

/// −90 dBFS.
fn limit() -> f64 {
    10f64.powf(-90.0 / 20.0)
}

struct Run {
    element: String,
    variant: String,
    rate: f32,
    knobs: String,
    change_at: usize,
    input: String,
    output: String,
}

fn folder() -> Option<PathBuf> {
    match std::env::var("GUITARFX_REFERENCE_DIR") {
        Ok(value) if !value.trim().is_empty() => {
            let path = PathBuf::from(value);
            assert!(
                path.join("index.properties").is_file(),
                "GUITARFX_REFERENCE_DIR is set but {} holds no index.properties",
                path.display()
            );
            Some(path)
        }
        _ => None,
    }
}

pub fn index(folder: &Path) -> BTreeMap<String, String> {
    let text = std::fs::read_to_string(folder.join("index.properties")).expect("the index");
    let mut index = BTreeMap::new();
    for line in text.lines() {
        if line.starts_with('#') || line.trim().is_empty() {
            continue;
        }
        let (key, value) = line.split_once('=').expect("key=value");
        index.insert(key.to_string(), value.to_string());
    }
    index
}

fn runs(index: &BTreeMap<String, String>) -> Vec<Run> {
    let mut runs = Vec::new();
    for (key, value) in index {
        let Some(id) = key.strip_prefix("run.").and_then(|rest| rest.strip_suffix(".element")) else {
            continue;
        };
        let field = |name: &str| {
            index
                .get(&format!("run.{id}.{name}"))
                .cloned()
                .unwrap_or_else(|| panic!("run.{id}.{name}"))
        };
        runs.push(Run {
            element: value.clone(),
            variant: field("variant"),
            rate: field("rate").parse().unwrap(),
            knobs: field("knobs"),
            change_at: field("change-at").parse().unwrap(),
            input: field("input"),
            output: field("output"),
        });
    }
    runs
}

fn read(path: &Path) -> Vec<f32> {
    let bytes = std::fs::read(path).unwrap_or_else(|e| panic!("{}: {e}", path.display()));
    bytes.chunks_exact(4).map(|b| f32::from_le_bytes([b[0], b[1], b[2], b[3]])).collect()
}

fn numbers(text: &str) -> Vec<f64> {
    text.split(',').map(|n| n.trim().parse().unwrap()).collect()
}

/// In blocks of `block`; `change` runs before the block that starts at `change_at`.
fn blocks(input: &[f32], block: usize, change_at: usize, mut process: impl FnMut(&mut [f32]), mut change: Option<impl FnMut()>) -> Vec<f32> {
    let mut out = input.to_vec();
    let mut offset = 0;
    while offset < out.len() {
        if offset == change_at {
            if let Some(change) = change.as_mut() {
                change();
            }
        }
        let n = block.min(out.len() - offset);
        process(&mut out[offset..offset + n]);
        offset += n;
    }
    out
}

fn pedal(kind: Kind, run: &Run, input: &[f32], block: usize, params: &Params) -> Vec<f32> {
    let (first, second) = match run.knobs.split_once('>') {
        Some((a, b)) => (a, Some(b)),
        None => (run.knobs.as_str(), None),
    };
    let first = PedalSettings::of(kind, true, &numbers(first));
    let second = second.map(|s| PedalSettings::of(kind, true, &numbers(s)));
    let pedal = &std::cell::RefCell::new(Pedal::new(kind, run.rate, block, params));
    pedal.borrow_mut().set(&first);
    blocks(
        input,
        block,
        run.change_at,
        |b| pedal.borrow_mut().process(b),
        second.map(|s| move || pedal.borrow_mut().set(&s)),
    )
}

fn amp_settings(text: &str) -> AmpSettings {
    let (character, rest) = text.split_once(',').unwrap();
    let k = numbers(rest);
    AmpSettings::new(Character::from_text(character).unwrap(), k[0], k[1], k[2], k[3], k[4], true)
}

fn amp(run: &Run, input: &[f32], block: usize, params: &Params) -> Vec<f32> {
    let (first, second) = match run.knobs.split_once('>') {
        Some((a, b)) => (amp_settings(a), Some(amp_settings(b))),
        None => (amp_settings(&run.knobs), None),
    };
    let amp = &std::cell::RefCell::new(Amp::new(run.rate, block, params));
    amp.borrow_mut().set(&first);
    blocks(
        input,
        block,
        run.change_at,
        |b| amp.borrow_mut().process(b),
        second.map(|s| move || amp.borrow_mut().set(&s)),
    )
}

fn cabinet(run: &Run, input: &[f32], block: usize, params: &Params) -> Vec<f32> {
    let (first, second) = run.knobs.split_once('>').unwrap();
    let (first, second): (bool, bool) = (first.parse().unwrap(), second.parse().unwrap());
    let cabinet = std::cell::RefCell::new(Cabinet::new(run.rate, block, params));
    cabinet.borrow_mut().set_enabled(first);
    blocks(
        input,
        block,
        run.change_at,
        |b| cabinet.borrow_mut().process(b),
        (first != second).then_some(|| cabinet.borrow_mut().set_enabled(second)),
    )
}

fn biquad(run: &Run, input: &[f32], block: usize, params: &Params) -> Vec<f32> {
    let step = params.get_key("guitarfx.smooth.glide-step").unwrap() as usize;
    let filter = std::cell::RefCell::new(Biquad::new(run.rate, block, step));
    let mut glide = false;
    {
        let mut f = filter.borrow_mut();
        match run.variant.as_str() {
            "low-pass" => f.set_low_pass(1000.0, 0.5f64.sqrt()),
            "high-pass" => f.set_high_pass(100.0, 0.5f64.sqrt()),
            "peak" => f.set_peak(700.0, 0.8, 6.0),
            "low-shelf" => f.set_low_shelf(200.0, 6.0),
            "high-shelf" => f.set_high_shelf(1800.0, -6.0),
            "glide" => {
                f.set_peak(500.0, 1.0, -6.0);
                glide = true;
            }
            other => panic!("biquad variant {other}"),
        }
    }
    blocks(
        input,
        block,
        run.change_at,
        |b| filter.borrow_mut().process(b),
        glide.then_some(|| filter.borrow_mut().set_peak(3000.0, 2.0, 9.0)),
    )
}

fn oversampler(run: &Run, input: &[f32], block: usize) -> Vec<f32> {
    let factor: usize = run.knobs.parse().unwrap();
    let mut oversampler = Oversampler::new(factor, block);
    let mut high = vec![0f32; block * factor];
    let mut out = vec![0f32; block];
    blocks(
        input,
        block,
        usize::MAX,
        |b| {
            let count = oversampler.up(b, &mut high);
            oversampler.down(&high[..count], &mut out[..b.len()]);
            b.copy_from_slice(&out[..b.len()]);
        },
        None::<fn()>,
    )
}

fn output_stage(run: &Run, input: &[f32], block: usize, params: &Params) -> Vec<f32> {
    let stage = std::cell::RefCell::new(OutputStage::new(run.rate, params, 0.7));
    if run.variant != "default" && run.variant != "stopped" {
        stage.borrow_mut().set_volume(run.knobs.parse().unwrap());
    }
    stage.borrow_mut().start();
    let stops = run.variant == "stopped";
    blocks(
        input,
        block,
        run.change_at,
        |b| stage.borrow_mut().process(b),
        stops.then_some(|| stage.borrow_mut().stop()),
    )
}

fn chain(run: &Run, input: &[f32], block: usize, params: &Params) -> Vec<f32> {
    let (first, second) = match run.knobs.split_once('>') {
        Some((a, b)) => (a, Some(b)),
        None => (run.knobs.as_str(), None),
    };
    let parse = |text: &str| -> ChainParams { chain_text::parse(text).unwrap_or_else(|| panic!("chain text {text}")) };
    let first = parse(first);
    let second = second.map(parse);
    let chain = &std::cell::RefCell::new(Chain::new(run.rate, block, params));
    chain.borrow_mut().apply(Some(&first));
    blocks(
        input,
        block,
        run.change_at,
        |b| chain.borrow_mut().process(b),
        second.map(|s| move || chain.borrow_mut().apply(Some(&s))),
    )
}

fn guard(run: &Run, input: &[f32], block: usize, params: &Params) -> Vec<f32> {
    let mut guard = FeedbackGuard::new(run.rate, block, params);
    input.chunks(block).map(|b| if guard.process(b) { 1.0 } else { 0.0 }).collect()
}

#[test]
fn every_element_gives_the_java_output_within_minus_90_dbfs() {
    let Some(folder) = folder() else {
        println!("skipped: no Java references");
        return;
    };
    let index = index(&folder);
    let block: usize = index["block"].parse().unwrap();
    let params = Params::defaults();
    let runs = runs(&index);
    assert_eq!(runs.len(), index["runs"].parse::<usize>().unwrap(), "every run of the index is read");
    let mut worst: BTreeMap<String, f64> = BTreeMap::new();
    let mut failures = Vec::new();
    for run in &runs {
        let input = read(&folder.join(&run.input));
        let expected = read(&folder.join(&run.output));
        let actual = if let Some(kind) = Kind::from_text(&run.element) {
            pedal(kind, run, &input, block, &params)
        } else if run.element.starts_with("amp-") {
            amp(run, &input, block, &params)
        } else {
            match run.element.as_str() {
                "cabinet" => cabinet(run, &input, block, &params),
                "biquad" => biquad(run, &input, block, &params),
                "oversampler" => oversampler(run, &input, block),
                "output-stage" => output_stage(run, &input, block, &params),
                "chain" => chain(run, &input, block, &params),
                "feedback-guard" => guard(run, &input, block, &params),
                other => panic!("an element the test does not know: {other}"),
            }
        };
        assert_eq!(actual.len(), expected.len(), "{} {} length", run.element, run.variant);
        let difference = actual
            .iter()
            .zip(&expected)
            .map(|(a, e)| (f64::from(*a) - f64::from(*e)).abs())
            .fold(0.0, f64::max);
        let entry = worst.entry(format!("{} @ {}", run.element, run.rate)).or_insert(0.0);
        *entry = entry.max(difference);
        if difference.is_nan() || difference >= limit() {
            failures.push(format!(
                "{} {} {} {}: {:.1} dB",
                run.element,
                run.variant,
                run.rate,
                run.output,
                20.0 * difference.log10()
            ));
        }
    }
    println!("largest difference per element and rate (dBFS; -inf is identical):");
    for (element, difference) in &worst {
        println!("  {element}: {:.1}", 20.0 * difference.log10());
    }
    assert!(
        failures.is_empty(),
        "{} of {} runs differ from Java by −90 dBFS or more:\n{}",
        failures.len(),
        runs.len(),
        failures.join("\n")
    );
}

#[test]
fn every_element_of_the_references_is_covered() {
    let Some(folder) = folder() else {
        println!("skipped: no Java references");
        return;
    };
    let index = index(&folder);
    let elements: std::collections::BTreeSet<String> = runs(&index).into_iter().map(|r| r.element).collect();
    for kind in Kind::ALL {
        assert!(elements.contains(kind.text_name()), "{}", kind.text_name());
    }
    for name in [
        "amp-clean",
        "amp-crunch",
        "amp-highgain",
        "cabinet",
        "biquad",
        "oversampler",
        "output-stage",
        "chain",
        "feedback-guard",
    ] {
        assert!(elements.contains(name), "{name}");
    }
}
