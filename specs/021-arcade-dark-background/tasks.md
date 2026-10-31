---
description: "Task list for spec 021: the Arcade view keeps its dark design in every system appearance"
---

# Tasks: The Arcade view keeps its dark design in every system appearance

**Input**: `specs/021-arcade-dark-background/` (spec.md, plan.md, research.md R1–R5, quickstart.md)

**Tests**: required by FR-008. Every new behaviour gets a new check, written first and seen red. No existing test is changed, skipped or removed.

**Rules for every phase**:
- local commands use JDK 21 (`export JAVA_HOME=$(/usr/libexec/java_home -v 21); export PATH="$JAVA_HOME/bin:$PATH"`) from `CB`;
- after each Maven build, run `git checkout -- specs/003-arcade-mode/benchmark/`;
- the owner's open TuxGuitar is never touched, and the owner's system appearance is never changed;
- one commit per phase, with no Co-Authored-By trailer;
- one push per round, both refs together (`git push origin feature/interactive-practice feature/interactive-practice:master`), with no verification fetch;
- gh always targets `-R krl91/tuxguitaradvanced`; a published tag is never moved.

**Paths**:
- `CB` = `desktop/build-scripts/tuxguitar-macosx-swt-cocoa`;
- `PR` = `desktop/TuxGuitar-practice`;
- `UI` = `PR/src/app/tuxguitar/app/tools/custom/practice/arcade/ui`;
- `UIT` = `PR/test/app/tuxguitar/app/tools/custom/practice/arcade/ui`;
- `BASE` = `specs/021-arcade-dark-background/baseline.txt`.

## Format: `[ID] [P?] [Story] Description`

---

## Phase 1: Setup (baseline)

- [ ] T001 Run the full gate `mvn -o -e clean verify -P native-modules` from `CB` and record its test counts in `BASE` (expected: the spec 020 counts, 73, 1, 1, 10 (1 skipped), 2, 378, 24, 9, 1371 (12 skipped)). Then, from `CB`, run `mvn -o test -P swt-gui-tests -pl ../../TuxGuitar-practice -am -Dtest='*SwtTest' -Dsurefire.failIfNoSpecifiedTests=false` once and record the number of native tests and their result.

No foundational phase: the change needs no shared groundwork.

---

## Phase 2: User Story 1 — The Arcade view looks the same in light and dark appearance (Priority: P1) 🎯 MVP

**Goal**: the Arcade canvas paints the design's dark background (0x0B0E13, `Theme.DARK.background()`) under every frame (FR-001–FR-005).

**Independent Test**: `ArcadeDarkBackgroundSwtTest` is green on the owner's Mac in dark appearance (it was red before), and all the native tests stay green.

- [ ] T002 [US1] Write `UIT/ArcadeDarkBackgroundSwtTest.java` (tag `swt-gui`, its own small harness; `ArcadePixelAlignmentSwtTest` and `PaintedAlignment` are not changed, only reused read-only where they are package-visible). It opens a real SWT window with an `ArcadeCanvas`, paints one frame on a fake clock and copies the canvas into pixels with `GC.copyArea`. It reads points with no scene item on them, chosen from the bounds of the frame's items (R3), and the expected colour is `0B0E13` within ±6 per channel. Cases:
  - (a) the normal path, 1280×720;
  - (b) the simplified path (`forceFallback = true`), 1280×720;
  - (c) after a resize to 1600×900 and then to 800×500, the normal path;
  - (d) `getControl().getBgColor()` gives red 0x0B, green 0x0E, blue 0x13.

  Run it from `CB` with `-Dtest=ArcadeDarkBackgroundSwtTest` and see it **red**: the read colour is the system background. Record the failures and the read colours in `BASE`. Commit.
- [ ] T003 [US1] In `UI/ArcadeCanvas.java`:
  - in the constructor, after `factory.createCanvas`, create a `UIColor` once from `Theme.DARK.background()` and set it with `this.canvas.setBgColor(...)`;
  - release that colour in `dispose()`, after the canvas.

  Add a comment that cites spec 021 FR-001 and says why the canvas and not the scene (research R1). Change nothing else: not the scene, not the renderers, not `Theme`.
