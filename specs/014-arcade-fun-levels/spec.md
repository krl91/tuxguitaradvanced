# Feature Specification: Arcade made fun: a more permissive recognition and five levels

**Feature Branch**: `feature/interactive-practice` (the spec directory is `014-arcade-fun-levels`)

**Created**: 2026-10-20

**Status**: Draft

**Input**: User description: "In Arcade mode, make the recognition of the notes and of the chords more permissive (it must stay configurable): let a few more wrong notes through and be a little more flexible on the tempo, so that the game is fun; favour the speed of the recognition and the gameplay. Then five levels in Arcade: level 1, the lowest, removes all the chords and keeps 30 % of the notes; level 2 keeps 30 % of the chords and 30 % of the notes; level 3 keeps 50 % of the chords and 50 % of the notes; level 4 keeps 75 % of the chords and 75 % of the notes; level 5 plays all the notes." (owner, 2026-10-20)

## Why this exists

Arcade is a game: the notes fly towards the neck and the player plays them as they land. The practice modes ask the recognition to be **right**; a game asks it to be **quick and forgiving**, because a verdict that comes late or a good attempt refused breaks the fun. Two things are asked:

1. **A recognition tuned for play in Arcade only**: quicker, a little more tolerant of near-misses and of the tempo, and still adjustable by the player. The other modes (Follow, Wait, the tuner) keep the strict recognition they have today.
2. **Five levels** that make a real piece playable by a beginner: the same piece, with only a share of its sounds to play, from a few notes (level 1) to the whole piece (level 5).

## Clarifications

### Session 2026-10-20

- Q: For levels 1 to 4, how are the sounds kept (for example 30 %) chosen? → A: **Spread evenly over the piece and always the same ones** at every run, so that the player can learn the piece and a test can check the choice. (Chosen by the owner among: spread evenly and fixed, the most important first, random at each run.)
- Q: At level 2 the request says "30 % of the chords and 30 % of the chords": what is the second share? → A: **30 % of the chords and 30 % of the notes.** (The five levels are then: no chord and 30 % of the notes; 30 % and 30 %; 50 % and 50 %; 75 % and 75 %; everything.)
- Q: What happens when the player plays a note that is close to the right one but not exact? → A: It is **accepted as right, without penalty**: the recognition lets more uncertain sounds count as played, at the price of a few wrong notes counted right. (Chosen by the owner among: accepted as right, counted wrong without breaking the streak, both.)
- Q: How many clearly wrong notes may the game accept as right at its default position? → A: **At most 5 %** of the clearly wrong notes of the negative tests (today 0 of 98 on the reference recording); the figure of each position is reported. (Chosen by the owner among 2 %, 5 %, 10 %, no figure.)
- Q: How fast must the game give its verdict on a played note? → A: **At least 20 % faster than the verdict of Follow**, in median, on the same recordings (at the default position). (Chosen by the owner among: not slower than today, 20 % faster, under 150 ms, as fast as the data allow with no figure.)
- Q: Which level is proposed the first time, before the player has chosen anything? → A: **Level 1 (the lowest) at the first launch, then the last level chosen**; and the player MUST be able to **choose the level by hand when Arcade is launched**. (Chosen by the owner, with the addition of the manual choice, among: level 5 as today, level 3, level 1 then the last chosen.)
- Q: On which data is the limit of 5 % of wrong notes measured? → A: **On the reference recording only** (its 98 negative tests: at most 4 accepted); the built-in-microphone corpus, which already accepts 1 of 21 as a negative control with the default settings, is **reported without a limit**. (Chosen by the owner at the review of the checklist, 2026-10-21.)
- Q: Which sounds are kept at levels 1 to 4 exactly? → A: **The first sound of each kind (notes, chords) is always kept**, the others spread evenly after it by rank (not by time); on the calibration piece (10 notes, 3 chords) level 1 keeps notes 1, 4, 7 and level 3 keeps notes 1, 3, 5, 7, 9 and chords 1 and 2. (Chosen by the owner among: centred, anchored at the start, anchored at both ends.)
- Q: Are the sounds that a level removes from the player's track heard? → A: **Yes, they are played by the backing** at their written time, so that the music stays complete: the player plays the kept sounds and hears the rest. (Chosen by the owner between: silent, played by the backing; this changes the earlier statement that the backing is unchanged.)

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Arcade is quick and forgiving (Priority: P1)

The player starts Arcade on a piece. The recognition gives its verdicts sooner, accepts a sound that is close to the expected note or chord, and is a little more flexible on the timing, so that a good attempt is not refused and the game keeps flowing. The player can change how permissive it is, from "like Practice" (exactly the recognition of today) to "very permissive", and the choice is remembered.

**Why this priority**: it is the first thing the owner asks for and what makes the game fun; the levels (story 2) are of little use if the game still refuses good attempts.

**Independent Test**: replay the same recorded performance in Arcade at each position of the control and in Follow; compare the share of right notes accepted, the share of wrong notes accepted and the time from the sound to the verdict.

**Acceptance Scenarios**:

