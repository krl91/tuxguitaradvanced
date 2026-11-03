//! The native recognition's own input stream (spec 023, research R4): the practice input at its device's current rate (asking `cpal` for
//! another rate would change the device's own setting), cut into blocks of 128 frames, each dated by its callback's `Instant`. The callback
//! copies the chosen channel into a block and pushes the full blocks into a lock-free ring: nothing else. It never allocates, locks or
//! waits; a full ring drops the block and counts it. Errors (a device unplugged) are kept for the analysis thread.

use cpal::traits::{DeviceTrait, HostTrait, StreamTrait};
use cpal::{BufferSize, Device, SampleFormat, Stream, StreamConfig};
use std::cell::UnsafeCell;
use std::sync::atomic::{AtomicBool, AtomicU64, AtomicUsize, Ordering};
use std::sync::{Arc, Mutex};
use std::time::Instant;

/// The frames of one block, the size the stream is asked for.
pub const BLOCK: usize = 128;

/// Why an open failed: the reasons of `NativeRecognitionException.Reason`.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Reason {
    DeviceNotFound,
    PermissionDenied,
    FormatUnsupported,
    StreamFailed,
}

impl Reason {
    pub fn name(self) -> &'static str {
        match self {
            Reason::DeviceNotFound => "DEVICE_NOT_FOUND",
            Reason::PermissionDenied => "PERMISSION_DENIED",
            Reason::FormatUnsupported => "FORMAT_UNSUPPORTED",
            Reason::StreamFailed => "STREAM_FAILED",
        }
    }
}

/// One block: its samples and when its callback ran, in nanoseconds since the stream's open.
#[derive(Clone, Copy)]
pub struct Block {
    pub samples: [f32; BLOCK],
    pub arrival_nanos: i64,
}

impl Block {
    pub const EMPTY: Block = Block { samples: [0.0; BLOCK], arrival_nanos: 0 };
}

/// A lock-free ring of blocks for one producer (the callback) and one consumer (the analysis thread).
pub struct BlockRing {
    slots: Box<[UnsafeCell<Block>]>,
    mask: usize,
    written: AtomicUsize,
    read: AtomicUsize,
    dropped: AtomicU64,
}

// One thread pushes, one thread pops: each side only touches the slots the other side does not see yet.
unsafe impl Sync for BlockRing {}
unsafe impl Send for BlockRing {}

impl BlockRing {
    /// A ring of at least `capacity` blocks (rounded up to a power of two).
    pub fn new(capacity: usize) -> BlockRing {
        let size = capacity.max(2).next_power_of_two();
        let slots = (0..size).map(|_| UnsafeCell::new(Block::EMPTY)).collect::<Vec<_>>().into_boxed_slice();
        BlockRing { slots, mask: size - 1, written: AtomicUsize::new(0), read: AtomicUsize::new(0), dropped: AtomicU64::new(0) }
    }

    pub fn capacity(&self) -> usize {
        self.mask + 1
    }

    pub fn len(&self) -> usize {
        self.written.load(Ordering::Acquire).wrapping_sub(self.read.load(Ordering::Acquire))
    }

    pub fn is_empty(&self) -> bool {
        self.len() == 0
    }

    /// Producer side: the block, or false and one more dropped block when the ring is full. Never waits.
    pub fn push(&self, samples: &[f32; BLOCK], arrival_nanos: i64) -> bool {
        let written = self.written.load(Ordering::Relaxed);
        if written.wrapping_sub(self.read.load(Ordering::Acquire)) >= self.capacity() {
            self.dropped.fetch_add(1, Ordering::Relaxed);
            return false;
        }
        let slot = unsafe { &mut *self.slots[written & self.mask].get() };
        slot.samples.copy_from_slice(samples);
        slot.arrival_nanos = arrival_nanos;
        self.written.store(written.wrapping_add(1), Ordering::Release);
        true
    }

    /// Consumer side: the oldest block into `out`, or false when none waits.
    pub fn pop(&self, out: &mut Block) -> bool {
        let read = self.read.load(Ordering::Relaxed);
        if self.written.load(Ordering::Acquire) == read {
            return false;
        }
        *out = unsafe { *self.slots[read & self.mask].get() };
        self.read.store(read.wrapping_add(1), Ordering::Release);
        true
    }

    /// The blocks dropped because the ring was full.
    pub fn dropped(&self) -> u64 {
        self.dropped.load(Ordering::Relaxed)
    }
}

/// What the callback and the analysis thread share: the ring and the stream's failure.
pub struct CaptureShared {
    pub blocks: BlockRing,
    failed: AtomicBool,
    message: Mutex<String>,
}

