# Contract: the flames in the Arcade picture (spec 009)

Packages `arcade.view` (pure), `arcade.ui` (clock and level), `session` (setting). Brought to the code by the tasks; this file fixes the surface and the invariants, each with a test.

## Surface

```java
enum FlameSetting { OFF, DISCREET, FULL }                              // session.FollowSettings: get/setFlames, default DISCREET
final class FlameState { FlameState(FlameSetting setting, double level, long flameClockNanos, boolean motion); }   // arcade.view, immutable
final class ReducedMotion { static boolean requested(CommandRunner runner); }   // arcade.ui, once when a run starts; false on any failure (R10)
// ArcadeScene: Scene build(long nowNanos, Viewport v, ArcadeRun.View run, int speedPercent, QualityTier tier, FlameState flames);
//   the existing build(...) without a FlameState is the same as build(..., null): no flame
final class FlameLevel { void update(double shareOfTheMaximum, int judgedEvents, long wallNanos);     // arcade.ui, UI thread only
    double level(); void hold(long wallNanos); void resume(long wallNanos); }
// ArcadeFrameSource: owns a FlameLevel, builds the FlameState of each picture, holds the level when paused
// SceneItem.Role: FLAME_TONGUE, FLAME_EMBER (both isDecoration)
```

## Invariants, each with a test

1. **Off is nothing.** `FlameSetting.OFF` or a null state: the list of items equals the list of the scene built without the feature, item for item (SC-005, `FlameSceneTest`, the existing reference scenes unchanged).
2. **Behind everything.** In every item list the flame items come before every other item; their roles are decoration, so the fallback renderer and the tier `PERFORMANCE` draw none (FR-005, FR-007).
3. **Tiers and settings.** `FULL`: 28 tongues of 3 layers and the embers; `BALANCED`: 14 tongues of 2 layers; `PERFORMANCE`: none; `DISCREET`: half the tongues, 60 % of the height and of the opacity of `FULL` (R5).
4. **Height by level.** For a level 0, 0.25, 0.5, 0.75, 1 the maximum height of the tongues is ordered and proportional to the level; at level 0 only embers are drawn.
5. **Reading guard.** With the flames at full level, on the seven window shapes of SC-002, no flame item above 12 % of opacity meets the box of a text widened by 4 pixels, and every text (string names, fret numbers, verdict badge, HUD, chord names, bend and technique labels) keeps a contrast ratio of at least 4.5 to 1 against what is painted behind it, in reference scenes that hold a badge, a bend, a technique and a chord name; in the middle of the width the flames are at most 22 % of the corners and at the corners at most 55 % of the picture height; the translucent neck is the one explicit exception (SC-002, FR-001, FR-005, `FlameContrastTest`).
6. **Deterministic.** The same inputs give the same items (no random source); a different flame clock gives a different sway only; with `motion` false the sway is the same whatever the clock and only the heights follow the level (FR-013, `FlameSceneTest`).
7. **The level.** The share is the real ratio score / maximum; fewer than 4 events of the run judged: target 0 (also for a whole run of fewer than 4); mapping `clamp((share - 0.5) / 0.5, 0, 1)`; from 0 to 1 in 0.5 s and from 1 to 0 in 3 s, each within 20 %; the step between two pictures at 60 a second is at most 4 % of the range when rising and 1 % when falling, and never larger than the rate times the time elapsed; held in a pause and during the count-in of a resume, the flame clock going on; a new object starts at 0 for each run (SC-003, `FlameLevelTest`, `ArcadeFrameSourceTest`).
8. **No effect on the judging.** The same recorded run with `OFF` and with `FULL` gives identical judgements, score, streak and result; `arcade/core` has no reference to the flame classes (SC-004, `FlameParityTest`, also on the stress harness).
9. **The setting.** Three states in the *Mode* tab, texts in English, French and Spanish, read when a run starts (`ArcadeSession` gets it; a change during a run applies to the next run), back to `DISCREET` when the application is opened again; no monochrome variant: the flames keep their colours (FR-008, FR-009, FR-011).
10. **Reduced motion.** `ReducedMotion.requested` gives true only when the system answers that motion is to be reduced (macOS first; false on any failure or platform not read); then `FlameState.motion` is false (FR-013, `ReducedMotionTest` with a fake command runner).
11. **Cost.** On the ten-minute stress run the flames at `FULL` lose no more than 1 % more frames than without them; at the lowest tier none are drawn (SC-006).
