# Data model: chord profile and chord leniency (version 2)

**Spec**: [spec.md](spec.md) · **Research**: [research.md](research.md)

## Chord profile (state of one strum, in the practice session)

The pure class `ChordProfile` (`TuxGuitar-practice/src/.../practice/session/`) holds the following.

| Field | Meaning | Rule |
|---|---|---|
| `sums[12]` | The summed level of each note name, C = 0 to B = 11 | For every frame since the arming, and every MIDI pitch from `low` to `top`: `sums[midi % 12] += weight(10^(bandDb/20))` |
| `low` | The lowest pitch summed | The expected chord's lowest pitch, minus one semitone, kept at least `SpectrumFrame.getMinBandMidi()` |
| `top` | The highest pitch summed | `CHORD_PROFILE_TOP` (R5) |
| `template[12]` | The expected note names (the required names; "expected names" means these everywhere) | 1 for each name of the required pitches, 0 elsewhere |
| `ignored[12]` | The tolerated names | true for each name of a tolerated pitch (still sounding from an earlier beat) that is not a required name: its bands are not summed, so it counts neither for nor against the chord |
| `summedNanos` | The time summed since the arming | |
| `noise` | The noise floor | A band more than `CHORD_PROFILE_NOISE` dB under the frame's strongest band is not summed (no floor when the setting is absent) |

Operations:
- `reset(required, tolerated, top, weight, noise)` at each arming: the sums go to 0, the template and the ignored names are set. Nothing is allocated; the arrays are made once per session.
- `add(SpectrumFrame)` sums one frame.
- `match()` returns the cosine of `sums` and `template`. It is 0 when `sums` is all 0.
- `weakNames(share)` counts the required names whose sum is under `CHORD_PROFILE_NAMES` × the strongest expected name's sum.
- `weakest(n)` returns the expected names whose share of the profile is lowest, for the refusal feedback.

Invariants, each tested:
- **Pure and deterministic.** The same frames give the same match.
- **Octave-blind.** Moving a played note by an octave inside `low`–`top` does not change the match.
- **Voicing-blind.** Two voicings with the same names have the same template.
- **Synthetic cases:**
  - a frame where only the expected names sound matches 1;
  - a chord sharing at most one name with a three-name template matches at most 1/√3 ≈ 0.58, under every threshold of R6;
  - a chord holding only two names of a four-name template, or three of a five-name one, may match 0.71 or 0.77, but has two weak names, so it is never complete;
  - a chord missing one name of four has one weak name and is judged by its match alone;
  - a band under the noise floor adds nothing;
  - a tolerated name adds nothing: a right chord with a silent tolerated name matches as without it, and a ringing tolerated name does not lower the match;
  - a missing name that holds the leak measured by the probe (T007: the highest share a name not played reaches in the reference takes) is still weak at *Normal*'s share.

## The profile rule (in `ChordMatcher`, used by `PracticeSession` and `FollowSession`)

