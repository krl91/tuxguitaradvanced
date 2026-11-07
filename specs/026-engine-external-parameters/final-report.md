# Spec 026: final report

**Every recognition engine value is an external parameter: 192 values that were fixed in the code are now settings, read alike by the Java and the native engine, documented, and the results unchanged with the defaults.** Written on 2026-11-07 (T041).

## What was built

- **192 new parameters in 35 new sections** (226 settings in all, with the 34 that existed), each with a stable key, a unit, a default equal to the former literal, bounds, and a name and tip in English, French and Spanish. They are the families of the inventory (`inventory.md`):
  - the engine stages (phase 3): onsets (normal and fast runs), pitch (YIN threshold, target search, tolerance), the polyphonic spectrum, the attack evidence, the chord evidence and strum clip, the input front end, the noise gate, the gain suggestion;
  - the practice session (phase 4): the acceptance of chords and notes, Wait, Follow, the player clock and latency limit, the timing windows, the Follow and Arcade relaxations, the techniques (bend, slide, vibrato, legato), the calibration, the level check and the weak-input warning.
- **Both engines read the same values.** The native engine receives the engine-stage values in a new tuning array (35 indices, `NativeParameters.tuning`) beside the unchanged `float[21]`. The library declares its layout (`tuningLayout`); a library with another layout, or without the function, is refused when it loads and the run falls back to the Java engine with the usual notice.
- **When a change applies.** A value an engine stage reads restarts the listening (the Java analysis is rebuilt, the native tuning is pushed with `configureTuning`). The session's, techniques' and calibration's values are read at the next run.
- **Ordered pairs** (`OrderedPair`, `RecognitionParameters.ORDERED_PAIRS`): an inverted pair (onset windows, candidate ranges, noise gate open/close, bend windows, calm/loud) is repaired on reading and on every change, as `note.accept` ≥ `tuner.in-tune` already was.
- **The Recognition settings window** shows the 35 sections as categories *Advanced: …* after *Advanced*, at most 7 values each with the *Restore the recommended values of this category* button.
- **The documentation** (`docs/architecture/recognition-engines.md`): every setting with its unit, default, bounds, section and when it takes effect (§4); the tuning array and its layout check (§3.4); every constant that stays fixed, grouped in 9 kinds, each with its reason (§6). The inventory lists the inline numbers that stay inline.
- **Tests keep the inventory complete**: `EngineConstantInventoryTest` (audio engine), `SessionConstantInventoryTest` (practice session and Arcade core) and the crate's `analysis::constant_inventory` fail on any number that is neither a documented parameter's default nor a documented fixed constant nor an allowed inline number; `EngineDocumentationFactsTest` checks each setting's documented facts against the code.
- **The guide, the help pages, sheet F16 and draft release notes** (`docs/release-notes/next-expert-parameters.md`).

## Each requirement

| Req. | Result | Evidence |
|---|---|---|
| FR-001 / SC-001 | **Met** in code and tests: every tuning value of the inventory is a parameter; a change is seen in a run, with each engine for the engine stages (one "takes effect" test per family). The check on the built application is the owner's (F16) | `*TakesEffectTest` (onset, pitch, polyphonic, attack, chord evidence, front end, acceptance, Wait, Follow, clock, timing, leniency, technique, calibration), run with the native library by the gate; sheet F16 (T043) |
| FR-002 / SC-004 | **Met**: the structural sizes, buffers, numerical safety, timeouts, clock, score and display values and the calibration exercise stay fixed; 224 fixed constants listed in §6 with their reason, 175 inline numbers in the inventory | DOC §6; `EngineConstantInventoryTest`, `SessionConstantInventoryTest`, crate `constant_inventory` |
| FR-003 | **Met**: key, meaning, unit, default, bounds for each; repairs as the existing settings (default or nearest bound), plus the ordered pairs | `*ParametersTest` per family, `RecognitionSectionsTest` |
| FR-004 | **Met**: one parameter per value shared by a Java stage and its native port; the native indices follow the Java keys | `NativeTuningTest`, `*ParityTest`, `*TakesEffectTest` (both engines) |
| FR-005 | **Met**: kept with the recognition settings, shown in the window, a name and tip in three languages ending with the expert sentence | `RecognitionStringsAllLanguagesTest`, `RecognitionSettingsPanelTest`, `RecognitionControlsTest` |
| FR-005a / SC-007 | **Met** at the 8-row budget of every category; the real window at 768 pixels is checked by the SWT run (T042) | `RecognitionControlsTest.everySectionFitsWithoutScrolling`, `RecognitionSettingsFitSwtTest` (T042) |
| FR-006 / SC-002 | **Met**: with each engine, every dataset report equals the phase 1 baseline; the only changed line is the settings list printed by the GuitarSet report, which gains the 192 keys at their defaults (none changed or removed) | `benchmark/before-*`, `compare.sh`, gates of phases 2–6 |
| FR-007 / SC-005 | **Met**: a file of v0.8.0-beta.7 and one of the end of spec 024 keep every stored value; the new keys take their defaults | `RecognitionSettingsMigrationTest` |
| FR-008 / SC-003 | **Met**: 226 of 226 settings documented with their facts; the check is green | `EngineDocumentationTest`, `EngineDocumentationFactsTest` |
| FR-009 | **Met**: the tuning array is a new interface beside the `float[21]`; a library without it is refused and the Java engine is used | `NativeLayoutCheckTest`, `AudioEngineTuningPushTest` |
| FR-010 | **Met, with a deviation from research R6** (below): the loop never allocates, locks or waits | crate tests `the_polyphonic_values_at_their_upper_bounds_allocate_nothing_in_the_loop`, the attack and front-end ones (`no_allocation.rs` harness) |
| FR-011 | **Met in code**: the same code, settings, defaults and documentation on the three systems; the Linux build is checked by the Ubuntu CI, Windows only by the release job | gate; CI (T040) |
| SC-006 | See *Gates* | — |

