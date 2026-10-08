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
| Drawing the isometric view with the application's own toolkit | `UICanvas` + `UIPaintListener` + `UIPainter` (`common/TuxGuitar-lib/.../ui/resource/UIPainter.java`): 2D paths, circles, rectangles, text, images, alpha, antialias, line styles; **no transform stack and no 3D**. Used by the tuner widgets (`TunerNeedle`, `TunerScale`). Implemented for SWT, JavaFX and Qt toolkits. | The isometric projection is computed in plain Java and drawn as 2D polygons (R3). It is feasible; its performance at 60 images a second is a measured acceptance criterion, not an assumption. |

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
* **O4 - bass.** Whether a four-string bass is promised in the first Arcade version depends on the recognition spike (T003). The neck, the colours and the sequence support N strings regardless.
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

**Result**: every requirement has a task or an explicit reason it has none; no task is orphaned; the dependencies are acyclic (T002 -> T004 -> T022 -> D; E and F independent). Three items are *not decided* and wait for the owner: O1, O2, O3 (and the outcome of O4, O5 comes from measurements). The two inherited limits (SC-002 vs the measured judging; Arcade release gated by 002 T102) are stated in `plan.md` and `tasks.md` T001.

