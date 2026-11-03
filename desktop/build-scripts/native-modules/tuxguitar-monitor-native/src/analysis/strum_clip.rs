//! The level of the first strum after a check begins (port of the audio engine's `StrumClipMeter`, spec 023 research R2): it arms on a hop
//! whose level reaches the floor and rises 12 dB above the quietest of the 60 hops before it, then counts, over the next 300 ms in samples,
//! the samples at the clip level and the mean level, and gives a verdict. Counters only on the analysis thread; the reading is published
//! through atomics, and a reset from another thread hides the old verdict at once.

use std::sync::atomic::{AtomicBool, AtomicU32, AtomicU64, Ordering};

/// `StrumVerdict` ordinals.
pub const NO_STRUM: i32 = 0;
pub const CLIPPING: i32 = 1;
pub const TOO_LOUD: i32 = 2;
pub const TOO_WEAK: i32 = 3;
pub const GOOD: i32 = 4;

const SILENCE_DBFS: f64 = -120.0;
const WINDOW_NANOS: f64 = 300_000_000.0;
const ARM_QUIET_HOPS: usize = 60;
const ATTACK_RISE_DB: f64 = 12.0;
const CLIP_SHARE: f64 = 0.005;

/// The three levels taken from the recognition settings (`StrumClipMeter.Parameters.of`).
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct StrumParameters {
    pub arm_floor_dbfs: f64,
    pub clip_sample: f64,
    pub weak_dbfs: f64,
    pub loud_dbfs: f64,
}

impl StrumParameters {
    pub fn defaults() -> StrumParameters {
        StrumParameters { arm_floor_dbfs: -40.0, clip_sample: 0.99, weak_dbfs: -40.0, loud_dbfs: -6.0 }
    }
}

/// One consistent reading: the verdict, the clipped share (0 before a verdict) and the mean level (NaN before a verdict).
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct StrumReading {
    pub verdict: i32,
    pub clipped_share: f64,
    pub rms_dbfs: f64,
}

pub const NONE: StrumReading = StrumReading { verdict: NO_STRUM, clipped_share: 0.0, rms_dbfs: f64::NAN };

/// The part shared with other threads: the generation of the check, the reset request and the last finished window.
pub struct StrumShared {
    generation: AtomicU32,
    reset_requested: AtomicBool,
    finished_generation: AtomicU32,
    finished_present: AtomicBool,
    verdict: AtomicU32,
    share: AtomicU64,
    rms: AtomicU64,
    sequence: AtomicU32,
}

impl Default for StrumShared {
    fn default() -> StrumShared {
        StrumShared {
            generation: AtomicU32::new(0),
            reset_requested: AtomicBool::new(false),
            finished_generation: AtomicU32::new(0),
            finished_present: AtomicBool::new(false),
            verdict: AtomicU32::new(0),
            share: AtomicU64::new(0),
            rms: AtomicU64::new(0),
            sequence: AtomicU32::new(0),
        }
    }
}

impl StrumShared {
    /// Starts a new check, from any thread.
    pub fn reset(&self) {
        self.generation.fetch_add(1, Ordering::SeqCst);
        self.reset_requested.store(true, Ordering::SeqCst);
    }

    /// The last finished window of the current check, or no strum.
    pub fn snapshot(&self) -> StrumReading {
        loop {
            let before = self.sequence.load(Ordering::Acquire);
            if before & 1 == 1 {
                std::hint::spin_loop();
                continue;
            }
            let present = self.finished_present.load(Ordering::Acquire);
            let generation = self.finished_generation.load(Ordering::Acquire);
            let reading = StrumReading {
                verdict: self.verdict.load(Ordering::Acquire) as i32,
                clipped_share: f64::from_bits(self.share.load(Ordering::Acquire)),
                rms_dbfs: f64::from_bits(self.rms.load(Ordering::Acquire)),
            };
            if self.sequence.load(Ordering::Acquire) == before {
                return if present && generation == self.generation.load(Ordering::SeqCst) { reading } else { NONE };
            }
        }
    }

    fn publish(&self, generation: u32, reading: StrumReading) {
        self.sequence.fetch_add(1, Ordering::AcqRel);
        self.finished_generation.store(generation, Ordering::Release);
        self.verdict.store(reading.verdict as u32, Ordering::Release);
        self.share.store(reading.clipped_share.to_bits(), Ordering::Release);
        self.rms.store(reading.rms_dbfs.to_bits(), Ordering::Release);
        self.finished_present.store(true, Ordering::Release);
        self.sequence.fetch_add(1, Ordering::AcqRel);
    }
}

/// The analysis thread's state.
pub struct StrumClip {
    p: StrumParameters,
    sample_rate: i32,
    window_samples: i32,
    history: [f32; ARM_QUIET_HOPS],
    history_count: usize,
    history_next: usize,
    armed: bool,
    done: bool,
    arm_generation: u32,
    counted: i32,
    clipped: i32,
    sum_squares: f64,
}

