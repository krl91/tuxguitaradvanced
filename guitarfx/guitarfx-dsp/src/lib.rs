//! GuitarFX's effects: the ported elements of spec 022, the chain, the safety and the parameter registry (spec 027).
//!
//! Each element is a line-for-line port of its Java class in TuxGuitarAdvanced's audio engine (`monitor/fx`): `f64` where Java has
//! `double`, `f32` where it has `float`, and the same order of operations, so that `tests/java_reference.rs` finds the same output within
//! −90 dBFS. Every buffer is made when an element is made; `set`, `process` and `reset` never allocate. Every internal value comes from
//! [`params::Params`]; the numbers left in the code are named in `specs/027-standalone-amp/inventory.md`.

pub mod amp;
pub mod biquad;
pub mod cabinet;
pub mod chain;
pub mod chain_text;
pub mod chorus;
pub mod compressor;
pub mod distortion;
pub mod echo;
pub mod feedback_guard;
pub mod gate;
pub mod knobs;
pub mod output_stage;
pub mod overdrive;
pub mod oversampler;
pub mod params;
pub mod pedals;
pub mod ready_made;
pub mod reverb;
pub mod settings;
