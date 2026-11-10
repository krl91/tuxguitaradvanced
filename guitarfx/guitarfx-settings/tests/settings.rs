//! GuitarFX's `settings.properties` (spec 027, T025, contracts/settings-files.md).

use guitarfx_settings::properties;
use guitarfx_settings::settings::{Notice, Settings};
use std::path::{Path, PathBuf};

fn folder(name: &str) -> PathBuf {
    let path = std::env::temp_dir().join(format!("guitarfx-settings-test-{}-{name}", std::process::id()));
    let _ = std::fs::remove_dir_all(&path);
    std::fs::create_dir_all(&path).unwrap();
    path
}

fn file(folder: &Path) -> PathBuf {
    folder.join("settings.properties")
}

#[test]
fn the_folder_is_the_systems_settings_folder() {
    let folder = guitarfx_settings::folder::settings_folder().expect("a settings folder");
    let text = folder.to_string_lossy();
    if cfg!(target_os = "macos") {
        assert!(text.ends_with("Library/Application Support/GuitarFX"), "{text}");
    } else if cfg!(windows) {
        assert!(text.ends_with("\\GuitarFX"), "{text}");
    } else {
        assert!(text.ends_with("/guitarfx"), "{text}");
    }
}

#[test]
fn no_file_gives_the_defaults() {
    let folder = folder("none");
    let loaded = Settings::load(&folder);
    assert!(loaded.notice.is_none());
    let s = loaded.settings;
    assert_eq!(s.input, None);
    assert_eq!(s.channel, None);
    assert_eq!(s.output, None);
    assert_eq!(s.volume, 0.7);
    assert_eq!(s.sound_current, "Clean");
    assert_eq!(s.chain_current, None);
    assert_eq!(s.params, guitarfx_dsp::params::Params::defaults());
}

#[test]
fn values_are_read_written_back_and_clamped() {
    let folder = folder("values");
    std::fs::write(
        file(&folder),
        "settings.format=1\ndevices.input=Rocksmith Guitar Adapter\ndevices.channel=2\ndevices.output=MacBook Pro Speakers\nvolume=1.7\n\
         sound.current=Crunch\nchain.current=gate:on,-60,150|amp:crunch,5,5,5,5,6|cab:on\nguitarfx.output.ceiling=-3\n\
         guitarfx.gate.floor=-500\nsomething.else=kept as is\n",
    )
    .unwrap();
    let loaded = Settings::load(&folder);
    assert!(loaded.notice.is_none(), "{:?}", loaded.notice);
    let s = loaded.settings;
    assert_eq!(s.input.as_deref(), Some("Rocksmith Guitar Adapter"));
    assert_eq!(s.channel, Some(2));
    assert_eq!(s.output.as_deref(), Some("MacBook Pro Speakers"));
    assert_eq!(s.volume, 1.0, "clamped");
    assert_eq!(s.sound_current, "Crunch");
    assert_eq!(s.chain_current.as_deref(), Some("gate:on,-60,150|amp:crunch,5,5,5,5,6|cab:on"));
    assert_eq!(s.params.get_key("guitarfx.output.ceiling"), Some(-3.0));
    assert_eq!(s.params.get_key("guitarfx.gate.floor"), Some(-120.0), "clamped");
    s.save(&folder).unwrap();
    let text = std::fs::read_to_string(file(&folder)).unwrap();
    assert!(text.starts_with("settings.format=1\n"), "{text}");
    assert!(text.contains("\nvolume=1\n"));
    assert!(text.contains("\nguitarfx.gate.floor=-120\n"), "the clamped value is written back");
    assert!(text.contains("\nguitarfx.reverb.all-pass=0.5\n"), "every parameter is written");
    assert!(text.contains("\nsomething.else=kept as is\n"), "an unknown key is kept");
    let again = Settings::load(&folder);
    assert!(again.notice.is_none());
    assert_eq!(again.settings, s);
}

#[test]
fn a_missing_key_takes_its_default() {
    let folder = folder("missing");
    std::fs::write(file(&folder), "settings.format=1\nvolume=0.25\n").unwrap();
    let s = Settings::load(&folder).settings;
    assert_eq!(s.volume, 0.25);
    assert_eq!(s.sound_current, "Clean");
    assert_eq!(s.params.get_key("guitarfx.devices.block"), Some(128.0));
}

#[test]
fn the_write_is_atomic_and_leaves_no_temporary_file() {
    let folder = folder("atomic");
    Settings::default().save(&folder).unwrap();
    let names: Vec<String> = std::fs::read_dir(&folder)
        .unwrap()
        .map(|e| e.unwrap().file_name().to_string_lossy().to_string())
        .collect();
    assert_eq!(names, vec!["settings.properties".to_string()]);
}

