//! The native live recognition (spec 023, research R4): the analysis thread that reads the capture's blocks, runs the front end, the strum
//! meter and the analysis stages hop by hop (`analysis::pipeline`, the stages of the offline analysis) and writes the frame records and,
//! while a hop listener is registered, the hop blocks into two rings that Java reads.
//!
//! The loop never allocates, locks or waits on Java: what Java changes (the parameters, the attack candidates, the expected pitches and chord)
//! is built on Java's thread and handed over in a mailbox that the loop takes at the top of its next iteration; what it replaces goes back to
//! a second slot of the mailbox, freed by Java's next change or at close. Only Java waits: `next_frames` and `next_hops`, on a condition
//! variable the loop signals.

use crate::analysis::attack::{Attack, AttackAnalyzer, AttackParameters};
use crate::analysis::chord::ExpectedChord;
use crate::analysis::frame;
use crate::analysis::front_end::{FrontEnd, FrontEndSettings};
use crate::analysis::pipeline::{HopResult, Pipeline, Request, HOP};
use crate::analysis::strum_clip::{StrumClip, StrumParameters, StrumShared};
use crate::analysis::Parameters;
use crate::workgroup::Workgroup;
use crate::capture::{Block, CaptureShared, BLOCK};
use crate::ring::Ring;
use std::ptr;
use std::sync::atomic::{AtomicBool, AtomicPtr, AtomicU32, AtomicU64, Ordering};
use std::sync::{Arc, Condvar, Mutex};
use std::time::{Duration, Instant};

/// The floats of one hop block: a header, then the samples. The header is 0 for a hop, or the sample rate for a `configure` marker (then the
/// samples are not used).
pub const HOP_BLOCK: usize = 1 + HOP;
const FRAME_RING: usize = 1 << 18;
const HOP_RING: usize = 64 * HOP_BLOCK;
/// The cost histogram: one bin per microsecond up to 10 ms, the last bin holding the rest.
const COST_BINS: usize = 10_000;

/// A value handed from Java's thread to the loop. `post` (Java, one at a time) frees what the loop gave back and any value not taken yet;
/// `take` and `give_back` (the loop) never free.
pub struct Mailbox<T> {
    pending: AtomicPtr<T>,
    retired: AtomicPtr<T>,
}

impl<T> Default for Mailbox<T> {
    fn default() -> Mailbox<T> {
        Mailbox { pending: AtomicPtr::new(ptr::null_mut()), retired: AtomicPtr::new(ptr::null_mut()) }
    }
}

fn free<T>(p: *mut T) {
    if !p.is_null() {
        // SAFETY: every non-null pointer in a mailbox came from Box::into_raw and is owned by whoever swapped it out
        drop(unsafe { Box::from_raw(p) });
    }
}

impl<T> Mailbox<T> {
    pub fn post(&self, value: Box<T>) {
        free(self.retired.swap(ptr::null_mut(), Ordering::AcqRel));
        free(self.pending.swap(Box::into_raw(value), Ordering::AcqRel));
    }

    pub fn take(&self) -> Option<Box<T>> {
        let p = self.pending.swap(ptr::null_mut(), Ordering::AcqRel);
        // SAFETY: as in `free`
        if p.is_null() { None } else { Some(unsafe { Box::from_raw(p) }) }
    }

    /// Hands back a used value for Java to free; gives it back when the slot is still taken (the loop keeps it and tries again).
    pub fn give_back(&self, value: Box<T>) -> Option<Box<T>> {
        let p = Box::into_raw(value);
        match self.retired.compare_exchange(ptr::null_mut(), p, Ordering::AcqRel, Ordering::Acquire) {
            Ok(_) => None,
            // SAFETY: p was just made from the box
            Err(_) => Some(unsafe { Box::from_raw(p) }),
        }
    }
}

impl<T> Drop for Mailbox<T> {
    fn drop(&mut self) {
        free(self.pending.swap(ptr::null_mut(), Ordering::AcqRel));
        free(self.retired.swap(ptr::null_mut(), Ordering::AcqRel));
    }
}

/// A new configuration: a new pipeline when an analysis value changed (the window is discarded and every state reset, as
/// `AnalysisService.reconfigure`), else only the front end and strum values, applied without a reset.
pub struct Config {
    pub pipeline: Option<Pipeline>,
    pub front: FrontEndSettings,
    pub strum: StrumParameters,
}

/// New attack candidates: the analyser and its evidence (none when off).
pub struct AttackSlot {
    pub analyzer: Option<AttackAnalyzer>,
    pub evidence: Attack,
}

impl AttackSlot {
    pub fn of(rate: i32, candidates: &[i32]) -> AttackSlot {
        if candidates.is_empty() {
            return AttackSlot { analyzer: None, evidence: Attack { pitches: Vec::new(), amplitudes: Vec::new(), attack_strength: 0.0, growth: Vec::new(), bin_hz: 0.0 } };
        }
        let analyzer = AttackAnalyzer::new(AttackParameters::defaults(), rate as f64, candidates);
        let evidence = analyzer.new_evidence();
        AttackSlot { analyzer: Some(analyzer), evidence }
    }
}

