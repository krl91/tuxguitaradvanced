//! No number that changes the sound is fixed in the code unless the inventory says why (spec 027, T009, FR-016, SC-008). The scan reads
//! every numeric literal of `guitarfx-dsp/src`, `guitarfx-app/src` and `guitarfx-plugin/src`, outside comments, strings and
//! `#[cfg(test)]` code, and compares it by value with the rules of `specs/027-standalone-amp/inventory.md` ("How the scan reads this file").

use std::collections::{BTreeMap, BTreeSet};
use std::path::{Path, PathBuf};

const ALLOWED_ANYWHERE: [f64; 8] = [0.0, 1.0, 2.0, 0.5, 10.0, 20.0, 100.0, 1000.0];

/// A value as a comparable key (the inventory's `0.010` and the code's `1e-2` are one value).
fn key(value: f64) -> u64 {
    value.abs().to_bits()
}

/// Every number of a Markdown table cell (`1116, 1188`, `-0.35`, `1e-20`); words and ellipses are skipped.
fn numbers_in(cell: &str) -> Vec<f64> {
    cell.split(|c: char| c == ',' || c.is_whitespace() || c == '`' || c == '(' || c == ')' || c == '/')
        .filter_map(|word| word.trim_start_matches('−').parse::<f64>().ok())
        .collect()
}

fn cells(line: &str) -> Vec<String> {
    line.trim().trim_matches('|').split('|').map(|c| c.trim().to_string()).collect()
}

/// The values allowed in each file name (`params.rs`, `biquad.rs`, …).
fn allowed() -> BTreeMap<String, BTreeSet<u64>> {
    let path = concat!(env!("CARGO_MANIFEST_DIR"), "/../../specs/027-standalone-amp/inventory.md");
    let text = std::fs::read_to_string(path).expect("the inventory");
    let mut allowed: BTreeMap<String, BTreeSet<u64>> = BTreeMap::new();
    let mut section = "";
    for line in text.lines() {
        if let Some(title) = line.strip_prefix("## ") {
            section = if title.starts_with("Parameters") {
                "parameters"
            } else if title.starts_with("Fixed") {
                "fixed"
            } else if title.starts_with("Knob ranges") {
                "knobs"
            } else {
                ""
            };
            continue;
        }
        if !line.starts_with('|')
            || line.starts_with("|---")
            || line.starts_with("| Key")
            || line.starts_with("| Value")
            || line.starts_with("| Kind")
        {
            continue;
        }
        let cells = cells(line);
        match section {
            "parameters" if cells[0].starts_with("`guitarfx.") => {
                let set = allowed.entry("params.rs".into()).or_default();
                for cell in &cells[2..5] {
                    set.extend(numbers_in(cell).into_iter().map(key));
                }
            }
            "fixed" => {
                let values: Vec<f64> = numbers_in(&cells[0]);
                for file in cells[1].split('`').filter(|f| f.ends_with(".rs")) {
                    allowed.entry(file.to_string()).or_default().extend(values.iter().copied().map(key));
                }
            }
            "knobs" => {
                let set = allowed.entry("knobs.rs".into()).or_default();
                for cell in &cells[2..5] {
                    set.extend(numbers_in(cell).into_iter().map(key));
                }
            }
            _ => {}
        }
    }
    assert!(
        allowed.contains_key("params.rs") && allowed.contains_key("biquad.rs") && allowed.contains_key("knobs.rs"),
        "the inventory's tables are read"
    );
    allowed
}

