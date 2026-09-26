# Research: Interactive Practice MVP

**Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Date**: 2026-09-26

Every conclusion below cites the repository file it rests on. Items that could not be settled by
reading source are marked **SPIKE** (needs an experiment) or **PRODUCT QUESTION** (needs the
product owner).

---

## R1. Build and module structure

**Evidence**

- No root build. Each platform/toolkit variant has an aggregator POM under
  `desktop/build-scripts/<variant>/pom.xml` listing modules and copying jars into the package
  (e.g. `desktop/build-scripts/tuxguitar-macosx-swt-cocoa/pom.xml`: module list lines 23–57;
  core jars → `Contents/MacOS/lib`, plugin jars → `Contents/MacOS/share/plugins`).
- `desktop/pom.xml` is the parent of all desktop modules and pins `maven-compiler-plugin`
  `<release>9</release>` (≈ line 360). `common/TuxGuitar-lib/pom.xml` also targets release 9
  and has no parent. **All new code must compile at Java 9 language level** (no `var`,
  records, switch expressions, text blocks) even though the validated JDK is 21.
- Desktop plugin modules use `<sourceDirectory>src</sourceDirectory>` (e.g.
  `desktop/TuxGuitar-tuner/pom.xml`, `desktop/TuxGuitar/pom.xml`), so a `src/test/java` folder
  inside them would be compiled as main code. New test folders must live outside `src`.
- The tuner plugin is bundled by 9 desktop variants (freebsd-jfx/swt, linux-jfx/swt/qt,
  macosx-jfx/swt, windows-jfx/swt). Android does not use it.
- Native modules (`desktop/build-scripts/native-modules/`) cover MIDI/synth output only
  (AudioUnit, ALSA, FluidSynth, JACK, LV2, WinMM). None captures audio.

**Decision**: new code lives in desktop plugin modules only; nothing is added to `common/`
(avoids Android and Java-9/Android constraints on shared code for now). The new practice plugin
is added to the macOS SWT build script first; other variants are added only when validated.

---

## R2. Plugin and extension architecture

**Evidence**

- Plugins implement `app.tuxguitar.util.plugin.TGPlugin` (`getModuleId`, `connect`,
  `disconnect`) — `common/TuxGuitar-lib/src/main/java/app/tuxguitar/util/plugin/TGPlugin.java`.
- Discovery: `TGPluginManager.lookupPlugins()` uses `TGServiceReader.lookupProviders(TGPlugin.class,…)`
  over `META-INF/services/app.tuxguitar.util.plugin.TGPlugin`
  (e.g. `desktop/TuxGuitar-tuner/share/META-INF/services/…`). Enable/disable per module id
  via `TGPluginProperties`.
- All jars under `share/plugins` are added to one shared `URLClassLoader`
  (`desktop/TuxGuitar/src/app/tuxguitar/app/util/TGClassLoader.java`,
  `TGFileUtils` lines 44–56). **Plugins can use each other's classes**; precedent:
  `desktop/TuxGuitar-synth-gervill/pom.xml` depends (`provided`) on `tuxguitar-synth`, and
  `TuxGuitar-jack-ui` on `tuxguitar-jack`.
- Menu contribution: `TGToolItemPlugin`
  (`desktop/TuxGuitar/src/app/tuxguitar/app/tools/custom/TGToolItemPlugin.java`) maps one
  action, registers it as key-bindable (`getKeyBindingActionIds().addActionId`) and UI-thread
  synchronised (`getSyncThreadInterceptor().addActionId`), then adds it to the **Tools** menu
  (`view/menu/impl/ToolMenuItem.java`, "CUSTOM TOOLS" loop).
- Additional actions can be registered the same way through public APIs:
  `TuxGuitar.getInstance().getActionManager().mapAction(id, action)` and
  `TGActionAdapterManager` interceptors (`action/TGActionAdapterManager.java`).
- The main window layout is fixed (`view/main/TGWindow.java`: toolbar, tab folder, table
  viewer, fretboard). **There is no API for plugins to dock panels into the main window.**
  Plugins open their own non-modal windows via `UIFactory.createWindow(parent, false, false)`
  (as `TGTunerDialog` does).
- Events: `TGEventManager`; `TGEditorManager.addUpdateListener` (`common/TuxGuitar-editor-utils/…/TGEditorManager.java:153`)
  delivers `TGUpdateEvent` (`SELECTION`, `MEASURE_UPDATED`, `SONG_UPDATED`, `SONG_LOADED`, …);
  `MidiPlayer.addListener` (`MidiPlayer.java:1252`) delivers `MidiPlayerEvent`
  (`NOTIFY_STARTED`, `NOTIFY_STOPPED`, …).

**Decision**: Interactive Practice is a **plugin** (new module) using only existing public
extension points: a `TGToolItemPlugin` entry ("Interactive Practice…"), extra actions mapped via
`TGActionManager` and registered as key-bindable/sync-thread, a non-modal practice window, and
listeners on update and player events. No main-window docking (no extension point exists).

---

## R3. Song and tablature model; expected pitch

**Evidence** (`common/TuxGuitar-lib/src/main/java/app/tuxguitar/song/models/`)

- `TGTrack`: `getStrings()`, `stringCount()`, `getOffset()` (range `MIN_OFFSET=-24` …
  `MAX_OFFSET=24`, lines 19–20), `isPercussion()` (via the channel, line 156), `getMaxFret()`,
  `getName()`, `getChannelId()`.
- `TGString`: `getNumber()`, `getValue()` = open-string MIDI note.
- `TGChannel`: `getProgram()`, `getBank()`; `isPercussionChannel()` = bank 128 (line 164).
- `TGMeasureHeader`: `isRepeatOpen()`, `getRepeatClose()`, `getRepeatAlternative()`.
- `TGBeat`: up to `MAX_VOICES = 2` voices (`TGBeat.java:15`); `TGVoice`: `isEmpty()`,
  `isRestVoice()`, `getDuration()`, `getNotes()`.
- `TGNote`: `getValue()` = fret, `getString()`, `isTiedNote()`, `getEffect()`.
- `TGNoteEffect`: `isDeadNote`, `isGhostNote`, `isVibrato`, `isBend/getBend`,
  `isTremoloBar`, `isTrill`, `isTremoloPicking`, `isHammer`, `isSlide`, `isHarmonic`
  (types natural/artificial/tapped/pinch/semi), `isGrace/getGrace`, `isPalmMute`,
  `isStaccato`, `isLetRing`, `isAccentuatedNote`, `isHeavyAccentuatedNote`, `isTapping`, …
- **Grace notes are not separate notes**: `TGEffectGrace` is an effect on the principal note
  with its own `fret`, `dead`, `onBeat`, `transition`, `duration`
  (`effects/TGEffectGrace.java`).
- **There is no capo field.** Importers write capo into the track offset:
  `GPXDocumentParser.java:134` (`tgTrack.setOffset(gpTrack.getCapo())`),
  `TESongParser.java:110`. The track-tuning dialog labels it "Offset"
  (`TGTrackTuningDialog.java:464`, key `tuning.offset`).
- Sounding pitch used by playback: `MidiSequenceParser.java:198`
  `key = transpose + track.getOffset() + note.getValue() + string.getValue()`; grace:
  line 216 (`offset + grace.getFret() + string`). `transpose` is a playback preference set on
  the parser, not score data.

**Decisions**

- Expected MIDI pitch of a note = `track.getOffset() + string(note).getValue() + note.getValue()`.
  Grace: `offset + string + grace.getFret()`. The player's `transpose` setting is ignored
  (spec FR-061: expected pitches come from the score).
- Two explicit concepts (spec FR-008/FR-052, decided P3):
  - **expected sounding pitch** = `offset + string + fret` — never altered;
  - **physical open-string target** (tuner guidance and preparation text only) =
    `string` when `offset ≥ 0` (positive offset shown as "capo at fret k", capo-equivalent),
    `string + offset` when `offset < 0` (shown as "tune down |k| semitone(s)").
  The realization is Practice's rule for reproducing the score's pitches on the written
  strings/frets; the product does not claim the file's author meant a capo or a down-tuning.
- Event classification (see data-model.md) uses only the accessors above.

**Extreme offsets (decided P8)**: `TGTrack` allows −24…+24 (`MIN_OFFSET`/`MAX_OFFSET`).
- Positive: no evidence for a capo limit (`TGTrack.DEFAULT_MAX_FRET = 29`, `TGTrack.java:22`,
  is an editing limit) → no extra notice; raw offset + "capo at fret k" + targets are shown.
- Negative: evidence exists in TuxGuitar's built-in presets. The "unusual setup" notice is shown
  when any physical target `openString[i] + offset` is below the minimum of string *i* across
  the built-in "Guitar › 6-String" group (`TuningManager.getTgTuningsGroup()`, loaded from
  `common/resources/tunings/tunings.xml`; user custom tunings excluded). The bounds are read at
  runtime, not hard-coded. With today's file (25 presets) the per-string minima are
  58, 53, 49, 44, 39, 34 (string 1 = highest; `A# Tuning`). Informational only: never blocks,
  never changes expected pitches or physical targets, never claims the data is wrong.

