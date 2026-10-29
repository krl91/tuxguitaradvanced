---
description: "Tasks of spec 017: the practice palette moves aside so it never hides the note to play"
---

# Tasks: The practice palette moves aside so it never hides the note to play

**Input**: `specs/017-palette-dodges-score/` (spec.md, plan.md, research.md, data-model.md, contracts/palette-dodge.md, quickstart.md)

**Tests**: written first and seen failing for every behaviour (owner's rules, constitution XI). **Existing tests are never modified**; if one fails, stop and report.

**Paths**: `PRACTICE` = `desktop/TuxGuitar-practice/src/app/tuxguitar/app/tools/custom/practice`, `TST` = `desktop/TuxGuitar-practice/test/app/tuxguitar/app/tools/custom/practice`, `TAB` = `desktop/TuxGuitar/src/app/tuxguitar/app/view/component/tab`, `TOOLKIT` = `desktop/TuxGuitar-ui-toolkit/src/app/tuxguitar/ui/widget`, `SWT` = `desktop/TuxGuitar-ui-toolkit-swt/src/app/tuxguitar/ui/swt/widget`, `BUILD` = `desktop/build-scripts/tuxguitar-macosx-swt-cocoa`. Commands as in `quickstart.md`.

## Format: `[ID] [P?] [Story] Description`

## Phase 1: Setup

- [ ] T001 Baseline before any code in `specs/017-palette-dodges-score/baseline.txt`: `mvn -o -e clean verify -P native-modules` from `BUILD` (test counts per module, skipped, failures) and the `swt-gui-tests` run of `TuxGuitar-practice` (count); the starting commit. Restore `specs/003-arcade-mode/benchmark/` after each build (`git checkout`).
- [ ] T002 Write in `specs/017-palette-dodges-score/baseline.txt` the existing tests that read what this feature touches and must stay unchanged and green: every test under `TST/ui` naming `PracticePalette`, `PracticeWindow` or `PracticeHighlight` (list them by `grep -l`), and the source-structure tests of the start path (`PracticeWindowStartPathSourceTest`).

## Phase 2: Foundational (the two hooks, research R2 and R3)

- [ ] T003 [P] Add to `TOOLKIT/UIControl.java` `default UIPosition toDisplay(UIPosition point) { return null; }` with a Javadoc ("screen position of a point given relative to this control, or null when the toolkit cannot tell"); release 9 kept; nothing else in the interface changes.
- [ ] T004 [P] Override it in `SWT/SWTControl.java` with `getControl().toDisplay(Math.round(x), Math.round(y))` returning a new `UIPosition`; null when the control is disposed.
- [ ] T005 In `TAB/TGExternalNoteFramePainter.java` extract the box computations of `paintScoreFrame` and `paintTablatureFrame` into `public static UIRectangle frameBounds(TGLayout layout, List<TGNote> notes, int trackSelection)` (the union of the score and tablature boxes as painted, canvas coordinates; null for empty notes, a non-`TGNoteImpl`, a measure out of bounds or of another track); `paint` uses the same per-staff boxes and draws exactly what it drew before (no visible change).
- [ ] T006 Build from `BUILD` (`mvn -o -q -DskipTests install -pl ../../TuxGuitar -am`) and check that the default test suite still has the baseline's counts.

**Checkpoint**: the hooks exist; nothing uses them yet; the application behaves as before.

## Phase 3: User Story 1 - The note to play is never hidden by the palette (Priority: P1) 🎯 MVP

**Goal**: in Wait and Follow, the palette jumps to a free corner of the score area, ahead of time, without focus.

**Independent test**: `PaletteDodgeTest` green; on F01 the palette never covers the framed note (quickstart step 4).

### Tests first (see them fail)

- [ ] T007 [P] [US1] Create `TST/ui/PaletteDodgeTest.java` with the pure scenarios: (a) the current box under the palette → `MoveTo` a corner whose palette box meets neither the current nor the next box; (b) only the next box under the palette → `MoveTo` (ahead of time, clarification 2026-10-30); (c) neither box under it → `Stay` (US1/AC4); (d) the 8 px margin: a box 7 px away counts as under, 9 px away does not; (e) every `MoveTo` is fully inside the score area at 16 px from its edges; (f) a null area or a null current and next → `Stay` (end of run, toolkit without mapping); (g) a source check: `PaletteDodge.java` and `ScoreBoxes.java` import nothing from the `session` and `model` write paths and post no `PracticeCommand`, so a move cannot pause the run or change a verdict (FR-006, SC-003).
- [ ] T008 [P] [US1] Create `TST/ui/PaletteMoveSwtTest.java` (tag `swt-gui`): open a palette over a test window, give the focus to a control of the main window, call the palette's move; the focused control is the same after the move, the palette's size is unchanged and its position is the new one (SC-003, research R7).

### Implementation

- [ ] T009 [US1] Create `PRACTICE/ui/PaletteDodge.java`, pure, no UI import: records `Box(x, y, width, height)`, `Corner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }`, `Decision` (`Stay` or `MoveTo(corner, x, y)`), and `decide(Box palette, Box area, Box current, Box next, PaletteState state)` per research R5 for story 1: margin 8 px, corners at 16 px inside `area`, free = meets neither box, nearest free corner to the palette's place; make T007 pass.
- [ ] T010 [US1] Create `PRACTICE/ui/ScoreBoxes.java` (UI thread): from `TGTabFolder.getInstance(context).findSelectedControl()` (its `getCanvas()`), `TuxGuitar.getInstance().getTablatureEditor().getTablature()` (`getViewLayout()`, `getTrackSelection()`), map with `canvas.toDisplay` the canvas box (the score area) and `TGExternalNoteFramePainter.frameBounds` of a `PracticeEvent`'s notes; any null on the way → null.
- [ ] T011 [US1] In `PRACTICE/ui/PracticePalette.java` add `Box bounds()` and `void moveTo(float x, float y)` (same size, `window.setBounds` only, no `setFocus`, no `moveToTop`), and record the position it set last (for US3), starting with the opening place given by `placeWindow`, so that the opening is not taken for a learner's move.
- [ ] T012 [US1] In `PRACTICE/ui/PracticeWindow.java` add the check: after each `highlight.show` of the Wait path (line ~789) and of the Follow path (line ~1166), and on a 100 ms UI-thread repeat started with a Wait or Follow run and stopped at its end or when the palette closes, ask `ScoreBoxes` for the area, the current event's box and the next event's box (the event after the current one in the run's `PracticeSequence.getEvents()`, none after the last; in Follow the event after `snapshot.openEvent()` in `plan.sequence().getEvents()`; no next box while the run is paused), call `PaletteDodge.decide` and apply a `MoveTo` with `palette.moveTo`; never from the Arcade path (FR-009); make T008 pass.

**Checkpoint**: US1 works on its own.

## Phase 4: User Story 2 - The palette stays calm (Priority: P2)

**Independent test**: the US2 cases of `PaletteDodgeTest` green; on a piece going back and forth the palette never returns to the place it left while the notes are near.

- [ ] T013 [US2] Add to `TST/ui/PaletteDodgeTest.java` (new methods only): (a) notes running left to right on a line under the top-right corner → the chosen corner is not on the side the next box heads to; (b) after a move, the framed note leaves the old place but stays within one palette width of it → `Stay` (never back); (c) no free corner (small area) → `MoveTo` the corner covering the least of the current box, then `Stay` for the next calls with the same sizes (FR-005); (d) the same current event after a move → `Stay`, but a change of the area's or the palette's size allows one new move (FR-004).
- [ ] T014 [US2] Implement in `PRACTICE/ui/PaletteDodge.java` the `PaletteState` fields of `data-model.md` (`lastAuto`, `leftCorner`, `movedFor`, `areaSize`, `paletteSize`, `outside`) and the rules of research R5 (heading-away, not back, least-covering, one move per framed note); make T013 pass.

## Phase 5: User Story 3 - The learner can still put the palette where they like (Priority: P3)

**Independent test**: the US3 cases green; dragging the palette beside the window during a run keeps it there.

- [ ] T015 [US3] Add to `TST/ui/PaletteDodgeTest.java` (new methods): (a) the palette's actual place differs from `lastAuto` by more than 2 px → it is the learner's place and `Stay` while nothing is under it; (b) the learner's place covers the current box → `MoveTo` as in US1; (c) a learner's move that ends with the palette box not inside the main window's box → `Stay` always, even with the current box under it, until a learner's move brings it back inside (FR-007); (d) the main window moves so that an untouched palette is no longer inside it → not "outside": it still moves aside when in the way.
- [ ] T016 [US3] Implement the learner's place and the `outside` rule in `PRACTICE/ui/PaletteDodge.java` (the main window's box as one more input) and pass the main window's bounds from `PracticeWindow`; `PracticePalette.close()` keeps recording `lastBounds` so a reopened palette opens where it was (FR-008, unchanged); make T015 pass.

## Phase 6: Polish & Cross-Cutting Concerns

- [ ] T017 [P] Describe the behaviour in `docs/PRACTICE.md`, `docs/PRACTICE-FR.md`, `docs/PRACTICE-ES.md` (the palette moves aside ahead of time to a corner of the score, never takes the focus, stays where you drag it, never moves when put beside the window).
- [ ] T018 [P] Add a manual sheet `specs/test-sheets/F10-palette-ecartee.md` (Wait run on `tests/basic_test.tg`, Follow run as in F01; what to watch: never hidden, no back and forth, shortcuts without a click, drag beside the window) and list it in `specs/test-sheets/README.md`.
- [ ] T019 Full gate from `BUILD`: `mvn -o -e clean verify -P native-modules`, then `mvn -o test -P swt-gui-tests -pl ../../TuxGuitar-practice -am -Dsurefire.failIfNoSpecifiedTests=false`; counts = baseline + the new tests; `git diff --name-status <T001 commit> -- '*/test/*'` shows only added files; restore `specs/003-arcade-mode/benchmark/`.
- [ ] T020 Relaunch the built application (jre symlink, `open <app> --args ~/git/tuxguitar/tests/basic_test.tg`).
- [ ] T021 **[HUMAN] Gate**: the owner practises F01 and a Wait run (sheet F10) and judges that the palette no longer hinders the reading (SC-004); record the answer in `specs/017-palette-dodges-score/final-report.md`.
- [ ] T022 Write `specs/017-palette-dodges-score/final-report.md` (what was built, what ran, the gate, not seen: JavaFX/Qt have no mapping, Linux/Windows by CI only) and add the row of spec 017 to `specs/STATUS.md`.

## Dependencies & Execution Order

- Phase 1 → Phase 2 (T003, T004 parallel; T005 independent) → US1 → US2 → US3 → Polish. US2 and US3 extend the class of US1, in order (same file).
- T021 waits for T020; T022 after T021.

## Parallel Opportunities

- T003 ∥ T004 ∥ T005 (three modules); T007 ∥ T008; T017 ∥ T018.

## Implementation Strategy

MVP = Phases 1–3 (the palette moves aside, ahead of time). Then US2 (calm) and US3 (manual drag); each phase is committed on its own; the owner's gate at the end.
