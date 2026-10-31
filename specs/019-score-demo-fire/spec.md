# Feature Specification: The demo fire at the bottom of the score in Wait and Follow, growing with the streak of right notes

**Feature Branch**: `feature/interactive-practice` (no branch of its own; spec directory `specs/019-score-demo-fire`)

**Created**: 2026-10-31

**Status**: Draft

**Input**: User description: "spec 019 : le feu démo Amiga de la spec 018 sur la partition en pratique note à note". The owner asked on 2026-10-30 whether the Amiga demo fire of Arcade (spec 018) could burn on the score in note-by-note practice, and answered the first questions the same day: at the bottom of the score area; it grows with the streak of right notes and falls back on a mistake; in *Wait for the right note* and *Follow the tempo* (not Arcade, which has its own fire); off by default; the same fire as spec 018, reused and translucent; it never hides the framed note nor the next one; sparks on each right note, rising from the bottom of the fire; identical on macOS, Linux and Windows; it honours *Reduce motion*.

## Clarifications

### Session 2026-10-30 (before this specification)

- Q: Where does the fire burn? → A: **At the bottom of the score area** (not around the framed note, not over the whole score).
- Q: What makes it grow? → A: **The streak of right notes** in a row; it falls back on a mistake (not the progress through the piece).
- Q: In which modes? → A: **Wait for the right note and Follow the tempo** (Arcade keeps its own fire of spec 018).
- Q: On or off by default? → A: **Off by default.**
- Q: Proposals of the assistant, confirmed by the owner: **the same fire as spec 018**, reused and translucent; it **never hides the framed note nor the next one**; it is **identical on macOS, Linux and Windows**; it honours the system's *Reduce motion*.
- Q: Where do the sparks of a right note go? → A: **From the bottom of the fire upwards** (not around the frame of the note).

### Session 2026-10-31

- Q: *Reduce motion* is read from the system on macOS only today (Arcade too): should this spec read it on Linux and Windows as well? → A: **Yes, in this spec**: the system's *Reduce motion* is read on macOS, Linux and Windows, for the score fire and for Arcade's fire.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A fire that grows with my streak while I practise on the score (Priority: P1)

The learner practises a piece in *Wait for the right note* or *Follow the tempo* with the score fire turned on. At the bottom of the score area, a fire in big pixels, like the classic fire of the Amiga demos, shows embers at the start. Each right note in a row makes it a little higher; at the first mistake it falls back to embers. The score stays readable: the fire is see-through, and the note to play and the next one are never under it.

**Why this priority**: it is the feature the owner asked for: a visible reward for playing a run of right notes, in the modes used every day.

**Independent Test**: turn the score fire on, play in Wait a run of right notes, then a wrong note; watch the fire grow, then fall back. Replay a recorded Follow run and check the height of the fire after each judged note.

**Acceptance Scenarios**:

1. **Given** the score fire is on and a Wait or Follow run starts, **When** no note is judged yet, **Then** only embers glow along the bottom of the score area.
2. **Given** a run with the score fire on, **When** the learner plays right notes in a row, **Then** the fire grows with each one, up to its full height at the streak of full height (see Assumptions), and stays there while the streak goes on.
3. **Given** a streak in progress, **When** a mistake happens (a wrong note in Wait; a wrong or missed note in Follow; a note passed with *Skip*), **Then** the fire falls back to embers within about one second, and grows again with the next streak.
4. **Given** the framed note or the next note to play lies near the bottom of the visible score, **When** the fire is drawn, **Then** neither of them is covered by the fire.
5. **Given** the score fire is on, **When** any note is judged, **Then** the verdict, the score, the timing and the moves of the score are exactly the same as with the fire off.

---

### User Story 2 - Sparks on each right note (Priority: P2)

Each right note throws a burst of sparks from the bottom of the fire upwards, more of them with a longer streak, fading within about one second, like Arcade's sparks of spec 018.

**Why this priority**: the owner asked for them; they make each right note felt, but the growing fire alone already rewards the streak.

