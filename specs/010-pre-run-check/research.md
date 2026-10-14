# Research: Pre-run check (spec 010)

Every claim cites a file that was opened or data that was measured on 2026-10-14.

## R1 - Where the clipping decision lives, and why it missed the owner's case

**Evidence.** `audio engine analysis/LevelAnalyzer.java` marks a 2048-sample window (`OfflineAnalysis.WINDOW`, hop 256) as `CLIPPING` when `clippedRatio >= clippingRatioThreshold`; the default `RecognitionParameters.LEVEL_CLIP_SHARE` is 0.10 (range 0.01 to 0.50) and `LEVEL_CLIP_SAMPLE` is 0.99. `analysis/InputAssessor.java` then looks only at **playing frames** (a confident pitch, `playingConfidence` 0.80): `SUBOPTIMAL/CLIPPING` when 10 % of them clip, `UNUSABLE` at 50 %; `QuickStart.canStart` lets `SUBOPTIMAL` start, and the transport-bar Arcade button starts without waiting for it at all. A polyphonic strum has little confident pitch, so most of its clipped frames are not "playing frames", and a strum clips for 2 % to 28 % of its first 300 ms (below).

**Measured.** Owner takes through the Rocksmith adapter, first 300 ms after the attack: gain 0.99, 21 takes, shares 0.0, 0.2, 0.6, 0.8 and 2.1 to 28.4 %; gain 0.40, 21 takes, 0.0 % each. GuitarSet, 180 clean microphone recordings, worst 300 ms window: **0.23 %** (26 of them touch 0.98 at least once). Accepted chords: 8 of 21 at 0.99, 18 of 21 at 0.40 (`OwnerChordsBenchmarkTest`).

**Decision.** A new pure meter, `StrumClipMeter`, in the audio engine: after an attack it counts the samples with `|x| >= LEVEL_CLIP_SAMPLE` over the next 300 ms and reports clipping when the share is **0.5 %** or more. 0.5 % is between the worst clean window (0.23 %, margin more than 2) and the owner's clipped takes (19 of 21 above). It is a constant of the meter's `Parameters` (provisional, like `InputAssessor.Parameters`), **not a new learner setting** (spec: no setting without need). The frame-level `LevelState` and `InputAssessor` stay as they are: the recognition does not depend on this meter (SC-005).

**Proof to do, through the real code.** The python figures used 0.98; P1 re-measures GuitarSet and the owner's takes through `OfflineAnalysis` with the real `LEVEL_CLIP_SAMPLE` 0.99 and writes them in `benchmark/`; if a figure moves the threshold is re-argued before it is used.

**Alternatives.** Lower `LEVEL_CLIP_SHARE` to 0.005 (rejected: it would flag the frame-level state used by the recognition and the tuner, and a 2048 window is too short to carry 0.5 %); count in `InputAssessor` playing frames only (rejected: it is the case that missed). Sum `clippedRatio` of the overlapping frames (rejected: each sample is counted in eight windows, edges are approximate).

## R2 - The attack and the 300 ms window

**Evidence.** `AnalysisService` loop pushes each hop (256 samples) to the attack analyser (`attackAnalyzer.push(hop, 0, hopSize)`); frames carry an `onset` flag from `ArticulationDetector` (`OfflineAnalysis` line 69). 300 ms are about 56 hops at 48 kHz.

**Correction (analysis A1/I1 of 2026-10-14).** `AnalysisService.pushAttack` returns at once when no attack candidates are set, which is the case before a run, so the hook cannot go there; the hop is complete in `run()` right after `frontEnd.process(hop, hopSize)` (the gain and the noise reduction come first), and that is where the listener is called: the meter then sees the same signal as the level, the pitch, the attack and the spectrum. The window reaches it through a facade `AudioEngine.addHopListener / removeHopListener`, as it reaches the frames through `AudioEngine.subscribe`.

**Decision.** The meter is fed once per hop through a small optional hop listener added to `AnalysisService` (in `run()`, after the front end) and to `OfflineAnalysis` (the same call in both, so the tests run the production loop). It arms on a strum: the first hop whose RMS is **at least `INPUT_LEVEL_MIN` (-40 dBFS by default) and** at least 12 dB above the quietest of the 60 hops before it (the hook receives hops, not frames, so the `onset` flag of the articulation detector, which lives on frames, is not used); it then accumulates the next 300 ms (clipped samples, sum of squares, peak) and gives a verdict; a new check re-arms it. Nothing is allocated per hop.

## R3 - The verdicts

**Precedence.** When more than one applies the reported verdict is `CLIPPING`, then `TOO_LOUD`, then `TOO_WEAK` (a clipping signal is never called weak, a loud one never weak).

