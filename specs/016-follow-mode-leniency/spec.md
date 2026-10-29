# Feature Specification: Follow the tempo, made playable: why a note is refused, a calibrated route, a more permissive judging

**Feature Branch**: `feature/interactive-practice` (the spec directory is `016-follow-mode-leniency`)

**Created**: 2026-10-29

**Status**: Draft

**Input**: User description: "Follow-the-tempo mode (and the timed judging it shares with Arcade) is too strict, both for the note and for the moment it is played. Owner's trial of 2026-10-29 (test sheet specs/test-sheets/F01, step 3, spec 002 T102): with the Rocksmith USB adapter, rhythm NOT calibrated, timing window 100 ms, tempo 70 %, it was "too difficult to play the right note at the right time; the note recognition engine is not permissive enough, for the note as for the moment the note must be played". Known context to start from: spec 012 (adjustable timing window 40-200 ms) did not make the default feel less hard, the owner's problem there was notes not recognised (Missed); spec 014 added a three-position leniency to Arcade only and measured that it changes nothing on the reference recording (the verdict delay belongs to the attack detector); the numbers-only diagnostic trace (tuxguitar.practice.trace, PracticeTrace) exists only for the wait mode, not for Follow, so today nobody can tell why a note was rejected in Follow. Goal: (1) first make it measurable why each event is rejected in Follow (pitch not recognised, no attack detected, onset outside the window, constant offset of an uncalibrated route), from the owner's own play-through, without asking the owner for new audio recordings; (2) then make Follow more permissive for the pitch and for the timing, so that a learner who plays the right note roughly in time is accepted, with an explicit bound on how many wrong notes may then be accepted (measured on the existing reference recording and negative tests); (3) make the uncalibrated case visible and easy to fix before a run. Wait mode and the tuner must stay unchanged. Owner decides the exact permissiveness targets during clarification."

## Why this exists

*Follow the tempo* plays the score at its tempo and judges each note or chord at its written time. It is the mode that must be validated before Arcade can be released (spec 003, T001). At the owner's first real trial (2026-10-29), with the guitar through the Rocksmith adapter, it was **too hard to get a note accepted**: the right note played roughly in time was refused, for the pitch as for the moment.

Three facts frame the problem:

1. **Nobody can say today why a note was refused in this mode.** The diagnostic trace of the wait mode does not cover it, and the feedback says only *missed* or *wrong note*. Widening the timing window (spec 012) did not help the owner, and the leniency added to Arcade (spec 014) measured no change on the reference recording, which is a clean take. A permissive setting cannot be tuned blind: the reasons must be measured first, on the owner's own play-through.
2. **The trial was made with an uncalibrated route.** Without the rhythm calibration, every note can be judged with the same offset. The application says "reduced rhythmic precision" but does not offer to fix it at the moment it matters, before a run.
3. **Follow was kept strict on purpose by spec 014** (its FR-004: Arcade only). This feature reverses that decision for Follow, at the owner's request, with a stated limit on the wrong notes it lets through.

## Clarifications

### Session 2026-10-29

