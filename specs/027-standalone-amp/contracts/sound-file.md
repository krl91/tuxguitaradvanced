# Contract: the sound file (`.guitarfx`)

This format is shared by GuitarFX (the application and the plugin) and by TuxGuitarAdvanced's *Amp and effects* window (FR-023, SC-011).

## Format

- UTF-8 text, one `key=value` per line, with `\n` or `\r\n` line ends.
- Lines starting with `#` are comments. Unknown keys are ignored.
- The file extension is `.guitarfx`.

```properties
# GuitarFX sound
format=1
name=Lead with echo
chain=gate:on,-60,150|compressor:on,5,10,5|amp:highgain,7,5,6,6,5|cab:on|echo:on,380,3,5,30|reverb:on,5,5,25
```

| Key | Required | Rule |
|---|---|---|
| `format` | yes | an integer. This spec writes `1`. A reader of format 1 refuses any other value |
| `name` | yes | 1 to 40 characters once trimmed, no line break; escaped as in Java `Properties` for `\`, `=` and `:` |
| `chain` | yes | the text of `ChainSettings.toText()` (spec 022 research R6). Numbers are plain decimals with no trailing zeros (`BigDecimal.stripTrailingZeros().toPlainString()`) |

## Grammar of `chain`

```text
chain    = [pedals "|"] amp "|" cab ["|" pedals]
pedals   = pedal *("|" pedal)
pedal    = kind ":" ("on" | "off") *("," number)     ; exactly the kind's number of knobs, in EffectKind order
amp      = "amp:" ("clean" | "crunch" | "highgain") 5("," number)   ; gain, bass, middle, treble, volume
cab      = "cab:" ("on" | "off")
kind     = "gate" | "compressor" | "overdrive" | "distortion" | "chorus" | "echo" | "reverb"
```

- Each kind appears at most once.
- `gate`, `compressor`, `overdrive` and `distortion` come before `amp`; `chorus`, `echo` and `reverb` come after `cab`.
- A number out of the knob's range is clamped when read.

## Reading

| Case | Result |
|---|---|
| a valid file | the sound, with its knobs clamped to their ranges |
| `format` missing, not an integer, or greater than 1 | refused with the message "made by a newer version" (a greater number) or "not a sound file"; nothing changes |
| `name` or `chain` missing, or `chain` malformed | refused, "not a sound file"; nothing changes |
| the name of an existing own sound | the guitarist chooses: replace, or keep both (the import is named "<name> 2") |
| a ready-made sound's name | imported as "<name> (imported)" |

## Writing

- The keys are written in the order `format`, `name`, `chain`, after the comment line.
- The file is written to a temporary file in the same folder, then renamed.

## Both programs

- GuitarFX: `guitarfx-settings/src/sound_file.rs`.
- TuxGuitarAdvanced: `app.tuxguitar.audio.engine.monitor.SoundFile`, using `ChainSettings.parse` and `toText`.
- `SoundFileTest` (Java) and `sound_file.rs`'s tests read the same example files, kept in `guitarfx/guitarfx-settings/tests/sounds/`: one valid file, one with format 2, one with a malformed chain, one with every pedal.
- The round trip of SC-011 runs each program's output through the other's reader: Java writes and Rust reads in `cargo test`, and Rust writes and Java reads in the Java test, through the files of `target/guitarfx-reference/`.
