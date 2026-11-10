//! The chain's comparison with Java, sound by sound (spec 027, T037): the same chain runs as `java_reference.rs` (the four ready-made sounds
//! and the changes from one to the next), grouped by sound and rate for `specs/027-standalone-amp/benchmark/comparison.md`. Skipped without
//! `GUITARFX_REFERENCE_DIR`; it holds the same −90 dBFS limit.

use guitarfx_dsp::chain::Chain;
use guitarfx_dsp::chain_text;
use guitarfx_dsp::params::Params;
use std::collections::BTreeMap;
use std::path::PathBuf;

fn read(path: &PathBuf) -> Vec<f32> {
    let bytes = std::fs::read(path).unwrap_or_else(|e| panic!("{}: {e}", path.display()));
    bytes.chunks_exact(4).map(|b| f32::from_le_bytes([b[0], b[1], b[2], b[3]])).collect()
}

#[test]
fn each_ready_made_sound_gives_the_java_output() {
    let Some(folder) = std::env::var("GUITARFX_REFERENCE_DIR")
        .ok()
        .filter(|v| !v.trim().is_empty())
        .map(PathBuf::from)
    else {
        println!("skipped: no Java references");
        return;
    };
    let text = std::fs::read_to_string(folder.join("index.properties")).expect("the index");
    let index: BTreeMap<&str, &str> = text.lines().filter(|l| !l.starts_with('#')).filter_map(|l| l.split_once('=')).collect();
    let block: usize = index["block"].parse().unwrap();
    let mut worst: BTreeMap<(String, String), f64> = BTreeMap::new();
    for (key, value) in &index {
        let Some(id) = key.strip_prefix("run.").and_then(|r| r.strip_suffix(".element")) else {
            continue;
        };
        if *value != "chain" {
            continue;
        }
        let field = |name: &str| index[format!("run.{id}.{name}").as_str()];
        let rate: f32 = field("rate").parse().unwrap();
        let change_at: usize = field("change-at").parse().unwrap();
        let (first, second) = match field("knobs").split_once('>') {
            Some((a, b)) => (a, Some(b)),
            None => (field("knobs"), None),
        };
        let mut chain = Chain::new(rate, block, &Params::defaults());
        chain.apply(Some(&chain_text::parse(first).unwrap()));
        let second = second.map(|s| chain_text::parse(s).unwrap());
        let mut samples = read(&folder.join(field("input")));
        let expected = read(&folder.join(field("output")));
        let mut offset = 0;
        while offset < samples.len() {
            if offset == change_at {
                if let Some(second) = &second {
                    chain.apply(Some(second));
                }
            }
            let n = block.min(samples.len() - offset);
            chain.process(&mut samples[offset..offset + n]);
            offset += n;
        }
        let difference = samples
            .iter()
            .zip(&expected)
            .map(|(a, e)| (f64::from(*a) - f64::from(*e)).abs())
            .fold(0.0, f64::max);
        let entry = worst.entry((field("variant").to_string(), field("rate").to_string())).or_insert(0.0);
        *entry = entry.max(difference);
    }
    assert!(!worst.is_empty(), "no chain run in the index");
    println!("largest difference per sound and rate (dBFS; -inf is identical):");
    for ((variant, rate), difference) in &worst {
        println!("  {variant} @ {rate}: {:.1}", 20.0 * difference.log10());
        assert!(*difference < 10f64.powf(-90.0 / 20.0), "{variant} @ {rate}");
    }
}
