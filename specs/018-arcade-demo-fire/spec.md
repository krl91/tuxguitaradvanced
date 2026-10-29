# Feature Specification: Arcade's flames drawn like the classic fire of the Amiga demos, translucent

**Feature Branch**: `feature/interactive-practice` (no branch of its own; spec directory `specs/018-arcade-demo-fire`)

**Created**: 2026-10-30

**Status**: Draft

**Input**: User description: "pour le feu d'Arcade façon demo Amiga (spec 018) (pense à garder de la transparence des flammes)". The owner asked on 2026-10-29 for a cooler 2D render of Arcade's flames, "like the demo makers on the Amiga". A live demo with three variants was shown (`https://claude.ai/artifact/JygzDttzmT3dwJLRFR9bP6`); the owner answered "je veux exactement ça" and chose **variant A, the classic fire alone** (not the plasma, not the copper bars). On 2026-10-30 the owner added: **keep the flames translucent**.

## Clarifications

### Session 2026-10-29/30 (before this specification)

- Q: Which of the demo's renders? → A: **A · the classic fire alone**: a coarse grid of heat drawn with big hard pixels, each cell fed by the cells below it and cooling as it rises, coloured from deep red through red, orange and yellow to white at the hottest; **sparks** thrown on each right note. (Chosen among: classic fire, fire with plasma, fire with copper bars.)
- Q: Opaque fire or see-through? → A: **Translucent**: the game behind the fire (the neck, the lanes, the background) must keep showing through the flames, as with today's flames; the cold parts of the fire are fully transparent (no black band), and even the hottest parts let the game show through.

### Session 2026-10-30

- Q: How see-through must the fire be where it is hottest? → A: **As today's flames**: at most about **60 % of opacity** (the opacity of the strongest layer of spec 009's tongues), so the game stays clearly visible behind the fire.
- Q: Does the new fire replace today's flames, or do both stay to choose from in the Mode tab? → A: **It replaces them**: one look only; the setting stays *off / discreet / full*.
- Q (asked while planning): the tests of spec 009 that pin the **shape** of the tongues (their number, their layers, the reference picture file of the flames) cannot pass with the new look: what about them? → A: **A targeted exception**: only what pins the tongue shape may be adapted (tongue and layer counts, exact proportional height of a tongue, the regenerated reference file `flame-reading.scene.txt`); every rule of spec 009 (heights, corners 55 %, middle 22 %, discreet 60 %, nothing when off, contrast of texts, identical verdicts) stays checked, and each changed line is listed in its commit (Constitution XI).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - The flames look like a demo-scene fire (Priority: P1)

A learner plays an Arcade run. Behind the game, at the bottom of the picture, a fire made of big visible pixels burns and licks upwards, like the fires of the old Amiga demos: hot white and yellow at its base, orange and red higher, fading out as it rises. It follows the play exactly as today's flames do: embers when the run goes badly or has just started, a high fire when the learner plays well, stronger at the left and right corners than in the middle.

**Why this priority**: it is the owner's whole request: the same reward as spec 009, with the look they chose.

**Independent Test**: replay recorded runs of low, medium and high success with the setting *full* and compare the pictures with the demo's variant A and with the heights of spec 009: the look is the coarse-pixel fire, and the height is ordered and matches spec 009's figures.

**Acceptance Scenarios**:

1. **Given** a run at a high share of the maximum, **When** the picture is drawn, **Then** a fire of big hard-edged pixels burns from the bottom edge, hottest (white, yellow) at its base, turning orange then red as it rises, and fading out at its top.
2. **Given** runs at 50 % or less, 80 % and 100 % of the maximum, **When** they are drawn, **Then** the height of the fire follows spec 009 (embers, 60 % of the maximum height, full height), with the same fall (about 3 s) and rise (about 0.5 s).
3. **Given** the start of a run, **When** fewer than 4 events have been judged, **Then** only embers burn (spec 009 FR-003).
4. **Given** the setting *discreet*, **When** the share is the same as with *full*, **Then** the fire is lower and fainter than with *full*; **given** *off*, nothing of the fire is drawn and the picture is the same as before the feature.
5. **Given** the left and right corners and the middle of the width, **When** the fire burns at full level, **Then** it is strong at the corners and weak in the middle, under the fret numbers and the verdict badge (spec 009 FR-001).

---

### User Story 2 - The game shows through the fire (Priority: P1)

