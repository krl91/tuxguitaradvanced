//! The plugin's state in the host's project (spec 027, T054, contracts/plugin.md "State", US5 scenario 2): `state.format=1` and
//! `chain=<text>`, saved and restored; a newer format or an unreadable state gives the default sound, the newer one with a line in the
//! editor; the external parameters come from GuitarFX's `settings.properties` at load (defaults when it is missing) and never from the
//! state.

use guitarfx_dsp::chain_text;
use guitarfx_dsp::params::Params;
use guitarfx_dsp::ready_made;
use guitarfx_plugin::state::{self, Restored};
use std::path::PathBuf;

fn folder(name: &str) -> PathBuf {
    let path = std::env::temp_dir().join(format!("guitarfx-plugin-state-test-{}-{name}", std::process::id()));
    let _ = std::fs::remove_dir_all(&path);
    std::fs::create_dir_all(&path).unwrap();
    path
}

#[test]
fn the_chain_is_saved_and_restored() {
    let chain = chain_text::parse("gate:on,-60,150|compressor:on,5,10,5|amp:crunch,5,5,5,5,6|cab:on|echo:on,450,4,4,35").unwrap();
    let text = state::text(&chain);
    // written as Java writes properties, the colons escaped
    assert_eq!(
        guitarfx_settings::properties::parse(&text).unwrap(),
        vec![
            ("state.format".to_string(), "1".to_string()),
            ("chain".to_string(), chain_text::format(&chain))
        ]
    );
    assert_eq!(state::read(&text), Restored::Chain(chain));
}

#[test]
fn the_state_holds_nothing_else() {
    let text = state::text(&ready_made::chain("Lead").unwrap());
    let keys: Vec<&str> = text.lines().filter_map(|l| l.split_once('=')).map(|(k, _)| k).collect();
    assert_eq!(keys, vec!["state.format", "chain"], "no external parameter in the project");
}

#[test]
fn a_newer_format_gives_the_default_sound_and_says_so() {
    let restored = state::read("state.format=2\nchain=amp:clean,5,5,5,5,5|cab:on\n");
    assert_eq!(restored, Restored::Newer);
    assert_eq!(restored.chain(), ready_made::first());
    assert_eq!(restored.message_key(), Some("plugin.state.newer"));
}

#[test]
fn a_new_instance_or_an_unreadable_state_gives_the_default_sound_quietly() {
    for text in ["", "chain=amp:clean", "state.format=1\nchain=nothing\n", "state.format=x\n"] {
        let restored = state::read(text);
        assert_eq!(restored, Restored::Default, "{text:?}");
        assert_eq!(restored.chain(), ready_made::first());
        assert_eq!(restored.message_key(), None);
    }
}

#[test]
fn the_external_parameters_come_from_the_settings_file() {
    let folder = folder("params");
    assert_eq!(state::external_params(Some(&folder)), Params::defaults(), "no file: the defaults");
    std::fs::write(
        folder.join("settings.properties"),
        "settings.format=1\nguitarfx.devices.block=256\nguitarfx.gate.floor=-90\n",
    )
    .unwrap();
    let params = state::external_params(Some(&folder));
    assert_eq!(params.get_key("guitarfx.devices.block"), Some(256.0));
    assert_eq!(params.get_key("guitarfx.gate.floor"), Some(-90.0));
    assert_eq!(std::fs::read_dir(&folder).unwrap().count(), 1, "the plugin writes nothing there");
    assert_eq!(state::external_params(None), Params::defaults(), "no settings folder: the defaults");
}
