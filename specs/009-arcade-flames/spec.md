# Feature Specification: Arcade stage flames

**Feature Branch**: `feature/interactive-practice` (spec dir `009-arcade-flames`)

**Created**: 2026-10-14

**Status**: Draft

**Input**: Product-owner request (2026-10-14): "add flames in the background like a Rammstein concert: the better you play, the bigger the flames, without ever hindering reading". Visual proposals are in `mockups/`.

## Clarifications

### Session 2026-10-14 (the owner's answers to four questions, before this specification)

- Q: Where do the flames go? → A: Variant **A, foot of the neck** (`mockups/A-pied-du-manche.png`): the flames rise from the bottom edge of the game window, stronger towards the left and right corners, with a deliberately weakened central zone under the fret numbers, the verdict badge (and, as first said, the buttons: they are outside the picture, see the correction below). Variants B (side columns, `mockups/B-colonnes-laterales.png`) and C (wall of fire at the horizon, `mockups/C-mur-de-feu.png`) were shown and not chosen; they are kept for memory only.
- Q: What makes the flames grow ("the better you play")? → A: **The score as a percentage of the maximum** reached since the start of the run (the share of the maximum that the game already computes for its result): the height follows that percentage.
- Q: What happens when you miss or stop playing? → A: The flames **fall back progressively**, in a few seconds, as the percentage goes down, down to simple embers; never an abrupt extinction.
- Q: Can the effect be set or switched off? → A: **Yes**: one setting with three states, **off / discreet / full**, in the *Mode* tab with the other picture settings of the game (tilt, size of the rectangles).

### Session 2026-10-14 (`/speckit-clarify`)

- Q: Which range of the percentage of the maximum covers the whole height of the flames, from embers to full fire? → A: **Embers at 50 % or less, full fire at 100 %, linear between the two** (an 80 % gives 60 % of the height).
- Q: How many events must be judged before the flames follow the percentage? → A: **4 events** (about a first measure); until then only embers burn.
- Q: What fall and rise times for the flames? → A: **Fall about 3 seconds** from full fire to embers, **rise about 0.5 second**.
- Correction (found in the inspected source while planning, `plan.md` R6; no question to the owner): the share of the maximum is **0**, not 100 %, when nothing has been judged (the "100 %" at the top right of the picture is the tempo); the rule of 4 judged events stays, for the right reason: the share jumps from 0 to 100 % on a first right note. Wording corrected in FR-003 and in scenario 2 of story 1.
- Correction (found in the inspected source while planning, `plan.md` R7; no question to the owner): the buttons Restart, Pause and Stop are outside the picture (a row of widgets under the canvas), so the flames are drawn inside the picture only and the weakened central zone concerns the fret numbers and the verdict badge. Wording corrected in FR-001 and in scenario 2 of story 2.
- Q: Do the mockups of the plan (`mockups/A2-full.png`, `A2-discreet.png`, `A2-mouvement-coin-gauche.png`) suit for the shape, the colours and the difference between discreet and full? (gate G1) → A: **Yes, as they are**: shape and colours approved; *discreet* = the same shapes at **60 % of the height and of the opacity** of *full*, with **half the tongues** (`plan.md`, `research.md` R5). The numbers stay adjustable on the built application without redoing the specification; the motion is judged there (gate G2).
- Q: Which state of the setting is active by default? → A: **Discreet** (visible but lower and fainter than *full*).

### Session 2026-10-14 (the owner's answers to the questions of `checklists/flames.md`)

- CHK001, CHK004, CHK008 (reading): the contrast threshold applies to every text of the picture (string names, fret numbers, verdict badge, HUD, chord names, bend and technique labels); the seven window shapes are listed in SC-002; the reference scenes contain a verdict badge, a bend, a technique and a chord name while the flames burn at full level.
- CHK002, CHK003 (guard): the weakened central zone is quantified in FR-001 (at most 22 % of the height and of the opacity of the corners); "a flame meets a text" is defined in SC-002 (a flame item above 12 % of opacity inside the box of a text plus 4 pixels).
- CHK005, CHK023 (tiers and setting): the meaning of the tiers and of the three states is written in FR-007 and FR-008.
- CHK006 (translucent neck): the body of the neck lets the flames show faintly: written as an explicit exception in FR-005.
- CHK009 (motion): added as FR-013: when the system asks to reduce motion, the flames do not move (their height alone follows the play).
- CHK010 (monochrome): **there is no monochrome look of the flames**: FR-011 and the edge case are changed; the flames keep their colours and carry no information.
- CHK011, CHK012 (shapes, end of a run): maximum height at full level and absence from the result and preparation windows are written (FR-001, Edge Cases).
- CHK013 to CHK017, CHK021 (level): the share is the real ratio, "judged" is defined, the rise and fall times have a tolerance of 20 %, the maximum step between two pictures is 4 % (rise) and 1 % (fall) at 60 pictures a second, SC-001 gives reference heights, the level starts at 0 and a change of the setting applies to the next run.
- CHK018, CHK020, CHK022 (runs): the count of 4 judged events starts again at every run (replay, restart, run from the middle of the piece); the share does not change in a pause; at a stop or an end the flames disappear with the game window; a passage with fewer than 4 judged events in all keeps embers.
- CHK019 (pause and count-in): the flames **hold their level** during a pause and during the count-in that follows a resume, then go on from the same level.
- CHK024 (setting): a change made during a run applies to the next run.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - The flames show how well I play (Priority: P1)

