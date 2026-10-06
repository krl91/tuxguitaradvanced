# Interactive Practice, user guide

Interactive Practice listens to your guitar and checks, note by note and chord by chord, that you play what the score says. It does not play the score for you and it never records audio: only numbers derived from the sound (pitch, level, which notes were heard) live in memory while it runs.

*Français : [PRACTICE-FR.md](PRACTICE-FR.md). Español: [PRACTICE-ES.md](PRACTICE-ES.md).*

## What you need

* macOS (Apple Silicon), Linux or Windows: the practice modules are part of the three packages. Microphone access and audio-input behaviour depend on the system's Java Sound support.
* A guitar sound on an input: the built-in microphone works for clean playing; an audio interface with a direct guitar signal is better. Headphones are recommended if the score also plays through the speakers.
* A score with a guitar track (standard tuning or any tuning written in the score).

## Starting

<img src="images/features/practice-setup.png" width="460" alt="The preparation window of Interactive Practice">


| How | What happens |
|---|---|
| **Practice button** <img src="images/features/practice.png" width="18" alt="Practice"> next to the Play button | Practises the track under the score cursor, from the cursor, as soon as the input is usable. One click. |
| *Tools > Interactive Practice* | Opens the practice window to choose the track, the start and the input first. |

The first time (or after changing the input) the window checks your **input level**: play a string and read the meter. If it says *too low* or *clipping*, move closer or adjust the gain (below).

## While you practise

<img src="images/features/practice-window.png" width="380" alt="The practice window while a chord is expected">


A small palette stays on screen and the expected note or chord is framed on the score:

* **green tick**: heard, the practice moves on;
* **red cross**: another note was heard (shown only for a note that was just struck, not for the previous note still ringing);
* nothing happens on silence or noise, and a note that rings on is never taken for the next one;
* a chord is accepted when its notes are heard together; the missing notes are named when the chord is incomplete;
* **Skip** passes the note, **Resume** continues after a pause (a playback, an unusable input), **Restart** starts the passage again, **Stop** ends the practice immediately and brings the preparation window back.

Practice can also work **by passages**: a few measures at a time, repeated until you reach the share of correct notes you set.

What is verified is **pitch**. The string and fret you used cannot be known from audio. For each written technique (hammer-on, pull-off, slide, bend, vibrato) a line under the pitch result says what was heard: *heard*, *not heard*, *uncertain* or *cannot be checked yet*. No technique is verified yet (measured detection: vibrato 59 %, bend 82 %, slide 48 % of annotated notes, target 85 %), so for now the line says *cannot be checked yet*. It never blocks the exercise: a hammer-on or pull-off note is accepted from its pitch.

## Input gain, noise reduction and suggested gain

Under the level meter of the practice window, and in the tuner, an **Input gain** slider boosts or lowers the signal inside the application (-6 to +18 dB), and **Reset** returns to 0 dB. The gain is never remembered: it returns to 0 dB when the input changes and at each start. If the boost mostly amplifies background noise, or makes the signal clip, a warning says so and tells you to raise the gain on the audio interface instead.

While you play, if the notes you play are too weak for reliable fast recognition (below about -30 dBFS, measured), the practice window says so and tells you to raise the gain or play closer; slow playing may still be recognised.

Settings (*Recognition settings* in the practice window): an optional **noise gate / background-noise subtraction** and a **suggested gain** after the level check; both can be switched off.

## Recognition settings

<img src="images/features/practice-settings.png" width="560" alt="The audio recognition page of the configuration">

<img src="images/features/practice-settings-notes.png" width="560" alt="The note settings of the audio recognition page">


Every setting has a recommended value and a *Restore* button. The ones you may want to change:

| Setting | Effect |
|---|---|
| Accepted pitch range | How far from the written note (in cents) a note may be and still count. |
| Sharp allowance per fret (Advanced) | Fretted notes sound a little sharp, more so higher up the neck: this adds that many cents per fret on the sharp side only. 0 switches it off. |
| Minimum certainty | How sure the pitch detector must be before a single note is judged. |
| Time to confirm a note | How long a note must sound before it is accepted. |
| Pick attack sensitivity | How strong a sudden rise of the sound counts as a new attack. |
| Chord settings | How strictly the notes of a chord are checked and how long a strum may take. |

## The tuner

<img src="images/features/tuner.png" width="380" alt="The guided tuner">


*Tools > Guitar Tuner* opens the tuner. It shares its pitch detection with the practice mode.

