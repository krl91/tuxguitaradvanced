//! The *Advanced…* window (FR-025, contracts/application.md "Advanced…"): every internal value of the registry, by category in a
//! drop-down list, one row each with its name, slider and value, unit, "default X" and a tooltip; *Defaults* asks first; while the sound
//! is on, a line says the changes take effect the next time it is turned on. Names and tooltips come from the language files, the unit,
//! the range and the default from the registry.

use crate::window::Action;
use egui::{Align2, Context, RichText, Ui};
use guitarfx_dsp::params::{Entry, Params, ENTRIES};
use guitarfx_settings::texts::Texts;

/// The width of a row's name, and of its slider.
const NAME_WIDTH: f32 = 230.0;
const SLIDER_WIDTH: f32 = 200.0;
/// The most decimals a value shows.
const DECIMALS: usize = 4;
/// The units of values that are whole numbers.
const WHOLE_UNITS: &[&str] = &["frames", "blocks", "bins", "samples", "step"];
/// A slider whose maximum is this many times its minimum, or more, is logarithmic (the frequencies).
const LOGARITHMIC_SPAN: f64 = 100.0;

/// What the window shows.
#[derive(Clone, Debug, PartialEq)]
pub struct AdvancedModel {
    pub open: bool,
    /// The category shown.
    pub category: String,
    /// *Defaults* was pressed: the question is shown.
    pub confirm: bool,
    pub params: Params,
    pub sound_on: bool,
}

impl Default for AdvancedModel {
    fn default() -> Self {
        AdvancedModel {
            open: false,
            category: ENTRIES[0].category.to_string(),
            confirm: false,
            params: Params::defaults(),
            sound_on: false,
        }
    }
}

/// The categories in the registry's order.
pub fn categories() -> Vec<&'static str> {
    let mut categories: Vec<&'static str> = Vec::new();
    for entry in ENTRIES.iter() {
        if !categories.contains(&entry.category) {
            categories.push(entry.category);
        }
    }
    categories
}

/// The parameters of a category, in the registry's order.
pub fn rows(category: &str) -> Vec<&'static Entry> {
    ENTRIES.iter().filter(|e| e.category == category).collect()
}

pub fn name(entry: &Entry, texts: &Texts) -> String {
    texts.get(&entry.name_key()).to_string()
}

fn with_unit(value: f64, unit: &str) -> String {
    if unit.is_empty() {
        format!("{value}")
    } else {
        format!("{value} {unit}")
    }
}

/// "default X unit".
pub fn default_text(entry: &Entry, texts: &Texts) -> String {
    texts.format("advanced.default", &[("value", &with_unit(entry.default, entry.unit))])
}

/// What it changes, then its range and default with their unit.
pub fn tip(entry: &Entry, texts: &Texts) -> String {
    let unit = if entry.unit.is_empty() {
        String::new()
    } else {
        format!(" {}", entry.unit)
    };
    texts.format(
        "knob.tip",
        &[
            ("what", texts.get(&entry.tooltip_key())),
            ("min", &format!("{}", entry.min)),
            ("max", &format!("{}", entry.max)),
            ("unit", &unit),
            ("default", &format!("{}", entry.default)),
        ],
    )
}

/// The parameters with one value changed, kept within its bounds and the combined bounds (`Params::from_values`).
pub fn set(params: &Params, key: &str, value: f64) -> Params {
    let texts: Vec<(&str, String)> = ENTRIES
        .iter()
        .map(|e| {
            let current = if e.key == key { value } else { params.get(e.id) };
            (e.key, format!("{current}"))
        })
        .collect();
    Params::from_values(texts.iter().map(|(k, v)| (*k, v.as_str())))
}

fn row(ui: &mut Ui, entry: &'static Entry, params: &Params, texts: &Texts, actions: &mut Vec<Action>) {
    let tip = tip(entry, texts);
    ui.add_sized(
        [NAME_WIDTH, ui.spacing().interact_size.y],
        egui::Label::new(name(entry, texts)).truncate(),
    )
    .on_hover_text(&tip);
    let mut value = params.get(entry.id);
    ui.spacing_mut().slider_width = SLIDER_WIDTH;
    let mut slider = egui::Slider::new(&mut value, entry.min..=entry.max).max_decimals(DECIMALS);
    if WHOLE_UNITS.contains(&entry.unit) {
        slider = slider.integer();
    }
    if entry.min > 0.0 && entry.max >= LOGARITHMIC_SPAN * entry.min {
        slider = slider.logarithmic(true);
    }
    if ui.add(slider).on_hover_text(&tip).changed() {
        actions.push(Action::Param { key: entry.key, value });
    }
    ui.label(entry.unit);
    ui.label(RichText::new(default_text(entry, texts)).weak());
    ui.end_row();
}

pub fn show(ctx: &Context, model: &mut AdvancedModel, texts: &Texts, actions: &mut Vec<Action>) {
    if !model.open {
        return;
    }
    let mut open = true;
    egui::Window::new(texts.get("advanced.title"))
        .id(egui::Id::new("advanced"))
        .open(&mut open)
        .collapsible(false)
        .resizable(false)
        .anchor(Align2::CENTER_CENTER, [0.0, 0.0])
        .show(ctx, |ui| {
            ui.horizontal(|ui| {
                ui.label(texts.get("advanced.category")).on_hover_text(texts.get("advanced.category.tip"));
                egui::ComboBox::from_id_salt("advanced-category")
                    .selected_text(texts.get(&format!("advanced.category.{}", model.category)))
                    .show_ui(ui, |ui| {
                        for category in categories() {
                            let label = texts.get(&format!("advanced.category.{category}")).to_string();
                            ui.selectable_value(&mut model.category, category.to_string(), label);
                        }
                    })
                    .response
                    .on_hover_text(texts.get("advanced.category.tip"));
            });
            ui.separator();
            egui::Grid::new("advanced-rows").num_columns(4).striped(true).show(ui, |ui| {
                for entry in rows(&model.category) {
                    row(ui, entry, &model.params, texts, actions);
                }
            });
            ui.separator();
            if model.sound_on {
                ui.label(RichText::new(texts.get("advanced.takes-effect")).strong());
            }
            if model.confirm {
                ui.label(texts.get("advanced.defaults.confirm"));
                ui.horizontal(|ui| {
                    if ui.button(texts.get("advanced.defaults.yes")).clicked() {
                        model.confirm = false;
                        actions.push(Action::AdvancedDefaults);
                    }
                    if ui.button(texts.get("dialog.cancel")).clicked() {
                        model.confirm = false;
                    }
                });
            } else {
                ui.horizontal(|ui| {
                    if ui
                        .button(texts.get("advanced.defaults"))
                        .on_hover_text(texts.get("advanced.defaults.tip"))
                        .clicked()
                    {
                        model.confirm = true;
                    }
                    if ui
                        .button(texts.get("advanced.close"))
                        .on_hover_text(texts.get("advanced.close.tip"))
                        .clicked()
                    {
                        model.open = false;
                        actions.push(Action::CloseAdvanced);
                    }
                });
            }
        });
    if !open {
        model.open = false;
        actions.push(Action::CloseAdvanced);
    }
}
