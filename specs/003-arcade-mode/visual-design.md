# Arcade visual reconstruction contract

**Status**: approved product direction, 2026-10-07  
**Scope**: turn a selected TuxGuitar track and the tempo-following practice timeline into the approved isometric Arcade view.  
**Authority**: this file explains how to reproduce the approved visuals. `spec.md` remains authoritative for product behaviour; spec 002 remains authoritative for recognition and tempo judgement.

## Approved references

### Notes and neck

![Isometric neck with one stable colour per string](assets/arcade-isometric-string-colours-concept.png)

### Chords

![Chord timing bar with attached traditional diagram](assets/arcade-chord-timing-cartouche-concept.png)

These are composition references, not pixel templates. Generated fret widths, marker positions, text spacing and chord-grid details MUST be replaced by deterministic geometry from the score and instrument model.

## Non-negotiable visual grammar

1. The real instrument neck occupies the foreground; its strings run horizontally.
2. Musical events come from the depth field toward the player, perpendicular to the strings.
3. The fixed hit line is the written onset. Animation never moves the musical instant.
4. A single note is a string-coloured marker and trajectory resolving to one expected string/fret intersection.
5. A chord is **not** a collection of independent flying notes. It is one neutral timing bar with one attached chord cartouche.
6. The timing bar answers **when**. The cartouche answers **what**. The verdict badge answers **what was heard**.
7. Expected string/fret positions come from the tablature and are instructions only. Audio feedback verifies pitch and timing, never physical string, fret or fingering.
8. Animation is derived from the clock on every frame, not accumulated frame by frame; pause, seek and tempo changes therefore cannot cause drift.

## Existing source model to reuse

Do not traverse a `TGSong` independently inside the renderer. The practice module already converts the selected score track into playback order:

- `PracticeSequenceBuilder` in `desktop/TuxGuitar-practice/src/app/tuxguitar/app/tools/custom/practice/model/PracticeSequenceBuilder.java` traverses repeats and alternate endings through `MidiRepeatController`.
- `PracticeSequence` is the ordered sequence consumed by practice.
- `PracticeEvent` is the immutable display and judgement event. Its string/fret values are already documented as tablature instructions, not audio-verified positions.
- The Phase 9 `FollowTimeline` from spec 002 supplies the final event onset after tempo map, repeats, alternate endings and selected speed. Arcade MUST consume that timeline rather than duplicate its timing calculation.

The renderer may introduce an immutable UI-only projection such as `ArcadeRenderEvent`, but it MUST be built from `PracticeEvent` plus `FollowTimeline`; it MUST NOT become another score parser.

## Mapping a TuxGuitar track to the neck

| Visual datum | Authoritative source | Rule |
|---|---|---|
| String count | `TGTrack.stringCount()` / `TGTrack.getStrings()` | Render the selected track's actual count. Do not assume six strings. |
| Open-string pitch | `TGTrack.getString(number).getValue()` | String numbers are 1-based; current practice code documents string 1 as the highest string. |
| Track pitch offset | `TGTrack.getOffset()` | Apply through the same tuning model as practice; do not alter the displayed fret number. |
| Single-note string/fret | `PracticeEvent.getStringNumber()`, `getFret()` | Use directly for `EXPECTED_NOTE`. |
| Chord positions | `PracticeEvent.getChordNotes()` and each `ChordNote.getStringNumber()/getFret()/getMidi()` | These are the new written pitched notes of `EXPECTED_CHORD`. |
| Required sounding pitches | `PracticeEvent.getRequiredPitches()` | Feed chord-level labels and result detail; duplicates are already collapsed. |
| Still-sounding tolerated pitches | `PracticeEvent.getToleratedPitches()` | Never draw them as newly attacked chord positions. |
| Repeated/duplicate pitches | `PracticeEvent.hasDuplicatePitches()` | Never claim every written duplicate was separately heard. |
| Beat metadata | `PracticeEvent.getBeat()` | Optional chord name/diagram and written stroke live here. |
| Event identity/order | `getSequenceIndex()`, `getPlayIndex()`, `getMeasureIndex()` | Keep stable across repeats and UI updates. |

