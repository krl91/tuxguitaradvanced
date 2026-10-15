# Research: an adjustable timing window (spec 012)

Every claim cites a file opened on 2026-10-15. `PR` = `desktop/TuxGuitar-practice/src/app/tuxguitar/app/tools/custom/practice`, `PT` = `desktop/TuxGuitar-practice/test/app/tuxguitar/app/tools/custom/practice`, `AE` = `desktop/TuxGuitar-audio-engine/src/main/java/app/tuxguitar/audio/engine`.

## R1 - What exists today

* `PR/session/TimingTolerance.java`: an enum `Preset` with the numbers `STRICT(0.10, 40, 100, 0.25, 0.25, 100, 300)`, `NORMAL(0.15, 60, 150, 0.40, 0.40, 150, 450)`, `LENIENT(0.25, 90, 250, 0.60, 0.60, 220, 700)`: the tight window (on time) as a fraction of the local beat clamped between a floor and a ceiling in ms, then the outer window (early / late) the same way; `windowFor(target, beat, gapBefore, gapAfter)` cuts the outer window at the middle of the gap to the neighbouring events. At a beat of 500 ms (120 bpm): tight half-window 50 / 75 / 125 ms, outer 125 / 200 / 300 ms (`PT/session/TimingToleranceTest.java` l.38-43 pins them).
* `PR/session/FollowSettings.java`: keeps `TimingTolerance.Preset tolerance` **in memory for the run** (`getTolerance`, `setTolerance`), starts from the saved value `RecognitionParameters.TEMPO_TOLERANCE` and returns to it when the recognition settings change (`resetToleranceTo`, l.76-78).
* `AE/settings/RecognitionParameters.java` l.51-55: `TEMPO_TOLERANCE` is a **`Choice`** (`strict`, `normal`, `lenient`, default `normal`); the same file defines **`Number`** parameters with a unit, a default, bounds and an integer flag, e.g. `NOTE_STABILITY = new Number(PREFIX + "note.stability", Section.NOTES, "ms", 107, 50, 300, true)`.
* `PR/ui/RecognitionSettingsPanel.java` (l.173, 205, 261): the Configuration window **builds a control for every `Number` and every `Choice`** of a section by itself.
* `PR/ui/FollowPanel.java` (l.168-177, 275-280, 342-373): the preparation window's Mode tab has a label, a **drop-down of the three presets** and a hint; a pick calls `settings.setTolerance(preset)`; it is enabled only when idle and shown only for Follow and Arcade.
* `PR/session/FollowStarter.java` l.107-111: the run reads `settings.getTolerance()` once, when it is prepared, and builds `new TimingTolerance(preset)`. That is the **only** production reader (grep of `getTolerance()` and `new TimingTolerance(`, 2026-10-15): the change applies to the next run by construction (FR-008).
* `AE/settings/RecognitionSettings.java`: immutable; `fromStrings(Map)` reads stored text, a missing key gives the default, and a missing key is not a repair (older configurations simply do not have it yet).

## R2 - The model: one width in milliseconds

**Decision.** The learner's value is **W**, the half-width of the tight window in milliseconds **at a reference of 120 beats a minute (a beat of 500 ms)**. The windows are derived from W with the ratios of today's **Normal**, so that **W = 75 reproduces Normal exactly**:

* tight half-window = clamp( round( (W / 500) x beat ), floor = 0.8 W, ceiling = 2.0 W )
* outer half-window (early and late) = clamp( round( (W / 500) x 2.6667 x beat ), floor = 2.0 W, ceiling = 6.0 W ), never under the tight one, and cut at the middle of the gap to the neighbouring events as today.

Check with Normal: W = 75 gives fraction 0.15, floor 60, ceiling 150, outer fraction 0.40, floor 150, ceiling 450: identical to `NORMAL` (FR-010, SC-004). The new default W = 100 gives a tight half-window of 100 ms at 120 bpm (floor 80, ceiling 200) and an outer one of 267 ms. The range W = 40 to 200: the narrowest tight window is 40 ms (floor 32, ceiling 80), the widest 200 ms (floor 160, ceiling 400), outer up to 533 ms at 120 bpm (the cut at the middle of the gap keeps windows from overlapping in a fast passage).

