//! The Java side's entry points of the native recognition (spec 023, contract §1): the static native methods of
//! `app.tuxguitar.audio.engine.nativeengine.NativeRecognitionLibrary`. Errors become `NativeRecognitionException`s whose message is
//! "REASON: detail". Until the recognition is written (US1), every entry answers "not implemented".

use jni::objects::{JClass, JFloatArray, JIntArray, JString};
use jni::sys::{jboolean, jfloat, jfloatArray, jint, jlong};
use jni::JNIEnv;

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
pub extern "system" fn Java_app_tuxguitar_audio_engine_nativeengine_NativeRecognitionLibrary_analyze<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    _mono: JFloatArray<'local>,
    _rate: jint,
    _parameters: JFloatArray<'local>,
    _spectrum: jboolean,
    _attack_candidates: JIntArray<'local>,
    _chord_required: JIntArray<'local>,
    _chord_tolerated: JIntArray<'local>,
) -> jfloatArray {
    fail(&mut env, NOT_IMPLEMENTED);
    std::ptr::null_mut()
}
