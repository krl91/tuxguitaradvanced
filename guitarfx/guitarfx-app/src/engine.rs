//! The engine (spec 027, research R1 and R10): the input callback pushes the chosen channel into a ring; the output callback takes blocks
//! of `guitarfx.devices.block` frames from it (resampled to the output's rate when asked), runs the chain, the output stage and the feedback
//! guard on them, and writes the result to every output channel it fills. It is the loop of spec 022's `MonitorEngine.run`, moved into the
//! output callback.
//!
//! Changes reach the output callback without blocking: the chain through the mailbox (the callback gives every box it took back for the UI
//! thread to free, never dropping one), the volume and the stop through atomics. Everything is made in [`build`]; the callbacks allocate,
//! free, lock and wait for nothing.

use crate::resample::Resampler;
use guitarfx_dsp::chain::Chain;
use guitarfx_dsp::feedback_guard::FeedbackGuard;
use guitarfx_dsp::output_stage::OutputStage;
use guitarfx_dsp::params::{Id, Params};
use guitarfx_dsp::settings::ChainParams;
use std::sync::atomic::{AtomicBool, AtomicU32, AtomicU64, AtomicU8, AtomicUsize, Ordering};
use std::sync::Arc;
use std::time::{Duration, Instant};
use tuxguitar_monitor_native::recognition::Mailbox;
use tuxguitar_monitor_native::ring::Ring;

/// The most frames of one callback copied at a time (the native crate's `device.rs` `CHUNK`); a larger callback is copied in parts.
const LARGEST_CALLBACK: usize = 4096;
/// The ring holds this many of the largest callbacks, far more than it keeps (the native crate's `device.rs`).
const RING_CALLBACKS: usize = 16;
/// A 16-bit sample's scale, in and out.
const I16_IN: f32 = 32768.0;
const I16_OUT: f32 = 32767.0;
/// A 32-bit sample's scale, in and out.
const I32_IN: f64 = i32::MAX as f64 + 1.0;
const I32_OUT: f64 = i32::MAX as f64;

/// A 16-bit sample as a float.
pub fn from_i16(sample: i16) -> f32 {
    f32::from(sample) / I16_IN
}

/// A float as a 16-bit sample.
pub fn to_i16(sample: f32) -> i16 {
    (sample.clamp(-1.0, 1.0) * I16_OUT) as i16
}

/// A 32-bit sample as a float.
pub fn from_i32(sample: i32) -> f32 {
    (f64::from(sample) / I32_IN) as f32
}

/// A float as a 32-bit sample.
pub fn to_i32(sample: f32) -> i32 {
    (f64::from(sample.clamp(-1.0, 1.0)) * I32_OUT) as i32
}

/// The time of the overload rule: the system's clock, or a test's.
pub trait Clock: Send + 'static {
    fn nanos(&self) -> u64;
}

pub struct SystemClock(Instant);

impl Default for SystemClock {
    fn default() -> Self {
        Self(Instant::now())
    }
}

impl Clock for SystemClock {
    fn nanos(&self) -> u64 {
        self.0.elapsed().as_nanos() as u64
    }
}

/// The opened devices, as the engine sees them.
#[derive(Clone, Debug)]
pub struct Config {
    pub input_rate: f32,
    pub output_rate: f32,
    pub input_channels: usize,
    /// 0-based; a channel the input does not have is the first.
    pub channel: usize,
    pub output_channels: usize,
    /// Resample the input to the output's rate, with the ring's fill held at its target (two devices' clocks drift).
    pub resample: bool,
}

/// The sound's state, as spec 022's `MonitorState`.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
#[repr(u8)]
pub enum State {
    /// Fading in.
    Starting,
    On,
    /// Fading out, after a stop or before a mute.
    Stopping,
    Off,
    /// Muted by the feedback guard, until the next start.
    MutedFeedback,
    /// Muted after the lightest chain still overloaded, until the next start.
    MutedOverload,
}

impl State {
    const ALL: &[State] = &[
        State::Starting,
        State::On,
        State::Stopping,
        State::Off,
        State::MutedFeedback,
        State::MutedOverload,
    ];
}

struct Shared {
    ring: Ring,
    changes: Mailbox<ChainParams>,
    volume: AtomicU64,
    stop: AtomicBool,
    state: AtomicU8,
    lightening: AtomicUsize,
    dropped_blocks: AtomicU64,
    underruns: AtomicU64,
    slow_blocks: AtomicU64,
    clipped_blocks: AtomicU64,
    peak: AtomicU32,
    block: usize,
}

impl Shared {
    fn set_state(&self, state: State) {
        self.state.store(state as u8, Ordering::Release);
    }
}

/// The UI thread's side.
pub struct Control {
    shared: Arc<Shared>,
}

impl Control {
    /// The chain to play from the next block on. A box is made here and freed here (at the next post), never on the audio thread.
    pub fn post_chain(&self, chain: ChainParams) {
        self.shared.changes.post(Box::new(chain));
    }

