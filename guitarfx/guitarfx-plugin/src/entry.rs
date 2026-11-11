//! The CLAP entry: `nih_export_clap!`'s, written out for the one plugin (nih-plug commit of research R3), with one more factory,
//! clap-wrapper's `clap.plugin-factory-info-as-vst3/0`, that gives its VST3 entry the fixed class id of contracts/plugin.md. Without it
//! clap-wrapper would derive an id from the CLAP id. The Audio Unit codes are read from the bundle's `Info.plist` (T056).
//!
//! The plugin the host gets is nih-plug's with two extensions guarded (clap-validator, guitarfx.yml's third run); VST3 and Audio Unit
//! come through this entry too:
//! - the state: nih-plug reserves the length a stream announces before reading it, so a damaged project would abort the host. The
//!   whole state is read here first, refused when its length is beyond any GuitarFX state or the stream ends before it.
//! - the audio ports: nih-plug pairs the main input and output in place even when their channel counts differ (the mono input with the
//!   stereo output), which CLAP forbids; such a pair is removed.

use crate::{ClassId, GuitarFx, VENDOR, VST3_CLASS_ID};
use clap_sys::ext::audio_ports::{clap_audio_port_info, clap_plugin_audio_ports, CLAP_EXT_AUDIO_PORTS};
use clap_sys::ext::state::{clap_plugin_state, CLAP_EXT_STATE};
use clap_sys::id::CLAP_INVALID_ID;
use clap_sys::stream::{clap_istream, clap_ostream};
use nih_plug::wrapper::clap::{
    clap_host, clap_plugin, clap_plugin_descriptor, clap_plugin_entry, clap_plugin_factory, PluginDescriptor, Wrapper, CLAP_PLUGIN_FACTORY_ID,
    CLAP_VERSION,
};
use nih_plug::wrapper::setup_logger;
use std::ffi::{c_char, c_void, CStr};
use std::sync::{Arc, OnceLock};

/// Far beyond any GuitarFX state (a chain text of a few hundred bytes in nih-plug's JSON); a longer one is not ours.
const STATE_LIMIT: u64 = 1 << 20;

/// nih-plug's plugin as the host sees it: the same functions and data, but `get_extension` and `destroy`. The host's pointer is this
/// struct's; nih-plug's functions only read `plugin_data`, which is its own.
#[repr(C)]
struct Guarded {
    plugin: clap_plugin,
    inner: *const clap_plugin,
}

/// The inner plugin of the host's pointer.
unsafe fn inner(plugin: *const clap_plugin) -> *const clap_plugin {
    // SAFETY: the host's pointer is the first field of a `Guarded` made by `create_plugin`
    unsafe { (*(plugin as *const Guarded)).inner }
}

/// One of nih-plug's extensions.
unsafe fn inner_extension<T>(inner: *const clap_plugin, id: &CStr) -> *const T {
    // SAFETY: nih-plug's plugin, alive while the host's is
    unsafe { ((*inner).get_extension.unwrap())(inner, id.as_ptr()) as *const T }
}

static STATE: clap_plugin_state = clap_plugin_state {
    save: Some(state_save),
    load: Some(state_load),
};

static PORTS: clap_plugin_audio_ports = clap_plugin_audio_ports {
    count: Some(ports_count),
    get: Some(ports_get),
};

unsafe extern "C" fn get_extension(plugin: *const clap_plugin, id: *const c_char) -> *const c_void {
    let inner = unsafe { inner(plugin) };
    if id.is_null() {
        return std::ptr::null();
    }
    // SAFETY: the host passes a valid C string
    let wanted = unsafe { CStr::from_ptr(id) };
    let theirs = unsafe { ((*inner).get_extension.unwrap())(inner, id) };
    if theirs.is_null() {
        theirs
    } else if wanted == CLAP_EXT_STATE {
        &STATE as *const _ as *const c_void
    } else if wanted == CLAP_EXT_AUDIO_PORTS {
        &PORTS as *const _ as *const c_void
    } else {
        theirs
    }
}

unsafe extern "C" fn destroy(plugin: *const clap_plugin) {
    // SAFETY: made by `create_plugin` with `Box::into_raw`; the host destroys it once
    let guarded = unsafe { Box::from_raw(plugin as *mut Guarded) };
    unsafe { ((*guarded.inner).destroy.unwrap())(guarded.inner) };
}