A learner plays an Arcade run. Behind the game, flames burn at the bottom of the window like a stage pyrotechnic show. When the learner plays notes and chords right, the share of the maximum stays high and the flames are tall and bright; when the learner plays poorly, the flames are low.

**Why this priority**: it is the whole point of the feature: a visible, exciting reward for playing well, with no number shown (FR-021 of spec 003).

**Independent Test**: play (or replay a recorded run) at several levels of success and compare the pictures: the height of the flames follows the share of the maximum, and the same run with the effect off is identical in everything but the flames.

**Acceptance Scenarios**:

1. **Given** a run in which many events have been judged right, **When** the picture is drawn, **Then** the flames reach a high share of their maximum height, clearly higher than for a run of the same length with many misses.
2. **Given** the start of a run (nothing judged yet), **When** the picture is drawn, **Then** only embers burn, and they go on burning until 4 events have been judged: the flames do not leap from embers to full height on the first right note, when the share of the maximum jumps from 0 to 100 %.
3. **Given** two runs of the same score played with the effect on and off, **When** their judgements, scores and results are compared, **Then** they are identical.

---

### User Story 2 - Reading is never hindered (Priority: P1)

A learner reads the notes, chords, techniques, fret numbers, verdicts and top texts while the flames burn at full level. Nothing that must be read or played is covered, dimmed or made hard to see by the flames, at any window size.

**Why this priority**: the game is a learning tool: reading comes first (FR-053 of spec 003: audio and judging, then smoothness, then readability, then decoration). Flames are decoration.

**Independent Test**: draw reference scenes (notes, chords, bends, verdict badge, HUD) with the flames at full level on several window sizes and compare every readable item with the same scene without flames.

**Acceptance Scenarios**:

1. **Given** the flames at full level, **When** a scene with notes, a chord, a verdict badge, the fret numbers and the top texts is drawn, **Then** every one of those items is drawn on top of the flames, with a contrast against what is behind it at least as good as the minimum required for reading (SC-002).
2. **Given** any window shape and size supported by the game, **When** the flames are drawn, **Then** they stay inside the picture of the game and the central zone under the fret numbers and the verdict badge is weaker than the corners (the shape of `mockups/A-pied-du-manche.png`).
3. **Given** a machine that cannot keep the frame budget, **When** the picture quality drops, **Then** the flames are the first thing that is simplified or removed, before any information of the game (FR-054 of spec 003).

---

### User Story 3 - The flames fall back gently (Priority: P2)

When the learner makes mistakes or stops playing, the flames lose height progressively over a few seconds, down to embers, and rise again when the learner plays well again.

**Why this priority**: it makes the effect feel alive and fair (no sudden punishment) and keeps the picture calm.

**Independent Test**: feed the picture a drop of the share of the maximum and record the height of the flames over time: it falls smoothly within the stated time, never in one frame; and rises smoothly when the share rises.

**Acceptance Scenarios**:

1. **Given** flames at full level, **When** the share of the maximum falls sharply, **Then** the height of the flames goes down over a few seconds and never from full to embers in one frame.
2. **Given** a pause of the run, **When** the learner resumes, **Then** the flames keep the level they had and go on from there (no jump).

---

### User Story 4 - I can set or switch off the flames (Priority: P2)

A learner who finds the effect too strong, or whose machine is slow, sets it to *discreet* or switches it *off* in the *Mode* tab, next to the other picture settings, and the next run uses that setting.

**Why this priority**: the effect is decoration and a matter of taste; the owner must be able to remove it.

