# Contract: tempo-following mode (practice, Phase E)

```java
final class FollowTimeline { static FollowTimeline build(PracticeSequence seq, TempoMap tempo, int percent); long targetNanos(int eventIndex); }
final class TimingTolerance { Preset preset; double earlyBeatFraction, lateBeatFraction; long floorMillis; long latencyOffsetMillis; Window windowFor(int eventIndex, FollowTimeline tl); }
final class FollowSession implements SessionLoop.Handler<PracticeSnapshot> { /* onFrame, onCommand; same thread model as PracticeSession */ }
```

## Behaviour (testable)

1. Start: input must be suitable (same gate as wait mode), then a count-in (player count-down or own click), then position advances with the clock at `tempo × percent`.
2. Each onset with pitch evidence within an event's window `[t − earlyTol, t + lateTol]` is matched to that event (nearest unmatched); verdict ON_TIME inside the tight window, EARLY/LATE in the outer window, MISSED when the window closes unmatched, EXTRA for an onset matching nothing.
3. Resynchronisation: a wrong, missed or extra note never shifts the matching of later events (property test over random error injections).
4. Timing uses the **transient time** of the onset, not the decision time, and subtracts `latencyOffsetMillis`.
5. Tolerance scales with the local beat duration (fraction) with a millisecond floor, so the same preset works at 60 and 180 BPM.
6. Chords use `ChordMatcher`; techniques use the Phase D/F judges; neither changes the timing verdict.
7. Modes are exclusive per session; switching back to wait mode leaves MVP behaviour unchanged (existing tests stay green).
8. Repeats and tempo changes follow the player's `MidiRepeatController` order, as `PracticeSequenceBuilder` already does.
9. Summary: counts of ON_TIME/EARLY/LATE/MISSED/EXTRA, in memory only.

## Clock (subject to spike S-T1)

Recommended: TuxGuitar player as clock and backing track (practice track muted player-side only), frames related to the player position by a measured stream-time ↔ `System.nanoTime` mapping plus the latency offset. Fallback: practice-owned nanoTime scheduler and click. The contract above is identical for both; only `TempoClock` (an interface with `nowNanos()` and `songPositionFor(nanos)`) differs.

## Clock and route (decided 2026-10-07, research R6c)

* The clock is the **player's**: ticks from `MidiPlayer.getTickPosition()` interpolated with `System.nanoTime()` between two changes (`TempoClock`, `TickInterpolator`); the capture is dated by **sample count** and related to the wall clock by the lower envelope of the arrival lag (`CaptureClock`), never by the arrival time of a read.
* One **latency offset** (output + input), calibrated by the learner, is kept per **input + output + audio format/profile** and dropped or asked again when any of them changes (FR-058). Inputs and outputs are never filtered by name or transport, Bluetooth included (FR-057).
* No calibration, or an unstable route: the practice says **"reduced rhythmic precision"** in every mode that judges rhythm, warns of a high or unstable latency, and never blocks; the tuner and the wait mode do not depend on it.
* Calibration sounds are announced with their approximate duration and a Cancel button (FR-059).
