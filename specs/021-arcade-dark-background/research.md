# Research: The Arcade view keeps its dark design in every system appearance

## R1 — Where the dark background is painted

**Decision**: in `ArcadeCanvas`'s constructor, `this.canvas.setBgColor(...)` with a colour created once from `Theme.DARK.background()` (0x0B0E13, `arcade/view/Theme.java:44-46`). The colour is disposed in `ArcadeCanvas.dispose()`.

**Rationale**:
- The source shows that nothing paints a background today:
  - `ArcadeCanvas.paint` (lines 139-160) hands the scene to the renderer;
  - `UiPainterRenderer.render` replays the scene's items only;
  - the scene's first opaque-looking surfaces are translucent: `NECK_BODY` at `NECK_ALPHA` 0.10 (`ArcadeScene.java:24, 646`), and the field.

  What shows through is the canvas's background, which SWT takes from the system appearance. The diagnostic picture of spec 020 (run 41246758808) shows it light grey.
- `UIControl.setBgColor` → `SWTControl.setBgColor` → `Control.setBackground` (`SWTControl.java:181-184`). A `Composite` with a background colour has that colour filled by SWT under the paint listener, on Cocoa, GTK and Win32, in the double-buffered image (`SWTCanvas.java:15`). It is filled whatever paints next, so it covers the normal path, the simplified path (`UiPainterRenderer.fallback`), every tier and every size.
- `Theme.background()` is already the background of the reference pictures (`test/.../arcade/view/SceneImageWriter.java:34`, `PaintedAlignmentTest.java:32`), so the live view matches them (FR-002).
- The scene is always built in `Theme.DARK` (`ArcadeScene.java:136`; nothing calls `withTheme` outside tests), so the constant is right for every frame. FR-005 keeps it so.

**Alternatives considered**:
- *An opaque background item first in the scene* (`ArcadeScene`): it would change the items that many existing scene tests list and count. Never modify existing tests, so it is rejected.
- *A `fillRectangle` at the start of `UiPainterRenderer.render`*: the renderer tests record the painter's calls (`PaintRecorder`, `UiPainterRendererTest`, `FallbackRendererTest`, `FlameCellsPaintTest`), so an extra call can break their expectations. Rejected for the same reason.
- *Selecting `Theme.LIGHT` in light appearance*: this is option B, which the owner rejected (spec, Context).

## R2 — The rest of the Arcade window

**Decision**: unchanged. The row of buttons under the canvas (`ArcadeWindow.java:59-74`) is made of ordinary controls and keeps the system appearance (FR-006, clarification 1).

## R3 — How the change is tested

**Decision**: a new native SWT test class, `ArcadeDarkBackgroundSwtTest` (tag `swt-gui`). It opens a real window with an `ArcadeCanvas` like the existing pixel test does, paints one frame and copies the canvas back to pixels with `GC.copyArea`, the way `ArcadePixelAlignmentSwtTest` reads its pictures. That test is not touched: the new class has its own small harness. It checks that points of the frame with no scene item on them (the top corners, above the horizon of the field) have the colour 0x0B0E13, within ±6 per channel:
1. with the normal path;
2. with the simplified path (the constructor's `forceFallback = true`);
3. after a resize to a larger and a smaller size;
4. through the control itself: `getControl().getBgColor()` gives 0x0B0E13 (a test that does not depend on the screen).

**Rationale**:
- On the owner's Mac (dark appearance) the system's window background is not 0x0B0E13, so cases 1–4 fail before the fix: red first. Light appearance cannot be switched on the owner's Mac without touching their system settings, so the light case is checked by the CI machine, which is in light appearance. The two existing tests that fail there are the evidence (SC-003).
- Which empty points to read is decided from the scene of the frame itself (the items' bounds), not guessed. If the corners are covered by an item at some size, the test picks a point that no item covers.

## R4 — Risk: the CI screen is smaller than some checked sizes

The GitHub machine's display is 1024×768 (run 41246758808). The existing pixel test opens windows up to 1600×900, and those reach past the screen. The saved picture of 1280×720 was complete (the disk was found where expected), so painting off screen into the double-buffered canvas works. If the run after the fix still fails for a reason tied to the screen size, it is a second cause: it gets the bug fix protocol, with a reproducing test first and at most 3 tries, then it is reported. Tests are never changed.

## R5 — The release

**Decision**: after the green run, the release workflow is run by hand with the tag `v0.8.0-beta.4` (`release.yml` lines 5-6, 33-37). The notes go in `docs/release-notes/v0.8.0-beta.4.md` and say that the Arcade view keeps its dark design when the system is in light appearance, on macOS, Linux and Windows (all-OS parity). One push per round, both refs together. The tag is never moved.
