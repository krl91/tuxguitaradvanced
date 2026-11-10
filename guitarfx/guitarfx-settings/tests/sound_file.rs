//! The sound file `.guitarfx` (spec 027, T040, T042, contracts/sound-file.md), on the example files shared with TuxGuitarAdvanced's
//! `SoundFileTest`: format 1 only, a newer format refused as "made by a newer version", a missing field or a malformed chain refused as "not
//! a sound file", the name trimmed to 1 to 40 characters, and the writing order comment, `format`, `name`, `chain`. With the Java references
//! (`GUITARFX_REFERENCE_DIR`), the files Java wrote are read here, and the files written here are byte-identical to Java's (SC-011).

use guitarfx_dsp::chain_text;
use guitarfx_dsp::knobs::Kind;
use guitarfx_dsp::ready_made;
use guitarfx_settings::sound_file::{self, ReadError};
use std::path::{Path, PathBuf};

fn example(name: &str) -> PathBuf {
    Path::new(env!("CARGO_MANIFEST_DIR")).join("tests/sounds").join(name)
}

fn temp(name: &str) -> PathBuf {
    let path = std::env::temp_dir().join(format!("guitarfx-sound-file-test-{}-{name}", std::process::id()));
    let _ = std::fs::remove_dir_all(&path);
    std::fs::create_dir_all(&path).unwrap();
    path
}

#[test]
fn a_valid_file_is_read() {
    let sound = sound_file::read_file(&example("valid.guitarfx")).unwrap();
    assert_eq!(sound.name, "Lead with echo");
    assert_eq!(
        chain_text::format(&sound.chain),
        "gate:on,-60,150|compressor:on,5,10,5|amp:highgain,7,5,6,6,5|cab:on|echo:on,380,3,5,30|reverb:on,5,5,25"
    );
}

#[test]
fn every_pedal_and_an_escaped_name_are_read() {
    let sound = sound_file::read_file(&example("every-pedal.guitarfx")).unwrap();
    assert_eq!(sound.name, "Every pedal: a = b \\ été");
    for kind in Kind::ALL {
        assert!(sound.chain.pedal(kind).is_some(), "{kind:?}");
    }
    assert!(!sound.chain.pedal(Kind::Compressor).unwrap().enabled);
    assert!(!sound.chain.amp.cabinet);
}

#[test]
fn a_newer_format_and_a_malformed_chain_are_refused() {
    assert_eq!(sound_file::read_file(&example("format2.guitarfx")), Err(ReadError::Newer));
    assert_eq!(sound_file::read_file(&example("malformed.guitarfx")), Err(ReadError::NotASoundFile));
    for text in [
        "",
        "format=1\nname=x\n",
        "format=1\nchain=amp:clean,5,5,5,5,5|cab:on\n",
        "format=one\nname=x\nchain=amp:clean,5,5,5,5,5|cab:on\n",
        "name=x\nchain=amp:clean,5,5,5,5,5|cab:on\n",
        "format=0\nname=x\nchain=amp:clean,5,5,5,5,5|cab:on\n",
        "format=1\nname=   \nchain=amp:clean,5,5,5,5,5|cab:on\n",
    ] {
        assert_eq!(sound_file::read(text), Err(ReadError::NotASoundFile), "{text:?}");
    }
    assert_eq!(sound_file::read_file(Path::new("/no/such/file.guitarfx")), Err(ReadError::NotASoundFile));
}

#[test]
fn names_are_trimmed_and_at_most_40_characters() {
    let chain = "chain=amp:clean,5,5,5,5,5|cab:on\n";
    assert_eq!(sound_file::read(&format!("format=1\nname=  Mine  \n{chain}")).unwrap().name, "Mine");
    assert!(sound_file::read(&format!("format=1\nname={}\n{chain}", "é".repeat(40))).is_ok());
    assert_eq!(
        sound_file::read(&format!("format=1\nname={}\n{chain}", "x".repeat(41))),
        Err(ReadError::NotASoundFile)
    );
}

#[test]
fn crlf_comments_unknown_keys_and_clamping() {
    let text =
        "# GuitarFX sound\r\n! another comment\r\nformat=1\r\nname=Loud\r\nauthor=someone\r\nchain=gate:on,-500,150|amp:clean,50,5,5,5,5|cab:on\r\n";
    let sound = sound_file::read(text).unwrap();
    assert_eq!(sound.chain.pedal(Kind::Gate).unwrap().value(0), -90.0, "clamped");
    assert_eq!(sound.chain.amp.gain, 10.0);
}

#[test]
fn the_writing_order_is_comment_format_name_chain() {
    let folder = temp("write");
    let path = folder.join("Every pedal.guitarfx");
    let read = sound_file::read_file(&example("every-pedal.guitarfx")).unwrap();
    sound_file::write(&path, &read.name, &read.chain).unwrap();
    assert_eq!(std::fs::read(&path).unwrap(), std::fs::read(example("every-pedal.guitarfx")).unwrap());
    assert_eq!(std::fs::read_dir(&folder).unwrap().count(), 1, "no temporary file left");
    let valid = sound_file::read_file(&example("valid.guitarfx")).unwrap();
    assert_eq!(
        sound_file::text(&valid.name, &valid.chain),
        std::fs::read_to_string(example("valid.guitarfx")).unwrap()
    );
}

/// The five sounds of SC-011: the four ready-made ones and the every-pedal one, by file name.
fn round_trip_sounds() -> Vec<(String, String, guitarfx_dsp::settings::ChainParams)> {
    let mut sounds: Vec<_> = ready_made::ALL
        .iter()
        .map(|(name, _)| {
            (
                format!("{}.guitarfx", name.to_lowercase().replace(' ', "-")),
                name.to_string(),
                ready_made::chain(name).unwrap(),
            )
        })
        .collect();
    let every = sound_file::read_file(&example("every-pedal.guitarfx")).unwrap();
    sounds.push(("every-pedal.guitarfx".to_string(), every.name, every.chain));
    sounds
}

#[test]
fn java_and_rust_read_and_write_the_same_files() {
    let Some(folder) = std::env::var("GUITARFX_REFERENCE_DIR")
        .ok()
        .filter(|v| !v.trim().is_empty())
        .map(PathBuf::from)
    else {
        println!("skipped: no Java references");
        return;
    };
    let java = folder.join("sounds");
    let ours = temp("round-trip");
    for (file, name, chain) in round_trip_sounds() {
        // Java → Rust
        let read = sound_file::read_file(&java.join(&file)).unwrap_or_else(|e| panic!("{file}: {e:?}"));
        assert_eq!((read.name.as_str(), read.chain), (name.as_str(), chain), "{file}");
        // Rust's file is Java's, byte for byte: what Java reads, it reads from Rust too (Rust → Java)
        sound_file::write(&ours.join(&file), &name, &chain).unwrap();
        assert_eq!(
            std::fs::read(ours.join(&file)).unwrap(),
            std::fs::read(java.join(&file)).unwrap(),
            "{file}"
        );
    }
}