unsafe extern "C" fn state_save(plugin: *const clap_plugin, stream: *const clap_ostream) -> bool {
    let inner = unsafe { inner(plugin) };
    let state = unsafe { inner_extension::<clap_plugin_state>(inner, CLAP_EXT_STATE) };
    unsafe { ((*state).save.unwrap())(inner, stream) }
}

/// Fills `buffer` from the stream, which may give it in pieces; false when the stream fails or ends first.
unsafe fn read_exactly(stream: *const clap_istream, buffer: &mut [u8]) -> bool {
    let mut at = 0;
    while at < buffer.len() {
        // SAFETY: the host's stream; the rest of the buffer is ours
        let read = unsafe { ((*stream).read.unwrap())(stream, buffer[at..].as_mut_ptr() as *mut c_void, (buffer.len() - at) as u64) };
        if read <= 0 {
            return false;
        }
        at += read as usize;
    }
    true
}

/// The state read whole, given to nih-plug from memory.
struct Memory {
    stream: clap_istream,
    bytes: Vec<u8>,
    at: std::cell::Cell<usize>,
}

unsafe extern "C" fn memory_read(stream: *const clap_istream, buffer: *mut c_void, size: u64) -> i64 {
    // SAFETY: `ctx` is the `Memory` that holds this stream
    let memory = unsafe { &*((*stream).ctx as *const Memory) };
    let at = memory.at.get();
    let n = (size as usize).min(memory.bytes.len() - at);
    unsafe { std::ptr::copy_nonoverlapping(memory.bytes[at..].as_ptr(), buffer as *mut u8, n) };
    memory.at.set(at + n);
    n as i64
}

unsafe extern "C" fn state_load(plugin: *const clap_plugin, stream: *const clap_istream) -> bool {
    if stream.is_null() {
        return false;
    }
    let inner = unsafe { inner(plugin) };
    let mut length = [0u8; std::mem::size_of::<u64>()];
    if !unsafe { read_exactly(stream, &mut length) } {
        return false;
    }
    let announced = u64::from_le_bytes(length);
    if announced > STATE_LIMIT {
        return false;
    }
    let mut bytes = length.to_vec();
    bytes.resize(length.len() + announced as usize, 0);
    if !unsafe { read_exactly(stream, &mut bytes[length.len()..]) } {
        return false;
    }
    let mut memory = Box::new(Memory {
        stream: clap_istream {
            ctx: std::ptr::null_mut(),
            read: Some(memory_read),
        },
        bytes,
        at: std::cell::Cell::new(0),
    });
    memory.stream.ctx = &*memory as *const Memory as *mut c_void;
    let state = unsafe { inner_extension::<clap_plugin_state>(inner, CLAP_EXT_STATE) };
    unsafe { ((*state).load.unwrap())(inner, &memory.stream) }
}

unsafe extern "C" fn ports_count(plugin: *const clap_plugin, is_input: bool) -> u32 {
    let inner = unsafe { inner(plugin) };
    let ports = unsafe { inner_extension::<clap_plugin_audio_ports>(inner, CLAP_EXT_AUDIO_PORTS) };
    unsafe { ((*ports).count.unwrap())(inner, is_input) }
}

unsafe extern "C" fn ports_get(plugin: *const clap_plugin, index: u32, is_input: bool, info: *mut clap_audio_port_info) -> bool {
    let inner = unsafe { inner(plugin) };
    let ports = unsafe { inner_extension::<clap_plugin_audio_ports>(inner, CLAP_EXT_AUDIO_PORTS) };
    if !unsafe { ((*ports).get.unwrap())(inner, index, is_input, info) } {
        return false;
    }
    // SAFETY: filled by nih-plug just now
    let info = unsafe { &mut *info };
    if info.in_place_pair != CLAP_INVALID_ID {
        // the pair is the other side's main port, the first one
        let mut other: clap_audio_port_info = unsafe { std::mem::zeroed() };
        if !unsafe { ((*ports).get.unwrap())(inner, 0, !is_input, &mut other) } || other.channel_count != info.channel_count {
            info.in_place_pair = CLAP_INVALID_ID;
        }
    }
    true
}

/// `clap-wrapper/include/clapwrapper/vst3.h`.
const FACTORY_INFO_VST3: &CStr = c"clap.plugin-factory-info-as-vst3/0";

#[repr(C)]
struct PluginInfoAsVst3 {
    vendor: *const c_char,
    component_id: *const ClassId,
    features: *const c_char,
}

