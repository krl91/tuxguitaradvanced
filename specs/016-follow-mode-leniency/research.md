# Research: Follow the tempo, made playable (spec 016)

Date: 2026-10-29. Every claim cites the file it was read in (paths relative to `desktop/TuxGuitar-practice/src/app/tuxguitar/app/tools/custom/practice/` unless stated).

## R1. Where Follow refuses a note today

**Read.** `session/FollowSession.java`: an event ends as `MATCHED` (with `TimingVerdict` ON_TIME / EARLY / LATE), `WRONG_PITCH` (another pitch heard inside its window, never the right one; `OpenEvent.wrongSeen`), `MISSED` (window closed, nothing matched, `closeEvents` around line 1150), `NOT_JUDGED` (clocks not ready or paused) or `AUTO_PASSED`. An attack becomes a `PendingOnset` (`registerOnset`, line ~706) with its compensated time (`context.compensation().adjust(transient)`), the events whose window holds it, and then gathers pitch evidence frame by frame (`evaluateFrame`); an onset that matched nothing is counted EXTRA. The pitch decision is `TolerancePolicy.matches(reading, expectedMidi, fret)` (line ~1126) and the `ChordMatcher` for chords.

**Measured already.** `specs/002-practice-industrialization/benchmark/follow-benchmark-recorded-2026-10-08.txt` (test `FollowBenchmarkTest#theRealRecordingAtTheScoreTempoIsReportedWhenItIsThere`): on the reference recording laid at its own tempo, 35 of 42 matched, **7 missed, all 7 with the played note inside its window**, 0 wrong pitch. On real playing the losses were recognition (attack or pitch evidence), not the timing window.

**Decision.** The reasons of FR-001 are derived inside `FollowSession` from what it already sees, kept per event, with no change to any verdict:

| Reason | When |
|---|---|
| `NO_ATTACK` | MISSED, and no attack was registered whose compensated time lies within the event's search span (its window widened by 1 s each side, capped at the neighbours' targets). |
| `UNCLEAR` | MISSED, an attack lay inside its window but its pitch evidence never became decisive (no matching reading above the confidence, no chord decision). |
| `OTHER_PITCH` (heard pitch) | WRONG_PITCH; the most frequent decisive pitch of the attempt is kept (already in `OpenEvent`). |
| `EARLY_OUTSIDE` / `LATE_OUTSIDE` (offset) | MISSED, and an attack with the right pitch was heard **outside** the window within the search span (it was counted EXTRA or matched nothing). |
| `NOT_JUDGED` | as today (pause, clocks, input lost). |