**Decision.** `NO_STRUM` (nothing armed yet or only silence), `CLIPPING` (share >= 0.5 %), `TOO_WEAK` (the mean RMS of the window under `INPUT_LEVEL_MIN`, -40 dBFS by default, the same number the existing level state uses), `GOOD`. The "too loud without clipping" state of the existing check (`TOO_HIGH`, RMS above `INPUT_LEVEL_MAX`) is reported as `TOO_LOUD` with the same guidance as clipping. The texts of the existing `practice.level.guidance.*` are reused for the advice.

**Evidence of the weak side.** At gain 0.40 the owner's takes have an RMS of 0.008 to 0.021 (about -42 to -34 dBFS): the lower gain is near the weak limit; a "too weak" verdict at that level is expected for the quietest ones and is the right advice (raise to about 0.6).

## R4 - Where the start window opens, and restarts

**Evidence.** `PracticeWindow.startSession()` (line 638) is called by the Start button, by the one-click quick start (`startNow`, line 631) and by `openAndStartArcade()` (the transport-bar Arcade button, `immediateStart`); `restartArcade()`, `restartArcadeFromStart()`, `restartFollow()` re-enter `startArcade` / `startFollow` directly, not `startSession`.

**Decision (written in the spec as FR-013).** The check wraps `startSession()`: it opens `PreRunWindow`; the existing body runs only when the check says READY. Both ways of starting, the three modes and the quick start go through it with no other change. **Restarts and "play again" inside the same run window (Restart button, result window, from a measure) do not ask again**: they are the same sitting a few seconds later, the tuning and the level were just judged, and asking again after every try would make the practice loop unbearable; FR-001 speaks of the start of a mode from the preparation window and the buttons of the transport bar. This is the one reading of the spec to confirm with the owner at gate G.

**The start becomes asynchronous.** Until now `startSession()` began the run at once; the callers (the Start button, the quick start `executeLater`, `openAndStartArcade`) do nothing after it, so they do not assume the run started, but a second request or a change of the window while the start window is open must be handled: `StartFlow` keeps one pending check (the tuner wait included), the mode and the candidate are captured at the request, `startSessionNow` starts only if the preparation window is still open and idle, and the check is cancelled when the window is disposed (contract invariants 12 and 13). The real gate reads the engine, the UI factory and the parent at `request()`, because `this.engine` is set in `open()` and the constructor builds no widget; and the start window has no hidden parent, since the transport-bar flow starts with the preparation window hidden.

**The Arcade button.** It used to start "without waiting for the level check" (`QuickStart.canStart(..., immediate)`); it now opens the start window first, as the clarification says; the preparation window is still not shown.

## R5 - The tuner and the way back

**Evidence.** The tuner opens through the action `tuner.tuner` (`TunerNextPlugin.ACTION_ID`, `TGActionManager.execute`); `TunerWindow` sets `PracticeTunerContext.setTunerOpen(true)` on open and `false` when its window is disposed; `InputEvents.tunerStateChanged` already turns that into a `TUNER_OPEN` pause of a running session.

**Decision.** "No, open the tuner": the start window closes, the action `tuner.tuner` is executed, and the start window listens to `PracticeTunerContext` (the listener `InputEvents` uses); when the tuner closes it opens the start window again, at the tuning question, with the mode and the track as they were (`startSession` is simply called again by the caller of the check). The mode never starts by itself (FR-007). If the tuner was already open the action brings it forward.

## R6 - The live level indicator

**Evidence.** `LevelPanel` subscribes to the engine (`engine.subscribe(frameListener)`; the capture is held by the preparation window) and updates a progress bar from the latest frame at about 30 updates a second (`updateMeter`, RMS from -60 dBFS).

**Decision.** `PreRunWindow` reuses the same engine subscription and the same meter scale for the live bar, plus the verdict of `StrumClipMeter` for the sentence under it; the capture is already held: `PracticeWindow.open()` does `engine.acquire(CONSUMER_ID)` (line 290) and the preparation window is open, hidden or not, at every start including the transport-bar one, so `PreRunWindow` reuses it and does **not** acquire again (a second `acquire` would need its own consumer id and release); it adds and removes only its own subscription and its hop listener.

## R7 - Texts

**Decision.** New keys `practice.prerun.title`, `.tuned.question`, `.tuned.yes`, `.tuned.no`, `.strum.ask`, `.strum.heard`, `.continue`, `.check-again` and the verdict lines, in `messages.properties`, `messages_fr.properties`, `messages_es.properties`, checked by `FollowStringsTest`'s pattern (same keys, same placeholders). The advice for clipping and weak signals reuses `practice.level.guidance.CLIPPING` / `TOO_LOW` / `TOO_HIGH` unchanged.

## R8 - The recognition regression tests that exist, and what is missing

