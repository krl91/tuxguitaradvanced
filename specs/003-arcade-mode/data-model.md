# Data model: Arcade (specification 003)

All objects are in memory for one run (FR-026). "Source" names the existing class the data comes from; nothing is read from the song except through the practice model.

## Run side (package `arcade.core`)

| Entity | Fields | Source / rule |
|---|---|---|
| `ArcadeVerdict` | enum: `WAITING`, `ON_TIME`, `EARLY`, `LATE`, `WRONG`, `INCOMPLETE`, `MISSED`, `NOT_JUDGED`, `AUTO_PASSED` | One function from `FollowSession.EventResult` (R6): MATCHED + timing -> ON_TIME/EARLY/LATE; WRONG_PITCH -> WRONG (note) or INCOMPLETE (chord with `missingPitches`); MISSED; NOT_JUDGED; AUTO_PASSED; PENDING -> WAITING. Total: every status maps to exactly one verdict. |
| `ArcadeScoring` | table of points per verdict, streak multiplier steps | R5; data, not code paths. |
| `ArcadeEventState` | `eventIndex`, `verdict`, `offsetMillis`, `techniques` (as in `EventResult`, apart), `pointsEarned`, `streakAfter` | Computed once an event is resolved; never changed afterwards (SC-004). |
| `ArcadeRun` | `sequence` (`PracticeSequence`), `timeline` (`FollowTimeline`), `trackNumber`, `speedPercent`, `states[]`, `score`, `streak`, `bestStreak`, `extras` | Fed by the snapshots of the following session; holds no clock of its own. |
| `ArcadeResult` | `score`, `maxScore`, `percent`, counts per verdict (from `FollowSummary`), `bestStreak`, `techniques` apart | Built from the final snapshot; shown, then dropped. |

## View side (package `arcade.view`)

| Entity | Fields | Source / rule |
|---|---|---|
| `StringLayout` | `count`, `openMidi[]`, `scaleLengthMillimetres`, physical order (low at the top, FR-067) | `TGTrack.stringCount()`, `getString(n).getValue()`, `getOffset()`; one mapping function string number <-> row used everywhere (visual-design.md). Default scale length by string count (guitar 648 mm, bass 864 mm) unless a model overrides. |
| `NeckModel` | `StringLayout`, `fretCount`, `fretX(n) = L (1 - 2^(-n/12))`, string gauges, markers (3, 5, 7, 9, 15, 17, 19, 21 single; 12, 24 double) | Pure model space; fret 12 at L/2 (SC-011). |
| `StringColours` | frozen map string number -> colour | Six-string palette of FR-067 low to high (red, yellow, light blue, orange, green, violet); other counts: distinct colours of the same family keeping red low and violet high, frozen for the run. Each string also has a lit flag per frame (FR-068): true only for the strings of the active event. |
| `ArcadeRenderEvent` | `eventIndex`, `kind` (NOTE/CHORD/AUTO/UNSUPPORTED), `onsetNanos` (from `FollowTimeline.targetNanos`), `positions[]` (string, fret), `chordName?`, `stroke`, `fretSpan` | Built from `PracticeEvent` + `FollowTimeline` only (no second score parser). `MULTI_NOTE` and `AUTO_PASS` are shown as what they are. |
| `CameraTrack` | keyframes `(timeNanos, centreFret, zoom)`; `at(t)` | R4; built once per run; continuous; pure. |
| `IsometricProjection` | `project(modelX, modelY, depth) -> (screenX, screenY, scale)` for a viewport | One affine isometric transform plus depth scaling; the hit line is a fixed depth. |
| `FretArrivalLaneMap` | camera/viewport snapshot; painted top-edge fret-wire x positions; one `(horizonStart, cellTopEndpoint)` lane per visible fret plus the pre-nut lane | Built once from the same geometry sent to the painter. For fretted cell `n`, endpoint is `((wireX[n-1] + wireX[n]) / 2, neckTopY)`. Events select by fret only; string changes colour and the separate neck target, never lane geometry. |
| `ChordCartouche` | `strings` (count, vertical, low to high), `firstFret` window, `dots[]` (string, fret, colour, open/dead flags), `name?`, `stroke`, minimum screen size | visual-design.md; name only from non-blank `TGChord.getName()`; dead strings only when explicit. |
| `ArcadeScene` | list of drawable shapes (polygons, circles, text) for `(t, viewport, run state)` | Pure function; deterministic; the unit of the structural capture test. It draws the visual set only (below). |
| `VisualSet` | every event that is not completed and due within the look-ahead of five seconds, whatever their number; the active event always in | Computed inside `ArcadeScene` from the clock, the run and the event identities alone (no state between frames). Every event due later is absent: no primitive carries its identity. A completed event is not a member; only its verdict badge is drawn, for the feedback hold. |

## Monitoring side (package `audio.engine.monitor`, increment E, only if the spike allows)

`MonitoringPath` (input -> output, gain, balance), `LatencyBudget` (named terms of the accounting), `LatencyMeasurement` (trials, median, p95, maximum, method), `MonitoringGate` (22 ms rule on the worst credible value). Defined by the spike; not designed further here.
