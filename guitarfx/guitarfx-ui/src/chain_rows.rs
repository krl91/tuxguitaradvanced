//! The chain's three tabs (contracts/application.md, FR-005): a row per pedal with its switch, its knobs, ↑, ↓ and ✕, and *+ Add a pedal*;
//! the amp's tab with its character, its five knobs and the cabinet. The model's changes are plain functions on [`ChainParams`]: each kind
//! at most once and only in its section, moves within the section, the amp always there.

use crate::knobs;
use crate::window::Tab;
use egui::Ui;
use guitarfx_dsp::knobs::{Character, Kind, Section, AMP_KNOBS, AMP_MAX, AMP_MIN};
use guitarfx_dsp::settings::{ChainParams, PedalSettings};
use guitarfx_settings::texts::Texts;

fn pedals(chain: &ChainParams, section: Section) -> Vec<PedalSettings> {
    match section {
        Section::Before => chain.before_pedals().copied().collect(),
        Section::After => chain.after_pedals().copied().collect(),
    }
}

fn rebuilt(chain: &ChainParams, section: Section, list: &[PedalSettings]) -> ChainParams {
    let (before, after) = match section {
        Section::Before => (list.to_vec(), pedals(chain, Section::After)),
        Section::After => (pedals(chain, Section::Before), list.to_vec()),
    };
    ChainParams::new(&before, chain.amp, &after).unwrap_or(*chain)
}

/// The kinds of that section not in the chain yet, in their usual order.
pub fn addable(chain: &ChainParams, section: Section) -> Vec<Kind> {
    Kind::ALL
        .iter()
        .copied()
        .filter(|k| k.section() == section && chain.pedal(*k).is_none())
        .collect()
}

/// The pedal at its defaults, at the end of its section; the chain unchanged when it is there already.
pub fn add(chain: &ChainParams, kind: Kind) -> ChainParams {
    if chain.pedal(kind).is_some() {
        return *chain;
    }
    let mut list = pedals(chain, kind.section());
    list.push(PedalSettings::defaults(kind));
    rebuilt(chain, kind.section(), &list)
}

pub fn remove(chain: &ChainParams, kind: Kind) -> ChainParams {
    let list: Vec<PedalSettings> = pedals(chain, kind.section()).into_iter().filter(|p| p.kind != kind).collect();
    rebuilt(chain, kind.section(), &list)
}

fn position(chain: &ChainParams, kind: Kind) -> Option<(usize, usize)> {
    let list = pedals(chain, kind.section());
    list.iter().position(|p| p.kind == kind).map(|i| (i, list.len()))
}

pub fn can_move_up(chain: &ChainParams, kind: Kind) -> bool {
    position(chain, kind).is_some_and(|(i, _)| i > 0)
}

pub fn can_move_down(chain: &ChainParams, kind: Kind) -> bool {
    position(chain, kind).is_some_and(|(i, n)| i + 1 < n)
}

fn swap(chain: &ChainParams, kind: Kind, earlier: bool) -> ChainParams {
    let Some((i, n)) = position(chain, kind) else {
        return *chain;
    };
    let j = if earlier { i.checked_sub(1) } else { Some(i + 1).filter(|j| *j < n) };
    let Some(j) = j else {
        return *chain;
    };
    let mut list = pedals(chain, kind.section());
    list.swap(i, j);
    rebuilt(chain, kind.section(), &list)
}

/// One place earlier, within the section.
pub fn move_up(chain: &ChainParams, kind: Kind) -> ChainParams {
    swap(chain, kind, true)
}

/// One place later, within the section.
pub fn move_down(chain: &ChainParams, kind: Kind) -> ChainParams {
    swap(chain, kind, false)
}

pub fn switch(chain: &ChainParams, kind: Kind, on: bool) -> ChainParams {
    match chain.pedal(kind) {
        Some(pedal) => chain.with_pedal(pedal.with_enabled(on)),
        None => *chain,
    }
}

/// The knob at that position, clamped to its range.
pub fn set_knob(chain: &ChainParams, kind: Kind, index: usize, value: f64) -> ChainParams {
    match (chain.pedal(kind), kind.knobs().get(index)) {
        (Some(pedal), Some(knob)) => chain.with_pedal(pedal.with(index, knob.clamp(value))),
        _ => *chain,
    }
}

pub fn set_character(chain: &ChainParams, character: Character) -> ChainParams {
    let mut amp = chain.amp;
    amp.character = character;
    chain.with_amp(amp)
}

/// The amp's knob at that position of [`AMP_KNOBS`], clamped.
pub fn set_amp_knob(chain: &ChainParams, index: usize, value: f64) -> ChainParams {
    chain.with_amp(chain.amp.with_knob(index, value.clamp(AMP_MIN, AMP_MAX)))
}

