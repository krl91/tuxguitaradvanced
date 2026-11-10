//! The message line, and the dialogs over the window (contracts/application.md "Messages").

use crate::window::{Action, Dialog};
use egui::{Context, Id, Ui};
use guitarfx_settings::texts::Texts;

/// A dialog's width; its text wraps within it.
const DIALOG_WIDTH: f32 = 420.0;

/// The latest message, wrapped to the window's width.
pub fn line(ui: &mut Ui, message: Option<&str>) {
    ui.add(egui::Label::new(message.unwrap_or(" ")).wrap());
}

pub fn dialog(ctx: &Context, dialog: &Dialog, texts: &Texts, actions: &mut Vec<Action>) {
    egui::Modal::new(Id::new("dialog")).show(ctx, |ui| {
        ui.set_max_width(DIALOG_WIDTH);
        let text = match dialog {
            Dialog::BuiltInMicrophone => texts.get("message.builtin").to_string(),
            Dialog::NewerSettings => texts.get("message.settings.newer").to_string(),
            Dialog::Message(text) => text.clone(),
        };
        ui.add(egui::Label::new(text).wrap());
        ui.separator();
        ui.horizontal_wrapped(|ui| match dialog {
            Dialog::BuiltInMicrophone => {
                if ui.button(texts.get("message.builtin.on")).clicked() {
                    actions.push(Action::TurnOnAnyway);
                }
                if ui.button(texts.get("message.builtin.cancel")).clicked() {
                    actions.push(Action::CloseDialog);
                }
            }
            Dialog::NewerSettings => {
                if ui.button(texts.get("message.settings.newer.keep")).clicked() {
                    actions.push(Action::KeepNewerSettings);
                }
                if ui.button(texts.get("message.settings.newer.replace")).clicked() {
                    actions.push(Action::ReplaceNewerSettings);
                }
            }
            Dialog::Message(_) => {
                if ui.button(texts.get("dialog.ok")).clicked() {
                    actions.push(Action::CloseDialog);
                }
            }
        });
    });
}
