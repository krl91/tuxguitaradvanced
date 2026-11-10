//! The plugin's editor (spec 027, T055, contracts/plugin.md "Editor"): the sound list and the chain's three tabs, with no devices, no
//! volume, no *Sound on* and no *Advanced…*; it fits the editor's size with the longest text of every language and each sound dialog.

use egui::{Context, Pos2, RawInput, Rect, Vec2};
use guitarfx_dsp::ready_made;
use guitarfx_settings::texts::Texts;
use guitarfx_ui::sounds::{SoundDialog, SoundItem, SoundsModel};
use guitarfx_ui::window::{self, Model, Tab, PLUGIN_SIZE};

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

fn model(texts: &Texts) -> Model {
    Model {
        inputs: Vec::new(),
        outputs: Vec::new(),
        input: None,
        channel: None,
        output: None,
        sound_on: true,
        volume: 0.7,
        meter: 0.0,
        clipping: false,
        delay_ms: None,
        message: Some(texts.get("plugin.muted.overload").to_string()),
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

/// The shapes drawn, for every dialog and tab, on a screen of that size.
fn frames(size: [f32; 2], texts: &Texts) -> Vec<(String, egui::FullOutput, Rect)> {
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
    let screen = Rect::from_min_size(Pos2::ZERO, Vec2::from(size));
    let mut all = Vec::new();
    for dialog in dialogs {
        for tab in Tab::ALL {
            let ctx = Context::default();
            let mut model = model(texts);
            model.tab = *tab;
            let mut list = sounds(dialog.clone());
            let mut output = None;
            for _ in 0..6 {
                let input = RawInput {
                    screen_rect: Some(screen),
                    ..Default::default()
                };
                output = Some(ctx.run(input, |ctx| {
                    window::show_plugin(ctx, &mut model, &mut list, texts);
                }));
            }
            all.push((format!("{dialog:?} {tab:?}"), output.unwrap(), screen));
        }
    }
    all
}

fn problems(size: [f32; 2]) -> Vec<String> {
    let mut found = Vec::new();
    for (name, output, screen) in frames(size, &longest()) {
        for clipped in output.shapes {
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
                found.push(format!("{name}: {bounds:?} (clip {:?}) {:?}", clipped.clip_rect, clipped.shape));
            }
        }
    }
    found
}

fn texts_shown(output: &egui::FullOutput) -> Vec<String> {
    output
        .shapes
        .iter()
        .filter_map(|c| match &c.shape {
            egui::Shape::Text(text) => Some(text.galley.text().to_string()),
            _ => None,
        })
        .collect()
}

#[test]
fn the_editor_fits_its_size() {
    let found = problems(PLUGIN_SIZE);
    assert!(found.is_empty(), "{} shapes do not fit:\n{}", found.len(), found.join("\n"));
}

#[test]
fn a_smaller_editor_does_not_fit() {
    assert!(!problems([500.0, PLUGIN_SIZE[1]]).is_empty(), "the check must be able to fail");
}

#[test]
fn the_editor_has_the_sounds_and_the_tabs_and_nothing_of_the_devices() {
    let texts = Texts::english();
    for (name, output, _) in frames(PLUGIN_SIZE, &texts).into_iter().take(Tab::ALL.len()) {
        let shown = texts_shown(&output);
        for present in ["sound.list", "sound.save", "tab.before", "tab.amp", "tab.after", "plugin.muted.overload"] {
            assert!(shown.iter().any(|t| t == texts.get(present)), "{name}: {present} missing from {shown:?}");
        }
        for absent in [
            "sound.on",
            "volume",
            "advanced.open",
            "meter",
            "devices.refresh",
            "bluetooth",
            "delay.off",
        ] {
            assert!(!shown.iter().any(|t| t == texts.get(absent)), "{name}: {absent} shown");
        }
    }
}
