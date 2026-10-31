# Implementation Plan: The Arcade view keeps its dark design in every system appearance

**Branch**: `feature/interactive-practice` (no branch of its own) | **Date**: 2026-10-31 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/021-arcade-dark-background/spec.md`

## Summary

The Arcade canvas gets the design's dark background as its own background colour. The toolkit then fills the whole canvas with it before every paint, and the translucent scene is laid over that dark colour instead of over the system's window background (R1). That is one change in `ArcadeCanvas`, plus the release of the colour when the canvas is disposed:
- it covers both painting paths, the normal one and the simplified one (the colour is under every frame, whatever paints it);
- it covers every quality tier and every window size (the toolkit fills the area being painted).

New native SWT tests, written first and seen red on the owner's Mac in dark appearance, measure the painted pixels: the empty parts of a painted frame have the design's background, on both paths and after a resize (R3). Then one push, one run of *Practice GUI tests* on macOS 15, expected green. This closes T026 and T031 of spec 020. Then the v0.8.0-beta.4 release (R5).

## Technical Context

- **Language/Version**: Java, release 11 for `desktop/TuxGuitar-practice` (CLAUDE.md); JDK 21 locally.
- **Primary Dependencies**:
  - the TuxGuitar UI toolkit: `UIControl.setBgColor(UIColor)` (`desktop/TuxGuitar-ui-toolkit/src/app/tuxguitar/ui/widget/UIControl.java:49`) and `UIFactory.createColor`;
  - its SWT implementation: `SWTControl.setBgColor` calls `Control.setBackground` (`desktop/TuxGuitar-ui-toolkit-swt/src/app/tuxguitar/ui/swt/widget/SWTControl.java:181-184`). The canvas is a `Composite` with `SWT.DOUBLE_BUFFERED` (`SWTCanvas.java:15`).
- **Storage**: none.
- **Testing**:
  - JUnit 5;
  - native SWT tests tagged `swt-gui`, run with the profile `swt-gui-tests` locally and by `.github/workflows/practice-gui-tests.yml` on `macos-15`;
  - the full gate `mvn -o -e clean verify -P native-modules` from `desktop/build-scripts/tuxguitar-macosx-swt-cocoa`.
- **Target Platform**: the SWT builds of macOS (Cocoa), Linux (GTK) and Windows (Win32). The change is in the shared practice module.
- **Project Type**: desktop application plugin module.
- **Performance Goals**: no measurable cost per frame. The background fill is the toolkit's, done before each paint of a double-buffered canvas, and no scene item is added.
- **Constraints**:
  - no existing test changed (owner's rule);
  - the scene model (`ArcadeScene`, `Scene`) and the renderers (`UiPainterRenderer`, `GuardedRenderer`) are unchanged. Their existing tests record the exact calls and items, so adding a background item or a fill call there would change what they see (R1);
  - `Theme.LIGHT` stays unselected (FR-005).
- **Scale/Scope**: one source file (`ArcadeCanvas.java`), one new test class, one workflow run, one release.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Preserve TuxGuitar Compatibility**: pass. Only the fork's Arcade view changes. TuxGuitar's own windows and the other practice windows keep following the system (FR-006).
- **II. Extend Before Modifying Core**: pass. No core module is touched; the change uses the toolkit's existing `setBgColor`.
- **III. Evidence-Based Architecture**: pass. Every API is cited from source above and in research.md. That the SWT canvas fills its background before the paint listener is checked experimentally by the new tests: red before, green after.
- **IV. Spec-Driven**: pass. Specified, clarified (2 answers), planned before any code.
- **V. Incremental Delivery**: pass. Phase A: the tests and the fix, gate locally. Phase B: the CI run, then the release.
- **VI/VII. Real-time audio**: not concerned (UI painting only, no audio path touched).

Re-check after design: unchanged, pass.

## Project Structure

### Documentation (this feature)

```text
specs/021-arcade-dark-background/
├── spec.md
├── plan.md              # this file
├── research.md          # R1-R5
├── quickstart.md        # how to check it
├── checklists/requirements.md
└── tasks.md             # /speckit-tasks
```

No data-model.md (no data) and no contracts/ (no interface offered to other modules or users: the change is inside the Arcade canvas).

### Source Code (repository root)

```text
desktop/TuxGuitar-practice/
├── src/app/tuxguitar/app/tools/custom/practice/arcade/ui/ArcadeCanvas.java      # sets the dark background; releases the colour on dispose
└── test/app/tuxguitar/app/tools/custom/practice/arcade/ui/
    └── ArcadeDarkBackgroundSwtTest.java                                        # new, tag swt-gui
docs/release-notes/v0.8.0-beta.4.md                                              # new
specs/020-macos15-ci/{tasks.md,baseline.txt}                                     # T026, T031 closed with the green run
specs/021-arcade-dark-background/baseline.txt                                    # new: gates, runs, release
```

**Structure Decision**: the fix sits where the defect is. The canvas is the only surface whose background the system supplies; the scene and the painters stay as they are.

## Complexity Tracking

No violation to justify.
