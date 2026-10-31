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

- [ ] T001 Write the baseline before any code in `specs/019-score-demo-fire/baseline.txt`:
  - the starting commit;
  - the counts of `mvn -o -e clean verify -P native-modules` from `BUILD`, per module (practice expected 1324, 0 failures, 12 skipped);
  - the `swt-gui-tests` run (expected 37);
  - the number of flame items of `ReferenceScenes.flameReading()` at FULL and BALANCED.
  Restore `specs/003-arcade-mode/benchmark/` afterwards.
- [ ] T002 Measure the cost of a full Wait repaint of the score (research R2) and record the median and the 95th percentile in `specs/019-score-demo-fire/baseline.txt`.
  - **How**: a throwaway timing print around `fillPaintBuffer()` in `CORE/util/TGBufferedPainterLocked.java`, **not committed**. Built app, `tests/demo-arcade-morceau.tg`, window 1280×800, player stopped, 100 redraws forced by a throwaway loop.
  - **Follow** (research R2, "Follow"): with the same throwaway print, check whether `MidiPlayer.getInstance(context).isRunning()` is true during a Follow run, with the backing on and muted. If it is not, measure the Follow repaint in the same way; the threshold then decides the hook for both modes.
  - **Then**: remove the print, check that `git diff CORE` is empty, and write the decision: **hook needed** if the median is above 3 ms or the 95th percentile is above 6 ms, otherwise **no hook**. Copy the numbers and the decision into research R2 ("Measured").
- [ ] T003 List in `specs/019-score-demo-fire/baseline.txt` the existing tests that must stay unchanged and green: `FireFieldTest`, `FireColoursTest`, `FireTranslucencyTest`, `FlameSparksTest`, `FireLookTuningTest`, `FlameCellsPaintTest`, `FlameSceneTest`, `FlameContrastTest`, `FlameParityTest`, `ArcadeReferenceSceneTest`, `ArcadeSceneQualityTest`, `ArcadeFlamesSettingTest`, `ArcadeFrameSourceTest`, `ArcadeFrameSourceBurstsTest`, `FallbackRendererTest`, `ArcadeCanvasSwtTest`, `ReducedMotionTest`, and the `FollowPanel`/`PracticeWindow` tests that exist. Find them with `grep -rl` under `TST`.

## Phase 2: Foundational — the tuning, with identical pictures (blocks every story)

**Goal**: every value that shapes a fire lives in a `FireTuning` whose defaults are today's constants; no picture changes.

**Independent test**: `FireParameterDefaultsTest`, `FireTuningTest`, `FireTuningIdentityTest` and `FireTuningStoreTest` are green, and every test of T003 is green, unchanged.

### Tests first (see them fail)

- [ ] T004 [P] Create `TST/arcade/view/FireParameterDefaultsTest.java`:
  - every row of the data-model table exists as a `FireParameter`, with its key, kind, range and both defaults;
  - `min ≤ arcadeDefault ≤ max` and `min ≤ scoreDefault ≤ max` for every parameter, including `growth.full` (Arcade default 16, never read; `inArcade()` false);
  - one assertion per constant: `opacity.most == FireColours.MOST`, `cell.columns == FireField.COLUMNS`, `cell.smallest == FireField.SMALLEST`, `colour.steps == FireColours.STEPS`, `height.corners == FlameDrawing.CORNER_HEIGHT`, `height.middle == FlameDrawing.MIDDLE`, `discreet.share == FlameDrawing.DISCREET`, `ember.rows == FireField.EMBER_ROWS`, `sparks.most == FlameDrawing.MOST_SPARKS`, `sparks.life == FlameDrawing.SPARK_LIFE / 1e9`, `growth.minJudged == FlameLevel.MIN_JUDGED`, `growth.rise == FlameLevel.RISE_PER_SECOND`, `growth.fall == FlameLevel.FALL_PER_SECOND`, `growth.maxStep == FlameLevel.MAX_STEP_NANOS / 1e6`;
  - the score defaults: `height.corners` 1/3, `growth.full` 16, `growth.fall` 1.0, `growth.from` 0, `growth.minJudged` 0, `sparks.start` 0;
  - the cost ceilings: `cell.columns` max 384, `cell.smallest` min 3, `sparks.most` max 1000, `sparks.base` max 200, `sparks.perStreak` max 50, `colour.steps` max 64.
