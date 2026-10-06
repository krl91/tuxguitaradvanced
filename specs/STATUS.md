# Specifications status (2026-10-06)

Task counts are counted from the `tasks.md` files (`- [x]` = implemented **and** verified; a task that was only investigated stays open).

| Spec | Subject | Done | Status |
|---|---|---|---|
| 001-interactive-practice-mvp | MVP of Interactive Practice (tuner, level check, note and chord practice) | **87 / 103** | Delivered in code; 16 tasks open, none of them is an implementation gap of the MVP: manual acceptance runs not performed (T049, T054, T062, T069, T072, T075, T082, T096), specialised physical checks not performed (T078 USB interface, T079 multichannel input, T080 30-minute endurance and perceived response), T081 (build without the practice plugin), T094-T095 (owner chord clips and tuning from them), T097 (window fit bug) and **T084, the GPL release-compliance gate, explicitly deferred by the owner on 2026-10-06 (not blocking, not started)** |
| 002-practice-industrialization | One-click start, input gain / noise gate, faster recognition, technique recognition, tempo-following mode | **82 / 148** | Delivered: Phases 1-4, part of 5, the technique framework (Phase 6) and the first pass of Phases 7-8 (detectors measured, **no technique verified yet**). Phase 5 (fast recognition): T004, T006, T050, T051, T052, T054 done, **T053 (recordings with the owner) and T055 (manual run in the application) not done**; the targets SC-002 and SC-004 are not met (research R3 closing). Phase 13 (test support): T126, T127, T129, T133, T135, T136 done (live engine equals the offline analysis; functional runs of fast passages, legato and every technique family); T128, T131, T137 deferred. Phase 14 (Guitar Pro regression): T144, T145, T146, T148 done (460 of 460 corpus files read by content, 0 MIDI failures; the division by zero was a tempo of 0, fixed earlier). Technique line text tested in three languages (T140); T141, T128, T131, T137 deferred; T142 not done; T143 partly (manual GUI workflow not yet run). Not started: 9 tempo-following, 10 palm mute / tremolo picking, 11 harmonics / tapping, 12 polish |
| 003-arcade-mode | Arcade mode (play along at tempo, backing band, live guitar sound) | 0 | Specification only. Blocked by the tempo-following mode of spec 002 and by contradictory score-display requirements |
| 004-attack-difference-recognition | Notes and chords over ringing strings | **9 / 10** | Core delivered and measured. Done 2026-10-06: T008 (learner messages), T009 (delay shown in the application), T010 (speed limit measured: about 0.3 s between picked notes, research R11; validated by the owner as provisional on 2026-10-06; hammer-ons and pull-offs without an attack belong to the technique phase). **Open: T007** (incomplete chords): the independent GuitarSet benchmark shows 13.2 % of chords with a note removed accepted and only 59.5 % of complete chords accepted; no setting fixes both. **Owner decision 2026-10-06: keep the current setting provisionally, no stricter setting, find another method** (research R10) |

## The reference recording (validation of the MVP on real material): done

`tests/1/audio_15ps.m4a` played on the score `tests/1/_TEST_verifie.tg`, with the committed event-time fixture `desktop/TuxGuitar-practice/test-data/real-recording-1/events.csv`, is the valid real-guitar reference of the MVP validation. It is complete: the owner does not have to record or repeat this general validation. The audio and the score stay local and untracked (rights); the replay test is skipped when they are absent.

Measured on it (replay test `RecordedPracticeReplayTest`, 2026-10-06, default settings), stated as measured, not as the target:

* 42 events (34 notes, 8 chords); **38 judged**, **4 events closer than 0.15 s to a neighbour are not judged** and reported as a measured speed limit;
* **35 of the 38 judged events recognised** (the 3 not recognised are chords of bass + a quiet upper note: events 8, 13 and 32);
* **0 false acceptances** in 98 negative tests (neighbouring events expected instead); never accepted before the attack;
* chords with a note missing: **2 of 12 still accepted** (FR-003 is not met yet, task T007);
* delay: median 105 ms, 90th percentile 169 ms (targets: 200 ms and 400 ms).

SC-001 of spec 004 (42 of 42 recognised) is therefore **not met**: it is 35 of 38 judged, and 4 events are outside the measured speed limit. It must not be presented as 38/38 or 42/42.

## Decisions of the owner (2026-10-06)

* The limit of about 0.3 s between picked notes is a provisional measured limit, not a goal; the acceptance re-arm is not reduced globally until the risk of counting one attack twice is solved.
* T007 stays open with the current setting and its documented figures; a stricter setting that loses almost half of the complete chords is refused; the target is another method that tells a complete chord from one with a string missing.
* Hammer-ons and pull-offs without a new attack are handled in the technique-recognition phase (spec 002, Phase 7).
* The GPL release-compliance task (T084 of spec 001) stays deferred.

* SC-002 and SC-004 keep their original targets as product goals not yet met; 3 notes per second at about 90 % and 4 identical repeats per second at about 80 % are documented as the current measured performance, not as requirements.
* T053 (recordings with the owner) is deferred and the owner is not to be asked for new recordings for now; T055 stays open until a manual validation with the guitar.

## Not performed (never to be reported as done)

Multichannel interface input (T079), 30-minute endurance and dropped-sample count (T080), direct USB interface validation (T078), the manual acceptance checklists of the user stories, the build without the practice plugin (T081), and every technique detector's `verified` flag (none meets its target).

## Not specified by Spec Kit

The steadier tuner (2026-10-06, committed with its tests), the diagnostic trace, the documentation, the Linux/Windows packaging and the release workflow.
