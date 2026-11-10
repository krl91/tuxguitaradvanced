//! The plugin's state in the host's project (contracts/plugin.md "State"): `state.format=1` and `chain=<text>`, nothing else. The
//! external parameters are GuitarFX's, read from its `settings.properties` when the plugin is loaded (FR-025), never stored in the project.

use guitarfx_dsp::chain_text;
use guitarfx_dsp::params::Params;
use guitarfx_dsp::ready_made;
use guitarfx_dsp::settings::ChainParams;
use guitarfx_settings::properties;
use guitarfx_settings::settings::Settings;
use std::path::Path;

pub const FORMAT: u32 = 1;
const FORMAT_KEY: &str = "state.format";
const CHAIN_KEY: &str = "chain";

/// What a saved state gives.
// read once per load: its size does not matter
#[allow(clippy::large_enum_variant)]
#[derive(Clone, Copy, Debug, PartialEq)]
pub enum Restored {
    Chain(ChainParams),
    /// Saved by a later GuitarFX: the default sound, and a line in the editor.
    Newer,
    /// No state yet (a new instance) or one that cannot be read: the default sound.
    Default,
}

impl Restored {
    pub fn chain(&self) -> ChainParams {
        match self {
            Restored::Chain(chain) => *chain,
            Restored::Newer | Restored::Default => ready_made::first(),
        }
    }

    /// The editor's line about it, if any.
    pub fn message_key(&self) -> Option<&'static str> {
        match self {
            Restored::Newer => Some("plugin.state.newer"),
            Restored::Chain(_) | Restored::Default => None,
        }
    }
}

pub fn text(chain: &ChainParams) -> String {
    properties::format(&[
        (FORMAT_KEY.to_string(), FORMAT.to_string()),
        (CHAIN_KEY.to_string(), chain_text::format(chain)),
    ])
}

pub fn read(text: &str) -> Restored {
    let Ok(pairs) = properties::parse(text) else {
        return Restored::Default;
    };
    let value = |key: &str| pairs.iter().find(|(k, _)| k == key).map(|(_, v)| v.trim());
    match value(FORMAT_KEY).and_then(|v| v.parse::<u32>().ok()) {
        Some(format) if format > FORMAT => Restored::Newer,
        Some(FORMAT) => value(CHAIN_KEY).and_then(chain_text::parse).map_or(Restored::Default, Restored::Chain),
        _ => Restored::Default,
    }
}

/// The external parameters of GuitarFX's settings file in `folder`, the defaults without one; nothing is written.
pub fn external_params(folder: Option<&Path>) -> Params {
    folder.map_or_else(Params::defaults, |f| Settings::read_only(f).params)
}
