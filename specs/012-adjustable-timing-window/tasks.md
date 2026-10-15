---

description: "Tasks of spec 012: an adjustable timing window for Follow and Arcade"
---

# Tasks: An adjustable timing window

**Input**: `specs/012-adjustable-timing-window/` (spec.md with its Clarifications of 2026-10-15, plan.md P0 to P5, research.md R1-R8, data-model.md, contracts/timing-window.md, quickstart.md)

**Prerequisites**: spec.md (US1 to US4, FR-001 to FR-012, SC-001 to SC-008). **The recognition, the latency compensation and the Wait mode do not change.**

**Tests**: written **before** the code they test and seen failing (a compile error counts; say so).

**Conventions**: `AE` = `desktop/TuxGuitar-audio-engine/src/main/java/app/tuxguitar/audio/engine`, `AET` = `desktop/TuxGuitar-audio-engine/src/test/java/app/tuxguitar/audio/engine`, `SRC` = `desktop/TuxGuitar-practice/src/app/tuxguitar/app/tools/custom/practice`, `TST` = `desktop/TuxGuitar-practice/test/app/tuxguitar/app/tools/custom/practice`, `LANG` = `desktop/TuxGuitar-practice/share/lang`, `SPEC` = `specs/012-adjustable-timing-window`. Build commands from `desktop/build-scripts/tuxguitar-macosx-swt-cocoa` with `export JAVA_HOME=$(/usr/libexec/java_home -v 21)`. The application must be closed before a `clean`.

**Rules for every phase**: commit locally at the end of the phase with no co-author trailer and a message saying which tests failed first; never push (the owner asks); keep the build green; **run the whole default practice suite (`mvn test -pl ../../TuxGuitar-practice -am`), not only the native profile, before saying a phase is green** (a lesson of 2026-10-15); `git checkout specs/003-arcade-mode/benchmark` after runs; say at each phase which Spec Kit step we are at.

## Phase 1: Baseline (P0)

- [ ] T001 Take as the baseline the figures of `specs/011-interactive-modes-regression/benchmark/build-times.txt` (default practice suite 923 tests, audio-engine 291, the recognition reports of `specs/010-pre-run-check/benchmark/after/`, native 28) and write in `SPEC/benchmark/baseline.txt` that they are reused, with the commit (`642cb90cc` or later) and the list of tests that name `TEMPO_TOLERANCE`, `Preset` or `getTolerance` (grep of `AET` and `TST`), because they are the ones the change touches.

## Phase 2: User Stories 1 and 2 - the formula and the new default (P1)

**Goal**: `TimingTolerance.ofMillis(W)`: W = 75 equals Normal exactly; W = 100 judges a note 90 ms late at 120 bpm on time. **Independent test**: `-Dtest=TimingToleranceTest`.

