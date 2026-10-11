# Analysis of specification 003 (Arcade) against the code and the other specifications

**Date**: 2026-10-08. **Method**: Spec Kit `analyze` read of `spec.md`, `visual-design.md` and the checklist against specifications 001, 002, 004, 007 and 008 and against the source as it is today. Every claim about the code cites a file that was opened. Nothing was decided silently: what is not settled by the specifications is listed as an open question in section 3 and in `research.md`.

## 1. What the specification relies on, and the state of it today

| Arcade relies on | Real state (2026-10-08) | Consequence |
|---|---|---|
| The tempo-following mode of 002 (US2b) | Implemented and tested (`FollowSession`, `FollowTimeline`, `FollowRun`, `FollowPlayback`, `FollowStarter`, `FollowSummary`, `FollowView`), **not validated by the owner** (002 T096 to T098, T141, T102 are not ticked). | Arcade increments A to D can be built on it; Arcade must not be released before 002 T102 is done. |
| Its judging up to 8 notes a second (SC-002 here, SC-009 of 002) | **Not met**: 100 / 86 / 44 / 22 % right matches at 2 / 4 / 6 / 8 notes a second on synthetic audio (`specs/002-practice-industrialization/research.md` R15). | SC-002 of this specification repeats a target that the base mode does not reach. It is kept as written (targets are not rewritten) and listed as a known gap at every checkpoint. |
| A backing made of the other tracks | `MidiPlayer.setPlayerOnlyMute` / `isPlayerOnlyMuted` / `clearPlayerOnlyMutes` (`common/TuxGuitar-lib/.../player/base/MidiPlayer.java`) mute one track in the player only; `FollowPlayback` applies and gives it back exactly once. | FR-010 and FR-011 are covered by existing, tested code. |
| A count-in and the player's tempo percentage | `FollowPlayback` forces the player's count-down on, sets the tempo percentage and the loop off, and gives them back. | FR-003 and US1.4 covered. |
| Pause and resume of backing and judging together (US5.2) | `FollowSession.pause/resolve/resume` pauses the judging only; **the player is not paused** (`FollowPlayer` has no pause). | Gap: a player pause/resume must be added (task T020). |
| A neck for any string count (FR-027, US6.8) | `TrackTuning` requires exactly six strings (`TrackTuning.STRING_COUNT = 6`, `IllegalArgumentException("six strings expected")`), and `PracticeTrackCandidates` excludes any other count (`Exclusion.NOT_SIX_STRINGS`). **A four-string bass cannot be practised today.** | Gap: the shared tuning model must support N strings, additively (task T002). |
| Pitch recognition of a bass | `TarsosFastYinEstimator` runs on a 2048-sample window at 48 kHz (42.7 ms): an E1 (41.2 Hz, period 24.3 ms) leaves fewer than two periods in the window. | Risk, not a decision: bass recognition quality is unknown and is measured in a spike (task T003) before a bass is promised. |
| Chord verdicts from `ChordFeedback` (visual-design.md) | `ChordFeedback` belongs to the **wait mode** (`PracticeSession`). `FollowSession` produces `EventResult` (status, pitch, timing, `missingPitches`), not `ChordFeedback`. | Contradiction in the documents, resolved in `research.md` R6: Arcade maps `EventResult` to the chord-level badges; `STRUM_AGAIN` does not exist in the following mode and is not produced. |
| An "uncertain" verdict (FR-024, US2.5) | `FollowSession` has no UNCERTAIN status; it has NOT_JUDGED (a window not seen whole, a pause, the clocks not ready). | Open question O1 (section 3). |
| Judging with the tolerance presets, calibration and "reduced rhythmic precision" of 002 | Implemented (`TimingTolerance`, `RouteCalibrations`, `FollowPanel`). | Reused as they are. |
| A live return of the guitar (FR-030 to FR-047) | **Nothing exists.** The capture is a `TargetDataLine` read in 1024-frame blocks (about 21 ms at 48 kHz) by `CaptureService`; the synthesizer writes to a `SourceDataLine` through `TGAudioLine` (`desktop/TuxGuitar-synth`); the audio engine has an input selector (`CurrentInputSelection`) and **no output selector**. | The 22 ms limit is in doubt on the plain Java Sound path (one capture block alone is about 21 ms). Increment E is a spike and may conclude that monitoring cannot be offered on this stack. |
| A shared output timeline for backing and monitoring (FR-036) | The backing is the MIDI player's synthesizer line; a monitoring line would be a second Java Sound line with its own clock. | Risk recorded in `research.md` R12: two lines drift; the spike measures it. |
| Drawing the isometric view with the application's own toolkit | `UICanvas` + `UIPaintListener` + `UIPainter` (`common/TuxGuitar-lib/.../ui/resource/UIPainter.java`): 2D paths, circles, rectangles, text, images, alpha, antialias, line styles; **no transform stack and no 3D**. Used by the tuner widgets (`TunerNeedle`, `TunerScale`). Implemented for SWT, JavaFX and Qt toolkits. | The isometric projection and immutable scene stay plain Java and backend-neutral (R3). The existing toolkit renderer is benchmarked first on Apple Silicon; hardware acceleration is adopted only if the complete workload needs it. Adaptive quality, latest-frame replacement, a forced fallback and concurrent audio/render acceptance are T040-T048, not assumptions. |

