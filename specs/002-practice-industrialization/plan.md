# Implementation Plan: Interactive Practice Industrialization

**Branch**: `feature/interactive-practice` (spec dir `002-practice-industrialization`) | **Date**: 2026-09-30 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/002-practice-industrialization/spec.md`

## Summary

Extend the MVP (`specs/001-interactive-practice-mvp`) in six independently deliverable phases:
(A) a one-click Practice button beside Play; (B) an input front end (software gain, noise gate with
noise estimate and subtraction); (C) faster, more robust note recognition; (D) a technique
recognition framework with the P1 techniques; (E) a tempo-following practice mode; (F) the P2/P3
techniques. Everything stays deterministic signal processing (Constitution VIII); every technique
is displayed as verified only when a benchmark backs it, otherwise "not verifiable" (XIII).

The decisions, each tied to inspected source, are in [research.md](research.md). The three facts that
shape the plan most:

1. **The toolbar is closed to plugins.** `TGMainToolBar.layout()` builds `new TGMainToolBarConfigMap()`
   and the default layout is a static string array in `TGMainToolBarConfig`; a plugin cannot add a
   button, so Phase A needs a small, justified core change (Principle II).
2. **Latency is dominated by decision rules, not by the estimator.** Acceptance needs both a
   confirmed attack (`ArticulationDetector.DECISION_DELAY_HOPS` = 13 hops ≈ 69 ms) and a 107 ms
   stability run (`NOTE_STABILITY`); the MVP measured a median 171 ms / max 288 ms stable-detection
   latency. The SC-003 target of 150 ms median is therefore a stretch that Phase C must *earn by
   measurement*, not assume.
3. **The MVP auto-passes bends and harmonics** (`PracticeSequenceBuilder`: `AutoPassReason.BEND`,
   `HARMONIC`), and never judges techniques; both need new sequence semantics before they can be
   recognized.

## Technical Context

**Language/Version**: Java. Fork modules `TuxGuitar-audio-engine`, `-practice`, `-tuner-next` compile at release 11 (TarsosDSP 2.5 needs it, see the build notes in README.md); core/`common` modules stay at release 9.

**Primary Dependencies**: existing only: TarsosDSP 2.5 core (GPL-3.0, permitted by the constitution, isolated in `tuxguitar-audio-engine`), the UI toolkit abstraction. No new dependency is planned; any addition must be justified and its licence recorded in research.md.

**Storage**: persisted recognition settings via the existing `RecognitionSettingsStore` / `RecognitionParameters` (keys `recognition.*`). The input gain is deliberately NOT stored (spec FR-063). No audio is recorded or persisted (FR-042).

**Testing**: JUnit 5. Synthetic-signal unit tests (`SyntheticSignals`, Karplus-Strong plucks) in `desktop/TuxGuitar-audio-engine/src/test/java`; practice logic tests in `desktop/TuxGuitar-practice/test`; the real-guitar benchmark (JUnit tag `benchmark`, excluded by default) extended with fast-passage and technique clips; SWT UI contract tests as in `RecognitionSettingsSwtTest`.

**Target Platform**: macOS Apple Silicon, SWT build `desktop/build-scripts/tuxguitar-macosx-swt-cocoa` (the only build that packages the fork modules). Other variants must still compile; the core toolbar change (Phase A) is in `desktop/TuxGuitar`, shared by every desktop variant, and must be inert when the plugin is absent.

**Project Type**: desktop application plugin set (Maven), with a minimal core extension.

**Performance Goals**: provisional, to be confirmed from a fresh baseline (Phase C task 1): median note-to-feedback ≤ 150 ms, p95 ≤ 250 ms (SC-003); ≥ 95% of notes at 8 notes/s (SC-002); tempo-following at up to 8 notes/s (SC-009).

**Constraints**: audio path obeys Principle VI: no blocking I/O, no unbounded allocation, no locks with the UI on the analysis thread; every added stage's latency is counted (FR-065). `common/` code unchanged.

**Scale/Scope**: one learner, one track, 6-string guitar; songs up to a few thousand events.

**Baseline caveat**: at plan time the working tree holds a large amount of uncommitted MVP work (modified and untracked files under `desktop/TuxGuitar-audio-engine`, `-practice`, including `settings/`, `ChordMatcher`, `PassagePolicy`). The last commit is `b7639d4d2`. This plan builds on the working tree, not on HEAD; committing that baseline is the owner's decision and must happen before Phase A code lands so the phases stay reviewable.

## Constitution Check

*GATE: passed before Phase 0; re-checked after Phase 1 (see end).*

| Principle | Status | Notes |
|---|---|---|
| I Preserve compatibility | Pass | No file-format change. The toolbar addition is additive, hidden when the plugin is absent, and does not alter existing user toolbar configurations (a user-saved layout keeps working; the button is offered in the toolbar settings and in the default layout only). |
| II Extend before modifying core | **Justified deviation** | Phase A needs a core change because `TGMainToolBarConfigMap` is built inside core with no registration point. Smallest change: a registry that plugins fill, consulted by the config map and the default layout. See research R1. Everything else stays in fork modules. |
| III Evidence-based architecture | Pass | research.md cites the files/classes each decision rests on; unverified points are listed as spikes, not assumed. |
| IV Spec-driven | Pass | spec, clarifications and this plan precede tasks. |
| V Incremental delivery | Pass | phases A–F below each leave the app usable and testable; F is not started early. |
| VI Real-time audio | Pass with checks | All new stages run per hop on the existing analysis worker, preallocated; latency of each is measured (FR-065). Tempo mode adds a clock mapping that must not block. |
| VII Separable, testable | Pass | New logic lives in `tuxguitar-audio-engine` (no UI, no hardware) or in practice `session/`/`model/` (no UI toolkit); only thin UI glue is hardware/UI-bound. |
| VIII Deterministic first | Pass | rule-based features (pitch trajectory, envelope, spectrum); Kalman dropped by the owner; no ML. |
| IX Cross-platform conscious | Pass | validated on macOS SWT only, stated in every phase; core change is toolkit-neutral (uses the UI toolbar abstraction). |
| X No unrelated refactoring | Pass | `AnalysisFrame`/`ArticulationDetector` get additive changes only. |
| XI Build/test integrity | Pass | each phase ends with the validated macOS build and all existing tests green. |
| XII Truthful reporting | Pass | targets are provisional until measured; results reported as run. |
| XIII Honest guitar-audio semantics | Pass, central | every technique verdict is detected / not detected / uncertain / not verifiable; pitch-only stays pitch-only; single-string-origin claims are never made. |
| XIV Learner-centred feedback | Pass | technique and timing feedback is uncertain when evidence is borderline; summaries tell the learner what to improve (early/late/missed, which techniques). |

## Project Structure

### Documentation (this feature)

```text
specs/002-practice-industrialization/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── toolbar-extension.md
│   ├── signal-frontend.md
│   ├── technique-detection.md
│   └── tempo-following.md
└── tasks.md            # created later by /speckit-tasks
```

### Source Code (repository root)

```text
desktop/TuxGuitar/src/app/tuxguitar/app/view/toolbar/main/
├── TGMainToolBarConfigMap.java      # consult the registry (Phase A, core change, minimal)
├── TGMainToolBarConfig.java         # default layout: registered items after "transport.start"
└── TGMainToolBarItemRegistry.java   # NEW: plugin-fed item definitions

