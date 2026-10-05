# Real recording 1 — acoustic fingerstyle, 42 events (2026-10-05)

Inputs (kept outside the repository, not redistributable): `tests/1/_TEST_.tg` (8 measures, 3/4, 120 bpm, 34 notes + 8 chords, "Guitare folk") and `tests/1/audio_15ps.m4a` (15.5 s, 44.1 kHz, acoustic guitar, clean recording; the performer plays at about 99 bpm, i.e. the tab x1.215, first attack at 0.47 s).

Event times: constant-tempo fit of the tab onto the detected attacks (32 of 42 events within 40 ms of a detected attack). Approximate, not hand-labelled.

| File | What |
|---|---|
| `bilan-defaults.txt` | Practice session with the default settings, event by event (session started at each event, fed the audio around it; false accepts = session expecting a neighbouring event fed the same audio) |
| `yin-vs-expected.txt` | Single-pitch detector (YIN) against the expected note over the 60-300 ms after each note |
| `rank-static-spectrum.txt` | Rank of the expected note among the 12 pitches of the piece, static unique-partial evidence |
| `rank-attack-difference-notes.txt` | Same, with the attack-difference spectrum (what grew at the attack) |
| `rank-attack-difference-chords.txt` | Same for the 8 chords among the 7 distinct chords |

Results: defaults recognise 2/34 notes and 5/8 chords (1/126 and 2/30 false accepts); YIN follows the ringing bass (the dominant YIN pitch is the expected note in only 4 of 34 notes); static evidence ranks the expected note first 11/34; attack-difference ranks it first 27/34 (top-3 31/34) and the chords first 5/8 (top-2 7/8). Chord thresholds cannot separate right from wrong chords (research R3, 2026-10-05 entries).
