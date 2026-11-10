//! The chain text, read and written exactly as spec 022's `ChainSettings.parse` and `toText` (spec 027, T018, contracts/sound-file.md).

use guitarfx_dsp::chain_text::{format, parse};
use guitarfx_dsp::knobs::{Character, Kind};
use guitarfx_dsp::ready_made;
use guitarfx_dsp::settings::{AmpSettings, ChainParams, PedalSettings};

#[test]
fn a_chain_is_written_as_java_writes_it() {
    let chain = ChainParams::new(
        &[PedalSettings::of(Kind::Gate, true, &[-60.0, 150.0])],
        AmpSettings::new(Character::Crunch, 5.0, 5.0, 5.0, 5.0, 6.0, true),
        &[PedalSettings::of(Kind::Echo, true, &[380.0, 3.0, 5.0, 30.0])],
    )
    .unwrap();
    assert_eq!(format(&chain), "gate:on,-60,150|amp:crunch,5,5,5,5,6|cab:on|echo:on,380,3,5,30");
    let chorus = ChainParams::new(
        &[],
        AmpSettings::new(Character::HighGain, 7.5, 0.0, 10.0, 0.25, 0.1, false),
        &[PedalSettings::of(Kind::Chorus, false, &[0.8, 4.0, 40.0])],
    )
    .unwrap();
    assert_eq!(format(&chorus), "amp:highgain,7.5,0,10,0.25,0.1|cab:off|chorus:off,0.8,4,40");
}

#[test]
fn a_text_is_read_and_written_back_the_same() {
    for text in [
        "gate:on,-60,150|amp:crunch,5,5,5,5,6|cab:on|echo:on,380,3,5,30",
        "gate:on,-60,150|compressor:on,5,10,5|amp:highgain,7,5,6,6,5|cab:on|echo:on,380,3,5,30|reverb:on,5,5,25",
        "amp:clean,5,5,5,5,5|cab:off",
        "overdrive:off,5,5,5|distortion:on,6,5,5|amp:clean,0,10,0,10,0|cab:on|chorus:on,0.1,10,100|reverb:off,0,10,0",
    ] {
        let chain = parse(text).unwrap_or_else(|| panic!("{text}"));
        assert_eq!(format(&chain), text);
    }
}

#[test]
fn spaces_around_names_and_numbers_are_allowed_as_in_java() {
    let chain = parse("  gate : on , -60 , 150 | amp: clean ,5,5,5,5,5|cab: on ").unwrap();
    assert_eq!(format(&chain), "gate:on,-60,150|amp:clean,5,5,5,5,5|cab:on");
}

#[test]
fn a_value_out_of_range_is_clamped() {
    let chain = parse("gate:on,-200,5000|amp:crunch,12,-1,5,5,5|cab:on").unwrap();
    assert_eq!(format(&chain), "gate:on,-90,1000|amp:crunch,10,0,5,5,5|cab:on");
}

#[test]
fn a_malformed_text_is_refused_whole() {
    for text in [
        "",
        "   ",
        "amp:clean,5,5,5,5|cab:on",
        "amp:clean,5,5,5,5,5,5|cab:on",
        "amp:warm,5,5,5,5,5|cab:on",
        "amp:clean,5,5,5,5,5",
        "cab:on|amp:clean,5,5,5,5,5",
        "amp:clean,5,5,5,5,5|cab:maybe",
        "amp:clean,5,5,5,5,5|cab:on|cab:on",
        "amp:clean,5,5,5,5,5|amp:clean,5,5,5,5,5|cab:on",
        "gate:on,-60|amp:clean,5,5,5,5,5|cab:on",
        "gate:yes,-60,150|amp:clean,5,5,5,5,5|cab:on",
        "gate:on,-60,abc|amp:clean,5,5,5,5,5|cab:on",
        "gate:on,-60,150|gate:on,-60,150|amp:clean,5,5,5,5,5|cab:on",
        "echo:on,380,3,5,30|amp:clean,5,5,5,5,5|cab:on",
        "amp:clean,5,5,5,5,5|cab:on|gate:on,-60,150",
        "amp:clean,5,5,5,5,5|echo:on,380,3,5,30|cab:on",
        "fuzz:on,5|amp:clean,5,5,5,5,5|cab:on",
        ":on|amp:clean,5,5,5,5,5|cab:on",
        "amp:clean,5,5,5,5,5|cab:on|",
    ] {
        assert!(parse(text).is_none(), "{text:?} is refused");
    }
}

#[test]
fn the_ready_made_sounds_are_javas_and_round_trip() {
    assert_eq!(
        ready_made::ALL.iter().map(|(name, _)| *name).collect::<Vec<_>>(),
        ["Clean", "Crunch", "Lead", "Clean echo"]
    );
    for (name, text) in ready_made::ALL {
        let chain = ready_made::chain(name).unwrap();
        assert_eq!(format(&chain), *text);
    }
    assert!(ready_made::chain("clean ECHO").is_some(), "the name's case is ignored");
    assert!(ready_made::chain("Metal").is_none());
    let Ok(folder) = std::env::var("GUITARFX_REFERENCE_DIR") else {
        println!("skipped against Java: no Java references");
        return;
    };
    let index = std::fs::read_to_string(std::path::Path::new(&folder).join("index.properties")).expect("the index");
    let mut seen = 0;
    for line in index.lines() {
        let Some((key, value)) = line.split_once('=') else { continue };
        let Some(id) = key.strip_prefix("ready.").and_then(|k| k.strip_suffix(".chain")) else {
            continue;
        };
        let name_key = format!("ready.{id}.name=");
        let name = index.lines().find_map(|l| l.strip_prefix(&name_key)).unwrap();
        let ours = ready_made::ALL.iter().find(|(n, _)| *n == name).unwrap_or_else(|| panic!("{name}"));
        assert_eq!(ours.1, value, "{name} is Java's text");
        assert_eq!(format(&parse(value).unwrap()), value, "Java's {name} round-trips");
        seen += 1;
    }
    assert_eq!(seen, 4);
}