#[test]
fn a_newer_format_is_left_untouched_and_the_defaults_are_used() {
    let folder = folder("newer");
    let text = "settings.format=2\nvolume=0.3\nsound.current=Lead\n";
    std::fs::write(file(&folder), text).unwrap();
    let loaded = Settings::load(&folder);
    assert_eq!(loaded.notice, Some(Notice::Newer));
    assert_eq!(loaded.settings, Settings::default());
    assert!(!loaded.writable, "nothing is written over it until the guitarist confirms");
    assert_eq!(std::fs::read_to_string(file(&folder)).unwrap(), text);
}

#[test]
fn a_damaged_file_is_copied_aside_and_its_readable_values_kept() {
    let folder = folder("damaged");
    let text = "settings.format=1\nvolume=abc\nsound.current=Lead\nguitarfx.gate.hold=nothing\nguitarfx.gate.floor=-90\n";
    std::fs::write(file(&folder), text).unwrap();
    let loaded = Settings::load(&folder);
    let Some(Notice::Damaged(copy)) = &loaded.notice else {
        panic!("{:?}", loaded.notice)
    };
    let name = copy.file_name().unwrap().to_string_lossy().to_string();
    assert!(name.starts_with("settings.properties.damaged-"), "{name}");
    let stamp = &name["settings.properties.damaged-".len()..];
    assert!(
        stamp.len() == 15 && stamp.as_bytes()[8] == b'-' && stamp.chars().filter(char::is_ascii_digit).count() == 14,
        "{stamp}"
    );
    assert_eq!(std::fs::read_to_string(copy).unwrap(), text, "the copy is the damaged file");
    assert!(loaded.writable);
    let s = loaded.settings;
    assert_eq!(s.volume, 0.7, "the malformed value takes its default");
    assert_eq!(s.sound_current, "Lead", "the readable values are kept");
    assert_eq!(s.params.get_key("guitarfx.gate.hold"), Some(0.010));
    assert_eq!(s.params.get_key("guitarfx.gate.floor"), Some(-90.0));
}

#[test]
fn an_unreadable_file_is_copied_aside() {
    let folder = folder("unreadable");
    std::fs::write(file(&folder), [0xff, 0xfe, 0x00, b'=', 0x80]).unwrap();
    let loaded = Settings::load(&folder);
    assert!(matches!(loaded.notice, Some(Notice::Damaged(_))), "{:?}", loaded.notice);
    assert_eq!(loaded.settings, Settings::default());
}

#[test]
fn the_sound_is_never_stored_as_on() {
    let folder = folder("on");
    std::fs::write(file(&folder), "settings.format=1\nsound.on=true\n").unwrap();
    let s = Settings::load(&folder).settings;
    s.save(&folder).unwrap();
    let text = std::fs::read_to_string(file(&folder)).unwrap();
    assert!(!text.contains("sound.on"), "{text}");
    assert!(!text.to_lowercase().contains(".on="), "{text}");
}

#[test]
fn properties_are_read_and_written_as_java_writes_them() {
    let pairs =
        properties::parse("# a comment\n! another\n\nname = Lead\\: with \\= signs\\\\ \nlong=a\\\n    b\nunicode=\\u00e9t\\u00e9\n").unwrap();
    assert_eq!(
        pairs,
        vec![
            ("name".to_string(), "Lead: with = signs\\ ".to_string()),
            ("long".to_string(), "ab".to_string()),
            ("unicode".to_string(), "été".to_string())
        ]
    );
    let text = properties::format(&[("name".to_string(), "a=b:c\\d #e\nf".to_string())]);
    assert_eq!(text, "name=a\\=b\\:c\\\\d #e\\nf\n");
    assert_eq!(
        properties::parse(&text).unwrap(),
        vec![("name".to_string(), "a=b:c\\d #e\nf".to_string())]
    );
}

#[test]
fn the_windows_place_and_size_are_kept() {
    let folder = folder("window");
    let s = Settings {
        window: Some([12.0, 34.5, 960.0, 640.0]),
        ..Settings::default()
    };
    s.save(&folder).unwrap();
    assert_eq!(Settings::load(&folder).settings.window, Some([12.0, 34.5, 960.0, 640.0]));
    std::fs::write(file(&folder), "settings.format=1\nwindow.x=1\nwindow.y=2\nwindow.width=3\n").unwrap();
    assert_eq!(Settings::load(&folder).settings.window, None, "all four or none");
}

#[test]
fn read_only_copies_nothing() {
    let folder = folder("read-only");
    std::fs::write(file(&folder), "settings.format=1\nvolume=abc\nsound.current=Lead\n").unwrap();
    assert_eq!(Settings::read_only(&folder).sound_current, "Lead");
    assert_eq!(std::fs::read_dir(&folder).unwrap().count(), 1);
}
