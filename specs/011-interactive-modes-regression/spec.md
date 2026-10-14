# Feature Specification: Functional non-regression tests for the interactive modes

**Feature Branch**: `feature/interactive-practice` (the spec directory is `011-interactive-modes-regression`; the branch is the one the previous specs used)

**Created**: 2026-10-14

**Status**: Draft

**Input**: User description: "Create functional non-regression tests, the application is quite good now, aim for good coverage of the interactive modes, follow the Spec Kit approach" (owner, 2026-10-14)

## Why this exists

The application now has three interactive modes (**Wait for the right note**, **Follow the tempo**, **Arcade**), a start path in front of them (the start window of spec 010: tuning question, test strum, tuner way back, quick start, transport-bar button, restarts and replays), stage flames (spec 009) and a preparation window. Most of the *rules* inside the modes are well tested. What is **not** tested is how the pieces behave **together, the way a learner meets them**.

The proof is recent: after every test was green, the owner opened the built application and found by hand **three bugs in the start path** — the preparation window came over the start window before the question was answered, the tuner refused to open from the start window, and the start window was empty after the answer "yes". The tests had checked the pieces and the *text* of the controlling code, not what the learner sees and can do. This feature builds the safety net that would have caught those three, and the next ones.

## Clarifications

### Session 2026-10-15

- Q: To test the real practice window, does the owner accept a small declared change in the production code that isolates the window's few accesses to the application behind an interface, with no change of behaviour? → A: Yes (option A). The tests build the real window; the existing suites prove that nothing changed.
- Q: Does the owner want a code-coverage figure for the interactive packages besides the journey matrix? → A: Yes, reported without a minimum (option B): lines and branches per interactive package, measured before and after, in a profile separate from the default build.
- Q: How much time may the new tests add to the default build, the rest going to tagged suites run only on request? → A: Less than 2 minutes (option A, about +20 % on the 9 min 14 s of the practice suite today); native windows, the 20-run stability check and the coverage measure go to tagged suites run on request and before each release.

## User Scenarios & Testing *(mandatory)*

The "user" of this feature is the **owner / maintainer** who changes the application and needs to know, before releasing, that what worked yesterday still works. The learner's journeys below are what must be protected; each story is a set of journeys that a test can replay and that **fails when the behaviour breaks**.

### User Story 1 - The start of any mode behaves as the learner expects (Priority: P1)

Whatever way the learner starts a mode (the Start button of the preparation window, the Practice button of the transport bar, the coloured Arcade button) and whichever mode is chosen, the same start window comes first, answers lead where they should, and nothing unexpected appears or blocks.

**Why this priority**: this is where the three bugs found by hand were, it is the door to every mode, and it is the part no test could reach.

**Independent Test**: replay each start journey on the application's real window flow (not on its pieces) and check what is shown, what is enabled, and whether the mode began.

**Acceptance Scenarios**:

1. **Given** a track is chosen, **When** the learner starts any of the three modes from any of the three entry points, **Then** the start window opens first, alone, and the mode does not begin until the learner is ready.
2. **Given** the start window shows the tuning question, **When** the learner waits longer than the quick-start patience, **Then** no other practice window comes over it.
3. **Given** the learner answers "No, open the tuner", **When** the tuner opens and is closed, **Then** the question comes back, the mode never began, and the preparation window did not come up by itself.
4. **Given** the learner answers "Yes", **When** the strum part is shown, **Then** every shown part has a real size and is readable (no empty window, nothing outside the window), at the window sizes used elsewhere.
5. **Given** a good strum, a clipping strum, a too-weak strum and no strum, **When** each happens, **Then** the mode begins, is blocked with advice, is blocked with advice, or waits, in that order; *Continue anyway* goes on for that start only.
6. **Given** a start window is open, **When** the learner presses Start again or the transport-bar button, **Then** there is still one window and the mode starts once.
7. **Given** the learner cancels (button, close, Escape), **When** a new start is asked, **Then** it is accepted at once and nothing was left open or held (capture, listeners, tuner).

---

### User Story 2 - A whole run of each mode, from start to result (Priority: P1)

For each mode, one journey replays the learner's whole session on the application's real flow with a played-back performance: start, count-in, notes and chords judged, a mistake, a pause and resume, the end, and the result the learner is shown.

**Why this priority**: the rules are tested one by one, but nothing proves that the same modes still behave end to end when assembled, and each mode is what the learner pays attention to.

**Independent Test**: one scripted performance per mode on a known score gives a known result (accepted/missed counts, score and streak for Arcade, summary for the others), compared with a recorded expected result.

**Acceptance Scenarios**:

1. **Given** a known score and a perfect played-back performance, **When** each mode runs to the end, **Then** its result is the expected maximum (all accepted; Arcade at its maximum score and full streak).
2. **Given** a performance with right, wrong, late and missing notes, **When** each mode runs, **Then** each event gets the expected verdict and the mode's rules about retries, passages and summary hold.
3. **Given** a pause and a resume in the middle, **When** the run goes on, **Then** nothing is judged during the pause or the resume count-in and the rest of the run is judged as before.
4. **Given** Wait mode with chords and a passage retry, **When** the passage is repeated, **Then** the summary lists it as documented.

---

### User Story 3 - Stop, restart, replay and the result window never ask again and never leave anything behind (Priority: P2)

After a run, the learner can stop, restart, play again (from the start or from a measure) and close, and the application ends in a clean state each time.

**Why this priority**: it is the second place where the orchestration lives, and the decision "restarts do not ask again" is an owner decision that must stay true.

**Independent Test**: from a finished or stopped run, perform each follow-up action on the real flow and check what opens, what is asked, and the state left.

**Acceptance Scenarios**:

1. **Given** a finished Arcade run, **When** the learner restarts or plays again from the start or from a measure, **Then** no start window opens and the run begins as asked.
2. **Given** any stop or end, **When** the learner starts again or closes the windows, **Then** nothing is left running, no capture or listener is held, the tuner can be opened, and the preparation window comes back only when it should.
3. **Given** a run ended while the preparation window was hidden, **When** the result is closed, **Then** the learner is brought back to a usable window.

---

### User Story 4 - Changes made while preparing or running are handled (Priority: P2)

The learner changes the input, loses it, changes the track, the mode or a setting (tempo, tolerance, stage flames, passage choice), before or during a start.

**Why this priority**: these are the interruptions in which the hidden bugs of an orchestration usually live, and each already has a decision recorded in the specs.

**Independent Test**: for each change, apply it at each stage (preparing, start window open, running, paused) on the real flow and check the documented outcome.

**Acceptance Scenarios**:

1. **Given** a start window is open, **When** the mode, the track or the preparation window changes or closes, **Then** that start is cancelled and no mode begins from it.
2. **Given** the input becomes unusable during preparation, a one-click start, a start window or a run, **When** it happens, **Then** the documented reaction occurs (window shown, run paused or kept) and never a window over a start window the learner has not answered.
3. **Given** a setting is changed during a run, **When** the next run starts, **Then** it applies to the next run and not to the current one.
4. **Given** the flames are Off, Discreet or Full, **When** the same performance is played, **Then** the judgements, score and result are identical.

---

### User Story 5 - The same real recordings protect every mode, and the known weaknesses are recorded (Priority: P2)