/// The cost of each hop's analysis, in microseconds.
pub struct Costs {
    bins: Box<[AtomicU32]>,
}

impl Default for Costs {
    fn default() -> Costs {
        Costs { bins: (0..COST_BINS).map(|_| AtomicU32::new(0)).collect::<Vec<_>>().into_boxed_slice() }
    }
}

impl Costs {
    fn record(&self, nanos: u128) {
        let bin = ((nanos / 1000) as usize).min(COST_BINS - 1);
        self.bins[bin].fetch_add(1, Ordering::Relaxed);
    }

    /// The median and 99th percentile in nanoseconds (the middle of their bin), 0 before any hop.
    pub fn percentiles(&self) -> (f64, f64) {
        let counts: Vec<u64> = self.bins.iter().map(|b| b.load(Ordering::Relaxed) as u64).collect();
        let total: u64 = counts.iter().sum();
        if total == 0 {
            return (0.0, 0.0);
        }
        let at = |share: f64| {
            let rank = (share * total as f64).ceil().max(1.0) as u64;
            let mut seen = 0;
            for (bin, count) in counts.iter().enumerate() {
                seen += count;
                if seen >= rank {
                    return (bin as f64 + 0.5) * 1000.0;
                }
            }
            (COST_BINS as f64 - 0.5) * 1000.0
        };
        (at(0.5), at(0.99))
    }
}

/// What the loop and Java share.
pub struct Shared {
    pub capture: Arc<CaptureShared>,
    pub rate: i32,
    java_nanos_at_open: i64,
    frames: Ring,
    frames_lost: AtomicU64,
    hops: Ring,
    hops_enabled: AtomicBool,
    pub config: Mailbox<Config>,
    pub attack: Mailbox<AttackSlot>,
    pub expected: Mailbox<Vec<i32>>,
    pub chord: Mailbox<Option<ExpectedChord>>,
    spectrum: AtomicBool,
    pub strum: StrumShared,
    pub costs: Costs,
    stop: AtomicBool,
    closed: AtomicBool,
    lock: Mutex<()>,
    signal: Condvar,
}

impl Shared {
    pub fn new(capture: Arc<CaptureShared>, rate: i32, java_nanos_at_open: i64) -> Shared {
        Shared {
            capture,
            rate,
            java_nanos_at_open,
            frames: Ring::new(FRAME_RING),
            frames_lost: AtomicU64::new(0),
            hops: Ring::new(HOP_RING),
            hops_enabled: AtomicBool::new(false),
            config: Mailbox::default(),
            attack: Mailbox::default(),
            expected: Mailbox::default(),
            chord: Mailbox::default(),
            spectrum: AtomicBool::new(false),
            strum: StrumShared::default(),
            costs: Costs::default(),
            stop: AtomicBool::new(false),
            closed: AtomicBool::new(false),
            lock: Mutex::new(()),
            signal: Condvar::new(),
        }
    }

    pub fn set_spectrum(&self, on: bool) {
        self.spectrum.store(on, Ordering::Release);
    }

    pub fn set_hops_enabled(&self, on: bool) {
        self.hops_enabled.store(on, Ordering::Release);
    }

    /// The input blocks dropped because the analysis was late, and the frames Java was too late to read.
    pub fn dropped_blocks(&self) -> u64 {
        self.capture.blocks.dropped()
    }

    pub fn frames_lost(&self) -> u64 {
        self.frames_lost.load(Ordering::Relaxed)
    }

    /// Stops the loop; the waits return −2 from now on.
    pub fn close(&self) {
        self.closed.store(true, Ordering::Release);
        self.stop.store(true, Ordering::Release);
        self.signal.notify_all();
    }

    fn wake(&self) {
        self.signal.notify_all();
    }

    /// Java's wait for whole records (`nextFrames`): how many records were copied into `out`, 0 on timeout, −1 once the stream failed and every
    /// record was read, −2 after close.
    pub fn next_frames(&self, out: &mut [f32], timeout: Duration) -> i32 {
        self.wait(timeout, |shared| shared.copy_records(out))
    }

    /// As `next_frames`, for the hop blocks of `HOP_BLOCK` floats.
    pub fn next_hops(&self, out: &mut [f32], timeout: Duration) -> i32 {
        self.wait(timeout, |shared| {
            let mut count = 0;
            while (count + 1) * HOP_BLOCK <= out.len() && shared.hops.len() >= HOP_BLOCK {
                shared.hops.pop(&mut out[count * HOP_BLOCK..(count + 1) * HOP_BLOCK]);
                count += 1;
            }
            count as i32
        })
    }

