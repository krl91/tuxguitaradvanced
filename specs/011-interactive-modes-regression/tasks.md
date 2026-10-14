---

description: "Tasks of spec 011: functional non-regression tests of the interactive modes"
---

# Tasks: Functional non-regression tests of the interactive modes

**Input**: `specs/011-interactive-modes-regression/` (spec.md with its Clarifications of 2026-10-15, plan.md P0 to P9, research.md R1-R10, data-model.md, contracts/practice-host.md, contracts/journeys.md (38 rows), contracts/break-on-purpose.md (B1 to B17), quickstart.md)

**Prerequisites**: spec.md (US1 to US7, FR-001 to FR-016, SC-001 to SC-010). **No behaviour of the application changes**: the only production change is the seam `PracticeHost` (FR-014, SC-009).

**Tests**: this whole feature is tests. Each new test is written **before** what it needs and **seen failing** (a compile error counts as failing; say so). A journey test over behaviour that already exists cannot fail on today's code: it is then **seen failing on the old behaviour**, reinstated in a scratch copy, as listed in `contracts/break-on-purpose.md`. If a journey test reveals a **real bug**, the bug-fix protocol applies: the reproducing test first, the fix, at most 3 iterations, then report.

**Conventions**: `SRC` = `desktop/TuxGuitar-practice/src/app/tuxguitar/app/tools/custom/practice`, `TST` = `desktop/TuxGuitar-practice/test/app/tuxguitar/app/tools/custom/practice`, `SPEC` = `specs/011-interactive-modes-regression`, `BENCH` = `SPEC/benchmark`. Build commands run from `desktop/build-scripts/tuxguitar-macosx-swt-cocoa` with `export JAVA_HOME=$(/usr/libexec/java_home -v 21)`. The application must be closed before a `clean` (check with `pgrep`). Native tests open short windows on the owner's screen: keep them short.

**Rules for every phase**: commit locally at the end of the phase with **no co-author trailer** and a message that says which tests failed first; **never push** (the owner asks, one push per round); keep the build green; report exactly what ran, what was skipped and why; tick a task only when it is done and its tests ran green; after a run that rewrites `specs/003-arcade-mode/benchmark`, `git checkout specs/003-arcade-mode/benchmark`; say at each phase which Spec Kit step we are at.

## Phase 1: Setup - the baseline BEFORE any change (P0)

- [ ] T001 Record in `BENCH/build-times.txt` the state of the build **before any change**: `mvn test -pl ../../TuxGuitar-practice -am` with the total time and the test counts of each module as the logs print them (expected: practice 911 run, 4 skipped, about 9 min), and `mvn test -pl ../../TuxGuitar-audio-engine -am` (291). Note the machine, the JDK and the commit. This is the base of SC-007 (under 2 minutes added) and of every "equal before and after".
- [ ] T002 [P] Check the recognition reports of that run (`desktop/TuxGuitar-practice/target/dataset-benchmark/*`) against the base `specs/010-pre-run-check/benchmark/after/` (identical but for the `Code revision` line and the order of lines of the CSV: compare sorted); write the result in `BENCH/build-times.txt` under "recognition base". This base is what T012 and T046 compare with.
- [ ] T003 Add the profile `coverage` to `desktop/TuxGuitar-practice/pom.xml` (the JaCoCo Maven plugin, `prepare-agent` and `report` in the `test` phase, the surefire `argLine` composed with `@{argLine}` so that `swt-gui-tests` keeps `-XstartOnFirstThread`, report in `target/site/jacoco`). The profile is **not active by default**: prove it by running `mvn test -pl ../../TuxGuitar-practice -am -Dtest=QuickStartTest -Dsurefire.failIfNoSpecifiedTests=false` and checking that no `jacoco.exec` is written. Research R7.
- [ ] T004 Take the **coverage before**: `mvn test -P coverage -pl ../../TuxGuitar-practice -am`, then write `BENCH/coverage-before.txt` with lines and branches covered per package: `ui`, `session`, `arcade.core`, `arcade.ui`, `arcade.view` (from the JaCoCo CSV), and the 10 largest uncovered classes of `ui`. **If the plugin cannot be fetched or fails, write that, with the error, as a declared gap: no invented figure** (research R7). FR-016, SC-010.
- [ ] T005 Commit phase 1 locally: the pom profile and `BENCH/*` (message: what was measured; nothing failed; no behaviour change).

