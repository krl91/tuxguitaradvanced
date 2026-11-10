//! The sounds (contracts/settings-files.md `sounds.properties`, FR-007, FR-008), with the rules and keys of TuxGuitarAdvanced's
//! `AmpSoundStore`: the four ready-made sounds, each changed one kept as `ready.<name>.chain` until *Restore*; the own sounds as
//! `sound.<n>.name` and `sound.<n>.chain`, numbered from 0 with no gap. The current sound and chain are in `settings.properties`.

use crate::properties;
use crate::settings::{copy_aside, Notice};
use guitarfx_dsp::chain_text;
use guitarfx_dsp::ready_made;
use guitarfx_dsp::settings::ChainParams;
use std::path::Path;

pub const FILE: &str = "sounds.properties";
const FORMAT_KEY: &str = "sounds.format";
const FORMAT: u32 = 1;
/// A name's longest length, in characters (`AmpSoundStore.NAME_LENGTH`).
pub const NAME_LENGTH: usize = 40;

/// Why a name cannot be given to a sound (`AmpSoundStore.NameProblem`).
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum NameProblem {
    Empty,
    TooLong,
    ReadyMade,
    Taken,
}

/// What importing a sound file did (contracts/sound-file.md "Import").
#[derive(Clone, Debug, PartialEq, Eq)]
pub enum Import {
    /// Added under that name.
    Added(String),
    /// An own sound has that name: nothing changed until the guitarist chooses to replace it or keep both.
    Clash(String),
}

/// The text after a ready-made name, so that an imported sound never replaces a ready-made one.
const IMPORTED: &str = " (imported)";

#[derive(Clone, Debug, PartialEq)]
pub struct Sound {
    pub name: String,
    pub chain: ChainParams,
    pub ready_made: bool,
    /// A ready-made sound whose chain is not the shipped one.
    pub changed: bool,
}

#[derive(Clone, Debug, Default, PartialEq)]
pub struct Sounds {
    /// The changed ready-made sounds, by their shipped name.
    ready: Vec<(String, ChainParams)>,
    own: Vec<(String, ChainParams)>,
    unknown: Vec<(String, String)>,
}

#[derive(Debug)]
pub struct Loaded {
    pub sounds: Sounds,
    pub notice: Option<Notice>,
    /// False while a newer file must not be written over.
    pub writable: bool,
}

fn ready_name(name: &str) -> Option<&'static str> {
    ready_made::ALL.iter().map(|(n, _)| *n).find(|n| n.eq_ignore_ascii_case(name.trim()))
}

fn same(a: &str, b: &str) -> bool {
    a.trim().to_lowercase() == b.trim().to_lowercase()
}

impl Sounds {
    pub fn load(folder: &Path) -> Loaded {
        let path = folder.join(FILE);
        let fresh = |notice| Loaded {
            sounds: Sounds::default(),
            notice,
            writable: true,
        };
        let Ok(bytes) = std::fs::read(&path) else {
            return fresh(None);
        };
        let Some(pairs) = String::from_utf8(bytes).ok().and_then(|t| properties::parse(&t).ok()) else {
            return fresh(Some(copy_aside(&path)));
        };
        let get = |key: &str| pairs.iter().find(|(k, _)| k == key).map(|(_, v)| v.as_str());
        let mut damaged = false;
        match get(FORMAT_KEY).map(|f| f.trim().parse::<u32>()) {
            Some(Ok(format)) if format > FORMAT => {
                return Loaded {
                    sounds: Sounds::default(),
                    notice: Some(Notice::Newer),
                    writable: false,
                }
            }
            Some(Err(_)) => damaged = true,
            _ => {}
        }
        let mut sounds = Sounds::default();
        for (key, value) in &pairs {
            if key == FORMAT_KEY {
                continue;
            }
            if let Some(name) = key.strip_prefix("ready.").and_then(|r| r.strip_suffix(".chain")) {
                match (ready_name(name), chain_text::parse(value)) {
                    (Some(name), Some(chain)) => sounds.keep_ready(name, &chain),
                    _ if value.is_empty() => {}
                    _ => damaged = true,
                }
                continue;
            }
            let own = key
                .strip_prefix("sound.")
                .and_then(|r| r.split_once('.'))
                .filter(|(n, _)| n.parse::<usize>().is_ok());
            match own {
                Some((n, "name")) => {
                    if value.trim().is_empty() {
                        continue;
                    }
                    match get(&format!("sound.{n}.chain")).and_then(chain_text::parse) {
                        Some(chain) if sounds.name_problem(value, None).is_none() => sounds.own.push((value.trim().to_string(), chain)),
                        _ => damaged = true,
                    }
                }
                Some((_, "chain")) => {}
                _ => sounds.unknown.push((key.clone(), value.clone())),
            }
        }
        Loaded {
            sounds,
            notice: damaged.then(|| copy_aside(&path)),
            writable: true,
        }
    }