## 2. Consistency with the other specifications

* **001 (MVP)**: listed live audio return and effects as out of scope; 003 adds them with explicit requirements. No conflict.
* **002**: 003 reuses US2b. The *rhythmic calibration* of 002 (64 to 66 ms measured for a built-in microphone and speakers, a correction of the *judging*) is not the *monitoring latency* of 003 (the 22 ms limit of the direct path from captured input to audible output). The two must never be compared or substituted; stated again in `contracts/monitoring-latency-protocol.md`.
* **004**: chords and notes over ringing strings are judged by the polyphonic acceptance and the attack evidence; Arcade inherits their limits (R15 of 002: repeated identical notes at 4 a second, a bend sounding under the next note).
* **007 (refresh and select audio inputs)**: Arcade needs the learner to choose the input and, new, the output. 007 covers the input only; the output selector and the hot-plug of an output are new in 003 (FR-042, FR-044). Order: 007 before the first Arcade UI increment is useful, not blocking for the core (increment A) nor the backing (B).
* **008 (brand identity)**: independent. The working name "Arcade" is still to be confirmed by the owner.
* **The three languages**: FR-050 says English and French; the project keeps English, French and Spanish in step (`FollowStringsTest`). Arcade texts will exist in the three languages.

## 3. Open questions (none is decided here)

* **O1 - "uncertain" verdicts.** The following mode has no UNCERTAIN verdict. Proposal in R6: show an event that could not be judged (NOT_JUDGED: a pause, the clocks not ready, a window not seen whole) as "not judged", not as an error, and show no "uncertain" badge of a note; a chord-level "uncertain" appears only if the following mode one day produces it. To be confirmed by the owner before increment D.
* **O2 - scoring rules.** FR-022 asks the plan to document them; the specification gives none. R5 proposes a fixed table. To be confirmed by the owner before increment A is declared complete (the table is data, the code does not depend on its numbers).
* **O3 - the name "Arcade".** Pending the owner.
* **O4 - bass. Answered by measurement (T003, 2026-10-08).** B0 to F#1 are never recognised, G1 and above always: the open E of a standard bass and its first two frets cannot be judged by the existing chain, so a four-string bass is not promised below G1. The neck, the colours and the sequence support N strings regardless.
* **O5 - monitoring.** Whether any supported configuration reaches 22 ms is unknown and is the result of increment E; Arcade is complete and usable without it.

## 4. Verdict of the analysis

No contradiction blocks the plan. Four gaps need work that the specification does not mention but requires: a player pause (T020), N-string tuning (T002), the mapping of Follow results to Arcade verdicts (T012), and an output selector for the monitoring (increment E). Two inherited limits are stated rather than hidden: the judging does not reach 95 % above 2 notes a second, and the Arcade release depends on the manual validation of 002.

## 5. Final analysis after the tasks (coverage of every requirement)

