//! GuitarFX's windows (spec 027, contracts/application.md), shared by the application and the plugin's editor. The views draw a model and
//! return the guitarist's actions; they never touch a device or a file.

pub mod chain_rows;
pub mod devices_bar;
pub mod knobs;
pub mod messages;
pub mod window;
