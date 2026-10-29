# Data Model: spec 017

All values are screen pixels, held only while a run goes; nothing is stored.

## Box
x, y, width, height (doubles). Empty or null = unknown.

## DodgeInput
- `palette`: Box of the palette window.
- `window`: Box of the main window (to tell a learner's move outside it, FR-007).
- `area`: Box of the score area (the canvas mapped to the screen); null → no decision.
- `current`: Box of the current event's notes, or null (not displayed, end of run).
- `next`: Box of the next event's notes, or null (always null while the run is paused).
- `state`: PaletteState.

## PaletteState
- `lastAuto`: the position the dodge set last, or null.
- `leftCorner`: the corner left by the last move, or none.
- `movedFor`: the identity of the current event when the last move was made (one move per framed note).
- `areaSize`, `paletteSize`: the sizes at the last move (a change allows a new move).
- `outside`: true when the learner's last move put the palette outside the main window (never moved); not set when the main window moves away from the palette.

## Corner
TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT; position = the corner of `area` inset by 16 px, palette fully inside.

## Decision
Either `Stay` or `MoveTo(corner, x, y)`.

Rules: research R5 and R6; validation: a MoveTo position is always inside `area`.
