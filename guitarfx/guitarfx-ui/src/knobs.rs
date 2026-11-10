//! A knob as a slider with its name, value and unit, and a tooltip saying what it changes, its range and its default (FR-018).

use egui::Ui;
use guitarfx_settings::texts::Texts;

/// The width of a pedal's name, so that the knobs of the rows line up.
pub const NAME_WIDTH: f32 = 120.0;
/// A knob's slider length.
const SLIDER_WIDTH: f32 = 90.0;
/// A range this wide or wider is shown in whole units (ms, %, dB); a narrower one with a decimal.
const WHOLE_RANGE: f64 = 100.0;

fn decimals(min: f64, max: f64) -> usize {
    if max - min >= WHOLE_RANGE {
        0
    } else {
        1
    }
}

/// A value's tooltip: what it changes, then its range and default with their unit, which no translated text holds (FR-018).
pub fn tip(texts: &Texts, what: &str, min: f64, max: f64, unit: &str, default: f64) -> String {
    let places = decimals(min, max);
    let unit_text = if unit.is_empty() { String::new() } else { format!(" {unit}") };
    texts.format(
        "knob.tip",
        &[
            ("what", what),
            ("min", &format!("{min:.places$}")),
            ("max", &format!("{max:.places$}")),
            ("unit", &unit_text),
            ("default", &format!("{default:.places$}")),
        ],
    )
}

/// The new value when the guitarist moved it.
#[allow(clippy::too_many_arguments)]
pub fn slider(ui: &mut Ui, label: &str, what: &str, value: f64, min: f64, max: f64, unit: &str, default: f64, texts: &Texts) -> Option<f64> {
    let places = decimals(min, max);
    let unit_text = if unit.is_empty() { String::new() } else { format!(" {unit}") };
    let tip = tip(texts, what, min, max, unit, default);
    ui.label(label).on_hover_text(&tip);
    let mut current = value;
    ui.spacing_mut().slider_width = SLIDER_WIDTH;
    let response = ui
        .add(egui::Slider::new(&mut current, min..=max).suffix(unit_text).fixed_decimals(places))
        .on_hover_text(&tip);
    (response.changed() && current != value).then_some(current)
}