desktop/TuxGuitar-audio-engine/src/main/java/app/tuxguitar/audio/engine/
├── frontend/                        # NEW (Phase B): InputGain, HighPass, NoiseEstimator, NoiseGate, SpectralSubtractor, GainSuggester
├── analysis/                        # AnalysisService (apply front end per hop), ArticulationDetector (Phase C), new feature frames
├── technique/                       # NEW (Phases D, F): per-technique detectors + TechniqueEvidence
├── recognition/                     # NEW (Phase C): expected-pitch verifier ("score-informed" evidence)
└── settings/                        # new RecognitionParameters (noise gate on/off, suggestion on/off, tolerance preset…)

desktop/TuxGuitar-practice/src/app/tuxguitar/app/tools/custom/practice/
├── model/       # PracticeEvent (technique targets, bend path, harmonic sounding pitch), PracticeSequenceBuilder
├── session/     # PracticeSession (wait mode, updated), FollowSession (tempo mode, NEW), TechniqueJudge (NEW), TimingTolerance (NEW)
└── ui/          # PracticePlugin (registers toolbar item + state), PracticeWindow (mode, gain slider, technique row), LevelPanel

desktop/TuxGuitar-tuner-next/src/app/tuxguitar/app/tools/custom/tunernext/
└── TunerWindow.java                 # gain slider (Phase B), shared engine setting