| Test (module) | Kind of event | Data | Tag / condition |
|---|---|---|---|
| `RealGuitarRegressionTest` (audio engine) | single note, pitch baseline | 22 committed clips of `benchmark/builtin-mic` | default suite |
| `MonophonicRegressionReplayTest` (practice) | single notes, attack rule (spec 004 SC-004) | real single-pluck clips | skipped when absent |
| `RecordedPracticeReplayTest` (practice) | notes and chords, fingerstyle with a ringing bass | `tests/1` (owner's, not redistributed) | skipped when absent |
| `BendRecordedReplayTest` (practice) | bend, pre-bend, release (synthetic render) | `tests/2` (owner's) | skipped when absent |
| `LegatoFunctionalTest`, `PitchTechniqueDetectorsTest`, `BendDetectorTest` | hammer-on, pull-off, techniques on synthetic signals | generated | default suite |
| `TechniqueDatasetBenchmarkTest` (practice) | vibrato, bend, slide, plain, against IDMT-SMT-Guitar 2 | `~/tuxguitar-datasets` | `dataset` |
| `DatasetBenchmarkTest`, `FastPassageBenchmarkTest`, `NoteSpacingBenchmarkTest`, `WeakInputBenchmarkTest` | notes, intervals, chords, fast passages, weak input | IDMT-SMT-Guitar | `dataset` |
| `GuitarSetChordBenchmarkTest` (practice) | chords, complete and with a note missing | GuitarSet | `dataset` (runs in the default build, skips without data) |
| `OwnerChordsBenchmarkTest` (practice) | chords on the owner's guitar | `owner-chords*` (not committed) | `dataset` (runs in the default build); reports only until T029; skipped when absent |

**Missing.** (1) A **default-suite** guard for chords: every chord test above needs data kept outside the repository; a few committed chord clips (synthetic, generated by the tests' own signal code, no copyright) would catch a regression in the default build. (2) The **owner's takes have no committed figures**: the test reports them but asserts only 100 % / 0 %, which fails today; it needs a recorded baseline ("not worse than N of 42") in `before-after.txt`. (3) **Techniques other than bend, vibrato and slide** (hammer-on, pull-off, palm mute, tapping, tremolo picking) are covered only on synthetic signals (`LegatoFunctionalTest` and the technique signals): no recorded-audio test; the plan does not invent one without data, and says so (SC-007 covers "each technique" as far as data exists; the gap is reported, not hidden).

**Decision.** P0 runs every suite that can run here and writes its figures to `benchmark/before-after.txt` before any code; P4 adds (1) and (2) and compares.

## R9 - Findings outside this feature

While measuring, the D major chord (x x 0 2 3 2: D3 A3 D4 F#4) was refused in 3 of 5 takes even at gain 0.40, because its clear unique partials are few at a low level (`ChordEvidence`: 0 to 1 clear partials for each of the four pitches at -27 to -36 dBFS). That is recognition (spec 004 T007), out of scope here (constitution X); it is recorded in `OwnerChordsBenchmarkTest` figures and proposed as a separate piece of work.

## R14 - What the tenth analysis recomputed (2026-10-14)

**Arming (F1).** With the rule "12 dB above the quietest recent hop" alone the meter arms on noise or a pick click before the strum (hops at -62, -48 and -57 dBFS in the owner's takes) and only **14 of 21** takes at gain 0.99 reach 0.5 %; the 19 of 21 first measured used the maximum of the whole take, which the code cannot know. With the absolute floor (hop RMS at least -40 dBFS, the existing `INPUT_LEVEL_MIN`) **and** the 12 dB rise: **19 of 21** at gain 0.99, **0 of 21** at gain 0.40, and no take of the 42 is "too weak" (mean RMS over the 300 ms: -26 to -34 dBFS at gain 0.40). A floor of -30 dBFS would miss 3 takes at gain 0.40 (never armed): the floor must not be above -40.

**GuitarSet at the real clip level (0.99).** 180 files; 26 touch 0.99; the worst 300 ms window anywhere is **0.18 %** (0.23 % at 0.98); the first strum of each file is at **0 %**; margin of 2.8 on the 0.5 % threshold.

**Sample rate (F2).** The window is counted in **samples**, `round(0.3 x fs)`: 14,400 at 48 kHz and 13,230 at 44.1 kHz, so 0.5 % is 72 and 66 samples; 300 ms is not a whole number of hops of 256 (56.25 hops at 48 kHz), the meter cuts at the sample. `AnalysisService` can reconfigure while a check runs (`pendingConfiguration`, another device or rate): the hop listener is told the rate by `configure(int sampleRate)` at the start and at each reconfiguration, which resets the meter to `NO_STRUM`.

**Software boost (F3).** The meter sits after `frontEnd.process`, so it judges the signal the recognition sees, boost included. When the verdict is `CLIPPING` and the in-application gain is above 1, the start window adds the existing text `practice.gain.warning.BOOST_CAUSES_CLIPPING` to the advice, because the interface gain alone would not cure it.

**"Too loud" (F6).** No take of the 42 has a mean RMS above -6 dBFS (the loudest is -7 dBFS at gain 0.99): `TOO_LOUD` without clipping is covered by synthetic signals only; real saturation is caught by `CLIPPING`, which the verdict order puts first.