The fire is see-through: the learner still sees the neck, the lanes and the background through it, as with today's flames. Where the fire is cold there is nothing at all (no black band at the bottom of the picture); where it is hot it is brighter but still lets the game show through.

**Why this priority**: the owner asked for it explicitly; an opaque fire would hide the bottom of the game.

**Independent Test**: draw the same reference scene with the fire at full level and without fire, and compare the colours behind the fire: everywhere the game's colour still contributes visibly, and where the fire is cold the picture is identical to the picture without fire.

**Acceptance Scenarios**:

1. **Given** the fire at full level, **When** any point covered by the fire is compared with the same point without fire, **Then** the game's own colour still shows through (the fire's opacity is never full).
2. **Given** a cell of the fire that has cooled to nothing, **When** the picture is drawn, **Then** that place is exactly the picture without fire (no black, no darkening).
3. **Given** embers only, **When** the picture is drawn, **Then** the bottom edge of the picture shows a low glow and no dark band.

---

### User Story 3 - Sparks on each right note (Priority: P2)

Each time the learner plays a note right, a few sparks fly up from the fire and fade, the more the stronger the streak, like the bursts of the demos. They stay behind the game and never cover anything that must be read.

**Why this priority**: it is part of the chosen variant and makes each right note felt, but the fire alone already answers the request.

**Independent Test**: replay a recorded run and count the bursts: one burst per note judged right, none for a wrong or missed note, and none when the setting is *off*; check on the reference scenes of spec 009 that no spark meets a text.

**Acceptance Scenarios**:

1. **Given** a note judged right, **When** the next pictures are drawn, **Then** a short burst of sparks rises from the fire and fades within about one second.
2. **Given** a wrong or missed note, **When** it is judged, **Then** no spark is thrown.
3. **Given** a long streak of right notes, **When** sparks are thrown, **Then** the bursts are bigger than at the start of a streak, within a fixed maximum.
4. **Given** sparks in the air, **When** they reach the zone of a text (fret numbers, verdict badge, HUD, chord names, labels), **Then** they are not drawn there (the reading guard of spec 009 applies to them).

### Edge Cases

