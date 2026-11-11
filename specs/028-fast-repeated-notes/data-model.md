# Data model: fast repeated notes recognised

**Spec**: [spec.md](spec.md) | **Research**: [research.md](research.md)

## New recognition settings

All in `RecognitionParameters`, read at run time, documented in `docs/architecture/recognition-engines.md` (FR-007). Nine keys. Defaults are set by the probe (phase 2); the values below are the starting points of the sweep.

| Key | Section | Unit | Start | Bounds | User setting | What it does |
|---|---|---|---|---|---|---|
| `recognition.reattack.rise` | new `REATTACK` | dB | 3.0 | 1.0–10.0 | no (expert) | Rise of the expected pitch's band above its minimum before, for a re-attack |
| `recognition.reattack.before` | `REATTACK` | hops | 8 | 2–19 | no | Hops before the rise over which the band's minimum is taken |
| `recognition.reattack.spread` | `REATTACK` | share | 0.6 | 0.3–1.0 | no | A rise seen in this share of the bands or more in the same frame is a scratch or a click, not a re-attack |
| `recognition.reattack.octave` | `REATTACK` | flag | on | — | no | Whether the band an octave above counts with the fundamental's |
| `recognition.reattack.enabled` | `REATTACK` | flag | on | — | no | Off: today's behaviour (no re-attack, and the first open event takes an attack), for comparison |
| `recognition.tuning.drift-threshold` | new `TUNING_DRIFT` (first row) | cents | 20 | 10–35 | **yes** | Mean offset from which a retune is suggested (clarification Q5) |
| `recognition.tuning.drift-notes` | `TUNING_DRIFT` | notes | 6 | 3–20 | no | Notes the mean is taken over |
| `recognition.tuning.drift-agreement` | `TUNING_DRIFT` | share | 0.8 | 0.5–1.0 | no | Share of them off the same way |
| `recognition.tuning.drift-quiet` | `TUNING_DRIFT` | notes | 16 | 4–100 | no | Notes without a new suggestion after one |

Types: numbers are `RecognitionParameter.Number`, the on/off values `RecognitionParameter.Flag`. The new sections come after the existing ones in `Section` (the first eight are pinned by `RecognitionSectionsTest`), hold 5 and 4 values (1 to 7 allowed), and are never overlaid by a device profile (`ProfileParameters` unchanged). Each key has, in the same change: a row in `docs/architecture/recognition-engines.md` under its section (unit, default, bounds, "next run"), a row in both help pages `desktop/TuxGuitar/share/help/tools_audio_recognition{,_fr}.html` with an `<h2>` per new section, and a name and tip in `LANG` (three languages, translated). The section names too.

Existing settings untouched: every `ONSET_*`, `ONSET_FAST_*`, `NOTE_ATTACK`, `NOTE_ACCEPT`, `NOTE_FRET_SHARP`, the leniencies of Follow and Arcade.

## Session state (`FollowSession`)

- **Expected repeat** (per event, computed once from the full written sequence, before Arcade's level filter): the pitches of the event that already sound at its target (held by the previous written event, or by a chord still ringing). Empty for most events. Also in `PracticeSession` (Wait), for the awaited event.
- **No numeric `static final`** in the new session code: every value comes from the settings (`SessionConstantInventoryTest`); the rings are sized from the parameters' upper bounds.
- **Band history**: for each pitch of an expected repeat still open, the last `reattack.before` band levels (a ring of floats, preallocated; nothing allocated per frame).
- **Re-attack**: `{ transient wall time, pitch, rise dB }`. Fed into the same path as a detector onset (`PendingOnset`), marked as a re-attack in the trace.
- **Attack assignment**: an attack whose time falls in the windows of several open events of the same pitch goes to the one with the nearest target (R4).

## `TuningDrift` (pure, new, `session/`)

- Input per judged single note (Wait, Follow, Arcade; chords are not inputs): `cents` (signed offset from the written pitch), `string` (`PracticeEvent.getStringNumber()`, or 0 when unknown), `outOfTune` (refused for the margin).
- Output after each note: `none`, or `suggest(direction, string or 0)`.
- State: a ring of the last `drift-notes` entries; a count of notes since the last suggestion.

## Offset test case and table

- **Case**: folder (one per entry of `tuxguitar.owner.offset`), GP5 tablature, WAV, track number, offsets (−100, −70, −50, −40, −30, −20, 0, 20, 30, 40, 50, 70, 100 ms), target (100 % in −70..+70).
- **Row**: mode (Wait at offset 0, Follow, Arcade), engine (Java, native), offset ms, recognised / events, refusals by `RefusalReason`.
