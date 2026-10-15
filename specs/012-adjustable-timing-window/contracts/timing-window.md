# Contract: the timing window (spec 012)

## 1. The formula (`TimingTolerance.ofMillis(W)`)
For W in 40..200 and a local beat B (ns), `windowFor(target, B, gapBefore, gapAfter)` returns:

| | value |
|---|---|
| tight half-window `T` | `clamp(round(W/500 x B), 0.8 x W ms, 2.0 x W ms)` |
| early and late half-window `O` | `max(T, clamp(round(W/500 x 2.6667 x B), 2.0 x W ms, 6.0 x W ms))` |
| cut | `O` is cut at the middle of the gap to the neighbouring event, as `TimingTolerance` does today |

**Invariants.** (1) `ofMillis(75)` equals `new TimingTolerance(Preset.NORMAL)` for every beat (0 and 30 to 2000 ms) and gap, to the nanosecond. (2) `T` is non-decreasing in W for a given beat. (3) At B = 500 ms: W = 40 gives T = 40; W = 75 gives 75; W = 100 gives 100; W = 200 gives 200 (inside its floor and ceiling). (4) A note 90 ms late at B = 500 ms is on time for W >= 90 and late for W = 75. (5) Two events closer than `O` + `O` leave their windows disjoint. (6) W outside 40..200 never reaches the formula (it is brought to the bound by the setting).

## 2. The setting
`RecognitionParameters.TEMPO_WINDOW`: `Number`, key `...tempo.window`, section NOTES, unit "ms", default 100, minimum 40, maximum 200, integer. `RecognitionParameters.TEMPO_TOLERANCE` (the old Choice) stays, legacy, with no control.

**Migration (in `RecognitionSettings.fromStrings`).** If the stored map has no `tempo.window` and has `tempo.tolerance` equal to `strict` or `lenient`, the window is 50 or 125; `normal` (the old default, which the store writes whether or not it was chosen), an absent or an unreadable value gives the default 100; a stored `tempo.window` is repaired into 40..200.

## 3. The one setting
`FollowSettings.getWindowMillis()` returns the store's current W; `setWindowMillis(ms)` stores `clamp(ms, 40, 200)` through `RecognitionSettingsStore.update`, which persists it and notifies the listeners (the Configuration window). A change applies to the **next run** (the run reads W once, in `FollowStarter.prepare`).

## 4. The control
`FollowPanel`: a slider from 40 to 200, step 5, with the text "±W ms at 120 bpm" (three languages), shown for Follow and Arcade only, enabled only when idle; the drop-down of presets is gone.
