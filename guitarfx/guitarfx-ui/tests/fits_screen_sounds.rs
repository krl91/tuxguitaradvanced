//! The main window with its sound list still fits a 1024×768 screen (spec 027, T043), with the longest text of every language, a long sound
//! name, and each of the sound's dialogs; the same check as `fits_screen.rs`, whose window has no sound list.

use egui::{Context, Pos2, RawInput, Rect, Vec2};
use guitarfx_dsp::ready_made;
use guitarfx_settings::texts::Texts;
use guitarfx_ui::sounds::{SoundDialog, SoundItem, SoundsModel};
use guitarfx_ui::window::{self, Model, Tab};

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

fn model() -> Model {
    let long = "Rocksmith Guitar Adapter Mono USB Audio Interface (2)".to_string();
    Model {
        inputs: vec![(long.clone(), 2)],
        outputs: vec!["External Headphones with a long name".to_string()],
        input: Some(long),
        channel: Some(2),
        output: Some("External Headphones with a long name".to_string()),
        sound_on: true,
        volume: 0.7,
        meter: 0.8,
        clipping: true,
        delay_ms: Some(123.4),
        message: Some(longest().get("message.sound-file.not").to_string()),
        dialog: None,
        chain: ready_made::first(),
        tab: Tab::Before,
    }
}

fn sounds(dialog: Option<SoundDialog>) -> SoundsModel {
    let long = "W".repeat(guitarfx_settings::sounds::NAME_LENGTH);
    SoundsModel {
        sounds: vec![
            SoundItem {
                name: "Clean".to_string(),
                ready_made: true,
                changed: true,
            },
            SoundItem {
                name: long.clone(),
                ready_made: false,
                changed: false,
            },
        ],
        current: long,
        unsaved: true,
        dialog,
    }
}

fn problems(width: f32) -> Vec<String> {
    let texts = longest();
    let long = "W".repeat(guitarfx_settings::sounds::NAME_LENGTH);
    let problem = Some(texts.get("sound.name.too-long").to_string());
    let dialogs = [
        None,
        Some(SoundDialog::Save {
            name: long.clone(),
            problem: problem.clone(),
        }),
        Some(SoundDialog::Rename {
            from: long.clone(),
            name: long.clone(),
            problem,
        }),
        Some(SoundDialog::Clash { name: long.clone() }),
    ];
    let screen = Rect::from_min_size(Pos2::ZERO, Vec2::new(width, 768.0));
    let mut found = Vec::new();
    for dialog in dialogs {
        let ctx = Context::default();
        let mut model = model();
        let mut sounds = sounds(dialog.clone());
        let mut output = None;
        for _ in 0..6 {
            let input = RawInput {
                screen_rect: Some(screen),
                ..Default::default()
            };
            output = Some(ctx.run(input, |ctx| {
                window::show_with(ctx, &mut model, &mut sounds, &texts);
            }));
        }
        for clipped in output.unwrap().shapes {
            let bounds = clipped.shape.visual_bounding_rect();
            if !bounds.is_positive() {
                continue;
            }
            if let egui::Shape::Rect(rect) = &clipped.shape {
                if rect.stroke.is_empty() {
                    continue;
                }
            }
            let inside = |r: Rect| r.expand(0.5).contains_rect(bounds);
            if !inside(screen) || !inside(clipped.clip_rect) {
                found.push(format!("{dialog:?}: {bounds:?} (clip {:?}) {:?}", clipped.clip_rect, clipped.shape));
            }
        }
    }
    found
}

#[test]
fn the_window_with_its_sounds_fits_1024_by_768() {
    let found = problems(1024.0);
    assert!(found.is_empty(), "{} shapes do not fit:\n{}", found.len(), found.join("\n"));
}

#[test]
fn a_screen_of_600_pixels_does_not_fit() {
    assert!(!problems(600.0).is_empty(), "the check must be able to fail");
}
