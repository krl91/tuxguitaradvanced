# TuxGuitarAdvanced

<p align="center"><img src="specs/008-brand-identity/assets/tuxguitaradvanced-logo-primary.png" width="260" alt="TuxGuitarAdvanced"></p>

<p align="center"><sub>The logo is an AI-assisted redesign selected by the product owner. The upstream TuxGuitar notices and licence are unchanged, and no ownership of third-party marks is claimed.</sub></p>

*English · [Français](docs/README-FR.md) · [Español](docs/README-ES.md)*

### 🎸 Your guitar is the controller. Your score is the game.

**Press play on a tab. Play the notes as they fly towards your fretboard. Get judged on pitch *and* timing, in real time, with your own guitar.**

<p align="center"><img src="docs/images/arcade-flames.png" width="760" alt="Arcade mode with the flames of a streak of 12 notes on time, a hammer-on/pull-off and a bend"></p>

<p align="center"><em>Image drawn by the Arcade renderer of the application from a reference scene (not a screenshot of a window): a streak of 12 notes on time, the flames rising on both sides, a hammer-on/pull-off and a bend.</em></p>

**Your own tablatures, played on your real guitar: no subscription, no imposed catalogue.** TuxGuitarAdvanced is a free, open source tablature editor that **listens to your guitar** (a microphone or an audio interface is all you need). Open any score (Guitar Pro, MIDI, TuxGuitar...), pick a track, and play along with the rest of the band. No subscription, no catalogue, no account: **your songs, your guitar, your machine**.