/// The source with comments, strings, character literals and `#[cfg(test)]` items blanked out (line breaks kept).
fn code_only(source: &str) -> String {
    let chars: Vec<char> = source.chars().collect();
    let mut out = String::with_capacity(source.len());
    let mut i = 0;
    let blank = |c: char| if c == '\n' { '\n' } else { ' ' };
    while i < chars.len() {
        let c = chars[i];
        let next = chars.get(i + 1).copied().unwrap_or('\0');
        if c == '/' && next == '/' {
            while i < chars.len() && chars[i] != '\n' {
                out.push(' ');
                i += 1;
            }
        } else if c == '/' && next == '*' {
            let mut depth = 0;
            loop {
                if i >= chars.len() {
                    break;
                }
                if chars[i] == '/' && chars.get(i + 1) == Some(&'*') {
                    depth += 1;
                    out.push_str("  ");
                    i += 2;
                } else if chars[i] == '*' && chars.get(i + 1) == Some(&'/') {
                    depth -= 1;
                    out.push_str("  ");
                    i += 2;
                    if depth == 0 {
                        break;
                    }
                } else {
                    out.push(blank(chars[i]));
                    i += 1;
                }
            }
        } else if c == 'r' && (next == '"' || next == '#') && (i == 0 || !is_ident(chars[i - 1])) {
            // a raw string r"…" or r#"…"#
            let mut j = i + 1;
            let mut hashes = 0;
            while chars.get(j) == Some(&'#') {
                hashes += 1;
                j += 1;
            }
            if chars.get(j) != Some(&'"') {
                out.push(c);
                i += 1;
                continue;
            }
            let closing: String = std::iter::once('"').chain(std::iter::repeat_n('#', hashes)).collect();
            let rest: String = chars[j + 1..].iter().collect();
            let end = rest
                .find(&closing)
                .map(|e| j + 1 + rest[..e].chars().count() + closing.chars().count())
                .unwrap_or(chars.len());
            while i < end {
                out.push(blank(chars[i]));
                i += 1;
            }
        } else if c == '"' {
            out.push(' ');
            i += 1;
            while i < chars.len() && chars[i] != '"' {
                if chars[i] == '\\' {
                    out.push(' ');
                    i += 1;
                }
                if i < chars.len() {
                    out.push(blank(chars[i]));
                    i += 1;
                }
            }
            out.push(' ');
            i += 1;
        } else if c == '\'' && (chars.get(i + 2) == Some(&'\'') || next == '\\') {
            // a character literal, not a lifetime
            out.push(' ');
            i += 1;
            while i < chars.len() && chars[i] != '\'' {
                if chars[i] == '\\' {
                    out.push(' ');
                    i += 1;
                }
                out.push(' ');
                i += 1;
            }
            out.push(' ');
            i += 1;
        } else {
            out.push(c);
            i += 1;
        }
    }
    blank_test_items(&out)
}

fn blank_test_items(code: &str) -> String {
    let mut chars: Vec<char> = code.chars().collect();
    let marker: Vec<char> = "#[cfg(test)]".chars().collect();
    let mut i = 0;
    while i + marker.len() <= chars.len() {
        if chars[i..i + marker.len()] != marker[..] {
            i += 1;
            continue;
        }
        let mut j = i + marker.len();
        while j < chars.len() && chars[j] != '{' && chars[j] != ';' {
            j += 1;
        }
        if j < chars.len() && chars[j] == '{' {
            let mut depth = 0;
            while j < chars.len() {
                if chars[j] == '{' {
                    depth += 1;
                } else if chars[j] == '}' {
                    depth -= 1;
                    if depth == 0 {
                        break;
                    }
                }
                j += 1;
            }
        }
        let end = (j + 1).min(chars.len());
        for c in chars.iter_mut().take(end).skip(i) {
            if *c != '\n' {
                *c = ' ';
            }
        }
        i = j + 1;
    }
    chars.into_iter().collect()
}

fn is_ident(c: char) -> bool {
    c.is_ascii_alphanumeric() || c == '_'
}

