# TuxGuitarAdvanced

*English · [Français](docs/README-FR.md) · [Español](docs/README-ES.md)*

TuxGuitarAdvanced turns a free score and tablature editor into a complete environment for learning guitar interactively. You can open and edit your own pieces, then use a real guitar to move forward note by note, work at tempo and get precise feedback on the notes, the chords, the rhythm and the written techniques.

Its main strengths are the freedom to work with your own scores and tablatures, local operation with no subscription and no imposed catalogue, feedback built directly into the score, and detailed verification of techniques such as bends, slides, hammer-ons and pull-offs. Users keep control of their files, their audio devices, their settings and their progress.

### Where it stands today

Available now (version 0.4.6): practice note by note and chord by chord with your real guitar (the pitch is checked), bends, releases and pre-bends followed along their written path, hammer-on, pull-off and slide destinations accepted without a new pick, a steady tuner, input gain and noise reduction. **In progress, not available yet:** the tempo-following mode (working at the tempo of the score, with early, late and missed notes told apart) and a verdict *heard / not heard* for each technique: the technique detectors are not yet validated on enough real recordings, so the technique line still says "cannot be checked yet". The user guide lists what is measured and what is not: [English](docs/PRACTICE.md) · [Français](docs/PRACTICE-FR.md) · [Español](docs/PRACTICE-ES.md).

