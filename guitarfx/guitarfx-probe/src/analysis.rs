//! The clicks and how they are found, as spec 022's probes do (`MonitorLatencyProbeTest`, `NativeMonitorProbeTest`): 20 clicks of 1 ms,
//! 3 s apart, the first one 3 s after the start (the sound has faded in by then). Each click goes into GuitarFX's engine at a known frame
//! of the input; it is found in the input's own recording, where the output brought it back through the air, as the first sample above
//! the threshold of `MonitorLatencyProbeTest`. The delay is the distance between the two, in the input's frames: what a note played at
//! that frame waits before it is heard.

use std::time::Duration;

/// `MonitorLatencyProbeTest`: 3 s between clicks.
const CLICK_SPACING: Duration = Duration::from_secs(3);
/// 1 ms of click.
const CLICK_LENGTH: Duration = Duration::from_millis(1);
/// `MonitorLatencyProbeTest`: the threshold is 600 in 16 bits…
const FLOOR: f32 = 600.0 / 32768.0;
/// …or 8 times the noise's RMS…
const NOISE_TIMES: f64 = 8.0;
/// …over the 0.1 s before the click (4800 frames at 48 kHz),
const NOISE_SPAN: Duration = Duration::from_millis(100);
/// and the click is looked for over the 0.3 s after it (14400 frames at 48 kHz).
const WINDOW: Duration = Duration::from_millis(300);

/// When the clicks are put into the engine, at one input rate.
#[derive(Clone, Copy, Debug)]
pub struct Schedule {
    pub rate: f32,
    pub clicks: usize,
    pub spacing: usize,
    pub click_frames: usize,
    noise_frames: usize,
    window_frames: usize,
}

fn frames(rate: f32, span: Duration) -> usize {
    (f64::from(rate) * span.as_secs_f64()).round() as usize
}

impl Schedule {
    pub fn new(rate: f32, clicks: usize) -> Schedule {
        Schedule {
            rate,
            clicks,
            spacing: frames(rate, CLICK_SPACING),
            click_frames: frames(rate, CLICK_LENGTH),
            noise_frames: frames(rate, NOISE_SPAN),
            window_frames: frames(rate, WINDOW),
        }
    }

    /// The frames to record: every click, and half a spacing after the last.
    pub fn total_frames(&self) -> usize {
        self.spacing * (self.clicks + 1) + self.spacing / 2
    }

    /// The value put into the engine at input frame `frame`: 0.8 in a click (spec 022's), silence elsewhere.
    pub fn click_value(&self, frame: usize) -> f32 {
        let click = frame / self.spacing;
        if click >= 1 && click <= self.clicks && frame % self.spacing < self.click_frames {
            0.8
        } else {
            0.0
        }
    }
}

/// The input frame at which click `index` (0 for the first) goes into the engine.
pub fn click_start(schedule: &Schedule, index: usize) -> usize {
    schedule.spacing * (index + 1)
}

/// Each click found in `captured` (the input's own recording), as its delay in milliseconds.
pub fn find_delays(captured: &[f32], schedule: &Schedule) -> Vec<f64> {
    let mut delays = Vec::new();
    for index in 0..schedule.clicks {
        let start = click_start(schedule, index);
        if start >= captured.len() {
            break;
        }
        let from = start.saturating_sub(schedule.noise_frames);
        let sum: f64 = captured[from..start].iter().map(|s| f64::from(*s) * f64::from(*s)).sum();
        let rms = if start > from { (sum / (start - from) as f64).sqrt() } else { 0.0 };
        let threshold = f64::from(FLOOR).max(NOISE_TIMES * rms);
        let end = captured.len().min(start + schedule.window_frames);
        if let Some(at) = (start..end).find(|&i| f64::from(captured[i].abs()) > threshold) {
            delays.push((at - start) as f64 * Duration::from_secs(1).as_millis() as f64 / f64::from(schedule.rate));
        }
    }
    delays
}

/// One setting's figures.
#[derive(Clone, Debug)]
pub struct Summary {
    pub block: usize,
    pub rate: u32,
    /// The delays found, in milliseconds.
    pub found: Vec<f64>,
    pub clicks: usize,
    /// The window's estimate, in milliseconds.
    pub estimate: f64,
    pub dropped: u64,
    pub underruns: u64,
}

fn tenth(value: f64) -> String {
    format!("{value:.1}")
}

/// The header of spec 022's table (`native-probe-usb.md`), with the engine's underruns where spec 022 counted silent reads.
pub const HEADER: &str = "| Block (frames) | Rate (Hz) | Clicks found | Median (ms) | Min (ms) | Max (ms) | Estimate (ms) | Median − estimate (ms) | Dropped blocks | Underruns |\n|---|---|---|---|---|---|---|---|---|---|";

/// The setting's row of the table.
pub fn row(summary: &Summary) -> String {
    let mut sorted = summary.found.clone();
    sorted.sort_by(f64::total_cmp);
    let (median, min, max, gap) = match (sorted.get(sorted.len() / 2), sorted.first(), sorted.last()) {
        (Some(median), Some(min), Some(max)) => (tenth(*median), tenth(*min), tenth(*max), tenth(median - summary.estimate)),
        _ => ("-".into(), "-".into(), "-".into(), "-".into()),
    };
    format!(
        "| {} | {} | {} / {} | {median} | {min} | {max} | {} | {gap} | {} | {} |",
        summary.block,
        summary.rate,
        summary.found.len(),
        summary.clicks,
        tenth(summary.estimate),
        summary.dropped,
        summary.underruns
    )
}
