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

**The start window (before every start).** Each time a mode starts — the Start button, the Practice button of the transport bar, the coloured Arcade button — a small window first asks *Is your guitar in tune?* *No, open the tuner* opens the tuner and the mode does not begin; when you close the tuner the question comes back. *Yes, in tune* asks for a test strum: strum a chord and let it ring. The window judges the level of the 0.3 seconds after the strum: if more than 0.5 % of the samples reach the maximum it says the signal clips (lower the volume of the adapter or of the guitar); a signal that is too loud or too weak is named too, with what to do; a good level starts the mode at once. A bad level blocks the start: *Check again* judges a new strum, and *Continue anyway* goes on **for this start only** (it is also offered after 10 seconds without a strum). In **Follow the tempo** and **Arcade**, when the rhythm of your input and output was never calibrated, the window then says so, with **Calibrate now** (the 8 clicks of the Mode tab, about 10 seconds, inside the window; *Cancel* stops them and the two buttons come back; the delay found is kept as from the Mode tab) and **Go on without** (not asked again for this input and output until TuxGuitar starts again). Another input, output or format is not calibrated by the first one. Restarting a run or *play again* does not ask again. The window never changes the gain of the interface or of the system. The 0.5 % threshold was calibrated on the owner's recordings through a Rocksmith adapter and on the 180 microphone recordings of GuitarSet (none is flagged); the advice for Windows and Linux uses the macOS wording and was not tried there.

## While you practise

<img src="images/features/practice-window.png" width="380" alt="The practice window while a chord is expected">


A small palette stays on screen and the expected note or chord is framed on the score:

* **green tick**: heard, the practice moves on;
* **red cross**: another note was heard (shown only for a note that was just struck, not for the previous note still ringing);
* nothing happens on silence or noise, and a note that rings on is never taken for the next one;
* a chord is accepted when its notes are heard together; the missing notes are named when the chord is incomplete;
* a **bent note** is accepted when the bend reaches the written target: the note it starts from (a pre-bend, its bent pitch) starts the bend and the practice tells you to bend it up; while you bend, release or pre-bend along what is written, no pitch on the way is a wrong note, the target of the bend is not taken for the next note (which needs its own pick), and a written release must be made too (the practice tells you to release down to its end); **Skip** passes a bend or release you do not want to play. Whether the bend was exactly on pitch is a technique result, which for now says *cannot be checked yet*;
* **Skip** passes the note, **Resume** continues after a pause (a playback, an unusable input), **Restart** starts the passage again, **Stop** ends the practice immediately and brings the preparation window back.

Practice can also work **by passages**: a few measures at a time, repeated until you reach the share of correct notes you set.

What is verified is **pitch**. The string and fret you used cannot be known from audio. For each written technique (hammer-on, pull-off, slide, bend, vibrato) a line under the pitch result says what was heard: *heard*, *not heard*, *uncertain* or *cannot be checked yet*. No technique is verified yet (measured detection: vibrato 59 %, bend 82 %, slide 48 % of annotated notes, target 85 %), so for now the line says *cannot be checked yet*. It never blocks the exercise: a hammer-on or pull-off note is accepted from its pitch.

## Practising with the band: Follow the tempo and Arcade (alpha)

In the **Mode** tab of the practice window you choose how the score is practised:

| Mode | What happens |
|---|---|
| **Wait for the right note** | The score waits for you (the mode described above). |
| **Follow the tempo** | The score plays at its tempo, after a count-in. The track you practise is silent, the other tracks sound as a backing. Each note or chord is judged on time: on time, early, late, wrong, missed. |
| **Arcade** | The same run, shown as a game: the notes come towards you on a neck and are judged as they pass. The result of the run is shown at the end. |

Both timed modes share the **tempo** (20 % to 150 % of the written tempo) and the **rhythm calibration** of your audio route (calibrate with clicks, or type the delay by hand: without it the notes may be judged a little early or late, and the window says *Reduced rhythmic precision*). **Arcade** judges the time with the **timing window** (a slider from 40 to 200 ms each side at 120 beats a minute, **100 ms** by default; it grows and shrinks with the tempo, is one saved setting also shown in the Configuration window, and an older choice is converted: strict 50 ms, lenient 125 ms, normal the new default; a wide window can take a note for the one still ringing in a dense passage); **Follow the tempo** judges it with the **time to play the note** (below).

