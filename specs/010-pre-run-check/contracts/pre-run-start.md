# Contract: the pre-run check (spec 010)

Packages `audio engine analysis` (the meter) and `practice ui` (the state machine and the window). Brought to the code by the tasks; this file fixes the surface and the invariants, each with a test.

## Surface

```java
enum StrumVerdict { NO_STRUM, CLIPPING, TOO_LOUD, TOO_WEAK, GOOD }
final class StrumClipMeter {                       // audio engine, pure, hop by hop
    StrumClipMeter(Parameters p);
    void configure(int sampleRate);                      // before the first hop, at every reconfiguration of the engine, and for a listener added while the engine runs (on the analysis thread): resets to NO_STRUM; the window is round(0.3 x sampleRate) samples
    void hop(float[] samples, int offset, int length);   // analysis thread, no allocation
    void reset();
    StrumVerdict verdict();                         // NO_STRUM until 300 ms after an attack were counted
    double clippedShare();  double rmsDbfs();       // of the window, for the screen and the tests
}
// AnalysisService (in run(), after the front end) / OfflineAnalysis / AudioEngine facade: addHopListener(HopListener) with void configure(int sampleRate) and void hop(float[] samples, int offset, int length)
final class PreRunCheck {                           // practice ui, no widgets
    PreRunCheck(Mode mode);                         // a new object for each start
    State state();
    void answerTuned(boolean tuned);                // yes: STRUM; no: TUNER_OPEN (the full table is below)
    void tunerClosed();                             // back to TUNING
    void verdict(StrumVerdict v);                   // from the meter, read by the window on the UI thread: only in STRUM does it act (GOOD -> READY, a bad one -> LEVEL_BAD, NO_STRUM keeps STRUM)
    void continueAnyway();                          // LEVEL_BAD or STRUM -> READY, for this start only
    void checkAgain();                              // LEVEL_BAD -> STRUM, meter reset
    void cancel();                                  // CANCELLED, the mode does not begin
}
interface StartGate { void request(PreRunCheck check, Runnable onReady); void toFront(); }   // the gate only calls onReady on READY; a cancel changes the check's state and needs no callback: the flow reads check.state()
     // practice ui; the real one opens PreRunWindow
final class StartFlow {                                                  // practice ui, one instance per PracticeWindow, pure
    StartFlow(StartGate gate);
    boolean begin(PreRunCheck check, Runnable startNow);   // false and nothing done when a check is already pending (TUNER_OPEN counts); holds `check`; startNow runs only on READY, once, through the onReady the gate calls (the held reference is dropped before startNow runs)
    void toFront();                                         // brings the pending start window to the front (the tuner, while TUNER_OPEN)
    void cancelPending();                                   // the window closes, its mode changes or the learner picks another track: the pending check is cancelled
    boolean pending();                                      // DERIVED, never a stored flag: true while the held PreRunCheck is in TUNING, TUNER_OPEN, STRUM or LEVEL_BAD
}
// PracticeWindow.startSession(): flow.begin(new PreRunCheck(mode), () -> startSessionNow(mode, candidate)) with the mode and the candidate captured at the request; restarts call startArcade/startFollow directly
```

## The transitions of `PreRunCheck` (total: every pair of state and event is defined)

| State \ event | `answerTuned(true)` | `answerTuned(false)` | `tunerClosed()` | `verdict(GOOD)` | `verdict(CLIPPING / TOO_LOUD / TOO_WEAK)` | `verdict(NO_STRUM)` | `continueAnyway()` | `checkAgain()` | `cancel()` |
|---|---|---|---|---|---|---|---|---|---|
| `TUNING` | `STRUM` (meter reset) | `TUNER_OPEN` | ignored | ignored | ignored | ignored | ignored | ignored | `CANCELLED` |
| `TUNER_OPEN` | ignored | ignored | `TUNING` | ignored | ignored | ignored | ignored | ignored | `CANCELLED` |
| `STRUM` | ignored | ignored | ignored | `READY` | `LEVEL_BAD` | `STRUM` | `READY` | `STRUM` (meter reset) | `CANCELLED` |
| `LEVEL_BAD` | ignored | ignored | ignored | ignored | ignored | ignored | `READY` | `STRUM` (meter reset) | `CANCELLED` |
| `READY` | ignored | ignored | ignored | ignored | ignored | ignored | ignored | ignored | ignored |
| `CANCELLED` | ignored | ignored | ignored | ignored | ignored | ignored | ignored | ignored | ignored |

The window, not the machine, handles an unavailable tuner: it asks the tuner opener whether the tuner exists **before** calling `answerTuned(false)`; when it does not exist it shows `practice.prerun.tuner.unavailable` and calls nothing, so the check stays in `TUNING`.

*Ignored* means: no exception, no change of state, nothing started or undone. `READY` and `CANCELLED` are final; a `cancel()` after `READY` never undoes a start. `READY` is therefore reachable only after `answerTuned(true)`: a "continue anyway" before the tuning was answered is ignored (FR-005, FR-008). A strum heard while the learner is still reading the tuning question is ignored, and entering `STRUM` asks the meter for a fresh start, so the strum that counts is the one after the answer.

## Invariants, each with a test

