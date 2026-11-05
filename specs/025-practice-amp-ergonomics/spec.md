# Feature Specification: The practice and amp windows fit the screen and say what to do

**Feature Branch**: `feature/interactive-practice` (no branch of its own; spec directory `specs/025-practice-amp-ergonomics`)

**Created**: 2026-11-05

**Status**: Draft

**Input**: User description: "spec 025 : ergonomie des fenêtres de pratique et d'ampli. (1) Bug : le bouton « Travailler cette piste » reste grisé ; la vérification du niveau d'entrée, finie sur « pas de signal » après 10 s sans jeu, ne se relance que si l'entrée est resélectionnée ; elle doit se relancer quand on joue, et la fenêtre doit dire clairement ce qui bloque et quoi faire, sous le bouton (une infobulle sur un bouton grisé ne s'affiche pas sur macOS), avec un bouton « Vérifier à nouveau ». (2) La fenêtre Pratique interactive dépasse la hauteur d'un écran standard : son contenu doit défiler et le bouton de lancement rester visible. (3) La fenêtre Ampli et effets ne tient pas à l'écran (toutes les commandes ne se voient pas, même agrandie) et ses noms sont peu clairs (« Son d'ampli » doit se lire comme un interrupteur, « Baffle » n'est pas compris) : la repenser en onglets (pédales avant, ampli, effets après, sortie), renommer les commandes, tenir dans environ 600 px de haut, chaque onglet défilant si besoin. Retours du propriétaire à la porte T087 de la spec 023 (2026-11-05) ; maquette proposée et acceptée comme exemple ; uniquement des composants que la boîte à outils d'interface de TuxGuitar offre déjà (onglets, défilement, interrupteurs, cases à cocher, infobulles) ; parité macOS, Linux, Windows ; textes en anglais, français, espagnol."

**Where it comes from**: the owner's gate T087 of spec 023 (2026-11-05, recorded in `specs/023-native-recognition-engine/final-report.md`). Every recognition step passed, but the owner met three obstacles that any learner will meet before the first note:

- *Practise this track* stayed greyed. The input level check listens for about 10 seconds; when nobody plays during that time it ends on "no signal", and it is not run again until the input is chosen again. The owner found the way out (unselect the input, select it again, play) by chance; the line that explains the greyed button did not tell what to do.
- The *Interactive practice* window is taller than the owner's screen, so the start button could not be reached.
- The *Amp and effects* window (spec 022) shows only part of its controls, even when enlarged, and some names are not understood: *Amp sound* does not read as a switch, *Cabinet* (*Baffle* in French) means nothing to a learner.

A mock-up of both windows was shown to the owner and accepted as the example to follow (see Assumptions).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - The start button is never stuck, and the window says what to do (Priority: P1)

A learner opens the practice window with the guitar plugged in but does not play straight away. Today the level check gives up after about 10 seconds and the start button stays greyed for good, with a line that does not say how to get out. With this story, the check starts again by itself as soon as the guitar is heard, the start button becomes available, and while it is greyed a line right under it says in plain words what blocks it and what to do, with a **Check again** button next to it.

**Why this priority**: it blocks every practice mode. A learner who cannot start does not reach anything else.

**Independent Test**: open the practice window, wait 15 seconds without playing, then play a few notes: the start button becomes available within a few seconds, without touching the input choice.

**Acceptance Scenarios**:

1. **Given** the practice window is open and the level check ended on "no signal" because nothing was played, **When** the learner plays a few notes, **Then** the level is checked again by itself and the start button becomes available once the level is usable, without the input being chosen again.
2. **Given** the start button is greyed, **When** the learner looks at the window, **Then** a line directly under the button, visible without hovering, names the cause and the action in plain words (for example "Play a few notes: the input has not heard your guitar yet").
3. **Given** the start button is greyed because of the input level, **When** the learner presses **Check again**, **Then** a new level check starts and the line says that it is listening.
4. **Given** the cause changes (no sound, level too low, input saturating, interface unplugged, microphone access refused), **When** the line is shown, **Then** it names that cause and its own action; it never shows a cause that no longer applies.
5. **Given** the start button is available, **When** the learner looks at the window, **Then** no blocking line is shown.

