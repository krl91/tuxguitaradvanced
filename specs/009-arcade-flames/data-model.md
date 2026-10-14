# Data model: Arcade stage flames (spec 009)

All objects are in memory for one run or one session; nothing is stored.

| Entity | Fields | Source / rule |
|---|---|---|
| `FlameSetting` | enum `OFF`, `DISCREET`, `FULL`; default `DISCREET` | Field of `FollowSettings` with the other picture settings; read when a run starts (a change during a run applies to the next run); kept until the application is closed (FR-008). |
| `ReducedMotion` | `requested` (boolean, read once when a run starts through the injected command runner of R10; false on any failure or on a platform that is not read) | Gives `FlameState.motion`; never stored. |
| `FlameLevel` (picture-only) | `level` in 0..1 (0 = embers, 1 = full), `lastWallNanos`, `held` | Updated by the frame source for each picture from the real share of the maximum (R4): target 0 until 4 events of the run are judged, then `clamp((share - 0.5) / 0.5, 0, 1)`; rises at 2 per second and falls at 1/3 per second (each within 20 %), the step of a picture being `rate x min(elapsed, 50 ms)`; held in a pause and during the count-in of a resume (R11); 0 at the start of a run; one object per run (R12). Never read by the judging. |
| `FlameState` (input of the scene) | `setting`, `level`, `flameClockNanos`, `motion` (boolean) | The values the scene needs to draw the flames; `OFF` or no state draws nothing; `motion` false (reduced motion, FR-013) freezes the sway at a fixed phase so only the height follows the play; the clock is the monotonic flame clock of R11, never the picture instant. Immutable. |
| `Tongue` (derived, not stored) | index, centre x, base y, height, width, phase, layer, colour, opacity | Computed by the scene for each picture from the `FlameState` and the viewport: the number of tongues by tier and setting (28 / 14 / 0), the height from the level and the weight across the width, the sway from the instant and the phase, the opacity from the guard (R8). |
| `Ember` (derived) | x, y, radius, opacity | A few discs at the base, brighter when the level is high; present at every level above off in the `FULL` tier. |
| Roles | `FLAME_TONGUE`, `FLAME_EMBER` | New `SceneItem.Role` values, both decoration (`isDecoration() == true`). |

**Validation rules.** A level outside 0..1 is clamped; a viewport of zero size draws nothing; the setting `OFF` yields exactly the item list of a scene without the feature (SC-005); no flame item is ever after a non-flame item in the list (they are drawn first, behind everything).

**State transitions of the level.** `embers` (start, fewer than 4 events judged; also for the whole run of a passage with fewer than 4 judged events) -> `following` (4 events judged: the level moves toward its target at the rise and fall rates) -> `held` (pause, then the count-in of a resume, R11) -> `following` (the picture goes back to the clock of the player) ; a restart or a replay creates a new `FlameLevel` at 0; the flame clock never goes back.
