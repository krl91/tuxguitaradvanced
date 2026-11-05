# Research: the owner's chords accepted at least 9 times in 10, with a chord leniency the learner can set

**Spec**: [spec.md](spec.md) · **Date**: 2026-11-05

Every decision cites the source read. The paths are under `desktop/` unless said otherwise.

## R0. What decides that a chord is accepted (source read)

A chord is accepted on either of two paths of `PracticeSession` (`TuxGuitar-practice/src/.../practice/session/PracticeSession.java`). One path is enough.

- **The attack path.**
  - It is lines 439–456 of `PracticeSession`. `PolyphonicAcceptance.offer` reads the attack evidence of each hop. The expected pitches must explain `minShare` (0.30) of what the attack added. The strongest must reach `minPeakRatio` (0.7) of the strongest pitch. Every separable pitch must reach `minBalance` (0.10) shared among them. All this must hold for `hops` (6) hops in a row.
  - Clipping removes the evidence (`frame.getLevelState() != CLIPPING`).
  - Its parameters are `PolyphonicAcceptance.Parameters.defaults()`. They are not learner settings.
- **The chord check.**
  - It is `onChordFrame`, lines 705–744. `ChordMatcher.check` reads `ChordEvidence`, which gives, for each required pitch, its unique partials, its clear unique partials and the clear fraction.
  - A pitch with fewer than `minClearPartials` unique partials is *unverifiable*. It is never counted as heard or missing.
  - A verifiable pitch is heard with at least `minClearPartials` clear unique partials and at least `minClearFraction` of them.
  - The chord is complete when every verifiable pitch is heard (`Result.isComplete`). It is accepted after `stableHops` complete frames in a row, within `windowNanos` of the strum's attack.
  - A frame at a level that is not usable (`CLIPPING`, too low) resets the run.
- **Where the values come from.** `PracticeParameters.chordParameters()` reads them from `RecognitionSettings`:
  - `CHORD_MIN_PARTIALS` (2);
  - `CHORD_EVIDENCE` (1/3);
  - `CHORD_CONFIRM` (43 ms, which is 8 hops);
  - `CHORD_WINDOW` (600 ms).

  All four are declared in `TuxGuitar-audio-engine/src/main/java/.../settings/RecognitionParameters.java`.
- **The native engine.**
  - It carries the chord evidence: the counts, for the expected chord. With the Java engine, `ChordEvidence.evaluate` computes them from the spectrum.
  - The rule that decides is the same Java code (`ChordMatcher.judge`) for both engines, and `ChordEvidenceParityTest` holds the evidence equal.
  - **So any change to the decision made in Java applies to both engines the same way (FR-009) and adds nothing to the JNI contract.**

## R1. The diagnostic comes first, as a counterfactual instrument (FR-010, US3)

**Decision.** A new test, `OwnerChordsDiagnosticTest` (tag `dataset`, excluded from the default run), goes in `TuxGuitar-practice/test/.../practice/dataset/`. It runs once with each engine (`-Dtuxguitar.recognition.engine=java|native`, as in spec 023 quickstart §3). For each of the owner's 42 takes and its control (the same pairing as `OwnerChordsBenchmarkTest`), it records:

1. the outcome with the default settings;
2. per frame:
   - the level state, with the share of clipping frames in the take;
   - for each required pitch of the chord check: heard, missing or unverifiable, with its unique partials, clear unique partials and fraction, taken at its best frame inside the window;
   - the longest run of complete frames compared with `stableHops`;
3. on the attack path: whether any hop passed, otherwise its best share, peak ratio and balance;
4. the counterfactual: which single relaxation, taken alone, turns a refusal into an acceptance (or an acceptance into a refusal, for a wrong neighbour). The candidate relaxations are those of R3.

The cause written for each take is the first that applies:

1. *clipped*: more than half the frames inside the window are `CLIPPING`;
2. *one string short*: one verifiable pitch is missing;
3. *weak evidence*: a pitch is just under the fraction or the clear-partial count;
4. *too short*: the run of complete frames was shorter than `stableHops`;
5. *other*: none of these.

The report gives the causes grouped with their counts, for both engines. It goes to `specs/024-lenient-chords/benchmark/diagnostic.md`.

