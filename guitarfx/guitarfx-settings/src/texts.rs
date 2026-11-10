//! GuitarFX's texts (FR-018): every text of the windows and messages by key, in English, French and Spanish, from the language files
//! `guitarfx/lang/messages{,_fr,_es}.properties` built into the program. The language is the system's when GuitarFX has it, English
//! otherwise. A key with no text shows the key itself, so a missing text is seen, not hidden.

use crate::properties;
use std::collections::HashMap;
use std::sync::OnceLock;

/// The language files, English first: the reference whose keys every other file has.
const FILES: &[(&str, &str)] = &[
    ("en", include_str!("../../lang/messages.properties")),
    ("fr", include_str!("../../lang/messages_fr.properties")),
    ("es", include_str!("../../lang/messages_es.properties")),
];

/// A language's texts in the file's order, read once.
fn table(index: usize) -> &'static [(String, String)] {
    static TABLES: OnceLock<Vec<Vec<(String, String)>>> = OnceLock::new();
    &TABLES.get_or_init(|| {
        FILES
            .iter()
            .map(|(code, text)| properties::parse(text).unwrap_or_else(|e| panic!("the {code} language file: {e}")))
            .collect()
    })[index]
}

/// The language GuitarFX shows for a system locale such as `fr-FR` or `es_MX`: its first part when GuitarFX has it, else English.
pub fn language_of(locale: &str) -> &'static str {
    let first = locale.split(['-', '_', '.']).next().unwrap_or("").to_ascii_lowercase();
    FILES.iter().map(|(code, _)| *code).find(|code| *code == first).unwrap_or(FILES[0].0)
}

#[derive(Clone, Debug)]
pub struct Texts {
    language: String,
    map: HashMap<String, String>,
}

impl Texts {
    fn of(index: usize) -> Texts {
        Texts {
            language: FILES[index].0.to_string(),
            map: table(index).iter().cloned().collect(),
        }
    }

    pub fn english() -> Texts {
        Texts::of(0)
    }

    /// The texts of that language code, English when GuitarFX does not have it.
    pub fn for_language(code: &str) -> Texts {
        Texts::of(FILES.iter().position(|(c, _)| *c == language_of(code)).unwrap_or(0))
    }

    /// The texts of the system's language (`sys-locale`), English otherwise.
    pub fn system() -> Texts {
        Texts::for_language(&sys_locale::get_locale().unwrap_or_default())
    }

    /// Every language GuitarFX has, English first.
    pub fn languages() -> Vec<Texts> {
        (0..FILES.len()).map(Texts::of).collect()
    }

    /// The keys this language's file has.
    pub fn own_keys(&self) -> Vec<&str> {
        self.map.keys().map(String::as_str).collect()
    }

    pub fn language(&self) -> &str {
        &self.language
    }

    /// Every key of the reference language.
    pub fn keys() -> impl Iterator<Item = &'static str> {
        table(0).iter().map(|(k, _)| k.as_str())
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
