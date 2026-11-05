# Implementation Plan: every chord the owner plays right is accepted, a clearly wrong chord refused, with a chord leniency the learner can set

**Branch**: `feature/interactive-practice` | **Date**: 2026-11-05 (version 2) | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/024-lenient-chords/spec.md`

**Version 2** replaces the plan of 2026-11-05 (commit `13b3d6bd6`), after the diagnostic (phases 1–2, done), the owner's answers, and the owner's third series of chords.

## Summary

- **The per-string chord check cannot reach the target.** On the first two series and on the owner's new third series, every setting of it either refuses right chords or accepts clearly wrong ones (research R1): 17/25 right today on the third series, 24/25 with one string short but 25 clearly wrong accepted.
- **The new method:** the chord's *pitch-class profile*. The level of each note name (C, C#, … B, the octaves summed) is summed over the strum and compared with the expected chord's note names; the chord is accepted when they match enough (R3). Offline, on the third series, it accepts 25/25 right chords and 0 of 408 clearly wrong ones, with a wide margin.
- **It runs in Java on the semitone bands both engines already give**, so both engines judge the same way and the native interface does not change (R4).
- **Every new value is an external parameter** of the recognition settings, read at run time (FR-013, R5). The engines get one document: how they work, their interface, every parameter, and the constants still fixed (FR-014, R10).
- **The reference recordings are the owner's third series** (R2). The spec still counts the first two series (42 chords) and must be amended through `/speckit-clarify` before tasks.
- **The levels** *Strict*, *Normal* and *Lenient* are sets of chord settings chosen by a measured sweep. *Normal* is the default values (R6, R7). The setting sits in the *Mode* tab (R9).
- **Order:** a Java probe of the method on the sessions' own spectra first, with a stop condition. Then the rule in every mode, tests first. Then the levels and the setting. Then the documentation and the gate.

## Technical Context

**Language/Version**: Java, release 11 for the fork modules (`TuxGuitar-audio-engine`, `TuxGuitar-practice`); JDK 21 locally. The native crate (Rust) is unchanged.

**Primary Dependencies**: none new in the product. `PracticeSession`, `FollowSession`, `ChordMatcher`, `PolyphonicAcceptance`, `SpectrumFrame`, the settings (`RecognitionParameters`, `RecognitionSettings`, `RecognitionSettingsStore`) and TuxGuitar's UI toolkit. The offline tools (`misc/owner-chords-*.py`, librosa, Basic Pitch) are evidence only, never shipped.

**Storage**: the existing `tuxguitar-audio-recognition` settings file. One option is added to `recognition.chord.completeness`, and five new keys (R5). `VERSION` stays 1. Untouched spec 023 files are migrated on read (R7).

**Testing**: JUnit 5.
- Unit tests: the profile and its match (`ChordProfile`), `ChordMatcher` with the profile rule, the session rule and its feedback, `ChordLeniency`, the migration.
- Dataset tests, tagged `dataset`, run with `-Dtuxguitar.recognition.engine=java|native` and `-Dtuxguitar.owner.chords=<folder>`: the probe, `OwnerChordsProfileTest`, and the committed benchmarks.
- An SWT GUI test for the *Mode* tab control (`-P swt-gui-tests`).

**Target Platform**: macOS (Apple Silicon) validated; Linux and Windows built by CI with the same code.

**Project Type**: desktop application plugin (the practice module) and its audio engine module.

**Performance Goals**: per frame, about 50 band levels summed into 12 values and one cosine. Nothing is allocated per frame, and nothing runs on the audio callback.

**Constraints**:
- With *Normal* and both engines, 25/25 right and 0/408 clearly wrong on the third series.
- At least 8 and 18 accepted on the first two series, the committed minimum.
- No dataset guard line lower.
- Committed tests change only under the approved exceptions (R7).
- Nothing is added to the JNI contract.
- Deterministic rules only (constitution VIII).

**Scale/Scope**:
- 25 takes, 625 judgements on the third series, and the 42 takes of the first two series as a guard;
- five dataset benchmarks;
- two new pure classes, one rule option, one panel;
- five new settings, in the chords section and a new chord details section;
- about 20 strings in three languages;
- one architecture document.

## Constitution Check

| Principle | How this plan meets it |
|---|---|
| I. Compatibility | TuxGuitar's behaviour and formats are untouched. The recognition file keeps its format and version. Old files load: untouched ones get *Normal*, changed ones keep their values (R7, tests). |
| II. Extend before core | Changes are only in the fork modules `TuxGuitar-practice` and `TuxGuitar-audio-engine`. No core module, and no native change. |
| III. Evidence | Every decision cites the source read (R0–R11). The method is chosen on measured figures (R1, R3), and measured again in Java before any rule changes (R6). |
| IV. Spec-driven | The spec is clarified. One amendment is pending through `/speckit-clarify` (the reference series, R2), before tasks. |
| V. Incremental | Four phases left, each testable alone: probe, rule and *Normal*, levels and setting, polish (documentation and gate). |
| VI. Real-time audio | Nothing on the audio threads. The profile is computed in the session's frame handling, with nothing allocated per frame. |
| VII. Separable logic | `ChordProfile`, `ChordLeniency` and the migration are pure and unit-tested. |
| VIII. Deterministic | A sum, a cosine and a threshold, no learned model. The method is the classic pitch-class profile (R3). |
| IX. Cross-platform | No platform code. Validated on macOS; Linux and Windows are built by CI only, with no "only on macOS" line. |
| X. No unrelated refactoring | The per-string rule stays as an option (`all-verifiable`). `DatasetJudge` and the committed benchmarks are left as they are. New instruments are new test classes. |
| XI. Build and tests | Every phase ends with the full gate. Committed tests change only under the exceptions the owner approved (R7). |
| XII. Truthful reporting | Figures before and after, both engines, are kept in `benchmark/`. The owner's gate is sheet F15. A stop condition reports instead of lowering a target. |
| XIII. Honest semantics | The report says that voicings cannot be told apart. A refused chord lists the note names its profile lacks, not strings it cannot know. |
| XIV. Learner-centred | Each level says what it accepts and what it risks. A refusal names the missing note names. The input warnings stay. |
| Texts | English, French and Spanish in the plugin's `share/lang` files. |

**Gate:** passes. The spec was amended through `/speckit-clarify` (the reference series, the first two series a guard, the level's values, voicings, the bass). Re-checked after design: passes. Stop condition A (R6) goes to the owner instead of being decided here.

## Project Structure

### Documentation (this feature)

```text
specs/024-lenient-chords/
├── plan.md               (version 2)
├── research.md           (version 2)
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── chord-leniency.md
│   └── chord-profile-rule.md
├── benchmark/            (before-*, diagnostic.md, owner-chords-3/ done; probe.md, levels.md, after-*, comparison.md to come)
└── tasks.md              (to rebuild with /speckit-tasks: T001–T005 done)
```

### Source Code (repository root)

```text
desktop/TuxGuitar-audio-engine/src/main/java/app/tuxguitar/audio/engine/settings/
├── RecognitionParameter.java         + Section.CHORD_DETAILS, the values that shape the profile (R5)
├── RecognitionParameters.java        + option "profile" of CHORD_COMPLETENESS (default), + CHORD_PROFILE_MATCH, _NAMES and CHORD_ATTACK in the chords section, CHORD_PROFILE_TOP and _SETTLE in the chord details section (R5)
├── RecognitionSettings.java          fromStrings: migration of untouched spec 023 chord values (R7)
└── ChordLeniency.java                new: the history of past level values for the migration (phase 4), then the levels, derived and applied (phase 5) (R7)

