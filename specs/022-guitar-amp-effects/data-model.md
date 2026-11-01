# Data model: the guitar heard through an amp and effect pedals (spec 022)

`AE` = `desktop/TuxGuitar-audio-engine/src/main/java/app/tuxguitar/audio/engine`; `PR` = `desktop/TuxGuitar-practice/src/app/tuxguitar/app/tools/custom/practice`.

## Effect kinds and knobs (AE `monitor/`)

### `EffectKind` (enum)

| Kind | Section | Knobs (key: range, unit, default) |
|---|---|---|
| `GATE` | before | `threshold`: −90…−20 dB, −60; `release`: 20…1000 ms, 150 |
| `COMPRESSOR` | before | `sustain`: 0…10, 5; `attack`: 1…100 ms, 10; `level`: 0…10, 5 |
| `OVERDRIVE` | before | `drive`: 0…10, 5; `tone`: 0…10, 5; `level`: 0…10, 5 |
| `DISTORTION` | before | `gain`: 0…10, 6; `tone`: 0…10, 5; `level`: 0…10, 5 |
| `CHORUS` | after | `rate`: 0.1…5 Hz, 0.8; `depth`: 0…10, 5; `mix`: 0…100 %, 50 |
| `ECHO` | after | `time`: 50…1500 ms, 380; `repeats`: 0…10, 3; `tone`: 0…10, 5; `mix`: 0…100 %, 30 |
| `REVERB` | after | `size`: 0…10, 5; `damping`: 0…10, 5; `mix`: 0…100 %, 25 |

- Each kind appears at most once in a chain (FR-005).
- A pedal of the *before* section may be moved only within *before*; an *after* pedal only within *after*. The noise gate is a *before* pedal and is suggested first.
- A value outside its range is brought back into it (spec edge case).

### `AmpSettings`

- `character`: `CLEAN`, `CRUNCH` or `HIGH_GAIN`;
- `gain`, `bass`, `middle`, `treble`, `volume`: each from 0 to 10;
- `cabinet`: on or off.

## `PedalSettings`

`kind`, `enabled` (boolean) and `values` (a map from knob key to number). It is immutable; `with(key, value)` returns a copy clamped to the knob's range.

## `ChainSettings` (immutable snapshot)

- `before`: an ordered list of `PedalSettings`;
- `amp`: `AmpSettings`;
- `after`: an ordered list of `PedalSettings`.

Rules:
- **Validation:** kinds are unique across both lists, and each kind is in its own section.
- **`toText()` / `parse(String)`:** the compact text of research R6 (`gate:on,-60,150|amp:crunch,5,5,5,5,6|cab:on|echo:on,380,3,5,30`). Parsing malformed text returns `Optional.empty()`, so the caller falls back to the ready-made sound.
- **`equals` and `hashCode`:** by value.

## `Sound`

- `name`: non-empty, at most 40 characters, unique and case-insensitive;
- `chain`: a `ChainSettings`;
- `readyMade`: a boolean.

### Ready-made sounds (FR-008)

| Name | Chain |
|---|---|
| Clean | gate −70; amp clean 3/5/5/6/6; cabinet on; reverb 4/5/20 % |
| Crunch | gate −60; amp crunch 6/6/5/6/6; cabinet on |
| Lead | gate −55; compressor 4/10/5; amp high gain 7/6/6/6/6; cabinet on; echo 380 ms/3/5/25 % |
| Clean echo | compressor 5/10/5; amp clean 3/5/5/6/6; cabinet on; chorus 0.8/4/40 %; echo 450 ms/4/4/35 %; reverb 4/5/20 % |

The chains live in one place, AE `monitor/ReadyMadeChains`, used by the engine tests and by `Sound`. The values are a starting point, tuned at the owner's gate and by the silence test of FR-011. A ready-made sound can be changed and then restored (US3/AC3).

## `AmpSoundSettings` (PR `session/AmpSoundStore`)

| Field | Default | Key in `tuxguitar-practice-amp` |
|---|---|---|
| `enabled` | false | `enabled` |
| `output` | the system's default output | `output` (mixer name) |
| `guitarVolume` | 0.7 (from 0 to 1) | `guitar.volume` |
| `currentSound` | "Clean" | `sound.current` |
| `currentChain` (the chain in use, with the learner's unsaved changes) | the Clean chain | `chain.current` |
| `lastSound` | "Clean" | `sound.last` |
| own sounds | none | `sound.<n>.name`, `sound.<n>.chain` |
| changed ready-made sounds | none | `ready.<name>.chain` (removed by *Restore*) |
| sound per score | none | `score.<hash>` → sound name |
| window bounds | none (placed by R7) | `window.bounds` |

The score volume is not stored here: it is TuxGuitar's own `MidiPlayer` volume.

### `ScoreKey`

- `of(title, artist, fileName)` lower-cases and trims the title and the artist, and joins them with ` — `.
- When both are empty, it uses the file name.
- The key stored is a hex SHA-256 prefix of 16 characters, so no title is written in the settings.
- When there is no title, no artist and no file name (a new unsaved score), there is no key and the last sound is used.

## Runtime (AE `monitor/`)

### `MonitorState` (state transitions)

```
OFF ──turn on──▶ STARTING (lines open, 0.5 s fade in) ──▶ ON
ON ──turn off / practice window closed──▶ STOPPING (20 ms fade out) ──▶ OFF
ON ──howl found──▶ MUTED_FEEDBACK (fade out 20 ms; message) ──turn on again──▶ STARTING
ON ──output lost / format refused──▶ FAILED (message) ──output chosen again──▶ STARTING
ON ──over budget──▶ ON with a lighter chain (message) ──still over──▶ MUTED_OVERLOAD (message)
```

### Counters shown in the diagnostic

`droppedBlocks` (the output was late), `silentBlocks` (the input was late), `overBudgetBlocks`, `howls`, `clippedInputBlocks` (a sample at or above −0.1 dBFS), and the estimated delay in ms (FR-002).

## Relationships

- `AmpSoundStore` holds the sounds and picks the `ChainSettings` handed to `MonitorEngine.setChain`.
- `PracticeWindow` starts and stops `MonitorEngine` with the practice window (FR-001). When the practice window opens on a score, and whenever the score in front of it changes, it asks `AmpSoundStore.soundFor(ScoreKey)`, and when the learner picks a sound, it calls `remember(ScoreKey, name)`.
- `CaptureService` hands blocks to `MonitorEngine` only while it is ON (research R2).
