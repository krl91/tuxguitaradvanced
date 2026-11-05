# Spec 024: the guards before and after

Written by `ChordLeniencyComparisonTest` (task T027, research R8) from `before-{java,native}/` (spec 023's code) and `after-{java,native}/` (the chord rule by profile, *Normal*). A guard line fails when it is lower than before; the two lines the spec allows lower (the owner's option B, 2026-11-05) fail under their floor only.

## Engine java

| Line | Before | After | Guard | Verdict |
|---|---|---|---|---|
| IDMT-SMT-Audio-Effects mono | wrong note +1 semitone(s) refused | 48 / 48 | 48 / 48 | not lower | pass |
| IDMT-SMT-Audio-Effects mono | wrong note +2 semitone(s) refused | 48 / 48 | 48 / 48 | not lower | pass |
| IDMT-SMT-Audio-Effects poly | chord a whole tone higher refused | 66 / 66 | 66 / 66 | at least 85 % | pass |
| IDMT-SMT-Audio-Effects poly | recognised when played | 64 / 66 | 58 / 66 | at least 85 % (allowed lower, SC-003) | pass |
| IDMT-SMT-Guitar | chord a whole tone higher refused | 16 / 16 | 16 / 16 | at least 85 % | pass |
| IDMT-SMT-Guitar | legato (simulated hammer-on or pull-off): second note recognised | 40 / 40 | 40 / 40 | not lower | pass |
| IDMT-SMT-Guitar | second note recognised while the first still rings (other pitch) | 39 / 40 | 39 / 40 | not lower | pass |
| IDMT-SMT-Guitar | wrong note +1 semitone(s) refused | 32 / 32 | 32 / 32 | not lower | pass |
| IDMT-SMT-Guitar | wrong note +2 semitone(s) refused | 32 / 32 | 32 / 32 | not lower | pass |
| IDMT-SMT-Guitar | wrong note -1 semitone(s) refused | 28 / 28 | 28 / 28 | not lower | pass |
| IDMT-SMT-Guitar | wrong note -2 semitone(s) refused | 28 / 28 | 28 / 28 | not lower | pass |
| IDMT-SMT-Guitar | wrong second note refused while the first still rings | 36 / 36 | 36 / 36 | not lower | pass |
| played: IDMT-SMT-Audio-Effects mono | single note | 48 / 48 | 48 / 48 | not lower | pass |
| played: IDMT-SMT-Guitar | single note | 32 / 32 | 32 / 32 | not lower | pass |
| incomplete chords refused, both IDMT datasets | 43 / 44 | 20 / 44 | at least 20 of 44 (allowed lower, SC-003) | pass |
| GuitarSet complete chord, six string channels summed | 33 / 56 | 40 / 56 | not lower | pass |
| GuitarSet complete chord, microphone | 23 / 56 | 25 / 56 | not lower | pass |
| owner-chords, own chord accepted | 8 / 21 | 8 / 21 | at least 8 | pass |
| owner-chords-2, own chord accepted | 18 / 21 | 18 / 21 | at least 18 | pass |
| technique-report.txt | | | unchanged | pass |
| bass-recognition-2026-10-08.txt | | | unchanged | pass |

Reported, not guarded:

| Line | Before | After | | |
|---|---|---|---|---|
| IDMT-SMT-Audio-Effects mono | recognised when played | 48 / 48 | 48 / 48 | reported | - |
| IDMT-SMT-Audio-Effects poly | incomplete chord refused (one more note expected) | 35 / 36 | 16 / 36 | summed below | reported |
| IDMT-SMT-Guitar | incomplete chord refused (one more note expected) | 8 / 8 | 4 / 8 | summed below | reported |
| IDMT-SMT-Guitar | recognised when played | 47 / 48 | 48 / 48 | reported | - |
| played: IDMT-SMT-Audio-Effects poly | major seventh | 6 / 6 | 6 / 6 | reported | - |
| played: IDMT-SMT-Audio-Effects poly | major third | 6 / 6 | 6 / 6 | reported | - |
| played: IDMT-SMT-Audio-Effects poly | major triad | 5 / 6 | 5 / 6 | reported | - |
| played: IDMT-SMT-Audio-Effects poly | minor seventh | 6 / 6 | 6 / 6 | reported | - |
| played: IDMT-SMT-Audio-Effects poly | minor third | 6 / 6 | 6 / 6 | reported | - |
| played: IDMT-SMT-Audio-Effects poly | minor triad | 6 / 6 | 0 / 6 | reported | - |
| played: IDMT-SMT-Audio-Effects poly | octave | 6 / 6 | 6 / 6 | reported | - |
| played: IDMT-SMT-Audio-Effects poly | perfect fifth | 6 / 6 | 6 / 6 | reported | - |
| played: IDMT-SMT-Audio-Effects poly | perfect fourth | 6 / 6 | 6 / 6 | reported | - |
| played: IDMT-SMT-Audio-Effects poly | power chord | 6 / 6 | 6 / 6 | reported | - |
| played: IDMT-SMT-Audio-Effects poly | sus4 triad | 5 / 6 | 5 / 6 | reported | - |
| played: IDMT-SMT-Guitar | 1-E1-Major | 3 / 4 | 4 / 4 | reported | - |
| played: IDMT-SMT-Guitar | 2-E1-Minor | 4 / 4 | 4 / 4 | reported | - |
| played: IDMT-SMT-Guitar | 3-A2-Major | 4 / 4 | 4 / 4 | reported | - |
| played: IDMT-SMT-Guitar | 4-A2-Minor | 4 / 4 | 4 / 4 | reported | - |

