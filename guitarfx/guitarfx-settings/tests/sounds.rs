//! GuitarFX's sounds in `sounds.properties` (spec 027, T039, contracts/settings-files.md, with the rules of `AmpSoundStore`): the ready-made
//! sounds first, then the own sounds by name; names of 1 to 40 characters, unique, never a ready-made sound's; a changed ready-made sound
//! kept until *Restore*; an own sound edited shows as changed until *Save*; a newer or damaged file handled as `settings.properties`.

use guitarfx_dsp::chain_text;
use guitarfx_dsp::ready_made;
use guitarfx_dsp::settings::ChainParams;
use guitarfx_settings::settings::Notice;
use guitarfx_settings::sounds::{NameProblem, Sounds};
use std::path::{Path, PathBuf};

fn folder(name: &str) -> PathBuf {
    let path = std::env::temp_dir().join(format!("guitarfx-sounds-test-{}-{name}", std::process::id()));
    let _ = std::fs::remove_dir_all(&path);
    std::fs::create_dir_all(&path).unwrap();
    path
}

fn file(folder: &Path) -> PathBuf {
    folder.join("sounds.properties")
}

fn lead() -> ChainParams {
    ready_made::chain("Lead").unwrap()
}

fn names(sounds: &Sounds) -> Vec<String> {
    sounds.list().into_iter().map(|s| s.name).collect()
}

#[test]
fn the_ready_made_sounds_come_first_then_the_own_sounds_by_name() {
    let mut sounds = Sounds::default();
    assert_eq!(names(&sounds), vec!["Clean", "Crunch", "Lead", "Clean echo"]);
    sounds.save_as("zebra", lead()).unwrap();
    sounds.save_as("Alpha", lead()).unwrap();
    sounds.save_as("beta", lead()).unwrap();
    assert_eq!(names(&sounds), vec!["Clean", "Crunch", "Lead", "Clean echo", "Alpha", "beta", "zebra"]);
    let list = sounds.list();
    assert!(list[0].ready_made && !list[4].ready_made);
}

#[test]
fn names_follow_the_rules() {
    let mut sounds = Sounds::default();
    sounds.save_as("My lead", lead()).unwrap();
    assert_eq!(sounds.name_problem("   ", None), Some(NameProblem::Empty));
    assert_eq!(sounds.name_problem(&"x".repeat(41), None), Some(NameProblem::TooLong));
    assert_eq!(sounds.name_problem(&"é".repeat(40), None), None, "40 characters, not bytes");
    assert_eq!(sounds.name_problem("crunch", None), Some(NameProblem::ReadyMade));
    assert_eq!(sounds.name_problem("my LEAD", None), Some(NameProblem::Taken));
    assert_eq!(sounds.name_problem("my lead", Some("My lead")), None, "renaming a sound to itself");
    assert_eq!(sounds.save_as("Clean", lead()), Err(NameProblem::ReadyMade));
    assert_eq!(sounds.save_as("a\nb", lead()), Err(NameProblem::Empty), "no line break");
    // saving under an own sound's name replaces it
    sounds.save_as(" my lead ", ready_made::first()).unwrap();
    assert_eq!(sounds.list().iter().filter(|s| !s.ready_made).count(), 1);
    assert_eq!(sounds.sound("MY LEAD").unwrap().chain, ready_made::first());
}

#[test]
fn rename_and_delete_only_own_sounds() {
    let mut sounds = Sounds::default();
    sounds.save_as("One", lead()).unwrap();
    sounds.save_as("Two", lead()).unwrap();
    assert_eq!(sounds.rename("One", "Two"), Err(NameProblem::Taken));
    assert_eq!(
        sounds.rename("Crunch", "Mine"),
        Err(NameProblem::ReadyMade),
        "a ready-made sound is not renamed"
    );
    sounds.rename("One", "Three").unwrap();
    assert!(sounds.sound("One").is_none() && sounds.sound("Three").is_some());
    assert!(!sounds.delete("Crunch"));
    assert!(sounds.delete("two"));
    assert_eq!(names(&sounds).len(), 5);
}

