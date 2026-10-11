# Arcade visual reconstruction contract

**Status**: approved product direction, 2026-10-07  
**Scope**: turn a selected TuxGuitar track and the tempo-following practice timeline into the approved isometric Arcade view.  
**Authority**: this file explains how to reproduce the approved visuals. `spec.md` remains authoritative for product behaviour; spec 002 remains authoritative for recognition and tempo judgement.  
**Vocabulary** (defined once, in `spec.md` Key Entities and FR-060 to FR-066; used here with exactly that meaning): the *active event* is the earliest playable event that is not completed; a *completed* event has its judgement; the *visual set* is every event that is not completed and due within the look-ahead of five seconds; an event outside it is *absent* from the picture; a *fret lane* is the predefined approach line of one fret cell. There is no other visibility class: no preview, compact or ghost form of an event exists.

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
4. A single note of the visual set is a string-coloured marker travelling on the one predefined lane of its written fret. That lane ends at the midpoint of the fret cell's upper edge, at the top edge of the neck; the separate opaque neck target of the active event identifies the exact string/fret intersection.
5. A chord is **not** a collection of independent flying notes. It is one neutral timing bar with one attached chord cartouche (the bar alone while another chord of the set has the cartouche).
6. The timing bar answers **when**. The cartouche answers **what**. The verdict badge answers **what was heard**.
7. Expected string/fret positions come from the tablature and are instructions only. Audio feedback verifies pitch and timing, never physical string, fret or fingering.
8. Animation is derived from the clock on every frame, not accumulated frame by frame; pause, seek and tempo changes therefore cannot cause drift.
9. The picture of the approach holds the visual set and nothing else; the physical neck carries target disks for one event identity only, the active event (one note, or the complete chord). Camera look-ahead never creates targets for other events on the neck, and never admits an event to the set.

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

TuxGuitar numbers string 1 as the highest string. Since the owner's reference of 2026-10-11 the neck draws the **low string at the top and the high string (violet) at the bottom** (FR-067). Therefore, for `N` strings:

- screen row from top to bottom uses TuxGuitar string numbers `N, N-1, ... 1`;
- a traditional chord diagram from left to right also uses `N, N-1, ... 1`;
- all conversion goes through one tested mapping function; do not reverse strings separately in each widget.

## Neck geometry

Let `L` be the model scale length and `n` a fret number. The distance from the nut is:

`x(n) = L * (1 - 2^(-n/12))`

Fret 12 is therefore at `L / 2`. Compute geometry in model space and project it through the isometric camera. Never copy fret widths or dot positions from the generated images.

Default position markers may use single dots at 3, 5, 7, 9, 15, 17, 19 and 21 and double dots at 12 and 24. This is a visual-model convention, not a universal musical rule; a future instrument/theme model may override it.

## Stable colour map

Assign one colour to each physical string when the Arcade run starts and keep it for the entire run. For the approved six-string palette, low to high:

1. low E: red `#E5453B`;
2. A: yellow `#F5E642` (glow core `#F2F2A2`);
3. D: light blue `#5CB8F0` (core `#A2D7F0`);
4. G: orange `#F58A2E` (core `#F5AE68`);
5. B: green `#3DE0A0` (core `#A0E1D1`);
6. high e: violet `#9B59D0` (dim line `#4C3C60`).

The values come from the owner's visual reference of 2026-10-11 (sampled from the image; the red of the lowest string is by the convention of guitar games). **Lit string (FR-068):** a string is drawn dim (its colour at low brightness, no glow) while no event of the active moment targets it, and bright with a glow in its colour along its length while the active note or chord targets it; the other strings stay dim at the same instant.

The exact chain is:

`string tint = string label = note marker = lane of an note of the set = target disk = corresponding dot in the chord diagram`

Verdict colours are separate and live only in verdict badges. Position, labels and shapes MUST remain sufficient in monochrome. For another string count, choose distinct colours from the accessible palette in physical low-to-high order and freeze the resulting map for the run.

## Single-note event

For `PracticeEvent.Kind.EXPECTED_NOTE`:

1. read the written string and fret from the event;
2. ensure that fret is inside the camera's anticipated visible range;
3. spawn the marker at the event's look-ahead boundary;
4. use the destination string's stable colour for the marker, its lane and the target disk;
5. select the predefined lane indexed by the written fret and project depth from event time so the marker reaches that lane's cell-top endpoint exactly at the event onset;
6. apply the tempo judgement as a separate shape/text badge without changing the marker's string colour.

