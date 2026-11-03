//! The Java side's entry points of the native recognition (spec 023, contract §1): the static native methods of
//! `app.tuxguitar.audio.engine.nativeengine.NativeRecognitionLibrary`. Errors become `NativeRecognitionException`s whose message is
//! "REASON: detail".

use jni::objects::{JClass, JFloatArray, JIntArray, JString};
use jni::sys::{jboolean, jfloat, jfloatArray, jint, jlong};
use jni::JNIEnv;

use crate::analysis::front_end::FrontEnd;
use crate::analysis::pipeline::{self, HopResult, Pipeline, Request};
use crate::analysis::chord::ExpectedChord;
use crate::analysis::strum_clip::{self, StrumClip, StrumShared};
use crate::analysis::Parameters;
use crate::workgroup::Workgroup;
use crate::capture::{Capture, CaptureShared};
use crate::recognition::{self, front_of, strum_of, AttackSlot, Config, Requests, Shared, Worker, HOP_BLOCK};
use std::sync::{Arc, Mutex};
use std::thread::JoinHandle;
use std::time::{Duration, Instant};

const EXCEPTION: &str = "app/tuxguitar/audio/engine/nativeengine/NativeRecognitionException";
/// The capture's ring: 1024 blocks of 128 frames, more than a second at 96 kHz before a block is dropped.
const CAPTURE_BLOCKS: usize = 1024;

fn fail(env: &mut JNIEnv, message: &str) {
    let _ = env.throw_new(EXCEPTION, message);
}

/// A live recognition. The handle Java holds is the address of a boxed `LiveHandle`: its `Live` is taken at close (the stream stopped, the
/// thread joined), the small box itself stays, so a late or repeated call on a closed handle finds nothing instead of freed memory.
struct LiveHandle {
    live: Mutex<Option<Live>>,
    frames_scratch: Mutex<Vec<f32>>,
    hops_scratch: Mutex<Vec<f32>>,
}

struct Live {
    shared: Arc<Shared>,
    capture: Capture,
    thread: Option<JoinHandle<()>>,
    parameters: Parameters,
    requests: Requests,
}

fn live_handle<'a>(handle: jlong) -> Option<&'a LiveHandle> {
    if handle == 0 {
        return None;
    }
    // SAFETY: a non-zero handle is a box made by open, never freed
    Some(unsafe { &*(handle as *const LiveHandle) })
}

/// Runs `f` on the open recognition of `handle`, under its lock (Java's threads only: the analysis thread never takes it).
fn with_live<T>(handle: jlong, f: impl FnOnce(&mut Live) -> T) -> Option<T> {
    let handle = live_handle(handle)?;
    let mut live = handle.live.lock().unwrap_or_else(|e| e.into_inner());
    live.as_mut().map(f)
}

fn shared_of(handle: jlong) -> Option<Arc<Shared>> {
    with_live(handle, |live| live.shared.clone())
}

fn parameters_of(env: &mut JNIEnv, parameters: &JFloatArray) -> Option<Parameters> {
    floats(env, parameters).and_then(|p| Parameters::from_slice(&p))
}

#[no_mangle]
pub extern "system" fn Java_app_tuxguitar_audio_engine_nativeengine_NativeRecognitionLibrary_open<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    input: JString<'local>,
    channel: jint,
    _block: jint,
    java_nanos_at_open: jlong,
    parameters: JFloatArray<'local>,
) -> jlong {
    let name: String = if input.is_null() { String::new() } else { env.get_string(&input).map(|s| s.into()).unwrap_or_default() };
    let Some(parameters) = parameters_of(&mut env, &parameters) else {
        fail(&mut env, "STREAM_FAILED: the parameters are missing or too short");
        return 0;
    };
    // the blocks are always of 128 frames (capture::BLOCK), two to a hop, whatever the system's callback size
    let capture_shared = Arc::new(CaptureShared::new(CAPTURE_BLOCKS));
    let origin = Instant::now();
    let capture = match Capture::open(&name, channel, capture_shared.clone(), origin) {
        Ok(capture) => capture,
        Err((reason, detail)) => {
            fail(&mut env, &format!("{}: {}", reason.name(), detail));
            return 0;
        }
    };
    let rate = capture.sample_rate() as i32;
    let shared = Arc::new(Shared::new(capture_shared, rate, java_nanos_at_open));
    let worker = Worker::new(shared.clone(), parameters, front_of(&parameters), strum_of(&parameters));
    let thread = recognition::start_in(worker, Workgroup::of_device(capture.name()));
    let live = Live { shared, capture, thread: Some(thread), parameters, requests: Requests::default() };
    let handle = LiveHandle { live: Mutex::new(Some(live)), frames_scratch: Mutex::new(Vec::new()), hops_scratch: Mutex::new(Vec::new()) };
    Box::into_raw(Box::new(handle)) as jlong
}

