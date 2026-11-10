//! `settings.properties` (contracts/settings-files.md): the devices, the volume, the current sound and chain, and every external parameter.
//! A missing key takes its default, a value out of bounds is clamped, an unknown key is kept as is. The sound's on/off state is never stored
//! (FR-009). A file from a newer GuitarFX is left untouched; a damaged one is copied aside and its readable values kept.

use crate::properties;
use guitarfx_dsp::params::{Params, ENTRIES};
use std::path::{Path, PathBuf};

pub const FILE: &str = "settings.properties";
const FORMAT_KEY: &str = "settings.format";
const FORMAT: u32 = 1;
const INPUT: &str = "devices.input";
const CHANNEL: &str = "devices.channel";
const OUTPUT: &str = "devices.output";
const VOLUME: &str = "volume";
const SOUND_CURRENT: &str = "sound.current";
const CHAIN_CURRENT: &str = "chain.current";
/// The main window's place and size, in points.
const WINDOW: [&str; 4] = ["window.x", "window.y", "window.width", "window.height"];
/// Never stored, and dropped when an older file has it.
const SOUND_ON: &str = "sound.on";
const DEFAULT_VOLUME: f64 = 0.7;
const DEFAULT_SOUND: &str = "Clean";
const DAMAGED: &str = ".damaged-";

#[derive(Clone, Debug, PartialEq)]
pub struct Settings {
    pub input: Option<String>,
    pub channel: Option<u32>,
    pub output: Option<String>,
    /// 0 to 1.
    pub volume: f64,
    pub sound_current: String,
    pub chain_current: Option<String>,
    pub params: Params,
    /// The main window's x, y, width and height, once it was shown.
    pub window: Option<[f32; 4]>,
    /// The keys this version does not know, written back as they were.
    pub unknown: Vec<(String, String)>,
}

impl Default for Settings {
    fn default() -> Self {
        Self {
            input: None,
            channel: None,
            output: None,
            volume: DEFAULT_VOLUME,
            sound_current: DEFAULT_SOUND.to_string(),
            chain_current: None,
            params: Params::defaults(),
            window: None,
            unknown: Vec::new(),
        }
    }
}

#[derive(Clone, Debug, PartialEq)]
pub enum Notice {
    /// The file comes from a newer GuitarFX: it is left untouched and the defaults are used.
    Newer,
    /// The file was damaged; this is its copy.
    Damaged(PathBuf),
}

#[derive(Debug)]
pub struct Loaded {
    pub settings: Settings,
    pub notice: Option<Notice>,
    /// False while a newer file must not be written over (until the guitarist confirms).
    pub writable: bool,
}

/// A malformed value marks the file as damaged; the value takes its default.
fn number<T: std::str::FromStr>(text: &str, damaged: &mut bool) -> Option<T> {
    let parsed = text.trim().parse().ok();
    if parsed.is_none() {
        *damaged = true;
    }
    parsed
}

/// Copies a damaged file to `<name>.damaged-<yyyymmdd-hhmmss>` beside it.
pub(crate) fn copy_aside(path: &Path) -> Notice {
    let name = path.file_name().map(|n| n.to_string_lossy().to_string()).unwrap_or_default();
    let copy = path.with_file_name(format!("{name}{DAMAGED}{}", crate::time::now()));
    let _ = std::fs::copy(path, &copy);
    Notice::Damaged(copy)
}

impl Settings {
    /// As [`Settings::load`], copying a damaged file aside.
    pub fn load(folder: &Path) -> Loaded {
        Self::read(folder, true)
    }

    /// The settings with nothing written, not even a damaged file's copy (`guitarfx --check`).
    pub fn read_only(folder: &Path) -> Settings {
        Self::read(folder, false).settings
    }

    fn read(folder: &Path, copy_damaged: bool) -> Loaded {
        let copy_aside = |path: &Path| {
            if copy_damaged {
                copy_aside(path)
            } else {
                Notice::Damaged(path.to_path_buf())
            }
        };
        let path = folder.join(FILE);
        let fresh = |notice| Loaded {
            settings: Settings::default(),
            notice,
            writable: true,
        };
        let Ok(bytes) = std::fs::read(&path) else {
            return fresh(None);
        };
        let Ok(text) = String::from_utf8(bytes) else {
            return fresh(Some(copy_aside(&path)));
        };
        let Ok(pairs) = properties::parse(&text) else {
            return fresh(Some(copy_aside(&path)));
        };
        let mut damaged = false;
        let mut settings = Settings::default();
        let mut values = Vec::new();
        let mut window = [None; 4];
        for (key, value) in &pairs {
            match key.as_str() {
                FORMAT_KEY => {
                    if number::<u32>(value, &mut damaged).is_some_and(|format| format > FORMAT) {
                        return Loaded {
                            settings: Settings::default(),
                            notice: Some(Notice::Newer),
                            writable: false,
                        };
                    }
                }
                INPUT => settings.input = Some(value.clone()),
                OUTPUT => settings.output = Some(value.clone()),
                CHANNEL => settings.channel = number(value, &mut damaged),
                VOLUME => {
                    if let Some(volume) = number::<f64>(value, &mut damaged).filter(|v| !v.is_nan()) {
                        settings.volume = volume.clamp(0.0, 1.0);
                    }
                }
                SOUND_CURRENT => settings.sound_current = value.clone(),
                CHAIN_CURRENT => settings.chain_current = Some(value.clone()),
                SOUND_ON => {}
                _ if WINDOW.contains(&key.as_str()) => {
                    let index = WINDOW.iter().position(|k| k == key).expect("one of them");
                    window[index] = number::<f32>(value, &mut damaged).filter(|v| v.is_finite());
                }
                _ if ENTRIES.iter().any(|e| e.key == key) => {
                    if number::<f64>(value, &mut damaged).is_some_and(|v| !v.is_nan()) {
                        values.push((key.as_str(), value.as_str()));
                    } else {
                        damaged = true;
                    }
                }
                _ => settings.unknown.push((key.clone(), value.clone())),
            }
        }
        settings.params = Params::from_values(values);
        if let [Some(x), Some(y), Some(width), Some(height)] = window {
            settings.window = Some([x, y, width, height]);
        }
        Loaded {
            settings,
            notice: damaged.then(|| copy_aside(&path)),
            writable: true,
        }
    }

    /// Writes every key atomically, the format first, the unknown keys last.
    pub fn save(&self, folder: &Path) -> std::io::Result<()> {
        let mut pairs = vec![(FORMAT_KEY.to_string(), FORMAT.to_string())];
        let mut put = |key: &str, value: String| pairs.push((key.to_string(), value));
        if let Some(input) = &self.input {
            put(INPUT, input.clone());
        }
        if let Some(channel) = self.channel {
            put(CHANNEL, channel.to_string());
        }
        if let Some(output) = &self.output {
            put(OUTPUT, output.clone());
        }
        put(VOLUME, self.volume.clamp(0.0, 1.0).to_string());
        put(SOUND_CURRENT, self.sound_current.clone());
        if let Some(chain) = &self.chain_current {
            put(CHAIN_CURRENT, chain.clone());
        }
        if let Some(window) = self.window {
            for (key, value) in WINDOW.iter().zip(window) {
                put(key, value.to_string());
            }
        }
        for entry in &ENTRIES {
            put(entry.key, self.params.get(entry.id).to_string());
        }
        for (key, value) in &self.unknown {
            if key != SOUND_ON {
                put(key, value.clone());
            }
        }
        properties::write_atomic(&folder.join(FILE), &properties::format(&pairs))
    }
}
