# The owner's third series (owner-chords-3), 2026-11-05

The owner judged the first two series bad (a contact problem) and recorded a new one through the Rocksmith USB Guitar Adapter: 8 chords of his choice (Am, C, G, Em, D, A, Bm, Dm), 25 strums, no clipping (peaks -14 to -21 dBFS, noise floor about -80 dBFS). It replaces the first two series for spec 024. The audio is not committed (`specs/004-attack-difference-recognition/benchmark/owner-chords-3/`, git-ignored); its labels are `labels-owner-chords-3.csv` here.

- `owner-chords-{java,native}.txt`: `OwnerChordsBenchmarkTest` with `-Dtuxguitar.owner.chords=<that folder>`: 17 of 25 accepted, 4 of 25 controls wrongly accepted, identical with both engines.
- `owner-chords-strum-hold-{java,native}.txt`: `OwnerChordsStrumHoldProbeTest` on every pair: today 17/25 right and 9/408 clearly wrong (the G takes accepted as Dm); one string short 24/25 and 25/408; holding the strings over the strum adds nothing.
- `chroma-probe.txt`: `misc/owner-chords-chroma-probe.py`, a pitch-class profile matched against the chord template over the first second: at a cosine of 0.85, 25/25 right, 0/138 one name off, 0/408 clearly wrong (the right minimum 0.851, the clearly wrong maximum 0.66).
