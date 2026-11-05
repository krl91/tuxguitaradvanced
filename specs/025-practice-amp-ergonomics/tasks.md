# Tasks: The practice and amp windows fit the screen and say what to do

**Input**: Design documents from `specs/025-practice-amp-ergonomics/`
**Prerequisites**: plan.md, spec.md, research.md (R1–R8), data-model.md, contracts/practice-window.md, contracts/amp-window.md, quickstart.md

**Tests**: required (the owner's rules: tests first and seen red; never modify an existing test, only add new ones, except the owner's exception of 2026-11-05 limited to the lines named in research R6; if a committed test fails, fix the code under the bug protocol, at most 3 attempts, then stop and report).

**Paths**:
- `AE` = `desktop/TuxGuitar-audio-engine/src/main/java/app/tuxguitar/audio/engine`
- `AET` = `desktop/TuxGuitar-audio-engine/src/test/java/app/tuxguitar/audio/engine`
- `UI` = `desktop/TuxGuitar-practice/src/app/tuxguitar/app/tools/custom/practice/ui`
- `TUI` = `desktop/TuxGuitar-practice/test/app/tuxguitar/app/tools/custom/practice/ui`
- `LANG` = `desktop/TuxGuitar-practice/share/lang`
- `BUILD` = `desktop/build-scripts/tuxguitar-macosx-swt-cocoa`
- `SPEC` = `specs/025-practice-amp-ergonomics`

**Standing rules for every phase**:
- JDK 21 pinned. After each Maven build, `git checkout -- specs/003-arcade-mode/benchmark/`.
- Gate = `mvn -o -e clean verify -P native-modules` from `BUILD` green, plus `-P swt-gui-tests` when a window changed, plus `git diff --name-status <phase start> -- '*test*'` showing only added files (and, in phase 5, the four files of the exception with only their R6 lines changed).
- Every new string in `LANG/messages.properties`, `_fr`, `_es`.
- Commit each phase without any co-author trailer. One push per round, both refs together.

## Format: `[ID] [P?] [Story] Description`

## Phase 1: Setup

- [X] T001 Write `SPEC/baseline.txt`: HEAD, the counts of the full gate and of `swt-gui-tests` run now, and the list of committed tests that read the files this spec changes (`GreyedStartExplainedTest`, `QuickStartTest`, `WindowFitTest`, `PracticeWindowStartPathSourceTest`, `PreRunWindowTest`, `FollowPanelSwtTest`, `AmpWindowSwtTest`, `AmpSafetySwtTest`, `AmpAutoStartWarningSwtTest`, `AmpSoundListSwtTest`, `AmpPedalRowsSwtTest`), with the four of the owner's exception marked.

## Phase 2: Foundational