**Rationale.**
- Spec 023's experiment B failed because it changed a rule before the causes were known (`specs/023-native-recognition-engine/benchmark/chord-experiments.md`).
- The counterfactual says directly which lever can recover which chord, at what cost in wrong acceptances.
- It uses only public API: `ChordMatcher.check` and its `Result`, `ChordEvidence`, `AttackEvidence`, `AnalysisFrame.getLevelState`, and session parameters.
- No production class changes for it. `DatasetJudge` is committed and stays as it is; the new test has its own judge that takes the settings as an argument.

**Alternatives rejected.**
- Exposing the internal conditions of `PolyphonicAcceptance`: a production change for a test.
- A Rust-side diagnostic: the decision is in Java (R0).

### What the committed report already shows

This is a preliminary reading, not the diagnostic. It comes from `specs/023-native-recognition-engine/benchmark/after-native/owner-chords.txt`, which has identical figures on both engines.

- **13 of the 16 refused takes are in the `owner-chords` series.** That series was recorded with the input volume at 0.99, saturated (the comment in `owner-chords-baseline.properties`). Clipping removes both paths (R0), so *clipped* is likely a main cause. Reaching 38 of 42 needs at least 12 of the 16 refusals recovered, so most of that series.
- **The other 3 refusals are the same D chord, `[50 57 62 66]`,** in `owner-chords-2`: takes 09, 11 and 12.
- **12 of the 13 wrong acceptances are the same chord in another voicing.** For example:
  - `[45 50 57 62 66]` against `[50 57 62 66]`, a subset;
  - `[52 55 60]` against `[52 55 60 64]`, where 64 is an octave of 52 and so unverifiable;
  - `[43 55 59 67]` against `[55 59 67]`.

  Audio cannot tell these apart (constitution XIII; the edge case on strings that cannot be told apart). The only wrong acceptance of a really different chord is `owner-chords/take18`, `[52 57 60]` accepted as F `[41 53 57 65]`.

  So a more lenient rule risks raising the count mainly through the 29 controls that are really different chords. Making *Strict* go below 13 means refusing some of these voicing pairs, or the one real wrong chord.

## R2. The level is derived from the chord settings, and *Normal* is the set of default values

**Decision.**
- **A level is a set of values of the chord settings.** The new pure class `ChordLeniency` goes in `TuxGuitar-audio-engine/src/main/java/.../settings/`. It holds the values of *Strict*, *Normal* and *Lenient*.
- **The level shown is derived, not stored.** It is the named level whose values the default layer of the store holds, otherwise *Custom*.
- **Choosing a level writes its values into the default layer** with `RecognitionSettingsStore.update`, so the expert panel shows them (FR-012).
- **A change by hand in the expert panel makes the derived level *Custom*,** with no extra code.
- **The level's settings** are:
  - `CHORD_EVIDENCE`;
  - `CHORD_CONFIRM`;
  - `CHORD_COMPLETENESS` (R3);
  - at most one new chord value, if the diagnostic shows it is needed (R3).
- **Not in the level:**
  - `CHORD_WINDOW`: the spec's edge case says the strum window is unchanged unless the learner changes it;
  - `CHORD_MIN_PARTIALS`: it also decides which pitches are unverifiable (`ChordMatcher.judge`), and the edge case says the leniency must not change that.
- ***Normal* is exactly the default values** (`RecognitionSettings.defaults()`):
  - `CHORD_EVIDENCE` stays at 1/3 and `CHORD_CONFIRM` at 43 ms;
  - the gain of *Normal* comes from the new default of `CHORD_COMPLETENESS` (and of the possible new value).

**Rationale (source read).** The committed tests constrain where the gain can come from:
- `RecognitionSettingsTest` lines 48 and 50 assert `CHORD_EVIDENCE` = 1/3 and 8 confirmation hops in `defaults()`.
- `RecognitionControlsTest` lines 81–84 assert the same values in what the panel shows.
- `RecognitionSettingsStoreTest.valuesSurviveARestart` asserts that the file holds version `"1"` and that any value written is read back unchanged.
- `RecognitionSettingsStoreProfileTest.restoringTheDefaultsActsOnTheDefaultLayerOnly` asserts that restoring gives `RecognitionSettings.defaults()`.

So the evidence and confirmation defaults cannot move, `VERSION` cannot change, and the store cannot rewrite values. That leaves two levers:
- **`CHORD_COMPLETENESS`, which no test pins.** It was declared in spec 002 for exactly this choice, "Strings required" (`messages.properties` line 175). It has a single option and is read nowhere (grep: no Java use outside `RecognitionParameters`).
- **A new value with a new key,** whose default old files do not hold.

