//! The latencies a device declares, in frames, where the system tells them (spec 022, research R8): on macOS, CoreAudio's device latency,
//! stream latency and safety offset (what the delay probes measured, research R1). Elsewhere nothing is known and 0 is returned: the
//! estimate then counts the buffers only, and says it is an estimate.

#[cfg(target_os = "macos")]
pub fn declared(device_name: &str, input: bool) -> u32 {
    use coreaudio_sys::*;
    use std::ffi::CStr;
    use std::os::raw::c_char;
    use std::ptr::null;

    unsafe fn property_u32(object: AudioObjectID, selector: u32, scope: u32) -> u32 {
        let address = AudioObjectPropertyAddress { mSelector: selector, mScope: scope, mElement: kAudioObjectPropertyElementMain };
        let mut value: u32 = 0;
        let mut size = std::mem::size_of::<u32>() as u32;
        if AudioObjectGetPropertyData(object, &address, 0, null(), &mut size, &mut value as *mut u32 as *mut _) != 0 {
            return 0;
        }
        value
    }

    unsafe {
        let scope = if input { kAudioObjectPropertyScopeInput } else { kAudioObjectPropertyScopeOutput };
        let address = AudioObjectPropertyAddress {
            mSelector: kAudioHardwarePropertyDevices,
            mScope: kAudioObjectPropertyScopeGlobal,
            mElement: kAudioObjectPropertyElementMain,
        };
        let mut size: u32 = 0;
        if AudioObjectGetPropertyDataSize(kAudioObjectSystemObject, &address, 0, null(), &mut size) != 0 {
            return 0;
        }
        let mut devices = vec![0 as AudioObjectID; size as usize / std::mem::size_of::<AudioObjectID>()];
        if AudioObjectGetPropertyData(kAudioObjectSystemObject, &address, 0, null(), &mut size, devices.as_mut_ptr() as *mut _) != 0 {
            return 0;
        }
        for device in devices {
            let name_address = AudioObjectPropertyAddress {
                mSelector: kAudioObjectPropertyName,
                mScope: kAudioObjectPropertyScopeGlobal,
                mElement: kAudioObjectPropertyElementMain,
            };
            let mut name: CFStringRef = std::ptr::null();
            let mut name_size = std::mem::size_of::<CFStringRef>() as u32;
            if AudioObjectGetPropertyData(device, &name_address, 0, null(), &mut name_size, &mut name as *mut CFStringRef as *mut _) != 0
                || name.is_null()
            {
                continue;
            }
            let mut buffer = [0 as c_char; 256];
            let ok = CFStringGetCString(name, buffer.as_mut_ptr(), buffer.len() as _, kCFStringEncodingUTF8) != 0;
            CFRelease(name as *const _);
            if !ok || CStr::from_ptr(buffer.as_ptr()).to_string_lossy() != device_name {
                continue;
            }
            let mut stream_latency = 0;
            let streams_address = AudioObjectPropertyAddress { mSelector: kAudioDevicePropertyStreams, mScope: scope, mElement: kAudioObjectPropertyElementMain };
            let mut streams_size: u32 = 0;
            if AudioObjectGetPropertyDataSize(device, &streams_address, 0, null(), &mut streams_size) == 0 && streams_size > 0 {
                let mut streams = vec![0 as AudioObjectID; streams_size as usize / std::mem::size_of::<AudioObjectID>()];
                if AudioObjectGetPropertyData(device, &streams_address, 0, null(), &mut streams_size, streams.as_mut_ptr() as *mut _) == 0 {
                    stream_latency = property_u32(streams[0], kAudioStreamPropertyLatency, kAudioObjectPropertyScopeGlobal);
                }
            }
            return property_u32(device, kAudioDevicePropertyLatency, scope) + stream_latency + property_u32(device, kAudioDevicePropertySafetyOffset, scope);
        }
        0
    }
}

#[cfg(not(target_os = "macos"))]
pub fn declared(_device_name: &str, _input: bool) -> u32 {
    0
}
