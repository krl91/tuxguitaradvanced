//! The Java side's entry points (spec 022, research R1 addendum): the static native methods of
//! `app.tuxguitar.audio.engine.monitor.NativeMonitorLibrary`. A handle is the address of a boxed `Duplex`; all calls for one handle come
//! from the engine's one thread. Errors become `MonitorDeviceException`s with the reason.

use crate::device::Duplex;
use jni::objects::{JClass, JFloatArray, JString};
use jni::sys::{jfloat, jint, jlong};
use jni::JNIEnv;

const EXCEPTION: &str = "app/tuxguitar/audio/engine/monitor/MonitorDeviceException";

fn fail(env: &mut JNIEnv, message: &str) {
    let _ = env.throw_new(EXCEPTION, message);
}

fn duplex<'a>(handle: jlong) -> Option<&'a mut Duplex> {
    if handle == 0 {
        None
    } else {
        Some(unsafe { &mut *(handle as *mut Duplex) })
    }
}

fn text(env: &mut JNIEnv, value: &JString) -> String {
    if value.is_null() {
        return String::new();
    }
    env.get_string(value).map(|s| s.into()).unwrap_or_default()
}

#[no_mangle]
pub extern "system" fn Java_app_tuxguitar_audio_engine_monitor_NativeMonitorLibrary_open<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    input: JString<'local>,
    channel: jint,
    output: JString<'local>,
    block: jint,
) -> jlong {
    let input = text(&mut env, &input);
    let output = text(&mut env, &output);
    match Duplex::open(&input, channel, &output, block.max(16) as usize) {
        Ok(device) => Box::into_raw(Box::new(device)) as jlong,
        Err(message) => {
            fail(&mut env, &message);
            0
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_app_tuxguitar_audio_engine_monitor_NativeMonitorLibrary_sampleRate<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
) -> jfloat {
    duplex(handle).map(|d| d.sample_rate()).unwrap_or(0.0)
}

#[no_mangle]
pub extern "system" fn Java_app_tuxguitar_audio_engine_monitor_NativeMonitorLibrary_outputRate<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
) -> jfloat {
    duplex(handle).map(|d| d.output_rate()).unwrap_or(0.0)
}

#[no_mangle]
pub extern "system" fn Java_app_tuxguitar_audio_engine_monitor_NativeMonitorLibrary_inputLatency<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
) -> jint {
    duplex(handle).map(|d| d.input_declared() as jint).unwrap_or(0)
}

#[no_mangle]
pub extern "system" fn Java_app_tuxguitar_audio_engine_monitor_NativeMonitorLibrary_outputLatency<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
) -> jint {
    duplex(handle).map(|d| d.output_declared() as jint).unwrap_or(0)
}

#[no_mangle]
pub extern "system" fn Java_app_tuxguitar_audio_engine_monitor_NativeMonitorLibrary_droppedBlocks<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
) -> jlong {
    duplex(handle).map(|d| d.dropped_blocks() as jlong).unwrap_or(0)
}

#[no_mangle]
pub extern "system" fn Java_app_tuxguitar_audio_engine_monitor_NativeMonitorLibrary_read<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    block: JFloatArray<'local>,
    frames: jint,
) -> jint {
    let Some(device) = duplex(handle) else {
        fail(&mut env, "the sound's device is closed");
        return 0;
    };
    let mut buffer = [0f32; 4096];
    let n = (frames.max(0) as usize).min(buffer.len());
    match device.read(&mut buffer[..n]) {
        Ok(read) => {
            if read > 0 && env.set_float_array_region(&block, 0, &buffer[..read]).is_err() {
                return 0;
            }
            read as jint
        }
        Err(message) => {
            fail(&mut env, &message);
            0
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_app_tuxguitar_audio_engine_monitor_NativeMonitorLibrary_write<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    left: JFloatArray<'local>,
    right: JFloatArray<'local>,
    frames: jint,
) {
    let Some(device) = duplex(handle) else {
        fail(&mut env, "the sound's device is closed");
        return;
    };
    let mut l = [0f32; 4096];
    let mut r = [0f32; 4096];
    let n = (frames.max(0) as usize).min(l.len());
    if env.get_float_array_region(&left, 0, &mut l[..n]).is_err() || env.get_float_array_region(&right, 0, &mut r[..n]).is_err() {
        return;
    }
    if let Err(message) = device.write(&l[..n], &r[..n]) {
        fail(&mut env, &message);
    }
}

#[no_mangle]
pub extern "system" fn Java_app_tuxguitar_audio_engine_monitor_NativeMonitorLibrary_close<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
) {
    if handle != 0 {
        drop(unsafe { Box::from_raw(handle as *mut Duplex) });
    }
}
