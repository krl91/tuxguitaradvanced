//! The plugin's editor (contracts/plugin.md "Editor"): the application's sound list and the chain's three tabs, with no devices, volume,
//! *Sound on* or *Advanced…*. The sounds are the application's `sounds.properties`, read when the editor opens and written only when the
//! sound list changes them; a chain change is played and kept in the project's state. The message line says why the sound was lightened
//! or muted, and when the project's state came from a newer GuitarFX.

use crate::processor::{Shared, State};
use crate::state;
use guitarfx_dsp::ready_made;
use guitarfx_dsp::settings::ChainParams;
use guitarfx_settings::settings::Notice;
use guitarfx_settings::sound_file;
use guitarfx_settings::sounds::{Import, NameProblem, Sounds};
use guitarfx_settings::texts::Texts;
use guitarfx_ui::sounds::{SoundDialog, SoundItem, SoundsModel};
use guitarfx_ui::window::{self, Action, Dialog, Model, Tab, PLUGIN_SIZE};
use nih_plug::prelude::Editor;
use nih_plug_egui::{create_egui_editor, egui, EguiSettings, EguiState};
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, RwLock};

/// What the editor shares with the plugin.
#[derive(Clone)]
pub struct Link {
    pub shared: Arc<Shared>,
    /// The project's state text (`state.rs`).
    pub state: Arc<RwLock<String>>,
    /// The project's state came from a newer GuitarFX and is not replaced until the guitarist changes the sound.
    pub newer: Arc<AtomicBool>,
}

/// The message line's key for the sound's state: why it was muted, else how far it was lightened.
pub fn safety_key(state: State, lightening: usize) -> Option<&'static str> {
    const LIGHTER: &[&str] = &[
        "message.lighter.1",
        "message.lighter.2",
        "message.lighter.3",
        "message.lighter.4",
        "message.lighter.5",
    ];
    match state {
        State::MutedFeedback => Some("plugin.muted.feedback"),
        State::MutedOverload => Some("plugin.muted.overload"),
        State::Starting | State::On | State::Stopping => lightening.checked_sub(1).and_then(|i| LIGHTER.get(i)).copied(),
    }
}

fn name_problem_key(problem: NameProblem) -> &'static str {
    match problem {
        NameProblem::Empty => "sound.name.empty",
        NameProblem::TooLong => "sound.name.too-long",
        NameProblem::ReadyMade => "sound.name.ready-made",
        NameProblem::Taken => "sound.name.taken",
    }
}

fn file_name(path: &Path) -> String {
    path.file_name().map(|n| n.to_string_lossy().to_string()).unwrap_or_default()
}

/// The editor without its window: the sounds, the models the shared widgets show, and what the guitarist's actions do.
pub struct EditorLogic {
    folder: Option<PathBuf>,
    texts: Texts,
    sounds: Sounds,
    /// False while a newer sounds file must not be written over.
    sounds_writable: bool,
    model: Model,
    list: SoundsModel,
    /// An imported sound waiting for the guitarist's answer: its name and chain.
    pending_import: Option<(String, ChainParams)>,
    /// The safety message last shown, so that each is shown once.
    safety: Option<&'static str>,
}

impl EditorLogic {
    /// The editor as it opens on `chain`, with the sounds of the settings `folder` (none: the ready-made sounds only).
    pub fn open(folder: Option<&Path>, chain: ChainParams, newer: bool, texts: Texts) -> Self {
        let loaded = folder.map(Sounds::load);
        let mut message = newer.then(|| texts.get("plugin.state.newer").to_string());
        if let Some(loaded) = &loaded {
            match &loaded.notice {
                Some(Notice::Newer) => message = Some(texts.get("message.sounds.newer").to_string()),
                Some(Notice::Damaged(copy)) => message = Some(texts.format("message.sounds.damaged", &[("file", &file_name(copy))])),
                None => {}
            }
        }
        let mut editor = Self {
            folder: folder.map(Path::to_path_buf),
            sounds_writable: loaded.as_ref().is_some_and(|l| l.writable),
            sounds: loaded.map(|l| l.sounds).unwrap_or_default(),
            model: Model {
                inputs: Vec::new(),
                outputs: Vec::new(),
                input: None,
                channel: None,
                output: None,
                sound_on: true,
                volume: 1.0,
                meter: 0.0,
                clipping: false,
                delay_ms: None,
                message,
                dialog: None,
                chain,
                tab: Tab::default(),
            },
            list: SoundsModel::default(),
            pending_import: None,
            safety: None,
            texts,
        };
        editor.list.current = editor
            .sounds
            .list()
            .into_iter()
            .find(|s| s.chain == chain)
            .map(|s| s.name)
            .unwrap_or_default();
        editor.refresh_list();
        editor
    }