**Independent Test**: replay a recorded run and count the bursts: one per right note, none for a mistake, none with the fire off.

**Acceptance Scenarios**:

1. **Given** the score fire is on, **When** a note is judged right, **Then** a burst of sparks rises from the bottom of the fire and fades within about one second.
2. **Given** a mistake, **When** it is judged, **Then** no spark is thrown.
3. **Given** a longer streak, **When** sparks are thrown, **Then** the burst is bigger, within a fixed maximum.
4. **Given** sparks in the air, **When** they reach the framed note, the next note or a text of the palette area, **Then** they are not drawn there.

---

### User Story 3 - My choice of fire, kept and the same everywhere (Priority: P3)

The learner turns the score fire on or off, or chooses a discreet fire, in the practice settings; the choice is kept for the next runs and the next sessions, and the fire looks and behaves the same on macOS, Linux and Windows.

**Why this priority**: the feature is off by default, so it must be easy to find and to keep; parity across systems is a standing rule of the owner.

**Independent Test**: change the setting, close and reopen TuxGuitarAdvanced, start a Wait run: the fire is as chosen. Run the same checks on the three systems (Linux and Windows by the automated builds and by testers).

**Acceptance Scenarios**:

1. **Given** a fresh install, **When** a Wait or Follow run starts, **Then** no fire is drawn (off by default).
2. **Given** the learner chooses *Full* or *Discreet*, **When** the next run starts and after a restart of the application, **Then** the fire is drawn as chosen.
3. **Given** the setting is *Off*, **When** a run goes, **Then** nothing of the fire is drawn and the practice costs nothing more than without the feature.

---

### Edge Cases