---

## R4. FR-003 — identifying a supported 6-string guitar track

**Trustworthy properties** (exact, structural):

| Property | Source | Reliable for |
|---|---|---|
| Exactly six strings | `TGTrack.stringCount()` | excluding 4/5-string bass, 7-string guitar |
| Each string has a pitch | `TGString.getValue()` | "defined open-string pitch" |
| Percussion | `TGTrack.isPercussion()` / `TGChannel.isPercussionChannel()` (bank 128) | excluding drum tracks |
| Contains practice events | traversal of measures/beats | "at least one practice event" |

**Untrustworthy or absent for "is this a guitar"**:

- No instrument-type field exists on `TGTrack` or `TGSong`.
- `TGChannel.getProgram()` is the General MIDI *playback sound* (guitars 24–31, basses 32–39).
  It is user-chosen and importer-dependent; a guitar part played with a piano sound is common.
- `TGTrack.getName()` is free text.
- Tuning values do not separate instruments: TuxGuitar's own presets
  (`common/resources/tunings/tunings.xml`) include a **6-string bass** (`48,43,38,33,28,23`)
  and a **6-string violin** (`76,69,62,55,48,41`) next to guitar tunings from
  `F` (`65…41`) down to `B` (`59…35`), plus Major Thirds, Fourths, NST, Nashville.

**Conclusion**: the existing model cannot reliably distinguish a conventional 6-string guitar
from another six-string fretted instrument. This was returned to the product owner, who decided
(spec FR-003, clarification 2026-09-26): the learner selects or confirms exactly one **Practice
Track**; structural checks exclude incompatible tracks; a guitar-family General MIDI program
(0-based programs 24–31 as stored by `TGChannel.getProgram()`) is only an advisory hint for
preselection; a compatible candidate without that hint needs an explicit learner confirmation;
nothing is written into the song.

**Caveat on the hint**: `TGChannel.DEFAULT_PROGRAM = 25` (acoustic steel guitar,
`TGChannel.java:16`), so channels created in TuxGuitar without changing the sound look like
guitars. The hint therefore mainly helps with imported files and must never replace the
learner's choice — consistent with the decision.

**Follow-up decisions (P6)**: preselect the caret's track when it is a compatible candidate,
else a guitar-hinted candidate; remember confirmations in memory per song/track for the
application run; selecting another track or moving the caret during a session neither stops nor
retargets it (FR-066 revised); outside practice the tuner uses a track's tuning only for a
guitar-confirmed track.

---

## R5. Existing tuner

**Evidence** (`desktop/TuxGuitar-tuner/src/app/tuxguitar/app/tools/custom/tuner/`)

- Exposed as a Tools-menu item through `TGTunerPlugin extends TGToolItemPlugin`
  (item key `tuner.tuner`). Opens `TGTunerDialog` (non-modal `UIWindow`).
- **It does use live audio**: `TGTuner extends Thread` reads a Java Sound `TargetDataLine`
  obtained from `AudioSystem.getLine(DataLine.Info)` with the **default device only**
  (`TGTunerSettings.getDataLine`, lines 45–64; `deviceName` field is never used).
- Format: 11 025 Hz, **8-bit**, mono (`TGTunerSettings.getDefaults`, lines 30–40); buffer
  4096 samples (~370 ms), FFT size 16384; sleeps 100 ms per loop; calls `dataLine.flush()`
  after every read (drops audio).
- Detection: **FFT magnitude peak picking** in a frequency window
  (`TGTuner.run`, lines 101–128) + `TGTunerQueue` smoothing that scores half/double frequency
  matches (octave-error mitigation). No confidence, no level metering, no clipping detection.
- Reference pitch: `getNoteFrequency` A=110 Hz at MIDI 45 (i.e. A4 = 440 Hz).
- String guidance: tuning taken from the **caret track** (`TGTunerPlugin.doAction`); string
  buttons `TGTuningString`; fine/rough widgets are custom-drawn with the UI toolkit.
- Settings dialog (`TGTunerSettingsDialog`) exposes sample rate, sample size, buffer size, FFT
  size and noise gate — all specific to the FFT engine; settings are not persisted
  (`loadTuxGuitarSettings` returns defaults).
- FFT code `craigl/spectrumanalyzer/FFT.java`: permissive Poskanzer licence; reusable.
- No chromatic note/octave/cents display, no presets, no custom tunings, no input selection.

**Reusable**: plugin/menu entry and action id, dialog shell, custom-drawn fine/rough widgets
(as display components), string-button concept, FFT class, the A4=440 convention.
**Not reusable**: the detection loop (8-bit, 370 ms buffers, flush-after-read, peak picking,
no confidence), settings model.

**Decision**: satisfy "one tuner experience" (FR-046) **per build** with an **evolved tuner**
that keeps the existing user-facing identity (same Tools-menu item and action id, same plugin
module id — R19) and adds the new engine, chromatic and guided modes, presets, custom tuning,
input selection and level status. Per P9 it is a **new module** (`desktop/TuxGuitar-tuner-next`)
packaged **only in the macOS SWT build instead of** `desktop/TuxGuitar-tuner`, which stays
unchanged (as the legacy compatibility tuner) for the other eight builds. The practice plugin
opens the tuner through its action id, so it has no compile-time dependency on either tuner
module. **Decided (P4, spec FR-059)**: "no regression"
means the preserved learner-facing capabilities (Tools-menu access, single tuner window, live
pitch feedback with Hz readout, fine/rough widgets where meaningful, clickable string targets).
`TGTunerSettingsDialog`'s detector-specific controls (FFT size, buffer size, sample rate/size,
noise gate %, info line) are removed; the dialog offers the shared device/channel selection,
level status and recalibration. Sample rate/size and buffer size become capture-engine details
unless spike S1 shows a learner-facing need; no control name is reused for a new meaning.

---

## R6. Existing training/practice capabilities

**Evidence**

- Speed trainer and loop: `MidiPlayerMode` (`TYPE_SIMPLE`, `TYPE_CUSTOM` with tempo
  from/to/increment, `loop`, `loopSHeader/EHeader`) and the transport mode dialog
  (`action/impl/transport/TGOpenTransportModeDialogAction`, loop header actions).
- Playback highlight: `Tablature.isPlaying(beat)` uses `TGTransport` cache while the player
  runs (`view/component/tab/Tablature.java:245`).
- No existing "wait for input" or MIDI-input practice mode.

**Decision**: nothing to reuse for the practice loop itself; the loop/speed-trainer concepts
stay out of scope. Practice explicitly uses **no** transport loop range (see R7).

---

## R7. Playback order, score position, highlighting

**Evidence**

- Repeat/alternate-ending traversal is centralised in
  `common/TuxGuitar-lib/src/main/java/app/tuxguitar/player/base/MidiRepeatController.java`
  (public; `process()`, `shouldPlay()`, `getIndex()`; optional start/end headers for loops),
  used by `MidiSequenceParser.parse` and `desktop/.../util/MidiTickUtil.java`. It is covered by
  `common/TuxGuitar-lib/src/test/java/app/tuxguitar/player/base/TestRepeatController.java`
  with fixture `altRepeatLoop_20.tg`.
- Starting playback from the caret: `MidiTickUtil.getTick(context, start)` walks the same
  controller and returns the **first** played occurrence of the caret's measure.
- Caret: `view/component/tab/Caret.java` (`moveTo(track, measure, beat, string)`,
  `update(...)`). Action `TGMoveToAction` (`action.caret.move-to`) moves it; configured
  `LOCKABLE` only (`TGActionConfigMap.java:398`) — not a document modifier, not
  thread-synchronised.
- When not playing, the view follows caret changes (`TGControl.java` lines 168–171:
  `if caret.hasChanges() → jumpTo(caret.getMeasure())`).

**Decision**: practice maintains its **own practice-event sequence**, built by walking
`MidiRepeatController(song, -1, -1)` (no loop range) and reading the selected track's measures
in that order. This reuses TuxGuitar's single source of repeat/alternate-ending logic without
touching the playback engine or its timing. The start position is resolved as playback does:
first occurrence of the caret's measure, then the first practice event at or after the caret
beat (FR-004/005/006). The practice highlight is **separate from the caret** (FR-014, decided
P7) — see R18. Rejected: driving the MIDI player (timing-based, would sound audio, conflicts
with FR-065); duplicating repeat logic; using the caret as highlight (the learner must stay free
to move it).

---

## R8. Audio input infrastructure

**Evidence**

- Only live-capture code in the desktop build: the tuner's Java Sound `TargetDataLine`
  (R5). `desktop/gervill` provides output mixers; Android has its own `javax.sound` shims.
