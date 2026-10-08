# Tasks: Arcade mode (specification 003)

**Inputs**: `spec.md`, `visual-design.md`, `analysis.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/`.
**Rules for every task**: tests are written first and must fail for the right reason; a task is ticked only when its criteria were actually run; hardware, human and loopback tasks are marked **[HARDWARE]** / **[HUMAN]** and are never ticked on a proxy; the six-string wait and following modes and all existing tests stay unchanged; commits are small and local; nothing is pushed.
**Paths** are relative to `desktop/TuxGuitar-practice/` (`SRC` = `src/app/tuxguitar/app/tools/custom/practice`, `TEST` = `test/app/tuxguitar/app/tools/custom/practice`) unless stated.

## Phase 0 - Prerequisites

- [ ] T001 Gate: at every checkpoint report the state of the manual validation of specification 002 (T096, T097, T098, T141, T102) and the measured SC-009 figures; an Arcade release is blocked until 002 T102 is ticked by the owner (no code).
- [ ] T002 [P] Generalise `SRC/model/TrackTuning.java` to the actual string count, additively: `TrackTuning.of(track, ...)` for a track of N strings, the six-string behaviour, `STRING_COUNT`, `builtInSixStringMinimum` and every existing test unchanged. Tests first in `TEST/model/` (four-string bass E1 A1 D2 G2, six-string guitar, seven-string guitar, a track offset, the six-string constructor still throwing for a wrong array length). Separate commit.
- [ ] T003 [SPIKE] [P] Measure pitch recognition of bass notes (B0 to G2) through the existing chain on synthetic plucked strings (`OfflineAnalysis`, `NoteInterpreter`), with and without a longer analysis window, and report the recognised share and the delay: `TEST/functional/BassRecognitionBenchmarkTest.java` (tag `benchmark`), report `specs/003-arcade-mode/benchmark/bass-recognition-<date>.txt`. Outcome decides open question O4; no change to the analysis window in this task.
- [ ] T004 `SRC/model/ArcadeTrackCandidates.java`: the tracks the learner can choose (pitched, not percussion, with at least one practice event, any string count), each with the reason when excluded; the cursor's track is pre-selected but never started without the learner's confirmation. Tests first (`TEST/model/ArcadeTrackCandidatesTest.java`): guitar, bass, percussion excluded with its reason, empty track excluded, pre-selection, the wait-mode `PracticeTrackCandidates` unchanged.

## Increment A - Core without interface (`SRC/arcade/core`)

**Goal**: score, streak, verdicts and result from the results of the following session, deterministically, with no widget and no clock. **Independent test**: feed recorded sequences of results, compare score and result exactly.

- [ ] T010 [P] Tests first `TEST/arcade/core/ArcadeScoringTest.java`: the table of research R5 (points per verdict, extras unscored, not-judged and auto-passed uncounted), the multiplier steps (below 10, from 10, from 25), the maximum score of a run, the multiplier using the streak before the event.
- [ ] T011 `SRC/arcade/core/ArcadeScoring.java` (data table + pure functions) until T010 passes.
- [ ] T012 [P] `SRC/arcade/core/ArcadeVerdict.java` with tests first: the mapping is total and unique over every `FollowSession.Status` x `TimingVerdict` x chord/note, a chord with `missingPitches` is INCOMPLETE, a note is WRONG, PENDING is WAITING, techniques never change it. (Resolves the contradiction between `visual-design.md` and the following mode, research R6.)
- [ ] T013 `SRC/arcade/core/ArcadeRun.java`, `ArcadeResult.java` with tests first: idempotent `update` (same snapshot -> same view), streak rules (extend / reset / unchanged), best streak, extras, result built from `FollowSummary` with `percent` of the maximum; mutations of each rule fail a test.
- [ ] T014 Whole-run determinism `TEST/arcade/core/ArcadeRunFunctionalTest.java` on `FakePlayerRun`: an injected wrong note, a missed note and an extra note never delay the following events (their windows are unchanged), two runs of the same recorded play give identical verdicts and score (SC-004), a pause leaves NOT_JUDGED events uncounted.
- [ ] T015 [P] No persistence: a test with a recording stub of the configuration and the file system proves a whole run and its result write nothing (FR-026).
- [ ] T016 Checkpoint A: targeted tests, practice module, audio-engine and tuner modules, full macOS build; report exactly.