- [ ] T002 [US1] Write first (red) in `TST/session/TimingToleranceTest.java`: (a) `ofMillis(75)` equals `new TimingTolerance(Preset.NORMAL)` over beats 0 and 30 to 2000 ms (step 10) and gaps none, 100 ms, 150 ms, 400 ms, **to the nanosecond** (contract invariant 1); (b) at a beat of 500 ms the tight half-window is 40, 75, 100 and 200 ms for W = 40, 75, 100 and 200 (invariant 3); (c) a note 90 ms late is on time with W = 100 and late with W = 75 (invariant 4, SC-003); (d) the tight window is non-decreasing in W (invariant 2); (e) two events 150 ms apart with W = 200 have disjoint windows (invariant 5); (f) a W outside 40..200 is refused by `ofMillis` with an `IllegalArgumentException` (the setting brings it back before, invariant 6). Red: `ofMillis` does not exist (compile error).
- [ ] T003 [US1] Implement `TimingTolerance.ofMillis(int widthMillis)` in `SRC/session/TimingTolerance.java` with the formula of `contracts/timing-window.md` (the ratios 0.8, 2.0, 2.6667, 2.0 and 6.0 as named constants with a comment saying they are today's Normal); `Preset` and `TimingTolerance(Preset)` stay untouched; make T002 green.
- [ ] T004 Run `-Dtest=TimingToleranceTest,TimingContextTest,FollowSessionTest` green; commit phase 2 locally (message: T002 failed first, compile error).

## Phase 3: User Story 3 - the one setting and the old ones (P2)

**Goal**: `TEMPO_WINDOW` saved by the store, an old preset migrated, `FollowSettings` reading and writing it. **Independent test**: `-Dtest='RecognitionSettingsTest,FollowSettingsTest'`.

- [ ] T005 [US3] Write first (red) in `AET/settings/RecognitionSettingsTest.java` (and the practice-side test if one exists): `TEMPO_WINDOW` exists with default 100, bounds 40 and 200, unit "ms"; a stored map with `tempo.tolerance=strict` and no `tempo.window` gives 50, `lenient` gives 125, **`normal` gives 100** (the old default is not a choice: research R4), an absent or unreadable one gives 100; a stored `tempo.window` of 10 and of 900 gives 40 and 200 (repair, no error); a stored `tempo.window` wins over a stored `tempo.tolerance`; the old key is **not** in `RecognitionParameters.ALL` and is not reported as an unknown key by `fromStrings`; `toStrings()` writes the window and not the old key. Update the existing assertions that name `TEMPO_TOLERANCE`. Red: compile error.
- [ ] T006 [US3] Implement in `AE/settings/RecognitionParameters.java` (`TEMPO_WINDOW = new Number(PREFIX + "tempo.window", Section.NOTES, "ms", 100, 40, 200, true)`; `TEMPO_TOLERANCE` removed from `ALL` and kept as the constant `LEGACY_TEMPO_TOLERANCE_KEY` and the three value constants) and the migration in `AE/settings/RecognitionSettings.java` `fromStrings` (the rule of the contract); make T005 green; run the whole audio-engine suite (`mvn test -pl ../../TuxGuitar-audio-engine -am`).
- [ ] T007 [US3] Write first (red) in `TST/session/FollowSettingsTest.java`: `getWindowMillis()` is 100 on a fresh store; `setWindowMillis(150)` is stored through the store (a new `FollowSettings` on the same store reads 150; the store persisted it: the test persistence holds `tempo.window=150`), notifies the store's listeners once, and is bounded (10 gives 40, 900 gives 200); changing the store from outside (the Configuration window) changes `getWindowMillis()`; a change during a run does not change the window of a run already prepared (it is read once in `FollowStarter.prepare`, FR-008). Red: compile error.
- [ ] T008 [US3] Implement in `SRC/session/FollowSettings.java`: `getWindowMillis()` and `setWindowMillis(int)` over the store (`store.update(store.get().with(TEMPO_WINDOW, ms))`), the in-memory preset and `resetToleranceTo` removed, `getTolerance`/`setTolerance` removed (**the callers are `FollowPanel`, `FollowStarter` and the tests found in T001**); `FollowSettings()` (no store) keeps a window of 100; in `SRC/session/FollowStarter.java` l.107-111 build `TimingTolerance.ofMillis(settings.getWindowMillis())`. **Adapt the existing tests that relied on the default Normal** (`TST/session/FollowHarness.java`, `FakePlayerRun.java`, `FollowBenchmarkTest`, `FollowFunctionalTest`, `ArcadeRunFunctionalTest`, the replays): they set the window to **75** explicitly so that their figures do not move (SC-004); list each adapted test in the phase report. Make T007 green.
- [ ] T009 [US3] Run the whole default suite (`mvn test -pl ../../TuxGuitar-practice -am`): green, equal to `SPEC/benchmark/baseline.txt` plus the new tests, the recognition reports equal to the base; commit phase 3 locally (message: T005 and T007 failed first, compile errors; the adapted tests listed).

## Phase 4: User Story 1 - the slider (P3)

**Goal**: the preparation window shows a slider "Timing window" from 40 to 200; the Configuration window shows the same value. **Independent test**: `-Dtest='FollowPanelTest,FollowStringsTest'` and natively `-Dtest=FollowPanelSwtTest`.

- [ ] T010 [US1] Write first (red): in `TST/ui/FollowStringsTest.java` the keys `practice.follow.tolerance` (reworded), `practice.follow.tolerance.value` (with the placeholder `{0}`) and the hint exist and are not empty in the three languages with the same placeholders, and the keys `practice.follow.tolerance.strict`, `.normal`, `.lenient` are gone; in `TST/ui/FollowPanelTest.java` the panel has a slider (`windowSlider()`) with minimum 40, maximum 200, showing `settings.getWindowMillis()`; moving it calls `setWindowMillis` and updates the value text; it is enabled only when idle and shown only for Follow and Arcade; reopening the panel shows the stored value. Replace the cases that picked a preset. Red: the drop-down is still there (compile error on `windowSlider()`).
- [ ] T011 [US1] Implement in `SRC/ui/FollowPanel.java`: the drop-down becomes the toolkit slider (`UIScale`, step 5, as `GainControl` does) with a value label "±{0} ms at 120 bpm"; the hint reworded; the texts in `LANG/messages.properties`, `messages_fr.properties`, `messages_es.properties`; make T010 green and run `FollowPanelTest`, `FollowStringsTest`.
- [ ] T012 [US1] Write and make green a test that the Configuration window's panel shows the same value as `FollowSettings` (`TST/ui/RecognitionSettingsPanelTest.java`: the control for `TEMPO_WINDOW` exists in the Notes section with the unit "ms"; a change made in the preparation slider is shown there and the other way round through the store) and that no control exists for the old key. Rewrite `TST/ui/FollowPanelSwtTest.java` for the slider (a native value change reaches the setting; every visible control stays inside the window).
- [ ] T013 [US1] Run the whole default suite and `mvn test -P swt-gui-tests -pl ../../TuxGuitar-practice -am` (native 28 green); commit phase 4 locally (message: T010 failed first).

## Phase 5: User Story 4 - nothing else changed (P4)

- [ ] T014 [US4] Write `TST/session/TimingWindowParityTest.java`: the same recorded-style performance on `FakePlayerRun` judged with the window at 75 gives **identical judgements, score and result** to the digest recorded before the change (the digest is computed in the test by running the previous construction `new TimingTolerance(Preset.NORMAL)` on a `FollowSession` built directly, as `FollowFunctionalTest` does); and with the window at 100 the same performance judges the notes of 76 to 100 ms late on time (US2). It is a guard: it passes at once if the formula is right; **seen failing** by changing a ratio in a scratch copy (record it in the report).
- [ ] T015 [US4] Run the recognition and judging suites and compare with the baseline: the default practice suite, the recognition reports (9 files identical, the owner's takes 26 of 42 and 13 wrong), `FollowBenchmarkTest`, `ArcadeRunFunctionalTest`; write `SPEC/benchmark/after.txt` with an equal-or-better line per figure; any figure worse stops the work (bug-fix protocol).
- [ ] T016 Commit phase 5 locally.

## Phase 6: Polish and closing (P5)

- [ ] T017 [P] Documentation (FR-011): the section of `docs/PRACTICE.md`, `PRACTICE-FR.md`, `PRACTICE-ES.md` (the Follow and Arcade parts) and `docs/MANUAL.md`, `MANUAL-FR.md` (B5 and B6) that describe the three tolerances now describe the slider: the value in milliseconds each side at 120 beats a minute, the range 40 to 200, the default 100, that the window grows and shrinks with the tempo, that it is one saved setting also shown in the Configuration window, that an older choice is converted (strict 50, lenient 125, normal the new default), and that the numbers are provisional until validated on real play.
- [ ] T018 Full local gate: `mvn -e clean verify -P native-modules,swt-gui-tests` **and** `mvn test -pl ../../TuxGuitar-practice -am` green with the counts of the baseline plus the new tests; `git status` clean except the untracked `tests/` and the owner's takes; `git diff --stat` empty on `SRC/session/PracticeSession.java`, `SRC/arcade/core`, `AE/pitch`, `AE/recognition`.
- [ ] T019 Update `specs/STATUS.md` (a row for 012) and `SPEC/checklists/requirements.md` if anything changed state; build and relaunch the application for the owner.
- [ ] T020 [HUMAN] **The owner's trial** (SC-008; `quickstart.md` step 5) on the built application: the slider shows 100 ms; moving it and restarting keeps the value; the Configuration window shows the same value; the default feels less hard than before; the answer is written in `quickstart.md` and here.
- [ ] T021 Write the final report `SPEC/final-report.md`: what ran, what was skipped, the tests adapted, the approximations declared (the outer windows of the old Strict and Lenient are not reproduced exactly; the numbers stay provisional), the answer of T020; commit locally; the push and a release are the owner's decision.

## Dependencies and order

Phase 1 first; Phase 2 (the formula) before Phase 3; Phase 3 (the setting) before Phase 4 (the slider needs `getWindowMillis`); Phase 5 after Phase 4; Phase 6 last; T020 needs T019. Inside a phase the test task comes before its implementation task. T014 can be written once T003 is done.

## Parallel examples

T002 || T005 || T007 (different test files, no shared production file) can be written together; the implementations follow in order T003, T006, T008. T017 (docs) runs in parallel with the long build of T018.

## Implementation strategy

**MVP = Phases 1 to 3** (the formula, the default 100 and the saved setting, usable even before the slider because the Configuration window already shows the control the store builds); then the slider, then the proof that nothing else moved, then the docs and the owner's trial. The owner is told at each phase which Spec Kit step we are at; the method in order: tasks now, then checklist, analyze, implement, converge.
