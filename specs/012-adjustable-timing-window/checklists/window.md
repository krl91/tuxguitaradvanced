# Timing Window Requirements Quality Checklist: An adjustable timing window

**Purpose**: validate that the written requirements of spec 012 (spec, plan, tasks, contract) are clear, consistent and measurable before implementation. Standard depth, for the product owner as reviewer.
**Created**: 2026-10-15
**Feature**: [spec.md](../spec.md), [plan.md](../plan.md), [tasks.md](../tasks.md), [contracts/timing-window.md](../contracts/timing-window.md)

> **Ownership.** These boxes belong to the reviewer (the owner). `[x]` means "this requirement is written well enough"; it does not mean any work is done. The implementation reads this file and never changes a box.

## The model and the numbers

- [ ] CHK001 Is it clear to a learner what "±100 ms at 120 bpm" means, and that the real window is wider or narrower at another tempo (a fraction of the beat between a floor and a ceiling)? [Clarity, Spec §FR-004]
- [ ] CHK002 Are the floor and the ceiling of the window (0.8 W and 2.0 W) justified, or are they only the ratios of today's Normal kept for equality at 75? The plan says the second; is that acceptable to the owner? [Assumption, research R2]
- [ ] CHK003 Is the outer (early / late) window, derived at 2.6667 times the tight one, specified as a requirement the owner has seen, or only decided by the author? Spec FR-005 says it follows the tight one. [Clarity, Spec §FR-005, contract §1]
- [ ] CHK004 Is the maximum of 200 ms (half a beat at 120 bpm) defined with what it means for the judging: a note up to half a beat away counts as on time. Is that what the owner wants as the widest setting? [Measurability, Spec §FR-002]
- [ ] CHK005 Is "a bit more lenient" quantified (75 to 100 ms, a third wider) and is it the same measure the owner used when asking? [Clarity, Spec §FR-003]

## The setting and the old ones

- [ ] CHK006 **[Conflict, resolved]** The first spec said an old `normal` maps to 75; the analysis showed the store writes every key, so a saved `normal` is not a choice, and the corrected rule maps it to the new default 100. Does the owner accept that someone who really chose Normal now gets 100? [Conflict, Spec §FR-006, research R4]
- [ ] CHK007 Is it stated what the Configuration window shows for the window (the same value, with the unit "ms"), and that the old tolerance choice no longer appears there? [Completeness, Spec §FR-006, research R4]
- [ ] CHK008 Is the behaviour defined when the application is downgraded to a version that only knows the presets (the new key is ignored there; the old key was dropped when saved)? [Edge Case, Gap]
- [ ] CHK009 Is the step of the slider (5 ms) and its keyboard behaviour stated, or left to the toolkit? [Gap, tasks T011]

## Scope and non-regression

- [ ] CHK010 Is it clear that the setting is **not** shown in the Wait mode (it has no tempo) and does nothing there? [Clarity, Spec §Edge Cases]
- [ ] CHK011 Is the claim "with W = 75 every judgement equals today's" measurable by one test that compares the windows to the nanosecond, and is a run-level comparison also required (tasks T014)? [Measurability, Spec §SC-004]
- [ ] CHK012 Are the tests that relied on the default Normal listed and adapted to W = 75 explicitly (tasks T008), so that "no figure moves" is not obtained by changing the figure? [Consistency, tasks T001, T008]
- [ ] CHK013 Is the approximation of the old Strict and Lenient outer windows (under about 10 % at most tempos) written where the owner reads it, with the fact that the numbers stay provisional until real validation? [Clarity, Spec §Assumptions, research R2]

## Honesty and acceptance

- [ ] CHK014 Is SC-008 ("the owner finds the default less hard") the only measure of success for the feel, and is it said that nobody else has tried it? [Assumption, Spec §SC-008]
- [ ] CHK015 Is the "judging is deterministic" requirement tied to a test (same performance, same window, same result twice)? [Measurability, Spec §FR-009]
