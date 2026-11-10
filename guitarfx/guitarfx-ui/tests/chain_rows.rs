//! The chain's rows on the UI's model, without rendering (spec 027, T034, FR-005): adding puts a pedal at the end of its section, a kind
//! already in the chain is not offered, moves stay in their section, and removing, switching and turning knobs give the expected chain.

use guitarfx_dsp::knobs::{Character, Kind, Section};
use guitarfx_dsp::ready_made;
use guitarfx_dsp::settings::{AmpSettings, ChainParams, PedalSettings};
use guitarfx_ui::chain_rows as rows;

fn chain(before: &[Kind], after: &[Kind]) -> ChainParams {
    let of = |kinds: &[Kind]| kinds.iter().map(|k| PedalSettings::defaults(*k)).collect::<Vec<_>>();
    ChainParams::new(&of(before), AmpSettings::default(), &of(after)).unwrap()
}

#[test]
fn adding_puts_the_pedal_at_the_end_of_its_section() {
    let c = chain(&[Kind::Gate], &[Kind::Reverb]);
    let c = rows::add(&c, Kind::Overdrive);
    assert_eq!(c.before(), &[Kind::Gate, Kind::Overdrive]);
    let c = rows::add(&c, Kind::Chorus);
    assert_eq!(c.after(), &[Kind::Reverb, Kind::Chorus]);
    assert_eq!(c.pedal(Kind::Chorus), Some(&PedalSettings::defaults(Kind::Chorus)), "at its defaults, on");
}

#[test]
fn a_kind_already_in_the_chain_is_not_offered() {
    let c = chain(&[Kind::Gate, Kind::Distortion], &[Kind::Echo]);
    assert_eq!(rows::addable(&c, Section::Before), vec![Kind::Compressor, Kind::Overdrive]);
    assert_eq!(rows::addable(&c, Section::After), vec![Kind::Chorus, Kind::Reverb]);
    assert_eq!(rows::add(&c, Kind::Gate), c, "adding a kind already there changes nothing");
}

#[test]
fn moves_stay_in_the_section() {
    let c = chain(&[Kind::Gate, Kind::Compressor, Kind::Overdrive], &[Kind::Chorus, Kind::Echo]);
    let up = rows::move_up(&c, Kind::Compressor);
    assert_eq!(up.before(), &[Kind::Compressor, Kind::Gate, Kind::Overdrive]);
    assert_eq!(rows::move_up(&up, Kind::Compressor), up, "the first stays first");
    let down = rows::move_down(&c, Kind::Overdrive);
    assert_eq!(down, c, "the last before the amp stays before the amp");
    assert_eq!(rows::move_up(&c, Kind::Chorus), c, "the first after the amp stays after the amp");
    assert_eq!(rows::move_down(&c, Kind::Chorus).after(), &[Kind::Echo, Kind::Chorus]);
    assert!(!rows::can_move_up(&c, Kind::Gate) && rows::can_move_down(&c, Kind::Gate));
    assert!(rows::can_move_up(&c, Kind::Echo) && !rows::can_move_down(&c, Kind::Echo));
}

#[test]
fn removing_and_switching_give_the_expected_chain() {
    let c = ready_made::chain("Clean echo").unwrap();
    let kind = c.after()[0];
    let removed = rows::remove(&c, kind);
    assert!(removed.pedal(kind).is_none());
    assert_eq!(removed.after().len(), c.after().len() - 1);
    assert_eq!(removed.amp, c.amp);
    let off = rows::switch(&c, kind, false);
    assert!(!off.pedal(kind).unwrap().enabled);
    assert_eq!(off.after(), c.after(), "switched off, it keeps its place");
    assert_eq!(rows::switch(&off, kind, true), c);
}

#[test]
fn knobs_are_clamped_to_their_range() {
    let c = chain(&[Kind::Gate], &[]);
    let c = rows::set_knob(&c, Kind::Gate, 0, -500.0);
    assert_eq!(c.pedal(Kind::Gate).unwrap().value(0), Kind::Gate.knobs()[0].min);
    let c = rows::set_knob(&c, Kind::Gate, 1, 300.0);
    assert_eq!(c.pedal(Kind::Gate).unwrap().value(1), 300.0);
}

#[test]
fn the_amp_is_always_there_and_changes_in_place() {
    let c = chain(&[], &[]);
    let c = rows::set_character(&c, Character::HighGain);
    assert_eq!(c.amp.character, Character::HighGain);
    let c = rows::set_amp_knob(&c, 0, 12.0);
    assert_eq!(c.amp.gain, 10.0, "clamped");
    let c = rows::set_cabinet(&c, false);
    assert!(!c.amp.cabinet);
}
