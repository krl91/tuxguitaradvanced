# Contract: Arcade isometric view (increment C)

Normative visual rules are in `../visual-design.md` and in `../spec.md` (FR-062 to FR-082, Q15 to Q42); this contract fixes the programmatic surface and the tests. Packages `arcade.view` (pure, no UI toolkit) and `arcade.ui` (canvas, window, frame source, controller). Brought into line with the code on 2026-10-13.

## Pure layer (`arcade.view`)

```java
final class StringLayout { static StringLayout of(TGTrack track); int count(); int rowOf(int tgString); int tgStringAtRow(int row); }
final class NeckModel { static NeckModel of(TrackTuning tuning, int fretCount); double fretX(int n); double fretCentreX(int n); double cellLeftX(int n); double cellRightX(int n); List<Marker> markers(); }
final class StringColours { static StringColours forStrings(int count); int of(int tgString); }
final class IsometricProjection { IsometricProjection(Viewport v, double farScale, double horizon); double neckX(double xMm, Camera c); double stringY(int row, int count);
    ScreenPoint approach(double xMm, double progress, Camera c); static double depthShare(double progress); double hitLineY(); double neckTopY(); double horizonY(); static final double NECK_NEAR_EDGE; }
final class PaintedFretGrid { static PaintedFretGrid of(NeckModel n, IsometricProjection p, Camera c); double wireX(int n); boolean isPainted(int n); double neckTopY(); double neckBottomY(); }
final class FretArrivalLaneMap { static FretArrivalLaneMap fromPaintedFrets(PaintedFretGrid g, IsometricProjection p); Lane laneForFret(int fret); }   // Lane: origin (horizon) and end (foot of the neck)
final class CameraTrack { static CameraTrack build(List<ArcadeRenderEvent> events, NeckModel neck, Settings s); Camera at(long nanos); }
final class ArcadeRenderEvent { static ArcadeRenderEvent of(int index, PracticeEvent e, PracticeEvent next, FollowTimeline t); /* kind NOTE / CHORD / AUTO / UNSUPPORTED, bend, technique marks */ }
final class ArcadeScene { ArcadeScene(List<ArcadeRenderEvent> events, NeckModel neck, StringColours c, CameraTrack camera, SceneLabels l, boolean monochrome);
    ArcadeScene withPerspective(double farScale, double horizon); ArcadeScene withMarkerSize(double f); ArcadeScene withDensityBudget(boolean on);
    Scene build(long nowNanos, Viewport v, ArcadeRun.View run, int speedPercent, QualityTier tier); }   // pure and deterministic: a list of `SceneItem` (roles in `SceneItem.Role`)
final class BendMark / TechniqueMark   // written effects only: `BendMark.of(PitchPath)`, `TechniqueMark.of(event, next)`
```

## Invariants, each with a test