impl StrumClip {
    pub fn new(p: StrumParameters) -> StrumClip {
        StrumClip {
            p,
            sample_rate: 0,
            window_samples: 0,
            history: [0.0; ARM_QUIET_HOPS],
            history_count: 0,
            history_next: 0,
            armed: false,
            done: false,
            arm_generation: 0,
            counted: 0,
            clipped: 0,
            sum_squares: 0.0,
        }
    }

    pub fn set_parameters(&mut self, p: StrumParameters) {
        self.p = p;
    }

    /// The rate of the hops that follow: any check in progress is dropped and its verdict hidden.
    pub fn configure(&mut self, sample_rate: i32, shared: &StrumShared) {
        self.sample_rate = sample_rate;
        self.window_samples = super::comb::java_round(WINDOW_NANOS / 1e9 * sample_rate as f64) as i32;
        shared.generation.fetch_add(1, Ordering::SeqCst);
        self.clear();
    }

    pub fn hop(&mut self, samples: &[f32], shared: &StrumShared) {
        if shared.reset_requested.swap(false, Ordering::SeqCst) {
            self.clear();
        }
        let length = samples.len();
        if self.sample_rate <= 0 || length == 0 || self.done {
            return;
        }
        if !self.armed {
            let mut sum = 0.0;
            for s in samples {
                sum += (*s as f64) * (*s as f64);
            }
            let db = to_dbfs(sum / length as f64);
            if self.history_count > 0 && db >= self.p.arm_floor_dbfs && db >= self.quietest() + ATTACK_RISE_DB {
                self.armed = true;
                self.arm_generation = shared.generation.load(Ordering::SeqCst);
            } else {
                self.history[self.history_next] = db as f32;
                self.history_next = (self.history_next + 1) % ARM_QUIET_HOPS;
                self.history_count = (self.history_count + 1).min(ARM_QUIET_HOPS);
                return;
            }
        }
        let take = (length as i32).min(self.window_samples - self.counted) as usize;
        let clip_level = self.p.clip_sample;
        for &value in &samples[..take] {
            if (value as f64).abs() >= clip_level {
                self.clipped += 1;
            }
            self.sum_squares += (value as f64) * (value as f64);
        }
        self.counted += take as i32;
        if self.counted >= self.window_samples {
            self.finish(shared);
        }
    }

    fn finish(&mut self, shared: &StrumShared) {
        let share = self.clipped as f64 / self.window_samples as f64;
        let rms = to_dbfs(self.sum_squares / self.window_samples as f64);
        let verdict = if share >= CLIP_SHARE - 1e-12 {
            CLIPPING
        } else if rms > self.p.loud_dbfs {
            TOO_LOUD
        } else if rms < self.p.weak_dbfs {
            TOO_WEAK
        } else {
            GOOD
        };
        self.done = true;
        shared.publish(self.arm_generation, StrumReading { verdict, clipped_share: share, rms_dbfs: rms });
    }

    fn clear(&mut self) {
        self.history_count = 0;
        self.history_next = 0;
        self.armed = false;
        self.done = false;
        self.counted = 0;
        self.clipped = 0;
        self.sum_squares = 0.0;
    }

    fn quietest(&self) -> f64 {
        let mut quietest = f64::INFINITY;
        for &h in &self.history[..self.history_count] {
            quietest = quietest.min(h as f64);
        }
        quietest
    }
}

fn to_dbfs(mean_square: f64) -> f64 {
    if mean_square <= 1e-12 {
        SILENCE_DBFS
    } else {
        10.0 * mean_square.log10()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn run(level: f32, clip_every: usize) -> StrumReading {
        let shared = StrumShared::default();
        let mut meter = StrumClip::new(StrumParameters::defaults());
        meter.configure(48000, &shared);
        for _ in 0..10 {
            meter.hop(&[0.0005; 256], &shared);
        }
        for h in 0..80 {
            let hop: Vec<f32> = (0..256).map(|i| if clip_every > 0 && (h * 256 + i) % clip_every == 0 { 1.0 } else { level * if i % 2 == 0 { 1.0 } else { -1.0 } }).collect();
            meter.hop(&hop, &shared);
        }
        shared.snapshot()
    }

    #[test]
    fn verdicts_follow_the_levels_and_the_clipped_share() {
        assert_eq!(run(0.1, 0).verdict, GOOD);
        assert_eq!(run(0.7, 0).verdict, TOO_LOUD);
        let clipped = run(0.1, 100);
        assert_eq!(clipped.verdict, CLIPPING);
        assert!((clipped.clipped_share - 0.01).abs() < 0.001, "{clipped:?}");
    }

    #[test]
    fn a_reset_hides_the_verdict_and_starts_again() {
        let shared = StrumShared::default();
        let mut meter = StrumClip::new(StrumParameters::defaults());
        meter.configure(48000, &shared);
        meter.hop(&[0.0005; 256], &shared);
        for _ in 0..60 {
            meter.hop(&[0.1; 256], &shared);
        }
        assert_eq!(shared.snapshot().verdict, GOOD);
        shared.reset();
        assert_eq!(shared.snapshot().verdict, NO_STRUM);
        assert!(shared.snapshot().rms_dbfs.is_nan());
    }
}
