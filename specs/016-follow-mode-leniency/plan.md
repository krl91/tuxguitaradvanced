# Implementation Plan: Follow the tempo, made playable

**Branch**: `feature/interactive-practice` (spec directory `016-follow-mode-leniency`) | **Date**: 2026-10-29 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/016-follow-mode-leniency/spec.md`

## Summary

Follow refuses notes the owner played right. The plan measures before it relaxes: (1) every refused event gets a reason, the summary counts them, and an opt-in record of a run (numbers, and the run's audio on a second box) lets the owner's own playing be replayed; (2) the start window offers the rhythm calibration when the route has none; (3) Follow gets the owner's timing rule (a time to play from the red, 300 ms, 100–600, cut 20 ms before the next note, 50 ms of advance) in place of the timing window of spec 012, and a separate pitch permissiveness (Strict / Permissive / Very permissive) whose values come from the measures, within at most 4 of the 98 negative tests. Wait mode, the tuner, the calibration and Arcade stay unchanged. Details and evidence: [research.md](research.md).

## Technical Context

**Language/Version**: Java, release 11 for the fork modules (`TuxGuitar-practice`, `TuxGuitar-audio-engine`), JDK 21 locally (CLAUDE.md)

**Primary Dependencies**: TuxGuitar UI toolkit (SWT on macOS), the fork's audio engine (TarsosDSP inside it); no new dependency

**Storage**: the recognition settings store (existing, default layer) for the time to play and the pitch position; files in `~/tuxguitar-follow-takes/` for the opt-in record and audio copy

**Testing**: JUnit 5 in `desktop/TuxGuitar-practice/test` and `desktop/TuxGuitar-audio-engine/src/test/java`; tags `dataset` (reference recording, owner's takes), `swt-gui` (native windows), `benchmark`

**Target Platform**: macOS Apple Silicon validated; Windows and Linux built by CI only

**Project Type**: desktop application plugin (`TuxGuitar-practice`)

**Performance Goals**: no allocation added on the analysis thread (the recorder is the existing allocation-free `HopRecorder`); the verdict delay unchanged

**Constraints**: no existing test modified (owner's rule), except the Follow-mode assertions on the old timing-window slider in `FollowPanelTest` and `FollowPanelSwtTest` (owner's exception of 2026-10-29, tasks T002/T028); the source-structure tests of the start path unchanged and green; Arcade's figures of spec 014 equal; the semitone never accepted

**Scale/Scope**: one session class extended additively, one new window policy, one leniency class, one record writer, one step of the start window, one slider and one choice in the preparation window, texts in three languages

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status |
|---|---|
| I. Compatibility | Pass: no file format, no core module touched; `.tg` untouched. |
| II. Extend before modifying core | Pass: all in the fork's plugin and audio-engine modules; one new parameter in `RecognitionParameters` (fork module). |
| III. Evidence-based | Pass: research cites the files and the measured benchmark; values of story 3 come from measures (R4, R6). |
| IV. Spec-driven | Pass: specify, clarify (and one question at plan), tasks and analysis done. |
| V. Incremental | Pass: stories 1, 2, 3 each shippable; a human gate between 1 and 3. |
| VI. Real-time | Pass: recording by the existing allocation-free `HopRecorder`; file writing at the end of a run, off the analysis thread. |
| VII. Separable logic | Pass: reasons, window policy, leniency and record are pure classes tested without the UI. |
| VIII. Deterministic DSP | Pass: no new detector; FR-009 determinism tested. |
| IX. macOS first | Pass; Windows/Linux by CI, said in the report. |
| X. No unrelated refactoring | Pass: `TimingTolerance` only gains `implements WindowPolicy`. |
| XI. Build and test integrity | Pass: baseline taken first, existing tests unchanged except the Follow-mode slider assertions the owner explicitly allowed to change (T028), listed line by line in the commit. |
| XII. Truthful reporting | Pass: SC-003 reported "not measured" until the owner's run exists. |
| XIII. Honest audio semantics | Pass: the semitone is never a near-miss; "not clear enough" is not called wrong. |
| XIV. Learner-centred feedback | Pass: the reasons say what was heard and by how much. |

Post-design re-check (after Phase 1): unchanged, all pass. No complexity to justify.

## Project Structure

### Documentation (this feature)

```text
specs/016-follow-mode-leniency/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── refusal-reasons.md
│   ├── play-time-window.md
│   ├── follow-leniency.md
│   ├── run-record.md
│   └── calibration-offer.md
└── tasks.md
```

### Source Code (repository root)

```text
desktop/TuxGuitar-practice/src/app/tuxguitar/app/tools/custom/practice/
├── session/
│   ├── FollowSession.java          # + reason per event (additive), snapshot exposes it
│   ├── RefusalReason.java          # new: the fixed list of FR-001
│   ├── WindowPolicy.java           # new: one-method interface; TimingTolerance implements it
│   ├── PlayTimeWindow.java         # new: the owner's rule (FR-011)
│   ├── TimingContext.java          # takes a WindowPolicy, + windowPolicy(); FollowSession asks it for each window (nanoseconds)
│   ├── FollowStarter.java          # Follow builds PlayTimeWindow + FollowLeniency; the Arcade overload unchanged
│   ├── FollowLeniency.java         # new: positions applied one way (like ArcadeLeniency)
│   ├── FollowLeniencyPosition.java # new
│   ├── FollowSettings.java         # + time to play, + pitch position
│   ├── FollowSummary.java          # + counts by reason, position and time to play
│   └── FollowRunRecord.java        # new: the CSV of a run (pure writer)
├── ui/
│   ├── FollowPanel.java            # slider "time to play the note" + position choice + the two boxes (Follow only)
│   ├── FollowView.java             # the reason in the per-event text; summaryText adds the counts by reason
│   ├── PreRunCheck.java / PreRunWindow.java  # + the calibration step (Follow, Arcade)
│   ├── DeclinedRoutes.java         # new: the routes declined in this launch (memory only)
│   ├── PracticeWindow.java         # starts Follow through FollowStarter.prepareFollow
│   ├── RecognitionSettingsPanel.java # lists RecognitionParameters.of(section) itself: FOLLOW_PLAY_TIME appears with its label
│   └── FollowTakeCopy.java         # new: writes the WAV, like DiagnosticCopy
└── share/lang/messages*.properties # en, fr, es