- Native modules: output only (R1). No device enumeration or selection exists for input.
- macOS packaging: `desktop/build-scripts/common-resources/common-macosx/Contents/Info.plist`
  has **no `NSMicrophoneUsageDescription`**. The bundle executable is `tuxguitar.sh`, which
  launches `./jre/bin/java` (`Contents/MacOS/tuxguitar.sh`), so TCC attributes microphone
  access to the TuxGuitar bundle.

**Decision**: use **Java Sound (`javax.sound.sampled`)**: `AudioSystem.getMixerInfo()` →
mixers supporting `TargetDataLine` for enumeration; for each mixer, inspect the supported
`AudioFormat`s (channel counts, sample sizes) from `DataLine.Info`. A mono device yields one
logical input; a device offering N-channel PCM yields N **logical inputs** ("<device> — Input
k"). Capture opens the device at its native channel count (preference 48 kHz or 44.1 kHz,
16-bit, else 24-bit converted in Java) and **de-interleaves only the selected channel** into the
mono ring buffer. No mixing, no automatic channel choice, no implicit channel 1 (spec FR-035,
decided P2). No native code for the MVP. Add `NSMicrophoneUsageDescription` to the packaged
`Info.plist` **of the macOS SWT build only**: `common-resources/common-macosx/Contents/Info.plist`
is shared with the macOS JFX build (both POMs copy `../common-resources/common-macosx`, SWT POM
line 368, JFX POM line 381), so the key is inserted by a build step in the SWT POM after that copy
rather than by editing the shared file (P9: other builds unchanged).

**SPIKE S1** (before Phase 3): on macOS 14/15 Apple Silicon, with the packaged `.app` and with
a Terminal launch — (a) does capture work at all with and without the plist key; (b) what does
Java Sound report when permission is denied (exception vs. digital silence); (c) which mixers
and formats appear for the built-in microphone and a USB interface; (d) for common two-input
interfaces (e.g. Scarlett 2i2): the channel counts and formats Java Sound offers, whether a
multi-channel `TargetDataLine` can be opened, whether de-interleaved channel k really
corresponds to physical input k, and what a mono request does. If individual channels cannot be
selected, S1 reports that exact limitation to the product owner (no substitution by
strongest-channel selection or mixing).

---

## R9. Threading model

**Evidence**

- `TGThreadManager` (`common/.../thread/TGThreadManager.java`: `start`, `loop`) with
  `TGMultiThreadHandler` installed on desktop (`TuxGuitar.java:107`).
- UI updates must go through `TGSynchronizer.getInstance(ctx).executeLater(Runnable)`
  (used by `TGTunerDialog.fireFrequency`).
- Actions not flagged sync-thread run on the caller's thread; sync is opt-in via
  `getSyncThreadInterceptor().addActionId` (`TGActionInstaller.java:605`).

**Decision** (details in plan):

1. **Capture thread** (dedicated, started via `TGThreadManager.start` with high priority):
   blocking `TargetDataLine.read` of small blocks into a preallocated lock-free
   single-producer/single-consumer ring buffer. No allocation, logging, locks or UI calls.
2. **Analysis thread**: consumes fixed hops, computes level, pitch estimate and onset
   features with preallocated buffers; publishes immutable `AnalysisFrame`s.
3. **Session thread**: single-threaded practice state machine; receives analysis frames and
   learner commands through one queue; the only writer of session state.
4. **UI thread**: receives coalesced snapshots (latest value, ≤ ~30 Hz) via
   `TGSynchronizer.executeLater`; runs the external-beat highlight calls and all widget updates.
   Learner commands from actions are posted to the session queue.

---

## R10. Pitch-detection approach (deterministic)

**Requirements from spec**: 6-string guitar fundamentals from about 62 Hz (B-tuned low string,
TuxGuitar preset `B Tuning` low note 35) / 73 Hz (Drop D) to about 1.3 kHz (24th fret high E);
note + octave + cents; octave errors must not be accepted (FR-019); confidence for *uncertain*
(FR-018/FR-026); perceived response ≤ 300 ms provisional (SC-002); tuner/practice consistency
(FR-057); works for interface and microphone input; testable deterministically; Java 9; LGPL
compatible.

| Approach | Octave/harmonic robustness | Low-E latency | Confidence measure | CPU | Verdict |
|---|---|---|---|---|---|
| FFT peak (existing tuner) | Poor: picks strongest partial; guitar 2nd/3rd harmonics often dominate low strings | Needs long windows for bin resolution (current ~370 ms) | None | Low | Reject |
| Harmonic product spectrum | Better, still octave-prone; needs fine bins | Long windows | Weak | Low | Reject |
| Plain autocorrelation | Octave-down/up errors common without normalisation | ~2 periods | Weak | Medium | Reject |
| **YIN** (de Cheveigné & Kawahara 2002) | Good: cumulative-mean-normalised difference + absolute threshold targets the fundamental | ~2–3 periods of lowest f0 (≈ 30–45 ms at 62–73 Hz) | Aperiodicity (CMNDF minimum) → natural *uncertain* signal | O(W·τmax) naive; O(N log N) via FFT-based ACF | **Recommended** |
| **McLeod Pitch Method** (McLeod & Wyvill 2005) | Good; "key maximum" selection; used in instrument tuners | Similar to YIN | NSDF clarity | Similar | **Candidate B** |

**Decision (superseded 2026-09-26 — see "S3 results")**: the original plan was to implement YIN
from the paper without third-party DSP. After S3 the product owner chose **TarsosDSP `FastYin`,
2048-sample window** as the provisional MVP detector. TarsosDSP 2.5 is published under
**GPL-3.0** (`<license>GNU GENERAL PUBLIC LICENSE Version 3` in its POM). Constitution v1.1.0
permits GPL-3.0 dependencies (intended GPLv3 combined distribution) and makes distribution
compliance a release gate; provenance is recorded in plan.md.
**SPIKE S3**: benchmark YIN vs McLeod on the synthetic suite and the first recorded set
(interface + built-in microphone); switch to McLeod only if it is measurably better on octave
errors and false acceptance at equal latency. The chosen estimator is the **only** pitch
estimator used by both tuner and practice.

Note interpretation (shared): MIDI note = round(69 + 12·log2(f/440)); cents = 1200·log2(f/f_note)
relative to the detected note's equal-tempered centre (FR-017, FR-049); octave from MIDI number
(E2 = 40).

---

## R11. Input level, calibration, gain control

**Evidence**: no level metering anywhere today; the tuner has only an FFT-magnitude "noise gate"
(`TGTunerSettings.threshold`). A commented-out `Port`/`FloatControl.VOLUME` example exists in
`TGTunerSettings.java` lines 150–171 — never used.

**Decisions**

- Level analysis on raw samples per hop: RMS and peak (dBFS), clipped-sample ratio
  (|x| ≥ ~0.99 FS), and an onset/attack envelope. Classification into *too low / suitable /
  too high / clipping* for the live meter and *suitable / suboptimal / unusable* for the
  assessment (FR-038) uses thresholds that are **parameters**, set from the benchmark (SC-003),
  not hard-coded guesses in the spec.
- Quick level check = assessment over a short window of the learner playing (duration a
  parameter); calibration = the same assessment run explicitly with guidance.
- **Automatic gain adjustment is not planned for the MVP.** Java Sound exposes input gain only
  through optional `Port` controls whose availability on macOS/CoreAudio is unproven.
  **SPIKE S2** checks `AudioSystem.getMixer(...).getLine(Port.Info...)` volume controls on
  macOS; if absent (expected), the UI gives manual guidance only, which FR-041 allows.
- Microphone vs instrument classification: Java Sound gives only mixer name/vendor/description.
  Only the built-in microphone can be recognised (by name, spike S1 confirms names); every
  other input is "unknown" → headphone recommendation shown (FR-036 allows this).

---

## R12. Shared pitch behaviour between tuner and practice

**Decision**: one engine, one interpretation, two consumers — see
[contracts/audio-pitch-engine.md](./contracts/audio-pitch-engine.md). Packages live in the
shared engine module `desktop/TuxGuitar-audio-engine` (R13) and have no UI or TuxGuitar-app
dependencies:

- `…audio.input` — device enumeration, format negotiation, capture thread, ring buffer,
  shared current-input selection (per `TGContext` singleton), failure reporting.
- `…audio.analysis` — level analyzer, YIN estimator, onset detector, `AnalysisFrame`.
- `…audio.pitch` — `NoteInterpreter` (Hz → note/octave/cents), tolerance policy objects with
  the invariant *practice acceptance tolerance ≥ tuner in-tune range* (FR-020) enforced in code
  and tested.

Tuner and practice subscribe to the same analysis stream, so identical input gives identical
note identification (FR-057, SC-005).

---

## R13. Where the code lives (revisited after P9)

