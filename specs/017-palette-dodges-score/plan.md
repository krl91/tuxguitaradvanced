# Implementation Plan: The practice palette moves aside so it never hides the note to play

**Branch**: `feature/interactive-practice` (spec directory `017-palette-dodges-score`) | **Date**: 2026-10-30 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/017-palette-dodges-score/spec.md`

## Summary

During a *Wait for the right note* or *Follow the tempo* run, the practice palette (`ui/PracticePalette.java`) floats over the main window at a fixed place and can hide the note to play. The plan gives the practice window, at each change of event and every 100 ms while a run goes, the screen box of the **current** event's notes and of the **next** one, computed with the same geometry as the red frame (`TGExternalNoteFramePainter`), and a pure decision class (`PaletteDodge`) that keeps the palette out of both boxes by jumping to one of the four corners of the score area, ahead of time, without focus, at most once per framed note, never back and forth, and never when the learner put it outside the main window. Two small additive changes outside the plugin are needed and justified (R2, R3): the frame geometry is exposed by the fork's own frame painter, and the UI toolkit gets a way to map a control's point to the screen. Details: [research.md](research.md).

## Technical Context

**Language/Version**: Java, release 11 for `TuxGuitar-practice`; release 9 kept for `desktop/TuxGuitar` and the UI toolkit modules; JDK 21 locally

**Primary Dependencies**: TuxGuitar UI toolkit (SWT on macOS), the score layout (`app.tuxguitar.graphics.control`); no new dependency

**Storage**: none (the palette's place is remembered for the session only, as today: `PracticePalette.lastBounds`)

**Testing**: JUnit 5 in `desktop/TuxGuitar-practice/test` (pure `PaletteDodge` tests, all spec scenarios); one `swt-gui` test for the move without focus; manual check on F01 (gate)

**Target Platform**: macOS Apple Silicon validated; Linux and Windows built by CI only; JavaFX and Qt toolkits: the mapping is absent, the palette keeps today's behaviour (R3)

**Project Type**: desktop application plugin (`TuxGuitar-practice`) plus two additive hooks

**Performance Goals**: the check costs a few rectangle computations every 100 ms on the UI thread; no repaint is caused by it; no effect on the audio threads

**Constraints**: no existing test modified (owner's rule); no verdict changed; Arcade and the editor outside practice unchanged; the palette never takes the focus

**Scale/Scope**: one new pure class, one new small UI glue class, changes in `PracticePalette` and `PracticeWindow`, one static method extracted in `TGExternalNoteFramePainter`, one default method in `UIControl` with its SWT implementation

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status |
|---|---|
| I. Compatibility | Pass: no file format or setting; the frame painter paints exactly as before (R2); the toolkit method is a `default` that changes nothing for existing callers. |
| II. Extend before modifying core | Pass with justification: (a) the frame box is computed only inside `desktop/TuxGuitar/.../tab/TGExternalNoteFramePainter.java` (a file added by this fork in `e5e06477a`), privately; extracting it as a public static method is the minimal change (R2); (b) the toolkit has no way to map a control's point to the screen (`UIControl`, `UIWindow`, `UILayoutContainer.getChildArea()` give parent-relative or client-relative boxes only; SWT's `Control.toDisplay` is used only inside `TuxGuitar-ui-toolkit-swt`), so one `default` method is added (R3). |
| III. Evidence-based | Pass: research cites `TGControl.paintTablature` (canvas coordinates already include the scroll), `PracticeHighlight`, `PracticeWindow`, `PracticePalette.placeWindow`. |
| IV. Spec-driven | Pass: specify, clarify (2026-10-30) done before this plan. |
| V. Incremental | Pass: story 1 (dodge) is shippable alone; story 2 (calm) and story 3 (manual drag) add rules to the same pure class, each with its tests. |
| VI. Real-time | Pass: UI thread only, nothing on the audio path. |
| VII. Separable logic | Pass: `PaletteDodge` is pure (rectangles in, a place out) and tested without the UI. |
| VIII. Deterministic | Pass: same inputs, same place; no randomness. |
| IX. macOS first | Pass: SWT implementation validated on macOS; Linux/Windows by CI; JavaFX/Qt degrade to today's behaviour (said in the report). |
| X. No unrelated refactoring | Pass: only the box computation is moved into a method, the painting code is otherwise untouched. |
| XI. Build and test integrity | Pass: baseline first; new tests only. |
| XII. Truthful reporting | Pass: the gate on F01 is the owner's (SC-004). |
| XIII. Honest audio semantics | Not concerned (no judging change). |
| XIV. Learner-centred feedback | Pass: the feedback stays readable and the note stays visible. |

Post-design re-check (after Phase 1): unchanged, all pass. Core changes listed under Complexity Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/017-palette-dodges-score/
├── spec.md
├── plan.md              # this file
├── research.md          # R1–R8
├── data-model.md
├── quickstart.md
├── contracts/palette-dodge.md
├── checklists/requirements.md
└── tasks.md             # /speckit-tasks
```