/// Every numeric literal of the code, with its line.
fn literals(code: &str) -> Vec<(usize, f64, String)> {
    let chars: Vec<char> = code.chars().collect();
    let mut found = Vec::new();
    let mut line = 1;
    let mut i = 0;
    while i < chars.len() {
        let c = chars[i];
        if c == '\n' {
            line += 1;
            i += 1;
            continue;
        }
        let previous = if i > 0 { chars[i - 1] } else { ' ' };
        let field_access = previous == '.' && !(i > 1 && chars[i - 2] == '.');
        if !c.is_ascii_digit() || is_ident(previous) || field_access {
            if is_ident(c) {
                while i < chars.len() && is_ident(chars[i]) {
                    i += 1;
                }
            } else {
                i += 1;
            }
            continue;
        }
        let start = i;
        let value;
        if c == '0' && matches!(chars.get(i + 1), Some('x') | Some('o') | Some('b')) {
            let radix = match chars[i + 1] {
                'x' => 16,
                'o' => 8,
                _ => 2,
            };
            i += 2;
            let digits_start = i;
            while i < chars.len() && (chars[i].is_ascii_hexdigit() || chars[i] == '_') {
                i += 1;
            }
            let digits: String = chars[digits_start..i].iter().filter(|c| **c != '_').collect();
            value = u64::from_str_radix(&digits, radix).unwrap_or(0) as f64;
        } else {
            while i < chars.len() && (chars[i].is_ascii_digit() || chars[i] == '_') {
                i += 1;
            }
            if i + 1 < chars.len() && chars[i] == '.' && chars[i + 1].is_ascii_digit() {
                i += 1;
                while i < chars.len() && (chars[i].is_ascii_digit() || chars[i] == '_') {
                    i += 1;
                }
            } else if i < chars.len() && chars[i] == '.' && chars.get(i + 1) != Some(&'.') && !chars.get(i + 1).copied().is_some_and(is_ident) {
                i += 1; // `1.`
            }
            if i < chars.len() && (chars[i] == 'e' || chars[i] == 'E') {
                let mut j = i + 1;
                if j < chars.len() && (chars[j] == '+' || chars[j] == '-') {
                    j += 1;
                }
                if j < chars.len() && chars[j].is_ascii_digit() {
                    i = j;
                    while i < chars.len() && (chars[i].is_ascii_digit() || chars[i] == '_') {
                        i += 1;
                    }
                }
            }
            let text: String = chars[start..i].iter().filter(|c| **c != '_').collect();
            value = text.parse().unwrap_or_else(|_| panic!("literal {text}"));
        }
        // a type suffix (f64, usize, …)
        while i < chars.len() && is_ident(chars[i]) {
            i += 1;
        }
        found.push((line, value, chars[start..i].iter().collect()));
    }
    found
}

fn sources(dir: &Path, files: &mut Vec<PathBuf>) {
    let Ok(entries) = std::fs::read_dir(dir) else {
        return;
    };
    for entry in entries.flatten() {
        let path = entry.path();
        if path.is_dir() {
            sources(&path, files);
        } else if path.extension().is_some_and(|e| e == "rs") {
            files.push(path);
        }
    }
}

#[test]
fn every_number_of_the_code_is_in_the_inventory() {
    let allowed = allowed();
    let anywhere: BTreeSet<u64> = ALLOWED_ANYWHERE.iter().copied().map(key).collect();
    let root = Path::new(env!("CARGO_MANIFEST_DIR")).join("..");
    let mut files = Vec::new();
    for crate_dir in ["guitarfx-dsp", "guitarfx-app", "guitarfx-plugin"] {
        sources(&root.join(crate_dir).join("src"), &mut files);
    }
    files.sort();
    let mut silent = Vec::new();
    for file in &files {
        let name = file.file_name().unwrap().to_string_lossy().to_string();
        let here = allowed.get(&name);
        let source = std::fs::read_to_string(file).unwrap();
        for (line, value, text) in literals(&code_only(&source)) {
            let k = key(value);
            if anywhere.contains(&k) || here.is_some_and(|set| set.contains(&k)) {
                continue;
            }
            silent.push(format!("{}:{line}: {text}", file.strip_prefix(&root).unwrap_or(file).display()));
        }
    }
    assert!(
        silent.is_empty(),
        "numbers the inventory does not name for their file (add a row, or make it a parameter):\n{}",
        silent.join("\n")
    );
}

#[test]
fn the_scan_sees_literals_and_skips_comments_strings_and_tests() {
    let source = "fn f() -> f64 { let a = 0.45 * 3e-2_f64; let s = \"7\"; x.3 + 1..=12 } // 99\n/* 98 */ #[cfg(test)] mod tests { const X: f64 = 97.0; }\nconst Y: u32 = 0x10;";
    let found: Vec<f64> = literals(&code_only(source)).into_iter().map(|(_, v, _)| v).collect();
    assert_eq!(found, vec![0.45, 3e-2, 1.0, 12.0, 16.0]);
}