**Original evidence and why it no longer holds**: the first plan put the engine inside the
existing tuner module only to avoid packaging a new library in all 9 variants that bundle the
tuner (option 1 below). P9 removes that constraint: the evolved tuner and Interactive Practice
ship only in the macOS SWT build, and the other eight builds keep the untouched legacy tuner, so
a new library needs packaging in one build only. No other repository evidence tied the engine to
the tuner module, so the boundary is reconsidered on its merits.

**Options**

1. **Shared engine library module** used by the evolved tuner and practice.
2. Engine inside the evolved tuner plugin; practice depends on that plugin jar (synth-gervill
   precedent).
3. Engine inside the practice plugin.

**Decision**: option 1 — `desktop/TuxGuitar-audio-engine` (artifact `tuxguitar-audio-engine`),
a plain library jar packaged into `Contents/MacOS/lib` like `tuxguitar-gm-utils` (precedent:
`desktop/build-scripts/tuxguitar-macosx-swt-cocoa/pom.xml` lines 113–117; `lib/*` is on the
launcher classpath, `Contents/MacOS/tuxguitar.sh`, and plugins are loaded by `TGClassLoader`
on top of it). Reasons:

- The module boundary enforces the constitution's separation (VII): its only dependencies are
  the JDK (`javax.sound.sampled`) and `tuxguitar-lib` (`provided`, for `TGContext`/singletons);
  it cannot import UI or app classes.
- Standard Maven layout (`src/main/java`, `src/test/java`, `src/test/resources`) — no
  `<testSourceDirectory>` workaround, and the recorded benchmark sits next to the code it
  measures.
- Practice and the evolved tuner depend on the engine, not on each other (no plugin-to-plugin
  coupling); both see the same per-`TGContext` input selection and analysis stream (FR-039,
  FR-057).
- Nothing is duplicated: the legacy tuner keeps its own FFT loop and is not migrated to the
  engine (P9).

**Modules during the MVP transition**

| Module | Kind | Packaged in |
|---|---|---|
| `desktop/TuxGuitar-audio-engine` (new) | library jar | macOS SWT only (`lib/`) |
| `desktop/TuxGuitar-tuner-next` (new, evolved tuner) | plugin, module id `tuxguitar-tuner` (R19) | macOS SWT only (`share/plugins/`) |
| `desktop/TuxGuitar-practice` (new) | plugin, module id `tuxguitar-practice` | macOS SWT only (`share/plugins/`) |
| `desktop/TuxGuitar-tuner` (existing, unchanged) | legacy compatibility tuner | the other eight builds (unchanged); removed from macOS SWT |

## R14. Keyboard commands

**Evidence**

- Any action id added to `TGActionAdapterManager.getKeyBindingActionIds()` becomes assignable
  in the existing key-binding editor (`view/dialog/keybindings/TGKeyBindingEditor.java:129`) and
  is saved to the user's `shortcuts.xml` (`KeyBindingActionManager.getUserFileName`).
  **Reassignment therefore comes for free.**
- Default bindings come from exactly one resource, `tuxguitar-shortcuts.xml`
  (`KeyBindingActionDefaults.DEFAULT_SHORTCUT_FILE`; file `desktop/TuxGuitar/dist/tuxguitar-shortcuts.xml`).
  New defaults are merged without overriding user bindings (`KeyBindingActionManager.merge`),
  and bindings to actions that are not installed are ignored (`isKeyBindingAvailable`).
  **Plugins cannot contribute default bindings.**
- Key events reach bindings only from controls that call `appendListenersTo`
  (`TGControl.java:117`, `TGFretBoard`, `TGPiano`, `TGMatrixEditor`).
- Existing defaults to avoid: digits set fret numbers; `Space` = `action.transport.play`;
  `F9`, `Ctrl t` (transport).

**Decision**: register `start`, `skip`, `resume`, `stop` (and `open`) as key-bindable,
sync-thread actions; call `appendListenersTo` on the practice window's controls; add default
combinations for them to `desktop/TuxGuitar/dist/tuxguitar-shortcuts.xml` (minimal core data
change, justified above). Exact keys are chosen in Phase 1 after checking the default file for
conflicts. This file is copied into every build (`TuxGuitar/dist/`, SWT POM line 358).

**Q-B verification — builds without `TuxGuitar-practice`** (all in
`desktop/TuxGuitar/src/app/tuxguitar/app/`):

| Concern | Evidence | Result |
|---|---|---|
| Startup/runtime errors | `KeyBindingReader.getBindings` only reads `keys`/`action` strings into `KeyBindingAction` objects; no lookup or validation of action ids. `KeyBindingActionManager.init` merges lists without resolving actions. | No errors |
| Bindings invoking anything | `getActionForKeyBinding` returns an action only if `isKeyBindingAvailable` (id registered in `getKeyBindingActionIds()`); otherwise the loop continues to later matches. `processKeyBinding` only runs a returned id. | Inert; a combination shared with a registered action still reaches that action |
| Shortcut configuration UI | `TGKeyBindingEditor.createKeyBindingActions` builds rows only from registered ids (`getKeyBindingActionIds().getActionIds()`), both for "current" and "load defaults"; its conflict check `exists` uses only those rows. | No practice rows; absent defaults never block assigning a key |
| Menu accelerators | `getKeyBindingForAction(action)` is queried only for existing menu actions and also checks availability. | Unaffected |
| Loading/saving user shortcuts | `saveKeyBindings` is called only from the editor after `reset(list)` with registered actions; `KeyBindingWriter.setBindings` writes that list plus registered ids without bindings. Loading merges defaults only where the user file has no binding for that action or combination (`merge`). | Unchanged; practice ids never written on those builds |
| Collisions | No existing action id or message key uses `practice.` / `action.practice.` (repository search). Combination collisions with existing defaults would matter on the macOS SWT build (first available match wins), so Phase 1 picks combinations absent from `tuxguitar-shortcuts.xml`. | None, subject to the Phase 1 check |

**Observation (pre-existing, not caused by the defaults)**: the user file `shortcuts.xml` lives
in the per-user config directory (`TGUserFileUtils.PATH_USER_CONFIG`), shared by all builds on
one machine. Saving shortcuts in a build where a plugin's actions are not registered rewrites the
file without that plugin's custom bindings — true today for every plugin action. A learner who
customised practice shortcuts on macOS SWT and then saves shortcuts in another build on the same
machine would fall back to the practice defaults. The shared defaults neither cause nor worsen
this; a macOS-SWT-only injection would behave the same.

**Decision (Q-B)**: keep the practice default entries in the shared
`tuxguitar-shortcuts.xml`; no macOS-specific build transformation.

---

## R15. Privacy

**Decision**: audio samples exist only in the preallocated capture ring buffer and analysis
work arrays, overwritten continuously; no class in the engine accepts a file/stream sink; no
logging of sample arrays; benchmark fixtures are read-only test resources. A test asserts no
engine type exposes a write-to-file/record method; code review checklist item in each phase.

---

## R16. Test infrastructure

**Evidence**: JUnit 5 (`junit-jupiter-engine` 5.9.2, test scope) only in `common/TuxGuitar-lib`,
`-compat`, `-midi`, `-editor-utils`. Desktop modules have no tests and use `src` as source dir.
Fixture pattern: `.tg` files in `src/test/resources` loaded with `TGSongReaderImpl`
(`TestFileFormat20.readSong`). Tests run during `mvn verify` of a build-script variant.

**Decisions**

- `desktop/TuxGuitar-audio-engine` uses the standard layout with JUnit 5 (test scope, same
  version as `common/`). The two new plugin modules (`TuxGuitar-tuner-next`,
  `TuxGuitar-practice`) follow the plugin convention `<sourceDirectory>src</sourceDirectory>`
  and therefore put tests in `<testSourceDirectory>test</testSourceDirectory>` (outside `src`).
  The legacy `desktop/TuxGuitar-tuner` is not touched.
- Deterministic audio fixtures are **synthesised in test code** (sine + harmonic series,
  Karplus–Strong plucks, detuning in cents, noise, clipping, silence, repeated plucks) — no
  large binaries in the repo.
- Recorded benchmark (SC-003, decided P5): a compact curated real-guitar set recorded by the
  project owner on the validation setup, CC0-1.0 (own `LICENSE` file), PCM WAV mono 48 kHz
  16-bit read with `javax.sound.sampled.AudioSystem.getAudioInputStream` (no new dependency),
  clips ≈ 1–3 s, hard target ≤ 50 MB, under `desktop/TuxGuitar-audio-engine/src/test/resources/benchmark/`
  laid out as `<input-type>/<tuning>/<clip>.wav` (`interface/`, `builtin-mic/`; `standard/`,
  `drop-d/`) with `LICENSE` and `manifest.csv`. The runner reads only `manifest.csv` (file,
  expected note/pitch, case, tuning, input type, played note/cents where relevant, setup,
  gain/level, notes) — no file names in test code. JUnit-tagged, excluded from the default build,
  run explicitly (`-Dgroups=benchmark -DexcludedGroups=`); results written to
  `specs/001-interactive-practice-mvp/benchmark/`, reported per input type. A build-time check
  fails the benchmark run if the set exceeds 50 MB or a WAV is not mono/48 kHz/16-bit (except
  cases flagged as channel-handling tests in the manifest). Never created from learner audio.
