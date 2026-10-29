# Final report, spec 016 "Follow made more lenient: why a note is refused, the calibration offered, a time to play"

Written 2026-10-29, branch `feature/interactive-practice`, local commits only (nothing pushed): `bc0436f69` (story 1), `4c8a254cd` (story 2), `494c284de` (story 3).

## What was built
- **Story 1, why a note is refused** (T001–T014): every event that Follow does not accept on time has a reason (no attack heard, wrong pitch, unclear pitch, too early, too late, chord incomplete, not judged); the event line and the summary show it in three languages; an opt-in **record of a run** (Mode tab, *Record this run*, plus *Keep a copy of the sound too*) writes `~/tuxguitar-follow-takes/follow-<date>.csv` and `.wav`, which `FollowTakeReplayTest` replays.
- **Story 2, the calibration offered** (T017–T023): before Follow or Arcade on a route with no rhythm calibration, the start window says so and offers *Calibrate now* / *Go on without* (once per launch after *Go on without*), through the same flow as the Mode tab.
- **Story 3, a more lenient Follow** (T024–T037): the **time to play the note** (`PlayTimeWindow`, setting `recognition.follow.play-time`, 100–600 ms, default 300, in the Passages section and in the Mode tab): a note may be played up to that time after its red, and from 50 ms before it, shortened between close notes so that two events never overlap; on time up to half of it, late beyond. The timing window stays for Arcade only. The **pitch permissiveness** of Follow (`FollowLeniency`, Strict / Permissive by default / Very permissive), one way on top of the effective settings, saved apart from Arcade's. The summary and the record name both.

## What ran (2026-10-29, final state)
- `mvn -o -e clean verify -P native-modules`: green. Practice 1277 tests, 0 failures, 12 skipped (baseline T001: 1175 / 10); audio engine 377 (baseline 373); tuner 24, lib 73, gtp 10, midi 2, synth 9, editor-utils 1, compat 1, as in the baseline.
- `mvn -o test -P swt-gui-tests -pl ../../TuxGuitar-practice -am`: green, 36 SWT tests. (Two commands: the profile restricts the practice module to its SWT tests.)
- Recognition reports equal to `benchmark/before/` (revision line aside; the parameters line gains the new key; two CSV files equal once sorted). Follow benchmark on the reference recording equal to the baseline (42 events, 35 matched, 7 missed). Arcade's figures of spec 014 equal to T001's at every position.
- Tests: `git diff --name-status 60027dd1d -- '*/test/*'` shows only added files, plus `M` for `TST/ui/FollowPanelTest.java`, **the owner's exception of T028, one line** (148: `windowSlider()` → `playTimeSlider()`). `FollowPanelSwtTest` needed no change.

## What was measured
- **The owner's run (gate G1, R8)**: on `basic_test.tg` (not the reference score, the owner kept it), Rocksmith adapter, route not calibrated, 70 %: 1 of 13 accepted; of the 12 refusals, **8 no attack heard**, 3 too late (467–943 ms), 1 unclear, **no wrong or out-of-tune pitch**. Input levels −38 to −44 dBFS. The replay agrees on 12 of 13.
- **The values of the permissiveness (R9 final)**: on the reference recording every candidate, down to the bounds of the settings, keeps 35 of 38 right notes and 0 of 98 wrong notes; only a wider acceptance in cents loses a note. Chosen: Permissive 0.60 confidence / 55 ms stability / 0.22 chord evidence / 1 partial / 24 ms; Very permissive at the bounds (0.50 / 50 / 0.20 / 1 / 20); acceptance 35 cents for both. SC-004 holds (0 of 98, limit 4).
- **SC-003 is not measured**: the owner's run has no event heard at the right pitch within 300 ms after its red (0 of 0). Spec US3 scenario 1 (20 cents out of tune, 250 ms late, accepted) holds in a test with a synthetic note.

## The honest result
On the owner's guitar the refusals come mostly from **attacks that were never detected**, at a low input level. Neither the pitch permissiveness nor the time to play changes the attack detection (the contract forbids touching level thresholds). The time to play does accept a note played up to 300 ms late (100 ms before), which covers the "moment" half of the owner's complaint; whether it is enough is gate G2's question.

## Not seen or not done
- The record of G1 has an **empty route** (`route=||`): the route was not known when the recorder started; not fixed, reported.
- Event 0 of the owner's record is MATCHED in the run and NOT_JUDGED in the replay: not explained.
- The 50 ms early limit and the on-time half are the plan's choices (research R3), pinned by tests, not validated by the owner.
- Linux and Windows builds: by CI only, not run here.

## Deviations from the plan
`RhythmOffer` instead of a RHYTHM state of the start flow; the replay test lives in the `session` package (it needs `FakePlayerRun`); `window_ms` added to the record header; the recorder's sound is sized to the timeline plus 60 s, at most 10 minutes; the end hook is in `FollowRun`; `FOLLOW_PLAY_TIME` is in the Passages section (owner's choice: Notes would exceed eight rows).

## Gate G2 and what followed (2026-10-29/30)
- First run of G2: three recorded runs after the calibration showed the right notes heard but played 0.4–0.7 s after their red; the owner found it much too strict. Two defects found on the way and fixed with a reproducing test first: the rhythm calibration was always greyed out (the route had no format, `c6621f45e`) and Restart at the end of a Follow run did nothing (`41e7c6184`).
- The owner's decisions (spec, Session gate G2): the time to play is 600 ms by default, 100–1200 ms; the start window says where to set the delay by hand. Done by `/speckit-converge` T042–T047 (`caa92e184`); four existing tests adapted on the old values only, by the owner's explicit exceptions.
- **G2 passed**: the owner's run at 600 ms (Very permissive, tempo 100 %) accepted 13 of 13; "c'est bon".
- Released as `v0.7.0-beta.1` (pre-release, all four packages, version verified by the workflow).
