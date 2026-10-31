# Research: spec 019, the score fire and the Advanced tab

## R1. Where the score fire is painted

**Decision**: a second `UIPaintListener` added by the plugin to the canvas of the selected score control, `TGTabFolder.getInstance(context).findSelectedControl().getCanvas()`. That is the canvas `ScoreBoxes.canvas()` already reaches. The listener is added when a Wait or Follow run starts with the setting not Off. It is removed when the run stops, and when the selected control changes (it is checked at each tick).

**Rationale**: the toolkit calls listeners in order on the same painter: `UIPaintListenerManager.onPaint` copies its list and calls each listener with the same event, and `SWTPaintListenerManager.paintControl` wraps `e.gc` once. The score's own listener, `TGBufferedPainterListenerLocked`, is added in the `TGControl` constructor (line 96), so it comes first. Its `paintBufferLocked` ends with `painter.drawImage(buffer, 0, 0)`, and the plugin's listener then paints over that picture. The fire never enters the buffer:
- In Follow, the buffer is repainted incrementally: `TGControl.paintTablature` repaints the full tablature only when something changed, otherwise only the played beat. A fire drawn into the buffer would pile up.
- In Wait, the buffer is fully repainted at each paint.

**Alternatives considered**:
- Drawing inside `TGControl.paintTablature`: a core change, and the buffer problem above.
- A translucent window over the score (as the palette of spec 017 is a window): it behaves differently on each system, can catch the pointer, and its compositing differs (parity rule).
- A child canvas over the score: the toolkit has no transparent child canvas.

## R2. Animating without repainting the whole score at each frame (conditional core hook)

**Decision**:
- The timer redraws the score canvas at about 30 frames per second while the fire moves, and at no rate when it is still: reduced motion, pause, or level steady and no spark.
- In Follow, a redraw is cheap. The player is running, so `paintTablature` repaints only the played beat, and only when it changed.
- In Wait, `paintBufferLocked` calls `fillPaintBuffer()` on every paint, which repaints the whole visible tablature.
- Phase 1 measures that repaint on `tests/demo-arcade-morceau.tg` at 1280×800, 100 redraws. **If the median exceeds 3 ms or the 95th percentile exceeds 6 ms**, the plan adds a small, additive core hook:
  - `TGBufferedPainterLocked` gets `requestOverlayOnly()`, a one-shot flag. The next paint skips `fillPaintBuffer()` and draws the buffer as it is, if the buffer exists and has the canvas size.
  - `TGControl` gets `redrawOverlay()`, which sets the flag and calls `canvas.redraw()`.
  - `TGControl.redraw()` clears the flag, so any real change still repaints.
  - Nothing changes for upstream unless `redrawOverlay()` is called.
- The decision and the numbers are written in `baseline.txt` and below.

**Rationale**: the threshold keeps the score fire within its 4 ms share of a 33 ms frame, with room for the tablature's own redraws. The measurement comes first because Constitution II asks for a justified core change.

**Alternatives considered**:
- Always adding the hook: a core change without evidence.
- Lowering the frame rate to 10 per second in Wait: the fire would stutter, and the owner judges the look.

**Follow**: the reasoning above assumes the MIDI player is running during a Follow run (`MidiFollowPlayer`, `FollowPlayback`), so that `paintTablature` takes its incremental branch. T002 checks it: whether `MidiPlayer.isRunning()` is true during a Follow run, also with the backing muted. If it is not, Follow repaints the whole score like Wait, its repaint is measured too, and the same threshold decides the hook for both modes.

**Measured** (filled in Phase 1): _see `baseline.txt`_.

## R3. The streak, and what counts as right or wrong

**Decision**: `ScoreStreak` is a pure class fed from the snapshots the window already handles.

- **Wait** (`PracticeWindow.render(PracticeSnapshot)`):
  - a pass with `getLastPassedOutcome()` ACCEPTED is a right note (the streak goes up by one, and a burst is thrown);
  - SKIPPED is a mistake;
  - AUTO_PASSED is neutral (rests and notes passed by the rules of spec 016);
  - `getVerdict()` WRONG is a mistake, counted once per wrong verdict until the next pass.
  - New passes are detected by `getAcceptedCount()` and the last-passed identity, so the same snapshot rendered twice counts once.
