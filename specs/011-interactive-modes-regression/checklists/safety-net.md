# Safety-net Requirements Quality Checklist: Functional non-regression tests of the interactive modes

**Purpose**: validate that the written requirements of spec 011 (spec, plan, tasks, contracts) are complete, clear, consistent and measurable before implementation. Standard depth, for the product owner as reviewer.
**Created**: 2026-10-15
**Feature**: [spec.md](../spec.md), [plan.md](../plan.md), [tasks.md](../tasks.md), [contracts/](../contracts/)

> **Ownership.** These boxes belong to the reviewer (the owner). `[x]` means "this requirement is written well enough"; it does **not** mean any work is done. The implementation reads the state of this file and never changes a box. Items marked **[Conflict]** or **[Gap]** are problems found while writing the checklist: each one needs a decision or a correction before `/speckit-implement`.

## The contract of the interactive modes (journeys)

- [ ] CHK001 Is the list of 38 journeys derived from a documented source of what a learner can do (the PRACTICE and MANUAL docs, the specs), or only from one reading of the code and the tests? [Traceability, Gap, contracts/journeys.md]
- [ ] CHK002 Is the **tuner opened during a running session** (the run pauses with the tuner reason, and resumes when the tuner closes) a journey? It is a behaviour of the window flow and has no row. [Gap, Spec §US4, contracts/journeys.md]
- [ ] CHK003 Are the **palette commands during a Wait run** (pause, resume, stop, restart of the session from the compact palette) journeys? Only Follow and Arcade pauses are in WR-02/WR-03. [Gap, Spec §US2]
- [ ] CHK004 Are the journeys of the **preparation window itself** listed: choosing the input, the level panel and *check level again*, the gain slider, the Passages tab, the rhythm calibration, the track list when the track under the cursor cannot be practised? Only the start path is. [Gap, Spec §US1, §US4]
- [ ] CHK005 Is "no song open" or "no track can be practised" a journey for the transport-bar buttons (nothing starts, the learner is told)? [Gap, Edge Case]
- [ ] CHK006 Is a **song changed while the window is open** covered by a journey (the track list and the prepared sequence follow, a pending start is cancelled)? [Gap, Spec §US4]
- [ ] CHK007 Is each "Behaviour that must hold" written so that two people would agree whether it holds? "The documented reaction" (IN-01) and "the documented score" (WR-03) do not say **where** it is documented. [Clarity, Ambiguity, contracts/journeys.md]
- [ ] CHK008 Are the priorities explained: why FU and IN are P2 and layouts P3, given that the three bugs found by hand were a start-path, a tuner and a layout bug (the last is P3)? [Consistency, Spec §US6]
- [ ] CHK009 Is the single declared gap **RR-06** (really played hammer-on, pull-off, palm mute, tapping, tremolo picking) accepted by the owner as a gap, and is it written what would close it (which recordings are needed)? [Assumption, Spec §US5]
- [ ] CHK010 Does the contract say what happens to a row when the learner-visible behaviour is **changed on purpose** later (the matrix and the test are updated together; who approves)? [Gap, Spec §FR-001]
- [ ] CHK011 Are the three modes treated consistently: do WR-01, WR-02, WR-03 each define the same kinds of events (right, wrong, late, missing, chord), and is "late" defined for Wait mode, which has no tempo? [Consistency, Spec §US2]

## The proof that the tests can fail

- [ ] CHK012 **[Conflict]** SC-003 asks that at least 80 % of the P2 journeys be broken on purpose once. The 19 P2 rows (WR-04, FU 6, IN 6, RR 6) have break-on-purpose entries for only FU-02, FU-06, IN-03, IN-04, IN-06 and WR-04 (6 of 19, about 32 %), and the RR rows are existing tests that this feature does not change. Is the 80 % reachable as written, or must the denominator or the target change? [Conflict, Measurability, Spec §SC-003, contracts/break-on-purpose.md]
- [ ] CHK013 Is it defined what "broken on purpose" and "seen failing" mean for a test over **behaviour that already works** (the old behaviour is reinstated in a scratch copy; a compile error counts as failing only for support code)? [Clarity, tasks.md header]
- [ ] CHK014 Are the changes of B8 to B11 and B15 specific enough to be applied by someone else? They are described as "the plan's best reading of the source". [Ambiguity, contracts/break-on-purpose.md]
- [ ] CHK015 Is it required that each break-on-purpose change makes **only the named journey's test** fail (and not the whole suite), so that the test is shown to be the one that protects the row? [Measurability, Spec §FR-010]
- [ ] CHK016 Is a safeguard written against leaving a deliberately broken production file in the tree (restoration check, a clean `git status` after each entry)? [Edge Case, Gap, tasks.md T041]
- [ ] CHK017 Is it written what happens when a journey test **passes on the broken behaviour** (the test is wrong: it is fixed, not the production code), and how many iterations are allowed? [Gap, tasks.md header]
- [ ] CHK018 Do SC-002 ("the three bugs would have been caught") and FR-010 name the same three bugs in the same words as the break-on-purpose rows B1 to B3? [Consistency, Spec §SC-002]

## The seam `PracticeHost`