* **Two modes**: *Guided (6 strings)* walks you through the strings of a tuning, *Chromatic* shows the nearest note of whatever you play.
* **Tuning**: a preset, *Custom...* (one note per string), or the tuning of a track of the open score ("Tuning from track N").
* **The needle** shows the **median of the confident readings of the last quarter second**: a glitch cannot move it, a short gap does not freeze it, a new note replaces the old one within a few readings. The text under it says what to do: "Too low: 12 cents flat of E2. Tune up", "In tune", "Too high... Tune down".
* **A frozen grey needle** (shown as "Last reading (held)") means the string is silent or too quiet now: play it again.
* **Input**: the selected input and its level ("good / too low / too high / clipping"), a **Check level again** button, and the **Input gain** slider (-6 to +18 dB) with its **Reset** button, as in the practice window.
* **Not while practising**: the tuner refuses to open while the practice window is open (they share the microphone); close the practice window first.

## Score analysis (invalid measures)

Opening a Guitar Pro or MIDI file no longer interrupts you with a dialog about measures whose duration does not match the time signature. To see **all the problems of a song**:

1. *Edit > Display invalid measures* (or the toolbar icon <img src="images/features/analyse-score.png" width="18" alt="Analyse">);
2. the window lists the invalid measures and tied notes of every track (track, measure, voice, too long / too short...), with filters, the status of the current measure, and a **Fix** button;
3. it does not block anything: leave it open while you edit, and use it again whenever you want.

## Diagnostic trace (to understand a failure)

When a note you played correctly is not recognised (or a wrong one is), you can ask the application to write what it heard, **as numbers only, no audio**:

1. quit the application, then start it with the property `tuxguitar.practice.trace` naming a file, for example from a terminal on macOS:
   `open -n --env JAVA_TOOL_OPTIONS="-Dtuxguitar.practice.trace=/tmp/practice-trace.csv" /path/to/TuxGuitarAdvanced.app`
2. play the passage in Interactive Practice;
3. open the CSV: one line per analysis step (time, event, expected notes, detected pitch and certainty, level, strength of the attack, amplitude of each expected note and of its neighbours, `ACCEPTED` lines). Send it with the description of what should have happened.

The file is only written while the property is set.

## Checking the quality yourself

A benchmark replays annotated recordings through the real practice chain and reports, per kind of test, how many pass, fail or are not run (and why). It reads recordings you keep outside the repository (the licences of such datasets forbid redistributing them):

```sh
cd desktop/build-scripts/tuxguitar-macosx-swt-cocoa
mvn test -pl ../../TuxGuitar-practice -am -Dgroups=dataset -DexcludedGroups= \
  -Dtest=DatasetBenchmarkTest -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtuxguitar.datasets=/path/to/your/datasets -Dtuxguitar.datasets.sample=70
```

The report is written to `desktop/TuxGuitar-practice/target/dataset-benchmark/report.txt` (summary) and `outcomes.csv` (every test). Tests that cannot be run are counted with their reason (for example: an octave cannot be told from a partial of the played note; the note is outside the range of a six-string guitar).

Two replay tests run in the normal build when their recordings are present under `tests/1` and skip otherwise.

## Measured quality and limits

On a benchmark of 1,208 annotated recordings of real guitars (clean and with 11 effects), 6,178 tests:

* **single notes recognised**: 99.8 % (effects) to 100 % (clean);
* **a note a semitone or a tone away from the played one accepted**: none in more than 2,700 tests;
* **intervals and triads with effects recognised**: 87 %; **chords of clean guitars**: 95 %;
* **chords with a note missing refused**: 96 to 98 %;
* **notes played one after the other, the previous one still ringing**: 97 to 100 % recognised;
* **delay** between playing a note and its recognition: about 0.1 s (median 105 ms, 90 % within 170 ms) on a real recording; the practice preparation window states it.

On an **independent dataset** (GuitarSet, 180 recordings of rhythmic comping on an acoustic guitar, 702 strummed chords whose annotation could be trusted, with the previous strings still ringing), the result is much lower than above:

* **complete chords recognised**: 60 % (six pickup channels summed), 46 % through the microphone;
* **chords with one note removed accepted by mistake**: 13 % (29 % when the missing note is the bass, 5 % an inner note, 3 % the top note);
* no setting fixes both at once: a stricter one refuses more incomplete chords and also more complete ones (details and table in the specification research notes, R10).

Limits, measured and not hidden:

* **notes close together**: about 0.3 s between picked notes are followed (91 % of three-note runs at 0.35 s, 67 % at 0.25 s); faster, only the first note of a run is recognised and Practice waits for you, it never marks a wrong note (0 of 92 tests). The practice window says so when it happens;

* chords whose lowest note is G2 are sometimes accepted a whole tone higher (the two chords are one frequency step apart at that pitch);
* four-note seventh chords and some open chords (A major, E major) are recognised less often;
* an octave, or a note that is a harmonic of a lower chord note, cannot be verified separately from it;
* the settings were tuned on the recordings above; another guitar or microphone may call for another gain or sensitivity.
