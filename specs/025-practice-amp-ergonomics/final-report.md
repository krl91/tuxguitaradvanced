# Spec 025: final report

The practice and amp windows fit the screen and say what to do. Implemented on 2026-11-05 in phases 1 to 6 (commits 2306a3039, 5ccd5441c, faef7144b, 7f26d07bf, 37e302af6 and the phase 6 commit). Waits for the owner's gate (T031, sheet F14).

## Requirements

| Requirement | Status | Evidence |
|---|---|---|
| FR-001 the check starts again when the guitar is heard | Done | `LevelRecheck` (after *no signal* and *saturating* only, never during a check or a run, on a frame the check counts as playing), asked in `LevelPanel.frameReceived`; `LevelRecheckTest`, `StartAreaWiringSourceTest`. The root cause: since spec 002 a check ran again only for a new input (research R1). |
| FR-002 a line under the greyed start button | Done | `QuickStart.blocker` (one key per cause), `StartArea` in the window's bottom area above the buttons, outside the tabs; `QuickStartBlockerTest`, `StartAreaTest`, `StartAreaWiringSourceTest`. |
| FR-003 Check again, no second check | Done | `StartArea`'s button for the level causes; `AudioEngine.runLevelCheck` returns while a check listens (it started over and subscribed the assessor twice); `LevelCheckNotTwiceTest`, seen red. |
| FR-004 the line follows the cause and disappears | Done | `updateStartButton` writes the blocker, the check state included; `StartAreaTest`. |
| FR-005 the practice window fits 768 px | Done | `PracticeLayout.tabHeight` and `sizeFolder`, `Screens.usableHeight`; `PracticeLayoutTest`, `PracticeWindowFitsSwtTest`. The practice window keeps no size between launches (it opens packed and centred), so no restored size can exceed the screen. |
| FR-006 tabs scroll, the start button stays in view | Done | `PracticeLayout.scrollingTab` for every tab; `PracticeWindowFitsSwtTest` (the last row scrolled into view, the start button inside the window), `PracticeWindowFitsSourceTest`. |
| FR-007 the amp window in four tabs | Done | `AmpWindow`, `AmpChainRows`'s second constructor; `AmpWindowTabsSwtTest`. |
| FR-008 at most 600 px, fits 768 px, tabs scroll | Done | `AmpWindow.FIRST_HEIGHT`, `withinScreen`; `AmpWindowTabsSwtTest` (first opening, kept bounds of 1200 px brought within 768). |
| FR-009 the check box and Speaker simulation | Done | Texts in three languages; `AmpWindowTabsSwtTest`. |
| FR-010 every control kept, same behaviour | Done | The spec 022 tests green: `AmpPedalRowsSwtTest` unchanged; the other four with only the lines of the owner's exception. |
| FR-011 texts in three languages | Done | `AmpTextsTest`, `QuickStartBlockerTest`. |
| FR-012 existing kinds of controls, same on the three systems | Done, with one note | Tabs, scrolling panels, check box, popup menu, small window. `UICheckBox` now declares `getToolTipText` and `setToolTipText`: the SWT, JavaFX and Qt check boxes already had both through their base classes, the interface did not declare them. No platform branch; Linux and Windows not looked at (sheet F14, row X1). |
| FR-013 rules during a run unchanged | Done | No re-check during a run (`LevelRecheck`, `setRunGoing`); the recognition, the amp sound and the modes not changed; every recognition and benchmark test green. |
| FR-014 Save… and ⋯, no Name field | Done | `AmpNamePrompt`; `AmpNamePromptSwtTest`. |

## Success criteria

| Criterion | Status |
|---|---|
| SC-001 start within 5 s after waiting, 10 of 10 | Waits for the owner (F14 step 1). Unit level: the check starts again on the first playing frame. |
| SC-002 each cause named with its action, three languages | Met: `QuickStartBlockerTest`. |
| SC-003 both windows entirely visible on a 768-pixel screen | Met in the SWT tests; waits for the owner's look (F14 steps 3, 4, 9). |
| SC-004 the amp window at most about 600 px | Met: `AmpWindowTabsSwtTest`. |
| SC-005 the owner finds the switch and the speaker simulation, both windows clear | Waits for the owner (F14 steps 5, 11). |
| SC-006 every test green, sounds unchanged | Met: final gate (practice 1466, audio engine 498, tuner 24, swt-gui 91); `AmpSoundsUnchangedTest`. |

## Tests changed under the owner's exception

See `exception-lines.md`: 8 lines in `AmpWindowSwtTest`, `AmpSafetySwtTest` and `AmpSoundListSwtTest`, and one helper method added to `AmpSoundListSwtTest`; `AmpAutoStartWarningSwtTest` unchanged. Every other committed test unchanged.

## Deviations from tasks.md

- T010 and T016: no test builds the whole practice window, so the bottom area and the sizing are tested on their own classes (`StartAreaTest`, `PracticeWindowFitsSwtTest` with the real `PracticeLayout` and `WindowFit`) and the window's use of them on its source (`StartAreaWiringSourceTest`, `PracticeWindowFitsSourceTest`).
- T024 was green at once: the store is not changed by this spec, the test guards it.
- The delay line of the amp window is on its own line under the sounds, not beside the check box: beside it, the window was too wide to open beside the run's notes (`AmpWindowSwtTest`, one attempt).
- The GUI tests need an unlocked session: with the screen locked, macOS selects no tab and lays nothing out (`PracticeWindowFitsSwtTest` failed so, and passed once the Mac was unlocked).

## Waits for the owner

T031: sheet `specs/test-sheets/F14-ergonomie.md` on the built application.