impl CaptureShared {
    pub fn new(capacity_blocks: usize) -> CaptureShared {
        CaptureShared { blocks: BlockRing::new(capacity_blocks), failed: AtomicBool::new(false), message: Mutex::new(String::new()) }
    }

    /// Marks the stream failed (from cpal's error callback, never the data callback).
    pub fn fail(&self, message: String) {
        if let Ok(mut kept) = self.message.lock() {
            if kept.is_empty() {
                *kept = message;
            }
        }
        self.failed.store(true, Ordering::Release);
    }

    pub fn failed(&self) -> bool {
        self.failed.load(Ordering::Acquire)
    }

    pub fn message(&self) -> String {
        self.message.lock().map(|m| m.clone()).unwrap_or_default()
    }
}

/// The callback's state: the block being filled. Its samples come in pieces of any size; each full block is pushed with the arrival of the
/// callback that completed it.
pub struct Accumulator {
    block: [f32; BLOCK],
    fill: usize,
}

impl Default for Accumulator {
    fn default() -> Accumulator {
        Accumulator { block: [0.0; BLOCK], fill: 0 }
    }
}

impl Accumulator {
    /// The frames of one callback: `frame(i)` gives the chosen channel of frame `i`, `frames` of them.
    pub fn feed(&mut self, frames: usize, frame: impl Fn(usize) -> f32, arrival_nanos: i64, ring: &BlockRing) {
        for i in 0..frames {
            self.block[self.fill] = frame(i);
            self.fill += 1;
            if self.fill == BLOCK {
                ring.push(&self.block, arrival_nanos);
                self.fill = 0;
            }
        }
    }
}

/// The device whose name is `name`, else one whose name contains it or is contained in it; the default one for `""`.
fn find(host: &cpal::Host, name: &str) -> Result<Device, (Reason, String)> {
    if name.is_empty() {
        return host.default_input_device().ok_or((Reason::DeviceNotFound, "no input device is available".to_string()));
    }
    let devices: Vec<Device> = host.input_devices().map_err(|e| (Reason::StreamFailed, e.to_string()))?.collect();
    let names: Vec<String> = devices.iter().map(|d| d.name().unwrap_or_default()).collect();
    if let Some(i) = names.iter().position(|n| n == name) {
        return Ok(devices.into_iter().nth(i).unwrap());
    }
    let wanted = name.to_lowercase();
    if let Some(i) = names.iter().position(|n| {
        let n = n.to_lowercase();
        !n.is_empty() && (n.contains(&wanted) || wanted.contains(&n))
    }) {
        return Ok(devices.into_iter().nth(i).unwrap());
    }
    Err((Reason::DeviceNotFound, format!("the input \"{name}\" is not connected")))
}

/// The input stream: it captures while it lives; dropping it stops and closes the device.
pub struct Capture {
    _stream: Stream,
    rate: f32,
    channels: u16,
    name: String,
}

impl Capture {
    /// Opens `name` (`""` for the default) at its current rate, the channel `channel` (-1 for the first), blocks dated from `origin`.
    pub fn open(name: &str, channel: i32, shared: Arc<CaptureShared>, origin: Instant) -> Result<Capture, (Reason, String)> {
        let host = cpal::default_host();
        let device = find(&host, name)?;
        let config = device.default_input_config().map_err(|e| (Reason::FormatUnsupported, format!("the input cannot be opened: {e}")))?;
        let channels = config.channels() as usize;
        let chosen = if channel >= 0 && (channel as usize) < channels { channel as usize } else { 0 };
        let stream_config = StreamConfig { channels: config.channels(), sample_rate: config.sample_rate(), buffer_size: BufferSize::Fixed(BLOCK as u32) };
        let build = |stream_config: &StreamConfig| -> Result<Stream, cpal::BuildStreamError> {
            let data = shared.clone();
            let errors = shared.clone();
            let on_error = move |e: cpal::StreamError| errors.fail(format!("the input stopped: {e}"));
            let mut accumulator = Accumulator::default();
            match config.sample_format() {
                SampleFormat::F32 => device.build_input_stream(
                    stream_config,
                    move |samples: &[f32], _: &cpal::InputCallbackInfo| {
                        let arrival = origin.elapsed().as_nanos() as i64;
                        accumulator.feed(samples.len() / channels, |i| samples[i * channels + chosen], arrival, &data.blocks);
                    },
                    on_error,
                    None,
                ),
                SampleFormat::I16 => device.build_input_stream(
                    stream_config,
                    move |samples: &[i16], _: &cpal::InputCallbackInfo| {
                        let arrival = origin.elapsed().as_nanos() as i64;
                        accumulator.feed(samples.len() / channels, |i| samples[i * channels + chosen] as f32 / 32768.0, arrival, &data.blocks);
                    },
                    on_error,
                    None,
                ),
                other => Err(cpal::BuildStreamError::BackendSpecific {
                    err: cpal::BackendSpecificError { description: format!("unsupported input sample format {other}") },
                }),
            }
        };
        // some systems refuse a fixed buffer (shared WASAPI): then the system's own buffer is used
        let stream = build(&stream_config)
            .or_else(|_| build(&StreamConfig { buffer_size: BufferSize::Default, ..stream_config.clone() }))
            .map_err(|e| (open_reason(&e.to_string()), format!("the input cannot be opened: {e}")))?;
        stream.play().map_err(|e| (open_reason(&e.to_string()), format!("the input cannot start: {e}")))?;
        Ok(Capture { _stream: stream, rate: config.sample_rate().0 as f32, channels: config.channels(), name: device.name().unwrap_or_default() })
    }