`TrackTuning` currently accepts exactly six strings. That is an existing limitation of the wait-mode implementation, not permission for Arcade to assume six. The Arcade plan MUST either generalise the shared tuning representation or introduce a renderer input that supports the actual `TGTrack` string count, including a normal four-string bass.

### Physical display order

TuxGuitar numbers string 1 as the highest string. The approved neck draws the low string at the bottom and high string at the top. Therefore, for `N` strings:

- screen row from bottom to top uses TuxGuitar string numbers `N, N-1, ... 1`;
- a traditional chord diagram from left to right also uses `N, N-1, ... 1`;
- all conversion goes through one tested mapping function; do not reverse strings separately in each widget.

## Neck geometry

Let `L` be the model scale length and `n` a fret number. The distance from the nut is:

`x(n) = L * (1 - 2^(-n/12))`

Fret 12 is therefore at `L / 2`. Compute geometry in model space and project it through the isometric camera. Never copy fret widths or dot positions from the generated images.

Default position markers may use single dots at 3, 5, 7, 9, 15, 17, 19 and 21 and double dots at 12 and 24. This is a visual-model convention, not a universal musical rule; a future instrument/theme model may override it.

## Stable colour map

Assign one colour to each physical string when the Arcade run starts and keep it for the entire run. For the approved six-string palette, low to high:

1. low E: cobalt blue;
2. A: cyan;
3. D: teal;
4. G: gold;
5. B: violet;
6. high e: magenta.

The exact chain is:

`string tint = string label = note marker = trajectory = target ring = corresponding dot in the chord diagram`

Verdict colours are separate and live only in verdict badges. Position, labels and shapes MUST remain sufficient in monochrome. For another string count, choose distinct colours from the accessible palette in physical low-to-high order and freeze the resulting map for the run.

## Single-note event

For `PracticeEvent.Kind.EXPECTED_NOTE`:

1. read the written string and fret from the event;
2. ensure that fret is inside the camera's anticipated visible range;
3. spawn the marker at the event's look-ahead boundary;
4. use the destination string's stable colour for marker, trajectory and target ring;
5. project depth from event time so the marker reaches the hit line exactly at the event onset;
6. apply the tempo judgement as a separate shape/text badge without changing the marker's string colour.

## Chord event: timing bar plus cartouche

Use this representation only for `PracticeEvent.Kind.EXPECTED_CHORD`. `MULTI_NOTE` and `AUTO_PASS` need their own honest unsupported/automatic presentation; do not disguise them as recognised chords.

### Timing bar

- Draw one neutral, high-contrast bar perpendicular to the approach direction.
- Attach the cartouche to its centre with a short stem. They share one transform and cannot drift apart.
- The leading edge reaches the fixed hit line at the timeline onset.
- A very small translucent depth thickness may show the written stroke spread. It is not a second timing target.
- The bar carries no per-string colours and no pitches; it encodes time only.

### Traditional cartouche

- Keep it camera-facing (billboarded), even though the bar and world are isometric.
- Draw exactly the track's number of **vertical** string lines, ordered low-to-high from left to right.
- Draw horizontal fret lines and a side label such as `5fr` when the window starts above fret 1.
- Place one dot per `PracticeEvent.ChordNote` at its written string/fret, using that string's colour.
- Preserve the cartouche at a minimum readable screen size; perspective may scale it, but it must not become illegible.
- Add a ghost outline of the same expected positions on the real neck when the event enters the near/readable state.

### Name and diagram metadata

`TGBeat.isChordBeat()` and `TGBeat.getChord()` provide an optional `TGChord` with `getName()`, `getFirstFret()`, `countStrings()` and per-string fret values. Treat it as notation metadata, not as the event source:

1. the actual event positions always come from `PracticeEvent.getChordNotes()`;
2. use `TGChord.getName()` only when non-blank; do not invent a chord name from a pitch set in the renderer;
3. use its first-fret/window hint only when compatible with the event positions and string count;
4. if it is absent or inconsistent, generate the grid from the event positions and label the cartouche `Accord` rather than guessing a name;
5. show `X` only for an explicitly dead/muted score note or compatible chord-diagram metadata; an omitted string is otherwise blank, not automatically muted;
6. show `O` for an explicitly played open string (`fret == 0`);
7. never invent finger numbers because the current event model does not provide them.

