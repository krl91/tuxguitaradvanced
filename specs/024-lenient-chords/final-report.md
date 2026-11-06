# Spec 024: final report

**Lenient chords: every chord the owner plays right is accepted, a clearly wrong chord is always refused.** Written on 2026-11-06 (T044), overnight, at the owner's request: everything except the native SWT runs, which wait for the owner (see "Open").

## What was built

- **The chord rule by pitch-class profile** (phase 4). A chord is judged by the note names heard together over the strum, whatever the voicing. It is the default rule (`recognition.chord.completeness=profile`), in Java, on the semitone bands that both engines give: no JNI change, no change to the crate. It applies in Wait (`PracticeSession`), Follow and Arcade (`FollowSession`).
- **Its settings are external parameters** (FR-013): `recognition.chord.profile.match`, `recognition.chord.profile.names`, `recognition.chord.attack`, `recognition.chord.detail.top` and `recognition.chord.detail.settle`. They are read at the next start and shown in the Recognition settings window, with the help pages in three languages.
- **Three chord check levels** in the *Mode* tab (phase 5): *Strict*, *Normal* (the defaults) and *Lenient*, plus *Custom* while the settings hold no level.
  - The level is derived from the settings (`ChordLeniency.of`) and never stored.
  - Choosing a level writes three values: the rule, the match and the attack. A change made by hand shows *Custom*.
  - A file saved with spec 023's chord values is read as *Normal* (the `ChordLeniency` history).
- **The engines documented** (FR-014): `docs/architecture/recognition-engines.md`, kept complete by `EngineDocumentationTest`.
- **The practice guide** in three languages, **sheet F15** and **draft release notes** (`docs/release-notes/next-lenient-chords.md`).

## Each requirement

| Req. | Result | Evidence |
|---|---|---|
| FR-001 / SC-001 | **Met**: 25 of 25 takes of the reference series accepted with the defaults (17 at the end of spec 023), both engines | `OwnerChordsProfileTest`; `benchmark/levels.md` (Normal) |
| FR-002 / SC-002 | **Met**: 0 of 408 clearly wrong accepted with *Normal*, both engines. One name off: 6 of 138. *Strict* accepts 0 | `OwnerChordsProfileTest`; `benchmark/levels.md` |
| FR-003 / SC-003 | **Met, with the two lines the owner allowed lower** (option B, 2026-11-05). Single notes and wrong notes unchanged, the whole tone higher 100 % refused, techniques and bass unchanged. Lower, above their floors: incomplete chords refused 43 → 20 of 44 (floor 20); IDMT poly recognised 64 → 58 of 66 (floor 85 %) | `benchmark/comparison.md` (both engines) |
| FR-003a | **Met**: the first two series keep 8 and 18 | `benchmark/comparison.md` |
| FR-004 | **Met, better**: GuitarSet complete chords 33 → 40 of 56 (summed) and 23 → 25 of 56 (microphone) | `benchmark/comparison.md` |
| FR-005 | **Met**: *Chord check* in the *Mode* tab under the mode choice, three levels with a line saying what each accepts and risks, in three languages. The native check of the panel waits for the owner | `ChordLeniencyPanelSourceTest`; `ChordLeniencyPanelSwtTest` (open) |
| FR-006 | **Met**: *Normal* accepts every right chord, and among the settings that do, it refuses the most wrong ones (the T007 probe) | `benchmark/probe.md` |
| FR-007 / SC-004 | **Met** on the reference series, both engines: right chords 23 ≤ 25 ≤ 25 (Strict, Normal, Lenient); wrong acceptances 0 ≤ 6 ≤ 69 | `ChordLeniencyLevelsTest`; `benchmark/levels.md` |
| FR-008 | **Met**: the level is derived from the stored values, so it survives a restart; the defaults are *Normal*; a spec 023 file is read as *Normal* | `ChordLeniencyTest`, `ChordProfileMigrationTest` |
| FR-009 | **Met**: one rule in Java for both engines (identical figures everywhere), in Wait, Follow and Arcade | `FollowSessionChordProfileTest`, `PracticeSessionChordProfileTest`; both engines' reports |
| FR-010 / SC-006 | **Met** (phase 2) | `benchmark/diagnostic.md` |
| FR-011 | **Met**: before and after for each engine | `benchmark/before-*`, `after-*`, `comparison.md`, `levels.md` |
| FR-012 | **Met**: a level writes the rule, the match and the attack, never the strum window or the chord evidence; a hand change shows *Custom* | `ChordLeniencyTest`; `ChordLeniencyPanelSwtTest` (open) |
| FR-013 | **Met**: every value added is a recognition setting read at the next start, the same for both engines | `ChordProfileSettingsTest`, `ChordProfileParametersTest`; F15 step 8 (owner) |
| FR-014 | **Met** | `docs/architecture/recognition-engines.md`, `EngineDocumentationTest` |
| SC-005 | **Open**: the owner's gate, sheet F15 (T045) | — |

## Stop conditions and deviations

- **Stop condition A** (T006) was met: the leak criterion caused it. The owner chose to drop the criterion.
- **Stop condition C** (T027) was met: incomplete chords. The owner chose option B. The approved exceptions to committed tests are listed in tasks.md and research R7:
  - `DatasetBenchmarkTest`: incomplete chord floor 0.90 → 0.45;
  - `SyntheticChordGuardTest`: baseline 43 → 77;
  - `FollowDeterminismTest`: line 230 removed;
  - `owner-chords-baseline.properties`: the `wrong.max` lines.
- **Known limit**: the minor triad on G2 (IDMT) is refused. It is a low-register semitone leak (research R8).
- **The spec's edge case "Strict refuses an incomplete chord more often" is not met.** *Strict* and *Normal* both accept 24 of the 44 incomplete chords of the datasets (*Lenient* 32). What makes *Strict* stricter is the one-name-off chords (0 of 138 against 6) and the attack path. As T030 says, the edge case is to be corrected through `/speckit-clarify` by the owner. The guide and the release notes say only what was measured.
- **T012**: "two chord candidates of one onset" cannot happen (the windows never overlap). It was tested as two onsets.

## Gates

| Gate | Result |
|---|---|
| Phase 4 (T028), 2026-11-05 23:24 | build success; 1,509 practice tests, 0 failures (after fixing 2 bugs and applying 2 approved exceptions) |
| Phase 5 (T037), 2026-11-06 00:24, without SWT | build success; 1,512 practice and 519 engine tests, 0 failures |
| Final (T043), 2026-11-06 01:00, without SWT | build success; 1,512 practice and 520 engine tests, 0 failures (`SyntheticChordGuardTest` included); then, with each engine (`-Dtuxguitar.recognition.engine=java` and `native`), `OwnerChordsProfileTest`, `ChordLeniencyLevelsTest` and `DatasetBenchmarkTest`: green |

## Open

- **The native SWT runs** (`-P swt-gui-tests`) of the whole branch, including the new `ChordLeniencyPanelSwtTest`. They need the Mac session unlocked, so they were moved to the owner's return at the owner's request (tasks.md, Phase 5 note). Nothing is pushed before they are green.
- **`/speckit-clarify`** on the *Strict* edge case (above).
- **T045**: sheet F15 (SC-005).
