//! GuitarFX, the standalone amp and effect pedals (spec 027).

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
