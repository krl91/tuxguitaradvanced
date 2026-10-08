# Rendering backend decision (spec 003 T045), with the T046, T047 and T048 figures

## Decision

**Keep the existing toolkit renderer (`UIPainter`, SWT on this machine) as the selected path, with a forced and automatic fallback path behind the same `ArcadeRenderer` contract. No game engine, no OpenGL binding and no direct Metal backend is introduced: the measurements do not call for one.** The scene model stays plain Java and independent of any renderer.

## What was measured, with what

| measure | figure | where |
|---|---|---|
| paint cost of a dense passage on the real SWT painter, before the density budget | p50 7.7 ms, p99 8.9 ms, max 11.9 ms | `scene-swt-2026-10-08.txt` (first version) |
| the same, with the budget of four detailed events | p50 2.1 ms, p99 3.3 ms, max 4.2 ms | `ArcadeCanvasSwtTest`, 600 frames |
| complete assembly, ten minutes, real `MidiPlayer`, live recognition chain on a seeded recording, real canvas painting a deliberately heavy stand-in scene (twenty notes a second, then six chords a second) | paint cost p50 2.40, p95 4.47, p99 5.15, max 25.12 ms | `stress-10min-2026-10-08.md`, run 3 |
| forced fallback path, same dense passage | p50 2.10, p99 2.88 ms | `fallback-2026-10-08.txt` |

The cost of painting is far inside the targets of SC-014 (p95 at most 16.7 ms, p99 at most 33.3 ms). What cost time before was the number of items drawn, and the density budget of FR-062 removed it: that is the reduction that counts, and a faster backend would not have been needed.

## `fieldResolution()`

It was never applied by any renderer and has been **removed** from `QualityTier` (and its tests): the cost depends on the number of items, not on the number of pixels (measured above), so a lower internal resolution would have added an off-screen surface and a scaling step for no measured gain. If a future measurement on a denser display shows otherwise, it can be added behind `ArcadeRenderer` without touching the scene.

## The fallback (T046, T048)

`UiPainterRenderer.fallback` draws the same information at the same places with the same texts, opaque and without antialiasing of the shapes, and leaves out the decoration, the depth cues and the inlays. `GuardedRenderer` uses it when it is forced and, for good, when the selected path fails on a frame. Tests: same texts at the same places for every reference scene, the selected path is never used when forced, a failing path is replaced within the frame, the scene is the same, and the target keeps its opaque core through the fallback (`FallbackRendererTest`, `TargetPrimitivesTest`, `ArcadeCanvasSwtTest`). The fallback costs about as much as the selected path on a scene already reduced by the budget (p50 2.10 against 2.25 ms): it is a safety, not a speed-up. Live resize through nine window sizes and a doubling of the scale (the proxy for a Retina display: the scene is built in points and every essential position and size doubles) keep the labels disjoint and inside (`ArcadeCanvasSwtTest`, `ArcadeSceneScaleTest`). No real Retina display was available to this run.

## T047, the ten-minute acceptance: NOT MET, and not claimed

Two criteria were missed, one of them not because of the picture:

1. **No two consecutive missed deadlines: missed.** In the run with the renderer, 109 of 27,101 deadline slots had no paint ending in them, with a longest run of 12 consecutive slots (about 200 ms), beginning 14.7 s into the run. A 120-second run of the same assembly showed the same kind of run at 14.3 s (2 slots), and a 60-second run of the renderer alone did not (at most 2 slots, at the start). No paint was longer than two frames, and the quality controller never left FULL, so the cost of painting is not what stopped the frames; the cause was not found (the garbage collector paused 1 to 4 ms in the short run; the long run had 70 young collections of 131 ms in all).
2. **Identical digest of the judgements: not obtained, and not obtainable in this way.** The two runs *without* the renderer already differ from each other in 9 of 1,129 lines (a note matched in one and missed in the other, a timing label early against on time), and the run with the renderer differs from the first of them in 13. The real-time chain (capture thread, analysis thread, session thread) is not bit-reproducible over ten minutes whatever draws, so a strict identity cannot be asserted against it; a pair of runs with the renderer was not made, so the 13 cannot be told apart statistically from the 9. The structural proof that the picture changes nothing in the judging is the pure one: `ArcadeDensityDigestTest` runs the same recorded play three times on the deterministic fake player (no picture, budgeted picture, unreduced picture) and the digests are identical.
3. Met: no capture overrun, no sample refused by the ring buffer, all 563 notes of the backing delivered by the real sequencer in each run, no frame queue (at most one pending), allocation per paint 199 KB at the median and 214 KB at p99 on the painting thread.

**No absence of audio regression is claimed.** The run with the renderer shows a larger jitter than the two without: longest gap between two analysis frames 65 ms against 42 and 48 ms, longest lateness of the feeder 35 ms against 23 and 27 ms, longest time the player's position did not move 58 ms against 31 and 37 ms. There was no overrun and no lost sample, but with one run of each kind this is a hint, not a conclusion, and the acceptance must be repeated (more runs, on a quiet machine) before the claim can be made. Monitoring does not exist yet: **not applicable, to be replayed after Increment E.**
