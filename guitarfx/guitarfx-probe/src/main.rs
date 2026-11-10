//! GuitarFX's latency probe (spec 027, research R8, SC-002). **It is audible**: it plays 20 clicks per setting through the output, so it
//! runs only with `--audible`.
//!
//! The devices are opened as the application opens them (`guitarfx_app::devices::start`), and GuitarFX's own engine
//! (`guitarfx_app::engine::build`) plays the first ready-made sound. The engine is not given the microphone: it is given silence with a
//! click every 3 s, so nothing heard is played back (no howl). The microphone is recorded beside it; each click is found there once the
//! output has brought it back through the air (`analysis`). Its distance from the frame where it went into the engine is the delay a note
//! played at that frame waits: the input's block and ring, the chain, the output's block and buffer, both devices, and the air.
//!
//! usage: guitarfx-probe --audible [--input NAME] [--channel N] [--output NAME] [--blocks 64,128,256] [--volume V] [--report FILE]
//! The devices are the system's default ones when not named; the table goes to the standard output, and to FILE when given.

use cpal::traits::{DeviceTrait, HostTrait, StreamTrait};
use cpal::{BufferSize, Device, SampleFormat, SizedSample, Stream, StreamConfig, SupportedBufferSize};
use guitarfx_app::delay_estimate::{self, Parts};
use guitarfx_app::devices::supported_format;
use guitarfx_app::engine::{self, Config, InputSide, OutputSide, SystemClock};
use guitarfx_dsp::params::{Id, Params};
use guitarfx_dsp::ready_made;
use guitarfx_probe::analysis::{find_delays, row, Schedule, Summary, HEADER};
use std::process::ExitCode;
use std::sync::atomic::{AtomicU32, AtomicUsize, Ordering};
use std::sync::Arc;
use std::time::{Duration, Instant};
use tuxguitar_monitor_native::latency;

/// spec 022's settings and count.
const BLOCKS: &[usize] = &[64, 128, 256];
const CLICKS: usize = 20;
/// The output's volume: the clicks are heard clearly without being loud.
const VOLUME: f64 = 0.5;
/// The most frames of one input callback handled at a time (the engine's own `LARGEST_CALLBACK`).
const LARGEST_CALLBACK: usize = 4096;
/// Seconds allowed beyond the recording before a setting is given up.
const GRACE: Duration = Duration::from_secs(10);
const POLL: Duration = Duration::from_millis(100);

struct Options {
    input: Option<String>,
    channel: u32,
    output: Option<String>,
    blocks: Vec<usize>,
    volume: f64,
    report: Option<String>,
}

fn options() -> Result<Options, String> {
    let mut args = std::env::args().skip(1);
    let mut audible = false;
    let mut options = Options {
        input: None,
        channel: 1,
        output: None,
        blocks: BLOCKS.to_vec(),
        volume: VOLUME,
        report: None,
    };
    while let Some(arg) = args.next() {
        let mut value = || args.next().ok_or(format!("{arg} needs a value"));
        match arg.as_str() {
            "--audible" => audible = true,
            "--input" => options.input = Some(value()?),
            "--channel" => options.channel = value()?.parse().map_err(|_| "--channel takes a number from 1".to_string())?,
            "--output" => options.output = Some(value()?),
            "--blocks" => {
                options.blocks = value()?
                    .split(',')
                    .map(|b| b.trim().parse::<usize>())
                    .collect::<Result<_, _>>()
                    .map_err(|_| "--blocks takes numbers separated by commas".to_string())?
            }
            "--volume" => options.volume = value()?.parse().map_err(|_| "--volume takes a number from 0 to 1".to_string())?,
            "--report" => options.report = Some(value()?),
            _ => return Err(format!("unknown option: {arg}")),
        }
    }
    if !audible {
        return Err("this probe plays 20 loud clicks per setting through the output: run it with --audible, on purpose".to_string());
    }
    Ok(options)
}

/// The block when the device accepts it, else the system's own size (as the application).
fn buffer(supported: &SupportedBufferSize, block: usize) -> BufferSize {
    match supported {
        SupportedBufferSize::Range { min, max } if (*min as usize..=*max as usize).contains(&block) => BufferSize::Fixed(block as u32),
        _ => BufferSize::Default,
    }
}

fn named(devices: Result<impl Iterator<Item = Device>, cpal::DevicesError>, name: &str) -> Result<Device, String> {
    devices
        .map_err(|e| e.to_string())?
        .find(|d| d.name().map(|n| n == name).unwrap_or(false))
        .ok_or(format!("no device named {name}"))
}