- **Reduce motion asked by the system** (on macOS, Linux or Windows): the fire does not flicker and no spark is thrown; its height alone follows the streak. Arcade's fire follows the same reading.
- **The system's setting cannot be read** (unknown desktop, an error): the fire behaves as if no reduction was asked, as today.
- **Pause**: the fire holds its height and its motion holds still; the sparks hold still.
- **The score scrolls, changes page or is resized**: the fire stays at the bottom of the visible score area, across its width, and keeps avoiding the framed and the next note.
- **A very small score area**: the fire stays low enough that the score keeps most of its height (see FR-004); with no room, embers only.
- **The run ends or is stopped**: the fire goes out at once.
- **Fast passages in Follow**: many right notes close together keep the sparks under their fixed maximum.
- **A slow computer**: if the score would stop scrolling smoothly, the fire gets cheaper (coarser pixels, then no sparks, then off) rather than slowing the practice.
- **Arcade**: unchanged, keeps its own *Stage flames* setting.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001** (where): In *Wait for the right note* and *Follow the tempo*, when the score fire is on, a fire MUST be drawn along the bottom of the visible score area, across its width, behind nothing that must be read (see FR-005).
- **FR-002** (look): The fire MUST be the fire of spec 018: big square pixels from dark red through red, orange and yellow to white, flickering and rising, translucent (at most 85 % opaque at the hottest point, more see-through at its top, nothing drawn where it is cold), with the corners higher than the middle.
- **FR-003** (growth): The height of the fire MUST follow the current streak of right notes: embers at a streak of 0, growing with each right note up to full height at the streak of full height, then holding; a mistake MUST bring it back to embers within about one second. A right note is a green tick in Wait, and an on time, early or late verdict in Follow; a mistake is a red cross in Wait, a wrong or missed verdict in Follow, or a note passed with *Skip*.
- **FR-004** (size): At full height the fire MUST NOT rise above one third of the visible score area's height at the corners, and lower in the middle (as the corner/middle profile of spec 009).
- **FR-005** (never hides what must be read): The fire and its sparks MUST NOT cover the framed note, the next note to play, nor the practice palette; near them they MUST fade out as near the texts of spec 018 (no pixel above 12 % opacity within 4 pixels of their boxes).
- **FR-006** (sparks): Each right note MUST throw a burst of sparks rising from the bottom of the fire, fading within about one second; more with a longer streak, up to a fixed maximum per note and in the air; none for a mistake.
- **FR-007** (setting): A setting *Score fire* MUST offer *Off* (the default), *Discreet* (a lower and fainter fire with fewer sparks, as spec 018's discreet) and *Full*; it MUST be kept between runs and sessions, and apply to Wait and Follow only; Arcade's *Stage flames* setting is unchanged.
- **FR-008** (decoration only): The fire and the sparks MUST NOT change the recognition, the verdicts, the timing, the score, the streak, the moves of the score cursor, the palette's behaviour of spec 017, or anything recorded about a run.
- **FR-009** (motion): The motion MUST hold still during a pause and MUST stop when the system asks to reduce motion (the height alone follows the streak, no spark).
- **FR-013** (reduce motion on every system): The system's request to reduce motion MUST be read on macOS, Linux and Windows, when a run starts, for the score fire and for Arcade's fire of spec 018 alike; when it cannot be read, no reduction is assumed (no wait, no error shown).
- **FR-010** (smoothness): The fire MUST NOT make the score scroll or redraw less smoothly, nor delay the display of a verdict; when the computer cannot keep up, the fire MUST get cheaper or go out before the practice suffers.
- **FR-011** (parity): The fire, its setting and its behaviour MUST be the same on macOS, Linux and Windows (owner's standing rule).
- **FR-012** (documentation): The practice guides in the three languages MUST describe the score fire, its setting and that it never changes the judging.

### Key Entities

- **Score fire setting**: Off / Discreet / Full, saved with the practice settings, default Off.
- **Streak (for the fire)**: the number of right notes in a row in the current run, back to 0 on a mistake; read from the verdicts, never changing them.
- **Burst**: the sparks thrown by one right note, with its time and the streak at that moment.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: On the owner's try (gate), the owner judges the score fire to be "the Amiga fire of Arcade" at the bottom of the score, growing with the streak and falling on a mistake, and keeps it.
- **SC-002**: On a replayed Wait run and a replayed Follow run, the fire's height after each judged note follows the streak (embers at 0, full height from the streak of full height), and returns to embers within 1 second after each mistake.
- **SC-003**: On the reference scores and window shapes, no pixel of the fire or of a spark above 12 % opacity meets the box of the framed note, of the next note or of the palette widened by 4 pixels.
- **SC-004**: The same recorded run with the fire off and at full gives identical verdicts, score, streak, timings and summary; with *Off* the picture of the score is identical to the picture without the feature.
- **SC-005**: The number of bursts equals the number of right notes on a replayed run, and is 0 with *Off*; no spark lives more than about one second.
- **SC-006**: With the fire at *Full*, the score keeps scrolling at the same smoothness as with the fire off on the owner's computer, and a verdict is shown no later than without the fire.
- **SC-007**: The setting survives a restart, and a fresh install starts with *Off*.
- **SC-008**: With the system set to reduce motion, on each of macOS, Linux and Windows, a run of Wait, Follow or Arcade shows a fire that does not move and throws no spark (macOS checked by the owner; Linux and Windows by the automated builds and testers).

## Assumptions

- **Streak of full height**: 16 right notes in a row (adjustable on the built application at the owner's try).
- The fire is drawn at the bottom of the visible part of the score, not of the whole piece, and follows scrolling and page changes.
- *Skip* breaks the streak (a skipped note was not played right).
- In Follow, early and late notes count as right, as Arcade's streak does; wrong, missed and not judged notes break it.
- *Discreet* is 60 % of the height and the opacity of *Full*, as in spec 018.
- The fire reuses the fire of spec 018 (same colours, grid of big pixels, translucency, sparks), not a new look.
- The setting lives in the practice settings (Mode tab) beside the other settings of Wait and Follow.
- Linux and Windows are checked by the automated builds and by testers' feedback; the owner checks macOS.
- Out of scope: a fire in Arcade other than spec 018's, a fire around the framed note, sounds, and any change to the recognition or the judging.