---

### User Story 2 - The practice window fits the screen (Priority: P1)

A learner on a laptop screen opens the *Interactive practice* window: it fits the screen, its tab content scrolls when it is taller than the space left, and the start button with its line stays visible at the bottom whatever the tab.

**Why this priority**: the owner could not reach the start button; on a small screen the window is unusable.

**Independent Test**: on a screen 768 pixels high (or with the window made as short as it allows), open each tab: the start button is visible and every control of the tab can be reached by scrolling.

**Acceptance Scenarios**:

1. **Given** a screen 768 pixels high, **When** the practice window opens, **Then** the whole window, title bar included, is on the screen.
2. **Given** a tab whose content is taller than the space left, **When** the learner scrolls inside it, **Then** every control of that tab can be reached.
3. **Given** any tab is shown and scrolled anywhere, **When** the learner looks at the bottom of the window, **Then** the start button and its line are visible.
4. **Given** the learner makes the window taller or shorter, **When** it is resized, **Then** the scrolling follows and nothing is cut off without a way to reach it.

---

### User Story 3 - The amp window fits the screen, in tabs, with clear names (Priority: P2)

A learner opens *Amp and effects*: the switch that turns the amp sound on reads as a switch ("Hear my guitar through the amp"), the controls are sorted in tabs (pedals before the amp, the amp, effects after the amp, output), the window fits in about 600 pixels of height, each tab scrolls if needed, and every name says what it does (no *Cabinet* / *Baffle* without explanation).

**Why this priority**: the amp sound works (owner: "fonctionnellement ça marche bien"); its window is hard to use but does not block practice.

**Independent Test**: open the amp window on a screen 768 pixels high: every control can be reached through the tabs and scrolling; a learner told "turn the amp sound on and soften the speaker sound" finds both controls without help.

**Acceptance Scenarios**:

1. **Given** the amp window opens, **When** the learner looks at it, **Then** its height is at most about 600 pixels and it is entirely on a screen 768 pixels high.
2. **Given** the amp window, **When** the learner looks at the top, **Then** the on/off switch of the amp sound, the sound (preset) choice and the delay estimate are visible whatever tab is chosen.
3. **Given** the tabs *Pedals before the amp*, *Amp*, *Effects after the amp* and *Output*, **When** the learner opens each, **Then** it holds only its own controls, and scrolling reaches all of them.
4. **Given** the switch of the amp sound, **When** it is off and then on, **Then** its label and state read as a switch (for example a check box "Hear my guitar through the amp"), not as a button whose state must be guessed.
5. **Given** the former *Cabinet* control, **When** the learner reads it, **Then** it is named after what it does (for example "Speaker simulation") and its tip explains it.
6. **Given** the sound (preset) management (save as, rename, delete, restore), **When** the learner uses it, **Then** every action of today is still there and works as before, and the name field is asked only when it is needed.
7. **Given** a sound saved before this change, **When** the amp window opens, **Then** the sound, its pedals, their order and their settings are the same as before.

---

### Edge Cases

