//! Pushing, popping, dropping and rendering never allocate once the ring and the resampler are made (spec 022, research R1 addendum):
//! the callbacks of the audio devices must not.

use crate::resampler::Resampler;
use crate::ring::Ring;
use std::alloc::{GlobalAlloc, Layout, System};
use std::cell::Cell;

struct Counting;

thread_local! {
    static ALLOCATIONS: Cell<u64> = const { Cell::new(0) };
}

unsafe impl GlobalAlloc for Counting {
    unsafe fn alloc(&self, layout: Layout) -> *mut u8 {
        ALLOCATIONS.with(|a| a.set(a.get() + 1));
        unsafe { System.alloc(layout) }
    }

    unsafe fn dealloc(&self, ptr: *mut u8, layout: Layout) {
        unsafe { System.dealloc(ptr, layout) }
    }
}

#[global_allocator]
static COUNTING: Counting = Counting;

fn allocations() -> u64 {
    ALLOCATIONS.with(|a| a.get())
}

#[test]
fn the_ring_and_the_resampler_never_allocate_after_they_are_made() {
    let ring = Ring::new(4096);
    let mut resampler = Resampler::new(48000.0, 44100.0, 128);
    let block = vec![0.25f32; 2 * 128];
    let mut out = vec![0f32; 128];
    let (mut l, mut r) = (vec![0f32; 128], vec![0f32; 128]);
    let before = allocations();
    for _ in 0..1000 {
        ring.push(&block);
        ring.keep_at_most(3 * 256, 256);
        resampler.render(&ring, &mut l, &mut r);
        ring.pop(&mut out);
        ring.skip(16);
    }
    assert_eq!(allocations() - before, 0);
}
