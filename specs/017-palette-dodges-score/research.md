# Research: The practice palette moves aside (spec 017)

## R1. Where the palette is today
- **Finding**: `ui/PracticePalette.java` opens a top-level `UIWindow` (`uiFactory.createWindow(parent, false, false)`), packs it and places it by `placeWindow`: at the main window's top right (`x = right − width − 24`, `y = top + 80`), or at `lastBounds`, recorded in `close()`. `refit()` resizes it through `WindowFit.refit` after content changes. The keyboard shortcuts are attached by `listenKeys` on the palette's own controls.
- **Decision**: keep this; add a `moveTo(x, y)` that only changes the position (`window.setBounds` with the same size) and records the place set automatically, to tell a learner's move from an automatic one (R6).

## R2. The box of the note to play
- **Finding**: `PracticeHighlight.show` sends the current event's notes to `TGExternalBeatViewerManager.showExternalNotes`; `TGControl.paintTablature` paints the layout with `-scrollX, -scrollY`, so `TGMeasureImpl.getPosX/getPosY` are canvas coordinates after a paint; `TGExternalNoteFramePainter.paintScoreFrame/paintTablatureFrame` compute the frame from those positions and paint it **only while the player is stopped** (`TGControl` line ~222: `if (!isPlaying)`). In Follow the `MidiPlayer` runs (`MidiFollowPlayer`), so the screen shows the player's beat highlight (`paintPlayMode`) at the same beat instead of the red frame.
- **Decision**: extract the two box computations into `public static UIRectangle frameBounds(TGLayout, List<TGNote>, int trackSelection)` (the union of the score and tablature boxes; null when the measure is out of bounds or not displayed), used by `paint` unchanged. The plugin asks it for the **current** event and the **next** event (FR-002 "or the next note to play"). The same box serves both modes: in Follow the highlighted beat is the current event's beat.
- **Alternatives**: recompute in the plugin (duplicate geometry, rejected); listen to paints (no such event, rejected).

## R3. From canvas to screen
- **Finding**: `UIControl.getBounds()` is parent-relative; `UILayoutContainer.getChildArea()` is client-relative (`SWTLayoutContainer` returns `getClientArea()`); no toolkit method maps to the screen; SWT's `toDisplay` appears only inside `TuxGuitar-ui-toolkit-swt` (`SWTScaleKnob`, `SWTToolMenuItem`). The canvas is `TGTabFolder.getInstance(context).findSelectedControl().getCanvas()`.
- **Decision**: add `default UIPosition toDisplay(UIPosition point) { return null; }` to `UIControl`; `SWTControl` overrides it with `getControl().toDisplay`. Null means "unknown": the feature does nothing (JavaFX, Qt). The score area is the canvas box mapped to the screen.

## R4. When to check
- **Decision**: on every `PracticeWindow` render that calls `highlight.show` (event change), and with a UI-thread repeat every **100 ms** while a Wait or Follow run is running (catches scroll, resize, palette refit, and a paint that came after the event change). 100 ms keeps SC-001's 0.3 s after a scroll or resize with margin; the cost is a few rectangle operations.
- **Alternatives**: listen to resize/scroll events (no scroll event exposed by `TGControl`; rejected).

## R5. The decision (pure)
- **Decision**: `PaletteDodge.decide(input)` with: the palette box, the score area, the current box, the next box (both may be null), the palette's state (auto place left, learner's place, outside), returns a new position or none.
  - Move only if the palette box (grown by a margin of 8 px) meets the current or the next box.
  - Candidates: the four corners of the score area at a margin of 16 px (palette fully inside the area).
  - Free = meets neither box. Among free corners: exclude the corner just left while the current box is still within one palette width of it; prefer corners not on the side the notes head to (the next box's centre compared with the current box's centre on the same line: same line = vertical overlap); then the nearest to the current place.
  - No free corner: the corner covering the least area of the current box, and stay (no further move until a corner becomes free or the window changes size).
  - At most one move per current event, except on a size change of the score area or the palette.
- **Rationale**: pure and testable; covers FR-002–FR-005 and US2.

## R6. The learner's move
- **Decision**: the palette remembers the position it set last; if its actual position differs (by more than 2 px) the learner moved it: that becomes the learner's place. If the palette box is not inside the main window's box, it is "outside": never moved (FR-007). Reopening uses `lastBounds` as today (FR-008).

## R7. Focus
- **Finding**: SWT `Shell.setBounds` does not activate the shell; the move uses only `setBounds`.
- **Decision**: verified by an `swt-gui` test: the focused control before the move keeps the focus after it (SC-003).

## R8. Scope
- **Decision**: wired only from the Wait and Follow paths of `PracticeWindow`; Arcade has its own window and never calls it (FR-009); stopped when the run ends or the palette closes; at the end of a run the palette stays where it is (Edge Cases).