    fn wait(&self, timeout: Duration, mut copy: impl FnMut(&Shared) -> i32) -> i32 {
        let deadline = Instant::now() + timeout;
        loop {
            if self.closed.load(Ordering::Acquire) {
                return -2;
            }
            let copied = copy(self);
            if copied > 0 {
                return copied;
            }
            if self.capture.failed() {
                return -1;
            }
            let now = Instant::now();
            if now >= deadline {
                return 0;
            }
            // slices of 1 ms: a signal sent between the check and the wait is never lost for longer
            let guard = self.lock.lock().unwrap_or_else(|e| e.into_inner());
            let _ = self.signal.wait_timeout(guard, (deadline - now).min(Duration::from_millis(1)));
        }
    }

    fn copy_records(&self, out: &mut [f32]) -> i32 {
        let mut copied = 0usize;
        let mut count = 0;
        while let Some(length) = self.frames.peek(frame::LENGTH) {
            let length = length as usize;
            if copied + length > out.len() {
                break;
            }
            self.frames.pop(&mut out[copied..copied + length]);
            copied += length;
            count += 1;
        }
        count
    }
}

/// A value the loop could not hand back yet (the slot was taken): kept and handed back on a later iteration, never freed by the loop.
struct Held<T>(Option<Box<T>>);

impl<T> Held<T> {
    fn retry(&mut self, mailbox: &Mailbox<T>) {
        if let Some(value) = self.0.take() {
            self.0 = mailbox.give_back(value);
        }
    }

    fn give_back(&mut self, mailbox: &Mailbox<T>, value: Box<T>) {
        self.0 = mailbox.give_back(value);
        if let Some(value) = self.0.take() {
            // the previous value is still waiting for Java: this one must wait too; at most one is held per mailbox
            self.0 = Some(value);
        }
    }

    fn is_free(&self) -> bool {
        self.0.is_none()
    }
}

/// The loop's state. `step` does one iteration; the thread calls it until it says stop.
pub struct Worker {
    shared: Arc<Shared>,
    pipeline: Pipeline,
    front: FrontEnd,
    strum: StrumClip,
    block: Block,
    hop: [f32; HOP],
    hop_fill: usize,
    hop_arrival: i64,
    record: Vec<f32>,
    hop_block: [f32; HOP_BLOCK],
    marker_pending: bool,
    hops_were_enabled: bool,
    spectrum: bool,
    processed: i64,
    observed_dropped: u64,
    held_config: Held<Config>,
    held_attack: Held<AttackSlot>,
    held_expected: Held<Vec<i32>>,
    held_chord: Held<Option<ExpectedChord>>,
}

/// What one iteration did.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Step {
    /// No block was waiting.
    Idle,
    /// A block was read.
    Block,
    /// The loop must end (closed, or the stream failed).
    Stop,
}

impl Worker {
    pub fn new(shared: Arc<Shared>, parameters: Parameters, front: FrontEndSettings, strum: StrumParameters) -> Worker {
        let rate = shared.rate;
        let mut front_end = FrontEnd::new(rate as f64, HOP);
        front_end.set(front);
        let mut meter = StrumClip::new(strum);
        meter.configure(rate, &shared.strum);
        let observed_dropped = shared.capture.blocks.dropped();
        Worker {
            pipeline: Pipeline::new(rate, parameters),
            front: front_end,
            strum: meter,
            block: Block::EMPTY,
            hop: [0.0; HOP],
            hop_fill: 0,
            hop_arrival: 0,
            record: vec![0f32; frame::CAPACITY],
            hop_block: [0.0; HOP_BLOCK],
            marker_pending: true,
            hops_were_enabled: false,
            spectrum: false,
            processed: 0,
            observed_dropped,
            held_config: Held(None),
            held_attack: Held(None),
            held_expected: Held(None),
            held_chord: Held(None),
            shared,
        }
    }

    /// One iteration: the changes from Java first (as `AnalysisService` swaps its configuration at the top of its loop), then one block.
    pub fn step(&mut self) -> Step {
        if self.shared.stop.load(Ordering::Acquire) {
            return Step::Stop;
        }
        self.take_changes();
        let dropped = self.shared.capture.blocks.dropped();
        if dropped != self.observed_dropped {
            // a gap: the window and every history restart, the timestamps go on counting the lost samples (as AnalysisService)
            self.processed += ((dropped - self.observed_dropped) as i64) * BLOCK as i64;
            self.observed_dropped = dropped;
            self.restart();
        }
        if !self.shared.capture.blocks.pop(&mut self.block) {
            if self.shared.capture.failed() {
                self.shared.wake();
                return Step::Stop;
            }
            return Step::Idle;
        }
        self.hop[self.hop_fill..self.hop_fill + BLOCK].copy_from_slice(&self.block.samples);
        self.hop_fill += BLOCK;
        self.hop_arrival = self.block.arrival_nanos;
        if self.hop_fill == HOP {
            self.hop_fill = 0;
            self.analyze_hop();
        }
        Step::Block
    }

    fn restart(&mut self) {
        self.pipeline.reset();
        self.front.reset();
        self.hop_fill = 0;
    }