## Deviations and points for the owner

- **Research R6**: the stages are not allocated at their upper bound when the engine opens. As `configure` does since spec 023, `configureTuning` builds the new pipeline (its buffers sized by the new values) on Java's thread, and the real-time loop only swaps it in. The loop never allocates (crate tests), which is R6's aim, without the memory of every buffer at its bound.
- **Native record capacity 4096 → 8192 floats** (T016): with the harmonics and the attack frequency at their highest, a record carrying the spectrum and the attack evidence did not fit. `frame_layout.txt`, `frame.rs` and `FrameRecords.java` changed alike.
- **A stale native library is refused** (FR-009), so a build that mixes an older library falls back to the Java engine; the packages carry their own library.
- **Follow ranges widened** (T022): `follow.onset-memory` minimum 500 → 20 ms and `follow.history` minimum 400 → 100 ms, since no value of the former ranges changed anything. Defaults unchanged. *To confirm by the owner.*
- **`bend.edge` minimum 1** (T026) instead of 0: 0 hops reads the median of nothing. *To confirm by the owner.*
- **The calibration texts** say "under the required rate" instead of "under the 60 % mark", since the rate is now a setting (T027). *To confirm by the owner.*
- **The latency limit** (T023) bounds the manual offset and the calibration median; stored offsets are read up to 3000 ms, so lowering the limit never drops a saved calibration.
- **`BEND_FRAMES_MAX`** became 800, in proportion to the bend window maximum (T021).
- **The tests of phases 3 and 4** were seen red only by compilation (the parameters did not exist), not by a wrong value.
- **The Spanish help page** had three row names of an older wording, now the current ones, and gains the row it lacked (T036). The two onset *pre-last* tips now say which way the value goes.
- **The GuitarSet report's settings line** gains the new keys at each gate: it prints every setting, so it is the expected difference of `compare.sh`.

## Gates

| Gate | Result |
|---|---|
| Phase 1 (T001–T002), baseline | see `baseline.txt` and `benchmark/README.md` |
| Phase 2 (T012) | green, reports identical |
| Phase 3 (T019) | green; reports identical but the settings line (60 keys added) |
| Phase 4 (T028) | green (practice 1,546 tests, 0 failures, 12 skipped); reports identical but the settings line (192 keys added) |
| Phase 5 (T034) | green (practice 1,547, 0 failures, 12 skipped; engine 760, 0 failures, 1 skipped; crate 64 passed, 4 ignored); same reports |
| Final (T040), 2026-11-07 | green: `clean verify -P native-modules` build success (practice 1,547 tests, 0 failures, 12 skipped; engine 762, 0 failures, 1 skipped, with the 2 migration tests); the native tests with the library green, no class without a test run; with the Java engine and with the native engine, the dataset reports (40 practice and 6 engine tests, 9 skipped for missing local files) equal the phase 1 baseline but the settings line: 192 keys added, none changed or removed |
| SWT (T042) | run in T042, the Mac unlocked |
| CI | Ubuntu and macOS dispatched on the branch after the push of T042 |

The three wall-clock timing tests stay out of the CI runs only (`.github/ci-timing-excludes.txt`, owner's decision of 2026-11-06, option A); they run in every local gate. Windows has no CI build outside the release workflow: it is verified when that job runs for the release.

## Open

- **T043**: sheet F16, the owner's gate (SC-001 on the built application, SC-007 on the screen).
- The three points *to confirm by the owner* above.