desktop/TuxGuitar-audio-engine/src/main/java/app/tuxguitar/audio/engine/settings/RecognitionParameters.java  # + FOLLOW_PLAY_TIME
desktop/TuxGuitar-practice/test/...   # new tests only (see quickstart.md)
docs/PRACTICE.md, PRACTICE-FR.md, PRACTICE-ES.md
```

**Structure Decision**: everything stays in the existing fork modules, beside the classes it extends; no new module.

**Terminology**: the spec's *permissiveness* (what the learner reads) is *leniency* in the code (`FollowLeniency`, `FollowLeniencyPosition`), as Arcade's (`ArcadeLeniency`); its Strict position keeps today's **pitch** judging, while the time is judged by the time to play in every position (FR-011).

## Phases

1. **Baseline** (no code): default suite counts, recognition reports, Follow benchmark, Arcade figures of spec 014.
2. **Story 1 — reasons and record** (P1): `RefusalReason`, derivation in `FollowSession`, view and summary, `FollowRunRecord`, the audio copy, the replay test of a record. **Gate G1 [HUMAN]**: the owner plays one run with both boxes; the reasons are reported (SC-002).
3. **Story 2 — calibration offer** (P1): the step in `PreRunCheck`, the launch-scoped declined set, texts.
4. **Story 3 — time to play and pitch positions** (P2): `WindowPolicy`, `PlayTimeWindow`, the parameter and the slider replacing the old one in Follow; `FollowLeniency` with values chosen from the measures (SC-003 on the owner's run, SC-004 on the 98 negatives), reported in research.
5. **Closing**: texts and guides, full gate (SC-006), relaunch the application, **gate G2 [HUMAN]**: the owner reruns F01 (SC-007).

## Complexity Tracking

No constitution violation.
