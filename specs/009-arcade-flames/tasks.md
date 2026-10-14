---

description: "Tasks of spec 009: the stage flames behind the Arcade run"
---

# Tasks: Arcade stage flames

**Input**: `specs/009-arcade-flames/` (spec.md with its Clarifications, plan.md, research.md R1-R9, data-model.md, contracts/arcade-flames.md, quickstart.md)

**Prerequisites**: plan.md and spec.md (US1 to US4, FR-001 to FR-012, SC-001 to SC-007). **Nothing in `arcade/core` changes** (the judging cannot depend on the flames). Gate G1 (the mockups) is passed (2026-10-14); **gate G2** is the owner looking at the built application (T036).

**Tests**: requested (the plan lists them); written **before** the code they test and seen red first.

**Conventions**: `SRC` = `desktop/TuxGuitar-practice/src/app/tuxguitar/app/tools/custom/practice`, `TST` = `desktop/TuxGuitar-practice/test/app/tuxguitar/app/tools/custom/practice`, `LANG` = `desktop/TuxGuitar-practice/share/lang`. `[HUMAN]` = needs the owner. `[P]` = parallel (different files). Build and test commands are those of `quickstart.md`; every task that changes code ends with the module tests green. Commit each phase with no co-author trailer; one push per round; the whole Spec Kit method in order (the owner's rule of 2026-10-14).

## Phase 1: Setup

- [ ] T001 Record the state before the change: run `cd desktop/build-scripts/tuxguitar-macosx-swt-cocoa && mvn -e clean verify -P native-modules,swt-gui-tests` and `mvn test -pl ../../TuxGuitar-practice -am`, keep the counts of tests run, failures and skipped in `specs/009-arcade-flames/benchmark/before-after.txt` (reference for SC-005 and the build integrity of Constitution XI).
- [ ] T002 [P] Copy the mockups needed by the reviews to `specs/009-arcade-flames/mockups/` if not there (`A2-full.png`, `A2-discreet.png`, `A2-mouvement-coin-gauche.png`: already there) and say in `specs/009-arcade-flames/benchmark/before-after.txt` that the reference scenes `TST/arcade/reference/*.scene.txt` must stay unchanged by this feature (they are made with the setting off, SC-005).

## Phase 2: Foundational (blocks every story)

- [ ] T003 [P] Add the roles `FLAME_TONGUE` and `FLAME_EMBER` to `SRC/arcade/view/SceneItem.java` (`Role`) and to `isDecoration()` (both decoration, so the fallback renderer and `UiPainterRenderer` leave them out with the existing rule); check every `switch` on `Role` in `SRC` and `TST` (for example `SceneBoxes.labels`) still compiles and means the same.
- [ ] T004 [P] Add `FlameSetting` (enum `OFF`, `DISCREET`, `FULL`) in `SRC/session/FlameSetting.java` and `getFlames()` / `setFlames(FlameSetting)` with default `DISCREET` in `SRC/session/FollowSettings.java` (kept in memory for the session of the application, like `getFarScalePercent()`); test `TST/session/FollowSettingsFlamesTest.java` written first: default is `DISCREET`, set and get, an unknown value is refused.
- [ ] T005 [P] Add the immutable `FlameState` in `SRC/arcade/view/FlameState.java` with `FlameState(FlameSetting setting, double level, long instantNanos)`: "A level outside 0..1 is clamped; a viewport of zero size draws nothing" (data-model.md); getters only.

**Checkpoint**: the module builds; no behaviour yet.

## Phase 3: User Story 1 - The flames show how well I play (P1) MVP

**Goal**: flames burn at the bottom of the picture, their height follows the share of the maximum (embers at 50 % or less, full at 100 %, linear), they start as embers and wait for 4 judged events, they rise in about 0.5 s and fall in about 3 s (FR-001 to FR-004, FR-006, SC-001, SC-003).

**Independent Test**: a recorded run at low, medium and high success gives ordered heights; the same run with the setting off is identical in everything else (SC-004, SC-005).

- [ ] T006 [P] [US1] Write `TST/arcade/view/FlameSceneTest.java` first (red): for the three tiers and the three settings, the number of tongues and layers (`FULL` 28 tongues of 3 layers plus embers, `BALANCED` 14 of 2 layers, `PERFORMANCE` none, `DISCREET` half the tongues at 60 % of the height and 60 % of the opacity of `FULL`); at level 0 only embers; the maximum height of the tongues is ordered and proportional for levels 0, 0.25, 0.5, 0.75, 1; `OFF` or a null state gives exactly the item list of the scene without the feature (SC-005); the same inputs give the same items and a different instant changes only the sway (R9); every flame item is **before** every other item of the list (contracts/arcade-flames.md invariants 1 to 4, 6).
- [ ] T007 [P] [US1] Write `TST/arcade/ui/FlameLevelTest.java` first (red): fewer than 4 judged events: target 0; mapping `clamp((share - 0.5) / 0.5, 0, 1)` (share 0.5 gives 0, 0.8 gives 0.6, 1.0 gives 1); from 0 to 1 in about 0.5 s (rate 2 per second); from 1 to 0 in about 3 s (rate 1/3 per second); never a step larger than the rate times the time elapsed (SC-003); `hold` keeps the level and `resume` goes on without a jump; a new `FlameLevel` starts at 0 (invariant 7).
- [ ] T008 [US1] Implement `SRC/arcade/ui/FlameLevel.java` (UI thread only): `update(double shareOfTheMaximum, int judgedEvents, long wallNanos)`, `level()`, `hold(long)`, `resume(long)` as in `contracts/arcade-flames.md` and `research.md` R4 (rise 2 per second, fall 1/3 per second, target 0 until 4 judged events); make `FlameLevelTest` green.
- [ ] T009 [US1] Implement the flames in `SRC/arcade/view/ArcadeScene.java`: a `build(long nowNanos, Viewport viewport, ArcadeRun.View run, int speedPercent, QualityTier tier, FlameState flames)` overload (the existing `build` keeps working and equals `build(..., null)`), and `drawFlames` called **first** so every flame item precedes the others: tongues as tapering filled quads in three layers (dark red outer, orange middle, yellow core, each smaller and more opaque), embers and a base glow as discs, height and brightness from the level and the weight `0.22 + 0.78 * ((|x - centre| / half width)^2)` across the width, sway from `instantNanos` and the tongue index with fixed phases (no random source); counts by tier and setting as in `research.md` R2 and R5; make `FlameSceneTest` green and run `ArcadeReferenceSceneTest` (must stay unchanged).
- [ ] T010 [US1] Wire the level in `SRC/arcade/ui/ArcadeFrameSource.java`: it owns a `FlameLevel` and an `ArcadeScoring`, computes `share = score / scoring.maximum(judged)` from the `ArcadeRun.View` it already gets (0 when `maximum` is 0), updates the level with the wall time of each picture, and passes `new FlameState(setting, level, pictureNanos)` to the scene; `setPaused(true, ...)` calls `hold`, `setPaused(false, ...)` calls `resume`; `SRC/arcade/ui/ArcadeSession.java` gives it the setting and `ArcadeScoring.standard()`; add to `TST/arcade/ui/ArcadeFrameSourceTest.java` a test that the level holds in a pause and goes on without a jump.
- [ ] T011 [US1] In `SRC/ui/PracticeWindow.java` (`startArcade`) read `FollowSettings.getInstance(this.context).getFlames()` when a run starts and hand it to `ArcadeSession.of(...)` with the other picture settings (the settings UI is T022); full module tests green.

**Checkpoint**: with the default setting (`DISCREET`) the flames burn behind the Arcade run and follow the play; the module builds and the existing tests keep their outcome.

## Phase 4: User Story 2 - Reading is never hindered (P1)

**Goal**: with the flames at full level every text and every game item stays readable, at any window shape; flames are decoration and go first (FR-005, FR-007, SC-002).

**Independent Test**: a painted-pixel test on seven window shapes measures the contrast of every text and finds no cover; the tiers and the fallback renderer draw no flame at the lowest tier.

- [ ] T012 [P] [US2] Write `TST/arcade/view/FlameContrastTest.java` first (red), with the recording painter `TST/arcade/view/Java2dPainter.java` and the shapes of `HudVisibilityTest` (1280x720, 800x600, 640x480, 1500x500, 2000x1000, 1100x950, 900x400): with the flames at level 1 and `FULL`, for every text item of the picture (string labels, fret numbers, verdict badge, HUD) the contrast ratio against what is painted behind it is at least 4.5 to 1, and no flame item meets a text box with an opacity above the guard value (12 %); flames stay inside the picture (SC-002, invariant 5).
- [ ] T013 [US2] Implement the reading guard in `SRC/arcade/view/ArcadeScene.java`: the boxes of the texts of the picture (the ones `hudBoxes`/`plan` already compute, plus the string labels and the fret numbers under the neck and the verdict badge) fade every tongue that meets them to at most 12 % of its opacity near the box (a fade, not a hard edge); the yellow core is never used in the central weak zone; make `FlameContrastTest` green on the seven shapes.
- [ ] T014 [P] [US2] Add tests to `TST/arcade/view/ArcadeSceneQualityTest.java` and `TST/arcade/ui/FallbackRendererTest.java`: at tier `PERFORMANCE` no flame item exists; the fallback renderer paints no `FLAME_TONGUE` or `FLAME_EMBER` (decoration); `BALANCED` draws fewer tongues than `FULL` (FR-007, invariants 2 and 3).
- [ ] T015 [US2] Add one reference scene with the flames at full level, `FULL`, level 1 and a fixed instant, to `TST/arcade/view/ReferenceScenes.java` and regenerate **only that new** `TST/arcade/reference/*.scene.txt` with `-Dtuxguitar.arcade.reference.write=true` (the existing references must not change: `git diff` on them is empty), then make `ArcadeReferenceSceneTest` green.

**Checkpoint**: reading is proven on seven window shapes and at every tier.

## Phase 5: User Story 3 - The flames fall back gently (P2)

**Goal**: the flames lose height progressively over a few seconds when the share falls and rise again when it rises, never in one frame, and hold in a pause (FR-004, FR-010, SC-003).

**Independent Test**: feed a drop of the share and record the level over time: smooth within the stated time, never one frame; a pause holds the level and the motion.

- [ ] T016 [P] [US3] Add to `TST/arcade/ui/FlameLevelTest.java` the scenarios of story 3: a sharp drop of the share from 1.0 to 0.4 makes the level fall over about 3 seconds and never from full to embers in one frame; a rise after a fall goes up at the rise rate without jumping; a pause of 10 seconds holds the level and a resume goes on from it (scenario 2 of story 3).
- [ ] T017 [US3] Make the motion of the tongues hold still in a pause: in `SRC/arcade/ui/ArcadeFrameSource.java` the `instantNanos` of the `FlameState` is the **picture instant** (it is frozen while the picture is paused, FR-010) and never the player's clock in a way that moves an event; add the test to `TST/arcade/ui/ArcadeFrameSourceTest.java` (two frames in a pause give the same flame items).

## Phase 6: User Story 4 - I can set or switch off the flames (P2)

**Goal**: one setting with three states in the *Mode* tab, texts in English, French and Spanish, read when a run starts, back to the default when the application is opened again (FR-008, FR-009).

**Independent Test**: for each of the three states start a run and check the picture; the texts exist in the three languages.

- [ ] T018 [P] [US4] Write the language test first: extend `TST/ui/FollowStringsTest.java` so that the keys `arcade.flames.label`, `arcade.flames.off`, `arcade.flames.discreet` and `arcade.flames.full` exist and are not empty in `LANG/messages.properties`, `messages_fr.properties` and `messages_es.properties` (red).
- [ ] T019 [P] [US4] Add the four keys with their English, French and Spanish texts to `LANG/messages.properties`, `LANG/messages_fr.properties` and `LANG/messages_es.properties` (for example "Stage flames", "Off", "Discreet", "Full"; "Flammes de scène", "Désactivées", "Discrètes", "Pleines"; "Llamas de escenario", "Desactivadas", "Discretas", "Plenas"); make T018 green.
- [ ] T020 [US4] Write `TST/ui/FollowPanelTest.java` additions first (red): the panel offers one control for the flames with three entries (off, discreet, full), shows the setting of `FollowSettings`, and writes a change back to `FollowSettings.setFlames`.
- [ ] T021 [US4] Add the control to `SRC/ui/FollowPanel.java` under the tilt settings (the rows after the horizon spinner, same layout style, a read-only combo box with the three texts), shown with the other picture settings of the Arcade mode, and make T020 green; add a native check to `TST/ui/FollowPanelSwtTest.java` that the control exists and keeps its choice.
- [ ] T022 [US4] Check end to end in `TST/arcade/ui/` (a test of `ArcadeSession`/`ArcadeFrameSource`): the setting `OFF` gives a scene with no flame item, `DISCREET` fewer and fainter than `FULL`; and that a setting changed in the panel is used by the **next** run, not by the run in progress (read when a run starts, FR-008).

## Phase 7: Polish and cross-cutting

- [ ] T023 [P] Write `TST/arcade/view/FlameParityTest.java`: the judging does not depend on the flames: (a) a source check that no file under `SRC/arcade/core/` mentions `Flame`; (b) the same recorded run replayed through `ArcadeRun` gives identical verdicts, score, streak and result whatever the `FlameState` given to the scene (invariant 8, SC-004).
- [ ] T024 Add to `TST/arcade/stress/StressRun.java` a `Config.flames` (default `OFF`) given to the frame source, and a case in `TST/arcade/stress/ArcadeStressTest.java` that the **digest of the judgements** of the run is identical with `OFF` and with `FULL` (tag `benchmark`, as the existing cases).
- [ ] T025 [P] Add to `TST/arcade/ui/ArcadeCanvasSwtTest.java` (profile `swt-gui-tests`) a native paint of the flame scene in each tier on the real painter without exception, and that nothing is drawn at `PERFORMANCE`.
- [ ] T026 Measure the cost (SC-006): run the ten-minute stress run of spec 003 (`mvn test -pl ../../TuxGuitar-audio-engine -am -pl ../../TuxGuitar-practice -Dgroups=benchmark -DexcludedGroups= -Dtest=ArcadeStressTest ...`) with the flames off and at `FULL`; write both results in `specs/009-arcade-flames/benchmark/cost-2026-10-14.txt` (frames lost, ratio); the flames at `FULL` must not lose more than 1 % more frames than without; if they do, reduce the tongue counts of `FULL` and `BALANCED` (parameters of `drawFlames`) and measure again, and say so.
- [ ] T027 [P] Documentation (FR-012): add a paragraph on the stage flames (what they show, the three states, that they never change the judging, the default discreet) to `docs/MANUAL.md` (B6, the Arcade section), `docs/MANUAL-FR.md`, `docs/PRACTICE.md`, `docs/PRACTICE-FR.md` and `docs/PRACTICE-ES.md`, and to the picture settings list of the manuals.
- [ ] T028 Full local gate: `mvn -e clean verify -P native-modules,swt-gui-tests` green with the same counts as in `before-after.txt` plus the new tests (reported exactly), `mvn test -pl ../../TuxGuitar-practice -am` green, `git status` clean except the untracked `tests/`; `git diff --stat -- 'desktop/TuxGuitar-practice/src/**/arcade/core/*'` empty.
- [ ] T029 Relaunch the built application for the owner (`target/tuxguitar-9.99-SNAPSHOT-macosx-swt-cocoa.app`, with the `jre` symlink as in `CLAUDE.md`), starting from the demo score, and ask what the owner sees.
- [ ] T030 [HUMAN] **Gate G2** (on the built application, `quickstart.md` manual steps 2 to 5): play at a high level of success then miss on purpose (embers at 50 % or less, full at 100 %, fall over about 3 s); reading with the flames at full level and at several window shapes; the three states of the setting and that the choice holds until the application is closed; a pause and a resume; the owner judges the **motion** and the look against `mockups/A2-full.png`; the owner's answer is written in `quickstart.md` and the tasks.
- [ ] T031 Update `specs/STATUS.md` (a row for 009), `specs/009-arcade-flames/checklists/requirements.md` (what changed state), and, when a release is made, `docs/release-notes/` (the flames, honest about what was checked and by whom).

## Dependencies and order

- Phase 1, then Phase 2 (T003, T004, T005 parallel), then US1 (T006 and T007 before T008 to T011), then US2 (T012 before T013; T014 and T015 after T009), then US3 and US4 (independent of each other after US1), then Polish. T009 needs T003 and T005; T010 needs T004, T005, T008, T009; T011 needs T004 and T010; T013 needs T009; T017 needs T010; T021 needs T004 and T019; T022 needs T011 and T021; T023 to T026 need T011; T028 needs everything before it; T030 (G2) needs T029.
- Tests are written first and seen red: T006 and T007 before T008 to T010; T012 before T013; T016 before T017; T018 before T019; T020 before T021.

## Parallel examples

- After Phase 2: T006 || T007; then T008 (levels) and T009 (scene) in parallel (different files) before T010.
- In US4: T018 || T020 (different test files), then T019 || T021 (lang files and the panel).
- In Polish: T023 || T025 || T027.

## Implementation strategy

- **MVP = US1** (T001 to T011): flames that follow the play, with the default setting, behind the picture; then US2 (reading proven) before anything is shown to the owner as a feature, then US3 and US4, then the checks, the cost and G2.
- Nothing is called done until its test ran green; the stress and cost results are reported exactly as measured; the mockups are static, so the motion and the look are judged only at G2.
- The whole Spec Kit method follows this step: `/speckit-checklist`, `/speckit-analyze`, then `/speckit-implement`, then `/speckit-converge`.
