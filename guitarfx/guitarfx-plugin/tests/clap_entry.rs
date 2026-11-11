//! The plugin through its CLAP entry, as a host loads it (clap-validator's `state-invalid-random` and `layout-audio-ports-config`,
//! guitarfx.yml's third run): a damaged project state is refused, never read past its length, and an input and an output of different
//! channel counts are never given as an in-place pair.

use clap_sys::ext::audio_ports::{clap_audio_port_info, clap_plugin_audio_ports, CLAP_EXT_AUDIO_PORTS};
use clap_sys::ext::audio_ports_config::{clap_audio_ports_config, clap_plugin_audio_ports_config, CLAP_EXT_AUDIO_PORTS_CONFIG};
use clap_sys::ext::state::{clap_plugin_state, CLAP_EXT_STATE};
use clap_sys::factory::plugin_factory::{clap_plugin_factory, CLAP_PLUGIN_FACTORY_ID};
use clap_sys::host::clap_host;
use clap_sys::id::CLAP_INVALID_ID;
use clap_sys::plugin::clap_plugin;
use clap_sys::stream::{clap_istream, clap_ostream};
use clap_sys::version::CLAP_VERSION;
use guitarfx_plugin::entry::clap_entry;
use std::cell::RefCell;
use std::ffi::c_void;

unsafe extern "C" fn no_extension(_host: *const clap_host, _id: *const std::ffi::c_char) -> *const c_void {
    std::ptr::null()
}

unsafe extern "C" fn nothing(_host: *const clap_host) {}

static HOST: clap_host = clap_host {
    clap_version: CLAP_VERSION,
    host_data: std::ptr::null_mut(),
    name: c"test".as_ptr(),
    vendor: c"test".as_ptr(),
    url: c"".as_ptr(),
    version: c"1".as_ptr(),
    get_extension: Some(no_extension),
    request_restart: Some(nothing),
    request_process: Some(nothing),
    request_callback: Some(nothing),
};

/// A new instance, initialised, as a host makes it.
fn plugin() -> *const clap_plugin {
    unsafe {
        assert!((clap_entry.init.unwrap())(c"".as_ptr()));
        let factory = (clap_entry.get_factory.unwrap())(CLAP_PLUGIN_FACTORY_ID.as_ptr()) as *const clap_plugin_factory;
        let descriptor = ((*factory).get_plugin_descriptor.unwrap())(factory, 0);
        let plugin = ((*factory).create_plugin.unwrap())(factory, &HOST, (*descriptor).id);
        assert!(!plugin.is_null());
        assert!(((*plugin).init.unwrap())(plugin));
        plugin
    }
}

fn extension<T>(plugin: *const clap_plugin, id: &std::ffi::CStr) -> &'static T {
    unsafe {
        let ext = ((*plugin).get_extension.unwrap())(plugin, id.as_ptr()) as *const T;
        assert!(!ext.is_null(), "{id:?}");
        &*ext
    }
}

fn destroy(plugin: *const clap_plugin) {
    unsafe { ((*plugin).destroy.unwrap())(plugin) }
}

/// An input stream over bytes, read in pieces of at most `piece` bytes as a host may give them.
struct Input {
    stream: clap_istream,
    bytes: Vec<u8>,
    at: RefCell<usize>,
    piece: usize,
}

unsafe extern "C" fn read(stream: *const clap_istream, buffer: *mut c_void, size: u64) -> i64 {
    let input = unsafe { &*((*stream).ctx as *const Input) };
    let mut at = input.at.borrow_mut();
    let n = (size as usize).min(input.piece).min(input.bytes.len() - *at);
    unsafe { std::ptr::copy_nonoverlapping(input.bytes[*at..].as_ptr(), buffer as *mut u8, n) };
    *at += n;
    n as i64
}

fn input(bytes: Vec<u8>, piece: usize) -> Box<Input> {
    let mut input = Box::new(Input {
        stream: clap_istream {
            ctx: std::ptr::null_mut(),
            read: Some(read),
        },
        bytes,
        at: RefCell::new(0),
        piece,
    });
    input.stream.ctx = &*input as *const Input as *mut c_void;
    input
}