pub fn set_cabinet(chain: &ChainParams, on: bool) -> ChainParams {
    let mut amp = chain.amp;
    amp.cabinet = on;
    chain.with_amp(amp)
}

/// The tab's rows; the changed chain when the guitarist changed something.
pub fn show(ui: &mut Ui, chain: &ChainParams, tab: Tab, texts: &Texts) -> Option<ChainParams> {
    match tab {
        Tab::Before => section(ui, chain, Section::Before, texts),
        Tab::After => section(ui, chain, Section::After, texts),
        Tab::Amp => amp(ui, chain, texts),
    }
}

fn section(ui: &mut Ui, chain: &ChainParams, section: Section, texts: &Texts) -> Option<ChainParams> {
    let mut changed = None;
    let list = pedals(chain, section);
    if list.is_empty() {
        ui.label(egui::RichText::new(texts.get("chain.empty")).weak());
    }
    for pedal in &list {
        let kind = pedal.kind;
        ui.horizontal_wrapped(|ui| {
            let mut on = pedal.enabled;
            let name = texts.get(&format!("pedal.{}", kind.text_name())).to_string();
            if ui
                .add_sized([knobs::NAME_WIDTH, ui.spacing().interact_size.y], egui::Checkbox::new(&mut on, name))
                .on_hover_text(texts.get("chain.switch.tip"))
                .changed()
            {
                changed = Some(switch(chain, kind, on));
            }
            for (index, knob) in kind.knobs().iter().enumerate() {
                let label = texts.get(&format!("knob.{}.{}", kind.text_name(), knob.key)).to_string();
                let what = texts.get(&format!("knob.{}.{}.tip", kind.text_name(), knob.key)).to_string();
                if let Some(value) = knobs::slider(ui, &label, &what, pedal.value(index), knob.min, knob.max, knob.unit, knob.default, texts) {
                    changed = Some(set_knob(chain, kind, index, value));
                }
            }
            if ui
                .add_enabled(can_move_up(chain, kind), egui::Button::new("↑"))
                .on_hover_text(texts.get("chain.up.tip"))
                .clicked()
            {
                changed = Some(move_up(chain, kind));
            }
            if ui
                .add_enabled(can_move_down(chain, kind), egui::Button::new("↓"))
                .on_hover_text(texts.get("chain.down.tip"))
                .clicked()
            {
                changed = Some(move_down(chain, kind));
            }
            if ui.button("✕").on_hover_text(texts.get("chain.remove.tip")).clicked() {
                changed = Some(remove(chain, kind));
            }
        });
        ui.separator();
    }
    let missing = addable(chain, section);
    if !missing.is_empty() {
        egui::ComboBox::from_id_salt(("add", section == Section::Before))
            .selected_text(texts.get("chain.add"))
            .show_ui(ui, |ui| {
                for kind in missing {
                    if ui.selectable_label(false, texts.get(&format!("pedal.{}", kind.text_name()))).clicked() {
                        changed = Some(add(chain, kind));
                    }
                }
            })
            .response
            .on_hover_text(texts.get("chain.add.tip"));
    }
    changed
}

fn amp(ui: &mut Ui, chain: &ChainParams, texts: &Texts) -> Option<ChainParams> {
    let mut changed = None;
    ui.horizontal_wrapped(|ui| {
        ui.label(texts.get("amp.character")).on_hover_text(texts.get("amp.character.tip"));
        for character in Character::ALL {
            let name = texts.get(&format!("amp.character.{}", character.text_name())).to_string();
            if ui
                .selectable_label(chain.amp.character == character, name)
                .on_hover_text(texts.get("amp.character.tip"))
                .clicked()
            {
                changed = Some(set_character(chain, character));
            }
        }
    });
    ui.separator();
    ui.horizontal_wrapped(|ui| {
        for (index, (key, value)) in AMP_KNOBS.iter().zip(chain.amp.knobs()).enumerate() {
            let label = texts.get(&format!("knob.amp.{key}")).to_string();
            let what = texts.get(&format!("knob.amp.{key}.tip")).to_string();
            let default = guitarfx_dsp::knobs::AMP_DEFAULT;
            if let Some(value) = knobs::slider(ui, &label, &what, value, AMP_MIN, AMP_MAX, "", default, texts) {
                changed = Some(set_amp_knob(chain, index, value));
            }
        }
    });
    ui.separator();
    let mut cabinet = chain.amp.cabinet;
    if ui
        .checkbox(&mut cabinet, texts.get("amp.cabinet"))
        .on_hover_text(texts.get("amp.cabinet.tip"))
        .changed()
    {
        changed = Some(set_cabinet(chain, cabinet));
    }
    changed
}
