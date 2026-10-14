# Contract: the pre-run check (spec 010)

Packages `audio engine analysis` (the meter) and `practice ui` (the state machine and the window). Brought to the code by the tasks; this file fixes the surface and the invariants, each with a test.

## Surface

```java
enum StrumVerdict { NO_STRUM, CLIPPING, TOO_LOUD, TOO_WEAK, GOOD }
final class StrumClipMeter {                       // audio engine, pure, hop by hop
    StrumClipMeter(Parameters p);
    void configure(int sampleRate);                      // at the start and at every reconfiguration of the engine: resets to NO_STRUM; the window is round(0.3 x sampleRate) samples
    void hop(float[] samples, int offset, int length);   // analysis thread, no allocation
    void reset();
    StrumVerdict verdict();                         // NO_STRUM until 300 ms after an attack were counted
    double clippedShare();  double rmsDbfs();       // of the window, for the screen and the tests
}
// AnalysisService (in run(), after the front end) / OfflineAnalysis / AudioEngine facade: addHopListener(HopListener) with void configure(int sampleRate) and void hop(float[] samples, int offset, int length)
final class PreRunCheck {                           // practice ui, no widgets
    PreRunCheck(Mode mode);                         // a new object for each start
    State state();
    void answerTuned(boolean tuned);                // yes: STRUM; no: TUNER_OPEN
    void tunerClosed();                             // back to TUNING
    void verdict(StrumVerdict v);                   // from the meter: GOOD -> READY, else LEVEL_BAD (NO_STRUM keeps STRUM)
    void continueAnyway();                          // LEVEL_BAD or STRUM -> READY, for this start only
    void checkAgain();                              // LEVEL_BAD -> STRUM, meter reset
    void cancel();                                  // CANCELLED, the mode does not begin
}
interface StartGate { void request(PreRunCheck check, Runnable onReady); void toFront(); }     // practice ui; the real one opens PreRunWindow
final class StartFlow {                                                  // practice ui, one instance per PracticeWindow, pure
    StartFlow(StartGate gate);
    boolean begin(PreRunCheck check, Runnable startNow);   // false and nothing done when a check is already pending (TUNER_OPEN counts); startNow runs only on READY, once
    void toFront();                                         // brings the pending start window to the front (the tuner, while TUNER_OPEN)
    void cancelPending();                                   // the window closes, its mode changes or the learner picks another track: the pending check is cancelled
    boolean pending();
}
// PracticeWindow.startSession(): flow.begin(new PreRunCheck(mode), () -> startSessionNow(mode, candidate)) with the mode and the candidate captured at the request; restarts call startArcade/startFollow directly
```

## Invariants, each with a test

1. **Every start is checked.** `startSession()` never reaches `startArcade`, `startFollow` or the wait-mode session without a `PreRunCheck` that ended in `READY`, for the three modes and from the Start button, the quick start and the transport-bar Arcade button (`StartFlowTest` for the rule, `PracticeWindowStartPathSourceTest` for the shape of `PracticeWindow`, the native test T025 for the real window).
2. **Nothing is remembered.** A new `PreRunCheck` starts at `TUNING`; no field outlives it; the same two starts in a row ask twice (SC-002, FR-001, FR-005).
3. **Not in tune means no start.** `answerTuned(false)` gives `TUNER_OPEN` and never `READY`; `tunerClosed()` gives `TUNING` again, never `READY` by itself (FR-006, FR-007, SC-004).
4. **Bad level blocks.** `CLIPPING`, `TOO_LOUD` and `TOO_WEAK` give `LEVEL_BAD`; only `continueAnyway()` (this object only) or a later `GOOD` gives `READY`; silence (`NO_STRUM`) is never `GOOD` (FR-002, FR-003, FR-008, Edge Cases).
5. **The meter.** On the owner's takes through the real analyser at gain 0.99, at least 19 of 21 are `CLIPPING`; at gain 0.40 none; on the 180 GuitarSet microphone recordings none (SC-001); a signal with no sample at the clip level is never `CLIPPING` (FR-004).
6. **Recognition untouched.** The same recorded run gives identical judgements with and without the hop listener and the start window (SC-005, parity test as in spec 009).
7. **Regression.** Every recognition figure of `benchmark/before-after.txt` is equal or better after the change (SC-007, FR-012).
8. **Texts.** Every `practice.prerun.*` key exists, not empty, in English, French and Spanish with the same placeholders (FR-010).
9. **Time.** From the attack of the strum to `READY` at most 1 second (the 300 ms window and the change of window); the learner's own time to answer and to strum is not counted; no timer of the state machine adds to it (FR-009, SC-003, measured by the owner at gate G).
10. **Verdict order.** `CLIPPING` before `TOO_LOUD` before `TOO_WEAK` when several apply (FR-003, `StrumClipMeterTest`).
11. **Restarts.** A restart, a replay from the result window (from the start or from a measure) and the automatic retry after a failed start never open the start window (FR-013, `PracticeWindowStartPathSourceTest`, native test T025).
12. **One start window.** `begin` while a check is pending (any state, `TUNER_OPEN` included) returns false, brings the open window to the front, opens nothing and runs nothing; after `READY`, `CANCELLED` or `cancelPending()` a new `begin` is accepted (`StartFlowTest`, native test T025 with a double press).
13. **The run is the one asked for.** `startSessionNow` receives the mode and the candidate captured at the request, and starts only if the preparation window is still open and no run or session exists; closing the preparation window, a change of its mode (`modeChanged()`) and a change of the selected track (the tracks drop-down listener) call `cancelPending()` (`StartFlowTest`, `PracticeWindowStartPathSourceTest`).
14. **Never stuck.** `pending()` is cleared on `READY` **before** `startNow` runs (so also when `startNow` throws or does nothing because the window is no longer open), on `CANCELLED` (the Cancel button, the close button, the Escape key), on `cancelPending()` and when `gate.request` throws; a tuner that cannot be opened leaves the check at `TUNING` with a message and `pending()` stays true only while a start window or the tuner is really open (`StartFlowTest`, `PreRunCheckTest`, native test T025).
15. **Arming.** The meter arms only on a hop whose RMS is at least `INPUT_LEVEL_MIN` (-40 dBFS) and at least 12 dB above the quietest of the 60 hops before it: a click or noise at -50 dBFS never arms it (`StrumClipMeterTest`); on the owner's takes this gives 19 of 21 at gain 0.99 and 0 of 21 at gain 0.40 (`StrumClipDatasetTest`).
16. **Sample rate.** The 300 ms window and the 0.5 % share are counted in samples for the rate given by `configure`; the same synthetic signal gives the same verdict at 44.1 kHz and at 48 kHz; a `configure` during a check resets it (`StrumClipMeterTest`, `HopListenerTest`).
