# Research: Arcade mode (specification 003)

**Date**: 2026-10-08. Each decision cites the source it rests on. A claim that could not be confirmed in source is marked **open** and has a task that settles it.

## R1 - Where the code lives

**Decision.** The Arcade core, backing and view live in the existing module `desktop/TuxGuitar-practice`, in a new package `app.tuxguitar.app.tools.custom.practice.arcade` (sub-packages `core`, `view`, `ui`). The live-monitoring work lives in `desktop/TuxGuitar-audio-engine`, in a new package `app.tuxguitar.audio.engine.monitor` (the capture and the analysis are there; monitoring must read the same samples).

**Why.** Arcade consumes classes that are package-private or in the practice module (`PracticeSequence`, `PracticeEvent` accessors, `FollowSession`, `FollowTimeline`, `FollowRun`, `FollowStarter`, `PracticePalette`); a separate plugin module would force them public for no gain. The constitution asks to extend before modifying core (II): no change to `common/TuxGuitar-lib` or `desktop/TuxGuitar` is planned. The only shared-model change is `TrackTuning` (R2), which is in the practice module itself.

**Rejected.** A new `TuxGuitar-arcade` module: duplicates the plugin descriptor, the build-script entries (`desktop/build-scripts/*`) and the Windows/Linux packaging for no isolation benefit.

## R2 - Strings: N, not six

**Evidence.** `model/TrackTuning.java` declares `STRING_COUNT = 6` and throws `IllegalArgumentException("six strings expected")`; `model/PracticeTrackCandidates.java` returns `Exclusion.NOT_SIX_STRINGS` when `track.stringCount() != TrackTuning.STRING_COUNT`; `PracticeSequenceBuilder` takes a `TrackTuning`. `TGTrack.stringCount()`, `getString(n).getValue()` and `getOffset()` give the real definition (`common/TuxGuitar-lib/.../song/models/TGTrack.java`). `visual-design.md` requires the real track definition to win.

**Decision.** Generalise `TrackTuning` to the actual string count, additively: the six-string behaviour, the preset-minimum logic (`builtInSixStringMinimum`) and every existing test stay as they are; a track of another count (a four-string bass, a seven-string guitar) gets a `TrackTuning` of its own count. `PracticeTrackCandidates` keeps `NOT_SIX_STRINGS` for the **wait and following modes** until the owner asks otherwise (constitution I: existing behaviour unchanged) and offers a separate rule to the Arcade (`ArcadeTrackCandidates`: any track with a pitched string definition, not percussion, with practice events). Tests first (T002): the six-string tests unchanged and green, the new counts added.

**Open.** Whether the wait/following modes should accept a bass too is a product question; not decided here.

## R3 - Drawing the isometric view

**Evidence.** `common/TuxGuitar-lib/src/main/java/app/tuxguitar/ui/resource/UIPainter.java`: path-based 2D (`initPath(style)`, `moveTo`, `lineTo`, `cubicTo`, `addCircle`, `addRectangle`, `addRoundedRectangle`, `closePath`), `drawString`, `drawImage`, `setForeground/Background`, `setAlpha`, `setAntialias`, `setLineWidth`, line styles, font metrics. **No transform, no depth, no 3D.** `UICanvas` (`addPaintListener`) exists in the SWT, JavaFX and Qt toolkits (`UIFactory.createCanvas`), and the tuner (`TunerNeedle`, `TunerScale`) already paints on it.

**Decision.** A pure-Java scene layer (`ArcadeScene`, `IsometricProjection`) computes, for a render time `t`, every shape in screen coordinates (polygons, circles, text with positions) from model-space geometry; `ArcadeCanvas` only replays that list on a `UIPainter`. Nothing 3D is invented in the toolkit. The scene is a pure function of `(timeline, camera track, t, viewport)`, so every geometry, visibility and determinism test runs without a window; pixel tests use controlled captures (R15).

**Rejected.** A game engine or OpenGL (the specification assumes none, and the three UI toolkits must keep working); drawing on a prerendered image (cannot honour real fret geometry and per-string colours).

**Measured later, not assumed.** 60 images a second on a dense passage: a task (T042) measures the cost of one scene replay and sets the acceptance bound.

## R4 - Camera

**Decision.** The camera is a **precomputed keyframe track**, built once per run from the timeline and the neck model, then evaluated as a pure function of time: for each event, the span `[lowFret, highFret]` of its written positions (every `ChordNote` included) plus a margin; the required window at a time is the union of the spans of the events whose onset is within the look-ahead horizon plus the spawn lead. Whenever that required window changes, an eased segment (smoothstep, bounded speed, duration fixed by the distance) is scheduled to *end* before the event spawns; segments never overlap in an oscillating way because a new target replaces the remaining part of the running segment from its current value (continuity by construction). Zoom is used only when the required span exceeds the normal window, bounded by the readability limit.