- [ ] T004 [US1] Run `ArcadeDarkBackgroundSwtTest` again and see it **green** (all four cases). Run all the native tests (`-Dtest='*SwtTest'`) and see them green, with the T001 count plus the new tests. Run the full gate: BUILD SUCCESS with the T001 counts. Record everything in `BASE`.
- [ ] T005 [US1] Write `docs/release-notes/v0.8.0-beta.4.md`. It says that the Arcade view keeps its dark design when the system is in light appearance (before, it came out washed out), on macOS, Linux and Windows; that the other windows still follow the system; that macOS 15 is still the minimum; and what was checked. Name the release "TuxGuitarAdvanced", in the style of `docs/release-notes/v0.8.0-beta.3.md`. Commit the phase: T003–T005, the fix, the gates and the notes.

**Checkpoint**: US1 is done locally. The light appearance itself is checked by US2 on the CI machine.

---

## Phase 3: User Story 2 — The graphical checks pass on the build machines (Priority: P1)

**Goal**: *Practice GUI tests* is green on GitHub's macOS 15 machine, which is in light appearance (FR-009, SC-003). This closes T026 and T031 of spec 020.

**Independent Test**: one green run of the workflow.

- [ ] T006 [US2] One push of the branch and master (it carries every unpushed commit since `df545e3ca`). Start *Practice GUI tests* once (`gh workflow run practice-gui-tests.yml -R krl91/tuxguitaradvanced --ref master`) and follow it to the end.
  - **Green**: record the run id and the counts in `BASE`.
  - **Red**: download the artifact `practice-gui-diagnostics` into the scratchpad and look at the pictures. Apply the bug fix protocol: a reproducing test first, at most 3 tries, one push and one run per try; then report and stop (R4). Never change a test.
- [ ] T007 [US2] Close spec 020:
  - in `specs/020-macos15-ci/baseline.txt`, record the T006 run under T031 as the fix of the cause found by T030 (spec 021);
  - in `specs/020-macos15-ci/tasks.md`, tick T026 and T031, and T027 for what it still lacked;
  - in `specs/STATUS.md`, set 020 to its final count and add the 021 line.

  Commit (the push waits for T008).

---

## Phase 4: The release v0.8.0-beta.4 (FR-010, SC-006)

- [ ] T008 Push the T007 commit (both refs; one push). Start the release: `gh workflow run release.yml -R krl91/tuxguitaradvanced --ref master -f tag=v0.8.0-beta.4`. Follow it to the end. Expected:
  - published as a pre-release with 4 files: `linux-amd64.deb`, `linux-x86_64.tar.gz`, `macos-arm64.zip`, `windows-x86_64.zip`;
  - the macOS package checks OK [1]–[6] (`H"280bd902…"`);
  - the notes of T005.

  Record the run id, the files and the checks in `BASE`. If it fails, apply the bug fix protocol (at most 3 tries) and never move a published tag.
- [ ] T009 Polish:
  - in `specs/STATUS.md`, record 021 with the release;
  - in the memories, update `pending-macos15-ci` (the GUI tests green, spec 020 closed) and add the dark-background fact where it belongs;
  - set `.specify/feature.json` back to `specs/019-score-demo-fire`, so that the next `/speckit-implement` resumes spec 019 at phases 4 to 6, and say so in the report.

  Commit; the push waits for the next round.

---

## Phase 5: Owner's check [HUMAN]

- [ ] T010 [HUMAN] Install v0.8.0-beta.4 over v0.8.0-beta.3. With the Mac in light appearance (System Settings > Appearance > Light), open the Arcade view and play a song. Check that:
  - the scene is dark, as in dark appearance;
  - the buttons under it and the other windows are light (SC-005).

  Also do the pending check of spec 020, T028: *Open Anyway* is offered at the first launch, there is no new microphone request, and the tuner hears the input.

---

## Dependencies & Execution Order

- **T001 → T002 → T003 → T004**: the test must be seen red before the fix.
- **T005** can be written any time before T006, so that the single push of T006 carries the notes.
- **T006** needs Phase 2 committed. **T007** needs T006 green. **T008** needs T007 pushed. **T009** comes after T008. **T010** needs T008 published.

## Parallel Opportunities

Few, since the work is small and sequential. T005 (release notes) is independent of T002–T004 (different file) and could be written alongside.

## Implementation Strategy

- MVP = Phase 2: the fix and its test, green locally.
- Phase 3 proves it on the light-appearance machine and closes spec 020.
- Phase 4 delivers it to the players as v0.8.0-beta.4, the same day.