    fn take_changes(&mut self) {
        let shared = self.shared.clone();
        self.held_config.retry(&shared.config);
        if self.held_config.is_free() {
            if let Some(mut config) = shared.config.take() {
                if let Some(pipeline) = config.pipeline.as_mut() {
                    std::mem::swap(&mut self.pipeline, pipeline);
                    self.spectrum = false;
                    self.front.reset();
                    self.strum.configure(shared.rate, &shared.strum);
                    self.hop_fill = 0;
                    self.marker_pending = true;
                }
                self.front.set(config.front);
                self.strum.set_parameters(config.strum);
                self.held_config.give_back(&shared.config, config);
            }
        }
        self.held_attack.retry(&shared.attack);
        if self.held_attack.is_free() {
            if let Some(mut slot) = shared.attack.take() {
                let AttackSlot { analyzer, evidence } = &mut *slot;
                self.pipeline.swap_attack(analyzer, evidence);
                self.held_attack.give_back(&shared.attack, slot);
            }
        }
        self.held_expected.retry(&shared.expected);
        if self.held_expected.is_free() {
            if let Some(expected) = shared.expected.take() {
                self.pipeline.set_expected_pitches(&expected);
                self.held_expected.give_back(&shared.expected, expected);
            }
        }
        self.held_chord.retry(&shared.chord);
        if self.held_chord.is_free() {
            if let Some(mut chord) = shared.chord.take() {
                self.pipeline.swap_expected_chord(&mut chord);
                self.held_chord.give_back(&shared.chord, chord);
            }
        }
        let spectrum = shared.spectrum.load(Ordering::Acquire);
        if spectrum != self.spectrum {
            self.pipeline.set_spectrum(spectrum);
            self.spectrum = spectrum;
        }
    }

    fn analyze_hop(&mut self) {
        let shared = self.shared.clone();
        self.processed += HOP as i64;
        // the front end first: the hop listeners, the strum meter and every stage see the same signal (as AnalysisService)
        self.front.process(&mut self.hop);
        self.strum.hop(&self.hop, &shared.strum);
        let hops_enabled = shared.hops_enabled.load(Ordering::Acquire);
        if hops_enabled && !self.hops_were_enabled {
            self.marker_pending = true;
        }
        self.hops_were_enabled = hops_enabled;
        if hops_enabled {
            if self.marker_pending && shared.hops.capacity() - shared.hops.len() >= HOP_BLOCK {
                self.hop_block[0] = shared.rate as f32;
                self.hop_block[1..].iter_mut().for_each(|s| *s = 0.0);
                shared.hops.push(&self.hop_block);
                self.marker_pending = false;
            }
            if !self.marker_pending && shared.hops.capacity() - shared.hops.len() >= HOP_BLOCK {
                self.hop_block[0] = 0.0;
                self.hop_block[1..].copy_from_slice(&self.hop);
                shared.hops.push(&self.hop_block);
            }
        }
        let started = Instant::now();
        let timestamp = self.processed * 1_000_000_000 / shared.rate as i64;
        let arrival = shared.java_nanos_at_open + self.hop_arrival;
        let result = self.pipeline.hop_dated(&self.hop, timestamp, self.processed, arrival, &mut self.record);
        shared.costs.record(started.elapsed().as_nanos());
        if let HopResult::Record(length) = result {
            if shared.frames.capacity() - shared.frames.len() >= length {
                shared.frames.push(&self.record[..length]);
            } else {
                shared.frames_lost.fetch_add(1, Ordering::Relaxed);
            }
        }
        shared.wake();
    }
}

/// The requests Java made, kept on Java's side so that a new pipeline starts with them.
#[derive(Default, Clone)]
pub struct Requests {
    pub spectrum: bool,
    pub attack: Vec<i32>,
    pub expected: Vec<i32>,
    pub chord: Option<(Vec<i32>, Vec<i32>)>,
}

/// A pipeline for these parameters with these requests (Java's thread: it allocates).
pub fn pipeline_for(rate: i32, parameters: Parameters, requests: &Requests) -> Pipeline {
    let (required, tolerated) = match &requests.chord {
        Some((r, t)) => (r.as_slice(), t.as_slice()),
        None => (&[][..], &[][..]),
    };
    let mut pipeline = Pipeline::new(rate, parameters);
    pipeline.set_request(Request { spectrum: false, attack_candidates: &requests.attack, expected_pitches: &requests.expected, chord_required: required, chord_tolerated: tolerated });
    pipeline
}

pub fn front_of(p: &Parameters) -> FrontEndSettings {
    FrontEndSettings { gain: p.front_gain, noise_reduction: p.front_noise_reduction, subtraction: p.front_subtraction }
}

pub fn strum_of(p: &Parameters) -> StrumParameters {
    StrumParameters { arm_floor_dbfs: p.strum_arm_floor_dbfs, clip_sample: p.strum_clip_sample, weak_dbfs: p.strum_weak_dbfs, loud_dbfs: p.strum_loud_dbfs }
}