struct Output {
    stream: clap_ostream,
    bytes: RefCell<Vec<u8>>,
}

unsafe extern "C" fn write(stream: *const clap_ostream, buffer: *const c_void, size: u64) -> i64 {
    let output = unsafe { &*((*stream).ctx as *const Output) };
    output
        .bytes
        .borrow_mut()
        .extend_from_slice(unsafe { std::slice::from_raw_parts(buffer as *const u8, size as usize) });
    size as i64
}

#[test]
fn a_damaged_state_is_refused_without_reading_past_it() {
    let plugin = plugin();
    let state: &clap_plugin_state = extension(plugin, CLAP_EXT_STATE);
    // clap-validator's case: a length of about 10^18 bytes, then a few random bytes
    let mut bytes = 1_025_222_176_999_353_387u64.to_le_bytes().to_vec();
    bytes.extend_from_slice(&[7, 1, 200, 13, 99]);
    let damaged = input(bytes, 3);
    assert!(!unsafe { (state.load.unwrap())(plugin, &damaged.stream) });
    // a length the stream does not hold
    let mut bytes = 64u64.to_le_bytes().to_vec();
    bytes.extend_from_slice(b"{\"short\"");
    let short = input(bytes, 5);
    assert!(!unsafe { (state.load.unwrap())(plugin, &short.stream) });
    destroy(plugin);
}

#[test]
fn a_saved_state_loads_back_even_in_small_pieces() {
    let plugin = plugin();
    let state: &clap_plugin_state = extension(plugin, CLAP_EXT_STATE);
    let mut output = Box::new(Output {
        stream: clap_ostream {
            ctx: std::ptr::null_mut(),
            write: Some(write),
        },
        bytes: RefCell::new(Vec::new()),
    });
    output.stream.ctx = &*output as *const Output as *mut c_void;
    assert!(unsafe { (state.save.unwrap())(plugin, &output.stream) });
    let saved = output.bytes.borrow().clone();
    assert!(saved.len() > 8);
    let again = input(saved, 3);
    assert!(unsafe { (state.load.unwrap())(plugin, &again.stream) });
    destroy(plugin);
}

#[test]
fn an_input_and_an_output_of_different_channel_counts_are_not_an_in_place_pair() {
    let plugin = plugin();
    let configs: &clap_plugin_audio_ports_config = extension(plugin, CLAP_EXT_AUDIO_PORTS_CONFIG);
    let ports: &clap_plugin_audio_ports = extension(plugin, CLAP_EXT_AUDIO_PORTS);
    let count = unsafe { (configs.count.unwrap())(plugin) };
    assert!(count >= 2, "stereo and mono inputs");
    for index in 0..count {
        let mut config: clap_audio_ports_config = unsafe { std::mem::zeroed() };
        assert!(unsafe { (configs.get.unwrap())(plugin, index, &mut config) });
        assert!(unsafe { (configs.select.unwrap())(plugin, config.id) });
        let mut port = [unsafe { std::mem::zeroed::<clap_audio_port_info>() }, unsafe { std::mem::zeroed() }];
        for (side, is_input) in [true, false].into_iter().enumerate() {
            assert!(unsafe { (ports.get.unwrap())(plugin, 0, is_input, &mut port[side]) });
        }
        let (input, output) = (&port[0], &port[1]);
        if input.channel_count == output.channel_count {
            assert_eq!(input.in_place_pair, output.id, "config {index}: equal ports may be paired");
            assert_eq!(output.in_place_pair, input.id, "config {index}");
        } else {
            assert_eq!(
                input.in_place_pair, CLAP_INVALID_ID,
                "config {index}: {} in, {} out",
                input.channel_count, output.channel_count
            );
            assert_eq!(output.in_place_pair, CLAP_INVALID_ID, "config {index}");
        }
    }
    destroy(plugin);
}
