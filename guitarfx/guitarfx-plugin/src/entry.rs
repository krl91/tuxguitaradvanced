//! The CLAP entry: `nih_export_clap!`'s, written out for the one plugin (nih-plug commit of research R3), with one more factory,
//! clap-wrapper's `clap.plugin-factory-info-as-vst3/0`, that gives its VST3 entry the fixed class id of contracts/plugin.md. Without it
//! clap-wrapper would derive an id from the CLAP id. The Audio Unit codes are read from the bundle's `Info.plist` (T056).

use crate::{ClassId, GuitarFx, VENDOR, VST3_CLASS_ID};
use nih_plug::wrapper::clap::{
    clap_host, clap_plugin, clap_plugin_descriptor, clap_plugin_entry, clap_plugin_factory, PluginDescriptor, Wrapper, CLAP_PLUGIN_FACTORY_ID,
    CLAP_VERSION,
};
use nih_plug::wrapper::setup_logger;
use std::ffi::{c_char, c_void, CStr};
use std::sync::{Arc, OnceLock};

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
    unsafe { (*Arc::into_raw(Wrapper::<GuitarFx>::new(host))).clap_plugin.as_ptr() }
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