desktop/TuxGuitar-practice/src/app/tuxguitar/app/tools/custom/practice/
├── session/ChordProfile.java         new, pure: the profile of a frame, its sum, the match, the weakest names (R3)
├── session/ChordMatcher.java         Parameters + rule and profile values; check by profile when the rule says so
├── session/PracticeParameters.java   chordParameters() passes the new values
├── session/PracticeSession.java      onChordFrame (Wait): one profile, reset at each arming, summed per usable frame; the attack path for chords per R6
├── session/FollowSession.java        Follow and Arcade: one profile per chord candidate of a PendingOnset, summed per usable frame (R4)
├── ui/ChordLeniencyPanel.java        new: label, drop-down and the level's line (R9)
└── ui/PracticeWindow.java            the panel in the Mode tab

desktop/TuxGuitar-practice/share/lang/messages{,_fr,_es}.properties

desktop/TuxGuitar-practice/test/app/tuxguitar/app/tools/custom/practice/
├── dataset/OwnerChordsProfileProbeTest.java    new (dataset): the sweep of R6, both engines
├── dataset/OwnerChordsProfileTest.java         new (dataset): 25/25 and 0 clearly wrong with the defaults
├── dataset/ChordLeniencyLevelsTest.java        new (dataset): the levels' figures and order (SC-004)
├── dataset/ChordLeniencyComparisonTest.java    new (benchmark): before and after, both engines (R8)
├── session/ChordProfileTest.java               new (unit)
├── session/ChordMatcherProfileTest.java        new (unit)
├── session/ChordProfileParametersTest.java     new (unit): the values reach the run, Follow and Arcade (FR-009, FR-013)
├── session/PracticeSessionChordProfileTest.java new (unit): arming, summing, the window, the feedback
├── session/FollowSessionChordProfileTest.java new (unit): Follow and Arcade use the profile rule, per candidate
└── ui/ChordLeniencyPanelSwtTest.java           new (swt-gui)

