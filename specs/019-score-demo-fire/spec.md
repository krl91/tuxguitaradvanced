# Feature Specification: The demo fire at the bottom of the score in Wait and Follow, growing with the streak of right notes, and an advanced tab to tune both fires

**Feature Branch**: `feature/interactive-practice` (no branch of its own; spec directory `specs/019-score-demo-fire`)

**Created**: 2026-10-31

**Status**: Clarified and planned (2026-10-31)

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
- Owner's request (2026-10-31): "pour les flammes et les étincelles, ajoute des réglages pour les deux modes : un onglet de réglage avancé pour le mode practice, avec un max de réglages : la transparence, la quantité d'étincelles, la densité des flammes, la longueur des flammes, la vitesse d'animation ; toutes les valeurs en dur de ces flammes doivent être réglables, une info-bulle sur chaque paramètre, les valeurs actuelles par défaut."
- Q: Do the three values of the reading guard (the fire fades to 0.2 % near texts and notes, within a 4-pixel margin and a 28-pixel ramp) become adjustable too? → A: **No, a fixed guarantee**: every other value is adjustable, but the fire can never hide a note or a text, whatever the settings.
- Q: Are the advanced settings separate for Arcade and for the score (Wait / Follow), or shared? → A: **Separate per mode**: one set for Arcade's fire, one for the score fire, each with its own *Default values* button.
- Q: How does the learner see the effect of a setting while tuning? → A: **An animated preview in the Advanced tab**: a small live fire follows every setting at once, with a button that throws a burst of sparks and a slider that sets the streak (or, for Arcade, the share of the score); the real game applies the settings at its next run.
- Q: How far do the settings go? → A: **Wide bounds**: creative freedom (for example the most opacity up to 100 %, flames up to the full height of the picture, many sparks); the only limits are the reading guard (fixed) and a ceiling of cost so that the automatic quality does not collapse.
- Q: Where is the Off / Discreet / Full choice of the score fire? → A: **In the Mode tab**, beside the other settings of Wait and Follow (as Arcade's *Stage flames*); the Advanced tab holds only the fine settings.

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

### User Story 4 - Tuning every value of both fires in an advanced tab (Priority: P2)

The learner opens a new **Advanced** tab of the practice window. It holds two groups of settings, **Arcade fire** and **Score fire**, with every value that shapes the flames and the sparks: transparency, number of sparks, density and length of the flames, speed of the animation, size of the pixels, colours, and so on. Each setting has a tooltip saying what it changes; each group has a *Default values* button. The defaults are the values of today's fires, so nothing changes until the learner tunes something.

**Why this priority**: the owner asked for it explicitly, to tune the look himself without a new build; the fire works without it.

**Independent Test**: open the Advanced tab: every setting shows today's value and has a tooltip; change the transparency and the number of sparks of the score fire, start a Wait run: the fire follows the new values; Arcade's fire is unchanged; *Default values* brings back today's look.

**Acceptance Scenarios**:

1. **Given** a fresh install, **When** the Advanced tab is opened, **Then** each setting of both groups shows the value used by today's fires (spec 018 after the owner's look gate for Arcade; the defaults of this spec for the score fire).
2. **Given** the pointer rests on a setting, **When** the tooltip shows, **Then** it says in the learner's language what the setting changes, its unit and its default.
3. **Given** the learner changes a value of one group, **When** the next run of that mode starts, **Then** its fire follows the new value, and the other group's fire is unchanged.
4. **Given** the Advanced tab is open, **When** the learner changes any setting of a group, **Then** the animated preview of that group shows the change at once (within a tenth of a second), and its *Sparks* button and its *Streak* slider let the sparks and the growth be seen without playing.
5. **Given** tuned values, **When** the learner presses *Default values* of a group, **Then** every value of that group returns to its default and the fire looks as before any tuning.
6. **Given** any combination of values within their ranges, **When** a fire is drawn, **Then** it still never covers a text, the framed note or the next note (the reading guard is not adjustable), and the judging is unchanged.
7. **Given** tuned values, **When** the application restarts, **Then** the values are kept.

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
- **Arcade**: keeps its own *Stage flames* setting (Off / Discreet / Full); its look changes only through its group of the Advanced tab.
- **Values that cost a lot** (very small pixels, many sparks): the automatic quality tiers still protect the smoothness (coarser pixels, then no sparks, then no fire); every setting has bounds.
- **A value typed out of its range**: it is brought back within the range, never refused with an error.
- **An opaque setting** (opacity 100 %, full height): the hottest pixels hide the game behind them, except near the texts, the framed note and the next note, where the fire still fades (FR-016).
- **A setting changed during a run**: it applies to the next run of that mode.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001** (where): In *Wait for the right note* and *Follow the tempo*, when the score fire is on, a fire MUST be drawn along the bottom of the visible score area, across its width, behind nothing that must be read (see FR-005).
- **FR-002** (look): The fire MUST be the fire of spec 018: big square pixels from dark red through red, orange and yellow to white, flickering and rising, translucent (by default at most 85 % opaque at the hottest point, adjustable within FR-019's range, more see-through at its top, nothing drawn where it is cold), with the corners higher than the middle.
- **FR-003** (growth): The height of the fire MUST follow the current streak of right notes: embers at a streak of 0, growing with each right note up to full height at the streak of full height, then holding; a mistake MUST bring it back to embers within about one second. A right note is a green tick in Wait, and an on time, early or late verdict in Follow; a mistake is a red cross in Wait, a wrong or missed verdict in Follow, or a note passed with *Skip*.
- **FR-004** (size): At full height the fire MUST NOT rise above one third of the visible score area's height at the corners, and lower in the middle (as the corner/middle profile of spec 009).
- **FR-005** (never hides what must be read): The fire and its sparks MUST NOT cover the framed note, the next note to play, nor the practice palette; near them they MUST fade out as near the texts of spec 018 (no pixel above 12 % opacity within 4 pixels of their boxes).
- **FR-006** (sparks): Each right note MUST throw a burst of sparks rising from the bottom of the fire, fading within about one second; more with a longer streak, up to a fixed maximum per note and in the air; none for a mistake.
- **FR-007** (setting): A setting *Score fire*, in the **Mode tab** beside the other settings of Wait and Follow, MUST offer *Off* (the default), *Discreet* (a lower and fainter fire with fewer sparks, as spec 018's discreet) and *Full*; it MUST be kept between runs and sessions, and apply to Wait and Follow only; Arcade's *Stage flames* setting is unchanged.
- **FR-008** (decoration only): The fire and the sparks MUST NOT change the recognition, the verdicts, the timing, the score, the streak, the moves of the score cursor, the palette's behaviour of spec 017, or anything recorded about a run.
- **FR-009** (motion): The motion MUST hold still during a pause and MUST stop when the system asks to reduce motion (the height alone follows the streak, no spark).
- **FR-010** (smoothness): The fire MUST NOT make the score scroll or redraw less smoothly, nor delay the display of a verdict; when the computer cannot keep up, the fire MUST get cheaper or go out before the practice suffers.
- **FR-011** (parity): The fire, its setting and its behaviour MUST be the same on macOS, Linux and Windows (owner's standing rule).
- **FR-012** (documentation): The practice guides in the three languages MUST describe the score fire, its setting, the Advanced tab and that the fire never changes the judging.
- **FR-013** (reduce motion on every system): The system's request to reduce motion MUST be read on macOS, Linux and Windows, when a run starts (on Windows, where reading the system takes long, the answer read in the background since the practice window opened and refreshed at each run start; until a first answer arrives, no reduction), for the score fire and for Arcade's fire of spec 018 alike; when it cannot be read, no reduction is assumed (no wait, no error shown).
- **FR-014** (advanced tab): The practice window MUST have an **Advanced** tab with two groups, *Arcade fire* and *Score fire*, each holding every adjustable value of its fire and a *Default values* button for that group.
- **FR-015** (every value adjustable): Every value that shapes a fire or its sparks MUST be adjustable in its group, at least: the most opacity; the size of the big pixels; the number of colour steps and the colours of the gradient (dark red to white); the length of the flames (height at the corners) and the height in the middle; the density of the flames (how full and how hot the body of the fire is); the strength and the speed of the flicker; the speed of the animation as a whole; the height and the glow of the embers; how fast the fire rises and falls (Arcade: about 0.5 s up, 3 s down, embers until 4 notes are judged; score fire: the streak of full height and the fall after a mistake); the share of *Discreet*; for the sparks: how many per right note, how many more with the streak and up to which streak, the most in the air, how long they live, how high they rise, how far they drift, and where they start. The only exceptions are the three values of the reading guard (FR-016) and what keeps the fire repeatable for the tests.
- **FR-016** (reading guard fixed): The fade near texts and notes (to 0.2 % within 4 pixels, over a 28-pixel ramp) MUST stay fixed whatever the settings, so that FR-005 and spec 009's reading rules hold for every combination of values.
- **FR-017** (defaults): The default of every setting MUST be the value used today: Arcade's fire exactly as after spec 018's look gate (85 %, 1/192, 20 to 50 sparks per note, 300 in the air, …), the score fire as defined by this spec; with all defaults the pictures MUST be identical to the pictures without the Advanced tab.
- **FR-018** (tooltips): Every setting and both *Default values* buttons MUST have a tooltip in English, French and Spanish saying what it changes, its unit and its default.
- **FR-019** (bounds and saving): Every setting MUST have a wide range (owner's answer: for example the most opacity from 0 to 100 %, the length of the flames up to the full height of the picture or of the score area), bounded only by the reading guard (FR-016) and by a ceiling of cost (the smallest pixel and the most sparks are capped so that the automatic quality tiers can still keep the picture smooth); a value outside is brought back within the range; MUST be saved with the practice settings, and MUST apply at the start of the next run of its mode.
- **FR-020** (preview): Each group of the Advanced tab MUST show an animated preview of its fire that follows every setting at once, with a control that throws a burst of sparks and a control that sets the growth (the streak for the score fire, the share of the maximum score for Arcade); the preview MUST honour *Reduce motion* and stop animating when the tab is not shown.
- **FR-021** (spec 018's limits become defaults): spec 018's "at most 85 % opaque" and its spark counts describe the defaults of Arcade's group; a tuned value may go beyond them within FR-019's ranges, while the reading guard and the judging rules of specs 009 and 018 hold for every value.

### Key Entities

- **Score fire setting**: Off / Discreet / Full, saved with the practice settings, default Off.
- **Streak (for the fire)**: the number of right notes in a row in the current run, back to 0 on a mistake; read from the verdicts, never changing them.
- **Burst**: the sparks thrown by one right note, with its time and the streak at that moment.
- **Fire tuning**: the set of adjustable values of one fire (Arcade or score), each with a name, a range, a default and a tooltip; two independent sets.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: On the owner's try (gate), the owner judges the score fire to be "the Amiga fire of Arcade" at the bottom of the score, growing with the streak and falling on a mistake, and keeps it.
- **SC-002**: On a replayed Wait run and a replayed Follow run, the fire's height after each judged note follows the streak (embers at 0, full height from the streak of full height), and returns to embers within 1 second after each mistake.
- **SC-003**: On the reference scores and window shapes, no pixel of the fire or of a spark above 12 % opacity meets the box of the framed note, of the next note or of the palette widened by 4 pixels.
- **SC-004**: The same recorded run with the fire off and at full gives identical verdicts, score, streak, timings and summary; with *Off* the picture of the score is identical to the picture without the feature.
- **SC-005**: The number of bursts equals the number of right notes on a replayed run, and is 0 with *Off*; no spark lives more than about one second.
- **SC-006**: With the fire at *Full*, the score keeps scrolling at the same smoothness as with the fire off on the owner's computer, and a verdict is shown no later than without the fire.
- **SC-007**: The setting survives a restart, and a fresh install starts with *Off*.
- **SC-008**: With the system set to reduce motion, on each of macOS, Linux and Windows, a run of Wait, Follow or Arcade started once the system's answer is known (on Windows: read in the background when the practice window opens) shows a fire that does not move and throws no spark (macOS checked by the owner; Linux and Windows by the automated builds and testers).
- **SC-009**: Every adjustable value of both fires (FR-015) appears in the Advanced tab with a tooltip in the three languages; a check of the code finds no value that shapes a fire or its sparks outside the tuning sets, except the reading guard and the repeatability seed.
- **SC-010**: With all defaults, the pictures of Arcade's fire are identical to the pictures of spec 018 (same reference files), and *Default values* restores them after any tuning.
- **SC-011**: For the extreme values of every setting (each at its minimum and at its maximum), no fire pixel or spark above 12 % opacity meets a text, the framed note or the next note, and the verdicts are unchanged. In Arcade a text is every text of the picture (spec 009); on the score the protected boxes are those of FR-005 (the framed note, the next note and the practice palette), while the other notes and texts of the score stay readable through the translucent fire.

## Assumptions

- **Streak of full height**: 16 right notes in a row (adjustable on the built application at the owner's try).
- The fire is drawn at the bottom of the visible part of the score, not of the whole piece, and follows scrolling and page changes.
- *Skip* breaks the streak (a skipped note was not played right).
- In Follow, early and late notes count as right, as Arcade's streak does; wrong, missed and not judged notes break it.
- *Discreet* is 60 % of the height and the opacity of *Full*, as in spec 018.
- The fire reuses the fire of spec 018 (same colours, grid of big pixels, translucency, sparks), not a new look.
- Linux and Windows are checked by the automated builds and by testers' feedback; the owner checks macOS.
- The settings apply to the real game at its next run; the preview of the Advanced tab shows them at once; the ranges of each setting are chosen in the plan and adjusted at the owner's try.
- The quality tiers (full, balanced, performance) stay automatic, not a setting.
- Out of scope: a fire in Arcade other than spec 018's (it is only tuned), a fire around the framed note, sounds, and any change to the recognition or the judging.