    /// Writes every sound atomically, the format first, the unknown keys last.
    pub fn save(&self, folder: &Path) -> std::io::Result<()> {
        let mut pairs = vec![(FORMAT_KEY.to_string(), FORMAT.to_string())];
        for (name, chain) in &self.ready {
            pairs.push((format!("ready.{name}.chain"), chain_text::format(chain)));
        }
        for (n, (name, chain)) in self.own.iter().enumerate() {
            pairs.push((format!("sound.{n}.name"), name.clone()));
            pairs.push((format!("sound.{n}.chain"), chain_text::format(chain)));
        }
        pairs.extend(self.unknown.iter().cloned());
        properties::write_atomic(&folder.join(FILE), &properties::format(&pairs))
    }

    /// The ready-made sounds in their order, then the own sounds by name, case ignored.
    pub fn list(&self) -> Vec<Sound> {
        let mut list: Vec<Sound> = ready_made::ALL
            .iter()
            .map(|(name, _)| {
                let shipped = ready_made::chain(name).expect("a shipped chain");
                let kept = self.ready.iter().find(|(n, _)| n == name).map(|(_, c)| *c);
                Sound {
                    name: name.to_string(),
                    chain: kept.unwrap_or(shipped),
                    ready_made: true,
                    changed: kept.is_some_and(|c| c != shipped),
                }
            })
            .collect();
        let mut own: Vec<Sound> = self
            .own
            .iter()
            .map(|(name, chain)| Sound {
                name: name.clone(),
                chain: *chain,
                ready_made: false,
                changed: false,
            })
            .collect();
        own.sort_by_key(|s| s.name.to_lowercase());
        list.extend(own);
        list
    }

    /// The sound of that name, case ignored.
    pub fn sound(&self, name: &str) -> Option<Sound> {
        self.list().into_iter().find(|s| same(&s.name, name))
    }

    /// What is wrong with that name for a new or renamed sound; `except` is the sound being renamed.
    pub fn name_problem(&self, name: &str, except: Option<&str>) -> Option<NameProblem> {
        let trimmed = name.trim();
        if trimmed.is_empty() || trimmed.contains(['\n', '\r']) {
            return Some(NameProblem::Empty);
        }
        if trimmed.chars().count() > NAME_LENGTH {
            return Some(NameProblem::TooLong);
        }
        if ready_name(trimmed).is_some() {
            return Some(NameProblem::ReadyMade);
        }
        let taken = self.own.iter().any(|(n, _)| same(n, trimmed) && !except.is_some_and(|e| same(e, n)));
        taken.then_some(NameProblem::Taken)
    }

    /// Keeps a chain as an own sound; an own sound of the same name, case ignored, is replaced.
    pub fn save_as(&mut self, name: &str, chain: ChainParams) -> Result<(), NameProblem> {
        match self.name_problem(name, None) {
            None | Some(NameProblem::Taken) => {}
            Some(problem) => return Err(problem),
        }
        let name = name.trim().to_string();
        match self.own.iter_mut().find(|(n, _)| same(n, &name)) {
            Some(sound) => *sound = (name, chain),
            None => self.own.push((name, chain)),
        }
        Ok(())
    }