When `CHORD_COMPLETENESS` is `profile`, a frame is complete when `match() ≥ CHORD_PROFILE_MATCH`, `weakNames() ≤ 1` and `summedNanos ≥ CHORD_PROFILE_SETTLE`. Two weak names or more refuse the frame whatever the match (the spec's clearly wrong chord, FR-002, for any chord size).

**Who holds the profile:** `PracticeSession` holds one, reset at each arming (Wait). `FollowSession` holds one per chord candidate of each `PendingOnset`, made with the onset (Follow and Arcade).

**Each session decides as today, on its own reference** (the arming in Wait, the onset in Follow and Arcade):
- it accepts after `stableHops` complete frames in a row, within `windowNanos` of its reference;
- when the window ends first, the strum is a confirmed wrong chord. The feedback is `INCOMPLETE`, with missing = `weakest(…)` mapped to the expected pitches of those names, and unverifiable empty;
- a frame at a level that is not usable resets the run, as today. The profile keeps its sums.

When `CHORD_COMPLETENESS` is `all-verifiable`, the per-string rule of spec 023 applies, unchanged.

## Settings (R5)

| Constant | Key | Unit | Default | Bounds | Section |
|---|---|---|---|---|---|
| `CHORD_COMPLETENESS` (existing) | `recognition.chord.completeness` | choice: `all-verifiable`, `profile` | `profile` | — | chords |
| `CHORD_PROFILE_MATCH` | `recognition.chord.profile.match` | % | from the sweep | 50–99 | chords |
| `CHORD_PROFILE_SETTLE` | `recognition.chord.profile.settle` | ms | from the sweep | 0–500 | chords |
| `CHORD_PROFILE_NAMES` | `recognition.chord.profile.names` | % | from the sweep | 5–60 | chords |
| `CHORD_PROFILE_TOP` | `recognition.chord.detail.top` | MIDI | from the sweep | 60–112 | chord details (new) |
| `CHORD_PROFILE_NOISE` (only if the sweep keeps it) | `recognition.chord.detail.noise` | dB | from the sweep | 20–60 | chord details |
| `CHORD_PROFILE_WEIGHT` (only if the sweep keeps it variable) | `recognition.chord.detail.weight` | choice: `linear`, `root` | from the sweep | — | chord details |
| `CHORD_ATTACK` (only if the sweep turns it off) | `recognition.chord.detail.attack` | on/off | from the sweep | — | chord details |

- The chords section then holds 7 parameters, 8 rows, the most `RecognitionControls.MAX_SECTION_ROWS` allows; the chord details section is new (research R5).
- None of these is a device-profile parameter (spec 013).
- Follow and Arcade do not relax them unless the sweep shows a need (R7).

## Chord leniency level

The level is not stored. It is derived from the default layer of `RecognitionSettingsStore` (R7).

| Field | Values | Rule |
|---|---|---|
| `level` | `STRICT`, `NORMAL`, `LENIENT`, `CUSTOM` | The named level whose values the settings hold, all of them; otherwise `CUSTOM` |
| Level values | `CHORD_COMPLETENESS`, `CHORD_PROFILE_MATCH`, and any profile value the sweep moves between levels | `NORMAL` = `RecognitionSettings.defaults()`; `STRICT` and `LENIENT` from the sweep (R6) |
| Not level values | `CHORD_WINDOW`, `CHORD_MIN_PARTIALS`, `CHORD_EVIDENCE`, every other setting | Never written by choosing a level |

Operations of the pure class `ChordLeniency`:
- `of(RecognitionSettings) → Level` derives the level;
- `apply(Level, RecognitionSettings) → RecognitionSettings` returns the settings with the level's values (`CUSTOM` cannot be applied);
- `values(Level)` returns a level's values, for the tips and the tests.

Invariants, each tested:
- `of(RecognitionSettings.defaults()) == NORMAL`.
- `of(apply(L, s)) == L` for every named `L` and every `s`.
- `apply` changes no other value.
- **Ordering (SC-004, on the recorded figures):**
  - *Lenient* accepts at least as many right chords as *Normal*;
  - *Strict* wrongly accepts at most as many as *Normal*;
  - all three accept 0 clearly wrong.

## Migration (R7)

`RecognitionSettings.fromStrings` reads a file holding `all-verifiable`, `CHORD_EVIDENCE` 1/3, `CHORD_CONFIRM` 43 ms and no `recognition.chord.profile.match` key with *Normal*'s values. Any other file is read as it is. `VERSION` stays 1.

## Judgement categories (reports, not code state)

For a take whose played names are P, judged against an expected chord whose names are E:

| Category | Rule |
|---|---|
| right | the take's own chord |
| voicing | E ⊆ P or P ⊆ E, and not right; reported apart |
| one name off | otherwise, with one name of E missing from P |
| clearly wrong | two or more names of E missing from P: always refused (FR-002) |
