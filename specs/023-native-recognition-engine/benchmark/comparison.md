# Java and native engines on the datasets (spec 023)

Written by `EngineComparisonReportTest` from `before/` (the Java engine before the spec), `after-java/` and `after-native/`.
Result: every line passes.

## SC-001: the native engine at least the baseline on each line

| Line | Before | Java after | Native after | Native |
|---|---|---|---|---|
| IDMT-SMT-Audio-Effects mono / recognised when played (% OK) | 100 | 100 | 100 | pass |
| IDMT-SMT-Audio-Effects mono / wrong note +1 semitone(s) refused (% OK) | 100 | 100 | 100 | pass |
| IDMT-SMT-Audio-Effects mono / wrong note +2 semitone(s) refused (% OK) | 100 | 100 | 100 | pass |
| IDMT-SMT-Audio-Effects poly / chord a whole tone higher refused (% OK) | 66.7 | 100 | 100 | pass |
| IDMT-SMT-Audio-Effects poly / incomplete chord refused (one more note expected) (% OK) | 97.2 | 97.2 | 97.2 | pass |
| IDMT-SMT-Audio-Effects poly / recognised when played (% OK) | 95.5 | 97 | 97 | pass |
| IDMT-SMT-Guitar / chord a whole tone higher refused (% OK) | 100 | 100 | 100 | pass |
| IDMT-SMT-Guitar / incomplete chord refused (one more note expected) (% OK) | 100 | 100 | 100 | pass |
| IDMT-SMT-Guitar / legato (simulated hammer-on or pull-off): second note recognised (% OK) | 100 | 100 | 100 | pass |
| IDMT-SMT-Guitar / recognised when played (% OK) | 97.9 | 97.9 | 97.9 | pass |
| IDMT-SMT-Guitar / second note recognised while the first still rings (other pitch) (% OK) | 97.5 | 97.5 | 97.5 | pass |
| IDMT-SMT-Guitar / wrong note +1 semitone(s) refused (% OK) | 100 | 100 | 100 | pass |
| IDMT-SMT-Guitar / wrong note +2 semitone(s) refused (% OK) | 100 | 100 | 100 | pass |
| IDMT-SMT-Guitar / wrong note -1 semitone(s) refused (% OK) | 100 | 100 | 100 | pass |
| IDMT-SMT-Guitar / wrong note -2 semitone(s) refused (% OK) | 100 | 100 | 100 | pass |
| IDMT-SMT-Guitar / wrong second note refused while the first still rings (% OK) | 100 | 100 | 100 | pass |
| Chorus / chorus (% OK) | 90.4 | 100 | 100 | pass |
| Distortion / distortion (% OK) | 86.5 | 96.2 | 96.2 | pass |
| EQ / amplifier simulation (% OK) | 90.4 | 98.1 | 98.1 | pass |
| FeedbackDelay / feedback delay (% OK) | 92.3 | 100 | 100 | pass |
| Fender Strat Clean Neck SC (% OK) | 98.5 | 98.5 | 98.5 | pass |
| Fender Strat Clean Neck SC Chords (% OK) | 95 | 95 | 95 | pass |
| Flanger / flanger (% OK) | 92.3 | 100 | 100 | pass |
| Ibanez Power Strat Clean Bridge HU (% OK) | 100 | 100 | 100 | pass |
| Ibanez Power Strat Clean Bridge HU Chords (% OK) | 100 | 100 | 100 | pass |
| Ibanez Power Strat Clean Bridge+Neck SC (% OK) | 100 | 100 | 100 | pass |
| Ibanez Power Strat Clean Neck HU (% OK) | 100 | 100 | 100 | pass |
| NoFX / no effect (% OK) | 98.1 | 100 | 100 | pass |
| IDMT-SMT-Audio-Effects mono / single note (% OK) | 100 | 100 | 100 | pass |
| IDMT-SMT-Audio-Effects poly / major seventh (% OK) | 100 | 100 | 100 | pass |
| IDMT-SMT-Audio-Effects poly / major third (% OK) | 100 | 100 | 100 | pass |
| IDMT-SMT-Audio-Effects poly / major triad (% OK) | 66.7 | 83.3 | 83.3 | pass |
| IDMT-SMT-Audio-Effects poly / minor seventh (% OK) | 100 | 100 | 100 | pass |
| IDMT-SMT-Audio-Effects poly / minor third (% OK) | 100 | 100 | 100 | pass |
| IDMT-SMT-Audio-Effects poly / minor triad (% OK) | 100 | 100 | 100 | pass |
| IDMT-SMT-Audio-Effects poly / octave (% OK) | 100 | 100 | 100 | pass |
| IDMT-SMT-Audio-Effects poly / perfect fifth (% OK) | 100 | 100 | 100 | pass |
| IDMT-SMT-Audio-Effects poly / perfect fourth (% OK) | 100 | 100 | 100 | pass |
| IDMT-SMT-Audio-Effects poly / power chord (% OK) | 100 | 100 | 100 | pass |
| IDMT-SMT-Audio-Effects poly / sus4 triad (% OK) | 83.3 | 83.3 | 83.3 | pass |
| IDMT-SMT-Guitar / 1-E1-Major (% OK) | 75 | 75 | 75 | pass |
| IDMT-SMT-Guitar / 2-E1-Minor (% OK) | 100 | 100 | 100 | pass |
| IDMT-SMT-Guitar / 3-A2-Major (% OK) | 100 | 100 | 100 | pass |
| IDMT-SMT-Guitar / 4-A2-Minor (% OK) | 100 | 100 | 100 | pass |
| IDMT-SMT-Guitar / single note (% OK) | 100 | 100 | 100 | pass |
| GuitarSet complete chord, six string channels summed accepted | 33 | 33 | 33 | pass |
| GuitarSet complete chord, microphone accepted | 23 | 23 | 23 | pass |
| Owner's chords accepted (of 42) | 26 | 26 | 26 | pass |
| Owner's neighbouring chords wrongly accepted (of 42) | 13 | 13 | 13 | pass |
| Technique VI detected (%) | 59 | 59 | 59 | pass |
| Technique VI wrongly detected on plain notes (%) | 0.5 | 0.5 | 0.5 | pass |
| Technique BE detected (%) | 82 | 82 | 82 | pass |
| Technique BE wrongly detected on plain notes (%) | 3.7 | 3.7 | 3.7 | pass |
| Technique SL detected (%) | 48 | 48 | 48 | pass |
| Technique SL wrongly detected on plain notes (%) | 1.3 | 1.3 | 1.3 | pass |
| Bass B0 (midi 23) right (%) | 0 | 0 | 0 | pass |
| Bass C1 (midi 24) right (%) | 0 | 0 | 0 | pass |
| Bass E1 (midi 28) right (%) | 0 | 0 | 0 | pass |
| Bass F1 (midi 29) right (%) | 0 | 0 | 0 | pass |
| Bass F#1 (midi 30) right (%) | 0 | 0 | 0 | pass |
| Bass G1 (midi 31) right (%) | 100 | 100 | 100 | pass |
| Bass A1 (midi 33) right (%) | 100 | 100 | 100 | pass |
| Bass B1 (midi 35) right (%) | 100 | 100 | 100 | pass |
| Bass D2 (midi 38) right (%) | 100 | 100 | 100 | pass |
| Bass E2 (midi 40) right (%) | 100 | 100 | 100 | pass |
| Bass G2 (midi 43) right (%) | 100 | 100 | 100 | pass |

## SC-001: tests OK with Java and KO with the native engine

None.

## SC-002: offline delay, GuitarSet chords (median)

| Line | Before | Java after | Native after | Limit | Native |
|---|---|---|---|---|---|
| complete chord, six string channels summed | 103 ms | 103 ms | 103 ms | ≤ 103 ms and ≤ Java | pass |
| complete chord, microphone | 149 ms | 149 ms | 149 ms | ≤ 149 ms and ≤ Java | pass |

## FR-007: the Java engine's outcome files against before/

| File | Java after |
|---|---|
| outcomes.csv | 23 lines KO before, OK on both engines (US3, both engines) |
| guitarset-chords-outcomes.csv | identical |

## SC-004: chord targets of the native engine (aimed at, not blocking)

| Target | Native | Status |
|---|---|---|
| Owner's chords accepted ≥ 34/42 | 26 | not reached |
| Owner's neighbours wrongly accepted ≤ 6/42 | 13 | not reached |
| GuitarSet complete chords ≥ 40/56 summed | 33 | not reached |
| GuitarSet complete chords ≥ 30/56 microphone | 23 | not reached |
| Chord a whole tone higher refused ≥ 85 % (Audio-Effects) | 100 | reached |
