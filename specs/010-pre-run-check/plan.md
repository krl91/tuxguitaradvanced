# Implementation Plan: Pre-run check (tuning and input level at every start)

**Branch**: `feature/interactive-practice` (spec dir `010-pre-run-check`) | **Date**: 2026-10-14 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification `specs/010-pre-run-check/spec.md` with its Clarifications of 2026-10-14 (test strum in one small dedicated start window, a bad level blocks with "Continue anyway" for this start only, clipping threshold 0.5 % in the 300 ms after a strum, recognition non-regression tests).

## Summary

Before every start of *Wait*, *Follow* or *Arcade* a small start window opens. It asks "is your guitar in tune?" (*Yes, in tune* / *No, open the tuner*) and asks for a **test strum**, showing the live level. The strum is judged by a new pure meter (`StrumClipMeter`, audio engine) that counts the samples at full scale in the 300 ms after the attack: 0.5 % or more is **clipping**, a mean level under the minimum is **too weak**, nothing heard is **no strum**. A bad verdict blocks the start; *Continue anyway* lets this start through. The window is opened from the one place all starts go through (`PracticeWindow.startSession`), so the preparation window and the transport-bar buttons share it. The recognition is not changed; its regression tests are inventoried, their figures recorded **before** any change, and completed where a kind of event has none.

## Technical Context

