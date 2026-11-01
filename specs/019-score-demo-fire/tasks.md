---
description: "Tasks of spec 019: the demo fire at the bottom of the score in Wait and Follow, and an Advanced tab to tune both fires"
---

# Tasks: The demo fire at the bottom of the score in Wait and Follow, and an Advanced tab to tune both fires

**Input**: `specs/019-score-demo-fire/`: spec.md, plan.md, research.md (R1–R10), data-model.md, contracts/score-overlay.md, contracts/fire-tuning.md, contracts/advanced-tab.md, quickstart.md.

**Tests**:
- Written first and seen failing for every behaviour (owner's rules, constitution XI).
- **Existing tests are never modified. There is no exception in this spec.** Any existing test that fails: stop and report.
- `ReducedMotionTest` stays as it is. The new behaviour goes into the new class `SystemReducedMotion` (research R7).

**Paths**:

| Name | Path |
|---|---|
| `PR` | `desktop/TuxGuitar-practice/src/app/tuxguitar/app/tools/custom/practice` |
| `ARCADE` | `PR/arcade` |
| `TST` | `desktop/TuxGuitar-practice/test/app/tuxguitar/app/tools/custom/practice` |
| `LANG` | `desktop/TuxGuitar-practice/share/lang` |
| `CORE` | `desktop/TuxGuitar/src/app/tuxguitar/app/view` |
| `BUILD` | `desktop/build-scripts/tuxguitar-macosx-swt-cocoa` |

Commands as in `quickstart.md` (JDK 21; restore `specs/003-arcade-mode/benchmark/` after each build).

## Format: `[ID] [P?] [Story] Description`

## Phase 1: Setup (baseline, no code)

- [X] T001 Write the baseline before any code in `specs/019-score-demo-fire/baseline.txt`:
  - the starting commit;
  - the counts of `mvn -o -e clean verify -P native-modules` from `BUILD`, per module (practice expected 1324, 0 failures, 12 skipped);
  - the `swt-gui-tests` run (expected 37);
  - the number of flame items of `ReferenceScenes.flameReading()` at FULL and BALANCED.
  Restore `specs/003-arcade-mode/benchmark/` afterwards.
- [X] T002 Measure the cost of a full Wait repaint of the score (research R2) and record the median and the 95th percentile in `specs/019-score-demo-fire/baseline.txt`.
  - **How**: a throwaway timing print around `fillPaintBuffer()` in `CORE/util/TGBufferedPainterLocked.java`, **not committed**. Built app, `tests/demo-arcade-morceau.tg`, window 1280×800, player stopped, 100 redraws forced by a throwaway loop.
  - **Follow** (research R2, "Follow"): with the same throwaway print, check whether `MidiPlayer.getInstance(context).isRunning()` is true during a Follow run, with the backing on and muted. If it is not, measure the Follow repaint in the same way; the threshold then decides the hook for both modes.
  - **Then**: remove the print, check that `git diff CORE` is empty, and write the decision: **hook needed** if the median is above 3 ms or the 95th percentile is above 6 ms, otherwise **no hook**. Copy the numbers and the decision into research R2 ("Measured").
- [X] T003 List in `specs/019-score-demo-fire/baseline.txt` the existing tests that must stay unchanged and green: `FireFieldTest`, `FireColoursTest`, `FireTranslucencyTest`, `FlameSparksTest`, `FireLookTuningTest`, `FlameCellsPaintTest`, `FlameSceneTest`, `FlameContrastTest`, `FlameParityTest`, `ArcadeReferenceSceneTest`, `ArcadeSceneQualityTest`, `ArcadeFlamesSettingTest`, `ArcadeFrameSourceTest`, `ArcadeFrameSourceBurstsTest`, `FallbackRendererTest`, `ArcadeCanvasSwtTest`, `ReducedMotionTest`, and the `FollowPanel`/`PracticeWindow` tests that exist. Find them with `grep -rl` under `TST`.

## Phase 2: Foundational — the tuning, with identical pictures (blocks every story)

**Goal**: every value that shapes a fire lives in a `FireTuning` whose defaults are today's constants; no picture changes.

**Independent test**: `FireParameterDefaultsTest`, `FireTuningTest`, `FireTuningIdentityTest`, `FireTuningStoreTest`, `FireSourceScanTest` and `ScoreFireSettingKeptTest` are green, and every test of T003 is green, unchanged.

### Tests first (see them fail)

- [X] T004 [P] (FR-017, FR-021) Create `TST/arcade/view/FireParameterDefaultsTest.java`:
  - every row of the data-model table exists as a `FireParameter`, with its key, kind, range and both defaults;
  - `min ≤ arcadeDefault ≤ max` and `min ≤ scoreDefault ≤ max` for every parameter, including `growth.full` (Arcade default 16, never read; `inArcade()` false);
  - one assertion per constant: `opacity.most == FireColours.MOST`, `cell.columns == FireField.COLUMNS`, `cell.smallest == FireField.SMALLEST`, `colour.steps == FireColours.STEPS`, `height.corners == FlameDrawing.CORNER_HEIGHT`, `height.middle == FlameDrawing.MIDDLE`, `discreet.share == FlameDrawing.DISCREET`, `ember.rows == FireField.EMBER_ROWS`, `sparks.most == FlameDrawing.MOST_SPARKS`, `sparks.life == FlameDrawing.SPARK_LIFE / 1e9`, `growth.minJudged == FlameLevel.MIN_JUDGED`, `growth.rise == FlameLevel.RISE_PER_SECOND`, `growth.fall == FlameLevel.FALL_PER_SECOND`, `growth.maxStep == FlameLevel.MAX_STEP_NANOS / 1e6`, and for the literals without a constant `height.curve == 2`, `noise.scale == 2`, `noise.aspect == 2`;
  - the score defaults: `height.corners` 1/3, `growth.full` 16, `growth.fall` 1.0, `growth.from` 0, `growth.minJudged` 0, `sparks.start` 0;
  - the cost ceilings: `cell.columns` max 384, `cell.smallest` min 3, `sparks.most` max 1000, `sparks.base` max 200, `sparks.perStreak` max 50, `colour.steps` max 64.
- [X] T005 [P] Create `TST/arcade/view/FireTuningTest.java`:
  - `FireTuning.of(group, map)` clamps every value into `[min, max]`;
  - NaN or a missing value gives the group's default;
  - colours are masked to RGB;
  - `ARCADE_DEFAULTS` and `SCORE_DEFAULTS` differ only on the parameters whose two defaults differ;
  - equal maps give equal tunings (same `equals` and `hashCode`);
  - the spark count `floor(2.5 * min(streak, 12) + 1e-9) + 20` equals `20 + 5 * min(streak, 12) / 2` for every streak from 0 to 128.
- [X] T006 [P] (FR-017, SC-010) Create `TST/arcade/view/FireTuningIdentityTest.java`:
  - `FlameDrawing.items(viewport, tier, flames, texts)` and the new overload with `FireTuning.ARCADE_DEFAULTS` give equal item lists (shape, role, points, colour, opacity, code) for FULL and BALANCED, Discreet and Full, levels 0, 0.3 and 1, motion on and off, 0 and 5 bursts, on the seven window shapes of spec 009;
  - the reference scene built with the defaults is byte-identical to `reference/flame-reading.scene.txt` (read the file, do not regenerate it);
  - `new FlameLevel()` and `new FlameLevel(ARCADE_DEFAULTS)` give the same levels over a scripted sequence of updates, holds and resumes.
- [X] T007 [P] Create `TST/session/FireTuningStoreTest.java` with an in-memory `ArcadeChoices.Persistence`:
  - `save` then `load` gives the same tuning for each group, and only values that differ from the default are written;
  - `reset(group)` gives the defaults and leaves the other group untouched;
  - a corrupt value (`"abc"`), an out-of-range value or a colour without `#` gives the default or the clamped value;
  - a persistence that throws on get or set gives the defaults, and `false` on write, never an exception;
  - `scoreSetting()` is OFF when missing or unknown, and `setScoreSetting(FULL)` then `scoreSetting()` gives FULL.
- [X] T008 [P] Create `TST/arcade/view/FireSourceScanTest.java` (SC-009, contracts/fire-tuning.md "Catalogue"). It scans the number literals of `ARCADE/view/FireField.java`, `FireColours.java` and `FlameDrawing.java`:
  - inside the pure helpers (`value`, `hash`, `smooth`, `lerp`, `clamp`, the two `mix`, `gap`), every number is allowed;
  - 0 and 1 are allowed anywhere;
  - every other literal must be on the allow-list, classified as **fixed (R4)** or **neutral (with its reason)**.
  Steps:
  1. Write the scanner with an empty allow-list and run it on today's code. It is red, and it prints every literal with its method and line.
  2. Classify each hit as **setting** (it must disappear once T012 is done), **fixed (R4)** or **neutral**. Write this classification as research **R11**, a table with literal, method, class and reason.
  3. Put the fixed and neutral lines into the test's allow-list. The test stays red only on the settings.
  4. A hit that shapes the look but is in no catalogue row is a missing setting: stop and report before adding it (spec FR-015).
  T012 makes it green.
  *Done*: research R11 holds the 71 hits of 344185cb8, all classified; the octave ratios of the noise are neutral (an octave doubles the frequency), so no setting was missing.
- [X] T009 [P] Create `TST/session/ScoreFireSettingKeptTest.java` (SC-007): a fresh store gives OFF. FULL, then a new `FireTuningStore` over the same persistence, gives FULL; the same for DISCREET; a tuned score value survives the same way. (It extends T007's scope through a new file; T007 is left as it is.) Written before T014, it is seen red (the store does not exist yet).

### Implementation

- *Note on T004 and T006*: the `FlameLevel` constants are package-private in `arcade.ui`, so their two checks live in that package: `TST/arcade/ui/FireParameterLevelDefaultsTest.java` (the growth defaults) and `TST/arcade/ui/FireParameterFlameLevelIdentityTest.java` (the levels over a scripted run). `FireParameterDefaultsTest` counts 48 parameters: the data model has 44 rows, one of them the five colours (plan.md said 50 by mistake, fixed).
- [X] T010 Create `ARCADE/view/FireParameter.java` (public enum): the catalogue of data-model.md, with the Arcade defaults referencing the existing constants where they exist, `labelKey()` = `practice.fire.<key>`, `tooltipKey()` = `practice.fire.<key>.tip`, `section()`, `unit()`, `kind()`, `inArcade()`/`inScore()` (`growth.full` is score only). Make T004 pass.
- [X] T011 Create `ARCADE/view/FireTuning.java` (public, immutable): `of(Group, Map<FireParameter, Double>)` with clamping, `ARCADE_DEFAULTS`, `SCORE_DEFAULTS`, `value`, `integer`, `colour`, `with(parameter, value)`, `equals` and `hashCode`; `Group` is ARCADE or SCORE. Make T005 pass.
- [X] T012 (FR-002, FR-015, FR-016) Give the fire classes a tuning **without changing any existing signature or constant**:
  - `ARCADE/view/FireColours.java`: `colour(step, tuning)` and `alpha(step, discreet, tuning)`; steps, scale, most and discreet are read from the tuning.
  - `ARCADE/view/FireField.java`: `FireField(viewport, tier, flames, tuning)` and `cell(viewport, tier, tuning)`. Every literal of `heat`/`noise` (0.12, 0.08, 3.0, 1.15, 1.5, 1.3, 0.2, 0.6, 1.7, 0.35, 0.5, 0.3, 0.2, the octave divisors 2, 1, 0.5 and 4, 2, 1 through `noise.scale` and `noise.aspect`, RISE, EMBER_ROWS, COLUMNS, SMALLEST, CORNER_HEIGHT, DISCREET) is read from the tuning, and the clock is multiplied by `motion.speed`.
  - `ARCADE/view/FlameDrawing.java`: `items(viewport, tier, flames, texts, tuning)`. The spark literals (20, 2.5, 12, 0.25, 10, 16, 10, 8, MOST_SPARKS, SPARK_LIFE) come from the tuning. The spark start row is `EMBER_ROWS + round(sparks.start × tongueRows(start))`, which at 1 is today's top of the flames. `weight` uses `height.middle` and `height.curve` (`d * d` when the curve is 2, so that the defaults stay bit-identical).
  - `ARCADE/view/FlameState.java`: an additive constructor with a `FireTuning`, and `tuning()`, which is `ARCADE_DEFAULTS` when absent.
  - The old entry points delegate with `ARCADE_DEFAULTS`, or `flames.tuning()`. Keep `WIDEN`, `NEAR_TEXT`, `RAMP`, `SEED`, the hash and `TONGUES` fixed (research R4).
  - Make T006 and T008 pass, with every T003 test green.
- [X] T013 `ARCADE/ui/FlameLevel.java`: an additive constructor `FlameLevel(FireTuning)` reading `growth.from`, `growth.minJudged`, `growth.rise`, `growth.fall` and `growth.maxStep`; `new FlameLevel()` keeps the constants. `ARCADE/ui/ArcadeFrameSource.java`: an additive constructor parameter for the Arcade tuning (default `ARCADE_DEFAULTS`), used for its `FlameLevel`, its spark life (in place of `SPARK_NANOS`) and its `FlameState`. Make the FlameLevel part of T006 pass; `ArcadeFrameSourceTest` and `ArcadeFrameSourceBurstsTest` stay green.
- [X] T014 Create `PR/session/FireTuningStore.java`:
  - module `tuxguitar-practice-fire`;
  - keys `arcade.<key>`, `score.<key>` and `score.setting`;
  - numbers written with `Double.toString`, colours as `#RRGGBB`;
  - `of(TGContext)` built on `TGConfigManager` like `FollowChoices.of`;
  - `load`, `save`, `reset`, `scoreSetting`, `setScoreSetting`; never throws.
  Make T007 and T009 pass.
- [X] T015 Use the stored Arcade tuning in a real Arcade run: `PR/ui/PracticeWindow.java` (around line 858, where `ArcadeSession` gets `getFlames()`) passes `FireTuningStore.of(context).load(ARCADE)` down to `ArcadeFrameSource` (through `PR/arcade/ui/ArcadeSession.java`, with an additive parameter). With nothing stored, it is `ARCADE_DEFAULTS`. Gate: practice suite green, T003 tests unchanged. Commit the phase.

## Phase 3: User Story 1 — A fire that grows with my streak (Priority: P1) 🎯 MVP

**Goal**: with *Score fire* on, a fire burns at the bottom of the visible score in Wait and Follow, grows with the streak and falls on a mistake, without ever covering the framed note, the next note or the palette.

**Independent test**: `ScoreStreakTest`, `ScoreFireSceneTest`, `ScoreFireLevelTest`, `ScoreFireOverlaySwtTest`, `ScoreGuardBoxesTest` and `ScoreFireVerdictsTest` are green; manually, Full in the Mode tab, then a Wait run of right notes and a wrong note.

### Tests first (see them fail)

- [X] T016 [P] [US1] Create `TST/ui/ScoreStreakTest.java` (pure, data-model "ScoreStreak" table):
  - **Wait**: three snapshots each passing an ACCEPTED event give streaks 1, 2 and 3 with three bursts; the same snapshot fed twice counts once; a WRONG verdict gives 0; SKIPPED gives 0; AUTO_PASSED leaves the streak unchanged with no burst.
  - **Follow**: results going PENDING → MATCHED count once per `eventIndex`, including early and late offsets; WRONG_PITCH and MISSED give 0; a result seen again counts nothing.
  - `reset()` gives 0 and no bursts.
  - The streak never changes the snapshot it reads.
- [X] T017 [P] [US1] (FR-001, FR-002, FR-004) Create `TST/arcade/view/ScoreFireSceneTest.java` on `ScoreFireScene.scene(viewport, tier, flames, guard)` with `SCORE_DEFAULTS`:
  - null, OFF or PERFORMANCE gives no item;
  - level 0 gives ember rows only, on the bottom two rows;
  - at level 1, the highest lit row at the corners is within one cell of 1/3 of the viewport height and never above it (FR-004), and the middle tenth is at most 22 % of the corners;
  - every item has opacity ≤ 0.85, or ≤ 0.85 × 0.6 discreet;
  - a small score area (Edge Case): at 600×120 the corners stay within 40 px; at 600×24, fewer than 8 cells high, there are ember rows only, whatever the level (contracts/score-overlay.md);
  - with guard boxes at the bottom (a framed note, a next note, a palette), no item above 12 % opacity meets a box widened by 4 px (FR-005, SC-003), on the window shapes 1280×800, 800×600, 1500×500 and 600×300;
  - the same inputs give the same items.
- [X] T018 [P] [US1] (FR-003, FR-009) Create `TST/ui/ScoreFireLevelTest.java` on `new FlameLevel(SCORE_DEFAULTS)` fed with `share = min(1, streak / 16)` and `judged` = the number of events, on a scripted clock at 30 updates per second:
  - streak 0 stays at 0;
  - each right note raises the level towards `streak / 16`, reaching 1 at streak 16 and holding there;
  - after a mistake at full height, the level is 0 within 1.0 s (+ one step) (FR-003, SC-002);
  - `hold`/`resume` keep the level through a pause.
  It will likely pass at once, because `FlameLevel(FireTuning)` exists since T013. It is then seen red by a deliberate mutation (the score default of `growth.fall` set to 1/3 for one run, not committed), and the mutation is named in the commit message.
- [X] T019 [P] [US1] Create `TST/ui/ScoreFireOverlaySwtTest.java` (profile `swt-gui-tests`, real toolkit, like `ArcadeCanvasSwtTest`):
  - a canvas with a first listener that fills it white, given to `ScoreFire` through its canvas port (T025), then `ScoreFire` started at level 1 and FULL: after a paint, the bottom-corner pixels are not white and the top half is white;
  - with setting OFF, `start` adds no listener and the canvas stays white;
  - after `stop()`, a paint leaves the canvas white;
  - the first listener's pixels outside the fire are unchanged.
  - a slow computer (Edge Case, FR-010): with a painting cost of 20 ms per frame fed through a test seam of `ScoreFire` (the measured cost replaced by a fake clock), the tier steps down to BALANCED, then to PERFORMANCE, where nothing of the fire is painted, within the controller's window. With a cost of 1 ms, it climbs back to FULL.
- [X] T020 [P] [US1] Create `TST/ui/ScoreGuardBoxesTest.java` on the pure `ScoreGuardBoxes` (SC-003, research R8):
  - with a layout stub giving frame boxes for the current and next events, the result is those two boxes as `{x1, y1, x2, y2}`, canvas-local, unchanged;
  - a palette at screen (900, 700, 200, 60) over a score area whose screen origin is (100, 80) gives the canvas box (800, 620, 1000, 680);
  - an event with no frame (off the visible score), no next event or no palette is left out, and no box at all gives an empty list;
  - a box partly outside the canvas is kept as it is (the guard fades the fire up to the canvas edge).
- [X] T021 [P] [US1] Create `TST/ui/ScoreFireVerdictsTest.java` (FR-008, SC-004): the same scripted Wait run and Follow run, with the fire OFF and FULL, give identical verdicts, accepted counts, streak, timings and summary, and identical calls to `paletteGuard` (the palette of spec 017 moves the same). It drives `ScoreStreak` + `ScoreFire` through a fake canvas port (T025) and checks that nothing of the session is written. Written now, it is red (`ScoreStreak` and `ScoreFire` do not exist yet); T025 makes it pass.

### Implementation

- [X] T022 [US1] (FR-001) Create `ARCADE/view/ScoreFireScene.java` (public): `scene(Viewport, QualityTier, FlameState, List<double[]> guard)` returns a `Scene` holding only `FlameDrawing.items(viewport, tier, flames, guard, flames.tuning())`; when the viewport is fewer than 8 cells high, the level is taken as 0 (ember rows only). Make T017 pass.
- [X] T023 [US1] Create `PR/ui/ScoreStreak.java` (pure) per research R3. Make T016 pass.
- [X] T024 [US1] Check T018 with `FlameLevel(FireTuning)` and the score defaults, after its mutation; if it is red without the mutation, fix only `ARCADE/ui/FlameLevel.java` (or the score defaults of `FireParameter`), keeping `new FlameLevel()` identical (T006).
- [X] T025 [US1] (FR-001, FR-010) Create `PR/ui/ScoreFire.java` per contracts/score-overlay.md and data-model "ScoreFire":
  - `start(setting, tuning, motion)`, `update(streak, newBursts, paused, currentEvent, nextEvent, paletteBox)` and `stop()`;
  - a constructor taking a **canvas port**: a small interface `ScoreFire.Score` with `UICanvas canvas()` (null when there is none), `List<double[]> guardBoxes(PracticeEvent current, PracticeEvent next)` and `void redraw(boolean overlayOnly)`; the production port wraps `TGTabFolder.getInstance(context).findSelectedControl()` and `ScoreGuardBoxes`; the tests give a fake;
  - attaches one `UIPaintListener` to `port.canvas()` and re-attaches if it changes;
  - the guard boxes are asked from `port.guardBoxes(current, next)` **at each paint**, not cached between paints. When the score scrolls, changes page or is resized, the protected boxes therefore follow at the next picture (Edge Case "The score scrolls"). `ScoreFireOverlaySwtTest` checks it with a fake port whose boxes move between two paints;
  - a timer at about 33 ms through `TGSynchronizer`/`UIApplication.runLater`, ticking only while the fire moves;
  - the fire clock advances only while running;
  - `FlameLevel(tuning)`;
  - guard boxes from `PR/ui/ScoreGuardBoxes.java` (new, pure): the frame boxes of the current and next events (`TGExternalNoteFramePainter.frameBounds`, canvas-local) and the palette's screen box minus the score area's screen origin, as `{x1, y1, x2, y2}`; missing frames or palette are left out; it makes T020 pass;
  - a `QualityController(8_000_000L)` fed with the painting time;
  - a `UiPainterRenderer`;
  - `stop()` removes the listener and redraws once.
  Make T019 and T021 pass.
- [X] T026 [US1] **Only if T002 decided "hook needed"**: in `CORE/util/TGBufferedPainterLocked.java`, add `requestOverlayOnly()` (a one-shot flag; `paintBufferLocked` skips `fillPaintBuffer()` when the flag is set and the buffer exists with the canvas size, and clears it in every case). In `CORE/component/tab/TGControl.java`, add `redrawOverlay()`, and make `redraw()` clear the flag. `ScoreFire` uses `redrawOverlay()` in Wait.
  - **First**, a new test `TST/ui/OverlayOnlyRepaintSwtTest.java` (swt-gui-tests): after `redrawOverlay()`, `paintControl` of the handle is not called and the buffer is drawn; after `redraw()`, it is called.
  - Mark the task done with "not needed (T002)" otherwise.
- [X] T027 [US1] (FR-001, FR-009) Wire the fire in `PR/ui/PracticeWindow.java`:
  - when a Wait or Follow run starts, read `FireTuningStore.of(context)` (setting and SCORE tuning) and `SystemReducedMotion` (Phase 6; until then `!ReducedMotion.requested()`), reset a `ScoreStreak` and call `ScoreFire.start`;
  - in `render(PracticeSnapshot)`, next to `paletteGuard.show` (~line 798), and in `renderFollow` (~line 1177), feed the streak and call `ScoreFire.update` with the pause state read as the palette reads it;
  - at `paletteGuard.stop()` (~line 1253) and in `stopNow()`, call `ScoreFire.stop()`.
- [X] T028 [US1] (FR-007) Add the *Score fire* drop-down to the Mode tab in `PR/ui/FollowPanel.java`, beside the existing flames choice (~line 210), with Off, Discreet and Full read from and written to `FireTuningStore.setScoreSetting` (contracts/advanced-tab.md "Mode tab"), and its label and tooltip keys `practice.fire.score.setting` and `practice.fire.score.setting.tip`. Add the EN/FR/ES texts in `LANG/messages*.properties`.
- [X] T029 [US1] (FR-010, SC-006) Gate: practice suite and `swt-gui-tests` green, T003 tests unchanged. Relaunch the app for a smoke check of Wait with Full on `tests/demo-arcade-morceau.tg`. With a throwaway timing print (not committed), measure the fire's own painting in the listener over 100 frames at FULL, window 1280×800, in Wait and in Follow. Record the median and the 95th percentile in `specs/019-score-demo-fire/baseline.txt` against the plan's 4 ms goal (SC-006). Above it, report before going on. Commit the phase.

**Implementation notes (Phase 3, as built):**
- `ScoreStreak` reads primitives (`waitSnapshot(acceptedCount, lastPassed, outcome, verdict)`, `followResult(eventIndex, status)`), with `waitSnapshot(PracticeSnapshot)` and `followSnapshot(FollowSession.Snapshot)` for the window: the snapshot constructors are package-private in `session`, so the tests drive the primitives.
- `ScoreFire.update` takes `judged` as well (for `growth.minJudged`), and `Score.guardBoxes` takes the palette's screen box (the port knows the canvas's screen origin); see contracts/score-overlay.md "The ports (as built)".
- `ScoreFireScene` gives the tongues `height.corners` minus the ember rows' share, so that the whole fire stays within that share of the score area (FR-004).
- T026: `redraw()` asks for a full picture (`requestFull()`), which wins over an overlay request made since the last paint in either order (the toolkit merges both into one paint); contract and research R2 updated.
- T018 passed at once; it was seen red by forcing the score's `growth.fall` to 1/3 for one run (not committed).
- T029: the fire's own painting is below 4 ms at the median and slightly above at the 95th percentile (baseline.txt); reported to the owner before Phase 4.

## Phase 4: User Story 2 — Sparks on each right note (Priority: P2)

**Goal**: each right note throws a burst rising from the bottom of the fire, more with the streak, gone within `sparks.life`, never over a guard box.

**Independent test**: `ScoreSparksTest` is green; a replay throws exactly one burst per right note.

- [X] T030 [P] [US2] (FR-006, FR-009) Create `TST/arcade/view/ScoreSparksTest.java` on `ScoreFireScene` with `SCORE_DEFAULTS`:
  - one burst at clock t gives spark items whose lowest row is the bottom of the fire (`sparks.start` 0) at age 0, and that rise with age;
  - no spark at age ≥ 1 s;
  - the count is 20 at streak 0 and 50 at streak 12 and above, × 0.6 discreet;
  - at most 300 in the air with 20 bursts;
  - none at BALANCED, with motion off or with OFF;
  - during a pause (the fire clock held), two pictures taken 0.5 s of wall time apart have the same sparks at the same places (Edge Case "Pause");
  - none above 12 % within 4 px of a guard box.
  It will likely pass at once, because `sparks.start` exists since T012 and `ScoreFireScene` since T022. It is then seen red by a deliberate mutation (`sparks.start` forced to 1 for one run, not committed), and the mutation is named in the commit message.
- [X] T031 [P] [US2] Create `TST/ui/ScoreBurstsTest.java`: on a scripted Wait run of 10 right notes, 2 wrong and 1 skip, and on a Follow run of 12 results, the number of bursts equals the number of right notes, and is 0 with OFF (SC-005). `ScoreFire` drops bursts older than `sparks.life`.
- [X] T032 [US2] (FR-006) Feed the bursts of `ScoreStreak` into the `FlameState` of `ScoreFire` (stamped with the fire clock) in `PR/ui/ScoreFire.java`; drop expired bursts; keep the timer ticking while sparks live. Make T030 and T031 pass. Gate and commit the phase.

**Implementation notes (Phase 4, as built):**
- T025 already fed, stamped and dropped the bursts, and kept the timer going while they live; T032 only opened `ScoreFire.flames()` to the package, so that `ScoreBurstsTest` reads the live bursts. `ScoreBurstsTest` was red at first (it did not compile), then green.
- T030 passed at once; it was seen red by forcing the score default of `sparks.start` to 1 for one run (not committed): `aBurstStartsAtTheBottomOfTheFireAndRises` failed (779 expected, 709 read).
- The "Pause" case is checked in both: the scene depends on the fire clock only (`ScoreSparksTest`), and `ScoreFire` holds that clock and its bursts through a pause (`ScoreBurstsTest`).
- A burst's streak is at least 1 (`FlameState.Burst`), so a burst shows 22 sparks at streak 1; the count of 20 at streak 0 is checked on `FlameDrawing.sparkCount`.
- Gate: practice suite 1382 tests, 0 failures, 12 skipped (1371 + 11 new).

## Phase 5: User Story 4 — The Advanced tab (Priority: P2)

**Goal**: an Advanced tab with the groups *Arcade fire* and *Score fire*, one row per parameter with a tooltip, a live preview, a *Sparks* button, a growth slider and *Default values*.

**Independent test**: `FireTextsTest` and `FireSettingsPanelSwtTest` are green; manually, move a setting and see the preview follow, then restart and check the value is kept.

- [X] T033 [P] [US4] Create `TST/ui/FireTextsTest.java` (FR-018, SC-009): for every `FireParameter` key in its groups, and for the fixed keys (`practice.tab.advanced`, `practice.fire.group.arcade`, `practice.fire.group.score`, `practice.fire.defaults`, `practice.fire.defaults.tip`, `practice.fire.preview.sparks`, `practice.fire.preview.growth`, `practice.fire.score.setting` and their `.tip`), a non-empty value exists in `LANG/messages.properties`, `messages_fr.properties` and `messages_es.properties`.
- [X] T034 [P] [US4] (FR-016, FR-019) Create `TST/ui/FireExtremesTest.java` (SC-011): for each parameter at its minimum and at its maximum, the others at their defaults, both groups:
  - the score scene (1280×800, guard boxes at the bottom) and the Arcade flame items (reference viewport, text boxes) have no item above 12 % opacity within 4 px of a box;
  - the item count is at most `(384 columns × rows) + 1000`;
  - the scripted Wait and Follow runs of T021, replayed with the score tuning at that extreme, give the same verdicts, streak and summary as at the defaults (SC-011, "the verdicts are unchanged");
  - nothing throws.
  It may pass at once after T012; it is then seen red by a deliberate mutation (the guard's `faded` bypassed for one run, not committed), and the mutation is named in the commit message.
- [X] T035 [P] [US4] (FR-020) Create `TST/ui/FirePreviewTest.java` (pure part of `FirePreview`):
  - a changed tuning is used at the next frame;
  - *Sparks* adds one burst at the slider's streak;
  - the growth slider maps to `share = streak / growth.full` for the score and to the share of the maximum for Arcade;
  - with motion off, the clock does not move and no burst is drawn;
  - `setShown(false)` stops the ticks.
- [X] T036 [P] [US4] Create `TST/ui/FireSettingsPanelSwtTest.java` (swt-gui-tests):
  - the panel builds two groups with one row per parameter of each group;
  - with an empty store, each row shows its group's default, and with a stored value it shows that value (US4 scenario 1);
  - each label and control has a tooltip that contains the parameter's text, its unit and its default as shown (US4 scenario 2, FR-018);
  - moving a scale writes the clamped value through a fake store at once;
  - *Default values* puts every row of the group back to its default and calls `reset`, leaving the other group untouched;
  - hiding the panel stops the preview timer;
  - a value changed while a run is going (a fake run holding the tuning it read at its start) leaves that run's tuning unchanged, and the next `load` gives the new value (FR-019, Edge Case "A setting changed during a run").
- [X] T037 [US4] (FR-020) Create `PR/ui/FirePreview.java`:
  - a `UICanvas` of about 360×120 painted by `UiPainterRenderer` from `ScoreFireScene` (score) or from the flame items of an empty Arcade picture (Arcade) through a public facade in `ARCADE/view` (`ScoreFireScene.arcadePreview(viewport, tier, flames)`);
  - its own `FlameLevel(tuning)`, clock, bursts and `QualityController(8_000_000L)`;
  - 30 frames per second only while shown and with motion.
  Make T035 pass.
- [X] T038 [US4] (FR-014, FR-015, FR-018) Create `PR/ui/FireSettingsPanel.java` per contracts/advanced-tab.md:
  - a scroll panel with two groups; each group has the preview, *Sparks*, the growth slider and *Default values*, then the sections (Look, Flames, Motion, Embers, Growth, Sparks, Colours);
  - number and integer rows: label, `createHorizontalScale` mapped onto `[min, max]` (1000 steps for numbers, exact steps for integers), value label with unit;
  - colour rows: swatch button and `createColorChooser`;
  - tooltips from `tooltipKey()` + unit + "default: X";
  - changes go through `FireTuning.of` (clamped), are saved through `FireTuningStore` and refresh the preview at once.
  Make T036 pass.
- [X] T039 [US4] (FR-014, FR-019) Add the tab `practice.tab.advanced` after *Passages* in `PR/ui/PracticeWindow.java` with `tab(uiFactory, folder, "practice.tab.advanced", layout)`, holding a `FireSettingsPanel`. The preview is shown only when this tab is selected and the window is visible (tab selection listener and window close). Arcade and score runs read the stored tunings at their start (T015, T027).
- [X] T040 [US4] Write every label and tooltip in `LANG/messages.properties` (EN), `messages_fr.properties` (FR) and `messages_es.properties` (ES): each tooltip says what the value changes in plain words. Make T033 pass; make T034 pass and check that T008 is still green, fixing code only. Gate and commit the phase.

**Implementation notes (Phase 5, as built):**
- Red first: `FirePreviewTest` and `FireSettingsPanelSwtTest` did not compile before `FirePreview` and `FireSettingsPanel`; `FireTextsTest` failed on the missing texts until T040. `FireExtremesTest` passed at once and was seen red with the guard's `faded` bypassed for one run (not committed).
- The judging at the extremes (T034, third point) is in the new `TST/session/FireExtremesVerdictsTest.java`: the harness of `ScoreFireVerdictsTest` (whose session snapshots are package-private) with the tuning as a parameter; T021's file is left as it is.
- `FireTuningStore.save(tuning, parameter)` (additive) writes the one value a move changes: `save(tuning)` writes the whole group, each write saving the configuration file.
- The fixed texts also hold `practice.fire.tooltip.unit`, `practice.fire.tooltip.default` and `practice.fire.colour.choose`, with the section titles and the units: 129 keys per language.
- The Advanced tab is not one of the refitted tabs: it scrolls and asks for no height, so the window keeps its size. The previews move only while the tab is selected and the window is shown (`updateFirePreviews`). The preview's motion uses `!ReducedMotion.requested()` until T043.
- Gate: practice suite 1394 tests, 0 failures, 12 skipped (1382 + 12 new); `FireSettingsPanelSwtTest` 5/5 under `swt-gui-tests`.

## Phase 6: User Story 3 — My choice kept, and the same everywhere (Priority: P3)

**Goal**: the setting survives a restart, defaults to Off, and Reduce motion is read on macOS, Linux and Windows for both fires.

**Independent test**: `SystemReducedMotionTest` is green, and `ScoreFireSettingKeptTest` (Phase 2) still is; manually, restart the app and find the setting kept.

- [X] T041 [P] [US3] (FR-013, SC-008) Create `TST/arcade/ui/SystemReducedMotionTest.java` with a fake `CommandRunner` (research R7):
  - **macOS**: delegates (same questions as `ReducedMotionTest`).
  - **Linux** (every question is asked; reduce if any answer says so): `gsettings` answering `false` gives reduce; `gsettings` `true` with `kreadconfig6` `0` gives reduce (KDE with gsettings installed); `kreadconfig6` failing and `kreadconfig5` `0` gives reduce; `true` with `1` gives none; when everything fails, none. It is fetched in the background like Windows.
  - **Windows**: the PowerShell command of research R7 answering `False` gives reduce and `True` gives none.
  - **Background, on Windows and Linux**: `requested()` returns the latest answer, none before the first one, and a fake slow runner never blocks the caller beyond 5 ms.
  - Other systems: none, nothing run.
  - Every command has a limit, and a runner that throws gives none.
- [X] T042 [US3] (FR-013) Create `ARCADE/ui/SystemReducedMotion.java`:
  - macOS through `ReducedMotion.requested(runner, os)`, synchronous as today;
  - Linux (all questions asked, reduce if any says so) and Windows through a single-thread daemon executor, refreshed by `refresh()`;
  - `requested()` is non-blocking on Windows and Linux.
  Make T041 pass, with `ReducedMotionTest` unchanged.
- [X] T043 [US3] (FR-009, FR-013, SC-008) Use `SystemReducedMotion` for Arcade (`PR/ui/PracticeWindow.java` line ~858, in place of `!ReducedMotion.requested()`), for the score fire (T027) and for the previews (T037). Call `refresh()` when the practice window opens and at each run start. Check that T009 (now in Phase 2) is still green, then gate and commit the phase.

**Implementation notes (Phase 6, as built):**
- `SystemReducedMotionTest` (7 cases) did not compile before `SystemReducedMotion`, then passed; `ReducedMotionTest` is unchanged and green.
- PowerShell gets a 5 s limit (its start alone often passes 500 ms); the Linux commands keep 500 ms. A `kreadconfig6` that fails or writes nothing counts as no answer, and `kreadconfig5` is asked.
- `PracticeWindow` holds one `SystemReducedMotion.ofThisSystem()` (its own daemon thread), refreshed when the window opens and when Start is pressed (before the start check, so the answer has time to come). Arcade, the score fire and the previews read it; the previews take the latest answer each time the Advanced tab is shown (`FireSettingsPanel.setMotion`).
- Gate: practice suite 1401 tests, 0 failures, 12 skipped (1394 + 7); `swt-gui-tests` 50 tests, 0 failures (45 + `FireSettingsPanelSwtTest` 5). T009 (`ScoreFireSettingKeptTest`) is in the green suite.

## Phase 7: Polish & cross-cutting concerns

- [X] T044 [P] Describe the score fire, its Mode tab setting, the Advanced tab, the previews and "the fire never changes the judging" in `docs/PRACTICE.md`, `docs/PRACTICE-FR.md` and `docs/PRACTICE-ES.md` (FR-012). Add a screenshot of the score fire and of the Advanced tab in `docs/images/` (taken on the built app).
- [X] T045 [P] (FR-011) Write the manual sheet `specs/test-sheets/F11-feu-partition.md`:
  - Wait and Follow at Full and at Discreet;
  - a mistake makes it fall, a pause holds it;
  - the guard near the framed note at the bottom;
  - the Advanced tab: every tooltip, the preview, *Default values*, a restart;
  - the system's Reduce motion;
  - a section for Linux and Windows testers.
  Add it to `specs/test-sheets/README.md`.
- [X] T046 Full gate: `mvn -o -e clean verify -P native-modules` from `BUILD` and the `swt-gui-tests` run. Record the counts in `specs/019-score-demo-fire/baseline.txt` (expected: 1324 + the new tests, 0 failures) and restore `specs/003-arcade-mode/benchmark/`. Check `git diff` on `TST` shows only new files.
- [X] T047 Write `specs/019-score-demo-fire/final-report.md`:
  - what was built and the R2 decision;
  - the tests added;
  - what is unverified (the real Linux and Windows commands of R7, judged by testers).
  Update the 019 row of `specs/STATUS.md` and the memory `pending-score-fire-spec`. Commit.
- [X] T048 [HUMAN] (FR-011) The owner's gate on the relaunched app, per sheet F11 (SC-001, SC-006): the score fire looks like Arcade's, grows and falls, the scrolling stays smooth, and the Advanced tab works. Values to tune are noted, changed and re-tried.

**Implementation notes (Phase 7, as built):**
- T044 is left open: the three guides describe the score fire, its Mode tab choice, the Advanced tab, the previews, Reduce motion on the three systems and "the fire never changes the judging", but the two screenshots in `docs/images/` are not taken (they need the built app driven by hand, and the owner's open TuxGuitar is not touched). They can be taken during T048.
- T046: gate green with the counts in `baseline.txt`; the test folders have additions only.
- T047: `final-report.md`; STATUS and the memory updated.

## Dependencies & execution order

- **Phase 1** comes before everything. **T002** decides **T026**.
- **Phase 2** (T004–T015) blocks every story. The tests T004–T009 come before T010–T014 (T008 before T012, T009 before T014, so both are seen red).
- **US1** (Phase 3) is the MVP. **US2** (Phase 4) needs US1's `ScoreFire`. **US4** (Phase 5) needs Phase 2 and US1's `ScoreFireScene` for the score preview. **US3** (Phase 6) needs Phase 2. It can come before or after US4, but **T043 touches T027's and T037's call sites**, so it runs after them.
- **Polish** (Phase 7) comes after all the stories. **T048** is the only human task.
- **Same-file sequencing**: `PracticeWindow.java` is changed by T015, T027, T039 and T043, in that order; `FlameDrawing.java`, `FireField.java` and `FireColours.java` only by T012; `ScoreFire.java` by T025, T026 and T032.

## Parallel examples

- **Phase 2**: T004–T009 together (six new test files).
- **US1**: T016–T021 together; then T022 and T023 in parallel.
- **US4**: T033–T036 together (four new test files).
- **US3**: T041 alone (T009 moved to Phase 2).
- **Polish**: T044 and T045 together.

## Implementation strategy

1. **MVP**: Phases 1–3. The fire on the score, at the defaults, chosen in the Mode tab. It can be shipped on its own.
2. **Then**: US2 (sparks), US4 (the Advanced tab), US3 (kept everywhere, Reduce motion on every system).
3. **Each phase ends green and committed**, without pausing between phases (owner's rule). The push comes after a few phases, in one round, and the release after the owner's gate T048.

## Phase 8: Convergence

- [X] T049 Bring the fire's own painting at FULL (1280×800, Wait and Follow, 100 frames, measured as in T029) to at most 4 ms at the 95th percentile (today 4.4 ms in Wait and 4.0 ms in Follow, medians 3.3 and 2.8 ms), without changing any picture of the identity tests; or, if the owner accepts the measured times, record the acceptance and its date in `specs/019-score-demo-fire/final-report.md` ("Measured") and leave the code as it is. The owner decides which one first. Per plan: Performance Goals (partial)
- [ ] T050 When the next release that carries spec 019 is prepared, write its notes with the call for Linux and Windows feedback: whether Reduce motion is honoured (GNOME, KDE, Windows), and how the score fire and the Advanced tab look and run there, with a link to the issues and to the L1, L2, W1 and X1 rows of sheet F11. Per plan: step 7 Closing, release notes (missing)

**Implementation notes (Phase 8, as built):**
- T049: the owner chose on 2026-11-01 to accept the measured times (95th percentile 4.4 ms in Wait, 4.0 ms in Follow); recorded in `final-report.md` ("Measured"). No code changed.
- T050 stays open until the next release that carries spec 019 is prepared.
- T048: on 2026-11-01 the owner tried the built app (development build of 11:16, after the last code change) with sheet F11 and answered "ça me semble bon": gate passed, no value to tune. The answer was overall: on the sheet, step 1 is marked OK and the other steps are left blank; no step was reported KO. T044's screenshots were not taken during the gate.
- T044 (closed 2026-11-01, after the owner chose a throwaway build): the two screenshots `docs/images/features/practice-score-fire.png` and `practice-advanced-tab.png` were taken with `screencapture` on a separate, never committed build (a git worktree in the scratchpad, removed afterwards). Its only change: when started with `TGA_SHOTS`, it started the score fire at Full with the score defaults and fed it a scripted streak of right notes (one every 0.4 s, up to 14), or opened the practice window on the Advanced tab. No audio and no real run: the score-fire picture shows the fire at the bottom of `tests/demo-arcade-morceau.tg` without the palette or the red frame of a run. Both images are in the three guides.
