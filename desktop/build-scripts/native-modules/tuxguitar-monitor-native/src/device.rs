//! The duplex device: the guitar's input and the output, each at its device's current rate (asking `cpal` for another rate would change
//! the device's own setting), joined by two rings (spec 022, research R1 addendum).
//!
//! The input callback copies the chosen channel into the input ring. Java reads it block by block: when more than two blocks wait, the
//! oldest are dropped and counted, so the delay never creeps. Java writes the processed stereo blocks into the output ring, and the output
//! callback plays them through the resampler. The callbacks never allocate, lock or wait; errors (a device unplugged) are kept for
//! Java's next read or write.

use crate::latency;
use crate::resampler::Resampler;
use crate::ring::Ring;
use cpal::traits::{DeviceTrait, HostTrait, StreamTrait};
use cpal::{BufferSize, Device, SampleFormat, Stream, StreamConfig};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex};
use std::time::{Duration, Instant};

/// The longest callback handled in one piece; longer ones are cut into pieces of this size.
const CHUNK: usize = 4096;

struct Shared {
    input: Ring,
    output: Ring,
    failed: AtomicBool,
    message: Mutex<String>,
}

impl Shared {
    fn fail(&self, message: String) {
        if let Ok(mut kept) = self.message.lock() {
            if kept.is_empty() {
                *kept = message;
            }
        }
        self.failed.store(true, Ordering::Release);
    }

    fn check(&self) -> Result<(), String> {
        if self.failed.load(Ordering::Acquire) {
            return Err(self.message.lock().map(|m| m.clone()).unwrap_or_else(|_| "the audio device failed".to_string()));
        }
        Ok(())
    }
}

pub struct Duplex {
    // the streams play while they live; dropping them stops and closes the devices
    _input: Stream,
    _output: Stream,
    shared: Arc<Shared>,
    block: usize,
    input_rate: f32,
    output_rate: f32,
    input_declared: u32,
    output_declared: u32,
    dropped_blocks: u64,
    interleaved: Vec<f32>,
}

/// The device whose name is `name`, else one whose name contains it or is contained in it, else the default one.
fn find(devices: Vec<Device>, name: &str, default: Option<Device>) -> Result<Device, String> {
    if !name.is_empty() {
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
    }
    default.ok_or_else(|| "no audio device is available".to_string())
}

