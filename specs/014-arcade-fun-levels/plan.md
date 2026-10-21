# Implementation Plan: Arcade made fun: a more permissive recognition and five levels

**Branch**: `feature/interactive-practice` (spec directory `014-arcade-fun-levels`) | **Date**: 2026-10-20 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/014-arcade-fun-levels/spec.md` (clarified with the owner on 2026-10-20).

## Summary

Two additions, both **Arcade only**, both built on what exists:

1. **A leniency** (three positions: like Practice, fun as the default, very permissive). It is a set of relaxations of existing recognition parameters (confidence, stability time, the chord evidence, minimum partials and confirmation time; **not** the acceptance in cents, which the measure showed to lose a right note, and none of them makes the verdict quicker: research R6-ter) applied to the *effective* settings (default + calibration profile) **when an Arcade run is prepared**, plus a wider on-time window. At "like Practice" nothing changes. Follow, Wait, the tuner and the calibration never see it.
2. **Five levels**: a pure filter that keeps a share of the notes and of the chords of the track the player performs, always the same, the first of each kind kept and the others spread evenly after it (kept rank *i* of *k* among *n*: `floor(i * n / k)`), producing a smaller `PracticeSequence` before the run is built. Everything downstream (the timeline, the judging, the scene, the score, the result) already works from the sequence, so the sounds not kept simply do not exist for the run. They are **heard in the backing**: a small player sends them to the player's MIDI output at their written time (research R10), which is the one new piece of machinery of this feature.

The level and the leniency are remembered in a small store of their own (like the default-input store), and the level is chosen in the **start window** that every Arcade start already shows (spec 010), so the player can choose it by hand at every launch.

## Technical Context

**Language/Version**: Java; `TuxGuitar-practice` and `TuxGuitar-audio-engine` compile at release 11 (CLAUDE.md); no `common/` code is touched.

**Primary Dependencies**: none new. Reuses `RecognitionSettings` / `RecognitionParameters` / `SettingsOverlay` (engine), `PracticeParameters`, `FollowStarter`, `TimingTolerance`, `PracticeSequence`, `PreRunWindow`, `FollowPanel`, `ArcadeResultText`.

**Storage**: the level and the leniency position in a small configuration module of their own (the application's configuration manager, as `InputDefaultStore` and the profile library do); **nothing is added to `RecognitionParameters`**, so the Configuration page, the store file and every test that lists the parameters are untouched.

**Testing**: JUnit 5 in `TuxGuitar-practice/test` (pure filter, the leniency overlay, the store, the texts, source-structure checks of the Arcade start path, native tests with `-P swt-gui-tests` for the start window); a gated measure on the recorded performances. Existing tests stay unchanged (owner's rule).

**Target Platform**: macOS Apple Silicon validated; the code is toolkit-neutral (written against the abstract UI toolkit).

**Project Type**: desktop application, plugin module.

**Performance Goals** (spec SC-001, SC-002): verdict not slower than Follow's and no right note lost, at most 4 of the 98 negatives accepted at the default position; measured, reported, not assumed.

**Constraints**: nothing changes for Follow, Wait, the tuner, the calibration; at "like Practice" and level 5 every figure equals the base bit for bit; deterministic; the leniency never makes a setting *stricter* than the player's own; no recognition algorithm changes.

**Scale/Scope**: one overlay class, one filter, one store, three UI additions (start window row, leniency line in the Arcade preparation, two lines in the result), 3 languages.

## Constitution Check

*GATE: passes before research and again after design.*

| Principle | Check | Result |
|---|---|---|
| I Preserve TuxGuitar Compatibility | no file format, no core file touched; new configuration keys are additive and ignored by older versions | pass |
| II Extend Before Modifying Core | only the fork's practice module (the removed sounds go through the player's MIDI output as `MidiClickPlayer` does; a core change would be justified in R10 first); the engine is untouched; `PracticeSequence` gets one method as `onlyEvent` did (spec 013); `FollowStarter` gets an overload, its signature stays | pass |
| III Evidence-Based Architecture | each decision cites a file (research R1 to R9); the one thing not read to the end, the exact constraints of the source-structure tests on `PracticeWindow`, is a first task | pass |
| IV Spec-Driven Development | specify, clarify (three questions), plan in order; this plan before any code | pass |
| V Incremental Delivery | phases: pure parts (filter, overlay, store), then the start path, then the UI, then the measure; each green | pass |
| VI Real-Time Audio Correctness | no change on the analysis path: the overlay is computed once when a run is prepared, the filter once per start; nothing is allocated per hop. The removed sounds are sent to the MIDI output from a scheduler of their own, never from the analysis thread, never blocking it | pass, to confirm by the spike |
| VII Separable, Testable Audio Logic | the filter, the overlay and the store are pure and tested without UI or hardware | pass |
| VIII Deterministic, Explainable | fixed values per position, a printed table; the same performance gives the same result; no randomness in the levels | pass |
| IX Cross-Platform Conscious | no platform code; validated on macOS only, stated | pass |
| X No Unrelated Refactoring | `FollowSettings`, `TimingTolerance` and the judging are not reshaped; the new code sits beside them | pass |

## Project Structure

### Documentation (this feature)

```text
specs/014-arcade-fun-levels/
├── plan.md              # this file
├── research.md          # Phase 0: decisions R1 to R9, with the files read
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1
├── contracts/
│   ├── arcade-leniency.md
│   └── arcade-levels.md
└── tasks.md             # /speckit-tasks (not made here)
```

### Source code (what the plan adds or touches)

```text
desktop/TuxGuitar-practice/src/app/tuxguitar/app/tools/custom/practice/
├── arcade/core/ArcadeLevel.java            # NEW: levels 1..5, note share, chord share
├── arcade/core/ArcadeLevelFilter.java      # NEW: the even, fixed choice of the sounds kept
├── arcade/core/ArcadeLeniency.java         # NEW: the three positions and their overlay on RecognitionSettings, and the window factor
├── arcade/core/ArcadeChoices.java          # NEW: the level and the position, remembered (own configuration module)
├── session/RemovedSoundsPlayer.java        # NEW (R10): plays the sounds a level removed, through the player's MIDI output, in step with the run
├── model/PracticeSequence.java             # + keeping(...) (one method, like onlyEvent)
├── session/FollowStarter.java              # + prepare(...) overload with the window given (the existing one delegates)
├── ui/PracticeWindow.java                  # startArcade: filter and overlay applied; at most a few lines (see R8)
├── ui/PreRunWindow.java / StartFlow ...    # the level row for an Arcade start (constructor variant; the existing one unchanged)
├── ui/FollowPanel.java                     # the leniency line in the Arcade section
├── arcade/ui/ArcadeResultText.java         # the level and the position in the result
└── share/lang/messages{,_fr,_es}.properties# new texts
desktop/TuxGuitar-practice/test/...         # new tests only
```

**Structure Decision**: all the new logic lives in `arcade/core` (pure, no toolkit); the UI pieces are thin.

## Phases

*Numbering: these phases P0 to P5 and P2b are not the phases of `tasks.md`; the correspondence is written at the top of its Dependencies section (the measure of the leniency comes before the levels there).*

| Phase | Content |
|---|---|
| P0 | Baseline (the default suite, the recognition reports, the replay figures of Follow for SC-001), and **read the constraints** of the source-structure tests on `PracticeWindow` and the start path (R8) |
| P1 | `ArcadeLevel`, `ArcadeLevelFilter`, `PracticeSequence.keeping`: pure, tests first (counts, even spread, determinism, chords whole, at least one sound, a piece with no chords or only chords, the start index) |
| P2 | `ArcadeLeniency` (overlay + window factor) and `ArcadeChoices` (store), tests first; the measure of SC-001 and SC-002 on the recorded performances to **fix the values** of the two permissive positions |
| P2b | **A spike, then `RemovedSoundsPlayer`** (FR-015, SC-007, research R10): how a note is sent to the track's channel and ended, how its time comes from the run's clock, tempo percentage, pause, stop, restart; tests first with a fake output; **the spike ends in a go or a no-go for the owner** |
| P3 | The Arcade start path: the filter and the overlay applied in `startArcade`, `FollowStarter` overload, restart and replay keep the level |
| P4 | UI: level row in the start window, leniency line in the Arcade preparation, level and position in the result; texts in three languages |
| P5 | Validation (measures reported honestly), documentation, STATUS, the build, **the owner's trial** (is it fun?) |

## Complexity Tracking

No constitution violation. **The risk to watch is R10** (the removed sounds heard in the backing): it is new machinery in step with the run's clock. It stays in the practice module and uses the player's existing MIDI output as `MidiClickPlayer` does (no core change planned, to be confirmed by the spike); if the spike shows it cannot be made to keep time, the fallback is that the removed sounds are silent (the earlier choice), which **needs the owner's agreement**. The other choice to watch: the level is asked in the start window (R7) which also carries the tuning question and the strum check; if that window cannot take a row without breaking its native layout tests, the fallback is the line in the Arcade preparation plus "the last level chosen" at a one-click start, which weakens FR-006 and needs the owner's agreement.