- Practice-event traversal tested against `.tg` fixtures (repeats, alternate endings, voices,
  ties, grace, dead, harmonic, bend) built programmatically with `TGFactory` or stored in the
  practice module's `test-resources`.
- Hardware-dependent behaviour (device loss, permission, real latency) is covered by the manual
  acceptance checklist (SC-009), never claimed as automated.

---

## R17. UI toolkit compatibility

**Evidence**: desktop UI is written against `app.tuxguitar.ui.*` (`UIFactory`, `UIWindow`,
`UILabel`, `UICanvas`/`UIPainter`, layouts) with SWT/JFX/Qt implementations
(`desktop/TuxGuitar-ui-toolkit*`). The tuner widgets already paint through this abstraction.

**Decision**: practice and tuner UI use only `app.tuxguitar.ui.*`; no direct SWT imports. DSP,
input, event-sequence and session packages import no UI types. Validated runtime: macOS Apple
Silicon, SWT Cocoa.

---

## R18. Separate practice highlight (P7) — existing mechanisms and minimal hook

**Mechanisms examined**

| Mechanism | Evidence | Usable for FR-014? |
|---|---|---|
| Editing caret | `view/component/tab/Caret.java`, `TGMoveToAction` | No — the learner must be free to move it (FR-014, FR-003). |
| Playback highlight | `TGControl.paintTablature` paints `TGLayout.paintPlayMode(measure, beat)` for `TGTransport.getCache().getPlayBeat()` only while `MidiPlayer.isRunning()`; beat/measure "playing" state comes from `TGController.isRunning(beat/measure)`, implemented by `Tablature.isRunning` using the transport cache (`Tablature.java:244–250`) | No — bound to the MIDI player; starting the player would play audio and violate FR-065. |
| External beat viewer | `app/editor/TGExternalBeatViewerManager` (`showExternalBeat(beat)`, `hideExternalBeat()`), event `TGExternalBeatViewerEvent` (`ui-external-beat-viewer`), actions `action.gui.show-external-beat` / `action.gui.hide-external-beat`; consumed only by `TGFretBoardEditor` (`setExternalBeat`) and `TGPianoEditor`; **no in-tree caller** | **Partly** — a generic, public "show this beat" API already exists, but the tablature ignores it. |
| Paint listeners / overlays for plugins | none: `TGController` (`common/.../graphics/control/TGController.java`) exposes only resources, styles, track selection, running/loop checks; `TGControl` has no contribution list | No. |

**Conclusion**: no existing mechanism highlights an arbitrary beat in the tablature. The
smallest generic change is to make the **tablature honour the existing external-beat API**, the
same way the fretboard and piano already do. This adds no new API: the practice plugin calls
`TGExternalBeatViewerManager.getInstance(ctx).showExternalBeat(beat)` / `hideExternalBeat()`.

**Minimal hook (desktop core only, `desktop/TuxGuitar`)**

1. `view/component/tab/TablatureEditor.java` — also handle `TGExternalBeatViewerEvent`
   (listener registration like `TGFretBoardEditor`), store the beat on the `Tablature`, request
   a redraw on the UI thread.
2. `view/component/tab/Tablature.java` — hold an optional external beat; `isRunning(beat)` /
   `isRunning(measure)` additionally return true for that beat/its measure **only while the MIDI
   player is not running** (so the existing painting code draws it with the existing
   played-beat style). No change when no external beat is set.
3. `view/component/tab/TGControl.java` — when the player is not running and an external beat is
   set: paint it with `getViewLayout().paintPlayMode(...)` after the full repaint, and scroll to
   its measure once **when the external beat changes** (not continuously, so the learner can
   still browse). Caret handling is untouched.

No change in `common/` (the shared `TGController`/`TGLayout` painting code used by Android, SVG
and print is reused as is). No song change, no document-modified flag (events only). When the
highlighted beat's track is not the one displayed (single-track view showing another track), the
score cannot show it; the practice window still shows the expected event, and the highlight
reappears when the Practice Track is displayed again.

**Side effect of reusing the generic API**: the fretboard and piano views, when open, also show
the highlighted beat (their existing external-beat behaviour). This is instructional only
(FR-027) but is user-visible; recorded as a product point in the plan.

---

## R19. Plugin identity of the evolved tuner (P9)

**Evidence**

- Enable/disable state is stored per module id as `<moduleId>.enabled` in the `plugin-settings`
  resource, default `true` (`common/.../util/plugin/TGPluginProperties.java` lines 9, 28–38).
- Plugin info is read by resource name `META-INF/<moduleId>.info`
  (`desktop/.../system/plugins/TGPluginInfoHandler.java`; `TGPluginInfo.loadPluginInfo`).
- The plugin list dialog groups plugins by module id (`TGPluginListDialog.getModuleIds`).
- Language files: every `lang/messages*.properties` found on the class path is merged with
  `putAll` (`common/.../resource/TGResourceBundle.loadResources`), so two jars providing the
  same keys would silently override each other.
- The tuner's menu action id is its item name `tuner.tuner` (`TGTunerPlugin.getItemName`,
  `TGToolItemPlugin` maps `TGCustomTool(name, name)`); user key bindings are stored per action
  id in `shortcuts.xml`.

**Conclusion — reuse is safe under one condition.** The evolved tuner reuses module id
`tuxguitar-tuner`, info file name `META-INF/tuxguitar-tuner.info`, and action/item id
`tuner.tuner`, so a macOS user's existing enable/disable choice and any key binding for the tuner
carry over. This is safe **only because the two tuner jars are never packaged into the same
build** (P9 per-build rule); otherwise the `.info` lookup would be ambiguous, both plugins would
share one enable flag, and their `tuner.*` language keys would override each other. The build
must enforce the rule: the macOS SWT build-script POM lists and copies only
`tuxguitar-tuner-next`, and a packaging check (Phase 4) fails the build if both jars end up in
`share/plugins`.

To avoid accidental class-name clashes while both modules exist in the repository, the evolved
tuner uses its own Java package (e.g. `app.tuxguitar.app.tools.custom.tunernext`), not the legacy
package `app.tuxguitar.app.tools.custom.tuner`.

**Migration path for other builds** (P9): for each additional build — validate audio capture and
device/channel behaviour on that OS; validate the tuner UI on that toolkit; run the FR-059
regression checks; only then switch that build-script POM from `tuxguitar-tuner` to
`tuxguitar-tuner-next` (+ `tuxguitar-audio-engine` in `lib/`). Support is never claimed before
that. When all intended builds have switched, remove `desktop/TuxGuitar-tuner` in a separate
cleanup change.

---

## Spike results (Phase 0) — S1, S2, S4

**Environment (all spikes)**: MacBook Pro, Apple Silicon (`arm64`), macOS 26.7 (build 25G229),
Temurin 21.0.12.1 (`os.arch=aarch64`). Audio hardware present (`system_profiler
SPAudioDataType`): built-in microphone "Micro MacBook Pro" (1 input channel, 48 kHz, system
default input), Continuity microphone of an iPhone (1 channel), "Microsoft Teams Audio" virtual
device (1 in / 1 out), built-in speakers. **No external audio interface was attached.**
Experiments were standalone Java programs in the session scratch directory (not in the
repository). Only level statistics were printed; no audio was written or kept.

### S1 — Java Sound input on macOS