impl Duplex {
    /// Opens the input (`""` for the default; `channel` -1 for the first) and the output (`""` for the default) with buffers of `block` frames.
    pub fn open(input_name: &str, channel: i32, output_name: &str, block: usize) -> Result<Duplex, String> {
        let host = cpal::default_host();
        let input = find(host.input_devices().map_err(|e| e.to_string())?.collect(), input_name, host.default_input_device())?;
        let output = find(host.output_devices().map_err(|e| e.to_string())?.collect(), output_name, host.default_output_device())?;
        let input_config = input.default_input_config().map_err(|e| format!("the input cannot be opened: {e}"))?;
        let output_config = output.default_output_config().map_err(|e| format!("the output cannot be opened: {e}"))?;
        let input_rate = input_config.sample_rate().0 as f64;
        let output_rate = output_config.sample_rate().0 as f64;
        let input_channels = input_config.channels() as usize;
        let output_channels = output_config.channels() as usize;
        let chosen = if channel >= 0 && (channel as usize) < input_channels { channel as usize } else { 0 };

        let shared = Arc::new(Shared {
            input: Ring::new(16 * block.max(CHUNK)),
            output: Ring::new(2 * 16 * block.max(CHUNK)),
            failed: AtomicBool::new(false),
            message: Mutex::new(String::new()),
        });

        let input_stream = {
            let shared_data = shared.clone();
            let shared_error = shared.clone();
            let mono = vec![0f32; CHUNK];
            let on_error = move |e: cpal::StreamError| shared_error.fail(format!("the input stopped: {e}"));
            let config = StreamConfig { channels: input_config.channels(), sample_rate: input_config.sample_rate(), buffer_size: BufferSize::Fixed(block as u32) };
            let build = |config: &StreamConfig| -> Result<Stream, cpal::BuildStreamError> {
                let shared_data = shared_data.clone();
                let on_error = on_error.clone();
                let mut mono = mono.clone();
                match input_config.sample_format() {
                    SampleFormat::F32 => input.build_input_stream(
                        config,
                        move |data: &[f32], _: &cpal::InputCallbackInfo| {
                            for frames in data.chunks(CHUNK * input_channels) {
                                let n = frames.len() / input_channels;
                                for i in 0..n {
                                    mono[i] = frames[i * input_channels + chosen];
                                }
                                shared_data.input.push(&mono[..n]);
                            }
                        },
                        on_error,
                        None,
                    ),
                    SampleFormat::I16 => input.build_input_stream(
                        config,
                        move |data: &[i16], _: &cpal::InputCallbackInfo| {
                            for frames in data.chunks(CHUNK * input_channels) {
                                let n = frames.len() / input_channels;
                                for i in 0..n {
                                    mono[i] = frames[i * input_channels + chosen] as f32 / 32768.0;
                                }
                                shared_data.input.push(&mono[..n]);
                            }
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
            build(&config)
                .or_else(|_| build(&StreamConfig { buffer_size: BufferSize::Default, ..config.clone() }))
                .map_err(|e| format!("the input cannot be opened: {e}"))?
        };

        let output_stream = {
            let shared_data = shared.clone();
            let shared_error = shared.clone();
            let on_error = move |e: cpal::StreamError| shared_error.fail(format!("the output stopped: {e}"));
            let config = StreamConfig { channels: output_config.channels(), sample_rate: output_config.sample_rate(), buffer_size: BufferSize::Fixed(block as u32) };
            let build = |config: &StreamConfig| -> Result<Stream, cpal::BuildStreamError> {
                let shared_data = shared_data.clone();
                let on_error = on_error.clone();
                let mut resampler = Resampler::new(input_rate, output_rate, block);
                let mut left = vec![0f32; CHUNK];
                let mut right = vec![0f32; CHUNK];
                let mut render = move |frames: usize, put: &mut dyn FnMut(usize, f32, f32)| {
                    let mut done = 0;
                    while done < frames {
                        let n = (frames - done).min(CHUNK);
                        resampler.render(&shared_data.output, &mut left[..n], &mut right[..n]);
                        for i in 0..n {
                            put(done + i, left[i], right[i]);
                        }
                        done += n;
                    }
                };
                match output_config.sample_format() {
                    SampleFormat::F32 => output.build_output_stream(
                        config,
                        move |data: &mut [f32], _: &cpal::OutputCallbackInfo| {
                            let frames = data.len() / output_channels;
                            render(frames, &mut |i, l, r| write_frame(&mut data[i * output_channels..(i + 1) * output_channels], l, r, |v| v));
                        },
                        on_error,
                        None,
                    ),
                    SampleFormat::I16 => output.build_output_stream(
                        config,
                        move |data: &mut [i16], _: &cpal::OutputCallbackInfo| {
                            let frames = data.len() / output_channels;
                            render(frames, &mut |i, l, r| {
                                write_frame(&mut data[i * output_channels..(i + 1) * output_channels], l, r, |v| (v.clamp(-1.0, 1.0) * 32767.0) as i16)
                            });
                        },
                        on_error,
                        None,
                    ),
                    other => Err(cpal::BuildStreamError::BackendSpecific {
                        err: cpal::BackendSpecificError { description: format!("unsupported output sample format {other}") },
                    }),
                }
            };
            build(&config)
                .or_else(|_| build(&StreamConfig { buffer_size: BufferSize::Default, ..config.clone() }))
                .map_err(|e| format!("the output cannot be opened: {e}"))?
        };

        input_stream.play().map_err(|e| format!("the input cannot start: {e}"))?;
        output_stream.play().map_err(|e| format!("the output cannot start: {e}"))?;

        let input_declared = latency::declared(&input.name().unwrap_or_default(), true);
        let output_declared = latency::declared(&output.name().unwrap_or_default(), false);

        Ok(Duplex {
            _input: input_stream,
            _output: output_stream,
            shared,
            block,
            input_rate: input_rate as f32,
            output_rate: output_rate as f32,
            input_declared,
            output_declared,
            dropped_blocks: 0,
            interleaved: vec![0f32; 2 * CHUNK],
        })
    }

    pub fn sample_rate(&self) -> f32 {
        self.input_rate
    }

    pub fn output_rate(&self) -> f32 {
        self.output_rate
    }

    /// The input device's declared latency, in its frames (0 when the system does not tell).
    pub fn input_declared(&self) -> u32 {
        self.input_declared
    }

    /// The output device's declared latency, in its frames (0 when the system does not tell).
    pub fn output_declared(&self) -> u32 {
        self.output_declared
    }

    pub fn dropped_blocks(&self) -> u64 {
        self.dropped_blocks
    }

    fn block_time(&self) -> Duration {
        Duration::from_secs_f64(self.block as f64 / self.input_rate as f64)
    }

    /// Reads `out.len()` frames of input, waiting for at most one block time; returns the frames read (0 when none came in time).
    pub fn read(&mut self, out: &mut [f32]) -> Result<usize, String> {
        self.shared.check()?;
        let n = out.len();
        let input = &self.shared.input;
        // never more than two blocks waiting once this one is read: the oldest go
        self.dropped_blocks += input.keep_at_most(3 * n.max(1), n);
        let deadline = Instant::now() + self.block_time();
        while input.len() < n {
            if Instant::now() >= deadline {
                return Ok(0);
            }
            std::thread::sleep(Duration::from_micros(200));
            self.shared.check()?;
        }
        Ok(input.pop(out))
    }

    /// Writes one stereo block, waiting for at most one block time for room; what does not fit then is left out.
    pub fn write(&mut self, left: &[f32], right: &[f32]) -> Result<(), String> {
        self.shared.check()?;
        let n = left.len().min(right.len()).min(CHUNK);
        for i in 0..n {
            self.interleaved[2 * i] = left[i];
            self.interleaved[2 * i + 1] = right[i];
        }
        let output = &self.shared.output;
        let deadline = Instant::now() + self.block_time();
        while output.capacity() - output.len() < 2 * n && Instant::now() < deadline {
            std::thread::sleep(Duration::from_micros(200));
        }
        output.push(&self.interleaved[..2 * n]);
        Ok(())
    }
}

/// One output frame: left and right on the first two channels (their mean on a mono device), silence on the others.
fn write_frame<T: Copy>(frame: &mut [T], left: f32, right: f32, convert: impl Fn(f32) -> T) {
    match frame.len() {
        0 => {}
        1 => frame[0] = convert(0.5 * (left + right)),
        _ => {
            frame[0] = convert(left);
            frame[1] = convert(right);
            for sample in frame[2..].iter_mut() {
                *sample = convert(0.0);
            }
        }
    }
}
