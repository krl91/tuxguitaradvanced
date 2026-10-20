# Research: Arcade made fun (spec 014)

What was read in the source on 2026-10-20, and what it decides. Files are under `desktop/TuxGuitar-practice/src/app/tuxguitar/app/tools/custom/practice/` unless said otherwise.

## R1. Arcade is the tempo-following run, so the judging is shared with Follow

**Read:** `arcade/core/ArcadeStarter.java` builds the run with `FollowStarter.prepare(...)`; `ui/PracticeWindow.java` `startArcade` (about l.784) takes `recognition = RecognitionSettingsStore.getInstance(context).get()` (the effective settings: default plus the device profile of spec 013), then `PracticeParameters.of(recognition)` and passes both to `ArcadeStarter.prepareFrom`. `session/PracticeParameters.java` derives the note interpreter (confidence), the tolerance policy (in-tune and acceptance cents), the stability time and the chord parameters from those settings, once per start.

**Decision:** the leniency is applied **at that one point**, to `recognition`, before `PracticeParameters.of`, only in `startArcade`. `startFollow` (l.1001) and the Wait session (l.687) keep reading `store.get()` untouched, so Follow and Wait cannot change (FR-004).

**Alternatives:** a new setting in the store (rejected, R5); changing `PracticeParameters` (rejected: shared by every mode).

## R2. The parameters the leniency can relax, and their allowed ranges

**Read:** `audio-engine/.../settings/RecognitionParameters.java`: `NOTE_ACCEPT` 35 cents (10 to 45), `NOTE_CONFIDENCE` 0.80 (0.50 to 0.95), `NOTE_STABILITY` 107 ms (50 to 300), `CHORD_EVIDENCE` 1/3 (0.20 to 0.80), `CHORD_WINDOW` 600 ms (300 to 1500), `CHORD_MIN_PARTIALS` 2 (1 to 4), `CHORD_CONFIRM` 43 ms (20 to 150). `CHORD_COMPLETENESS` and `CHORD_FOREIGN` exist but offer a single value each: no lenient mode of chord completeness exists, and none is added (FR-014).

**Findings that shape the design:**
- `NOTE_ACCEPT` stops at **45 cents**: a note a **semitone or more away can never be accepted** by acceptance alone; "a few more wrong notes" can only be sounds within half a semitone, plus the effect of a lower confidence and a shorter stability.
- Every value stays inside the range the settings already guarantee, so no validation is added.
- The relaxation must be **one-way**: for each parameter the Arcade value is the more lenient of the player's effective value and the position's value (a player or a profile already more lenient is never made stricter).

**Decision:** `ArcadeLeniency` holds, per position, a value for `NOTE_ACCEPT`, `NOTE_CONFIDENCE`, `NOTE_STABILITY`, `CHORD_EVIDENCE`, `CHORD_MIN_PARTIALS`, `CHORD_CONFIRM` (and `CHORD_WINDOW` if the measure asks), applied one-way to the effective settings. Starting values, to be **fixed by the measure of R6, not decided here**: fun = 42 cents, 0.70, 75 ms, 0.28, 2, 32 ms; very permissive = 45 cents, 0.60, 55 ms, 0.22, 1, 24 ms. "Like Practice" changes nothing.

## R3. The timing window

**Read:** `session/FollowStarter.java` l.108: `windowMillis = settings.getWindowMillis()`, then `TimingTolerance.ofMillis(windowMillis)`. `session/TimingTolerance.java` `ofMillis` **throws** outside 40 to 200 ms (`MIN_WIDTH_MILLIS`, `MAX_WIDTH_MILLIS`).

**Decision:** Arcade widens the window by a factor (fun 1.25, very permissive 1.5, like Practice 1.0) **on top of** the player's window of spec 012, **capped at 200 ms** (the existing maximum); `FollowStarter` gets an overload taking the window, the existing `prepare` delegating with `settings.getWindowMillis()`, so Follow's path is the same code with the same value. A player already at 200 ms gets no more: stated in the contract.