desktop/TuxGuitar-audio-engine/src/test/java/...   # synthetic tests, benchmark extensions
desktop/TuxGuitar-practice/test/...                # session/model/UI tests
desktop/TuxGuitar-practice/share/lang/             # messages.properties, messages_fr.properties
desktop/TuxGuitar-practice/share/skins/<skin>/     # practice toolbar icon per skin (to verify, R1)
```

**Structure Decision**: keep the three existing fork modules; add packages inside them rather than new modules (no new POM, no change to the single-tuner-jar build rule). The only edit outside them is the core toolbar registry (Phase A).

## Phases (each independently reviewable, build green at the end)

| Phase | Delivers | Spec | Exit gate |
|---|---|---|---|
| A | Practice button next to Play; Tools entry unchanged; state + tooltip + availability | US1, FR-001…007 | clicking opens the same window as Tools; absent plugin ⇒ no button; SWT build + tests green |
| B | Gain slider (practice + tuner), noise gate/estimate/subtraction, suggested gain (switchable) | FR-060…066 | benchmark with/without front end shows no loss in recognition; gain resets on input change/launch |
| C | Faster, more robust recognition incl. identical repeated notes and legato acceptance rule | US2, FR-010…017 | fast-passage benchmark meets SC-002/003/004 *or* measured gaps are reported and targets revised with the owner |
| D | Technique framework + P1 techniques (hammer-on, pull-off, slide, vibrato, bend) | US3, US4, US7, FR-020…032 | SC-005 per technique on benchmark; unproven ones shown "not verifiable" |
| E | Tempo-following mode | US2b, FR-050…056 | SC-009 on synthetic + recorded runs; clock spike (S-T1) passed |
| F | P2/P3 techniques (palm mute, tremolo picking, natural harmonic; artificial/pinch harmonic, tapping) | US5, US6 | each meets its target or ships as "not verifiable" (SC-006) |

Order rationale: A and B are low-risk, visible and feed everything else; C must precede E
(tempo-following is only as good as recognition speed) and partly D (legato acceptance); D before F
so the framework is proven on the highest-value techniques.

## Complexity Tracking

| Violation | Why needed | Simpler alternative rejected because |
|---|---|---|
| Core change in `desktop/TuxGuitar` (toolbar registry) | `TGMainToolBarConfigMap` is instantiated in core with a closed item list; no plugin hook exists (inspected: `TGMainToolBar.layout`, `TGMainToolBarConfig`) | Hard-coding a `"transport.practice"` item that names the plugin's action in core would couple core to a GPL-isolated fork plugin and leave a dead button when the plugin is absent |

## Post-design Constitution re-check

No new violation after Phase 1 design. Open risks carried as spikes (research.md): S-T1 (audio/clock
offset for tempo mode), S-F1 (feature separability of legato/palm-mute/harmonics on the real
guitar), S-I1 (skin icon resolution for a plugin-shipped icon), S-B1 (subtraction artefacts).