1. **Given** Arcade at its default position, **When** the player plays a note slightly off pitch or slightly late, **Then** it counts as played and the game goes on without any penalty.
2. **Given** the control at "like Practice", **When** the same performance is played, **Then** every verdict, score and result equals what the application gave before this feature.
3. **Given** the control moved to another position, **When** the player starts the next Arcade run, **Then** that position is used and is remembered at the next launch.
4. **Given** the same performance in Follow or in Wait, **When** it is judged, **Then** the result is identical to before this feature: only Arcade is affected.

---

### User Story 2 - Five levels from a few notes to the whole piece (Priority: P1)

When Arcade is launched the player can choose a level from 1 to 5 (the level proposed is level 1 the first time, then the last level chosen). Level 1 keeps no chord and about 30 % of the notes; level 2 keeps 30 % of the chords and 30 % of the notes; level 3, 50 % and 50 %; level 4, 75 % and 75 %; level 5 keeps everything. The sounds that are not kept are neither shown, nor expected, nor judged, and the score is counted on the sounds kept; **they are played by the backing**, so the piece stays complete to the ear. The first sound of each kind is always kept and the others are spread evenly after it; they are the same at every run.

**Why this priority**: it is the second thing the owner asks for, and it makes a hard piece playable and lets the player climb a ladder on the same piece.

**Independent Test**: for a piece with known notes and chords, list the sounds kept at each level and check the counts, the even spread, and that two runs give the same list.

**Acceptance Scenarios**:

1. **Given** a piece with chords, **When** level 1 is chosen, **Then** no chord is shown or expected and about 30 % of the notes are.
2. **Given** level 3, **When** the run is played, **Then** about half of the chords and half of the notes are shown, spread over the whole piece, and the same ones as at the previous run of level 3.
3. **Given** level 5, **When** the run is played, **Then** every note and every chord of the piece is shown, as today, and the backing plays exactly what it plays today.
8. **Given** level 3, **When** a sound that is not kept comes at its written time, **Then** the backing plays it (a chord whole), at the tempo of the run, and it stops and pauses with the run.
4. **Given** any level, **When** the run ends, **Then** the score and the result are counted on the sounds kept, and the result says which level was played.
5. **Given** a level chosen, **When** the application is restarted, **Then** the same level is proposed.
6. **Given** Arcade has never been launched with levels, **When** it is launched, **Then** level 1 is proposed, and the player can pick any other level before the run starts.
7. **Given** the launch of Arcade, **When** the level choice is shown, **Then** the player can change it by hand every time, and the choice made applies to that run and is the one proposed next time.

---

### User Story 3 - The player stays in control and understands what changed (Priority: P2)

The control of the leniency and the choice of the level are in the Arcade preparation, in the three shipped languages, and described in the documentation. The result window says which level and which leniency were used, so that two runs can be compared fairly.

**Why this priority**: the owner asks that it stays configurable; without it the player cannot tell why a score changed.

**Independent Test**: open the preparation window in each language; play a run and read the result.

**Acceptance Scenarios**:

1. **Given** the Arcade preparation, **When** it is shown, **Then** the level and the leniency are visible with their current values and a short description.
2. **Given** a finished run, **When** the result is shown, **Then** it names the level and the leniency position used.

### Edge Cases