- **Follow** (`PracticeWindow.renderFollow(FollowSession.Snapshot)`): each `results()` entry is counted once, when its `status()` first leaves PENDING, keyed by `eventIndex()`. MATCHED is right; early and late are MATCHED with an offset, so they are right too (spec assumption). WRONG_PITCH and MISSED are mistakes.
- The streak is reset when a run starts. On pause it holds its value.
- The streak is read only, never written back: the verdicts are untouched (FR-008).

**Rationale**: these are the same hook points `paletteGuard.show(...)` uses (`render` line ~798, `renderFollow` line ~1177), so nothing is added to the sessions.

**Alternatives considered**:
- A listener inside `PracticeSession` / `FollowSession`: it changes the sessions for a decoration.
- Reusing Arcade's `ArcadeRun.View.streak`: Arcade only.

## R4. The tuning: a catalogue whose defaults are today's constants

**Decision**:
- **`FireParameter`** (enum) lists every value that shapes a fire. For each one it gives:
  - a key, a kind (NUMBER, INTEGER or COLOUR) and a unit;
  - a minimum and a maximum;
  - the Arcade default, which is **the existing constant or literal itself**, referenced where it is a named constant: `FireColours.MOST`, `FireField.COLUMNS`, `FlameDrawing.CORNER_HEIGHT`, …;
  - the score default (data-model);
  - its message keys (label and tooltip).
- **`FireTuning`** is an immutable map from parameter to value, clamped to the range when built. It provides `ARCADE_DEFAULTS` and `SCORE_DEFAULTS`.
- The fire classes get overloads that take a tuning. Each old signature calls the new one with `ARCADE_DEFAULTS`, so every existing test compiles and passes unchanged.
- The literals inside `FireField.heat`/`noise` and `FlameDrawing.sparks` become reads of the tuning: ember 0.12 and 0.08, ramp 3, body 1.15, exponent 1.5, flicker 1.3 and 0.2, sway 0.6, 1.7 and 0.35, octave weights 0.5, 0.3 and 0.2, the grain size and the tongue aspect (the octave divisors 2, 1, 0.5 and 4, 2, 1), the exponent 2 of the corner-to-middle profile in `FlameDrawing.weight`, sparks 20, 5/2 and 12, start zone 0.25, rise 10 and 16, drift 10, colour fade 8.

