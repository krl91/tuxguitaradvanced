# Research: spec 025, the practice and amp windows

Every decision below was taken after reading the source named in it (constitution III).

## R1. Why the start button stays greyed

**Found.** `AudioEngine.acquire` (`desktop/TuxGuitar-audio-engine/src/main/java/app/tuxguitar/audio/engine/AudioEngine.java`) runs the level check only when the selection's assessment is `UNCHECKED`; `selectionChanged` runs it again only for a new input revision. `InputAssessor.analysisFrame` (`.../analysis/InputAssessor.java`) ends a check after `timeoutNanos` (10 s) without `requiredPlayingNanos` (0.5 s) of playing (a pitched frame with confidence ≥ 0.80) and returns `UNUSABLE` / `NO_SIGNAL` (or `CLIPPING`, `ACCESS_DENIED_SUSPECTED` from `classifyWithoutPlaying`). Nothing runs it again while the window stays open: the learner who did not play in the first 10 s is stuck until the input is chosen again. The code dates from spec 002 (commit 56b8d613f, 2026-09-27); it is not a spec 023 regression.

**Decision.** A small rule, `LevelRecheck` (practice module, no UI, unit-tested), decides from the current assessment, the frame and whether a run is going whether a new check must start: only when the assessment is `UNUSABLE` with `NO_SIGNAL` or `CLIPPING` (clarification 5), no run is going, no check is running, and the frame counts as playing by the assessor's own criterion (pitched, confidence ≥ `InputAssessor.Parameters.playingConfidence`). `LevelPanel`'s frame listener (already subscribed while the window holds the capture) asks it and calls `engine.runLevelCheck()` on the UI thread.