/// What the input callback shares with the main thread: the recording, preallocated, and the callbacks' sizes.
struct Recording {
    samples: Vec<AtomicU32>,
    position: AtomicUsize,
    input_frames: AtomicU32,
    output_frames: AtomicU32,
}

fn input_stream<S: SizedSample + 'static>(
    device: &Device,
    config: &StreamConfig,
    channel: usize,
    mut side: InputSide,
    convert: fn(S) -> f32,
    recording: Arc<Recording>,
    schedule: Schedule,
) -> Result<Stream, String> {
    let channels = usize::from(config.channels.max(1));
    let channel = if channel < channels { channel } else { 0 };
    let mut clicks = vec![0.0f32; LARGEST_CALLBACK];
    device
        .build_input_stream(
            config,
            move |data: &[S], _: &cpal::InputCallbackInfo| {
                recording.input_frames.store((data.len() / channels) as u32, Ordering::Relaxed);
                for frames in data.chunks(LARGEST_CALLBACK * channels) {
                    let n = frames.len() / channels;
                    let mut position = recording.position.load(Ordering::Relaxed);
                    for (i, click) in clicks[..n].iter_mut().enumerate() {
                        if let Some(slot) = recording.samples.get(position) {
                            slot.store(convert(frames[i * channels + channel]).to_bits(), Ordering::Relaxed);
                        }
                        *click = schedule.click_value(position);
                        position += 1;
                    }
                    recording.position.store(position, Ordering::Release);
                    side.on_input(&clicks[..n], |s| s);
                }
            },
            |e| eprintln!("input: {e}"),
            None,
        )
        .map_err(|e| e.to_string())
}

fn output_stream<S: SizedSample + 'static>(
    device: &Device,
    config: &StreamConfig,
    mut side: OutputSide<SystemClock>,
    convert: fn(f32) -> S,
    recording: Arc<Recording>,
) -> Result<Stream, String> {
    let channels = usize::from(config.channels.max(1));
    device
        .build_output_stream(
            config,
            move |data: &mut [S], _: &cpal::OutputCallbackInfo| {
                recording.output_frames.store((data.len() / channels) as u32, Ordering::Relaxed);
                side.on_output(data, convert);
            },
            |e| eprintln!("output: {e}"),
            None,
        )
        .map_err(|e| e.to_string())
}

/// One setting: the devices opened at `block`, the clicks played, recorded and found.
fn run(options: &Options, input: &Device, output: &Device, block: usize) -> Result<Summary, String> {
    let input_name = input.name().map_err(|e| e.to_string())?;
    let output_name = output.name().map_err(|e| e.to_string())?;
    let input_supported = input.default_input_config().map_err(|e| e.to_string())?;
    let output_supported = output.default_output_config().map_err(|e| e.to_string())?;
    if !supported_format(input_supported.sample_format()) || !supported_format(output_supported.sample_format()) {
        return Err("a device's sample format is not supported".to_string());
    }
    let block_text = block.to_string();
    let params = Params::from_values([(Params::entry(Id::DevicesBlock).key, block_text.as_str())]);
    let block = params.get(Id::DevicesBlock) as usize;
    let input_config = StreamConfig {
        buffer_size: buffer(input_supported.buffer_size(), block),
        ..input_supported.config()
    };
    let output_config = StreamConfig {
        buffer_size: buffer(output_supported.buffer_size(), block),
        ..output_supported.config()
    };
    let config = Config {
        input_rate: input_config.sample_rate.0 as f32,
        output_rate: output_config.sample_rate.0 as f32,
        // the engine is given one channel: the clicks
        input_channels: 1,
        channel: 0,
        output_channels: usize::from(output_config.channels),
        resample: input_config.sample_rate != output_config.sample_rate || input_name != output_name,
    };
    let (input_side, output_side, control) = engine::build(&config, &params, ready_made::first(), options.volume, SystemClock::default());
    let schedule = Schedule::new(config.input_rate, CLICKS);
    let recording = Arc::new(Recording {
        samples: (0..schedule.total_frames()).map(|_| AtomicU32::new(0)).collect(),
        position: AtomicUsize::new(0),
        input_frames: AtomicU32::new(0),
        output_frames: AtomicU32::new(0),
    });
    let channel = options.channel.saturating_sub(1) as usize;
    let input_stream = match input_supported.sample_format() {
        SampleFormat::I16 => input_stream(input, &input_config, channel, input_side, engine::from_i16, recording.clone(), schedule),
        SampleFormat::I32 => input_stream(input, &input_config, channel, input_side, engine::from_i32, recording.clone(), schedule),
        _ => input_stream(input, &input_config, channel, input_side, |s: f32| s, recording.clone(), schedule),
    }?;
    let output_stream = match output_supported.sample_format() {
        SampleFormat::I16 => output_stream(output, &output_config, output_side, engine::to_i16, recording.clone()),
        SampleFormat::I32 => output_stream(output, &output_config, output_side, engine::to_i32, recording.clone()),
        _ => output_stream(output, &output_config, output_side, |s: f32| s, recording.clone()),
    }?;
    input_stream.play().map_err(|e| e.to_string())?;
    output_stream.play().map_err(|e| e.to_string())?;
    let limit = Instant::now() + Duration::from_secs_f64(schedule.total_frames() as f64 / f64::from(schedule.rate)) + GRACE;
    while recording.position.load(Ordering::Acquire) < schedule.total_frames() && Instant::now() < limit {
        std::thread::sleep(POLL);
    }
    drop(input_stream);
    drop(output_stream);
    let recorded = recording.position.load(Ordering::Acquire).min(schedule.total_frames());
    let captured: Vec<f32> = recording.samples[..recorded]
        .iter()
        .map(|s| f32::from_bits(s.load(Ordering::Relaxed)))
        .collect();
    let frames = |size: BufferSize, seen: &AtomicU32| match (seen.load(Ordering::Relaxed), size) {
        (seen, _) if seen > 0 => seen,
        (_, BufferSize::Fixed(frames)) => frames,
        (_, BufferSize::Default) => block as u32,
    };
    let parts = Parts {
        input_rate: config.input_rate,
        output_rate: config.output_rate,
        input_block: frames(input_config.buffer_size, &recording.input_frames),
        output_block: frames(output_config.buffer_size, &recording.output_frames),
        input_declared: latency::declared(&input_name, true),
        output_declared: latency::declared(&output_name, false),
        resample_fill: if config.resample {
            params.get(Id::DevicesResampleFill) * block as f64
        } else {
            0.0
        },
    };
    if control.state() != engine::State::On {
        eprintln!("block {block}: the sound ended {:?}", control.state());
    }
    Ok(Summary {
        block,
        rate: config.input_rate as u32,
        found: find_delays(&captured, &schedule),
        clicks: CLICKS,
        estimate: delay_estimate::milliseconds(&parts),
        dropped: control.dropped_blocks(),
        underruns: control.underruns(),
    })
}

