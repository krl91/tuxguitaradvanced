# Contract: Arcade isometric view (increment C)

Normative visual rules are in `../visual-design.md`; this contract fixes the programmatic surface and the tests. Packages `arcade.view` (pure) and `arcade.ui` (`ArcadeCanvas`).

## Pure layer

```java
final class StringLayout { static StringLayout of(TGTrack track); int count(); int rowOf(int tgString); int tgStringAtRow(int row); }
final class NeckModel { static NeckModel of(StringLayout l, double scaleLength, int frets); double fretX(int n); double fretCentreX(int n); Marker[] markers(); }
final class StringColours { static StringColours of(int count); Colour of(int tgString); }
final class IsometricProjection { Point project(double modelX, double modelY, double depth, Viewport v); }
final class PaintedFretGrid { static PaintedFretGrid of(NeckModel neck, IsometricProjection p, Camera c); double wireX(int n); double neckTopY(); }
final class FretArrivalLaneMap { static FretArrivalLaneMap fromPaintedFrets(PaintedFretGrid grid, IsometricProjection p); Lane laneForFret(int fret); }
final class CameraTrack { static CameraTrack build(List<ArcadeRenderEvent> events, NeckModel neck, CameraSettings s); Camera at(long nanos); }
final class ChordCartouche { static ChordCartouche of(PracticeEvent chord, StringLayout l, TGChord metadata?); }
final class ArcadeScene { static List<Shape> build(ArcadeRun.View run, CameraTrack camera, NeckModel neck, long nowNanos, Viewport v); }
```

## Invariants, each with a test

1. **Strings.** String 1 of TuxGuitar is the high string and is drawn at the **bottom** of the neck (FR-067: the lowest string is at the top) and at the right of a chord diagram; each string carries a `lit` flag (FR-068) true only for the strings of the active event; for 4, 6 and 7 strings the mapping is one tested function; colours never swap between strings.
2. **Frets.** `fretX(12) == L / 2` and every `fretX(n)` equals `L (1 - 2^(-n/12))` to 1e-9; markers at 3, 5, 7, 9, 15, 17, 19, 21 single and 12, 24 double.
3. **Hit line.** For every event, `progress(onset) == 1` exactly (for a single note the middle of the upper edge of its cell, FR-066); at any other time progress is a function of `(onset - now)` and the look-ahead only, so pause, restart, speed and tempo changes cannot drift (the onset comes from `FollowTimeline`).
4. **Visibility.** For every event and every instant from its first visible frame to its judgement, every target fret (every `ChordNote` of a chord) lies inside the camera window with the margin (SC-010), over single notes, wide chords and large jumps; the camera track is continuous (bounded step between consecutive samples, no teleport) and identical for the same timeline.
5. **Colour chain.** Marker, lane, target disk, string label and the dot in the cartouche of one note share the destination string's colour from the first frame of the event until its judgement (SC-012); verdict colours are separate and appear only in verdict badges.
6. **Chord.** One neutral timing bar and one camera-facing cartouche per chord event, moving as one transform; A5 fixture (low E 5, A 7, D 7) gives a `5fr` window and three coloured dots; open string -> `O`, explicit dead note -> `X`, omitted string blank; the header uses the exact non-blank `TGChord.getName()`, otherwise a deterministic conventional name resolved from written pitches/bass before rendering, or an explicit pitch-list fallback for an unsupported set—never only "Accord"/"Chord"; stroke arrow from `TGStroke` (none -> no arrow); the cartouche keeps a minimum readable size.
7. **Feedback.** The chord-level badge maps from `ArcadeVerdict` (waiting, correct, incomplete with missing pitch *names*, not-judged); no per-string "heard" claim; duplicate pitches are shown "not checked separately".
8. **Monochrome.** Rendering with colours removed still identifies string (label, row), fret (position), chord timing (bar) and verdict (symbol and word).
9. **No numeric score** is ever part of a scene during play (FR-021).
10. **Resize / density.** A resize keeps the minimum cartouche size; the picture holds the visual set (FR-062): every event due within five seconds, the active one always, every later event absent; the set is the same in every tier and at every size; only a fret number that would overlap another is left out.
11. **Predefined fret lanes.** For every visible fretted cell, one lane ends at the midpoint of its painted upper edge; fret 0 has one pre-nut lane. Lane selection depends only on fret, so notes on different strings at the same fret reuse identical geometry and differ only through string colour and the separate target disk. The marker remains on its lane, reaches the endpoint at onset, and no approach line or connector enters the physical neck (FR-066/SC-024).
12. **Visual set.** For 8, 16 and 32 events in the look-ahead (notes only, and notes with chords), at several sizes, tiers, instants and camera positions: exactly the events due within five seconds are painted, each note with its marker and whole lane; every other event carries no primitive; no role of a preview or compact form of an event exists (`NoteBudgetTest`); labels are disjoint and inside the viewport (SC-019).
13. **Neck targets.** Exactly one event identity owns targets on the neck, the active event; completion replaces them atomically (SC-017). A completed event keeps only its verdict badge for the feedback hold.

## Canvas layer

`ArcadeCanvas` owns a `UICanvas`, a repaint timer on the UI thread, and replays the scene on the `UIPainter` of the paint event. It reads the clock through the same `TempoClock` as the session (the player's), never accumulates time, and never blocks: a slow frame is skipped, not queued. Native tests: paint a six-string and a four-string scene without exception; the structural capture (list of shapes and texts) equals the approved reference.
12. **Open string.** A single note at fret 0 yields one `OPEN_BAR` item (horizontal, string colour, lane-wide, cap at the left end, label *open*) and no marker, no fret-number label and no target disk; the string is lit from the onset (FR-068); inside a chord the open string is `O` of the cartouche only.
13. **Rounded rectangles.** The marker and the target of a fretted note are rounded rectangles (`MARKER`, `TARGET_RECT`), the marker is raised and joined by `MARKER_THREAD` to `MARKER_FOOTPRINT` with `MARKER_FRET` under it; `FRET_RULER_ACTIVE` marks the fret of the active event; `APPROACH_BAND` is the only background of the field.