#[test]
fn a_changed_ready_made_sound_is_kept_until_restored() {
    let mut sounds = Sounds::default();
    let changed = lead().with_amp(lead().amp.with_knob(0, 1.0));
    sounds.keep_current("Lead", &changed);
    let sound = sounds.sound("Lead").unwrap();
    assert!(sound.changed && sound.chain == changed);
    assert!(!sounds.is_changed("Lead", &changed), "the current chain is the sound's");
    assert_eq!(sounds.restore("Lead"), Some(lead()));
    assert!(!sounds.sound("Lead").unwrap().changed);
    sounds.keep_current("Lead", &lead());
    assert!(!sounds.sound("Lead").unwrap().changed, "back to the shipped chain: not changed");
    assert_eq!(sounds.restore("Mine"), None);
}

#[test]
fn an_own_sound_edited_shows_as_changed_until_saved() {
    let mut sounds = Sounds::default();
    sounds.save_as("Mine", lead()).unwrap();
    let edited = lead().with_amp(lead().amp.with_knob(1, 9.0));
    sounds.keep_current("Mine", &edited);
    assert!(sounds.is_changed("Mine", &edited));
    assert_eq!(sounds.sound("Mine").unwrap().chain, lead(), "not saved yet");
    // another sound picked: its chain is played, the edit is dropped
    assert_eq!(sounds.pick("Crunch"), ready_made::chain("Crunch"));
    sounds.save_as("Mine", edited).unwrap();
    assert!(!sounds.is_changed("Mine", &edited));
}

#[test]
fn the_file_is_written_and_read_back() {
    let folder = folder("round");
    let mut sounds = Sounds::default();
    sounds.save_as("My lead", lead()).unwrap();
    sounds.save_as("Odd = name: \\ here", ready_made::first()).unwrap();
    sounds.keep_current("Crunch", &lead());
    sounds.save(&folder).unwrap();
    let text = std::fs::read_to_string(file(&folder)).unwrap();
    assert!(text.starts_with("sounds.format=1\n"), "{text}");
    assert!(
        text.contains(&format!("ready.Crunch.chain={}\n", chain_text::format(&lead()).replace(':', "\\:"))),
        "{text}"
    );
    assert!(text.contains("sound.0.name=") && text.contains("sound.1.chain="), "{text}");
    let loaded = Sounds::load(&folder);
    assert!(loaded.notice.is_none());
    assert_eq!(names(&loaded.sounds), names(&sounds));
    assert_eq!(loaded.sounds.sound("Crunch").unwrap().chain, lead());
    assert_eq!(loaded.sounds.sound("odd = name: \\ here").unwrap().chain, ready_made::first());
}

#[test]
fn a_newer_file_is_left_untouched() {
    let folder = folder("newer");
    let text = "sounds.format=2\nsound.0.name=Mine\nsound.0.chain=amp:clean,5,5,5,5,5|cab:on\n";
    std::fs::write(file(&folder), text).unwrap();
    let loaded = Sounds::load(&folder);
    assert_eq!(loaded.notice, Some(Notice::Newer));
    assert!(!loaded.writable);
    assert_eq!(names(&loaded.sounds).len(), 4);
    assert_eq!(std::fs::read_to_string(file(&folder)).unwrap(), text);
}

#[test]
fn a_damaged_file_is_copied_aside_and_its_readable_sounds_kept() {
    let folder = folder("damaged");
    let text = "sounds.format=1\nsound.0.name=Good\nsound.0.chain=amp:crunch,5,5,5,5,5|cab:on\nsound.1.name=Bad\nsound.1.chain=amp:nothing\nready.Lead.chain=broken\n";
    std::fs::write(file(&folder), text).unwrap();
    let loaded = Sounds::load(&folder);
    let Some(Notice::Damaged(copy)) = &loaded.notice else {
        panic!("{:?}", loaded.notice)
    };
    assert!(copy.file_name().unwrap().to_string_lossy().starts_with("sounds.properties.damaged-"));
    assert_eq!(std::fs::read_to_string(copy).unwrap(), text);
    assert!(loaded.sounds.sound("Good").is_some());
    assert!(loaded.sounds.sound("Bad").is_none(), "a sound whose chain cannot be read is left out");
    assert_eq!(loaded.sounds.sound("Lead").unwrap().chain, lead(), "the shipped chain");
}