- [ ] T005 [P] Create `TST/arcade/view/FireTuningTest.java`:
  - `FireTuning.of(group, map)` clamps every value into `[min, max]`;
  - NaN or a missing value gives the group's default;
  - colours are masked to RGB;
  - `ARCADE_DEFAULTS` and `SCORE_DEFAULTS` differ only on the parameters whose two defaults differ;
  - equal maps give equal tunings (same `equals` and `hashCode`);
  - the spark count `floor(2.5 * min(streak, 12) + 1e-9) + 20` equals `20 + 5 * min(streak, 12) / 2` for every streak from 0 to 128.
- [ ] T006 [P] Create `TST/arcade/view/FireTuningIdentityTest.java`:
  - `FlameDrawing.items(viewport, tier, flames, texts)` and the new overload with `FireTuning.ARCADE_DEFAULTS` give equal item lists (shape, role, points, colour, opacity, code) for FULL and BALANCED, Discreet and Full, levels 0, 0.3 and 1, motion on and off, 0 and 5 bursts, on the seven window shapes of spec 009;
  - the reference scene built with the defaults is byte-identical to `reference/flame-reading.scene.txt` (read the file, do not regenerate it);
  - `new FlameLevel()` and `new FlameLevel(ARCADE_DEFAULTS)` give the same levels over a scripted sequence of updates, holds and resumes.
- [ ] T007 [P] Create `TST/session/FireTuningStoreTest.java` with an in-memory `ArcadeChoices.Persistence`:
  - `save` then `load` gives the same tuning for each group, and only values that differ from the default are written;
  - `reset(group)` gives the defaults and leaves the other group untouched;
  - a corrupt value (`"abc"`), an out-of-range value or a colour without `#` gives the default or the clamped value;
  - a persistence that throws on get or set gives the defaults, and `false` on write, never an exception;
  - `scoreSetting()` is OFF when missing or unknown, and `setScoreSetting(FULL)` then `scoreSetting()` gives FULL.