**Why.** The visual contract demands: continuous, never teleporting, deterministic for the same timeline, seekable (pause, restart and a section replay recompute from the time, nothing accumulates). A keyframe track is O(events), testable on any timeline, and independent of the frame rate. SC-010 becomes a property test over the track.

**Rejected.** A per-frame damped spring: frame-rate dependent, not seekable, cannot guarantee "visible before spawn".

## R5 - Scoring (FR-021 to FR-023, FR-026)

The specification asks the plan to document the rules and gives none. **Proposal, to be confirmed by the owner (question O2); the table is data in `ArcadeScoring`, the code does not depend on its numbers:**

| Judgement of an event | Points | Streak |
|---|---|---|
| on time | 100 | extends |
| early or late (right pitch) | 60 | extends |
| wrong pitch | 0 | restarts at 0 |
| missed | 0 | restarts at 0 |
| not judged (pause, clocks not ready) | not counted | unchanged |
| passed without judging (dead note, harmonic) | not counted | unchanged |
| extra note (an attack matching no event) | 0, no penalty | unchanged |

Streak multiplier applied to the points of an event: x1 for a streak below 10, x1.5 from 10, x2 from 25. The maximum score of a run is the score of a run where every judged event is on time with the same streak rule; the result shows the score, the percentage of that maximum, the counts per judgement (from `FollowSummary`), and the best streak. Identical for identical play (SC-004): the score is a function of the ordered results only. Nothing is stored (FR-026): the result lives in the object of the run and in the result window.

## R6 - Verdicts, chords and "uncertain"

**Evidence.** `FollowSession.EventResult` carries `status()` (MATCHED, WRONG_PITCH, MISSED, NOT_JUDGED, AUTO_PASSED, PENDING), `pitch()`, `timing()` (ON_TIME, EARLY, LATE, MISSED), `offsetNanos()`, `missingPitches()`, `techniques()`. `ChordFeedback` (kinds WAITING, UNCERTAIN, STRUM_AGAIN, INCOMPLETE) belongs to `PracticeSession` (wait mode).

**Decision.** `ArcadeVerdict` is derived from `EventResult` by one tested function: ON_TIME, EARLY, LATE, WRONG (note) / INCOMPLETE (chord: `missingPitches` not empty), MISSED, NOT_JUDGED, AUTO_PASSED, and WAITING while PENDING. A chord's verdict is chord-level (one badge); `getUnverifiable()` of the wait mode is not available in the following mode, so the cartouche says that duplicated or inseparable pitches are "not checked separately" from the event's `hasDuplicatePitches()` alone. `STRUM_AGAIN` is not produced (the following mode never asks for a second strum). UNCERTAIN: see O1 in `analysis.md`; proposal: NOT_JUDGED is shown as "not judged", neutral, never as an error.

**Techniques** stay a separate line, as in `FollowView`: they never change the verdict, the score or the streak.

## R7 - Pause and resume (US5.2)

**Evidence.** `FollowPlayer` has no pause; `MidiPlayer.pause()` exists (`stop(true)`) and `play()` resumes from the stored tick position. `FollowSession` pauses the judging with reasons.

