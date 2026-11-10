//! The date and time of a damaged file's copy, `yyyymmdd-hhmmss` in UTC, without a calendar library.

use std::time::{SystemTime, UNIX_EPOCH};

/// The civil date of a day count since 1970-01-01 (Howard Hinnant's `civil_from_days`).
fn civil(days: i64) -> (i64, u32, u32) {
    let z = days + 719_468;
    let era = z.div_euclid(146_097);
    let doe = z.rem_euclid(146_097);
    let yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365;
    let doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
    let mp = (5 * doy + 2) / 153;
    let day = (doy - (153 * mp + 2) / 5 + 1) as u32;
    let month = if mp < 10 { mp + 3 } else { mp - 9 } as u32;
    let year = yoe + era * 400 + i64::from(month <= 2);
    (year, month, day)
}

/// `seconds` since 1970 as `yyyymmdd-hhmmss`.
pub fn stamp(seconds: i64) -> String {
    let (year, month, day) = civil(seconds.div_euclid(86_400));
    let rest = seconds.rem_euclid(86_400);
    format!("{year:04}{month:02}{day:02}-{:02}{:02}{:02}", rest / 3600, rest / 60 % 60, rest % 60)
}

pub fn now() -> String {
    stamp(SystemTime::now().duration_since(UNIX_EPOCH).map(|d| d.as_secs() as i64).unwrap_or(0))
}

#[cfg(test)]
mod tests {
    #[test]
    fn known_dates() {
        assert_eq!(super::stamp(0), "19700101-000000");
        assert_eq!(super::stamp(951_782_400), "20000229-000000");
        assert_eq!(super::stamp(1_793_750_399), "20261103-235959");
    }
}
