# Contract: the score fire overlay (spec 019)

## Painting

- `ScoreFire` adds one `UIPaintListener` to the canvas of `TGTabFolder.findSelectedControl()` when a Wait or Follow run starts with the setting not OFF, and removes it on stop. It never touches the score's own listener or its buffer.
- The listener paints after the score's listener, on the same painter (toolkit order). It paints nothing when it is not attached to the selected control, when the canvas is disposed, or when the tier is PERFORMANCE.
- **With setting OFF**: no listener, no timer, no redraw. The score's pixels are identical to the pixels without the feature (SC-004).

## The scene: `ScoreFireScene.scene(viewport, tier, flames, guard)` (public, `arcade/view`)

| Input | Output |
|---|---|
| flames null, OFF, or tier PERFORMANCE | an empty scene |
| level 0 | ember rows only, along the bottom of the viewport |
| viewport fewer than 8 cells high | ember rows only, whatever the level (Edge Case "very small score area") |
| level 1 | corner height = `height.corners` × viewport height (1/3 by default); middle = `height.middle` of it |
| any cell or spark | opacity ≤ `opacity.most` (× `discreet.share` if discreet); within 4 px of a guard box ≤ 0.2 % (fixed); never above 12 % there |
| sparks | from `sparks.start` of the fire's height (0 = bottom), only at FULL and with motion; ≤ `sparks.most` in the air |
| same inputs | same items |

## The Arcade preview: `ScoreFireScene.arcadePreview(viewport, tier, flames)` (public, `arcade/view`)

It gives the flame items of an Arcade picture with no game (no lanes, no text), built by `FlameDrawing.items(viewport, tier, flames, no texts, flames.tuning())`, for the Advanced tab's Arcade preview only. With `ARCADE_DEFAULTS`, they are the same items as those of a real Arcade picture of that size, without the fading near texts.

## The ports (as built)

- `ScoreFire(factory, score, ticker, wall, cost)`: `ScoreFire.Score` gives `canvas()`, `guardBoxes(current, next, palette)` (asked at each paint; the palette's screen box is turned into canvas coordinates by the port, which knows the canvas's screen origin) and `redraw(overlayOnly)`; `ScoreFire.Ticker` is the 33 ms timer; `wall` and `cost` are clocks, so that the tests drive time and painting cost.
- `update(streak, judged, newBursts, paused, current, next, palette)`: `judged` (the notes judged so far) feeds `growth.minJudged`.
- The height: `height.corners` is the height of the whole fire, ember rows included (FR-004), so `ScoreFireScene` gives the tongues `height.corners` minus the ember rows' share of the score's height.

## Streak to level

- `share = min(1, streak / growth.full)`; the level goes towards `share` at `growth.rise` per second and falls at `growth.fall` per second.
- **On pause**: the level holds, the clock holds, sparks hold still.
- **On stop**: the fire is gone at the next paint, which is requested at once.

## Redraws

- The timer requests about 30 redraws per second while the level is moving, sparks are alive, or motion is on and the fire is not cold. Otherwise it requests none.
- **Follow**: `TGControl.redraw()`, which repaints the played beat only.
- **Wait**: `TGControl.redraw()`; or `TGControl.redrawOverlay()` if the core hook of research R2 was added.

## Core hook (added: research R2's threshold was exceeded, T002)

- `TGBufferedPainterLocked.requestOverlayOnly()` sets a one-shot flag, and `requestFull()` records that a full picture was asked.
- At the next `paintBufferLocked`, `fillPaintBuffer()` is skipped and the buffer is drawn as it is only if the overlay was asked, **no full picture was asked since the last paint** (in either order: the toolkit merges the two requests into one paint), and the buffer exists with the canvas size. Both flags are cleared at every paint.
- `TGControl.redrawOverlay()` calls `requestOverlayOnly()` and then `canvas.redraw()`; it does not touch the `painting` flag of `redrawPlayingMode`. `TGControl.redraw()` calls `requestFull()`, and so does the retry of a paint that could not take the editor's lock.
- With no caller of `redrawOverlay()`, the behaviour is unchanged.

## Never

- It never changes a verdict, the streak of a session, timing, recording, the palette's moves or the score cursor (FR-008).
