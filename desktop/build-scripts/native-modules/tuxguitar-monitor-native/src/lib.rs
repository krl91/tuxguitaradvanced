//! The duplex audio device of TuxGuitar's amp sound (spec 022, research R1 addendum). It opens the guitar's input and the chosen output
//! through `cpal` (CoreAudio, ALSA, WASAPI) and moves samples between their callbacks and Java through two lock-free rings. No effect
//! runs here: the chain is the Java of the audio engine.

pub mod analysis;
pub mod device;
mod jni_bridge;
mod jni_recognition;
mod latency;
pub mod realtime;
pub mod resampler;
pub mod ring;
#[cfg(test)]
mod no_allocation;
