# Implementation Plan: Per-device calibration of the note and chord recognition

**Branch**: `feature/interactive-practice` | **Date**: 2026-10-15 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/013-device-recognition-calibration/spec.md` (clarified 2026-10-15 in three passes: the piece is `basic_test.tg`, guitar only; the recording stays in memory and is dropped; a result not better than the default, and every refusal, offer three choices; one modal window; the profile applies everywhere the input is used; a *Forget the calibration* button; a grey dot with no input)

## Summary

A **profile library** keeps, for each input device, the recognition settings found best for it. The player calibrates a device from the *Input* tab: the tuning question, the advice, the piece (`basic_test.tg`, shown as a list of events, played without tempo), a **listening mode** that counts sounds without saying which and records them in memory, a quality question, then an **offline search** (a deterministic coordinate descent, with the default as the first candidate, scored by judging each recorded sound alone against its own event) and a result: the profile found, or **a message with three choices** (keep what was found, record again, use the default) when it is not better than the default or below 60 %. A **dot** (green, orange, red, with a short text) next to the device chooser shows the state and follows the input. The recognition code is not changed: a profile only replaces some **values** of the default settings, through one new layer of the settings store; with no profile every figure is as before.

## Technical Context

**Language/Version**: Java; the practice and engine modules compile at release 11; JDK 21 to build.

**Primary Dependencies**: none new (the toolkit, JUnit 5, the engine's `OfflineAnalysis`, `PracticeSession`, `PolyphonicAcceptance`, `HopListener` of spec 010, `TGConfigManager`).

**Storage**: a new configuration module `tuxguitar-recognition-profiles` (the profile library); the default settings stay where they are. **No audio is ever written to disk.**

**Testing**: the default suite (baseline 937 tests, `specs/012-adjustable-timing-window/benchmark/after.txt`), native tests for the window, the committed built-in-microphone corpus (22 notes), the owner's chord takes and his calibration recording (skip when absent).

**Target Platform**: macOS Apple Silicon validated; Windows and Linux built, not looked at.

**Project Type**: desktop application, audio-engine and practice modules.

**Performance Goals**: a search of at most **120 s and 2000 candidates**, with progress and cancel; the live listening mode allocates nothing per hop.

**Constraints**: nothing changes without a profile; the default is always a candidate; deterministic search; no audio on disk; no recognition algorithm change; no improvement figure promised.

**Scale/Scope**: about 18 new production classes in two modules, one change of the store, one line in the engine, a window, a dot and two buttons in the UI, 3 languages; 51 tasks.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

| Principle | Check | Result |
|---|---|---|
| I Preserve TuxGuitar Compatibility | no file format and no core file touched | pass |
| II Extend Before Modifying Core | only the fork's engine and practice modules; the store gets a layer; the engine gets one line (the activator) and otherwise nothing new (the hop listener of spec 010 is reused; `engineValuesDiffer` already rebuilds on a profile change) | pass |
| III Evidence-Based Architecture | each decision cites a file (research R1 to R11); the unverified points are first tasks (device id uniqueness, front-end state read at recording time; the share path is settled: a classpath resource) | pass |
| IV Spec-Driven Development | specify, clarify, plan done in order | pass |
| V Incremental Delivery | seven phases, each green and reviewable | pass |
| VI Real-Time Audio Correctness | the **live** part (recording hops) copies into a preallocated array and counts onsets with its **own** detectors on the hop samples (`HopListener` gives samples only), no allocation per hop, no lock on the analysis thread beyond a volatile counter; the **search** is offline on its own thread; the activation runs on the selection thread and causes one analysis rebuild, as an input change does | pass |
| VII Separable, Testable Audio Logic | the sound counter, the segmenter, the judge, the search, the state of the dot and the flow are pure classes tested without UI or hardware | pass |
| VIII Deterministic, Explainable | a deterministic coordinate descent with a printed report; no machine learning | pass |
| IX Cross-Platform Conscious | no platform code; validated on macOS only, stated | pass |
| X No Unrelated Refactoring | the store layer and the writers' `getDefault()` are the only edits to existing code, each listed | pass |
| XI Build and Test Integrity | counts before and after; the default suite after every phase | pass |
| XII Truthful Reporting | **no gain is promised**; the validation is independent and reported; deviations (one window instead of five) flagged for the owner | pass |
| XIII/XIV | the learner is told what is measured and why a profile is refused; the dot has a text | pass |

**Post-design re-check (after the three clarify passes):** unchanged; the engine change is one declared line. Points to watch: every store writer uses `getDefault()` (R11.1); one window for the steps is **decided by the owner** (spec Clarifications 2026-10-15) and judged in his trial.

## Project Structure

```text
specs/013-device-recognition-calibration/
├── plan.md, research.md, data-model.md, quickstart.md
├── contracts/  profile-library.md, calibration-flow.md, scoring-search.md
├── checklists/requirements.md
└── tasks.md                          # /speckit-tasks

