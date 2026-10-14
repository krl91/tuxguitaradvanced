# Procedure: proving the tests can fail (spec 011, FR-010, SC-002, SC-003)

No mutation-testing tool exists in the repository (research R6). For each entry: apply the change to a **scratch state** (a clean working tree on a throw-away branch or after `git stash`), run only the named test, **paste the failure** in `specs/011-.../benchmark/break-on-purpose-results.md`, then restore with `git checkout -- <file>`. The change is never committed. The table is filled by the tasks of phase P8 (the "Change" column is the plan, the result column is empty until then).

| # | Journey | Change (file, what to invert) | Test that must fail | Result |
|---|---|---|---|---|
| B1 | SP-04 (bug 1 of spec 010) | `QuickStart.showAfterPatience`: drop `!startPending` | `StartPathJourneyTest#...patience...`, `QuickStartTest` | _to record_ |
| B2 | SP-05 (bug 2) | `PracticeTunerOpener.open`: remove the lifting of the practice track | `StartPathJourneyTest#...tuner...`, `PracticeTunerOpenerTest` | _to record_ |
| B3 | SP-06 (bug 3) | `PreRunWindow.refresh`: remove the `WindowFit.refit` call | `LayoutSwtJourneyTest`, `PracticeWindowSwtTest` | _to record_ |
| B4 | SP-01 | `PracticeWindow.startSession`: call `startSessionNow` directly, without `startFlow.begin` | `StartPathJourneyTest` | _to record_ |
| B5 | SP-08 | `PreRunWindow.windowClosed`: do not cancel | `StartPathJourneyTest` | _to record_ |
| B6 | SP-09 | `StartFlow.begin`: ignore `pending()` | `StartPathJourneyTest`, `StartFlowTest` | _to record_ |
| B7 | SP-10, FU-02 | `restartArcade`: go through `startSession()` | `RunJourneyTest`, `FollowUpJourneyTest` | _to record_ |
| B8 | WR-02 | `FollowRun` / `FollowPlayback` (where the count-in is handled; confirmed by the analysis of 2026-10-15, not `FollowSession`): judge during the count-in | `RunJourneyTest`, `FollowFunctionalTest` | _to record_ |
| B9 | WR-03 | `ArcadeScoring`: change the maximum | `RunJourneyTest`, `ArcadeRunFunctionalTest` | _to record_ |
| B10 | WR-01 | `PassagePolicy`: never repeat a passage | `RunJourneyTest`, `PassagePracticeTest` | _to record_ |
| B11 | WR-04 | `ArcadeFrameSource`: feed the flame level into the judging | `RunJourneyTest`, `FlameParityTest` | _to record_ |
| B12 | FU-06 | `PracticeWindow` dispose: do not `engine.release` | `FollowUpJourneyTest` | _to record_ |
| B13 | IN-03 | the tracks drop-down listener: do not `cancelPending` | `InterruptionJourneyTest` | _to record_ |
| B14 | IN-04 | `modeChanged`: do not `cancelPending` | `InterruptionJourneyTest` | _to record_ |
| B15 | IN-06 | read the setting at every frame instead of at the start | `InterruptionJourneyTest` | _to record_ |

| B16 | SP-02, SP-03 | `PracticeWindow.watchQuickStart` / `open`: show the preparation window at once for a quick start | `StartPathJourneyTest` | _to record_ |
| B17 | SP-07 | `PreRunCheck.verdict`: let `CLIPPING` go to `READY` | `StartPathJourneyTest`, `PreRunCheckTest` | _to record_ |
| B18 | FU-01 | `PracticeWindow.stopNow`: do not call `showPreparation()` / `showWindow()` | `FollowUpJourneyTest` | _to record_ |
| B19 | FU-03 | `PracticeWindow.restartFollow`: go through `startSession()` (ask again) | `FollowUpJourneyTest` | _to record_ |
| B20 | FU-04 | `PracticeWindow.retryArcadeRestart`: never retry (return false at once) | `FollowUpJourneyTest` | _to record_ |
| B21 | FU-05 | the result window's close path: do not bring the preparation window back | `FollowUpJourneyTest` | _to record_ |
| B22 | IN-01 | `QuickStart.showForUnusableInput`: ignore `runOngoing` | `InterruptionJourneyTest`, `QuickStartTest` | _to record_ |
| B23 | IN-02 | `PracticeWindow.inputChanged`: do not call `updateStartButton()` | `InterruptionJourneyTest` | _to record_ |
| B24 | IN-05 | the window's dispose listener: remove `startFlow.cancelPending()` | `InterruptionJourneyTest` | _to record_ |
The exact files and methods of B8 to B11 and B15 are confirmed when the entry is run (the names above are the plan's best reading of the source and are corrected in the result file if a name is different). **Coverage of the counts (analysis of 2026-10-15).** P1 journeys = SP-01 to SP-10, WR-01 to WR-03: B1 to B12, B16 and B17 cover them (SP-01 B4, SP-02 and SP-03 B16, SP-04 B1, SP-05 B2, SP-06 B3, SP-07 B17, SP-08 B5, SP-09 B6, SP-10 B7, WR-01 B10, WR-02 B8, WR-03 B9: 100 %). P2 journeys **whose test is new** (WR-04, FU-01 to FU-06, IN-01 to IN-06: 13 rows; the six RR rows are existing baselines that this feature does not change and are **excluded** from the denominator): WR-04 B11, FU-01 B18, FU-02 B7, FU-03 B19, FU-04 B20, FU-05 B21, FU-06 B12, IN-01 B22, IN-02 B23, IN-03 B13, IN-04 B14, IN-05 B24, IN-06 B15: **13 of 13**. The first version of this file covered only 6 of 19 P2 rows (32 %) against the 80 % of SC-003: that was the conflict found by the checklist and confirmed by the analysis.
