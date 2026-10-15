# Implementation Plan: An adjustable timing window for Follow and Arcade

**Branch**: `feature/interactive-practice` | **Date**: 2026-10-15 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/012-adjustable-timing-window/spec.md` (clarified 2026-10-15: a slider in milliseconds, 40 to 200 each side at 120 bpm; default 100; one remembered setting)

## Summary

The three fixed presets (Strict, Normal, Lenient) are replaced by **one number W**: the half-width, in milliseconds at 120 beats a minute, of the window in which a note counts as on time. The windows keep today's shape (a fraction of the local beat between a floor and a ceiling, the outer early/late window cut at the middle of the gap to the neighbours) and are **derived from W with the ratios of today's Normal**, so that **W = 75 reproduces Normal exactly** and the new default is **W = 100**. W is a new saved parameter `TEMPO_WINDOW` (40 to 200 ms), shown by a slider in the preparation window and by the control the Configuration window builds by itself: **one setting**. A configuration saved with an old preset name loads: strict 50, lenient 125, normal (the old default) the new default 100. The recognition, the latency compensation and the Wait mode do not change.

## Technical Context

**Language/Version**: Java; the practice and engine modules compile at release 11 (`CLAUDE.md`); JDK 21 to build.

**Primary Dependencies**: none new (the toolkit's slider `UIScale`, the recognition store, JUnit 5).

**Storage**: the existing recognition store (`RecognitionSettingsStore`, persisted configuration); one new key `tempo.window`.

**Testing**: the default suite (`mvn test -pl ../../TuxGuitar-practice -am`) and `swt-gui-tests` for the native panel test; baselines: `specs/011-interactive-modes-regression/benchmark/build-times.txt` (practice 923) and the recognition reports of spec 010.

**Target Platform**: macOS Apple Silicon validated; Windows and Linux built but not looked at.

**Project Type**: desktop application, practice plugin and audio-engine module.

**Performance Goals**: none; the window is computed once per event, as today.

**Constraints**: deterministic judging; with W = 75 every judgement equals today's; an old saved setting loads; no change of recognition; build green after each phase.

**Scale/Scope**: one parameter, one formula, one slider, texts in three languages, docs; about 8 production files and 6 test files.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

| Principle | Check | Result |
|---|---|---|
| I Preserve TuxGuitar Compatibility | no file format and no core file touched | pass |
| II Extend Before Modifying Core | all changes are in the fork's practice and audio-engine modules (`RecognitionParameters`, `RecognitionSettings`, `TimingTolerance`, `FollowSettings`, `FollowPanel`) | pass |
| III Evidence-Based Architecture | every claim cites a file (research R1 to R8); the unread point (does the store write default values) is a task, not an assumption | pass |
| IV Spec-Driven Development | specify, clarify, plan done in order | pass |
| V Incremental Delivery | five phases, each leaving the build green | pass |
| VI Real-Time Audio Correctness | the window is computed off the audio thread as today; nothing is added to the analysis thread | pass |
| VII Separable, Testable Logic | the formula stays a pure class (`TimingTolerance`), tested without UI | pass |
| IX Cross-Platform Conscious | no platform-specific code; validated on macOS only, stated | pass |
| X No Unrelated Refactoring | `Preset` stays (mapping and equality reference); only what the feature needs changes | pass |
| XI Build and Test Integrity | counts reported before and after; the equality test at W = 75 guards the judging | pass |
| XII Truthful Reporting | the old Strict and Lenient windows are approximated (under 10 %) and **declared**; the numbers stay provisional until real validation | pass |
| XIII/XIV | no recognition semantics change; the learner sees the value in milliseconds | pass |

**Post-design re-check:** unchanged. One point to watch: the legacy `TEMPO_TOLERANCE` choice must stay readable but get no control (research R4, R8.1).

## Project Structure

```text
specs/012-adjustable-timing-window/
├── plan.md, research.md, data-model.md, quickstart.md
├── contracts/timing-window.md      # the formula, the mapping, the persistence
├── checklists/requirements.md
└── tasks.md                        # /speckit-tasks

desktop/TuxGuitar-audio-engine/src/main/java/app/tuxguitar/audio/engine/settings/
├── RecognitionParameters.java      # + TEMPO_WINDOW (Number, ms, 100, 40..200); TEMPO_TOLERANCE becomes legacy
└── RecognitionSettings.java        # + the migration of an old preset in fromStrings

desktop/TuxGuitar-practice/src/app/tuxguitar/app/tools/custom/practice/
├── session/TimingTolerance.java    # + ofMillis(W) (the formula); Preset kept
├── session/FollowSettings.java     # tolerance in memory -> windowMillis read from / written to the store
├── session/FollowStarter.java      # builds TimingTolerance.ofMillis(settings.getWindowMillis())
└── ui/FollowPanel.java             # drop-down -> slider 40..200 with a value text
desktop/TuxGuitar-practice/share/lang/messages{,_fr,_es}.properties   # the texts
docs/PRACTICE{,-FR,-ES}.md, docs/MANUAL{,-FR}.md                      # the documentation
```

**Structure Decision.** No new class beyond the factory method on `TimingTolerance`; the parameter lives with the others so that the store, the validation, the persistence and the Configuration window work with no new mechanism.

## Phases (each leaves the build green; each is committed alone)

| Phase | Content |
|---|---|
| P0 | Baseline: the counts of the default and native suites and the recognition reports before any change (reuse `specs/011.../benchmark/build-times.txt` and say so) |
| P1 | **The formula:** `TimingTolerance.ofMillis(W)` and its tests (equal to Normal at 75 over a grid; 100 judges 90 ms late on time; bounds 40 and 200; two close events never overlap) |
| P2 | **The setting:** `TEMPO_WINDOW`, the legacy `TEMPO_TOLERANCE`, the migration in `fromStrings`, `FollowSettings` reading and writing it, `FollowStarter` using it; tests first |
| P3 | **The slider** in `FollowPanel` and the texts in three languages (the language test), the Configuration window showing the same value |
| P4 | **Non-regression:** the recorded and functional runs at W = 75 equal to the figures before; the recognition reports equal; the default suite and the native suite green |
| P5 | Docs, `STATUS.md`, the build, the owner's trial [HUMAN] (SC-008), the report |

## Complexity Tracking

| Item | Why it is needed | Simpler alternative rejected |
|---|---|---|
| A legacy parameter kept beside the new one | an older saved setting must still load (FR-006) | dropping the old key: would silently reset every user's choice |
| The derived outer window with fixed ratios | `early` and `late` must keep their meaning at every W (FR-005) | an independent second slider: more controls for no asked benefit |