/// True when the analysis values differ (a new pipeline), false when only the front end or the strum meter changed.
pub fn analysis_changed(a: &Parameters, b: &Parameters) -> bool {
    let mut a = *a;
    let mut b = *b;
    for p in [&mut a, &mut b] {
        p.front_gain = 0.0;
        p.front_noise_reduction = false;
        p.front_subtraction = false;
        p.strum_arm_floor_dbfs = 0.0;
        p.strum_clip_sample = 0.0;
        p.strum_weak_dbfs = 0.0;
        p.strum_loud_dbfs = 0.0;
    }
    a != b
}

/// Starts the loop on its own thread, promoted as the amp's audio thread (spec 022). It runs until `close`, or until the stream fails and
/// every block was read.
pub fn start(worker: Worker) -> std::thread::JoinHandle<()> {
    start_in(worker, None)
}

/// As `start`, the thread also joining the device's audio workgroup when there is one (macOS), so it runs at the pace the device's deadline
/// needs (T057, research R9).
pub fn start_in(worker: Worker, workgroup: Option<Workgroup>) -> std::thread::JoinHandle<()> {
    std::thread::Builder::new()
        .name("tuxguitar-recognition".to_string())
        .spawn(move || {
            let mut worker = worker;
            let _ = crate::realtime::promote_current_thread(HOP, worker.shared.rate as f32);
            let _membership = workgroup.as_ref().and_then(|group| group.join().ok());
            loop {
                match worker.step() {
                    Step::Stop => break,
                    Step::Block => {}
                    // a block of 128 frames lasts 1.3 ms at 96 kHz: a short sleep is enough, and no lock is taken
                    Step::Idle => std::thread::sleep(Duration::from_micros(250)),
                }
            }
            worker.shared.wake();
        })
        .expect("the recognition thread starts")
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::analysis::pipeline;

    fn signal(rate: i32, seconds: f64) -> Vec<f32> {
        let n = (rate as f64 * seconds) as usize;
        (0..n)
            .map(|i| {
                let t = i as f64 / rate as f64;
                let (f, since) = if t < seconds / 2.0 { (110.0, t) } else { (146.83, t - seconds / 2.0) };
                (0.3 * (-3.0 * since).exp() * (2.0 * std::f64::consts::PI * f * t).sin()) as f32
            })
            .collect()
    }

    fn shared(rate: i32) -> Arc<Shared> {
        Arc::new(Shared::new(Arc::new(CaptureShared::new(1 << 14)), rate, 1_000_000))
    }

    fn feed(shared: &Shared, samples: &[f32]) {
        let mut block = [0f32; BLOCK];
        for (i, chunk) in samples.chunks_exact(BLOCK).enumerate() {
            block.copy_from_slice(chunk);
            assert!(shared.capture.blocks.push(&block, i as i64 * 1000));
        }
    }

    fn run(worker: &mut Worker) {
        while worker.step() == Step::Block {}
    }

    fn records(shared: &Shared) -> Vec<f32> {
        let mut out = vec![0f32; FRAME_RING];
        let mut all = Vec::new();
        loop {
            let n = shared.next_frames(&mut out, Duration::from_millis(0));
            if n <= 0 {
                break;
            }
            let mut offset = 0;
            for _ in 0..n {
                let length = out[offset] as usize;
                all.extend_from_slice(&out[offset..offset + length]);
                offset += length;
            }
        }
        all
    }

    /// The records equal, slot by slot, except where the live ones carry the capture's sample end and arrival.
    fn assert_same(offline: &[f32], live: &[f32]) {
        assert_eq!(offline.len(), live.len(), "length of all records");
        let mut offset = 0;
        while offset < offline.len() {
            let length = offline[offset] as usize;
            for k in 0..length {
                if (frame::CAPTURE_SAMPLE_END..frame::CAPTURE_SAMPLE_END + 2).contains(&k) || (frame::ARRIVAL_NANOS..frame::ARRIVAL_NANOS + 2).contains(&k) {
                    continue;
                }
                assert_eq!(offline[offset + k].to_bits(), live[offset + k].to_bits(), "slot {k} of the record at {offset}");
            }
            offset += length;
        }
    }

    fn offline(samples: &[f32], rate: i32, attack: &[i32]) -> Vec<f32> {
        pipeline::analyze(samples, rate, Parameters::defaults(), Request { spectrum: true, attack_candidates: attack, expected_pitches: &[45, 50], chord_required: &[], chord_tolerated: &[] })
    }

    #[test]
    fn the_live_records_are_the_offline_records_of_the_same_samples_at_every_rate() {
        for rate in [44100, 48000, 88200, 96000] {
            let samples = signal(rate, 1.0);
            let shared = shared(rate);
            let p = Parameters::defaults();
            let mut worker = Worker::new(shared.clone(), p, front_of(&p), strum_of(&p));
            shared.set_spectrum(true);
            shared.attack.post(Box::new(AttackSlot::of(rate, &[40, 45, 50])));
            shared.expected.post(Box::new(vec![45, 50]));
            feed(&shared, &samples);
            // read as Java does, while the analysis runs: the frame ring holds less than a second of spectrum records
            let mut live = Vec::new();
            let mut steps = 0;
            while worker.step() == Step::Block {
                steps += 1;
                if steps % 64 == 0 {
                    live.extend(records(&shared));
                }
            }
            live.extend(records(&shared));
            assert_eq!(shared.frames_lost(), 0);
            assert_same(&offline(&samples, rate, &[40, 45, 50]), &live);
        }
    }

    #[test]
    fn the_records_carry_the_sample_end_and_the_arrival_in_javas_base() {
        let rate = 48000;
        let shared = shared(rate);
        let p = Parameters::defaults();
        let mut worker = Worker::new(shared.clone(), p, front_of(&p), strum_of(&p));
        feed(&shared, &signal(rate, 0.2));
        run(&mut worker);
        let live = records(&shared);
        let read_long = |offset: usize, slot: usize| ((live[offset + slot].to_bits() as u64) << 32 | live[offset + slot + 1].to_bits() as u64) as i64;
        // the first record ends after 8 hops, the 16th block, which arrived at 15 µs after the open
        assert_eq!(read_long(0, frame::CAPTURE_SAMPLE_END), 2048);
        assert_eq!(read_long(0, frame::ARRIVAL_NANOS), 1_000_000 + 15 * 1000);
    }

    #[test]
    fn a_new_configuration_discards_the_window_and_starts_afresh() {
        let rate = 48000;
        let samples = signal(rate, 1.0);
        let shared = shared(rate);
        let p = Parameters::defaults();
        let mut worker = Worker::new(shared.clone(), p, front_of(&p), strum_of(&p));
        let half = samples.len() / 2 / HOP * HOP;
        feed(&shared, &samples[..half]);
        run(&mut worker);
        records(&shared);
        shared.config.post(Box::new(Config { pipeline: Some(pipeline_for(rate, p, &Requests::default())), front: front_of(&p), strum: strum_of(&p) }));
        feed(&shared, &samples[half..]);
        run(&mut worker);
        let after = pipeline::analyze(&samples[half..], rate, p, Request { spectrum: false, attack_candidates: &[], expected_pitches: &[], chord_required: &[], chord_tolerated: &[] });
        let live = records(&shared);
        // the timestamps go on from the first half; the rest is a fresh analysis
        assert_eq!(after.len(), live.len());
        let mut offset = 0;
        while offset < after.len() {
            let length = after[offset] as usize;
            for k in frame::RMS_DBFS..length {
                assert_eq!(after[offset + k].to_bits(), live[offset + k].to_bits(), "slot {k}");
            }
            offset += length;
        }
    }

    #[test]
    fn next_frames_waits_then_reports_timeout_failure_and_close() {
        let rate = 48000;
        let shared = shared(rate);
        let mut out = vec![0f32; 4096];
        let started = Instant::now();
        assert_eq!(shared.next_frames(&mut out, Duration::from_millis(20)), 0, "timeout");
        assert!(started.elapsed() >= Duration::from_millis(20));
        let p = Parameters::defaults();
        let worker = Worker::new(shared.clone(), p, front_of(&p), strum_of(&p));
        let thread = start(worker);
        feed(&shared, &signal(rate, 0.1));
        assert!(shared.next_frames(&mut out, Duration::from_millis(2000)) > 0, "records arrive");
        shared.capture.fail("the input stopped: unplugged".to_string());
        let mut result = 1;
        while result > 0 {
            result = shared.next_frames(&mut out, Duration::from_millis(500));
        }
        assert_eq!(result, -1, "the stream stopped");
        shared.close();
        assert_eq!(shared.next_frames(&mut out, Duration::from_millis(500)), -2, "closed");
        thread.join().unwrap();
    }

    #[test]
    fn close_wakes_a_waiting_reader_at_once() {
        let shared = shared(48000);
        let reader = shared.clone();
        let waiting = std::thread::spawn(move || {
            let mut out = vec![0f32; 4096];
            let started = Instant::now();
            (reader.next_frames(&mut out, Duration::from_secs(5)), started.elapsed())
        });
        std::thread::sleep(Duration::from_millis(50));
        shared.close();
        let (result, waited) = waiting.join().unwrap();
        assert_eq!(result, -2);
        assert!(waited < Duration::from_millis(500));
    }

    #[test]
    fn hop_blocks_only_while_enabled_and_a_marker_first() {
        let rate = 48000;
        let samples = signal(rate, 0.5);
        let shared = shared(rate);
        let p = Parameters::defaults();
        let mut worker = Worker::new(shared.clone(), p, front_of(&p), strum_of(&p));
        let mut out = vec![0f32; 64 * HOP_BLOCK];
        feed(&shared, &samples[..10 * HOP]);
        run(&mut worker);
        assert_eq!(shared.next_hops(&mut out, Duration::from_millis(0)), 0, "no hop block while disabled");
        shared.set_hops_enabled(true);
        feed(&shared, &samples[10 * HOP..20 * HOP]);
        run(&mut worker);
        assert_eq!(shared.next_hops(&mut out, Duration::from_millis(0)), 11, "a marker and ten hops");
        assert_eq!(out[0], rate as f32, "the configure marker first");
        for h in 0..10 {
            assert_eq!(out[(h + 1) * HOP_BLOCK], 0.0, "a hop");
            assert_eq!(&out[(h + 1) * HOP_BLOCK + 1..(h + 2) * HOP_BLOCK], &samples[(10 + h) * HOP..(11 + h) * HOP], "the hop's samples (neutral front end)");
        }
        shared.config.post(Box::new(Config { pipeline: Some(pipeline_for(rate, p, &Requests::default())), front: front_of(&p), strum: strum_of(&p) }));
        feed(&shared, &samples[20 * HOP..22 * HOP]);
        run(&mut worker);
        assert_eq!(shared.next_hops(&mut out, Duration::from_millis(0)), 3, "a marker after the reconfiguration, then the hops");
        assert_eq!(out[0], rate as f32);
        shared.set_hops_enabled(false);
        feed(&shared, &samples[22 * HOP..24 * HOP]);
        run(&mut worker);
        assert_eq!(shared.next_hops(&mut out, Duration::from_millis(0)), 0);
    }

    #[test]
    fn dropped_blocks_restart_the_analysis_and_count_in_the_timestamps() {
        let rate = 48000;
        let samples = signal(rate, 1.0);
        let shared = Arc::new(Shared::new(Arc::new(CaptureShared::new(16)), rate, 0));
        let p = Parameters::defaults();
        let mut worker = Worker::new(shared.clone(), p, front_of(&p), strum_of(&p));
        let mut block = [0f32; BLOCK];
        // 20 blocks into a ring of 16: 4 dropped, counted, never waited for
        for chunk in samples.chunks_exact(BLOCK).take(20) {
            block.copy_from_slice(chunk);
            shared.capture.blocks.push(&block, 0);
        }
        assert_eq!(shared.dropped_blocks(), 4);
        run(&mut worker);
        let live = records(&shared);
        let read_long = |offset: usize, slot: usize| ((live[offset + slot].to_bits() as u64) << 32 | live[offset + slot + 1].to_bits() as u64) as i64;
        // the gap is seen before the first block: 4 blocks counted, then 16 blocks (8 hops) fill the window once
        assert_eq!(live[0] as usize, live.len(), "one record");
        assert_eq!(read_long(0, frame::TIMESTAMP_NANOS), (4 + 16) * BLOCK as i64 * 1_000_000_000 / rate as i64);
    }

    #[test]
    fn the_cost_percentiles_come_from_the_histogram() {
        let costs = Costs::default();
        assert_eq!(costs.percentiles(), (0.0, 0.0));
        for _ in 0..98 {
            costs.record(100_000);
        }
        costs.record(2_000_000);
        costs.record(3_000_000);
        let (median, p99) = costs.percentiles();
        assert_eq!(median, 100_500.0);
        assert_eq!(p99, 2_000_500.0);
    }

    #[test]
    fn only_analysis_values_need_a_new_pipeline() {
        let a = Parameters::defaults();
        let mut b = a;
        b.front_gain = 2.0;
        b.strum_loud_dbfs = -3.0;
        assert!(!analysis_changed(&a, &b));
        b.onset_gate_db = 6.0;
        assert!(analysis_changed(&a, &b));
    }

    #[test]
    fn a_mailbox_frees_what_was_never_taken_and_what_came_back() {
        let mailbox: Mailbox<Vec<i32>> = Mailbox::default();
        mailbox.post(Box::new(vec![1]));
        mailbox.post(Box::new(vec![2]));
        let taken = mailbox.take().unwrap();
        assert_eq!(*taken, vec![2], "the newest value");
        assert!(mailbox.give_back(taken).is_none());
        let other = Box::new(vec![3]);
        let refused = mailbox.give_back(other);
        assert!(refused.is_some(), "the slot is taken until Java's next post");
        mailbox.post(Box::new(vec![4]));
        assert!(mailbox.give_back(refused.unwrap()).is_none(), "the post freed the slot");
    }
}