    pub fn model(&self) -> &Model {
        &self.model
    }

    pub fn sound_list(&self) -> &SoundsModel {
        &self.list
    }

    /// The list as the window shows it, the open dialog kept.
    fn refresh_list(&mut self) {
        self.list.sounds = self
            .sounds
            .list()
            .into_iter()
            .map(|s| SoundItem {
                name: s.name,
                ready_made: s.ready_made,
                changed: s.changed,
            })
            .collect();
        self.list.unsaved = self.sounds.is_changed(&self.list.current, &self.model.chain);
    }

    fn save_sounds(&mut self) {
        if let Some(folder) = self.folder.as_ref().filter(|_| self.sounds_writable) {
            if let Err(error) = self.sounds.save(folder) {
                self.model.message = Some(self.texts.format("message.sound.not-saved", &[("error", &error.to_string())]));
            }
        }
    }

    /// Shows what is wrong with the name in the open prompt.
    fn name_problem(&mut self, problem: NameProblem) {
        let length = guitarfx_settings::sounds::NAME_LENGTH.to_string();
        let text = self.texts.format(name_problem_key(problem), &[("length", &length)]);
        if let Some(SoundDialog::Save { problem, .. } | SoundDialog::Rename { problem, .. }) = &mut self.list.dialog {
            *problem = Some(text);
        }
    }

    fn imported(&mut self, name: &str) {
        self.list.dialog = None;
        self.model.message = Some(self.texts.format("message.sound.imported", &[("name", name)]));
        self.save_sounds();
    }

    /// *Export…*: the chain in use, under the current sound's name.
    fn export_sound(&mut self) {
        let name = if self.list.current.is_empty() {
            ready_made::ALL[0].0.to_string()
        } else {
            self.list.current.clone()
        };
        let chosen = rfd::FileDialog::new()
            .add_filter(self.texts.get("sound.file.filter"), &[sound_file::EXTENSION])
            .set_file_name(format!("{name}.{}", sound_file::EXTENSION))
            .save_file();
        let Some(mut path) = chosen else {
            return;
        };
        if !path.extension().is_some_and(|e| e.eq_ignore_ascii_case(sound_file::EXTENSION)) {
            path.as_mut_os_string().push(format!(".{}", sound_file::EXTENSION));
        }
        self.model.message = Some(match sound_file::write(&path, &name, &self.model.chain) {
            Ok(()) => self.texts.format("message.sound.exported", &[("file", &file_name(&path))]),
            Err(error) => self.texts.format("message.sound.not-saved", &[("error", &error.to_string())]),
        });
    }

    /// *Import…*: a file refused changes nothing; a name of an own sound waits for the guitarist's answer.
    fn import_sound(&mut self) {
        let chosen = rfd::FileDialog::new()
            .add_filter(self.texts.get("sound.file.filter"), &[sound_file::EXTENSION])
            .pick_file();
        let Some(path) = chosen else {
            return;
        };
        match sound_file::read_file(&path) {
            Err(error) => {
                let text = self.texts.get(error.key()).to_string();
                self.model.dialog = Some(Dialog::Message(text.clone()));
                self.model.message = Some(text);
            }
            Ok(sound) => match self.sounds.import(&sound.name, sound.chain) {
                Import::Added(name) => self.imported(&name),
                Import::Clash(name) => {
                    self.pending_import = Some((sound.name, sound.chain));
                    self.list.dialog = Some(SoundDialog::Clash { name });
                }
            },
        }
    }

