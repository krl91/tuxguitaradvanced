# Feature Specification: An adjustable timing window for Follow and Arcade

**Feature Branch**: `feature/interactive-practice` (the spec directory is `012-adjustable-timing-window`)

**Created**: 2026-10-15

**Status**: Draft

**Input**: User description: "The tempo tolerance is a bit hard; I think there should be an adjustable time window that is a little wider, with a default a bit more lenient than now" (owner, 2026-10-15, after trying the built application)

## Why this exists

In the tempo-following mode and in Arcade the learner plays against the clock: each note must arrive close enough to its written time to count as **on time**. Today the learner chooses between **three fixed presets** (Strict, Normal, Lenient). The owner, trying the built application, found the tolerance **a little hard**, and wants (1) a window he or she can **adjust continuously**, (2) a **wider** reach than the present presets give, and (3) a **default a bit more lenient** than today's Normal.

What the code does today (inspected 2026-10-15, `TimingTolerance.Preset`): each preset gives two windows around the target time of an event, as a fraction of the local beat with a floor and a ceiling in milliseconds. The **tight window** decides *on time*; the **outer window** decides *early* or *late*; beyond it nothing matches. For the default **Normal** the tight window is **15 % of the beat, never under 60 ms nor over 150 ms**, which is **75 ms each side at 120 beats a minute** (150 ms at 60 bpm, 60 ms at 180 bpm); the outer window is 40 % of the beat between 150 and 450 ms. **Lenient** is 25 % between 90 and 250 ms (125 ms each side at 120 bpm). The numbers are marked *provisional* in the source until validated on real play.

## Clarifications

### Session 2026-10-15

