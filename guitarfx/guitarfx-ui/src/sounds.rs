//! The sound list (contracts/application.md "The sound list", FR-007, FR-008, FR-023): the sounds, *Save…* with its name prompt, and the ⋯
//! menu with *Rename…*, *Delete*, *Restore*, *Export…* and *Import…*. The view says what the guitarist chose; the application keeps the
//! sounds, checks the names and opens the file dialogs.

use crate::window::Action;
use egui::{Context, Id, Ui};
use guitarfx_settings::texts::Texts;

/// The sound list's width; a longer name is cut with "…".
const LIST_WIDTH: f32 = 280.0;
/// The name prompt's width, and its field's: room for 40 wide letters.
const DIALOG_WIDTH: f32 = 560.0;
const NAME_WIDTH: f32 = 520.0;

/// A sound as the list shows it.
#[derive(Clone, Debug, PartialEq)]
pub struct SoundItem {
    pub name: String,
    pub ready_made: bool,
    /// A ready-made sound whose chain is not the shipped one.
    pub changed: bool,
}

/// A dialog of the sound list.
#[derive(Clone, Debug, PartialEq)]
pub enum SoundDialog {
    /// *Save…*: the name, and what is wrong with it once tried.
    Save { name: String, problem: Option<String> },
    /// *Rename…* of the own sound `from`.
    Rename { from: String, name: String, problem: Option<String> },
    /// An imported sound has the name of an own sound: replace it, or keep both.
    Clash { name: String },
}

/// What the sound list shows.
#[derive(Clone, Debug, Default, PartialEq)]
pub struct SoundsModel {
    /// Ready-made sounds first, then own sounds by name.
    pub sounds: Vec<SoundItem>,
    pub current: String,
    /// The chain in use differs from the current own sound's saved chain.
    pub unsaved: bool,
    pub dialog: Option<SoundDialog>,
}

/// Which ⋯ items apply to the current sound; *Export…* and *Import…* always do.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct Menu {
    pub rename: bool,
    pub delete: bool,
    pub restore: bool,
}

fn current(model: &SoundsModel) -> Option<&SoundItem> {
    model.sounds.iter().find(|s| s.name.eq_ignore_ascii_case(model.current.trim()))
}

/// The sound's name, with "(changed)" after a changed ready-made sound or the current own sound with unsaved changes.
pub fn label(item: &SoundItem, model: &SoundsModel, texts: &Texts) -> String {
    let is_current = current(model).is_some_and(|c| c.name == item.name);
    if item.changed || (is_current && !item.ready_made && model.unsaved) {
        format!("{} {}", item.name, texts.get("sound.changed"))
    } else {
        item.name.clone()
    }
}

pub fn menu(model: &SoundsModel) -> Menu {
    let sound = current(model);
    let own = sound.is_some_and(|s| !s.ready_made);
    Menu {
        rename: own,
        delete: own,
        restore: sound.is_some_and(|s| s.ready_made && s.changed),
    }
}

pub fn show(ui: &mut Ui, model: &mut SoundsModel, texts: &Texts, actions: &mut Vec<Action>) {
    ui.horizontal_wrapped(|ui| {
        ui.label(texts.get("sound.list")).on_hover_text(texts.get("sound.list.tip"));
        let selected = current(model).map(|s| label(s, model, texts)).unwrap_or_else(|| model.current.clone());
        egui::ComboBox::from_id_salt("sound")
            .selected_text(selected)
            .width(LIST_WIDTH)
            .truncate()
            .show_ui(ui, |ui| {
                for item in &model.sounds {
                    let is_current = item.name.eq_ignore_ascii_case(model.current.trim());
                    if ui.selectable_label(is_current, label(item, model, texts)).clicked() {
                        actions.push(Action::PickSound(item.name.clone()));
                    }
                }
            })
            .response
            .on_hover_text(texts.get("sound.list.tip"));
        if ui.button(texts.get("sound.save")).on_hover_text(texts.get("sound.save.tip")).clicked() {
            let name = current(model).filter(|s| !s.ready_made).map(|s| s.name.clone()).unwrap_or_default();
            model.dialog = Some(SoundDialog::Save { name, problem: None });
        }
        let applies = menu(model);
        ui.menu_button(texts.get("sound.more"), |ui| {
            if ui
                .add_enabled(applies.rename, egui::Button::new(texts.get("sound.rename")))
                .on_hover_text(texts.get("sound.rename.tip"))
                .clicked()
            {
                let from = current(model).map(|s| s.name.clone()).unwrap_or_default();
                model.dialog = Some(SoundDialog::Rename {
                    name: from.clone(),
                    from,
                    problem: None,
                });
                ui.close();
            }
            if ui
                .add_enabled(applies.delete, egui::Button::new(texts.get("sound.delete")))
                .on_hover_text(texts.get("sound.delete.tip"))
                .clicked()
            {
                actions.push(Action::DeleteSound(model.current.clone()));
                ui.close();
            }
            if ui
                .add_enabled(applies.restore, egui::Button::new(texts.get("sound.restore")))
                .on_hover_text(texts.get("sound.restore.tip"))
                .clicked()
            {
                actions.push(Action::RestoreSound(model.current.clone()));
                ui.close();
            }
            ui.separator();
            if ui
                .button(texts.get("sound.export"))
                .on_hover_text(texts.get("sound.export.tip"))
                .clicked()
            {
                actions.push(Action::ExportSound);
                ui.close();
            }
            if ui
                .button(texts.get("sound.import"))
                .on_hover_text(texts.get("sound.import.tip"))
                .clicked()
            {
                actions.push(Action::ImportSound);
                ui.close();
            }
        })
        .response
        .on_hover_text(texts.get("sound.more.tip"));
    });
}

