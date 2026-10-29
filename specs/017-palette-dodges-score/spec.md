# Feature Specification: The practice palette moves aside so it never hides the note to play

**Feature Branch**: `feature/interactive-practice` (no branch of its own; spec directory `specs/017-palette-dodges-score`)

**Created**: 2026-10-30

**Status**: Draft

**Input**: User description: "pour la palette qui s'écarte de la partition (spec 017)." The owner's report of 2026-10-29: when a practice run starts, the small window that says whether the notes are right or not (the practice palette) hinders the reading of the score; it should move so as not to be in the way. Answers already given by the owner on 2026-10-29: the palette **moves aside by itself**: when the note to play (framed in the score) passes under it, it jumps to another free corner of the main window, without taking the keyboard focus (chosen over "always docked below the score" and "remember where I put it"); the modes concerned are **Wait for the right note** and **Follow the tempo**, not Arcade.

## Clarifications

### Session 2026-10-29 (before this specification)

- Q: What should the palette do when it hides the score? → A: **Move aside by itself**: when the note to play goes under it, it jumps to another free corner of the main window, without taking the focus. (Chosen by the owner among: move aside by itself, dock below the score, remember the position the learner gave it.)
- Q: In which modes is it a problem? → A: **Wait for the right note** and **Follow the tempo**. (Arcade has its own window and is not concerned.)

### Session 2026-10-30

- Q: When the framed note moves towards the palette along the same line, should the palette move aside ahead of time, or only once the note is under it? → A: **Ahead of time**: it moves aside as soon as the framed note **or the next note to play** would be under it, so that the note to play is never hidden.
- Q: Which places can the palette take when it moves aside? → A: **The four corners of the score area**, as written.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - The note to play is never hidden by the palette (Priority: P1)

A learner practises a piece in *Wait for the right note* or *Follow the tempo*. The score shows the note to play, framed. The palette (the detected note, whether it is right, the buttons Skip, Resume, Restart, Stop) floats over the main window. When the framed note comes under the palette, the palette jumps to another corner of the area where the score is shown (clarification of 2026-10-30) where it hides neither the framed note nor what the learner is about to read, and the learner keeps reading without touching the mouse.

**Why this priority**: it is the owner's whole request; a hidden note to play makes the practice impossible at that moment.

**Independent Test**: open a piece whose notes run under the palette's place, practise in each of the two modes, and check that, each time the framed note reaches the palette, the palette goes elsewhere within a fraction of a second, that the framed note is visible again, and that the keyboard shortcuts of practice still work without a click.

**Acceptance Scenarios**:

1. **Given** a run in *Wait for the right note* with the palette in its usual place, **When** the note to play is framed at a place covered by the palette, **Then** the palette moves to another corner of the score area where it does not cover the framed note, and the framed note is entirely visible.
2. **Given** a run in *Follow the tempo*, **When** the next note to play would come under the palette as the piece goes on, **Then** the palette moves aside before that note is framed, so the framed note is never hidden, and the run goes on (no pause, no lost note because of the move).
3. **Given** the palette has just moved, **When** the learner plays or uses a practice shortcut, **Then** the keyboard focus is where it was before the move: the palette never takes it, and the shortcuts work as before.
4. **Given** a framed note that is not under the palette, **When** the run goes on, **Then** the palette does not move.
5. **Given** an Arcade run, **When** it is played, **Then** nothing of this feature applies (Arcade has its own window).

---

### User Story 2 - The palette stays calm (Priority: P2)

The learner is not distracted by a palette that jumps around: it moves only when it must, goes to a place where it will stay a while, and never flickers between two places.

**Why this priority**: a palette that jumps at every note would hinder the reading as much as a palette that hides it.

**Independent Test**: practise a piece whose notes go back and forth across the palette's place and count the moves: the palette moves only when the framed note would otherwise be covered, and never comes back to the place it just left while the next notes are still there.

**Acceptance Scenarios**:

1. **Given** notes that keep running along the same line of the score, **When** the palette moves, **Then** it chooses a corner that the next notes of that line will not reach soon (not the corner the notes are heading to).
2. **Given** the palette moved, **When** the framed note leaves the place the palette left, **Then** the palette does not go back to it on its own; it stays where it is until it is in the way again.
3. **Given** no free corner (every corner would cover the framed note, for example in a very small main window), **When** the palette would have to move, **Then** it goes to the place that covers the least of the framed note and stays there; it never moves back and forth.

---

### User Story 3 - The learner can still put the palette where they like (Priority: P3)

The learner can still drag the palette by hand, as today. The automatic move then starts from the place the learner chose.

**Why this priority**: the manual move exists today and must not be lost; the automatic move is a help, not a constraint.

**Independent Test**: drag the palette somewhere else during a run and check that it stays there as long as it does not hide the framed note, and that it still moves aside when the framed note comes under it.

