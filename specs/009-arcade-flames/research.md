# Research: Arcade stage flames (spec 009)

Every claim cites a file that was opened on 2026-10-14.

## R1 - What the scene can draw

**Evidence.** `arcade/view/ArcadeScene.java`: the inner class `Out` offers `line`, `curve`, `quad` (four corners, filled, with an alpha), `disc` (ellipse, filled), text and rounded shapes; the approach band (`APPROACH_BAND`) is already a translucent filled quad at alpha 0.16. `arcade/ui/UiPainterRenderer.java` fills them with the toolkit painter. There is no gradient and no blur primitive.

**Decision.** Draw each flame tongue as a **filled quadrilateral that tapers to its tip**, three layers per tongue (dark red outer, orange middle, yellow core, each smaller and more opaque), plus a few **discs** for embers and a low glow at the base. No new painter primitive: the existing shapes and the alpha are enough (constitution II, X).

**Alternatives.** A new gradient or particle primitive in the toolkit (rejected: a core change with no need shown); bitmap sprites (rejected: the scene has no image item and it would not scale to every window).

## R2 - Decoration and the quality tiers

**Evidence.** `SceneItem.Role.isDecoration()` lists `GLOW`, `TRAIL`, `SHADOW`, `TARGET_HALO`, `APPROACH_BAND`; `UiPainterRenderer` (line 60) leaves decoration out in the fallback renderer; `QualityTier` has `FULL` (glow, trails, shadows), `BALANCED` (simple glow) and `PERFORMANCE` (nothing).

**Decision.** Add the roles `FLAME_TONGUE` and `FLAME_EMBER` to the decoration set: the fallback renderer then drops them with no new code. By tier: `FULL` draws the whole effect (28 tongues, 3 layers, embers and base glow), `BALANCED` draws 14 tongues in 2 layers and no glow, `PERFORMANCE` draws nothing (FR-007).

## R3 - Where the smoothed level lives

**Evidence.** `ArcadeScene.build` documents itself as a function of the clock, the run and the identities alone, with no state between frames (spec 003 FR-055); `ArcadeFrameSource` already holds picture-only state (the count-in clock, the pause) and is used on the UI thread only; a pause holds the picture (`setPaused`).

**Decision.** A small class `FlameLevel` in `arcade/ui`, owned by `ArcadeFrameSource`: it is given the share of the maximum and the wall time of each picture and returns the level (0 = embers, 1 = full). The scene receives the level and the picture instant as **inputs** (`FlameState`): the scene stays pure and deterministic, and no code of `arcade/core` can read the level, so the judging cannot depend on it (FR-006).

**Alternatives.** A state inside `ArcadeScene` (rejected: breaks its purity and the reference scenes); deriving the level from the history of the run in closed form (rejected: more code, no gain).

## R4 - The share of the maximum and the rules of the level

**Evidence.** `arcade/core/ArcadeRun.java`: `View.score()` and `View.judged()`; `ArcadeScoring.maximum(judgedEvents)` is the score of a run where every judged event is on time; `ArcadeResult.percent()` is the share rounded down, "0 when nothing was judged".