- Window resized during a run: the fire is rebuilt to the new width without a visible jump of its level.
- Very small or very wide window (the seven window shapes of spec 009 SC-002): the pixels of the fire stay big and visible, the fire still fits inside the picture.
- Pause: the fire holds its level and its motion holds still, the sparks hold still too (spec 009 FR-010); the count-in after a resume: the motion goes on, the level holds.
- Reduced motion asked by the system: the fire does not move and no spark is thrown; its height alone follows the play (spec 009 FR-013).
- The picture quality drops (tiers FULL, BALANCED, PERFORMANCE of spec 003): at BALANCED the sparks go and the fire is drawn with coarser pixels; in PERFORMANCE nothing of the fire is drawn (FR-008).
- End of a run, result and preparation windows: no fire, as today.
- Many right notes very close together (fast passages): the number of sparks in the air stays under a fixed maximum.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001** (look): In an Arcade run with the flames on, the flames MUST be drawn as a coarse fire of big hard-edged square pixels (each pixel of the fire covers several pixels of the screen and its edges are not smoothed), whose heat rises from the bottom edge of the picture, spreads upwards and cools as it rises, with a flicker that keeps changing the tongues; the colours go from red at the coolest visible heat through orange and yellow to white at the hottest (variant A of the demo).
- **FR-002** (translucency): The fire MUST be translucent at every point: its opacity grows with the heat but MUST NOT exceed **60 %** at the hottest point (as today's strongest flames), so that the game behind it always shows through; where the heat is nil the fire MUST draw nothing (no black and no darkening of the game); where a spark crosses the fire, the opacity of both together MUST NOT exceed 60 % either.
- **FR-003** (what it shows, unchanged): The height and the brightness of the fire MUST follow spec 009 FR-002 to FR-004 (share of the maximum, embers at 50 % or less, full fire at 100 %, linear between; 4 judged events before leaving the embers; fall about 3 s, rise about 0.5 s).
- **FR-004** (placement, unchanged): The fire MUST stay inside the picture, behind every other item of the game, stronger at the left and right corners, with the weakened central zone of spec 009 FR-001 (in the middle at most 22 % of the height and of the opacity of the corners; at most 55 % of the height of the picture at the corners at full level).
- **FR-005** (sparks): Each note judged right MUST throw a short burst of sparks rising from the fire and fading within about one second; their number grows with the streak up to a fixed maximum; wrong, missed and unjudged events throw none; the sparks in the air at the same time MUST stay under a fixed maximum.
- **FR-006** (reading guard): The fire and the sparks MUST NOT cover, dim or reduce the contrast of any text or item that must be read or played (spec 009 FR-005 and SC-002, with the same texts, window shapes and 12 % threshold).
- **FR-007** (setting, unchanged): The *Mode* tab setting *off / discreet / full* of spec 009 FR-008 MUST keep its meaning and its default (*discreet*): *discreet* is a lower and fainter fire (60 % of the height and of the opacity of *full*) with fewer sparks; *off* draws nothing, throws no spark and adds no cost.
- **FR-008** (quality tiers): Under the tiers of spec 009 FR-007, FULL draws the whole effect; BALANCED removes the sparks and draws the fire with coarser pixels; PERFORMANCE draws nothing of the fire.
- **FR-009** (decoration only): The fire and the sparks MUST NOT change the judgement, the score, the streak, the result, the timing of any event or the clock.
- **FR-010** (motion and pause): The motion MUST hold still during a pause, MUST stop when the system asks to reduce motion (the height alone follows the play, no spark), and MUST NOT depend on the player's clock in a way that moves any event.
- **FR-011** (repeatable): For the same run replayed with the same inputs, the pictures of the fire and the sparks MUST be the same, so that they can be checked by tests.
- **FR-012** (documentation): The manuals and the practice guides in the three languages MUST describe the new look, the sparks, and that the fire is see-through and never changes the judgement.

### Key Entities

- **Fire**: a coarse grid of heat values under the game, fed at its bottom according to the flame level and the corners, spreading upwards and cooling; each cell is drawn as one big pixel whose colour and opacity come from its heat.
- **Heat colour scale**: the mapping from heat to colour and opacity: nil heat → nothing drawn; low → dark red, faint; then red, orange, yellow; highest → white, still translucent.
- **Spark**: a small bright point thrown from the fire on a right note, rising and fading within about one second.
- **Flame level and flame setting**: unchanged from spec 009.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: On the owner's look check (gate), the owner judges the fire on the built application to be "the demo's variant A" (big pixels, white-yellow base, red top, flicker) and keeps it.
- **SC-002**: At every point covered by the fire or its sparks at full level, the game's colour still contributes at least **40 %** of the colour seen (the opacity of the fire and its sparks never exceeds 60 %); where neither a fire cell nor a spark is drawn, the picture is identical to the picture without fire, pixel for pixel.
- **SC-003**: The heights of spec 009 SC-001 hold: shares of 60 %, 80 % and 100 % give 20 %, 60 % and 100 % of the maximum height, to within 5 points; the fall and rise of spec 009 SC-003 hold.
- **SC-004**: On the reference scenes and the seven window shapes of spec 009 SC-002, every readable item keeps a contrast ratio of at least 4.5 to 1, and no fire pixel or spark above 12 % of opacity meets the box of a text widened by 4 pixels.
- **SC-005**: On a replayed run, the number of bursts of sparks equals the number of notes judged right, and is 0 with the setting *off*; no spark lives more than about one second.
- **SC-006**: The same recorded run played with the fire off and at full level gives identical judgements, score, streak and result; with *off* the picture is identical to the picture without the feature.
- **SC-007**: The picture keeps the frame rate of today's flames on the owner's machine with the setting *full* (no drop of quality tier caused by the new fire on the reference runs).

## Assumptions

- The new look replaces spec 009's vector tongues; there is no setting to choose between the old and the new look (owner's answer of 2026-10-30).
- Everything spec 009 decided about what the flames show (level, start, fall and rise, placement, setting, tiers, pause, reduced motion, decoration only, no monochrome look) stays; only the look changes, and the sparks are added.
- The sparks stay behind the game, inside the flame zone and above it, like the fire; they are not the "effects in front of the game" spec 009 kept out of scope.
- The size of the big pixels is a choice of the plan, adjustable on the built application; the demo's grid (about 192 by 108 for a 16:9 picture) is the starting point.
- The 60 % maximum opacity (SC-002, owner's answer of 2026-10-30) is a ceiling: the owner's look gate may lower it, not raise it, without changing this specification.
- Plasma, copper bars and any other effect of the demo are out of scope.
- The tuner, the Wait and Follow modes and the editor are out of scope.
