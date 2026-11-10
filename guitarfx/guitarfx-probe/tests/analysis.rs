//! The probe's analysis (research R8), on made-up recordings: the clicks are found at their delay, with the threshold of spec 022's
//! `MonitorLatencyProbeTest` (600 in 16 bits, or 8 times the noise of the 0.1 s before), and the table row is spec 022's.

use guitarfx_probe::analysis::{click_start, find_delays, row, Schedule, Summary};

const RATE: f32 = 48000.0;

fn schedule() -> Schedule {
    Schedule::new(RATE, 20)
}

/// A recording with low noise and each click heard `delays[k]` frames after it was put into the engine.
fn recording(schedule: &Schedule, delays: &[usize], noise: f32) -> Vec<f32> {
    let mut seed = 1u32;
    let mut captured: Vec<f32> = (0..schedule.total_frames())
        .map(|_| {
            seed = seed.wrapping_mul(1664525).wrapping_add(1013904223);
            noise * ((seed >> 8) as f32 / (1u32 << 24) as f32 * 2.0 - 1.0)
        })
        .collect();
    for (k, delay) in delays.iter().enumerate() {
        let at = click_start(schedule, k) + delay;
        for sample in &mut captured[at..at + 24] {
            *sample = 0.3;
        }
    }
    captured
}

#[test]
fn the_schedule_is_spec_022s() {
    let s = schedule();
    assert_eq!(s.spacing, 144000, "3 s apart");
    assert_eq!(s.click_frames, 48, "1 ms");
    assert_eq!(click_start(&s, 0), 144000, "the first click after the fade in");
    assert_eq!(s.total_frames(), 144000 * 21 + 144000 / 2);
}

#[test]
fn every_click_is_found_at_its_delay() {
    let s = schedule();
    let delays: Vec<usize> = (0..20).map(|k| 1200 + 10 * k).collect();
    let found = find_delays(&recording(&s, &delays, 0.001), &s);
    assert_eq!(found.len(), 20);
    for (k, ms) in found.iter().enumerate() {
        let expected = delays[k] as f64 * 1000.0 / f64::from(RATE);
        assert!((ms - expected).abs() < 1e-9, "click {k}: {ms} ms, expected {expected}");
    }
}

#[test]
fn a_click_lost_in_loud_noise_is_not_found() {
    let s = schedule();
    // the noise's RMS is about 0.29 × 0.5, so 8 times it is above the click
    let found = find_delays(&recording(&s, &[1200; 20], 0.5), &s);
    assert!(found.len() < 20, "{} found", found.len());
}

#[test]
fn a_click_later_than_the_window_is_not_found() {
    let s = schedule();
    // 0.3 s is the window of spec 022's probe
    let found = find_delays(&recording(&s, &[14500; 20], 0.001), &s);
    assert!(found.is_empty());
}

#[test]
fn the_row_is_spec_022s_table() {
    let summary = Summary {
        block: 128,
        rate: 48000,
        found: vec![32.6, 32.3, 32.5],
        clicks: 20,
        estimate: 31.4,
        dropped: 5,
        underruns: 10,
    };
    assert_eq!(row(&summary), "| 128 | 48000 | 3 / 20 | 32.5 | 32.3 | 32.6 | 31.4 | 1.1 | 5 | 10 |");
    let none = Summary { found: vec![], ..summary };
    assert_eq!(row(&none), "| 128 | 48000 | 0 / 20 | - | - | - | 31.4 | - | 5 | 10 |");
}
