# Data model: Arcade stage flames (spec 009)

All objects are in memory for one run or one session; nothing is stored.

| Entity | Fields | Source / rule |
|---|---|---|
| `FlameSetting` | enum `OFF`, `DISCREET`, `FULL`; default `DISCREET` | Field of `FollowSettings` with the other picture settings; read when a run starts; kept until the application is closed (FR-008). |
| `FlameLevel` (picture-only) | `level` in 0..1 (0 = embers, 1 = full), `lastWallNanos`, `held` | Updated by the frame source for each picture from the share of the maximum: target 0 until 4 events are judged, then `clamp((share - 0.5) / 0.5, 0, 1)`; rises at 2 per second, falls at 1/3 per second; held in a pause; 0 at the start of a run (R4). Never read by the judging. |
| `FlameState` (input of the scene) | `setting`, `level`, `instantNanos` | The three values the scene needs to draw the flames; `OFF` or no state draws nothing. Immutable. |
| `Tongue` (derived, not stored) | index, centre x, base y, height, width, phase, layer, colour, opacity | Computed by the scene for each picture from the `FlameState` and the viewport: the number of tongues by tier and setting (28 / 14 / 0), the height from the level and the weight across the width, the sway from the instant and the phase, the opacity from the guard (R8). |
| `Ember` (derived) | x, y, radius, opacity | A few discs at the base, brighter when the level is high; present at every level above off in the `FULL` tier. |
| Roles | `FLAME_TONGUE`, `FLAME_EMBER` | New `SceneItem.Role` values, both decoration (`isDecoration() == true`). |

**Validation rules.** A level outside 0..1 is clamped; a viewport of zero size draws nothing; the setting `OFF` yields exactly the item list of a scene without the feature (SC-005); no flame item is ever after a non-flame item in the list (they are drawn first, behind everything).

**State transitions of the level.** `embers` (start, fewer than 4 events judged) -> `following` (4 events judged: the level moves toward its target at the rise and fall rates) -> `held` (pause) -> `following` (resume) ; a restart or a replay creates a new `FlameLevel` at 0.
