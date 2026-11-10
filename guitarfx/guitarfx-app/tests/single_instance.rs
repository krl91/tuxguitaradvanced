//! One GuitarFX at a time (spec 027, T030): the first instance holds a lock file in the settings folder; a second process finds it taken and
//! quits; once the first is gone, the lock is free again.

use guitarfx_app::single_instance;
use std::path::PathBuf;
use std::process::Command;

const CHILD: &str = "GUITARFX_LOCK_CHILD";

fn folder(name: &str) -> PathBuf {
    let path = std::env::temp_dir().join(format!("guitarfx-lock-test-{}-{name}", std::process::id()));
    let _ = std::fs::remove_dir_all(&path);
    std::fs::create_dir_all(&path).unwrap();
    path
}

/// Run in a second process: tries the lock of the folder named by the variable and prints what it found.
#[test]
fn child() {
    if let Ok(folder) = std::env::var(CHILD) {
        let held = single_instance::acquire(&PathBuf::from(folder));
        println!("child: {}", if held.is_some() { "acquired" } else { "already open" });
    }
}

fn second_process(folder: &PathBuf) -> String {
    let output = Command::new(std::env::current_exe().unwrap())
        .args(["--exact", "child", "--nocapture", "--test-threads=1"])
        .env(CHILD, folder)
        .output()
        .unwrap();
    let text = String::from_utf8_lossy(&output.stdout).to_string();
    // the harness may print "test child ... " on the same line
    let at = text.find("child: ").unwrap_or_else(|| panic!("{text}"));
    text[at..].lines().next().unwrap().to_string()
}

#[test]
fn a_second_process_finds_the_first_one() {
    let folder = folder("two");
    let first = single_instance::acquire(&folder).expect("the first instance");
    assert_eq!(second_process(&folder), "child: already open");
    drop(first);
    assert_eq!(second_process(&folder), "child: acquired", "free once the first is gone");
}

#[test]
fn the_folder_is_made_when_missing() {
    let folder = folder("missing").join("GuitarFX");
    assert!(single_instance::acquire(&folder).is_some());
}
