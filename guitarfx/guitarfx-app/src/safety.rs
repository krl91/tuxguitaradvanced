//! The safety messages (FR-012, SC-005, contracts/application.md "Messages"): what the window says when the engine mutes a howl, lightens
//! the chain or gives up under overload, and when the input clips. The application reads the engine each frame and shows the outcome.

use crate::devices;
use crate::engine::State;
use std::time::{Duration, Instant};

/// A repeated message (the clipping) is shown at most this often, and the clip light stays on this long.
pub fn notice_interval() -> Duration {
    Duration::from_secs(1)
}

/// What the engine reports, read once per frame.
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct Reading {
    pub state: State,
    pub lightening: usize,
    pub clipped: u64,
}

/// What the window does with it.
#[derive(Clone, Debug, Default, PartialEq)]
pub struct Outcome {
    /// The key of the message to show on the message line.
    pub message: Option<String>,
    /// The message also needs an OK.
    pub dialog: bool,
    /// The sound is off: the devices are closed and the switch turned off.
    pub off: bool,
    /// The meter's clip light.
    pub clipping: bool,
}

/// Turning the sound on with the built-in microphone first asks, unless the guitarist chose *Turn on anyway* (FR-012).
pub fn needs_warning(input: &str, warned: bool) -> bool {
    !warned && devices::is_built_in_microphone(input)
}

/// What was already said, so that each step is said once.
#[derive(Debug, Default)]
pub struct Watch {
    lightening: usize,
    clipped: u64,
    clipping_until: Option<Instant>,
    last_clipping_notice: Option<Instant>,
}

impl Watch {
    pub fn new() -> Watch {
        Watch::default()
    }

    pub fn step(&mut self, reading: &Reading, now: Instant) -> Outcome {
        let mut outcome = Outcome::default();
        if reading.clipped > self.clipped {
            self.clipped = reading.clipped;
            self.clipping_until = Some(now + notice_interval());
            if self.last_clipping_notice.is_none_or(|t| now - t >= notice_interval()) {
                self.last_clipping_notice = Some(now);
                outcome.message = Some("message.clipping".to_string());
            }
        }
        outcome.clipping = self.clipping_until.is_some_and(|t| now < t);
        if reading.lightening > self.lightening {
            self.lightening = reading.lightening;
            outcome.message = Some(format!("message.lighter.{}", reading.lightening));
        }
        match reading.state {
            State::Off => outcome.off = true,
            State::MutedFeedback => {
                outcome.off = true;
                outcome.dialog = true;
                outcome.message = Some("message.feedback".to_string());
            }
            State::MutedOverload => {
                outcome.off = true;
                outcome.message = Some("message.overload".to_string());
            }
            State::Starting | State::On | State::Stopping => {}
        }
        outcome
    }
}