## Engine native

| Line | Before | After | Guard | Verdict |
|---|---|---|---|---|
| IDMT-SMT-Audio-Effects mono | wrong note +1 semitone(s) refused | 48 / 48 | 48 / 48 | not lower | pass |
| IDMT-SMT-Audio-Effects mono | wrong note +2 semitone(s) refused | 48 / 48 | 48 / 48 | not lower | pass |
| IDMT-SMT-Audio-Effects poly | chord a whole tone higher refused | 66 / 66 | 66 / 66 | at least 85 % | pass |
| IDMT-SMT-Audio-Effects poly | recognised when played | 64 / 66 | 58 / 66 | at least 85 % (allowed lower, SC-003) | pass |
| IDMT-SMT-Guitar | chord a whole tone higher refused | 16 / 16 | 16 / 16 | at least 85 % | pass |
| IDMT-SMT-Guitar | legato (simulated hammer-on or pull-off): second note recognised | 40 / 40 | 40 / 40 | not lower | pass |
| IDMT-SMT-Guitar | second note recognised while the first still rings (other pitch) | 39 / 40 | 39 / 40 | not lower | pass |
| IDMT-SMT-Guitar | wrong note +1 semitone(s) refused | 32 / 32 | 32 / 32 | not lower | pass |
| IDMT-SMT-Guitar | wrong note +2 semitone(s) refused | 32 / 32 | 32 / 32 | not lower | pass |
| IDMT-SMT-Guitar | wrong note -1 semitone(s) refused | 28 / 28 | 28 / 28 | not lower | pass |
| IDMT-SMT-Guitar | wrong note -2 semitone(s) refused | 28 / 28 | 28 / 28 | not lower | pass |
| IDMT-SMT-Guitar | wrong second note refused while the first still rings | 36 / 36 | 36 / 36 | not lower | pass |
| played: IDMT-SMT-Audio-Effects mono | single note | 48 / 48 | 48 / 48 | not lower | pass |
| played: IDMT-SMT-Guitar | single note | 32 / 32 | 32 / 32 | not lower | pass |
| incomplete chords refused, both IDMT datasets | 43 / 44 | 20 / 44 | at least 20 of 44 (allowed lower, SC-003) | pass |
| GuitarSet complete chord, six string channels summed | 33 / 56 | 40 / 56 | not lower | pass |
| GuitarSet complete chord, microphone | 23 / 56 | 25 / 56 | not lower | pass |
| owner-chords, own chord accepted | 8 / 21 | 8 / 21 | at least 8 | pass |
| owner-chords-2, own chord accepted | 18 / 21 | 18 / 21 | at least 18 | pass |
| technique-report.txt | | | unchanged | pass |
| bass-recognition-2026-10-08.txt | | | unchanged | pass |

Reported, not guarded:

| Line | Before | After | | |
|---|---|---|---|---|
| IDMT-SMT-Audio-Effects mono | recognised when played | 48 / 48 | 48 / 48 | reported | - |
| IDMT-SMT-Audio-Effects poly | incomplete chord refused (one more note expected) | 35 / 36 | 16 / 36 | summed below | reported |
| IDMT-SMT-Guitar | incomplete chord refused (one more note expected) | 8 / 8 | 4 / 8 | summed below | reported |
| IDMT-SMT-Guitar | recognised when played | 47 / 48 | 48 / 48 | reported | - |
| played: IDMT-SMT-Audio-Effects poly | major seventh | 6 / 6 | 6 / 6 | reported | - |
| played: IDMT-SMT-Audio-Effects poly | major third | 6 / 6 | 6 / 6 | reported | - |
| played: IDMT-SMT-Audio-Effects poly | major triad | 5 / 6 | 5 / 6 | reported | - |
| played: IDMT-SMT-Audio-Effects poly | minor seventh | 6 / 6 | 6 / 6 | reported | - |
| played: IDMT-SMT-Audio-Effects poly | minor third | 6 / 6 | 6 / 6 | reported | - |
| played: IDMT-SMT-Audio-Effects poly | minor triad | 6 / 6 | 0 / 6 | reported | - |
| played: IDMT-SMT-Audio-Effects poly | octave | 6 / 6 | 6 / 6 | reported | - |
| played: IDMT-SMT-Audio-Effects poly | perfect fifth | 6 / 6 | 6 / 6 | reported | - |
| played: IDMT-SMT-Audio-Effects poly | perfect fourth | 6 / 6 | 6 / 6 | reported | - |
| played: IDMT-SMT-Audio-Effects poly | power chord | 6 / 6 | 6 / 6 | reported | - |
| played: IDMT-SMT-Audio-Effects poly | sus4 triad | 5 / 6 | 5 / 6 | reported | - |
| played: IDMT-SMT-Guitar | 1-E1-Major | 3 / 4 | 4 / 4 | reported | - |
| played: IDMT-SMT-Guitar | 2-E1-Minor | 4 / 4 | 4 / 4 | reported | - |
| played: IDMT-SMT-Guitar | 3-A2-Major | 4 / 4 | 4 / 4 | reported | - |
| played: IDMT-SMT-Guitar | 4-A2-Minor | 4 / 4 | 4 / 4 | reported | - |

Known limit (research R8): IDMT-SMT-Audio-Effects "minor triad" is one recording, G2 B♭2 D3, whose low semitone bands leak into their neighbours; the other 29 triads of the NoFX folder are accepted (`stop-t027/`).

Verdict: every guard holds with both engines.