#[no_mangle]
pub extern "system" fn Java_app_tuxguitar_audio_engine_nativeengine_NativeRecognitionLibrary_sampleRate<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
) -> jfloat {
    with_live(handle, |live| live.capture.sample_rate()).unwrap_or(0.0)
}

#[no_mangle]
pub extern "system" fn Java_app_tuxguitar_audio_engine_nativeengine_NativeRecognitionLibrary_configure<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    parameters: JFloatArray<'local>,
) {
    let Some(parameters) = parameters_of(&mut env, &parameters) else {
        return;
    };
    with_live(handle, |live| {
        // the new pipeline is built here, on Java's thread, with the requests made so far: the loop only swaps it in
        let pipeline = if recognition::analysis_changed(&live.parameters, &parameters) { Some(recognition::pipeline_for(live.shared.rate, parameters, &live.requests)) } else { None };
        live.parameters = parameters;
        live.shared.config.post(Box::new(Config { pipeline, front: front_of(&parameters), strum: strum_of(&parameters) }));
    });
}

#[no_mangle]
pub extern "system" fn Java_app_tuxguitar_audio_engine_nativeengine_NativeRecognitionLibrary_setSpectrumEnabled<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    on: jboolean,
) {
    with_live(handle, |live| {
        live.requests.spectrum = on != 0;
        live.shared.set_spectrum(on != 0);
    });
}

#[no_mangle]
pub extern "system" fn Java_app_tuxguitar_audio_engine_nativeengine_NativeRecognitionLibrary_setExpectedPitches<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    midi: JIntArray<'local>,
) {
    let midi = ints(&mut env, &midi);
    with_live(handle, |live| {
        live.requests.expected = midi.clone();
        live.shared.expected.post(Box::new(midi));
    });
}

#[no_mangle]
pub extern "system" fn Java_app_tuxguitar_audio_engine_nativeengine_NativeRecognitionLibrary_setExpectedChord<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    required: JIntArray<'local>,
    tolerated: JIntArray<'local>,
) {
    let chord = if required.is_null() { None } else { Some((ints(&mut env, &required), ints(&mut env, &tolerated))) };
    with_live(handle, |live| {
        let expected = chord.as_ref().map(|(r, t)| ExpectedChord::new(r, t));
        live.requests.chord = chord;
        live.shared.chord.post(Box::new(expected));
    });
}

#[no_mangle]
pub extern "system" fn Java_app_tuxguitar_audio_engine_nativeengine_NativeRecognitionLibrary_setAttackCandidates<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    pitches: JIntArray<'local>,
) {
    let pitches = ints(&mut env, &pitches);
    with_live(handle, |live| {
        live.shared.attack.post(Box::new(AttackSlot::of(live.shared.rate, &pitches)));
        live.requests.attack = pitches;
    });
}

/// A wait of Java's frame or hop thread: into a scratch buffer as long as the Java array, then copied into it.
fn wait_into(
    env: &mut JNIEnv,
    handle: jlong,
    array: &JFloatArray,
    timeout_millis: jint,
    scratch: impl FnOnce(&LiveHandle) -> &Mutex<Vec<f32>>,
    wait: impl FnOnce(&Shared, &mut [f32], Duration) -> (i32, usize),
) -> jint {
    let Some(live) = live_handle(handle) else {
        return -2;
    };
    let Some(shared) = shared_of(handle) else {
        return -2;
    };
    let length = env.get_array_length(array).unwrap_or(0).max(0) as usize;
    let mut buffer = scratch(live).lock().unwrap_or_else(|e| e.into_inner());
    buffer.resize(length, 0.0);
    let (result, floats) = wait(&shared, &mut buffer, Duration::from_millis(timeout_millis.max(0) as u64));
    if result > 0 && env.set_float_array_region(array, 0, &buffer[..floats]).is_err() {
        return -1;
    }
    result
}