| Requirement | Covered by | Note |
|---|---|---|
| FR-001 entry points | T050, T057 | name pending (O3) |
| FR-002 explicit track, suitable input | T004, T022 | |
| FR-003 count-in, tempo x speed, never waits | T022, T024, T014 | |
| FR-004 same order/repeats/tempo as the judging | T024 | |
| FR-010, FR-011, FR-014 exclusion of the chosen track only, restored | T023 | |
| FR-012 balance backing / live guitar | conditional on E (T075) | meaningless without a live return |
| FR-013 backing not judged, headphones advice | T026 | |
| FR-020 verdicts with the tolerance of 002 | T012, T013 | uncertain: O1 |
| FR-021, FR-026, FR-023 score kept in memory, shown at the end only | T013, T015, T053, T054 | |
| FR-022 documented fixed scoring | T010, T011 (R5) | table to confirm (O2) |
| FR-024 only pitch and timing claimed | T012, T038, T039 | |
| FR-025 isometric view, colours, hit line, chords grouped | T034 to T041, T043, T044 | |
| FR-027 geometry from the track, equal temperament | T030, T031 | |
| FR-028 chord bar + cartouche | T036, T038 | |
| FR-029 anticipatory camera | T035 | |
| FR-030 to FR-037 live return, no effects, click-free, shared timeline | E (T070-T075, then T076+ if allowed); F for effects | decided by measurement |
| FR-038, FR-039 effects research and benchmark | T090 to T092 | |
| FR-040 to FR-047 latency measurement and the 22 ms gate | T070 to T075 | hardware tasks stay open until done |
| FR-042 output choice | live return: E; **backing: the MIDI output already chosen in the configuration of the application** (no new selector; documented in T051 text) | gap noted, not silently decided |
| FR-044 lost or refused device | T025 (run), E (monitoring) | |
| FR-050 texts | T056 (English, French, Spanish) | |
| FR-051 tests including GUI | every task is test-first; T043, T058 | |
| FR-052 other modes unchanged | checkpoints T016, T027, T049, T059 | |
| FR-053 priority: audio/judging, pacing, readability, decoration | T040, T047 | acceptance includes concurrent audio and digest parity |
| FR-054 adaptive quality and protected gameplay information | T040, T044, T048 | deterministic tiers and drop order |
| FR-055 latest-frame replacement, no backlog | T041, T047 | stalled-paint test plus long stress run |
| FR-056 render/audio isolation | T041, T047 | no audio lock/path allocation; dropout and overrun counters |
| FR-057 measured vendor-neutral acceleration and fallback | T042, T045, T046, T048 | the valid decision may be to retain `UIPainter` |
| FR-058 concept composition over pixel resolution | T040, T044 | human review includes minimalist tier |
| FR-059 high-density lower-resolution field with crisp essentials | T040, T042, T048 | |
| FR-060 one active target event on the physical neck | T037, T038, T043 | the active event alone owns neck targets; camera look-ahead does not expose the others and does not admit events to the visual set |
| FR-061 useful approaching chord identity | T038, T043, T056 | exact written name wins; otherwise deterministic resolver from written pitches/bass; pitch-list fallback, never audio |
| FR-062 visual set | T040, T043, T044, T062 | every event not completed and due within five seconds, whatever their number, each note whole on its lane; every later event absent (no preview, dot, bar or label); completed events keep only their verdict badge; every note of the set on its FR-066 lane |
| FR-063 chords in the set | T038, T040, T043, T044 | one expanded diagram; the other chords of the set are bars without diagram on one centred axis; chords due later absent; no fret-based lateral scattering |
| FR-064 optional external real-score corpus | T060-T065 | content-aware recursive read, deterministic diversity selection, no score bytes committed |
| FR-065 opaque active target disks | T037, T038, T040, T043, T062, T063 | filled alpha-1 core, bounded size, string colour, contrasting outline, identical across tiers |
| FR-066 predefined fret-arrival lanes | T037, T043, T062, T063, T066 | one lane per visible fret, shared across strings; endpoint is the painted cell-top midpoint; no approach primitive or connector crosses the neck |
| SC-001 start in under 30 s | manual, T068-adjacent walk-through | not measured by a task; reported only if timed |
| SC-002 95 % at 8 notes a second | inherited from 002 SC-009 | **not met by the base mode** (R15 of 002) |
| SC-003 monitoring loopback | T072, T073 | |
| SC-004 identical judgements and score | T014 | |
| SC-005, SC-006 clear return, no dropout | conditional on E | |
| SC-007 no regression | checkpoints | |
| SC-008, SC-009 human checks | T068, T069 | |
| SC-010 visibility | T035 | |
| SC-011 geometry | T031 | |
| SC-012 colour chain, monochrome | T037, T039 | |
| SC-013 effects decision | T092 | |
| SC-014 Apple Silicon frame pacing | T042, T045, T047 | ten-minute complete-workload acceptance |
| SC-015 zero audio regression and identical judgement digest | T047 | renderer-disabled comparison |
| SC-016 functional fallback | T046, T048 | achieved fallback rate is reported, not invented |
| SC-017 no mixed future targets on the neck | T037, T038, T043 | crowded note and chord fixtures, atomic event transition |
| SC-018 chord-name readability in every tier | T038, T043, T056 | written, derived common/inverted, unsupported pitch-list and single-note fixtures |
| SC-019 dense-scene readability and unchanged judging | T040, T043, T047 | 8/16/32-event fixtures, collision bounds and digest parity |
| SC-020 multiple-chord readability | T038, T040, T043 | 2/4/8 chords, one expanded diagram, centred bars, ordered promotion and no clipping |
| SC-021 corpus size and diversity | T060, T061, T065 | >=2,000 profiled files and >=60 selected track cases across required categories |
| SC-022 real-score structural/render acceptance | T062-T065, T044 | all selected cases plus twelve local screenshot pairs; degraded files separate |
| SC-023 active-target visibility | T037, T038, T040, T043, T062, T063 | structural alpha/size/centre assertions plus light/dark/monochrome screenshots |
| SC-024 predefined-lane registration | T037, T043, T062, T063, T066 | primitive-level marker-on-lane and exact painted cell-top endpoint across strings/progress/camera/tiers; no neck-crossing primitive |