**Alternatives:** raising the maximum (rejected: changes spec 012's range and its tests); replacing the player's window by a fixed Arcade one (rejected: the owner asked for configurable, and spec 012 exists).

## R4. The level filter works on the sequence

**Read:** `model/PracticeSequence.java`: immutable list of `PracticeEvent`, a start index, and two precedents of a derived sequence, `startingAt(index)` and `onlyEvent(index)` (spec 013), which keep the events' measure and play index so times still come from the song. `PracticeEvent.Kind`: `EXPECTED_NOTE`, `EXPECTED_CHORD`, `MULTI_NOTE` (several notes with a pitch-changing effect, validated by Skip), `AUTO_PASS` (not judged). `targetsOf(index)` finds a legato origin in the events kept, so a note whose origin was removed is judged as a plain note (the same already holds for `startingAt`).

**Decision:** a new `PracticeSequence.keeping(...)` returns the sequence of the kept events (in order, same start-index rule), and `ArcadeLevelFilter` chooses them: notes = `EXPECTED_NOTE`, chords = `EXPECTED_CHORD` and `MULTI_NOTE`; `AUTO_PASS` events are not sounds and are kept as they are. For *n* events of a kind and a share *s*: *k* = max(1, round(*s* × *n*)) (0 when the share is 0 and the kind is chords at level 1, except the "never empty" rule of the spec), kept ranks `floor((i + 0.5) × n / k)` for *i* = 0..*k*-1. This is even, deterministic and tested by counts (SC-004). Notes and chords are spread each over their own order.

**Edge rules (spec FR-009 and edge cases):** at least one sound kept at every level whenever the piece has one; a piece with only chords keeps one chord at level 1; a chord is kept whole.

**Start index:** the preparation builds `full` from the caret (`getStartIndex`); the filter keeps the first kept event at or after the original start index as the new start. The replay of a section (`startingAt(index)`) and the restarts use the **filtered** `full` (`arcadeFull` in `PracticeWindow`), so the level survives them.

## R5. Where the level and the leniency are remembered

**Read:** `session/FollowSettings.java` keeps the Arcade look settings in memory only; only the timing window is saved, as `RecognitionParameters.TEMPO_WINDOW` (spec 012), which shows in the Configuration page. `session/InputDefaultStore.java` and the profile library (spec 013) are small stores on the application's configuration manager.

**Decision:** a small store of its own (`ArcadeChoices`, its own configuration module): the level (1 to 5, default 1 when nothing is saved, FR-006) and the leniency position (default fun). **No `RecognitionParameters` entry is added**: the Configuration page, `restoreDefaults`, the settings file and the tests that list the parameters stay as they are. A missing or invalid value falls back (FR-012).

## R6. What can be measured for SC-001 and SC-002, and what the figures are today

**Read:** `specs/STATUS.md` and the 2026-10-06 measures: on the reference recording (`tests/1/audio_15ps.m4a` with `_TEST_verifie.tg`, replay test `RecordedPracticeReplayTest`, default settings): **35 of 38 judged events recognised, 0 false acceptances in 98 negative tests**, delay **median 105 ms, 90th percentile 169 ms**. `specs/013.../benchmark/search-results.txt`: on the built-in-microphone corpus the negative control (a note judged against the *next* event) is **already accepted 1 time in 21 (4.8 %) with the default settings**.

**Findings:**
- The "at most 5 %" of SC-002 must be measured on the **reference recording's 98 negative tests** (0 today, so up to 4 allowed); on the built-in-microphone corpus the default is already at 4.8 %, so that corpus **cannot** carry a 5 % limit and is only reported.
- The 20 % of SC-001 means a median delay of at most about **84 ms** where Follow gives 105 ms on the same recording; the stability time (107 ms) is the main lever, and the fast early acceptance (`PracticeParameters.EARLY_STABILITY_ENABLED = false`, switched off because the evidence does not separate right from wrong on this microphone) stays off.

**Decision:** a new gated replay test runs the reference recording (and the chord takes) with each position, printing the three figures of the spec (right accepted, wrong accepted, median delay) next to Follow's; the **values of R2 are fixed from it**, within the limits of SC-001 and SC-002. If no value reaches both, that is reported and the owner decides (the spec says report, never hide).

## R7. Where the level is chosen at every launch

**Read:** an Arcade start goes through `ui/StartFlow` and the start window `ui/PreRunWindow` (spec 010) at every start from the Start button, the transport-bar button and the quick start; a restart or a replay does not ask again. The preparation window (`ui/FollowPanel`) has the Arcade controls (marker size, horizon, flames).

**Decision:** the level is a row of the **start window when the mode is Arcade**, preselected with the last level (level 1 the first time), so that **every launch offers the choice** (FR-006); the leniency position is a line of the Arcade section of the preparation window. The result window names both (FR-010). A restart keeps the level, a new start asks again.

**Risk:** the start window has native layout tests (`PracticeWindowSwtTest`: every visible control has a size inside the window) and a source test forbidding some strings (`PreRunParityTest`). The new row goes through a constructor variant so the existing one and its tests are unchanged; if it cannot be done, see the fallback in the plan's complexity note.

## R8. The source-structure tests on the Arcade start path

**Read (headers only):** `test/.../ui/PracticeWindowStartPathSourceTest.java` forbids `startArcade(` and `startFollow(` in some bodies and checks where `startFlow.pending()` is read; `PracticeHostSourceTest` counts calls in `PracticeWindow`/`AppPracticeHost`. Their exact constraints were **not** read to the end.

**Decision:** the first task of the plan reads them completely and lists what `startArcade` may and may not contain; the level and leniency code is written **outside** `PracticeWindow` (in `arcade/core`) so that `startArcade` gets a few lines at most. Nothing is edited in an existing test.

## R9. What stays out

Not touched: the recognition algorithms, `ChordMatcher`, `PolyphonicAcceptance` parameters (its `defaults()` are passed as today), the technique judge, the backing band, the scene drawing, the latency compensation. A device profile (spec 013) keeps applying; the leniency is one-way on top of it.
