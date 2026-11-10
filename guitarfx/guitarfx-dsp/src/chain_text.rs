//! The chain's compact text, read and written exactly as spec 022's `ChainSettings.parse` and `toText` (contracts/sound-file.md), for
//! example `gate:on,-60,150|amp:crunch,5,5,5,5,6|cab:on|echo:on,380,3,5,30`.

use crate::knobs::{Character, Kind, AMP_KNOBS, KINDS};
use crate::settings::{AmpSettings, ChainParams, PedalSettings};
use std::fmt::Write;

/// A number as `BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()` writes it: the shortest decimal that reads back the same,
/// with no exponent and no trailing zero.
pub fn number(value: f64) -> String {
    if value == 0.0 {
        // BigDecimal has no negative zero
        return "0".to_string();
    }
    format!("{value}")
}

fn append_pedal(text: &mut String, pedal: &PedalSettings) {
    if !text.is_empty() {
        text.push('|');
    }
    text.push_str(pedal.kind.text_name());
    text.push_str(if pedal.enabled { ":on" } else { ":off" });
    for value in pedal.values() {
        let _ = write!(text, ",{}", number(*value));
    }
}

/// The chain's text.
pub fn format(chain: &ChainParams) -> String {
    let mut text = String::new();
    for pedal in chain.before_pedals() {
        append_pedal(&mut text, pedal);
    }
    if !text.is_empty() {
        text.push('|');
    }
    text.push_str("amp:");
    text.push_str(chain.amp.character.text_name());
    for value in chain.amp.knobs() {
        let _ = write!(text, ",{}", number(value));
    }
    text.push_str("|cab:");
    text.push_str(if chain.amp.cabinet { "on" } else { "off" });
    for pedal in chain.after_pedals() {
        append_pedal(&mut text, pedal);
    }
    text
}

/// Java's `String.trim`: every character up to the space.
fn trim(text: &str) -> &str {
    text.trim_matches(|c: char| c <= ' ')
}

fn value(field: &str) -> Option<f64> {
    trim(field).parse().ok()
}

fn on_off(field: &str) -> Option<bool> {
    match trim(field) {
        "on" => Some(true),
        "off" => Some(false),
        _ => None,
    }
}

/// The chain of that text, or none when the text is malformed (the caller then falls back to a ready-made sound). Values out of their
/// range are clamped.
pub fn parse(text: &str) -> Option<ChainParams> {
    let text = trim(text);
    if text.is_empty() {
        return None;
    }
    let mut before: [Option<PedalSettings>; KINDS] = [None; KINDS];
    let mut before_count = 0;
    let mut after: [Option<PedalSettings>; KINDS] = [None; KINDS];
    let mut after_count = 0;
    let mut amp: Option<AmpSettings> = None;
    let mut cabinet: Option<bool> = None;
    for element in text.split('|') {
        let colon = element.find(':').filter(|c| *c > 0)?;
        let name = trim(&element[..colon]);
        let fields: Vec<&str> = element[colon + 1..].split(',').collect();
        if name == "amp" {
            if amp.is_some() || fields.len() != AMP_KNOBS.len() + 1 {
                return None;
            }
            let character = Character::from_text(trim(fields[0]))?;
            let mut knobs = [0.0; AMP_KNOBS.len()];
            for (knob, field) in knobs.iter_mut().zip(&fields[1..]) {
                *knob = value(field)?;
            }
            let [gain, bass, middle, treble, volume] = knobs;
            amp = Some(AmpSettings::new(character, gain, bass, middle, treble, volume, true));
        } else if name == "cab" {
            if amp.is_none() || cabinet.is_some() || fields.len() != 1 {
                return None;
            }
            cabinet = Some(on_off(fields[0])?);
        } else {
            let kind = Kind::from_text(name)?;
            if fields.len() != kind.knobs().len() + 1 {
                return None;
            }
            let enabled = on_off(fields[0])?;
            let mut values = [0.0; crate::knobs::MAX_KNOBS];
            for (slot, field) in values.iter_mut().zip(&fields[1..]) {
                *slot = value(field)?;
            }
            let pedal = PedalSettings::of(kind, enabled, &values[..kind.knobs().len()]);
            if amp.is_none() {
                if before_count == KINDS {
                    return None;
                }
                before[before_count] = Some(pedal);
                before_count += 1;
            } else {
                if cabinet.is_none() || after_count == KINDS {
                    return None;
                }
                after[after_count] = Some(pedal);
                after_count += 1;
            }
        }
    }
    let (Some(amp), Some(cabinet)) = (amp, cabinet) else {
        return None;
    };
    let before: Vec<PedalSettings> = before.iter().flatten().copied().collect();
    let after: Vec<PedalSettings> = after.iter().flatten().copied().collect();
    ChainParams::new(&before, AmpSettings { cabinet, ..amp }, &after)
}