The physical-neck target is emitted only when this event is the active event. It is not a faint ring: it is a large filled disk, fully opaque, centred exactly on the destination string/fret intersection, in that string's stable colour, with a contrasting 2–4 logical-pixel outline. Its filled-core diameter is `clamp(24 logical px, 60% of the rendered inter-string spacing, 42 logical px)` and scales with UI density. An optional halo or pulse may surround it, but the opaque core always remains. The other notes of the set continue travelling on their lanes with no disk, ring, ghost or fret highlight on the neck.

### Predefined fret-arrival lanes

The approach field uses a small fixed vocabulary: one lane per visible fret cell, not one computed trajectory per note. For the fret cell `n` bounded by the two wires actually painted at `paintedWireX(n-1)` and `paintedWireX(n)`, define:

```text
arrivalX(n) = (paintedWireX(n-1) + paintedWireX(n)) / 2
arrivalY    = paintedNeckTopY
```

The lane for fret `n` joins its documented horizon origin to this cell-top endpoint. Every single note on fret `n` selects exactly that lane, whatever its destination string. String identity changes the marker colour, target disk and labels, never the lane geometry. Fret 0 has one explicit pre-nut lane.

The marker centre stays on the selected lane throughout the approach and equals the endpoint at onset. The visible approach line is clipped at `paintedNeckTopY`: it never continues, bends or adds a connector across the physical neck. The opaque target disk remains separately centred on the tablature's string/fret position inside the neck. Camera pan, zoom and resize rebuild all visible lane endpoints from the same painted fret-boundary snapshot before any marker is placed; no event retains a stale or private trajectory.

## Chord event: timing bar plus cartouche

Use this representation only for `PracticeEvent.Kind.EXPECTED_CHORD`. `MULTI_NOTE` and `AUTO_PASS` need their own honest unsupported/automatic presentation; do not disguise them as recognised chords.

### Timing bar

- Draw one neutral, high-contrast bar perpendicular to the approach direction.
- Attach the cartouche to its centre with a short stem. They share one transform and cannot drift apart.
- Place every chord bar on the same documented chord axis, centred across the playable approach lane. A chord's fret span affects the camera look-ahead and, while the chord is active, its target disks, never the bar's lateral placement.
- The leading edge reaches the fixed hit line at the timeline onset.
- A very small translucent depth thickness may show the written stroke spread. It is not a second timing target.
- The bar carries no per-string colours and no pitches; it encodes time only.

### Traditional cartouche

- Keep it camera-facing (billboarded), even though the bar and world are isometric.
- Put the chord's useful musical identity in a stable header on the cartouche. When `TGChord.getName()` is non-blank, show that exact written name. Otherwise show the deterministic conventional name resolved before rendering from the written expected pitches and bass/inversion. Only an unsupported pitch set uses a localized explicit note list (`Notes: A–C–F♯`); never show the bare generic word `Chord` / `Accord` for a pitched event. Keep the header readable for as long as the chord is in the visual set, in every quality tier. It must not be confused with a fret number or verdict and must not cover the following event.
- Draw exactly the track's number of **vertical** string lines, ordered low-to-high from left to right.
- Draw horizontal fret lines and a side label such as `5fr` when the window starts above fret 1.
- Place one dot per `PracticeEvent.ChordNote` at its written string/fret, using that string's colour.
- Preserve the cartouche at a minimum readable screen size; perspective may scale it, but it must not become illegible.
- While this chord is the active event, draw its target disks (FR-065) at the same expected positions on the real neck: all and only its simultaneous positions. No other event has a target.

### Several chords in the visual set

Chord diagrams are disclosed serially, never scattered around the highway. Only the chords that belong to the visual set are drawn at all:

1. Of the chords of the set, the active chord, or otherwise the nearest one that has reached the readable state, is the sole expanded chord identity.
2. Give it one full cartouche, centred on its timing bar and clamped wholly inside the viewport.
3. Draw every other chord of the set as a timing bar without diagram on the same centre axis. A short chord-name chip is optional only when its bounds do not collide; it is not a second full cartouche.
4. When the expanded chord is completed, promote the next chord of the set in onset order in one scene update. Never show two expanded diagrams in the same frame.
5. Keep the bar's depth and impact derived from onset time. Promotion changes disclosure only; it cannot move the bar in time.
6. Use chord fret positions only for the anticipatory camera and, while the chord is active, its target disks. Never use their mean/min/max fret to scatter the timing bar left or right.

