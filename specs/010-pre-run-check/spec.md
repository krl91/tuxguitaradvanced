# Feature Specification: Pre-run check (tuning and input level at every start)

**Feature Branch**: `feature/interactive-practice` (spec dir `010-pre-run-check`)

**Created**: 2026-10-14

**Status**: Draft

**Input**: Owner's request (2026-10-14), after chords of the owner's guitar were not recognised in *Wait for the right note*: "at every start of a mode, ask whether the guitar is in tune; if yes go on, if not start the tuner; and check the gain at every start".

**Second evidence, the online data set** (GuitarSet 1.0.1, Zenodo 10.5281/zenodo.1422265, 180 clean microphone recordings, kept outside the repository, `~/tuxguitar-datasets`): the worst 300 ms window of any of them has **0.23 %** of its samples at full scale (26 of the 180 touch the peak at least once), so a threshold under 0.23 % would give false warnings on good recordings, and a threshold of 2 % would let 4 of the owner's 21 clipped takes through (0.0 %, 0.2 %, 0.6 % and 0.8 % in their first 300 ms). **0.5 %** separates the two: no false warning on the 180 recordings (margin of more than 2), 19 of the 21 clipped takes caught.

**Evidence** (owner recordings through the Rocksmith USB adapter, 42 takes, `specs/004-attack-difference-recognition/benchmark/owner-chords*`, test `OwnerChordsBenchmarkTest`): with the input gain of the interface at 0.99, **20 of 21 takes clipped** (2 % to 28 % of the samples in the first 300 ms after the strum, the whole peak at full scale) and only **8 of 21** chords played were accepted; with the gain lowered to 0.40 nothing clipped and **18 of 21** were accepted. The level check of the application did not warn: it flags clipping only when 10 % of the samples of one analysis frame are at full scale.

## Clarifications

### Session 2026-10-14

- Q: How is the input level measured at every start of a mode? → A: **Option A**: one start window asks the tuning question and a **test strum**, with a live level indicator; the mode starts when the level is good (or the learner goes on anyway). No check is left to the first seconds of the run, and the existing "play a few notes" state of the preparation window is not enough by itself.
- Q: Does a bad level stop the start or only warn? → A: **Option A**: the mode does not start while the level is bad, and a **"Continue anyway"** button lets the learner go on for this start only (FR-002, FR-008 confirmed; the choice is never stored).
- Q: Where do the tuning question and the test strum appear? → A: **Option A**: one **small dedicated start window**, the same for the three modes and for both ways of starting (preparation window and transport bar), opened at every start; it holds the tuning question with two answers (*Yes, in tune* / *No, open the tuner*), the live level indicator with "strum a chord", and *Continue anyway* when the level is bad.
- Request (owner, same session): add **non-regression tests for the recognition** of notes, techniques (bend and the others) and chords, so that this feature and the next ones cannot degrade them: FR-012 and SC-007.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - I am told when my signal is too loud or too weak before I play (Priority: P1)

A learner starts a practice mode (*Wait for the right note*, *Follow* or *Arcade*). Before the run begins, the application checks the input signal and, when it clips or is too weak, says so in plain words with what to do (lower or raise the gain of the interface or the input volume of the system), instead of letting the learner play with a signal that the recognition cannot use.

**Why this priority**: it is the measured cause of the failure that started this specification (38 % of chords accepted when clipping, 86 % when not).

**Independent Test**: play the recorded takes of `owner-chords` (gain 0.99) and `owner-chords-2` (gain 0.40) as the input of the check: the first are reported as too loud, the second are not.

**Acceptance Scenarios**:

1. **Given** an input that clips on the strum, **When** the learner starts a mode, **Then** the check reports that the signal is too loud, names the gain of the interface and the input volume of the system as the two things to lower, and the mode does not begin until the learner has checked again or chosen to go on anyway.
2. **Given** an input that is too weak to be recognised, **When** the learner starts a mode, **Then** the check reports it with what to raise.
3. **Given** a well-set input, **When** the learner starts a mode, **Then** the check takes no extra step: the run begins.
4. **Given** the recorded takes of the owner at gain 0.99, **When** they are checked, **Then** at least 19 of the 21 are reported as clipping, and none of the 21 takes at gain 0.40 are.

---

### User Story 2 - I am asked whether my guitar is in tune (Priority: P1)

At every start of a mode the learner is asked whether the guitar is in tune. If yes, the run goes on at once. If not, the tuner opens; when the learner is done tuning, the learner comes back to the start of the mode.

**Why this priority**: a guitar out of tune makes every note and chord fail the recognition, and the learner cannot tell why; the owner asks for the question at every start.

**Independent Test**: start each of the three modes, answer yes, then start again and answer no: the first goes on without the tuner, the second opens the tuner and, once closed, offers the start again.

**Acceptance Scenarios**:

1. **Given** a start of a mode, **When** the learner answers that the guitar is in tune, **Then** the run goes on (after the level check of story 1) with nothing else asked.
2. **Given** a start of a mode, **When** the learner answers that the guitar is not in tune, **Then** the tuner opens and the mode does not begin.
3. **Given** the tuner opened by this question and closed by the learner, **When** it is closed, **Then** the learner is back at the start of the mode, with the same choices, and can start it.
4. **Given** the same learner starting a mode again later in the session, **When** the start is requested, **Then** the question is asked again (it is not remembered).

---

### User Story 3 - A learner in a hurry can go on without losing the safeguards (Priority: P2)

The two checks must be quick and must not become a wall: answering yes to the tuning question and having a good level takes at most a few seconds, and a learner who knows what they are doing can go on past a level warning on their own responsibility.

**Why this priority**: the checks run at every start; if they annoy, they will be ignored or switched off, and the evidence shows they matter.

