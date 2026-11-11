# Data model: fast repeated notes recognised

**Spec**: [spec.md](spec.md) | **Research**: [research.md](research.md)

## New recognition settings

All in `RecognitionParameters`, read at run time, documented in `docs/architecture/recognition-engines.md` (FR-007). Eleven keys. The `%` values are stored as shares and shown ×100, as `NOTE_CONFIDENCE` and `CHORD_EVIDENCE` are (`RecognitionParameters`, display factor 100.0). Defaults are set by the probe (phase 2); the values below are the starting points of the sweep.

| Key | Section | Unit | Start | Bounds | User setting | What it does |
|---|---|---|---|---|---|---|
| `recognition.reattack.rise` | new `REATTACK` | dB | 3.0 | 1.0–10.0 | no (expert) | Rise of the expected pitch's band above its minimum before, for a re-attack |
| `recognition.reattack.before` | `REATTACK` | hops | 8 | 2–19 | no | Hops before the rise over which the band's minimum is taken |
| `recognition.reattack.spread` | `REATTACK` | % | 0.6 | 0.3–1.0 | no | A rise seen in this share of the bands or more in the same frame is a scratch or a click, not a re-attack |
| `recognition.reattack.octave` | `REATTACK` | flag | on | — | no | Whether the band an octave above counts with the fundamental's |
| `recognition.reattack.enabled` | `REATTACK` | flag | on | — | no | Off: today's behaviour (no re-attack, and the first open event takes an attack), for comparison |
| `recognition.tuning.drift-threshold` | new `TUNING_DRIFT` (first row) | cents | 20 | 10–35 | **yes** | Mean offset from which a retune is suggested (clarification Q5) |
| `recognition.tuning.drift-max-fret` | `TUNING_DRIFT` | frets | 0 | 0–12 | no | Highest fret whose notes count for the drift; 0: open strings only, as a tuner (owner's answer, 2026-11-11: a fretted note sounds sharp on a guitar in tune) |
| `recognition.tuning.drift-notes` | `TUNING_DRIFT` | notes | 6 | 3–20 | no | Notes the mean is taken over |
| `recognition.tuning.drift-agreement` | `TUNING_DRIFT` | % | 0.8 | 0.5–1.0 | no | Share of them off the same way |
| `recognition.tuning.drift-quiet` | `TUNING_DRIFT` | notes | 16 | 4–100 | no | Notes without a new suggestion after one |
| `recognition.tuning.arcade-boost` | `TUNING_DRIFT` | % | 0.10 | 0.05–0.25 | no | What Arcade's "more tolerant" choice adds to the run's pitch acceptance, as a share of its value at the start of the run (never above 45 cents) |

Types: numbers are `RecognitionParameter.Number`, the on/off values `RecognitionParameter.Flag`. The new sections come after the existing ones in `Section` (the first eight are pinned by `RecognitionSectionsTest`), hold 5 and 6 values (1 to 7 allowed), and are never overlaid by a device profile (`ProfileParameters` unchanged). Each key has, in the same change: a row in `docs/architecture/recognition-engines.md` under its section (unit, default, bounds, "next run"), a row in both help pages `desktop/TuxGuitar/share/help/tools_audio_recognition{,_fr}.html` with an `<h2>` per new section, and a name and tip in `LANG` (three languages, translated). The section names too.

Existing settings untouched: every `ONSET_*`, `ONSET_FAST_*`, `NOTE_ATTACK`, `NOTE_ACCEPT`, `NOTE_FRET_SHARP`, the leniencies of Follow and Arcade (`FollowLeniencyTest` and `ArcadeLeniencyTest` require that the leniencies change none of the new keys). Arcade's boost changes the value one run uses, never the setting. If the probe shows that a mode's default must become more permissive (spec Assumptions, FR-007a), T006 records it in "R3 closing" with its former and new default (FR-007), and the guards decide (stop condition B).

## Session state (`FollowSession`)

- **Expected repeat** (per event, computed once from the full written sequence, before Arcade's level filter): the pitches of the event that already sound at its target (held by the previous written event, or by a chord still ringing). Empty for most events. Also in `PracticeSession` (Wait), for the awaited event.
- **No numeric `static final`** in the new session code: every value comes from the settings (`SessionConstantInventoryTest`); the rings are sized from the parameters' upper bounds.
- **Band history**: for each pitch of an expected repeat still open, the last `reattack.before` band levels (a ring of floats, preallocated; nothing allocated per frame).
- **Re-attack**: `{ transient wall time, pitch, rise dB }`. Fed into the same path as a detector onset (`PendingOnset`), marked as a re-attack in the trace.
- **Attack assignment**: an attack whose time falls in the windows of several open events of the same pitch goes to the one with the nearest target (R4).

## `TuningDrift` (pure, new, `session/`)

- Input per judged single note (Wait, Follow, Arcade; chords are not inputs): `cents` (signed offset from the written pitch), `string` (`PracticeEvent.getStringNumber()`, or 0 when unknown), `outOfTune` (refused for the margin).
- Output after each note: `none`, or `suggest(direction, string or 0)`.

## Arcade retune offer (`arcade/core/ArcadeRetuneOffer.java`, pure, new)

- States: `PLAYING` → (suggestion) `OFFERED` → `TUNING` (Yes) → `PLAYING` on the tuner's close; `OFFERED` → `PLAYING` (No, or the window closed); `OFFERED` → `PLAYING` with the boost raised (More tolerant).
- Run state: `boostSteps` (0 at the start of each run); the run's acceptance = min(45, start × (1 + steps × `arcade-boost`)).
- The session takes the new acceptance from the next judged frame (`FollowSession.setNoteAcceptCents`, new); a verdict already given never changes. `FollowSession.tolerance` is today a final `TolerancePolicy` (`session/FollowSession.java` line 497) built from `NOTE_ACCEPT`: the setter builds one new policy per choice (on the learner's click, never per frame) with the same in-tune, fret-sharp and 49-cent values, and the swap is posted on the session's loop, as `FollowEvents` posts pause and resolve.
- A Restart is a new run: a new `ArcadeRetuneOffer`, steps 0.
- State: a ring of the last `drift-notes` entries; a count of notes since the last suggestion.

## Offset test case and table

- **Case**: folder (one per entry of `tuxguitar.owner.offset`), GP5 tablature, WAV, track number, offsets (−100, −70, −50, −40, −30, −20, 0, 20, 30, 40, 50, 70, 100 ms), target (100 % in −70..+70).
- **Row**: mode (Wait at offset 0, Follow, Arcade at each level 1–5), engine (Java, native), offset ms, recognised / events, refusals by `RefusalReason`.
