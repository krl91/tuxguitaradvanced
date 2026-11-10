//! GuitarFX's texts (FR-018): every text of the windows and messages by key. English only for now; French and Spanish come with the
//! language files (T046). A key with no text shows the key itself, so a missing text is seen, not hidden.

use std::collections::HashMap;

/// English, the reference language. The messages are those of contracts/application.md; the lightening steps say what
/// TuxGuitarAdvanced's amp window says (`practice.amp.lighter.*`).
const ENGLISH: &[(&str, &str)] = &[
    ("app.title", "GuitarFX"),
    ("devices.input", "Input"),
    ("devices.input.tip", "The device your guitar is plugged into."),
    ("devices.channel", "Channel"),
    ("devices.channel.tip", "The input's channel your guitar is on (1 for the first)."),
    ("devices.output", "Output"),
    ("devices.output.tip", "Where you hear the sound: headphones are advised."),
    ("devices.refresh", "⟳"),
    ("devices.refresh.tip", "Look for the devices again, after plugging one in."),
    ("devices.choose", "Choose…"),
    ("devices.missing", "{name} (missing)"),
    ("sound.on", "Sound on"),
    (
        "sound.on.tip",
        "Plays your guitar through the amp. Off at every start; turning it on fades the sound in.",
    ),
    ("volume", "Volume"),
    ("volume.tip", "The sound's volume, from 0 to 100 %. Default 70 %."),
    ("meter", "Input"),
    (
        "meter.tip",
        "Your guitar's level at the input. \"clip\" means it is too loud for the input.",
    ),
    ("meter.clip", "clip"),
    ("delay", "Delay ≈ {ms} ms (estimated)"),
    ("delay.off", "Delay: estimated once the sound is on"),
    (
        "delay.tip",
        "The devices' blocks and declared latencies, added up. The delay you hear can be a little longer; guitarfx-probe measures it.",
    ),
    ("bluetooth", "Bluetooth headphones add their own delay, not counted here."),
    ("tab.before", "Before the amp"),
    ("tab.amp", "Amp"),
    ("tab.after", "After the amp"),
    ("chain.add", "+ Add a pedal"),
    (
        "chain.add.tip",
        "Adds a pedal at the end of this section. Each pedal can be in the chain once.",
    ),
    ("chain.up.tip", "Moves the pedal one place earlier in the chain."),
    ("chain.down.tip", "Moves the pedal one place later in the chain."),
    ("chain.remove.tip", "Takes the pedal out of the chain."),
    (
        "chain.switch.tip",
        "Turns the pedal on or off; off, the sound passes through it unchanged.",
    ),
    ("chain.empty", "No pedal here yet."),
    ("knob.tip", "{what} From {min} to {max}{unit}; default {default}{unit}."),
    ("pedal.gate", "Noise gate"),
    ("pedal.compressor", "Compressor"),
    ("pedal.overdrive", "Overdrive"),
    ("pedal.distortion", "Distortion"),
    ("pedal.chorus", "Chorus"),
    ("pedal.echo", "Echo"),
    ("pedal.reverb", "Reverb"),
    ("knob.gate.threshold", "Threshold"),
    (
        "knob.gate.threshold.tip",
        "The level under which the gate closes and silences the hiss between notes.",
    ),
    ("knob.gate.release", "Release"),
    (
        "knob.gate.release.tip",
        "How long the gate takes to close once the note falls under the threshold.",
    ),
    ("knob.compressor.sustain", "Sustain"),
    (
        "knob.compressor.sustain.tip",
        "How much the compressor evens out loud and soft notes; more makes notes last longer.",
    ),
    ("knob.compressor.attack", "Attack"),
    (
        "knob.compressor.attack.tip",
        "How fast the compressor reacts to a note; slower lets the pick's attack through.",
    ),
    ("knob.compressor.level", "Level"),
    ("knob.compressor.level.tip", "The pedal's output level; 5 keeps the level unchanged."),
    ("knob.overdrive.drive", "Drive"),
    ("knob.overdrive.drive.tip", "How hard the signal is pushed into a soft, warm saturation."),
    ("knob.overdrive.tone", "Tone"),
    ("knob.overdrive.tone.tip", "Darker to the left, brighter to the right."),
    ("knob.overdrive.level", "Level"),
    ("knob.overdrive.level.tip", "The pedal's output level; 5 keeps the level unchanged."),
    ("knob.distortion.gain", "Gain"),
    (
        "knob.distortion.gain.tip",
        "How much hard clipping: more gives a thicker, more saturated sound.",
    ),
    ("knob.distortion.tone", "Tone"),
    ("knob.distortion.tone.tip", "Darker to the left, brighter to the right."),
    ("knob.distortion.level", "Level"),
    ("knob.distortion.level.tip", "The pedal's output level; 5 keeps the level unchanged."),
    ("knob.chorus.rate", "Rate"),
    ("knob.chorus.rate.tip", "How fast the chorus sways."),
    ("knob.chorus.depth", "Depth"),
    ("knob.chorus.depth.tip", "How wide the sway is."),
    ("knob.chorus.mix", "Mix"),
    ("knob.chorus.mix.tip", "The share of the chorus in the sound; 0 is the dry sound."),
    ("knob.echo.time", "Time"),
    ("knob.echo.time.tip", "The time between the note and its repeat."),
    ("knob.echo.repeats", "Repeats"),
    ("knob.echo.repeats.tip", "How many times the repeat comes back before it fades."),
    ("knob.echo.tone", "Tone"),
    ("knob.echo.tone.tip", "Darker repeats to the left, brighter to the right."),
    ("knob.echo.mix", "Mix"),
    ("knob.echo.mix.tip", "The share of the repeats in the sound; 0 is the dry sound."),
    ("knob.reverb.size", "Size"),
    ("knob.reverb.size.tip", "The size of the room: larger rooms ring longer."),
    ("knob.reverb.damping", "Damping"),
    ("knob.reverb.damping.tip", "How quickly the room's high notes die away."),
    ("knob.reverb.mix", "Mix"),
    ("knob.reverb.mix.tip", "The share of the room in the sound; 0 is the dry sound."),
    ("amp.character", "Character"),
    (
        "amp.character.tip",
        "The amp's voice: clean, crunch (lightly broken up) or high gain (heavily saturated). Default clean.",
    ),
    ("amp.character.clean", "Clean"),
    ("amp.character.crunch", "Crunch"),
    ("amp.character.highgain", "High gain"),
    ("knob.amp.gain", "Gain"),
    ("knob.amp.gain.tip", "How hard the amp is driven."),
    ("knob.amp.bass", "Bass"),
    ("knob.amp.bass.tip", "The low frequencies."),
    ("knob.amp.middle", "Middle"),
    ("knob.amp.middle.tip", "The middle frequencies, where the guitar sits."),
    ("knob.amp.treble", "Treble"),
    ("knob.amp.treble.tip", "The high frequencies."),
    ("knob.amp.volume", "Volume"),
    ("knob.amp.volume.tip", "The amp's output level."),
    ("amp.cabinet", "Cabinet"),
    (
        "amp.cabinet.tip",
        "The speaker cabinet's colour after the amp. Off, the amp sounds bright and raw, as through a line output. Default on.",
    ),
    ("dialog.ok", "OK"),
    ("dialog.cancel", "Cancel"),
    ("sound.list", "Sound"),
    (
        "sound.list.tip",
        "The sound in use: the ready-made sounds first, then yours. \"(changed)\" means it differs from the saved sound.",
    ),
    ("sound.changed", "(changed)"),
    ("sound.save", "Save…"),
    ("sound.save.tip", "Keeps the chain as one of your sounds, under a name you choose."),
    ("sound.save.title", "Save the sound"),
    ("sound.more", "⋯"),
    ("sound.more.tip", "More: rename, delete, restore, export or import a sound."),
    ("sound.rename", "Rename…"),
    ("sound.rename.tip", "Gives your sound another name."),
    ("sound.rename.title", "Rename the sound"),
    ("sound.delete", "Delete"),
    ("sound.delete.tip", "Deletes your sound. The chain in use stays as it is."),
    ("sound.restore", "Restore"),
    ("sound.restore.tip", "Brings the ready-made sound back as it was shipped."),
    ("sound.export", "Export…"),
    (
        "sound.export.tip",
        "Saves the sound in use to a .guitarfx file, which GuitarFX and TuxGuitarAdvanced can import.",
    ),
    ("sound.import", "Import…"),
    (
        "sound.import.tip",
        "Adds the sound of a .guitarfx file, made by GuitarFX or TuxGuitarAdvanced, to your sounds.",
    ),
    ("sound.import.clash", "You already have a sound named \"{name}\"."),
    ("sound.import.replace", "Replace it"),
    ("sound.import.keep-both", "Keep both"),
    ("sound.name", "Name"),
    ("sound.name.tip", "At most 40 characters."),
    ("sound.name.empty", "Type a name."),
    ("sound.name.too-long", "The name is too long: at most 40 characters."),
    ("sound.name.ready-made", "A ready-made sound has this name: choose another one."),
    ("sound.name.taken", "You already have a sound with this name: choose another one."),
    ("sound.file.filter", "GuitarFX sound"),
    (
        "message.feedback",
        "Howling detected: the sound was turned off. Use headphones, or move away from the speakers.",
    ),
    (
        "message.builtin",
        "With the built-in microphone, the speakers can howl. Headphones are advised.",
    ),
    ("message.builtin.on", "Turn on anyway"),
    ("message.builtin.cancel", "Cancel"),
    (
        "message.permission",
        "GuitarFX may not use the microphone. Allow it in the system's privacy settings, then turn the sound on again.",
    ),
    ("message.lost.input", "The input device is gone. Choose a device to go on."),
    ("message.lost.output", "The output device is gone. Choose a device to go on."),
    (
        "message.missing.input",
        "The input device {name} is not connected. Connect it, or choose another one.",
    ),
    (
        "message.missing.output",
        "The output device {name} is not connected. Connect it, or choose another one.",
    ),
    ("message.choose", "Choose an input and an output, then turn the sound on."),
    ("message.unsupported", "This device's format is not supported: choose another device."),
    ("message.failed", "The audio device failed. Choose a device to go on."),
    (
        "message.lighter.1",
        "Your computer cannot keep up: the reverb was turned off to lighten the sound.",
    ),
    (
        "message.lighter.2",
        "Your computer cannot keep up: the chorus was turned off to lighten the sound.",
    ),
    (
        "message.lighter.3",
        "Your computer cannot keep up: the amp's detail was lowered to lighten the sound.",
    ),
    ("message.lighter.4", "Your computer cannot keep up: the amp's detail was lowered again."),
    (
        "message.lighter.5",
        "Your computer cannot keep up: only the amp and the cabinet are kept, the pedals are bypassed.",
    ),
    (
        "message.overload",
        "Your computer cannot keep up even with the amp alone: the sound was turned off. Close other programs, then turn the sound on again.",
    ),
    (
        "message.clipping",
        "The input clips: lower the input gain of your interface or of the system.",
    ),
    ("message.already-open", "GuitarFX is already open."),
    (
        "message.sound-file.newer",
        "This sound file was made by a newer version: it cannot be imported. Nothing was changed.",
    ),
    (
        "message.sound-file.not",
        "This file is not a sound file, or it is damaged: it cannot be imported. Nothing was changed.",
    ),
    ("message.sound.imported", "\"{name}\" was added to your sounds."),
    ("message.sound.exported", "The sound was saved to {file}."),
    ("message.sound.not-saved", "The sound could not be saved: {error}"),
    ("message.sounds.damaged", "The sounds file could not be read: a copy was kept as {file}."),
    (
        "message.sounds.newer",
        "The sounds file comes from a newer GuitarFX: your sounds are shown but not saved.",
    ),
    (
        "message.settings.newer",
        "The settings file comes from a newer GuitarFX: the defaults are used, and the file is not changed.",
    ),
    ("message.settings.newer.keep", "Keep the file"),
    ("message.settings.newer.replace", "Use the defaults and save over it"),
    (
        "message.settings.damaged",
        "The settings file was damaged: a copy was kept as {file}, and the values that could be read are used.",
    ),
];

