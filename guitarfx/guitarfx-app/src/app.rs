//! The application: the main window over the engine (contracts/application.md). It keeps the settings, lists the devices, turns the sound
//! on and off, and turns what the engine reports into messages. Only this thread writes the settings.

use crate::delay_estimate;
use crate::devices::{self, DeviceList, Problem, Running};
use crate::engine::State;
use eframe::egui;
use guitarfx_dsp::{chain_text, ready_made, settings::ChainParams};
use guitarfx_settings::settings::{Notice, Settings};
use guitarfx_settings::sound_file;
use guitarfx_settings::sounds::{Import, NameProblem, Sounds};
use guitarfx_settings::texts::Texts;
use guitarfx_ui::sounds::{SoundDialog, SoundItem, SoundsModel};
use guitarfx_ui::window::{self, Action, Dialog, Model, Tab};
use std::path::PathBuf;
use std::process::ExitCode;
use std::time::{Duration, Instant};

/// The chain to play: the chain as the guitarist left it, else the current sound's, else the first ready-made sound.
pub fn current_chain(settings: &Settings) -> ChainParams {
    settings
        .chain_current
        .as_deref()
        .and_then(chain_text::parse)
        .or_else(|| ready_made::chain(&settings.sound_current))
        .unwrap_or_else(ready_made::first)
}

/// How often the meter and the messages are refreshed while the sound is on.
fn refresh() -> Duration {
    Duration::from_secs(1) / 20
}

/// A repeated message (the clipping) is shown at most this often.
fn notice_interval() -> Duration {
    Duration::from_secs(1)
}

pub struct App {
    folder: PathBuf,
    settings: Settings,
    writable: bool,
    texts: Texts,
    devices: DeviceList,
    model: Model,
    running: Option<Running>,
    lightening: usize,
    clipped: u64,
    clipping_until: Option<Instant>,
    last_clipping_notice: Option<Instant>,
    /// A change not saved yet (the knobs move many times a second: they are saved once they rest).
    unsaved_since: Option<Instant>,
    sounds: Sounds,
    /// False while a newer sounds file must not be written over.
    sounds_writable: bool,
    sound_list: SoundsModel,
    /// An imported sound waiting for the guitarist's answer: its name and chain.
    pending_import: Option<(String, ChainParams)>,
}

fn name_problem_key(problem: NameProblem) -> &'static str {
    match problem {
        NameProblem::Empty => "sound.name.empty",
        NameProblem::TooLong => "sound.name.too-long",
        NameProblem::ReadyMade => "sound.name.ready-made",
        NameProblem::Taken => "sound.name.taken",
    }
}

fn file_name(path: &std::path::Path) -> String {
    path.file_name().map(|n| n.to_string_lossy().to_string()).unwrap_or_default()
}

impl App {
    pub fn new(folder: PathBuf) -> App {
        let loaded = Settings::load(&folder);
        let sounds = Sounds::load(&folder);
        let texts = Texts::english();
        let settings = loaded.settings;
        let mut app = App {
            folder,
            model: Model {
                inputs: Vec::new(),
                outputs: Vec::new(),
                input: settings.input.clone(),
                channel: settings.channel,
                output: settings.output.clone(),
                sound_on: false,
                volume: settings.volume,
                meter: 0.0,
                clipping: false,
                delay_ms: None,
                message: None,
                dialog: None,
                chain: current_chain(&settings),
                tab: Tab::default(),
            },
            settings,
            writable: loaded.writable,
            texts,
            devices: DeviceList::default(),
            running: None,
            lightening: 0,
            clipped: 0,
            clipping_until: None,
            last_clipping_notice: None,
            unsaved_since: None,
            sounds: sounds.sounds,
            sounds_writable: sounds.writable,
            sound_list: SoundsModel::default(),
            pending_import: None,
        };
        match sounds.notice {
            Some(Notice::Newer) => app.say("message.sounds.newer"),
            Some(Notice::Damaged(copy)) => {
                app.model.message = Some(app.texts.format("message.sounds.damaged", &[("file", &file_name(&copy))]));
            }
            None => {}
        }
        match loaded.notice {
            Some(Notice::Newer) => app.model.dialog = Some(Dialog::NewerSettings),
            Some(Notice::Damaged(copy)) => {
                let name = copy.file_name().map(|n| n.to_string_lossy().to_string()).unwrap_or_default();
                app.model.message = Some(app.texts.format("message.settings.damaged", &[("file", &name)]));
            }
            None => {}
        }
        app.refresh_devices();
        app
    }

