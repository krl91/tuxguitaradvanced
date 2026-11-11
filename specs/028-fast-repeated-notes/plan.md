# Implementation Plan: fast repeated notes recognised, early or late, and a suggestion to retune

**Branch**: `feature/interactive-practice` | **Date**: 2026-11-11 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/028-fast-repeated-notes/spec.md` (clarified, 10 answers in two sessions)

## Summary

- **Cause:** the attack detector does not report a note played again on a string that still rings. Its gate compares with the *maximum* level of the 100 ms before, which still holds the previous pluck 153 ms earlier. Spec 002 documented this limit and kept it (R1). Without an attack, Follow and Arcade judge the note `NO_ATTACK`.
- **The detector stays as it is.** Committed tests pin its defaults, and spec 002 measured false attacks with a lower gate (R2).
- **The method:** a *re-attack expected by the score*. Where the tab plays a pitch that already sounds, a rise of that pitch's own semitone band counts as its attack. It is computed in Java on the bands both engines already give, in `FollowSession` (Follow and Arcade) and `PracticeSession` (Wait), so every mode and both engines judge the same way. A rise of every band at once (a scratch) is not one. There is no native change (R3).
- **Early and late:** an attack inside the windows of two notes of the same pitch goes to the nearest one, not to the first one still open. No window gets wider (R4).
- **Out of tune:** the margin stays at ±35 cents. A new `TuningDrift` suggests retuning when the notes drift the same way (20 cents by default, a user setting in a new section, since *Notes* is full), in every mode. Wait and Follow are never stopped; Arcade pauses on a window (Yes: the tuner; No; More tolerant: +10 % for the run) (R5).
- **The offset test** becomes `OwnerOffsetTest`, skipped where the recording is absent and run on the owner's machine before each release. Its table goes into the release notes, and the release workflow refuses notes without it or below 100 % (R7).
- **Order:**
  1. the figures before;
  2. a probe that confirms the cause and sets the values, with a stop condition;
  3. the rule, tests first;
  4. the assignment of early and late attacks;
  5. the release check;
  6. the retune suggestion;
  7. documentation and gate.

## Technical Context

**Language/Version**: Java, release 11 for the fork modules (`TuxGuitar-audio-engine`, `TuxGuitar-practice`); JDK 21 locally. The native crate is unchanged.

**Primary Dependencies**: none new. `FollowSession` (and its `FollowParameters`), `PracticeSession` and `PracticeParameters`, `FollowStarter`, `ArcadeStarter`, `ArcadeLevelFilter`, `SpectrumFrame`, `RecognitionParameters`, `RecognitionSettings`, `PracticePalette`, `ArcadeController`, `PracticeTunerOpener`, `ConfirmWindow` (model), `RecognitionSettingsPanel`.

**Storage**: the existing recognition settings file, with ten new keys in two new sections (data-model.md). `VERSION` is unchanged; a missing key reads as its default.

**Testing**: JUnit 5.
- **Unit tests**: `FollowSessionReattackTest`, `PracticeSessionReattackTest`, `ArcadeReattackTest`, `FollowSessionNearestTargetTest`, `TuningDriftTest`, `FollowSessionTuningDriftTest`, `PracticeSessionTuningDriftTest`, `TuningDriftTextTest`, `ArcadeRetuneOfferTest`, `FollowSessionAcceptBoostTest`, `ArcadeRetuneFlowTest`, and the settings tests. The committed `FastRunFunctionalTest` (`REPEATED` runs, Wait) is the guard and the measure of repeats at 3–12 notes per second.
- **Dataset tests**: `RepeatedNotesProbeTest` (probe), `OwnerOffsetTest` (tag `owner-offset`) and the existing `dataset` and `benchmark` suites, with both engines.
- **SWT tests**: the threshold setting in the panel and Arcade's retune window, run once at the end (the owner's rule: SWT runs at the end, Mac unlocked).

**Target Platform**: macOS (Apple Silicon) validated; Linux and Windows are built by CI with the same code.

**Project Type**: a desktop application plugin (the practice module) and its audio engine module.

**Performance Goals**: per frame and per open expected repeat, two band reads and a minimum over at most 19 floats. Nothing is allocated per frame.

**Constraints**:
- 100 % on the DI recording from −70 to +70 ms, in Follow and in Arcade at each level 1 to 5 (and in Wait at offset 0), with both engines.
- No guard figure lower than before (SC-002).
- No committed test modified.
- No engine constant hard-coded; the re-attack values and the drift values are parameters (FR-007). No numeric `static final` in `PR/session` or `PR/arcade/core` that `SessionConstantInventoryTest` would not account for.
- Committed tests that constrain new settings stay green from the phase that adds them: sections (`RecognitionSectionsTest`), documentation (`EngineDocumentationTest`, `EngineDocumentationFactsTest`), help pages (`RecognitionControlsTest`), strings (`RecognitionStringsAllLanguagesTest`, `RecognitionSettingsPanelTest`). So the settings, their documentation rows, help rows and strings land together (phase 2).
- Arcade's scene, `SceneLabels` and `ArcadeRenderer` are unchanged: the suggestion is a window over the paused run.
- No JNI change.
- The recording is never committed.

**Scale/Scope**:
- one rule in `FollowSession` and in `PracticeSession`, one assignment change, two pure classes (`TuningDrift`, `ArcadeRetuneOffer`), one palette line, one modal window over the paused Arcade run and its flow;
- ten settings in two new sections;
- about 14 strings in three languages (the suggestion, the window's title, text and three buttons, the risk, the tuner unavailable, the settings' names and tips);
- two scripts with self-tests (the check and the release-notes verification), one release step, one workflow line and one test sheet.

## Constitution Check

| Principle | How this plan meets it |
|---|---|
| I. Compatibility | TuxGuitar's formats are untouched. The settings file keeps its version, and a missing key reads as its default. |
| II. Extend before core | The changes are in the fork modules only: `TuxGuitar-practice`, plus settings in `TuxGuitar-audio-engine`. No core module and no native crate change. |
| III. Evidence | Every decision cites the source it read (R0). The probe confirms the cause and sets the values before any rule changes (R1, R3). |
| IV. Spec-driven | The spec has been clarified (10 answers); the plan follows them. |
| V. Incremental | Seven phases, each testable alone, each ending with the gate. |
| VI. Real-time audio | Nothing runs on the audio threads. The rule runs in the sessions' frame handling, with preallocated rings. |
| VII. Separable logic | `TuningDrift` and the repeat detection per event are pure and unit-tested. |
| VIII. Deterministic | A band minimum, a rise in dB and a mean of cents: no learned model. |
| IX. Cross-platform | No platform code. CI builds Linux and Windows, with no "only on macOS" line. |
| X. No unrelated refactoring | The detector, its parameters and the leniencies are left as they are. New behaviour sits behind `reattack.enabled`. |
| XI. Build and tests | Full gate at the end of each phase. Committed tests are not modified. |
| XII. Truthful reporting | Figures before and after, both engines and both modes, are kept in `benchmark/`. The stop conditions report instead of lowering a target. |
| XIII. Honest semantics | The suggestion names the string *as written in the tab*, never a string found from the sound. A re-attack is evidence of a new pluck of the pitch, not of the string. |
| XIV. Learner-centred | Refusals keep their reasons. The suggestion says the cause is the tuning, not the playing, and how far off (about N cents, flat or sharp); Wait and Follow go on, Arcade pauses on a choice whose risk is stated. |
| Texts | English, French and Spanish in the plugin's `share/lang`. |

**Gate:** passes. Re-checked after design: passes. Stop conditions A (probe), B (a guard lower) and C (a committed refusal test that contradicts FR-007a) go to the owner.

## Project Structure

### Documentation (this feature)

```text
specs/028-fast-repeated-notes/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── reattack-rule.md
│   ├── retune-suggestion.md
│   └── owner-offset-test.md
├── benchmark/          (before-*, probe.md, after-*, comparison.md, offset tables)
└── tasks.md            (/speckit-tasks)
```

### Source Code (repository root)

```text
desktop/TuxGuitar-audio-engine/src/main/java/app/tuxguitar/audio/engine/settings/
├── RecognitionParameter.java     + Section.REATTACK, Section.TUNING_DRIFT (after the existing sections)
└── RecognitionParameters.java    + reattack.{rise,before,spread,octave,enabled}, tuning.drift-{threshold,notes,agreement,quiet}, tuning.arcade-boost

