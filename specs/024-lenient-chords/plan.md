# Implementation Plan: the owner's chords accepted at least 9 times in 10, with a chord leniency the learner can set

**Branch**: `feature/interactive-practice` | **Date**: 2026-11-05 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/024-lenient-chords/spec.md`

## Summary

The work starts with a diagnostic of the owner's 16 refused chords and 13 wrong acceptances (research R1).

- **A counterfactual instrument.** For each take it records the evidence of both acceptance paths and which single relaxation would flip the outcome, with both engines.
- **The committed report already points at two causes.**
  - 13 of the 16 refusals are in the series recorded with the input saturated.
  - 12 of the 13 wrong acceptances are the same chord in another voicing.

The chord rule then gets only the levers the diagnostic justifies (R3). They are decisions in Java on evidence both engines already carry, so both engines judge the same way and the JNI contract is untouched:
- L1, a chord one string short, as a new option of the existing "Strings required" setting;
- L4, chord frames judged when clipped, as a new chord flag.

The three levels *Strict*, *Normal* and *Lenient* are sets of chord settings:
- *Normal* is exactly the default values. A fresh learner, "Restore defaults" and the committed benchmarks all get it.
- The level is derived from the settings, so a change by hand shows *Custom* (R2).
- A migration moves untouched spec 023 files to *Normal* (R6).
- The setting sits in the *Mode* tab (R6).

The levels are chosen by a measured sweep with a written rule (R4). The guards on every dataset are measured before and after, with both engines (R5). If no configuration meets 38 accepted and at most 13 wrong on both engines, the work stops and reports to the owner.

## Technical Context

**Language/Version**: Java, release 11 for the fork modules (`TuxGuitar-audio-engine`, `TuxGuitar-practice`); JDK 21 locally. The native crate (Rust) is unchanged.

**Primary Dependencies**: none new. The practice session (`PracticeSession`, `ChordMatcher`, `PolyphonicAcceptance`), the settings (`RecognitionParameters`, `RecognitionSettings`, `RecognitionSettingsStore`) and TuxGuitar's UI toolkit (`UIDropDownSelect`, wrap label).

**Storage**: the existing `tuxguitar-audio-recognition` configuration file. One option is added to `recognition.chord.completeness`, and at most one new key (L4). `VERSION` stays 1. There is a migration of untouched old files on read.

**Testing**: JUnit 5.
- Unit tests: `ChordMatcher`, `ChordLeniency`, the migration and the session rule, in `TuxGuitar-practice/test` and `TuxGuitar-audio-engine/src/test/java`.
- Dataset benchmarks, tagged `dataset`, run with `-Dtuxguitar.recognition.engine=java|native`.
- An SWT GUI test for the *Mode* tab control (`-P swt-gui-tests`).

**Target Platform**: macOS (Apple Silicon) validated; Linux and Windows built by CI with the same code.

**Project Type**: desktop application plugin (the practice module) and its audio engine module.

**Performance Goals**: no new cost per frame beyond a few comparisons in `ChordMatcher.Result` and `onChordFrame`. Nothing runs on the audio callback.

**Constraints**:
- At least 38 of 42 accepted and at most 13 wrong, with *Normal* and both engines, and per series within the committed baseline.
- No dataset guard line lower.
- Committed tests unchanged: R2 explains why *Normal* must equal the defaults, and that an exception would need the owner.
- Nothing is added to the JNI contract.
- Deterministic rules only (constitution VIII).

**Scale/Scope**:
- 42 takes and 42 controls;
- five dataset benchmarks;
- one pure class, one panel, at most two settings touched;
- about 15 strings in three languages.

## Constitution Check

| Principle | How this plan meets it |
|---|---|
| I. Compatibility | TuxGuitar's own behaviour and formats are untouched. The recognition file keeps its format and version. Old files load: untouched ones get *Normal*, changed ones keep their values (R6, tests). |
| II. Extend before core | Changes are only in the fork modules `TuxGuitar-practice` and `TuxGuitar-audio-engine`. No core module. |
| III. Evidence | Every decision cites the source read (R0 to R6). The diagnostic gives the evidence for the levers before any rule changes (FR-010). |
| IV. Spec-driven | Spec and clarifications are done; tasks come next. |
| V. Incremental | Three phases, each testable alone: diagnostic (US3), rule and *Normal* (US1), levels and setting (US2). |
| VI. Real-time audio | Nothing on the audio threads. The rule runs in the session's frame handling, as today. |
| VII. Separable logic | `ChordLeniency`, the completeness rule of `ChordMatcher` and the migration are pure and unit-tested. |
| VIII. Deterministic | Counting rules on existing evidence. No learned model. |
| IX. Cross-platform | No platform code. Validated on macOS; Linux and Windows are built by CI only, with no "only on macOS" line. |
| X. No unrelated refactoring | `DatasetJudge` and the spec 023 benchmarks are left as they are. New instruments are new test classes. |
| XI. Build and tests | Every phase ends with the full gate. No committed test is changed (R2). |
| XII. Truthful reporting | Figures before and after, both engines, kept in `benchmark/`. The owner's gate is sheet F15. |
| XIII. Honest semantics | Unverifiable strings (an octave, the same note) stay "not checked separately" whatever the level (edge case, a test). The report says that the voicing pairs cannot be told apart from audio. |
| XIV. Learner-centred | Each level says what it accepts and what it risks. Clipping warnings stay. |
| Texts | English, French and Spanish in the plugin's `share/lang` files. |

Gate: passes. Re-checked after design: passes. The two stop conditions, a default that must move or evidence the engine lacks, go to the owner instead of being decided here.

## Project Structure

### Documentation (this feature)

```text
specs/024-lenient-chords/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── chord-leniency.md
├── benchmark/            (before-*, after-*, diagnostic.md, levels.md, comparison.md; written during implementation)
└── tasks.md              (next: /speckit-tasks)
```

### Source Code (repository root)

```text
desktop/TuxGuitar-audio-engine/src/main/java/app/tuxguitar/audio/engine/settings/
├── ChordLeniency.java                new: the levels, derived and applied (R2)
├── RecognitionParameters.java        + option all-but-one of CHORD_COMPLETENESS, its default; + L4 flag if kept (R3)
└── RecognitionSettings.java          fromStrings: migration of untouched spec 023 chord values (R6)

