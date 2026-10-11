# Implementation Plan: Arcade mode

**Branch**: `feature/interactive-practice` (spec directory `003-arcade-mode`) | **Date**: 2026-10-08 | **Spec**: [spec.md](spec.md), [visual-design.md](visual-design.md)

**Inputs read**: `analysis.md` (consistency and gaps), `research.md` (decisions R1 to R16), the source files cited in them.

## Summary

Arcade is a third practice mode beside *wait* and *follow*. It runs the tempo-following session of specification 002 on the track the learner explicitly chooses, with every other track rendered by the MIDI player, and presents the run in a deterministic isometric view with a game score shown only at the end. Live monitoring of the guitar at 22 ms at most is a separate increment that begins with a measurement spike and may conclude that it cannot be offered; effects are deferred and only a research task is prepared.

The work is cut into six increments (A to F) that are independently reviewable; A to D need no new audio capability and can be built on the existing following mode; E is a spike; F is a research protocol only.

## Technical Context

**Language/Version**: Java, Temurin 21 for the build; the practice and audio-engine modules compile at release 11 (TarsosDSP, see `CLAUDE.md`); nothing is added to `common/` (release 9).

**Primary Dependencies (existing, cited)**: `FollowSession`, `FollowTimeline`, `FollowRun`, `FollowPlayback`, `FollowStarter`, `FollowSummary`, `FollowView`, `FollowPlayer`/`MidiFollowPlayer`, `FollowSettings`, `FollowPanel` (all `desktop/TuxGuitar-practice/src/app/tuxguitar/app/tools/custom/practice/{session,ui}`); `PracticeSequenceBuilder`, `PracticeSequence`, `PracticeEvent`, `TrackTuning`, `PracticeTrackCandidates` (`.../model`); `MidiPlayer.setPlayerOnlyMute` (`common/TuxGuitar-lib/.../player/base/MidiPlayer.java`); `UICanvas`, `UIPaintListener`, `UIPainter` (`desktop/TuxGuitar-ui-toolkit`, `common/TuxGuitar-lib/.../ui/resource/UIPainter.java`); `AudioEngine`, `CaptureService`, `CaptureClock`, `CurrentInputSelection` (`desktop/TuxGuitar-audio-engine`); `TGTrack`, `TGBeat`, `TGChord`, `TGStroke` (`common/TuxGuitar-lib/.../song/models`). **No new third-party dependency** in increments A to D. Effects libraries are a research item of increment F (licence compatibility checked per candidate before any adoption).

**Storage**: none. Nothing about a run is persisted (FR-026, R10).

**Testing**: JUnit 5 in the practice module (`test/`) and in the audio-engine module; fakes `FakeTempoClock` and `FakeMidiPlayer` (`test/.../practice/testsupport`) and `FakePlayerRun` for whole runs; `SwtContractToolkit` for headless widgets; the profile `swt-gui-tests` for native SWT; the `benchmark` tag for measurements; and an optional external real-score suite under `TG_ARCADE_GP_CORPUS`, skipped without it and never copying score bytes into Git (R17).

**Target Platform**: macOS on Apple Silicon is the only validated platform (constitution IX). Windows and Linux must keep building (the practice module is already in their packages); nothing is claimed for them. The live return is validated, if at all, on this machine.

**Project Type**: desktop application plugin (fork module).

**Performance Goals**: on the validated Apple Silicon machine the complete dense workload targets 60 frames/s, with p95 frame time at most 16.7 ms and p99 at most 33.3 ms after adaptive quality reduction, no two consecutive missed presentation deadlines and no stale-frame queue. The same recorded input produces the same judgement digest with and without rendering, with zero capture overrun, dropped input sample, MIDI/audio dropout or monitoring deadline miss. A software/toolkit fallback remains functional and is measured rather than assumed (SC-014 to SC-016, T042/T046/T047).

**Constraints**: no change to `common/TuxGuitar-lib` or `desktop/TuxGuitar`; the song, its mute/solo and its undo stack are never touched; the existing tests stay unchanged; 22 ms end to end for monitoring, measured, never assumed. Rendering never owns an audio lock or runs on capture/analysis/playback threads. The scene model is renderer- and vendor-neutral; any accelerated backend is optional, chosen by measurement, and has a functionally equivalent fallback. Visual quality degrades before audio, judgement, frame pacing or gameplay information.

## Constitution Check

| Principle | Status |
|---|---|
| I Preserve compatibility | Additive. `TrackTuning` is generalised without changing six-string behaviour; the wait and following modes keep their candidate rule. The song format and configuration are untouched (no new persisted key except none). |
| II Extend before modifying core | Practice module and audio-engine only. The only shared-model change (`TrackTuning`) is in the practice module and justified by inspected source (R2). |
| III Evidence-based | Every class cited above was opened; open points have tasks (T003, T070 to T075, T090). |
| IV Spec-driven | This cycle: analysis, plan, research, data model, contracts, tasks, final analysis. Open product questions O1 to O5 are listed, not decided. |
| V Incremental | Increments A to F; E and F do not block the others; no later increment is started early. |
| VI Real-time audio | The scene and the score run off the audio threads; monitoring (E) is a spike under the explicit rule "no blocking, no unbounded allocation" and a measured gate. |
| VII Separable audio logic | Core, scoring, geometry, camera and projection are pure and tested without UI or hardware; the loopback needs hardware and is reported as such. |
| VIII Deterministic first | Everything is deterministic; no learned model. |
| IX Cross-platform | macOS validated only; stated in every report. |
| X No unrelated refactoring | Only the `TrackTuning` generalisation, as a separate commit. |
| XI Build and test integrity | Each increment ends with the targeted tests, the module tests and the full macOS build. |
| XII Truthful reporting | Hardware, human and loopback validations are never reported before they are done. |
| XIII Honest guitar audio | The view shows tablature positions as instructions; only pitch and timing are claimed verified. |
| XIV Learner-centred feedback | Verdicts carry a word and a shape; not-judged is not an error (O1). |

