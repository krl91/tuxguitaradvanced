//! The real-time audio thread (spec 022, research R3 addendum): the thread that runs the chain asks the system for the scheduling an
//! audio thread gets, so that it is not woken up blocks late. macOS: the Mach time-constraint policy; Windows: MMCSS "Pro Audio";
//! Linux: `SCHED_FIFO` when the user may. A refusal is not an error: the thread keeps its scheduling and the reason is given back.

/// Promotes the calling thread for blocks of `block_frames` at `sample_rate`. `Ok` names the policy taken, `Err` says why none was.
pub fn promote_current_thread(block_frames: usize, sample_rate: f32) -> Result<String, String> {
    if block_frames == 0 || !(sample_rate > 0.0) {
        return Err(format!("no block ({} frames at {} Hz)", block_frames, sample_rate));
    }
    promote(block_frames as f64 / sample_rate as f64 * 1e9)
}

/// Mach absolute time units for that many nanoseconds.
#[cfg(target_os = "macos")]
fn nanos_to_ticks(nanos: f64) -> u32 {
    let mut info = mach2::mach_time::mach_timebase_info { numer: 0, denom: 0 };
    unsafe { mach2::mach_time::mach_timebase_info(&mut info) };
    if info.numer == 0 {
        return nanos.round() as u32;
    }
    (nanos * info.denom as f64 / info.numer as f64).round() as u32
}

/// Period one block, computation half of it (the least the kernel keeps: asked a quarter, it gave half), constraint one block, preemptible: as CoreAudio's own I/O threads.
#[cfg(target_os = "macos")]
fn promote(block_nanos: f64) -> Result<String, String> {
    let period = nanos_to_ticks(block_nanos);
    let mut policy = mach2::thread_policy::thread_time_constraint_policy { period, computation: period / 2, constraint: period, preemptible: 1 };
    let result = unsafe {
        mach2::thread_policy::thread_policy_set(
            libc::pthread_mach_thread_np(libc::pthread_self()),
            mach2::thread_policy::THREAD_TIME_CONSTRAINT_POLICY,
            &mut policy as *mut mach2::thread_policy::thread_time_constraint_policy as libc::thread_policy_t,
            mach2::thread_policy::THREAD_TIME_CONSTRAINT_POLICY_COUNT,
        )
    };
    if result == 0 {
        Ok(format!("time constraint, period {:.2} ms", block_nanos / 1e6))
    } else {
        Err(format!("thread_policy_set refused the time constraint ({})", result))
    }
}

/// The calling thread's time-constraint policy: period, computation, constraint and preemptible (for the tests).
#[cfg(all(target_os = "macos", test))]
fn read_back() -> Option<(u32, u32, u32, bool)> {
    let mut policy = mach2::thread_policy::thread_time_constraint_policy { period: 0, computation: 0, constraint: 0, preemptible: 0 };
    let mut count = mach2::thread_policy::THREAD_TIME_CONSTRAINT_POLICY_COUNT;
    let mut default: mach2::boolean::boolean_t = 0;
    let result = unsafe {
        mach2::thread_policy::thread_policy_get(
            libc::pthread_mach_thread_np(libc::pthread_self()),
            mach2::thread_policy::THREAD_TIME_CONSTRAINT_POLICY,
            &mut policy as *mut mach2::thread_policy::thread_time_constraint_policy as libc::thread_policy_t,
            &mut count,
            &mut default,
        )
    };
    if result != 0 || default != 0 {
        return None;
    }
    Some((policy.period, policy.computation, policy.constraint, policy.preemptible != 0))
}

/// `SCHED_FIFO` halfway up its priorities, when the user may (the `rtprio` limit); else the thread keeps its scheduling.
#[cfg(target_os = "linux")]
fn promote(block_nanos: f64) -> Result<String, String> {
    let _ = block_nanos;
    unsafe {
        let low = libc::sched_get_priority_min(libc::SCHED_FIFO);
        let high = libc::sched_get_priority_max(libc::SCHED_FIFO);
        let param = libc::sched_param { sched_priority: (low + high) / 2 };
        let result = libc::pthread_setschedparam(libc::pthread_self(), libc::SCHED_FIFO, &param);
        if result == 0 {
            Ok(format!("SCHED_FIFO, priority {}", param.sched_priority))
        } else {
            Err(format!("SCHED_FIFO refused (error {}: the user's rtprio limit, often given to the audio group, does not allow it)", result))
        }
    }
}

#[cfg(target_os = "windows")]
#[link(name = "avrt")]
extern "system" {
    fn AvSetMmThreadCharacteristicsW(task: *const u16, index: *mut u32) -> *mut core::ffi::c_void;
}

/// MMCSS "Pro Audio": no rights needed.
#[cfg(target_os = "windows")]
fn promote(block_nanos: f64) -> Result<String, String> {
    let _ = block_nanos;
    let task: Vec<u16> = "Pro Audio".encode_utf16().chain(std::iter::once(0)).collect();
    let mut index: u32 = 0;
    let handle = unsafe { AvSetMmThreadCharacteristicsW(task.as_ptr(), &mut index) };
    if handle.is_null() {
        Err(format!("MMCSS refused Pro Audio ({})", std::io::Error::last_os_error()))
    } else {
        Ok("MMCSS Pro Audio".to_string())
    }
}

#[cfg(not(any(target_os = "macos", target_os = "linux", target_os = "windows")))]
fn promote(block_nanos: f64) -> Result<String, String> {
    let _ = block_nanos;
    Err("no real-time policy on this system".to_string())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn a_fresh_thread_is_promoted_or_says_why_not() {
        let result = std::thread::spawn(|| promote_current_thread(128, 48000.0)).join().expect("no panic");
        match result {
            Ok(policy) => assert!(!policy.is_empty()),
            Err(reason) => {
                assert!(!reason.is_empty());
                assert!(!cfg!(target_os = "macos"), "macOS always grants the time-constraint policy: {}", reason);
            }
        }
    }

    #[cfg(target_os = "macos")]
    #[test]
    fn on_macos_the_thread_reads_back_the_time_constraint_of_one_block() {
        let read = std::thread::spawn(|| {
            promote_current_thread(128, 48000.0).expect("promoted");
            read_back()
        })
        .join()
        .expect("no panic");
        let (period, computation, constraint, preemptible) = read.expect("policy read back");
        let block = nanos_to_ticks(128.0 / 48000.0 * 1e9);
        let near = |a: u32, b: u32| (a as i64 - b as i64).abs() <= (b as i64 / 50).max(2);
        assert!(near(period, block), "period {} for a block of {}", period, block);
        assert!(near(computation, block / 2), "computation {} for {}", computation, block / 2);
        assert!(near(constraint, block), "constraint {} for {}", constraint, block);
        assert!(preemptible);
    }
}