### Follow the tempo: the time to play the note and the permissiveness

**Time to play the note** (Mode tab, Follow only; 100 to 600 ms, **300 ms** by default; one saved setting, also shown in the Configuration window, section *Passages*): from the moment a note turns red, how long you may still play it and have it accepted. Up to half of that time it counts *on time*, after that *late*; it may also come up to 50 ms before its red (*early*). When the notes are close together, the time stops 20 ms before the next note turns red and the next note's advance shrinks to what is left, so that one note never counts for two: with notes 200 ms apart, a note may be played up to 180 ms after its red, and the next one from 20 ms before its own. A change made during a run applies to the next run.

**Permissiveness** (Mode tab, Follow only): *Strict* (the note recognition of the other practice modes), *Permissive* (the default) or *Very permissive*: a note a little out of tune or not quite clear is accepted more easily; a wrong note is still refused, and silence is never accepted. It only relaxes, never tightens, what you or a calibration profile already set; it is saved apart from Arcade's permissiveness; the summary and the record of a run name the position and the time to play. **Measured so far:** on the reference recording the three positions give the same results (35 of 38 right notes accepted, none of the 98 wrong-note tests), and widening the acceptance in cents loses a right note, so it is not widened. The values were then chosen at the most lenient that still lose no right note (spec 016). On the owner's first recorded run, two refusals out of three were an attack never heard, which no permissiveness can fix: play firmly enough for the level meter to move.

### Follow the tempo: why a note was refused, and the record of a run

In **Follow the tempo**, the palette says under each refused note why it was refused, from a fixed list: *no attack heard*, *a sound was heard but not clearly enough to tell the note* (with its certainty; this is never counted as a wrong note), *heard X instead of Y*, *right note, N ms too early* or *too late*, *not judged*. The summary at the end counts each reason.

Two boxes of the **Mode** tab, shown in Follow only and off each time TuxGuitar starts, keep a record of the next runs: **Record this run** writes, at the end of a run (complete, stopped, or ended by playing the score), a CSV file `follow-<date>-<time>.csv` in the folder `tuxguitar-follow-takes` of your home folder: per note, what was expected, what was heard, when, with what certainty, the verdict and its reason, and the settings of the run. **Keep a copy of the sound**, offered only with the record, adds a WAV of what the recognition heard (at most 10 minutes) with the same name. Nothing is written for a run stopped during its count-in, nothing is written without the box, and nothing leaves your computer: you send the file yourself if you want it looked at. Details: `specs/016-follow-mode-leniency/`.

### Arcade

Two ways to start it: the **coloured Arcade button** of the transport bar (right after the ♪ Practice button) starts Arcade **at once** on the track under the score cursor, with the current input, without the preparation window; or choose **Arcade** in the Mode tab, choose the track you play in the Track tab (any pitched track, bass included), then press *Start Arcade*. The practice window hides while the game window runs.

<img src="images/features/practice-modes.png" width="480" alt="Choice of the mode">

<img src="images/features/practice-mode-arcade.png" width="420" alt="Mode tab with Arcade chosen, with the size of the rectangles and the tilt of the plane">

<img src="images/features/arcade-notes.png" width="640" alt="Arcade run: rectangles coming from the horizon in a straight line, one landing on its target on the neck">

