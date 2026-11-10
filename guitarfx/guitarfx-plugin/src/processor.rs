//! The plugin's processing (contracts/plugin.md "Behaviour", FR-024): the host's input, its channels averaged to mono, runs through the
//! chain, the output stage at full volume and the feedback guard in pieces of at most `guitarfx.devices.block` frames, as in the
//! application's engine, and the result is written to every output channel. When a piece takes longer than its share of time, the
//! application's overload rule lightens the chain and then mutes it (FR-014); offline, no clock is given and none is read.
//!
//! Everything is made in [`Processor::new`]; [`Processor::process`] allocates, frees, locks and waits for nothing. Editor changes arrive
//! through the mailbox: `process` hands every box it took back, never dropping one.

use crate::mailbox::Mailbox;
use guitarfx_dsp::chain::Chain;
use guitarfx_dsp::feedback_guard::FeedbackGuard;
use guitarfx_dsp::output_stage::OutputStage;
use guitarfx_dsp::oversampler::{stage_delay, LARGEST_FACTOR};
use guitarfx_dsp::params::{Id, Params};
use guitarfx_dsp::settings::ChainParams;
use std::sync::atomic::{AtomicU8, AtomicUsize, Ordering};
use std::sync::Arc;
use std::time::{Duration, Instant};

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

/// The sound's state, as the application's, without *Sound on*: the host starts it again.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
#[repr(u8)]
pub enum State {
    /// Fading in.
    Starting,
    On,
    /// Fading out before a mute.
    Stopping,
    /// Muted by the feedback guard, until the host starts the plugin again.
    MutedFeedback,
    /// Muted after the lightest chain still overloaded, until the host starts the plugin again.
    MutedOverload,
}

impl State {
    const ALL: &[State] = &[State::Starting, State::On, State::Stopping, State::MutedFeedback, State::MutedOverload];
}

/// What the editor and `process` share.
pub struct Shared {
    changes: Mailbox<ChainParams>,
    state: AtomicU8,
    lightening: AtomicUsize,
}

impl Default for Shared {
    fn default() -> Self {
        Self {
            changes: Mailbox::default(),
            state: AtomicU8::new(State::Starting as u8),
            lightening: AtomicUsize::new(0),
        }
    }
}

impl Shared {
    /// The chain to play from the next piece on. A box is made here and freed here (at the next post), never in `process`.
    pub fn post_chain(&self, chain: ChainParams) {
        self.changes.post(Box::new(chain));
    }

    pub fn state(&self) -> State {
        let value = self.state.load(Ordering::Acquire);
        State::ALL.iter().copied().find(|s| *s as u8 == value).unwrap_or(State::Starting)
    }

    /// The lightening steps taken (0 to [`Chain::lightest`]).
    pub fn lightening(&self) -> usize {
        self.lightening.load(Ordering::Acquire)
    }

    fn set_state(&self, state: State) {
        self.state.store(state as u8, Ordering::Release);
    }
}

pub struct Processor<C: Clock> {
    shared: Arc<Shared>,
    rate: f32,
    chain: Chain,
    stage: OutputStage,
    guard: FeedbackGuard,
    /// None offline.
    clock: Option<C>,
    piece: Vec<f32>,
    wanted: ChainParams,
    applied: ChainParams,
    returning: Option<Box<ChainParams>>,
    budget: f64,
    overload_blocks: usize,
    slow: usize,
    lightening: usize,
    stopping: bool,
    muting: State,
    finished: bool,
}

impl<C: Clock> Processor<C> {
    /// Everything `process` needs, for the host's rate; `_max_block` is the host's largest block, which `process` cuts into pieces.
    pub fn new(rate: f32, max_block: usize, params: &Params, chain: ChainParams, clock: Option<C>) -> Self {
        Self::with_shared(Arc::default(), rate, max_block, params, chain, clock)
    }

