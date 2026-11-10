//! The application's mailbox (`tuxguitar-monitor-native`'s `recognition::Mailbox`), the same code: the plugin cannot link that crate,
//! whose JNI entry points would be exported by the plugin's library (research R3).
//!
//! A value handed from the editor's thread to `process`. `post` (the editor, one at a time) frees what `process` gave back and any value
//! not taken yet; `take` and `give_back` (`process`) never free.

use std::ptr;
use std::sync::atomic::{AtomicPtr, Ordering};

pub struct Mailbox<T> {
    pending: AtomicPtr<T>,
    retired: AtomicPtr<T>,
}

impl<T> Default for Mailbox<T> {
    fn default() -> Mailbox<T> {
        Mailbox {
            pending: AtomicPtr::new(ptr::null_mut()),
            retired: AtomicPtr::new(ptr::null_mut()),
        }
    }
}

fn free<T>(p: *mut T) {
    if !p.is_null() {
        // SAFETY: every non-null pointer in a mailbox came from Box::into_raw and is owned by whoever swapped it out
        drop(unsafe { Box::from_raw(p) });
    }
}

impl<T> Mailbox<T> {
    pub fn post(&self, value: Box<T>) {
        free(self.retired.swap(ptr::null_mut(), Ordering::AcqRel));
        free(self.pending.swap(Box::into_raw(value), Ordering::AcqRel));
    }

    pub fn take(&self) -> Option<Box<T>> {
        let p = self.pending.swap(ptr::null_mut(), Ordering::AcqRel);
        // SAFETY: as in `free`
        if p.is_null() {
            None
        } else {
            Some(unsafe { Box::from_raw(p) })
        }
    }

    /// Hands back a used value for the editor to free; gives it back when the slot is still taken (`process` keeps it and tries again).
    pub fn give_back(&self, value: Box<T>) -> Option<Box<T>> {
        let p = Box::into_raw(value);
        match self.retired.compare_exchange(ptr::null_mut(), p, Ordering::AcqRel, Ordering::Acquire) {
            Ok(_) => None,
            // SAFETY: p was just made from the box
            Err(_) => Some(unsafe { Box::from_raw(p) }),
        }
    }
}

impl<T> Drop for Mailbox<T> {
    fn drop(&mut self) {
        free(self.pending.swap(ptr::null_mut(), Ordering::AcqRel));
        free(self.retired.swap(ptr::null_mut(), Ordering::AcqRel));
    }
}
