//! The four ready-made sounds of spec 022's `ReadyMadeChains.java`, as chain texts equal to Java's (spec 027, T019). They are the project's
//! own starting points.

use crate::chain_text;
use crate::settings::ChainParams;

/// Clean, Crunch, Lead and Clean echo, in that order: each one's name and chain text.
pub const ALL: &[(&str, &str)] = &[
    ("Clean", "gate:on,-70,150|amp:clean,3,5,5,6,6|cab:on|reverb:on,4,5,20"),
    ("Crunch", "gate:on,-60,150|amp:crunch,6,6,5,6,6|cab:on"),
    (
        "Lead",
        "gate:on,-55,150|compressor:on,4,10,5|amp:highgain,7,6,6,6,6|cab:on|echo:on,380,3,5,25",
    ),
    (
        "Clean echo",
        "compressor:on,5,10,5|amp:clean,3,5,5,6,6|cab:on|chorus:on,0.8,4,40|echo:on,450,4,4,35|reverb:on,4,5,20",
    ),
];

/// The ready-made chain of that name (case ignored), or none.
pub fn chain(name: &str) -> Option<ChainParams> {
    ALL.iter()
        .find(|(n, _)| n.eq_ignore_ascii_case(name))
        .and_then(|(_, text)| chain_text::parse(text))
}

/// The first ready-made sound, the one used when nothing else is known.
pub fn first() -> ChainParams {
    chain(ALL[0].0).expect("the ready-made texts are valid")
}