    fn save_settings(&self) {
        if self.writable {
            if let Err(error) = self.settings.save(&self.folder) {
                eprintln!("the settings could not be saved: {error}");
            }
        }
    }

    fn save_sounds(&self) {
        if self.sounds_writable {
            if let Err(error) = self.sounds.save(&self.folder) {
                eprintln!("the sounds could not be saved: {error}");
            }
        }
    }

    /// The list as the window shows it, the open dialog kept.
    fn refresh_sound_list(&mut self) {
        self.sound_list.sounds = self
            .sounds
            .list()
            .into_iter()
            .map(|s| SoundItem {
                name: s.name,
                ready_made: s.ready_made,
                changed: s.changed,
            })
            .collect();
        self.sound_list.current = self.settings.sound_current.clone();
        self.sound_list.unsaved = self.sounds.is_changed(&self.settings.sound_current, &self.model.chain);
    }

    /// The chain in use becomes `chain`: played at once, kept in the settings.
    fn use_chain(&mut self, chain: ChainParams) {
        self.model.chain = chain;
        self.settings.chain_current = Some(chain_text::format(&chain));
        if let Some(running) = &self.running {
            running.control.post_chain(chain);
        }
    }

    fn pick_sound(&mut self, name: &str) {
        if let Some(sound) = self.sounds.sound(name) {
            self.settings.sound_current = sound.name;
            self.use_chain(sound.chain);
            self.save_settings();
        }
    }

    fn save_sound(&mut self, name: &str) {
        match self.sounds.save_as(name, self.model.chain) {
            Ok(()) => {
                self.settings.sound_current = name.trim().to_string();
                self.sound_list.dialog = None;
                self.save_sounds();
                self.save_settings();
            }
            Err(problem) => self.name_problem(problem),
        }
    }

    fn rename_sound(&mut self, from: &str, to: &str) {
        match self.sounds.rename(from, to) {
            Ok(()) => {
                if self.settings.sound_current.eq_ignore_ascii_case(from.trim()) {
                    self.settings.sound_current = to.trim().to_string();
                }
                self.sound_list.dialog = None;
                self.save_sounds();
                self.save_settings();
            }
            Err(problem) => self.name_problem(problem),
        }
    }

    /// Shows what is wrong with the name in the open prompt.
    fn name_problem(&mut self, problem: NameProblem) {
        let text = self.texts.get(name_problem_key(problem)).to_string();
        if let Some(SoundDialog::Save { problem, .. } | SoundDialog::Rename { problem, .. }) = &mut self.sound_list.dialog {
            *problem = Some(text);
        }
    }

    /// Deletes an own sound; the chain in use stays, and Clean becomes the current sound (as `AmpSoundStore.current`).
    fn delete_sound(&mut self, name: &str) {
        if self.sounds.delete(name) {
            if self.settings.sound_current.eq_ignore_ascii_case(name.trim()) {
                self.settings.sound_current = ready_made::ALL[0].0.to_string();
            }
            self.save_sounds();
            self.save_settings();
        }
    }

    fn restore_sound(&mut self, name: &str) {
        if let Some(chain) = self.sounds.restore(name) {
            if self.settings.sound_current.eq_ignore_ascii_case(name.trim()) {
                self.use_chain(chain);
            }
            self.save_sounds();
            self.save_settings();
        }
    }

