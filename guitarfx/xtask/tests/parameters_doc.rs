//! docs/guitarfx/parameters.md is the registry's (FR-019, T061): regenerated here and compared, so a parameter changed without
//! `cargo run -p xtask -- parameters` fails.

use guitarfx_dsp::params::ENTRIES;

#[test]
fn the_committed_page_is_the_generated_one() {
    let committed = std::fs::read_to_string(xtask::parameters_path()).expect("docs/guitarfx/parameters.md is missing");
    assert!(
        committed == xtask::parameters_markdown(),
        "docs/guitarfx/parameters.md is out of date: run `cargo run -p xtask -- parameters` in guitarfx/"
    );
}

#[test]
fn every_parameter_is_listed_with_its_bounds() {
    let page = xtask::parameters_markdown();
    for entry in ENTRIES.iter() {
        let row = page
            .lines()
            .find(|line| line.starts_with(&format!("| `{}` |", entry.key)))
            .unwrap_or_else(|| panic!("{} is not listed", entry.key));
        for value in [entry.default, entry.min, entry.max] {
            assert!(row.contains(&xtask::number(value)), "{}: {} missing from {row}", entry.key, value);
        }
    }
}
