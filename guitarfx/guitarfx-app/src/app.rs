//! The application: the main window over the engine (contracts/application.md). It keeps the settings, lists the devices, turns the sound
//! on and off, and turns what the engine reports into messages. Only this thread writes the settings.

use crate::delay_estimate;
use crate::devices::{self, DeviceList, Problem, Running};
use crate::engine::State;
use eframe::egui;
use guitarfx_dsp::{chain_text, ready_made, settings::ChainParams};
use guitarfx_settings::settings::{Notice, Settings};
use guitarfx_settings::texts::Texts;
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
}

impl App {
    pub fn new(folder: PathBuf) -> App {
        let loaded = Settings::load(&folder);
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
        };
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
        let actions = window::show(ctx, &mut self.model, &self.texts);
        for action in actions {
            self.act(action);
        }
        self.remember_window(ctx);
        if self.running.is_some() {
            ctx.request_repaint_after(refresh());
        }
    }

    fn on_exit(&mut self, _gl: Option<&eframe::glow::Context>) {
        self.running = None;
        self.save_settings();
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