### Name and diagram metadata

`TGBeat.isChordBeat()` and `TGBeat.getChord()` provide an optional `TGChord` with `getName()`, `getFirstFret()`, `countStrings()` and per-string fret values. Treat it as notation metadata, not as the event source:

1. the actual event positions always come from `PracticeEvent.getChordNotes()`;
2. use `TGChord.getName()` exactly when non-blank; otherwise consume the deterministic name already derived by the pure chord-name resolver from written pitches—never perform recognition inside the renderer or from captured audio;
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
2. `DISTANT`: the bar and the (small) cartouche of a chord of the visual set are visible.
3. `READABLE`: cartouche held above its minimum size.
4. `ARMED`: target frets are visible with margin; event approaches the hit line.
5. `IMPACT`: bar leading edge coincides with the hit line.
6. `FEEDBACK`: only for an event that is completed: its marker, lane, bar, cartouche and target are gone; its verdict badge stays for the feedback hold.
7. `EXPIRED`: event fades without blocking later events.

## Anticipatory neck camera

Before an event enters the picture, inspect the active event plus the configured look-ahead events (the camera may look further ahead than the visual set draws):

1. collect every written fret, including every `ChordNote`;
2. compute minimum and maximum required frets plus a visual margin;
3. choose the camera centre and, only when necessary, bounded zoom that keeps the current and future targets readable;
4. start a continuous critically damped/eased pan early enough to settle before event spawn;
5. never teleport, oscillate between adjacent future targets or hide an event already on screen.

If an unusually wide chord or fast jump cannot fit at normal zoom, zoom out within the readability bound before spawning it. The product invariant is absolute: no visible note or chord may target an off-screen fret.

Camera anticipation and neck targeting are deliberately separate. The camera may inspect several future events and pan toward their fret region before they become active, but this MUST NOT draw their targets, chord ghosts or fret highlights, and MUST NOT admit them to the visual set. The active event is the earliest playable event that is not completed. During a chord it is the whole chord, never one member. When it completes or expires, remove its complete target group and install the next event's group in one scene update; no frame may mix both event identities.

For an active chord, draw one opaque filled target disk per written string/fret position, using each destination string's colour and the same sizing/outline rule as a single note. Those disks form one atomic group. Feedback may add a check, cross or chord-level badge, but it must not make the target cores translucent or visually disappear. Quality reduction may remove halos, shadows and pulses first; it must never reduce the target core's opacity, size, centre accuracy or contrast.

## The visual set

At every viewport the picture holds the **visual set** (`spec.md` FR-062) and nothing else:

1. The set is every event that is not completed and due within the look-ahead of **five seconds**: a note is given the same time to be seen, found and played, whatever the number of events (one or a hundred). A chord counts as one event, an automatically passed event as one.
2. A note enters at the horizon exactly five seconds before its onset, at the far end of its fret lane, and goes down the whole lane to the top edge of the neck in those five seconds, in perspective: the last seconds take about half of the lane, the first ones little. The horizon is high in the picture (about 4% of its height), so that the way is long.
3. The marker of a note is always drawn. Its fret number is drawn only where it does not overlap a label already placed: the nearest notes are labelled first, the active event always. The same for the word of an automatic event. Labels, chord names, diagrams and verdict badges never overlap each other and are never clipped.
4. Every event due later is absent: no marker, halo, trail, lane in its string colour, timing bar, chip, diagram, label or target. In particular, never create a cloud of dots detached from the lanes, and never keep a compact or preview form of an event. It enters at the horizon when it becomes due.
5. A completed event is not in the set: only its verdict badge stays, near the impact point, for the feedback hold.
6. Labels and cartouches use collision bounds before paint. They may be offset within a bounded annotation lane, but their marker's musical trajectory and impact time do not move.

The set is separate from quality tiers: `FULL` may decorate its members, while `PERFORMANCE` removes decoration, but neither tier may draw another event. The lanes of the fret cells are also drawn faintly as depth cues; they belong to no event and are not previews.

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
3. legibility of the hit line, the events of the visual set, the string/fret targets, camera look-ahead, essential HUD and verdicts;
4. decorative polish.

Use deterministic quality tiers so tests and diagnostics can name the active state:

