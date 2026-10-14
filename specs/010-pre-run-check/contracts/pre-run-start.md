# Contract: the pre-run check (spec 010)

Packages `audio engine analysis` (the meter) and `practice ui` (the state machine and the window). Brought to the code by the tasks; this file fixes the surface and the invariants, each with a test.

## Surface

```java
enum StrumVerdict { NO_STRUM, CLIPPING, TOO_LOUD, TOO_WEAK, GOOD }
final class StrumClipMeter {                       // audio engine, pure, hop by hop
    StrumClipMeter(Parameters p);
    void hop(float[] samples, int offset, int length);   // analysis thread, no allocation
    void reset();
    StrumVerdict verdict();                         // NO_STRUM until 300 ms after an attack were counted
    double clippedShare();  double rmsDbfs();       // of the window, for the screen and the tests
}
// AnalysisService / OfflineAnalysis: addHopListener(HopListener) with void hop(float[] samples, int offset, int length)
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
// PracticeWindow.startSession(): opens PreRunWindow; the old body runs only on READY
```

## Invariants, each with a test

1. **Every start is checked.** `startSession()` never reaches `startArcade`, `startFollow` or the wait-mode session without a `PreRunCheck` that ended in `READY`, for the three modes and from the Start button, the quick start and the transport-bar Arcade button (`PreRunWindowTest`, a call-path test of `PracticeWindow`).
2. **Nothing is remembered.** A new `PreRunCheck` starts at `TUNING`; no field outlives it; the same two starts in a row ask twice (SC-002, FR-001, FR-005).
3. **Not in tune means no start.** `answerTuned(false)` gives `TUNER_OPEN` and never `READY`; `tunerClosed()` gives `TUNING` again, never `READY` by itself (FR-006, FR-007, SC-004).
4. **Bad level blocks.** `CLIPPING`, `TOO_LOUD` and `TOO_WEAK` give `LEVEL_BAD`; only `continueAnyway()` (this object only) or a later `GOOD` gives `READY`; silence (`NO_STRUM`) is never `GOOD` (FR-002, FR-003, FR-008, Edge Cases).
5. **The meter.** On the owner's takes through the real analyser at gain 0.99, at least 19 of 21 are `CLIPPING`; at gain 0.40 none; on the 180 GuitarSet microphone recordings none (SC-001); a signal with no sample at the clip level is never `CLIPPING` (FR-004).
6. **Recognition untouched.** The same recorded run gives identical judgements with and without the hop listener and the start window (SC-005, parity test as in spec 009).
7. **Regression.** Every recognition figure of `benchmark/before-after.txt` is equal or better after the change (SC-007, FR-012).
8. **Texts.** Every `practice.prerun.*` key exists, not empty, in English, French and Spanish with the same placeholders (FR-010).
9. **Time.** With a good strum the state machine reaches `READY` after one answer and the 300 ms window; no timer of its own adds more than the strum (SC-003, measured by the owner at gate G).