#[cfg(test)]
mod live_cost {
    use super::*;

    /// The loop as it runs live, fed at the pace of a device (a block of 128 every 2.9 ms at 44.1 kHz), spectrum on and candidates set: the 99th
    /// percentile of one hop's cost (T057, research R9). `promote` asks for the time-constraint policy as `start` does.
    fn live_p99_micros(promote: bool, seconds: f64) -> (f64, f64) {
        let rate = 44100;
        let shared = Arc::new(Shared::new(Arc::new(CaptureShared::new(1024)), rate, 0));
        let p = Parameters::defaults();
        let mut worker = Worker::new(shared.clone(), p, front_of(&p), strum_of(&p));
        shared.set_spectrum(true);
        shared.attack.post(Box::new(AttackSlot::of(rate, &[40, 45, 47, 50, 52, 55, 57, 59, 64])));
        let feeder = shared.clone();
        let blocks = (seconds * rate as f64 / BLOCK as f64) as usize;
        let feeding = std::thread::spawn(move || {
            let start = Instant::now();
            let mut block = [0f32; BLOCK];
            for b in 0..blocks {
                for (i, s) in block.iter_mut().enumerate() {
                    let n = b * BLOCK + i;
                    *s = (0.2 * (2.0 * std::f64::consts::PI * 110.0 * n as f64 / rate as f64).sin()) as f32;
                }
                feeder.capture.blocks.push(&block, 0);
                let due = start + Duration::from_nanos(((b + 1) * BLOCK) as u64 * 1_000_000_000 / rate as u64);
                while Instant::now() < due {
                    std::thread::sleep(Duration::from_micros(200));
                }
            }
            feeder.close();
        });
        let reader = shared.clone();
        let reading = std::thread::spawn(move || {
            let mut out = vec![0f32; 1 << 16];
            while reader.next_frames(&mut out, Duration::from_millis(50)) != -2 {}
        });
        let thread = std::thread::spawn(move || {
            if promote {
                let _ = crate::realtime::promote_current_thread(HOP, rate as f32);
            }
            loop {
                match worker.step() {
                    Step::Stop => break,
                    Step::Block => {}
                    Step::Idle => std::thread::sleep(Duration::from_micros(250)),
                }
            }
        });
        feeding.join().unwrap();
        thread.join().unwrap();
        reading.join().unwrap();
        let (median, p99) = shared.costs.percentiles();
        (median / 1000.0, p99 / 1000.0)
    }