The recorded performances that exist (the owner's takes, the committed recordings) are replayed through each mode where it makes sense, with their figures recorded as baselines; the weaknesses known today are recorded as baselines, not hidden.

**Why this priority**: recorded audio is the only evidence that recognition and the modes work on a real guitar; it protects all modes with the same data.

**Independent Test**: replay each recording through the modes and compare with the recorded figures: worse fails, better is reported.

**Acceptance Scenarios**:

1. **Given** the committed real recordings, **When** they are replayed through each mode they apply to, **Then** the figures are not worse than the recorded baseline.
2. **Given** the owner's takes, **When** they are present on the machine, **Then** the baseline guard keeps its figures; when absent, the test skips without failing the build.
3. **Given** the kinds of event without any recorded-audio test (really played hammer-on, pull-off, palm mute, tapping, tremolo picking), **When** the coverage is reported, **Then** they are listed as gaps, not as covered.

---

### User Story 6 - The learner-visible screens keep their layout (Priority: P3)

Every window of the interactive modes (start window, preparation window and its tabs, Arcade window, result window, the flames) shows its content, with real sizes, inside the window, at the sizes used elsewhere, in the languages shipped.

**Why this priority**: one of the three bugs was a layout that was never redone; layout problems are invisible to rule-level tests.

**Independent Test**: open each window on the real toolkit in each state and check that every visible part has a size and lies inside the window.

**Acceptance Scenarios**:

1. **Given** each window in each of its states, **When** it is shown, **Then** every visible part has a non-zero size and lies inside the window.
2. **Given** the longest texts of the three languages, **When** a window is shown, **Then** nothing is cut or outside.

---

### User Story 7 - The coverage is a list the owner can read (Priority: P3)

A readable matrix says, for each journey of each mode, which test protects it, and which journeys are **not** covered and why.

**Why this priority**: "good coverage" must be something the owner can check, not a feeling; and a gap must be visible to be accepted or closed.

**Independent Test**: read the matrix; every row has a test name or a declared gap, and each test named exists.

**Acceptance Scenarios**:

1. **Given** the matrix, **When** it is checked against the tests, **Then** every named test exists and every journey has a test or a declared gap.
2. **Given** a new journey is added to a mode later, **When** the matrix is not updated, **Then** a check flags the row that is missing.

---

### Edge Cases

- A test that passes on the pieces but would not notice the behaviour breaking (the very failure of the three bugs): each journey's test must be shown to **fail** when its behaviour is deliberately broken.
- Timing-dependent journeys (the quick-start patience, the ten seconds before *Continue anyway*, the count-in): tests must not rely on real waiting nor be flaky.
- Windows opened on the owner's real screen by native tests: they must be short and must not steal focus for long.
- Data that is not on every machine (the owner's takes, the datasets): tests skip cleanly and never fail the build because of an absent file.
- A journey that cannot be tested without a change in the production code: the change must be minimal, declared and justified, and must not alter behaviour.
- Windows and Linux: the journeys are platform-agnostic in principle but are only run on macOS here; this must be stated, not implied.
- A baseline that gets better: the test reports it and asks to raise the baseline, it does not fail.
- Languages: the three shipped languages must not break a layout.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: There MUST be a written **contract of the interactive modes**: the list of learner journeys of each mode and of the start path (start from each entry point, play, pause and resume, stop, end, restart, replay, quick start, input lost, tuner, change of track, mode or setting, window closed), each with the behaviour that must hold. It is the reference the tests are written against and the matrix of US7.
- **FR-002**: Every journey of the contract MUST be protected by at least one **behavioural** test, that is a test that replays what the learner does and checks what the learner sees and what the application does, not only the internal rule nor the text of the source.
- **FR-003**: The start path MUST be tested on the application's real window flow for the three modes and the three entry points, including: the start window alone, the quick-start patience, the tuner way and back, the strum verdicts, *Continue anyway*, cancel by each way, a double press, and the transport-bar button while a start window is open.
- **FR-004**: Each of the three modes MUST have a whole-run journey, from start to result, on a known score and a played-back performance, with a recorded expected result.
- **FR-005**: Stop, restart, play again (from the start and from a measure), the automatic retry after a failed start and the closing of the result window MUST be tested, including that **restarts do not ask the tuning question again** and that nothing is left held afterwards.
- **FR-006**: Changes made while preparing or running (input lost or changed, track, mode, window closed, setting changed) MUST be tested at each stage where they can happen, with the outcome the specs already decided.
- **FR-007**: The stage flames MUST be tested as having no effect on any judgement, score or result, in the three settings, over the whole-run journeys.
- **FR-008**: Every window of the interactive modes MUST be tested, in each of its states, for visible parts that have a size and lie inside the window, at the sizes used elsewhere and with the longest texts of the three languages.
- **FR-009**: The recorded performances that exist MUST be replayed through the modes where they apply, with their figures kept as baselines (worse fails, better is reported); the known weaknesses (a chord with a string missing, the D major chord) MUST be kept as baselines, not hidden.
- **FR-010**: Each journey's test MUST be shown to **fail** when its behaviour is deliberately broken (a check of the tests themselves), starting with the three bugs found by hand (a practice window over the unanswered start window, the tuner refused from the start window, an empty start window after the answer yes).
- **FR-011**: The tests MUST be deterministic: no real waiting for the patience, the ten seconds or the count-in, and no dependence on the machine's audio devices or on the owner's files being present.
- **FR-012**: Tests that need the owner's non-committed recordings or the datasets MUST skip cleanly when they are absent and MUST say so in their report.
- **FR-013**: A **coverage report** MUST list, for each journey, the tests that protect it, and list the **gaps** (journeys or kinds of event with no test, such as the kinds with no recorded-audio test) with the reason; the report MUST be checkable against the tests, so that a missing row or a missing test is flagged.
- **FR-014**: The feature MUST NOT change what the application does. The only allowed production change is the one the owner accepted (2026-10-15): a **small, declared** change that isolates the practice window's accesses to the application (the document and the cursor of the score, the window factory and the main window, the icon, the dialogs, the texts) behind an interface whose default is exactly today's behaviour, so that the real window can be built by a test without the whole application. It is justified on inspected source, listed, and proven to change no behaviour by the existing suites (every figure of the recognition reports and every count of tests equal, plus the new ones).
- **FR-015**: The work MUST keep the repository buildable after each phase and MUST report exactly which tests ran, which were skipped and why, and which are new.
- **FR-016**: A **code-coverage report** (lines and branches) MUST be produced for each interactive package (the practice window and its start path, the three modes, Arcade, the flames), once **before** the new tests and once **after**, in a profile that is not part of the default build; the figures are reported with the list of the biggest uncovered areas, and **no minimum** is imposed (the build never fails on a figure).

### Key Entities

- **Journey**: one thing a learner does through a mode or the start path (for example "start Arcade from the transport button and answer yes"), with the behaviour that must hold.
- **Contract**: the written list of journeys and behaviours of the interactive modes (FR-001).
- **Coverage matrix**: the table that says, for each journey, which test protects it or why none does (FR-013).
- **Baseline**: a recorded figure (accepted, wrong, judged) that a test keeps from getting worse and reports when it gets better.
- **Performance**: a played-back sequence of notes (right, wrong, late, missing, with pause) used to drive a mode without a person and a guitar.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: **100 % of the journeys of the contract** have a behavioural test or a declared, reasoned gap, and the coverage matrix is checked against the tests with zero missing rows and zero tests named that do not exist.
- **SC-002**: **The three bugs found by hand would have been caught**: for each of them, reinstating the old behaviour makes at least one test fail (3 of 3), shown once and recorded.
- **SC-003**: Every journey's test is shown to fail when its behaviour is broken on purpose: **100 % of the P1 journeys** (US1 and US2) and at least **80 % of the P2 journeys** (US3 to US5) have been broken on purpose once and the failure recorded; the rest are listed.
- **SC-004**: For each of the three modes, **one whole run from start to result** is replayed and gives the recorded expected result; the same run with the flames Off, Discreet and Full gives **identical judgements, score and result** (0 differences).
- **SC-005**: Every window of the interactive modes, in each state and each shipped language, shows **only parts with a size, inside the window** (0 violations over the matrix of windows × states × languages).
- **SC-006**: The tests are **stable**: the whole new set passes **20 times in a row** on the development machine with no failure and no skip that is not declared.
- **SC-007**: The new tests add **less than 2 minutes** to the default build (measured on the development machine, before and after: the practice suite takes 9 min 14 s today); what is slower or repeated (native windows, the 20-run stability check of SC-006, the coverage measure) is in **tagged suites** that run on request and before each release, and the report says which ones were run.
- **SC-008**: The kinds of event with **no recorded-audio test** and the **known weaknesses** are listed in the coverage report with their baseline figures, so that none is presented as covered.
- **SC-009**: **No behaviour of the application changes**: the existing suites and the recognition figures of spec 010 are equal before and after, and any production change is limited to the declared seam.
- **SC-010**: The coverage report exists for every interactive package, **before and after**, and the figure of **no package goes down**; the uncovered areas that remain are listed and each is either covered by a journey or declared a gap in the matrix.

## Assumptions

- The interactive modes, the start window (spec 010), the stage flames (spec 009) and the preparation window are the scope; the **recognition itself**, new features, the tuner window itself and the score editor are **out of scope** (the tuner is only exercised as the thing the start window opens).
- Many rules are already well tested one by one (the verdicts of Wait mode, the passage retries, the Follow timing, the Arcade scoring and its view, the strum meter, the start-window pieces); this feature **does not redo them**, it adds the journeys that join them and the places where they are not joined today (the controlling code of the practice window, the whole run, the follow-up actions, the layouts).
- Recorded performances available today: the owner's two recorded sessions (the files `tests/1` and `tests/2`, committed expected-event files), the owner's chord takes (not committed, 42 takes, two input volumes), GuitarSet and the IDMT datasets (present on the maintainer's machine, not in the repository). Synthetic performances are used where a recording does not exist, and are said to be synthetic.
- **How the real flow of the practice window is exercised** is decided (see Clarifications): the tests build and drive the real window through a small declared interface in front of the application (FR-014). Driving the built application from outside is not the main way; a few scripted checks on the built application before a release remain possible as a complement and are decided at planning.
- **"Good coverage"** has two parts (see Clarifications): the checkable matrix of journeys and gaps (FR-013, SC-001), which is the contract, and a **code-coverage figure** (lines and branches) per interactive package, reported as information with **no minimum** and no build failure (FR-016, SC-010). A new tool is needed to measure it and it runs in a profile separate from the default build.
- The suites run on macOS (Apple Silicon) here; the native-window tests open short windows on the maintainer's screen. Windows and Linux are not run and this is stated, not implied.
- The default build must stay green and must not depend on anything absent from a clean checkout; what depends on the owner's files or on the datasets skips.
- The work follows Spec Kit in order (clarify, plan, tasks, checklist, analyze, implement) and is delivered in small phases that can each be reviewed.