    pub fn sample_rate(&self) -> f32 {
        self.rate
    }

    pub fn channels(&self) -> u16 {
        self.channels
    }

    pub fn name(&self) -> &str {
        &self.name
    }
}

/// The reason of a failed build or start, from the system's text.
fn open_reason(text: &str) -> Reason {
    let text = text.to_lowercase();
    if text.contains("permission") || text.contains("not authorized") || text.contains("access denied") || text.contains("denied") {
        Reason::PermissionDenied
    } else if text.contains("format") || text.contains("not supported") || text.contains("unsupported") {
        Reason::FormatUnsupported
    } else if text.contains("not available") || text.contains("no longer") || text.contains("not found") {
        Reason::DeviceNotFound
    } else {
        Reason::StreamFailed
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn blocks_of_128_come_out_in_order_with_their_arrival_whatever_the_callback_size() {
        for rate in [44100usize, 48000, 88200, 96000] {
            let ring = BlockRing::new(1024);
            let mut accumulator = Accumulator::default();
            // callbacks of a few odd sizes, about 3 ms each, as systems give them
            let sizes = [rate / 333, 128, 77, 512, 1];
            let mut next = 0usize;
            let mut callback = 0i64;
            let total = rate / 2;
            while next < total {
                let n = sizes[callback as usize % sizes.len()].min(total - next);
                let start = next;
                accumulator.feed(n, |i| (start + i) as f32, callback * 1000, &ring);
                next += n;
                callback += 1;
            }
            let mut block = Block::EMPTY;
            let mut expected = 0usize;
            let mut last_arrival = -1;
            while ring.pop(&mut block) {
                for s in block.samples {
                    assert_eq!(s, expected as f32, "rate {rate}");
                    expected += 1;
                }
                assert!(block.arrival_nanos >= last_arrival, "arrivals in order");
                last_arrival = block.arrival_nanos;
            }
            assert_eq!(expected, total / BLOCK * BLOCK, "every full block, rate {rate}");
            assert_eq!(ring.dropped(), 0);
        }
    }

    #[test]
    fn a_full_ring_drops_and_counts_the_new_blocks_and_never_waits() {
        let ring = BlockRing::new(4);
        let block = [0.5f32; BLOCK];
        for i in 0..4 {
            assert!(ring.push(&block, i));
        }
        let start = Instant::now();
        for i in 0..10 {
            assert!(!ring.push(&block, 10 + i), "full");
        }
        assert!(start.elapsed().as_millis() < 50, "the callback is never held");
        assert_eq!(ring.dropped(), 10);
        let mut out = Block::EMPTY;
        assert!(ring.pop(&mut out));
        assert_eq!(out.arrival_nanos, 0, "the oldest kept");
        assert!(ring.push(&block, 99), "room again");
    }

    #[test]
    fn a_failure_keeps_its_first_message() {
        let shared = CaptureShared::new(4);
        assert!(!shared.failed());
        shared.fail("the input stopped: unplugged".to_string());
        shared.fail("later".to_string());
        assert!(shared.failed());
        assert_eq!(shared.message(), "the input stopped: unplugged");
    }

    #[test]
    fn the_reasons_are_read_from_the_system_text() {
        assert_eq!(open_reason("Permission denied by the user"), Reason::PermissionDenied);
        assert_eq!(open_reason("The requested stream format is not supported"), Reason::FormatUnsupported);
        assert_eq!(open_reason("The requested device is no longer available"), Reason::DeviceNotFound);
        assert_eq!(open_reason("A backend-specific error has occurred"), Reason::StreamFailed);
        assert_eq!(Reason::FormatUnsupported.name(), "FORMAT_UNSUPPORTED");
    }
}
