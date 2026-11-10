//! GuitarFX's texts (spec 027, T046, FR-018): the three language files have the same keys; French and Spanish differ from English except a
//! short listed set; no text holds a figure with a unit (the unit and the default are appended from the knob table or the registry when
//! shown); the language comes from the system's locale, English otherwise; every parameter and category of the *Advanced* window has its
//! texts.

use guitarfx_dsp::knobs::Kind;
use guitarfx_dsp::params::ENTRIES;
use guitarfx_settings::texts::{self, Texts};

/// The texts that are the same in every language: names, symbols and words French or Spanish guitarists use as they are.
const SAME: &[&str] = &[
    "app.title",
    "devices.refresh",
    "sound.more",
    "dialog.ok",
    "volume",
    "pedal.gate",
    "pedal.overdrive",
    "pedal.chorus",
    "knob.compressor.sustain",
    "knob.overdrive.drive",
    "knob.distortion.gain",
    "knob.amp.gain",
    "knob.amp.volume",
    "amp.character.crunch",
    "amp.character.highgain",
    "advanced.category.gate",
    "advanced.category.overdrive",
    "advanced.category.chorus",
    "guitarfx.reverb.all-pass.name",
];

fn languages() -> Vec<Texts> {
    Texts::languages()
}

#[test]
fn there_are_three_languages_english_first() {
    let codes: Vec<String> = languages().iter().map(|t| t.language().to_string()).collect();
    assert_eq!(codes, ["en", "fr", "es"]);
}

#[test]
fn every_key_is_in_the_three_files_and_no_other() {
    let english: Vec<&str> = Texts::keys().collect();
    for texts in languages() {
        let mut keys: Vec<&str> = texts.own_keys();
        keys.sort_unstable();
        let mut expected = english.clone();
        expected.sort_unstable();
        assert_eq!(keys, expected, "{}", texts.language());
        for key in &english {
            assert!(!texts.get(key).trim().is_empty(), "{}: {key} is empty", texts.language());
        }
    }
}

#[test]
fn french_and_spanish_differ_from_english_except_the_listed_set() {
    let english = Texts::english();
    for texts in languages().iter().skip(1) {
        for key in Texts::keys() {
            let same = texts.get(key) == english.get(key);
            if same && !SAME.contains(&key) {
                panic!("{}: {key} is still English: {}", texts.language(), texts.get(key));
            }
        }
    }
}

#[test]
fn no_text_holds_a_figure_with_a_unit() {
    let units = [
        "%",
        "ms",
        "dBFS",
        "dB",
        "Hz",
        "kHz",
        "s",
        "sec",
        "seconds",
        "secondes",
        "segundos",
        "characters",
        "caractères",
        "caracteres",
        "px",
        "frames",
        "blocks",
        "steps",
    ];
    for texts in languages() {
        for key in Texts::keys() {
            let text = texts.get(key);
            let words: Vec<&str> = text.split(|c: char| c.is_whitespace() || c == '(' || c == ')').collect();
            for (i, word) in words.iter().enumerate() {
                let digits = word.trim_end_matches(|c: char| !c.is_ascii_digit());
                if digits.is_empty() || !digits.chars().all(|c| c.is_ascii_digit() || c == '.' || c == ',') {
                    continue;
                }
                let rest = &word[digits.len()..];
                let next = words.get(i + 1).copied().unwrap_or("");
                let unit = |w: &str| units.iter().any(|u| w.trim_end_matches(['.', ',', ';', ':']) == *u);
                assert!(
                    !unit(rest) && !unit(next),
                    "{}: {key} holds a figure with its unit: {text}",
                    texts.language()
                );
            }
        }
    }
}

#[test]
fn the_language_comes_from_the_locale_and_is_english_otherwise() {
    assert_eq!(texts::language_of("fr-FR"), "fr");
    assert_eq!(texts::language_of("fr"), "fr");
    assert_eq!(texts::language_of("FR_ca"), "fr");
    assert_eq!(texts::language_of("es_MX"), "es");
    assert_eq!(texts::language_of("de-DE"), "en");
    assert_eq!(texts::language_of("en-GB"), "en");
    assert_eq!(texts::language_of(""), "en");
    assert_eq!(Texts::for_language("es").language(), "es");
    assert_eq!(Texts::for_language("it").language(), "en");
    let system = Texts::system();
    assert!(["en", "fr", "es"].contains(&system.language()));
}

#[test]
fn every_parameter_and_category_of_the_advanced_window_has_its_texts() {
    let english = Texts::english();
    for entry in ENTRIES {
        for key in [entry.name_key(), entry.tooltip_key()] {
            assert_ne!(english.get(&key), key, "{key} has no text");
        }
        let category = format!("advanced.category.{}", entry.category);
        assert_ne!(english.get(&category), category, "{category} has no text");
    }
}

#[test]
fn every_knob_has_a_name_and_a_tooltip() {
    let english = Texts::english();
    for kind in Kind::ALL {
        let pedal = format!("pedal.{}", kind.text_name());
        assert_ne!(english.get(&pedal), pedal);
        for knob in kind.knobs() {
            let name = format!("knob.{}.{}", kind.text_name(), knob.key);
            assert_ne!(english.get(&name), name);
            let tip = format!("{name}.tip");
            assert_ne!(english.get(&tip), tip);
        }
    }
}
