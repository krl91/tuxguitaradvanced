# Contract of the interactive modes = the coverage matrix (spec 011, FR-001, FR-013)

**How to read.** One row = one thing the learner does. *Protected by* names the test (`Class#method`, in `TST`) or a **GAP** with the reason. *State*: **new** = a test of this feature, **existing** = a test that exists today and must be **confirmed** to cover the row by the first task of phase P7 (listed as "confirmed" or "not confirmed" in the final report), **gap** = declared. `JourneyMatrixTest` checks that every row has a test or a gap and that every named test exists. **Limit:** the code cannot say what a journey is, so a journey added to a mode without a row is found only by the owner's review of this file.

Modes: **W** = Wait for the right note, **F** = Follow the tempo, **A** = Arcade. Priority as in the spec's stories (P1 first).

## SP - the start path (story 1, P1)

| id | Journey | Behaviour that must hold | Mode | Prio | Protected by | State |
|---|---|---|---|---|---|---|
| SP-01 | Start button of the preparation window | the start window opens first, alone; the mode does not begin | W F A | P1 | `StartPathJourneyTest` | new |
| SP-02 | Practice button of the transport bar (quick start) | the start window opens; the preparation window stays hidden | W F | P1 | `StartPathJourneyTest` | new |
| SP-03 | Coloured Arcade button of the transport bar | same as SP-02, with the Arcade mode chosen | A | P1 | `StartPathJourneyTest` | new |
| SP-04 | The learner takes longer than the quick-start patience | no practice window comes over the unanswered start window (bug 1 of spec 010) | W F A | P1 | `StartPathJourneyTest`; rule: `QuickStartTest` | new |
| SP-05 | "No, open the tuner" then the tuner closes | the tuner opens although the preparation window exists (bug 2); the question comes back; the mode never began; the preparation window did not appear | W F A | P1 | `StartPathJourneyTest`; `PracticeTunerOpenerTest` | new |
| SP-06 | "Yes, in tune" | the strum part is shown with real sizes (bug 3); the question is gone | W F A | P1 | `StartPathJourneyTest` (headless); `LayoutSwtJourneyTest` (native); `PracticeWindowSwtTest` | new |
| SP-07 | Verdicts: good, clipping, too loud, too weak, no strum; *Check again*; *Continue anyway* (after a bad verdict, or after 10 s without a strum) | good begins the mode; bad blocks with advice; *Continue anyway* goes on for this start only | W F A | P1 | `StartPathJourneyTest`; `PreRunWindowTest`; `PreRunCheckTest` | new + existing |
| SP-08 | Cancel by the button, the close button, Escape | the start is cancelled; a new start is accepted at once; nothing is held | W F A | P1 | `StartPathJourneyTest`; `PracticeWindowSwtTest` | new + existing |
| SP-09 | Double press of Start, or the transport button while a start window is open | one window; the mode starts once | W F A | P1 | `StartPathJourneyTest`; `StartFlowTest` | new + existing |
| SP-10 | Restart, play again, automatic retry | the start window is **not** opened again (FR-013 of spec 010) | A F | P1 | `FollowUpJourneyTest`; `PracticeWindowStartPathSourceTest` (source only) | new |

## WR - a whole run, start to result (story 2, P1)

| id | Journey | Behaviour that must hold | Mode | Prio | Protected by | State |
|---|---|---|---|---|---|---|
| WR-01 | A whole Wait run with right, wrong, chord notes and a passage retry | each event gets its verdict; a passage with more than half wrong is repeated; the summary lists it | W | P1 | `RunJourneyTest`; rules: `PracticeSessionTest`, `ChordPracticeTest`, `PassagePracticeTest` | new + existing |
| WR-02 | A whole Follow run: count-in, right/late/missed, pause and resume, end | no frame is judged during the count-in or the pause; the rest is judged as before; the end gives the state back | F | P1 | `RunJourneyTest`; rules: `FollowFunctionalTest`, `FollowPauseFunctionalTest` | new + existing |
| WR-03 | A whole Arcade run to the result window | perfect play gives the maximum score and the full streak; mixed play the documented score; the result window is shown | A | P1 | `RunJourneyTest`; rules: `ArcadeRunFunctionalTest` | new + existing |
| WR-04 | The same performance with the flames Off, Discreet and Full | identical judgements, score and result | A | P2 | `RunJourneyTest`; `FlameParityTest` | new + existing |

## FU - after a run (story 3, P2)