    pub fn rename(&mut self, from: &str, to: &str) -> Result<(), NameProblem> {
        if ready_name(from).is_some() {
            return Err(NameProblem::ReadyMade);
        }
        if let Some(problem) = self.name_problem(to, Some(from)) {
            return Err(problem);
        }
        match self.own.iter_mut().find(|(n, _)| same(n, from)) {
            Some(sound) => {
                sound.0 = to.trim().to_string();
                Ok(())
            }
            None => Err(NameProblem::Empty),
        }
    }

    /// Deletes an own sound; false for a ready-made sound or no sound.
    pub fn delete(&mut self, name: &str) -> bool {
        let before = self.own.len();
        self.own.retain(|(n, _)| !same(n, name));
        self.own.len() < before
    }

    fn keep_ready(&mut self, name: &str, chain: &ChainParams) {
        self.ready.retain(|(n, _)| n != name);
        if ready_made::chain(name).as_ref() != Some(chain) {
            self.ready.push((name.to_string(), *chain));
        }
    }

    /// The chain in use changed: while a ready-made sound is current, its changes are kept on it (`AmpSoundStore.saveCurrentChain`); an
    /// own sound keeps its saved chain until *Save*.
    pub fn keep_current(&mut self, current: &str, chain: &ChainParams) {
        if let Some(name) = ready_name(current) {
            self.keep_ready(name, chain);
        }
    }

    /// The chain in use differs from the current sound's: an own sound's unsaved changes.
    pub fn is_changed(&self, current: &str, chain: &ChainParams) -> bool {
        self.sound(current).is_some_and(|s| s.chain != *chain)
    }

    /// The chain to play when that sound is picked.
    pub fn pick(&self, name: &str) -> Option<ChainParams> {
        self.sound(name).map(|s| s.chain)
    }

    /// Brings a ready-made sound back as shipped; its chain, or none for any other sound.
    pub fn restore(&mut self, name: &str) -> Option<ChainParams> {
        let name = ready_name(name)?;
        self.ready.retain(|(n, _)| n != name);
        ready_made::chain(name)
    }

    /// The name an imported sound takes: its own, trimmed, or "<name> (imported)" for a ready-made name.
    fn import_name(name: &str) -> String {
        match ready_name(name) {
            Some(ready) => format!("{ready}{IMPORTED}"),
            None => name.trim().to_string(),
        }
    }

    /// Imports a sound read from a file: added under its name, or a clash with an own sound, left for the guitarist to answer.
    pub fn import(&mut self, name: &str, chain: ChainParams) -> Import {
        let name = Self::import_name(name);
        if self.name_problem(&name, None) == Some(NameProblem::Taken) {
            return Import::Clash(self.sound(&name).map(|s| s.name).unwrap_or(name));
        }
        self.own.push((name.clone(), chain));
        Import::Added(name)
    }

    /// The guitarist chose to replace the own sound of that name.
    pub fn import_replace(&mut self, name: &str, chain: ChainParams) {
        let name = Self::import_name(name);
        // a clash comes from a valid name, so the save cannot fail
        let _ = self.save_as(&name, chain);
    }

    /// The guitarist chose to keep both: the import is named "<name> 2" (then 3…, the name shortened to fit). Returns the name given.
    pub fn import_keep_both(&mut self, name: &str, chain: ChainParams) -> String {
        let name = Self::import_name(name);
        let mut number = 2;
        loop {
            let suffix = format!(" {number}");
            let room = NAME_LENGTH.saturating_sub(suffix.chars().count());
            let base: String = name.chars().take(room).collect();
            let candidate = format!("{}{suffix}", base.trim_end());
            if self.name_problem(&candidate, None).is_none() {
                self.own.push((candidate.clone(), chain));
                return candidate;
            }
            number += 1;
        }
    }
}
