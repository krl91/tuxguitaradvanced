//! Importing a sound file into the sounds (spec 027, T043, contracts/sound-file.md "Import"): a new name is added, the name of an own sound
//! asks the guitarist (replace, or keep both as "<name> 2"), a ready-made name is imported as "<name> (imported)". The same rule as
//! TuxGuitarAdvanced's `AmpSoundImport` (T044).

use guitarfx_dsp::ready_made;
use guitarfx_settings::sounds::{Import, Sounds, NAME_LENGTH};

fn chain() -> guitarfx_dsp::settings::ChainParams {
    ready_made::chain("Lead").unwrap()
}

#[test]
fn a_new_name_is_added() {
    let mut sounds = Sounds::default();
    assert_eq!(sounds.import("  My lead ", chain()), Import::Added("My lead".to_string()));
    assert_eq!(sounds.pick("my lead"), Some(chain()));
}

#[test]
fn an_own_name_asks_and_nothing_changes_until_the_guitarist_answers() {
    let mut sounds = Sounds::default();
    let first = ready_made::chain("Crunch").unwrap();
    sounds.save_as("Mine", first).unwrap();
    assert_eq!(sounds.import("MINE", chain()), Import::Clash("Mine".to_string()));
    assert_eq!(sounds.pick("Mine"), Some(first), "not overwritten without the guitarist's answer");
    assert_eq!(sounds.list().len(), ready_made::ALL.len() + 1);
}

#[test]
fn replace_overwrites_and_keep_both_adds_name_2_then_3() {
    let mut sounds = Sounds::default();
    sounds.save_as("Mine", ready_made::chain("Crunch").unwrap()).unwrap();
    assert_eq!(sounds.import_keep_both("Mine", chain()), "Mine 2");
    assert_eq!(sounds.import_keep_both("Mine", chain()), "Mine 3");
    assert_eq!(sounds.pick("Mine 2"), Some(chain()));
    sounds.import_replace("mine", chain());
    assert_eq!(sounds.pick("Mine"), Some(chain()));
    assert_eq!(sounds.list().len(), ready_made::ALL.len() + 3);
}

#[test]
fn a_ready_made_name_is_imported_as_name_imported() {
    let mut sounds = Sounds::default();
    assert_eq!(sounds.import("crunch", chain()), Import::Added("Crunch (imported)".to_string()));
    assert_eq!(sounds.pick("Crunch"), ready_made::chain("Crunch"), "the ready-made sound is unchanged");
    assert_eq!(sounds.import("Crunch", chain()), Import::Clash("Crunch (imported)".to_string()));
}

#[test]
fn keep_both_shortens_a_long_name_to_fit() {
    let mut sounds = Sounds::default();
    let long = "x".repeat(NAME_LENGTH);
    sounds.save_as(&long, chain()).unwrap();
    let kept = sounds.import_keep_both(&long, chain());
    assert_eq!(kept.chars().count(), NAME_LENGTH);
    assert!(kept.ends_with(" 2"), "{kept}");
}