| id | Journey | Behaviour that must hold | Mode | Prio | Protected by | State |
|---|---|---|---|---|---|---|
| FU-01 | Stop | the preparation window comes back usable; nothing runs | W F A | P2 | `FollowUpJourneyTest` | new |
| FU-02 | Arcade restart, restart from the start, play again from a measure | no start window; the run begins as asked | A | P2 | `FollowUpJourneyTest` | new |
| FU-03 | Follow restart | no start window; the run begins again | F | P2 | `FollowUpJourneyTest` | new |
| FU-04 | The player is still stopping when a restart is asked | the start is retried up to the limit, then gives up with the message | A | P2 | `FollowUpJourneyTest` | new |
| FU-05 | The result window is closed | the learner is brought back to a usable window | A | P2 | `FollowUpJourneyTest` | new |
| FU-06 | Everything ends | the engine consumer is released, listeners removed, the tuner can open, no window left | W F A | P2 | `FollowUpJourneyTest` | new |

## IN - interruptions and changes (story 4, P2)

| id | Journey | Behaviour that must hold | Mode | Prio | Protected by | State |
|---|---|---|---|---|---|---|
| IN-01 | The input becomes unusable: preparing, quick start, start window open, running | the documented reaction; never a window over an unanswered start window | W F A | P2 | `InterruptionJourneyTest`; rule: `QuickStartTest` | new |
| IN-02 | The input is changed | the assessment is refreshed; the start button follows | W F A | P2 | `InterruptionJourneyTest` | new |
| IN-03 | The track is changed with a start window open | the start is cancelled; no mode begins | W F A | P2 | `InterruptionJourneyTest` | new |
| IN-04 | The mode is changed with a start window open | the start is cancelled; no mode begins | W F A | P2 | `InterruptionJourneyTest` | new |
| IN-05 | The preparation window is closed with a start window open | the start is cancelled; nothing is left open | W F A | P2 | `InterruptionJourneyTest` | new |
| IN-06 | A setting (tempo, tolerance, flames, passage) is changed during a run | it applies to the next run, not the current one | F A | P2 | `InterruptionJourneyTest`; `ArcadeFlamesSettingTest`; `FollowSettingsFlamesTest` | new + existing |

## LY - layouts (story 6, P3, tag `swt-gui`)

| id | Journey | Behaviour that must hold | Prio | Protected by | State |
|---|---|---|---|---|---|
| LY-01 | The start window in each state (question, strum, bad level, tuner way back) | every visible part has a size and lies inside the window | P3 | `LayoutSwtJourneyTest`; `PracticeWindowSwtTest` | new + existing |
| LY-02 | The preparation window and its four tabs | same | P3 | `LayoutSwtJourneyTest`; `FollowPanelSwtTest`; `RecognitionSettingsSwtTest` | new + existing |
| LY-03 | The Arcade window and the result window | same | P3 | `ArcadeWindowSwtTest`; `ArcadeCanvasSwtTest` | existing |
| LY-04 | The longest texts of English, French and Spanish | nothing cut or outside | P3 | `LayoutSwtJourneyTest` | new |

## RR - recorded performances and known weaknesses (story 5, P2)

| id | Journey | Behaviour that must hold | Mode | Prio | Protected by | State |
|---|---|---|---|---|---|---|
| RR-01 | The owner's recorded session (`tests/1`) | the figures are not worse than recorded | W F | P2 | `RecordedPracticeReplayTest`; `MonophonicRegressionReplayTest` | existing |
| RR-02 | The owner's bend recording (`tests/2`) | same | W F | P2 | `BendRecordedReplayTest` | existing |
| RR-03 | The owner's chord takes | accepted and wrong counts not worse (skips when absent) | W | P2 | `OwnerChordsBenchmarkTest` | existing |
| RR-04 | GuitarSet and IDMT through the modes' recognition | not worse than the reports of spec 010 | W | P2 | `DatasetBenchmarkTest`; `GuitarSetChordBenchmarkTest` | existing |
| RR-05 | Chord with a string missing; D major | **known weakness kept as a baseline**: 43 of 78 synthetic cases accepted at most; D takes refused | W | P2 | `SyntheticChordGuardTest`; `OwnerChordsBenchmarkTest` | existing |
| RR-06 | Really played hammer-on, pull-off, palm mute, tapping, tremolo picking | **GAP**: no recorded-audio test exists; synthetic signals only (`LegatoFunctionalTest`, `TechniqueSignalsTest`) | W F A | P2 | none | **gap** |

## CV - the matrix itself (story 7, P3)

| id | Journey | Behaviour that must hold | Prio | Protected by | State |
|---|---|---|---|---|---|
| CV-01 | The matrix is checked against the tests | no row without a test or a gap; every named test exists | P3 | `JourneyMatrixTest` | new |
| CV-02 | The coverage report | a figure per interactive package, before and after, none lower | P3 | the `coverage` profile and `benchmark/coverage-*.txt` | new |

**Declared gaps today:** RR-06 only. Windows and Linux are not run (stated in the final report, not a row).
