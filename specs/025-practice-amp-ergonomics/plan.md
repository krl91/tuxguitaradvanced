# Implementation Plan: The practice and amp windows fit the screen and say what to do

**Branch**: `feature/interactive-practice` | **Date**: 2026-11-05 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/025-practice-amp-ergonomics/spec.md`

## Summary

Three fixes from the owner's gate of spec 023. (1) The level check starts again by itself when the guitar is heard after "no signal" or "saturating", a second check never runs on top of a running one, and the reason of a greyed start is a line in the window's fixed bottom area with **Check again** (research R1 to R3). (2) Every tab of the practice window scrolls inside a height that fits a 768-pixel screen, so the bottom area stays in view (R4). (3) The amp window gets a top block (check box, sound with **Save…** and **⋯**, delay) above four scrolling tabs, at most 600 px high, with the renamed controls (R5, R6). Only toolkit controls already shipped are used.

## Technical Context

**Language/Version**: Java, release 11 for the fork modules (`TuxGuitar-audio-engine`, `TuxGuitar-practice`); JDK 21 locally.

**Primary Dependencies**: TuxGuitar's abstract UI toolkit (`desktop/TuxGuitar-ui-toolkit`: `UITabFolder`, `UIScrollBarPanel`, `UICheckBox`, `UIPopupMenu`, `UIWindow`) with its SWT implementation; no new dependency.

**Storage**: the existing amp settings (`AmpSoundStore`, `AmpSoundSettings`, kept window bounds) and the practice window's kept bounds; formats unchanged.

**Testing**: JUnit 5; pure rules as unit tests (`TuxGuitar-practice/test`), windows as SWT GUI tests (`-P swt-gui-tests`), the audio-engine guard in `TuxGuitar-audio-engine/src/test/java`.

**Target Platform**: macOS (Apple Silicon) validated; Linux and Windows built by CI with the same code.

**Project Type**: desktop application plugin (the practice module).

**Performance Goals**: none new; the frame listener's added rule is a few comparisons per frame on the UI-bound listener that already exists.

**Constraints**: both windows fit a 768-pixel screen; the amp window opens at most 600 px high; no tooltip relied on for a disabled control (macOS); committed tests unchanged except the owner's exception of 2026-11-05 (research R6).

**Scale/Scope**: two windows, one engine guard, about 25 new strings in three languages.

## Constitution Check

| Principle | How this plan meets it |
|---|---|
| I. Compatibility | TuxGuitar's own windows and formats untouched; amp sounds and kept settings load unchanged (FR-010, a test). |
| II. Extend before core | All changes are in the fork modules `TuxGuitar-practice` and `TuxGuitar-audio-engine`; no core module. |
| III. Evidence | Every decision cites the source read (research R1 to R7). |
| IV. Spec-driven | Spec and clarifications done; tasks come next. |
| V. Incremental | Three user stories, each its own phase, each testable alone. |
| VI. Real-time audio | Nothing on the audio threads; the re-check rule runs in the existing frame listener and posts to the UI thread. |
| VII. Separable logic | `LevelRecheck`, `QuickStart.blocker`, `PracticeLayout.tabHeight` and the amp window's height rule are pure and unit-tested. |
| IX. Cross-platform | Toolkit controls only, no platform branch; `Screens` keeps its "no SWT: leave the bounds" fallback. Validated on macOS, Linux and Windows built only. |
| X. No unrelated refactoring | `AmpChainRows` gains a constructor, the old one stays; no reformatting. |
| XI. Build and tests | Every phase ends with the full macOS build and the swt-gui profile; the only changed test lines are those of the owner's exception (R6), listed in the final report. |
| XII. Truthful reporting | Results reported as run; the owner's gate on a new sheet F14. |
| XIV. Learner-centred | Each blocking cause says what to do (FR-002). |
| Texts | English, French, Spanish in the plugin's `share/lang` files. |

Gate: passes. Re-checked after design: passes (the exception of R6 is the owner's, recorded).

## Project Structure

### Documentation (this feature)

```text
specs/025-practice-amp-ergonomics/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── practice-window.md
│   └── amp-window.md
└── tasks.md            (next: /speckit-tasks)
```

### Source Code (repository root)

```text
desktop/TuxGuitar-audio-engine/src/main/java/app/tuxguitar/audio/engine/
└── AudioEngine.java                  runLevelCheck: no second check during one (R2)

desktop/TuxGuitar-practice/src/app/tuxguitar/app/tools/custom/practice/ui/
├── LevelRecheck.java                 new: when a failed check starts again (R1)
├── QuickStart.java                   + blocker(...): the cause, its text key, Check again or not (R3)
├── PracticeLayout.java               new: the tabs' height for a 768-pixel screen (R4)
├── LevelPanel.java                   asks LevelRecheck in its frame listener
├── PracticeWindow.java               scrolling tabs, the bottom area (line, Check again, buttons)
├── Screens.java                      + the client area's height of the screen holding a rectangle
├── AmpWindow.java                    top block, four tabs, check box, Save…, ⋯ menu, name prompt
├── AmpNamePrompt.java                new: the small window asking a sound's name
├── AmpChainRows.java                 + constructor with three parents (before, amp, after)
└── AmpTexts.java                     new keys

desktop/TuxGuitar-practice/share/lang/messages{,_fr,_es}.properties

desktop/TuxGuitar-practice/test/app/tuxguitar/app/tools/custom/practice/ui/
├── LevelRecheckTest.java, QuickStartBlockerTest.java, PracticeLayoutTest.java    (new, unit)
├── PracticeWindowFitsSwtTest.java, AmpWindowTabsSwtTest.java, AmpNamePromptSwtTest.java   (new, swt-gui)
└── AmpWindowSwtTest, AmpSafetySwtTest, AmpAutoStartWarningSwtTest, AmpSoundListSwtTest   (owner's exception, R6 lines only)

desktop/TuxGuitar-audio-engine/src/test/java/.../LevelCheckNotTwiceTest.java      (new)

specs/test-sheets/F14-ergonomie.md     the owner's gate
```

**Structure Decision**: the existing practice module and audio-engine module; no new module.

## Phases

1. **US1 (P1)**: R2 guard, `LevelRecheck`, `QuickStart.blocker`, the bottom area with the line and **Check again**, texts.
2. **US2 (P1)**: scrolling tabs, `PracticeLayout.tabHeight`, kept bounds brought within the screen.
3. **US3 (P2)**: the amp window's top block, four tabs, check box, renamed cabinet, **Save…**, **⋯**, name prompt, the exception's test lines, texts.
4. **Polish**: docs (practice guide in three languages), sheet F14, release notes draft, full gate, final report.

## Complexity Tracking

| Item | Why needed | Simpler alternative rejected because |
|---|---|---|
| Changing lines of four committed tests | The owner chose a check box and a menu (clarifications 1 and 3); the tests reach the old controls by type | Keeping the old controls would reopen the owner's answers; approved by the owner on 2026-11-05, limited to those lines |
