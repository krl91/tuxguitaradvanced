//! An advanced value takes effect the next time the sound is turned on, and not before (spec 027, T049, FR-025): for one parameter of each
//! category, a value other than the default is written in `settings.properties` and read back; an engine started after it plays
//! differently from one started with the defaults, and an engine already started before it plays exactly as before.
//!
//! The scenario plays every element: a 44.1 kHz input resampled to 48 kHz, the chain of the category's character with every pedal on, a
//! noisy guitar then a loud steady tone (the guard's case), a pedal switched and the pedals reordered halfway (the smooth changes), and
//! default-sized blocks taking a quarter of their time (the overload budget). What is compared is every output sample and the engine's state, lightening,
//! clipping count and block size.

use guitarfx_app::engine::{self, Clock, Config};
use guitarfx_dsp::knobs::index::compressor;
use guitarfx_dsp::knobs::{Character, Kind, Section};
use guitarfx_dsp::params::{Id, Params, ENTRIES};
use guitarfx_dsp::settings::{AmpSettings, ChainParams, PedalSettings};
use guitarfx_settings::settings::Settings;
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::Arc;

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

const INPUT_RATE: f32 = 44100.0;
const OUTPUT_RATE: f32 = 48000.0;
const CALLBACK: usize = 256;
const SECONDS: usize = 4;

/// The parameter changed in each category, and its new value: the end of its range farther from the default.
fn changed(category: &str) -> (&'static str, f64) {
    let key = match category {
        "feedback" => "guitarfx.feedback.loud",
        "feedback-analysis" => "guitarfx.feedback-analysis.hop",
        "devices" => "guitarfx.devices.block",
        "overload" => "guitarfx.overload.budget",
        _ => ENTRIES.iter().find(|e| e.category == category).unwrap().key,
    };
    let entry = ENTRIES.iter().find(|e| e.key == key).unwrap();
    // a smaller budget makes the quarter-time blocks slow
    let value = if key == "guitarfx.overload.budget" || entry.max - entry.default < entry.default - entry.min {
        entry.min
    } else {
        entry.max
    };
    (entry.key, value)
}

/// The values the comparison starts from: the defaults, except for the guard's analysis, which only matters once a howl is caught: there
/// the guard is made to catch the loud tone (`feedback.loud` lowered), and the analysis value moves the moment it does.
fn base(category: &str) -> Params {
    match category {
        "feedback-analysis" => Params::from_values([("guitarfx.feedback.loud", "-40")]),
        _ => Params::defaults(),
    }
}

fn chain(character: Character, swapped: bool) -> ChainParams {
    let of = |section: Section| -> Vec<PedalSettings> {
        let mut pedals: Vec<PedalSettings> = Kind::ALL
            .iter()
            .filter(|k| k.section() == section)
            .map(|k| match k {
                // a level away from unity, so that the level's step is heard
                Kind::Compressor => PedalSettings::defaults(*k).with(compressor::LEVEL, 7.0),
                _ => PedalSettings::defaults(*k),
            })
            .collect();
        if swapped {
            pedals.reverse();
            pedals[0] = pedals[0].with_enabled(false);
        }
        pedals
    };
    ChainParams::new(
        &of(Section::Before),
        AmpSettings::new(character, 6.0, 5.0, 5.0, 6.0, 6.0, true),
        &of(Section::After),
    )
    .unwrap()
}

fn character(category: &str) -> Character {
    match category {
        "amp-crunch" => Character::Crunch,
        "amp-high-gain" => Character::HighGain,
        _ => Character::Clean,
    }
}

/// Every output sample's bits, then the engine's state, lightening, clipping and block size.
fn play(params: &Params, character: Character) -> Vec<u64> {
    let config = Config {
        input_rate: INPUT_RATE,
        input_channels: 1,
        channel: 0,
        output_rate: OUTPUT_RATE,
        output_channels: 2,
        resample: true,
    };
    let clock = FakeClock::default();
    let (mut input, mut output, control) = engine::build(&config, params, chain(character, false), 1.0, clock.clone());
    // the clock is read as each block starts and ends: every default-sized block takes a quarter of its time
    let block_nanos = Params::defaults().get(Id::DevicesBlock) / f64::from(OUTPUT_RATE) * 1e9;
    clock.step.store((block_nanos / 4.0) as u64, Ordering::Relaxed);
    let mut seed = 12345u64;
    let mut phase = 0f64;
    let mut out = vec![0f32; 2 * CALLBACK];
    let mut print = Vec::new();
    let callbacks = SECONDS * OUTPUT_RATE as usize / CALLBACK;
    for n in 0..callbacks {
        let frames = (CALLBACK as f64 * f64::from(INPUT_RATE) / f64::from(OUTPUT_RATE)).round() as usize;
        let block: Vec<f32> = (0..frames)
            .map(|_| {
                if n < callbacks / 4 {
                    seed ^= seed << 13;
                    seed ^= seed >> 7;
                    seed ^= seed << 17;
                    ((seed >> 40) as f64 / (1u64 << 24) as f64 - 0.5) as f32 * 0.6
                } else {
                    phase += 2.0 * std::f64::consts::PI * 1000.0 / f64::from(INPUT_RATE);
                    (0.95 * phase.sin()) as f32
                }
            })
            .collect();
        if n == callbacks / 2 {
            control.post_chain(chain(character, true));
        }
        input.on_input(&block, |s| s);
        output.on_output(&mut out, |s| s);
        print.extend(out.iter().map(|s| u64::from(s.to_bits())));
        print.push(control.state() as u64);
    }
    print.extend([control.lightening() as u64, control.clipped_blocks(), control.block() as u64]);
    print
}

fn folder(name: &str) -> std::path::PathBuf {
    let path = std::env::temp_dir().join(format!("guitarfx-takes-effect-{}-{name}", std::process::id()));
    let _ = std::fs::remove_dir_all(&path);
    std::fs::create_dir_all(&path).unwrap();
    path
}

#[test]
fn each_category_takes_effect_the_next_time_the_sound_is_turned_on() {
    let mut categories: Vec<&str> = Vec::new();
    for entry in ENTRIES {
        if !categories.contains(&entry.category) {
            categories.push(entry.category);
        }
    }
    let mut unchanged = Vec::new();
    for category in categories {
        let (key, value) = changed(category);
        let character = character(category);
        let defaults = base(category);
        let before = play(&defaults, character);
        // the value is written as the window writes it, and read back as the next start reads it
        let folder = folder(category);
        let mut values: Vec<(&str, String)> = ENTRIES.iter().map(|e| (e.key, defaults.get(e.id).to_string())).collect();
        values.retain(|(k, _)| *k != key);
        values.push((key, value.to_string()));
        let settings = Settings {
            params: Params::from_values(values.iter().map(|(k, v)| (*k, v.as_str()))),
            ..Settings::default()
        };
        settings.save(&folder).unwrap();
        let read = Settings::load(&folder).settings.params;
        assert_eq!(read.get_key(key), Some(value), "{key} is read back");
        // an engine already playing keeps the parameters it was started with
        assert_eq!(
            play(&defaults, character),
            before,
            "{key}: nothing changes before the sound is turned on again"
        );
        if play(&read, character) == before {
            unchanged.push(format!("{key} = {value}"));
        }
    }
    assert!(unchanged.is_empty(), "no effect once turned on again: {unchanged:?}");
}