**Independent Test**: for each of the three states, start a run and check the picture: off shows no flame at all, discreet shows lower and fainter flames than full, full shows the whole effect.

**Acceptance Scenarios**:

1. **Given** the setting *off*, **When** a run is drawn, **Then** nothing of the effect is drawn and the cost of the picture is the same as before the feature.
2. **Given** the setting *discreet*, **When** the share of the maximum is the same as with *full*, **Then** the flames are lower and fainter than with *full*.
3. **Given** a setting chosen in the *Mode* tab, **When** the application is still open and a new run starts, **Then** the new run uses it; when the application is closed and opened again, the setting returns to its default (nothing is kept on disk, like the other picture settings).

---

### Edge Cases

- A very small or very wide window: the flames scale with the window and stay inside it; the weakened central zone keeps the same share of the width.
- A run that starts in the middle of the piece: the flames follow the share of the maximum of the events judged in this run only.
- The monochrome look of the game (spec 003): the flames keep their colours (FR-011).
- A pause, a stop, a restart or a replay from the result window: the flames of the new run start from embers; a pause holds the level.
- A run in which no event can be judged yet or in which the share cannot be computed: only embers burn.
- The result window and the preparation window: no flames there; at a stop or at the end of a run the flames disappear with the game window; the share does not change during a pause.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001** (placement): In an Arcade run, flames MUST burn behind the game, rising from the bottom edge of the picture of the game (the flames are drawn **inside the picture**: the buttons Restart, Pause and Stop are a row of widgets under it and are not concerned), stronger towards the left and right corners, with a weakened central zone under the fret numbers and the verdict badge (**in the middle of the width the height and the opacity are at most 22 % of those at the corners; at full level the flames at the corners are at most 55 % of the height of the picture**) (variant A, `mockups/A-pied-du-manche.png`).
- **FR-002** (what they show): The height and the brightness of the flames MUST follow the score as a share of the maximum reached since the start of the run (the share of the maximum of the result of the run, used as a **real number, not the rounded percent**: exactly 50 % gives embers and exactly 100 % gives full fire); **embers at 50 % or less, full fire at 100 %, linear between the two**; no number is shown during play (FR-021 of spec 003).
- **FR-003** (start of a run): Until **4 events** of the run have been judged (owner's answer, `/speckit-clarify` 2026-10-14; **judged** means judged right, wrong or missed, as in the maximum of the result; events not judged or passed automatically are not counted; the count starts again at zero at every run, replay or restart; a passage with fewer than 4 judged events in all keeps embers), only embers MUST burn, so that the flames do not leap from embers to full height on the first right note (the share of the maximum is 0 when nothing has been judged, and jumps to 100 % on a first right note).
- **FR-004** (fall and rise): When the percentage falls, the flames MUST lose height progressively over a few seconds (**about 3 seconds** from full height to embers, owner's answer of 2026-10-14), down to embers, never in one frame; when it rises, they MUST rise faster than they fall (**about 0.5 second**, owner's answer of 2026-10-14), without jumping. The times are given with a tolerance of 20 %; between two pictures at 60 a second the level changes by at most 4 % of its range when it rises and 1 % when it falls; at the start of a run the level is 0 and a change of the setting applies to the next run, so no jump is ever visible during a run.
- **FR-005** (never hindering): The flames MUST be drawn behind every other item of the picture; they MUST NOT cover, dim or reduce the contrast of any note, chord, technique mark, string, fret wire, fret number, verdict badge, HUD text or button, at any window size (FR-080 of spec 003). The translucent body of the neck (spec 003 FR-075) lets the flames show faintly through it: this is an explicit exception and not a covering, because everything that must be read is drawn on top and keeps the contrast of SC-002.
- **FR-006** (decoration only): The flames MUST NOT change the judgement, the score, the streak, the result, the timing of any event or the clock (SC-004).
- **FR-007** (quality): The flames are decoration: when the picture quality drops (levels FULL, BALANCED, PERFORMANCE of spec 003) they MUST be simplified first, then removed, before anything of the game is touched (FR-053, FR-054 of spec 003); in PERFORMANCE they MUST be absent. Simplified means: the tier FULL draws the whole effect, BALANCED draws half the tongues in two layers and no glow, PERFORMANCE draws no flame (CHK005).
- **FR-008** (setting): The *Mode* tab MUST offer one setting for the flames with three states, **off**, **discreet** and **full**; the setting is read when a run starts and kept until the application is closed, like the other picture settings; the default state is **discreet** (owner's answer of 2026-10-14); *off* draws nothing and adds no cost; *discreet* draws lower and fainter flames than *full* for the same percentage. *Discreet* is the same shapes at **60 % of the height and of the opacity** of *full* with half the tongues (approved on the mockups, gate G1); a change of the setting made during a run applies to the next run.
- **FR-009** (the three languages): The texts of the setting MUST exist in English, French and Spanish.
- **FR-010** (motion): The flames MUST move (a slow flicker) and the motion MUST hold still while the run is paused; the motion MUST NOT depend on the time of the player's clock in a way that moves any event (FR-055 of spec 003). The flames hold their **level** during a pause and during the count-in that follows a resume (the motion goes on during that count-in) and then go on from the same level (CHK019).
- **FR-011** (colours): There is **no monochrome look of the flames**: they keep their colours in every look of the game; they MUST NOT carry any information that is not also given by the rest of the picture.
- **FR-012** (documentation): The manuals and the practice guides in the three languages MUST say what the flames show, how to set or switch them off, and that they never change the judgement.
- **FR-013** (reduced motion): When the system asks to reduce motion, the flames MUST NOT move (no sway, no flicker); their height alone follows the play.

### Key Entities *(include if feature involves data)*

- **Flame level**: a number from embers to full height, computed for each picture from the share of the maximum of the run, with the fall and rise of FR-004; it is a picture-only value that never feeds the judging.
- **Flame setting**: one of off, discreet, full; the learner's choice, kept for the session of the application.
- **Share of the maximum**: the score of the run as a percentage of the maximum already reachable, as the result of the run gives it.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: On reference runs of low, medium and high success, the height of the flames is clearly ordered (low < medium < high); at 50 % or less only embers burn, at 100 % the fire is full, and in between the height is linear (for example 60 % of the height at 80 %); at the start of a run only embers are drawn. Reference heights: shares of 60 %, 80 % and 100 % give 20 %, 60 % and 100 % of the maximum height, to within 5 points.
- **SC-002**: With the flames at full level, on reference scenes of notes, chords, bends, techniques, verdict badge and HUD at several window sizes, every readable item keeps a contrast ratio of at least 4.5 to 1 against what is behind it, and no item is covered by the flames. The texts concerned are the string names, the fret numbers, the verdict badge, the HUD texts, the chord names and the bend and technique labels; "a flame meets a text" means a flame item above 12 % of opacity inside the box of the text widened by 4 pixels; the window shapes are 1280x720, 800x600, 640x480, 1500x500, 2000x1000, 1100x950 and 900x400; the reference scenes hold a verdict badge, a bend, a technique and a chord name while the flames burn at full level.
- **SC-003**: The height of the flames never changes by more than a fixed small share of its range between two consecutive pictures (no jump), and it takes about 3 seconds to fall from full to embers (the figures of FR-004). The fall takes 3 seconds and the rise 0.5 second, each to within 20 %; the level changes by at most 4 % of its range (rise) and 1 % (fall) between two pictures at 60 a second.
- **SC-004**: The same recorded run played with the flames off and with the flames at full level gives identical judgements, score, streak and result.
- **SC-005**: With the setting *off* the picture is identical to the picture without the feature, item for item.
- **SC-006**: On the ten-minute stress run of spec 003, the flames at full level do not make the game lose more frames than the same run without them by more than 1 % of the frames, and at the lowest quality level no flame is drawn.
- **SC-007**: The owner confirms on the built application that the flames look like the proposal (`mockups/A-pied-du-manche.png`), that they follow the way they play, and that reading is not hindered.

## Assumptions

- The share of the maximum is the one the result of the run already computes (spec 003); this specification does not change how it is computed.
- The exact shape and colours of the flames were approved by the owner on the mockups of the plan (2026-10-14), as assumptions of rendering; *discreet* is 60 % of the height and of the opacity of *full*, with half the tongues; the mockups are static images drawn on a real capture of the game, not a promise of rendering.
- The effect is for the Arcade run window only; the other modes, the result window and the preparation window have no flames.
- The effect is validated on macOS Apple Silicon first; Windows and Linux are not claimed to be validated.
- No sound is added.

## Out of Scope

- Flames, sparks or smoke over the neck, per-note flashes, explosions, screen shake and any effect in front of the game.
- Sound effects.
- Changing the score, the judging or the share of the maximum.
- Flames in the other practice modes, the result window and the preparation window.
- Settings other than the three states (no colour, no size, no speed setting in this version).