**Decision.** `share = score / scoring.maximum(judged)` as a **real number, never the rounded percent** (FR-002; `maximum` is 0 when nothing was judged, then the share is taken as 0). **Target** = 0 while fewer than 4 events are judged (owner's answer; `ArcadeRun.View.judged()` counts the events judged right, wrong or missed in **this run**, so the count starts again at every run, replay and restart by construction, and a passage with fewer than 4 judged events in all keeps embers, FR-003); else `clamp((share - 0.5) / 0.5, 0, 1)` (embers at 50 % or less, full at 100 %, linear). **Level** moves toward the target: rise at 2 per second (0 to 1 in 0.5 s), fall at 1/3 per second (1 to 0 in 3 s), each within 20 % (FR-004); at 60 pictures a second that is at most 3.3 % of the range per picture when rising and 0.6 % when falling, inside the limits of 4 % and 1 % (SC-003); the step of a picture is `rate x min(elapsed time, 50 ms)`, so a slow picture never makes a jump; it holds while the run is paused and during the count-in of a resume (R11); a new run starts at 0.

## R5 - What "discreet" is

**Decision (approved on the mockups `A2-full.png` and `A2-discreet.png`, gate G1, and written in FR-008).** *Discreet* = the same shapes at **60 % of the height and 60 % of the opacity** of *full*, **half the tongues** (14 instead of 28). *Full* is the whole effect: at the corners the tallest tongue is at most **55 % of the picture height** (FR-001). *Off* draws nothing: the item list is equal to the list without the feature (SC-005). A change of the setting in the panel applies to the next run (the setting is read when a run starts).

## R6 - Correction of the spec: the share is 0, not 100 %, at the start

**Evidence.** The "100 %" at the top right of the picture is the **tempo** (`HUD_SPEED`), not the score; `ArcadeResult.percent()` is 0 when nothing was judged.

**Consequence.** The reason given in FR-003 and in scenario 2 of story 1 ("the share is 100 % when nothing has been judged") is wrong. The rule stays useful (the owner's 4 events): the share jumps from 0 to 100 % on the first right note, which would make the flames leap; the minimum of 4 judged events avoids that. The text of the spec is to be corrected by `/speckit-clarify` (F0).

## R7 - Correction of the spec: the buttons are outside the picture

**Evidence.** `arcade/ui/ArcadeWindow.java`: the picture is a canvas, and Restart, Pause/Resume and Stop are a row of widgets **under** it; the mockups show the flames over that row only because they were drawn on a screenshot of the whole window.

**Consequence.** The flames can only be drawn **inside the picture**. FR-001 speaks of the zone "under the fret numbers, the verdict badge and the buttons": the buttons are not concerned. To be corrected by `/speckit-clarify` (F0).

## R8 - The reading guard

**Evidence.** `ArcadeScene.plan` and `hudBoxes` already compute the boxes of the texts (HUD, fret numbers, names, badges) to keep them apart; the string labels are at the left edge of the neck; the fret numbers and the verdict badge are under the neck.

**Decision.** (a) The flame weight across the width is `0.22 + 0.78 * ((|x - centre| / half width)^2)`: weak in the middle (**22 % of the corners**, FR-001), strong at the corners (as in the mockups). (b) Every tongue whose box meets the box of a text of the picture (string labels, fret numbers, verdict badge) is **faded** to at most 12 % of its opacity near that box. (c) The flames are drawn **first**, behind everything. (d) The brightest colours (the yellow core) are never used inside the central weak zone. (e) SC-002 (contrast 4.5 to 1 for the string names, fret numbers, verdict badge, HUD texts, chord names and bend and technique labels) is checked by a painted-pixel test on the recording painter at the seven window shapes of SC-002; "a flame meets a text" means a flame item above 12 % of opacity inside the box of the text widened by 4 pixels; the reference scenes of the test hold a verdict badge, a bend, a technique and a chord name; (f) the translucent body of the neck lets the flames show faintly through it (spec 003 FR-075): an explicit exception of FR-005, not a covering, because every readable item is drawn on top and keeps the contrast. The mockup `A2-full.png` shows the guard on the string labels as a hard edge: the real one is a fade.

## R9 - Determinism, motion and tests

**Decision.** The flicker is a function of the picture instant (`FlameState.instantNanos`) and the tongue index, with fixed phases: no random source, so the reference scenes are stable. A pause holds the instant (the picture holds). The **reference scenes** (`reference/*.scene.txt`) are made with the setting off, so they do not change (SC-005); one new reference scene is made with the flames at full level. Tests: `FlameSceneTest` (items, roles, tiers, setting, determinism, "off = same list"), `FlameLevelTest` (4-event rule, mapping, rise, fall, hold, restart), `FlameContrastTest` (contrast and no cover, seven window shapes), `FlameParityTest` (same run, same judgements and score, flames off and full, on the stress harness), a native paint test per tier in `ArcadeCanvasSwtTest`, and the stress run for the cost (SC-006).

## R10 - Reading the "reduce motion" preference of the system (FR-013)

**Evidence.** The toolkit of the application (`desktop/TuxGuitar-ui-toolkit`) and `desktop/TuxGuitar/src` have no reference to a reduced-motion preference; the SWT cocoa jar (`org.eclipse.swt.cocoa.macosx-4.37.jar`) holds `org.eclipse.swt.internal.cocoa.NSWorkspace` but with **no** `accessibilityDisplayShouldReduceMotion` method (checked with `javap`), and the practice module does not depend on SWT (it uses the toolkit abstraction). On this machine (macOS) `defaults read com.apple.Accessibility ReduceMotionEnabled` answers `0`, and `defaults read com.apple.universalaccess reduceMotion` fails with "does not exist" (the key is absent when it was never set).

**Decision.** A small class `ReducedMotion` in `arcade/ui` asks the system **once, when a run starts**, through a command, with a time limit of 500 ms and a fallback of "no preference" on any failure: on macOS `defaults read com.apple.Accessibility ReduceMotionEnabled` (1 means reduce); the command runner is injected so that the tests give it fake answers. **No native module and no change of the toolkit** (constitution II). **Honest limits:** the answer `1` could not be tried on this machine (only `0`): the owner turns the system setting on at gate G2 and looks; on **Windows and Linux** nothing is read in this version (no preference is assumed, the flames move) and that is **not claimed as supported** (constitution IX); the setting *off* is the remedy for anyone who needs it.

**Alternatives.** A new method in the toolkit and a native call (rejected: a core change and a native module for a decoration); reading nothing (rejected: FR-013 would be empty); the key `com.apple.universalaccess reduceMotion` (kept as a second try if the first key is absent).

## R11 - The level during a pause and the count-in of a resume (FR-010)

**Evidence.** `ArcadeFrameSource` keeps its own picture time during the count-in and goes back by one measure on a resume (spec 003 FR-079, `setPaused`); `ArcadeFrameSource.pictureNanosAt` is not monotonic across a resume.

**Decision.** The flames use their **own monotonic flame clock**, never the picture instant: the wall time accumulated while the run is not paused. (a) In a pause the level and the clock hold. (b) On a resume the **level stays held** during the whole count-in (while `countInOver` is false) and the **flame clock goes on**, so the tongues keep moving; the level is released (`resume`) when the picture goes back to the clock of the player, and then follows its target from the same value. (c) The sway never jumps back, because the flame clock does not depend on the picture instant.

## R12 - Where the count of four judged events lives

**Decision.** Nowhere: `ArcadeRun.View.judged()` is the count of the run, rebuilt from the snapshots of the session of **this** run, so it is 0 at every new run (replay from the result window, Restart, a run from the middle of the piece); `FlameLevel` only compares it with 4 and holds no counter. A new `FlameLevel` is made with each `ArcadeFrameSource`, that is, for each run.

