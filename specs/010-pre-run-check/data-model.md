# Data model: Pre-run check (spec 010)

Nothing is stored; every object lives for one start.

| Entity | Fields | Rules |
|---|---|---|
| `StrumVerdict` | enum `NO_STRUM`, `CLIPPING`, `TOO_LOUD`, `TOO_WEAK`, `GOOD` | Precedence when several apply: `CLIPPING`, then `TOO_LOUD`, then `TOO_WEAK`. Decided by `StrumClipMeter` once 300 ms after an attack were counted; `NO_STRUM` until then (FR-004, spec Edge Cases: never a level from silence). |
| `StrumClipMeter.Parameters` | `windowNanos` 300 ms (counted in samples: `round(0.3 x fs)` after `configure(fs)`), `armFloorDbfs` = `INPUT_LEVEL_MIN` (-40; the hop RMS must reach it to arm), `armQuietHops` 60, `clipShare` 0.005, `clipSample` = `LEVEL_CLIP_SAMPLE` (0.99), `weakDbfs` = `INPUT_LEVEL_MIN` (-40), `loudDbfs` = `INPUT_LEVEL_MAX` (-6), `attackRiseDb` 12 (above the quietest of the `armQuietHops` before) | Provisional constants of the meter, **not** learner settings; the three levels come from the existing recognition settings. |
| `StrumClipMeter` | counters of the current window: samples, clipped samples, sum of squares, peak; armed flag; last verdict | Pure; fed by hop; `reset()` for a new check; no allocation per hop; the verdict of a window never changes after it is given. |
| `PreRunCheck.State` | enum `TUNING`, `TUNER_OPEN`, `STRUM`, `LEVEL_BAD`, `READY`, `CANCELLED` | The transitions are the total table of `contracts/pre-run-start.md` (every state and event defined, the others ignored); keeps nothing between two starts (a new object per start). |
| `PreRunCheck` | state, last `StrumVerdict`, `continueAnyway` (this start only), the mode and track it was opened for | `READY` only when tuning was answered *yes* and the verdict is `GOOD`, or `continueAnyway` was chosen with a bad verdict; never when the answer was *no*. |
| `PreRunWindow` | the widgets: question with two buttons, live bar, sentence, *Check again*, *Continue anyway*, *Cancel* | A shell over `PreRunCheck`; holds the engine capture while open. |
| `StartFlow` | the gate, the `PreRunCheck` it holds (no stored flag: `pending()` is derived from the held check's state) | One per `PracticeWindow`; `begin` is refused while a check is pending (the tuner wait included) and brings the pending window (the tuner while it waits) to the front with `toFront()`; no longer pending when the held check is in `READY` or `CANCELLED` (the reference is dropped before the start code runs, whatever it does), `CANCELLED` (Cancel, close button, Escape), `cancelPending()` or a failure of the gate; the flag never outlives an open start window or tuner. |
