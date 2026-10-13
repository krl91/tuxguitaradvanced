# Implementation Plan: Arcade stage flames

**Branch**: `feature/interactive-practice` (spec dir `009-arcade-flames`) | **Date**: 2026-10-14 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification `specs/009-arcade-flames/spec.md` (with its Clarifications of 2026-10-14).

## Summary

Draw flames behind the Arcade run, rising from the bottom of the picture, with a height that follows the share of the maximum (embers at 50 % or less, full at 100 %, linear), a smoothed level (fall about 3 s, rise about 0.5 s), a three-state setting (off, discreet, full; discreet by default) and a guard that keeps every text readable. The effect is **pure decoration made of the shapes the scene already has** (filled quadrilaterals and discs with transparency): no new painter primitive, no change to the judging, the score or the clock. The scene stays a pure function: the flame level and the flicker instant are **inputs** of `ArcadeScene.build`, the smoothed level lives in the picture's own clock object (`ArcadeFrameSource`), and the new roles are decoration, so the quality tiers and the fallback renderer remove them first with the existing rule.

## Technical Context

**Language/Version**: Java, module `desktop/TuxGuitar-practice` at release 11 (the fork's modules); no new dependency.

**Primary Dependencies**: none new. Uses `ArcadeScene` / `SceneItem` / `QualityTier` (`arcade/view`), `UiPainterRenderer` (`arcade/ui`), `ArcadeFrameSource`, `ArcadeRun.View` and `ArcadeScoring` (`arcade/core`), `FollowSettings` and `FollowPanel` (`session`, `ui`), the language files `desktop/TuxGuitar-practice/share/lang/messages{,_fr,_es}.properties`.

**Storage**: none (the setting lives for the session of the application, like the tilt and the size of the rectangles).

**Testing**: JUnit 5 in `desktop/TuxGuitar-practice/test`: pure scene tests, a painted-pixel contrast test on the recording painter, a level (fall and rise) test, a parity test of the judging, reference scenes, native SWT paint tests (profile `swt-gui-tests`), the stress run (tag `benchmark`).

**Target Platform**: macOS Apple Silicon first (checked by the owner); Windows and Linux are built by the existing workflow and not claimed as validated (constitution IX).

**Project Type**: desktop application (the Arcade picture).

**Performance Goals**: with the flames at full level the game loses no more than 1 % more frames than without (SC-006); `BALANCED` draws fewer tongues, `PERFORMANCE` and the fallback renderer draw none.

**Constraints**: FR-053 to FR-055 and FR-080 of spec 003 (decoration first to go, no queue of frames, nothing cut at any window size), FR-021 (no number of score during play), no state between frames in `ArcadeScene` (its rule since spec 003).

**Scale/Scope**: about 100 scene items at the full tier, 28 at balanced, 0 at performance; one new role family, one new settings field, one new combo box, four texts in three languages.

## Constitution Check

*GATE: passed before research; re-checked after design.*

| Principle | Check |
|---|---|
| I Preserve compatibility | Additive: a setting and decoration; with the setting off the picture is item-for-item the same (SC-005). No file format or core change. |
| II Extend before modifying core | Only the fork's module `TuxGuitar-practice` changes; nothing in `desktop/TuxGuitar` or `common/`. |
| III Evidence-based | Every touch-point is a file that was opened (see `research.md`); the facts that corrected the spec are recorded (R6, R7). |
| IV Spec-driven | Spec, clarify, then this plan; tasks, checklist, analysis, implement and converge follow, in that order, with the owner told at each step. |
| V Incremental | Five increments with gates (below). |
| VI Real-time | The flames are drawn on the UI thread inside the existing paint; no work on the audio or the analysis threads; the judging is not read or changed. |
| VII Separable, testable | The geometry is a pure function of (level, instant, viewport, tier, setting); the level is a small class testable without a display. |
| IX Cross-platform | macOS validated by the owner; other platforms reported as not looked at. |
| X No unrelated refactoring | Nothing outside the flames. |
| XI Build and test integrity | Full macOS build with the same counts plus the new tests before and after. |
| XII Truthful reporting | Mockups are static images, said so; results reported as run. |

*Post-design re-check*: no violation; no entry in Complexity Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/009-arcade-flames/
├── spec.md
├── plan.md                  # this file
├── research.md              # decisions R1 to R9
├── data-model.md            # setting, level, flame state, tongue
├── contracts/arcade-flames.md   # the surface added to the scene, the level and the settings
├── quickstart.md            # how to see and check the flames
├── mockups/                 # A-pied-du-manche (chosen, first proposal), A2-full, A2-discreet, A2-mouvement-coin-gauche (this plan), B and C (not chosen)
├── checklists/requirements.md
└── tasks.md                 # created by /speckit-tasks
```

### Source (what is added or changed in the module)

```text
desktop/TuxGuitar-practice/src/app/tuxguitar/app/tools/custom/practice/
├── arcade/view/SceneItem.java            # + roles FLAME_TONGUE, FLAME_EMBER (decoration)
├── arcade/view/FlameState.java           # new: level, setting, flicker instant (input of the scene)
├── arcade/view/ArcadeScene.java          # + drawFlames (first, behind everything), build(..., FlameState)
├── arcade/ui/FlameLevel.java             # new: picture-only smoothed level (rise, fall, hold, 4-event rule)
├── arcade/ui/ArcadeFrameSource.java      # owns the FlameLevel, passes the FlameState to the scene, holds it in a pause
├── arcade/ui/ArcadeSession.java          # passes the setting and the scoring to the frame source
├── session/FollowSettings.java           # + flames setting (OFF, DISCREET, FULL; default DISCREET)
├── ui/FollowPanel.java                   # + one combo box under the tilt settings
└── ui/PracticeWindow.java                # reads the setting when a run starts
desktop/TuxGuitar-practice/share/lang/messages{,_fr,_es}.properties   # arcade.flames.label, .off, .discreet, .full
desktop/TuxGuitar-practice/test/.../arcade/{view,ui}/                 # FlameSceneTest, FlameLevelTest, FlameContrastTest, FlameParityTest, native paint test, reference scene
docs/MANUAL.md, docs/MANUAL-FR.md, docs/PRACTICE{,-FR,-ES}.md          # FR-012
```

**Structure Decision**: the geometry in `arcade/view` (pure), the level and the clock in `arcade/ui`, the setting in `session` with the other picture settings; nothing in `arcade/core`, so the judging cannot depend on the flames.

## Increments

* **F0 - Fix the spec** (before any code): two statements of the spec do not match the code (`research.md` R6, R7): the share is 0, not 100 %, when nothing was judged, and the buttons are outside the picture. Run `/speckit-clarify` to correct FR-001 and FR-003 and the scenario 2 of story 1. **Gate G1: the owner looks at the mockups of this plan (`A2-full.png`, `A2-discreet.png`, `A2-mouvement-coin-gauche.png`) and approves the shape, the colours and what "discreet" is.**
* **F1 - The scene (pure).** Roles, `FlameState`, `drawFlames` (tongues, embers, reading guard, tiers), pure tests and the reference scene.
* **F2 - The level.** `FlameLevel` (mapping, rise and fall, 4-event rule, hold in a pause) and its wiring in `ArcadeFrameSource`, with tests.
* **F3 - The setting.** `FollowSettings`, the combo box, the three languages, read at the start of a run.
* **F4 - Checks.** Painted contrast test (several window sizes), parity of the judging with flames on and off (also on the stress run), native paint test, stress run for the cost. **Gate G2: the owner looks at the built application.**
* **F5 - Documentation.** Manuals and guides in three languages, `STATUS.md`, release notes when a release is made.

## Risks

* The reading guard must hold at every window shape: it is a function of the boxes of the texts of the picture, and is tested on seven shapes (the list of `HudVisibilityTest`).
* About 100 filled items per frame may cost on the macOS painter: measured by the stress run; the tongue counts are parameters, and the tiers already reduce them.
* The flicker must be the same for the same instant (deterministic): it is a function of the picture instant, never of a random number source.
* Static mockups do not show motion: the owner judges the motion on the built application (G2).

## Complexity Tracking

None.
