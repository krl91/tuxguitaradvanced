# Feature Specification: Fast repeated notes recognised

**Feature Branch**: `feature/interactive-practice`

**Created**: 2026-11-11

**Status**: Draft

**Input**: User description: "détecter les notes répétées rapides, avec le test d'offset sur l'enregistrement DI (tests/tiktok/1 : extrait_offspring_guitare_DI.wav + tablature_extrait.gp5), et 100 % de reconnaissance jusqu'à 70 ms d'offset ; ce test est gardé et rejoué à chaque version"

## Context

On 2026-11-11 the owner gave a 20-second recording of a guitar played straight into the interface, with no effect and no other instrument (a "DI" recording), together with the tablature it plays: 100 events, 75 single notes and 25 two-note chords, a punk riff of sixteenth notes about 153 ms apart, where a muted low note is played again and again between the chords.

Replayed through a Follow run with the default settings, with the whole performance moved later by 0 to 100 ms to stand for the player's reaction time or the system's delay, both recognition engines gave the same figures:

| Played late by | 0 ms | 20 ms | 30 ms | 40 ms | 50 ms | 70 ms | 100 ms |
|---|---|---|---|---|---|---|---|
| Recognised (both engines) | 51 % | 49 % | 41 % | 38 % | 31 % | 30 % | 30 % |

Every refused event was refused for "no attack": no event was refused for a wrong pitch. The attack finder reported 24 attacks for the 100 events. The chords were almost all recognised. The low note played again 153 ms after itself was almost never recognised, although the sound's energy rises at each of them. The owner judged these figures very bad and set the goal: every event recognised up to 70 ms late, and this test kept and replayed on every version.

## Clarifications

### Session 2026-11-11

- Q: If the same note is played very fast, down to what gap between two attacks must the system still count two notes? → A: 100 ms (sixteenth notes up to 150 bpm).
- Q: Must a player who is early (20 to 70 ms before the beat) also get 100 %, or only one who is late? → A: Early too: 100 % from 70 ms early to 70 ms late.
- Q: Must the 100 % target also be checked in Arcade mode, or only in Follow mode? → A: In every mode, each with its default settings. The owner's principle: whatever the mode, it is better that the engine believes it heard the right note when the note is a little off (not too much), a little early, a little late or on time; the engines must be more permissive.
- Q: Down to what does a note out of tune still count as the right note? → A: Keep what exists: ±35 cents (setting `NOTE_ACCEPT`, 10 to 45, below the halfway to the next fret), plus the extra sharp margin on high frets (up to 49 cents); the neighbouring fret stays a wrong note. And offer to retune the guitar, without stopping the play.
- Q: From what steady average gap, over several notes, should the practice suggest retuning? → A: 20 cents by default (before the first refusals at 35), configurable by the user.
- Q: Arcade's default level judges 30 % of the notes and no chord; at which Arcade level(s) must every event be recognised? → A: At every level, 1 to 5.
- Q: Arcade has no place for a message during the run; how does Arcade suggest retuning? → A: Arcade pauses and shows a window proposing to retune, with three choices: Yes, No, and make Arcade more tolerant (+10 %).
- Q: What does "make Arcade more tolerant (+10 %)" widen? → A: The pitch acceptance in Arcade, +10 % (35 → 38.5 cents), capped at 45 cents so that the neighbouring fret stays a wrong note.
- Q: What happens when the learner chooses "Yes" (retune) in Arcade's window? → A: The application's tuner opens (the one of the pre-run check), the run stays paused; when the tuner closes, the run resumes where it paused.
- Q: Does the "+10 %" chosen in the window hold for the current run only, or is it kept? → A: For the current run only; several choices in the same run add up, never above 45 cents; the next run starts from the usual setting.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Every note of a fast riff counts (Priority: P1)

A guitarist practises a fast riff in which the same note comes back many times in a row (palm-muted sixteenth notes, a pedal note between chords). Each time they play the note again on time, the practice judges it played, as it does for a new note.

**Why this priority**: today half of such a riff is marked missed although it was played right. The learner is told they failed when they did not, which is the worst feedback the product can give (constitution XIV), and this kind of riff is common in rock, punk and metal.