desktop/TuxGuitar-audio-engine/src/main/java/app/tuxguitar/audio/engine/
├── AudioEngine.java                         # + one line in getInstance: creates the ProfileActivator (declared change)
├── settings/RecognitionSettingsStore.java   # + getDefault(), active profile layer, get() = effective, restoreDefaults on the default
├── settings/ProfileParameters.java          # NEW: which parameters a profile may hold, and the merge
└── calibration/                             # NEW package (pure, no UI)
    ├── DeviceKey.java, RecognitionProfile.java, ProfileState.java, ProfileLibrary.java (save, load, remove), ProfilePersistence.java, ProfileActivator.java
    ├── HopRecorder.java, SoundCounter.java, SoundSegmenter.java, RecordingQuality.java (the measures and the silent / clipped checks)
    └── (all tested without hardware)

desktop/TuxGuitar-practice/src/app/tuxguitar/app/tools/custom/practice/
├── calibration/                             # NEW package
│   ├── CalibrationPiece.java (loads the shipped score, its events), EventJudge.java, CalibrationScorer.java, ProfileSearch.java
│   ├── CalibrationFlow.java (pure state machine), CalibrationController.java (live listening + worker thread)
├── ui/CalibrationWindow.java, ui/ProfileDot.java      # NEW
├── ui/InputSelectorPanel.java               # + the Calibrate and Forget buttons and the dot
└── ui/AudioRecognitionOption.java, session/FollowSettings.java   # the two writers: they read and write getDefault()
desktop/TuxGuitar-practice/share/calibration/guitar.tg   # the shipped copy of basic_test.tg (a classpath resource in the plugin jar)
desktop/TuxGuitar-practice/share/lang/messages{,_fr,_es}.properties   # the texts
```

**Structure Decision.** Everything that can be pure lives in two new packages (`audio.engine.calibration`, `practice.calibration`) with no widget; the UI is a thin window and a dot; the one edit of existing behaviour is the store's layer.

## Phases (each leaves the build green; each is committed alone)

| Phase | Content |
|---|---|
| P0 | Baseline (reuse spec 012's), **the owner records `basic_test.tg` on the built-in microphone** with a small tool (outside git) [HUMAN], the number of events of the piece counted, the committed built-in corpus judged with the default (figures kept), the unverified points of R3 and R4 checked |
| P1 | **Profile model and the store's layer:** `ProfileParameters`, `DeviceKey`, `RecognitionProfile`, `ProfileState`, `ProfileLibrary` with its own persistence; `getDefault()` and the effective `get()`; every writer moved to `getDefault()`; tests first; with no profile all figures equal |
| P2 | **Scoring and search (pure, offline):** `EventJudge` moved to production from the test judge, `SoundSegmenter`, `CalibrationScorer`, `ProfileSearch`; tests on synthetic signals, the 22-note corpus, and the owner's recording (skip when absent) |
| P3 | **Listening mode:** `HopRecorder`, `SoundCounter`, the quality check, timeout and cancel; tests with the device-less engine |
| P4 | **The flow and the window:** `CalibrationFlow` (total table with `OFFER`, `REFUSED`, `CHOICE`), `CalibrationWindow` (one window, modal during the search, measures at the quality question, the result with rates), the offer for every device |
| P5 | **The dot, the buttons and the activation:** `ProfileDot` (grey, orange, green, red, the warning sentence), *Calibrate* and *Forget the calibration* in the Input tab, `ProfileActivator` created with the engine (applies the profile for the selected device at each input change and at start, to the tuner and the practice modes alike), persistence across a restart |
| P6 | Validation (independent measure, reported honestly), texts in three languages, documentation, `STATUS.md`, the build and **the owner's trial** [HUMAN] |

**Existing tests stay unchanged**: new tests only, new engine code only in `calibration`, `PracticeWindow`, `AppPracticeHost`, `PracticeSession` and `DatasetJudge` not edited (research R11.10).

Tests are written first and seen failing; the whole default suite runs after every phase (after a clean compile).

## Complexity Tracking

| Item | Why it is needed | Simpler alternative rejected |
|---|---|---|
| A second layer in the settings store | one place to choose profile or default; twelve readers unchanged | a profile manager every reader asks |
| A separate profile file | the store rewrites its own file and drops other keys | storing profiles in the store's file |
| Judging each sound alone against its event | independent of earlier misses; a boolean per event | the Wait-mode whole-stream judge (stuck on the first miss) |
| A negative control in the objective | a loose setting that accepts everything must not win | counting accepted events only |
| One window for the steps | decided by the owner (spec Clarifications), to be tried | separate windows as first described (the fallback if the trial says so) |