**TuxGuitarAdvanced** is an advanced version of [TuxGuitar](https://www.tuxguitar.app/), the open source multitrack tablature editor and player written in Java. It keeps everything TuxGuitar does (editing, playback, Guitar Pro / MIDI / MusicXML import and export...) and adds a **practice mode that listens to your guitar**, a **steadier tuner**, and tools to check your scores.

> Practise any track of any score with your real guitar: TuxGuitarAdvanced listens through your microphone or audio interface, tells you note by note and chord by chord whether you played what is written, and waits for you.

## What is new

| | Feature | In short |
|---|---|---|
| <img src="docs/images/features/practice.png" width="44" alt="Practice button"> | **Interactive Practice** | One click on the Practice button next to Play: the track under the score cursor is practised, note by note and chord by chord, with immediate feedback. [User guide](docs/PRACTICE.md) |
| | **Polyphonic recognition** | Notes and chords are recognised from what each attack adds to the sound, so a note is found even over a string that still rings (fingerstyle, arpeggios, bass under a melody). |
| | **Steady tuner** | The needle shows the median of the confident readings of the last quarter second: no shaking, no freezing on a fading string. |
| | **Input gain and noise reduction** | A gain slider (-6 to +18 dB) in the practice window and in the tuner, an optional noise gate / background-noise subtraction and a suggested gain. |
| <img src="docs/images/features/analyse-score.png" width="44" alt="Analyse the score"> | **Score analysis** | A score that opens is no longer interrupted by a dialog. *Edit > Display invalid measures* (or the toolbar icon) lists every problem of the song, with a one-click fix. |
| | **Diagnostic trace** | An optional text trace (numbers only, no audio) to understand why a note was or was not recognised. |

## Interactive Practice in 30 seconds

1. Open a score and put the cursor on the measure where you want to start.
2. Click the **Practice** button (<img src="docs/images/features/practice.png" width="18" alt="">) next to the Play button, or choose it in the *Tools* menu.
3. The first time, choose your audio input and play a string: the level meter tells you if the signal is good. Adjust the **Input gain** slider if needed.
4. Play what is shown. The next note or chord is framed on the score; a green tick means it was heard, a red cross means another note was heard, and nothing advances on silence or noise. **Stop** ends the practice at once.

What is verified: **pitch** (and every note of a chord). String and fret are never claimed from audio, and the written techniques (hammer-on, pull-off, slide, bend, vibrato) are shown with what was heard, or "cannot be checked yet" while no detector meets its quality target. Details, settings and limits: [docs/PRACTICE.md](docs/PRACTICE.md) (English), [docs/PRACTICE-FR.md](docs/PRACTICE-FR.md) (français), [docs/PRACTICE-ES.md](docs/PRACTICE-ES.md) (español).

## Tuned and tested on real recordings

The recognition was not tuned on synthetic sounds only. It was measured and adjusted on **more than 1,200 annotated recordings of real guitars** (single notes, intervals and chords, clean and with 11 audio effects such as distortion, overdrive, chorus, flanger, phaser, delay, reverb), plus recordings of an acoustic guitar played fingerstyle on a real score.

On that benchmark (6,178 automatic tests):

* single notes: **99.8 %** recognised, and **no wrong note** (a semitone or a tone away) accepted in more than 2,300 tests;
* intervals and triads with effects: 87 % recognised, chords of clean guitars: 95 %;
* chords with a note missing refused 96 to 98 % of the time.

Known limits are documented and measured, not hidden: chords whose bass note is G2 can be confused with the chord a whole tone higher, four-note seventh chords are recognised less often, and notes less than 0.15 s apart cannot be told apart. See [docs/PRACTICE.md](docs/PRACTICE.md#measured-quality-and-limits).

The default build runs **479 automatic tests**; the benchmark on recordings is an extra test you can run yourself (see the user guide). No audio of the benchmark is stored in this repository.

## Download and installation

Packages for macOS (Apple Silicon), Linux and Windows are published in [Releases](https://github.com/krl91/tuxguitaradvanced/releases). The macOS package is signed with a self-made certificate and not notarised: the first launch needs *Open Anyway* in *System Settings > Privacy & Security*. The Windows package needs Java 17 or later; the three packages include the Interactive Practice modules.

To build from source, refer to [INSTALL.md](INSTALL.md). The practice modules need a few more things:

* JDK 17 or later (Temurin 21 is the version the project is built and tested with) and Maven; SWT installed into the local Maven repository as described in [INSTALL.md](INSTALL.md);
* the build is run from the folder of your platform: `cd desktop/build-scripts/tuxguitar-macosx-swt-cocoa` (or `tuxguitar-linux-swt`, `tuxguitar-windows-swt-x86_64`), then `mvn -e clean verify -P native-modules` (add `-DskipTests` to skip the tests);
* the audio engine uses TarsosDSP, which is fetched from `https://mvn.0110.be/releases` (it is not on Maven Central), so the first build needs network access;
* on macOS, the launcher is compiled and signed with `clang` and `codesign`: install the Xcode Command Line Tools (`xcode-select --install`). The build does not embed a Java runtime in the `.app`: link or copy one into `Contents/MacOS/jre` to run it from the build folder.

## Documentation

* [Interactive Practice, user guide](docs/PRACTICE.md) - [Guide en français](docs/PRACTICE-FR.md) - [Guía en español](docs/PRACTICE-ES.md)
* [Architecture: CESAM views and sequence diagrams](docs/architecture/README.md)
* [INSTALL.md](INSTALL.md), [docs/CONTRIBUTING.md](docs/CONTRIBUTING.md)

## Credits and license

TuxGuitarAdvanced is a fork of TuxGuitar and is released under the GNU Lesser General Public License, like TuxGuitar.

Copyright (C) 2005-2022 Julián Casadesús, 2023-2025 guiv42, helge17 (TuxGuitar). The additions of this fork are by the TuxGuitarAdvanced contributors.

## Third party products

TuxGuitar includes the following third party products:

* SWT version: SWT (Standard Widget Toolkit): https://www.eclipse.org/swt/
* JFX version: JavaFX (Java client application platform): https://openjfx.io/
* Gervill (Java Software Synthesizer)
* iText (Free Java-PDF library): https://itextpdf.com/
* Magic Sound Font v2.0 - Contributed by Dennis Deutschmann
* TarsosDSP (pitch detection, GPL-3.0), used inside the audio engine of the practice mode