## Phase 2: Foundational - the seam (P1), blocks everything after it

**Goal**: the real `PracticeWindow` can be built by a test, with no change of behaviour (FR-014, SC-009). Contract: `contracts/practice-host.md`. **The seam does not grow without being declared in the task that needs it.**

- [ ] T006 [P] Write first (red) `TST/ui/flow/PracticePluginConstructionTest.java`: a `PracticePlugin` can be constructed in a test without the application (research R10.1). Red if it cannot. If it cannot, **declare** the growth: a `PluginPort` group of the seam with `windowStateChanged` and `setCommandSink`, nothing more, and write it in the task report.
- [ ] T007 [P] Write first (red) `TST/ui/PracticeHostSourceTest.java`, a source-structure test (its limit written in its comment: it checks structure, the behaviour is proved by T009 and by the harness): `PracticeWindow.java` contains none of `TGApplication.getInstance(`, `TGWindow.getInstance(`, `TuxGuitar.getInstance(`, `TGDialogUtil.openDialog(`, `AudioEngine.getInstance(`, `new EngineFollowAdapter(`; `AppPracticeHost.java` contains each of them **exactly once** (one for one with the table of research R1); the public constructor `PracticeWindow(TGContext, PracticePlugin)` still exists. Red today (compile error: the class is absent).
- [ ] T008 Implement the seam: `SRC/ui/PracticeHost.java` (package-private interface of `contracts/practice-host.md`), `SRC/ui/CaretView.java` (the value: `TGTrack track`, `TGMeasure measure`, `long position`), `SRC/ui/AppPracticeHost.java` (each line of research R1, one for one), and in `SRC/ui/PracticeWindow.java` the new package-private constructor `PracticeWindow(TGContext, PracticePlugin, PracticeHost)`; the public constructor delegates with `new AppPracticeHost(context)`; replace **only** the access sites of the table (l.173-174, 245-248, 253, 326, 511-512, 571, 847, 922, 1249; the same for `PracticePalette`'s factory and parent if the harness needs it: declare it). No other line changes. If the engine cannot be faked as `AudioEngine`, see T012 (the type of `engine()`).
- [ ] T009 **Proof of no behaviour change**: `mvn test -pl ../../TuxGuitar-practice -am` equal to `BENCH/build-times.txt` plus the new tests of T006 and T007 only, the recognition reports equal to the base of T002 (sorted diff), `mvn test -P swt-gui-tests -pl ../../TuxGuitar-practice -am` 28 SWT tests green; write the counts, `git diff --stat` of the phase and the list of the changed lines of `PracticeWindow.java` in `BENCH/seam-proof.txt`.
- [ ] T010 Commit phase 2 locally (message: which tests failed first: T006 and T007 compile errors; the seam declared; the proof of T009).

## Phase 3: Foundational - the harness (P2), blocks the journeys

**Goal**: a whole window flow runs in seconds, deterministically, with no real waiting (FR-011, research R3, R4).

- [ ] T011 Write first (red) `TST/ui/flow/WindowHarnessSmokeTest.java`: with the harness, the real `PracticeWindow` opens on the contract toolkit, the preparation lists the track of a fixture song, the Start button is enabled when the input is suitable and disabled when it is not, and **no static of the application is touched** (the test would throw). Red: the harness does not exist.
- [ ] T012 Settle the engine in the seam (research R4, "to verify"): open `desktop/TuxGuitar-audio-engine/src/main/java/app/tuxguitar/audio/engine/AudioEngine.java`; if a test can construct an `AudioEngine` with no device, `FakeEngine` wraps it and `engine()` keeps its type; **otherwise declare the growth of the seam**: a small interface `PracticeEngine` with the 8 methods used by `PracticeWindow` (`getSelection`, `setSpectrumEnabled`, `setAttackCandidates`, `subscribe`, `unsubscribe`, `setExpectedPitches`, `acquire`, `release`), implemented by an adapter over `AudioEngine` in `AppPracticeHost` and by `FakeEngine` in tests. Write the decision and its proof in `BENCH/seam-proof.txt`.
- [ ] T013 [P] Extend `TST/ui/SwtContractToolkit.java` (test code only) **where a journey needs it**: a selected value for drop-downs and tabs, `moveToTop`, `isDisposed` after `dispose`, window bounds, and a helper to read what is shown or hidden. Each extension is justified in the journey that needs it (no speculative extension).
- [ ] T014 [P] Create `TST/ui/flow/FakeHost.java` (the seam's test double: the contract toolkit's factory and a main window, no icon, a song and a caret given by the journey, an `openDialog` that packs and opens), `TST/ui/flow/FakeEngine.java` (per T012; frames are pushed by the test, as `FollowHarness` does), and `TST/ui/flow/QueuedSynchronizer.java` (a `TGSynchronizerController` that queues `executeLater` tasks and runs them when the test calls `runPending()`: no thread, no waiting).
- [ ] T015 Create `TST/ui/flow/WindowHarness.java`: builds the real window with the fake host on a fresh `new TGContext()`, gives the context's `TGSynchronizer` the queued controller, builds the environment on `TST/testsupport/FakeMidiPlayer.java` and `FakeTempoClock.java`, and offers the learner's actions (press Start, press the transport button entries `open`, `openAndStart`, `openAndStartArcade`, answer the start window, push an analysis frame, advance the fake clock) and the observations (what is shown, enabled, started). A `Performance` helper (a score from `SongFixture` and timed plays: event, offset, right or wrong pitch, silence). Research R4.
- [ ] T016 Make `WindowHarnessSmokeTest` green; run the default suite (equal plus the new tests, build time noted); commit phase 3 locally (message: T011 failed first, compile error).

## Phase 4: User Story 1 - the start of any mode (P1) MVP

**Goal**: SP-01 to SP-09 of `contracts/journeys.md` on the real window (SP-10 is in Phase 6 with the follow-up actions). **Independent test**: `mvn test ... -Dtest=StartPathJourneyTest`.

- [ ] T017 [US1] Write `TST/ui/flow/StartPathJourneyTest.java` for **SP-01, SP-02, SP-03**: from the Start button, the transport Practice button and the coloured Arcade button, for each of the three modes, the start window opens **first and alone**, the mode does not begin, and the preparation window is hidden for the two transport entries.
- [ ] T018 [US1] Add **SP-04** and **SP-05** (the two first bugs of spec 010): after the quick-start patience (driven by the queued synchronizer and the fake clock, **never waited for**) with the question unanswered, no practice window is shown; "No, open the tuner" opens the tuner through the fake tuner action **with the practice track set**, the preparation window does not appear, the tuner closing brings the question back, the mode never began. Seen failing on the old behaviour: reinstate the old line of **B1** and of **B2** in a scratch copy, see the test fail, restore; paste the failures in `BENCH/break-on-purpose-results.md` (rows B1 and B2).
- [ ] T019 [US1] Add **SP-06** and **SP-07**: "Yes" shows the strum part (meter, sentence, Cancel) and removes the question; each verdict (good, clipping, too loud, too weak, no strum) does what the contract says; *Check again* resets; *Continue anyway* after a bad verdict and after 10 seconds (fake clock) goes on **for this start only**. The headless harness checks what is shown and enabled; the **sizes** are checked natively in Phase 8 (the third bug, B3). Seen failing: **B17** (let `CLIPPING` go to `READY`) in a scratch copy.
- [ ] T020 [US1] Add **SP-08** and **SP-09**: Cancel, the close button and Escape cancel the start and a new start is accepted at once with nothing held (the engine consumer, the listeners, the tuner); a double press of Start, and the transport button while a start window is open, give one window and one start. Seen failing: **B5** and **B6** in a scratch copy.
- [ ] T021 [US1] Seen failing on the rest of the start path: **B4** (`startSession` calling `startSessionNow` directly) and **B16** (the preparation shown at once for a quick start); paste the failures (rows B4 and B16 of `BENCH/break-on-purpose-results.md`). If any journey reveals a real bug, apply the bug-fix protocol (reproducing test, fix, at most 3 iterations, report).
- [ ] T022 [US1] Run the default suite (equal plus the new tests; the added time noted against `BENCH/build-times.txt`); commit phase 4 locally (message: which tests failed first and on which reinstated old behaviour).

## Phase 5: User Story 2 - a whole run of each mode (P1)

**Goal**: WR-01 to WR-04. **Independent test**: `-Dtest=RunJourneyTest`.

- [ ] T023 [US2] Write `TST/ui/flow/RunJourneyTest.java` **WR-01** (Wait): through the real window, a known score with a performance of right, wrong, chord notes and a passage with more than half wrong; each event gets its verdict, the passage is repeated, the summary lists it as documented (the rules stay tested by `PracticeSessionTest`, `ChordPracticeTest`, `PassagePracticeTest`; this journey proves they are still **assembled**). Seen failing: **B10**.
- [ ] T024 [US2] Add **WR-02** (Follow): count-in, right, late and missed notes, a pause and a resume, the end; no frame is judged during the count-in or the pause; the end gives the state back. Seen failing: **B8**.
- [ ] T025 [US2] Add **WR-03** (Arcade): a perfect performance gives the maximum score and the full streak; a mixed one the documented score; the result window is shown with the expected figures. Seen failing: **B9**.
- [ ] T026 [US2] Add **WR-04**: the same performance with the flames Off, Discreet and Full gives identical judgements, score and result, through the window's setting. Seen failing: **B11**.
- [ ] T027 [US2] Run the default suite, note the added time; commit phase 5 locally.

## Phase 6: User Story 3 - after a run (P2)

**Goal**: FU-01 to FU-06 and SP-10. **Independent test**: `-Dtest=FollowUpJourneyTest`.

- [ ] T028 [US3] Write `TST/ui/flow/FollowUpJourneyTest.java` **FU-01** and **FU-06**: Stop brings the preparation back usable and nothing runs; at the end of every run (stop, end, close) the engine consumer is released, the listeners are removed, the tuner can open, and no window is left. Seen failing: **B12**.
- [ ] T029 [US3] Add **FU-02, FU-03, FU-04, FU-05 and SP-10**: Arcade restart, restart from the start, play again from a measure; Follow restart; the retry up to the limit while the player is still stopping and then the message; the result window closed brings a usable window; **none of them opens the start window**. Seen failing: **B7**.
- [ ] T030 [US3] Run the default suite; commit phase 6 locally.

## Phase 7: User Story 4 - interruptions and changes (P2)

**Goal**: IN-01 to IN-06. **Independent test**: `-Dtest=InterruptionJourneyTest`.

- [ ] T031 [US4] Write `TST/ui/flow/InterruptionJourneyTest.java` **IN-01 and IN-02**: the input becoming unusable while preparing, in a quick start, with a start window open and while running gives the documented reaction and never a window over an unanswered start window; a changed input refreshes the assessment and the start button.
- [ ] T032 [US4] Add **IN-03, IN-04, IN-05**: changing the track or the mode, or closing the preparation window, with a start window open cancels that start and no mode begins. Seen failing: **B13** and **B14**.
- [ ] T033 [US4] Add **IN-06**: a setting (tempo, tolerance, flames, passage) changed during a run applies to the next run only. Seen failing: **B15**.
- [ ] T034 [US4] Run the default suite; commit phase 7 locally.

## Phase 8: User Story 6 - layouts on the real toolkit (P3, tag `swt-gui`)

**Goal**: LY-01, LY-02, LY-04 (LY-03 exists). **Independent test**: `mvn test -P swt-gui-tests ... -Dtest=LayoutSwtJourneyTest`.

- [ ] T035 [US6] Write `TST/ui/flow/LayoutSwtJourneyTest.java` (`@Tag("swt-gui")`, the short-window rules of `PracticeWindowSwtTest`): the start window in each state (question, strum, bad level, tuner way back), the preparation window and its four tabs, **every visible part has a size and lies inside the window**; for **English, French and Spanish** the real texts are loaded with `TGMessagesManager.setResources` from the practice `share/lang/messages*.properties` (research R2: confirm that it accepts them; if not, report it as a gap of LY-04). Seen failing: **B3** (remove the `WindowFit.refit` of `PreRunWindow.refresh`).
- [ ] T036 [US6] Run `mvn test -P swt-gui-tests -pl ../../TuxGuitar-practice -am` (all native tests green, count noted); commit phase 8 locally.

## Phase 9: User Stories 5 and 7 - recorded baselines and the matrix (P2, P3)

- [ ] T037 [US5] Open **each existing test** named in `contracts/journeys.md` (state *existing*) and confirm it covers its row; write `BENCH/matrix-confirmation.txt` with each row as **confirmed** or **not confirmed** and why; fix the matrix rows that are not confirmed (a different test, or a **declared gap**), never the other way round. FR-013.
- [ ] T038 [US7] Write first (red) `TST/ui/flow/JourneyMatrixTest.java`: reads `SPEC/contracts/journeys.md`; every row has a test or a gap; every `Class#method` or class named exists in `TST` (a scan of the test sources, no classloading); every journey id used in the new journey tests' comments is a row. Seen failing on a scratch copy of the file with a row whose test does not exist. Header limit written in the test (a journey added to a mode without a row is found only by the owner's review).
- [ ] T039 [US5] Run the existing recorded and baseline tests (RR-01 to RR-05: `RecordedPracticeReplayTest`, `MonophonicRegressionReplayTest`, `BendRecordedReplayTest`, `OwnerChordsBenchmarkTest`, `DatasetBenchmarkTest`, `GuitarSetChordBenchmarkTest`, `SyntheticChordGuardTest`) and confirm their figures equal the base of T002; write `BENCH/recorded-baselines.txt`; **RR-06 stays a declared gap** (really played hammer-on, pull-off, palm mute, tapping, tremolo picking have no recorded-audio test). Where one of these tests can be driven through a mode of the new harness at little cost, add it; otherwise say it is not.
- [ ] T040 Commit phase 9 locally.