### Written strum

`TGBeat.getStroke()` returns `TGStroke`:

- `STROKE_DOWN` or `STROKE_UP` selects the arrow direction;
- `getIncrementTime(beat)` is the player-compatible per-string timing increment;
- the visual sweep duration may use that increment times the number of gaps between attacked strings;
- `STROKE_NONE` shows no directional arrow and does not invent a downstroke.

The visual stroke spread describes the written score. Recognition tolerance remains owned by spec 002 and MUST NOT be silently derived from the cartouche thickness.

## Chord verdict

Consume the existing chord-level `ChordFeedback` rather than assigning verdicts to displayed strings:

| `ChordFeedback.Kind` | Visual result |
|---|---|
| `WAITING` | Neutral cartouche; keep listening inside the chord window. |
| `UNCERTAIN` | Amber question/uncertain badge; never show a definite mistake. |
| `STRUM_AGAIN` | Neutral retry badge with localised “grattez à nouveau”. |
| `INCOMPLETE` | Chord-level incomplete badge and missing *pitch names* from `getMissing()`. |
| `CORRECT` | One chord-level check badge. |

`getUnverifiable()` identifies duplicate or harmonically inseparable pitches. Present them as not separately verifiable; never turn them into green checks on specific strings. Expected positions remain on screen as tablature guidance throughout the verdict.

## Timeline-to-depth transform

For each frame, derive visual progress from the same clock used by follow mode:

```text
timeUntil = eventOnsetNanos - clockNowNanos
linearProgress = clamp(1 - timeUntil / lookAheadNanos, 0, 1)
```

Map `linearProgress` through a deterministic visual easing/projection, with these invariants:

- progress is exactly 1 at the written onset;
- pause freezes because the underlying practice clock freezes;
- seek/restart recomputes position immediately instead of accumulating error;
- tempo changes affect `eventOnsetNanos` through `FollowTimeline`, not an independent render tempo;
- judgement latency cannot move the bar or cartouche; it only delays the verdict badge.

Recommended render states:

1. `HIDDEN`: outside look-ahead.
2. `DISTANT`: bar and compact cartouche visible.
3. `READABLE`: cartouche held above its minimum size; neck ghost appears.
4. `ARMED`: target frets are visible with margin; event approaches the hit line.
5. `IMPACT`: bar leading edge coincides with the hit line.
6. `FEEDBACK`: cartouche stays briefly while the chord-level verdict appears.
7. `EXPIRED`: event fades without blocking later events.

## Anticipatory neck camera

Before an event enters `DISTANT`, inspect the current event plus the configured look-ahead events:

1. collect every written fret, including every `ChordNote`;
2. compute minimum and maximum required frets plus a visual margin;
3. choose the camera centre and, only when necessary, bounded zoom that keeps the current and future targets readable;
4. start a continuous critically damped/eased pan early enough to settle before event spawn;
5. never teleport, oscillate between adjacent future targets or hide an event already on screen.

If an unusually wide chord or fast jump cannot fit at normal zoom, zoom out within the readability bound before spawning it. The product invariant is absolute: no visible note or chord may target an off-screen fret.

## Layout contract

- Neck: lower foreground, with string labels at its low/high edge.
- Impact line: fixed immediately before the neck.
- Approach field: upper two-thirds, with unobstructed depth cues.
- Note markers: in world space.
- Chord timing bar: in world space, perpendicular to travel.
- Chord cartouche: anchored to the bar but camera-facing.
- HUD: speed and streak; never the numerical score during play.
- Verdict badge: beside or above the impacted event, never covering the next event.

## Rendering budget and adaptive quality

The reference images define the visual grammar, not a pixel-resolution target. The renderer follows this order of importance at all times:

