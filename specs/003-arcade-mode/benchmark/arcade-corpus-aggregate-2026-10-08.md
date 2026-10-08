# Arcade on real scores: aggregate report (spec 003 T060 to T065)

Produced by `ArcadeCorpusProfilerTest` from a folder of Guitar Pro 3 and 4 files named by `TG_ARCADE_GP_CORPUS`. No score, title, path or image is in this file: only counts, distributions and anonymous case identifiers.

## Files

- found recursively: 48506 (23046 by extension .gp3, 25460 by extension .gp4)
- sampled by stable hash of the relative path (SHA-256): 2200 (up to 1100 of each extension)
- read (format detected from the content by the application's detectors): 2197; format shown by the content: {gp4=1104, gp3=1093}; extension and content disagree: 7
- outcome of each sampled file: {DEGRADED=1483, OK=699, PATHOLOGICAL=12, NO_ELIGIBLE_TRACK=2, UNREADABLE=3, IMPOSSIBLE_TUNING=1}
  - OK: read cleanly with at least one playable track; DEGRADED: read with measure errors (never used as a case); UNREADABLE: could not be read;
    NO_ELIGIBLE_TRACK: no pitched guitar or bass track with events; IMPOSSIBLE_TUNING: strings without a pitch; PATHOLOGICAL: tempo or duration out of range, or events out of order
- negative cases:
  - the file (4): DEGRADED, 7 playable tracks
  - the known defective file (6): DEGRADED, 3 playable tracks
- duration: profile of the sample 13.9 s (from the cache of an earlier pass: 2200 of 2200 files), second pass from nothing 72.7 s, structural check of the cases 2.0 s, in all 92.1 s

## Playable tracks of the files read cleanly: 2288

| metric | min | median | p90 | p99 | max |
|---|---|---|---|---|---|
| events | 1.0 | 360.0 | 1066.0 | 2236.0 | 2700.0 |
| chords / (notes + chords) | 0.0 | 0.1 | 1.0 | 1.0 | 1.0 |
| longest run of chords | 0.0 | 5.0 | 185.0 | 819.0 | 2172.0 |
| peak events in one second | 1.0 | 6.0 | 11.0 | 17.0 | 29.0 |
| named chords | 0.0 | 0.0 | 0.0 | 99.0 | 712.0 |
| unnamed chords | 0.0 | 26.0 | 455.0 | 1063.0 | 2287.0 |
| largest chord (notes) | 0.0 | 2.0 | 6.0 | 6.0 | 6.0 |
| largest jump of the fret centre | 0.0 | 6.0 | 13.0 | 20.0 | 27.0 |
| strings | 4.0 | 6.0 | 6.0 | 6.0 | 7.0 |
| tracks in the score | 1.0 | 5.0 | 8.0 | 12.0 | 19.0 |
| measures with a repeat sign | 0.0 | 0.0 | 8.0 | 21.0 | 63.0 |
| tempo changes | 0.0 | 0.0 | 3.0 | 12.0 | 21.0 |

## Cases

| category | tracks available | cases chosen | GP3 | GP4 |
|---|---|---|---|---|
| unusual-strings | 417 | 8 | 4 | 4 |
| large-jumps | 474 | 8 | 4 | 4 |
| chord-runs | 959 | 8 | 4 | 4 |
| fast-notes | 352 | 8 | 4 | 4 |
| multi-track-repeats-tempo | 694 | 8 | 4 | 4 |
| chord-heavy | 529 | 8 | 4 | 4 |
| mixed | 518 | 8 | 4 | 4 |
| sparse-notes | 905 | 8 | 4 | 4 |

Total 64 cases (at least 60 and five in each category: met); no folder or artist gives more than 3; one arrangement per title.

## Structural check (T062)

- 64 tracks, 2400 scenes (two anchors, five moments, FULL and PERFORMANCE, colour and monochrome), failures: 0
- identity shown for every chord event of the 64 tracks (FR-061): {WRITTEN=56, DERIVED=9999, PITCH_SET=2176} (written name, name derived from the written notes, list of notes); no chord shows a bare generic word (asserted)

## Images (T063)

- 32 images written locally (FULL and PERFORMANCE of 16 cases), each with an active target; the contact sheet is a local file for the owner's review (T044), never committed.

## Reproducibility (T065)

- the selection was made twice, the second time from nothing (every file read and measured again): the anonymous identifiers, categories and metrics of the 64 cases are identical.

## Selected cases (anonymous)

| case | category | format | metrics |
|---|---|---|---|
| C0074d88c-T3 | unusual-strings | gp3 | events 298 (notes 298, chords 0: 0 named, 0 unnamed; longest run 0; largest 0 notes), peak 3 events/s, frets 1-6 (largest jump 3), 4 strings, 11 tracks, 0 repeats, 0 tempo changes, 214 s |
| C094ece3a-T3 | unusual-strings | gp4 | events 772 (notes 772, chords 0: 0 named, 0 unnamed; longest run 0; largest 0 notes), peak 6 events/s, frets 1-6 (largest jump 5), 4 strings, 4 tracks, 0 repeats, 1 tempo changes, 213 s |
| C0634cdfa-T3 | unusual-strings | gp3 | events 582 (notes 582, chords 0: 0 named, 0 unnamed; longest run 0; largest 0 notes), peak 6 events/s, frets 2-7 (largest jump 5), 4 strings, 4 tracks, 0 repeats, 0 tempo changes, 174 s |
| C04fbe8f2-T4 | unusual-strings | gp4 | events 689 (notes 685, chords 0: 0 named, 0 unnamed; longest run 0; largest 0 notes), peak 6 events/s, frets 2-9 (largest jump 4), 4 strings, 4 tracks, 0 repeats, 0 tempo changes, 218 s |
| C01afc57a-T3 | unusual-strings | gp3 | events 536 (notes 536, chords 0: 0 named, 0 unnamed; longest run 0; largest 0 notes), peak 6 events/s, frets 2-10 (largest jump 5), 4 strings, 4 tracks, 0 repeats, 0 tempo changes, 248 s |
| C0489be9a-T3 | unusual-strings | gp4 | events 240 (notes 227, chords 13: 0 named, 13 unnamed; longest run 1; largest 2 notes), peak 4 events/s, frets 1-12 (largest jump 11), 4 strings, 5 tracks, 0 repeats, 2 tempo changes, 308 s |
| C09d86f84-T7 | unusual-strings | gp3 | events 686 (notes 673, chords 13: 0 named, 13 unnamed; longest run 1; largest 2 notes), peak 7 events/s, frets 1-7 (largest jump 4), 4 strings, 8 tracks, 0 repeats, 0 tempo changes, 408 s |
| C04af4307-T3 | unusual-strings | gp4 | events 613 (notes 613, chords 0: 0 named, 0 unnamed; longest run 0; largest 0 notes), peak 7 events/s, frets 2-10 (largest jump 8), 5 strings, 4 tracks, 0 repeats, 0 tempo changes, 218 s |
| C077ffee6-T1 | large-jumps | gp3 | events 851 (notes 157, chords 533: 0 named, 533 unnamed; longest run 87; largest 5 notes), peak 6 events/s, frets 1-13 (largest jump 10), 6 strings, 7 tracks, 0 repeats, 0 tempo changes, 272 s |
| C0456a87d-T5 | large-jumps | gp4 | events 397 (notes 313, chords 84: 0 named, 84 unnamed; longest run 12; largest 3 notes), peak 8 events/s, frets 1-20 (largest jump 14), 6 strings, 5 tracks, 0 repeats, 0 tempo changes, 254 s |
| C0a23af78-T1 | large-jumps | gp3 | events 886 (notes 396, chords 476: 0 named, 476 unnamed; longest run 51; largest 6 notes), peak 12 events/s, frets 1-15 (largest jump 10), 6 strings, 5 tracks, 0 repeats, 0 tempo changes, 515 s |
| C078b3cbf-T2 | large-jumps | gp4 | events 2468 (notes 1773, chords 694: 0 named, 694 unnamed; longest run 53; largest 4 notes), peak 21 events/s, frets 1-24 (largest jump 21), 6 strings, 5 tracks, 0 repeats, 7 tempo changes, 435 s |
| C09079d6c-T7 | large-jumps | gp3 | events 173 (notes 69, chords 96: 0 named, 96 unnamed; longest run 54; largest 3 notes), peak 8 events/s, frets 5-24 (largest jump 12), 6 strings, 8 tracks, 0 repeats, 0 tempo changes, 219 s |
| C08a70960-T1 | large-jumps | gp4 | events 845 (notes 474, chords 296: 0 named, 296 unnamed; longest run 54; largest 2 notes), peak 15 events/s, frets 1-15 (largest jump 12), 6 strings, 4 tracks, 0 repeats, 0 tempo changes, 190 s |
| C081d113c-T2 | large-jumps | gp3 | events 751 (notes 466, chords 223: 0 named, 223 unnamed; longest run 7; largest 4 notes), peak 6 events/s, frets 2-16 (largest jump 14), 6 strings, 7 tracks, 0 repeats, 0 tempo changes, 223 s |
| C0ab471dd-T8 | large-jumps | gp4 | events 134 (notes 134, chords 0: 0 named, 0 unnamed; longest run 0; largest 0 notes), peak 9 events/s, frets 2-22 (largest jump 16), 6 strings, 8 tracks, 6 repeats, 0 tempo changes, 183 s |
| C06c93dec-T1 | chord-runs | gp3 | events 1553 (notes 0, chords 1553: 0 named, 1553 unnamed; longest run 1553; largest 6 notes), peak 8 events/s, frets 1-8 (largest jump 4), 6 strings, 5 tracks, 0 repeats, 0 tempo changes, 251 s |
| C08117584-T5 | chord-runs | gp4 | events 156 (notes 64, chords 92: 0 named, 92 unnamed; longest run 51; largest 4 notes), peak 6 events/s, frets 2-14 (largest jump 7), 6 strings, 7 tracks, 0 repeats, 0 tempo changes, 429 s |
| C07febfde-T5 | chord-runs | gp3 | events 294 (notes 0, chords 294: 0 named, 294 unnamed; longest run 294; largest 3 notes), peak 3 events/s, frets 2-5 (largest jump 2), 6 strings, 5 tracks, 1 repeats, 0 tempo changes, 172 s |
| C06720b6c-T2 | chord-runs | gp4 | events 408 (notes 0, chords 408: 0 named, 408 unnamed; longest run 408; largest 6 notes), peak 4 events/s, frets 1-5 (largest jump 2), 6 strings, 2 tracks, 2 repeats, 0 tempo changes, 137 s |
| C0b77775b-T2 | chord-runs | gp3 | events 609 (notes 236, chords 373: 0 named, 373 unnamed; longest run 373; largest 3 notes), peak 6 events/s, frets 1-12 (largest jump 7), 6 strings, 3 tracks, 0 repeats, 4 tempo changes, 133 s |
| C059c9903-T3 | chord-runs | gp4 | events 295 (notes 0, chords 295: 0 named, 295 unnamed; longest run 295; largest 3 notes), peak 4 events/s, frets 2-7 (largest jump 3), 6 strings, 4 tracks, 0 repeats, 0 tempo changes, 270 s |
| C07b7572a-T2 | chord-runs | gp3 | events 90 (notes 0, chords 90: 0 named, 90 unnamed; longest run 90; largest 5 notes), peak 3 events/s, frets 1-3 (largest jump 2), 6 strings, 4 tracks, 0 repeats, 0 tempo changes, 33 s |
| C01081606-T1 | chord-runs | gp4 | events 987 (notes 315, chords 525: 0 named, 525 unnamed; longest run 98; largest 3 notes), peak 6 events/s, frets 2-9 (largest jump 5), 6 strings, 5 tracks, 0 repeats, 0 tempo changes, 300 s |
| C03ae4e8f-T2 | fast-notes | gp3 | events 839 (notes 714, chords 115: 0 named, 115 unnamed; longest run 17; largest 4 notes), peak 9 events/s, frets 1-19 (largest jump 8), 6 strings, 6 tracks, 0 repeats, 6 tempo changes, 215 s |
| C06b03060-T2 | fast-notes | gp4 | events 332 (notes 332, chords 0: 0 named, 0 unnamed; longest run 0; largest 0 notes), peak 11 events/s, frets 7-14 (largest jump 5), 6 strings, 2 tracks, 6 repeats, 0 tempo changes, 148 s |
| C0c87747e-T3 | fast-notes | gp3 | events 1031 (notes 1031, chords 0: 0 named, 0 unnamed; longest run 0; largest 0 notes), peak 13 events/s, frets 1-3 (largest jump 2), 6 strings, 7 tracks, 0 repeats, 0 tempo changes, 164 s |
| C02a33603-T3 | fast-notes | gp4 | events 1559 (notes 1559, chords 0: 0 named, 0 unnamed; longest run 0; largest 0 notes), peak 13 events/s, frets 2-12 (largest jump 7), 6 strings, 4 tracks, 0 repeats, 3 tempo changes, 361 s |
| C0558ad4f-T1 | fast-notes | gp3 | events 997 (notes 710, chords 270: 0 named, 270 unnamed; longest run 16; largest 3 notes), peak 18 events/s, frets 2-22 (largest jump 15), 6 strings, 2 tracks, 2 repeats, 0 tempo changes, 224 s |
| C0567b279-T10 | fast-notes | gp4 | events 396 (notes 396, chords 0: 0 named, 0 unnamed; longest run 0; largest 0 notes), peak 12 events/s, frets 2-7 (largest jump 5), 7 strings, 12 tracks, 0 repeats, 0 tempo changes, 227 s |
| C0748b2d2-T4 | fast-notes | gp3 | events 466 (notes 400, chords 18: 0 named, 18 unnamed; longest run 1; largest 3 notes), peak 10 events/s, frets 2-19 (largest jump 14), 6 strings, 6 tracks, 4 repeats, 0 tempo changes, 331 s |
| C0adece1a-T1 | fast-notes | gp4 | events 299 (notes 225, chords 68: 0 named, 68 unnamed; longest run 20; largest 3 notes), peak 11 events/s, frets 1-19 (largest jump 13), 6 strings, 4 tracks, 2 repeats, 0 tempo changes, 72 s |
| C0886f011-T4 | multi-track-repeats-tempo | gp3 | events 68 (notes 68, chords 0: 0 named, 0 unnamed; longest run 0; largest 0 notes), peak 5 events/s, frets 1-4 (largest jump 2), 6 strings, 6 tracks, 6 repeats, 0 tempo changes, 190 s |
| C04886c08-T1 | multi-track-repeats-tempo | gp4 | events 549 (notes 36, chords 512: 0 named, 512 unnamed; longest run 237; largest 3 notes), peak 6 events/s, frets 1-13 (largest jump 7), 6 strings, 4 tracks, 6 repeats, 0 tempo changes, 130 s |
| C014f5ed0-T3 | multi-track-repeats-tempo | gp3 | events 257 (notes 200, chords 57: 0 named, 57 unnamed; longest run 57; largest 2 notes), peak 7 events/s, frets 2-13 (largest jump 7), 6 strings, 4 tracks, 16 repeats, 0 tempo changes, 278 s |
| C0562772c-T4 | multi-track-repeats-tempo | gp4 | events 155 (notes 155, chords 0: 0 named, 0 unnamed; longest run 0; largest 0 notes), peak 8 events/s, frets 2-12 (largest jump 5), 4 strings, 5 tracks, 0 repeats, 1 tempo changes, 40 s |
| C02886634-T3 | multi-track-repeats-tempo | gp3 | events 270 (notes 121, chords 140: 0 named, 140 unnamed; longest run 126; largest 3 notes), peak 5 events/s, frets 2-14 (largest jump 7), 6 strings, 9 tracks, 0 repeats, 1 tempo changes, 201 s |
| C02214837-T1 | multi-track-repeats-tempo | gp4 | events 1416 (notes 1276, chords 140: 0 named, 140 unnamed; longest run 12; largest 4 notes), peak 8 events/s, frets 1-20 (largest jump 10), 6 strings, 6 tracks, 20 repeats, 5 tempo changes, 331 s |
| C0307d871-T1 | multi-track-repeats-tempo | gp3 | events 625 (notes 548, chords 77: 0 named, 77 unnamed; longest run 21; largest 3 notes), peak 5 events/s, frets 1-27 (largest jump 26), 6 strings, 7 tracks, 0 repeats, 4 tempo changes, 580 s |
| C05f0c35a-T3 | multi-track-repeats-tempo | gp4 | events 196 (notes 196, chords 0: 0 named, 0 unnamed; longest run 0; largest 0 notes), peak 4 events/s, frets 1-3 (largest jump 2), 6 strings, 9 tracks, 0 repeats, 2 tempo changes, 313 s |
| C060601b5-T2 | chord-heavy | gp3 | events 497 (notes 177, chords 299: 0 named, 299 unnamed; longest run 39; largest 6 notes), peak 6 events/s, frets 1-17 (largest jump 11), 6 strings, 2 tracks, 0 repeats, 0 tempo changes, 213 s |
| C0689d9a2-T1 | chord-heavy | gp4 | events 322 (notes 56, chords 201: 55 named, 146 unnamed; longest run 25; largest 6 notes), peak 4 events/s, frets 2-8 (largest jump 4), 6 strings, 1 tracks, 0 repeats, 0 tempo changes, 140 s |
| C05441ceb-T5 | chord-heavy | gp3 | events 857 (notes 218, chords 639: 0 named, 639 unnamed; longest run 611; largest 5 notes), peak 11 events/s, frets 8-29 (largest jump 14), 6 strings, 8 tracks, 0 repeats, 0 tempo changes, 295 s |
| C055b3c41-T4 | chord-heavy | gp4 | events 166 (notes 0, chords 166: 0 named, 166 unnamed; longest run 166; largest 2 notes), peak 6 events/s, frets 3-15 (largest jump 8), 6 strings, 7 tracks, 0 repeats, 0 tempo changes, 111 s |
| C0b87f308-T1 | chord-heavy | gp3 | events 712 (notes 18, chords 574: 0 named, 574 unnamed; longest run 27; largest 4 notes), peak 6 events/s, frets 1-9 (largest jump 5), 6 strings, 5 tracks, 0 repeats, 0 tempo changes, 222 s |
| C019416cf-T1 | chord-heavy | gp4 | events 917 (notes 1, chords 916: 0 named, 916 unnamed; longest run 916; largest 5 notes), peak 6 events/s, frets 1-25 (largest jump 21), 6 strings, 4 tracks, 0 repeats, 0 tempo changes, 236 s |
| C076acebd-T1 | chord-heavy | gp3 | events 891 (notes 206, chords 680: 0 named, 680 unnamed; longest run 203; largest 2 notes), peak 13 events/s, frets 1-22 (largest jump 20), 6 strings, 5 tracks, 0 repeats, 0 tempo changes, 245 s |
| C01760bf2-T5 | chord-heavy | gp4 | events 42 (notes 0, chords 42: 0 named, 42 unnamed; longest run 42; largest 4 notes), peak 1 events/s, frets 5-17 (largest jump 8), 6 strings, 7 tracks, 0 repeats, 0 tempo changes, 297 s |
| C01d67f17-T2 | mixed | gp3 | events 512 (notes 367, chords 145: 0 named, 145 unnamed; longest run 3; largest 2 notes), peak 8 events/s, frets 1-13 (largest jump 7), 6 strings, 2 tracks, 9 repeats, 0 tempo changes, 111 s |
| C069740bd-T2 | mixed | gp4 | events 924 (notes 590, chords 334: 0 named, 334 unnamed; longest run 159; largest 3 notes), peak 6 events/s, frets 1-16 (largest jump 15), 6 strings, 5 tracks, 0 repeats, 0 tempo changes, 285 s |
| C0a7f4819-T4 | mixed | gp3 | events 70 (notes 44, chords 24: 0 named, 24 unnamed; longest run 2; largest 3 notes), peak 4 events/s, frets 1-13 (largest jump 8), 6 strings, 6 tracks, 0 repeats, 0 tempo changes, 344 s |
| C0a96b296-T3 | mixed | gp4 | events 76 (notes 32, chords 44: 0 named, 44 unnamed; longest run 9; largest 3 notes), peak 6 events/s, frets 2-7 (largest jump 4), 6 strings, 5 tracks, 0 repeats, 0 tempo changes, 306 s |
| C00c57bd3-T1 | mixed | gp3 | events 268 (notes 187, chords 81: 0 named, 81 unnamed; longest run 5; largest 5 notes), peak 4 events/s, frets 1-9 (largest jump 4), 6 strings, 1 tracks, 2 repeats, 0 tempo changes, 193 s |
| C0680ec6e-T2 | mixed | gp4 | events 90 (notes 54, chords 36: 0 named, 36 unnamed; longest run 4; largest 2 notes), peak 3 events/s, frets 2-8 (largest jump 6), 6 strings, 4 tracks, 0 repeats, 0 tempo changes, 143 s |
| C01943414-T1 | mixed | gp3 | events 625 (notes 427, chords 187: 0 named, 187 unnamed; longest run 12; largest 6 notes), peak 7 events/s, frets 1-15 (largest jump 7), 6 strings, 4 tracks, 0 repeats, 0 tempo changes, 204 s |
| C0648b568-T1 | mixed | gp4 | events 924 (notes 525, chords 399: 0 named, 399 unnamed; longest run 6; largest 6 notes), peak 7 events/s, frets 1-10 (largest jump 7), 6 strings, 1 tracks, 13 repeats, 2 tempo changes, 247 s |
| C03cb2f49-T2 | sparse-notes | gp3 | events 264 (notes 264, chords 0: 0 named, 0 unnamed; longest run 0; largest 0 notes), peak 5 events/s, frets 2-7 (largest jump 4), 6 strings, 6 tracks, 0 repeats, 0 tempo changes, 220 s |
| C01f4773a-T3 | sparse-notes | gp4 | events 61 (notes 58, chords 3: 0 named, 3 unnamed; longest run 3; largest 3 notes), peak 3 events/s, frets 1-7 (largest jump 3), 6 strings, 6 tracks, 0 repeats, 0 tempo changes, 213 s |
| C0881e997-T3 | sparse-notes | gp3 | events 202 (notes 196, chords 6: 1 named, 5 unnamed; longest run 5; largest 4 notes), peak 4 events/s, frets 1-6 (largest jump 5), 6 strings, 9 tracks, 0 repeats, 0 tempo changes, 304 s |
| C0197ae41-T4 | sparse-notes | gp4 | events 208 (notes 208, chords 0: 0 named, 0 unnamed; longest run 0; largest 0 notes), peak 7 events/s, frets 2-3 (largest jump 1), 6 strings, 8 tracks, 0 repeats, 0 tempo changes, 313 s |
| C08fd438f-T6 | sparse-notes | gp3 | events 391 (notes 391, chords 0: 0 named, 0 unnamed; longest run 0; largest 0 notes), peak 4 events/s, frets 1-8 (largest jump 5), 6 strings, 7 tracks, 0 repeats, 0 tempo changes, 288 s |
| C0525c469-T4 | sparse-notes | gp4 | events 420 (notes 407, chords 0: 0 named, 0 unnamed; longest run 0; largest 0 notes), peak 5 events/s, frets 2-9 (largest jump 4), 4 strings, 4 tracks, 0 repeats, 0 tempo changes, 294 s |
| C0544f533-T2 | sparse-notes | gp3 | events 408 (notes 408, chords 0: 0 named, 0 unnamed; longest run 0; largest 0 notes), peak 5 events/s, frets 1-10 (largest jump 7), 6 strings, 5 tracks, 0 repeats, 0 tempo changes, 232 s |
| C06505026-T4 | sparse-notes | gp4 | events 292 (notes 292, chords 0: 0 named, 0 unnamed; longest run 0; largest 0 notes), peak 4 events/s, frets 5-12 (largest jump 7), 6 strings, 6 tracks, 0 repeats, 0 tempo changes, 218 s |