    /// *Export…*: the chain in use, under the current sound's name.
    fn export_sound(&mut self) {
        let name = self.settings.sound_current.clone();
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
                    self.sound_list.dialog = Some(SoundDialog::Clash { name });
                }
            },
        }
    }

    fn imported(&mut self, name: &str) {
        self.sound_list.dialog = None;
        self.model.message = Some(self.texts.format("message.sound.imported", &[("name", name)]));
        self.save_sounds();
    }

    fn refresh_devices(&mut self) {
        self.devices = devices::list();
        self.model.inputs = self.devices.inputs.iter().map(|i| (i.name.clone(), i.channels)).collect();
        self.model.outputs = self.devices.outputs.clone();
    }

    fn say(&mut self, key: &str) {
        self.model.message = Some(self.texts.get(key).to_string());
    }

    fn problem(&mut self, problem: &Problem) {
        let name = match problem {
            Problem::Missing { input: true } => self.settings.input.clone(),
            Problem::Missing { input: false } => self.settings.output.clone(),
            _ => None,
        }
        .unwrap_or_default();
        let text = self.texts.format(problem.key(), &[("name", &name)]);
        if matches!(problem, Problem::PermissionDenied | Problem::Lost { .. } | Problem::Unsupported) {
            self.model.dialog = Some(Dialog::Message(text.clone()));
        }
        self.model.message = Some(text);
    }

    /// Turns the sound on, after the warning when the input is the built-in microphone (FR-012).
    fn sound_on(&mut self, warned: bool) {
        let (Some(input), Some(output)) = (self.settings.input.clone(), self.settings.output.clone()) else {
            self.say("message.choose");
            return;
        };
        if !warned && devices::is_built_in_microphone(&input) {
            self.model.dialog = Some(Dialog::BuiltInMicrophone);
            return;
        }
        let channel = self.settings.channel.unwrap_or(1);
        match devices::start(&input, channel, &output, &self.settings.params, self.model.chain, self.settings.volume) {
            Ok(running) => {
                self.running = Some(running);
                self.model.sound_on = true;
                self.lightening = 0;
                self.clipped = 0;
                self.model.message = None;
            }
            Err(problem) => self.problem(&problem),
        }
    }

    /// Closes the devices at once (a device change, or a problem).
    fn sound_off_now(&mut self) {
        self.running = None;
        self.model.sound_on = false;
        self.model.delay_ms = None;
        self.model.meter = 0.0;
        self.model.clipping = false;
    }

    fn act(&mut self, action: Action) {
        match action {
            Action::Refresh => self.refresh_devices(),
            Action::Input(name) => {
                self.settings.input = Some(name.clone());
                self.settings.channel = Some(1);
                self.model.input = Some(name);
                self.model.channel = Some(1);
                self.device_changed();
            }
            Action::Channel(channel) => {
                self.settings.channel = Some(channel);
                self.model.channel = Some(channel);
                self.device_changed();
            }
            Action::Output(name) => {
                self.settings.output = Some(name.clone());
                self.model.output = Some(name);
                self.device_changed();
            }
            Action::SoundOn => self.sound_on(false),
            Action::SoundOff => {
                if let Some(running) = &self.running {
                    running.control.stop();
                } else {
                    self.model.sound_on = false;
                }
            }
            Action::Volume { value, done } => {
                self.settings.volume = value;
                if let Some(running) = &self.running {
                    running.control.set_volume(value);
                }
                if done {
                    self.save_settings();
                }
            }
            Action::TurnOnAnyway => {
                self.model.dialog = None;
                self.sound_on(true);
            }
            Action::KeepNewerSettings => self.model.dialog = None,
            Action::ReplaceNewerSettings => {
                self.model.dialog = None;
                self.writable = true;
                self.save_settings();
            }
            Action::CloseDialog => self.model.dialog = None,
            Action::Chain(chain) => {
                self.use_chain(*chain);
                self.sounds.keep_current(&self.settings.sound_current, &chain);
                self.unsaved_since.get_or_insert_with(Instant::now);
            }
            Action::PickSound(name) => self.pick_sound(&name),
            Action::SaveSound(name) => self.save_sound(&name),
            Action::RenameSound { from, to } => self.rename_sound(&from, &to),
            Action::DeleteSound(name) => self.delete_sound(&name),
            Action::RestoreSound(name) => self.restore_sound(&name),
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
                self.sound_list.dialog = None;
                self.pending_import = None;
            }
        }
    }

    /// A new device or channel: saved, and the sound restarted on it when it was on.
    fn device_changed(&mut self) {
        self.save_settings();
        if self.running.is_some() {
            self.sound_off_now();
            self.sound_on(true);
        }
    }

    /// Reads what the engine and the devices report.
    fn poll(&mut self) {
        let Some(running) = &self.running else {
            return;
        };
        let control = &running.control;
        let state = control.state();
        let problem = running.problem();
        let lightening = control.lightening();
        let clipped = control.clipped_blocks();
        let peak = control.take_peak();
        let delay = delay_estimate::milliseconds(&running.parts());
        let now = Instant::now();
        self.model.meter = peak;
        self.model.delay_ms = Some(delay);
        if clipped > self.clipped {
            self.clipped = clipped;
            self.clipping_until = Some(now + notice_interval());
            if self.last_clipping_notice.is_none_or(|t| now - t >= notice_interval()) {
                self.last_clipping_notice = Some(now);
                self.say("message.clipping");
            }
        }
        self.model.clipping = self.clipping_until.is_some_and(|t| now < t);
        if lightening > self.lightening {
            self.lightening = lightening;
            self.say(&format!("message.lighter.{lightening}"));
        }
        if let Some(problem) = problem {
            self.sound_off_now();
            self.problem(&problem);
            return;
        }
        match state {
            State::Off => self.sound_off_now(),
            State::MutedFeedback => {
                self.sound_off_now();
                let text = self.texts.get("message.feedback").to_string();
                self.model.message = Some(text.clone());
                self.model.dialog = Some(Dialog::Message(text));
            }
            State::MutedOverload => {
                self.sound_off_now();
                self.say("message.overload");
            }
            State::Starting | State::On | State::Stopping => {}
        }
    }

    fn remember_window(&mut self, ctx: &egui::Context) {
        let rect = ctx.input(|i| {
            let viewport = i.viewport();
            viewport.outer_rect.zip(viewport.inner_rect)
        });
        if let Some((outer, inner)) = rect {
            self.settings.window = Some([outer.min.x, outer.min.y, inner.width(), inner.height()]);
        }
    }
}