**Alternatives.** Re-arm inside `AudioEngine` itself: rejected, the engine does not know whether a practice run is going (FR-013 keeps the run's rules), and the tuner shares the engine. A periodic re-check every N seconds: rejected, it would flip the status while the learner reads the window.

## R2. A second check must not start during one (FR-003)

**Found.** `AudioEngine.runLevelCheck` calls `assessor.runLevelCheck()` then `subscribe(assessor)`; `subscribers` is a `CopyOnWriteArrayList`, so a second call during a check subscribes the assessor twice and every frame is counted twice. `InputAssessor.isChecking()` exists.

**Decision.** `AudioEngine.runLevelCheck` returns at once when the assessor is checking (an additive guard in the fork's audio-engine module, with its own test). **Check again** and `LevelRecheck` both go through it.

## R3. The line under the start button (FR-002, FR-004)

**Found.** `PracticeWindow.updateStartButton` writes `QuickStart.greyedReason(...)` into `startLabel`, which is row 5 of the **Track** tab (`createPreparation`), not under the button: on the Input or Mode tab the learner does not see it. The keys are per status only (`practice.start.greyed.UNCHECKED`, `.UNUSABLE`). `GreyedStartExplainedTest` (committed) requires `updateStartButton` to call `QuickStart.greyedReason(` and `this.startLabel.setText(`, and `greyedReason` to keep returning those keys.

**Decision.** `startLabel` moves to the window's fixed bottom area, above the buttons (row 3 today holds the buttons). `greyedReason` is kept as it is; a new `QuickStart.blocker(...)` gives the refined key per cause (no sound yet, too low, saturating, input lost, access refused, no track, waiting for the guitar) and whether **Check again** applies. `updateStartButton` keeps calling `greyedReason` (the status-level text stays the fallback) and writes the refined text through `this.startLabel.setText(`. A **Check again** button sits next to the line, shown only for level causes. On macOS a disabled button shows no tooltip, so nothing relies on one.

## R4. The practice window taller than the screen (FR-005, FR-006)

**Found.** `PracticeWindow.refit` sets the folder's packed height to its **tallest** tab + 48 px, and `WindowFit.refit` applies the packed size as the window's minimum size and "never caps" it (`WindowFitTest.keepsTheTopLeftVisibleAndNeverCapsTheSizeWhenLargerThanTheArea`, committed). The Advanced tab already scrolls through `UIScrollBarPanel` (`FireSettingsPanel`, vertical only, "asks for no height of its own") and is left out of the refit.

**Decision.** Every tab content goes into a vertical `UIScrollBarPanel` the same way as `FireSettingsPanel` (the pattern already shipped on the three systems). The folder's packed height becomes a fixed cap: the smallest of the tallest tab and what a 768-pixel screen leaves after the title bar, the engine notice and the bottom area (`PracticeLayout.tabHeight`, a pure function, unit-tested). `WindowFit` is not changed: with a capped content its packed size fits, so it never has to grow past the screen. A size restored from an earlier launch taller than the screen is brought within the screen's client area, read through `Screens` (SWT monitors by reflection, as spec 022 does); with another toolkit the bounds are left as they are.

## R5. The amp window in tabs (FR-007, FR-008)

**Found.** `AmpWindow.open` builds one tall column: a top panel (switch, sounds, output, two volumes, delay), then `AmpChainRows` (pedals before, amp, cabinet, pedals after, Add pedal) placed in the window's layout from row 2, then a message line. `relayout` packs the window to its whole content after every pedal change. `AmpChainRows` takes `(factory, parent, parentLayout, row, ...)`.

**Decision.** Above the tabs: the switch, the sound choice with **Save…** and **⋯**, the delay. A `UITabFolder` (already used by `PracticeWindow`) with four tabs, each a vertical `UIScrollBarPanel`: *Pedals before the amp*, *Amp* (character, five sliders, speaker simulation), *Effects after the amp*, *Output* (output, guitar volume, score volume). `AmpChainRows` gets a second constructor that takes the three parents (before, amp, after) instead of one parent and a row; the existing constructor stays for nothing else to change. The message line stays under the tabs. `relayout` no longer packs to the whole content: the window keeps its size (at most 600 px high at the first opening) and the tab scrolls.

## R6. The switch as a check box, the sounds' menu (FR-009, FR-014)

**Found.** The toolkit has `createCheckBox` (`UICheckBox`, SWT `SWTCheckBox`, a `Button` with `SWT.CHECK`) and `createPopupMenu` (`UIPopupMenu.open(UIPosition)`). `AmpWindow` has `getSwitch()` returning `UIToggleButton`, `getSoundName()` (a text field) and `getSaveAs()`, `getRename()`, `getDelete()`, `getRestore()` (buttons). `AmpWindowSwtTest`, `AmpSafetySwtTest`, `AmpAutoStartWarningSwtTest` and `AmpSoundListSwtTest` reach them.

**Decision.** The switch becomes a `UICheckBox`; the cabinet keeps its toggle type but is renamed (its test only reads it as `UIToggleButton`). The name field leaves the window: **Save…** and **Rename…** open a small modal name prompt (a `UIWindow` like the built-in microphone warning, with the same name rules `AmpSoundStore.NameProblem`); **⋯** opens a popup menu with Rename…, Delete, Restore, each enabled under the same rules as today's buttons.

**Owner's exception (2026-11-05).** The four tests above may be changed **only** in the lines that reach the switch as a toggle button, the Name field and the Rename / Delete / Restore buttons, so that they reach the check box, the name prompt and the menu items instead; what they check stays the same. Every other test line and every other test stays unchanged; new tests are added for the new behaviour.

## R7. Texts

**Found.** Plugin strings are in `desktop/TuxGuitar-practice/share/lang/messages.properties`, `_fr`, `_es` (`practice.amp.*`, `practice.start.greyed.*`). `GreyedStartExplainedTest` checks every reason key in the three files.

**Decision.** New keys, never reused meanings: `practice.start.blocker.<cause>` and `practice.start.check-again`; `practice.amp.enabled` and `practice.amp.cabinet` change their text (Hear my guitar through the amp / Entendre ma guitare dans l'ampli / Oír mi guitarra a través del amplificador; Speaker simulation / Simulation du haut-parleur / Simulación del altavoz), their tips too; `practice.amp.tab.before|amp|after|output`, `practice.amp.save`, `practice.amp.more`, `practice.amp.name-prompt.*`.

## R8. What is validated where (constitution IX)

macOS: the unit tests, the SWT GUI tests (`-P swt-gui-tests`) and the owner's gate. Linux and Windows: built by the CI, layout code shared (no platform branch); not run.
