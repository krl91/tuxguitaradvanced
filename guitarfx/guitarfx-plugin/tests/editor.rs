//! The plugin editor's logic (spec 027, T055, contracts/plugin.md "Editor"): the sounds are the application's `sounds.properties`, read when
//! the editor opens and written only when the sound list changes them; a chain change is played and kept in the project's state, not in
//! the sounds file; the message line says why the sound was muted or lightened, and when the project's state came from a newer GuitarFX.

use guitarfx_dsp::chain_text;
use guitarfx_dsp::ready_made;
use guitarfx_plugin::editor::{safety_key, EditorLogic};
use guitarfx_plugin::processor::State;
use guitarfx_settings::texts::Texts;
use guitarfx_ui::window::Action;
use std::path::PathBuf;

fn folder(name: &str) -> PathBuf {
    let path = std::env::temp_dir().join(format!("guitarfx-plugin-editor-test-{}-{name}", std::process::id()));
    let _ = std::fs::remove_dir_all(&path);
    std::fs::create_dir_all(&path).unwrap();
    path
}

#[test]
fn the_current_sound_is_the_one_whose_chain_is_played() {
    let folder = folder("current");
    let lead = ready_made::chain("Lead").unwrap();
    let editor = EditorLogic::open(Some(&folder), lead, false, Texts::english());
    assert_eq!(editor.sound_list().current, "Lead");
    assert_eq!(editor.model().chain, lead);
    assert!(editor.model().message.is_none());
    let other = chain_text::parse("amp:clean,1,2,3,4,5|cab:off").unwrap();
    let editor = EditorLogic::open(Some(&folder), other, false, Texts::english());
    assert_eq!(editor.sound_list().current, "", "no sound has that chain");
}

#[test]
fn own_sounds_come_from_the_applications_file() {
    let folder = folder("own");
    let chain = chain_text::parse("amp:highgain,5,5,5,5,6|cab:on").unwrap();
    std::fs::write(
        folder.join("sounds.properties"),
        format!("sounds.format=1\nsound.0.name=Mine\nsound.0.chain={}\n", chain_text::format(&chain)),
    )
    .unwrap();
    let editor = EditorLogic::open(Some(&folder), chain, false, Texts::english());
    assert!(editor.sound_list().sounds.iter().any(|s| s.name == "Mine" && !s.ready_made));
    assert_eq!(editor.sound_list().current, "Mine");
}

#[test]
fn a_chain_change_is_played_and_not_written_to_the_sounds() {
    let folder = folder("change");
    let mut editor = EditorLogic::open(Some(&folder), ready_made::first(), false, Texts::english());
    let lead = ready_made::chain("Lead").unwrap();
    assert_eq!(editor.act(Action::Chain(Box::new(lead))), Some(lead));
    assert_eq!(editor.model().chain, lead);
    assert!(!folder.join("sounds.properties").exists(), "nothing written for a knob");
    assert_eq!(editor.act(Action::PickSound("Crunch".to_string())), ready_made::chain("Crunch"));
    assert_eq!(editor.sound_list().current, "Crunch");
    assert!(!folder.join("sounds.properties").exists(), "nothing written for a pick");
}

#[test]
fn saving_a_sound_writes_the_sounds_file() {
    let folder = folder("save");
    let mut editor = EditorLogic::open(Some(&folder), ready_made::chain("Lead").unwrap(), false, Texts::english());
    assert_eq!(editor.act(Action::SaveSound("My lead".to_string())), None);
    assert_eq!(editor.sound_list().current, "My lead");
    let text = std::fs::read_to_string(folder.join("sounds.properties")).unwrap();
    assert!(text.contains("sound.0.name=My lead"), "{text}");
    assert!(!folder.join("settings.properties").exists(), "the application's settings are not touched");
    // a bad name: the prompt says what is wrong, nothing is written
    editor.act(Action::SaveSound("Clean".to_string()));
    assert_eq!(std::fs::read_to_string(folder.join("sounds.properties")).unwrap(), text);
    editor.act(Action::RenameSound {
        from: "My lead".to_string(),
        to: "Solo".to_string(),
    });
    assert!(std::fs::read_to_string(folder.join("sounds.properties"))
        .unwrap()
        .contains("sound.0.name=Solo"));
    assert_eq!(editor.sound_list().current, "Solo");
    editor.act(Action::DeleteSound("Solo".to_string()));
    assert!(!std::fs::read_to_string(folder.join("sounds.properties")).unwrap().contains("Solo"));
}

#[test]
fn a_newer_state_is_said_in_the_message_line() {
    let texts = Texts::english();
    let editor = EditorLogic::open(None, ready_made::first(), true, texts.clone());
    assert_eq!(editor.model().message.as_deref(), Some(texts.get("plugin.state.newer")));
    assert_eq!(editor.sound_list().current, ready_made::ALL[0].0);
}

#[test]
fn the_safety_messages_are_the_plugins() {
    assert_eq!(safety_key(State::On, 0), None);
    assert_eq!(safety_key(State::Starting, 0), None);
    assert_eq!(safety_key(State::On, 1), Some("message.lighter.1"));
    assert_eq!(safety_key(State::On, 5), Some("message.lighter.5"));
    assert_eq!(safety_key(State::MutedFeedback, 0), Some("plugin.muted.feedback"));
    assert_eq!(safety_key(State::Stopping, 5), Some("message.lighter.5"));
    assert_eq!(safety_key(State::MutedOverload, 5), Some("plugin.muted.overload"));
    let texts = Texts::english();
    for state in [State::MutedFeedback, State::MutedOverload] {
        let key = safety_key(state, 0).unwrap();
        assert_ne!(texts.get(key), key, "{key} has a text");
    }
    for step in 1..=guitarfx_dsp::chain::Chain::lightest() {
        let key = safety_key(State::On, step).unwrap();
        assert_ne!(texts.get(key), key, "{key} has a text");
    }
}