    /// Does what the guitarist asked; returns the chain to play and keep in the project when it changed.
    pub fn act(&mut self, action: Action) -> Option<ChainParams> {
        let mut played = None;
        match action {
            Action::Chain(chain) => {
                self.model.chain = *chain;
                played = Some(*chain);
            }
            Action::PickSound(name) => {
                if let Some(sound) = self.sounds.sound(&name) {
                    self.list.current = sound.name;
                    self.model.chain = sound.chain;
                    played = Some(sound.chain);
                }
            }
            Action::SaveSound(name) => match self.sounds.save_as(&name, self.model.chain) {
                Ok(()) => {
                    self.list.current = name.trim().to_string();
                    self.list.dialog = None;
                    self.save_sounds();
                }
                Err(problem) => self.name_problem(problem),
            },
            Action::RenameSound { from, to } => match self.sounds.rename(&from, &to) {
                Ok(()) => {
                    if self.list.current.eq_ignore_ascii_case(from.trim()) {
                        self.list.current = to.trim().to_string();
                    }
                    self.list.dialog = None;
                    self.save_sounds();
                }
                Err(problem) => self.name_problem(problem),
            },
            Action::DeleteSound(name) => {
                if self.sounds.delete(&name) {
                    // the chain in use stays
                    if self.list.current.eq_ignore_ascii_case(name.trim()) {
                        self.list.current = String::new();
                    }
                    self.save_sounds();
                }
            }
            Action::RestoreSound(name) => {
                if let Some(chain) = self.sounds.restore(&name) {
                    if self.list.current.eq_ignore_ascii_case(name.trim()) {
                        self.model.chain = chain;
                        played = Some(chain);
                    }
                    self.save_sounds();
                }
            }
            Action::ExportSound => self.export_sound(),
            Action::ImportSound => self.import_sound(),
            Action::ImportReplace => {
                if let Some((name, chain)) = self.pending_import.take() {
                    self.sounds.import_replace(&name, chain);
                    let name = self.sounds.sound(&name).map(|s| s.name).unwrap_or(name);
                    self.imported(&name);
                }
            }
            Action::ImportKeepBoth => {
                if let Some((name, chain)) = self.pending_import.take() {
                    let name = self.sounds.import_keep_both(&name, chain);
                    self.imported(&name);
                }
            }
            Action::CloseSoundDialog => {
                self.list.dialog = None;
                self.pending_import = None;
            }
            Action::CloseDialog => self.model.dialog = None,
            // the application's parts, which the editor does not show
            Action::Refresh
            | Action::Input(_)
            | Action::Channel(_)
            | Action::Output(_)
            | Action::SoundOn
            | Action::SoundOff
            | Action::Volume { .. }
            | Action::TurnOnAnyway
            | Action::KeepNewerSettings
            | Action::ReplaceNewerSettings
            | Action::OpenAdvanced
            | Action::CloseAdvanced
            | Action::Param { .. }
            | Action::AdvancedDefaults => {}
        }
        self.refresh_list();
        played
    }

    /// Shows a safety message once, when the sound's state brings a new one.
    pub fn poll(&mut self, state: State, lightening: usize) {
        let key = safety_key(state, lightening);
        if key != self.safety {
            self.safety = key;
            if let Some(key) = key {
                self.model.message = Some(self.texts.get(key).to_string());
            }
        }
    }

    pub fn show(&mut self, ctx: &egui::Context) -> Vec<Action> {
        window::show_plugin(ctx, &mut self.model, &mut self.list, &self.texts)
    }
}

/// The editor's window for the host, at the plugin size.
pub fn create(link: Link) -> Option<Box<dyn Editor>> {
    let size = EguiState::from_size(PLUGIN_SIZE[0] as u32, PLUGIN_SIZE[1] as u32);
    let open = link.clone();
    create_egui_editor(
        size,
        None::<EditorLogic>,
        EguiSettings::default(),
        move |_ctx, _queue, editor: &mut Option<EditorLogic>| {
            // read again each time the window opens: the application may have changed the sounds meanwhile
            let text = open.state.read().map(|t| t.clone()).unwrap_or_default();
            let folder = guitarfx_settings::folder::settings_folder();
            *editor = Some(EditorLogic::open(
                folder.as_deref(),
                state::read(&text).chain(),
                open.newer.load(Ordering::Acquire),
                Texts::system(),
            ));
        },
        move |ctx, _setter, _queue, editor: &mut Option<EditorLogic>| {
            let Some(editor) = editor.as_mut() else {
                return;
            };
            editor.poll(link.shared.state(), link.shared.lightening());
            for action in editor.show(ctx) {
                if let Some(chain) = editor.act(action) {
                    if let Ok(mut text) = link.state.write() {
                        *text = state::text(&chain);
                    }
                    link.newer.store(false, Ordering::Release);
                    link.shared.post_chain(chain);
                }
            }
        },
    )
}
