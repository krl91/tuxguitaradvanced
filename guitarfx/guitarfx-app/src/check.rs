//! `guitarfx --check` (contracts/application.md "Headless start"): no window and no device. It loads the settings (writing nothing), the
//! texts and the registry, builds the current sound's chain at 48 kHz, runs one second of silence through it, the output stage and the
//! feedback guard, and says `ok`. Every CI system runs it after installing the package.

use crate::app;
use guitarfx_dsp::chain::Chain;
use guitarfx_dsp::feedback_guard::FeedbackGuard;
use guitarfx_dsp::output_stage::OutputStage;
use guitarfx_dsp::params::Id;
use guitarfx_settings::folder::settings_folder;
use guitarfx_settings::settings::Settings;
use guitarfx_settings::texts::Texts;

/// The rate of the check, the most common device rate.
const RATE: f32 = 48000.0;

pub fn run() -> Result<(), String> {
    let settings = settings_folder().map(|f| Settings::read_only(&f)).unwrap_or_default();
    let texts = Texts::english();
    if texts.get("app.title") == "app.title" {
        return Err("the texts are missing".to_string());
    }
    let params = settings.params;
    let block = params.get(Id::DevicesBlock) as usize;
    let mut chain = Chain::new(RATE, block, &params);
    chain.apply(Some(&app::current_chain(&settings)));
    let mut stage = OutputStage::new(RATE, &params, settings.volume);
    stage.start();
    let mut guard = FeedbackGuard::new(RATE, block, &params);
    let mut buffer = vec![0.0f32; block];
    for _ in 0..(RATE as usize).div_ceil(block) {
        buffer.fill(0.0);
        chain.process(&mut buffer);
        stage.process(&mut buffer);
        guard.process(&buffer);
        if buffer.iter().any(|s| !s.is_finite() || s.abs() > 1.0) {
            return Err("the chain gave a sample out of range".to_string());
        }
    }
    Ok(())
}