- [ ] T008 [P] Create `TST/arcade/view/FireSourceScanTest.java` (SC-009, contracts/fire-tuning.md "Catalogue"): it scans `ARCADE/view/FireField.java`, `FireColours.java` and `FlameDrawing.java` for decimal literals other than 0, 1, 2 and 0.5 (used in arithmetic), outside the declarations of the fixed constants and the constants that serve as defaults, against an explicit allow-list. It fails on any value shaping the fire that the tuning does not cover. Written before T012, it is seen red on today's literals.
- [ ] T009 [P] Create `TST/session/ScoreFireSettingKeptTest.java` (SC-007): a fresh store gives OFF. FULL, then a new `FireTuningStore` over the same persistence, gives FULL; the same for DISCREET; a tuned score value survives the same way. (It extends T007's scope through a new file; T007 is left as it is.) Written before T014, it is seen red (the store does not exist yet).

### Implementation

- [ ] T010 Create `ARCADE/view/FireParameter.java` (public enum): the catalogue of data-model.md, with the Arcade defaults referencing the existing constants where they exist, `labelKey()` = `practice.fire.<key>`, `tooltipKey()` = `practice.fire.<key>.tip`, `section()`, `unit()`, `kind()`, `inArcade()`/`inScore()` (`growth.full` is score only). Make T004 pass.
- [ ] T011 Create `ARCADE/view/FireTuning.java` (public, immutable): `of(Group, Map<FireParameter, Double>)` with clamping, `ARCADE_DEFAULTS`, `SCORE_DEFAULTS`, `value`, `integer`, `colour`, `with(parameter, value)`, `equals` and `hashCode`; `Group` is ARCADE or SCORE. Make T005 pass.
- [ ] T012 Give the fire classes a tuning **without changing any existing signature or constant**:
  - `ARCADE/view/FireColours.java`: `colour(step, tuning)` and `alpha(step, discreet, tuning)`; steps, scale, most and discreet are read from the tuning.
  - `ARCADE/view/FireField.java`: `FireField(viewport, tier, flames, tuning)` and `cell(viewport, tier, tuning)`. Every literal of `heat`/`noise` (0.12, 0.08, 3.0, 1.15, 1.5, 1.3, 0.2, 0.6, 1.7, 0.35, 0.5, 0.3, 0.2, RISE, EMBER_ROWS, COLUMNS, SMALLEST, CORNER_HEIGHT, DISCREET) is read from the tuning, and the clock is multiplied by `motion.speed`.
  - `ARCADE/view/FlameDrawing.java`: `items(viewport, tier, flames, texts, tuning)`. The spark literals (20, 2.5, 12, 0.25, 10, 16, 10, 8, MOST_SPARKS, SPARK_LIFE) come from the tuning. The spark start row is `EMBER_ROWS + round(sparks.start × tongueRows(start))`, which at 1 is today's top of the flames. `weight` uses `height.middle`.
  - `ARCADE/view/FlameState.java`: an additive constructor with a `FireTuning`, and `tuning()`, which is `ARCADE_DEFAULTS` when absent.
  - The old entry points delegate with `ARCADE_DEFAULTS`, or `flames.tuning()`. Keep `WIDEN`, `NEAR_TEXT`, `RAMP`, `SEED`, the hash and `TONGUES` fixed (research R4).
  - Make T006 pass with every T003 test green.
- [ ] T013 `ARCADE/ui/FlameLevel.java`: an additive constructor `FlameLevel(FireTuning)` reading `growth.from`, `growth.minJudged`, `growth.rise`, `growth.fall` and `growth.maxStep`; `new FlameLevel()` keeps the constants. `ARCADE/ui/ArcadeFrameSource.java`: an additive constructor parameter for the Arcade tuning (default `ARCADE_DEFAULTS`), used for its `FlameLevel`, its spark life (in place of `SPARK_NANOS`) and its `FlameState`. Make the FlameLevel part of T006 pass; `ArcadeFrameSourceTest` and `ArcadeFrameSourceBurstsTest` stay green.
- [ ] T014 Create `PR/session/FireTuningStore.java`:
  - module `tuxguitar-practice-fire`;
  - keys `arcade.<key>`, `score.<key>` and `score.setting`;
  - numbers written with `Double.toString`, colours as `#RRGGBB`;
  - `of(TGContext)` built on `TGConfigManager` like `FollowChoices.of`;
  - `load`, `save`, `reset`, `scoreSetting`, `setScoreSetting`; never throws.
  Make T007 pass.
- [ ] T015 Use the stored Arcade tuning in a real Arcade run: `PR/ui/PracticeWindow.java` (around line 858, where `ArcadeSession` gets `getFlames()`) passes `FireTuningStore.of(context).load(ARCADE)` down to `ArcadeFrameSource` (through `PR/arcade/ui/ArcadeSession.java`, with an additive parameter). With nothing stored, it is `ARCADE_DEFAULTS`. Gate: practice suite green, T003 tests unchanged. Commit the phase.

## Phase 3: User Story 1 — A fire that grows with my streak (Priority: P1) 🎯 MVP

**Goal**: with *Score fire* on, a fire burns at the bottom of the visible score in Wait and Follow, grows with the streak and falls on a mistake, without ever covering the framed note, the next note or the palette.

**Independent test**: `ScoreStreakTest`, `ScoreFireSceneTest`, `ScoreFireLevelTest` and `ScoreFireOverlaySwtTest` are green; manually, Full in the Mode tab, then a Wait run of right notes and a wrong note.

### Tests first (see them fail)

- [ ] T016 [P] [US1] Create `TST/ui/ScoreStreakTest.java` (pure, data-model "ScoreStreak" table):
  - **Wait**: three snapshots each passing an ACCEPTED event give streaks 1, 2 and 3 with three bursts; the same snapshot fed twice counts once; a WRONG verdict gives 0; SKIPPED gives 0; AUTO_PASSED leaves the streak unchanged with no burst.
  - **Follow**: results going PENDING → MATCHED count once per `eventIndex`, including early and late offsets; WRONG_PITCH and MISSED give 0; a result seen again counts nothing.
  - `reset()` gives 0 and no bursts.
  - The streak never changes the snapshot it reads.
- [ ] T017 [P] [US1] Create `TST/arcade/view/ScoreFireSceneTest.java` on `ScoreFireScene.scene(viewport, tier, flames, guard)` with `SCORE_DEFAULTS`:
  - null, OFF or PERFORMANCE gives no item;
  - level 0 gives ember rows only, on the bottom two rows;
  - at level 1, the highest lit row at the corners is within one cell of 1/3 of the viewport height and never above it (FR-004), and the middle tenth is at most 22 % of the corners;
  - every item has opacity ≤ 0.85, or ≤ 0.85 × 0.6 discreet;
  - a small score area (Edge Case): at 600×120 the corners stay within 40 px; at 600×24, fewer than 8 cells high, there are ember rows only, whatever the level (contracts/score-overlay.md);
  - with guard boxes at the bottom (a framed note, a next note, a palette), no item above 12 % opacity meets a box widened by 4 px (FR-005, SC-003), on the window shapes 1280×800, 800×600, 1500×500 and 600×300;
  - the same inputs give the same items.
- [ ] T018 [P] [US1] Create `TST/ui/ScoreFireLevelTest.java` on `new FlameLevel(SCORE_DEFAULTS)` fed with `share = min(1, streak / 16)` and `judged` = the number of events, on a scripted clock at 30 updates per second:
  - streak 0 stays at 0;
  - each right note raises the level towards `streak / 16`, reaching 1 at streak 16 and holding there;
  - after a mistake at full height, the level is 0 within 1.0 s (+ one step) (FR-003, SC-002);
  - `hold`/`resume` keep the level through a pause.
- [ ] T019 [P] [US1] Create `TST/ui/ScoreFireOverlaySwtTest.java` (profile `swt-gui-tests`, real toolkit, like `ArcadeCanvasSwtTest`):
  - a canvas with a first listener that fills it white, given to `ScoreFire` through its canvas port (T024), then `ScoreFire` started at level 1 and FULL: after a paint, the bottom-corner pixels are not white and the top half is white;
  - with setting OFF, `start` adds no listener and the canvas stays white;
  - after `stop()`, a paint leaves the canvas white;
  - the first listener's pixels outside the fire are unchanged.
- [ ] TNEW [P] [US1] Create `TST/ui/ScoreGuardBoxesTest.java` on the pure `ScoreGuardBoxes` (SC-003, research R8):
  - with a layout stub giving frame boxes for the current and next events, the result is those two boxes as `{x1, y1, x2, y2}`, canvas-local, unchanged;
  - a palette at screen (900, 700, 200, 60) over a score area whose screen origin is (100, 80) gives the canvas box (800, 620, 1000, 680);
  - an event with no frame (off the visible score), no next event or no palette is left out, and no box at all gives an empty list;
  - a box partly outside the canvas is kept as it is (the guard fades the fire up to the canvas edge).

### Implementation

- [ ] T021 [US1] Create `ARCADE/view/ScoreFireScene.java` (public): `scene(Viewport, QualityTier, FlameState, List<double[]> guard)` returns a `Scene` holding only `FlameDrawing.items(viewport, tier, flames, guard, flames.tuning())`; when the viewport is fewer than 8 cells high, the level is taken as 0 (ember rows only). Make T017 pass.
- [ ] T022 [US1] Create `PR/ui/ScoreStreak.java` (pure) per research R3. Make T016 pass.
- [ ] T023 [US1] Make T018 pass with `FlameLevel(FireTuning)` and the score defaults. Change only `ARCADE/ui/FlameLevel.java` if needed, keeping `new FlameLevel()` identical (T006).
- [ ] T024 [US1] Create `PR/ui/ScoreFire.java` per contracts/score-overlay.md and data-model "ScoreFire":
  - `start(setting, tuning, motion)`, `update(streak, newBursts, paused, currentEvent, nextEvent, paletteBox)` and `stop()`;
  - a constructor taking a **canvas port**: a small interface `ScoreFire.Score` with `UICanvas canvas()` (null when there is none), `List<double[]> guardBoxes(PracticeEvent current, PracticeEvent next)` and `void redraw(boolean overlayOnly)`; the production port wraps `TGTabFolder.getInstance(context).findSelectedControl()` and `ScoreGuardBoxes`; the tests give a fake;
  - attaches one `UIPaintListener` to `port.canvas()` and re-attaches if it changes;
  - a timer at about 33 ms through `TGSynchronizer`/`UIApplication.runLater`, ticking only while the fire moves;
  - the fire clock advances only while running;
  - `FlameLevel(tuning)`;
  - guard boxes from `PR/ui/ScoreGuardBoxes.java` (new, pure): the frame boxes of the current and next events (`TGExternalNoteFramePainter.frameBounds`, canvas-local) and the palette's screen box minus the score area's screen origin, as `{x1, y1, x2, y2}`; missing frames or palette are left out; it makes T020 pass;
  - a `QualityController(8_000_000L)` fed with the painting time;
  - a `UiPainterRenderer`;
  - `stop()` removes the listener and redraws once.
  Make T019 pass.
- [ ] T025 [US1] **Only if T002 decided "hook needed"**: in `CORE/util/TGBufferedPainterLocked.java`, add `requestOverlayOnly()` (a one-shot flag; `paintBufferLocked` skips `fillPaintBuffer()` when the flag is set and the buffer exists with the canvas size, and clears it in every case). In `CORE/component/tab/TGControl.java`, add `redrawOverlay()`, and make `redraw()` clear the flag. `ScoreFire` uses `redrawOverlay()` in Wait.
  - **First**, a new test `TST/ui/OverlayOnlyRepaintSwtTest.java` (swt-gui-tests): after `redrawOverlay()`, `paintControl` of the handle is not called and the buffer is drawn; after `redraw()`, it is called.
  - Mark the task done with "not needed (T002)" otherwise.
- [ ] T026 [US1] Wire the fire in `PR/ui/PracticeWindow.java`:
  - when a Wait or Follow run starts, read `FireTuningStore.of(context)` (setting and SCORE tuning) and `SystemReducedMotion` (Phase 6; until then `!ReducedMotion.requested()`), reset a `ScoreStreak` and call `ScoreFire.start`;
  - in `render(PracticeSnapshot)`, next to `paletteGuard.show` (~line 798), and in `renderFollow` (~line 1177), feed the streak and call `ScoreFire.update` with the pause state read as the palette reads it;
  - at `paletteGuard.stop()` (~line 1253) and in `stopNow()`, call `ScoreFire.stop()`.
- [ ] T027 [US1] Add the *Score fire* drop-down to the Mode tab in `PR/ui/FollowPanel.java`, beside the existing flames choice (~line 210), with Off, Discreet and Full read from and written to `FireTuningStore.setScoreSetting` (contracts/advanced-tab.md "Mode tab"), and its label and tooltip keys `practice.fire.score.setting` and `practice.fire.score.setting.tip`. Add the EN/FR/ES texts in `LANG/messages*.properties`.
- [ ] T028 [US1] Create `TST/ui/ScoreFireVerdictsTest.java` (FR-008, SC-004): the same scripted Wait run and Follow run, with the fire OFF and FULL, give identical verdicts, accepted counts, streak, timings and summary, and identical calls to `paletteGuard` (the palette of spec 017 moves the same). It drives `ScoreStreak` + `ScoreFire` through a fake canvas port and checks that nothing of the session is written. See it fail before the port exists, then make it pass.
- [ ] T029 [US1] Gate: practice suite and `swt-gui-tests` green, T003 tests unchanged. Relaunch the app for a smoke check of Wait with Full on `tests/demo-arcade-morceau.tg`. With a throwaway timing print (not committed), measure the fire's own painting in the listener over 100 frames at FULL, window 1280×800, in Wait and in Follow. Record the median and the 95th percentile in `specs/019-score-demo-fire/baseline.txt` against the plan's 4 ms goal (SC-006). Above it, report before going on. Commit the phase.

## Phase 4: User Story 2 — Sparks on each right note (Priority: P2)

**Goal**: each right note throws a burst rising from the bottom of the fire, more with the streak, gone within `sparks.life`, never over a guard box.

**Independent test**: `ScoreSparksTest` is green; a replay throws exactly one burst per right note.

- [ ] T030 [P] [US2] Create `TST/arcade/view/ScoreSparksTest.java` on `ScoreFireScene` with `SCORE_DEFAULTS`:
  - one burst at clock t gives spark items whose lowest row is the bottom of the fire (`sparks.start` 0) at age 0, and that rise with age;
  - no spark at age ≥ 1 s;
  - the count is 20 at streak 0 and 50 at streak 12 and above, × 0.6 discreet;
  - at most 300 in the air with 20 bursts;
  - none at BALANCED, with motion off or with OFF;
  - during a pause (the fire clock held), two pictures taken 0.5 s of wall time apart have the same sparks at the same places (Edge Case "Pause");
  - none above 12 % within 4 px of a guard box.
  See it fail where the start row is wrong, then pass.
- [ ] T031 [P] [US2] Create `TST/ui/ScoreBurstsTest.java`: on a scripted Wait run of 10 right notes, 2 wrong and 1 skip, and on a Follow run of 12 results, the number of bursts equals the number of right notes, and is 0 with OFF (SC-005). `ScoreFire` drops bursts older than `sparks.life`.
- [ ] T032 [US2] Feed the bursts of `ScoreStreak` into the `FlameState` of `ScoreFire` (stamped with the fire clock) in `PR/ui/ScoreFire.java`; drop expired bursts; keep the timer ticking while sparks live. Make T030 and T031 pass. Gate and commit the phase.

## Phase 5: User Story 4 — The Advanced tab (Priority: P2)

**Goal**: an Advanced tab with the groups *Arcade fire* and *Score fire*, one row per parameter with a tooltip, a live preview, a *Sparks* button, a growth slider and *Default values*.

**Independent test**: `FireTextsTest` and `FireSettingsPanelSwtTest` are green; manually, move a setting and see the preview follow, then restart and check the value is kept.

- [ ] T033 [P] [US4] Create `TST/ui/FireTextsTest.java` (FR-018, SC-009): for every `FireParameter` key in its groups, and for the fixed keys (`practice.tab.advanced`, `practice.fire.group.arcade`, `practice.fire.group.score`, `practice.fire.defaults`, `practice.fire.defaults.tip`, `practice.fire.preview.sparks`, `practice.fire.preview.growth`, `practice.fire.score.setting` and their `.tip`), a non-empty value exists in `LANG/messages.properties`, `messages_fr.properties` and `messages_es.properties`.
- [ ] T034 [P] [US4] Create `TST/ui/FireExtremesTest.java` (SC-011): for each parameter at its minimum and at its maximum, the others at their defaults, both groups:
  - the score scene (1280×800, guard boxes at the bottom) and the Arcade flame items (reference viewport, text boxes) have no item above 12 % opacity within 4 px of a box;
  - the item count is at most `(384 columns × rows) + 1000`;
  - nothing throws.
  It may pass at once after T012; it is then seen red by a deliberate mutation (the guard's `faded` bypassed for one run, not committed), and the mutation is named in the commit message.
- [ ] T035 [P] [US4] Create `TST/ui/FirePreviewTest.java` (pure part of `FirePreview`):
  - a changed tuning is used at the next frame;
  - *Sparks* adds one burst at the slider's streak;
  - the growth slider maps to `share = streak / growth.full` for the score and to the share of the maximum for Arcade;
  - with motion off, the clock does not move and no burst is drawn;
  - `setShown(false)` stops the ticks.
- [ ] T036 [P] [US4] Create `TST/ui/FireSettingsPanelSwtTest.java` (swt-gui-tests):
  - the panel builds two groups with one row per parameter of each group, and each label and control has a non-empty tooltip;
  - moving a scale writes the clamped value through a fake store at once;
  - *Default values* puts every row of the group back to its default and calls `reset`, leaving the other group untouched;
  - hiding the panel stops the preview timer.
- [ ] T037 [US4] Create `PR/ui/FirePreview.java`:
  - a `UICanvas` of about 360×120 painted by `UiPainterRenderer` from `ScoreFireScene` (score) or from the flame items of an empty Arcade picture (Arcade) through a public facade in `ARCADE/view` (`ScoreFireScene.arcadePreview(viewport, tier, flames)`);
  - its own `FlameLevel(tuning)`, clock, bursts and `QualityController(8_000_000L)`;
  - 30 frames per second only while shown and with motion.
  Make T035 pass.
- [ ] T038 [US4] Create `PR/ui/FireSettingsPanel.java` per contracts/advanced-tab.md:
  - a scroll panel with two groups; each group has the preview, *Sparks*, the growth slider and *Default values*, then the sections (Look, Flames, Motion, Embers, Growth, Sparks, Colours);
  - number and integer rows: label, `createHorizontalScale` mapped onto `[min, max]` (1000 steps for numbers, exact steps for integers), value label with unit;
  - colour rows: swatch button and `createColorChooser`;
  - tooltips from `tooltipKey()` + unit + "default: X";
  - changes go through `FireTuning.of` (clamped), are saved through `FireTuningStore` and refresh the preview at once.
  Make T036 pass.
- [ ] T039 [US4] Add the tab `practice.tab.advanced` after *Passages* in `PR/ui/PracticeWindow.java` with `tab(uiFactory, folder, "practice.tab.advanced", layout)`, holding a `FireSettingsPanel`. The preview is shown only when this tab is selected and the window is visible (tab selection listener and window close). Arcade and score runs read the stored tunings at their start (T015, T026).
- [ ] T040 [US4] Write every label and tooltip in `LANG/messages.properties` (EN), `messages_fr.properties` (FR) and `messages_es.properties` (ES): each tooltip says what the value changes in plain words. Make T033 pass; make T008 and T034 pass, fixing code only. Gate and commit the phase.

## Phase 6: User Story 3 — My choice kept, and the same everywhere (Priority: P3)

**Goal**: the setting survives a restart, defaults to Off, and Reduce motion is read on macOS, Linux and Windows for both fires.

**Independent test**: `ScoreFireSettingKeptTest` and `SystemReducedMotionTest` are green.

- [ ] T041 [P] [US3] Create `TST/arcade/ui/SystemReducedMotionTest.java` with a fake `CommandRunner` (research R7):
  - **macOS**: delegates (same questions as `ReducedMotionTest`).
  - **Linux**: `gsettings` answering `false` gives reduce; `true` gives none; when `gsettings` fails, `kreadconfig6` answering `0` gives reduce; when it fails too, `kreadconfig5` answering `0` gives reduce; `1` gives none; when everything fails, none.
  - **Windows**: the PowerShell command of research R7 answering `False` gives reduce and `True` gives none. It is fetched in the background: `requested()` returns the latest answer, none before the first one, and a fake slow runner never blocks the caller beyond 5 ms.
  - Other systems: none, nothing run.
  - Every command has a limit, and a runner that throws gives none.
- [ ] T042 [US3] Create `ARCADE/ui/SystemReducedMotion.java`:
  - macOS through `ReducedMotion.requested(runner, os)`; the Linux order of research R7; Windows through a single-thread daemon executor, refreshed by `refresh()`;
  - `requested()` is non-blocking on Windows and uses `LIMIT_MILLIS` elsewhere.
  Make T041 pass, with `ReducedMotionTest` unchanged.
- [ ] T043 [US3] Use `SystemReducedMotion` for Arcade (`PR/ui/PracticeWindow.java` line ~858, in place of `!ReducedMotion.requested()`), for the score fire (T026) and for the previews (T037). Call `refresh()` when the practice window opens and at each run start. Check that T009 (now in Phase 2) is still green, then gate and commit the phase.

## Phase 7: Polish & cross-cutting concerns

- [ ] T044 [P] Describe the score fire, its Mode tab setting, the Advanced tab, the previews and "the fire never changes the judging" in `docs/PRACTICE.md`, `docs/PRACTICE-FR.md` and `docs/PRACTICE-ES.md` (FR-012). Add a screenshot of the score fire and of the Advanced tab in `docs/images/` (taken on the built app).
- [ ] T045 [P] Write the manual sheet `specs/test-sheets/F11-feu-partition.md`:
  - Wait and Follow at Full and at Discreet;
  - a mistake makes it fall, a pause holds it;
  - the guard near the framed note at the bottom;
  - the Advanced tab: every tooltip, the preview, *Default values*, a restart;
  - the system's Reduce motion;
  - a section for Linux and Windows testers.
  Add it to `specs/test-sheets/README.md`.
- [ ] T046 Full gate: `mvn -o -e clean verify -P native-modules` from `BUILD` and the `swt-gui-tests` run. Record the counts in `specs/019-score-demo-fire/baseline.txt` (expected: 1324 + the new tests, 0 failures) and restore `specs/003-arcade-mode/benchmark/`. Check `git diff` on `TST` shows only new files.
- [ ] T047 Write `specs/019-score-demo-fire/final-report.md`:
  - what was built and the R2 decision;
  - the tests added;
  - what is unverified (the real Linux and Windows commands of R7, judged by testers).
  Update the 019 row of `specs/STATUS.md` and the memory `pending-score-fire-spec`. Commit.
- [ ] T048 [HUMAN] The owner's gate on the relaunched app, per sheet F11 (SC-001, SC-006): the score fire looks like Arcade's, grows and falls, the scrolling stays smooth, and the Advanced tab works. Values to tune are noted, changed and re-tried.

## Dependencies & execution order

- **Phase 1** comes before everything. **T002** decides **T025**.
- **Phase 2** (T004–T015) blocks every story. The tests T004–T009 come before T010–T014 (T008 before T012, T009 before T014, so both are seen red).
- **US1** (Phase 3) is the MVP. **US2** (Phase 4) needs US1's `ScoreFire`. **US4** (Phase 5) needs Phase 2 and US1's `ScoreFireScene` for the score preview. **US3** (Phase 6) needs Phase 2. It can come before or after US4, but **T043 touches T026's and T037's call sites**, so it runs after them.
- **Polish** (Phase 7) comes after all the stories. **T048** is the only human task.
- **Same-file sequencing**: `PracticeWindow.java` is changed by T015, T026, T039 and T043, in that order; `FlameDrawing.java`, `FireField.java` and `FireColours.java` only by T012; `ScoreFire.java` by T024, T025 and T032.

## Parallel examples

- **Phase 2**: T004–T009 together (six new test files).
- **US1**: T016–T020 together; then T021 and T022 in parallel.
- **US4**: T033–T036 together (four new test files).
- **US3**: T041 alone (T009 moved to Phase 2).
- **Polish**: T044 and T045 together.

## Implementation strategy

1. **MVP**: Phases 1–3. The fire on the score, at the defaults, chosen in the Mode tab. It can be shipped on its own.
2. **Then**: US2 (sparks), US4 (the Advanced tab), US3 (kept everywhere, Reduce motion on every system).
3. **Each phase ends green and committed**, without pausing between phases (owner's rule). The push comes after a few phases, in one round, and the release after the owner's gate T048.