1. uninterrupted capture, recognition, MIDI playback and optional live monitoring;
2. correct musical clock and stable frame pacing;
3. legibility of the hit line, current and upcoming notes/chords, string/fret targets, camera look-ahead, essential HUD and verdicts;
4. decorative polish.

Use deterministic quality tiers so tests and diagnostics can name the active state:

| Tier | Preserved | Allowed reduction |
|---|---|---|
| `FULL` | all gameplay and accessibility information | approved glow, trails, soft shadows and highest antialias quality |
| `BALANCED` | identical gameplay geometry and overlays | fewer particles/trails, simpler glow/shadow, lower antialias cost |
| `PERFORMANCE` | identical hit timing, targets, chord cartouche, camera and verdicts | minimalist materials, no decorative effects, reduced internal 3D-field resolution/detail, then upscale |

Quality may fall quickly when measured frame cost threatens the budget and recover slowly with hysteresis so it does not oscillate. It is driven by recent render durations, never by changing the musical clock. A frame that cannot be presented on time is skipped; it is never queued, and the next paint rebuilds from the current clock. Text, controls, the hit line, target rings, chord diagrams and verdict symbols should remain at native UI resolution even when the world field is rendered lower on a high-density display.

The scene model is backend-neutral. The first implementation uses the existing toolkit renderer. An accelerated renderer may replace only the replay layer after an Apple Silicon benchmark demonstrates a material complete-workload benefit; the same scene and tests must also run through the software/toolkit fallback. No render preparation, paint call, asset upload or quality decision may execute on or hold a lock needed by capture, recognition, MIDI playback or live monitoring. Geometry and reusable assets are prepared or cached outside the steady-state audio path.

## Required deterministic tests

1. **String mapping**: 4-, 6- and 7-string tracks map TuxGuitar string 1 to the high-string side and never swap colours.
2. **Fret geometry**: fret 12 is half scale length; every fret matches the formula in model space.
3. **Single-note projection**: every event reaches the hit line exactly at its timeline onset across tempo changes and selected speeds.
4. **A5 fixture**: low-E 5, A 7 and D 7 produce one bar/cartouche, a `5fr` grid window and three correctly coloured dots.
5. **Open chord fixture**: fret 0 becomes `O`; explicit dead strings become `X`; omitted strings remain blank without metadata.
6. **Metadata fallback**: missing, blank or inconsistent `TGChord` metadata never changes event positions and never invents a chord name.
7. **Stroke**: up, down and none produce the correct arrow/sweep behaviour using `TGStroke`; none produces no arrow.
8. **Chord feedback**: all five `ChordFeedback.Kind` values map to chord-level badges; no test produces a per-string “heard” claim.
9. **Duplicate pitches**: `hasDuplicatePitches()` / `getUnverifiable()` remain explicitly unverifiable.
10. **Camera look-ahead**: single notes, wide chords and large fret jumps are already fully visible before spawn; camera output is continuous and deterministic.
11. **Accessibility**: a monochrome render still identifies string, fret, chord timing and verdict.
12. **Resize/performance**: resizes preserve minimum cartouche size; rendering does not block playback, judgement or the real-time audio path.
13. **Quality tiers**: every tier preserves the same hit times, visible playable events, target string/fret, chord grouping, camera window, accessibility cues and verdict semantics; only the documented decoration/detail changes.
14. **Frame replacement**: an intentionally delayed paint never creates a backlog; the next scene is built from the newest clock value and reaches the same written onset.
15. **Backend parity**: the selected accelerated path (if any) and the forced software/toolkit fallback produce the same structural scene for fixed fixtures.
16. **Concurrent stress**: dense notes, chord cartouches and camera movement run with MIDI and recognition, plus monitoring when available; report frame percentiles and prove no audio drop, capture overrun or judgement-digest change.

## Do not infer

- Do not infer the physical string played from audio.
- Do not infer a chord name when `TGChord.getName()` is absent.
- Do not infer muted strings merely because no note is written on them.
- Do not infer a stroke direction when `TGStroke.STROKE_NONE` is written.
- Do not use generated-image fret spacing, marker placement or diagram mistakes as data.
- Do not let UI animation create a second tempo, repeat traversal or tolerance policy.