- The level check is running when the learner presses **Check again**: one check runs, not two.
- The guitar is heard while a practice run is going: the level check rules of the run are unchanged (no new check starts in the middle of a run by itself).
- The level check ended on a cause the learner must fix outside the window (input saturating, interface unplugged, microphone access refused): playing again does not hide the cause; the line keeps naming it until it is fixed or a check passes.
- The window is restored at a size saved before this change that is taller than the screen: it is brought back within the screen.
- A very small window (shorter than the tabs, the bottom line and one row of controls): the bottom line and the start button keep priority over the tab content.
- The texts are longer in French and Spanish: the line under the start button wraps; nothing is cut off.
- Linux and Windows: same layout and same behaviour; no line says "only on macOS".
- Dark and light appearance: both readable.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: When the input level check has ended on "no signal", the system MUST start a new check by itself as soon as the guitar is heard while the practice window is open and no run is going, without the input being chosen again.
- **FR-002**: While the start button is greyed, the practice window MUST show, directly under it and without hovering, a line that names the cause and the learner's action, one wording per cause (no sound yet, level too low, input saturating, input lost or unplugged, microphone access refused, no track chosen, waiting for the guitar for a one-click start).
- **FR-003**: When the cause is the input level, the line MUST offer a **Check again** action that starts a new level check and says it is listening; a second press during a check MUST NOT start a second one.
- **FR-004**: The line MUST disappear as soon as the start button is available, and MUST follow the cause when it changes.
- **FR-005**: The practice window MUST fit a screen 768 pixels high when it opens, including a size restored from before this change.
- **FR-006**: The tab content of the practice window MUST scroll when it is taller than the space left; the start button and its line MUST stay visible at the bottom for every tab and every scroll position.
- **FR-007**: The amp window MUST be organised in four tabs: pedals before the amp, the amp, effects after the amp, output (with the guitar and score volumes); the amp sound switch, the sound choice and the delay estimate MUST stay visible above the tabs.
- **FR-008**: The amp window MUST open at most about 600 pixels high and fit a screen 768 pixels high; each tab MUST scroll when its content is taller than the space left.
- **FR-009**: The amp sound switch MUST read as an on/off control with a label that says what it does (for example "Hear my guitar through the amp"); the *Cabinet* control MUST be renamed after what it does (for example "Speaker simulation") with a tip that explains it.
- **FR-010**: Every control and action of the amp window today (sounds and their management, output, volumes, pedals with move up, move down, remove and add, the amp settings, the cabinet, the effects after the amp) MUST remain available and behave as before; saved sounds and settings MUST load unchanged.
- **FR-011**: Every new or renamed text MUST exist in English, French and Spanish, with a tip where the control's meaning is not obvious.
- **FR-012**: The changes MUST use only kinds of controls that TuxGuitar's interface toolkit already offers (tabs, scrolling panels, toggle buttons, check boxes, tips) and MUST behave the same on macOS, Linux and Windows.
- **FR-013**: The level check rules during a practice run, the recognition, the amp sound and the practice modes MUST be unchanged.

### Key Entities

- **Start blocker**: why the start button is greyed now (no track, waiting for the guitar, level not checked yet, no sound, level too low, saturating, input lost, access refused), with its line and, for level causes, the **Check again** action.
- **Amp window tab**: one of four groups of the amp window's controls (pedals before, amp, effects after, output); the switch, sound choice and delay stay outside the tabs.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A learner who opens the practice window, waits 15 seconds without playing, then plays, can start a practice within 5 seconds of the first notes, without changing the input, in 10 tries out of 10.
- **SC-002**: For each cause of a greyed start button, the line under it names that cause and its action (checked for every cause, in the three languages).
- **SC-003**: On a screen 768 pixels high, the practice window and the amp window are entirely visible when they open, and every control of every tab can be reached; the start button is visible in every tab.
- **SC-004**: The amp window opens at most about 600 pixels high.
- **SC-005**: The owner, on the built application, turns the amp sound on and finds the speaker simulation without help, and judges both windows clear (gate on a test sheet).
- **SC-006**: Every existing practice, recognition and amp test still passes; sounds saved before the change load unchanged.

## Assumptions

- The mock-up shown to the owner on 2026-11-05 is the example: the practice window keeps its tabs (Track, Input, Mode, Passages, Advanced) with a scrolling content and a fixed bottom area (the blocking line, **Check again**, the start button); the amp window has the switch, the sound choice with a **Save…** button and the other sound actions grouped, and the delay above four tabs.
- "A standard screen" is taken as 768 pixels high (the smallest common laptop height); the owner's own screen is larger.
- On macOS a greyed button does not show its tip; this is why the cause is a visible line, not a tip.
- The exact wordings (for example "Hear my guitar through the amp", "Speaker simulation", "Check again") are proposals; `/speckit-clarify` may settle them with the owner.
- The cause of the stuck button was found in the source on 2026-11-05: the check is started again only when the input changes or has never been checked; it dates from spec 002 and is not a spec 023 regression.
- The rest of TuxGuitar's windows are out of scope; only the practice window and the amp window change.
- The JavaFX and Qt toolkits are not built in the packages; the layout is required for the SWT builds of macOS, Linux and Windows.