**Language/Version**: Java, `desktop/TuxGuitar-practice` at release 11 and `desktop/TuxGuitar-audio-engine` (the fork's modules); no new dependency.

**Primary Dependencies**: `AudioEngine`, `AnalysisService`, `LevelAnalyzer`, `InputAssessor`, `RecognitionParameters` (audio engine); `PracticeWindow`, `LevelPanel`, `QuickStart`, `FollowSettings`, `ArcadeResultWindow` as the model of a small window (practice plugin); `PracticeTunerContext` and the tuner action `tuner.tuner` (`TunerNextPlugin.ACTION_ID`); the UI toolkit abstraction (`createWindow`, `createProgressBar`, `createButton`, `createWrapLabel`).

**Storage**: none (nothing is remembered between two starts, FR-001, FR-005).

**Testing**: JUnit 5: a pure meter test, an offline test of the meter on GuitarSet (180 clean microphone recordings) and on the owner's takes through the real analyser, a state-machine test of the start window, a headless-toolkit test and a native SWT test of the window, the recognition regression suites (before and after files).

**Target Platform**: macOS Apple Silicon first (the owner's Rocksmith adapter); the guidance texts already name the macOS input volume; Windows and Linux are built by the existing workflow and not claimed as validated.

**Project Type**: desktop application (practice plugin and audio engine).

**Performance Goals**: with a good level and a tuned guitar, one answer and one strum, and the run begins at most 1 second after the attack of the strum, the learner's own time to answer and to strum not counted (FR-009, SC-003); the meter adds no allocation per hop on the analysis thread (counters only).

**Constraints**: constitution VI (nothing blocking on the audio or analysis threads), VII (pure testable logic), XIII (the level verdict is what was measured, not a claim about the guitar); the operating-system gain is never changed (spec 002 FR-041, kept).

**Scale/Scope**: one pure meter, one start window and its state machine, one hook in `startSession`, six new texts in three languages, a hop hook in `AnalysisService`, a set of regression tests with a before-and-after file.

## Constitution Check

*GATE: passed before research; re-checked after design.*

| Principle | Check |
|---|---|
| I Preserve compatibility | Additive. The frame-level `LevelState` used by the recognition is unchanged (the 10 % per-frame rule stays); the strum rule is a second, separate verdict used only by the start window. |
| II Extend before modifying core | Only the fork's modules change; `desktop/TuxGuitar` and `common/` do not. The tuner is opened through its existing action, not modified. |
| III Evidence-based | Every touch-point is a file that was opened (`research.md`); the threshold comes from 180 clean and 42 owner recordings (R1). |
| IV Spec-driven | Specify, clarify, then this plan; tasks, checklist, analyze, implement and converge follow in that order, the owner told at each step. |
| V Incremental | Five increments with gates (below); the "before" figures are recorded in increment P0, before any change. |
| VI Real-time | The meter is fed from the existing hop loop with integer counters only; the start window runs on the UI thread; the judging is not touched. |
| VII Separable, testable | The meter and the window state machine are pure classes tested without a display or a device. |
| VIII Deterministic | The verdict is a count over a fixed window against fixed numbers, replayable offline. |
| IX Cross-platform | macOS validated by the owner; nothing macOS-only is added except the existing guidance wording. |
| X No unrelated refactoring | The Ré-chord weakness found while measuring is **not** touched (spec 004 T007). |
| XI Build and test integrity | Full macOS build with the same counts plus the new tests, before and after. |
| XII Truthful reporting | The takes were labelled by a transcription tool, not by the owner: said so; figures reported as run. |
| XIII Honest semantics | The window says "the signal clips", never "your chord is wrong"; "in tune" is the learner's answer, not a measurement. |
| XIV Learner-centred | One clear instruction per state, with what to do; "Continue anyway" respects the learner. |

*Post-design re-check*: no violation; no entry in Complexity Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/010-pre-run-check/
├── spec.md
├── plan.md                  # this file
├── research.md              # decisions R1 to R9
├── data-model.md            # verdict, start state, meter
├── contracts/pre-run-start.md   # the surface and the invariants, each with a test
├── quickstart.md            # how to see and check the start window and the regression suites
├── benchmark/               # before-after.txt (figures BEFORE the change) created by the tasks
├── checklists/requirements.md
└── tasks.md                 # created by /speckit-tasks
```

### Source (what is added or changed)

```text
desktop/TuxGuitar-audio-engine/src/main/java/app/tuxguitar/audio/engine/
├── analysis/StrumClipMeter.java       # new: pure; counts clipped samples and the level in the 300 ms after an attack; verdict
├── analysis/AnalysisService.java      # + an optional hop listener, called in run() after the front end (one call per hop with the samples)
├── AudioEngine.java                   # + addHopListener / removeHopListener (facade used by the start window)
└── OfflineAnalysis.java               # + the same hook, so the tests run the meter through the real loop
desktop/TuxGuitar-practice/src/app/tuxguitar/app/tools/custom/practice/
├── ui/PreRunCheck.java                # new: the state machine of the start window (no widgets): TUNING, STRUM, LEVEL_BAD, READY, TUNER_OPEN
├── ui/PreRunWindow.java               # new: the small window (toolkit widgets), modelled on ArcadeResultWindow
└── ui/PracticeWindow.java             # startSession() goes through the check; restarts of the same run do not (R4)
desktop/TuxGuitar-practice/share/lang/messages{,_fr,_es}.properties   # practice.prerun.* (see R7)
desktop/TuxGuitar-practice/test/...    # StrumClipMeterTest, StrumClipDatasetTest, PreRunCheckTest, PreRunWindowTest, PracticeWindowStartPathTest, PracticeWindowSwtTest, regression suites
docs/MANUAL.md, docs/MANUAL-FR.md, docs/PRACTICE{,-FR,-ES}.md
```

**Structure Decision**: the verdict logic in the audio engine (pure, offline-testable on recordings), the start logic as a widget-free state machine in the practice plugin, the window as a thin shell over it; one hook, in the one place every start goes through.

## Increments

* **P0 - The figures before.** Run the recognition regression suites (R8) and record their figures in `benchmark/before-after.txt`, plus the full build counts, before any code.
* **P1 - The meter (pure).** `StrumClipMeter`, the hop hook, tests on synthetic signals, then on GuitarSet (no false clipping at 0.5 %) and on the owner's takes through `OfflineAnalysis` (SC-001).
* **P2 - The state machine.** `PreRunCheck` with its states and transitions, pure tests (all of FR-001 to FR-009 without a display).
* **P3 - The window and the hook.** `PreRunWindow`, texts in three languages, `startSession` wired, native test; transport-bar and preparation-window starts both covered.
* **P4 - Regression and checks.** The missing recognition tests (R8), the before-and-after comparison (SC-007), the parity test (SC-005), the full gate; **gate G: the owner** on the built application with the Rocksmith at 0.99 (SC-006).
* **P5 - Documentation.** Manuals and guides in three languages, `STATUS.md`, release notes when a release is made.

## Risks

* A 300 ms window after an attack needs a reliable attack: the hook receives hops, not frames, so the meter arms on the rise of the RMS (12 dB above the quietest recent hop, R2), not on the onset flag of the articulation detector; the strum of the owner's takes is the test.
* The ring of an earlier note could count as clipping: the window starts at the attack and the meter is reset by each new check.
* The start window must not become a wall: one answer and one strum, the run beginning at most 1 second after the attack of the strum with a good level (FR-009, SC-003), checked by the state machine on a fake clock and by the owner.
* The takes were labelled by a tool: the regression figures of the owner's takes are recorded as such and not used as the only judge.
* Restarting inside a run: decided not to re-ask (R4, written as FR-013); the owner may want otherwise: it is the one decision to confirm at the gate.

## Complexity Tracking

None.