- [ ] CHK019 **[Conflict]** The plan and research say the seam carries **seven** accesses; the interface in `contracts/practice-host.md` lists **eight** methods (factory, mainWindow, appIcon, song, caret, openDialog, engine, environment). Which is right? [Conflict, plan §Summary, research R1]
- [ ] CHK020 Is the rule "the seam does not grow without being declared" clear about **who approves** a growth (the owner, or only a note in the task report), and about the two growths already foreseen (`PluginPort`, `PracticeEngine`)? [Clarity, contracts/practice-host.md rule 5 and 6, tasks T006, T012]
- [ ] CHK021 **[Gap]** The proof of "no behaviour change" is the existing suites, which **do not build `PracticeWindow`**, and a source-structure test. The only check of the real window's unchanged behaviour is the owner's smoke at T049, **after all other phases**. Should an owner smoke follow the seam directly (phase 2), before anything is built on it? [Gap, Consistency, tasks T009, T049]
- [ ] CHK022 Is the limit of the source-structure test (it proves the calls moved one for one, not that the behaviour is equal) written where the owner will read it, not only in the test's comment? [Clarity, tasks T007]
- [ ] CHK023 Is it stated that `PracticePalette`, `AudioRecognitionOption`, `PracticeLauncherItem` and `PracticePlugin` also touch the application and are **out of the seam** (or in it), and that this is a decision rather than an omission? [Completeness, research R1]
- [ ] CHK024 Does FR-014 allow the seam to be reverted if the owner dislikes it after the first phase, and does the plan say what remains of the work then (tasks "Implementation strategy" says it stops)? [Dependency, Spec §FR-014]

## Coverage figure and time budget

- [ ] CHK025 Are the packages measured for the coverage figure listed in the **spec** (they are only in the plan and tasks), and is the unit (lines, branches, both) and the tool-independent definition stated? [Completeness, Spec §FR-016]
- [ ] CHK026 Are FR-016 ("no minimum, the build never fails on a figure") and SC-010 ("no package goes down") consistent: is "goes down" a failure of the feature or only a reported fact, and by how much (rounding, measurement noise)? [Consistency, Ambiguity, Spec §FR-016, §SC-010]
- [ ] CHK027 Is "under 2 minutes added" measurable: on which machine, how many runs, what variation counts as noise, and is the time of the new tests or of the whole build compared (task T044 uses both)? [Measurability, Spec §SC-007]
- [ ] CHK028 Is it written which tests are in which tagged suite (`swt-gui`, `stability`, `coverage`) and **who runs them when** ("on request and before each release": by whom, with what command, reported where)? [Clarity, research R9]
- [ ] CHK029 Is the case "the JaCoCo plugin cannot be fetched or does not work with the module's Java release" handled as a **declared gap** in the requirements, not only in a task? [Edge Case, Spec §FR-016]
- [ ] CHK030 Is the statement about **Windows and Linux** (not run, journeys platform-agnostic in principle) in the spec's success criteria or only in the Assumptions, and is it repeated in the final report requirement? [Completeness, Spec §Assumptions, tasks T050]

## Honesty of reporting (what a test cannot see)

- [ ] CHK031 Is it written what the **headless toolkit cannot see** (real sizes, real native events, focus, window stacking) and which journeys rely on the native toolkit for it (only the start window and the preparation window are planned natively)? [Clarity, research R3, Spec §US6]
- [ ] CHK032 Are the Arcade window, the result window and the palette layouts part of the layout journeys (LY-03 is "existing"), or are they gaps of the native layout check? [Coverage, contracts/journeys.md LY]
- [ ] CHK033 Does the spec say what **20 consecutive passes** proves (no flakiness on the development machine) and what it does not (nothing about other machines or real-time audio)? [Clarity, Spec §SC-006]
- [ ] CHK034 Is the owner's manual smoke defined precisely enough to be answered yes or no ("nothing looks different"): which screens, which steps, compared with what reference? [Measurability, tasks T049, quickstart step 7]
- [ ] CHK035 **[Gap]** SC-004 asks for one whole run per mode "with a recorded expected result". Do the tasks say **where** that expected result is recorded and who updates it when a behaviour changes on purpose? [Gap, Spec §SC-004, tasks T023 to T026]
- [ ] CHK036 Is the final report required to list the tests that **skipped** and the reason (owner's takes, datasets absent), and the journeys whose "existing" test was **not confirmed** to cover its row? [Completeness, Spec §FR-012, §FR-015, tasks T037, T050]
- [ ] CHK037 Does the plan say that a successful run of the whole safety net is **not** a proof that the application is free of bugs (it was green three times while three bugs were there), so that the owner's own use of the built application stays part of every release? [Assumption, Spec §Why this exists]

## Notes

- Seven of these items ask about **problems found while writing** (CHK012, CHK019, CHK021, CHK035 are the strongest): they are corrections to make in the spec, plan or tasks before implementation, not just questions.
- The analysis (`/speckit-analyze`) reads these items as input; it does not tick them.

## Resolution notes (added by the analysis of 2026-10-15; the boxes above stay the reviewer's)

- CHK012 (SC-003 80 % of P2): the conflict was real (6 of 19); the denominator is now the 13 P2 journeys whose test is new and the procedure has 24 entries covering 13 of 13 (spec SC-003, `contracts/break-on-purpose.md`).
- CHK019 (seven against eight): real but small; the seam is now declared as **twelve** methods because the analysis found four more accesses (two sleeping threads, the clock, the plugin calls): `contracts/practice-host.md`.
- CHK021 (owner smoke only at the end): real; task T010 is now an owner smoke right after the seam, before Phase 3.
- CHK035 (where the expected result of a run is recorded): task T023 now names the committed file `TST/ui/flow/expected-runs.properties`.