#[no_mangle]
pub extern "system" fn Java_app_tuxguitar_audio_engine_nativeengine_NativeRecognitionLibrary_nextFrames<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    records: JFloatArray<'local>,
    timeout_millis: jint,
) -> jint {
    wait_into(&mut env, handle, &records, timeout_millis, |h| &h.frames_scratch, |shared, out, timeout| {
        let count = shared.next_frames(out, timeout);
        let mut floats = 0;
        for _ in 0..count.max(0) {
            floats += out[floats] as usize;
        }
        (count, floats)
    })
}

#[no_mangle]
pub extern "system" fn Java_app_tuxguitar_audio_engine_nativeengine_NativeRecognitionLibrary_nextHops<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    hops: JFloatArray<'local>,
    timeout_millis: jint,
) -> jint {
    wait_into(&mut env, handle, &hops, timeout_millis, |h| &h.hops_scratch, |shared, out, timeout| {
        let count = shared.next_hops(out, timeout);
        (count, count.max(0) as usize * HOP_BLOCK)
    })
}

#[no_mangle]
pub extern "system" fn Java_app_tuxguitar_audio_engine_nativeengine_NativeRecognitionLibrary_setHopsEnabled<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    on: jboolean,
) {
    with_live(handle, |live| live.shared.set_hops_enabled(on != 0));
}

#[no_mangle]
pub extern "system" fn Java_app_tuxguitar_audio_engine_nativeengine_NativeRecognitionLibrary_droppedBlocks<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
) -> jlong {
    with_live(handle, |live| live.shared.dropped_blocks() as jlong).unwrap_or(0)
}

#[no_mangle]
pub extern "system" fn Java_app_tuxguitar_audio_engine_nativeengine_NativeRecognitionLibrary_close<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
) {
    let Some(handle) = live_handle(handle) else {
        return;
    };
    let taken = handle.live.lock().unwrap_or_else(|e| e.into_inner()).take();
    if let Some(mut live) = taken {
        // the waits return −2 at once, the loop ends at its next iteration, then the stream stops; the rings go with the last reader
        live.shared.close();
        if let Some(thread) = live.thread.take() {
            let _ = thread.join();
        }
        drop(live);
    }
}

#[no_mangle]
pub extern "system" fn Java_app_tuxguitar_audio_engine_nativeengine_NativeRecognitionLibrary_strumClip<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
) -> jfloatArray {
    let reading = with_live(handle, |live| live.shared.strum.snapshot()).unwrap_or(strum_clip::NONE);
    float_array(&mut env, &[reading.verdict as f32, reading.clipped_share as f32, reading.rms_dbfs as f32])
}

#[no_mangle]
pub extern "system" fn Java_app_tuxguitar_audio_engine_nativeengine_NativeRecognitionLibrary_resetStrumClip<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
) {
    with_live(handle, |live| live.shared.strum.reset());
}

#[no_mangle]
pub extern "system" fn Java_app_tuxguitar_audio_engine_nativeengine_NativeRecognitionLibrary_stats<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
) -> jfloatArray {
    let stats = with_live(handle, |live| {
        let (median, p99) = live.shared.costs.percentiles();
        [live.shared.dropped_blocks() as f32, median as f32, p99 as f32]
    })
    .unwrap_or([0.0, 0.0, 0.0]);
    float_array(&mut env, &stats)
}

fn floats(env: &mut JNIEnv, array: &JFloatArray) -> Option<Vec<f32>> {
    if array.is_null() {
        return None;
    }
    let length = env.get_array_length(array).ok()? as usize;
    let mut values = vec![0f32; length];
    env.get_float_array_region(array, 0, &mut values).ok()?;
    Some(values)
}