## Phase 10: The proof of the safety net (P8)

- [ ] T041 Run the whole break-on-purpose procedure `SPEC/contracts/break-on-purpose.md` for **B1 to B17** that were not already recorded (B1 to B6, B8 to B12, B16, B17 in phases 4 to 8): each change on a scratch state, the named test run and seen failing, restored with `git checkout -- <file>`; fill `BENCH/break-on-purpose-results.md` with the failure message of each; confirm the names of B8 to B11 and B15 against the source and correct them in the file if different. SC-002: the three bugs (B1, B2, B3) 3 of 3; SC-003: 100 % of P1 journeys and at least 80 % of P2 journeys broken on purpose once; list those not.
- [ ] T042 Add `TST/ui/flow/StabilityTest.java` (`@Tag("stability")`, excluded by default): runs the journey set 20 times in a row on fresh harnesses; run `mvn test -pl ../../TuxGuitar-practice -am -Dgroups=stability -DexcludedGroups= -Dsurefire.failIfNoSpecifiedTests=false`; expected 20 of 20, no undeclared skip. SC-006.
- [ ] T043 Take the **coverage after** (`mvn test -P coverage ...`), write `BENCH/coverage-after.txt` and the comparison with `coverage-before.txt` per package (lines and branches); **no package goes down** (SC-010); list the largest uncovered areas that remain and say for each whether a journey covers it or it is a gap of the matrix. If T004 was a gap, T043 is a gap too and says so.
- [ ] T044 Measure the **time added** to the default build: `mvn test -pl ../../TuxGuitar-practice -am`, total and the new `flow` classes' time from the surefire reports, against `BENCH/build-times.txt`; **under 2 minutes** (SC-007); if more, move the slow tests to a tag and report.
- [ ] T045 Commit phase 10 locally.