**Result**: every requirement has a task or an explicit reason it has none; no task is orphaned; the dependencies are acyclic (T002 -> T004 -> T022 -> D; E and F independent). Three items are *not decided* and wait for the owner: O1, O2, O3 (and the outcome of O4, O5 comes from measurements). The two inherited limits (SC-002 vs the measured judging; Arcade release gated by 002 T102) are stated in `plan.md` and `tasks.md` T001.

## 6. Change of 2026-10-11 (palette, order, lit string)

The owner supplied a visual reference and three decisions (Q15 to Q17): palette red, yellow, light blue, orange, green, violet; the lowest string at the top; a string bright while the active event targets it. Recorded as FR-067, FR-068, SC-025; SC-012, FR-025, visual-design.md, data-model.md, contracts/arcade-view.md, plan.md, research R21 and quickstart follow. Impact: `StringColours`, the string rows of `StringLayout`, the neck and string painting, the reference scenes and images, the corpus images; no change to judgement, timing or recognition. Contradiction check: every mention of the old palette (cobalt, cyan, teal, gold, violet, magenta) and of "low string at the bottom" was replaced; the chord diagram keeps its own traditional orientation. Open for the owner: the lowest string red is by convention (accepted), the exact brightness of the dim state is a design value to be shown to the owner (T101).

## 7. Change of 2026-10-11 (open string)

Second reference image of the owner: an open note is a horizontal bar across the lanes in the string colour. Recorded as Q18, FR-069, SC-026, research R22; FR-065 (no disk for an open string) and FR-066 (no pre-nut lane for a single note) amended. Impact: `FretArrivalLaneMap` (pre-nut lane for single notes removed), a new scene item `OPEN_BAR`, the label `arcade.label.open` in three languages, the reference scenes with fret 0. Chords keep `O` in the cartouche. Not decided here: a first-run tutorial caption like the one on the image (noted in visual-design.md as a later possibility).

## 8. Change of 2026-10-11 (rounded rectangles)

Third reference image of the owner and three answers (Q19 to Q21): rounded rectangles for the marker and the target, thread, footprint and number under the footprint, highlighted fret ruler, a discreet approach band. Recorded as FR-070 to FR-072, SC-027, research R23; FR-065 reworded (the disk becomes a rounded rectangle, the tests keep their intent). Contradictions resolved with the owner: the disk is replaced everywhere (FR-060/FR-065), the number leaves the marker, the lanes stay the only lines from the horizon to the neck. Impact: scene roles, renderer primitive, every reference scene and test of the target and the marker.

## 9. Change of 2026-10-11 (chord panel) and size setting

Fourth and fifth reference images: a chord as a translucent panel with its note rectangles at fret and string, name above the left corner (Q22), fret numbers on the active chord (Q23). Recorded as FR-074, SC-028, research R24; FR-028 and FR-063 superseded for the chord's form. Also FR-073 (size setting: 0 % one pixel, 100 % three quarters of the former size, implemented 2026-10-11). Impact: chord drawing, name placement, density rules and chord tests; the corpus contact sheet follows. Open: the visual density of panels in a very dense passage (to be judged by the owner).
