//! The estimated delay (spec 027, T027, FR-017, data-model "Estimated delay"): the input block, the output block, the two devices' declared
//! latencies and the resampler's target fill, in milliseconds to a tenth.

use guitarfx_app::delay_estimate::{milliseconds, Parts};

fn parts() -> Parts {
    Parts {
        input_rate: 48000.0,
        output_rate: 48000.0,
        input_block: 128,
        output_block: 128,
        input_declared: 0,
        output_declared: 0,
        resample_fill: 0.0,
    }
}

#[test]
fn two_blocks_of_128_at_48_khz_are_5_3_ms() {
    assert_eq!(milliseconds(&parts()), 5.3);
}

#[test]
fn every_part_is_counted() {
    let p = Parts {
        input_declared: 48,
        output_declared: 240,
        resample_fill: 256.0,
        ..parts()
    };
    // (128 + 128 + 48 + 240 + 256) / 48 = 16.67
    assert_eq!(milliseconds(&p), 16.7);
}

#[test]
fn each_device_is_counted_at_its_own_rate() {
    let p = Parts {
        input_rate: 44100.0,
        output_rate: 48000.0,
        input_block: 441,
        output_block: 480,
        input_declared: 441,
        output_declared: 0,
        resample_fill: 882.0,
    };
    // 10 + 10 + 10 + 0 + 20 ms (the fill is in input frames)
    assert_eq!(milliseconds(&p), 50.0);
}

#[test]
fn no_rate_gives_no_estimate() {
    let p = Parts { output_rate: 0.0, ..parts() };
    assert!(milliseconds(&p).is_nan());
}