### Source Code (repository root)

```text
desktop/TuxGuitar-ui-toolkit/src/app/tuxguitar/ui/widget/UIControl.java          # + default UIPosition toDisplay(UIPosition) → null
desktop/TuxGuitar-ui-toolkit-swt/src/app/tuxguitar/ui/swt/widget/SWTControl.java  # implements it with Control.toDisplay
desktop/TuxGuitar/src/app/tuxguitar/app/view/component/tab/TGExternalNoteFramePainter.java  # + public static frameBounds(...)
desktop/TuxGuitar-practice/src/app/tuxguitar/app/tools/custom/practice/ui/
├── PaletteDodge.java        # new, pure: the decision (FR-002–FR-005, FR-007)
├── ScoreBoxes.java          # new, UI glue: canvas → screen boxes of the current and next event, score area
├── PracticePalette.java     # + moveTo without focus, manual-drag detection, place reported
└── PracticeWindow.java      # wires the check for Wait and Follow (not Arcade), `PaletteDodge.CHECK_MILLIS` (100 ms) repeat while running
desktop/TuxGuitar-practice/test/app/tuxguitar/app/tools/custom/practice/ui/
├── PaletteDodgeTest.java    # new
└── PaletteMoveSwtTest.java  # new, tag swt-gui
docs/PRACTICE.md, PRACTICE-FR.md, PRACTICE-ES.md
specs/test-sheets/F10-palette-ecartee.md   # new manual sheet of the gate
```

**Structure Decision**: the decision lives in the plugin as a pure class; only the two minimal hooks live outside it.

## Phases

1. **Baseline** (no code): default suite and SWT suite counts.
2. **Hooks**: `UIControl.toDisplay` default and SWT implementation; `TGExternalNoteFramePainter.frameBounds` extracted (painting unchanged).
3. **Story 1 — dodge** (P1): `PaletteDodge` (move when the current or next box is under the palette, choose a free corner), `ScoreBoxes`, palette move without focus, wiring in `PracticeWindow` for Wait and Follow.
4. **Story 2 — calm** (P2): heading-away rule, never back to the place left while the notes are near, least-covering place when no corner is free, one move per framed note.
5. **Story 3 — manual drag** (P3): detect the learner's move; stay there until in the way; never move a palette outside the main window; reopen at the last place.
6. **Closing**: guides in three languages, full gate, relaunch the application, **gate [HUMAN]**: the owner practises F01 and a Wait run (SC-004).

## Complexity Tracking

| Core change | Why needed | Simpler alternative rejected because |
|---|---|---|
| `UIControl.toDisplay` default method + SWT implementation | The palette is a top-level window placed in screen coordinates; the score is drawn in canvas coordinates; no toolkit method maps one to the other (inspected `UIControl`, `UIWindow`, `UILayoutContainer`) | Summing parent bounds misses the window's title bar and borders (the window's bounds include them, its children are relative to the client area); using SWT from the plugin would tie the plugin to SWT (Constitution IX) |
| `TGExternalNoteFramePainter.frameBounds` public static | The only code that knows where the red frame is; the plugin must protect the same box, and the next event's box, which is not painted | Re-implementing the geometry in the plugin would duplicate fork code that can drift from the painted frame |