**Independent Test**: time a start with a good input and a tuned guitar; start with a warning and choose to go on anyway.

**Acceptance Scenarios**:

1. **Given** a tuned guitar and a well-set input, **When** the learner starts a mode, **Then** the added steps take one answer and no more than 5 seconds of checking.
2. **Given** a level warning, **When** the learner chooses to go on anyway, **Then** the mode begins and the choice is not stored for the next start.

### Edge Cases

- The input is lost or no sound arrives at all during the check: the existing messages for these cases are shown (no signal, device lost, microphone access refused) and the mode does not begin.
- The learner plays nothing during the check: the window says it heard nothing and keeps asking for a strum; it never decides a level from silence, and the mode does not begin on silence unless the learner goes on anyway (FR-008).
- The check is asked while a mode is already running or while a calibration runs: it is not repeated inside a run.
- The tuner is already open when a mode is started: the question is still asked, and answering no brings the already open tuner forward.
- The Arcade button of the transport bar, which starts at once on the track under the cursor, goes through the same two checks.
- The learner has no instrument connected at all: the checks do not claim anything about the guitar; the existing "no input" messages apply.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: At every start of *Wait for the right note*, *Follow* and *Arcade*, from the preparation window as well as from the buttons of the transport bar (including the Arcade button that used to start at once), the application MUST open one small dedicated start window (the same for the three modes and for both ways of starting) that asks the tuning question (FR-005) and asks for a **test strum**, with a live level indicator; the level is judged on that strum before the run begins. Nothing is remembered from one start to the next.
- **FR-002**: When the input signal clips, the check MUST say so and say what to lower (the gain of the interface, the input volume of the system), using the existing wording of the level guidance; the mode MUST NOT begin until the learner checks again with a good result or chooses to go on anyway.
- **FR-003**: When the input signal is too weak to be recognised, the check MUST say so and say what to raise, with the same rule as FR-002.
- **FR-004**: The clipping decision MUST catch the case measured by the owner: the share of samples at full scale in the 300 ms after a strum, at or above **0.5 %**, is clipping (2 % was the first proposal; the data set above shows it misses 4 of the 21 measured takes). A signal with no sample at full scale is never reported as clipping.
- **FR-005**: At every start of those modes the application MUST ask whether the guitar is in tune and MUST NOT remember the answer from one start to the next.
- **FR-006**: When the learner answers that the guitar is in tune the run MUST go on (after FR-001 to FR-004); when the answer is no the tuner MUST open and the mode MUST NOT begin.
- **FR-007**: When the tuner opened by FR-006 is closed, the learner MUST be back at the start of the mode with the same choices; the mode MUST NOT begin by itself.
- **FR-008**: A learner who has been warned about the level MUST be able to go on anyway; that choice applies to this start only.
- **FR-009**: With a good level and a tuned guitar the two checks MUST add one answer and at most 5 seconds before the run begins.
- **FR-010**: The texts of the question, of the answers and of the level messages MUST exist in English, French and Spanish.
- **FR-012**: The recognition of single notes, of the techniques (bend, vibrato, slide, hammer-on, pull-off and the others already recognised) and of chords MUST be covered by non-regression tests that run with this feature and fail if their results get worse: the existing recorded-audio and data-set tests (real-guitar regression, monophonic and bend replays, technique data set, GuitarSet chords, the owner's takes) are kept in the gate with their figures recorded before the change, and any recognition test that is missing for a kind of event (note, each technique, chord) is added.
- **FR-011**: The checks MUST NOT change the operating-system or interface gain by themselves, and MUST NOT change how notes and chords are recognised (spec 004, T007) or judged.

### Key Entities

- **Pre-run check**: the sequence done at each start: level, then tuning question; its outcome is *go on*, *level to fix* or *tuner opened*; it keeps nothing between two starts.
- **Level finding**: clipping, too weak, no signal, good; it comes from the existing level analysis, with the clipping decision of FR-004.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: On the 42 recorded takes of the owner, the check reports clipping for at least 19 of the 21 takes at gain 0.99 and for none of the 21 takes at gain 0.40.
- **SC-002**: In all three modes and from both ways of starting (preparation window and transport bar), a start always passes through both checks (tested for each).
- **SC-003**: With a good level and an "in tune" answer, the added time before the run is at most 5 seconds and the added steps are one answer.
- **SC-004**: Answering "not in tune" opens the tuner 100 % of the time and the mode never begins in that case; closing the tuner brings the learner back to the start.
- **SC-005**: No setting, judgement, score or recognition result changes for a run started after the checks (the same recorded run gives the same judgements with and without the checks).
- **SC-007**: After the change, every recognition figure recorded before it (notes, each technique, chords on GuitarSet and on the owner's takes) is equal or better, shown in a before and after file like `specs/009-arcade-flames/benchmark/before-after.txt`.
- **SC-006**: The owner confirms on the built application, with the Rocksmith adapter at the gain that clipped (0.99), that the warning appears and tells what to do, and that after lowering the gain the chords of `basic_test.tg` are recognised more often.

## Assumptions

- The level analysis, the guidance texts (`practice.level.guidance.*`) and the tuner already exist in the practice plugin and the audio engine; this specification adds when they are used and one threshold, not a new analysis.
- The measurements above were made by the model on the owner's recordings, with chord pitches read by a polyphonic transcription tool (Basic Pitch) and not by the owner; they are evidence for the threshold, not a labelled benchmark.
- The three design questions of the first draft are answered in Clarifications; nothing is left open.
- Out of scope: the recognition algorithm for chords (spec 004 T007), automatic change of the system gain, a stored "I already tuned today" answer, and the Windows and Linux interface differences (the guidance names the macOS input volume as the existing text does).
