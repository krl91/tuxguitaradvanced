# Data Model: GuitarFX (spec 027)

The chain's entities keep the meaning, names and ranges of spec 022 (`EffectKind`, `Knob`, `PedalSettings`, `AmpSettings`, `ChainSettings` in `desktop/TuxGuitar-audio-engine/.../monitor/`). GuitarFX ports them; it does not redefine them.

## Knob (ported from `Knob`, `EffectKind`)

| Kind | Section | Knobs: key (min, max, unit, default) |
|---|---|---|
| gate | before | threshold (−90, −20, dB, −60), release (20, 1000, ms, 150) |
| compressor | before | sustain (0, 10, –, 5), attack (1, 100, ms, 10), level (0, 10, –, 5) |
| overdrive | before | drive (0, 10, –, 5), tone (0, 10, –, 5), level (0, 10, –, 5) |
| distortion | before | gain (0, 10, –, 6), tone (0, 10, –, 5), level (0, 10, –, 5) |
| chorus | after | rate (0.1, 5, Hz, 0.8), depth (0, 10, –, 5), mix (0, 100, %, 50) |
| echo | after | time (50, 1500, ms, 380), repeats (0, 10, –, 3), tone (0, 10, –, 5), mix (0, 100, %, 30) |
| reverb | after | size (0, 10, –, 5), damping (0, 10, –, 5), mix (0, 100, %, 25) |
| amp | the amp | character (`clean`, `crunch`, `highgain`; default clean), gain, bass, middle, treble, volume (0 to 10, default 5 each; `AmpSettings.defaults()`) |
| cab | the amp | on/off |

**Rules**:
- A value out of range is clamped, never an error.
- Each kind is in the chain at most once, and only in its section.
- The amp is always there.

## Chain

- **Fields**:
  - `before`: an ordered list of pedals;
  - `amp`: the character and five knobs;
  - `cabinet`: on/off;
  - `after`: an ordered list of pedals.
  - Each pedal: kind, enabled, knob values.
- **Text**: exactly `ChainSettings.toText()`, for example `gate:on,-60,150|amp:crunch,5,5,5,5,6|cab:on|echo:on,380,3,5,30`. Parsing follows `ChainSettings.parse`; a malformed text is refused whole.
- **At run time**: the 9 elements always exist (R10), and the chain at run time is `order[9]` + `enabled[9]` + the knob values. A change is a new `ChainParams`, posted through the mailbox.

## Sound

| Field | Rule |
|---|---|
| name | 1 to 40 characters, not blank, unique among the sounds; it cannot take a ready-made sound's name |
| chain | a Chain |
| ready-made | true for the four of `ReadyMadeChains`: Clean, Crunch, Lead, Clean echo |
| changed | ready-made only: true when its chain differs from the shipped one. *Restore* clears it |

**State**: a ready-made sound goes shipped → changed (a knob moved) → shipped (*Restore*). An own sound is saved → edited (shown as changed) → saved (*Save*) or dropped (another sound picked).

## Sound file (`.guitarfx`, contracts/sound-file.md)

- `format` (an integer, 1 in this spec), `name`, `chain`.
- **Refused whole** when `format` > 1, when a field is missing, or when the chain is malformed.
- **Import**: a name that already exists asks the guitarist before replacing it (US3, scenario 5). A ready-made name is imported as "<name> (imported)".

## Device choice

- `input` (the device's name), `channel` (1-based), `output` (the device's name), `block` (frames; an external parameter, default 128 as `MonitorEngine.BLOCK_FRAMES`).
- A device that is missing at start is shown as missing; nothing is opened and nothing is switched to another device (FR-013).

## Settings

| Key | Meaning | Default |
|---|---|---|
| `devices.input`, `devices.channel`, `devices.output` | the device choice | none, chosen at first start |
| `volume` | the output volume | 0.7, as `AmpSoundSettings.DEFAULT_GUITAR_VOLUME` (spec 022) |
| `sound.current` | the current sound's name | `Clean` |
| `chain.current` | the current chain (it may differ from the sound: changed) | the current sound's chain |
| `guitarfx.<category>.<name>` | every external parameter | its default |

- The sound is never stored as on (FR-009).
- **Version**: a `settings.format=1` line. A newer or damaged file is kept aside and the defaults are used (spec edge case).

## External parameter

| Field | Rule |
|---|---|
| key | `guitarfx.<category>.<name>`, unique |
| category | one of the *Advanced* window's categories (contracts/application.md), at most 8 rows each |
| unit | dB, dBFS, ms, s, Hz, frames, bins, times, % |
| default | equal to the Java constant it replaces (inventory.md). A test compares the two |
| min, max | min ≤ default ≤ max; a value out of bounds is clamped |
| description | a tooltip in three languages, with no figure in its text; the unit and the default are appended from the registry when it is shown (FR-018) |
| takes effect | the next time the sound is turned on (the application) or the plugin is loaded |

## Plugin state (contracts/plugin.md)

- `state.format=1` and `chain=<text>`, saved by the host with its project.
- The plugin's external parameters are read from `settings.properties` when it is loaded; they are not part of the project.

## Estimated delay

`input block + output block + declared input latency + declared output latency + resampler target fill`, in ms at the output's rate, shown with one decimal and the word "estimated".
