# Final report — spec 018: Arcade's flames as the classic fire of the Amiga demos

Date: 2026-10-30. Branch `feature/interactive-practice`. Starting commit `8be3bcaa9` (baseline in `baseline.txt`).

## What was built

- **`FireColours`**: 16 heat steps, from dark red `0x5A0A04` through red, orange and yellow to white `0xFFF6D8`. Step 0 draws nothing; the opacity is `0.60 × step / 15`, × 0.6 when discreet.
- **`FireField`** (pure):
  - square cells of width / 120 (at least 4 px), twice as big at the balanced tier;
  - two ember rows at the foot, and in each column a flame of spec 009's height (`level × factor × 55 % × weight`, the weight taken at the column's outer edge);
  - the top cell is cut to that height, so the fire follows the level smoothly;
  - the heat comes from a seeded value noise, twice as tall as wide, scrolled upwards by the flame clock with a slight sway. The clock never changes the height.
- **`FlameDrawing`** rewritten: one translucent quad per lit cell, in place of the tongues, ember discs and glow. The columns carry spec 009's codes (28 × 3 groups at full, 14 × 2 at balanced, half when discreet). The near-text fade and the place before every other item are kept.
- **Sparks**:
  - `FlameState.Burst` is additive; the old constructor is unchanged.
  - `ArcadeFrameSource` adds one burst per event newly right (on time, early, late), with the flame clock and the streak, and drops it after 1 s of flame clock (a pause holds it).
  - `FlameDrawing` throws `4 + min(streak, 12) / 2` single-cell sparks per burst (60 % when discreet, at most 60 in the air), only at the full tier with motion. They fade near texts and are capped over a fire cell so the two together stay ≤ 60 %.
  - Nothing under `arcade/core` changed.
- Docs: `docs/PRACTICE.md`, `-FR`, `-ES`; sheet F06 updated (steps 10 to 14); `docs/images/arcade-flames.png` redrawn by `ArcadeReferenceImagesTest`.

## Deviations from the plan

- **Cell = width / 120 instead of / 192, and one item per cell instead of merged runs.** The kept test `FlameSceneTest.theSameInputsGiveTheSameItemsAndTheClockMovesOnlyTheSway` needs the same item count whatever the clock, so the lit cells depend on the level only. With 10 px cells the count was 2716 / 764 (above the plan's ≈2500 / 700); with 11 px cells it is **2268 at FULL and 646 at BALANCED** (measured, `baseline.txt`).
- **Opacity linear in the step**, not `heat^0.8`: with `heat^0.8` the middle (a fifth of the corners' heat) would have 28 % of their opacity, above spec 009's 22 %.
- **The flame height is counted above the two ember rows**, as the tongues were measured. The whole fire is therefore 55 % of the height plus two cells at the outermost columns.
- **The top cell of each column is cut** (not square), so the height follows the level by small steps, as `ArcadeFrameSourceTest` requires.

## The owner's exception (research R8)

Not used for any assertion. **No existing test was changed.** `FlameSceneTest`, `ArcadeSceneQualityTest`, `ArcadeFlamesSettingTest`, `FlameContrastTest`, `FlameParityTest`, `ArcadeFrameSourceTest`, `FallbackRendererTest` and `ArcadeCanvasSwtTest` pass unchanged. Changed under the exception: only `arcade/reference/flame-reading.scene.txt`, regenerated with `-Dtuxguitar.arcade.reference.write=true`. The count assertions of spec 009 (28 × 3 …) hold because the cells carry codes grouped that way: a labelling of the columns, not tongues.

## What ran

- New tests: `FireColoursTest` 3, `FireFieldTest` 7, `FireTranslucencyTest` 2, `FlameSparksTest` 7, `ArcadeFrameSourceBurstsTest` 3 (22). Each was seen failing first: compile errors for the missing classes; `FireTranslucencyTest` failed on the old tongues; four tests of `FlameSparksTest` failed before the sparks were drawn.
- Full gate (T021), `mvn -o -e clean verify -P native-modules`: BUILD SUCCESS. Results:
  - lib 73, compat 1, editor-utils 1, gtp 10 (1 skipped), midi 2, audio-engine 378, tuner-next 24, synth 9;
  - **practice 1318 (= 1296 + 22), 0 failures, 12 skipped**;
  - `swt-gui-tests`: **37**, 0 failures;
  - `git diff --name-status 8be3bcaa9 -- '*/test/*'`: the five added test files and the `M` of the regenerated reference.
- The built application was relaunched on `tests/demo-arcade-morceau.tg` (T022).

## The owner's gate (T023) and the tuning (Phase 6, T025–T035)

**Done 2026-10-30.** The owner found the fire too transparent, with too few sparks and pixels too coarse, and chose: **85 %** at most, **five times the sparks** (20 to 50 per right note, 300 at most in the air), **the demo's grid** (cell = width / 192, 7 px at 1280). Recorded in spec.md and research R9.

- The real-painter test `ArcadeCanvasSwtTest` then fell from FULL to BALANCED (no sparks): about 12 ms to paint a frame. Three tries (bug-fix protocol): batching the cells by colour and alpha, joining row neighbours, then **filling without antialiasing**, which fixed it (about 2 ms; hard pixels, as in the demos). Research R10.
- Measured: 5302 flame items at FULL, 1450 at BALANCED (1280×720); scene built in about 2.5 ms.
- Test lines changed under the owner's exceptions (each listed in commits 01228dfcf and 0b069fc50): the 0.60 and spark-count lines of `FireColoursTest`, `FireTranslucencyTest`, `FlameSparksTest`; three lines of `FireFieldTest` (cell size); the last line of `FallbackRendererTest.theFallbackPaintsNoFlameAndTheSelectedPathPaintsThemAll`; `flame-reading.scene.txt` regenerated. Added: `FireLookTuningTest` (2), `FlameCellsPaintTest` (4), helper `FlameItemsAccess`.
- Gate (T035): BUILD SUCCESS; **practice 1324 (= 1318 + 6), 0 failures, 12 skipped**; `swt-gui-tests` **37**, 0 failures.

**T036, the owner's second try: done 2026-10-31** — the owner tried the tuned fire on the built application: "ça marche bien" (SC-001 kept; smoothness seen by the owner, SC-007).

## Not seen

- The fire on screen in the application with a real run (only the real-canvas test measured the paint cost).
- Linux and Windows: by CI only.
- The Arcade screenshots of the manual (`docs/images/manual/arcade-*.png`, `features/arcade-*.png`) are real captures of the old flames and were not retaken.
