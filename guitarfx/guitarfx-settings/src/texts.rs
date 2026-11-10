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
    ("dialog.ok", "OK"),
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