## Phase 11: Polish and closing (P9)

- [ ] T046 Full local gate: `mvn -e clean verify -P native-modules,swt-gui-tests` green with the counts of `BENCH/build-times.txt` plus the new tests (reported exactly), `mvn test -pl ../../TuxGuitar-practice -am` and `mvn test -pl ../../TuxGuitar-audio-engine -am` green, the recognition reports equal to the base of T002, `git status` clean except the untracked `tests/` and the owner's takes; `git diff --stat` empty on the recognition and judging code (`SRC/arcade/core`, `SRC/session/PracticeSession.java`, `desktop/TuxGuitar-audio-engine/.../pitch`, `.../recognition`) and the only production change is the seam (`git diff --stat` of `SRC/ui`). SC-009.
- [ ] T047 [P] Update `specs/STATUS.md` (a row for 011), `specs/011-interactive-modes-regression/checklists/requirements.md` if anything changed state, and the "Tests" section of `CLAUDE.md` with one line: the contract and matrix of the interactive modes are in `specs/011-interactive-modes-regression/contracts/journeys.md`, the commands of the tagged suites, and that a journey added to a mode needs a row.
- [ ] T048 Relaunch the built application for the owner (`target/tuxguitar-9.99-SNAPSHOT-macosx-swt-cocoa.app`, the `jre` symlink as in `CLAUDE.md`, from `tests/basic_test.tg`), after closing the previous one launched by this session.
- [ ] T049 [HUMAN] **Gate**: the owner does the smoke of `quickstart.md` step 7 on the built application (the start path as in spec 010's gate G: the question alone, *Yes* shows the strum part, a bad level blocks, *No* opens the tuner and brings the question back) and confirms that **nothing looks different** from before the seam; the answer is written in `quickstart.md` and in this task.
- [ ] T050 Write the final report in `SPEC/final-report.md`: what ran, what was skipped and why, the new tests and their count, the journeys covered and the **declared gaps** (RR-06, Windows and Linux not run), the break-on-purpose results, the coverage before and after, the time added, the seam as changed; commit locally; the push and a release are decided by the owner.

## Dependencies and order

- Phase 1 (baseline, committed alone) before everything; Phase 2 (the seam) needs Phase 1; Phase 3 (the harness) needs Phase 2; **Phases 4 to 7 need Phase 3**; Phase 8 needs Phase 3; Phase 9 needs Phases 4 to 8; Phase 10 needs Phase 9; Phase 11 needs Phase 10; T049 needs T048.
- Inside Phase 2: T006 and T007 first (red), then T008, then T009. Inside Phase 3: T011 first (red), T012 before T014, T013 and T014 in parallel, then T015, then T016.
- Phases 4, 5, 6 and 7 are independent of each other once the harness exists (different test files); they are done in that order because the order is the priority (P1 first).
- Tests are written first and seen failing: T006, T007, T011, T038 on missing code; the journey tests (T017 to T035) on the old behaviour reinstated in a scratch copy as the task says.

## Parallel examples

- After T005: T006 || T007 (different files).
- In Phase 3: T013 || T014.
- Phases 5, 6 and 7 can be written in parallel by different hands; here they are sequential commits.
- In Phase 11: T047 alone in parallel with the long build of T046.

## Implementation strategy

- **MVP = Phases 1 to 4**: the baseline, the seam, the harness and the start path journeys, which is where the three bugs were and what the owner asked to protect first. Then the whole runs (US2), then the follow-up and the interruptions, then the layouts, then the matrix and the proof.
- The seam is the only production change and is the first thing reviewed: if the owner does not like it after T009, the rest stops there (the tests cannot be built without it).
- The owner is told at each phase which Spec Kit step we are at; the whole method in order: tasks now, then checklist, analyze, implement, converge.
