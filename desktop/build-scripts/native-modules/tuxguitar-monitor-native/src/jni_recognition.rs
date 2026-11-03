//! The Java side's entry points of the native recognition (spec 023, contract §1): the static native methods of
//! `app.tuxguitar.audio.engine.nativeengine.NativeRecognitionLibrary`. Errors become `NativeRecognitionException`s whose message is
//! "REASON: detail". The live entries answer "not implemented" until the live recognition is written (US1, phase 4).

use jni::objects::{JClass, JFloatArray, JIntArray, JString};
use jni::sys::{jboolean, jfloat, jfloatArray, jint, jlong};
use jni::JNIEnv;

use crate::analysis::front_end::{FrontEnd, FrontEndSettings};
use crate::analysis::pipeline::{self, HopResult, Pipeline, Request};
use crate::analysis::strum_clip::{StrumClip, StrumParameters, StrumShared};
use crate::analysis::Parameters;

const EXCEPTION: &str = "app/tuxguitar/audio/engine/nativeengine/NativeRecognitionException";
const NOT_IMPLEMENTED: &str = "STREAM_FAILED: the native recognition is not written yet";

fn fail(env: &mut JNIEnv, message: &str) {
    let _ = env.throw_new(EXCEPTION, message);
}

#[no_mangle]
pub extern "system" fn Java_app_tuxguitar_audio_engine_nativeengine_NativeRecognitionLibrary_open<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    _input: JString<'local>,
    _channel: jint,
    _block: jint,
    _java_nanos_at_open: jlong,
    _parameters: JFloatArray<'local>,
) -> jlong {
    fail(&mut env, NOT_IMPLEMENTED);
    0
}

#[no_mangle]
pub extern "system" fn Java_app_tuxguitar_audio_engine_nativeengine_NativeRecognitionLibrary_sampleRate<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    _handle: jlong,
) -> jfloat {
    0.0
}

#[no_mangle]
pub extern "system" fn Java_app_tuxguitar_audio_engine_nativeengine_NativeRecognitionLibrary_configure<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    _handle: jlong,
    _parameters: JFloatArray<'local>,
) {
}

#[no_mangle]
pub extern "system" fn Java_app_tuxguitar_audio_engine_nativeengine_NativeRecognitionLibrary_setSpectrumEnabled<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    _handle: jlong,
    _on: jboolean,
) {
}

#[no_mangle]
pub extern "system" fn Java_app_tuxguitar_audio_engine_nativeengine_NativeRecognitionLibrary_setExpectedPitches<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    _handle: jlong,
    _midi: JIntArray<'local>,
) {
}

#[no_mangle]
pub extern "system" fn Java_app_tuxguitar_audio_engine_nativeengine_NativeRecognitionLibrary_setExpectedChord<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    _handle: jlong,
    _required: JIntArray<'local>,
    _tolerated: JIntArray<'local>,
) {
}

#[no_mangle]
pub extern "system" fn Java_app_tuxguitar_audio_engine_nativeengine_NativeRecognitionLibrary_setAttackCandidates<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    _handle: jlong,
    _pitches: JIntArray<'local>,
) {
}

#[no_mangle]
pub extern "system" fn Java_app_tuxguitar_audio_engine_nativeengine_NativeRecognitionLibrary_nextFrames<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    _handle: jlong,
    _records: JFloatArray<'local>,
    _timeout_millis: jint,
) -> jint {
    -1
}

#[no_mangle]
pub extern "system" fn Java_app_tuxguitar_audio_engine_nativeengine_NativeRecognitionLibrary_nextHops<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    _handle: jlong,
    _hops: JFloatArray<'local>,
    _timeout_millis: jint,
) -> jint {
    -1
}

#[no_mangle]
pub extern "system" fn Java_app_tuxguitar_audio_engine_nativeengine_NativeRecognitionLibrary_setHopsEnabled<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    _handle: jlong,
    _on: jboolean,
) {
}

#[no_mangle]
pub extern "system" fn Java_app_tuxguitar_audio_engine_nativeengine_NativeRecognitionLibrary_droppedBlocks<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    _handle: jlong,
) -> jlong {
    0
}

#[no_mangle]
pub extern "system" fn Java_app_tuxguitar_audio_engine_nativeengine_NativeRecognitionLibrary_close<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    _handle: jlong,
) {
}

#[no_mangle]
pub extern "system" fn Java_app_tuxguitar_audio_engine_nativeengine_NativeRecognitionLibrary_strumClip<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    _handle: jlong,
) -> jfloatArray {
    fail(&mut env, NOT_IMPLEMENTED);
    std::ptr::null_mut()
}

#[no_mangle]
pub extern "system" fn Java_app_tuxguitar_audio_engine_nativeengine_NativeRecognitionLibrary_resetStrumClip<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    _handle: jlong,
) {
}

#[no_mangle]
pub extern "system" fn Java_app_tuxguitar_audio_engine_nativeengine_NativeRecognitionLibrary_stats<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    _handle: jlong,
) -> jfloatArray {
    fail(&mut env, NOT_IMPLEMENTED);
    std::ptr::null_mut()
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
    front.set(FrontEndSettings { gain: parameters.front_gain, noise_reduction: parameters.front_noise_reduction, subtraction: parameters.front_subtraction });
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
    let mut meter = StrumClip::new(strum_parameters(&parameters));
    meter.configure(rate, &shared);
    for hop in samples.chunks_exact(pipeline::HOP) {
        meter.hop(hop, &shared);
    }
    let reading = shared.snapshot();
    float_array(&mut env, &[reading.verdict as f32, reading.clipped_share as f32, reading.rms_dbfs as f32])
}

fn strum_parameters(p: &Parameters) -> StrumParameters {
    StrumParameters { arm_floor_dbfs: p.strum_arm_floor_dbfs, clip_sample: p.strum_clip_sample, weak_dbfs: p.strum_weak_dbfs, loud_dbfs: p.strum_loud_dbfs }
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