**Decision.** `FollowPlayer.pause()/resume()` and `FollowPlayback.pause()/resume()`: pause stops the player at its tick (the mutes, percentage and count-down stay in place, the listener stays attached, the stop notification is the run's own and is not an end), resume sets the tick position to the paused tick and plays again with a count-in; the session is paused with a reason for the whole time, so the events of the pause are NOT_JUDGED and nothing is missed. Tests with `FakeMidiPlayer` first (T020, T021). The capture keeps running.

## R8 - Choosing the track

**Decision.** The learner chooses explicitly from a list of tracks that can be played (`ArcadeTrackCandidates`, R2), with the cursor's track pre-selected as in `PracticeTrackCandidates.evaluate`, never silently. Percussion tracks and tracks without events are excluded with the reason shown, as in the practice window. The chosen track is the only one muted in the player (`FollowPlayback` with its track number); a song with a single track keeps the chosen one silent and the count-in/metronome (FR-014) - a test pins that it is not re-enabled.

## R9 - Entry points and mode integration

**Evidence.** `FollowSettings.Mode` (WAIT, FOLLOW), `FollowPanel` (mode dropdown), `PracticeWindow.startSession` branching, `PracticePlugin` (toolbar button, Tools menu, `OPEN_ACTION_ID = "practice.open"`).

**Decision.** `Mode.ARCADE` is added next to the two; the preparation window offers it with its track, speed and (when available) monitoring settings; the Arcade run reuses `FollowRun`/`FollowStarter` for the clock, the backing and the session, and replaces `PracticePalette` by the Arcade window. Mode choice remembered for the session only (US5.1), as `FollowSettings` already does. The Follow verdicts are never modified by Arcade (the score is computed from the results, on the side).

## R10 - Result without persistence

`ArcadeResult` is an immutable object built from the final `FollowSession.Snapshot` and the score table; it is shown in a result window and dropped. No file, no configuration key, no history (FR-026). A test pins that no `TGConfigManager` write happens during a run (a recording stub).

## R11 - Bass recognition (open, spike)

Window 2048 samples at 48 kHz in `TarsosFastYinEstimator` holds 1.7 periods of an E1. The tuner and the wait mode were validated on a guitar. **Task T003** measures the recognition of synthetic and, when available, recorded bass notes down to E1/B0 through the existing chain and reports the rate before a bass is promised. Possible remedy (not decided): a longer window for low pitches. Out of scope until measured.

## R12 - Live monitoring: what the existing stack gives (spike E)

**Evidence.** `CaptureService` reads a `TargetDataLine` in 1024-frame blocks (about 21.3 ms at 48 kHz) into `SampleRingBuffer`, the analysis pulls from it; `CaptureClock` dates samples. The synthesizer writes to a `SourceDataLine` (`TGAudioLine`, 44.1 kHz in `TGAudioBuffer`, with a buffer of several blocks). There is no output selector in the audio engine.

**Arithmetic, not a measurement.** A capture block of 1024 frames is 21.3 ms by itself; the output buffer and the device latencies come on top. On the plain Java Sound path the 22 ms limit is therefore unlikely; it can only be settled by **measurement**: smaller capture blocks (128 to 256 frames), a dedicated low-buffer `SourceDataLine`, and a physical loopback. The spike (T070 to T075) measures, reports median, 95th percentile and maximum, and concludes. If no configuration of this machine reaches the limit with the worst credible value, monitoring is not offered (FR-041) and Arcade ships without it, with the explanation.

**Not to be confused with**: the 64 to 66 ms of the rhythmic calibration of 002 (an acoustic speaker-to-microphone round trip, used to correct the judging). Different quantity, different path, different purpose.

**Open**: sharing a timeline between the MIDI backing line and a monitoring line (FR-036); two Java Sound lines have independent clocks; measured drift is part of the spike.

## R13 - Latency protocol

See `contracts/monitoring-latency-protocol.md`: deterministic accounting, wired loopback (reference), acoustic loopback (indicative), trial counts, statistics, worst credible value.

## R14 - Effects (deferred)

No effect is implemented in the first increment (FR-032). Task T090 is the **research** only: inventory of free/open-source real-time libraries, licence/maintenance/platform/integration/real-time safety/CPU/allocations table, and the identical benchmark in five states, in the actual monitoring path, with the 22 ms gate. `contracts/effects-research-protocol.md` fixes the protocol so that the later work is reproducible. A library with an incompatible or uncertain licence is rejected before any code is written (the project is GPL-3.0 with TarsosDSP already inside the audio engine; compatibility is checked per candidate).

## R15 - Tests

* **Pure and deterministic** (no window): the core, the verdict mapping, the scoring, the neck geometry, the string mapping and colours, the projection, the camera track, the visibility invariants, the chord cartouche model, the accessibility rules (monochrome identification), the pause/backing lifecycle on `FakeMidiPlayer`/`FakeTempoClock`.
* **Headless toolkit** (`SwtContractToolkit`): the preparation controls and the result window.
* **Native SWT** (profile `swt-gui-tests`): the canvas paints without exception for a six-string and a four-string scene; a controlled capture of a fixed scene compared with an approved reference **by structure** (the list of shapes and texts of the scene, which is deterministic) and, optionally, by pixels on this machine.
* **Performance**: the time to build and replay a scene on a dense passage, measured and reported (R3).
* **Real-world**: three guitarists reading the view (SC-009), the qualitative check of SC-008, the physical loopback (SC-003): human or hardware, never reported as done before they are.

## R16 - Texts and accessibility

English, French and Spanish keys under `arcade.*`, with the parity test pattern of `FollowStringsTest`. Verdicts always carry a symbol and a word; the string label, the position and the geometry identify a string with all colours removed (a test renders the scene in monochrome and checks the identifiers). The numeric score is never shown while playing (FR-021, FR-026).
