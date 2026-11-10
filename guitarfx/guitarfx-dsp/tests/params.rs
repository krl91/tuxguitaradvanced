//! The parameter registry against the inventory (spec 027, T008, FR-016): every parameter row of `specs/027-standalone-amp/inventory.md` is
//! in `params.rs` with the same unit, category and bounds, and a default equal to the Java literal it replaces.

use guitarfx_dsp::params::{Params, ENTRIES};
use std::collections::{BTreeMap, BTreeSet};

struct Row {
    key: String,
    unit: String,
    default: f64,
    min: f64,
    max: f64,
}

fn inventory() -> Vec<Row> {
    let path = concat!(env!("CARGO_MANIFEST_DIR"), "/../../specs/027-standalone-amp/inventory.md");
    let text = std::fs::read_to_string(path).expect("the inventory");
    let mut rows = Vec::new();
    for line in text.lines() {
        if !line.starts_with("| `guitarfx.") {
            continue;
        }
        let cells: Vec<&str> = line.trim_matches('|').split('|').map(str::trim).collect();
        rows.push(Row {
            key: cells[0].trim_matches('`').to_string(),
            unit: cells[1].to_string(),
            default: cells[2].parse().unwrap_or_else(|_| panic!("{}: default {}", cells[0], cells[2])),
            min: cells[3].parse().unwrap(),
            max: cells[4].parse().unwrap(),
        });
    }
    rows
}

#[test]
fn every_parameter_of_the_inventory_is_in_the_registry_with_its_java_default() {
    let rows = inventory();
    assert_eq!(rows.len(), 103, "the inventory's total");
    assert_eq!(ENTRIES.len(), rows.len(), "one entry per parameter row");
    for row in &rows {
        let entry = ENTRIES
            .iter()
            .find(|e| e.key == row.key)
            .unwrap_or_else(|| panic!("{} is missing from params.rs", row.key));
        assert_eq!(entry.unit, row.unit, "{} unit", row.key);
        assert!(
            entry.default == row.default,
            "{} default {} is not the Java literal {}",
            row.key,
            entry.default,
            row.default
        );
        assert!(entry.min == row.min && entry.max == row.max, "{} bounds", row.key);
        let category = row.key.split('.').nth(1).unwrap();
        assert_eq!(entry.category, category, "{} category", row.key);
    }
}

#[test]
fn every_default_is_within_its_bounds() {
    for entry in ENTRIES {
        assert!(entry.min <= entry.default && entry.default <= entry.max, "{}", entry.key);
    }
}

#[test]
fn at_most_eight_parameters_per_category() {
    let mut counts: BTreeMap<&str, usize> = BTreeMap::new();
    for entry in ENTRIES {
        *counts.entry(entry.category).or_default() += 1;
    }
    for (category, count) in counts {
        assert!(count <= 8, "{category} has {count}");
    }
}

#[test]
fn the_keys_are_unique_and_named_by_their_category() {
    let mut seen = BTreeSet::new();
    for entry in ENTRIES {
        assert!(seen.insert(entry.key), "{} twice", entry.key);
        assert!(entry.key.starts_with(&format!("guitarfx.{}.", entry.category)), "{}", entry.key);
    }
}

#[test]
fn the_defaults_are_the_registry_defaults() {
    let params = Params::defaults();
    for entry in ENTRIES {
        assert!(params.get_key(entry.key) == Some(entry.default), "{}", entry.key);
    }
}

#[test]
fn a_value_out_of_bounds_is_clamped_and_a_bad_or_unknown_one_ignored() {
    let params = Params::from_values([
        ("guitarfx.output.ceiling", "5"),
        ("guitarfx.gate.floor", "-500"),
        ("guitarfx.devices.block", "256"),
        ("guitarfx.gate.hold", "abc"),
        ("guitarfx.nothing.here", "1"),
    ]);
    assert_eq!(params.get_key("guitarfx.output.ceiling"), Some(0.0));
    assert_eq!(params.get_key("guitarfx.gate.floor"), Some(-120.0));
    assert_eq!(params.get_key("guitarfx.devices.block"), Some(256.0));
    assert_eq!(params.get_key("guitarfx.gate.hold"), Some(0.010));
    assert_eq!(params.get_key("guitarfx.nothing.here"), None);
}

#[test]
fn the_combined_bounds_keep_the_reverb_and_the_echo_decaying() {
    let params = Params::from_values([
        ("guitarfx.reverb.feedback", "0.9"),
        ("guitarfx.reverb.size-step", "0.029"),
        ("guitarfx.echo.repeats-step", "0.099"),
    ]);
    let feedback = params.get_key("guitarfx.reverb.feedback").unwrap();
    let step = params.get_key("guitarfx.reverb.size-step").unwrap();
    assert!(feedback + 10.0 * step <= 0.99 + 1e-12, "{feedback} + 10 × {step}");
    assert!(10.0 * params.get_key("guitarfx.echo.repeats-step").unwrap() <= 0.99 + 1e-12);
    let defaults = Params::defaults();
    assert_eq!(defaults.get_key("guitarfx.reverb.size-step"), Some(0.028));
    assert_eq!(defaults.get_key("guitarfx.echo.repeats-step"), Some(0.09));
}
