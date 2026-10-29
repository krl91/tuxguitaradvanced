# Contract: the calibration offer before a run (FR-004, SC-005)

In the start window of spec 010, after a good level (or *Continue anyway*), for **Follow and Arcade only**:

| Route state | What the window does |
|---|---|
| calibrated (`RouteCalibrations.forRoute(route).isCalibrated()`) | nothing; the run begins |
| not calibrated, route declined in this launch | nothing; the run begins |
| not calibrated, not declined | the sentence "The rhythm is not calibrated for this input and output: your notes may be judged a little early or late." with **Calibrate now** and **Go on without** |
| route unknown (null) | nothing (as today: reduced precision is shown in the run) |

- **Calibrate now** runs the existing calibration (8 clicks one second apart, about 10 s, *Cancel* stops the clicks at once) inside the start window, through the same `FollowPanel.Backend` and the same flow as the Mode tab's *Calibrate* button (`ui/FollowPanel.java` `startCalibration`, `calibrationChanged`, `finish`); a result is saved as today (per input + output + format) and the run begins; cancel or a failure (too few notes, too spread) shows the reason and the two buttons again.
- **Go on without** adds the route to the launch-scoped declined set and the run begins.
- The wait mode never shows the step; restarts and replays from the result window do not run the start window (spec 010 R4, unchanged).
- Closing the start window or Escape during the step: nothing begins (spec 010 behaviour).

Tests (first): the state machine of `PreRunCheck` with the step (pure); the declined set (pure); the source-structure tests of the start path unchanged and green; a native test of the step's buttons (`swt-gui`).