    /// As [`Processor::new`], with what the editor already holds: the state starts again, a change waiting in the mailbox is kept.
    pub fn with_shared(shared: Arc<Shared>, rate: f32, _max_block: usize, params: &Params, chain: ChainParams, clock: Option<C>) -> Self {
        shared.set_state(State::Starting);
        shared.lightening.store(0, Ordering::Release);
        let block = params.get(Id::DevicesBlock) as usize;
        let mut elements = Chain::new(rate, block, params);
        elements.apply(Some(&chain));
        // the plugin has no volume: the host's own fader follows it
        let mut stage = OutputStage::new(rate, params, 1.0);
        stage.start();
        Self {
            shared,
            rate,
            chain: elements,
            stage,
            guard: FeedbackGuard::new(rate, block, params),
            clock,
            piece: vec![0.0; block],
            wanted: chain,
            applied: chain,
            returning: None,
            budget: params.get(Id::OverloadBudget),
            overload_blocks: params.get(Id::OverloadBlocks) as usize,
            slow: 0,
            lightening: 0,
            stopping: false,
            muting: State::MutedOverload,
            finished: false,
        }
    }

    pub fn shared(&self) -> Arc<Shared> {
        self.shared.clone()
    }

    /// The chain in use.
    pub fn applied(&self) -> ChainParams {
        self.applied
    }

    /// The host starts the plugin again (after a stop, a mute or a jump): the whole chain, from silence, faded in.
    pub fn reset(&mut self) {
        self.lightening = 0;
        self.chain.lighten(0);
        self.chain.reset();
        self.guard.reset();
        self.stage.start();
        self.slow = 0;
        self.stopping = false;
        self.finished = false;
        self.shared.lightening.store(0, Ordering::Release);
        self.shared.set_state(State::Starting);
    }

    /// The host's buffer, in place: the input in its first `inputs` channels, the output written to every channel.
    pub fn process(&mut self, channels: &mut [&mut [f32]], inputs: usize) {
        let Some(frames) = channels.first().map(|c| c.len()) else {
            return;
        };
        let inputs = inputs.clamp(1, channels.len());
        let scale = 1.0 / inputs as f32;
        let mut offset = 0;
        while offset < frames {
            let n = self.piece.len().min(frames - offset);
            for (i, sample) in self.piece[..n].iter_mut().enumerate() {
                *sample = channels[..inputs].iter().map(|c| c[offset + i]).sum::<f32>() * scale;
            }
            self.run(n);
            for channel in channels.iter_mut() {
                channel[offset..offset + n].copy_from_slice(&self.piece[..n]);
            }
            offset += n;
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

    fn mute(&mut self, state: State) {
        self.muting = state;
        self.stopping = true;
        self.stage.stop();
        self.shared.set_state(State::Stopping);
    }

    /// One piece of `n` frames, in `self.piece`.
    fn run(&mut self, n: usize) {
        if self.finished {
            self.piece[..n].fill(0.0);
            return;
        }
        self.take_changes();
        if self.stopping && self.stage.is_silent() {
            self.finished = true;
            self.shared.set_state(self.muting);
            self.piece[..n].fill(0.0);
            return;
        }
        if self.wanted != self.applied {
            self.chain.apply(Some(&self.wanted));
            self.applied = self.wanted;
        }
        let began = self.clock.as_ref().map(|c| c.nanos());
        let piece = &mut self.piece[..n];
        self.chain.process(piece);
        self.stage.set_volume(1.0);
        self.stage.process(piece);
        // watched once faded in: the fade in raises every note, which the rising rule would take for a howl
        if !self.stopping && self.stage.is_faded_in() && self.guard.process(piece) {
            self.mute(State::MutedFeedback);
        }
        if let (Some(began), Some(clock)) = (began, self.clock.as_ref()) {
            let piece_nanos = Duration::from_secs(1).as_nanos() as f64 * n as f64 / f64::from(self.rate);
            if (clock.nanos() - began) as f64 > self.budget * piece_nanos {
                self.slow += 1;
            } else {
                self.slow = 0;
            }
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

/// The latency reported to the host (inventory.md "Reported latency"): the delay of the amp's oversampling at its largest factor, which is
/// always there, up and down, rounded down. The drives' oversamplers come and go with the chain, which a host cannot follow.
pub fn latency() -> u32 {
    let mut delay = 0.0;
    let mut factor = 2;
    while factor <= LARGEST_FACTOR {
        // at `factor` times the rate: a half-band stage on the way up, and one on the way down, which keeps the sample just before its
        // filter's middle (measured in tests/render.rs)
        delay += (2.0 * stage_delay() - 1.0) / factor as f64;
        factor *= 2;
    }
    delay as u32
}
