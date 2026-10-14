# Data model: functional non-regression tests (spec 011)

No persistent data. The entities are the things the tests and the reports are made of.

## Journey
One thing a learner does through a mode or the start path. Fields: **id** (`SP-01`..), **group** (`SP` start path, `WR` whole run, `FU` follow-up, `IN` interruption, `LY` layout, `RR` recorded, `CV` coverage), **mode** (Wait, Follow, Arcade, or all), **behaviour** (one sentence of what must hold), **priority** (P1, P2, P3 as in the spec's stories), **protected by** (`Class#method` or a declared **gap** with its reason). Rule: every journey has a test or a gap (FR-002, SC-001).

## Contract
The file `contracts/journeys.md`: the list of journeys. It is the reference the tests are written against and the coverage matrix. Rule: checked by `JourneyMatrixTest` (every named test exists; no row without a test or a gap).

## Seam (`PracticeHost`)
The interface through which the practice window reaches the application: `factory`, `mainWindow`, `appIcon`, `song`, `caret`, `openDialog`, `engine`, `environment`. Two implementations: the application's (`AppPracticeHost`, today's behaviour) and the test's (`FakeHost`). Rule: it does not grow without a declared reason (R10.1).

## Performance
A played-back sequence that drives a run without a person: a score (a `SongFixture`) and a list of timed plays (event index, offset, right or wrong pitch, or silence). Used by the whole-run journeys. Rule: deterministic (the fake clock), same result on every run.

## Baseline
A recorded figure that a test keeps from getting worse and reports when it gets better: the owner's takes (`owner-chords-baseline.properties`), the synthetic chord guard (`SyntheticChordGuardTest`), the recorded replays, the unit counts. Rule: worse fails; better is reported and the file is raised.

## Break-on-purpose entry
A row of `contracts/break-on-purpose.md`: the journey, the **change** that breaks it (file, line, what to invert), the test that must fail, the command, and the recorded result (the failure message). Rule: P1 journeys and the three spec-010 bugs, 100 %.

## Coverage figure
Lines and branches covered per interactive package, from JaCoCo, before and after, with the largest uncovered areas. Rule: reported, never a build failure; no package goes down (SC-010).

## Journey run report
What the final report states: tests run, skipped (with the reason), new; the time added to the default build; the tagged suites that were run.