    /// From 0 to 1.
    pub fn set_volume(&self, volume: f64) {
        self.shared.volume.store(volume.clamp(0.0, 1.0).to_bits(), Ordering::Release);
    }

    /// Fades out; the state becomes [`State::Off`] once silent.
    pub fn stop(&self) {
        self.shared.stop.store(true, Ordering::Release);
    }

    pub fn state(&self) -> State {
        let value = self.shared.state.load(Ordering::Acquire);
        State::ALL.iter().copied().find(|s| *s as u8 == value).unwrap_or(State::Off)
    }

    /// The lightening steps taken (0 to [`Chain::lightest`]).
    pub fn lightening(&self) -> usize {
        self.shared.lightening.load(Ordering::Acquire)
    }

    pub fn dropped_blocks(&self) -> u64 {
        self.shared.dropped_blocks.load(Ordering::Acquire)
    }

    pub fn underruns(&self) -> u64 {
        self.shared.underruns.load(Ordering::Acquire)
    }

    pub fn slow_blocks(&self) -> u64 {
        self.shared.slow_blocks.load(Ordering::Acquire)
    }

    /// The input callbacks whose peak reached `guitarfx.devices.clipping`.
    pub fn clipped_blocks(&self) -> u64 {
        self.shared.clipped_blocks.load(Ordering::Acquire)
    }

    /// The input's peak since the last call, linear.
    pub fn take_peak(&self) -> f32 {
        f32::from_bits(self.shared.peak.swap(0, Ordering::AcqRel))
    }

    pub fn block(&self) -> usize {
        self.shared.block
    }
}

/// The input callback's side.
pub struct InputSide {
    shared: Arc<Shared>,
    channels: usize,
    channel: usize,
    clipping: f32,
    mono: Vec<f32>,
}

impl InputSide {
    /// Pushes the chosen channel of interleaved frames, converted by `convert`.
    pub fn on_input<S: Copy>(&mut self, data: &[S], convert: impl Fn(S) -> f32) {
        let mut peak = 0f32;
        for frames in data.chunks(LARGEST_CALLBACK * self.channels) {
            let n = frames.len() / self.channels;
            for (i, mono) in self.mono[..n].iter_mut().enumerate() {
                *mono = convert(frames[i * self.channels + self.channel]);
                peak = peak.max(mono.abs());
            }
            self.shared.ring.push(&self.mono[..n]);
        }
        // a positive float's bits order as the float does
        self.shared.peak.fetch_max(peak.to_bits(), Ordering::AcqRel);
        if peak >= self.clipping {
            self.shared.clipped_blocks.fetch_add(1, Ordering::AcqRel);
        }
    }
}

/// The output callback's side: the chain and everything it needs, made in [`build`].
pub struct OutputSide<C: Clock> {
    shared: Arc<Shared>,
    channels: usize,
    chain: Chain,
    stage: OutputStage,
    guard: FeedbackGuard,
    resampler: Option<Resampler>,
    clock: C,
    block: Vec<f32>,
    /// The next frame of `block` to write; `block.len()` when a new block is needed.
    position: usize,
    keep: usize,
    wanted: ChainParams,
    applied: ChainParams,
    returning: Option<Box<ChainParams>>,
    budget: f64,
    overload_blocks: usize,
    slow: usize,
    lightening: usize,
    stopping: bool,
    muting: Option<State>,
    finished: bool,
    started: bool,
}

/// Makes the engine for these devices: everything the callbacks need is allocated here.
pub fn build<C: Clock>(config: &Config, params: &Params, chain: ChainParams, volume: f64, clock: C) -> (InputSide, OutputSide<C>, Control) {
    let block = params.get(Id::DevicesBlock) as usize;
    let rate = config.output_rate;
    let resampler = config
        .resample
        .then(|| Resampler::new(f64::from(config.input_rate), f64::from(rate), block, params));
    let ring_blocks = params.get(Id::DevicesRingBlocks) as usize;
    // resampled, the ring holds its target fill and may hold the ring's blocks over it
    let keep = block * ring_blocks + resampler.as_ref().map_or(0, |r| r.target_fill().ceil() as usize);
    let shared = Arc::new(Shared {
        ring: Ring::new(RING_CALLBACKS * block.max(LARGEST_CALLBACK)),
        changes: Mailbox::default(),
        volume: AtomicU64::new(volume.clamp(0.0, 1.0).to_bits()),
        stop: AtomicBool::new(false),
        state: AtomicU8::new(State::Starting as u8),
        lightening: AtomicUsize::new(0),
        dropped_blocks: AtomicU64::new(0),
        underruns: AtomicU64::new(0),
        slow_blocks: AtomicU64::new(0),
        clipped_blocks: AtomicU64::new(0),
        peak: AtomicU32::new(0),
        block,
    });
    let mut elements = Chain::new(rate, block, params);
    elements.apply(Some(&chain));
    let mut stage = OutputStage::new(rate, params, volume);
    stage.start();
    let block_nanos = Duration::from_secs(1).as_nanos() as f64 * block as f64 / f64::from(rate);
    let input_channels = config.input_channels.max(1);
    let input = InputSide {
        shared: shared.clone(),
        channels: input_channels,
        channel: if config.channel < input_channels { config.channel } else { 0 },
        clipping: 10f64.powf(params.get(Id::DevicesClipping) / 20.0) as f32,
        mono: vec![0.0; LARGEST_CALLBACK],
    };
    let output = OutputSide {
        shared: shared.clone(),
        channels: config.output_channels.max(1),
        chain: elements,
        stage,
        guard: FeedbackGuard::new(rate, block, params),
        resampler,
        clock,
        block: vec![0.0; block],
        position: block,
        keep,
        wanted: chain,
        applied: chain,
        returning: None,
        budget: params.get(Id::OverloadBudget) * block_nanos,
        overload_blocks: params.get(Id::OverloadBlocks) as usize,
        slow: 0,
        lightening: 0,
        stopping: false,
        muting: None,
        finished: false,
        started: false,
    };
    (input, output, Control { shared })
}

