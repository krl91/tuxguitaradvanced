# TuxGuitarAdvanced user manual

*Français : [MANUAL-FR.md](MANUAL-FR.md).*

TuxGuitarAdvanced is an advanced version of [TuxGuitar](https://www.tuxguitar.app/), the multitrack tablature editor and player. It keeps everything TuxGuitar does (**editing and creating** scores, playback, import and export of many formats) and adds **Practice** (interactive practice: the application listens to your guitar and checks what you play), a **steady tuner** and score-analysis tools.

This manual has two parts:

* **[Part A: editing and creating](#part-a-editing-and-creating)**: write, change, listen to and export a score;
* **[Part B: Practice](#part-b-practice-interactive-practice)**: work on a score with your real guitar, in three modes (Wait for the right note, Follow the tempo, Arcade), plus the tuner and the recognition settings.

*The screenshots come from the macOS version, with the French interface and the dark theme. On Linux and Windows the windows are the same; shortcuts that use ⌘ on macOS use Ctrl.*

## Contents

* [Installation and first launch](#installation-and-first-launch)
* **Part A: editing and creating**
  * [A1. The main window](#a1-the-main-window)
  * [A2. The menus](#a2-the-menus)
  * [A3. Creating a first song](#a3-creating-a-first-song)
  * [A4. Entering notes](#a4-entering-notes)
  * [A5. Playing effects](#a5-playing-effects)
  * [A6. Chords](#a6-chords)
  * [A7. The structure of the song](#a7-the-structure-of-the-song)
  * [A8. Tracks, instruments and lyrics](#a8-tracks-instruments-and-lyrics)
  * [A9. The view](#a9-the-view)
  * [A10. Playback](#a10-playback)
  * [A11. Files: open, save, import, export, print](#a11-files)
  * [A12. Tools and configuration](#a12-tools-and-configuration)
  * [A13. Keyboard shortcuts](#a13-keyboard-shortcuts)
* **Part B: Practice**
  * [B1. Principle and requirements](#b1-principle-and-requirements)
  * [B2. The Interactive Practice window](#b2-the-interactive-practice-window)
  * [B3. Input level, gain and noise](#b3-input-level-gain-and-noise)
  * [B4. "Wait for the right note" mode](#b4-wait-for-the-right-note-mode)
  * [B5. "Follow the tempo" mode](#b5-follow-the-tempo-mode)
  * [B6. Arcade mode (alpha)](#b6-arcade-mode-alpha)
  * [B7. Audio recognition settings](#b7-audio-recognition-settings)
  * [B8. The tuner](#b8-the-tuner)
  * [B9. Score analysis (invalid measures)](#b9-score-analysis-invalid-measures)
  * [B10. Measured quality, limits and diagnostic trace](#b10-measured-quality-limits-and-diagnostic-trace)
  * [B11. FAQ and troubleshooting](#b11-faq-and-troubleshooting)

---

## Installation and first launch

* **Packages:** macOS (Apple Silicon), Linux and Windows are published in the project's [Releases](https://github.com/krl91/tuxguitaradvanced/releases). Not every package is necessarily present for each version: the release page says which are. The Windows package needs Java 17 or later; the macOS and Linux packages embed their Java.
* **macOS:** the package is signed with a self-made certificate and not notarised. On first launch, open *System Settings > Privacy & Security* and choose *Open Anyway*. The first time Practice listens to the microphone, macOS asks for microphone access: allow it.
* **Building it yourself:** see [INSTALL.md](INSTALL.md).
* **Files:** the native format is `.tg`. The application also opens Guitar Pro (`.gp3`, `.gp4`, `.gp5`, `.gpx`, `.gp`), Power Tab (`.ptb`), TablEdit (`.tef`) and MIDI, and exports to many formats (see [A11](#a11-files)).
* **Built-in help:** **F1** opens TuxGuitar's help (in English, with audio recognition pages in French and Spanish).

---

# Part A: editing and creating

## A1. The main window

<img src="images/manual/main-window.png" width="900" alt="The main window of TuxGuitarAdvanced with a small score">

From top to bottom, the main window has:

1. **The menu bar** (File, Edit, View, Composition, Track, Measure, Note, Marker, Player, Tools, Help).
2. **The main toolbar**: files, undo/redo, view, zoom, playback. The ♪ button next to Play is the **Practice** button (one press: practise the track under the cursor, see [B2](#b2-the-interactive-practice-window)).
3. **The side toolbar** ("Edit"): edit modes, structure, durations, dynamics, effects, notes.
4. **The score area**: the staff (notation) above and the **tablature** (one line per string) below. The grey rectangle is the **cursor**: actions apply to it. The tabs above the score are the open files.
5. **The track list**, with the main volume, and the "transporter" (one box per measure) to move through the song.
6. **The fretboard controls** and the **fretboard** (the black part at the bottom): it shows where to put your fingers and can also be clicked to enter notes.

<img src="images/manual/toolbar.png" width="900" alt="The main toolbar">

*Main toolbar: new, open, save, save as, preview, print, undo, redo, views (side toolbar, track list, fretboard, instruments), zoom, multitrack mode, keyboard, matrix, add/remove a measure, marker; in the centre, playback controls, time, tempo, metronome, count-in and play mode.*

Toolbars, track list and fretboard are shown or hidden from the *View* menu. A **right click** in the score, in the tablature or on a track of the list opens a **context menu** with the same actions as the menus.

## A2. The menus

All functions are in the menus. Shortcuts appear on the right of the entries; most can be changed (see [A12](#a12-tools-and-configuration)).

### File

<img src="images/manual/menu-fichier.png" width="300" alt="The File menu">

*New* (empty song, or the "Blues Trio" and "Classical Guitar Duo" templates), *Open*, *Open URL*, *Set custom template* (new songs start from that file) and *Revert to default template*, *Close / Close others / Close all*, *Save* (⌘S), *Save as…* (F12), *Import* (MIDI), *Export* (submenu below), *Print preview*, *Print*, *History* (last 10 files), *Quit*.

<img src="images/manual/menu-export.png" width="260" alt="The Export submenu">

### Edit

<img src="images/manual/menu-edition.png" width="300" alt="The Edit menu">

*Cut, Copy, Paste, Repeat* (copies the selection and pastes it right after), *Undo / Redo*, *Select all*, *Select nothing*, *Extend the selection*, then the **edit modes** (selection, score edition, sharp/flat, free edition), *Show invalid measures*, and the choice of **voice 1 or 2**.

### View

<img src="images/manual/menu-affichage.png" width="320" alt="The View menu">

Toolbars, track list, fretboard; the *Instruments*, *Player*, *Keyboard* and *Matrix* windows; **page / linear / multitrack layout**; show the staff and/or the tablature; compact mode; chord style; zoom.

### Composition

<img src="images/manual/menu-composition.png" width="320" alt="The Composition menu">

*Time signature*, *Tempo*, *Clef*, *Key signature*, *Shuffle*, *Open / Close repeat*, *Alternative endings*, *Song properties* (F5).

### Track

<img src="images/manual/menu-piste.png" width="300" alt="The Track menu">

Navigation between tracks, *Add track*, *Clone track*, *Remove track*, *Move up / down*, **Solo**, **Mute**, *Lyrics*, *Track properties*.

### Measure

<img src="images/manual/menu-mesure.png" width="300" alt="The Measure menu">

Navigation (first, previous, next, last measure), *Add measure*, *Clean measure* (empties the measure, keeps it), *Remove measure*, *Copy / Paste measure*, *Remove unused voice*, *Line break*.

### Note

<img src="images/manual/menu-note.png" width="340" alt="The Note menu">

*Insert rest*, *Delete note or rest*, *Clean note*, **Duration**, **Expression** (dynamics), **Effects**, **Chord**, *Insert text*, stem direction, **brush strokes** (up / down) and **pick strokes** (down / up), *Alternative enharmonic*, semitone and string up/down, *Move left / right*, *Custom beat move*.

### Marker, Player, Tools, Help

<img src="images/manual/menu-marqueur.png" width="300" alt="The Marker menu"> <img src="images/manual/menu-lecteur.png" width="320" alt="The Player menu"> <img src="images/manual/menu-outils.png" width="280" alt="The Tools menu"> <img src="images/manual/menu-aide.png" width="360" alt="The Help menu">

* **Marker**: add, list and navigate between markers.
* **Player**: play, stop, metronome, count-in, play mode, loop, highlight the played beat, continuous scrolling.
* **Tools**: *Transposition*, *Scales list*, *Browser*, *Batch file converter*, **Interactive Practice**, **Tuner**, *Plugins*, *Shortcuts*, *Configuration* (F7).
* **Help**: the built-in help and the "About" window.

## A3. Creating a first song

**1. An empty document.** An empty document is ready at launch. To create another: *File > New* (⌘N) or the first toolbar button.

**2. The song information.** *Composition > Song properties* (F5): name, performer, album, author, date, copyright, tablature creator, transcriber, comments. The **name** and the **author** are printed on the first page of exports: fill them in if the score leaves TuxGuitar.

<img src="images/manual/song-properties.png" width="420" alt="The song properties">

**3. The first track.** A new document already has one track. A track is an instrument: a song with two guitars and a bass has three tracks. **Double-click** the track in the list (or *Track > Track properties*):

<img src="images/manual/track-properties.png" width="420" alt="The track properties">

* **Name** and **colour** (shown in the track list);
* **Instrument**: the playback sound (wrench button: detailed settings, see [A8](#a8-tracks-instruments-and-lyrics));
* **Tuning**: click the wrench to choose the instrument, the number of strings (4 for a bass, 7, 8...) and the tuning, or define a custom one;
* **Number of frets** (except percussion tracks).

<img src="images/manual/track-tuning.png" width="460" alt="The Tuning window">

Give the **right tuning**: without it, the staff and the fret numbers would be wrong. When you change the tuning of a track that is already written, notes are kept as far as possible and fret numbers adapted; a note that becomes unplayable can be lost. The **capo** shifts the pitch at playback without changing the display.

**4. Before the notes:** set the **time signature** (4/4 by default), the **tempo** (120 by default), the **clef** and the **key signature** ([A7](#a7-the-structure-of-the-song)).

## A4. Entering notes

### Moving around

Click where you want, or use the arrows: **← →** go to the next or previous note of the track, **⌘← ⌘→** (Ctrl on Windows/Linux) to the previous or next measure, **↑ ↓** change string in the tablature. *Configuration > General > Play the beat when moving the cursor* plays each note.

### Writing in the tablature

Put the cursor on a string and **type the fret number (0 to 29)**: the note is written with the **duration** and **dynamics** selected in the side toolbar. On an existing note, **⇧→ / ⇧←** raise or lower the fret on the same string, **⇧↑ / ⇧↓** move the note to the string above or below. You can also click the **fretboard** at the bottom of the window, or the **keyboard** (*View > Show keyboard*).

### Writing on the staff

Switch to *Score edition mode* (*Edit* menu or side toolbar): a click on a line or a space writes the note (extra lines are added above and below if needed). For sharps or flats, also turn on *Sharp/flat mode*. *Selection mode* only moves the cursor without changing anything, handy to navigate safely.

### Durations

The sum of the durations of a measure must equal its time signature: TuxGuitar prevents irregular measures. Choose the duration in the side toolbar or *Note > Duration* (whole note to 64th); **+ / −** (numeric keypad) change it by one step. A **dotted note** is 50 % longer (a dotted half note = 3/4), a **double-dotted** one 75 %. The **division** menu (side toolbar, arrow on the right) creates **tuplets** (triplets...). The **tie** (key **L**) ties the note to the previous one: needed for a note that continues into the next measure, or for durations such as 5/16 (a quarter tied to a 16th). Keys: **\*** dotted, **/** triplet.

### Dynamics

ppp, pp, p, mp, mf, f, ff, fff (side toolbar "Expression" or *Note > Expression*): a new note takes the chosen dynamics; clicking another dynamics with the cursor on a note changes that note.

### Rests, deleting, shifting

* *Note > Insert rest* (Ins) inserts a rest before the cursor (within the measure, if there is an empty beat at its end);
* *Delete note or rest* (Del): deletes the note under the cursor; on an empty beat, shifts what follows to the left (within the measure);
* *Clean note* (⌘Del): same, but does nothing on an already empty beat;
* *Move left / right* and *Custom beat move*: shift all beats after the cursor;
* *Semitone up / down*, *string up / down*: act on all selected notes if all can be moved;
* *Alternative enharmonic* (**e**): the same note can be written C# or D♭; the default depends on the key signature.

### Selecting, copying, pasting

Click and drag to select notes (an effect then applies to the whole selection). *Edit > Copy* with no selection opens a window to choose the measures to copy (current track or all). *Paste* overwrites the content from the cursor; for copied measures, you choose to overwrite or to insert new measures. *Repeat* (⌘R) copies the selection and pastes it right after.

### Two voices and free edition mode

Each track can have **two voices** (*Edit > Select voice 1 / 2*, ⌘1 / ⌘2) to write two independent rhythmic lines. **Free edition mode** removes the check on the sum of durations: use it only with a good knowledge of music theory, especially with two voices. *Show invalid measures* opens the window that lists and fixes measures whose duration does not match ([B9](#b9-score-analysis-invalid-measures)).

### Measures

*Measure > Add measure*: one or several measures before or after the cursor, or at the end, in all tracks.

<img src="images/manual/add-measure.png" width="320" alt="The Add measure window">

*Clean measure* empties the measure (in the current track); *Remove measure* removes it from **all** tracks; *Copy / Paste measure* copies one or several measures, from the current track or from all, with the markers if you want, and the number of pastes.

## A5. Playing effects

Almost every effect has an icon in the "Effects" side toolbar and an entry in *Note > Effects*. With notes selected, the effect applies to the whole selection; otherwise to the note under the cursor. Effects are applied in the **tablature** (keep it displayed).

| Effect | Key | What it does |
|---|---|---|
| **Vibrato** | V | Pitch oscillation; marked with small waves. No parameters. |
| **Bend** | B | Pitch rise; opens the **bend editor** (below). |
| **Tremolo bar** | | Pitch change up or down by as much as an octave; dedicated editor. |
| **Slide** | S | Glide between the note and the next on the same string. |
| **Dead note** | X | Short, percussive note; the pitch can still be set. |
| **Hammer-on / Pull-off** | H | Arc between the note and the next on the same string, without a new pick. |
| **Ghost note** | O | In parentheses; only the writing changes. |
| **Accentuated / heavy accentuated** | | A little / much louder (marks > and ^). |
| **Let ring** | | "L.R.": let the note ring. |
| **Harmonics** | | Natural, artificial (A.H. 12, 9, 5, 7, 4, 3), pinched, tapped, semi. |
| **Grace note** | G | Very short note before or on the beat, with a transition (bend, slide, hammer). |
| **Trill** | | Rapid hammer-ons and pull-offs between two notes; dedicated editor. |
| **Tremolo picking** | | Rapid repetition of the same note (≡). |
| **Palm mute** | P | "P.M.": muted with the palm. |
| **Staccato** | | Duration halved (dot above). |
| **Tapping** | | "T" mark (no effect on the sound). |
| **Slap / Pop** | | "S" and "P" marks (no effect on the sound). |
| **Fade in** | F | Volume rises gradually; "<" mark. |

<img src="images/manual/bend-editor.png" width="420" alt="The bend editor">

**The bend editor:** the list on the right offers the usual cases (Bend, Bend/release, Bend/release/bend, Pre-bend, Pre-bend/release), all of one tone (2 frets); then you adjust the curve by placing points (solid red lines: one tone; dashed red: a semitone; grey: a quarter tone). *Clean* removes the effect. In the tablature, the bend is a curved arrow with the amount of the shift.

The tremolo bar editor works the same way (from −12 to +12 frets around the original pitch; the black line is the starting pitch).

## A6. Chords

The **chord editor** (key **A**, or *Note > Chord*) composes, recognises, stores and inserts a chord. It is the only way to add a chord **name** or **diagram** to the tablature.

<img src="images/manual/chord-editor.png" width="900" alt="The chord editor with fingering proposals for C major">

* **On opening**, the notes at the cursor are loaded, the chord is recognised and you hear it.
* **Left column**: the root (♯ or ♭), then the type (M, 7, maj7, 6, m, m7, sus2, sus4, dim, aug, 5...), extensions (9, 11, 13, with "add"), alterations (/5+, /5−, /9+...), and the **bass** (a bass different from the root gives an inversion).
* **Centre: the diagram**: click to place dots, above the grid to switch a string between "open" and "not played" (×). The slider on the right sets the starting fret.
* **Proposals** (bottom): fingerings generated for the chosen theory and your tuning; clicking a diagram loads it in the editor and gives its name.
* **Recognised**: the possible names of what you drew, with a probability.
* **Library**: *Add*, *Rename*, *Remove*; saved chords are then available through the arrow next to the chord icon (or *Note > Chord*).
* **Configuration**: type (most common, open, closed, inversions), number of proposals, "open" chords and fret range; not remembered after closing.
* **Options**: *Insert diagram and notes* or *diagram only*.

In *View > Chords* you choose how a chord is shown in the score: the **diagram**, the **name**, or both.

## A7. The structure of the song

<img src="images/manual/time-signature.png" width="250" alt="Time signature"> <img src="images/manual/clef.png" width="240" alt="Clef"> <img src="images/manual/key-signature.png" width="330" alt="Key signature">

* **Time signature** (*Composition > Time signature* or the "4/4" icon): numerator and denominator; *Until the end* applies the change to the rest of the song (otherwise one measure only, then back to the previous one). It applies to all tracks and can change during the song.
* **Tempo** (30 to 320 BPM, 120 by default): you can choose the beat value (quarter by default) and the extent: the whole song, until the end, or until the next tempo marker.

<img src="images/manual/tempo.png" width="520" alt="Tempo">

* **Clef**: treble (default), bass, alto, tenor; can change during the song.
* **Key signature**: the scale of the song (natural = C major, one flat = F major...), so as not to write every sharp or flat.
* **Shuffle / triplet feel**: the first note of each pair is lengthened (2:1), in eighths or sixteenths, for a swing or blues feel.
* **Repeats**: *Open repeat*, *Close repeat* (you choose the number of repeats), *Alternative endings* (you say in which pass each one is played).
* **Markers** (*Marker* menu): name, colour and position, shown above the measure in all tracks and in the "transporter"; a list lets you jump to them.

## A8. Tracks, instruments and lyrics

### Managing tracks

*Track > Add track* ("+" button), *Clone*, *Remove* ("×" button), *Move up / down*. All can be undone with ⌘Z. **Solo** listens to the chosen track only; **Mute** listens to all the others. In the list, clicking a track name shows it; ⌘⇧↑ / ⌘⇧↓ go to the previous or next track.

### Instruments and mixer

*View > Show instruments* (or the icon) opens the **Instruments** window: for each MIDI channel, the program (instrument) and bank, the **volume**, the **pan** and the effects (reverb, chorus, tremolo, phaser). Tick **Percussion channel** for a drum track. **Add** creates a new channel; the bar on the right sets the overall gain.

<img src="images/manual/instruments.png" width="700" alt="The Instruments window">

**Channels:** MIDI has 16 channels, channel 9 being reserved for percussion. Each track uses a normal channel and an effect channel (to play a dead note or a harmonic together with a normal note, or to apply a bend, slide, vibrato or tremolo bar to some notes only). As long as you have fewer than 7 instruments and one percussion, TuxGuitar assigns channels on its own. Beyond that, make two tracks that never play together share a channel.

### Lyrics

*Track > Lyrics* opens the track's lyrics editor. Choose the starting measure; each word (separated by a space or a new line) goes under the next note, rests are ignored; add spaces to skip notes.

<img src="images/manual/lyrics.png" width="420" alt="The lyrics editor">

## A9. The view

* **Page layout** (like on paper, with line breaks) or **linear layout** (a single line extending to the right). **Multitrack mode** shows all the tracks; otherwise only the selected track is shown. In practice: *linear + multitrack* for the whole, *page without multitrack* for one track; turning multitrack on switches to linear, turning it off goes back to page.
* **Show staff**, **show tablature** and **compact mode** (some details, such as bend details, are then hidden).
* **Zoom**: *Zoom in*, *Zoom out*, *Reset the view*, or ⌘ + mouse wheel.
* **The fretboard** (bottom) shows the frets to play and accepts clicks to enter notes; it exists for right- and left-handed players.
* **The keyboard** (*Show keyboard*): enter notes with the mouse.

<img src="images/manual/piano.png" width="720" alt="The keyboard editor">

* **The matrix** (*Show matrix*): mostly useful for **percussion** tracks; it splits the measure into boxes and a click adds or removes a sound. *Number of grids* sets the fineness; it does not lock the main window (duration, dynamics and current measure stay editable).

<img src="images/manual/matrix-editor.png" width="520" alt="The matrix editor">

## A10. Playback

The playback controls are in the toolbar, the *Player* menu and the **Player** window (*View > Show player*, ⌘T). **Space** plays and pauses. During playback the played note is coloured red and the current measure has a red background (adjustable in *Configuration > Styles*, option *Highlight played beat*).

<img src="images/manual/player.png" width="320" alt="The Player window">

* **Metronome**: a click on every beat. **Count-in**: a measure of waiting before the start (4 beats in 4/4, adjustable in the Player window).
* **Play mode** (F9):
  * **Simple mode**: a fixed tempo percentage (50 % = half speed), loop playback, range of measures to play (loop start and end);
  * **Training mode**: a starting tempo, an ending tempo and an increment; the tempo rises by the increment on every cycle (for example from 50 % to 100 % in steps of 5 %).

<img src="images/manual/play-mode.png" width="330" alt="The play mode">

* **Scrolling:** by default the page scrolls by one screen when playback reaches the edge; *Continuous scrolling* scrolls continuously.
* **The "transporter"** in the track list can be used with the mouse and scrolls during playback.

## A11. Files

* **Open** (⌘O): `.tg` (native format, recommended), Guitar Pro (`.gtp`, `.gp3`, `.gp4`, `.gp5`, `.gpx`, `.gp`), Power Tab (`.ptb`), TablEdit (`.tef`); **Import**: MIDI. *Open URL* opens an online file (save it locally afterwards). **Several files** can be open, each in a tab.
* **Save** (⌘S) in the latest native format; **Save as** (F12).
* **Export**: PDF, MIDI, ASCII, LilyPond, SVG, Guitar Pro 3/4/5, old TuxGuitar formats (1.0 to 1.5), audio file, MusicXML, and "Create an image directory". **Audio export** uses the TuxGuitar synthesizer with the instrument settings of the song.
* **Print preview**, **Print** (⌘P): one or all tracks, range of measures, layout options.
* **Templates:** *New* offers an empty song, *Blues Trio* and *Classical Guitar Duo*; *Set custom template* makes every new song start from a file of your choice.
* **Batch converter:** see [A12](#a12-tools-and-configuration).

## A12. Tools and configuration

### Transposition, scales, browser, converter

<img src="images/manual/transposition.png" width="300" alt="Transposition"> <img src="images/manual/scales.png" width="520" alt="The scales list">

* **Transposition**: shifts all or part of the song.
* **Scales list**: scales are shown on the fretboard to help improvise or compose (*Scales* button under the score).
* **Browser** (⌘B): a browser of file collections (local folders).

<img src="images/manual/browser.png" width="420" alt="The browser">

* **Batch file converter**: input folder (subfolders included), output folder, output format; keeps names and changes the extension. A window shows progress and can be cancelled.

<img src="images/manual/converter.png" width="420" alt="The batch file converter">

### Plugins

*Tools > Plugins* lists the plugins (file formats, sound outputs, others): enable or disable them, **Configure** sets those with options (for example the soundbank), **Info** gives version, author and description. A plugin is a `.jar` file to copy into `share/plugins/`.

<img src="images/manual/plugins.png" width="360" alt="The plugins window">

### Shortcuts

*Tools > Shortcuts*: a table of all actions and their shortcuts; double-click to change, *Clean* to remove, a filter at the top, *Defaults* to restore everything.

<img src="images/manual/shortcuts.png" width="380" alt="The shortcut editor">

### Configuration (F7)

*Tools > Configuration* groups six pages:

<img src="images/manual/settings-general.png" width="760" alt="Configuration, General page">

* **General**: window title (with variables such as `${appname}`, `${filename}`, `${songname}`), automatic table resizing, splash screen, play the beat when moving the cursor.
* **Styles**: fonts and colours of the score, tablature and lyrics (colour of the played measure, etc.).
* **Languages**: system language or a manual choice (French, English, Spanish, and TuxGuitar's other languages).
* **Themes** ("skins"): icons and buttons, in light or dark version; a theme is chosen with a preview.
* **Sound**: audio output, that is the MIDI sequencer and MIDI port. Ports come from output plugins: on macOS, the TuxGuitar synthesizer, Java Sound (Gervill) and Audio Unit. The built-in synthesizer reads SF2 or DLS soundbanks (the "Magic Sound Font" is provided).
* **Audio recognition**: the Practice and tuner settings ([B7](#b7-audio-recognition-settings)).

<img src="images/manual/settings-styles.png" width="560" alt="Styles page"> <img src="images/manual/settings-languages.png" width="560" alt="Languages page">

<img src="images/manual/settings-themes.png" width="560" alt="Themes page"> <img src="images/manual/settings-sound.png" width="560" alt="Sound page">

## A13. Keyboard shortcuts

*On macOS, read Ctrl as ⌘.* All can be changed.

| Category | Action | Shortcut |
|---|---|---|
| **Application** | New / Open / Save | Ctrl+N / Ctrl+O / Ctrl+S |
| | Save as | F12 |
| | Close the file | Ctrl+W |
| | Preview / Print | Ctrl+Shift+P / Ctrl+P |
| | Quit | Alt+F4 (⌘Q on macOS) |
| **Edit** | Cut / Copy / Paste | Ctrl+X / Ctrl+C / Ctrl+V |
| | Repeat | Ctrl+R |
| | Undo / Redo | Ctrl+Z / Ctrl+Y |
| | Select the track | Ctrl+A |
| | Voice 1 / voice 2 | Ctrl+1 / Ctrl+2 |
| | Song properties | F5 |
| **Tracks** | Next / previous track | Ctrl+Shift+Down / Ctrl+Shift+Up |
| | Add / remove a track | Ctrl+Shift+Ins / Ctrl+Shift+Del |
| **Playback** | Play / pause | Space |
| **Measures** | First / previous / next / last | Ctrl+Shift+Left / Ctrl+Left / Ctrl+Right / Ctrl+Shift+Right |
| **Notes** | Insert a note | digits 0 to 29 |
| | Insert a rest | Ins |
| | Delete the note or selection | Del |
| | Clean the beat | Ctrl+Del |
| | Tied / dotted / triplet note | L / \* / / |
| | Text | T |
| | Alternative enharmonic | e |
| | Note on the string above / below | Shift+Up / Shift+Down |
| | Pitch + / − | Shift+Right / Shift+Left |
| | Duration + / − | + / − |
| | Stems up / down | Ctrl+Up / Ctrl+Down |
| **Effects** | Bend / ghost / hammer-on / slide | B / O / H / S |
| | Vibrato / dead / palm mute | V / X / P |
| | Fade in / grace note | F / G |
| **Markers** | Add | Shift+Ins |
| | First / previous / next / last | Alt+Shift+Left / Alt+Left / Alt+Right / Alt+Shift+Right |
| **Windows** | Chord editor | A |
| | Configuration | F7 |
| | Player | Ctrl+T |
| | Browser | Ctrl+B |
| | Play mode | F9 |
| | Help | F1 |

---

# Part B: Practice (interactive practice)

## B1. Principle and requirements

Practice **listens to your guitar** and checks, **note by note and chord by chord**, that you play what the score says. It does not play for you. **It never records audio**: only numbers derived from the sound (pitch, level, notes heard) exist in memory while you practise.

**What it checks: pitch.** It does not claim to tell the string and the fret by ear (that stays an instruction of the tablature). For the written techniques (hammer-on, pull-off, slide, bend, vibrato) it follows how the pitch moves; the technique line says "cannot be checked yet" while the detectors are not validated on enough real recordings, which is not a mistake on your part.

**You need:**

* macOS (Apple Silicon), Linux or Windows (the modules are in the three packages);
* an **audio input** with the sound of your guitar: the built-in microphone works for clean playing; an **audio interface** with a direct signal is better. **Headphones** are recommended if the score also plays through the speakers, so that the computer's sound is not taken for your guitar;
* a score with a guitar track (standard tuning or any tuning written in the score).

Three ways to work, chosen in the *Mode* tab:

| Mode | Principle |
|---|---|
| **Wait for the right note** | The score **waits for you**: play each note when you are ready. |
| **Follow the tempo** | The score **plays at its tempo** after a count-in, the other tracks as a backing; each note is judged on time. |
| **Arcade** (alpha) | The same, shown as a **game**: the notes come towards you on a neck. |

## B2. The Interactive Practice window

**To open it:** *Tools > Interactive Practice*, or the **♪** button next to Play: one press practises the track under the score cursor, from the cursor, as soon as the input is usable. The window has **four tabs**; the *Practise this track* button (or *Follow the tempo of this track* / *Start Arcade* depending on the mode) and *Close* are always visible, whatever the tab.

### "Track" tab

<img src="images/manual/practice-track.png" width="480" alt="The Track tab">

Choose the **track to practise** (only usable tracks are offered, with the reason for the others). The window recalls the track's **tuning** and where practice starts (the cursor or the beginning). In **Arcade**, any pitched track is offered, whatever its number of strings (bass included).

### "Input" tab

<img src="images/manual/practice-input.png" width="480" alt="The Input tab">

* **Input**: the list of audio inputs, one per mono device and one per usable channel of multichannel devices. The application never changes input by itself.
* **Refresh**: reads the list again, for an interface plugged in **while** the application runs (the list is not watched).
* **Level**: an indicator and a message (good, too low, too high, clipping); *Check level again* restarts the measure (play a string).
* **Input gain**, *Reset*, *Suggest a gain*, *Apply*: see [B3](#b3-input-level-gain-and-noise).
* A tip recalls that headphones are better.

### "Mode" tab

<img src="images/manual/practice-mode-follow.png" width="480" alt="The Mode tab with Follow the tempo">

The **practice mode** (drop-down) and, for the timed modes, the **tempo** (20 to 150 % of the written tempo), the **timing tolerance** (strict, normal, lenient), the **rhythm calibration** and a manual **delay**; in Arcade, the **size of the incoming discs** (40 to 150 %). At the bottom, the window recalls the measured recognition delay (about 0.1 s, and that notes closer than 0.3 s may not be followed: practice then waits for you).

### "Passages" tab

<img src="images/manual/practice-passages.png" width="480" alt="The Passages tab">

For the "Wait" mode: **repeat passages** (off, or every 1, 2, 4 measures) and **repeat when errors exceed** a threshold (as a percentage of notes and chords). Errors are wrong or skipped notes and chords; a passage is repeated twice at most. The choice is kept until the application is closed (it can also be set in *Configuration > Audio recognition > Passages*).

## B3. Input level, gain and noise

* Under the level meter, the **Input gain** slider raises or lowers the signal inside the application (−6 to +18 dB); **Reset** returns to 0 dB. The gain is never remembered: it returns to 0 dB when the input changes and at every start. If the boost only amplifies background noise, or makes the signal clip, a warning says so and tells you to raise the gain on the audio interface instead.
* While you play, if your notes are too weak for reliable fast recognition (below about −30 dBFS measured), the window says so: raise the gain or move closer; slow playing may still be recognised.
* **Reduce background noise** (setting, off by default): noise gate and background-noise subtraction. **Suggest a gain** (on by default): proposes a gain after the level check. See [B7](#b7-audio-recognition-settings).

## B4. "Wait for the right note" mode

This is the original mode. **Choosing it:** *Mode* tab, "Wait for the right note". Then *Practise this track*.

<img src="images/manual/practice-mode-wait.png" width="400" alt="The Mode tab, Wait for the right note mode">

The score **waits**: the expected note or chord is **framed** on the score, the current passage is highlighted, and the practice **palette** stays displayed.

<img src="images/manual/practice-wait-main.png" width="900" alt="Practice in Wait mode: framed note on the score and the palette">

The palette (the "Interactive Practice" window during practice) says:

* what was **heard** ("D5, 19 cents too high, confidence 98 %") and what to **play** next ("Play G4");
* the progress of the **passage** ("Passage 1 of 3 (measures 1–2) · try 1 of 3 · errors 1 of 5");
* the **input level**;
* the buttons **Skip** (ignore the current note), **Resume**, **Restart** and **Stop**.

A green tick means "heard", a red cross "another note was heard"; **nothing advances on silence or noise**. **Stop** ends practice at once. When the errors of a passage exceed the threshold set in the *Passages* tab, that passage is repeated.

## B5. "Follow the tempo" mode

**Choosing it:** *Mode* tab, "Follow the tempo". Then *Follow the tempo of this track*. The score runs **at its tempo after a count-in**; the **practised track is silent**, the other tracks play as a backing. Each note or chord is **judged on time**:

| Verdict | Meaning |
|---|---|
| On time | Right note, within the tolerance. |
| Early / Late | Right note, but shifted (in milliseconds). |
| Wrong | Another note was played. |
| Missed | Nothing expected was heard. |
| Extra | An extra note that is not written. |
| Not judged | An event that cannot be judged (passed automatically, technique not checkable...). |

**Settings:**

* **Tempo (% of the written tempo)**: 20 % to 150 %. Start slower and raise it step by step.
* **Timing tolerance**: how far from its time a note may be and still count as on time; the window adapts to the tempo. Three levels: strict, normal, lenient (the initial value is the one in *Configuration > Audio recognition > Practice, notes*).
* **Rhythm calibration**: sound takes time to cross your input, your output and the audio format; without calibration, your notes may be judged a little early or late and the window says **"Reduced rhythmic precision"**. The **Calibrate…** button plays **8 clicks** (about 10 seconds): play one note on each click, with the guitar; **Cancel** stops the clicks at once. The calibration is valid for **this input, this output and this audio format**: changing one asks for a new calibration. Otherwise, type a **delay (ms)** by hand (positive when your notes are heard late, between −1000 and 1000 ms) with *Apply*, or *Forget*.
* If the input delay is high or unstable (typically wireless), the window warns you: a wired input is steadier.

During the run, the palette shows the verdict of the last note, the counter (on time, early, late, wrong, missed, extra, not judged) and the **next** note. At the end, a **summary** details the numbers; it is only kept while the window is open.

<img src="images/manual/practice-follow-palette.png" width="400" alt="The Follow the tempo palette during a run"> <img src="images/manual/practice-follow-summary.png" width="400" alt="The summary at the end of a run">

**Pause, resume:** if you play the score from the main window, following stops (normal playback goes on); **Resume** restarts after a pause, the notes that went by meanwhile are not judged. The mode is not possible while the score is already playing: stop it first.

## B6. Arcade mode (alpha)

**Choosing it:** *Mode* tab, "Arcade". In the *Track* tab, choose the track you play (any pitched track, **bass included**), then **Start Arcade**. The practice window hides while the game window runs.

<img src="images/manual/practice-mode-arcade.png" width="460" alt="The Mode tab, Arcade chosen">

It is the "Follow the tempo" mode (same tempo, tolerance and calibration) shown as a game. The track you choose is silent, all the others play.

<img src="images/manual/arcade-run.png" width="760" alt="An Arcade run: notes coming from the horizon towards the neck">

**How to read the screen:**

* **Every note is seen coming for five seconds**, from the horizon, whatever the number of notes. A note written at fret *n* always follows **the same lane** to the **middle of its fret** on the neck; only its **colour** (that of its string) depends on the string. The disk that reaches the neck is the one you play now. The number in the disk is the fret number.
* **The neck** (bottom) shows the strings (with their open notes, the highest on top) and the numbered frets. The target of the note to play is a solid disk on the exact fret and string.
* **Chords** are a bar on a central axis; the next chord shows its **diagram** and **name**, the others are bars without diagram.
* **At the top**: your **streak** (left), the **tempo** as a percentage and **what comes next** (right: "Next: string 3, fret 2" or "Next: chord").
* **Verdicts** (on time, early, late, wrong, incomplete, missed) appear as a **badge** under the neck once the note is judged (the text always goes with the colour).
* **Pause / Resume** and **Stop** are at the bottom right. Playing the score from the main window stops the game.

<img src="images/manual/arcade-chord.png" width="760" alt="A chord: diagram and name, incoming notes and targets on the neck">

**The size of the incoming discs** is a setting (40 % to 150 %, 100 % by default) in the *Mode* tab, for the discs that are not on the neck yet. It is kept until the application is closed.

**At the end**, the **Result** window gives the score, the share of the maximum, the best streak and the count of each verdict. You can **play again from the start** or **from a measure** you choose (*From measure*, then *Play*). The techniques (hammer-on, slide, bend...) are reported apart and do not change the score.

<img src="images/manual/arcade-result.png" width="330" alt="The result window">

**Alpha, what is not verified:** see [B10](#b10-measured-quality-limits-and-diagnostic-trace). Notes **below G1 (49 Hz)** (the lowest of a standard bass) are shown but not judged. **Hearing your own guitar through the application is not offered**: play through your amplifier or through headphones plugged into the audio interface.

## B7. Audio recognition settings

*Tools > Configuration > Audio recognition*. Each setting has a **recommended value** and a **Restore** button per category; *Restore all audio recognition settings* resets everything. Changes are saved with **OK**; **Cancel** keeps the previous values. Tuner settings apply at once, practice settings at the **next Start**; level and attack settings briefly restart listening. Hover a setting's name to see its effect. The screenshots show the values of the test machine, not always the recommended ones (given below).

<img src="images/manual/rec-input.png" width="700" alt="Audio input category">

**"Audio input" category**: the **input** (shared by the tuner and practice; it changes at once and is not remembered after the application is closed, **Refresh** button included), the **minimum usable level** (−40 dBFS recommended), the **high-level alert** (−6 dBFS), **Reduce background noise** (off) and **Suggest an input gain** (on).

<img src="images/manual/rec-tuner.png" width="700" alt="Tuner category">

**"Tuner"**: the **deviation considered in tune** (5 cents) and **Keep the last note** (on: the grey needle keeps the last reading when the string goes silent).

<img src="images/manual/rec-notes.png" width="700" alt="Practice, notes category">

**"Practice, notes"**: the **accepted pitch deviation** (35 cents recommended); the detector's **minimum certainty** (80 %) before a note is judged; the **note confirmation time** (107 ms); the **attack sensitivity** (4.5 dB); the handling of **legato, pull-offs and slides** (a new attack is required for each note, the only choice in this version); **Check playing techniques** (on); the **timing tolerance** for playing at tempo (normal).

<img src="images/manual/rec-chords.png" width="700" alt="Practice, chords category">

**"Practice, chords"**: the **chord strictness** (33 %), the **strum window** (600 ms: the maximum time of a pick stroke across several strings), the **required strings** (all checkable strings) and the **extra strings** (not checked yet).

<img src="images/manual/rec-passages.png" width="700" alt="Passages category">

**"Passages"**: repeat passages (every 2 measures), repeat when errors exceed a percentage (50 %), **maximum number of repeats** (2).

<img src="images/manual/rec-engine.png" width="700" alt="Recognition engine category">

**"Recognition engine"**: the pitch detection engine (automatic, FastYin; the only choice in this version).

<img src="images/manual/rec-advanced.png" width="700" alt="Advanced category">

**"Advanced"**: the "too sharp" allowance per fret (0.7 cent: fretted notes sound a little sharper towards the high end of the neck; this setting adds those cents on the sharp side only, 0 turns it off), the saturated sample level (99 %), the share of saturated samples (10 %), the tuner's minimum certainty (80 %), the attack detection threshold (−50 dBFS), the harmonics per string for chords (2) and the chord confirmation time (43 ms).

## B8. The tuner

*Tools > Tuner* opens the tuner; it shares its pitch detection with practice.

<img src="images/manual/tuner.png" width="460" alt="The guided tuner">

* **Two modes**: *Guided (6 strings)* walks you through the strings; *Chromatic* shows the nearest note of whatever you play.
* **Tuning**: a preset, *Custom…* (one note per string) or the **tuning of a track** of the open song.
* **The needle** shows the **median of the confident readings of the last quarter second**: a glitch cannot move it, a short gap does not freeze it. The text says what to do ("Too low: 12 cents flat of E2. Tune up", "In tune", "Too high...").
* **A frozen grey needle** ("Last reading"): the string is silent or too quiet, play it again.
* **Input**: the selected input and its level (good / too low / too high / clipping), **Check level again** and the **gain** slider (−6 to +18 dB) with its **Reset**; **Settings** opens the tuner settings.
* **Not while practising**: the tuner refuses to open while the practice window is open (they share the microphone); close it first, tune, then start again.

## B9. Score analysis (invalid measures)

Opening a Guitar Pro or MIDI file no longer interrupts you with a dialog about measures whose duration does not match the time signature. To see **all the problems of a song**: *Edit > Show invalid measures* (or the toolbar icon). The window lists the invalid measures and tied notes of every track (track, measure, voice, too long / too short...), with filters, the status of the current measure and a **Fix** button. It blocks nothing: leave it open while you edit.

## B10. Measured quality, limits and diagnostic trace

On a benchmark of 1,208 annotated recordings of real guitars (clean and with 11 effects), 6,178 tests:

* **single notes recognised**: 99.8 % (effects) to 100 % (clean);
* **a note a semitone or a tone away from the played one accepted**: none in more than 2,700 tests;
* **intervals and triads with effects recognised**: 87 %; **chords of clean guitars**: 95 %;
* **chords with a note missing refused**: 96 to 98 %;
* **delay** between playing a note and its recognition: about 0.1 s (median 105 ms, 90 % within 170 ms).

On an **independent dataset** (GuitarSet, rhythmic comping on an acoustic guitar), the result is **much lower**: 60 % of complete chords recognised (six pickup channels summed), 46 % through the microphone; 13 % of chords with a note missing accepted by mistake.

**Limits, measured and not hidden:**

* **Notes close together:** about 0.3 s between picked notes are followed; faster, only the first note of a run is recognised and practice waits for you, it never marks a wrong note. The window says so.
* Chords whose lowest note is G2 are sometimes accepted a whole tone higher; four-note seventh chords and some open chords (A major, E major) are recognised less often.
* An octave, or a note that is a harmonic of a lower chord note, cannot be verified separately.
* **Alpha (Arcade and tempo following):** validated by automatic tests and by the owner on a Mac, **not** by other players, guitars, microphones or machines. In a 10-minute stress run, the audio chain still **misses about one analysis deadline per minute** (an operating-system scheduling gap); it was not shown to change a judgement, but the goal is not met.
* The settings were tuned on these recordings; another guitar or microphone may call for another gain or sensitivity.

**Diagnostic trace (to understand a failure).** When a note you played correctly is not recognised (or a wrong one is), the application can write **what it heard, as numbers only, no audio**:

1. quit the application, then start it with the property `tuxguitar.practice.trace` naming a file, for example on macOS from a terminal: `open -n --env JAVA_TOOL_OPTIONS="-Dtuxguitar.practice.trace=/tmp/practice-trace.csv" /path/to/TuxGuitarAdvanced.app`;
2. play the passage in practice;
3. open the CSV: one line per analysis step (time, event, expected notes, detected pitch and certainty, level, attack strength, amplitude of each expected note and of its neighbours, `ACCEPTED` lines). Send it with a description of what should have happened.

## B11. FAQ and troubleshooting

**Nothing is heard / the level is "too low".** Check the chosen **input** (*Input* tab), play a string while watching the indicator, raise the interface gain (preferably) or the application gain, move the microphone closer. With an interface plugged in after launch, press **Refresh**.

**macOS does not let practice listen to the microphone.** *System Settings > Privacy & Security > Microphone*: allow TuxGuitarAdvanced.

**The tuner does not open.** The practice window is open (or hidden during a run): close it, then open the tuner.

**Notes are always judged "late" or "early".** Calibrate the rhythm (*Mode* tab, **Calibrate…**), or enter a manual delay. A wired input and wired headphones are steadier than wireless.

**The practice window disappeared after an Arcade run.** Reopen it with *Tools > Interactive Practice* (it comes back in versions after 0.5.0-alpha.1; in that version, restart the application if needed).

**A Guitar Pro score reads badly or practice refuses the track.** Practice keeps only pitched tracks; percussion tracks are excluded, and the reason is shown in the *Track* tab. *Show invalid measures* helps find measures whose duration is wrong.

**I want more or less strictness.** In *Configuration > Audio recognition*, adjust the accepted pitch deviation, the minimum certainty, the confirmation time and, for chords, the chord strictness; *Restore* goes back to the measured values.

**Where to find more details?** [PRACTICE.md](PRACTICE.md) (Practice guide, quality measures), [README.md](README.md) and the release notes.
