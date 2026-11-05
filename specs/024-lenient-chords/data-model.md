# Data model: chord leniency

**Spec**: [spec.md](spec.md) · **Research**: [research.md](research.md)

## Chord leniency level

The level is not stored. It is derived from the default layer of `RecognitionSettingsStore` (research R2).

| Field | Values | Rule |
|---|---|---|
| `level` | `STRICT`, `NORMAL`, `LENIENT`, `CUSTOM` | The named level whose values the settings hold, all of them; otherwise `CUSTOM` |
| level values | `CHORD_EVIDENCE`, `CHORD_CONFIRM`, `CHORD_COMPLETENESS`, and the L4 flag if implemented (R3) | `NORMAL` = `RecognitionSettings.defaults()` for each; `STRICT` and `LENIENT` from the sweep (R4) |
| not level values | `CHORD_WINDOW`, `CHORD_MIN_PARTIALS`, every other setting | Never written by choosing a level |

Operations of the pure class `ChordLeniency`:
- `of(RecognitionSettings) → Level` derives the level.
- `apply(Level, RecognitionSettings) → RecognitionSettings` returns the settings with the level's values. `CUSTOM` is not appliable.
- `values(Level)` gives a level's values, for the tips and the tests.

Invariants, each tested:
- `of(RecognitionSettings.defaults()) == NORMAL`.
- `of(apply(L, s)) == L` for every named `L` and every `s`.
- `apply` changes no other value.
- Ordering (SC-004, on the recorded figures): *Lenient* accepts at least as many as *Normal*. *Strict* wrongly accepts at most as many as *Normal*, and fewer than 13.

## Chord completeness (existing setting, one option added)

`RecognitionParameters.CHORD_COMPLETENESS`, key `recognition.chord.completeness`:
- `all-verifiable`: today's rule, where every verifiable pitch must be heard;
- `all-but-one`: lever L1, if kept by the diagnostic. A chord with at least 3 verifiable pitches is complete when at most one is missing and that one shows at least one clear unique partial.

Which pitches are unverifiable never changes with the option.

Its default becomes *Normal*'s option. Migration in `RecognitionSettings.fromStrings` (R6): a file holding the spec 023 chord values (all-verifiable, 1/3, 43 ms) and no L4 key is read with *Normal*'s completeness. Any other file is read as it is.

## Clipped chord frames (new flag, only if the diagnostic keeps L4)

`RecognitionParameters.CHORD_CLIPPED` (provisional name), key `recognition.chord.clipped`, in `Section.CHORDS`. When set, the chord check judges a `CLIPPING` frame instead of resetting its run.
- It is not a device-profile parameter.
- It is not a Follow or Arcade leniency row.
- Its default is *Normal*'s value.

## Chord diagnostic (report, not code state)

Each of the 42 takes, and its control, with each engine. The fields are:
- the take;
- the expected and the played pitches;
- the outcome;
- the cause (*clipped*, *one string short*, *weak evidence*, *too short*, or *other*, the first that applies, research R1);
- per pitch: heard, missing or unverifiable, with its unique partials, clear unique partials and fraction at its best frame;
- the share of clipped frames;
- the longest complete run;
- the attack path's best share, peak and balance;
- the relaxations that would flip the outcome.

The report is `specs/024-lenient-chords/benchmark/diagnostic.md`, with the causes grouped and counted.