impl eframe::App for App {
    fn update(&mut self, ctx: &egui::Context, _frame: &mut eframe::Frame) {
        self.poll();
        self.refresh_sound_list();
        let actions = window::show_with(ctx, &mut self.model, &mut self.sound_list, &self.texts);
        for action in actions {
            self.act(action);
        }
        self.remember_window(ctx);
        if self.unsaved_since.is_some_and(|t| t.elapsed() >= notice_interval()) {
            self.unsaved_since = None;
            self.save_settings();
            self.save_sounds();
        }
        if self.unsaved_since.is_some() {
            ctx.request_repaint_after(notice_interval());
        }
        if self.running.is_some() {
            ctx.request_repaint_after(refresh());
        }
    }

    fn on_exit(&mut self, _gl: Option<&eframe::glow::Context>) {
        self.running = None;
        self.save_settings();
        self.save_sounds();
    }
}

fn options(settings: &Settings, title: &str) -> eframe::NativeOptions {
    let mut viewport = egui::ViewportBuilder::default()
        .with_title(title)
        .with_inner_size(window::FIRST_SIZE)
        .with_min_inner_size(window::SMALLEST_SIZE);
    if let Some([x, y, width, height]) = settings.window {
        viewport = viewport.with_position([x, y]).with_inner_size([width, height]);
    }
    eframe::NativeOptions {
        viewport,
        ..Default::default()
    }
}

/// Opens the main window until it is closed.
pub fn run(folder: PathBuf) -> ExitCode {
    let app = App::new(folder);
    let texts = Texts::english();
    let title = texts.get("app.title").to_string();
    let options = options(&app.settings, &title);
    match eframe::run_native(&title, options, Box::new(|_| Ok(Box::new(app)))) {
        Ok(()) => ExitCode::SUCCESS,
        Err(error) => {
            eprintln!("{error}");
            ExitCode::FAILURE
        }
    }
}

/// The second instance: a small window says GuitarFX is already open, then it quits. No device is opened.
pub fn already_open() -> ExitCode {
    let texts = Texts::english();
    let title = texts.get("app.title").to_string();
    let message = texts.get("message.already-open").to_string();
    let ok = texts.get("dialog.ok").to_string();
    let viewport = egui::ViewportBuilder::default()
        .with_title(&title)
        .with_inner_size(window::NOTICE_SIZE)
        .with_resizable(false);
    let options = eframe::NativeOptions {
        viewport,
        ..Default::default()
    };
    let shown = eframe::run_simple_native(&title, options, move |ctx, _| {
        egui::CentralPanel::default().show(ctx, |ui| {
            ui.label(&message);
            if ui.button(&ok).clicked() {
                ctx.send_viewport_cmd(egui::ViewportCommand::Close);
            }
        });
    });
    if shown.is_err() {
        eprintln!("{}", texts.get("message.already-open"));
    }
    ExitCode::SUCCESS
}