No violation to justify.

## Project Structure

### Documentation

```text
specs/003-arcade-mode/
├── spec.md, visual-design.md, assets/, checklists/
├── analysis.md                  # consistency analysis and gaps (this cycle)
├── plan.md                      # this file
├── research.md                  # decisions R1 to R16
├── data-model.md
├── quickstart.md                # validation, manual parts marked as such
├── contracts/
│   ├── arcade-core.md
│   ├── arcade-backing.md
│   ├── arcade-view.md
│   ├── monitoring-latency-protocol.md
│   └── effects-research-protocol.md
└── tasks.md
```

### Source code

```text
desktop/TuxGuitar-practice/src/app/tuxguitar/app/tools/custom/practice/
├── model/TrackTuning.java                  # N strings, additive (T002)
├── model/ArcadeTrackCandidates.java        # which tracks can be performed (T004)
├── session/FollowPlayer.java               # + pause()/resume() (T020)
├── session/FollowPlayback.java             # + pause()/resume() (T021)
├── session/FollowSettings.java             # Mode.ARCADE (T050)
└── arcade/
    ├── core/   ArcadeVerdict, ArcadeScoring, ArcadeRun, ArcadeResult
    ├── view/   StringLayout, NeckModel, StringColours, IsometricProjection,
    │           FretArrivalLaneMap, CameraTrack, ChordCartouche, ArcadeRenderEvent, ArcadeScene
    └── ui/     ArcadeCanvas, ArcadeWindow, ArcadeResultWindow, ArcadePanel
desktop/TuxGuitar-practice/share/lang/messages{,_fr,_es}.properties   # arcade.* texts
desktop/TuxGuitar-audio-engine/src/main/java/app/tuxguitar/audio/engine/monitor/   # increment E
desktop/TuxGuitar-practice/test/...                  # tests mirror the packages
```

**Structure Decision**: one module for the mode (R1), one package per layer, the pure layers (`core`, `view` without `ArcadeCanvas`) free of any widget, so that they are tested headless (constitution VII).

## Increments

* **A - Core without interface.** `ArcadeRun` turns the results of a `FollowSession` into verdicts, score, streak and result; `ArcadeScoring` holds the table (R5). No waiting after an error (inherited: the following session never waits). Deterministic tests on `FakePlayerRun` and on recorded result sequences (SC-004).
* **B - Backing.** Explicit choice of the performed track (`ArcadeTrackCandidates`), player-only mute of that track only, count-in, tempo, speed, repeats, tempo changes, player pause/resume (T020/T021), exact restoration after stop or error: all on `FakeMidiPlayer`, plus the real `MidiPlayer` without sound as in `MidiFollowPlayerTest`.
* **C - Isometric view.** (From 2026-10-11 the string palette is FR-067, the neck draws the lowest string at the top and a string is lit while the active event targets it, FR-068: `StringColours`, the row mapping of `StringLayout` and the neck painting change, see research R21.) `NeckModel` (N strings, `x(n) = L (1 - 2^(-n/12))`), `StringColours`, `IsometricProjection`, a `FretArrivalLaneMap` built from the fret boundaries actually painted (one fixed lane per visible fret, shared by every string), `CameraTrack`, `ChordCartouche`, `ArcadeScene` (pure), then a narrow renderer contract and `ArcadeCanvas`. Begin with the existing toolkit path; benchmark it on Apple Silicon under concurrent MIDI, recognition and (when available) monitoring. Adopt an accelerated backend only if the decision record proves a material gain without adding audio risk or disproportionate packaging complexity. Geometry, visibility (SC-010), colour (SC-012), the visual set (SC-019, SC-020), predefined-lane registration (SC-024), adaptive quality, fallback, monochrome, structural-capture and concurrent audio/render stress tests are required.
* **H - Real-score visual corpus.** Reuse content-aware GP3/GP4 reading, profile a deterministic sample of at least 2,000 external files and select at least 60 diverse playable tracks. Replay their real practice timelines through the Arcade scene in both quality tiers; keep exact paths/screenshots local and commit only aggregate anonymous evidence. H is part of C's visual acceptance and feeds T044; it does not replace pure fixtures.
* **D - Interface and result.** Arcade in the mode selector, the Arcade window with the HUD (speed, streak, next event; no number), pause/resume/stop, the result window (score, counts, best streak), the three languages, headless and native GUI tests.
* **E - Monitoring spike.** Measure, do not build: capture block sizes, a low-buffer output line, accounting, wired and acoustic loopback; gate at 22 ms on the worst credible value; decide whether a monitoring path is built. See `contracts/monitoring-latency-protocol.md`.
* **F - Effects research task.** Only the protocol and the inventory (`contracts/effects-research-protocol.md`); nothing implemented.

## Order across the pending features (proposal, nothing is started in parallel)

1. Finish and validate Phase 9 of 002 by the owner (blocking for any Arcade release).
2. Arcade A to C, real-score corpus H, then D (no effects, no monitoring dependency).
3. 007 (refresh and select audio inputs): useful before E and for the Arcade preparation window.
4. 006 (drag and drop): independent, small.
5. 008 (new logo): after the variants of the icons are created and validated.
6. 005 (notation rendering): touches the graphic core of the score view; last.
E (monitoring spike) runs when hardware time is available and does not wait for 3 to 6.

## Complexity Tracking

None.
