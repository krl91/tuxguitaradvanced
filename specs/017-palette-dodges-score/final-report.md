# Final report — spec 017: the practice palette moves aside

Date: 2026-10-30. Branch `feature/interactive-practice`. Starting commit `5b64785a7` (baseline in `baseline.txt`).

## What was built

- **Screen mapping in the UI toolkit**: `UIControl.toDisplay(UIPosition)` (default `null`), implemented for SWT in `SWTControl`. JavaFX and Qt keep the default: there the palette never moves (nothing decided without the score area).
- **The frame box**: `TGExternalNoteFramePainter.frameBounds(layout, notes, trackSelection)` gives the box the red frame is drawn in (union of the score and the tablature boxes); the painting is unchanged.
- **`PaletteDodge`** (pure decision): stay, or jump to one of the four corners of the score area, 16 px from its edges, ahead of time (the next note counts too, except in a pause), a note within 8 px counts as under; a corner on the side the notes head to comes last; never back to the place just left while the note is within one palette width of it; at most one move per framed note unless the score area or the palette changes size; with no free corner, the corner that covers the least, and only if it covers less than here; a palette moved by the learner keeps its place until a note comes under it; put outside the main window by the learner, never moved until brought back inside.
- **`ScoreBoxes`** (screen boxes of the score area and of an event's notes) and **`PaletteGuard`** (the check at each change of the framed note and every 100 ms while a run goes, through the synchronizer). Used in Wait and in Follow; never in Arcade.
- **`PracticePalette.moveWindow`** moves the window only (no focus, no activation).
- Documentation in `docs/PRACTICE.md`, `-FR`, `-ES`; manual sheet `specs/test-sheets/F10-palette-ecartee.md`.

## Deviation from the plan

- `PaletteGuard` is not in `tasks.md`: `PracticeWindow` may not create threads or timers (`PracticeHostSourceTest`), so the 100 ms check lives in its own class.
- The rules of US2 and US3 were written together with US1 in `PaletteDodge`, so their tests (T013, T015) could not be seen red first. Instead each rule was broken on purpose (8 mutations: heading away, not back, one move per note, outside, next note, margin, least covering then stay, learner reset): each time exactly one test of `PaletteDodgeTest` failed, and the source was restored.

## What ran

- `PaletteDodgeTest` 14 tests, `PaletteMoveSwtTest` 1 test (real SWT: the focus control and the active shell are unchanged by the move, only the position changes; the screen mapping is checked).
- Full gate (T019), `mvn -o -e clean verify -P native-modules`: BUILD SUCCESS; lib 73, compat 1, editor-utils 1, gtp 10 (1 skipped), midi 2, audio-engine 378, tuner-next 24, synth 9, **practice 1296 (= 1282 + 14), 0 failures, 12 skipped**. `swt-gui-tests`: **37 (= 36 + 1)**, 0 failures. `git diff --name-status 5b64785a7 -- '*/test/*'`: only the two added files.
- The built application was relaunched on `tests/basic_test.tg` (T020).

## The owner's gate (T021)

**Pending.** Sheet F10: Wait and Follow runs, the palette never hides the note to play or the next one, no back and forth, shortcuts without a click, a palette put beside the window stays there (SC-004).

## Not seen

- JavaFX and Qt have no screen mapping: the palette does not move there (by design, not tested by hand).
- Linux and Windows: by CI only.
- SC-001 (the note uncovered within 0.3 s after a scroll) is held by the 100 ms period, checked by a test of the constant, not measured on screen.