With *Normal* equal to the defaults:
- a fresh learner gets *Normal*, and "Restore defaults" in the chord section gives *Normal*;
- the committed benchmarks, which use `RecognitionSettings.defaults()`, measure *Normal* directly.

**If the diagnostic shows *Normal* needs a different default for `CHORD_EVIDENCE` or `CHORD_CONFIRM`**, that breaks the committed lines above. Implementation then stops and asks the owner for an exception (the owner's rule: never modify a committed test).

**Alternatives rejected.**
- **A stored level key that overrides the numbers in the store.** It breaks `valuesSurviveARestart` and `restoringTheDefaults…`. It would also have to undo Follow's and Arcade's one-way relaxation, which runs on the effective settings (`FollowLeniency.apply`, `ArcadeLeniency`).
- **A level resolved in `PracticeParameters.of`.** It would overwrite the values that Follow and Arcade relax before it.

## R3. The candidate levers, and the rule that keeps them honest

The candidates are evaluated by the diagnostic. The chosen ones are written as tests first, seen red.

| Id | Lever | Where | Level setting | Guarded by |
|---|---|---|---|---|
| L1 | **One string short**: a chord with at least 3 verifiable pitches is complete when every verifiable pitch is heard but one, and that one still shows at least one clear unique partial | `ChordMatcher.Result.isComplete`, through a new option `all-but-one` of `CHORD_COMPLETENESS` | `CHORD_COMPLETENESS` | the unverifiable pitches are classified as today (a test) |
| L2 | **Lower evidence fraction** | existing `CHORD_EVIDENCE` | `CHORD_EVIDENCE` (*Strict* / *Lenient* only, R2) | — |
| L3 | **Shorter confirmation** | existing `CHORD_CONFIRM` | `CHORD_CONFIRM` (*Strict* / *Lenient* only, R2) | — |
| L4 | **Judge a clipped frame in the chord check** (the attack path stays as it is). The clipping warnings of the level check and of the meter stay (the spec's edge case). | `PracticeSession.onChordFrame`, through a new chord flag | new value, if the diagnostic shows *clipped* is a main cause | the Audio-Effects line "chord a whole tone higher refused" (the distortion case of spec 023 rule A); the input warnings unchanged (a test) |

**Decision.**
- Only the levers that the diagnostic shows to recover refused chords are implemented, in the order of the chords they recover.
- Each lever is a Java decision on evidence both engines already carry (R0). No change is made to `chord.rs`, `attack.rs` or `NativeParameters`.
- If the diagnostic shows that no lever of this table reaches FR-001 without new evidence from the engine, implementation stops and reports to the owner. New evidence would mean a JNI change, which needs the owner.
- L4 is the only lever that adds a key. Its default is *Normal*'s value, so old files, which lack it, get *Normal*.

**Rationale.**
- Spec 023's rule A already handles the distortion case of L4. Its chord check refuses the chord a whole tone away through a strong soft clip (`chord-experiments.md`).
- L1 aims at *one string short* without touching verifiability.
- Spec 023's B (holding partials over the strum) is not a candidate. Its record shows it raised wrong acceptances as much as right ones.

## R4. How the three levels are chosen (FR-006, FR-007, SC-004)

**Decision.** A new test, `ChordLeniencyBenchmarkTest` (tag `dataset`), judges with given settings. It sweeps the candidate configurations (each lever's options, and `CHORD_EVIDENCE` in steps of 0.05 between 0.20 and 0.50) on the owner's 42 takes and controls, with each engine.

- ***Normal*** is the configuration that meets all of these on both engines, with the fewest wrong acceptances (ties: the fewest settings changed from spec 023):
  - at least 38 accepted;
  - at most 13 wrongly accepted;
  - **per series at most the committed baseline** of `owner-chords-baseline.properties`: `owner-chords` ≤ 2 and `owner-chords-2` ≤ 11 wrong, and at least 8 and 18 accepted. The committed `OwnerChordsBenchmarkTest` fails otherwise, since it runs with the defaults, which are *Normal* (R2);
  - the guards of R5.
- ***Strict*** has fewer than 13 wrongly accepted, and at most *Normal*'s, with the most accepted.
- ***Lenient*** accepts at least as many as *Normal*, more if any configuration does, with the fewest wrong.

  The ordering of SC-004 is checked by a test on the recorded figures.

**If no configuration meets *Normal*'s conditions on both engines, implementation stops and reports the best figures to the owner.** The targets are not changed silently.

## R5. The guards (FR-003, FR-004, FR-011)

**Decision.** The figures are measured before (at commit `7372178b6`, spec 023's code) and after, with each engine, by the committed dataset benchmarks run as in spec 023 quickstart §3:
- `DatasetBenchmarkTest`;
- `GuitarSetChordBenchmarkTest`;
- `TechniqueDatasetBenchmarkTest`;
- `OwnerChordsBenchmarkTest`;
- `BassRecognitionBenchmarkTest`.

The guards are:
- every single-note and wrong-note line is not lower than before;
- Audio-Effects "chord a whole tone higher refused" stays at least 85 % (100 % at the end of spec 023);
- GuitarSet complete chords are not lower, summed or by microphone (33 and 23 of 56);
- the bass report is unchanged. A bass track has no chord rule of its own: it goes through the same `ChordMatcher`, so the bass figures are a guard.

The reports are kept in `specs/024-lenient-chords/benchmark/before-{java,native}/` and `after-{java,native}/`. A new report test, `ChordLeniencyComparisonTest`, writes `comparison.md` (before against after, both engines, each line pass or fail) and fails on any guard line lower. Spec 023's `EngineComparisonReportTest` is committed and stays as it is: it compares spec 023's own folders.

The "incomplete chord refused" lines (IDMT-SMT-Guitar 8 of 8, Audio-Effects poly 35 of 36) are not named by the spec. They are reported, and any drop is told to the owner in the final report.

## R6. The setting in the *Mode* tab (FR-005, FR-008, FR-012)

**Decision.**
- **The control.** A `ChordLeniencyPanel` (new, `TuxGuitar-practice/src/.../practice/ui/`) is a label "Chord check" and a drop-down with *Strict*, *Normal*, *Lenient*, plus *Custom* shown only while the values match no level. It goes in the *Mode* tab of `PracticeWindow`, on the row after `FollowPanel`, next to the choice of Wait, Follow or Arcade.
- **Tips.** `UIDropDownSelect` has no tooltip (spec 025 finding). The tip of each level is therefore a wrapped line under the drop-down, which shows the chosen level's text. Every text is in English, French and Spanish in `TuxGuitar-practice/share/lang/messages*.properties`.
- **Choosing a level** writes the level's values into the store's default layer (`RecognitionSettingsStore.update(getDefault().with(…))`). The store's listener refreshes the panel, and the Recognition settings window if it is open.
- **During a run** the panel is enabled. A change applies from the next run, because the session's `ChordMatcher` is built at the start (`PracticeWindow` line 865), and it never stops the run (acceptance scenario US2-5).
- **Persistence** is the store's own: the values are written to the `tuxguitar-audio-recognition` file. A level chosen is kept across restarts and updates, because its values are.
- **A learner who never chose a level:**
  - With a new installation, the defaults are *Normal*.
  - With a file from spec 023 or before, the file holds every value (`RecognitionSettings.toStrings` writes all of them). An old file therefore holds `CHORD_COMPLETENESS = all-verifiable` with 1/3 and 43 ms. That is no level, so it would show *Custom* and keep the old behaviour, against FR-008.
  - The fix is a migration in `RecognitionSettings.fromStrings`, like the legacy tempo key already there (lines 96–101): when the file has no L4 key (or, without L4, when `CHORD_COMPLETENESS` is `all-verifiable` and `CHORD_EVIDENCE` and `CHORD_CONFIRM` are their defaults), the chord completeness becomes *Normal*'s.
  - A learner who changed the evidence or the confirmation by hand keeps their values and sees *Custom*, as FR-012 asks.
  - `VERSION` stays 1 (R2). A test covers each case.
- **A device profile (spec 013)** may still overlay `CHORD_EVIDENCE` and `CHORD_CONFIRM`, as before (`ProfileParameters.LIST`). The level is derived from the default layer, the one the learner edits. The new values are not profile parameters.
- **Follow and Arcade** keep relaxing evidence and confirmation one way from the effective values (`FollowLeniency`, `ArcadeLeniency`, unchanged), so a level applies in every mode (FR-009).

## R7. Platforms and the native library

There is no native change (R0, R3), so the native library of spec 023 is used as built.

- **Validated:** macOS (Apple Silicon): unit tests, the full gate and the benchmarks with both engines.
- **Built by CI only:** Linux and Windows. They have the same Java code, so the same setting and rule (no "only on macOS" line).
- **The owner's gate:** a new test sheet, F15, played through the cable in Wait, Follow and Arcade (SC-005).