#[repr(C)]
struct PluginFactoryAsVst3 {
    vendor: *const c_char,
    vendor_url: *const c_char,
    email_contact: *const c_char,
    get_vst3_info: Option<unsafe extern "C" fn(factory: *const PluginFactoryAsVst3, index: u32) -> *const PluginInfoAsVst3>,
}

// SAFETY: both hold pointers to 'static data only
unsafe impl Sync for PluginInfoAsVst3 {}
unsafe impl Sync for PluginFactoryAsVst3 {}

const VENDOR_C: &CStr = c"TuxGuitarAdvanced";
const _: () = assert!(VENDOR_C.to_bytes().len() == VENDOR.len());

static VST3_INFO: PluginInfoAsVst3 = PluginInfoAsVst3 {
    vendor: VENDOR_C.as_ptr(),
    component_id: &VST3_CLASS_ID,
    // derived from the CLAP features
    features: std::ptr::null(),
};

static VST3_FACTORY: PluginFactoryAsVst3 = PluginFactoryAsVst3 {
    vendor: VENDOR_C.as_ptr(),
    vendor_url: std::ptr::null(),
    email_contact: std::ptr::null(),
    get_vst3_info: Some(get_vst3_info),
};

unsafe extern "C" fn get_vst3_info(_factory: *const PluginFactoryAsVst3, index: u32) -> *const PluginInfoAsVst3 {
    if index == 0 {
        &VST3_INFO
    } else {
        std::ptr::null()
    }
}

const PLUGIN_FACTORY: clap_plugin_factory = clap_plugin_factory {
    get_plugin_count: Some(get_plugin_count),
    get_plugin_descriptor: Some(get_plugin_descriptor),
    create_plugin: Some(create_plugin),
};

fn descriptor() -> &'static PluginDescriptor {
    static DESCRIPTOR: OnceLock<PluginDescriptor> = OnceLock::new();
    DESCRIPTOR.get_or_init(PluginDescriptor::for_plugin::<GuitarFx>)
}

unsafe extern "C" fn get_plugin_count(_factory: *const clap_plugin_factory) -> u32 {
    1
}

unsafe extern "C" fn get_plugin_descriptor(_factory: *const clap_plugin_factory, index: u32) -> *const clap_plugin_descriptor {
    if index == 0 {
        descriptor().clap_plugin_descriptor()
    } else {
        std::ptr::null()
    }
}

unsafe extern "C" fn create_plugin(_factory: *const clap_plugin_factory, host: *const clap_host, plugin_id: *const c_char) -> *const clap_plugin {
    // SAFETY: the host passes a valid C string, or null
    if plugin_id.is_null() || unsafe { CStr::from_ptr(plugin_id) } != descriptor().clap_id() {
        return std::ptr::null();
    }
    // SAFETY: as nih_export_clap!: the wrapper is kept alive by the host until it destroys the plugin
    let inner = unsafe { (*Arc::into_raw(Wrapper::<GuitarFx>::new(host))).clap_plugin.as_ptr() };
    // SAFETY: nih-plug's plugin, just made; its functions and data are copied, two of them replaced
    let mut plugin = unsafe { std::ptr::read(inner) };
    plugin.get_extension = Some(get_extension);
    plugin.destroy = Some(destroy);
    Box::into_raw(Box::new(Guarded { plugin, inner })) as *const clap_plugin
}

extern "C" fn init(_plugin_path: *const c_char) -> bool {
    setup_logger();
    true
}

extern "C" fn deinit() {}

extern "C" fn get_factory(factory_id: *const c_char) -> *const c_void {
    if factory_id.is_null() {
        return std::ptr::null();
    }
    // SAFETY: the host passes a valid C string
    let id = unsafe { CStr::from_ptr(factory_id) };
    if id == CLAP_PLUGIN_FACTORY_ID {
        &PLUGIN_FACTORY as *const _ as *const c_void
    } else if id == FACTORY_INFO_VST3 {
        &VST3_FACTORY as *const _ as *const c_void
    } else {
        std::ptr::null()
    }
}

#[unsafe(no_mangle)]
#[used]
pub static clap_entry: clap_plugin_entry = clap_plugin_entry {
    clap_version: CLAP_VERSION,
    init: Some(init),
    deinit: Some(deinit),
    get_factory: Some(get_factory),
};
