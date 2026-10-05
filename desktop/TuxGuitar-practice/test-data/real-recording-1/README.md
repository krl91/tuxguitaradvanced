# Reference recording 1: event-time fixture

`events.csv` gives, for each of the 42 events of the score `_TEST_verifie.tg` (34 notes, 8 chords), the time in seconds of its attack in the recording `audio_15ps.m4a` and the expected pitches (MIDI).

Method (spec 004 FR-014): the performer plays at about 99 bpm (the written tempo x 1.215, first attack at 0.47 s). Each event time is the written time on that tempo line, moved to the nearest attack found by a sensitive spectral-flux detector when one lies within 0.10 s (`source = attack`, 33 events), else left on the tempo line (`source = tempo-fit`, 9 events; their times are approximate, to about 0.1 s). The times do not come from the recogniser under test. Event 6 was set to the tempo line because the detector had assigned it the attack of event 7.

The audio and the score are not in the repository (rights); the replay test reads them from `tests/1` and is skipped when absent. The score was checked against the audio by the owner: the last bass of measures 5 and 6 are La2 + La3 and Sol2 + Sol3 in the audio, the last bass of measure 3 stays Mi2.