**Experiments**: (1) enumerate all mixers, their target/source line infos, advertised formats,
`isLineSupported` for 48/44.1 kHz × 16/24-bit × mono/stereo, and port controls; (2) open a
`TargetDataLine` per device and print per-250 ms RMS/peak and the count of exact-zero samples
for 0.5–3 s; (3) repeat opens to time them; (4) request stereo on a mono device; (5) run the
same capture from a throw-away app bundle whose executable is a shell script starting `java`
(same topology as TuxGuitar's `Contents/MacOS/tuxguitar.sh`) with **no**
`NSMicrophoneUsageDescription`, launched with `open`.

**Observed**

- Mixers: for every CoreAudio device there is a `com.sun.media.sound.DirectAudioDevice` mixer
  named after the device (localized name, e.g. "Micro MacBook Pro"), a `PortMixer` named
  "Port <device>", and a "Default Audio Device" mixer that follows the system default input.
  Vendor strings: "Apple Inc.", "Microsoft Corp.".
- Capture formats advertised per input device: PCM signed/unsigned 8-bit, signed 16- and
  24-bit (LE/BE) at unspecified rate, plus 48 kHz/16-bit — **all mono**, i.e. only the device's
  native channel count. `isLineSupported` is true for 48 kHz and 44.1 kHz, 16- and 24-bit mono;
  false for every stereo request on these mono devices.
- Captures (iTerm2 launch context): real signal at 48 kHz/16-bit mono (room noise ≈ −44…−53 dBFS
  RMS, 0.4–0.6 % exact-zero samples); 24-bit capture works (0–1 exact-zero samples per 12 000);
  44.1 kHz on the default device works (the device runs at 48 kHz; Java Sound/CoreAudio
  converts).
- Requesting stereo on a mono device throws `IllegalArgumentException: Line unsupported`
  (`DirectAudioDevice.getLine`); Java Sound does not up-mix or expose virtual channels.
- Open + start: 2.36 s on the first open after a while, then ≈ 104–107 ms on repeated opens.
- Permission: the shell capture ran under iTerm2 (process ancestry `iTerm2 → login → zsh →
  claude → zsh → java`), which already has microphone access, so data flowed. The TCC database
  could not be read (`authorization denied`). The script-launched bundle **without**
  `NSMicrophoneUsageDescription` opened the line without error, no prompt appeared, no crash —
  and delivered **100 % exact-zero samples** (−240 dBFS) for the whole capture.
- **Correction (verified from the unified log, `log show … process == "tccd"`)**: the silence
  was *not* caused by the missing usage description. In both script-launched bundles — without
  the key (15:16:36) and **with** it (`S1ProbeAllow`, 15:27:56) — `tccd` attributed the request
  to the Temurin `java` binary alone (`attribution={requesting={identifier=net.java.openjdk.java,
  binary_path=…/temurin-21.jdk/Contents/Home/bin/java}}`, no `responsible` process) and logged
  `Prompting policy for hardened runtime; service: kTCCServiceMicrophone requires entitlement
  com.apple.security.device.audio-input but it is missing`, result `authValue=1`; no prompt was
  shown and Java Sound received zeros. When the same java ran under iTerm2, `tccd` attributed
  it to `responsible=com.googlecode.iterm2` and allowed it (`authValue=2`).
- Temurin's `java` is Developer-ID signed (Eclipse Foundation, `JCDTMS22B4`) with the hardened
  runtime flag and entitlements `allow-jit`, `allow-unsigned-executable-memory`,
  `allow-dyld-environment-variables`, `debugger`, `disable-library-validation` — **no
  `com.apple.security.device.audio-input`** (`codesign -d --entitlements -`).
- The development app's `Contents/MacOS/jre` links to that same JDK, and TuxGuitar's bundle
  executable is the shell script `tuxguitar.sh`, i.e. the same topology as the probes. By
  inference (not run with TuxGuitar itself), a Finder/`open`-launched TuxGuitar currently cannot
  obtain microphone input on this setup — adding `NSMicrophoneUsageDescription` alone does not
  change that.
- No crash occurred: `S1ProbeAllow` ran its full 20 s and `java` exited 0; no diagnostic report
  exists for it.

**Confirmed**: 48 kHz/16-bit mono capture works on the built-in and Continuity microphones;
device names and default device are visible; in TuxGuitar's current macOS launch topology
(shell-script bundle executable → Developer-ID, hardened-runtime `java` without the audio-input
entitlement) macOS denies the microphone silently — no prompt, no error, zeros — with or without
`NSMicrophoneUsageDescription`; a denial is detectable only indirectly, but exact digital
silence is clearly distinguishable from a real microphone's noise floor.

**Not verified (hardware/interaction unavailable)**:
- Multi-channel interfaces: how Java Sound advertises a 2+ input interface, whether a
  multi-channel line can be opened, and whether channel k maps to physical input k. **The P2
  logical-channel design remains unverified** (see the plan's hardware follow-up).
- A **native stub that starts the existing shell script** (the smallest production-like change)
  was not tested; only a native launcher that starts `java` directly was.
- The behaviour of the real TuxGuitar `.app` (inferred to match the probe bundle, not run).

**P2 validation status**: mono capture on the tested macOS devices is verified; multi-channel
de-interleaving and channel k → physical input k mapping are **not verified** (no multi-input
interface attached). An Aggregate Device (e.g. built-in + iPhone microphones) may be used as an
intermediate technical test of multi-channel lines and de-interleaving, but it is **not** proof
that a USB interface maps Java Sound channel k to physical input k; final validation requires a
real multi-input interface. P2 is not reopened; if that validation fails, the exact limitation
returns to the product owner (no silent channel-1, mixing or strongest-channel fallback).

**Impact on the plan**: keep the SWT-only `NSMicrophoneUsageDescription` step; the engine must
query each device's advertised formats and open at the native channel count (mono devices:
never request stereo); prefer 48 kHz/16-bit, accept 24-bit; open the line off the UI thread and
show a "starting input" state (cold open measured at 2.4 s); report "no signal received — check
microphone access in System Settings and the input" when a window is entirely exact zeros
(wording must not claim denial as a fact, since some devices may output digital silence).

### S2 — input gain controls

**Experiments**: list controls on open `TargetDataLine`s and on every `Port`; on the built-in
microphone port, set `FloatControl.Type.VOLUME` to 0.1, 0.5, 1.0 and back to the original
(0.989369), measuring 1.5 s RMS after each change; the original value was restored in a
`finally` block, and the macOS input volume read 99 before and after (`osascript`).

**Observed**: capture lines expose **no controls** (`MASTER_GAIN`/`VOLUME` unsupported). Input
ports: built-in microphone — `Volume` [0,1] and `Mute`; iPhone microphone — no controls; Teams
virtual device — `Volume`/`Mute`. Changing the built-in microphone port volume changed the
captured level: ≈ −47 dBFS (original) → −69.6 (0.1) → −58.5 (0.5) → −48.0 (1.0) → −43.8
(restored; room noise varies by a few dB). This control is the **system-wide macOS input
volume** for that device (same value as `input volume of (get volume settings)`).

**Confirmed**: gain is controllable through Java Sound only for some devices (built-in mic
yes, iPhone mic no), only as the global macOS input volume (affects other apps and persists
after TuxGuitar exits), never on the capture line itself. Audio interfaces: not tested (none
available); many have hardware-only gain.

**Impact on the plan**: as planned, the MVP provides measurement plus manual guidance (macOS
Sound settings / interface gain knob) and **no automatic gain adjustment**. FR-041 ("MAY offer
automatic adjustment") is not exercised; changing the global input volume would be a separate
product decision.

### S4 — repeated-identical-note articulation

**Experiment**: deterministic synthetic signals at 48 kHz (Karplus–Strong strings with fixed
seeds; harmonic tones with vibrato/amplitude modulation), analysed with 1024-sample Hann
windows, 256-sample hop (5.3 ms). Cases: single sustained pluck; same-pitch re-pluck (equal and
weak ≈ 0.35 gain, E3 and E2); five repeats 150 ms apart; vibrato ±30 cents/5.5 Hz with ±1.5 dB AM;
wide vibrato ±50 cents/6 Hz with ±3 dB AM; beating ±4 dB at 2 Hz; −40 dBFS noise floor; 15 ms
noise click at −18 dB; 10 ms soft click at −30 dB; 25 ms −12 dB dropout; weak re-pluck followed by
a click. Detectors: energy rise vs 32 ms minimum (3/6 dB); envelope slope (≥ 4 dB per ≈ 11 ms);
log-magnitude spectral flux with adaptive median threshold; pitch-clarity drop and
reacquisition (normalised autocorrelation); and flux/slope gated by "mean level 21–70 ms after
the transient minus the **maximum** level 11–100 ms before it ≥ X dB". Correct = within ±30 ms.
No real guitar recording was used (none was provided; user files were not searched).

**Observed** (hits / 18 expected onsets, false detections):

| Detector | Hits | False | Notes |
|---|---|---|---|
| Pitch-clarity reacquisition | 5 | 2 | Periodicity stays continuous through a same-pitch re-pluck; only fast staccato repeats detected — **unusable** |
| Energy rise 3 dB | 16 | 19 | 16 false detections on wide vibrato with ±3 dB AM |
| Envelope slope 4 dB/11 ms | 16 | 3 | Robust to vibrato/beating; misses weak re-pluck; fires on clicks and dropout recovery |
| Spectral flux | 18 | 4 | Catches every re-pluck incl. the weak one; fires on loud and soft clicks and dropout recovery |
| Flux + gate ≥ 0 dB | 15 | 2 | Dropout rejected; loud click still passes |
| Flux + gate ≥ +1.5 dB | 13 | **0** | No false detections in any case; misses the weak re-pluck and 3 of 5 fast repeats |
| Slope + gate ≥ 0 dB | 14 | 1 | — |

**Confirmed**: same-pitch re-articulation needs a transient/energy detector, not pitch
continuity. A transient (spectral flux) gated by a post-vs-pre-maximum level rise can reject
clicks, dropouts, vibrato and amplitude modulation on synthetic signals, at the cost of missing
weak re-plucks. No tested deterministic detector achieved both zero misses and zero false
detections: a pick attack and a broadband click are both transients, and a weak re-pluck that
does not raise the level looks like a click.

**Uncertain**: behaviour on real guitars (pick noise, fret buzz, body knocks, room reflections,
microphone vs interface), exact thresholds, and hop/window choices — to be measured with the
`*_repeat` recordings of the benchmark.

**Impact on the plan**: the engine emits an **articulation event** = spectral-flux transient
AND post/pre-maximum level gate ≥ X dB. X and the flux threshold are tunable parameters, chosen
only after testing on the real `*_repeat` benchmark recordings; the +1.5 dB value is a
synthetic-test result, not a product constant. The event is independently testable with the
synthetic cases above. Spec FR-022 was revised accordingly (evidence of a new articulation;
bias toward false negatives; documented limitation). Session rule (unchanged
intent): a same-pitch expected note may be accepted only after an articulation event later
than the previous acceptance. The detector is biased toward **misses** (the learner simply plays
again — a safe failure under wait-for-correct-note) rather than false articulations (which would
let one sustained note validate two notes, FR-022). A residual risk remains that a strong
non-pluck transient raising the level (e.g. knocking the guitar body) is counted as an
articulation; this is a limitation of mono pitch-and-level input, like other pitched sounds.

### S1 follow-up — microphone permission with a native launcher (manual run, 2026-09-26)

**Experiment**: three throwaway bundles in the session scratch directory, each with
`NSMicrophoneUsageDescription`, ad-hoc signed, whose `CFBundleExecutable` is a ~30-line native
C launcher (`posix_spawn` of the same `java … S1Capture "Micro MacBook Pro" 48000 16 1 20`
command as before, logging levels only): `S1NativeProbeAllow`
(`app.tuxguitar.spike.s1probe.native.allow`), `S1NativeProbeDeny` (`…native.deny`, where Allow
was clicked by accident) and `S1NativeProbeDeny2` (`…native.deny2`). Launched with `open` by the
product owner, who reported the dialogs; results cross-checked with the probe logs and `tccd`
unified-log entries (saved as `perm2/tccd-15h33-15h41.txt` in scratch).

**Attribution**: every microphone request was attributed with
`responsible=app.tuxguitar.spike.s1probe.native.<variant>` and `requesting=net.java.openjdk.java`.
The bundle — not the hardened-runtime `java` — is what macOS evaluated, so the missing
`audio-input` entitlement on Temurin's `java` no longer blocked the prompt.

| Case | Dialog | `tccd` result | Java Sound behaviour |
|---|---|---|---|
| First run, Allow (`native.allow`, 15:33:47) | Yes (bundle name + usage text) | preflight `authValue=1` (undecided) → prompt → `authValue=2, authReason=2` | `TargetDataLine.open/start` blocked until answered (2 287 ms ≈ 15:33:47.79→15:33:50.12), then real signal: ≈ −39…−55 dBFS RMS, 0.4 % exact-zero samples |
| Remembered Allow (`native.allow` 15:36:32; `native.deny` 15:37:30, 15:38:11) | No | preflight `authValue=2` immediately | open in 60–84 ms, real signal (0.3–0.4 % zeros) |
| First run, Deny (`native.deny2`, 15:40:16) | Yes | preflight `1` → prompt → `authValue=0, authReason=2` | open/start succeeded after the answer (2 582 ms), **no exception**, 100 % exact-zero samples (−240 dBFS) |
| Remembered Deny (`native.deny2`, 15:41:02) | No | preflight `authValue=0` immediately | open in 66 ms, no exception, 100 % exact-zero samples |

(`kTCCServiceAudioCapture` was also preflighted and stayed `authValue=1`; microphone capture did
not depend on it.)

**Confirmed**:
1. macOS shows the permission dialog only when the **app bundle is the responsible process**;
   with a shell-script `CFBundleExecutable` (TuxGuitar's current `tuxguitar.sh`) the request is
   attributed to the Developer-ID, hardened-runtime `java` without the audio-input entitlement
   and is silently refused, with or without `NSMicrophoneUsageDescription`.
2. A denial (first-run or remembered) never raises a Java Sound error: the line opens and
   delivers exact digital silence. Detection must therefore rely on the "entire window of exact
   zeros" signal (a real microphone showed 0.3–0.6 % zero samples).
3. Decisions are remembered per bundle identity; relaunches do not prompt.
4. On first use, opening the line blocks while the dialog is displayed — the engine must never
   open the line on the UI thread (already planned) and should show a "waiting for microphone
   permission / starting input" state.

**Uncertain / not tested (at that point)**: a native stub that starts the existing
`tuxguitar.sh` — tested afterwards, see "Q-L option A probe" below; behaviour when the launcher is signed with the
hardened runtime (e.g. for notarization) — Apple's rule, as logged by `tccd`, then requires the
`com.apple.security.device.audio-input` entitlement on the responsible binary; whether an
ad-hoc re-signed rebuild keeps an earlier grant (TCC stores a code requirement per client);
the real TuxGuitar `.app` itself.

**Packaging requirement for the real macOS SWT app** (derived from the above):
- The `.app`'s `CFBundleExecutable` must be a **native Mach-O launcher**, so that the TuxGuitar
  bundle (`app.tuxguitar`) is the process macOS evaluates; the JVM started from it inherits that
  responsibility.
- The packaged `Info.plist` must contain `NSMicrophoneUsageDescription` (SWT build only, P9).
- If that launcher is ever signed with the hardened runtime, it must carry the
  `com.apple.security.device.audio-input` entitlement.
- The launcher must reproduce what `tuxguitar.sh` does today (classpath, library path,
  `-XstartOnFirstThread`, `tuxguitar.home.path`/`share.path`, theme property, arguments).

### Q-L option A probe — native stub starting the existing `tuxguitar.sh` (manual run, 2026-09-26)

**Experiment**: throwaway `S1StubProbe.app` (bundle id `app.tuxguitar.spike.s1probe.stub.a`, fresh
permission state, `NSMicrophoneUsageDescription`), laid out like the development SWT `.app`:
unsigned bundle; `Contents/MacOS/jre` → Temurin 21 JDK; `lib/*.jar`; `CFBundleExecutable` =
`tuxguitar-launcher`, a native arm64 stub (clang, carrying only the linker's automatic ad-hoc
signature) whose only job is `posix_spawn("/bin/sh", "<bundle>/Contents/MacOS/tuxguitar.sh")`
and wait; `tuxguitar.sh` is a copy of the real
`common-resources/common-macosx/Contents/MacOS/tuxguitar.sh` with only `MAINCLASS` and the
program arguments changed. Process chain: `launchd → tuxguitar-launcher → /bin/sh tuxguitar.sh
→ ./jre/bin/java … S1Capture`. Launched twice with `open`; the product owner clicked Allow on the
first prompt. Evidence: `perm3/s1-stub-a.log` and `tccd` entries (`perm3/tccd-15h48.txt`).

**Observed**

| Launch | Dialog | `tccd` (all entries) | Java Sound |
|---|---|---|---|
| 1st, 15:48:03 | Yes → Allow | `responsible=tuxguitar-launcher (responsible_path=…/S1StubProbe.app/Contents/MacOS/tuxguitar-launcher)`, `requesting=net.java.openjdk.java`; preflight `authValue=1` → prompt → `authValue=2, authReason=2` at 15:48:08.019 | open/start 4 093 ms (blocked while the dialog was shown), then real signal: ≈ −45…−53 dBFS RMS, desk taps −19 dBFS RMS / −6.8 dBFS peak, 0.2 % exact zeros |
| 2nd, 15:48:50 | No | same attribution; preflight `authValue=2` immediately | open 64 ms, real signal (≈ −39…−50 dBFS, 0.2 % exact zeros) |

No "requires entitlement … audio-input" message appeared for this probe.

**Confirmed**: responsibility survives the shell hop — a native `CFBundleExecutable` that only
starts `tuxguitar.sh` makes the app's launcher the responsible process, the prompt appears, Allow
yields real samples, and the decision is remembered. **Q-L option A is chosen.**

**Qualification — signing identity**: with an unsigned bundle, `tccd` identifies the responsible
client by the launcher's code-signing identifier (`tuxguitar-launcher`, the default the linker
gives an ad-hoc signed binary), not by the bundle id; the fully bundle-signed native probes were
identified by their bundle ids. For the real app the launcher should therefore be signed
(at least ad-hoc) with an identifier equal to `CFBundleIdentifier` (`app.tuxguitar`) so the
grant is tied to TuxGuitar rather than to a generic executable name. Not yet verified: that
identifier-signing of the launcher alone (the bundle cannot currently be sealed because of jars
in `Contents/MacOS`) makes `tccd` use `app.tuxguitar`, and whether a rebuilt launcher (new cdhash)
keeps an existing grant. Both are checked when the production launcher is built (Phase 3).

### S3 — pitch detection on real recordings + live validation (2026-09-26)

**Experiment**: TarsosDSP 2.5 (`be.tarsos.dsp:core:2.5`, from `mvn.0110.be/releases`, not Maven
Central) `Yin` and `McLeodPitchMethod` fed identical PCM frames of the 22 accepted builtin-mic
benchmark clips (mono 48 kHz/16-bit), windows 2048 (42.7 ms) and 4096 (85.3 ms), hop 256
(5.3 ms). Per clip: first stable note (same note for 10 consecutive frames ≈ 53 ms after the
energy onset), median f0 and cents, median probability, time to stable, and in the sustain window
the share of frames on the recorded note / an octave off / another note / unpitched. Clips 18–21
(requested wrong semitones) are excluded from the counts: their played pitch is unverified (the
owner may have played the expected notes; a pitch-analysis reading of the same clips is not
independent ground truth). (A first run with a 3-frame criterion was
discarded: pluck transients produce ~16 ms of subharmonic readings, e.g. G2 for D4.)

| Detector / window | First stable note correct (17 clips) | Time to stable after onset | Silence clip |
|---|---|---|---|
| **YIN 2048** | **14/17**; 11 clips ≥ 94 % of frames on the note | median 101 ms, max 149 ms | 16/555 frames pitched (F1–A1, below guitar range) |
| YIN 4096 | 14/17 | median 133 ms, max 181 ms | 8/547 frames pitched |
| MPM 2048 | 10/17; more unpitched frames and early subharmonic/octave readings | median 107 ms | 0 pitched frames |
| MPM 4096 | 8/17 | median 80 ms | 0 pitched frames |

YIN 2048 details: open E2/A2/D3/G3/B3 correct in 97–100 % of frames (cents +1…+11); fretted
A2/E3/D4/E4/B4/A5/B5 and the D4 repeat correct (A5/B5 lose confidence while decaying, not
misidentified); F#4 94 % correct but first stable reading an octave low; G3 on string 4 fret 5
only 27 % correct with 50 % octave-off frames; failures 07 (open E4) and 15 (E5) coincide with
other strings ringing −4 / −1 dB below the played note (recording quality). The D4 repeat stays on
D4 across both plucks. Fretted notes read consistently +10…+25 cents sharp versus open strings
(playing/intonation, not the detector).

`FastYin` (same YIN, FFT-based) matched `Yin` on 1 758/1 760 frames (±0.5 Hz) at 0.046 ms per
2048 frame versus 1.03 ms.

**Live validation**: a throwaway command-line tuner (`LiveTuner`, scratch) captured "Micro
MacBook Pro" via Java Sound (48 kHz, 16-bit, native channel count), ran `FastYin` on a 2048
window every 256 samples and displayed note/octave/Hz/cents/probability/level about 8×/s, with a
proof-of-concept display gate (pitched, probability ≥ 0.90, same note for 5 frames ≈ 27 ms,
otherwise "-- | uncertain"). **The product owner tested it with their guitar through the built-in
microphone and reported that notes appear quickly and correctly enough for the intended MVP use**
— recorded as positive manual validation of the basic real-time approach (qualitative, one
guitar, one input; no per-note numbers were recorded).

**Decisions**:
- Provisional MVP pitch detector: **TarsosDSP `FastYin`, 2048-sample window** (hop 256 used in
  the experiments), wrapped behind the engine's own estimator interface so it can be replaced.
- Confidence gate, stability window, acceptance tolerance and level gates stay **provisional
  parameters** until integration testing; the proof-of-concept values (0.90, 5 frames) are not
  product constants.
- Articulation/re-pluck detection remains a **separate component** (S4 model), not part of the
  pitch detector.
- The 48-clip manual benchmark need not be completed before implementation; the 22 accepted clips
  are kept as real-audio regression fixtures (copy into the repository approved by the product
  owner, 2026-09-26): clean, carefully recorded reference fixtures that complement the synthetic
  tests and do not represent all guitars, microphones, rooms or playing styles.

**Still to validate / improve**: direct USB/interface input and multi-channel (P2) input; octave-
error mitigation (G3/F#4 cases, pluck-transient subharmonics); sympathetic/other-string
interference (clips 07/15); low-frequency false readings in silence (YIN reported F1–A1 at low
level — guitar-range limiting and level/confidence gating to be tuned).

**T023 committed-fixture regression follow-up**: the accepted WAVs were copied byte-for-byte and
their SHA-256 values recorded in the manifest. The default-build regression uses test-only,
provisional parameters: confidence gate 0.80, minimum usable window level −35 dBFS, 20 consecutive
equal-note frames for stability (about 107 ms at hop 256), energy-onset floor −45 dBFS, at most 20
octave-off usable frames in a clip, no octave-off run reaching the 20-frame stability window, and
stable-detection latency at most 750 ms. These are evidence-backed regression parameters, not
product constants; the live proof-of-concept's 0.90 / 5-frame values remain non-normative.

With those parameters, every manifest `reference` fixture has the expected dominant note and first
stable note. The maximum observed stable-detection latency is 288 ms (F#4); the longest octave-off
run is 17 frames (also F#4), so no wrong octave becomes stable. The G3 string-4 fixture has zero
usable octave-off frames, and the open G3 fixture has one isolated octave-off frame. The silence
fixture yields no usable pitch. This resolves the G3/F#4 acceptance issue through the intended
level and stability gates rather than by relabelling ground truth or rewriting detector output.
Clips 07/15 remain informational recording-quality cases because another string dominates their
recorded signal; clips 18–21 remain informational because their played note has no independent
ground truth.

### Java release of the new modules (found during /speckit-tasks, 2026-09-26)

**Finding**: all 175 class files in `be.tarsos.dsp:core:2.5` (`core-2.5.jar`, including
`module-info.class`, which requires only `java.base`) have class-file major version 55 (Java 11).
The desktop parent POM (`desktop/pom.xml`) configures `maven-compiler-plugin` with
`<release>9</release>`. Checked with Temurin 21 `javac`: a class referencing `FastYin` compiles at
both `--release 9` (output major 53) and `--release 11` (exit 0 in both cases) — javac reads
dependency class files up to its own version. So release 9 is **not** a compile error with the
validated JDK; the real constraint is at run time: TarsosDSP, and therefore the engine and both
plugins, need a Java 11+ runtime.

**Decision**: `TuxGuitar-audio-engine`, `TuxGuitar-tuner-next` and `TuxGuitar-practice` compile at
`<release>11</release>`, so their bytecode states the Java 11 minimum they actually have (instead
of Java 9 bytecode that would still fail on Java 9/10 when TarsosDSP loads), and they may use
Java 11 APIs. The override lives in each new module's own `maven-compiler-plugin` configuration,
which Maven merges over the parent's per module, so existing modules keep release 9
(`desktop/pom.xml`, `common/TuxGuitar-lib/pom.xml`, `desktop/gervill/pom.xml` are unchanged).
`common/` is not touched, so Android is unaffected.

**Dependency direction** (must hold): new plugin modules → `tuxguitar-audio-engine` → existing
`tuxguitar-lib`; the core tablature hook uses only the existing `TGExternalBeatViewerManager` API.
No existing (release-9) module may declare a dependency on a new module.

**Runtime**: the macOS SWT app runs `Contents/MacOS/jre/bin/java` with a flat classpath
(`lib/*`, `share/`, `dist/`; `common-resources/common-macosx/Contents/MacOS/tuxguitar.sh`), so
`module-info.class` is ignored and only the class-file version matters. The validated runtime is
Temurin 21 (development `jre` link) and CI builds with Java 21
(`.github/workflows/macos-maven.yml`); release packaging jlinks a JRE from a current Homebrew
OpenJDK (`misc/build_tuxguitar_from_source.sh`). Java 11 bytecode therefore raises no runtime
requirement beyond what is already used.

**Alternatives rejected**: keeping release 9 on the new modules (compiles with JDK 21, but
misstates the runtime minimum); raising the parent POM to release 11 (changes every existing
desktop module); recompiling or shading TarsosDSP for Java 9 (diverges from the validated
published artifact and complicates provenance).

### T026 — packaged macOS SWT app, microphone permission (2026-09-26)

Automated checks on the `mvn -e clean verify -P native-modules` build: packaged `Info.plist` has
`CFBundleIdentifier` `app.tuxguitar`, `CFBundleExecutable` `tuxguitar-launcher` and
`NSMicrophoneUsageDescription`; the stub is an arm64 Mach-O, executable, ad-hoc signed with
identifier `app.tuxguitar`; only the legacy tuner is packaged.

Manual check by the product owner (app launched via `open`, microphone used through the legacy
tuner, the only packaged component that captures audio): **microphone access works and notes are
detected**. Not verified in this session: the `tccd` responsible-client attribution (the agent's
sandbox cannot run `log show`) and whether the grant persists across a rebuild; no privacy
setting was changed or reset.

