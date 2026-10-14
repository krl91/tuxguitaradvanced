# Implementation Plan: Functional non-regression tests for the interactive modes

**Branch**: `feature/interactive-practice` | **Date**: 2026-10-15 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/011-interactive-modes-regression/spec.md` (clarified 2026-10-15)

## Summary

The rules inside the three modes are well tested; what is not tested is the **assembly the learner meets**: the controlling code of the practice window (`PracticeWindow`), which no test can build because it reaches into the whole application. Three real bugs of spec 010 lived exactly there and were found by hand. The plan:

1. a **small declared seam**, `PracticeHost` (twelve small methods: eight accesses to the application, two for time, two for the plugin; default implementation identical to today's code), so that the real window can be built by a test (owner's decision A);
2. a **harness** that builds the real window on the existing headless toolkit with a fake engine, a fake player and a queued synchronizer, so that whole journeys run in seconds with no waiting;
3. **journey tests** for the start path, the whole run of each mode, the follow-up actions and the interruptions; a **layout** check on the real toolkit (tagged);
4. a **contract and coverage matrix** (`contracts/journeys.md`) checked against the tests, with declared gaps;
5. a **procedure to break each P1 journey on purpose** (and the three bugs) to prove the tests fail;
6. a **JaCoCo coverage figure** per interactive package, before and after, without a minimum, in a separate profile (owner's decision B);
7. everything inside **2 minutes** of added default build time, the rest in tagged suites (owner's decision A).

No recognition code, no mode rule and no screen changes. The only production change is the seam.

## Technical Context

**Language/Version**: Java; the practice module compiles at release 11 (TarsosDSP is Java 11 bytecode), older modules at 9; JDK 21 to build (`CLAUDE.md`).

**Primary Dependencies**: JUnit 5 and surefire 3.2.5 (present); the engine test-jar (`tuxguitar-audio-engine` `test-jar`, present, gives `SyntheticSignals`, `StressInput`); the SWT toolkit in the profile `swt-gui-tests` (present); **JaCoCo Maven plugin (new, profile `coverage` only)**.

**Storage**: files only: the matrix and the procedures in `specs/011-.../contracts/`, the coverage figures in `specs/011-.../benchmark/`.

**Testing**: the default suite (`mvn test -pl ../../TuxGuitar-practice -am` from `desktop/build-scripts/tuxguitar-macosx-swt-cocoa`), the tagged `swt-gui` and `stability` suites, the `coverage` profile.

**Target Platform**: macOS on Apple Silicon (validated here). The journeys are platform-agnostic; **Windows and Linux are not run** and the report says so.

**Project Type**: desktop application; this feature is test code plus one small production seam in the practice plugin module.

**Performance Goals**: the new tests add **under 2 minutes** to the default build (today the practice suite takes 9 min 14 s); stable over 20 consecutive runs.

**Constraints**: no behaviour change (the existing suites and the recognition figures of spec 010 stay equal); no real waiting in a test; tests skip cleanly when the owner's files or the datasets are absent; the build stays green after each phase.

**Scale/Scope**: 38 journeys in 7 groups (`contracts/journeys.md`); 12 seam methods; 4 to 6 new test classes plus a harness; 2 new tags/profiles.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

| Principle | Check | Result |
|---|---|---|
| I Preserve TuxGuitar Compatibility | no file format, no desktop core file changes | pass |
| II Extend Before Modifying Core | the seam is in the **practice plugin module** (`SRC/ui`), not in `desktop/TuxGuitar` or `common/`; the default implementation calls the same core APIs as today | pass |
| III Evidence-Based Architecture | every access of the seam cites a line of `PracticeWindow.java` (research R1); open points are listed as risks (R10), not assumed | pass |
| IV Spec-Driven Development | specify, clarify, plan done in order; tasks, checklist, analyze, implement follow | pass |
| V Incremental, Independently Testable Delivery | nine phases, each leaving the build green and reviewable (below) | pass |
| VI Real-Time Audio Correctness | no audio-thread code changes; the fake engine is test-only; the seam is called on the user-interface thread | pass |
| VII Separable, Testable Audio Logic | this feature **advances** it: the window flow becomes testable without hardware | pass |
| IX Cross-Platform Conscious, macOS-First | validated on macOS only; stated in the report | pass (declared) |
| X No Unrelated Refactoring | the seam changes only the access points of `contracts/practice-host.md`; the 34 text sites and `TGSynchronizer` stay as they are (R1, R2); no reformatting | pass |
| XI Build and Test Integrity | the build stays green after each phase; counts reported exactly before and after; the default build gains under 2 minutes | pass |
| XII Observable Acceptance and Truthful Reporting | the matrix declares its gaps; the break-on-purpose results are pasted; coverage that cannot be measured is reported as a gap | pass |
| XIII/XIV Honest guitar semantics, learner feedback | nothing about recognition semantics changes; the baselines keep the known weaknesses visible | pass |

**Post-design re-check (2026-10-15):** unchanged. The one point to watch: the seam must not grow beyond its twelve methods; any growth is declared in the task that needs it and approved by the owner. The analysis of 2026-10-15 grew it once, from eight to twelve, for two real reasons (R4: sleeping threads; R10.1: the plugin).

## Project Structure

### Documentation (this feature)

```text
specs/011-interactive-modes-regression/
├── plan.md                    # this file
├── research.md                # R1-R10
├── data-model.md              # the entities of the feature
├── quickstart.md              # how to run and check it
├── contracts/
│   ├── practice-host.md       # the seam
│   ├── journeys.md            # the contract of the modes = the coverage matrix
│   └── break-on-purpose.md    # the procedure that proves the tests can fail
├── benchmark/                 # coverage-before.txt, coverage-after.txt, build-times.txt (created by the tasks)
├── checklists/requirements.md
└── tasks.md                   # /speckit-tasks
```

### Source Code (repository root)

```text
desktop/TuxGuitar-practice/
├── pom.xml                                   # + profile `coverage` (JaCoCo); + the `stability` tag is only a JUnit tag
├── src/app/tuxguitar/app/tools/custom/practice/ui/
│   ├── PracticeHost.java                     # NEW: the seam (package-private)
│   ├── AppPracticeHost.java                  # NEW: the default, identical to today's code
│   └── PracticeWindow.java                   # CHANGED: reads the twelve methods of the host; the public constructor is unchanged
└── test/app/tuxguitar/app/tools/custom/practice/
    ├── ui/                              # NEW: the harness and the journey tests (default build)
    │   ├── WindowHarness.java                # builds the real window: fake host, queued synchronizer, fake engine, fake player
    │   ├── FakeHost.java                     # the seam's test double (a real device-less engine from FakeAudioInput, a recording `later`, the fake clock)
    │   ├── StartPathJourneyTest.java         # SP-*
    │   ├── RunJourneyTest.java               # WR-* (three modes, whole runs)
    │   ├── FollowUpJourneyTest.java          # FU-*
    │   ├── InterruptionJourneyTest.java      # IN-*
    │   └── JourneyMatrixTest.java            # CV-*: the matrix against the tests
    ├── ui/LayoutSwtJourneyTest.java     # LY-* (tag swt-gui)
    ├── ui/StabilityTest.java            # runs the journey set 20 times (tag stability)
    └── ui/SwtContractToolkit.java            # EXTENDED where a journey needs it (test code)