**Why this and not a new set of numbers.** It keeps the one tested shape of the window (fraction of the beat between a floor and a ceiling, scaling with the tempo, FR-004), changes one number, and makes "equal to today" provable at one value. A slider in percent of the beat (owner's alternative B) would not give a window that is readable in milliseconds; the owner chose milliseconds.

**Old presets.** Strict and Lenient map to W = 50 and 125; **Normal maps to the new default 100** (see R4: a saved "normal" cannot be told from a default that was never chosen). The window at **W = 75 is exactly** the old Normal; Strict and Lenient have the same tight width at 120 bpm and their outer windows and floors differ by under 10 % at most tempos (Strict outer 133 ms against 125 at 120 bpm; Lenient outer 333 against 300, floor 100 against 90): declared, not hidden.

## R3 - Where the one setting lives (owner's decision: one setting)

**Decision.** A new `Number` parameter **`TEMPO_WINDOW`** in `RecognitionParameters` (`PREFIX + "tempo.window"`, section NOTES, unit "ms", default 100, minimum 40, maximum 200, integer). It is saved by the existing store and read by `FollowSettings`. The preparation window's slider **writes it through the store at once** (the store persists, `RecognitionSettingsStore.update`), so the Configuration window, which builds its control from the parameter, shows the same value: one place of truth. `FollowSettings` no longer keeps its own in-memory tolerance: `getWindowMillis()` reads the store's current value, and `setWindowMillis(ms)` updates the store (bounded to 40..200).

**Alternatives.** Keeping `FollowSettings`' in-memory value as it is today and a separate saved default (rejected by the owner: "one setting"). A new file or key outside the recognition store (rejected: the store already persists, validates and notifies).

## R4 - An older saved setting still loads (FR-006, SC-005)

**Evidence.** `RecognitionSettings.fromStrings` gives the default for a missing key and does not tell "absent" from "default" afterwards. `TEMPO_TOLERANCE` (the old Choice) is in `RecognitionParameters.ALL`, so it keeps being read and written.

**Decision.** `TEMPO_TOLERANCE` stays as a **legacy parameter**, no longer shown in the Configuration window (**settled by reading `RecognitionSettingsPanel.refresh`, l.241-248: the panel builds and refreshes a control for every parameter of `RecognitionParameters.ALL`**): the old `TEMPO_TOLERANCE` is therefore **taken out of `ALL`** and kept only as a constant of its stored key, read by the migration in `fromStrings`; it then counts as an unknown key and is dropped at the next save, once the window is saved (the tests that name it are updated). In `fromStrings`, **when the key of `TEMPO_WINDOW` is absent and `TEMPO_TOLERANCE` holds `strict` or `lenient`, the window is 50 or 125; `normal`, absent or unreadable gives the default 100**; once the window is saved the old key is ignored. An out-of-range stored window is brought to the nearest bound by the existing repair of `Number` (FR-007).

**Why `normal` becomes 100 (found by the analysis of 2026-10-15).** `RecognitionSettings.toStrings()` writes **every** parameter, and `RecognitionSettingsStore.update` writes as soon as one setting changes (`AE/settings/RecognitionSettingsStore.java` l.75-95): any user who ever saved any recognition setting has `tempo.tolerance=normal` in their file without ever having chosen it. Mapping it to 75 would leave the owner and almost everyone on the old, harder window and defeat the request for a more lenient default. A user who deliberately chose Normal gets 100 (25 ms wider): declared, and the slider is there to go back to 75.

## R5 - The control (owner's decision: a slider in milliseconds)

**Evidence.** `PR/ui/GainControl.java` and its test use the toolkit's slider (`UIScale`, with `setMinimum`, `setMaximum`, `setValue`, the contract toolkit supports them: `PT/ui/SwtContractToolkit.java` l.66-95).

**Decision.** In `FollowPanel`, the drop-down becomes a **slider from 40 to 200** (step 5) with a text that shows the value, e.g. "±100 ms at 120 bpm"; the hint text explains that the window grows and shrinks with the tempo. It stays enabled only when idle and shown only for Follow and Arcade. `FollowPanelTest` and `FollowPanelSwtTest` that pick a preset are rewritten for the slider.

## R6 - Texts, docs

`practice.follow.tolerance` (label) is reworded to "Timing window", the three `...tolerance.strict/normal/lenient` keys are removed from the UI (kept nowhere), a `practice.follow.tolerance.value` text with a placeholder carries the value, the hint is updated; the three languages `practice/share/lang/messages*.properties`; the language test `FollowStringsTest` covers the keys; `docs/PRACTICE*.md` and `docs/MANUAL*.md` sections on Follow and Arcade.

## R7 - Non-regression

* **Equal at W = 75:** a new test compares `TimingTolerance.ofMillis(75).windowFor(...)` with `new TimingTolerance(Preset.NORMAL).windowFor(...)` over a grid of beats (30 to 2000 ms) and gaps: identical to the nanosecond (FR-010).
* The recorded and functional runs (`PT/session/FollowFunctionalTest`, `FollowBenchmarkTest`, `ArcadeRunFunctionalTest`, the recorded replays) are run with the window at 75 through the setting: every figure equal (SC-004). They currently use `Preset.NORMAL` through `FollowSettings` in `PT/session/FollowHarness.java`, `FakePlayerRun.java`.
* The recognition figures of spec 010 (`specs/010-pre-run-check/benchmark/after/`) are the base: the recognition is untouched.

## R8 - Risks

1. **The Configuration window's control for a `Number` in section NOTES** may need the legacy `Choice` to be hidden without breaking `RecognitionSettingsPanelTest` (which counts parameters). Settled in the first task.
2. **`Preset` stays in the code** only as the mapping of saved values and as the reference for the equality test; the enum is not removed (no unrelated refactoring) but is no longer used by the UI.
3. **A very wide window (W = 200) with fast passages** relies on the cut of the outer window at the middle of the gap; a test with two events 150 ms apart at W = 200 pins that windows never overlap (FR-005, edge case).
