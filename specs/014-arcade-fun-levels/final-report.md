# Final report, spec 014 "Arcade made fun: a more permissive recognition and five levels"

Written 2026-10-21, branch `feature/interactive-practice`, local commits only (nothing pushed).

## What was built (phases 1 to 6, T001 to T039)
- **Five levels** (`ArcadeLevel`, `ArcadeLevelFilter`, `PracticeSequence.keeping`): 30 % of the notes and no chord / 30 % and 30 % / 50 % and 50 % / 75 % and 75 % / everything. **The first sound of each kind is always kept**, the others are spread by rank (`floor(i x n / k)`), the same at every run; a chord is whole; never empty; on the calibration piece level 1 keeps notes 1, 4, 7, level 3 notes 1, 3, 5, 7, 9 and chords 1, 2, level 4 notes 1-4 and 6-9. The filter runs once on the fresh-start path of the practice window, the whole piece used by the replays is filtered at the same level, and the restarts and replays never filter again (a source test).
- **The removed sounds are heard in the backing** (`RemovedSoundsPlayer`, owner's decision of 2026-10-21 after a spike): they are sent through the player's output on the channel of the track, one poll thread of its own on the run's clock; each once at its tick, a chord whole, ended after its written duration, silenced on pause and stop, never played late or before the start, nothing created at level 5. No change of the core: the player-only mute is a mute of the sequencer's track, so notes sent to the channel are not silenced.
- **The permissiveness** (`ArcadeLeniency`, three positions: like Practice / fun, the default / very permissive): a one-way relaxation of the effective settings (confidence, stability time, chord evidence, minimum partials, confirmation time) and a wider timing window (x1.25, x1.5, capped at 200 ms), applied in the Arcade start only (a source test); like Practice changes nothing.
- **Controls**: the level row on the first line of the start window of every Arcade start (level 1 the first time, then the last chosen), the permissiveness line in the Arcade section of the preparation, the result naming both; `ArcadeChoices` remembers them in a configuration module of their own; 16 texts in English, French and Spanish; documentation in `docs/PRACTICE*.md`, the README and STATUS.

## What ran
- Whole default suite after a clean compile (`mvn -e clean verify -P native-modules`): practice 1147 tests (9 skipped: the gated and native ones), engine 373, 0 failures. Native tests (`-P swt-gui-tests`) green, including the existing `PracticeWindowSwtTest`, `RecognitionSettingsSwtTest`, `FollowPanelSwtTest` and the new `PreRunWindowLevelSwtTest`.
- No existing test was modified, renamed or deleted. The recognition code (`FollowSession`, `PracticeSession`, `ChordMatcher`, `PolyphonicAcceptance`, the engine's `analysis`, `recognition`, `pitch`), the audio engine's main sources and every other spec's reports are identical to the base.

## The honest result of the measure (benchmark/validation.txt)
- **The 20 % faster verdict is NOT reachable and was dropped by the owner.** None of the relaxed settings changes the verdict delay (105 ms median, 169 ms p90 at every position and at every single setting): a note is accepted only once its attack is reported, 13 hops (about 69 ms) after the transient, a constant of the attack detector, not a setting. Changing it would change the recognition algorithm (FR-014).
- **The relaxation of the recognition is neutral where it was measured**: on the reference recording every position gives 35 of 38 recognised, 0 of 98 false accepts, delay 105 ms, equal to Follow's; on the built-in-microphone corpus 21 of 21 and the control 1 of 20, equal to the base. The acceptance in cents was removed from the relaxation because widening it lost a right note.
- **What the player feels in Arcade therefore comes from the wider timing window and from the levels**, which these measures do not see. The effect of the relaxation on weaker inputs is **not measured**; no claim is made. Scope: this machine, this reference recording, this built-in microphone, this guitar.
- The removed sounds: the **software scheduling** was measured (median 2.4 to 3.0 ms, max 6.3 ms, never early); what was **not** and cannot be measured without sound: their offset against the sounds the sequencer plays, and bends, slides and ties (only the written pitch is sent).

## What was skipped or not covered
- **T040, the owner's trial**: is the game fun at the default position, do the removed sounds sound in time and in tune with the band, is the level row clear. Open.
- The chord takes of the owner were not run at the positions (T012, T036).
- No end-to-end Arcade run is driven through the practice window (no test builds it); the wiring is covered by source tests and by the parts.
- `ArcadeResultLevelTest` was written together with its code (not seen failing first); the result window with the two lines is tested on its lines and its source, not built.
- The checklist `arcade-fun.md` has 4 items ticked (CHK003, CHK014, CHK019, CHK021); the other 30 belong to the owner as reviewer.

## Defects found by the tests and fixed in the code
The acceptance in cents lost a right note (removed from the relaxation); `ArcadeChoices` in `arcade/core` broke an existing test that forbids any input or output there (moved to `session`); the notes of a removed chord ended in reverse order. Two defects of my own tests (a wait after a stop, a race on the first poll) were fixed in those tests.

## Open points
T040 (the owner's trial) and the 30 open checklist items; whether to release (no push, no release was made).
