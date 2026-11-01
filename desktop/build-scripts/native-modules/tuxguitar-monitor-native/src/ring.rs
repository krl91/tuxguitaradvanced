//! A lock-free ring of samples for one producer and one consumer (spec 022, research R1 addendum). Every buffer is made when the
//! sound starts; pushing and popping never allocate, lock or wait.

use std::cell::UnsafeCell;
use std::sync::atomic::{AtomicUsize, Ordering};

pub struct Ring {
    buffer: Box<[UnsafeCell<f32>]>,
    mask: usize,
    written: AtomicUsize,
    read: AtomicUsize,
}

// One thread pushes, one thread pops: each side only writes the cells the other side does not see yet.
unsafe impl Sync for Ring {}
unsafe impl Send for Ring {}

impl Ring {
    /// A ring holding at least `capacity` samples (rounded up to a power of two).
    pub fn new(capacity: usize) -> Ring {
        let size = capacity.max(2).next_power_of_two();
        let buffer = (0..size).map(|_| UnsafeCell::new(0.0)).collect::<Vec<_>>().into_boxed_slice();
        Ring { buffer, mask: size - 1, written: AtomicUsize::new(0), read: AtomicUsize::new(0) }
    }

    pub fn capacity(&self) -> usize {
        self.mask + 1
    }

    /// The samples waiting to be popped.
    pub fn len(&self) -> usize {
        self.written.load(Ordering::Acquire).wrapping_sub(self.read.load(Ordering::Acquire))
    }

    pub fn is_empty(&self) -> bool {
        self.len() == 0
    }

    /// Producer side: pushes as many samples as fit; returns how many.
    pub fn push(&self, samples: &[f32]) -> usize {
        let written = self.written.load(Ordering::Relaxed);
        let read = self.read.load(Ordering::Acquire);
        let room = self.capacity() - written.wrapping_sub(read);
        let count = samples.len().min(room);
        for (i, sample) in samples[..count].iter().enumerate() {
            unsafe { *self.buffer[(written.wrapping_add(i)) & self.mask].get() = *sample };
        }
        self.written.store(written.wrapping_add(count), Ordering::Release);
        count
    }

    /// Consumer side: pops up to `out.len()` samples; returns how many.
    pub fn pop(&self, out: &mut [f32]) -> usize {
        let read = self.read.load(Ordering::Relaxed);
        let written = self.written.load(Ordering::Acquire);
        let count = out.len().min(written.wrapping_sub(read));
        for (i, slot) in out[..count].iter_mut().enumerate() {
            *slot = unsafe { *self.buffer[(read.wrapping_add(i)) & self.mask].get() };
        }
        self.read.store(read.wrapping_add(count), Ordering::Release);
        count
    }

    /// Consumer side: drops up to `count` of the oldest samples; returns how many.
    pub fn skip(&self, count: usize) -> usize {
        let read = self.read.load(Ordering::Relaxed);
        let written = self.written.load(Ordering::Acquire);
        let count = count.min(written.wrapping_sub(read));
        self.read.store(read.wrapping_add(count), Ordering::Release);
        count
    }

    /// Consumer side: drops the oldest `step` samples at a time while more than `max` wait; returns how many steps were dropped.
    pub fn keep_at_most(&self, max: usize, step: usize) -> u64 {
        let step = step.max(1);
        let mut dropped = 0;
        while self.len() > max {
            self.skip(step);
            dropped += 1;
        }
        dropped
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::Arc;

    #[test]
    fn samples_come_out_in_order_across_threads() {
        let ring = Arc::new(Ring::new(256));
        let producer = ring.clone();
        let total = 200_000usize;
        let writer = std::thread::spawn(move || {
            let mut next = 0usize;
            let mut block = [0f32; 37];
            while next < total {
                let n = block.len().min(total - next);
                for (i, s) in block[..n].iter_mut().enumerate() {
                    *s = (next + i) as f32;
                }
                let pushed = producer.push(&block[..n]);
                next += pushed;
                if pushed == 0 {
                    std::thread::yield_now();
                }
            }
        });
        let mut expected = 0usize;
        let mut out = [0f32; 53];
        while expected < total {
            let n = ring.pop(&mut out);
            for s in &out[..n] {
                assert_eq!(*s, expected as f32);
                expected += 1;
            }
            if n == 0 {
                std::thread::yield_now();
            }
        }
        writer.join().unwrap();
    }

    #[test]
    fn a_full_ring_takes_only_what_fits_and_skip_drops_the_oldest() {
        let ring = Ring::new(8);
        assert_eq!(ring.push(&[1.0; 6]), 6);
        assert_eq!(ring.push(&[2.0, 3.0, 4.0, 5.0]), 2, "only two more fit");
        assert_eq!(ring.skip(5), 5);
        let mut out = [0f32; 8];
        assert_eq!(ring.pop(&mut out), 3);
        assert_eq!(&out[..3], &[1.0, 2.0, 3.0]);
        assert!(ring.is_empty());
        assert_eq!(ring.pop(&mut out), 0, "an empty ring gives nothing");
    }

    #[test]
    fn at_most_two_blocks_wait_after_a_read_the_oldest_dropped_and_counted() {
        let ring = Ring::new(1024);
        for b in 0..5 {
            ring.push(&[b as f32; 128]);
        }
        // the reader takes one block: three may wait before it (the one read and two behind), the two oldest go
        assert_eq!(ring.keep_at_most(3 * 128, 128), 2);
        let mut out = [0f32; 128];
        assert_eq!(ring.pop(&mut out), 128);
        assert_eq!(out[0], 2.0, "the oldest blocks were dropped");
        assert_eq!(ring.len(), 2 * 128);
        assert_eq!(ring.keep_at_most(3 * 128, 128), 0, "nothing more to drop");
    }
}
