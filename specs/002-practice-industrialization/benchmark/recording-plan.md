# Recording and benchmark plan (spec 002 T006, updated 2026-10-06)

Purpose: say exactly which material the recognition work needs, which of it is **already covered by public datasets** (kept outside
the repository, see `desktop/TuxGuitar-practice/test-data/datasets/README.md` for provenance and licences) and which can only come
from the owner's own guitar. The general real-material validation of the MVP is done (the reference recording, `STATUS.md`): the
owner is **not** asked to repeat it; only the specific clips below, and only for the cases that no public dataset covers.

## 1. Covered by public datasets (no recording to make)

| Need | Dataset and what is used | Where it is measured |
|---|---|---|
| single notes, wrong neighbouring notes, intervals and chords through 11 effects | IDMT-SMT-Guitar V2 dataset 1, IDMT-SMT-Audio-Effects (CC BY-NC-ND 4.0, evaluation only) | `DatasetBenchmarkTest` (`specs/002-practice-industrialization/benchmark/dataset-benchmark-2026-10-05.txt`) |
| complete versus incomplete chords, one string removed at a time, real hexaphonic recordings and a microphone | GuitarSet 1.0.1, 180 comping recordings (CC BY-NC 4.0) | `GuitarSetChordBenchmarkTest` (`specs/004-attack-difference-recognition/benchmark/guitarset-chords-2026-10-06.txt`, research R10) |
| the closest notes that are followed, recognition rate, false acceptances and delay by spacing (50 ms to 350 ms) | IDMT-SMT-Guitar V2 dataset 1 single notes mixed at controlled spacings | `NoteSpacingBenchmarkTest` (`specs/004-attack-difference-recognition/benchmark/note-spacing-2026-10-06.txt`, research R11) |
| vibrato, bend, slide, harmonic and dead-note examples with annotated style, plain notes as counterpart | IDMT-SMT-Guitar V2 dataset 2 (expression styles VI, BE, SL, HA, DN against NO); dataset 3 pieces | `TechniqueDatasetBenchmarkTest` (`technique-benchmark-2026-10-06.txt`) |
| muted (palm-muted) notes as a candidate for the palm-mute detector | IDMT-SMT-Guitar V2 excitation style `MU` (annotation to be checked: muted string, not necessarily a palm mute) | to be measured in Phase 10 before any use |
| delay by input and the front end (gain, noise gate, subtraction) | built-in microphone clips of the MVP (CC0-1.0, in the repository) | `BenchmarkSweepTest` (`builtin-mic-latency.csv`, `frontend-with-without.csv`) |

What these datasets do **not** contain (so it has to be recorded, or the technique stays "not verifiable"): hammer-ons and pull-offs,
tremolo picking, tapping, pinch and artificial harmonics, palm mute written as such, fast runs and identical repeats at the rates below,
weak-input and hum clips, the owner's own interface and microphone.

## 2. Clips to record on the owner's setup

Two inputs, reported separately: the direct instrument input of the audio interface and the built-in microphone. One clip per line,
the played note and the way it was played in the file name, as for the MVP clips.

| Group | Clips | For |
|---|---|---|
| fast runs | the same four-note figure picked at about 4, 6, 8, 10 and 12 notes per second (a metronome click is not recorded), up and down, at least 8 notes per clip | T051, T053: recognition rate, delay and the real speed limit, to be compared with the 0.3 s provisional limit of research R11 |
| identical repeats | the same note struck 4 times at 1, 2, 4, 6 notes per second | one attack counted once (the re-arm risk, research R11) |
| neighbouring pitches | a semitone and a whole tone apart, at the same speeds | wrong neighbours never accepted |
| hammer-on and pull-off | three ascending hammer-ons and three descending pull-offs (frets 5 to 7 and 7 to 5 on a few strings), each next to the same two notes picked | Phase 7: the technique detectors; the first real legato data |
| slide | slides up and down by 1, 2 and 5 frets on two strings, next to the picked jump | Phase 7 |
| bend and vibrato | half-tone, whole-tone and pre-bend with release; slow, medium and fast vibrato of wide and narrow depth, next to the plain note | Phase 8 (complements the IDMT annotations) |
| palm mute and tremolo picking | the same note palm-muted and plain, tremolo-picked at 8 and 12 notes per second | Phase 10 |
| harmonics and tapping | natural harmonics at frets 5, 7, 12, artificial and pinch harmonics, tapped notes next to the fretted ones | Phase 11 |
| weak input | the same notes at -30, -40 and -50 dBFS peak, with and without background noise, and with 50 Hz hum | T050: the "input too poor" message |
| noise only | 10 s of room noise, 10 s of hum, a few knocks | no recognition from noise |

## 3. Rules for the recordings

* short PCM WAV, mono, 48 kHz, 16-bit, guitar only, no click, no backing track, one take per file;
* a `LICENSE` file beside them with the CC0-1.0 dedication, and a manifest row per clip (input, tuning, guitar, strings, pickup or microphone
  position, interface and gain, macOS input volume, room, tuner reference, truth source, date), as in `manifest.csv` of the MVP;
* nothing is added to the repository before the owner confirms the clip is theirs, played by them, and fit to be published under CC0-1.0;
* the tuning is checked with a clip-on tuner before each series (A4 = 440 Hz) and written in the manifest.

## 4. Order

1. T051 runs on the datasets above first (no recording needed) and on the existing clips; its figures decide which fast-run clips are still needed.
2. The fast-run and repeat clips (T053) are recorded only if T051 shows that the public material does not settle a question.
3. The technique clips are recorded phase by phase (Phases 7, 8, 10, 11), each series right before the detector it serves.
