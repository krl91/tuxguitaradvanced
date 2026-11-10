//! GuitarFX, the standalone amp and effect pedals (spec 027).

// a window, not a console, on Windows (as on the other systems); `--check` still writes to a redirected output
#![cfg_attr(windows, windows_subsystem = "windows")]

use guitarfx_app::{app, check, single_instance};
use std::process::ExitCode;

fn main() -> ExitCode {
    if std::env::args().skip(1).any(|a| a == "--check") {
        return match check::run() {
            Ok(()) => {
                println!("ok");
                ExitCode::SUCCESS
            }
            Err(reason) => {
                eprintln!("{reason}");
                ExitCode::FAILURE
            }
        };
    }
    let Some(folder) = guitarfx_settings::folder::settings_folder() else {
        eprintln!("no settings folder");
        return ExitCode::FAILURE;
    };
    let Some(lock) = single_instance::acquire(&folder) else {
        return app::already_open();
    };
    let result = app::run(folder);
    drop(lock);
    result
}
