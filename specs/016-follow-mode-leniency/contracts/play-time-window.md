# Contract: the time to play a note in Follow (FR-011)

`PlayTimeWindow implements WindowPolicy`; `TimingTolerance implements WindowPolicy` unchanged otherwise. Times are compensated attack times (the attack, less the detector latency, less the route offset), as today.

For event *i* with target (red) time `T`, gap to the previous event `gb`, to the next `ga` (`Long.MAX_VALUE` when none), time to play `P` (100–600 ms):

- `late = max(0, min(P, ga − 20 ms))`
- `previousLate = max(0, min(P, gb − 20 ms))` (the late limit of the previous event, same rule)
- `early = max(0, min(50 ms, gb − previousLate − 1 ns))`
- `onTimeTo = T + min(late, P / 2)`; `onTimeFrom = T`
- outer window `[T − early, T + late]`; EARLY in `[T − early, T)`, ON_TIME in `[T, onTimeTo]`, LATE in `(onTimeTo, T + late]`, nothing outside.

Invariants (tested): two consecutive windows never overlap; at least 20 ms lie between the end of a window and the next red; with no neighbours the window is `[T − 50, T + P]`; the beat does not change it; `P` outside 100–600 is refused by the constructor and brought into range by the settings.

Worked examples (P = 300): notes 1 s apart → `[T − 50, T + 300]`; notes 200 ms apart → late 180 ms, and the next note's advance is 20 ms instead of 50 (the previous window ends 20 ms before its red, the next one begins just after) — **when notes are closer than P + 70 ms, the advance shrinks to what the 20 ms leave**; this is written in the guides.

Arcade: unchanged, keeps `TimingTolerance.ofMillis(TEMPO_WINDOW)` and its leniency factor.