impl<C: Clock> OutputSide<C> {
    /// Fills interleaved frames, converted by `convert`: the sound in the first two channels (or the one), silence in the others.
    pub fn on_output<S: Copy>(&mut self, data: &mut [S], convert: impl Fn(f32) -> S) {
        let silent = convert(0.0);
        for frame in data.chunks_mut(self.channels) {
            if self.position >= self.block.len() {
                self.next_block();
                self.position = 0;
            }
            let value = convert(self.block[self.position]);
            self.position += 1;
            for (c, slot) in frame.iter_mut().enumerate() {
                *slot = if c < 2 { value } else { silent };
            }
        }
    }

    /// Hands back a box taken from the mailbox, then takes the newest chain.
    fn take_changes(&mut self) {
        if let Some(value) = self.returning.take() {
            self.returning = self.shared.changes.give_back(value);
        }
        if self.returning.is_none() {
            if let Some(value) = self.shared.changes.take() {
                self.wanted = *value;
                self.returning = self.shared.changes.give_back(value);
            }
        }
    }

    fn read_input(&mut self) {
        let ring = &self.shared.ring;
        if let Some(resampler) = &mut self.resampler {
            let before = resampler.underruns();
            self.shared
                .dropped_blocks
                .fetch_add(ring.keep_at_most(self.keep, self.block.len()), Ordering::AcqRel);
            resampler.render(ring, &mut self.block);
            self.shared.underruns.fetch_add(resampler.underruns() - before, Ordering::AcqRel);
            return;
        }
        self.shared
            .dropped_blocks
            .fetch_add(ring.keep_at_most(self.keep, self.block.len()), Ordering::AcqRel);
        let frames = ring.pop(&mut self.block);
        if frames > 0 {
            self.started = true;
        }
        if frames < self.block.len() {
            self.block[frames..].fill(0.0);
            if self.started {
                self.shared.underruns.fetch_add(1, Ordering::AcqRel);
            }
        }
    }

    fn mute(&mut self, state: State) {
        self.muting = Some(state);
        self.stopping = true;
        self.stage.stop();
        self.shared.set_state(State::Stopping);
    }

    fn next_block(&mut self) {
        if self.finished {
            self.shared.ring.skip(self.shared.ring.len());
            self.block.fill(0.0);
            return;
        }
        self.take_changes();
        if self.shared.stop.load(Ordering::Acquire) && !self.stopping {
            self.stopping = true;
            self.stage.stop();
            self.shared.set_state(State::Stopping);
        }
        if self.stopping && self.stage.is_silent() {
            self.finished = true;
            self.shared.set_state(self.muting.unwrap_or(State::Off));
            self.block.fill(0.0);
            return;
        }
        self.read_input();
        if self.wanted != self.applied {
            self.chain.apply(Some(&self.wanted));
            self.applied = self.wanted;
        }
        let began = self.clock.nanos();
        self.chain.process(&mut self.block);
        self.stage.set_volume(f64::from_bits(self.shared.volume.load(Ordering::Acquire)));
        self.stage.process(&mut self.block);
        // watched once faded in: the fade in raises every note, which the rising rule would take for a howl
        if !self.stopping && self.stage.is_faded_in() && self.guard.process(&self.block) {
            self.mute(State::MutedFeedback);
        }
        if (self.clock.nanos() - began) as f64 > self.budget {
            self.shared.slow_blocks.fetch_add(1, Ordering::AcqRel);
            self.slow += 1;
        } else {
            self.slow = 0;
        }
        if self.slow >= self.overload_blocks && !self.stopping {
            self.slow = 0;
            if self.lightening < Chain::lightest() {
                self.lightening += 1;
                self.chain.lighten(self.lightening);
                self.shared.lightening.store(self.lightening, Ordering::Release);
            } else {
                self.mute(State::MutedOverload);
            }
        }
        if !self.stopping && self.stage.is_faded_in() && self.shared.state.load(Ordering::Acquire) == State::Starting as u8 {
            self.shared.set_state(State::On);
        }
    }
}