## Increment B - Backing (`SRC/session`, `SRC/arcade`)

**Goal**: the chosen track alone is silent in the player, everything else plays, with pause/resume and exact restoration. **Independent test**: `FakeMidiPlayer`/`FakeTempoClock` runs and the real `MidiPlayer` without sound.

- [ ] T020 `FollowPlayer.pause()/resume()` (port), `FakeMidiPlayer` (count-in on resume, tick kept, notifications of its own pause not seen as an end), `MidiFollowPlayer` adapter over `MidiPlayer.pause()/play()` + `setTickPosition`; tests first in `TEST/session/` and `TEST/testsupport/`.
- [ ] T021 `FollowPlayback.pause()/resume()`: lifecycle tests first (pause while counting in, pause twice, resume without pause, stop while paused, the learner's Play while paused ends the run and the playback goes on, the end of the song while paused), exactly-once restoration repeated for every paused path; implementation until green. Mutation: removing the restoration in a paused path fails a test.
- [ ] T022 `SRC/arcade/core/ArcadeStarter.java` on `FollowStarter`: refuses to start without an explicit track from T004; builds the run for the chosen track and speed; tests first with `FakePlayerRun`-style fakes.
- [ ] T023 [P] Tests: only the chosen track is muted in the player and only for the run; the song's `isMute()/isSolo()`, tracks and undo are unchanged (digest before/after as in `FollowSummaryTest`); a single-track song keeps its track silent and plays the count-in (FR-014); everything restored after stop, error, learner's Play and a new song.
- [ ] T024 [P] Tests: speed 70 and 100 %, a tempo change, a repeat and an alternative ending: the backing and the judging follow the same order and tempo (reuse the cases of `FollowFunctionalTest` through the Arcade starter).
- [ ] T025 [P] Device loss and refusal at run level: the player failing to start or stopping mid-run, the input becoming unusable (`FollowEvents` pause), never a hang; messages in the three languages are added with T056.
- [ ] T026 [P] `SRC/arcade/core/ArcadeAdvice.java` (pure): when the selected input may hear the speakers (a microphone-kind input with a speaker output) the preparation says to use headphones (FR-013); tests with `AudioInputDescriptor.Kind` cases.
- [ ] T027 Checkpoint B: as T016.

## Increment C - Isometric view (`SRC/arcade/view`, `SRC/arcade/ui`)

**Goal**: the approved view, derived from the real models, deterministic. **Independent test**: scene tests without a window; native paint tests.

- [ ] T030 [P] `StringLayout` (tests first: 4, 6, 7 strings, string 1 high at the top and at the right of a diagram, one mapping function, colours never swap).
- [ ] T031 [P] `NeckModel` (tests first: `fretX(12) == L/2`, every fret from `x(n) = L (1 - 2^(-n/12))` to 1e-9, markers 3 5 7 9 15 17 19 21 single and 12 24 double, plausible gauges; no value copied from the concept images) - SC-011.
- [ ] T032 [P] `StringColours` (tests first: the six-string palette low to high, other counts distinct and frozen for the run, monochrome-safe contrast).
- [ ] T033 [P] `IsometricProjection` (tests first: the hit line at a fixed depth, monotone depth scaling, resize invariants).
- [ ] T034 `ArcadeRenderEvent` built from `PracticeEvent` + `FollowTimeline` only (tests first: onset equals `targetNanos` across tempo changes and speeds; `MULTI_NOTE` and `AUTO_PASS` presented as what they are; no second score parser).
- [ ] T035 `CameraTrack` (tests first, property tests over generated timelines: every target fret of every visible event inside the window with the margin from the first visible frame to judgement; continuity (bounded step), determinism, seekability, bounded zoom, no oscillation between adjacent targets) - SC-010.
- [ ] T036 `ChordCartouche` (tests first: A5 fixture low-E 5, A 7, D 7 -> `5fr` window and three coloured dots; open chord `O`; explicit dead note `X`; omitted string blank; metadata fallback and blank name -> "Chord"; stroke up/down/none; duplicate pitches "not checked separately"; minimum size).
- [ ] T037 `ArcadeScene` for notes (tests first: marker, trajectory, target ring, label share the destination string colour from spawn to impact - SC-012; progress exactly 1 at the onset; render states HIDDEN .. EXPIRED).
- [ ] T038 `ArcadeScene` for chords and verdict badges (tests first: one bar + one cartouche as one transform, ghost on the neck when readable, chord-level badges from `ArcadeVerdict`, missing pitch names, no per-string "heard").
- [ ] T039 [P] Accessibility test: a monochrome render still identifies string, fret, chord timing and verdict; verdicts always have a symbol and a word.
- [ ] T040 [P] Density and resize rules (tests first: a documented drop order, the current event never hidden, the minimum cartouche size on resize).
- [ ] T041 `SRC/arcade/ui/ArcadeCanvas.java`: `UICanvas` + `UIPaintListener`, repaint timer on the UI thread, replays the scene on the `UIPainter`, reads the player's `TempoClock`, skips a slow frame instead of queueing it.
- [ ] T042 Performance measurement (tag `benchmark`): build + replay time of a scene on a dense passage and on a chord-heavy one; record the figures and set the acceptance bound in this task (SC of FR-025 "does not slow the judging").
- [ ] T043 Native SWT tests (`swt-gui` tag): the canvas paints a six-string and a four-string scene without exception; the structural list of shapes and texts of fixed scenes equals the approved reference.
- [ ] T044 [HUMAN] The approved reference scenes are reviewed by the owner against the approved concept images (composition, not pixels) before they become the test reference.
- [ ] T049 Checkpoint C: as T016 plus the native tests.

## Increment D - Interface and result (`SRC/arcade/ui`, `share/lang`)

**Goal**: start, play, pause, stop and read the result, in three languages. **Independent test**: headless and native GUI tests, then the manual guide.

- [ ] T050 `FollowSettings.Mode.ARCADE` and the mode choice in `FollowPanel` (tests first: the three values stay independent, the wait and following behaviours unchanged, the choice remembered for the session only).
- [ ] T051 `ArcadePanel` in the preparation window: the track (T004), the speed, the tolerance (reused), the explanation that monitoring is unavailable until E decides; headless tests with `SwtContractToolkit`.
- [ ] T052 `ArcadeController` orchestrating `ArcadeStarter`, the run, the scene feed and the end for every cause (Stop, error, learner's Play, end of the song, new song, closing the window); lifecycle tests on fakes (two successive runs, stop in the count-in, exception).
- [ ] T053 `ArcadeWindow`: the canvas, the HUD (speed, streak, next expected event; **no number for the score while playing**), pause/resume/stop; headless tests of the HUD texts and of the buttons' states.
- [ ] T054 `ArcadeResultWindow`: score, percentage of the maximum, counts per verdict, best streak, techniques apart; shown once, nothing kept (FR-026); tests.
- [ ] T055 Replay a section (US5.3): restart from a chosen measure with the same settings through `PracticeSequence.startingAt`; tests.
- [ ] T056 Texts `arcade.*` in `share/lang/messages.properties`, `_fr`, `_es` with the parity test of `FollowStringsTest`; the name "Arcade" stays a single key until the owner confirms it (O3).
- [ ] T057 Entry points: Arcade offered from the practice entry points (toolbar button, Tools menu); the mode remembered for the session; test through the existing launcher state.
- [ ] T058 Native SWT tests: the Arcade panel and window refit on resize, nothing outside the window, closing and opening again leave no stale listener (pattern of `FollowPanelSwtTest`).
- [ ] T068 [HUMAN] SC-008: at least three guitarists play a run and say whether the feedback told them what to improve (timing versus pitch); the owner recruits them and the answers are recorded as given.
- [ ] T069 [HUMAN] SC-009: at least three guitarists read the next note and its string/fret at about 90 BPM without stopping, identify a chord as one event and tell every verdict without relying on colour; recorded as given.
- [ ] T059 Checkpoint D: as T016, the manual guide of `quickstart.md` prepared; **[HUMAN]** the owner's play-through is not claimed.

## Increment E - Monitoring spike (`desktop/TuxGuitar-audio-engine`, benchmark tools)

**Goal**: know, by measurement, whether any configuration reaches 22 ms. Nothing is built before T075 decides. See `contracts/monitoring-latency-protocol.md`.

- [ ] T070 [P] Spike tooling in the audio-engine test tree (tag `benchmark`): a harness that reads the capture at chosen block sizes (128, 256, 512, 1024), writes to a low-buffer output line, and logs the accounting terms of the protocol per block.
- [ ] T071 Accounting measurement on this machine (built-in devices and, when present, the audio interface): the table of terms for each block size; report under `specs/003-arcade-mode/benchmark/`.
- [ ] T072 [HARDWARE] Wired loopback measurement per the protocol (50 trials per configuration, median, p95, maximum, two sample rates, several buffer sizes), only when an interface with two inputs and two outputs and cables are available.
- [ ] T073 [HARDWARE] Acoustic loopback per the protocol (distance, subtraction of propagation, trials, uncertainty), reported separately from T072.
- [ ] T074 [HARDWARE] 30-minute drift measurement between the MIDI backing line and the monitoring line (FR-036).
- [ ] T075 Decision record in `specs/003-arcade-mode/benchmark/`: offer monitoring on which configuration, or not at all, with the worst credible values; if not, Arcade ships without monitoring and the explanation text is added to T056. Only after a "yes": add the build tasks (path, gain, balance, click-free switching, output selector, feedback warning, lost device) to this file as T076 and following.

## Increment F - Effects research (no implementation)

- [ ] T090 Inventory of free/open-source real-time audio libraries with the criteria of `contracts/effects-research-protocol.md` (licence checked first), cited sources and tested versions.
- [ ] T091 The benchmark harness definition and the five states, reviewed before any candidate code is written.
- [ ] T092 Run the benchmark for each viable candidate and write the comparison and the decision (or the decision that none qualifies); gated by the 22 ms result of T075. Reverb and distortion are separate, combinable, bypassable, in a later specification.

## Final

- [ ] T099 Final analysis of the implemented increments against the specification (coverage of every FR and SC, contradictions, dependencies), the exact report of what was run and what was not, and the list of limits (inherited SC-009 figures of 002, bass recognition, monitoring outcome).

## Dependencies and order

* T001 runs at every checkpoint. T002 before T004, T022, T030. T003 and T004 are independent of A.
* A (T010-T016) has no dependency on B or C. B needs T002, T004. C's pure parts (T030-T040) need T002 and T034 needs the timeline only; T041 onwards needs the pure layer. D needs A, B, C.
* E and F depend on nothing above and block nothing above. F's T092 needs E's T075.
* Across features: Arcade A-D first, then 007, 006, 008, 005 (`plan.md`).

## Parallel opportunities

T002 || T003; T010 || T012 || T015; T023 || T024 || T025 || T026; T030 || T031 || T032 || T033 || T039 || T040; T070 || T090.

## MVP scope

Increments A + B + C + D without E and F: Arcade playable with the clear score and the isometric view, no live return. This is the first deliverable; it requires the manual validation of 002 before release.
