# Data model: Pre-run check (spec 010)

Nothing is stored; every object lives for one start.

| Entity | Fields | Rules |
|---|---|---|
| `StrumVerdict` | enum `NO_STRUM`, `CLIPPING`, `TOO_LOUD`, `TOO_WEAK`, `GOOD` | Decided by `StrumClipMeter` once 300 ms after an attack were counted; `NO_STRUM` until then (FR-004, spec Edge Cases: never a level from silence). |
| `StrumClipMeter.Parameters` | `windowNanos` 300 ms, `clipShare` 0.005, `clipSample` = `LEVEL_CLIP_SAMPLE` (0.99), `weakDbfs` = `INPUT_LEVEL_MIN` (-40), `loudDbfs` = `INPUT_LEVEL_MAX` (-6), `attackRiseDb` 12 | Provisional constants of the meter, **not** learner settings; the three levels come from the existing recognition settings. |
| `StrumClipMeter` | counters of the current window: samples, clipped samples, sum of squares, peak; armed flag; last verdict | Pure; fed by hop; `reset()` for a new check; no allocation per hop; the verdict of a window never changes after it is given. |
| `PreRunCheck.State` | enum `TUNING`, `TUNER_OPEN`, `STRUM`, `LEVEL_BAD`, `READY`, `CANCELLED` | Transitions in `contracts/pre-run-start.md`; keeps nothing between two starts (a new object per start). |
| `PreRunCheck` | state, last `StrumVerdict`, `continueAnyway` (this start only), the mode and track it was opened for | `READY` only when tuning was answered *yes* and the verdict is `GOOD`, or `continueAnyway` was chosen with a bad verdict; never when the answer was *no*. |
| `PreRunWindow` | the widgets: question with two buttons, live bar, sentence, *Check again*, *Continue anyway*, *Cancel* | A shell over `PreRunCheck`; holds the engine capture while open. |