**Acceptance Scenarios**:

1. **Given** the learner drags the palette to another place, **When** the framed note is not under it, **Then** it stays where the learner put it.
2. **Given** the learner put the palette outside the main window (another screen, beside the window), **When** the run goes on, **Then** it never moves (it cannot hide the score there).
3. **Given** a palette closed and opened again in the same session, **When** it opens, **Then** it opens where it was last, as today.

### Edge Cases

- The main window is resized or moved during a run: the palette's places follow the main window; a palette that now covers the framed note moves aside.
- The score scrolls (the framed note jumps to another line or page): the decision is taken on the new place of the framed note.
- A chord, or a note framed across two lines: the whole framed area counts.
- The run is paused (unusable input, the learner's pause): the palette does not move while nothing advances, except if the framed note is covered (the next note is not looked at while paused).
- The palette's own size changes (a longer message, the end-of-run summary): it is the new size that must not cover the framed note.
- The end of a run: there is no framed note any more; the palette stays where it is, with its summary and its Restart button.
- Multiple screens: the corners are those of the main window, whatever screen it is on.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: In *Wait for the right note* and *Follow the tempo*, while a run is going, the palette MUST NOT cover the framed note to play (the whole framed area); the only exceptions are the first picture of a run, a scroll or a resize of the window, after which it MUST be uncovered within 0.3 s (see SC-001).
- **FR-002**: When the framed note to play, **or the next note to play**, is under the palette, the palette MUST move by itself to another place inside the main window where it does not cover the framed note; the candidate places are the corners of the area where the score is shown.
- **FR-003**: Among the free places, the palette MUST choose one that the next notes to play are not about to reach (not the side the notes are heading to on the same line), and otherwise the one nearest to where it is.
- **FR-004**: The palette MUST move only when it covers, or is about to cover, the framed note; it MUST NOT move back on its own to a place it left; it MUST NOT move more than once for the same framed note, except when the main window or the palette changes size. "About to cover" means: the area of the next note to play (the one framed after the current one) is under the palette.
- **FR-005**: When no place is free, the palette MUST go to the place that covers the least of the framed note and stay there.
- **FR-006**: A move MUST NOT take the keyboard focus, MUST NOT pause or slow the run, and MUST NOT change any verdict.
- **FR-007**: The learner MUST still be able to drag the palette by hand; it then stays where it was put until it covers the framed note; a palette the learner put outside the main window MUST never be moved automatically (a palette left outside because the main window itself moved is not concerned).
- **FR-008**: The palette MUST still open where it was last in the session (today's behaviour), and otherwise in today's default place.
- **FR-009**: Arcade MUST be unchanged; the editor outside practice MUST be unchanged.

### Key Entities

- **Palette**: the floating window of a practice run (detected note, verdict, status, buttons). Its place and size are what is checked against the framed note.
- **Framed note**: the area of the score that shows the note or chord to play now: the red frame in *Wait for the right note*; in *Follow the tempo*, where the song plays, the score highlights the beat being played instead, at the same place (correction found in the inspected source while planning, `research.md` R2; no question to the owner). It changes with each event and when the score scrolls.
- **Candidate places**: the corners of the area of the main window where the score is shown, at a small margin from its edges.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: In a whole run of each of the two modes on a piece whose notes cross the palette's place, the framed note is **never** covered by the palette while a free corner exists, except just after the first picture of the run, a scroll or a resize of the window, and then for **no more than 0.3 s**.
- **SC-002**: The palette moves **at most once per framed note**, and on a whole run it **never** returns to a place it left while the framed note is still near that place (no back and forth).
- **SC-003**: After every move, the practice shortcuts work **without any click** (the focus never goes to the palette), and the run's verdicts are **the same** as without the feature on the same played notes.
- **SC-004**: The owner practises test sheet F01 (and a *Wait for the right note* run) and judges that the palette no longer hinders the reading of the score.

## Assumptions

- The framed note's area on the screen and the palette's place are both known to the practice window while a run goes; no change of the editor is needed to know them (to be confirmed in the plan, Constitution II).
- The 0.3 s of SC-001 only applies to the first picture of a run, a scroll and a resize: the time to notice the note is covered and to move; otherwise the palette moves ahead of time (owner's answer of 2026-10-30).
- The candidate places are the four corners of the score area (owner's answer of 2026-10-30); a smarter free-space search is out of scope.
- The move is a jump, not a slow slide (the owner said "jumps"); a short slide may be considered in the plan only if it keeps SC-001.
- The learner's manual drag is kept (Story 3); the place is remembered for the session only, as today; remembering it between launches is out of scope.
- Arcade, the tuner and the editor are out of scope.
- The feature changes no recognition and no judging.