**Alternatives rejected.** A separate analyser re-running the audio after the run (duplicates the session's logic, can disagree with it); extending `PracticeTrace` (frame lines of the wait mode, its format is read by people and tools as it is).

## R2. The record of a run and its audio

**Read.** `session/PracticeTrace.java` (wait mode only, system property, appends CSV per frame); `ui/DiagnosticCopy.java` (spec 013: writes a WAV into `~/tuxguitar-calibration-takes/`, named by date, never throws); `TuxGuitar-audio-engine/.../calibration/HopRecorder.java` (records the hops the analysis sees into memory, allocation-free on the analysis thread, bounded capacity). The replay of a recorded run through Follow exists in tests: `FollowBenchmarkTest` lays a score on a recording with `FakePlayerRun` and `OfflineAnalysis.analyze`.

**Decision.** A `FollowRunRecord` is written at the end of a run, **when the learner ticked the box**, to `~/tuxguitar-follow-takes/<date>.csv`: a header with the settings (percent, time to play, pitch position, route calibration offset, input name, sample rate, the wall time of the first recorded sample relative to the timeline start) and one line per event (index, kind, expected pitches, target ms, status, reason, heard pitch, offset ms, attack ms, level, confidence). With the **second box**, a `HopRecorder` (capacity 10 minutes; a longer run keeps its first 10 minutes and says so) records the run and a WAV of the same name is written. The replay test reads both and feeds `FakePlayerRun` with the recorded alignment, so a position or a time to play can be replayed on the owner's own playing (SC-003). Nothing is written on a run cancelled before its count-in ended; a write failure is told once and changes nothing else.

**Alternatives rejected.** Numbers only (the owner chose the audio copy, Q1); recording the raw capture before the front end (the analysis sees the front end's output: the hops are what must be replayed, as spec 013 decided).

## R3. The timing rule of the owner (FR-011)

**Read.** `session/TimingTolerance.java`: windows are symmetric around the target, as fractions of the beat with floors and ceilings, cut at the middle of the gap to the neighbours. At width 100 (the default of spec 012) and 120 beats a minute: on time ±100 ms, outer ±266 ms; at 70 % of 120 bpm the outer window is ±381 ms before cuts. `session/TimingContext.java` holds a `TimingTolerance` and the route's `LatencyCompensation`; the onset time is already the attack, corrected for the detector latency (`articulationLatencyNanos`) and the route offset (owner's answer at this plan: that is the moment that counts).

**Decision.** A new window policy for Follow, `PlayTimeWindow`, behind a small interface `WindowPolicy` (one method, `windowFor(target, beat, gapBefore, gapAfter)`, the same `Window` result) that `TimingTolerance` also implements, so `TimingContext` takes either and Arcade keeps `TimingTolerance` unchanged:

- late limit = `min(playTime, gapAfter − 20 ms)` (never below 0);
- early limit = `min(50 ms, gapBefore − lateLimitOfPrevious − 1 ns)`, so that the windows of two events never overlap and the 20 ms the owner asked for stays between them;
- verdicts: EARLY from the early limit to the red time, **ON_TIME from the red time to half the time to play (150 ms at the default)**, LATE beyond, up to the late limit. *The split between ON_TIME and LATE is not in the owner's rule; half the time to play is the plan's provisional choice, reported to the owner, and pinned by a test so that changing it is a decision.*
- the beat no longer scales the window: the owner's rule is in milliseconds; the cut at the next note makes fast passages work.

**Note for the owner.** Today's late tolerance is already about 266 ms at 120 bpm and more at slower tempi; the new rule is **not wider on the late side** except at fast tempi, and it is **narrower on the early side** (50 ms instead of up to 266 ms). It is clearer (one number, from the red), and the measures of story 1 will show whether timing was the cause at all.

## R4. The setting of the time to play and of the pitch positions

**Read.** `session/FollowSettings.java` stores the window as `RecognitionParameters.TEMPO_WINDOW` (`TuxGuitar-audio-engine/.../settings/RecognitionParameters.java`, 40–200 ms, default 100), in the default layer of the `RecognitionSettingsStore` so that the Configuration window shows the same value (spec 012). Arcade reads it too (`ui/PracticeWindow.java` line ~816 through `ArcadeLeniency.windowMillis`). `arcade/core/ArcadeLeniency.java` relaxes, one way, five recognition parameters (note confidence 0.70/0.60, note stability 75/55 ms, chord evidence, chord minimum partials, chord confirm) and **not** `NOTE_ACCEPT` (35 cents, range 10–45), because widening it lost a right note in spec 014's measure (R6-ter there).

**Decision.**
- A new parameter `FOLLOW_PLAY_TIME` (ms, 300, range 100–600) in `RecognitionParameters`, stored the same way as `TEMPO_WINDOW` (default layer), shown in the Configuration window in the notes section; `TEMPO_WINDOW` stays, now used by Arcade only (its label gains "(Arcade)" in the three languages).
- A new setting `FollowLeniencyPosition` { STRICT, PERMISSIVE (default), VERY_PERMISSIVE }, stored like Arcade's leniency, applied one way like `ArcadeLeniency` by a new `FollowLeniency` class. Its rows start from Arcade's five and add `NOTE_ACCEPT` (the "right note a little out of tune" of the clarification; its maximum, 45 cents, is below the 50 cents halfway to the neighbouring semitone, so the semitone can never become a near-miss). **The values of each position are not chosen here**: they are chosen in the implementation from the measures (the owner's recorded run for SC-003, the 98 negative tests for SC-004), and written into this research with the figures.

**Alternatives rejected.** Reusing Arcade's leniency (the owner chose a separate setting, Q2); reusing `TEMPO_WINDOW` with a new meaning (it would change Arcade and the values saved by spec 012).

## R5. The calibration offer before a run (story 2)

**Read.** `ui/StartFlow.java` holds one `PreRunCheck` per start; `ui/PreRunCheck.java` states TUNING, TUNER_OPEN, STRUM, LEVEL_BAD, then ready; `ui/PreRunWindow.java` shows them. The route and its calibration: `session/AudioRoute.java`, `session/RouteCalibrations.java` (`forRoute` gives `LatencyCompensation.none()` when not calibrated), `session/CalibrationRun.java` (8 clicks, the median, cancel). Source-structure tests guard the start path: `PracticeWindowStartPathSourceTest`, `PreRunParityTest`.

**Decision.** One more step after a good level, **only for Follow and Arcade**: when `forRoute(route)` is not calibrated and the route is not in the launch-scoped set of declined routes, the start window shows the sentence and two buttons; *Calibrate now* runs the existing `CalibrationRun` inside the start window (its clicks, its cancel), *Go on without* adds the route to the declined set (memory only, so it is asked again at the next launch). The wait mode never gets the step. The existing source tests must stay unchanged and green: the step is added inside `PreRunCheck`'s states, not as a new way to start.

## R6. Measures and limits (SC-003, SC-004, SC-006)

- **SC-004** uses the harness of `test/.../replay/ArcadeLeniencyReplayTest.java` (a copy with Follow's positions, the same 98 negatives built as in `RecordedPracticeReplayTest`), gated `dataset`, skipped without `tests/1`.
- **SC-003** uses the owner's recorded run (R2) replayed with `FakePlayerRun`; until the owner has made one, it is reported as not measured.
- **SC-006**: the default suite after a clean compile equal to the baseline plus the new tests; the recognition reports equal to `specs/010-pre-run-check/benchmark/after/`; Arcade's figures of spec 014 equal.

## R7. Order of the work

Story 1 (reasons and record) first, then a **human gate**: the owner plays one run with both boxes ticked; the shares of the reasons are reported (SC-002) before any value of story 3 is chosen. Story 2 is independent and can go between. Story 3 last.
