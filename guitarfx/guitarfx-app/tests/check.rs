//! `guitarfx --check` (spec 027, T031, contracts/application.md "Headless start"): no window and no device; it loads the settings, the texts
//! and the registry, builds the chain at 48 kHz, processes one second of silence, prints `ok` and exits with 0. It writes nothing.

use std::path::Path;
use std::process::Command;

fn check(home: &Path) -> std::process::Output {
    Command::new(env!("CARGO_BIN_EXE_guitarfx"))
        .arg("--check")
        .env("HOME", home)
        .env("XDG_CONFIG_HOME", home.join(".config"))
        .env("APPDATA", home.join("AppData"))
        .output()
        .unwrap()
}

fn home(name: &str) -> std::path::PathBuf {
    let path = std::env::temp_dir().join(format!("guitarfx-check-test-{}-{name}", std::process::id()));
    let _ = std::fs::remove_dir_all(&path);
    std::fs::create_dir_all(&path).unwrap();
    path
}

fn files(dir: &Path) -> Vec<String> {
    let mut found = Vec::new();
    if let Ok(entries) = std::fs::read_dir(dir) {
        for entry in entries.flatten() {
            let path = entry.path();
            if path.is_dir() {
                found.extend(files(&path));
            } else {
                found.push(path.to_string_lossy().to_string());
            }
        }
    }
    found
}

#[test]
fn check_prints_ok_and_writes_nothing() {
    let home = home("empty");
    let output = check(&home);
    assert!(output.status.success(), "{output:?}");
    assert_eq!(String::from_utf8_lossy(&output.stdout), "ok\n");
    assert!(files(&home).is_empty(), "{:?}", files(&home));
}

#[test]
fn check_reads_the_settings_and_leaves_a_damaged_file_alone() {
    let home = home("damaged");
    let folder = guitarfx_settings_folder(&home);
    std::fs::create_dir_all(&folder).unwrap();
    std::fs::write(
        folder.join("settings.properties"),
        "settings.format=1\nvolume=abc\nguitarfx.output.ceiling=-6\n",
    )
    .unwrap();
    let output = check(&home);
    assert!(output.status.success(), "{output:?}");
    assert_eq!(String::from_utf8_lossy(&output.stdout), "ok\n");
    assert_eq!(files(&home).len(), 1, "no copy made: {:?}", files(&home));
}

/// The settings folder under that home, as `directories` finds it on this system.
fn guitarfx_settings_folder(home: &Path) -> std::path::PathBuf {
    if cfg!(target_os = "macos") {
        home.join("Library/Application Support/GuitarFX")
    } else if cfg!(windows) {
        home.join("AppData").join("GuitarFX")
    } else {
        home.join(".config/guitarfx")
    }
}
