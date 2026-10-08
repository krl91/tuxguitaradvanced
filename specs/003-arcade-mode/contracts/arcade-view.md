# Contract: Arcade isometric view (increment C)

Normative visual rules are in `../visual-design.md`; this contract fixes the programmatic surface and the tests. Packages `arcade.view` (pure) and `arcade.ui` (`ArcadeCanvas`).

## Pure layer

```java
final class StringLayout { static StringLayout of(TGTrack track); int count(); int rowOf(int tgString); int tgStringAtRow(int row); }
final class NeckModel { static NeckModel of(StringLayout l, double scaleLength, int frets); double fretX(int n); double fretCentreX(int n); Marker[] markers(); }
final class StringColours { static StringColours of(int count); Colour of(int tgString); }
final class IsometricProjection { Point project(double modelX, double modelY, double depth, Viewport v); }
final class FretArrivalLaneMap { static FretArrivalLaneMap fromPaintedFrets(PaintedFretGrid grid); Lane laneForFret(int fret); }
final class CameraTrack { static CameraTrack build(List<ArcadeRenderEvent> events, NeckModel neck, CameraSettings s); Camera at(long nanos); }
final class ChordCartouche { static ChordCartouche of(PracticeEvent chord, StringLayout l, TGChord metadata?); }
final class ArcadeScene { static List<Shape> build(ArcadeRun.View run, CameraTrack camera, NeckModel neck, long nowNanos, Viewport v); }
```

## Invariants, each with a test

1. **Strings.** String 1 of TuxGuitar is the high string and is drawn at the top of the neck and at the right of a chord diagram; for 4, 6 and 7 strings the mapping is one tested function; colours never swap between strings.
2. **Frets.** `fretX(12) == L / 2` and every `fretX(n)` equals `L (1 - 2^(-n/12))` to 1e-9; markers at 3, 5, 7, 9, 15, 17, 19, 21 single and 12, 24 double.
3. **Hit line.** For every event, `progress(onset) == 1` exactly; at any other time progress is a function of `(onset - now)` and the look-ahead only, so pause, restart, speed and tempo changes cannot drift (the onset comes from `FollowTimeline`).
4. **Visibility.** For every event and every instant from its first visible frame to its judgement, every target fret (every `ChordNote` of a chord) lies inside the camera window with the margin (SC-010), over single notes, wide chords and large jumps; the camera track is continuous (bounded step between consecutive samples, no teleport) and identical for the same timeline.
5. **Colour chain.** Marker, trajectory, target ring, string label and the dot in the cartouche of one note share the destination string's colour from spawn to impact (SC-012); verdict colours are separate and appear only in verdict badges.
6. **Chord.** One neutral timing bar and one camera-facing cartouche per chord event, moving as one transform; A5 fixture (low E 5, A 7, D 7) gives a `5fr` window and three coloured dots; open string -> `O`, explicit dead note -> `X`, omitted string blank; the header uses the exact non-blank `TGChord.getName()`, otherwise a deterministic conventional name resolved from written pitches/bass before rendering, or an explicit pitch-list fallback for an unsupported set—never only "Accord"/"Chord"; stroke arrow from `TGStroke` (none -> no arrow); the cartouche keeps a minimum readable size.
7. **Feedback.** The chord-level badge maps from `ArcadeVerdict` (waiting, correct, incomplete with missing pitch *names*, not-judged); no per-string "heard" claim; duplicate pitches are shown "not checked separately".
8. **Monochrome.** Rendering with colours removed still identifies string (label, row), fret (position), chord timing (bar) and verdict (symbol and word).
9. **No numeric score** is ever part of a scene during play (FR-021).
10. **Resize / density.** A resize keeps the minimum cartouche size; a dense passage drops detail in a documented order (far events first) and never hides the current event.
11. **Predefined fret lanes.** For every visible fretted cell, one lane ends at the midpoint of its painted upper edge; fret 0 has one pre-nut lane. Lane selection depends only on fret, so notes on different strings at the same fret reuse identical geometry and differ only through string colour and the separate target disk. The marker remains on its lane, reaches the endpoint at onset, and no approach line or connector enters the physical neck (FR-066/SC-024).

## Canvas layer

`ArcadeCanvas` owns a `UICanvas`, a repaint timer on the UI thread, and replays the scene on the `UIPainter` of the paint event. It reads the clock through the same `TempoClock` as the session (the player's), never accumulates time, and never blocks: a slow frame is skipped, not queued. Native tests: paint a six-string and a four-string scene without exception; the structural capture (list of shapes and texts) equals the approved reference.
