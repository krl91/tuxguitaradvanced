//! The sound list (spec 027, T043, contracts/application.md "The sound list"): ready-made sounds first, "(changed)" after a changed sound,
//! *Restore* only on a changed ready-made sound, *Rename…* and *Delete* only on own sounds; *Export…* and *Import…* always.

use guitarfx_settings::texts::Texts;
use guitarfx_ui::sounds::{self, SoundItem, SoundsModel};

fn item(name: &str, ready_made: bool, changed: bool) -> SoundItem {
    SoundItem {
        name: name.to_string(),
        ready_made,
        changed,
    }
}

fn model(current: &str, unsaved: bool) -> SoundsModel {
    SoundsModel {
        sounds: vec![
            item("Clean", true, false),
            item("Crunch", true, true),
            item("Lead", true, false),
            item("Clean + echo", true, false),
            item("Mine", false, false),
        ],
        current: current.to_string(),
        unsaved,
        dialog: None,
    }
}

#[test]
fn changed_sounds_are_marked() {
    let texts = Texts::english();
    let m = model("Mine", true);
    assert_eq!(sounds::label(&m.sounds[0], &m, &texts), "Clean");
    assert_eq!(sounds::label(&m.sounds[1], &m, &texts), "Crunch (changed)");
    assert_eq!(
        sounds::label(&m.sounds[4], &m, &texts),
        "Mine (changed)",
        "the current own sound with unsaved changes"
    );
    let m = model("Mine", false);
    assert_eq!(sounds::label(&m.sounds[4], &m, &texts), "Mine");
}

#[test]
fn the_menu_offers_only_what_applies() {
    let menu = sounds::menu(&model("Crunch", false));
    assert!(menu.restore && !menu.rename && !menu.delete);
    let menu = sounds::menu(&model("Clean", false));
    assert!(!menu.restore && !menu.rename && !menu.delete);
    let menu = sounds::menu(&model("mine", false));
    assert!(!menu.restore && menu.rename && menu.delete);
    let menu = sounds::menu(&model("Gone", false));
    assert!(!menu.restore && !menu.rename && !menu.delete);
}
