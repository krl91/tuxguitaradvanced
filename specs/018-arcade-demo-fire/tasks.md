---
description: "Tasks of spec 018: Arcade's flames drawn like the classic fire of the Amiga demos, translucent"
---

# Tasks: Arcade's flames drawn like the classic fire of the Amiga demos, translucent

**Input**: `specs/018-arcade-demo-fire/` (spec.md, plan.md, research.md, data-model.md, contracts/fire-drawing.md, quickstart.md)

**Tests**: written first and seen failing for every behaviour (owner's rules, constitution XI). **Existing tests are never modified**, with **one exception granted by the owner on 2026-10-30** (spec Clarifications, research R8): only the assertions that pin the **shape of spec 009's tongues** (T012); every changed line is listed in its commit. Any other existing test that fails: stop and report.

**Paths**: `ARCADE` = `desktop/TuxGuitar-practice/src/app/tuxguitar/app/tools/custom/practice/arcade`, `TST` = `desktop/TuxGuitar-practice/test/app/tuxguitar/app/tools/custom/practice`, `BUILD` = `desktop/build-scripts/tuxguitar-macosx-swt-cocoa`. Commands as in `quickstart.md`.

## Format: `[ID] [P?] [Story] Description`

## Phase 1: Setup

- [ ] T001 Baseline before any code in `specs/018-arcade-demo-fire/baseline.txt`: `mvn -o -e clean verify -P native-modules` from `BUILD` (counts per module) and the `swt-gui-tests` run; the starting commit; the number of flame items of `ReferenceScenes.flameReading()` at FULL and BALANCED (a throwaway count, not a committed test). Restore `specs/003-arcade-mode/benchmark/` after each build.
- [ ] T002 Write in `specs/018-arcade-demo-fire/baseline.txt` the existing flame tests and their status: to stay unchanged and green — `TST/arcade/view/FlameContrastTest`, `TST/session/FlameParityTest`, `TST/arcade/view/ArcadeReferenceSceneTest` (code), `TST/arcade/ui/ArcadeFrameSourceTest`, `TST/arcade/ui/FallbackRendererTest`, `TST/arcade/ui/ArcadeCanvasSwtTest`, and every method of `FlameSceneTest`, `ArcadeSceneQualityTest`, `ArcadeFlamesSettingTest` not named in research R8; under the owner's exception — exactly the methods and lines of research R8.

## Phase 2: Foundational

- [ ] T003 [P] Create `TST/arcade/view/FireColoursTest.java`: heat step 0 → no colour (nothing drawn); steps 1..15 go from dark red to white in that order (red ≤ green ≤ blue rising); opacity grows with the step and is **≤ 0.60** at step 15, **≤ 0.36** discreet; see it fail.
- [ ] T004 Create `ARCADE/view/FireColours.java` (pure): 16 steps, the colour scale and opacity of research R4 (`0.60 × heat^0.8`, × 0.6 discreet); make T003 pass.

## Phase 3: User Story 1 + 2 - The flames look like a demo-scene fire, and the game shows through (Priority: P1) 🎯 MVP

**Goal**: the tongues replaced by the big-pixel fire, translucent, with every rule of spec 009 kept.

**Independent test**: `FireFieldTest` and the kept flame tests green; the reference scene shows the field.

### Tests first (see them fail)

- [ ] T005 [P] [US1] Create `TST/arcade/view/FireFieldTest.java` on `FireField` alone: (a) cell size = max(4, round(width / 192)) at FULL, twice at BALANCED; (b) level 0 → only the two bottom rows hold heat, at step ≤ 3; (c) the highest lit row of each column ≤ `level × factor × 0.55 × H × weight(col)` and within one cell of it at full level (SC-003); (d) in the middle tenth of the width the height and the highest step are ≤ 22 % of those of the corners (FR-004); (e) discreet = 60 % of the height of full within one cell; (f) the same inputs give the same field; with motion off two clocks give the same field; with motion on they differ (flicker); (g) no heat above the profile and nothing outside the picture.
- [ ] T006 [P] [US2] Create `TST/arcade/view/FireTranslucencyTest.java` on the scene (`ReferenceScenes.flameReading()` at FULL): every `FLAME_TONGUE` or `FLAME_EMBER` item has opacity ≤ 0.60 and no two fire or ember cells overlap, so that the opacity at every point is ≤ 0.60 (SC-002); every flame item is an axis-aligned square-celled rectangle (QUAD with horizontal and vertical edges, height = one cell); no flame item has a heat-0 colour or a zero opacity (nothing drawn where cold); the items are before every non-flame item.

### Implementation

- [ ] T007 [US1] Create `ARCADE/view/FireField.java` (pure): cells from the viewport and the tier (research R3); `height(col)` from `FlameState.level()`, the setting's factor (`FlameDrawing.DISCREET`) and `FlameDrawing.weight`; heat = profile × turbulence (three octaves of a seeded integer-hash value noise scrolled upwards by the flame clock, slight sway; clock 0 when `motion()` is false), research R2; the two ember rows of R6; make T005 pass.
- [ ] T008 [US1] Rewrite the tongue part of `ARCADE/view/FlameDrawing.java`: `items(...)` keeps its signature, its early returns (null, OFF, PERFORMANCE) and its place first in the scene; it walks `FireField` row by row, merges consecutive cells of the same step into one rectangle QUAD (research R5), colours and opacity from `FireColours`, opacity through `faded(alpha, hull, texts)` (kept); role `FLAME_TONGUE` with `tgString` = 1 + the column of the run's first cell, role `FLAME_EMBER` for the ember rows; remove the tongue, ember-disc and glow code and the now unused constants (`TONGUES`, `LAYERS`, `DARK_RED`, …) — keep `CORNER_HEIGHT`, `MIDDLE`, `DISCREET`, `WIDEN`, `NEAR_TEXT`, `RAMP`, `weight`, `faded`.
- [ ] T009 [US2] Check the translucency end to end: make T006 pass (adjust only `FireColours` and `FlameDrawing`).
- [ ] T010 [US1] Run the kept tests of T002 (`FlameContrastTest`, `FlameParityTest`, the kept methods of `FlameSceneTest`, `ArcadeSceneQualityTest`, `ArcadeFlamesSettingTest`, `ArcadeFrameSourceTest`, `FallbackRendererTest`); fix the code, never these tests, until green.
- [ ] T011 [US1] Regenerate `TST/arcade/reference/flame-reading.scene.txt` with `-Dtuxguitar.arcade.reference.write=true` (owner's exception, R8), then run `ArcadeReferenceSceneTest` without the property: green; the other reference files unchanged (`git status`).
- [ ] T012 [US1] Apply the owner's exception, nothing more: in `TST/arcade/view/FlameSceneTest.java` (`theTonguesAndTheirLayersFollowTheTierAndTheSetting`; the `pieces/tallest/layerCount` helpers read as columns; the exact proportionality given one cell of tolerance), in `TST/arcade/view/ArcadeSceneQualityTest.java` lines 177–178, and in `TST/arcade/ui/ArcadeFlamesSettingTest.java` line 113 only if it fails — each rule of spec 009 kept with the field's counts; list every changed line in the commit message.
- [ ] T013 [US1] Measure the flame items of `flameReading()` at FULL and BALANCED, 1280×720 (≤ about 2 500 and 700, plan) and write them in `specs/018-arcade-demo-fire/baseline.txt` beside the T001 figures; if above, merge more (coarser steps) before going on.

**Checkpoint**: the fire replaces the tongues; translucent; all of spec 009's rules hold.

## Phase 4: User Story 3 - Sparks on each right note (Priority: P2)

**Independent test**: `FlameSparksTest` green; on a replayed run, one burst per right note.

- [ ] T014 [P] [US3] Create `TST/arcade/view/FlameSparksTest.java`: (a) a `FlameState` with one burst 0.2 s old at FULL with motion → between 4 and 10 spark items (`FLAME_EMBER`, single-cell squares); a burst 1.0 s old or more → none (SC-005); (b) a longer streak → more sparks, at most 10 per burst, at most 60 in all with many bursts; (c) none at BALANCED, PERFORMANCE, OFF, or motion off; (d) discreet → fewer sparks than full for the same burst; (e) no spark item above 12 % opacity within 4 px of a text of the reference scene; (f) the same state → the same sparks; (g) where a spark lies over a fire cell, the combined opacity 1 − (1 − a)(1 − b) is ≤ 0.60 (FR-002, SC-002).
- [ ] T015 [P] [US3] Create `TST/arcade/ui/ArcadeFrameSourceBurstsTest.java`: with a scripted run whose views turn events RIGHT one by one (as the existing `ArcadeFrameSourceTest` drives the source), each new RIGHT state adds one burst with the flame clock and the view's `streak()`; WRONG and MISSED add none; a pause holds the flame clock so the bursts do not age; bursts older than 1 s are dropped.
- [ ] T016 [US3] Extend `ARCADE/view/FlameState.java` additively: `Burst(long clockNanos, int streak)` and a list of bursts (unmodifiable, empty by default); the existing constructor unchanged.
- [ ] T017 [US3] In `ARCADE/ui/ArcadeFrameSource.java` count the RIGHT states of each `ArcadeRun.View` in `flamesAt`; each new one adds a `Burst(flameClock, view.streak())`; drop bursts older than 1 s; reset at each run; pass them in the `FlameState`; nothing under `ARCADE/core` changes (`FlameParityTest`); make T015 pass.
- [ ] T018 [US3] Draw the sparks in `ARCADE/view/FlameDrawing.java` after the fire cells (still before every non-flame item): per research R7, positions from a hash of (burst time, index), rising and fading over 1 s, `faded` near texts, opacity capped over a fire cell to (0.60 − a) / (1 − a) (research R7); make T014 pass.

## Phase 5: Polish & Cross-Cutting Concerns

- [ ] T019 [P] Describe the new look in `docs/PRACTICE.md`, `docs/PRACTICE-FR.md`, `docs/PRACTICE-ES.md` (the big-pixel fire, see-through, sparks on right notes, the setting unchanged, never changes the judgement; FR-012).
- [ ] T020 [P] Update the manual sheet `specs/test-sheets/F06-flammes-arcade.md` for the new look (what to watch: big pixels, the game visible through the fire, no black band, sparks, smoothness).
- [ ] T021 Full gate from `BUILD`: `mvn -o -e clean verify -P native-modules`, then the `swt-gui-tests` run; counts = baseline + new tests; `git diff --name-status <T001 commit> -- '*/test/*'` shows only added files plus the `M` of T011–T012; restore `specs/003-arcade-mode/benchmark/`.
- [ ] T022 Relaunch the built application (jre symlink, `open <app> --args ~/git/tuxguitar/tests/demo-arcade-morceau.tg`).
- [ ] T023 **[HUMAN] Gate**: the owner plays Arcade with *full*, *discreet* and *off* (sheet F06) and judges the look ("the demo's variant A", SC-001) and the smoothness (SC-007); values tuned there (cell size, colours, opacity at most 60 %), each tuning with its test kept green.
- [ ] T024 Write `specs/018-arcade-demo-fire/final-report.md` (built, ran, measured item counts, the gate, the exception lines, not seen: Linux/Windows by CI only) and add the row of spec 018 to `specs/STATUS.md`.

## Dependencies & Execution Order

- Phase 1 → T003–T004 → US1+US2 (T005, T006 first; T007 → T008 → T009 → T010 → T011 → T012 → T013) → US3 (T014, T015 first; T016 → T017 → T018) → Polish (T021 → T022 → T023 → T024).
- US3 needs the fire drawing of US1 (same file `FlameDrawing.java`).

## Parallel Opportunities

- T003 ∥ T005 ∥ T006 (tests in different files); T014 ∥ T015; T019 ∥ T020.

## Implementation Strategy

MVP = Phases 1–3 (the translucent demo fire, every rule of spec 009 kept). Then the sparks (Phase 4). Each phase committed on its own; the owner's look gate at the end.