1. **Every start is checked.** `startSession()` never reaches `startArcade`, `startFollow` or the wait-mode session without a `PreRunCheck` that ended in `READY`, for the three modes and from the Start button, the quick start and the transport-bar Arcade button (`StartFlowTest` for the rule, `PracticeWindowStartPathSourceTest` for the shape of `PracticeWindow`, the native test T025 for the real window).
2. **Nothing is remembered.** A new `PreRunCheck` starts at `TUNING`; no field outlives it; the same two starts in a row ask twice (SC-002, FR-001, FR-005). Tests that can fail: `PreRunCheckTest` (T011, T018), `StartFlowTest` (T022).
3. **Not in tune means no start.** `answerTuned(false)` gives `TUNER_OPEN` and never `READY`; `tunerClosed()` gives `TUNING` again, never `READY` by itself (FR-006, FR-007, SC-004). Tests that can fail: `PreRunCheckTest` (T018), `PreRunWindowTest` (T021).
4. **Bad level blocks.** `CLIPPING`, `TOO_LOUD` and `TOO_WEAK` give `LEVEL_BAD`; only `continueAnyway()` (this object only) or a later `GOOD` gives `READY`; silence (`NO_STRUM`) is never `GOOD` (FR-002, FR-003, FR-008, Edge Cases). Tests that can fail: `PreRunCheckTest` (T011), `PreRunWindowTest` (T015).
5. **The meter.** On the owner's takes through the real analyser at gain 0.99, at least 19 of 21 are `CLIPPING`; at gain 0.40 none; on the 180 GuitarSet microphone recordings none (SC-001); a signal with no sample at the clip level is never `CLIPPING` (FR-004). Tests that can fail: `StrumClipMeterTest` (T006), `StrumClipDatasetTest` (T010), the whole path T017.
6. **Recognition untouched.** The same recorded run gives identical judgements with and without the hop listener and the start window (SC-005, parity test as in spec 009). Tests that can fail: `PreRunParityTest` (T026).
7. **Regression.** Every recognition figure of `benchmark/before-after.txt` is equal or better after the change (SC-007, FR-012). Tests that can fail: the before and after file (T030), `SyntheticChordGuardTest` (T027), `OwnerChordsBenchmarkTest` baseline (T029).
8. **Texts.** Every `practice.prerun.*` key exists, not empty, in English, French and Spanish with the same placeholders (FR-010). Tests that can fail: `FollowStringsTest` (T013, T020).
9. **Time.** From the attack of the strum to `READY` at most 1 second (the 300 ms window and the change of window); the learner's own time to answer and to strum is not counted; no timer of the state machine adds to it (FR-009, SC-003, measured by the owner at gate G). Tests that can fail: `PreRunCheckTest` timing on a fake clock (T024), the owner at gate G (T034).
10. **Verdict order.** `CLIPPING` before `TOO_LOUD` before `TOO_WEAK` when several apply (FR-003, `StrumClipMeterTest`).
11. **Restarts.** A restart, a replay from the result window (from the start or from a measure) and the automatic retry after a failed start never open the start window (FR-013, `PracticeWindowStartPathSourceTest`, native test T025).
12. **One start window.** `begin` while a check is pending (any state, `TUNER_OPEN` included) returns false, brings the open window to the front, opens nothing and runs nothing; after `READY`, `CANCELLED` or `cancelPending()` a new `begin` is accepted (`StartFlowTest`, native test T025 with a double press).
13. **The run is the one asked for.** `startSessionNow` receives the mode and the candidate captured at the request, and starts only if the preparation window is still open and no run or session exists; closing the preparation window, a change of its mode (`modeChanged()`) and a change of the selected track (the tracks drop-down listener) call `cancelPending()` (`StartFlowTest`, `PracticeWindowStartPathSourceTest`).
14. **Never stuck.** `pending()` is cleared on `READY` **before** `startNow` runs (so also when `startNow` throws or does nothing because the window is no longer open), on `CANCELLED` (the Cancel button, the close button, the Escape key), on `cancelPending()` and when `gate.request` throws; a tuner that cannot be opened leaves the check at `TUNING` with a message and `pending()` stays true only while a start window or the tuner is really open (`StartFlowTest`, `PreRunCheckTest`, native test T025).
15. **Arming.** The meter arms only on a hop whose RMS is at least `INPUT_LEVEL_MIN` (-40 dBFS) and at least 12 dB above the quietest of the 60 hops before it: with fewer than 60 hops before a hop the reference is the quietest of the hops that exist, and the first hop never arms (110 of the 180 GuitarSet files arm with fewer than 60 hops before); a click or noise at -50 dBFS never arms it (`StrumClipMeterTest`); on the owner's takes this gives 19 of 21 at gain 0.99 and 0 of 21 at gain 0.40 (`StrumClipDatasetTest`).
16. **Sample rate.** The 300 ms window and the 0.5 % share are counted in samples for the rate given by `configure`; the same synthetic signal gives the same verdict at 44.1 kHz and at 48 kHz; a `configure` during a check resets it, and a listener added while the engine runs gets its `configure` before its first hop (`StrumClipMeterTest`, `HopListenerTest`).
17. **Total state machine.** Every pair of state and event of the table above behaves as the table says; every *ignored* pair leaves the state and the outputs unchanged and throws nothing; `READY` is never reached without `answerTuned(true)`; `cancel()` after `READY` does not undo it (`PreRunCheckTest`, exhaustively over the 6 states and 9 events).
18. **Threads.** The meter is written by the analysis thread and read by the window on the UI thread: `verdict()`, `clippedShare()` and `rmsDbfs()` give a consistent snapshot of the last finished window (volatile or atomic), and `reset()` called from the UI thread only **sets a flag** that the analysis thread honours before its next hop, like `configure`; the window reads the meter on the 33 ms timer of `LevelPanel` and calls `PreRunCheck.verdict` on the UI thread (`StrumClipMeterTest` with a feeding thread and a reading and resetting thread, `HopListenerTest`).