**Independent Test**: replay the owner's DI recording over its tablature in Follow and in Arcade at each level, with the default settings, played on time: every event judged is judged played.

**Acceptance Scenarios**:

1. **Given** the owner's DI recording and its tablature, **When** they are replayed in Follow, or in Arcade at any of its five levels, with that mode's default settings, played on time, **Then** every event judged is recognised (all 100 in Follow), with each engine.
2. **Given** a note played, then played again at the same pitch 150 ms later, **When** both are expected by the score, **Then** both are recognised, one per expected event.
3. **Given** a note played once and left ringing, **When** the score expects that note twice, **Then** the second one is not recognised: a held note is not a note played again.

---

### User Story 2 - A little late or early is still played (Priority: P1)

A guitarist plays slightly behind the beat, because of their reaction time or of the system's delay, or slightly ahead of it, as learners often rush. Up to 70 ms late or 70 ms early, every note they play is still judged played.

**Why this priority**: today, in a fast passage, being 30 ms late loses a tenth of the notes and 50 ms late a fifth, while a learner is often that late. The owner set 70 ms, either way, as the limit that must cost nothing. Early is harder in a fast riff: a note 70 ms early on a riff of 153 ms falls nearer the previous note than its own.

**Independent Test**: replay the owner's DI recording moved by −70, −50, −40, −30, −20, 0, 20, 30, 40, 50 and 70 ms (negative: early): every event is recognised at each offset, with each engine.

**Acceptance Scenarios**:

1. **Given** the DI recording moved by any of −70, −50, −40, −30, −20, 0, 20, 30, 40, 50 or 70 ms, **When** it is replayed with the default settings, **Then** every event judged is recognised (all 100 in Follow, all those its level keeps in Arcade), with each engine.
2. **Given** the DI recording moved by −100 or 100 ms, **When** it is replayed, **Then** its figure is reported, without a target.

---

### User Story 3 - The test is replayed on every version (Priority: P2)

Before every version is published, the offset test is replayed on the DI recording with each engine, and its table (engine, offset, share recognised, the reasons of the refusals) is written in the version's report. A version that loses an event between 70 ms early and 70 ms late does not ship.

**Why this priority**: the owner wants this result kept. Without a check on every version, a later change to the recognition could lose it silently.

**Independent Test**: run the release check: the offset table is produced and the check fails when one event between 70 ms early and 70 ms late is not recognised.

**Acceptance Scenarios**:

1. **Given** a version about to be published, **When** the release check runs, **Then** the offset table is produced for both engines and kept with the version.
2. **Given** a change that makes one event of the DI recording missed at 50 ms, **When** the check runs, **Then** it fails and names the event, the offset and the reason.

---

### User Story 4 - The guitar drifts, the practice says so (Priority: P3)

During a run, the guitar goes out of tune (a string slips, the temperature changes): its notes come out more and more off, the same way. The practice suggests retuning, and says which string when it can tell. In Wait and Follow it says so without stopping the music or the run. In Arcade, a game where a message would be missed, the run pauses and a window offers: retune (Yes), go on (No), or make Arcade more tolerant (+10 %).

**Why this priority**: the out-of-tune margin stays at what was measured (a wider one would accept the neighbouring fret), so a guitar that drifts ends up with notes refused; the learner must be told the cause is the tuning, not their playing (constitution XIV), and the owner does not want Wait and Follow stopped for it; in Arcade the owner prefers a pause and a choice.

**Independent Test**: replay a recording whose notes are all 25 cents flat, then one 40 cents flat: in Wait and Follow, the suggestion appears during the run, the run and the music go on, and no note is judged differently because of it; in Arcade, the run pauses on the window, and each choice does what it says.

**Acceptance Scenarios**:

