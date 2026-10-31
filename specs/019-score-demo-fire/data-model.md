# Data model: spec 019

## ScoreFireSetting

- **Values**: `OFF` (the default), `DISCREET` or `FULL`. It reuses `session.FlameSetting`, the enum of spec 009.
- **Stored**: key `score.setting` of module `tuxguitar-practice-fire`. A missing or unknown value gives `OFF`.
- **Scope**: Wait and Follow only. Arcade keeps `FollowSettings.getFlames()`.

## FireParameter (catalogue)

**What each parameter carries**:
- `key`: a stable string, used to store the value and to name its texts;
- `kind`: NUMBER, INTEGER or COLOUR;
- `unit` and `section`;
- `min` and `max`;
- `arcadeDefault` and `scoreDefault`;
- `labelKey` (`practice.fire.<key>`) and `tooltipKey` (`practice.fire.<key>.tip`).

**Rules**:
- Every Arcade default is the value used today. Where a named constant exists, the catalogue references that constant (test: `FireParameterDefaultsTest`).
- `min ≤ default ≤ max` for both defaults.
- For colours, the range is any RGB value.

| Key | Section | Kind / unit | Range | Arcade default (source) | Score default |
|---|---|---|---|---|---|
| `opacity.most` | Look | number, share | 0 – 1 | 0.85 (`FireColours.MOST`) | 0.85 |
| `cell.columns` | Look | integer, columns across the width | 16 – 384 | 192 (`FireField.COLUMNS`) | 192 |
| `cell.smallest` | Look | number, px | 3 – 32 | 4 (`FireField.SMALLEST`) | 4 |
| `colour.steps` | Colours | integer | 4 – 64 | 16 (`FireColours.STEPS`) | 16 |
| `colour.1` … `colour.5` | Colours | colour | RGB | #5A0A04, #B7210F, #F26A12, #FFD23C, #FFF6D8 (`FireColours.SCALE`) | same |
| `height.corners` | Flames | number, share of the picture height | 0.02 – 1 | 0.55 (`FlameDrawing.CORNER_HEIGHT`) | 0.3333 (FR-004) |
| `height.middle` | Flames | number, share of the corners | 0 – 1 | 0.20 (`FlameDrawing.MIDDLE`) | 0.20 |
| `height.curve` | Flames | number, exponent of the corner-to-middle profile | 0.25 – 8 | 2 (`FlameDrawing.weight`: `d * d`) | 2 |
| `body.heat` | Flames (density) | number | 0.1 – 3 | 1.15 (`FireField.heat`) | 1.15 |
| `body.falloff` | Flames (density) | number, exponent | 0.2 – 6 | 1.5 (`FireField.heat`) | 1.5 |
| `body.ramp` | Flames (density) | number, × level | 0.5 – 20 | 3.0 (`FireField.heat`: `min(1, 3·level)`) | 3.0 |
| `flicker.strength` | Motion | number | 0 – 4 | 1.3 (`FireField.heat`) | 1.3 |
| `flicker.base` | Motion | number | 0 – 2 | 0.2 (`FireField.heat`: `0.2 + u`) | 0.2 |
| `motion.speed` | Motion | number, × time | 0 – 5 | 1.0 (the clock as it is) | 1.0 |
| `motion.rise` | Motion | number, rows per second | 0 – 60 | 9 (`FireField.RISE`) | 9 |
| `sway.width` | Motion | number, columns | 0 – 6 | 0.6 (`FireField.noise`) | 0.6 |
| `sway.speed` | Motion | number, rad/s | 0 – 12 | 1.7 (`FireField.noise`) | 1.7 |
| `sway.rows` | Motion | number, rad per row | 0 – 3 | 0.35 (`FireField.noise`) | 0.35 |
| `noise.large` | Motion (grain) | number | 0 – 1 | 0.5 (`FireField.noise`) | 0.5 |
| `noise.medium` | Motion (grain) | number | 0 – 1 | 0.3 | 0.3 |
| `noise.fine` | Motion (grain) | number | 0 – 1 | 0.2 | 0.2 |
| `noise.scale` | Motion (grain) | number, columns of the largest grain (the next ones are half and a quarter) | 0.25 – 16 | 2 (`FireField.noise`: `x / 2.0`, `x / 1.0`, `x / 0.5`) | 2 |
| `noise.aspect` | Motion (grain) | number, height of a tongue over its width | 0.25 – 8 | 2 (`FireField.noise`: `y / 4.0`, `y / 2.0`, `y / 1.0`) | 2 |
| `ember.rows` | Embers | integer, rows | 0 – 8 | 2 (`FireField.EMBER_ROWS`) | 2 |
| `ember.glow` | Embers | number, heat | 0 – 1 | 0.12 (`FireField.heat`) | 0.12 |
| `ember.flicker` | Embers | number, heat | 0 – 1 | 0.08 (`FireField.heat`) | 0.08 |
| `discreet.share` | Look | number, share of Full | 0.05 – 1 | 0.6 (`FlameDrawing.DISCREET`) | 0.6 |
| `growth.from` | Growth | number, share at which flames start | 0 – 0.95 | 0.5 (`FlameLevel.update`: `(share − 0.5) / 0.5`) | 0 |
| `growth.minJudged` | Growth | integer, notes | 0 – 64 | 4 (`FlameLevel.MIN_JUDGED`) | 0 |
| `growth.full` | Growth | integer, right notes in a row | 1 – 128 | 16 (not used by Arcade, not shown in its group; set to the score's value so that every default lies within its range) | 16 |
| `growth.rise` | Growth | number, heights per second | 0.05 – 20 | 2.0 (`FlameLevel.RISE_PER_SECOND`) | 2.0 |
| `growth.fall` | Growth | number, heights per second | 0.02 – 20 | 0.3333 (`FlameLevel.FALL_PER_SECOND`, the exact constant) | 1.0 (FR-003) |
| `growth.maxStep` | Growth | number, ms | 5 – 500 | 50 (`FlameLevel.MAX_STEP_NANOS`) | 50 |
| `sparks.base` | Sparks | integer, per right note | 0 – 200 | 20 (`FlameDrawing.sparks`) | 20 |
| `sparks.perStreak` | Sparks | number, more per note of streak | 0 – 50 | 2.5 (`5 · … / 2`, integer division kept) | 2.5 |
| `sparks.streakCap` | Sparks | integer, notes | 0 – 128 | 12 (`FlameDrawing.sparks`) | 12 |
| `sparks.most` | Sparks | integer, in the air | 0 – 1000 | 300 (`FlameDrawing.MOST_SPARKS`) | 300 |
| `sparks.life` | Sparks | number, s | 0.1 – 5 | 1.0 (`FlameDrawing.SPARK_LIFE`, `ArcadeFrameSource.SPARK_NANOS`) | 1.0 |
| `sparks.rise` | Sparks | number, rows | 0 – 200 | 10 (`FlameDrawing.sparks`) | 10 |
| `sparks.riseSpread` | Sparks | number, rows | 0 – 200 | 16 | 16 |
| `sparks.drift` | Sparks | number, columns | 0 – 100 | 10 (±5) | 10 |
| `sparks.zone` | Sparks | number, share of the columns from each corner | 0 – 0.5 | 0.25 | 0.25 |
| `sparks.start` | Sparks | number, 0 bottom of the fire … 1 top | 0 – 1 | 1 (from the top of the flames, today) | 0 (from the bottom, owner's answer) |
| `sparks.fade` | Sparks | number, colour steps over the life | 0 – 64 | 8 (`FlameDrawing.sparks`) | 8 |

Notes:
- `sparks.perStreak` reproduces the integer arithmetic of today: `5 * min(streak, 12) / 2` is computed as `floor(2.5 * min(streak, cap) + 1e-9)`. The test pins equality for every streak from 0 to 128.
- `motion.speed` multiplies the fire clock. At 1.0 the clock is unchanged, bit for bit.
- The grain: octave k (0, 1, 2) reads the noise at `x / (noise.scale / 2^k)` and `y / (noise.aspect × noise.scale / 2^k)`; with 2 and 2 these are today's divisors, exactly (powers of two).
- The profile: `weight = middle + (1 − middle) × d^curve`, computed as `d * d` when the curve is 2, so that the defaults stay bit-identical (`Math.pow` is not promised to equal `d * d`).
- The score fire has no Arcade-only parameter. `growth.full` is shown only in the score group (`inArcade()` false); its Arcade default 16 is never read.

**Fixed, not in the catalogue** (research R4):
- `WIDEN`, `NEAR_TEXT` and `RAMP` (the reading guard);
- `SEED` and the hash constants (repeatability);
- `TONGUES` (the item code, no pixel);
- the BALANCED ×2 cell and the `QualityController` rules (automatic tiers).

## FireTuning

- An immutable `EnumMap<FireParameter, Double>`; colours are held as RGB integers in the double.
- It is built with `FireTuning.of(group, Map<FireParameter, Double>)`. Every value is clamped to its range; a missing value gives the group's default; NaN gives the default.
- Two constants: `ARCADE_DEFAULTS` and `SCORE_DEFAULTS`.
- Accessors: `value(p)`, `integer(p)`, `colour(p)`. It provides `equals` and `hashCode`.
- It is carried by `FlameState`: a new constructor adds a tuning, and the old constructors use `ARCADE_DEFAULTS`.

## FireTuningStore

- **Operations**:
  - `load(group)` returns a FireTuning;
  - `save(group, tuning)` writes only the values that differ from the defaults;
  - `reset(group)` removes every key of the group;
  - `scoreSetting()` and `setScoreSetting(s)` read and write the score setting.
- None of them throws: `load` falls back to the defaults, and a write that fails returns false.

## ScoreStreak

- **State**:
  - `streak` (int ≥ 0);
  - the last pass identity counted (Wait);
  - the set of Follow event indices already counted;
  - the new bursts since the last read, as a list of `(clockNanos, streak)`.
- **Transitions**:

| Input | Streak | Burst |
|---|---|---|
| Right note: Wait ACCEPTED, or Follow MATCHED | +1 | one, with the new streak |
| Mistake: Wait WRONG verdict or SKIPPED, or Follow WRONG_PITCH or MISSED | → 0 | none |
| AUTO_PASSED, or a snapshot already counted | unchanged | none |
| `reset()` (run start) | → 0 | bursts cleared |

## ScoreFire (runtime)

- **Lifecycle**: `start(setting, tuning, motion)`, then `update(streak, newBursts, paused, currentEvent, nextEvent, paletteBox)` from each render, then `stop()` (the same signature as T025).
- **States**:
  - IDLE (no listener);
  - RUNNING (listener attached, timer ticking while the fire moves);
  - HELD (paused; listener attached, no ticks);
  - IDLE again after `stop()` (listener removed, one last redraw).
- **Holds**:
  - a `FlameLevel` built from the score tuning;
  - the fire clock;
  - the recent bursts (dropped after `sparks.life`);
  - a `QualityController(8 ms)`;
  - a `UiPainterRenderer`;
  - the canvas it is attached to.
- **Each paint**:
  - the viewport is the canvas size;
  - the guard is the boxes of the current event, the next event and the palette (canvas-local);
  - the scene is `ScoreFireScene.scene(viewport, tier, flameState, guard)`, which the renderer paints;
  - the cost is fed to the controller.

## FirePreview

- **State**: group, tuning, its own `FlameLevel`, clock, bursts and `QualityController`, the growth slider value and visibility.
- **Rules**:
  - it animates only while visible and with motion;
  - the *Sparks* button adds one burst at the slider's streak;
  - for Arcade, the growth slider gives the share of the maximum;
  - for the score, it gives the streak, from 0 to `growth.full` × 1.5.