desktop/TuxGuitar-practice/src/app/tuxguitar/app/tools/custom/practice/
├── session/FollowSession.java    expected repeats per event (full sequence), band rings, re-attacks into PendingOnset, nearest-target assignment, TuningDrift fed; FollowParameters reads the new values
├── session/PracticeSession.java  Wait: the re-attack of the awaited event, TuningDrift fed
├── session/PracticeParameters.java  the new values for Wait
├── session/TuningDrift.java      new, pure
├── ui/PracticePalette.java       the suggestion on a line of its own (Wait and Follow), apart from showFollowNotice's
├── arcade/core/ArcadeRetuneOffer.java  new, pure: the window's states and the run's boost
├── arcade/ui/ArcadeRetuneWindow.java   new: the modal window (model: ui/ConfirmWindow), three buttons, texts
├── ui/ArcadeRetuneFlow.java        new: pause → window → tuner (lift track, TUNER_OPEN) → resume, testable on fakes
├── ui/PracticeWindow.java          (owns ArcadeController) wires the flow
    (FollowSession.java also gains setNoteAcceptCents for the run, Arcade's boost)

desktop/TuxGuitar-practice/share/lang/messages{,_fr,_es}.properties
desktop/TuxGuitar/share/help/tools_audio_recognition{,_fr}.html   a row per new setting, an <h2> per new section

desktop/TuxGuitar-practice/test/app/tuxguitar/app/tools/custom/practice/
├── dataset/RepeatedNotesProbeTest.java     new (dataset): R1 per event, the sweep of R3, both engines, both modes
├── dataset/OwnerOffsetTest.java            new (owner-offset): contracts/owner-offset-test.md
├── dataset/TuningDriftReplayTest.java      new (dataset): SC-006
├── session/FollowSessionReattackTest.java  new (unit): SC-004, SC-005, a scratch is not a re-attack, enabled off = before
├── session/PracticeSessionReattackTest.java new (unit): Wait
├── session/ArcadeReattackTest.java      new (unit): Arcade, a level that removes notes
├── session/FollowSessionTuningDriftTest.java new (unit)
├── session/PracticeSessionTuningDriftTest.java new (unit)
├── ui/TuningDriftTextTest.java          new (unit)
├── arcade/core/ArcadeRetuneOfferTest.java new (unit): the states, the boost, the cap, the reset per run
├── session/FollowSessionAcceptBoostTest.java new (unit): a wider acceptance from the next frame, no verdict changed
├── ui/ArcadeRetuneFlowTest.java        new (unit): the order pause, tuner, resolve, resume; Yes without tuner = No
├── arcade/ui/ArcadeRetuneWindowSwtTest.java new (swt-gui): the three buttons, run at the end
├── dataset/RepeatedPlucksEnginesTest.java new (dataset): two synthetic plucks 100 ms apart, held notes, through both engines
├── session/FollowSessionNearestTargetTest.java new (unit): R4
├── session/TuningDriftTest.java            new (unit)
└── ui/TuningDriftSettingSwtTest.java       new (swt-gui)

desktop/TuxGuitar-audio-engine/src/test/java/app/tuxguitar/audio/engine/settings/
└── ReattackSettingsTest.java               new: keys, defaults, bounds, round trip

misc/owner-offset-check.sh, misc/test-owner-offset-check.sh            new
misc/verify-offset-table.sh, misc/test-verify-offset-table.sh          new
.github/workflows/release.yml               the self-tests listed, the notes required and verified (validate job, fetch-depth 0)
docs/VERSIONING.md                          "Before a release" step
docs/architecture/recognition-engines.md    the ten rows (phase 2), the method (phase 7)
docs/PRACTICE{,-FR,-ES}.md                  repeated notes, the retune suggestion
specs/test-sheets/F18-notes-repetees.md     the owner's gate
```

**Structure Decision**: the existing practice and audio-engine modules. No new module, and no native change. The untracked `OffsetReplayProbeTest` is the starting point of `RepeatedNotesProbeTest` and `OwnerOffsetTest`, then deleted.

## Phases

1. **Before:** run the dataset and benchmark suites, both engines, into `benchmark/before-*`. Also run the offset table in Wait (offset 0), Follow and Arcade (each level), from −100 to +100 ms (the Arcade and early figures were never measured).
2. **Probe, the gate of the method, and the settings:** `RepeatedNotesProbeTest` writes `benchmark/probe.md`. Then the ten settings with their documentation rows, help rows and strings.
   - Per missed event: the level rise, the flux, the band rise, and the event an attack went to (R1, R4).
   - A sweep of `reattack.rise`, `reattack.before`, `reattack.spread` and `reattack.octave`, Java and native, the three modes (Arcade at each level), all offsets, plus the guard datasets.
   - Stop condition A applies here (R3).
3. **US1 (P1), the re-attack:** tests first and seen red (SC-004, SC-005, a scratch, `enabled` off = before), in Follow, Arcade and Wait. Then the rule in `FollowSession` and `PracticeSession`, with the values from the probe. Guards after (stop condition B).
4. **US2 (P1), early and late:** `FollowSessionNearestTargetTest` red, then the assignment change. The offset table from −70 to +70 ms at 100 %, Follow and Arcade (each level), both engines.
5. **US3 (P2), every version:** `OwnerOffsetTest` from the probe, `misc/owner-offset-check.sh`, `misc/verify-offset-table.sh` in the release workflow, the step in `docs/VERSIONING.md`. Run on the DI recording, with the table in `benchmark/`.
6. **US4 (P3), the retune suggestion:** `TuningDriftTest` red, then `TuningDrift`, fed in the three modes, the palette (Wait, Follow), Arcade's pause and window with its three choices and the run's boost, texts in three languages, the threshold's SWT test (written here, run at the end), and `TuningDriftReplayTest`.
7. **Polish:**
   - the engines document (FR-007) and the practice guides;
   - sheet F18 and draft release notes;
   - the full gate, then the SWT tests once (Mac unlocked), then `final-report.md` and `STATUS.md`;
   - delete the untracked probe.

## Complexity Tracking

| Item | Why needed | Simpler alternative rejected because |
|---|---|---|
| A second way to find an attack, beside the detector | The detector misses repeats by design, and its defaults are pinned by committed tests (R1, R2) | Lowering its gate or using the fast detector creates false attacks on sustain (spec 002) and breaks committed tests |
| `reattack.enabled` | The verdicts of before stay reachable for comparison and for the guards | Without it, a regression cannot be told from a change of method |