desktop/TuxGuitar-audio-engine/src/test/java/app/tuxguitar/audio/engine/settings/
├── ChordProfileSettingsTest.java               new: keys, defaults, bounds, round trip
├── ChordProfileMigrationTest.java              new: untouched spec 023 files read as Normal
├── ChordLeniencyTest.java                      new
└── EngineDocumentationTest.java                new: every key documented (FR-014)

docs/architecture/recognition-engines.md        new (FR-014, R10)
specs/test-sheets/F15-accords-tolerants.md      the owner's gate
```

**Structure Decision**: the existing practice and audio-engine modules. No new module, and no change to the native crate.

## Phases

The numbers are those of tasks.md.

- **Phases 1–2, done:** the figures before with both engines; the diagnostic (US3). Since then, the strum hold probe and the third series with its figures (`benchmark/owner-chords-3/`).
- **Phase 3, probe (gate of the method):** `OwnerChordsProfileProbeTest` with both engines and both arming references (Wait's and Follow's). The sweep is written to `benchmark/probe.md`. Stop condition A applies here (R6).
- **Phase 4, US1 (P1):** `ChordProfile` and the profile rule in `ChordMatcher`, `PracticeSession` and `FollowSession`, tests first and seen red. Then *Normal* as the defaults, the migration, `OwnerChordsProfileTest`, the two `wrong.max` lines, and the guards after (R8, stop condition C).
- **Phase 5, US2 (P2):** `ChordLeniency`, *Strict* and *Lenient* from the sweep, the panel in the *Mode* tab, the expert tips, texts in three languages, the SWT test.
- **Phase 6, polish:**
  - `docs/architecture/recognition-engines.md` (FR-014), after its test;
  - the practice guide in three languages;
  - sheet F15;
  - draft release notes;
  - full gate;
  - `final-report.md`;
  - STATUS.md.

## Complexity Tracking

| Item | Why needed | Simpler alternative rejected because |
|---|---|---|
| A second chord rule, kept beside the per-string one | The profile reaches the target where the per-string rule cannot (R1, R3); the old rule stays selectable (`all-verifiable`) for comparison and for a learner who prefers it | Deleting the per-string rule removes the committed tests' subject and the expert option |
| The level derived from the values, not stored | Committed tests pin the version and the round trip of the store (R7) | A stored level that overrides values breaks those tests and Follow's and Arcade's relaxation |
| Migration on read | Old files hold every value, so an untouched learner would keep spec 023's rule (FR-008) | Bumping `VERSION` breaks `valuesSurviveARestart` |
