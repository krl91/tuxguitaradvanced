# Java engine, before spec 023

Copied on 2026-11-03 (T002) from `desktop/TuxGuitar-practice/target/dataset-benchmark/`, written on 2026-11-02 between
22:49 and 22:56 by the convergence gate of spec 022, committed right after as 0ffd99c89 (23:05).
`git diff --stat 0ffd99c89 -- desktop/TuxGuitar-audio-engine desktop/TuxGuitar-practice/src` is empty: no analysis code
changed between that run and the start of spec 023.

`bass-recognition-2026-10-08.txt` is the committed report of `BassRecognitionBenchmarkTest`
(`specs/003-arcade-mode/benchmark/`), the bass benchmark's only output.

Each SC-001 figure of the spec was found in these files, none lower:
- IDMT single notes 100 %, wrong notes ±1 and ±2 refused 100 % (report.txt);
- IDMT chords recognised 95.5 %, incomplete chord refused 97.2 %, a whole tone higher refused 66.7 % (Audio-Effects)
  and 100 % (IDMT-Guitar) (report.txt);
- distortion 86.5 %, chorus 90.4 % (report.txt);
- GuitarSet complete chords 33/56 summed (delay median 103 ms), 23/56 microphone (149 ms) (guitarset-chords-report.txt);
- the owner's chords 26/42 accepted, 13/42 neighbours wrongly accepted (owner-chords.txt);
- 942 tests: 592 OK, 28 KO, 322 not run (report.txt, outcomes.csv).
