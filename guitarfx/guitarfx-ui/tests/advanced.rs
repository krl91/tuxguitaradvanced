//! The *Advanced…* window (spec 027, T048, FR-025, contracts/application.md "Advanced…"): the categories in the registry's order, at most 8
//! rows each, each row's name, unit, "default X" and tooltip from the registry; a changed value keeps the bounds; *Defaults* asks first;
//! the line "Changes take effect…" only while the sound is on; and the window fits 1024×768 in every language and category.

use egui::{Context, Pos2, RawInput, Rect, Vec2};
use guitarfx_dsp::params::{Params, ENTRIES};
use guitarfx_dsp::ready_made;
use guitarfx_settings::texts::Texts;
use guitarfx_ui::advanced::{self, AdvancedModel};
use guitarfx_ui::sounds::SoundsModel;
use guitarfx_ui::window::{self, Action, Model, Tab};

#[test]
fn the_categories_follow_the_registry_with_at_most_eight_rows() {
    let categories = advanced::categories();
    let mut expected: Vec<&str> = Vec::new();
    for entry in ENTRIES {
        if !expected.contains(&entry.category) {
            expected.push(entry.category);
        }
    }
    assert_eq!(categories, expected);
    assert_eq!(&categories[..5], ["output", "feedback", "feedback-analysis", "devices", "smooth"]);
    let mut total = 0;
    for category in &categories {
        let rows = advanced::rows(category);
        assert!(!rows.is_empty() && rows.len() <= 8, "{category}: {}", rows.len());
        total += rows.len();
    }
    assert_eq!(total, ENTRIES.len(), "every parameter has its row");
}

#[test]
fn a_row_says_its_name_unit_default_and_what_it_changes() {
    let texts = Texts::english();
    let ceiling = ENTRIES.iter().find(|e| e.key == "guitarfx.output.ceiling").unwrap();
    assert_eq!(advanced::name(ceiling, &texts), "Ceiling");
    assert_eq!(advanced::default_text(ceiling, &texts), "default -1 dBFS");
    let tip = advanced::tip(ceiling, &texts);
    assert!(tip.starts_with("The loudest the output may be"), "{tip}");
    assert!(tip.contains("-12") && tip.contains("dBFS") && tip.contains("default -1"), "{tip}");
    let french = Texts::for_language("fr");
    assert_eq!(advanced::default_text(ceiling, &french), "par défaut -1 dBFS");
}

#[test]
fn a_changed_value_is_kept_within_its_bounds() {
    let params = advanced::set(&Params::defaults(), "guitarfx.output.ceiling", -3.0);
    assert_eq!(params.get_key("guitarfx.output.ceiling"), Some(-3.0));
    assert_eq!(
        params.get_key("guitarfx.output.fade-in"),
        Params::defaults().get_key("guitarfx.output.fade-in")
    );
    let params = advanced::set(&params, "guitarfx.output.ceiling", 9.0);
    assert_eq!(params.get_key("guitarfx.output.ceiling"), Some(0.0), "clamped to the maximum");
    let params = advanced::set(&Params::defaults(), "guitarfx.reverb.feedback", 0.9);
    let params = advanced::set(&params, "guitarfx.reverb.size-step", 0.029);
    let sum = params.get_key("guitarfx.reverb.feedback").unwrap() + 10.0 * params.get_key("guitarfx.reverb.size-step").unwrap();
    assert!(sum <= 0.99 + 1e-12, "the combined bounds still hold: {sum}");
}

fn model() -> Model {
    Model {
        inputs: vec![("Rocksmith Guitar Adapter Mono USB Audio Interface (2)".to_string(), 2)],
        outputs: vec!["External Headphones with a long name".to_string()],
        input: Some("Rocksmith Guitar Adapter Mono USB Audio Interface (2)".to_string()),
        channel: Some(2),
        output: Some("External Headphones with a long name".to_string()),
        sound_on: true,
        volume: 0.7,
        meter: 0.8,
        clipping: false,
        delay_ms: Some(9.8),
        message: None,
        dialog: None,
        chain: ready_made::first(),
        tab: Tab::Before,
    }
}

fn run(ctx: &Context, width: f32, model: &mut Model, advanced: &mut AdvancedModel, texts: &Texts) -> (Vec<Action>, egui::FullOutput) {
    let mut actions = Vec::new();
    let mut sounds = SoundsModel::default();
    let input = RawInput {
        screen_rect: Some(Rect::from_min_size(Pos2::ZERO, Vec2::new(width, 768.0))),
        ..Default::default()
    };
    let output = ctx.run(input, |ctx| {
        actions = window::show_all(ctx, model, &mut sounds, advanced, texts);
    });
    (actions, output)
}

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

fn problems(width: f32) -> Vec<String> {
    let texts = longest();
    let mut found = Vec::new();
    for category in advanced::categories() {
        for confirm in [false, true] {
            let ctx = Context::default();
            let mut model = model();
            let mut advanced = AdvancedModel {
                open: true,
                category: category.to_string(),
                confirm,
                params: Params::defaults(),
                sound_on: true,
            };
            let mut output = None;
            for _ in 0..6 {
                output = Some(run(&ctx, width, &mut model, &mut advanced, &texts).1);
            }
            let screen = Rect::from_min_size(Pos2::ZERO, Vec2::new(width, 768.0));
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
                    found.push(format!(
                        "{category} {confirm}: {bounds:?} (clip {:?}) {:?}",
                        clipped.clip_rect, clipped.shape
                    ));
                }
            }
        }
    }
    found
}

#[test]
fn the_window_fits_1024_by_768_in_every_category() {
    let found = problems(1024.0);
    assert!(found.is_empty(), "{} shapes do not fit:\n{}", found.len(), found.join("\n"));
}

#[test]
fn a_screen_of_600_pixels_does_not_fit() {
    assert!(!problems(600.0).is_empty(), "the check must be able to fail");
}

/// The texts of the advanced window that were drawn, from the shapes.
fn drawn(output: &egui::FullOutput) -> String {
    let mut text = String::new();
    for clipped in &output.shapes {
        if let egui::Shape::Text(shape) = &clipped.shape {
            text.push_str(shape.galley.text());
            text.push('\n');
        }
    }
    text
}

#[test]
fn the_takes_effect_line_shows_only_while_the_sound_is_on() {
    let texts = Texts::english();
    let line = texts.get("advanced.takes-effect");
    for sound_on in [true, false] {
        let ctx = Context::default();
        let mut model = model();
        let mut advanced = AdvancedModel {
            open: true,
            category: "output".to_string(),
            confirm: false,
            params: Params::defaults(),
            sound_on,
        };
        let mut output = None;
        for _ in 0..3 {
            output = Some(run(&ctx, 1024.0, &mut model, &mut advanced, &texts).1);
        }
        assert_eq!(drawn(&output.unwrap()).contains(line), sound_on);
    }
}

#[test]
fn defaults_asks_for_a_confirmation_first() {
    let texts = Texts::english();
    let ctx = Context::default();
    let mut model = model();
    let mut advanced = AdvancedModel {
        open: true,
        category: "output".to_string(),
        confirm: true,
        params: Params::defaults(),
        sound_on: false,
    };
    let mut output = None;
    for _ in 0..3 {
        output = Some(run(&ctx, 1024.0, &mut model, &mut advanced, &texts).1);
    }
    let text = drawn(&output.unwrap());
    assert!(text.contains(texts.get("advanced.defaults.confirm")), "{text}");
    assert!(text.contains(texts.get("advanced.defaults.yes")));
}