    #[test]
    #[ignore = "timing: run with --ignored on an idle machine"]
    fn the_live_loop_costs_under_a_tenth_of_a_hop() {
        let plain = live_p99_micros(false, 10.0);
        let promoted = live_p99_micros(true, 10.0);
        println!("live median / p99 µs: not promoted {:.0} / {:.0}, promoted {:.0} / {:.0}", plain.0, plain.1, promoted.0, promoted.1);
        let limit = HOP as f64 * 1e6 / 44100.0 / 10.0;
        assert!(promoted.1 < limit, "99th percentile {:.0} µs, limit {limit:.0} µs", promoted.1);
    }
}

#[cfg(test)]
mod device_cost {
    use super::*;
    use crate::capture::Capture;

    /// The loop on the default input, as the JNI opens it (capture only, nothing is played): the median and 99th percentile of one hop's cost, to
    /// compare with the live figure measured from Java (T057, research R9).
    #[test]
    #[ignore = "timing on the default input: run with --ignored"]
    fn the_device_loop_costs_under_a_tenth_of_a_hop() {
        let capture_shared = Arc::new(CaptureShared::new(1024));
        let capture = Capture::open("", -1, capture_shared.clone(), Instant::now()).expect("default input");
        let rate = capture.sample_rate() as i32;
        let shared = Arc::new(Shared::new(capture_shared, rate, 0));
        let p = Parameters::defaults();
        let worker = Worker::new(shared.clone(), p, front_of(&p), strum_of(&p));
        shared.set_spectrum(true);
        shared.attack.post(Box::new(AttackSlot::of(rate, &[40, 45, 47, 50, 52, 55, 57, 59, 64])));
        let thread = start_in(worker, Workgroup::of_device(capture.name()));
        let reader = shared.clone();
        let reading = std::thread::spawn(move || {
            let mut out = vec![0f32; 1 << 16];
            while reader.next_frames(&mut out, Duration::from_millis(50)) != -2 {}
        });
        let seconds: u64 = std::env::var("DEVICE_SECONDS").ok().and_then(|v| v.parse().ok()).unwrap_or(20);
        let mut before: Vec<u64> = vec![0; COST_BINS];
        for window in 0..seconds.div_ceil(10) {
            std::thread::sleep(Duration::from_secs(10));
            let now: Vec<u64> = shared.costs.bins.iter().map(|b| b.load(Ordering::Relaxed) as u64).collect();
            let delta: Vec<u64> = now.iter().zip(&before).map(|(a, b)| a - b).collect();
            let total: u64 = delta.iter().sum();
            let at = |share: f64| {
                let rank = (share * total as f64).ceil().max(1.0) as u64;
                let mut seen = 0;
                delta.iter().position(|c| { seen += c; seen >= rank }).unwrap_or(COST_BINS)
            };
            println!("window {:>3} s: hops {total}, median {} µs, p99 {} µs", (window + 1) * 10, at(0.5), at(0.99));
            before = now;
        }
        shared.close();
        thread.join().unwrap();
        reading.join().unwrap();
        drop(capture);
        let (median, p99) = shared.costs.percentiles();
        let limit = HOP as f64 * 1e9 / rate as f64 / 10.0;
        println!("device {rate} Hz median / p99 µs: {:.0} / {:.0}, limit {:.0}", median / 1000.0, p99 / 1000.0, limit / 1000.0);
        assert!(p99 < limit);
    }
}