- [X] T002 [P] Test first, `AET/LevelCheckNotTwiceTest.java`: with a fake backend feeding pitched frames, two `runLevelCheck()` calls during one check give one assessment and each frame counted once (the stats' frame count equals the frames fed); seen red.
- [X] T003 Make T002 green in `AE/AudioEngine.java` (`runLevelCheck` returns at once while `assessor.isChecking()`; research R2); add `public boolean isLevelCheckRunning()`.

## Phase 3: User Story 1 - The start button is never stuck, and the window says what to do (P1)

**Goal**: the check starts again by itself after "no signal" or "saturating" when the guitar is heard; a line under the start button names the cause and the action, with **Check again** for level causes.
**Independent Test**: quickstart §4 step 1 and 2; `LevelRecheckTest`, `QuickStartBlockerTest`.

- [ ] T004 [P] [US1] Test first, `TUI/LevelRecheckTest.java` (data-model "Level re-check rule"): true only for `UNUSABLE` + `NO_SIGNAL` or `CLIPPING`, no check running, no run going, a pitched frame with confidence ≥ 0.80; false for `DEVICE_LOST`, `ACCESS_DENIED_SUSPECTED`, `SUITABLE`, `SUBOPTIMAL`, `UNCHECKED`, a quiet or unpitched frame, a running check, a run going; seen red.
- [ ] T005 [P] [US1] Test first, `TUI/QuickStartBlockerTest.java` (data-model "Start blocker"): each cause gives its key and its Check again flag; no blocker when the start can be pressed; the one-click wait keeps `practice.quickstart.waiting`; every `practice.start.blocker.*` key and `practice.start.check-again` exists, non-empty, in the three language files; seen red.
- [ ] T006 [US1] `UI/LevelRecheck.java` (new): the rule of T004, using `InputAssessor.Parameters` for the confidence; T004 green.
- [ ] T007 [US1] `UI/QuickStart.java`: add `blocker(boolean trackChosen, InputAssessment assessment, boolean checking, boolean waitingForGuitar)` returning the cause, key and Check again flag; `greyedReason` unchanged; texts in `LANG` (data-model wordings, plain, with the action); T005 green, `GreyedStartExplainedTest` and `QuickStartTest` green unchanged.
- [ ] T008 [US1] `UI/LevelPanel.java`: in `frameReceived`, ask `LevelRecheck` (with the window's "run going" state given by a supplier set by `PracticeWindow`) and call `engine.runLevelCheck()` on the UI thread when it says so.
- [ ] T009 [US1] `UI/PracticeWindow.java`: move `startLabel` from the Track tab to a fixed bottom area above the buttons, with a **Check again** button beside it (shown only when the blocker's flag says so, calls `engine.runLevelCheck()`); `updateStartButton` keeps `QuickStart.greyedReason(` and `this.startLabel.setText(` and writes the blocker's text; hidden when the start can be pressed; refreshed when the check starts and ends. `PracticeWindowStartPathSourceTest`, `GreyedStartExplainedTest` green unchanged.
- [ ] T010 [US1] Test, `TUI/StartBlockerLineSwtTest.java` (`swt-gui-tests`): with a fake engine, a window whose check ended on no signal shows the line in the bottom area on every tab, a pitched frame starts a check (line "listening"), a suitable result hides the line and enables the start; **Check again** visible for input lost, hidden for no track.
- [ ] T011 [US1] Gate; commit "impl(025): phase 3".

## Phase 4: User Story 2 - The practice window fits the screen (P1)

**Goal**: every tab scrolls inside a height that fits a 768-pixel screen; the bottom area always in view.
**Independent Test**: `PracticeWindowFitsSwtTest`; quickstart §4 step 3.

- [ ] T012 [P] [US2] Test first, `TUI/PracticeLayoutTest.java`: `tabHeight(tallest, usable, others)` = min(tallest, usable − others), never below the one-row minimum; usable taken as 768 when unknown or larger; seen red.
- [ ] T013 [US2] `UI/PracticeLayout.java` (new); T012 green.
- [ ] T014 [US2] `UI/Screens.java`: add `usableHeight(UIRectangle)` (the client area height of the screen holding the rectangle, by the same SWT reflection; 0 when unknown); test in `TUI/ScreensUsableHeightTest.java` for the unknown case.
- [ ] T015 [US2] `UI/PracticeWindow.java`: each tab panel (Track, Input, Mode, Passages) inside a vertical `UIScrollBarPanel` as `FireSettingsPanel` does; `refit` sets the folder's packed height with `PracticeLayout.tabHeight` instead of the tallest tab + 48; kept bounds taller than the usable height brought within it. `WindowFitTest` unchanged and green.
- [ ] T016 [US2] Test, `TUI/PracticeWindowFitsSwtTest.java` (`swt-gui-tests`): with the usable height forced to 768, the opened window is at most 768 high, the start button is visible on every tab, the Input tab's last control can be scrolled into view, a kept bounds of 1200 px high opens within 768.
- [ ] T017 [US2] Gate; commit "impl(025): phase 4".

## Phase 5: User Story 3 - The amp window fits the screen, in tabs, with clear names (P2)

**Goal**: top block (check box, sound with Save… and ⋯, delay), four scrolling tabs, at most 600 px at the first opening, renamed controls.
**Independent Test**: `AmpWindowTabsSwtTest`, `AmpNamePromptSwtTest`; quickstart §4 step 4.

- [ ] T018 [P] [US3] Test first, `TUI/AmpWindowTabsSwtTest.java` (`swt-gui-tests`): four tabs in the contract's order, each holding its controls (data-model "Amp window tabs"); the check box, sound list, Save…, ⋯ and delay outside the tabs; first opening at most 600 px high; kept bounds of 1200 px brought within 768; **Add a pedal** puts a reverb in *Effects after the amp* and shows that tab; the switch is a `UICheckBox` with the text "Hear my guitar through the amp"; the cabinet's text "Speaker simulation" and a tip; every control still has a tooltip; seen red.
- [ ] T019 [P] [US3] Test first, `TUI/AmpNamePromptSwtTest.java`: Save… opens the prompt, an empty or ready-made or 41-character name keeps OK disabled with the same messages as today, OK saves; ⋯ shows Rename…, Delete, Restore enabled under today's rules; Rename… opens the prompt with the current name; seen red.
- [ ] T020 [US3] `UI/AmpChainRows.java`: a second constructor taking the three parents (before, amp, after) and their layouts; the old constructor kept and unchanged; `AmpPedalRowsSwtTest` green unchanged.
- [ ] T021 [US3] `UI/AmpNamePrompt.java` (new): a small modal window with the name field, the name problem line and OK / Cancel, using `AmpSoundStore.NameProblem`.
- [ ] T022 [US3] `UI/AmpWindow.java`: the top block, the `UITabFolder` with four vertical `UIScrollBarPanel` tabs, the check box switch, Save… and ⋯ (`UIPopupMenu` with three `UIMenuActionItem`), Add a pedal and the message line under the tabs; `relayout` keeps the size; first opening at most 600 px; kept bounds within the usable height; accessors as in `contracts/amp-window.md`. `UI/AmpTexts.java` and `LANG`: the new and renamed texts (research R7). T018, T019 green.
- [ ] T023 [US3] Owner's exception (research R6): in `TUI/AmpWindowSwtTest.java`, `TUI/AmpSafetySwtTest.java`, `TUI/AmpAutoStartWarningSwtTest.java`, `TUI/AmpSoundListSwtTest.java`, change only the lines that reach the switch as `UIToggleButton`, `getSoundName()` and the Rename / Delete / Restore buttons, so they reach the check box, the name prompt and the menu items; record the exact lines changed in `SPEC/exception-lines.md` with the before and after of each; every assertion's meaning kept. All four green.
- [ ] T024 [US3] Test, `TUI/AmpSoundsUnchangedTest.java`: a store written before this change (spec 022's format: enabled, output, volumes, current sound, own sounds, kept bounds) loads with the same values and chains.
- [ ] T025 [US3] Gate; commit "impl(025): phase 5".

## Phase 6: Polish & cross-cutting concerns

- [ ] T026 [P] `docs/PRACTICE.md`, `docs/PRACTICE-FR.md`, `docs/PRACTICE-ES.md`: the line under the start button and Check again; the amp window's tabs and new names (screens described, no "only on macOS").
- [ ] T027 [P] `specs/test-sheets/F14-ergonomie.md` (quickstart §4) and its row in `specs/test-sheets/README.md`.
- [ ] T028 [P] `docs/release-notes/next-ergonomics.md`: draft notes (the known issues of 0.8.0-beta.7 fixed).
- [ ] T029 Full gate (`clean verify -P native-modules`, `swt-gui-tests`), counts appended to `SPEC/baseline.txt`; the test diff since T001 shows added files and the four files of T023 only.
- [ ] T030 `SPEC/final-report.md`: FR and SC status with evidence, the exception's lines, what waits for the owner.
- [ ] T031 [HUMAN] The owner's gate on the relaunched app, sheet F14 (SC-001, SC-003, SC-005).

## Dependencies

- Phase 2 (T002–T003) before US1's re-check (T008) and **Check again** (T009).
- US1, US2, US3 are independent after phase 2; US1 and US2 both edit `PracticeWindow.java`, so they run in order (US1 then US2).
- T023 after T022 (the tests reach the new accessors).
- Polish after the three stories.

## Parallel examples

- US1: T004 and T005 together; then T006 and T007.
- US3: T018 and T019 together.
- Polish: T026, T027, T028 together.

## Implementation strategy

MVP = phases 1 to 3 (the stuck button fixed and explained). Then US2 (fits the screen), then US3 (amp window). Each phase ends green and committed.
