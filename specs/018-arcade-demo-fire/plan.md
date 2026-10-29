# Implementation Plan: Arcade's flames drawn like the classic fire of the Amiga demos, translucent

**Branch**: `feature/interactive-practice` (spec directory `018-arcade-demo-fire`) | **Date**: 2026-10-30 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/018-arcade-demo-fire/spec.md`

## Summary

Spec 009's flames are vector tongues built by `arcade/view/FlameDrawing.java` as scene items (`SceneItem` QUAD strips with role `FLAME_TONGUE`, discs with role `FLAME_EMBER`), put before every other item by `ArcadeScene`, painted by `ui/UiPainterRenderer`. The plan replaces the tongues by a **coarse heat field** of big square cells, computed **without history** from the flame state of the picture (level, setting, flame clock, motion), so the scene stays a pure function of its inputs (tests, FR-011); the field is drawn as run-merged square QUAD items, coloured from dark red to white and **at most 60 % opaque**, nothing where the heat is nil; **sparks** are small square items rising from the fire for about one second after each right note, given to the scene through the flame state by `ArcadeFrameSource`. Everything spec 009 decided about what the flames show is kept and reused (`FlameLevel`, `weight`, `faded`, tiers, setting). Details: [research.md](research.md).

## Technical Context

**Language/Version**: Java, release 11 (`TuxGuitar-practice`), JDK 21 locally

**Primary Dependencies**: the Arcade scene model and renderer of the plugin; no new dependency

**Storage**: none (the flame setting is spec 009's, unchanged)

**Testing**: JUnit 5 in `desktop/TuxGuitar-practice/test` (scene tests, contrast test, reference scene, parity test); the owner's look gate on the built application

**Target Platform**: macOS Apple Silicon validated; Linux and Windows by CI only

**Project Type**: desktop application plugin

**Performance Goals**: at FULL, at most about 2 500 flame items per picture at 1280×720 (run merging); the existing `QualityController` keeps the frame budget (SC-007)

**Constraints**: no existing test modified except the owner's targeted exception of 2026-10-30 (the tongue-shape assertions, listed in research R8 and line by line in the commit); no change under `arcade/core` (`FlameParityTest`); verdicts identical

**Scale/Scope**: `FlameDrawing` rewritten (fire field, colour scale, sparks), `FlameState` extended with the recent bursts, `ArcadeFrameSource` records the bursts; texts of the guides

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status |
|---|---|
| I. Compatibility | Pass: Arcade only; the setting and its stored values unchanged. |
| II. Extend before modifying core | Pass: only the fork's plugin. |
| III. Evidence-based | Pass: research cites `FlameDrawing`, `FlameState`, `ArcadeFrameSource.flamesAt`, `UiPainterRenderer` QUAD path, `ArcadeRun.View.streak/states`, and the existing tests. |
| IV. Spec-driven | Pass: specify, clarify, and the owner's answer on tests during planning, recorded in the spec. |
| V. Incremental | Pass: story 1+2 (fire, translucent) shippable without sparks; story 3 adds sparks. |
| VI. Real-time | Pass: UI thread only; nothing on the audio path. |
| VII. Separable logic | Pass: the field and the sparks are pure functions tested without the UI. |
| VIII. Deterministic | Pass: a seeded hash noise, no random generator with state; same inputs, same picture. |
| IX. macOS first | Pass; other platforms by CI. |
| X. No unrelated refactoring | Pass: only the flame drawing and the flame state change. |
| XI. Build and test integrity | Pass with the owner's recorded exception (spec Clarifications, research R8); every rule of spec 009 keeps a test. |
| XII. Truthful reporting | Pass: the look and the frame rate are judged on the built application by the owner (SC-001, SC-007). |
| XIII. Honest audio semantics | Not concerned. |
| XIV. Learner-centred feedback | Pass: decoration only; reading first. |

Post-design re-check (after Phase 1): unchanged, all pass. No complexity to justify.

## Project Structure

### Documentation (this feature)

```text
specs/018-arcade-demo-fire/
├── spec.md, plan.md, research.md, data-model.md, quickstart.md
├── contracts/fire-drawing.md
├── checklists/requirements.md
└── tasks.md             # /speckit-tasks
```

### Source Code (repository root)

```text
desktop/TuxGuitar-practice/src/app/tuxguitar/app/tools/custom/practice/arcade/
├── view/FireField.java        # new, pure: heat of each cell from level, setting, clock, motion, viewport
├── view/FireColours.java      # new, pure: heat → colour and opacity (≤ 0.60, nil → nothing)
├── view/FlameDrawing.java     # the tongues replaced by the field's run-merged cells and the sparks; weight/faded kept
├── view/FlameState.java       # + the recent bursts (flame-clock time, streak) — additive constructor
└── ui/ArcadeFrameSource.java  # records a burst at each new right note (from ArcadeRun.View states and streak)
desktop/TuxGuitar-practice/test/app/tuxguitar/app/tools/custom/practice/arcade/view/
├── FireFieldTest.java, FireColoursTest.java, FlameSparksTest.java   # new
└── (FlameSceneTest, ArcadeSceneQualityTest, ArcadeFlamesSettingTest, reference/flame-reading.scene.txt: owner's exception, R8)
docs/PRACTICE.md, PRACTICE-FR.md, PRACTICE-ES.md, specs/test-sheets/F06-flammes-arcade.md
```

**Structure Decision**: inside the existing Arcade view package, beside `FlameDrawing`.

## Phases

1. **Baseline** (no code): default and SWT suite counts; item counts and frame cost of today's flames on the reference scene (for SC-007).
2. **Story 1+2 — the fire, translucent** (P1): `FireColours`, `FireField`, `FlameDrawing` drawing the cells; the new tests first; then the exception edits of R8, listed line by line.
3. **Story 3 — sparks** (P2): bursts in `FlameState`, recorded by `ArcadeFrameSource`, drawn by `FlameDrawing`; removed at BALANCED, off, reduced motion.
4. **Closing**: guides and test sheet F06 in three languages, full gate, relaunch the application, **gate [HUMAN]**: the owner judges the look and the smoothness (SC-001, SC-007); values (cell size, opacity ≤ 60 %, colours) tuned there.

## Complexity Tracking

No constitution violation (the test exception is the owner's, recorded).