/// What the guitarist did in a dialog this frame.
enum Answer {
    Nothing,
    Ok(String),
    Cancel,
}

/// The name prompt: the name field, what is wrong with the name, OK and Cancel (Enter is OK).
fn name_prompt(ui: &mut Ui, name: &mut String, problem: &Option<String>, texts: &Texts) -> Answer {
    let mut answer = Answer::Nothing;
    ui.label(texts.get("sound.name"));
    let field = ui
        .add(
            egui::TextEdit::singleline(name)
                .char_limit(guitarfx_settings::sounds::NAME_LENGTH)
                .desired_width(NAME_WIDTH),
        )
        .on_hover_text(texts.get("sound.name.tip"));
    if field.lost_focus() && ui.input(|i| i.key_pressed(egui::Key::Enter)) {
        answer = Answer::Ok(name.clone());
    }
    if let Some(problem) = problem {
        ui.add(egui::Label::new(egui::RichText::new(problem).color(ui.visuals().error_fg_color)).wrap());
    }
    ui.separator();
    ui.horizontal_wrapped(|ui| {
        if ui.button(texts.get("dialog.ok")).clicked() {
            answer = Answer::Ok(name.clone());
        }
        if ui.button(texts.get("dialog.cancel")).clicked() {
            answer = Answer::Cancel;
        }
    });
    answer
}

/// The sound list's dialog, when one is open.
pub fn dialog(ctx: &Context, model: &mut SoundsModel, texts: &Texts, actions: &mut Vec<Action>) {
    let Some(dialog) = model.dialog.as_mut() else {
        return;
    };
    let mut answer = Answer::Nothing;
    egui::Modal::new(Id::new("sound-dialog")).show(ctx, |ui| {
        ui.set_max_width(DIALOG_WIDTH);
        match dialog {
            SoundDialog::Save { name, problem } => {
                ui.label(egui::RichText::new(texts.get("sound.save.title")).strong());
                answer = name_prompt(ui, name, problem, texts);
                if let Answer::Ok(name) = &answer {
                    actions.push(Action::SaveSound(name.clone()));
                }
            }
            SoundDialog::Rename { from, name, problem } => {
                ui.label(egui::RichText::new(texts.get("sound.rename.title")).strong());
                answer = name_prompt(ui, name, problem, texts);
                if let Answer::Ok(to) = &answer {
                    actions.push(Action::RenameSound {
                        from: from.clone(),
                        to: to.clone(),
                    });
                }
            }
            SoundDialog::Clash { name } => {
                ui.add(egui::Label::new(texts.format("sound.import.clash", &[("name", name)])).wrap());
                ui.separator();
                ui.horizontal_wrapped(|ui| {
                    if ui.button(texts.get("sound.import.replace")).clicked() {
                        actions.push(Action::ImportReplace);
                    }
                    if ui.button(texts.get("sound.import.keep-both")).clicked() {
                        actions.push(Action::ImportKeepBoth);
                    }
                    if ui.button(texts.get("dialog.cancel")).clicked() {
                        answer = Answer::Cancel;
                    }
                });
            }
        }
    });
    if matches!(answer, Answer::Cancel) {
        model.dialog = None;
        actions.push(Action::CloseSoundDialog);
    }
}
