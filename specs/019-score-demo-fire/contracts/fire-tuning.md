# Contract: the fire tuning (spec 019)

## Identity with the defaults (FR-017, SC-010)

- `FlameDrawing.items(viewport, tier, flames, texts)` (old signature) gives exactly the same items as the new overload with `FireTuning.ARCADE_DEFAULTS`, and as before this spec. `reference/flame-reading.scene.txt` is unchanged.
- `FireColours.colour/alpha`, `FireField.cell` and `FlameLevel()` with no tuning behave exactly as before.
- Every existing test passes unchanged.

## Catalogue (SC-009)

- Every parameter has a key, a range, both defaults, a label key and a tooltip key, present in `messages.properties`, `messages_fr.properties` and `messages_es.properties`.
- Each Arcade default equals the constant it comes from (one assertion per constant).
- No literal shaping a fire is left in `FireField`, `FireColours` or `FlameDrawing` outside the tuning, apart from the fixed list (research R4). This is checked by a source-scan test for decimal literals in those three files, with an explicit allow-list.

## Clamping (FR-019)

`FireTuning.of(...)` brings every value into `[min, max]`. NaN or a missing value gives the default. Colours are masked to RGB.

## Extremes (SC-011)

For each parameter at its minimum and at its maximum, with the others at their defaults, on the reference viewport with guard boxes:
- no item above 12 % opacity within 4 px of a guard box;
- the item count is at most the cost ceiling: columns ≤ 384, cell ≥ 3 px, sparks ≤ 1 000;
- the verdicts of a replayed run are unchanged.

## Store

- **`load` after `save`** gives the same tuning.
- **`reset`** gives the defaults.
- **A corrupt, out-of-range or missing value** gives the clamped value or the default.
- **No method throws.**
- **Score setting**: OFF by default.
