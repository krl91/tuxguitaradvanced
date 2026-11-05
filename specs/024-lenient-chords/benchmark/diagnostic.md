# Diagnostic of the owner's chords (spec 024, US3, T003–T004)

**Date**: 2026-11-05.

**Instrument**: `OwnerChordsDiagnosticTest`, run with `-Dtuxguitar.recognition.engine=java` and `=native`. The raw reports are next to this file: `owner-chords-diagnostic-java.txt` and `owner-chords-diagnostic-native.txt`.

**The two engines give the same lines, every one of them** (the two reports are identical below their title).

## What the instrument does

Each of the owner's 42 takes is judged against its own chord and against its control, the same pairing as `OwnerChordsBenchmarkTest`.

**For each judgement it records:**
- the path that accepted it: the attack path, or the chord check;
- the share of clipped frames;
- the longest run of frames where the chord check finds the chord complete, against the 8 needed;
- for each required string at its best frame: heard, missing or unverifiable, with its clear unique partials, its unique partials and its fraction;
- which relaxation would change the outcome.

**How each lever is measured:**
- The levers that are settings today ran in the real session: chord evidence 0.25 and 0.20, confirmation 30 ms.
- The others were simulated on the chord check over the whole take, from the same evidence that `ChordMatcher` reads. The strum window is not applied, so their figures are an upper bound.
- The simulation without any lever gives the session's outcome on 82 of the 84 judgements. The 2 others are `owner-chords/take13` with its control: its complete run of 16 frames is probably outside the strum window, which the simulation does not apply.

## Figures

| Line | Both engines |
|---|---|
| Takes accepted for their own chord | 26 of 42 |
| Controls wrongly accepted | 13 of 42 |
| Wrong acceptances by the attack path / by the chord check | 10 / 3 |
| **Wrong acceptances whose notes are those of the chord played** (another voicing: a subset, a superset, an octave more or less) | **12 of 13** |

The only wrong acceptance of a really different chord is `owner-chords/take18`, A minor `[52 57 60]` accepted as F `[41 53 57 65]`, by the chord check.

## The 16 refused takes

Each string's figures are clear unique partials / unique partials, then the fraction, at the best frame. "run" is the longest complete run (8 needed). "L1" says whether accepting one string short would recover the take.

| Take | Chord | Cause | Run | L1 | Strings at their best frame |
|---|---|---|---|---|---|
| owner-chords/take01 | E `[47 52 56 59 64]` | one string short | 0 | recovers | 52 missing (1 of 3, 0.33) |
| owner-chords/take02 | E `[52 56 59]` | too short | 2 | recovers | all heard, never 8 frames together |
| owner-chords/take03 | E `[52 56 59 68]` | too short | 3 | recovers | all heard, never 8 frames together |
| owner-chords/take05 | E `[52 56 59 64 68]` | one string short | 0 | no | all three verifiable strings heard at their best, never together |
| owner-chords/take06 | D `[50 57 62 66]` | one string short | 0 | no | all heard at their best, never together |
| owner-chords/take07 | D | one string short | 0 | no | 66 missing (1 of 3) |
| owner-chords/take08 | D | one string short | 0 | no | all heard at their best, never together |
| owner-chords/take09 | G `[43 50 55 59 67]` | other | 0 | no | 43 and 59 missing (1 of 3 each) |
| owner-chords/take11 | G | one string short | 0 | recovers | 43 missing (1 of 3) |
| owner-chords/take12 | C `[40 52 55 60]` | one string short | 0 | recovers | all heard at their best, never together |
| owner-chords/take13 | C `[52 55 60 64 67]` | other | 16 | recovers | complete for 16 frames, probably outside the strum window |
| owner-chords/take19 | F `[41 53 57 65]` | too short | 2 | no | all heard, never 8 frames together |
| owner-chords/take21 | D | one string short | 0 | recovers | all heard at their best, never together |
| owner-chords-2/take09 | D | other | 0 | no | all heard at their best, never together |
| owner-chords-2/take11 | D | too short | 2 | recovers | all heard, never 8 frames together |
| owner-chords-2/take12 | D | one string short | 0 | no | all heard at their best, never together |

**Causes, grouped:**
- one string short: 9;
- too short: 4;
- other: 3.

**Clipping is not a cause.** The share of clipped frames is at most 10 % on every refused take, and 0 to 4 % on most. The saturation of the `owner-chords` series, which the plan suspected (research R1), is not what refuses its chords.

**The common pattern: every string of the chord is heard, but not in the same frames.** On 12 of the 16 refusals, each verifiable string reaches the rule (2 clear unique partials and a third of them) at its own best frame. The chord check needs all of them together for 8 frames (43 ms). The strings of a strum start one after the other and fade at different speeds, so the moments when each one is clear do not overlap long enough.

## The levers

Each line gives the refusals recovered, then the wrong acceptances added. The figure in brackets is how many of the added ones are really another chord rather than a voicing of the chord played.

| Lever | Recovered | Wrong added (another chord) |
|---|---|---|
| L1, one string short with one clear partial | 8 | 6 (1) |
| L4, clipped frames judged | 1 | 1 (0) |
| L1 + L4 | 8 | 6 (1) |
| chord evidence 0.25 (real session) | 0 | 0 |
| chord evidence 0.20 (real session) | 0 | 0 |
| confirmation 30 ms (real session) | 0 | 0 |
| one string short, no clear partial needed | 9 | 11 (5) |
| two strings short (4 or more verifiable) | 1 | 1 (0) |
| confirmation with gaps (8 of 12 frames) | 1 | 1 (0) |
| L1 + confirmation with gaps | 8 | 6 (1) |

## Conclusion: the stop condition of T004 applies

- **No lever reaches 38 of 42.** The best one, L1, reaches at most 34 (26 + 8, an upper bound), with 19 wrong acceptances. Of those 19, 17 are the same chord in another voicing. The settings that exist today (evidence, confirmation) recover nothing.
- **The limit of 13 wrong acceptances mostly counts voicing pairs.** These are the same notes on more or fewer strings, or with an octave more or less. Audio cannot tell them apart (constitution XIII; the spec's edge case on strings that cannot be told apart). As counted today, almost any gain in acceptance raises the count through these pairs.
- **What would reach the refusals left:** judging each string over the strum instead of in the same frames. That is spec 023's experiment B, which held the partials over 15 hops. It was dropped because the wrong acceptances rose from 13 to 19, but those were counted the same way, so voicing pairs included. In the native engine, B sat in `chord.rs` (`ChordEvaluator`). Applied to both engines, it would change the shared chord evidence. That is outside the levers this plan allows without the owner (research R3).

The decision is the owner's. The implementation stops here (T004).
