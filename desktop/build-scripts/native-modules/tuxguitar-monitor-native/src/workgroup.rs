//! The audio workgroup of an input device (spec 023, T057, research R9): on macOS, CoreAudio's I/O thread works in an `os_workgroup`, and
//! a thread that works for it joins it so the system gives it the cores and the frequency the device's deadline needs. Without it, a thread
//! that works a fifth of a millisecond every few milliseconds on an idle machine is left on an efficiency core at its lowest frequency, six
//! times slower. Elsewhere there is no workgroup and nothing is joined.

/// A device's workgroup, released when dropped.
pub struct Workgroup {
    #[cfg(target_os = "macos")]
    group: *mut std::ffi::c_void,
}

// an os_workgroup may be joined from any thread
unsafe impl Send for Workgroup {}

/// The membership of the calling thread, left when dropped (on the thread that joined).
pub struct Membership<'a> {
    #[cfg(target_os = "macos")]
    group: &'a Workgroup,
    #[cfg(target_os = "macos")]
    token: JoinToken,
    #[cfg(not(target_os = "macos"))]
    _group: std::marker::PhantomData<&'a Workgroup>,
}

/// `os_workgroup_join_token_s`: a signature and 36 opaque bytes.
#[cfg(target_os = "macos")]
#[repr(C)]
struct JoinToken {
    sig: u32,
    opaque: [u8; 36],
}

#[cfg(target_os = "macos")]
extern "C" {
    fn os_workgroup_join(group: *mut std::ffi::c_void, token: *mut JoinToken) -> i32;
    fn os_workgroup_leave(group: *mut std::ffi::c_void, token: *mut JoinToken);
    fn os_release(object: *mut std::ffi::c_void);
}

/// `kAudioDevicePropertyIOThreadOSWorkgroup` ('oswg'), macOS 11 and later.
#[cfg(target_os = "macos")]
const IO_THREAD_OS_WORKGROUP: u32 = u32::from_be_bytes(*b"oswg");

impl Workgroup {
    /// The workgroup of the device of that name, if the system gives one.
    #[cfg(target_os = "macos")]
    pub fn of_device(device_name: &str) -> Option<Workgroup> {
        use coreaudio_sys::*;
        use std::ptr::null;
        let device = device_named(device_name)?;
        let address = AudioObjectPropertyAddress { mSelector: IO_THREAD_OS_WORKGROUP, mScope: kAudioObjectPropertyScopeGlobal, mElement: kAudioObjectPropertyElementMain };
        let mut group: *mut std::ffi::c_void = std::ptr::null_mut();
        let mut size = std::mem::size_of::<*mut std::ffi::c_void>() as u32;
        let status = unsafe { AudioObjectGetPropertyData(device, &address, 0, null(), &mut size, &mut group as *mut *mut std::ffi::c_void as *mut _) };
        if status != 0 || group.is_null() {
            return None;
        }
        Some(Workgroup { group })
    }

    #[cfg(not(target_os = "macos"))]
    pub fn of_device(_device_name: &str) -> Option<Workgroup> {
        None
    }

    /// Joins the workgroup from the calling thread (a real-time thread, as the system asks), until the membership is dropped.
    #[cfg(target_os = "macos")]
    pub fn join(&self) -> Result<Membership<'_>, String> {
        let mut token = JoinToken { sig: 0, opaque: [0; 36] };
        let result = unsafe { os_workgroup_join(self.group, &mut token) };
        if result == 0 {
            Ok(Membership { group: self, token })
        } else {
            Err(format!("os_workgroup_join refused ({})", result))
        }
    }

    #[cfg(not(target_os = "macos"))]
    pub fn join(&self) -> Result<Membership<'_>, String> {
        Err("no workgroup on this system".to_string())
    }
}

#[cfg(target_os = "macos")]
impl Drop for Workgroup {
    fn drop(&mut self) {
        unsafe { os_release(self.group) };
    }
}

#[cfg(target_os = "macos")]
impl Drop for Membership<'_> {
    fn drop(&mut self) {
        unsafe { os_workgroup_leave(self.group.group, &mut self.token) };
    }
}

/// The CoreAudio object of the device of that name.
#[cfg(target_os = "macos")]
fn device_named(device_name: &str) -> Option<coreaudio_sys::AudioObjectID> {
    use coreaudio_sys::*;
    use std::ffi::CStr;
    use std::os::raw::c_char;
    use std::ptr::null;
    unsafe {
        let address = AudioObjectPropertyAddress { mSelector: kAudioHardwarePropertyDevices, mScope: kAudioObjectPropertyScopeGlobal, mElement: kAudioObjectPropertyElementMain };
        let mut size: u32 = 0;
        if AudioObjectGetPropertyDataSize(kAudioObjectSystemObject, &address, 0, null(), &mut size) != 0 {
            return None;
        }
        let mut devices = vec![0 as AudioObjectID; size as usize / std::mem::size_of::<AudioObjectID>()];
        if AudioObjectGetPropertyData(kAudioObjectSystemObject, &address, 0, null(), &mut size, devices.as_mut_ptr() as *mut _) != 0 {
            return None;
        }
        for device in devices {
            let name_address = AudioObjectPropertyAddress { mSelector: kAudioObjectPropertyName, mScope: kAudioObjectPropertyScopeGlobal, mElement: kAudioObjectPropertyElementMain };
            let mut name: CFStringRef = std::ptr::null();
            let mut name_size = std::mem::size_of::<CFStringRef>() as u32;
            if AudioObjectGetPropertyData(device, &name_address, 0, null(), &mut name_size, &mut name as *mut CFStringRef as *mut _) != 0 || name.is_null() {
                continue;
            }
            let mut buffer = [0 as c_char; 256];
            let ok = CFStringGetCString(name, buffer.as_mut_ptr(), buffer.len() as _, kCFStringEncodingUTF8) != 0;
            CFRelease(name as *const _);
            if ok && CStr::from_ptr(buffer.as_ptr()).to_string_lossy() == device_name {
                return Some(device);
            }
        }
        None
    }
}

#[cfg(all(test, target_os = "macos"))]
mod tests {
    use super::*;

    #[test]
    fn an_unknown_device_has_no_workgroup() {
        assert!(Workgroup::of_device("no device has this name").is_none());
    }

    #[test]
    #[ignore = "needs an input device: run with --ignored"]
    fn a_real_time_thread_joins_the_default_input_s_workgroup() {
        let host = cpal::default_host();
        let name = cpal::traits::HostTrait::default_input_device(&host).and_then(|d| cpal::traits::DeviceTrait::name(&d).ok()).expect("default input");
        let group = Workgroup::of_device(&name).expect("the default input gives a workgroup");
        std::thread::spawn(move || {
            crate::realtime::promote_current_thread(256, 48000.0).expect("promoted");
            let membership = group.join();
            assert!(membership.is_ok(), "{:?}", membership.err());
        })
        .join()
        .unwrap();
    }
}
