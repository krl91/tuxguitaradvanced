//! The devices (spec 027, FR-003, FR-012, FR-013): the system's inputs, with their channels, and outputs; a remembered device found by its
//! exact name, never replaced by another; the two cpal streams that drive the engine; and cpal's errors as the messages of
//! contracts/application.md. A lost device, a denied permission or an unsupported format stop the sound; the application stays open.

use crate::delay_estimate::Parts;
use crate::engine::{self, Config, Control, InputSide, OutputSide, SystemClock};
use cpal::traits::{DeviceTrait, HostTrait, StreamTrait};
use cpal::{
    BufferSize, BuildStreamError, DefaultStreamConfigError, Device, PlayStreamError, SampleFormat, SizedSample, Stream, StreamConfig, StreamError,
    SupportedBufferSize,
};
use guitarfx_dsp::params::{Id, Params};
use guitarfx_dsp::settings::ChainParams;
use std::sync::atomic::{AtomicU32, AtomicU8, Ordering};
use std::sync::Arc;
use tuxguitar_monitor_native::latency;

/// Why the sound cannot go on.
#[derive(Clone, Debug, PartialEq, Eq)]
pub enum Problem {
    /// The remembered device is not connected.
    Missing {
        input: bool,
    },
    /// The device went away, or is not available.
    Lost {
        input: bool,
    },
    PermissionDenied,
    Unsupported,
    /// Anything else, with the system's words.
    Failed(String),
}

/// Words of the systems' refusals: CoreAudio and ALSA ("permission", "not authorized"), WASAPI ("Access is denied", E_ACCESSDENIED).
const DENIED: &[&str] = &["permission", "not authorized", "not authorised", "access is denied", "denied"];

impl Problem {
    fn from_backend(description: &str) -> Problem {
        let lower = description.to_lowercase();
        if DENIED.iter().any(|word| lower.contains(word)) {
            Problem::PermissionDenied
        } else {
            Problem::Failed(description.to_string())
        }
    }

    pub fn from_build(error: &BuildStreamError, input: bool) -> Problem {
        match error {
            BuildStreamError::DeviceNotAvailable => Problem::Lost { input },
            BuildStreamError::StreamConfigNotSupported | BuildStreamError::InvalidArgument => Problem::Unsupported,
            BuildStreamError::StreamIdOverflow => Problem::Failed(error.to_string()),
            BuildStreamError::BackendSpecific { err } => Problem::from_backend(&err.description),
        }
    }

    pub fn from_stream(error: &StreamError, input: bool) -> Problem {
        match error {
            StreamError::DeviceNotAvailable => Problem::Lost { input },
            StreamError::BackendSpecific { err } => Problem::from_backend(&err.description),
        }
    }

    pub fn from_play(error: &PlayStreamError, input: bool) -> Problem {
        match error {
            PlayStreamError::DeviceNotAvailable => Problem::Lost { input },
            PlayStreamError::BackendSpecific { err } => Problem::from_backend(&err.description),
        }
    }

    pub fn from_config(error: &DefaultStreamConfigError, input: bool) -> Problem {
        match error {
            DefaultStreamConfigError::DeviceNotAvailable => Problem::Lost { input },
            DefaultStreamConfigError::StreamTypeNotSupported => Problem::Unsupported,
            DefaultStreamConfigError::BackendSpecific { err } => Problem::from_backend(&err.description),
        }
    }

    /// The key of its message in the texts.
    pub fn key(&self) -> &'static str {
        match self {
            Problem::Missing { input: true } => "message.missing.input",
            Problem::Missing { input: false } => "message.missing.output",
            Problem::Lost { input: true } => "message.lost.input",
            Problem::Lost { input: false } => "message.lost.output",
            Problem::PermissionDenied => "message.permission",
            Problem::Unsupported => "message.unsupported",
            Problem::Failed(_) => "message.failed",
        }
    }

    /// The problem's code for the error callbacks (an atomic, no lock), and back. A failure's words are not kept.
    fn code(&self) -> u8 {
        let code = match self {
            Problem::Missing { input: true } => Code::MissingInput,
            Problem::Missing { input: false } => Code::MissingOutput,
            Problem::Lost { input: true } => Code::LostInput,
            Problem::Lost { input: false } => Code::LostOutput,
            Problem::PermissionDenied => Code::PermissionDenied,
            Problem::Unsupported => Code::Unsupported,
            Problem::Failed(_) => Code::Failed,
        };
        code as u8
    }

    fn of_code(value: u8) -> Option<Problem> {
        let code = Code::ALL.iter().copied().find(|c| *c as u8 == value)?;
        Some(match code {
            Code::None => return None,
            Code::MissingInput => Problem::Missing { input: true },
            Code::MissingOutput => Problem::Missing { input: false },
            Code::LostInput => Problem::Lost { input: true },
            Code::LostOutput => Problem::Lost { input: false },
            Code::PermissionDenied => Problem::PermissionDenied,
            Code::Unsupported => Problem::Unsupported,
            Code::Failed => Problem::Failed(String::new()),
        })
    }
}

