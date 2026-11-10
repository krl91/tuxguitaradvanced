//! GuitarFX as a CLAP, VST3 and Audio Unit plugin (spec 027, contracts/plugin.md): the application's chain, output stage and feedback
//! guard in music software. One library gives the three formats: the CLAP entry of `entry.rs`, which is nih-plug's (`nih_export_clap!`)
//! with two more factories that give clap-wrapper the VST3 class id and the Audio Unit codes, and clap-wrapper's VST3 and AUv2 entries
//! over it.

pub mod editor;
mod entry;
pub mod mailbox;
pub mod processor;
pub mod state;

use crate::editor::Link;
use crate::processor::{Processor, Shared, SystemClock};
use crate::state::Restored;
use nih_plug::prelude::*;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, RwLock};

pub const NAME: &str = "GuitarFX";
pub const VENDOR: &str = "TuxGuitarAdvanced";
pub const CLAP_ID: &str = "app.tuxguitar.guitarfx";
/// A VST3 class id: 16 bytes.
pub type ClassId = [u8; 16];
/// Generated once (2026-11-10) and kept: a host finds a saved project's plugin by it.
pub const VST3_CLASS_ID: ClassId = match u128::from_str_radix("91077A9918DB4B83849719BAD4BFC881", 16) {
    Ok(id) => id.to_be_bytes(),
    Err(_) => panic!("the VST3 class id is 32 hexadecimal digits"),
};
pub const AU_TYPE: &str = "aufx";
pub const AU_SUBTYPE: &str = "GFX1";
pub const AU_MANUFACTURER: &str = "TGAd";

/// The project's state: one text, `state.rs`'s. No host automation: the knobs are the editor's, the chain is the state.
#[derive(Params)]
pub struct GuitarFxParams {
    #[persist = "guitarfx"]
    state: Arc<RwLock<String>>,
}

pub struct GuitarFx {
    params: Arc<GuitarFxParams>,
    shared: Arc<Shared>,
    newer: Arc<AtomicBool>,
    processor: Option<Processor<SystemClock>>,
    inputs: usize,
}

impl Default for GuitarFx {
    fn default() -> Self {
        Self {
            params: Arc::new(GuitarFxParams {
                state: Arc::new(RwLock::new(state::text(&Restored::Default.chain()))),
            }),
            shared: Arc::default(),
            newer: Arc::default(),
            processor: None,
            inputs: 1,
        }
    }
}

impl Plugin for GuitarFx {
    const NAME: &'static str = NAME;
    const VENDOR: &'static str = VENDOR;
    const URL: &'static str = "https://github.com/krl91/tuxguitaradvanced";
    const EMAIL: &'static str = "";
    const VERSION: &'static str = env!("CARGO_PKG_VERSION");

    // a stereo input is summed to mono, as the application's channel choice gives mono
    const AUDIO_IO_LAYOUTS: &'static [AudioIOLayout] = &[
        AudioIOLayout {
            main_input_channels: NonZeroU32::new(2),
            main_output_channels: NonZeroU32::new(2),
            ..AudioIOLayout::const_default()
        },
        AudioIOLayout {
            main_input_channels: NonZeroU32::new(1),
            main_output_channels: NonZeroU32::new(2),
            ..AudioIOLayout::const_default()
        },
    ];

    type SysExMessage = ();
    type BackgroundTask = ();

    fn params(&self) -> Arc<dyn Params> {
        self.params.clone()
    }

    fn editor(&mut self, _async_executor: AsyncExecutor<Self>) -> Option<Box<dyn Editor>> {
        editor::create(Link {
            shared: self.shared.clone(),
            state: self.params.state.clone(),
            newer: self.newer.clone(),
        })
    }

    /// Everything `process` needs is made here: the chain of the project's state, at the host's rate, with GuitarFX's external parameters.
    fn initialize(&mut self, layout: &AudioIOLayout, config: &BufferConfig, context: &mut impl InitContext<Self>) -> bool {
        let text = self.params.state.read().map(|t| t.clone()).unwrap_or_default();
        let restored = state::read(&text);
        self.newer.store(restored == Restored::Newer, Ordering::Release);
        let folder = guitarfx_settings::folder::settings_folder();
        let params = state::external_params(folder.as_deref());
        // offline, the plugin never reads a clock
        let clock = (config.process_mode != ProcessMode::Offline).then(SystemClock::default);
        self.inputs = layout.main_input_channels.map_or(1, |n| n.get() as usize);
        self.processor = Some(Processor::with_shared(
            self.shared.clone(),
            config.sample_rate,
            config.max_buffer_size as usize,
            &params,
            restored.chain(),
            clock,
        ));
        context.set_latency_samples(processor::latency());
        true
    }

    fn reset(&mut self) {
        if let Some(processor) = &mut self.processor {
            processor.reset();
        }
    }

    fn process(&mut self, buffer: &mut Buffer, _aux: &mut AuxiliaryBuffers, _context: &mut impl ProcessContext<Self>) -> ProcessStatus {
        if let Some(processor) = &mut self.processor {
            processor.process(buffer.as_slice(), self.inputs);
        }
        ProcessStatus::Normal
    }
}

impl ClapPlugin for GuitarFx {
    const CLAP_ID: &'static str = CLAP_ID;
    const CLAP_DESCRIPTION: Option<&'static str> = Some("A guitar amp and effect pedals");
    const CLAP_MANUAL_URL: Option<&'static str> = None;
    const CLAP_SUPPORT_URL: Option<&'static str> = None;
    const CLAP_FEATURES: &'static [ClapFeature] = &[ClapFeature::AudioEffect, ClapFeature::Distortion, ClapFeature::Stereo];
}

clap_wrapper::export_vst3!();
clap_wrapper::export_auv2!();