- Q: How does the learner adjust the window, and over what range? → A: A **continuous slider in milliseconds each side, at a reference of 120 beats a minute, from 40 ms to 200 ms** (today's widest, Lenient, is 125 ms). It **replaces** the Strict / Normal / Lenient list; an older saved preset is converted to its width at that reference (Strict 50 ms, Normal 75 ms, Lenient 125 ms).
- Q: What is the default? → A: **100 ms each side at 120 bpm** (today's Normal is 75 ms: a third more lenient).
- Q: Where is the remembered value set? → A: **One setting**: what the learner chooses in the preparation window is remembered at once and is also the value shown in the Configuration window (one place of truth, no separate saved default).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - The learner widens or narrows the timing window (Priority: P1)

In the preparation window the learner sets how close to its time a note must be to count as on time, by a control that moves **continuously** between a narrow and a wide window, and sees the value they have chosen.

**Why this priority**: it is the owner's request: three presets are too coarse and the widest is not wide enough.

**Independent Test**: set the window to its narrowest, a middle and its widest value, play (or replay) the same performance in Follow and in Arcade, and compare how many notes are judged on time.

**Acceptance Scenarios**:

1. **Given** the preparation window in Follow or Arcade mode, **When** the learner moves the control, **Then** the chosen value is shown and applies to the next run.
2. **Given** the same performance with notes 100 ms late at 120 bpm, **When** the window is widened beyond 100 ms each side, **Then** those notes are judged on time; **When** it is narrowed below, **Then** they are judged late.
3. **Given** a window that is wide, **When** two events are close together, **Then** the windows never overlap (each outer window is still cut at the middle of the gap to its neighbour).

---

### User Story 2 - The default is more lenient than today (Priority: P1)

A learner who never touches the control gets a default window a bit more lenient than today's Normal.

**Why this priority**: the owner found today's default too hard; most learners will not adjust anything.

**Independent Test**: on a fresh configuration, replay a fixed performance and check that notes within the new default window are on time and that the default window is wider than the one of today's Normal at the same tempo.

**Acceptance Scenarios**:

1. **Given** a fresh installation, **When** the learner starts Follow or Arcade without touching the control, **Then** the window is the new default.
2. **Given** a performance whose notes are 90 ms late at 120 bpm, **When** judged with the new default, **Then** they are on time (today's Normal, at 75 ms each side, judges them late).

---

### User Story 3 - The choice is remembered, and a setting saved before still loads (Priority: P2)

The learner's window comes back as they left it, and a configuration saved by an earlier version, which holds a preset name, still loads and gives a sensible window.

**Why this priority**: losing the choice at every launch would make the adjustment tiresome, and an upgrade must not reset or break anyone's configuration.

**Independent Test**: set a value, restart the application, check it is restored; load a configuration that holds each of the three old preset names and check each maps to a window.

**Acceptance Scenarios**:

1. **Given** a chosen value, **When** the application is closed and opened again, **Then** the same value is shown.
2. **Given** a configuration saved by the previous version with the preset Strict, Normal or Lenient, **When** it is loaded, **Then** the window is the corresponding one and nothing fails.
3. **Given** a missing or invalid saved value, **When** the application starts, **Then** the default is used and the application does not fail.

---

### User Story 4 - Nothing else changes (Priority: P2)

Everything outside the width of the window stays as it is: the recognition, the latency compensation, the other judging rules and the existing figures.

**Why this priority**: a wider window must not change what is recognised, nor make results unrepeatable.

**Independent Test**: replay the recorded performances with the window set to the value that equals today's Normal and compare with the figures recorded before.

**Acceptance Scenarios**:

1. **Given** the window set to today's Normal, **When** the recorded performances are judged, **Then** every judgement, score and result equals the figures before this feature.
2. **Given** the same performance and the same window, **When** it is judged twice, **Then** the result is identical.

---

### Edge Cases

- A very slow tempo (the window grows with the beat) and a very fast one (the floors and the ceilings keep it usable): the window must stay inside the floor and the ceiling of the chosen value at every tempo.
- Two events very close together (a fast passage): the outer windows are cut at the middle of the gap, whatever the width.
- The widest setting with notes that are far from their time: a note that is much too late must still not be judged on time, and a note that belongs to the next event must not be taken for this one.
- A saved value outside the allowed range (edited by hand, or from another version): it is brought back inside the range, not refused with an error.
- The learner changes the window **during a run**: it applies to the next run (as the other settings do), not the current one.
- Wait mode has no tempo: the setting is not shown there and does nothing there.
- The setting in the Configuration window and the one in the preparation window must not disagree about which is the starting value.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The learner MUST be able to adjust the timing window **continuously** in the Follow and Arcade modes, and MUST see the chosen value.
- **FR-002**: The learner adjusts the window with a **continuous slider in milliseconds each side, measured at a reference of 120 beats a minute, from 40 ms to 200 ms**; this reaches wider than today's Lenient (125 ms) and narrower than today's Strict (50 ms), so that no window available today is lost. The slider replaces the Strict / Normal / Lenient list.
- **FR-003**: The **default** window is **100 ms each side at 120 beats a minute**, a third more lenient than today's Normal (75 ms).
- **FR-004**: The window MUST keep growing and shrinking with the tempo, within a floor and a ceiling, as today's presets do, so that the same setting is usable at 60 and at 180 beats a minute.
- **FR-005**: The setting MUST apply to **both Follow and Arcade**, which share the same judging, and MUST scale the **outer window** (early and late) with the tight one so that *early* and *late* keep their meaning; the outer window MUST still be cut at the middle of the gap to the neighbouring events.
- **FR-006**: The choice MUST be remembered between runs and between launches as **one setting**: the value chosen in the preparation window is remembered at once and is the value the Configuration window shows. A configuration saved by an earlier version with a preset name MUST still load and map to a window of the same width as that preset (Strict 50 ms, Normal 75 ms, Lenient 125 ms at 120 bpm).
- **FR-007**: A missing, invalid or out-of-range saved value MUST fall back to the default or to the nearest allowed value without an error.
- **FR-008**: A change MUST apply to the **next run**, not to a run in progress.
- **FR-009**: The judging MUST remain **deterministic**: the same performance and the same window give the same result.
- **FR-010**: With the window set to the width of today's Normal, every judgement, score and result MUST equal what the application gave before this feature (no change of recognition or of any other rule).
- **FR-011**: The new control and its texts MUST exist in the three shipped languages (English, French, Spanish) and be described in the documentation.
- **FR-012**: The feature MUST NOT change the recognition, the latency compensation of the route, the Wait mode, or the categories of judgement.

### Key Entities

- **Timing window**: how far from its written time a note may be and still count as on time, with its wider outer part for early and late. Attributes: the width chosen by the learner, the floor and the ceiling in milliseconds, the share of the beat.
- **Saved setting**: the value remembered between launches, possibly an old preset name.
- **Preset (old)**: Strict, Normal, Lenient, kept only so that an older saved setting still loads.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: The learner can set the window to **at least 5 distinct widths** between the narrowest and the widest, in the preparation window, in **under 10 seconds**, and the chosen value is shown.
- **SC-002**: The widest setting (200 ms each side at 120 bpm) is **wider than today's Lenient** (125 ms), and the narrowest (40 ms) is **no wider than today's Strict** (50 ms).
- **SC-003**: With the **new default (100 ms)**, a note played **90 ms late at 120 bpm is judged on time**, where today's Normal (75 ms) judges it late; and with the window set to 75 ms the same note is judged late as before.
- **SC-004**: With the window set to the width of today's Normal, **all recorded performances give identical judgements, scores and results** to those before this feature (0 differences).
- **SC-005**: A setting saved by the previous version in each of its three preset names **loads in 3 of 3 cases** and gives a window of the width of that preset.
- **SC-006**: The chosen value **survives a restart** in 100 % of the checks, and an invalid saved value never prevents the application from starting.
- **SC-007**: The control and its texts are present in **3 of 3 languages**, and a window with the longest text shows nothing cut or outside.
- **SC-008**: The owner, trying the built application, finds the default **less hard** and the adjustment **usable**; the answer is recorded.

## Assumptions

- The feature applies to Follow and Arcade (they share one judging); the Wait mode has none and is out of scope.
- The width of the window is the **tight** one (the *on time* window); the outer (early/late) window follows it proportionally and stays cut at the middle of the gap, so that no new judging category appears.
- The old three presets are **no longer shown**: they survive only as the mapping of an older saved setting to a width (decided at clarification).
- The numbers of today's presets are provisional in the source; this feature changes the default and the range at the owner's request and does not claim they are validated on real play (constitution XII): the owner's trial (SC-008) is the check.
- The existing recorded performances and the replay tests (the Follow and Arcade functional tests) are the base for SC-004.
- The work follows Spec Kit in order and is delivered in small phases; the build stays green after each; tests are written first and seen failing.