> 🙏 **Built on TuxGuitar, which is already excellent.** TuxGuitarAdvanced is a **fork of [TuxGuitar](https://www.tuxguitar.app/)**, the great free multitrack tablature editor and player. Everything TuxGuitar does is still here, untouched: editing, playback, Guitar Pro / MIDI / MusicXML / PDF import and export, many languages. **We only add new things on top**: the practice modes (Wait, Follow the tempo, Arcade) and a steadier tuner. All the credit for the editor goes to TuxGuitar's authors and contributors ([helge17](https://github.com/helge17/tuxguitar) and many others); if you only want a superb editor, [go and use TuxGuitar](https://www.tuxguitar.app/) too, it deserves it.

> ⚠️ **Read this before you rely on it.** TuxGuitarAdvanced is young. The automatic tests are numerous and the recorded benchmarks are good, **but live validation is small**: it has been played mostly on the author's own Mac and guitar, **the Windows and Linux packages have never been run by anyone**, and very few inputs have been tried: **two so far**, the Mac's built-in microphone and the author's audio interface (acquisition card). The practice modes are still marked alpha below. It works for us and it is honestly rough in places, which is exactly why we need players like you, on **your** machine and **your** interface. [What is validated, and what is not](#what-is-validated-and-what-is-not) · [how to help](#join-the-band-help-us-level-it-up).

## Choose your game mode

| | Mode | The idea |
|---|---|---|
| 🎯 | **Wait for the right note** | The score **waits for you**. The next note is framed on the tab; play it and a green tick lights up. No pressure, perfect for learning a new piece. |
| 🥁 | **Follow the tempo** | The band starts (count-in, bass, drums, everything but your track) and you play **in time**. Every note is *on time, early, late, wrong or missed*. Raise the tempo step by step from 20 % to 150 %. |
| 🕹️ | **Arcade** (alpha) | Same band, but as a **game**: rounded rectangles come from the horizon in one straight line, a measure ahead, to their fret on the neck, coloured like their string; chords arrive as a panel and everything lands at the foot of the neck at the same instant, after a count-in during which the notes come from the far end. One click on the coloured button of the transport bar starts it at once; the result window also shows when you stop early. Chase streaks, read the verdict badges, beat your score. |

<p align="center"><img src="docs/images/manual/arcade-chord.png" width="700" alt="Arcade mode: a chord arrives as a translucent panel with its name (French interface)"></p>

*Chords come as a bar with the diagram and the name of the next chord, so you can get your hand ready. (This screenshot is in French: the interface is available in English, French and Spanish, and more languages can be added.)*

### What you get when the song ends

<p align="center"><img src="docs/images/manual/arcade-result.png" width="300" alt="The Arcade result window (French interface): score, share of the maximum, best streak and every verdict"> &nbsp; <img src="docs/images/manual-en/practice-wait-main.png" width="460" alt="Wait mode: the next note is framed on the score and a palette tells what was heard"></p>

A score, your share of the maximum, your **best streak** and the count of every verdict, then **play again** from the start or from the measure that beat you.

## It is also a real editor (and a real tuner)

Under the game there is the full **TuxGuitar** editor: write guitar, bass and drums tracks, tabs and staff, chords and effects, play it back, import and export Guitar Pro, MIDI, MusicXML, PDF... You can build your own practice song in ten minutes: the **[worked example](docs/MANUAL.md#part-c-a-complete-example-step-by-step)** takes you from an empty page to a three-track song (guitar, bass, drums) and then plays all three modes.

<p align="center"><img src="docs/images/manual/main-window.png" width="760" alt="The TuxGuitarAdvanced editor with a guitar part in staff and tablature (French interface)"></p>

And before you play, a **steady tuner** (the needle stays still when you are in tune):

<p align="center"><img src="docs/images/manual-en/tuner.png" width="300" alt="The guided tuner reading a G string, needle vertical"></p>

## Get started in two minutes

1. **Download** the latest build from [Releases](https://github.com/krl91/tuxguitaradvanced/releases) (macOS Apple Silicon today; see below).
2. Open a score, then **Tools > Interactive Practice** (or the ♪ button next to Play).
3. Pick your track, your input (check the level by plucking a string) and a mode. **Start.** That is it.

Never written a tab? The **[user manual](docs/MANUAL.md)** ([français](docs/MANUAL-FR.md)) has everything, with screenshots, including the step-by-step example.

## Join the band: help us level it up

This is a young project and every kind of help changes it. You do **not** need to be a programmer:

* 🎸 **Play it and tell us.** Your guitar, your microphone, your interface, your machine: the recognition was tuned on 1,200+ real recordings, but **more guitars is the best test there is**. Open an [issue](https://github.com/krl91/tuxguitaradvanced/issues) with what happened (the practice can write a numbers-only trace, no audio, to help us understand).
* 🎚️ **Beat the bosses.** Open problems we would love help with: more reliable timing under load, bass notes below G1, validating hammer-on / slide / vibrato detection on real playing, a Linux and a Windows build we can test.
* 🌍 **Translate it.** The interface is in English, French and Spanish; more languages are one text file away ([how](docs/CONTRIBUTING.md#translate-app-content)).
* 🎨 **Make it prettier.** Arcade is drawn with simple shapes: colours, themes, effects and sounds are wide open.
* 🧪 **Write a test or fix a bug.** The code is Java, built with Maven, with 700+ automatic tests ([INSTALL.md](INSTALL.md)); the architecture is documented in [docs/architecture](docs/architecture/README.md). Every bug gets a reproducing test first.
* 📣 **Share it.** Know a guitarist who would like to play their tabs like a game? Send them here.

### Where it stands today

Available now (version 0.6.0): practice note by note and chord by chord with your real guitar (the pitch is checked), bends, releases and pre-bends followed along their written path, hammer-on, pull-off and slide destinations accepted without a new pick, a steady tuner, input gain and noise reduction. **New, in alpha:** the **tempo-following mode** (the score plays at its tempo with the other tracks as a backing, and each note is judged on time: early, late, missed) and **Arcade**, the same run as a game where the notes come towards you on a neck, a measure ahead. **Not available yet:** a verdict *heard / not heard* for each technique (the detectors are not yet validated on enough real recordings, so the technique line still says "cannot be checked yet"). The user guide lists what is measured and what is not: [English](docs/PRACTICE.md) · [Français](docs/PRACTICE-FR.md) · [Español](docs/PRACTICE-ES.md).

**User manual** (editing and creating a score, and the whole Practice, with screenshots): [English](docs/MANUAL.md) · [Français](docs/MANUAL-FR.md).

**TuxGuitarAdvanced** is an advanced version of [TuxGuitar](https://www.tuxguitar.app/), the open source multitrack tablature editor and player written in Java. It keeps everything TuxGuitar does (editing, playback, Guitar Pro / MIDI / MusicXML import and export...) and adds a **practice mode that listens to your guitar**, a **steadier tuner**, and tools to check your scores.

> Practise any track of any score with your real guitar: TuxGuitarAdvanced listens through your microphone or audio interface, tells you note by note and chord by chord whether you played what is written, and waits for you.

## What is new

| | Feature | In short |
|---|---|---|
| <img src="docs/images/features/practice.png" width="44" alt="Practice button"> | **Interactive Practice** | One click on the Practice button next to Play: the track under the score cursor is practised, note by note and chord by chord, with immediate feedback. [User guide](docs/PRACTICE.md) |
| | **Follow the tempo and Arcade (alpha)** | The score plays at its tempo, the other tracks as a backing; each note is judged on time. Arcade shows the run as a game: rectangles come from the horizon towards the neck in a straight line, a measure ahead; a coloured button of the transport bar starts it at once. [Guide](docs/PRACTICE.md#practising-with-the-band-follow-the-tempo-and-arcade-alpha) |
| | **Polyphonic recognition** | Notes and chords are recognised from what each attack adds to the sound, so a note is found even over a string that still rings (fingerstyle, arpeggios, bass under a melody). |
| | **Steady tuner** | The needle shows the median of the confident readings of the last quarter second: no shaking, no freezing on a fading string. |
| | **Input gain and noise reduction** | A gain slider (-6 to +18 dB) in the practice window and in the tuner, an optional noise gate / background-noise subtraction and a suggested gain. |
| <img src="docs/images/features/analyse-score.png" width="44" alt="Analyse the score"> | **Score analysis** | A score that opens is no longer interrupted by a dialog. *Edit > Display invalid measures* (or the toolbar icon) lists every problem of the song, with a one-click fix. |
| | **Diagnostic trace** | An optional text trace (numbers only, no audio) to understand why a note was or was not recognised. |
| | **Per-input calibration** | Play a short piece once and the recognition settings that suit this input (your microphone or interface) are searched and kept; a coloured dot shows whether the input is calibrated. No audio is kept. |
| | **Arcade levels** | Five levels keep a share of the notes and of the chords of a piece (from 30 % of the notes and no chord to the whole piece); the sounds left out are played by the band. A permissiveness setting makes Arcade more forgiving. |

## Interactive Practice in 30 seconds

1. Open a score and put the cursor on the measure where you want to start.
2. Click the **Practice** button (<img src="docs/images/features/practice.png" width="18" alt="">) next to the Play button, or choose it in the *Tools* menu.
3. The first time, choose your audio input and play a string: the level meter tells you if the signal is good. Adjust the **Input gain** slider if needed.
4. Play what is shown. The next note or chord is framed on the score; a green tick means it was heard, a red cross means another note was heard, and nothing advances on silence or noise. **Stop** ends the practice at once.

What is verified: **pitch** (and every note of a chord). String and fret are never claimed from audio, and the written techniques (hammer-on, pull-off, slide, bend, vibrato) are shown with what was heard, or "cannot be checked yet" while no detector meets its quality target. Details, settings and limits: [docs/PRACTICE.md](docs/PRACTICE.md) (English), [docs/PRACTICE-FR.md](docs/PRACTICE-FR.md) (français), [docs/PRACTICE-ES.md](docs/PRACTICE-ES.md) (español).

## What is validated, and what is not

Two different things are measured here, and only the first one is large.

### 1. Offline, on recordings: measured, and numerous

The recognition was not tuned on synthetic sounds only. It was measured and adjusted on **more than 1,200 annotated recordings of real guitars** (single notes, intervals and chords, clean and with 11 audio effects such as distortion, overdrive, chorus, flanger, phaser, delay, reverb), plus recordings of an acoustic guitar played fingerstyle on a real score.

On that benchmark (6,178 automatic tests):

* single notes: **99.8 %** recognised, and **no wrong note** (a semitone or a tone away) accepted in more than 2,300 tests;
* intervals and triads with effects: 87 % recognised, chords of clean guitars: 95 %;
* chords with a note missing refused 96 to 98 % of the time.

Known limits are documented and measured, not hidden: chords whose bass note is G2 can be confused with the chord a whole tone higher, four-note seventh chords are recognised less often, and notes less than 0.15 s apart cannot be told apart. See [docs/PRACTICE.md](docs/PRACTICE.md#measured-quality-and-limits).

The default build runs **more than 1,500 automatic tests**; the benchmark on recordings is an extra test you can run yourself (see the user guide). No audio of the benchmark is stored in this repository. These figures say how the recognition behaves **on recorded sounds**; they are not a promise about your setup.

### 2. Live, in real conditions: small, and said so

* **Validated by a person, live:** playing on the author's own Mac and guitar, with **two inputs**: the built-in microphone of that Mac and an **audio interface (acquisition card)** (the measurements of the calibration and of Arcade quoted in the release notes were made with the built-in microphone; the calibration found nothing better than the defaults there, so **no gain from calibration has been measured yet**); the macOS package (0.6.0-beta.3, the same application as 0.6.0) opens with *Open Anyway* on the author's Mac.
* **Not validated by anyone yet:** the **Windows and Linux packages** (built, and the version inside them checked, but never run); **other computers and operating system versions**; **other audio interfaces and microphones** (the calibration exists to adapt to them, and has been tried on almost none); the **sound of the sounds Arcade removes**, and the **layout of the About window** on screen; long sessions.
* **How you can change that:** play it on your machine and interface, run the calibration, and [tell us](https://github.com/krl91/tuxguitaradvanced/issues) what happened, good or bad. A report with your system, your input device and what you saw is worth more to us than a star.

## Arcade at a glance

The notes come towards you on a neck, a measure ahead; a chord arrives as a frame with its name, and the **flames** of the picture at the top of this page grow with your streak of notes on time.

![Arcade: a chord progression coming towards the player, each chord a frame with its name (C, G7, Dm, E, Am)](docs/images/arcade-chords.png)

*Drawn by the application's own Arcade renderer from a reference scene, not a screenshot of a running window.*

## Download and installation

Packages are published in [Releases](https://github.com/krl91/tuxguitaradvanced/releases). Each release has a **macOS (Apple Silicon)**, a **Windows** and a **Linux** (tar.gz and .deb) package, all built by the release workflow, which checks the version and the commit inside each one. **Only the macOS package has been opened by a person; the Windows and Linux packages have never been run** (see above). They can always be built from source. The macOS package is signed with a self-made certificate and not notarised: the first launch needs *Open Anyway* in *System Settings > Privacy & Security*. The Windows package needs Java 17 or later; the three packages include the Interactive Practice modules.

To build from source, refer to [INSTALL.md](INSTALL.md). The practice modules need a few more things:

* JDK 17 or later (Temurin 21 is the version the project is built and tested with) and Maven; SWT installed into the local Maven repository as described in [INSTALL.md](INSTALL.md);
* the build is run from the folder of your platform: `cd desktop/build-scripts/tuxguitar-macosx-swt-cocoa` (or `tuxguitar-linux-swt`, `tuxguitar-windows-swt-x86_64`), then `mvn -e clean verify -P native-modules` (add `-DskipTests` to skip the tests);
* the audio engine uses TarsosDSP, which is fetched from `https://mvn.0110.be/releases` (it is not on Maven Central), so the first build needs network access;
* on macOS, the launcher is compiled and signed with `clang` and `codesign`: install the Xcode Command Line Tools (`xcode-select --install`). The build does not embed a Java runtime in the `.app`: link or copy one into `Contents/Resources/tuxguitar/jre` to run it from the build folder (the application is in `Contents/Resources/tuxguitar`: macOS accepts only the launcher in `Contents/MacOS`, and a bundle that does not follow that rule cannot be signed).


**Opening the macOS app the first time** (the app is signed with a self-made certificate and not notarised by Apple, so macOS asks you to approve it once):

1. Unzip the download and move `TuxGuitarAdvanced.app` to *Applications* (or anywhere you like).
2. Double-click it. macOS answers *« Apple could not verify that TuxGuitarAdvanced is free of malware… »*: click **Done** (not *Move to Bin*).
3. Open **System Settings → Privacy & Security**, scroll down to the *Security* section: the message *« TuxGuitarAdvanced was blocked… »* is there, with an **Open Anyway** button. Click it, then confirm with your password or Touch ID.
4. From then on, the app opens normally. For practice, allow the microphone when macOS asks (*System Settings → Privacy & Security → Microphone*).

If macOS says the app is *damaged* instead (a build before 0.6.0-beta.3), download 0.6.0-beta.3 or later; as a workaround, run `xattr -cr /path/to/TuxGuitarAdvanced.app` in a Terminal once.

## Documentation

* [Interactive Practice, user guide](docs/PRACTICE.md) - [Guide en français](docs/PRACTICE-FR.md) - [Guía en español](docs/PRACTICE-ES.md)
* [Version and commit of a build: where to read them, how a release gets its version](docs/VERSIONING.md)
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