#[derive(Clone, Copy, PartialEq, Eq)]
#[repr(u8)]
enum Code {
    None,
    MissingInput,
    MissingOutput,
    LostInput,
    LostOutput,
    PermissionDenied,
    Unsupported,
    Failed,
}

impl Code {
    const ALL: &[Code] = &[
        Code::None,
        Code::MissingInput,
        Code::MissingOutput,
        Code::LostInput,
        Code::LostOutput,
        Code::PermissionDenied,
        Code::Unsupported,
        Code::Failed,
    ];
}

/// A microphone whose name says it is the computer's own (`InputDeviceCatalog.classify`): the speakers beside it can howl (FR-012).
pub fn is_built_in_microphone(name: &str) -> bool {
    let normalized = name.to_lowercase();
    let microphone = normalized.contains("microphone") || normalized.contains("micro");
    let built_in = normalized.contains("built-in") || normalized.contains("internal") || normalized.contains("macbook");
    microphone && built_in
}

/// The device of that exact name; none when it is not connected (it is shown as missing, never replaced).
pub fn find_exact(names: &[String], wanted: &str) -> Option<usize> {
    if wanted.is_empty() {
        return None;
    }
    names.iter().position(|n| n == wanted)
}

pub fn supported_format(format: SampleFormat) -> bool {
    matches!(format, SampleFormat::F32 | SampleFormat::I16 | SampleFormat::I32)
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Input {
    pub name: String,
    pub channels: u16,
}

#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub struct DeviceList {
    pub inputs: Vec<Input>,
    pub outputs: Vec<String>,
}

/// The system's devices now (FR-003); a device whose name or configuration cannot be read is left out.
pub fn list() -> DeviceList {
    let host = cpal::default_host();
    let mut found = DeviceList::default();
    if let Ok(devices) = host.input_devices() {
        for device in devices {
            if let (Ok(name), Ok(config)) = (device.name(), device.default_input_config()) {
                found.inputs.push(Input {
                    name,
                    channels: config.channels(),
                });
            }
        }
    }
    if let Ok(devices) = host.output_devices() {
        found.outputs.extend(devices.filter_map(|d| d.name().ok()));
    }
    found
}

fn device(devices: Result<impl Iterator<Item = Device>, cpal::DevicesError>, name: &str, input: bool) -> Result<Device, Problem> {
    let devices: Vec<Device> = devices.map_err(|e| Problem::Failed(e.to_string()))?.collect();
    let names: Vec<String> = devices.iter().map(|d| d.name().unwrap_or_default()).collect();
    let index = find_exact(&names, name).ok_or(Problem::Missing { input })?;
    Ok(devices.into_iter().nth(index).expect("found among them"))
}

/// The block when the device accepts it, else the system's own size.
fn buffer(supported: &SupportedBufferSize, block: usize) -> BufferSize {
    match supported {
        SupportedBufferSize::Range { min, max } if (*min as usize..=*max as usize).contains(&block) => BufferSize::Fixed(block as u32),
        _ => BufferSize::Default,
    }
}

/// What the error callbacks and the first callbacks tell the UI thread.
#[derive(Default)]
struct Watch {
    problem: AtomicU8,
    input_frames: AtomicU32,
    output_frames: AtomicU32,
}

impl Watch {
    fn fail(&self, problem: &Problem) {
        let _ = self
            .problem
            .compare_exchange(Code::None as u8, problem.code(), Ordering::AcqRel, Ordering::Acquire);
    }
}

fn input_stream<S: SizedSample + 'static>(
    device: &Device,
    config: &StreamConfig,
    mut side: InputSide,
    convert: fn(S) -> f32,
    watch: Arc<Watch>,
) -> Result<Stream, BuildStreamError> {
    let errors = watch.clone();
    let channels = usize::from(config.channels.max(1));
    device.build_input_stream(
        config,
        move |data: &[S], _: &cpal::InputCallbackInfo| {
            watch.input_frames.store((data.len() / channels) as u32, Ordering::Relaxed);
            side.on_input(data, convert);
        },
        move |e| errors.fail(&Problem::from_stream(&e, true)),
        None,
    )
}