| Tier | Preserved | Allowed reduction |
|---|---|---|
| `FULL` | all gameplay and accessibility information | approved glow, trails, soft shadows and highest antialias quality |
| `BALANCED` | identical gameplay geometry and overlays | fewer particles/trails, simpler glow/shadow, lower antialias cost |
| `PERFORMANCE` | identical hit timing, targets, chord cartouche, camera and verdicts | minimalist materials, no decorative effects, reduced internal 3D-field resolution/detail, then upscale |

Quality may fall quickly when measured frame cost threatens the budget and recover slowly with hysteresis so it does not oscillate. It is driven by recent render durations, never by changing the musical clock. A frame that cannot be presented on time is skipped; it is never queued, and the next paint rebuilds from the current clock. Text, controls, the hit line, target disks, chord diagrams and verdict symbols should remain at native UI resolution even when the world field is rendered lower on a high-density display.

The scene model is backend-neutral. The first implementation uses the existing toolkit renderer. An accelerated renderer may replace only the replay layer after an Apple Silicon benchmark demonstrates a material complete-workload benefit; the same scene and tests must also run through the software/toolkit fallback. No render preparation, paint call, asset upload or quality decision may execute on or hold a lock needed by capture, recognition, MIDI playback or live monitoring. Geometry and reusable assets are prepared or cached outside the steady-state audio path.

## Required deterministic tests

1. **String mapping**: 4-, 6- and 7-string tracks map TuxGuitar string 1 to the high-string side and never swap colours.
2. **Fret geometry**: fret 12 is half scale length; every fret matches the formula in model space.
3. **Single-note projection**: every event reaches the hit line exactly at its timeline onset across tempo changes and selected speeds.
4. **A5 fixture**: low-E 5, A 7 and D 7 produce one bar/cartouche, a `5fr` grid window and three correctly coloured dots.
5. **Open chord fixture**: fret 0 becomes `O`; explicit dead strings become `X`; omitted strings remain blank without metadata.
6. **Name resolution**: missing, blank or inconsistent `TGChord` metadata never changes event positions; common written pitch sets receive the deterministic conventional name, and unsupported sets receive an explicit localized note list rather than a fabricated name or bare “Chord”.
7. **Stroke**: up, down and none produce the correct arrow/sweep behaviour using `TGStroke`; none produces no arrow.
8. **Chord feedback**: all five `ChordFeedback.Kind` values map to chord-level badges; no test produces a per-string “heard” claim.
9. **Duplicate pitches**: `hasDuplicatePitches()` / `getUnverifiable()` remain explicitly unverifiable.
10. **Camera look-ahead**: single notes, wide chords and large fret jumps are already fully visible before spawn; camera output is continuous and deterministic.
11. **Accessibility**: a monochrome render still identifies string, fret, chord timing and verdict.
12. **Resize/performance**: resizes preserve minimum cartouche size; rendering does not block playback, judgement or the real-time audio path.
13. **Quality tiers**: every tier preserves the same hit times, the same visual set, target string/fret, chord grouping, camera window, accessibility cues and verdict semantics; only the documented decoration/detail changes.
14. **Frame replacement**: an intentionally delayed paint never creates a backlog; the next scene is built from the newest clock value and reaches the same written onset.
15. **Backend parity**: the selected accelerated path (if any) and the forced software/toolkit fallback produce the same structural scene for fixed fixtures.
16. **Concurrent stress**: dense notes, chord cartouches and camera movement run with MIDI and recognition, plus monitoring when available; report frame percentiles and prove no audio drop, capture overrun or judgement-digest change.
17. **Single active neck target**: with five or more events in the look-ahead, the neck contains targets for exactly one event identity, the active event; a chord contributes all and only its simultaneous positions; advancing never produces a mixed old/new target frame.
18. **Chord-name visibility**: named chord fixtures show the exact score name; unnamed A5, open major/minor, seventh, suspended and inversion fixtures show their deterministic derived names; unsupported sets show the pitch-list fallback; single notes have no chord header. Repeat in all quality tiers and ensure the header never obscures the next event.
19. **Visual set**: fixtures with 8, 16 and 32 events in the look-ahead, notes only and notes mixed with chords, at several window sizes, quality tiers, instants and camera positions, paint exactly the events due within five seconds, every note with its marker and its whole lane, and no primitive of any kind for every other event; a note enters at the horizon five seconds before its onset; labels never overlap or clip. A test fails if a compact or preview form of an event, a limit on the number of visible notes, or another look-ahead appears. The judgement digest is identical to the one without the label budget.
20. **Chord lane**: fixtures with 2, 4 and 8 chords in the look-ahead contain exactly one expanded diagram, the other chords of the set as timing bars and nothing for chords due later, every full cartouche is inside the viewport, every chord timing bar shares the same centre axis, and promotion follows onset order without a two-diagram transition frame.
21. **External real-score corpus**: at least 60 deterministically selected GP3/GP4 track cases exercise sparse, mixed, chord-heavy, repeated/dense-chord, fast, large-jump, bass/alternate-string and multi-track/repeat material. Apply tests 3, 8, 10 and 13-23 at representative frames in `FULL` and `PERFORMANCE`; generate at least twelve local, uncommitted screenshot pairs for owner review.
22. **Opaque active neck target**: structural tests inspect every active neck-target primitive in all quality tiers and require a filled core with alpha 1.0, the exact FR-065 diameter/centre and a contrasting outline. Single-note scenes contain one disk; chord scenes contain all and only the written chord positions; no other event contains any. Light, dark, monochrome, resize, high-density and fallback screenshots keep the target immediately visible.
23. **Predefined fret-lane registration**: for fret 0 and every visible cell, sample the complete approach and assert that the marker remains on the lane within 0.5 logical pixel and reaches the exact midpoint of the cell's painted upper edge at onset. Notes on several strings at the same fret must reuse byte-for-byte identical lane geometry while keeping their string colours. Assert that no approach primitive enters the neck, no connector is emitted, and camera/resize/tier changes rebuild lanes from the current painted wires. Repeat on real-score screenshots.

