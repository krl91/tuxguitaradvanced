//! The packed frame record (spec 023, data-model "Frame record", `frame_layout.txt`): what Java turns into an `AnalysisFrame`. A long or a
//! double takes two slots (high then low 32 bits, as raw float bits) so nothing is rounded; an int or a count is a float value. A record
//! longer than its buffer is refused, never cut.

pub const LENGTH: usize = 0;
pub const TIMESTAMP_NANOS: usize = 1;
pub const CAPTURE_SAMPLE_END: usize = 3;
pub const ARRIVAL_NANOS: usize = 5;
pub const RMS_DBFS: usize = 7;
pub const PEAK_DBFS: usize = 9;
pub const CLIPPED_RATIO: usize = 11;
pub const LEVEL_STATE: usize = 13;
pub const F0_HZ: usize = 14;
pub const CONFIDENCE: usize = 16;
pub const ONSET: usize = 18;
pub const PARTS: usize = 19;
pub const HEADER: usize = 20;

pub const PART_SPECTRUM: u32 = 1;
pub const PART_TARGET: u32 = 2;
pub const PART_ATTACK: u32 = 4;
pub const PART_CHORD: u32 = 8;

pub const CAPACITY: usize = 8192;

/// Writes one record into a slice; `overflow` is set when something did not fit (the record is then refused).
pub struct Writer<'a> {
    out: &'a mut [f32],
    at: usize,
    overflow: bool,
}

impl<'a> Writer<'a> {
    pub fn new(out: &'a mut [f32]) -> Writer<'a> {
        let at = HEADER.min(out.len());
        let overflow = out.len() < HEADER;
        Writer { out, at, overflow }
    }

    pub fn set_int(&mut self, slot: usize, value: i32) {
        if slot < self.out.len() {
            self.out[slot] = value as f32;
        }
    }

    pub fn set_long(&mut self, slot: usize, value: i64) {
        if slot + 1 < self.out.len() {
            self.out[slot] = f32::from_bits((value as u64 >> 32) as u32);
            self.out[slot + 1] = f32::from_bits(value as u64 as u32);
        }
    }

    pub fn set_double(&mut self, slot: usize, value: f64) {
        self.set_long(slot, value.to_bits() as i64);
    }

    pub fn int(&mut self, value: i32) {
        if self.at < self.out.len() {
            self.out[self.at] = value as f32;
            self.at += 1;
        } else {
            self.overflow = true;
        }
    }

    pub fn float(&mut self, value: f32) {
        if self.at < self.out.len() {
            self.out[self.at] = value;
            self.at += 1;
        } else {
            self.overflow = true;
        }
    }

    pub fn double(&mut self, value: f64) {
        if self.at + 1 < self.out.len() {
            let bits = value.to_bits();
            self.out[self.at] = f32::from_bits((bits >> 32) as u32);
            self.out[self.at + 1] = f32::from_bits(bits as u32);
            self.at += 2;
        } else {
            self.overflow = true;
        }
    }

    /// Ends the record: its length is written in its first slot; `None` when it did not fit.
    pub fn finish(mut self) -> Option<usize> {
        if self.overflow {
            return None;
        }
        let length = self.at;
        self.set_int(LENGTH, length as i32);
        Some(length)
    }
}

/// Reads a long or a double written by the writer (tests and the stats).
pub fn long_at(record: &[f32], slot: usize) -> i64 {
    (((record[slot].to_bits() as u64) << 32) | record[slot + 1].to_bits() as u64) as i64
}

pub fn double_at(record: &[f32], slot: usize) -> f64 {
    f64::from_bits(long_at(record, slot) as u64)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn layout() -> Vec<(String, usize)> {
        include_str!("frame_layout.txt")
            .lines()
            .filter(|l| !l.trim().is_empty() && !l.starts_with('#'))
            .map(|l| {
                let mut parts = l.split_whitespace();
                (parts.next().unwrap().to_string(), parts.next().unwrap().parse().unwrap())
            })
            .collect()
    }

    #[test]
    fn the_offsets_are_those_of_the_shared_table() {
        let mine: Vec<(&str, usize)> = vec![
            ("LENGTH", LENGTH),
            ("TIMESTAMP_NANOS", TIMESTAMP_NANOS),
            ("CAPTURE_SAMPLE_END", CAPTURE_SAMPLE_END),
            ("ARRIVAL_NANOS", ARRIVAL_NANOS),
            ("RMS_DBFS", RMS_DBFS),
            ("PEAK_DBFS", PEAK_DBFS),
            ("CLIPPED_RATIO", CLIPPED_RATIO),
            ("LEVEL_STATE", LEVEL_STATE),
            ("F0_HZ", F0_HZ),
            ("CONFIDENCE", CONFIDENCE),
            ("ONSET", ONSET),
            ("PARTS", PARTS),
            ("HEADER", HEADER),
            ("PART_SPECTRUM", PART_SPECTRUM as usize),
            ("PART_TARGET", PART_TARGET as usize),
            ("PART_ATTACK", PART_ATTACK as usize),
            ("PART_CHORD", PART_CHORD as usize),
            ("CAPACITY", CAPACITY),
        ];
        let table = layout();
        assert_eq!(table.len(), mine.len(), "every entry of the table has a constant");
        for (name, offset) in mine {
            let found = table.iter().find(|(n, _)| n == name).unwrap_or_else(|| panic!("{name} missing from frame_layout.txt"));
            assert_eq!(found.1, offset, "{name}");
        }
    }

    #[test]
    fn longs_and_doubles_keep_every_bit_and_an_overflow_is_refused() {
        let mut out = vec![0f32; 32];
        let mut writer = Writer::new(&mut out);
        writer.set_long(TIMESTAMP_NANOS, -1_234_567_890_123_456_789);
        writer.set_double(F0_HZ, f64::NAN);
        writer.set_double(RMS_DBFS, f64::NEG_INFINITY);
        writer.double(0.1 + 0.2);
        writer.int(-7);
        assert_eq!(writer.finish(), Some(HEADER + 3));
        assert_eq!(long_at(&out, TIMESTAMP_NANOS), -1_234_567_890_123_456_789);
        assert!(double_at(&out, F0_HZ).is_nan());
        assert_eq!(double_at(&out, RMS_DBFS), f64::NEG_INFINITY);
        assert_eq!(double_at(&out, HEADER), 0.1 + 0.2);
        assert_eq!(out[HEADER + 2], -7.0);
        assert_eq!(out[LENGTH], (HEADER + 3) as f32);

        let mut small = vec![0f32; HEADER + 1];
        let mut writer = Writer::new(&mut small);
        writer.double(1.0);
        assert_eq!(writer.finish(), None, "a record longer than its buffer is refused");
    }
}