- Q: May the record of a run also keep a copy of the run's audio? → A: **Yes, only when a box is ticked**, kept outside the project in the owner's home folder, as the diagnostic copy of the calibration (spec 013); it lets a setting be replayed and measured on the owner's own playing. (Chosen by the owner among: an opt-in copy of the audio, numbers only.)
- Q: Is the permissiveness of Follow the same setting as Arcade's leniency? → A: **A separate setting of Follow**, with its own default; Arcade keeps its own setting and its figures. (Chosen by the owner among: separate, one shared setting.)
- Q: How many clearly wrong notes may Follow accept at its default position? → A: **At most 5 %** of the 98 negative tests of the reference recording, **at most 4** (today 0), the same limit as Arcade. (Chosen by the owner among 2 %, 5 %, 10 %.)
- Q: At a permissive position, does a near-miss include the neighbouring semitone (F played for E)? → A: **No.** A near-miss is the right note a little out of tune, or a sound not clear enough to be sure; the neighbouring semitone stays a wrong note at every position, as in Arcade and as spec 001 requires. (Chosen by the owner among: never the semitone, the semitone at the most permissive position only, the semitone by default.)
- Q: How is the moment of a note judged in Follow? → A: (owner's own rule) **The window opens when the note turns red** (its written time) and the note is accepted if it is **played within a time to play it, 300 ms by default and adjustable**, measured from the turn to red to the moment the note is played (see the next answer); a note played **up to about 50 ms before** it turns red still counts and is marked early; when the next note comes sooner, the window **ends 20 ms before the next note turns red**, so that two windows never overlap (the time is then proportional to the gap to the next note). In Follow this **replaces** the *timing window* slider of spec 012 (±40 to 200 ms around the written time) by one slider *time to play the note*, **100 to 600 ms, 300 by default**; Arcade keeps today's slider. (The owner's answer to the question on the positions of the permissiveness, then two confirmations: a small advance is accepted; the slider replaces the old one in Follow only.)
- Q: How many positions has the permissiveness of the pitch in Follow, and which is the default? → A: **Three: Strict (today's pitch judging), Permissive (the default), Very permissive**, the same shape as Arcade's but a separate setting. (Chosen by the owner among: three with the middle by default, two, none.)
- Q: After *Go on without* the rhythm calibration, is the offer made again at the next start? → A: **Once per launch**: not again for that input and output until the application is closed; again at the next launch or when the input or the output changes. (Chosen by the owner among: once per launch, at every start, never again with a box.)
- Q: In the owner's timing rule, which moment counts for the 300 ms: the moment the note is played or the moment it is detected (about 105 ms later, up to 170 ms)? → A: **The moment the note is played** (its attack), corrected for the delay of the detector and of the audio route, as the mode does today: the learner really has 300 ms to play, the same on every machine and input. (Asked at /speckit-plan; chosen by the owner among: the moment played, the moment detected.)

### Session 2026-10-29 (gate G2)

- Q: After gate G2, is the time to play the note long enough? → A: **No: twice as long by default.** The owner's three recorded runs after calibration (Rocksmith, 100 %) show the right pitch heard almost every time, but 400 to 760 ms after the red; the pitch positions cannot change that. The time to play becomes **600 ms by default, adjustable from 100 to 1200 ms**; it is still cut 20 ms before the next red, and the on-time part stays half of it. (Chosen by the owner among: 600 by default with 1200 at most, 600 by default with 600 at most, no change.) The three tests that pinned 300 and 600 are adapted on those values only (owner's exception of 2026-10-29, like T028).
- Q: The start window says "or set it by hand" without saying where; what should it do? → A: **Say where**: in the Mode tab, *Delay (ms)*. (Chosen by the owner among: say where, a delay field in the start window.)

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Know why a note was refused (Priority: P1)

The learner plays a passage in *Follow the tempo*. For each note or chord that is not accepted on time, the run says **why**, in words a guitarist understands: no attack was heard, a sound was heard but its pitch was another note (which one), the right note was heard but too early or too late (by how much), or the right note was heard but not clearly enough to be sure. At the end, the summary counts the refusals by reason. The owner can also keep a record of the run, in numbers, to send to the developer, so that the reasons can be measured on the owner's own playing.

**Why this priority**: every other change of this feature depends on knowing which reason dominates. Without it, a more permissive setting is guesswork (spec 012 and spec 014 both changed a setting that turned out not to be the cause).

**Independent Test**: play a short score in Follow and do on purpose one of each: a note not played, a wrong note, a right note very late, a right note very soft; each is shown with its own reason, and the summary counts one of each.

**Acceptance Scenarios**:

1. **Given** a run in Follow, **When** an event passes its window with no attack heard, **Then** it is shown as missed with the reason "no attack heard".
2. **Given** a run in Follow, **When** the learner plays another pitch in the window of an event, **Then** the event is shown as a wrong note naming the expected and the heard pitch.
3. **Given** a run in Follow, **When** the right pitch is heard outside the window, **Then** the feedback says early or late and by how many milliseconds, and that the note was right.
4. **Given** a run in Follow, **When** a sound is heard in the window but its pitch cannot be told with enough certainty, **Then** the reason says the sound was not clear enough, not that it was wrong (constitution XIV: uncertain is not an error).
5. **Given** the end of a run, **When** the summary is shown, **Then** it counts the refusals by reason.
6. **Given** the owner chose to keep a record of the run, **When** the run ends, **Then** a file is written that holds, for each event, its expected time and pitch, what was heard and when, and the reason of the verdict; and, **only if the owner ticked a box for it**, a copy of the run's audio, kept outside the project in the owner's home folder (as the diagnostic copy of the calibration, spec 013 FR-020), so that a change of setting can be replayed and measured on the owner's own playing.

---

### User Story 2 - The route is calibrated before it matters (Priority: P1)

Before a run of *Follow the tempo* or Arcade, if the rhythm of the current input and output has never been calibrated, the start window says so in one sentence and offers to calibrate now, in one click, or to go on without it. A route calibrated once is not asked again; after *Go on without*, it is not asked again for that route until the application is closed.

**Why this priority**: the owner's trial was made without calibration; a constant offset of the route makes every note early or late, and the cheapest fix is to calibrate. It is independent of the leniency and can be shipped alone.

**Independent Test**: with a route never calibrated, start Follow: the start window offers the calibration; calibrate; start again: it is not offered any more; change the input: it is offered again.

**Acceptance Scenarios**:

1. **Given** a route never calibrated, **When** Follow or Arcade is started, **Then** the start window says that the rhythm is not calibrated for this input and output and offers **Calibrate now** and **Go on without**.
2. **Given** the learner chooses **Calibrate now**, **When** the calibration ends with a result, **Then** the run begins with that calibration; **when** it is cancelled or fails, **then** the start window is back with the same choice.
3. **Given** a route already calibrated, **When** a run is started, **Then** nothing about the calibration is asked.
4. **Given** the learner chose **Go on without**, **When** another run is started on the same route in the same launch, **Then** nothing is asked; **when** the application is launched again or the input or the output changes, **then** it is offered again.
5. **Given** the wait mode, **When** it is started, **Then** nothing about the calibration is asked (the wait mode does not judge rhythm).

---

### User Story 3 - Follow accepts a right note played roughly in time (Priority: P2)

The learner plays the right note at about the right moment, as a guitarist practising a piece does. *Follow the tempo* accepts it: the judging of the pitch tolerates a sound that is close to the expected note, and the judging of the time gives the learner a time to play the note from the moment it turns red (600 ms by default since gate G2, adjustable). The learner can choose how permissive it is, and the choice is remembered. A clearly wrong note is still refused, within a stated limit.

**Why this priority**: it is what the owner asked for, but its settings must come from the measures of story 1, so it comes after.

**Independent Test**: on the reference recording replayed in Follow, and on the owner's recorded run (story 1), compare the default position with the strict one: more right notes accepted, and no more wrong notes accepted than the limit.

**Acceptance Scenarios**:

1. **Given** the default settings, **When** the learner plays the right pitch slightly out of tune, and it is played 250 ms after the note turned red, **Then** it is accepted.
2. **Given** two notes 200 ms apart, **When** the first one is played 190 ms after it turned red, **Then** it is not accepted for the first note (its window ended 20 ms before the second turned red) and can count for the second one.
3. **Given** any position, **When** the learner plays a clearly wrong pitch, the neighbouring semitone included, **Then** it is refused, within the limit of SC-004.
4. **Given** the Strict position, **When** a run is judged, **Then** the pitch of every event is judged exactly as before this feature (the same recognition settings); its time is judged by the time to play (FR-011), which has no position "as today".
5. **Given** a change of position or of the time to play during a run, **When** it is made, **Then** it applies to the next run.
6. **Given** the default settings (since gate G2), **When** the learner plays the right pitch 550 ms after the note turned red, with no other note within that time, **Then** it is accepted, marked late.
6. **Given** a finished run, **When** the summary is shown, **Then** it names the position and the time to play used.

### Edge Cases

- **Silence is never accepted**: a sound must have been heard for an event to count as played, at every position.
- **Two events close together**: a permissive window must not let one attack be counted for two events.
- **A chord**: the permissiveness applies to a chord as one event; a chord with a string missing keeps the behaviour of today (spec 004 T007 is not solved by this feature).
- **A route calibrated, then the input or the output changed**: it counts as never calibrated (the calibration is kept per input, output and format, spec 002 FR-054).
- **A saved position missing or out of range** falls back to the default without an error.
- **The record of a run cannot be written** (disk full, folder missing): the run is not affected and the learner is told once.
- **Arcade** is not changed: its leniency is a separate setting (Clarifications, Q2) and its "like Practice" position keeps today's judging.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: In Follow, every event not accepted on time MUST carry one **reason** among a fixed list: no attack heard; other pitch heard (naming it); right pitch, early or late (with the offset); sound not clear enough; not judged (pause, input lost). The list is the same in the run feedback, in the summary and in the record of a run.
- **FR-002**: The summary at the end of a Follow run MUST count the refusals **by reason**.
- **FR-003**: The learner MUST be able to ask for a **record of a run** of Follow, written to a file outside the project, holding per event the expected time and pitch, what was heard (pitch, certainty, level, attack time) and the reason of its verdict. It is off by default and MUST NOT change any verdict. A copy of the run's **audio** MUST be written only when the learner ticks a separate box, outside the project. Neither the record nor the audio copy is written for a run that was cancelled before its count-in ended.
- **FR-004**: Before a run of Follow or Arcade on a route that has **no rhythm calibration**, the start window MUST say so and offer to calibrate now or to go on; a calibrated route MUST NOT be asked; after *Go on without*, the same route MUST NOT be asked again until the application is closed; the wait mode MUST NOT be asked. When the calibration fails there, the message MUST say where the delay can be set by hand (the Mode tab, *Delay (ms)*).
- **FR-005**: Follow MUST offer a **permissiveness** setting with **three positions: Strict** (today's pitch judging: the recognition settings unchanged), **Permissive** (the default) and **Very permissive**; for the **pitch** (a **near-miss** counts as the expected note: the right note a little out of tune, or a sound not clear enough to be sure; the **neighbouring semitone is never a near-miss**, at any position). The **time** is judged by FR-011, not by the positions. It is a **setting of Follow only, separate from Arcade's leniency** (spec 014): Arcade keeps its own setting, its positions and its figures.
- **FR-006**: The permissiveness MUST never accept silence and MUST never count one attack for two events.
- **FR-007**: The summary and the record MUST name the permissiveness position and the time to play used.
- **FR-008**: The **wait mode, the tuner and the calibration windows** MUST give exactly the same results as before this feature.
- **FR-009**: The judging MUST stay **deterministic**: the same performance with the same settings gives the same verdicts.
- **FR-010**: All new texts MUST exist in English, French and Spanish, and the practice guides MUST describe the reasons, the calibration offer and the permissiveness.

- **FR-011**: In Follow, the time of an event MUST be judged from the moment it **turns red** (its written time): it is accepted when the note is **played** (its attack, corrected for the delay of the detector and of the audio route) no later than the **time to play the note** after it turned red (**600 ms by default**, adjustable from **100 to 1200 ms**, remembered; changed from 300 / 600 after gate G2), and no earlier than **50 ms before** it turned red (then marked early); when the next event turns red sooner, the window of an event MUST end **20 ms before** that moment, so that no two windows overlap. This slider **replaces** the *timing window* of spec 012 **in Follow only**; Arcade keeps the timing window of spec 012 unchanged.

### Key Entities

- **Refusal reason**: why an event was not accepted on time; one of the fixed list of FR-001, with its detail (heard pitch, offset in ms).
- **Run record**: the per-event account of one Follow run (expected, heard, reason, settings used), written only on request.
- **Permissiveness position**: the chosen pitch tolerance of Follow: Strict (today's pitch judging), Permissive (default), Very permissive; remembered between launches. The plan and the code call it Follow's *leniency* (`FollowLeniency`), as Arcade's; the learner reads "permissiveness" in the texts.
- **Time to play the note**: the time after a note turns red within which its attack counts (100 to 1200 ms, 600 by default; 300 / 600 before gate G2), cut 20 ms before the next note turns red; remembered between launches.
- **Route calibration**: the measured delay of one input + output + format (existing, spec 002), now checked before a run.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: **100 %** of the events not accepted on time in a Follow run have a reason from the fixed list, on the reference recording replayed in Follow and on synthetic runs that contain each case.
- **SC-002**: From one play-through of the owner with the record on, the share of each refusal reason is reported, **before** any setting of story 3 is chosen.
- **SC-003**: At the default position, at least **90 %** of the events that the owner played with the right pitch and that were played within 300 ms after they turned red are accepted, measured by replaying the owner's recorded run (story 1, with its audio) at each position (the figure for the strict position is reported next to it).
- **SC-004**: At the default position, at most **5 %** of the clearly wrong notes of the **98 negative tests of the reference recording** are accepted, i.e. **at most 4** (today: 0), the same limit as Arcade (spec 014 SC-002); the figure of every position is reported.
- **SC-005**: A learner with an uncalibrated route reaches the end of a calibration from the start window in **one click and under 15 seconds**, and the offer appears before **100 %** of the runs of Follow and Arcade on such a route.
- **SC-006**: The wait mode, the tuner and the calibration give **0 differences** on the existing recognition reports.
- **SC-007**: The owner reruns test sheet F01 step 3 at the default settings (time to play the note 600 ms since gate G2, tempo 70 %, route calibrated) and judges that a right note played roughly in time is accepted.

## Assumptions

- In Follow the time to play the note (FR-011) replaces the timing window of spec 012; the tempo range of spec 012 stays as it is; Arcade keeps its timing window.
- "Without asking for new audio recordings" means that the owner is not asked to make a dedicated recording session; a play-through of the owner with the record on (story 1) is part of normal use and is in scope. The owner agreed (Q1) that this record may hold the run's audio when a box is ticked.
- The detector's own delay (about 105 ms median, already compensated in the onset time) is not changed by this feature: spec 014 measured that no relaxable setting shortens it.
- Arcade is out of scope: its leniency stays separate and its "like Practice" position keeps today's judging, so Arcade's figures (spec 014 SC-002, SC-003) stay as they are. Story 2 (the calibration offer) does apply to Arcade's start.
- The chord with a string missing (spec 004 T007) and the bass below G1 (spec 003) stay out of scope.
- The boxes that ask for a record of a run and for its audio copy are off at each launch (a diagnostic is asked for on purpose, not left on).
- macOS is the validated platform; Windows and Linux are built by CI only.
