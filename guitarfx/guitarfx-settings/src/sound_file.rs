//! The sound file `.guitarfx` (contracts/sound-file.md, FR-023), shared with TuxGuitarAdvanced's `SoundFile.java`: a comment line, then
//! `format=1`, `name=…` and `chain=…`. The two programs write the same bytes for the same sound (SC-011).

use crate::properties;
use crate::sounds::NAME_LENGTH;
use guitarfx_dsp::chain_text;
use guitarfx_dsp::settings::ChainParams;
use std::path::Path;

pub const EXTENSION: &str = "guitarfx";
const COMMENT: &str = "# GuitarFX sound";
const FORMAT: u32 = 1;

#[derive(Clone, Debug, PartialEq)]
pub struct SoundFile {
    pub name: String,
    pub chain: ChainParams,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum ReadError {
    /// "made by a newer version"
    Newer,
    /// "not a sound file"
    NotASoundFile,
}

impl ReadError {
    pub fn key(self) -> &'static str {
        match self {
            ReadError::Newer => "message.sound-file.newer",
            ReadError::NotASoundFile => "message.sound-file.not",
        }
    }
}

/// The sound in that text; knobs out of range are clamped.
pub fn read(text: &str) -> Result<SoundFile, ReadError> {
    let pairs = properties::parse(text).map_err(|_| ReadError::NotASoundFile)?;
    let get = |key: &str| pairs.iter().find(|(k, _)| k == key).map(|(_, v)| v.as_str());
    match get("format").map(|f| f.trim().parse::<u32>()) {
        Some(Ok(FORMAT)) => {}
        Some(Ok(format)) if format > FORMAT => return Err(ReadError::Newer),
        _ => return Err(ReadError::NotASoundFile),
    }
    let name = get("name").map(str::trim).ok_or(ReadError::NotASoundFile)?;
    let length = name.chars().count();
    if length == 0 || length > NAME_LENGTH || name.contains(['\n', '\r']) {
        return Err(ReadError::NotASoundFile);
    }
    let chain = get("chain").and_then(chain_text::parse).ok_or(ReadError::NotASoundFile)?;
    Ok(SoundFile {
        name: name.to_string(),
        chain,
    })
}

/// A file that cannot be read is not a sound file.
pub fn read_file(path: &Path) -> Result<SoundFile, ReadError> {
    let bytes = std::fs::read(path).map_err(|_| ReadError::NotASoundFile)?;
    read(&String::from_utf8(bytes).map_err(|_| ReadError::NotASoundFile)?)
}

/// The name escaped as Java's `Properties` reads it: `\`, `=` and `:`.
fn escape(name: &str) -> String {
    let mut out = String::with_capacity(name.len());
    for c in name.trim().chars() {
        if matches!(c, '\\' | '=' | ':') {
            out.push('\\');
        }
        out.push(c);
    }
    out
}

/// The file's text, as `SoundFile.java` writes it.
pub fn text(name: &str, chain: &ChainParams) -> String {
    format!("{COMMENT}\nformat={FORMAT}\nname={}\nchain={}\n", escape(name), chain_text::format(chain))
}

/// Writes the sound atomically.
pub fn write(path: &Path, name: &str, chain: &ChainParams) -> std::io::Result<()> {
    properties::write_atomic(path, &text(name, chain))
}