fn probe(options: &Options) -> Result<String, String> {
    let host = cpal::default_host();
    let input = match &options.input {
        Some(name) => named(host.input_devices(), name)?,
        None => host.default_input_device().ok_or("no default input")?,
    };
    let output = match &options.output {
        Some(name) => named(host.output_devices(), name)?,
        None => host.default_output_device().ok_or("no default output")?,
    };
    let input_name = input.name().unwrap_or_default();
    let output_name = output.name().unwrap_or_default();
    let mut report = String::new();
    report.push_str("# GuitarFX delay probe (spec 027, research R8)\n\n");
    report.push_str(&format!(
        "- GuitarFX {}, {} {}\n",
        env!("CARGO_PKG_VERSION"),
        std::env::consts::OS,
        std::env::consts::ARCH
    ));
    report.push_str(&format!("- Input: {input_name} (channel {}); output: {output_name}\n", options.channel));
    report.push_str(&format!(
        "- {CLICKS} clicks of 1 ms, 3 s apart, per setting, through GuitarFX's engine playing the first ready-made sound at volume {}; the input is never played back.\n",
        options.volume
    ));
    report.push_str("- Delay = the click's frame in the input's recording minus the frame where it went into the engine: the input's block and ring, the chain, the output, both devices and the air.\n");
    report.push_str("- Estimate: the window's (both callbacks' blocks, both declared latencies, the resampler's fill).\n\n");
    report.push_str(HEADER);
    report.push('\n');
    for &block in &options.blocks {
        eprintln!("block {block}: playing the clicks…");
        let line = row(&run(options, &input, &output, block)?);
        eprintln!("{line}");
        report.push_str(&line);
        report.push('\n');
    }
    Ok(report)
}

fn main() -> ExitCode {
    let options = match options() {
        Ok(options) => options,
        Err(message) => {
            eprintln!("{message}");
            return ExitCode::from(2);
        }
    };
    match probe(&options) {
        Ok(report) => {
            print!("{report}");
            if let Some(path) = &options.report {
                if let Err(error) = std::fs::write(path, &report) {
                    eprintln!("cannot write {path}: {error}");
                    return ExitCode::FAILURE;
                }
            }
            ExitCode::SUCCESS
        }
        Err(message) => {
            eprintln!("{message}");
            ExitCode::FAILURE
        }
    }
}