* **You see one measure ahead** (one second at least), whatever the tempo. A note is a **rounded rectangle** in the colour of its string; it comes **in one straight line** from the horizon to the middle of its fret at the foot of the neck, through the see-through neck, and is as wide as the cell of its fret. At the moment to play it is exactly on its target on the neck.
* **Chords** are a translucent panel from the first to the last fret of the chord, with a rectangle for every note and the name above the left corner.
* **Verdicts** (on time, early, late, wrong, incomplete, missed) appear as a badge once the note is judged. The top of the window shows your streak, the tempo and what comes next.
* **Restart, pause, resume and stop** are in the window. *Restart* begins again from the start of the piece (or of the passage) without leaving the game. During the count-in the notes come from the far end while you hear the clicks; after a pause, *Resume* counts in again from where you were. Playing the score from the main window stops the run.
* **Settings of the picture** (Mode tab): the **size of the incoming rectangles** (0 % to 150 % of the size of the target) and the **tilt of the plane** (scale at the horizon 20 % to 100 %, 35 % by default; height of the horizon 0 % to 40 %, 18 % by default).
* **Stage flames** (Mode tab): flames behind the picture that **grow as you play better** (their height follows your share of the maximum score; embers until four events are judged). **Off**, **Discreet** (default) or **Full**; read when a run starts. Decoration only: they never change the judging or the score, never cover a text, and stand still when macOS is set to *Reduce motion* (macOS only in this version). Details in the [manual](MANUAL.md#b6-arcade-mode-alpha).
* **Techniques written on a single note are drawn** on the note while it comes and again on the neck, in yellow: bends (arrow, amount, ghost rectangle at the cell reached), hammer-on and pull-off (arc, H or P, ghost on the next note of the string), slide (dashed rail, S), vibrato (wave), palm mute and tapping (pills P.M. and T), tremolo picking (three strokes); a harmonic is a bar that says *Harmonic ◇*. **What is checked: the timing and the pitch** (the written path of a bend, the destination note of a hammer-on, pull-off or slide). **No technique is reported as detected or not detected**: the detectors are not validated yet (bend 82 %, vibrato 59 %, slide 48 % of the annotated notes, 85 % needed; none for hammer-on and pull-off; none at all for palm mute, tapping and tremolo picking). Details and pictures in the [manual](MANUAL.md#b6-arcade-mode-alpha).
* At the end (and also when you stop the game before the end) the **result** gives the score, the share of the maximum, the best streak and the count of each judgement; you can **play again from the start or from a measure**.
* **Inputs:** the *Refresh* button of the Input tab lists the audio inputs again; *Remember as default* stores the chosen input in the configuration (selected when it is there, and again when it comes back after an absence).

<img src="images/features/arcade-chord.png" width="640" alt="A chord: a translucent panel with its name, a rectangle for every note, and the target on the neck">

<img src="images/features/arcade-result.png" width="300" alt="Result window at the end of a run">

<img src="images/features/practice-input-refresh.png" width="420" alt="The Input tab with the Refresh and Remember as default buttons">

*Screenshots of the application, shown with its French interface.*

### Arcade levels and permissiveness

**Five levels** make a hard piece playable. The start window of Arcade asks the level each time (level 1 the first time, then the last one you chose): **level 1** keeps about 30 % of the notes and no chord; **level 2** 30 % of the notes and 30 % of the chords; **level 3** 50 % and 50 %; **level 4** 75 % and 75 %; **level 5** the whole piece, as before. The first sound of each kind is always kept and the others are spread evenly after it, the same ones at every run. The sounds that a level leaves out are **not shown, not expected and not judged**, and the score is counted on the sounds kept; **the band plays them for you**, so the music stays complete (they are sent with the instrument of your track, without bends or slides). The result window names the level and the permissiveness.

**Permissiveness** (Mode tab, Arcade): *Like Practice* (the recognition and the timing of the practice modes, nothing relaxed), *Fun* (the default) or *Very permissive*. It is applied to Arcade **only** (Follow, Wait and the tuner are unchanged) and only relaxes, never tightens, what you or a calibration profile already set: it widens the timing window (by a quarter or a half, never beyond 200 ms) and relaxes the confidence, the stability time and the chord thresholds. **Measured:** on the reference recording and on the built-in-microphone notes it changes **nothing** in what is recognised (the default settings already reach the ceiling there) and it does **not** make the verdict quicker: that delay is fixed by the attack detector. Its effect on weaker inputs is not measured.

### Alpha: what is not verified yet

* This is an **alpha**: the timing of the Arcade view and of the tempo-following run is validated by automatic tests, and the look of the view by the owner on a Mac; it has **not** been validated by other players, guitars, microphones or machines.
* In a 10-minute stress run, the audio chain still **misses about one analysis deadline per minute** (an operating-system scheduling gap); it was not shown to change a judgement, but the goal of fewer is not met yet.
* **Bass:** notes below G1 (49 Hz) are shown but not judged.
* **Hearing your own guitar through the application** is not offered: play through your amplifier, or through headphones plugged in your audio interface.
* Notes closer than about 0.3 s may not be followed; the techniques (hammer-on, slide, bend...) are reported apart and do not change the score.

## Input gain, noise reduction and suggested gain

Under the level meter of the practice window, and in the tuner, an **Input gain** slider boosts or lowers the signal inside the application (-6 to +18 dB), and **Reset** returns to 0 dB. The gain is never remembered: it returns to 0 dB when the input changes and at each start. If the boost mostly amplifies background noise, or makes the signal clip, a warning says so and tells you to raise the gain on the audio interface instead.

While you play, if the notes you play are too weak for reliable fast recognition (below about -30 dBFS, measured), the practice window says so and tells you to raise the gain or play closer; slow playing may still be recognised.

Settings (*Recognition settings* in the practice window): an optional **noise gate / background-noise subtraction** and a **suggested gain** after the level check; both can be switched off.

**The start window (before every start).** Each time a mode starts — the Start button, the Practice button of the transport bar, the coloured Arcade button — a small window first asks *Is your guitar in tune?* *No, open the tuner* opens the tuner and the mode does not begin; when you close the tuner the question comes back. *Yes, in tune* asks for a test strum: strum a chord and let it ring. The window judges the level of the 0.3 seconds after the strum: if more than 0.5 % of the samples reach the maximum it says the signal clips (lower the volume of the adapter or of the guitar); a signal that is too loud or too weak is named too, with what to do; a good level starts the mode at once. A bad level blocks the start: *Check again* judges a new strum, and *Continue anyway* goes on **for this start only** (it is also offered after 10 seconds without a strum). In **Follow the tempo** and **Arcade**, when the rhythm of your input and output was never calibrated, the window then says so, with **Calibrate now** (the 8 clicks of the Mode tab, about 10 seconds, inside the window; *Cancel* stops them and the two buttons come back; the delay found is kept as from the Mode tab) and **Go on without** (not asked again for this input and output until TuxGuitar starts again). Another input, output or format is not calibrated by the first one. Restarting a run or *play again* does not ask again. The window never changes the gain of the interface or of the system. The 0.5 % threshold was calibrated on the owner's recordings through a Rocksmith adapter and on the 180 microphone recordings of GuitarSet (none is flagged); the advice for Windows and Linux uses the macOS wording and was not tried there.

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

## Calibrating the recognition for one input

The **Input** tab has a dot next to the input chooser (grey: no input, orange: not calibrated, green: calibrated at 60 % or more, red: calibrated under 60 %) and two buttons, **Calibrate the recognition** and **Forget the calibration**. Calibrating opens one window: you play a short piece once, the window counts the sounds, asks whether the recording is good enough, searches the best recognition settings for this input on that recording (at most about two minutes) and shows the recognition rate with the default settings and with the settings found. When the recording cannot be used, or the settings found are not better than the default, you choose: record again, use the default, or stop. The profile is kept per input, applies in the tuner window, Follow, Arcade and Wait, and no audio is written to disk unless you tick, at the quality question, the box *Keep a copy of this recording for diagnosis* (a WAV in `tuxguitar-calibration-takes` of your home folder, never read back). The window shows a tablature picture of the next sound to play: six lines, the fret number on the string of each note, a chord in one column. The result is measured on one recording: play to confirm it. Details: `specs/013-device-recognition-calibration/`.

## Frequently asked questions

**The start button is greyed: why?** The input has not heard your guitar yet. Before a start, the level of the selected input must be checked: play a few notes (or strum a chord) and the button can be pressed. If it stays greyed, the input cannot be used: the *Level* line of the *Input* tab says why (too low, clipping, unplugged, microphone access refused). A line under the start position says the same since 2026-10-29.
