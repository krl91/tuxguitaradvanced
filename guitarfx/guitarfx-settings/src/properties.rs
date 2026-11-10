//! UTF-8 `key=value` files read and written as Java's `Properties` does: `#` and `!` comments, `=`, `:` or a space between key and value,
//! backslash escapes and continued lines. Written atomically: a temporary file in the same folder, then a rename.

use std::io::Write;
use std::path::Path;

/// The lines joined where a line ends with an odd number of backslashes, the next line's leading spaces dropped.
fn logical_lines(text: &str) -> Vec<String> {
    let mut lines = Vec::new();
    let mut current = String::new();
    let mut continuing = false;
    for raw in text.split('\n') {
        let raw = raw.strip_suffix('\r').unwrap_or(raw);
        let line = if continuing { raw.trim_start_matches([' ', '\t', '\x0c']) } else { raw };
        if !continuing {
            let start = line.trim_start_matches([' ', '\t', '\x0c']);
            if start.is_empty() || start.starts_with('#') || start.starts_with('!') {
                continue;
            }
        }
        let trailing = line.chars().rev().take_while(|c| *c == '\\').count();
        if trailing % 2 == 1 {
            current.push_str(&line[..line.len() - 1]);
            continuing = true;
        } else {
            current.push_str(line);
            lines.push(std::mem::take(&mut current));
            continuing = false;
        }
    }
    if continuing {
        lines.push(current);
    }
    lines
}

fn unescape(text: &str) -> Result<String, String> {
    let mut out = String::with_capacity(text.len());
    let mut chars = text.chars();
    while let Some(c) = chars.next() {
        if c != '\\' {
            out.push(c);
            continue;
        }
        match chars.next() {
            Some('t') => out.push('\t'),
            Some('n') => out.push('\n'),
            Some('r') => out.push('\r'),
            Some('f') => out.push('\x0c'),
            Some('u') => {
                let hex: String = chars.by_ref().take(4).collect();
                let code = u32::from_str_radix(&hex, 16).map_err(|_| format!("a malformed \\u{hex}"))?;
                out.push(char::from_u32(code).ok_or_else(|| format!("a malformed \\u{hex}"))?);
            }
            Some(other) => out.push(other),
            None => {}
        }
    }
    Ok(out)
}

/// The pairs in the file's order; an error for a malformed escape.
pub fn parse(text: &str) -> Result<Vec<(String, String)>, String> {
    let mut pairs = Vec::new();
    for line in logical_lines(text) {
        let line = line.trim_start_matches([' ', '\t', '\x0c']);
        let chars: Vec<char> = line.chars().collect();
        let mut end = 0;
        while end < chars.len() {
            match chars[end] {
                '\\' => end += 2,
                '=' | ':' | ' ' | '\t' | '\x0c' => break,
                _ => end += 1,
            }
        }
        let end = end.min(chars.len());
        let key: String = chars[..end].iter().collect();
        let mut start = end;
        while start < chars.len() && matches!(chars[start], ' ' | '\t' | '\x0c') {
            start += 1;
        }
        if start < chars.len() && matches!(chars[start], '=' | ':') {
            start += 1;
            while start < chars.len() && matches!(chars[start], ' ' | '\t' | '\x0c') {
                start += 1;
            }
        }
        let value: String = chars[start..].iter().collect();
        pairs.push((unescape(&key)?, unescape(&value)?));
    }
    Ok(pairs)
}

fn escape(text: &str, key: bool) -> String {
    let mut out = String::with_capacity(text.len());
    for (i, c) in text.chars().enumerate() {
        match c {
            '\\' => out.push_str("\\\\"),
            '=' => out.push_str("\\="),
            ':' => out.push_str("\\:"),
            '\n' => out.push_str("\\n"),
            '\r' => out.push_str("\\r"),
            '\t' => out.push_str("\\t"),
            ' ' if key || i == 0 => out.push_str("\\ "),
            '#' | '!' if key && i == 0 => {
                out.push('\\');
                out.push(c);
            }
            _ => out.push(c),
        }
    }
    out
}

/// The pairs as lines, `key=value`.
pub fn format(pairs: &[(String, String)]) -> String {
    let mut text = String::new();
    for (key, value) in pairs {
        text.push_str(&escape(key, true));
        text.push('=');
        text.push_str(&escape(value, false));
        text.push('\n');
    }
    text
}

/// Writes the text to a temporary file beside `path`, then renames it over `path`.
pub fn write_atomic(path: &Path, text: &str) -> std::io::Result<()> {
    let folder = path.parent().unwrap_or(Path::new("."));
    std::fs::create_dir_all(folder)?;
    let name = path.file_name().map(|n| n.to_string_lossy().to_string()).unwrap_or_default();
    let temporary = folder.join(format!(".{name}.{}.tmp", std::process::id()));
    {
        let mut file = std::fs::File::create(&temporary)?;
        file.write_all(text.as_bytes())?;
        file.sync_all()?;
    }
    std::fs::rename(&temporary, path).inspect_err(|_| {
        let _ = std::fs::remove_file(&temporary);
    })
}