fn ints(env: &mut JNIEnv, array: &JIntArray) -> Vec<i32> {
    if array.is_null() {
        return Vec::new();
    }
    let length = env.get_array_length(array).unwrap_or(0) as usize;
    let mut values = vec![0i32; length];
    if env.get_int_array_region(array, 0, &mut values).is_err() {
        return Vec::new();
    }
    values
}

fn float_array(env: &mut JNIEnv, values: &[f32]) -> jfloatArray {
    match env.new_float_array(values.len() as i32) {
        Ok(array) => {
            if env.set_float_array_region(&array, 0, values).is_err() {
                return std::ptr::null_mut();
            }
            array.into_raw()
        }
        Err(_) => std::ptr::null_mut(),
    }
}

/// The samples and the parameters of an offline call, or the exception thrown.
fn offline_input(env: &mut JNIEnv, mono: &JFloatArray, rate: jint, parameters: &JFloatArray) -> Option<(Vec<f32>, Parameters)> {
    let Some(samples) = floats(env, mono) else {
        fail(env, "STREAM_FAILED: no samples");
        return None;
    };
    let Some(parameters) = floats(env, parameters).and_then(|p| Parameters::from_slice(&p)) else {
        fail(env, "STREAM_FAILED: the parameters are missing or too short");
        return None;
    };
    if rate <= 0 {
        fail(env, "FORMAT_UNSUPPORTED: invalid sample rate");
        return None;
    }
    Some((samples, parameters))
}

#[no_mangle]
pub extern "system" fn Java_app_tuxguitar_audio_engine_nativeengine_NativeRecognitionLibrary_analyze<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    mono: JFloatArray<'local>,
    rate: jint,
    parameters: JFloatArray<'local>,
    spectrum: jboolean,
    attack_candidates: JIntArray<'local>,
    expected_pitches: JIntArray<'local>,
    chord_required: JIntArray<'local>,
    chord_tolerated: JIntArray<'local>,
) -> jfloatArray {
    let Some((samples, parameters)) = offline_input(&mut env, &mono, rate, &parameters) else {
        return std::ptr::null_mut();
    };
    let attack = ints(&mut env, &attack_candidates);
    let expected = ints(&mut env, &expected_pitches);
    let required = ints(&mut env, &chord_required);
    let tolerated = ints(&mut env, &chord_tolerated);
    let request = Request { spectrum: spectrum != 0, attack_candidates: &attack, expected_pitches: &expected, chord_required: &required, chord_tolerated: &tolerated };
    let records = pipeline::analyze(&samples, rate, parameters, request);
    float_array(&mut env, &records)
}

#[no_mangle]
pub extern "system" fn Java_app_tuxguitar_audio_engine_nativeengine_NativeRecognitionLibrary_frontEnd<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    mono: JFloatArray<'local>,
    rate: jint,
    parameters: JFloatArray<'local>,
) -> jfloatArray {
    let Some((samples, parameters)) = offline_input(&mut env, &mono, rate, &parameters) else {
        return std::ptr::null_mut();
    };
    let mut front = FrontEnd::new(rate as f64, pipeline::HOP);
    front.set(front_of(&parameters));
    let hops = samples.len() / pipeline::HOP;
    let mut out = samples[..hops * pipeline::HOP].to_vec();
    for hop in out.chunks_exact_mut(pipeline::HOP) {
        front.process(hop);
    }
    float_array(&mut env, &out)
}

#[no_mangle]
pub extern "system" fn Java_app_tuxguitar_audio_engine_nativeengine_NativeRecognitionLibrary_strumClipOffline<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    mono: JFloatArray<'local>,
    rate: jint,
    parameters: JFloatArray<'local>,
) -> jfloatArray {
    let Some((samples, parameters)) = offline_input(&mut env, &mono, rate, &parameters) else {
        return std::ptr::null_mut();
    };
    let shared = StrumShared::default();
    let mut meter = StrumClip::new(strum_of(&parameters));
    meter.configure(rate, &shared);
    for hop in samples.chunks_exact(pipeline::HOP) {
        meter.hop(hop, &shared);
    }
    let reading = shared.snapshot();
    float_array(&mut env, &[reading.verdict as f32, reading.clipped_share as f32, reading.rms_dbfs as f32])
}