**Fixed values, outside the tuning, with their reasons** (FR-015 and FR-016 exceptions, SC-009):
- `FlameDrawing.WIDEN`, `NEAR_TEXT` and `RAMP`: the reading guard (owner's answer).
- `FireField.SEED`, the `mix` hash constants, and the bit fields that draw each spark from the hash in `FlameDrawing.sparks` (`31L`, the shifts `>>> 1`, `17` and `33`, the masks `0xFFFF` and `& 1L`, the divisor `65536.0`): they keep the fire repeatable for the tests. They choose *which* random number a spark gets, never how the fire looks on average.
- The rest of the reading guard: the text-box estimates of `FlameDrawing.textBoxes` (0.66 of the font size per character, 0.6 of it above and below the baseline) and the smoothstep `3.0 − 2.0 · f` of `faded`. With `WIDEN`, `NEAR_TEXT` and `RAMP` they guarantee that no text is hidden (FR-016), so they stay fixed whatever the settings.
- `FlameDrawing.TONGUES`, `parts = full ? 3 : 2` and the `* 4` of the group code in `items`: they only number the groups of cells in the item `code`. It changes no pixel (the renderer does not read it) and keeps the reference scene stable.
- The coarser cell at BALANCED (×2) and the tier rules (`QualityController` PRESSURE, COMFORT and WINDOW): the quality tiers stay automatic (spec assumption).

**Rationale**:
- Using the constants as the defaults makes FR-017 and SC-010 true by construction.
- A test also proves it: the defaults rebuild `flame-reading.scene.txt` byte for byte.
- A second test fails if a constant changes without its default.

**Alternatives considered**:
- Replacing the constants: this changes the existing tests that read them.
- One tuning shared by both fires: rejected by the owner (clarification 2026-10-31).

## R5. Saving the settings

**Decision**:
- **`FireTuningStore`** uses the `Persistence` interface of `ArcadeChoices` (the `get`/`set` pattern of `FollowChoices.of`) on `TGConfigManager` module `tuxguitar-practice-fire`.
- Keys:
  - `arcade.<key>` and `score.<key>` hold the parameter values: a decimal number written with `Double.toString`, or a colour written as `#RRGGBB`;
  - `score.setting` holds OFF, DISCREET or FULL.
- Only the values that differ from the default are stored. *Default values* removes a group's keys (it writes empty values).
- A missing, unreadable or out-of-range value gives the default or the clamped value; the store never throws.
- The tuning is read when a run starts (FR-019: applied at the next run). The previews read the settings being edited.
- The Arcade *Stage flames* choice stays in `FollowSettings`, as today (unchanged scope).

**Rationale**: these are the existing patterns of the plugin. A module of its own keeps the recognition settings untouched.

**Alternatives considered**: saving the values in `FollowSettings`. It keeps everything in memory and is not saved (it is the source of spec 009's setting).

## R6. The Advanced tab and its previews

**Decision**:
- A tab `practice.tab.advanced`, created like the others with `tab(uiFactory, folder, key, layout)` in `PracticeWindow`, after *Passages*.
- It holds a `UIScrollBarPanel` with the two groups, *Arcade fire* and *Score fire*.
- Each group is laid out in sections: Look, Flames, Motion, Embers, Growth, Sparks, Colours.
- Rows follow the pattern of `RecognitionSettingsPanel`:
  - a label, a horizontal scale and a value label in the unit;
  - `setToolTipText` on the label and the scale, with the text, the unit and the default;
  - colours use `createColorChooser` and a swatch button.
- At the top of each group: the preview, a *Sparks* button, a growth slider (the streak for the score, the share of the maximum for Arcade) and *Default values*.
- The preview is a `UICanvas` of about 360×120, painted by `UiPainterRenderer` from the same scene builders as the real fires: `ScoreFireScene` for the score, and the flame items of `ArcadeScene` with an empty game for Arcade. It uses its own `FlameLevel`, clock and bursts, and its own `QualityController` with the same budget, so the preview shows the tier the settings would cause.
- It animates at 30 frames per second only while the tab is the selected one and the window is visible; it stops on another tab, on close, and with Reduce motion (it is still drawn, without motion and sparks).
- Changing a value rebuilds the tuning at once (FR-020: within 0.1 s) and saves it.

**Rationale**: the window's existing widgets and patterns. The preview uses the real code path, so what it shows is what the run shows.

**Alternatives considered**:
- Spinners only: the owner wants maximum freedom and quick feeling, and sliders show the range.
- A separate preferences window: the owner asked for a tab.

## R7. Reduce motion on Linux and Windows

**Decision**: a new class `SystemReducedMotion`, used by both fires (and by the previews) instead of `ReducedMotion.requested()`.
- **macOS**: it delegates to `ReducedMotion.requested(runner, os)`, unchanged.
- **Linux**, in order:
  - `gsettings get org.gnome.desktop.interface enable-animations`: `false` means reduce;
  - else `kreadconfig6 --file kdeglobals --group KDE --key AnimationDurationFactor`, then `kreadconfig5` with the same arguments: `0` means reduce.
- **Windows**: `powershell -NoProfile -NonInteractive -Command "Add-Type -AssemblyName PresentationFramework; [System.Windows.SystemParameters]::ClientAreaAnimation"` returns `False` when the learner turned off "Show animations in Windows". This is the system value `SPI_GETCLIENTAREAANIMATION`.
- **Timing**:
  - PowerShell can take more than the 500 ms limit, so on Windows the answer is fetched in the background when the practice window opens and again when each run starts. The run uses the latest answer, and until one arrives, no reduction.
  - Linux and macOS are read synchronously when a run starts, with the existing 500 ms limit through `CommandRunner`.
- Any other answer, an error or another system: no reduction (FR-013).

**Rationale**: `ReducedMotionTest` (lines 99–103) pins that `ReducedMotion.requested` runs nothing on Windows or Linux. Rules: existing tests are never modified, and every test must keep passing. So the old reader stays as it is, and the new class carries the new behaviour with its own tests. The answers of each system are parsed by pure functions tested with a fake `CommandRunner`.

**Unverified**: the real commands on Linux and Windows (no machine). The release notes ask testers for feedback (owner's rule).

**Alternatives considered**:
- Extending `ReducedMotion` itself: it breaks the existing test line, which needs the owner's exception.
- JNA or registry access: a new dependency.

## R8. The guard boxes of the score

**Decision**: the boxes the fire and the sparks must avoid are passed to `FlameDrawing.faded` as `texts`, in canvas coordinates:
- the frame of the current event and of the next one, from `TGExternalNoteFramePainter.frameBounds(layout, notes, trackSelection)`, already canvas-local, as `ScoreBoxes.of` uses them before it adds the screen origin;
- the palette's screen box, made canvas-local by subtracting `ScoreBoxes.area()`'s origin.

The guard itself is unchanged: 4 px widening, 28 px ramp, 0.2 % (FR-005, FR-016).

**Rationale**: the same frame boxes as the palette dodge of spec 017. In Follow, the frame painter does not draw while the player runs, but `frameBounds` still gives the box of the current and next events from the layout.

## R9. Size, bounds and the cost ceiling

**Decision**:
- **Score fire geometry**: the viewport is the canvas size. Corner height 1/3 of the canvas height (FR-004), middle 0.20 of the corners, as Arcade.
- **Ranges**: wide (data-model), with these cost ceilings (FR-019):
  - columns at most 384;
  - smallest pixel at least 3 px;
  - sparks in the air at most 1 000;
  - per burst at most 200, plus at most 50 per streak note;
  - colour steps at most 64.
- **What happens beyond**: the `QualityController` of each fire still steps down to coarser cells, then no sparks, then no fire. For the score its budget is 8 ms, measured around the fire's own painting in the listener.
- **Typing out of range**: impossible with a slider. A stored value out of range is clamped by `FireTuning`.

**Rationale**: the owner chose wide bounds; the ceilings keep the automatic quality working (spec edge case).

## R10. Pause, end and clock of the score fire

**Decision**:
- `ScoreFire` keeps a fire clock that advances only while the run is running. The pause state comes from the snapshot: PAUSED and READY_TO_RESUME, as `paletteGuard.show` reads it.
- On pause, `FlameLevel.hold`; on resume, `FlameLevel.resume`; the bursts keep their clock times, so they hold still too.
- On stop, at the `paletteGuard.stop()` point (line ~1253) and in `stopNow()`, the listener is removed and the canvas is redrawn once, so the fire goes out at once.
- The score fire's level uses `FlameLevel` built from the score tuning:
  - minimum judged 0;
  - rise 0.5 s for the whole height;
  - fall 1.0 s to embers (FR-003: within about one second);
  - share = min(1, streak / streak of full height = 16).

## R11. Classification of the number literals of the fire (written in T008)

**Decision**: the exact allow-list of `FireSourceScanTest` is not guessed in advance. It is the classification of every literal the scanner finds in `FireField`, `FireColours` and `FlameDrawing` on today's code, written here while T008 is red:

| Literal | Method | Class | Reason |
|---|---|---|---|

_To be filled in T008._

**Rationale**: three analysis passes (4, 5 and 6) each found literals missing from a list written in advance. Classifying what the scanner actually finds is exact and leaves nothing out. A literal that shapes the look but is in no catalogue row stops the work and is reported (FR-015).