- **A piece without chords**: levels 1 and 2 differ only by the share of notes; the chord share changes nothing.
- **A piece with very few sounds**: at every level at least one sound is kept (a level never gives an empty run); a share is rounded to the nearest whole number of sounds, never below one.
- **A piece with only chords**: at level 1 (no chord) one chord is still kept, so that the run is not empty, and the preparation says so.
- **A chord is kept or removed as a whole**: it never loses some of its notes.
- **The leniency never accepts silence**: a sound must have been heard for something to count as played.
- **Changing the level or the leniency during a run**: it applies to the next run, not to the one in progress.
- **A player who already uses Arcade** sees level 1 proposed the first time after this feature (a one-time change that the owner chose); level 5 is one choice away and then remembered.
- **A saved level or leniency that is missing or out of range** falls back to the default or the nearest allowed value without an error.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: In Arcade, the recognition of notes and chords MUST be **more permissive and quicker** than in the practice modes: a sound close to the expected note or chord counts as played, **without penalty**, and the verdict MUST NOT wait for more certainty than the game needs.
- **FR-002**: The permissiveness MUST be **configurable** by the player over several positions, from **"like Practice"** (exactly today's recognition) to **"very permissive"**, with a default position that is **more permissive than today**; the choice is remembered between runs and launches and applies to the **next run**.
- **FR-003**: The timing of Arcade MUST be a little more flexible at the permissive positions, in addition to the timing window of the player (spec 012); at "like Practice" it MUST be exactly today's.
- **FR-004**: The permissiveness MUST apply to **Arcade only**: Follow, Wait, the tuner window and the calibration keep their recognition and their figures, identical to before this feature. A device profile (spec 013) keeps applying, and the Arcade permissiveness is applied on top of it.
- **FR-005**: The judging MUST remain **deterministic**: the same performance, the same position and the same level give the same result.
- **FR-006**: Arcade MUST offer **five levels**. **At the first launch the level proposed is level 1**; afterwards it is **the last level chosen**, remembered between launches. The player MUST be able to **choose the level by hand each time Arcade is launched**, before the run starts; **a restart or a replay of a section keeps the level and does not ask again**. The levels are: level 1 keeps **no chord and 30 % of the notes**; level 2 keeps **30 % of the chords and 30 % of the notes**; level 3 keeps **50 % and 50 %**; level 4 keeps **75 % and 75 %**; level 5 keeps **all the notes and all the chords**.
- **FR-007**: The sounds kept at levels 1 to 4 MUST be **the same at every run** of that level on that piece, with **the first sound of each kind (notes, chords) always kept** and the others **spread evenly after it, by rank in the piece and not by time**.
- **FR-008**: A sound that is not kept MUST be neither shown, nor expected, nor judged (it is **heard in the backing**, FR-015); the score, the best streak and the result MUST be counted **on the sounds kept**; a chord is kept or removed **as a whole**.
- **FR-009**: A share MUST be applied by rounding to the nearest whole number of sounds, and **at least one sound MUST be kept** at every level whenever the piece has one.
- **FR-010**: The result MUST name the **level** and the **leniency position** used.
- **FR-011**: With the leniency at "like Practice" and level 5, every judgement, score and result MUST equal what the application gave before this feature.
- **FR-012**: A missing, invalid or out-of-range saved level or position MUST fall back to the default (level 1 for the level) or to the nearest allowed value without an error.
- **FR-013**: The controls and their texts MUST exist in the three shipped languages (English, French, Spanish) and be described in the documentation.
- **FR-014**: The feature MUST NOT change the recognition algorithms themselves, the Follow, Wait and tuner behaviour, the backing band **other than by FR-015**, or the latency compensation of the route.
- **FR-015**: The sounds that a level removes from the player's track MUST be **played by the backing at their written time**, with the sound (instrument) of that track, at the tempo of the run, stopping and pausing with it; a chord is played whole; at level 5 nothing is added and the backing is exactly today's. The player's own sounds (the kept ones) are not played by the backing.

### Key Entities

- **Leniency position**: one of the ordered positions of the Arcade permissiveness, from "like Practice" to "very permissive", with its default.
- **Level**: 1 to 5, with its share of the notes and its share of the chords; the level proposed is level 1 at the first launch, then the last one chosen.
- **Sounds kept**: the notes and chords of the piece that a level keeps, in a fixed order, the first of each kind and the others spread after it; the others are the **sounds removed**, played by the backing.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: On the recorded performances of the project, at the default position the share of **right notes and chords accepted** is **higher than in Follow**, and the **median time from the sound to the verdict is at least 20 % shorter** than in Follow on the same recordings (**decided by the owner**); both figures are reported with the figures of Follow next to them.
- **SC-002**: At the default position, **at most 5 % of the clearly wrong notes** of the **98 negative tests of the reference recording** are accepted, i.e. **at most 4** (**decided by the owner**; today: 0 of 98); the built-in-microphone corpus, where the default already accepts 1 of 21 of its negative control, is **reported without a limit**; the figure is reported, not hidden, and the position "very permissive" is reported the same way (it has no limit of its own, only the report).
- **SC-003**: At "like Practice" and level 5, **0 differences** with the figures recorded before this feature; Follow, Wait and the tuner give **0 differences** at every position.
- **SC-004**: At each level, the **count of kept notes and of kept chords** is the stated share within one sound on the reference pieces, the first sound of each kind is kept, the kept sounds are the same at two runs (100 %), and no chord is partly kept.
- **SC-007**: The sounds removed are heard in the backing **at their written time** (the offset from the written time is measured and reported, with the offset of the existing backing next to it), and **no sound is heard twice or lost** when a run is paused, stopped or restarted (0 cases in the tests).
- **SC-005**: The texts exist in **3 of 3 languages**.
- **SC-006**: The owner, playing a real piece, finds the game **fun at the default position** (the owner's trial, the final check).

## Assumptions

- "A little more flexible on the tempo" is understood as a wider window **on top of** the adjustable window of spec 012, not a replacement for it; the figures are decided at the plan from the measures of SC-001, within the limit of SC-002.
- The control of the permissiveness has **three positions** (like Practice, fun as the default, very permissive); the plan may refine the number of positions if the measures call for it.
- The share kept is of the sounds of the **player's track** (the one played in Arcade); the rest of the backing band is unchanged, and the sounds the level removes are added to it (FR-015).
- The spread is by **rank in the sequence** of the sounds of each kind, not by time, with the first sound of each kind always kept (the rule of FR-007, written as a formula in the level contract); "always the same" holds for the same piece.
- The score at a lower level is **not compared** with the score at another level: the result names the level.
- The owner's trial of the game is the final check; the headless tests cannot judge whether it is fun.
- Out of scope: a level for the Follow or Wait modes, a different piece per level, a calibration of the leniency per device, bass-specific tuning.