/// A per-hop analysis of a stream captured by Java (amendment of 2026-11-03): the offline pipeline fed one hop at a time. Its buffers are made at open,
/// so a hop allocates nothing.
struct HopAnalysis {
    pipeline: Pipeline,
    hop: Vec<f32>,
    record: Vec<f32>,
}

fn hop_analysis<'a>(handle: jlong) -> Option<&'a mut HopAnalysis> {
    if handle == 0 {
        return None;
    }
    // SAFETY: a non-zero handle is a box made by openAnalysis and not yet closed (the Java side closes it once, from the thread that uses it)
    Some(unsafe { &mut *(handle as *mut HopAnalysis) })
}

#[no_mangle]
pub extern "system" fn Java_app_tuxguitar_audio_engine_nativeengine_NativeRecognitionLibrary_openAnalysis<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    rate: jint,
    parameters: JFloatArray<'local>,
) -> jlong {
    let Some(parameters) = floats(&mut env, &parameters).and_then(|p| Parameters::from_slice(&p)) else {
        fail(&mut env, "STREAM_FAILED: the parameters are missing or too short");
        return 0;
    };
    if rate <= 0 {
        fail(&mut env, "FORMAT_UNSUPPORTED: invalid sample rate");
        return 0;
    }
    let analysis = HopAnalysis { pipeline: Pipeline::new(rate, parameters), hop: vec![0f32; pipeline::HOP], record: vec![0f32; crate::analysis::frame::CAPACITY] };
    Box::into_raw(Box::new(analysis)) as jlong
}

#[no_mangle]
pub extern "system" fn Java_app_tuxguitar_audio_engine_nativeengine_NativeRecognitionLibrary_setAnalysisSpectrum<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    on: jboolean,
) {
    if let Some(analysis) = hop_analysis(handle) {
        analysis.pipeline.set_spectrum(on != 0);
    }
}

#[no_mangle]
pub extern "system" fn Java_app_tuxguitar_audio_engine_nativeengine_NativeRecognitionLibrary_setAnalysisAttackCandidates<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    pitches: JIntArray<'local>,
) {
    let pitches = ints(&mut env, &pitches);
    if let Some(analysis) = hop_analysis(handle) {
        analysis.pipeline.set_attack_candidates(&pitches);
    }
}

#[no_mangle]
pub extern "system" fn Java_app_tuxguitar_audio_engine_nativeengine_NativeRecognitionLibrary_setAnalysisExpectedPitches<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    midi: JIntArray<'local>,
) {
    let midi = ints(&mut env, &midi);
    if let Some(analysis) = hop_analysis(handle) {
        analysis.pipeline.set_expected_pitches(&midi);
    }
}

#[no_mangle]
pub extern "system" fn Java_app_tuxguitar_audio_engine_nativeengine_NativeRecognitionLibrary_analyzeHop<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    hop: JFloatArray<'local>,
    record: JFloatArray<'local>,
) -> jint {
    let Some(analysis) = hop_analysis(handle) else {
        return -1;
    };
    if env.get_float_array_region(&hop, 0, &mut analysis.hop).is_err() {
        return -1;
    }
    let capacity = env.get_array_length(&record).unwrap_or(0).max(0) as usize;
    let out = &mut analysis.record[..capacity.min(crate::analysis::frame::CAPACITY)];
    match analysis.pipeline.hop(&analysis.hop, -1, 0, out) {
        HopResult::NotYet => 0,
        HopResult::TooLarge => -1,
        HopResult::Record(length) => {
            if env.set_float_array_region(&record, 0, &analysis.record[..length]).is_err() {
                return -1;
            }
            length as jint
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_app_tuxguitar_audio_engine_nativeengine_NativeRecognitionLibrary_resetAnalysis<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
) {
    if let Some(analysis) = hop_analysis(handle) {
        analysis.pipeline.reset();
    }
}

#[no_mangle]
pub extern "system" fn Java_app_tuxguitar_audio_engine_nativeengine_NativeRecognitionLibrary_closeAnalysis<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
) {
    if handle != 0 {
        // SAFETY: the box made by openAnalysis, closed once
        drop(unsafe { Box::from_raw(handle as *mut HopAnalysis) });
    }
}
