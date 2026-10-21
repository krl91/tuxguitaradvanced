# Data model: Arcade made fun (spec 014)

## ArcadeLevel
Levels 1 to 5, each with a **note share** and a **chord share**: 1 = 30 % and 0 %; 2 = 30 % and 30 %; 3 = 50 % and 50 %; 4 = 75 % and 75 %; 5 = 100 % and 100 %. A level outside 1 to 5 is brought to the nearest.

## ArcadeLeniency
Three ordered positions: LIKE_PRACTICE, FUN (default), VERY_PERMISSIVE. Each has: a value for each relaxed parameter (R2), a **window factor** (1.0, 1.25, 1.5). `apply(RecognitionSettings)` returns settings where each relaxed parameter is **the more lenient** of the effective value and the position's value; LIKE_PRACTICE returns the settings unchanged (same object or an equal one). `windowMillis(int chosen)` returns `min(200, round(chosen × factor))`, and `chosen` itself for LIKE_PRACTICE.

## Sounds kept (ArcadeLevelFilter)
Input: a `PracticeSequence`, a level. Output: a `PracticeSequence` of the kept events in order. Rules: notes (`EXPECTED_NOTE`) and chords (`EXPECTED_CHORD`, `MULTI_NOTE`) each get *k* = max(1, round(share × *n*)) kept events at ranks `floor(i × n / k)` (so the first of each kind is always kept); a kind with share 0 keeps none, except that if that would leave **no sound at all** one chord is kept; `AUTO_PASS` events are kept as they are; a chord is whole; the start index is the first kept event at or after the original start.

## ArcadeChoices (stored)
`level` (int 1 to 5, absent = 1) and `leniency` (position name, absent or unknown = FUN). Read when Arcade is prepared and when the window is shown; written when the player chooses. Own configuration module; unreadable entries fall back and are not an error.

## What a run reports
The `ArcadeResult` shown to the player gets the level and the leniency position used (FR-010); the score, the streak and the counts are those of the sequence given to the run.

## Sounds removed (RemovedSoundsPlayer)
The events of the full sequence that the level did not keep, in order, each with its written time (from the run's timeline), its pitches (a chord whole) and its written duration. Played to the player's MIDI output on the channel of the track, not by the player's mute (the track stays muted in the player), in step with the run: started, paused, resumed, stopped and restarted with it. Nothing at level 5.
