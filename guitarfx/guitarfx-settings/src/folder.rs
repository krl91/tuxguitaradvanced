//! The settings folder (research R6): `~/Library/Application Support/GuitarFX` on macOS, `%APPDATA%\GuitarFX` on Windows,
//! `$XDG_CONFIG_HOME/guitarfx` (or `~/.config/guitarfx`) on Linux.

use std::path::PathBuf;

/// The folder, or none when the system gives no home.
pub fn settings_folder() -> Option<PathBuf> {
    let base = directories::BaseDirs::new()?;
    let name = if cfg!(any(target_os = "macos", windows)) {
        "GuitarFX"
    } else {
        "guitarfx"
    };
    Some(base.config_dir().join(name))
}