#[derive(Clone, Debug)]
pub struct Texts {
    language: String,
    map: HashMap<String, String>,
}

impl Texts {
    pub fn english() -> Texts {
        Texts {
            language: "en".to_string(),
            map: ENGLISH.iter().map(|(k, v)| (k.to_string(), v.to_string())).collect(),
        }
    }

    /// Every language GuitarFX has, English first.
    pub fn languages() -> Vec<Texts> {
        vec![Texts::english()]
    }

    pub fn language(&self) -> &str {
        &self.language
    }

    /// Every key of the reference language.
    pub fn keys() -> impl Iterator<Item = &'static str> {
        ENGLISH.iter().map(|(k, _)| *k)
    }

    /// The text, or the key itself when it has none.
    pub fn get<'a>(&'a self, key: &'a str) -> &'a str {
        self.map.get(key).map(String::as_str).unwrap_or(key)
    }

    /// The text with each `{name}` replaced by its value.
    pub fn format(&self, key: &str, values: &[(&str, &str)]) -> String {
        let mut text = self.get(key).to_string();
        for (name, value) in values {
            text = text.replace(&format!("{{{name}}}"), value);
        }
        text
    }

    /// These texts with one replaced (a test's longest texts).
    pub fn with(mut self, key: &str, text: String) -> Texts {
        self.map.insert(key.to_string(), text);
        self
    }
}
