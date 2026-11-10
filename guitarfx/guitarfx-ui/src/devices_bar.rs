//! The devices bar (FR-003): the input, its channel, the output, and ⟳. A remembered device that is not connected is shown as missing, never
//! replaced by another.

use crate::window::{Action, Model};
use egui::Ui;
use guitarfx_settings::texts::Texts;

/// A device list's width; a longer name is cut with "…" (its full name is in the list).
const DEVICE_WIDTH: f32 = 250.0;
const CHANNEL_WIDTH: f32 = 50.0;

fn selected(name: &Option<String>, present: bool, texts: &Texts) -> String {
    match name {
        None => texts.get("devices.choose").to_string(),
        Some(name) if present => name.clone(),
        Some(name) => texts.format("devices.missing", &[("name", name)]),
    }
}

fn device_list(ui: &mut Ui, id: &str, current: &Option<String>, names: &[String], texts: &Texts) -> Option<String> {
    let present = current.as_ref().is_some_and(|c| names.contains(c));
    let mut chosen = None;
    egui::ComboBox::from_id_salt(id)
        .selected_text(selected(current, present, texts))
        .width(DEVICE_WIDTH)
        .truncate()
        .show_ui(ui, |ui| {
            for name in names {
                if ui.selectable_label(current.as_ref() == Some(name), name).clicked() {
                    chosen = Some(name.clone());
                }
            }
        });
    chosen
}

pub fn show(ui: &mut Ui, model: &Model, texts: &Texts, actions: &mut Vec<Action>) {
    ui.horizontal_wrapped(|ui| {
        ui.label(texts.get("devices.input")).on_hover_text(texts.get("devices.input.tip"));
        let inputs: Vec<String> = model.inputs.iter().map(|(n, _)| n.clone()).collect();
        if let Some(name) = device_list(ui, "input", &model.input, &inputs, texts) {
            actions.push(Action::Input(name));
        }
        ui.label(texts.get("devices.channel")).on_hover_text(texts.get("devices.channel.tip"));
        let channels = model
            .inputs
            .iter()
            .find(|(n, _)| Some(n) == model.input.as_ref())
            .map_or(1, |(_, c)| u32::from(*c).max(1));
        let current = model.channel.unwrap_or(1);
        egui::ComboBox::from_id_salt("channel")
            .selected_text(current.to_string())
            .width(CHANNEL_WIDTH)
            .show_ui(ui, |ui| {
                for channel in 1..=channels {
                    if ui.selectable_label(channel == current, channel.to_string()).clicked() {
                        actions.push(Action::Channel(channel));
                    }
                }
            });
        ui.label(texts.get("devices.output")).on_hover_text(texts.get("devices.output.tip"));
        if let Some(name) = device_list(ui, "output", &model.output, &model.outputs, texts) {
            actions.push(Action::Output(name));
        }
        if ui
            .button(texts.get("devices.refresh"))
            .on_hover_text(texts.get("devices.refresh.tip"))
            .clicked()
        {
            actions.push(Action::Refresh);
        }
    });
}
