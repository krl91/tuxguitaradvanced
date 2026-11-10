//! One GuitarFX at a time (spec 027): the first instance holds an exclusive lock on a file of the settings folder until it quits (the
//! system frees it even after a crash); a second one finds it taken, says so and quits without opening a device.

use std::fs::{File, OpenOptions};
use std::path::Path;

const LOCK: &str = "guitarfx.lock";

/// The lock, held while it lives; none when another instance holds it.
pub fn acquire(folder: &Path) -> Option<File> {
    std::fs::create_dir_all(folder).ok()?;
    let file = OpenOptions::new().create(true).truncate(false).write(true).open(folder.join(LOCK)).ok()?;
    file.try_lock().ok()?;
    Some(file)
}
