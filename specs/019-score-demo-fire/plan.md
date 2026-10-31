# Implementation Plan: The demo fire at the bottom of the score in Wait and Follow, and an Advanced tab to tune both fires

**Branch**: `feature/interactive-practice` (spec directory `019-score-demo-fire`) | **Date**: 2026-10-31 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/019-score-demo-fire/spec.md`

## Summary

The fire of spec 018 is drawn today only in Arcade.
- `arcade/view/FlameDrawing.items` builds the scene items from `FireField` (the heat of each big pixel) and `FireColours` (heat to colour and opacity).
- `arcade/ui/UiPainterRenderer` paints them in batches.

The plan reuses that drawing for the score in four steps.

1. **Every value that shapes the fire is moved into a tuning.** It is an immutable `FireTuning` read from a catalogue of `FireParameter`s. Its defaults are today's constants, so with the defaults every picture is identical. Two sets are kept, one for Arcade and one for the score. They are saved in a small configuration module of their own, the way `ArcadeChoices` and `FollowChoices` already save their values.
2. **The score fire is a second paint listener on the score canvas.** The plugin adds it to the canvas of the selected `TGControl`. The toolkit calls paint listeners in order on the same painter (`UIPaintListenerManager.onPaint`), so the fire is painted *after* `TGBufferedPainterLocked` has copied its buffer to the screen. It never enters the buffer. That matters because in Follow the buffer is painted incrementally (`TGControl.paintTablature`, lines ~187–220), so a fire drawn into it would pile up.
3. **The fire's height follows a streak counter.** The counter is pure and is fed by the snapshots the practice window already receives in `render` and `renderFollow`. It grows and falls through `FlameLevel`, which is reused with the score's tuning.
4. **The fire is animated by a timer at about 30 frames per second** that redraws the score canvas.
   - In Follow, a redraw repaints only the played beat, so it is cheap.
   - In Wait, the player is stopped and each redraw repaints the whole visible score. Phase 1 measures that cost. Only if it is too high (research R2) does a small, additive core hook let a redraw reuse the score's buffer and repaint the fire alone.

Three more pieces complete the feature:
- **Advanced tab:** a new tab of the practice window holds both groups of settings. Each setting is a row with a slider and a tooltip, and each group has a live preview.
- **Reduce motion:** read on Linux and Windows too, through a new reader. The macOS reader of spec 009 is kept unchanged.
- **Score fire setting:** Off, Discreet or Full, in the Mode tab, and saved.

Details: [research.md](research.md).

## Technical Context

**Language/Version**:
- Java release 11 (`TuxGuitar-practice`), JDK 21 locally.
- Java release 9 for `desktop/TuxGuitar` if the conditional core hook of R2 is needed.

**Primary Dependencies**:
- the Arcade scene model and renderer (`arcade/view`, `arcade/ui`);
- the UI toolkit (`UICanvas.addPaintListener`, `UIFactory.createHorizontalScale`, `createColorChooser`, `createCanvas`, `createScrollBarPanel`, `setToolTipText`);
- `TGConfigManager`.

No new library.

**Storage**: `TGConfigManager` module `tuxguitar-practice-fire`, with keys `arcade.<parameter>`, `score.<parameter>` and `score.setting` (research R5).

**Testing**:
- JUnit 5 in `desktop/TuxGuitar-practice/test`: pure tests of the tuning, the catalogue, the streak, the level, the guard and the extremes; identity of the pictures with the defaults.
- `-P swt-gui-tests` for the overlay listener and the Advanced tab on the real toolkit.
- The owner's look gate on the built application.

**Target Platform**: macOS Apple Silicon validated by the owner; Linux and Windows built by CI and checked by testers (owner's rule, memory `all-os-feature-parity`).

**Project Type**: desktop application plugin.

**Performance Goals**:
- the score scrolls as smoothly with the fire at Full as without it (SC-006);
- painting the fire on the score costs at most 4 ms per frame at FULL;
- the score fire has its own `QualityController` with an 8 ms budget, so it falls to BALANCED and then PERFORMANCE (no fire) before the practice suffers (FR-010);
- a preview reacts within 0.1 s to a setting (FR-020).

**Constraints**:
- no existing test modified (owner's rule): every constant and every existing signature stays, and the old entry points use the Arcade defaults;
- with the defaults, `flame-reading.scene.txt` is byte-identical (SC-010);
- the reading guard (`WIDEN`, `NEAR_TEXT`, `RAMP`) is fixed (FR-016);
- the verdicts are untouched (FR-008);
- no change under `arcade/core` (`FlameParityTest`).

**Scale/Scope**:
- about 45 parameters per group (47 rows of the data-model catalogue, five of them colours);
- 8 new classes in the plugin, 4 classes of the fire given a tuning, a new tab;
- one optional core hook of about 20 lines in `desktop/TuxGuitar` (R2);
- texts in three languages; the guides; the manual sheet F11.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status |
|---|---|
| I. Compatibility | **Pass.** The upstream score drawing is unchanged when the fire is off (no listener is added). Arcade's stored settings are unchanged, and the new values live in a separate module. |
| II. Extend before modifying core | **Pass, with one conditional change.** The overlay uses the toolkit's existing paint listeners, with no core change. The optional hook of R2 is added only if Phase 1 measures a full Wait repaint above the threshold. It is justified by the inspected source: `TGBufferedPainterLocked.paintBufferLocked` always calls `fillPaintBuffer()`, which repaints the whole tablature while the player is stopped (`TGControl.paintTablature`, the `!isPlaying` branch). It is additive: nothing changes unless the plugin asks for it. |
| III. Evidence-based | **Pass.** Research cites `TGBufferedPainterLocked`, `UIPaintListenerManager`, `SWTPaintListenerManager`, `TGControl.paintTablature`/`redraw`, `TGExternalNoteFramePainter.frameBounds`, `ScoreBoxes`, `PracticeWindow.render`/`renderFollow`, `FireField`, `FireColours`, `FlameDrawing`, `FlameLevel`, `ReducedMotion`/`ReducedMotionTest`, `FollowChoices` and `ArcadeChoices`. |
| IV. Spec-driven | **Pass.** Specify, then clarify (2026-10-31, five answers), then this plan. |
| V. Incremental | **Pass.** Each phase is shippable: (1) the tuning with identical pictures; (2) the score fire, US1; (3) sparks, US2; (4) the Advanced tab, US4; (5) saving, parity and Reduce motion, US3. |
| VI. Real-time | **Pass.** The UI thread only, nothing on the audio path; the streak reads snapshots that have already been judged. |
| VII. Separable logic | **Pass.** The streak, the level, the tuning, the catalogue, clamping, the guard boxes and the Reduce-motion parsing are pure and tested without the UI. |
| VIII. Deterministic | **Pass.** The fixed seed and the hash noise are kept; same inputs and same tuning give the same items. |
| IX. Cross-platform | **Pass.** The toolkit only; Reduce motion is read on all three systems; Linux and Windows are checked by CI and testers. |
| X. No unrelated refactoring | **Pass.** Only the fire classes gain a tuning; everything else is new code. |
| XI. Build and test integrity | **Pass.** Existing tests are unchanged: `ReducedMotionTest` still pins the macOS reader, and the new reader is a new class (R7). The reference scene is identical with the defaults. |
| XII. Truthful reporting | **Pass.** Smoothness and look are judged by the owner on the built application (SC-001, SC-006). Linux and Windows are reported as unverified until testers answer. |
| XIII. Honest audio semantics | Not concerned. |
| XIV. Learner-centred feedback | **Pass.** The fire is decoration only, off by default; reading comes first, guaranteed by the fixed guard. |

Post-design re-check (after Phase 1): unchanged, all pass. The only item to watch is the conditional core hook, decided by measurement in Phase 1 and recorded in research R2.

## Project Structure

### Documentation (this feature)

```text
specs/019-score-demo-fire/
├── spec.md, plan.md, research.md, data-model.md, quickstart.md
├── contracts/score-overlay.md, contracts/fire-tuning.md, contracts/advanced-tab.md
├── checklists/requirements.md
├── baseline.txt         # Phase 1 measures
└── tasks.md             # /speckit-tasks
```

### Source Code (repository root)

```text
desktop/TuxGuitar-practice/src/app/tuxguitar/app/tools/custom/practice/
├── arcade/view/FireParameter.java    # new: the catalogue (key, kind, min, max, Arcade default, score default, text keys)
├── arcade/view/FireTuning.java       # new: immutable values, ARCADE_DEFAULTS, SCORE_DEFAULTS, clamped
├── arcade/view/FireField.java        # + constructor taking a FireTuning; the old one uses ARCADE_DEFAULTS
├── arcade/view/FireColours.java      # + colour/alpha taking a FireTuning; the old ones unchanged
├── arcade/view/FlameDrawing.java     # + items(..., tuning, guard); sparks from a start height; the old items(...) unchanged
├── arcade/view/FlameState.java       # + optional FireTuning (additive constructor), ARCADE_DEFAULTS when absent
├── arcade/view/ScoreFireScene.java   # new, public: the scene of the score fire (viewport, tier, flames, tuning, guard boxes)
├── arcade/ui/FlameLevel.java         # + constructor taking rise, fall, min judged and max step; the default one unchanged
├── arcade/ui/ArcadeFrameSource.java  # uses the Arcade tuning (spark life, level); defaults unchanged
├── arcade/ui/SystemReducedMotion.java # new: macOS (delegates to ReducedMotion), GNOME, KDE, Windows; cached, asynchronous on Windows
├── session/FireTuningStore.java      # new: TGConfigManager module "tuxguitar-practice-fire"; score setting; never throws
├── ui/ScoreStreak.java               # new, pure: the streak and the bursts from Wait and Follow snapshots
├── ui/ScoreFire.java                 # new: listener on the score canvas, timer, clock, guard boxes, quality, start/stop
├── ui/FireSettingsPanel.java         # new: the Advanced tab (two groups, rows, tooltips, defaults buttons)
├── ui/FirePreview.java               # new: the animated preview of a group (canvas, Sparks button, growth slider)
├── ui/FollowPanel.java               # + the "Score fire" choice in the Mode tab
└── ui/PracticeWindow.java            # + the Advanced tab; feeds ScoreStreak and ScoreFire from render/renderFollow; stops it
desktop/TuxGuitar-practice/share/lang/messages*.properties   # texts and tooltips EN/FR/ES
desktop/TuxGuitar/src/app/tuxguitar/app/view/util/TGBufferedPainterLocked.java      # conditional (R2)
desktop/TuxGuitar/src/app/tuxguitar/app/view/component/tab/TGControl.java          # conditional (R2): redrawOverlay()
desktop/TuxGuitar-practice/test/...   # new tests only (see quickstart)
docs/PRACTICE.md, PRACTICE-FR.md, PRACTICE-ES.md, specs/test-sheets/F11-feu-partition.md
```

**Structure Decision**: the tuning and the score scene go into `arcade/view`, beside the drawing they parameterise, because `FlameDrawing` and `FireField` are package-private. The score side (listener, streak, Advanced tab) goes into `ui`, beside `PaletteGuard` and `ScoreBoxes`, which already reach the score canvas.

## Phases

1. **Baseline** (no code):
   - default and SWT suite counts;
   - item count and frame cost of the fire on the reference scene;
   - **cost of a full Wait repaint of the score** on `tests/demo-arcade-morceau.tg` at 1280×800, with the median and the 95th percentile over 100 redraws. This decides R2.
2. **Foundation, the tuning** (no visible change):
   - `FireParameter`, `FireTuning`, `FireTuningStore`;
   - the fire classes take a tuning;
   - tests first: the catalogue is complete against the constants; the defaults equal the constants; the defaults give identical pictures (`flame-reading.scene.txt`); out-of-range values are clamped; the store round-trips its values and never throws.
3. **US1, the score fire grows with the streak** (P1):
   - `ScoreStreak`, the level, `ScoreFireScene` with the guard boxes (framed note, next note, palette), `ScoreFire` (listener, timer, clock, quality);
   - the choice in the Mode tab;
   - the conditional core hook if Phase 1 asked for it.
4. **US2, sparks** (P2): bursts from the streak, rising from the bottom of the fire; the most in the air; none at BALANCED, with Off or with Reduce motion.
5. **US4, the Advanced tab** (P2): both groups, a row per parameter, tooltips in EN/FR/ES, *Default values* buttons, the previews (Sparks button, growth slider, stopped when the tab is hidden), and the settings applied at the next run.
6. **US3, kept and the same everywhere** (P3): the score setting saved, defaulting to Off; `SystemReducedMotion` for the score fire and for Arcade, with parsing tests for every system and a fake runner.
7. **Closing**:
   - guides in three languages and sheet F11;
   - full gate and SWT run;
   - relaunch the application;
   - **gate [HUMAN]**: the owner tries the score fire in Wait and Follow, and the Advanced tab (SC-001, SC-006);
   - release notes with the Windows and Linux feedback call.

## Complexity Tracking

| Item | Why needed | Simpler alternative rejected because |
|---|---|---|
| Conditional core hook in `TGBufferedPainterLocked` / `TGControl` (R2), only if measured necessary | In Wait, each animation frame would otherwise repaint the whole tablature (`fillPaintBuffer` on every paint). | Animating at a lower rate makes the fire stutter. A separate translucent window over the score behaves differently on each system (parity) and catches the pointer. Drawing into the buffer piles the fire up in Follow (incremental painting). |