desktop/TuxGuitar-practice/src/app/tuxguitar/app/tools/custom/practice/
├── session/ChordMatcher.java         Parameters + completeness; Result.isComplete with one string short (L1)
├── session/PracticeParameters.java   chordParameters() passes the completeness (and the L4 flag)
├── session/PracticeSession.java      onChordFrame judges a clipped frame when the flag says so (L4, only if kept)
├── ui/ChordLeniencyPanel.java        new: label, drop-down and the level's line (R6)
└── ui/PracticeWindow.java            the panel in the Mode tab

desktop/TuxGuitar-practice/share/lang/messages{,_fr,_es}.properties

desktop/TuxGuitar-practice/test/app/tuxguitar/app/tools/custom/practice/
├── dataset/OwnerChordsDiagnosticTest.java      new (dataset): the diagnostic (R1)
├── dataset/ChordLeniencyBenchmarkTest.java     new (dataset): the sweep and the levels (R4)
├── dataset/ChordLeniencyComparisonTest.java    new (benchmark): before and after, both engines (R5)
├── session/ChordMatcherCompletenessTest.java   new (unit)
└── ui/ChordLeniencyPanelSwtTest.java           new (swt-gui)

desktop/TuxGuitar-audio-engine/src/test/java/app/tuxguitar/audio/engine/settings/
├── ChordLeniencyTest.java                      new
└── ChordLeniencyMigrationTest.java             new

specs/test-sheets/F15-accords-tolerants.md      the owner's gate
```

**Structure Decision**: the existing practice and audio-engine modules. No new module, and no change to the native crate.

## Phases

1. **Before figures** (setup): the five dataset benchmarks with both engines at the spec 023 code.
2. **US3 (P3, done first because everything depends on it)**: the diagnostic instrument and `diagnostic.md`. Gate: each of the 16 refusals and 13 wrong acceptances has a cause. A stop condition is checked here (R3).
3. **US1 (P1)**: the levers kept, tests first. *Normal* is chosen by the sweep (R4) and made the default. The migration (R6). The guards after (R5). A stop condition is checked here (R4).
4. **US2 (P2)**: `ChordLeniency`, *Strict* and *Lenient* from the sweep, the panel in the *Mode* tab, the expert tips, texts in three languages, the SWT test.
5. **Polish**:
   - the practice guide in three languages;
   - sheet F15;
   - draft release notes;
   - full gate;
   - `final-report.md`;
   - STATUS.md.

## Complexity Tracking

| Item | Why needed | Simpler alternative rejected because |
|---|---|---|
| The level derived from the values, not stored | Committed tests pin the defaults, the version and the round trip of the store (R2) | A stored level that overrides values breaks those tests and Follow's and Arcade's relaxation |
| Migration on read | Old files hold every value, so an untouched learner would keep spec 023's rule (FR-008) | Bumping `VERSION` breaks `valuesSurviveARestart` |
