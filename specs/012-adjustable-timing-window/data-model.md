# Data model: the adjustable timing window (spec 012)

## Timing window width (W)
The one value the learner sets. Integer milliseconds, the half-width of the tight (on time) window **at 120 beats a minute**. **Range 40 to 200; default 100.** Saved by the recognition store under the key `tempo.window` (the unit of the Configuration window's control is "ms"). A value outside the range is brought to the nearest bound when loaded; a missing or unreadable value gives the default.

## Window (derived, not stored)
For one event: the **tight half-window** and the **outer half-window** (early and late), in nanoseconds, computed from W, the local beat and the gaps to the neighbouring events:
* tight = clamp(round(W/500 x beat), 0.8 W, 2.0 W)
* outer = max(tight, clamp(round(W/500 x 2.6667 x beat), 2.0 W, 6.0 W)), then cut at the middle of the gap to each neighbour.
Rule: the windows of two events never overlap; the same W, beat and gaps always give the same window (deterministic).

## Legacy preset (read only)
The old saved value `strict`, `normal` or `lenient` under `tempo.tolerance`. Used only when `tempo.window` is absent: it gives W = 50, 75 or 125. Never written by this version as a choice of the learner.

## Setting in memory (FollowSettings)
No longer holds its own copy: it reads W from the store and writes it back through the store, so there is **one** value for the preparation window, the Configuration window and the saved file.
