//! The main window (contracts/application.md): the devices bar, the sound control (*Sound on*, the volume, the input meter, the estimated
//! delay and the Bluetooth note), the chain's three tabs, and the message line. It fits a 1024×768 screen.

use crate::{chain_rows, devices_bar, messages};
use egui::{Align, Context, Layout, RichText, Ui};
use guitarfx_dsp::settings::ChainParams;
use guitarfx_settings::texts::Texts;

/// The size the window opens at the first time, and the least it may be made.
pub const FIRST_SIZE: [f32; 2] = [960.0, 640.0];
pub const SMALLEST_SIZE: [f32; 2] = [800.0, 560.0];
/// The size of the small window that says GuitarFX is already open.
pub const NOTICE_SIZE: [f32; 2] = [360.0, 100.0];
/// The input meter's length.
const METER_WIDTH: f32 = 120.0;
/// The volume slider's length.
const VOLUME_WIDTH: f32 = 140.0;

#[derive(Clone, Copy, Debug, PartialEq, Eq, Default)]
pub enum Tab {
    #[default]
    Before,
    Amp,
    After,
}

impl Tab {
    pub const ALL: &[Tab] = &[Tab::Before, Tab::Amp, Tab::After];

    fn key(self) -> &'static str {
        match self {
            Tab::Before => "tab.before",
            Tab::Amp => "tab.amp",
            Tab::After => "tab.after",
        }
    }
}

/// A dialog over the window.
#[derive(Clone, Debug, PartialEq)]
pub enum Dialog {
    /// The feedback warning before turning on with the built-in microphone (FR-012).
    BuiltInMicrophone,
    /// The settings file comes from a newer GuitarFX.
    NewerSettings,
    /// A message that needs an OK (its text, already formatted).
    Message(String),
}

/// What the window shows.
#[derive(Clone, Debug)]
pub struct Model {
    /// The system's inputs, with their channels.
    pub inputs: Vec<(String, u16)>,
    pub outputs: Vec<String>,
    pub input: Option<String>,
    /// 1-based.
    pub channel: Option<u32>,
    pub output: Option<String>,
    pub sound_on: bool,
    /// 0 to 1.
    pub volume: f64,
    /// The input's peak, linear.
    pub meter: f32,
    pub clipping: bool,
    /// None while the sound is off.
    pub delay_ms: Option<f64>,
    /// The message line.
    pub message: Option<String>,
    pub dialog: Option<Dialog>,
    pub chain: ChainParams,
    pub tab: Tab,
}

/// What the guitarist did.
#[derive(Clone, Debug, PartialEq)]
pub enum Action {
    Refresh,
    Input(String),
    Channel(u32),
    Output(String),
    SoundOn,
    SoundOff,
    /// The volume moved; `done` once the slider is let go.
    Volume {
        value: f64,
        done: bool,
    },
    TurnOnAnyway,
    KeepNewerSettings,
    ReplaceNewerSettings,
    CloseDialog,
    /// The chain as the guitarist changed it.
    Chain(Box<ChainParams>),
}

pub fn show(ctx: &Context, model: &mut Model, texts: &Texts) -> Vec<Action> {
    let mut actions = Vec::new();
    egui::TopBottomPanel::top("devices").show(ctx, |ui| devices_bar::show(ui, model, texts, &mut actions));
    egui::TopBottomPanel::top("sound").show(ctx, |ui| sound(ui, model, texts, &mut actions));
    egui::TopBottomPanel::bottom("messages").show(ctx, |ui| messages::line(ui, model.message.as_deref()));
    egui::CentralPanel::default().show(ctx, |ui| tabs(ui, model, texts, &mut actions));
    if let Some(dialog) = model.dialog.clone() {
        messages::dialog(ctx, &dialog, texts, &mut actions);
    }
    actions
}

fn sound(ui: &mut Ui, model: &mut Model, texts: &Texts, actions: &mut Vec<Action>) {
    ui.horizontal_wrapped(|ui| {
        let mut on = model.sound_on;
        if ui
            .toggle_value(&mut on, RichText::new(texts.get("sound.on")).strong())
            .on_hover_text(texts.get("sound.on.tip"))
            .changed()
        {
            actions.push(if on { Action::SoundOn } else { Action::SoundOff });
        }
        ui.separator();
        ui.label(texts.get("volume"));
        let mut percent = model.volume * 100.0;
        ui.spacing_mut().slider_width = VOLUME_WIDTH;
        let slider = ui
            .add(egui::Slider::new(&mut percent, 0.0..=100.0).suffix(" %").fixed_decimals(0))
            .on_hover_text(texts.get("volume.tip"));
        if slider.changed() || slider.drag_stopped() {
            model.volume = percent / 100.0;
            actions.push(Action::Volume {
                value: model.volume,
                done: slider.drag_stopped() || !slider.dragged(),
            });
        }
        ui.separator();
        ui.label(texts.get("meter")).on_hover_text(texts.get("meter.tip"));
        meter(ui, model.meter, model.clipping, texts);
        ui.separator();
        let delay = match model.delay_ms {
            Some(ms) if ms.is_finite() => texts.format("delay", &[("ms", &format!("{ms:.1}"))]),
            _ => texts.get("delay.off").to_string(),
        };
        ui.label(delay).on_hover_text(texts.get("delay.tip"));
    });
    // always shown, whatever the output (clarification 2026-11-07)
    ui.label(RichText::new(texts.get("bluetooth")).small().weak());
}

fn meter(ui: &mut Ui, level: f32, clipping: bool, texts: &Texts) {
    let bar = egui::ProgressBar::new(level.clamp(0.0, 1.0)).desired_width(METER_WIDTH);
    ui.add(bar).on_hover_text(texts.get("meter.tip"));
    let clip = RichText::new(texts.get("meter.clip"));
    ui.label(if clipping {
        clip.color(ui.visuals().error_fg_color).strong()
    } else {
        clip.weak()
    });
}

fn tabs(ui: &mut Ui, model: &mut Model, texts: &Texts, actions: &mut Vec<Action>) {
    ui.with_layout(Layout::left_to_right(Align::Center), |ui| {
        for tab in Tab::ALL {
            ui.selectable_value(&mut model.tab, *tab, texts.get(tab.key()));
        }
    });
    ui.separator();
    egui::ScrollArea::vertical().auto_shrink([false, false]).show(ui, |ui| {
        if let Some(chain) = chain_rows::show(ui, &model.chain, model.tab, texts) {
            model.chain = chain;
            actions.push(Action::Chain(Box::new(chain)));
        }
    });
}