1. **Given** a run where the notes heard are steadily off the same way by 20 cents or more (the default, which the user can change), **When** this lasts several notes, **Then** a suggestion to retune appears, naming the string when the notes off are all on one string; in Wait and Follow the run goes on, in Arcade the run pauses on the window of three choices.
2. **Given** that suggestion in Wait or Follow, **When** the learner ignores it, **Then** nothing stops, and it does not come back at every note.
3. **Given** a guitar in tune and a learner who misses a few notes, **When** the run goes on, **Then** no suggestion to retune appears.
4. **Given** the Arcade window, **When** the learner chooses Yes, **Then** the application's tuner opens while the run stays paused, and the run resumes where it paused when the tuner closes; **When** the learner chooses No, **Then** the run resumes where it paused and the window does not come back at every note; **When** the learner chooses "more tolerant", **Then** Arcade's pitch acceptance widens by 10 % (35 → 38.5 cents, never above 45) for this run only (a second choice adds another 10 %), and the run resumes; the next run starts from the usual setting.

---

### Edge Cases

- A note played again while the previous one still rings (no silence between them, as with palm muting): it is a new note only if its attack is heard again.
- A note left ringing longer than the score says: it must not count as the next expected note of the same pitch (story 1, scenario 3).
- The same note picked twice very fast: two attacks 100 ms apart or more (sixteenth notes up to 150 bpm) MUST be counted as two notes; closer ones (tremolo) are not a goal of this spec. The owner's recording needs about 150 ms.
- Strings touched by the picking hand, or a muted string scratched between notes: a noise without a clear pitch must not be taken for a note played again.
- A wrong note played at the time of an expected repeated note: still refused as a wrong note.
- In Wait mode, where the music waits for each note, a note played again is recognised the same way; the offsets do not apply there (no tempo).
- The mixed recording (the same riff after an effect pedal, with drums) stays out of scope: the engines hear the guitar from the cable, before any pedal, and never the backing.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: A note played again at the same pitch, with an attack of its own, MUST be recognised as a new note when the score expects it, with each recognition engine, in every practice mode that judges notes (Wait, Follow and Arcade).
- **FR-001a**: Two attacks of the same pitch 100 ms apart or more MUST be told apart as two notes, with each engine; below 100 ms there is no target.
- **FR-002**: A note held or ringing on, without a new attack, MUST NOT be recognised as a note played again.
- **FR-003**: On the owner's DI recording (tablature `tablature_extrait.gp5`, recording `extrait_offspring_guitare_DI.wav`), every event judged MUST be recognised, in Follow (its 100 events), in Arcade at each of its five levels (the events each level keeps) and in Wait (its 100 events, at offset 0 only: Wait has no tempo), each with that mode's other default settings, when the performance is moved by −70, −50, −40, −30, −20, 0, 20, 30, 40, 50 or 70 ms (negative: early), with each engine. The figures at −100 and 100 ms are reported, without a target.
- **FR-004**: The offset test MUST be kept as an automated test: it replays a recording over its tablature in each mode with its default settings, at each offset and with each engine, aligns the start of the tablature on the recording's own note starts (not on what an engine detects), and writes a table of the mode, the engine, the offset, the share of events recognised and the reasons of the refusals. It MUST accept other recordings with their tablature, so that the owner can add more.
- **FR-005**: The offset test MUST be run before every version is published, and its table kept with the version's report; a version where an event of the DI recording is not recognised between 70 ms early and 70 ms late MUST NOT be published. The recording and its tablature stay on the owner's machine (`tests/tiktok/1`, never committed: an excerpt of a published song); the test is skipped where they are absent (every other build, the CI) and run on the owner's machine before each release, its table going into the release's report.
- **FR-006**: No other recognition result MAY get worse: every dataset report and every committed test of the recognition, with each engine, gives figures at least as good as before this spec, in particular wrong notes refused, chords with a note missing refused, and no note accepted before its own attack (owner's rule: the recognition must not get worse on any other test).
- **FR-007**: Every new value that decides what the recognition hears or accepts MUST be an external parameter with its default, bounds, unit and description, documented with the engines (owner's rule, spec 026); the settings changed by this spec are listed with their former and new defaults.
- **FR-007a**: When in doubt, the recognition MUST lean towards accepting: a note a little off pitch (within FR-007b), a little early or a little late is judged played, in every mode (owner's principle, 2026-11-11). Where this principle and an existing refusal test disagree, the work stops and the case is reported to the owner, the committed test being left unchanged.
- **FR-007b**: The acceptance of a note out of tune stays as it is: ±35 cents by default (setting 10 to 45), plus the extra sharp margin on high frets up to 49 cents; the neighbouring fret stays a wrong note in every mode (spec 016). The only exception is the learner's own choice in Arcade's retune window (FR-009): +10 % per choice for that run only, never above 45 cents, never saved.
- **FR-008**: A note judged missed on a repeated note MUST still give the reason the learner sees today ("no attack heard" or the like); it MUST NOT be presented as a wrong note (constitution XIV).
- **FR-009**: When the notes heard during a run are steadily off pitch the same way, the practice MUST suggest retuning the guitar (naming the string when the notes off are on one string) in every mode: in Wait and Follow without pausing the run, the playback or the judging; in Arcade by pausing the run and showing a window with three choices: Yes (the application's tuner opens; when it closes, the run resumes where it paused), No (resume), and make Arcade more tolerant (its pitch acceptance +10 % by default, an external parameter (FR-007), for this run only, cumulative, never above 45 cents, then resume); the suggestion MUST NOT repeat at every note, and MUST NOT appear for a guitar in tune with a few notes missed. It is triggered from a steady average gap of 20 cents by default, the same way over several notes; this threshold is a setting the user can change, with its bounds, and the other thresholds are external parameters (FR-007).

### Key Entities

- **Offset test case**: a recording of the guitar alone, its tablature, the track played, and the offsets to replay; ships with its expected figures (100 % from 70 ms early to 70 ms late for the owner's DI recording).
- **Arcade retune offer**: during one Arcade run, the window's state (playing, offered, tuning) and the number of "more tolerant" choices; it sets the run's pitch acceptance and is forgotten at the end of the run.
- **Offset table**: for each mode (and Arcade level), each engine and each offset, the events recognised out of the events of the score, and the count of each refusal reason.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: On the owner's DI recording, every event judged is recognised (100 of 100 in Follow and in Wait at 0 ms; all those kept by each Arcade level, 1 to 5), with the modes' other default settings, at 0, 20, 30, 40, 50 and 70 ms late (Follow today: 51, 49, 41, 38, 31 and 30; Arcade not measured yet) and at 20, 30, 40, 50 and 70 ms early (not measured yet; measured before the work starts), with each engine.
- **SC-002**: Every dataset figure of the recognition, with each engine, is at least as good as before this spec (no regression), in particular wrong notes and incomplete chords still refused.
- **SC-003**: The offset table of every published version is in its report, and a version below SC-001 is not published.
- **SC-004**: A held note expected twice by the score is recognised once, not twice, with each engine, on synthetic plucks and on sheet F18 played live.
- **SC-005**: The same note played again 100 ms after itself, both expected by the score, is recognised twice, with each engine.
- **SC-006**: With the default threshold, on a recording steadily 20 cents or more off the same way, the suggestion to retune appears during the run, without stopping it in Wait and Follow, as a paused window in Arcade; on the dataset recordings whose notes lie within 10 cents of their written pitch on average, it never appears.

## Assumptions

- "Recognised" is the verdict of a practice run in a given mode with that mode's default settings (for Follow: time to play 600 ms, permissiveness Permissive, no calibration); the default settings of a mode MAY be made more permissive to reach SC-001 (FR-007a), each change listed (FR-007). The offset stands for the player being late (positive) or early (negative) by that much on every note; the system's own delay is otherwise compensated by the calibration.
- The tablature matches what the DI recording plays, note for note; an event found to be wrong in the tablature is corrected with the owner before it counts as a failure.
- The offset 0 is where the tablature's attacks fall on the rises of the recording's energy, measured on the sound itself, so that no engine is judged on its own detection.
- Both engines are expected to reach SC-001; if one cannot without a regression elsewhere, the work stops and the figures are reported to the owner.
- The mixed recording (with the effect pedal and drums) is not a goal of this spec.
