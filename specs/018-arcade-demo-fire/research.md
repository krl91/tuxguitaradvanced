# Research: Arcade's demo fire (spec 018)

## R1. How the flames are drawn today
- **Finding**: `arcade/view/FlameDrawing.items(viewport, tier, flames, texts)` returns scene items put before every other item by `ArcadeScene`; tongues are QUAD strips (`Role.FLAME_TONGUE`, code in `tgString`), embers and the base glow are DISCs (`Role.FLAME_EMBER`); `weight(t)` gives the corner/middle profile (`MIDDLE = 0.20`), `faded(alpha, hull, texts)` lowers the opacity near texts to `NEAR_TEXT = 0.002`; `UiPainterRenderer` fills a QUAD as a path with the item's colour and alpha. Layer opacities 0.38 / 0.50 / 0.62.
- **Decision**: keep the item model, the roles, `weight`, `faded` and the order (flames first). The renderer and the contrast tooling (`SceneBoxes.ofQuad`) need no change.

## R2. A fire without history
- **Finding**: the scene tests build one picture from one `FlameState` (`FlameSceneTest.build(f, tier, state)`); the scene must stay a pure function of its inputs.
- **Decision**: the classic fire (each cell the cooled average of the cells below) is reproduced **without a stored grid**: heat(col, row) = profile(row / height(col)) × turbulence(col, row + rise × clock), where `height(col) = level × factor × 0.55 × H × weight(col)`, profile falls from 1 at the base to 0 at the top, and turbulence is a sum of three octaves of a seeded integer-hash value noise scrolled upwards (rising tongues) with a slight sideways sway. With reduced motion the clock is 0 (still fire, height follows the level).
- **Alternatives**: a stateful cellular grid as in the demo (needs history; breaks the purity of the scene and the replay determinism unless stepped at a fixed rate; rejected).

## R3. Cell size
- **Decision**: cell = max(4, round(viewport width / 192)) screen pixels at FULL (≈ the demo's 192 × 108 grid; 7 px at 1280); BALANCED doubles it. Cells are axis-aligned squares, no smoothing.

## R4. Colours and translucency
- **Decision**: heat quantized to 16 steps; colour scale dark red `0x5A0A04` → red `0xB7210F` → orange `0xF26A12` → yellow `0xFFD23C` → white `0xFFF6D8`; opacity = 0.60 × heat^0.8 (max **0.60**, FR-002, owner's answer), × 0.6 when discreet; heat 0 → no item (nothing drawn, SC-002). The middle-zone rule (≤ 22 % of the corners' opacity) comes from `weight` applied to the heat.

## R5. Number of items
- **Decision**: in each row, consecutive cells of the same heat step merge into one rectangle (QUAD); estimated ≤ 2 500 items at 1280×720 FULL, ≤ 700 at BALANCED. Measured at the baseline and after (phase 1 and 2).

## R6. Embers
- **Decision**: below level 0 + embers: the bottom two rows of cells at low heat (step ≤ 3), role `FLAME_EMBER`; the rest of the fire is `FLAME_TONGUE`. So "only embers at the start" (spec 009 FR-003) keeps its meaning.

## R7. Sparks
- **Finding**: `ArcadeFrameSource.flamesAt` reads `ArcadeRun.View` (score, judged, `streak()`, `states()`); nothing under `arcade/core` mentions flames (`FlameParityTest`).
- **Decision**: `ArcadeFrameSource` counts the right states of each view; each new one adds a burst (flame clock, streak) kept 1 s; `FlameState` carries the list. `FlameDrawing` draws each burst as 4 + min(streak, 12) / 2 sparks (at most 10 per burst, 60 in the air), single cells (QUAD, role `FLAME_EMBER`), position and path from a hash of the burst's time and index, rising and fading over 1 s, faded near texts; over a fire cell of opacity a the spark's opacity is at most (0.60 − a) / (1 − a), so that both together never exceed 0.60 (FR-002). None at BALANCED, off, reduced motion, or paused (the flame clock stands still).

## R9. Owner's look gate tuning (2026-10-30)
- **Finding**: on the built application the owner found the fire too transparent, the sparks too few, the pixels too coarse (spec, Session 2026-10-30 owner's look gate).
- **Decision**: `FireColours.MOST` 0.60 → **0.85** (linear in the step, so the middle keeps ≤ 22 % of the corners' opacity, spec 009 FR-001); `FireField.COLUMNS` 120 → **192**; sparks `20 + 5 × min(streak, 12) / 2` per burst (20 to 50), `MOST_SPARKS` 60 → **300**, the cap over a cell becomes (0.85 − a) / (1 − a). One item per cell is kept (an existing test of spec 009 wants the same number of items whatever the clock, so cells cannot merge): about 2.6 times the items at FULL; measured (T030) and left to the `QualityController` and the owner's smoothness check (SC-007).
- **Test exception (owner, 2026-10-30)**: only the lines pinning 0.60 and the spark counts in `FireColoursTest`, `FireTranslucencyTest`, `FlameSparksTest`, the three lines of `FireFieldTest.theCellsAreBigPixelsAndTwiceAsBigAtTheBalancedTier` pinning the old cell size (granted separately when it failed), plus the regenerated `flame-reading.scene.txt` and `docs/images/arcade-flames.png`.

## R8. The owner's targeted exception (Constitution XI)
Asserting the tongue shape, to be adapted with each line listed in the commit:
- `FlameSceneTest.theTonguesAndTheirLayersFollowTheTierAndTheSetting` (28×3 / 14×2 / 14×3 / 7×2 pieces) → the counts become the field's (FULL more cells than BALANCED; discreet lower).
- `FlameSceneTest.pieces/tallest/layerCount` helpers rely on one code per tongue layer; the height, corner and discreet tests keep their rules with the field's columns as pieces; the exact proportionality `level × top` gets the tolerance of one cell (SC-003: 5 points).
- `ArcadeSceneQualityTest.theFlamesAreDecorationOnlyTheLowestTierDrawsNoneAndTheBalancedTierDrawsHalfTheTonguesInTwoLayers`: "half the tongues in two layers" → "coarser cells, no sparks".
- `ArcadeFlamesSettingTest.offDrawsNoFlameDiscreetFewerAndFaintersThanFull` line 113 ("discreet has fewer tongues", counted by the helper `tongues`, one code per tongue): only if the field's pieces do not give fewer pieces for discreet; the "fainter" line 114 stays.
- `ArcadeSceneQualityTest` lines 177–178 (28×3 and 14×2 layers).
- `reference/flame-reading.scene.txt`: regenerated from the new drawing.
Kept unchanged and expected green: `FlameContrastTest`, `FlameParityTest`, `ArcadeReferenceSceneTest` (with the new file), every off / level-0 / order / monochrome / clamp test. If any other existing test fails, stop and report.