1. **Strings.** String 1 of TuxGuitar is the high string and is drawn at the **bottom** of the neck (FR-067: the lowest string at the top); each string carries a `lit` flag (FR-068) true only for the strings of the active event; for 4, 6 and 7 strings the mapping is one tested function; colours never swap between strings.
2. **Frets.** `fretX(12) == L / 2` and every `fretX(n)` equals `L (1 - 2^(-n/12))` to 1e-9; markers at 3, 5, 7, 9, 15, 17, 19, 21 single and 12, 24 double.
3. **Landing.** For every event, `progress(onset) == 1` exactly and, since Q37, **everything lands at the foot of the neck** (its bottom edge, `h * NECK_NEAR_EDGE`): a single note by its footprint, a **chord panel, an open-string bar and a timing bar on the same ground path** (straight line from the horizon to the foot, same depth share and scale), the rectangles of a chord on the height of their string rows above that ground (`FootLandingTest`, `ChordPanelTest`, `OpenBarTest`, `ArcadeSceneChordTest`). At any other time progress is a function of `(onset - now)` and the look-ahead only, so pause, restart, speed and tempo changes cannot drift (the onset comes from `FollowTimeline`).
4. **Visibility.** For every event and every instant from its first visible frame to its judgement, every target fret lies inside the camera window with the margin (SC-010); the camera track is continuous (bounded step between consecutive samples, no teleport) and identical for the same timeline. A chord panel never jumps during its approach (`ChordContinuityTest`).
5. **Colour chain.** Marker, lane, target and string label of one note share the destination string's colour from the first frame of the event until its judgement (SC-012); verdict colours are separate and appear only in verdict badges.
6. **Chord.** One translucent panel per chord (`CHORD_PANEL`, `CHORD_PANEL_EDGE`), one `PANEL_NOTE` per fretted position at (its fret, its string), one `PANEL_OPEN_LINE` per open string, `PANEL_NAME` above the left corner (the exact non-blank `TGChord.getName()`, otherwise a deterministic conventional name, never only "Chord"), `PANEL_FRET` on the active chord only, `PANEL_STROKE` from `TGStroke`; no timing bar and no cartouche (FR-074).
7. **Feedback.** The chord-level badge maps from `ArcadeVerdict` (waiting, correct, incomplete with missing pitch *names*, not-judged); no per-string "heard" claim.
8. **Monochrome.** Rendering with colours removed still identifies string (label, row), fret (position), chord timing and verdict (symbol and word).
9. **No numeric score** is ever part of a scene during play (FR-021).
10. **Visual set and HUD.** The picture holds the visual set of FR-062 as redefined by FR-076: every event that is not completed and due within **one floating measure** (never less than one second), the active one always, every later event absent; only a fret number or a name that would overlap another is left out. The texts of the HUD (`HUD_STREAK`, `HUD_SPEED`, `HUD_NEXT`) are inside the picture for every viewport, below a top margin that keeps them clear of a title bar (FR-080, `HudVisibilityTest`).
11. **Predefined fret lanes.** For every visible fretted cell, one lane runs from the horizon to the middle of the cell at the foot of the neck (`FIELD_GUIDE` above the neck, `NECK_LANE` across it); fret 0 has one pre-nut lane. Lane selection depends only on fret. The marker stays on its lane and reaches its end at the onset (FR-066, SC-024). The **approach band** and the lighter lane of the active fret follow the same straight lines to the foot (Q42, `FootLandingTest`).
12. **Open string.** A single note at fret 0 yields one `OPEN_BAR` (horizontal, string colour, lane-wide, `OPEN_CAP` at the left end, `OPEN_LABEL` *open*) and no marker, no fret number and no target; the string is lit from the onset (FR-068); inside a chord the open string is a `PANEL_OPEN_LINE`.
13. **Rounded rectangles.** The marker and the target of a fretted note are rounded rectangles (`MARKER`, `TARGET_RECT`); the marker is raised and joined by `MARKER_THREAD` to `MARKER_FOOTPRINT` with `MARKER_FRET` under it; `FRET_RULER_ACTIVE` marks the fret of the active event.
14. **Neck targets.** Exactly one event identity owns targets on the neck, the active event; completion replaces them atomically (SC-017). A completed event keeps only its verdict badge for the feedback hold.
15. **Bends and techniques.** The marks of FR-077 and FR-078 (`BEND_*`, `TECH_*`) come from written effects only, are drawn in flight and on the neck, are dashed as many short segments of one path (the dash style of the native painter is not honoured for paths on macOS), and never change the judgement.

## UI layer (`arcade.ui`)

- `ArcadeCanvas` owns a `UICanvas`, a repaint timer on the UI thread, and replays the scene on the `UIPainter` of the paint event; a slow frame is skipped, not queued.
- `ArcadeFrameSource` builds each frame from the clock of the player (FR-055) **and**, during the count-in, from its own time (FR-079): the picture runs from one count-in before the music, follows the clock only once it moves (30 ms) and agrees with that time within 400 ms (1.5 s past the count-in it follows it anyway), never jumps at that handover (`ArcadeFrameSourceTest`); a **pause holds** the picture (`setPaused`), a **resume counts in again** from where the music was.
- `ArcadeWindow`: canvas on top, Restart, Pause / Resume and Stop under it; the window is **maximized** (not full screen) and laid out again at every resize, so the picture is never taller than its room (FR-080, `ArcadeWindowSwtTest`); `pausePressed()` is the button logic, tested for Pause then Resume three times.
- `ArcadeController` / `ArcadeEndRouter`: the end of a run is told once, to the controller of the latest run only; the result (FR-081) is built when the song comes to its end (complete or not) or when the learner stops, not for another cause (`ArcadeControllerTest`, `ArcadeEndRouterTest`). `ArcadeResultWindow(…, firstMeasure, lastMeasure, startedMeasure, replay)` replays from the whole track.

Native tests: paint a six-string and a four-string scene without exception; the structural capture (list of shapes and texts) equals the approved reference (`reference/*.scene.txt`, regenerated with `-Dtuxguitar.arcade.reference.write=true`); the pixel tests measure the painted wires of the scene only.