## Do not infer

- Do not infer the physical string played from audio.
- Do not infer a chord name from captured/detected audio. When score metadata is absent, derive only from the written expected pitches through the tested resolver, outside the renderer.
- Do not infer muted strings merely because no note is written on them.
- Do not infer a stroke direction when `TGStroke.STROKE_NONE` is written.
- Do not use generated-image fret spacing, marker placement or diagram mistakes as data.
- Do not let UI animation create a second tempo, repeat traversal or tolerance policy.

## Open-string bar (FR-069, owner reference of 2026-10-11)

A single note at fret 0 is a **thin horizontal bar** in the colour of its string, as wide as the lanes, with a bright cap at its left end (the reference image shows a red line over the blue lanes with a white-blue cap). It comes from the horizon like every note, thickens with perspective and ends on the top edge of the neck; it carries the word *open* in the language of the interface; it has no disk and no fret number; at the onset its string is lit (FR-068). In a chord the open strings stay `O` in the cartouche and are lit with it. The explanatory text of the reference ("this horizontal line is an open note, pluck the string that matches the one shown") is a tutorial caption, not part of the Arcade picture; a first-run hint may reuse it later, it is not specified here.

## Rounded rectangles, thread, footprint and approach band (FR-070 to FR-072, owner reference of 2026-10-11)

Third reference image: a single fretted note on string 2, fret 2. The flying marker is a small solid rounded rectangle in the colour of the string (yellow in the image), raised above its lane; a thin vertical thread joins it to a small flat footprint on the lane, and the fret number (2) is written under the footprint. On the neck the target is a rounded rectangle that fills the cell of fret 2 on the yellow string. The numbers under the neck show the frets and the one of the active event is highlighted (orange). A translucent blue band lights the approach zone, the lane of the active fret being slightly lighter. The explanatory caption of the image is a tutorial text, not part of the picture.

## The chord panel (FR-074, owner references of 2026-10-11)

A chord is a translucent panel standing across the lane, with luminous cyan edges and a see-through body. Inside, the rectangles of its notes sit at their fret (horizontally, aligned with the frets of the neck) and their string (vertically, the lowest string at the top), in the string colours; the name of the chord is above the left corner, in a bold outlined type. The panels of the chords of the set come in perspective, each smaller and higher as it is farther, until the active one lands on the neck, where its string lines are lit and its note rectangles have their fret numbers. The numbers on the notes are the frets, not fingerings (Q23). This replaces the neutral timing bar and the diagram cartouche.

## Transparent neck (FR-075, owner reference of 2026-10-11)

In the references the neck has no opaque body: strings and fret wires float over the dark scene, with only a faint translucent tint under them. Everything that carries information (strings, wires, numbers, target rectangles) stays opaque.