fn output_stream<S: SizedSample + 'static>(
    device: &Device,
    config: &StreamConfig,
    mut side: OutputSide<SystemClock>,
    convert: fn(f32) -> S,
    watch: Arc<Watch>,
    block: usize,
) -> Result<Stream, BuildStreamError> {
    let errors = watch.clone();
    let channels = usize::from(config.channels.max(1));
    let rate = config.sample_rate.0 as f32;
    let mut first = true;
    device.build_output_stream(
        config,
        move |data: &mut [S], _: &cpal::OutputCallbackInfo| {
            if first {
                first = false;
                promote(block, rate);
            }
            watch.output_frames.store((data.len() / channels) as u32, Ordering::Relaxed);
            side.on_output(data, convert);
        },
        move |e| errors.fail(&Problem::from_stream(&e, false)),
        None,
    )
}

/// The output thread asks for the scheduling of an audio thread, once, before its first block is heard (its words of refusal are made and
/// freed this once). CoreAudio's own I/O thread already has it, and is already in its device's workgroup, so nothing is asked there.
#[cfg(not(target_os = "macos"))]
fn promote(block: usize, rate: f32) {
    let _ = tuxguitar_monitor_native::realtime::promote_current_thread(block, rate);
}

#[cfg(target_os = "macos")]
fn promote(_block: usize, _rate: f32) {}

/// The sound on: the two streams playing through the engine. Dropping it closes the devices.
pub struct Running {
    _input: Stream,
    _output: Stream,
    pub control: Control,
    watch: Arc<Watch>,
    parts: Parts,
}

impl Running {
    /// A problem reported by a device since the start.
    pub fn problem(&self) -> Option<Problem> {
        Problem::of_code(self.watch.problem.load(Ordering::Acquire))
    }

    /// The estimate's parts, with the callbacks' frames once known.
    pub fn parts(&self) -> Parts {
        let mut parts = self.parts;
        let input = self.watch.input_frames.load(Ordering::Relaxed);
        let output = self.watch.output_frames.load(Ordering::Relaxed);
        if input > 0 {
            parts.input_block = input;
        }
        if output > 0 {
            parts.output_block = output;
        }
        parts
    }
}

/// Opens the input (its `channel`, 1-based) and the output, and plays `chain` through them.
pub fn start(input_name: &str, channel: u32, output_name: &str, params: &Params, chain: ChainParams, volume: f64) -> Result<Running, Problem> {
    let host = cpal::default_host();
    let input = device(host.input_devices(), input_name, true)?;
    let output = device(host.output_devices(), output_name, false)?;
    let input_supported = input.default_input_config().map_err(|e| Problem::from_config(&e, true))?;
    let output_supported = output.default_output_config().map_err(|e| Problem::from_config(&e, false))?;
    if !supported_format(input_supported.sample_format()) || !supported_format(output_supported.sample_format()) {
        return Err(Problem::Unsupported);
    }
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
        input_channels: usize::from(input_config.channels),
        channel: channel.saturating_sub(1) as usize,
        output_channels: usize::from(output_config.channels),
        // two devices run on two clocks that drift apart, even at the same nominal rate
        resample: input_config.sample_rate != output_config.sample_rate || input_name != output_name,
    };
    let (input_side, output_side, control) = engine::build(&config, params, chain, volume, SystemClock::default());
    let resample_fill = if config.resample {
        params.get(Id::DevicesResampleFill) * block as f64
    } else {
        0.0
    };
    let watch = Arc::new(Watch::default());
    let input_stream = match input_supported.sample_format() {
        SampleFormat::I16 => input_stream(&input, &input_config, input_side, engine::from_i16, watch.clone()),
        SampleFormat::I32 => input_stream(&input, &input_config, input_side, engine::from_i32, watch.clone()),
        _ => input_stream(&input, &input_config, input_side, |s: f32| s, watch.clone()),
    }
    .map_err(|e| Problem::from_build(&e, true))?;
    let output_stream = match output_supported.sample_format() {
        SampleFormat::I16 => output_stream(&output, &output_config, output_side, engine::to_i16, watch.clone(), block),
        SampleFormat::I32 => output_stream(&output, &output_config, output_side, engine::to_i32, watch.clone(), block),
        _ => output_stream(&output, &output_config, output_side, |s: f32| s, watch.clone(), block),
    }
    .map_err(|e| Problem::from_build(&e, false))?;
    input_stream.play().map_err(|e| Problem::from_play(&e, true))?;
    output_stream.play().map_err(|e| Problem::from_play(&e, false))?;
    let frames = |size: BufferSize| match size {
        BufferSize::Fixed(frames) => frames,
        BufferSize::Default => block as u32,
    };
    let parts = Parts {
        input_rate: config.input_rate,
        output_rate: config.output_rate,
        input_block: frames(input_config.buffer_size),
        output_block: frames(output_config.buffer_size),
        input_declared: latency::declared(input_name, true),
        output_declared: latency::declared(output_name, false),
        resample_fill,
    };
    Ok(Running {
        _input: input_stream,
        _output: output_stream,
        control,
        watch,
        parts,
    })
}
