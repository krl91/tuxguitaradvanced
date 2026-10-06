# Specifications status (2026-10-06)

Task counts are counted from the `tasks.md` files (`- [x]` = implemented **and** verified; a task that was only investigated stays open).

| Spec | Subject | Done | Status |
|---|---|---|---|
| 001-interactive-practice-mvp | MVP of Interactive Practice (tuner, level check, note and chord practice) | **87 / 103** | Delivered in code; 16 tasks open, none of them is an implementation gap of the MVP: manual acceptance runs not performed (T049, T054, T062, T069, T072, T075, T082, T096), specialised physical checks not performed (T078 USB interface, T079 multichannel input, T080 30-minute endurance and perceived response), T081 (build without the practice plugin), T094-T095 (owner chord clips and tuning from them), T097 (window fit bug) and **T084, the GPL release-compliance gate, explicitly deferred by the owner on 2026-10-06 (not blocking, not started)** |
| 002-practice-industrialization | One-click start, input gain / noise gate, faster recognition, technique recognition, tempo-following mode | **65 / 148** | Delivered: Phases 1-4, part of 5, the technique framework (Phase 6) and the first pass of Phases 7-8 (detectors measured, **no technique verified yet**). Not started: 9 tempo-following, 10 palm mute / tremolo picking, 11 harmonics / tapping, 12 polish |
| 003-arcade-mode | Arcade mode (play along at tempo, backing band, live guitar sound) | 0 | Specification only. Blocked by the tempo-following mode of spec 002 and by contradictory score-display requirements |
| 004-attack-difference-recognition | Notes and chords over ringing strings | **6 / 10** | Core delivered and measured on the reference recording; open: T007 (incomplete chords: independent benchmark delivered 2026-10-06, 13.2 % of incomplete chords accepted and 59.5 % of complete chords accepted on GuitarSet, owner decision needed, research R10), T008 (learner messages), T009 (delay report in the application), T010 (limit for close notes) |

## The reference recording (validation of the MVP on real material): done

`tests/1/audio_15ps.m4a` played on the score `tests/1/_TEST_verifie.tg`, with the committed event-time fixture `desktop/TuxGuitar-practice/test-data/real-recording-1/events.csv`, is the valid real-guitar reference of the MVP validation. It is complete: the owner does not have to record or repeat this general validation. The audio and the score stay local and untracked (rights); the replay test is skipped when they are absent.

Measured on it (replay test `RecordedPracticeReplayTest`, 2026-10-06, default settings), stated as measured, not as the target:

* 42 events (34 notes, 8 chords); **38 judged**, **4 events closer than 0.15 s to a neighbour are not judged** and reported as a measured speed limit;
* **35 of the 38 judged events recognised** (the 3 not recognised are chords of bass + a quiet upper note: events 8, 13 and 32);
* **0 false acceptances** in 98 negative tests (neighbouring events expected instead); never accepted before the attack;
* chords with a note missing: **2 of 12 still accepted** (FR-003 is not met yet, task T007);
* delay: median 105 ms, 90th percentile 169 ms (targets: 200 ms and 400 ms).

SC-001 of spec 004 (42 of 42 recognised) is therefore **not met**: it is 35 of 38 judged, and 4 events are outside the measured speed limit. It must not be presented as 38/38 or 42/42.

## Not performed (never to be reported as done)

Multichannel interface input (T079), 30-minute endurance and dropped-sample count (T080), direct USB interface validation (T078), the manual acceptance checklists of the user stories, the build without the practice plugin (T081), and every technique detector's `verified` flag (none meets its target).

## Not specified by Spec Kit

The steadier tuner (2026-10-06, committed with its tests), the diagnostic trace, the documentation, the Linux/Windows packaging and the release workflow.
