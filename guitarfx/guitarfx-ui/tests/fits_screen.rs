//! The main window fits a 1024×768 screen (spec 027, T029, contracts/application.md): its layout is run headless with the longest text of
//! every language for each key, every pedal in the chain, each tab in turn, a message and each dialog, and every shape drawn must lie inside
//! the screen and inside its clip rectangle (a shape cut by its clip is a widget that did not fit; a filled background is not a widget). A
//! screen of 600 pixels must fail.

use egui::{Context, Pos2, RawInput, Rect, Vec2};
use guitarfx_dsp::knobs::{Kind, Section};
use guitarfx_dsp::settings::{AmpSettings, ChainParams, PedalSettings};
use guitarfx_settings::texts::Texts;
use guitarfx_ui::window::{self, Dialog, Model, Tab};

/// For each key, the longest text among the languages, made a little longer still.
fn longest() -> Texts {
    let languages = Texts::languages();
    let mut texts = Texts::english();
    for key in Texts::keys() {
        let text = languages
            .iter()
            .map(|t| t.get(key).to_string())
            .max_by_key(|t| t.chars().count())
            .unwrap();
        texts = texts.with(key, text);
    }
    texts
}

fn every_pedal() -> ChainParams {
    let of = |section: Section| -> Vec<PedalSettings> {
        Kind::ALL
            .iter()
            .filter(|k| k.section() == section)
            .map(|k| PedalSettings::defaults(*k))
            .collect()
    };
    ChainParams::new(&of(Section::Before), AmpSettings::default(), &of(Section::After)).unwrap()
}

fn model(tab: Tab, dialog: Option<Dialog>) -> Model {
    let long = "Rocksmith Guitar Adapter Mono USB Audio Interface (2)".to_string();
    Model {
        inputs: vec![(long.clone(), 2), ("MacBook Pro Microphone".to_string(), 1)],
        outputs: vec!["External Headphones with a long name".to_string(), "MacBook Pro Speakers".to_string()],
        input: Some(long),
        channel: Some(2),
        output: Some("A device that is not connected anymore (USB)".to_string()),
        sound_on: true,
        volume: 0.7,
        meter: 0.8,
        clipping: true,
        delay_ms: Some(123.4),
        message: Some(longest().get("message.overload").to_string()),
        dialog,
        chain: every_pedal(),
        tab,
    }
}

/// The shapes outside the screen or cut by their clip, after a few frames.
fn problems(width: f32, height: f32) -> Vec<String> {
    let texts = longest();
    let screen = Rect::from_min_size(Pos2::ZERO, Vec2::new(width, height));
    let dialogs = [
        None,
        Some(Dialog::BuiltInMicrophone),
        Some(Dialog::Message("message.permission".to_string())),
        Some(Dialog::NewerSettings),
    ];
    let mut found = Vec::new();
    for tab in Tab::ALL {
        for dialog in &dialogs {
            let ctx = Context::default();
            let mut model = model(*tab, dialog.clone());
            let mut output = None;
            for _ in 0..6 {
                let input = RawInput {
                    screen_rect: Some(screen),
                    ..Default::default()
                };
                output = Some(ctx.run(input, |ctx| {
                    window::show(ctx, &mut model, &texts);
                }));
            }
            for clipped in output.unwrap().shapes {
                let bounds = clipped.shape.visual_bounding_rect();
                if !bounds.is_positive() {
                    continue;
                }
                // a panel's background may reach under the next panel; a widget cut by its clip still shows by its text or its outline
                if let egui::Shape::Rect(rect) = &clipped.shape {
                    if rect.stroke.is_empty() {
                        continue;
                    }
                }
                // half a pixel of anti-aliasing
                let inside = |r: Rect| r.expand(0.5).contains_rect(bounds);
                if !inside(screen) || !inside(clipped.clip_rect) {
                    found.push(format!(
                        "{tab:?} {dialog:?}: {bounds:?} (clip {:?}) {:?}",
                        clipped.clip_rect, clipped.shape
                    ));
                }
            }
        }
    }
    found
}

#[test]
fn the_window_fits_1024_by_768() {
    let found = problems(1024.0, 768.0);
    assert!(found.is_empty(), "{} shapes do not fit:\n{}", found.len(), found.join("\n"));
}

#[test]
fn a_screen_of_600_pixels_does_not_fit() {
    assert!(!problems(600.0, 768.0).is_empty(), "the check must be able to fail");
}