```

**Structure Decision.** One production change (two new files, one changed), all in `SRC/ui`, no change outside the practice module. All new tests are in a new `TST/ui` package so that the owner can see the safety net at once. The existing tests are not moved or rewritten.

## Phases (each leaves the build green; each is committed alone)

| Phase | Content | Spec Kit task kind |
|---|---|---|
| P0 | **Baseline before any change:** test counts, build time of the default suite, the recognition reports (diff base), the JaCoCo profile added and the **coverage-before** figures | measure, no behaviour change |
| P1 | **The seam:** `PracticeHost`, `AppPracticeHost`, the new `PracticeWindow` constructor; proof of no behaviour change (the existing suites equal; the plugin calls are in the seam: R10.1) | the only production change |
| P2 | **The harness:** `WindowHarness`, `FakeHost` (with the real device-less engine from `FakeAudioInput.newEngine`), the queued synchronizer, the extension of `SwtContractToolkit`; a first smoke journey (open the window, start the preparation) | test support |
| P3 | **Start path journeys SP-01..SP-10** on the real window (the area of the three bugs) | tests first, seen failing |
| P4 | **Whole runs WR-01..WR-03** (Wait, Follow, Arcade) with the flames off, discreet and full | tests |
| P5 | **Follow-up and interruptions FU-*, IN-*** | tests |
| P6 | **Layouts LY-*** on the real toolkit in three languages (tag `swt-gui`) | tests |
| P7 | **Recorded baselines RR-*** mapped through the modes where they apply; **the matrix** `contracts/journeys.md` finished and `JourneyMatrixTest` | tests + doc |
| P8 | **Break-on-purpose** run (the three bugs, every P1 journey), the **20-run stability** check, the **coverage-after** report, the build-time measure | the proof |
| P9 | `STATUS.md`, docs, the final report; commits and (when the owner asks) push | closing |

Tests are written **before** the code they protect where there is new code (P1, P2); for P3 to P7 a test is "seen failing" by the break-on-purpose of P8 and, where cheap, at once on the old behaviour of the three bugs (the old behaviour is reinstated in a scratch copy).

## Complexity Tracking

| Item | Why it is needed | Simpler alternative rejected |
|---|---|---|
| A production seam (`PracticeHost`) | the only way a test can build the real window; owner's decision A | driving the built application from outside: slower, no layout detail, not in the default build |
| A new profile `coverage` and a JaCoCo dependency | owner's decision B | no figure: the owner asked for one |
| A recorded break-on-purpose procedure instead of a mutation tool | about 15 changes; no tool is in the repository | adding PIT for 15 cases |
