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
  coding assistant → zsh → java`), which already has microphone access, so data flowed. The TCC database
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


### R14 correction — macOS default shortcuts (T028, 2026-09-26)

R14 stated that default bindings come from exactly one resource. That holds per build, but the
macOS builds (SWT and JFX) copy `desktop/build-scripts/common-resources/common-macosx/Contents/MacOS/dist/tuxguitar-shortcuts.xml`
over the shared `desktop/TuxGuitar/dist/tuxguitar-shortcuts.xml` when packaging; the macOS copy uses
`Command` where the shared file uses `Ctrl`. The practice defaults are therefore added to both files
(`F2`; `Ctrl Shift` / `Command Shift` + `s` `k` `r` `x`). Like the shared entries, the macOS entries are
inert where the practice actions are not registered (macOS JFX); T081 covers that check.

### T076 — provisional parameters from the committed real-guitar clips (2026-09-27)

Run: `mvn test -pl ../../TuxGuitar-audio-engine -am -Dgroups=benchmark -DexcludedGroups=
-Dsurefire.failIfNoSpecifiedTests=false` (macOS SWT build directory). Reports in
`specs/001-interactive-practice-mvp/benchmark/`: `builtin-mic.csv` (per clip), `builtin-mic-sweep.csv`
(pitch parameters), `builtin-mic-articulation.csv` and `builtin-mic-articulation-sweep.csv`
(articulation), `builtin-mic-level-check.csv` (level check), `builtin-mic-cents.csv` (intonation).
Evidence covers one guitar, the built-in microphone and a quiet room only (15 reference clips);
interface input is still to be measured (T078).

- **Confidence gate × stability window × level floor** (27 combinations): the gate (0.70 / 0.80 /
  0.90) does not change correctness, only latency; a level floor (none / −40 / −35 dBFS) has no
  effect. Stability 10 hops (≈ 53 ms) makes one first stable note an octave off; 20 hops (≈ 107 ms)
  and 30 hops give 15/15 correct first stable notes. At gate 0.80 / 20 hops: median latency 171 ms,
  max 288 ms (F#4); 30 hops adds ≈ 55 ms. The silence clip never produces a stable note.
  **Kept: confidence 0.80, stability 20 hops.**
- **Intonation**: fretted notes read sharp (median +10.6…+24.8 cents, p90 up to +30.5 cents on A5);
  open strings within ±12 cents. With ±30 cents part of the A5 frames would break the stability run.
  **Practice acceptance widened from ±30 to ±35 cents** (still ≥ the tuner's ±5 cents in-tune range
  and well below the ±50 cents neighbouring-semitone boundary, FR-020). **Tuner in-tune: ±5 cents
  kept.**
- **Articulation** (17 clips with a known onset count, 18 expected onsets): flux factor 1.5 / 2 / 3
  makes no difference; the level gate does: +1.5 dB (S4 value) reports 2 false articulations (A5
  decay), +3 dB one, +4.5 dB and +6 dB none; every setting reports one onset fewer than expected, on
  the D4 repeat clip (see T077: that recording contains a single audible pluck). **Gate raised from
  +1.5 to +4.5 dB**; the synthetic S4 tests still pass.
- **Level check**: every guitar clip concludes SUITABLE; the silence clip does not conclude within
  its 3 s (the check waits for playing, 10 s timeout). **Level thresholds kept** (−40 / −6 dBFS,
  0.99, 10 %).

All values stay provisional and configurable until acceptance.

### T077 — octave errors, other strings, low readings in silence (2026-09-27)

- **Octave errors (G3 on string 4, F#4)**: with the 20-hop stability window no first stable note is
  an octave off on any reference clip; F#4 still has a 17-frame octave-off run, below the 20-frame
  window, so it never becomes stable (a 10-hop window would, see T076). Covered by
  `RealGuitarRegressionTest` (octave-off runs must stay shorter than the stability window). No
  detector change.
- **Pluck-transient subharmonics**: the ≈ 16 ms of subharmonic readings at the attack (S3) are
  absorbed by the same stability window. No change.
- **Other strings ringing (clips 07 E4, 15 E5)**: the dominant readings are A2 and F#2, i.e. another
  string louder than the played one in the recording. The detector reports the strongest
  periodicity correctly; wait-for-correct-note then simply does not accept, a safe false negative.
  Recording-quality cases, kept as `known-issue` fixtures; no change.
- **Clip 22 (D4 "repeat")**: the level envelope shows a single attack at 0.65 s followed by a
  smooth decay to the end of the 4 s clip, with no level rise anywhere. The recording therefore
  contains one audible pluck, not the two planned by the recording matrix, so it gives no evidence
  about real same-pitch re-plucks. Accepted correction (2026-09-27): its manifest `expected_onsets`
  is now 1, with a note; the WAV is unchanged. With it, the +4.5 dB gate reports every expected
  onset and no extra one on the 17 clips (`builtin-mic-articulation-sweep.csv` regenerated). There
  is still no real-guitar evidence for FR-022 re-articulation (synthetic S4 tests only); a real
  same-pitch re-pluck clip should be recorded later.
- **Low readings in silence**: at confidence 0.80 the silence clip gives 16 confident frames out of
  555, all F#1–A1 (MIDI 30–33), below the lowest open string of TuxGuitar's built-in 6-string
  tunings (A#1). They never form a stable note, but practice showed them as "incorrect". Fixed per
  FR-026: a non-matching reading at a too-low level is now *uncertain* (FeedbackTest). The tuner
  already shows "input level too low" for such frames (FR-056). Limiting pitch detection below A#1
  would remove these readings entirely but would also make notes below A#1 undetectable in the
  "unusual setup" tunings that FR-008 allows; left as a product decision.


## R20. Scope expansion 2026-09-27 — what exists, what does not

Inspected on `b7639d4d2`:

- **Single notes are judged and advance.** `PracticeSession.onFrame` accepts an `EXPECTED_NOTE`
  after reliable, matching (±35 cents), stable (20 hops) readings, plus an onset for a same-pitch
  note, then advances; `PracticeWindow.startSession` subscribes the engine frames to the session
  loop; `PracticeSessionTest` covers it. A track whose first practice event is a chord stops
  there, because `MULTI_NOTE` waits for Skip by design (FR-012) — a likely reason for practice
  appearing not to advance on a chord-heavy song.
- **Chords are not recognised.** The engine gives one fundamental per frame (FastYin →
  `PitchReading`); `MULTI_NOTE` events carry no pitch data (`PracticeEvent.NO_PITCH`).
- **The practice highlight is beat-level.** It reuses the played-beat style: every note of the
  beat in the play colour and, with "highlight played beat" on, one rounded rectangle spanning the
  measure's height (`TGBeatImpl.paint`). No existing API frames single notes, per staff, or in
  another colour (plan "Core change for stage B").

## R21. Chord detection — method choice (plan stage D/E)

Constraints: deterministic and explainable first (Constitution VIII), no ML in scope, TarsosDSP
only inside the engine, Java 11. TarsosDSP 2.5 has no polyphonic pitch estimator (its YIN/MPM
family is monophonic), so the chord analysis is written in the engine.

- **Chosen direction:** per-semitone spectral band magnitudes plus harmonic-sum salience over a
  longer FFT window, and *score-conditioned* matching in practice (is each expected pitch there,
  is anything else strong?) rather than unconditioned transcription. Conditioning on the known
  chord is far more reliable than open-ended multi-pitch estimation.
- **Frequency resolution:** adjacent semitones near E2 are ≈ 4.9 Hz apart. A Hann window of 8192
  samples at 48 kHz (≈ 171 ms) has a main lobe of ≈ 11.7 Hz, so low fundamentals are separated
  mainly through their harmonics; a 16384 window (≈ 341 ms) resolves them directly but smears a
  strum and adds latency. Provisional 8192, to be measured on real chords (SC-010).
- **Intrinsic limits (Constitution XIII):** identical pitches on two strings are one pitch
  (FR-078). A pitch equal to a harmonic of a lower chord pitch (octave, octave + fifth, two
  octaves) is always "present" when the lower note rings; whether it counts as verified is open
  question Q1 (proposed: report it as not separately verifiable). Built-in microphones attenuate
  low fundamentals, which harmonic-sum salience partly compensates.
- **Evidence:** none yet from real chords; synthetic chords (sums of Karplus–Strong plucks with
  fixed seeds and strum offsets) for unit tests; owner-recorded strummed chords required before
  any accuracy claim (SC-010, tasks T094–T095).

### T091 — polyphonic spectrum on synthetic strums (2026-09-27)

Implemented in the engine (`PolyphonicAnalyzer`, `HarmonicComb`, `ChordEvidence`, `SpectrumFrame`,
own radix-2 FFT; no new `be.tarsos.*` import), opt-in per `AudioEngine.setSpectrumEnabled`, and
pinned by `PolyphonicAnalyzerTest` / `AnalysisServiceTest`. Findings on Karplus–Strong strums
(fixed seeds, 10 ms between strings, window ending 250 ms after the first string). Synthetic
strums are brighter and noisier than a real guitar, so these are method observations, not
accuracy figures:

- **Method iterations.** A fixed 10-harmonic sum put a lone E2's 6th harmonic (B4) first: high
  candidates collect a full comb from a low note. Summing every partial to 5 kHz (Klapuri) fixed
  E2 but not A2 (its 5th harmonic won); a subharmonic preference made every pick drop an octave
  until it required evidence from the subharmonic's *own* partials (not multiples of k). Wide
  tolerance windows high up (±35 cents ≈ ±40 Hz at 2 kHz) caught neighbouring partials of other
  pitches, flattening salience; 12 partials within ±25 cents, on a spectrum keeping only content
  above twice the local mean, gives: **every single pluck tested (E2, A2, D3, G3, B3, E4, E5) is the
  first candidate.**
- **Open-ended chord transcription stays unreliable** (typical outcome: 2–4 of 5–6 chord tones
  among the first candidates, plus harmonics of chord tones). This confirms that practice must ask
  score-conditioned questions (`ChordEvidence`) rather than compare transcriptions.
- **Missing string.** Removing an independent chord tone roughly halves its independent salience
  (G#3 in E major 0.0051 → 0.0026; G3 in C major 0.0073 → 0.0035; G3 in E minor 0.0057 → 0.0034).
  Separation is about 2×, so the presence threshold must come from real recordings.
- **Harmonic-coincident tones (Q1).** A missing E3/B3/E4 in E major, or A3 in A minor, keeps a
  clear independent salience (e.g. A3 0.0083 → 0.0052), as expected: their presence cannot be
  established independently. Supports the proposed answer to Q1.
- **Foreign note (Q2).** An added F3 is named first in the residual for A minor and A major; in
  other cases the residual names one of its harmonics (e.g. G5 for C3), and a clean chord already
  leaves a residual of the same order (0.005–0.012) as a foreign note's (0.007–0.014). On synthetic
  strums foreign-pitch rejection would be weak; a threshold cannot be chosen before real recordings
  (T094–T095).
- **Cost.** One analysis (8192 FFT, bands, salience, 6 candidates) measured 0.5–2.5 ms per call in
  a short un-warmed JVM probe on the development Mac (not a benchmark); it runs only while the
  spectrum is enabled, i.e. never for the tuner.

### Requirements A (2026-09-28) — fresh onset for every note, usable level, tuner hold

- **Fresh onset for every note.** Previously only a same-pitch note needed a new articulation
  (FR-022). Now every expected note needs an onset whose transient follows the last reference point
  (acceptance, Start, Resume, Restart, Skip), and matches count only at a usable level (not
  TOO_LOW, not CLIPPING). Evidence that this does not block real notes:
  `AcceptancePreconditionTest` applies the rule with the production analysis to the 15
  real-guitar reference clips (built-in microphone, one guitar): every clip meets it on its played
  note. T076 had already shown that a −40 dBFS level floor changes neither correctness nor latency
  on these clips. Negative signals (digital silence, the recorded silence clip, white noise at −50
  and −30 dBFS, brown noise, mains hum, room noise with hum, clicks, body knocks) never meet it.
- **Latency.** The onset is reported 13 hops (≈ 69 ms) after its transient, while the stability
  window is 20 hops (≈ 107 ms) from the first matching frame, which usually starts at the attack;
  acceptance therefore still comes when the stability window ends, not later. Not re-measured on the
  device (T080).
- **Legato (open, spec Q4-L).** No recording yet shows whether hammer-ons, pull-offs or slides
  produce an onset; the synthetic S4 cases did not cover them. Under the new rule they may wait
  for Skip.
- **Tuner hold.** Display-only change: the last reliable reading stays, marked held, with the
  reason; covered by `TunerHoldTest`.

### Requirements B (2026-09-28) — chords failing in the real build: diagnosis and fix

- **Diagnosis (from source, not a detector fault).** In the build the owner tested, chords could
  never be accepted: `PracticeSession.onFrame` judged only `EXPECTED_NOTE`; `EXPECTED_CHORD` waited
  for Skip by design until T092; and practice never called `AudioEngine.setSpectrumEnabled`, so
  frames carried no spectrum. Single notes worked because they use FastYin alone.
- **Evidence weakness found with deterministic tests.** On synthetic strums (7 open chords, every
  string dropped in turn) the earlier "independent salience" separated a sounding from a missing
  chord tone by only ≈ 2×, and even the unique-partial *mean amplitude* left a margin of 0.10
  (missing G3 in E minor) against 0.11 (sounding B2 in G major) — a threshold there would be fitted,
  not justified. Counting the pitch's **unique partials** (none shared, within ±25 cents, with any
  partial of another expected or still-sounding pitch) that clearly sound (≥ 5 % of the frame's
  strongest expected partial, −26 dB) separates structurally: every sounding verifiable pitch had
  ≥ 2 such partials (≥ 40 % of its unique partials), every missing one ≤ 1. Pitches with fewer than
  2 unique partials (E3, B3, E4 over E2; A3 and E4 in A minor/major; C4/E4 in C major; G3, B3, G4 in
  G major; D4 in D major) cannot be verified separately (Q1 answer A).
- **Matcher and session.** Heard = ≥ 2 clear unique partials and ≥ 1/3 of them; accepted after 8
  consecutive complete frames (≈ 43 ms, on top of the 171 ms spectrum window) within 600 ms of the
  strum's attack; otherwise a confirmed wrong chord listing the missing pitches, and a new strum is
  needed (FR-086). `ChordPracticeTest` runs the whole path (level, articulation and polyphonic
  analysis as in the engine → evidence → matcher → session) on synthetic strums.
- **Limits.** Synthetic Karplus–Strong strums only; one fixed strum spacing (10 ms); no real chord
  recording yet (T094–T095). Foreign pitches (Q2) are neither blocking nor reported. With a built-in
  microphone, weak low fundamentals may reduce the unique partials of bass strings; to be measured.

### Passages and retries (2026-09-28) — design proposal (superseded in part by the owner's policy, implemented as T100)

Owner handoff item 5 (identified after the truncated message) asks for passage segmentation and a
retry policy without silently inventing the final product policy. Everything below is a proposal;
the product decisions are spec.md "Session 2026-09-28" QP1–QP8. No code has been written.

- **What exists (inspected source).** `PracticeSequence` is a flat, immutable list of
  `PracticeEvent`s in played order, built by `PracticeSequenceBuilder` through
  `MidiRepeatController`; each event carries `getSequenceIndex()`, `getPlayIndex()` (played
  measure occurrence), `getMeasureIndex()` and `getBeat()` (hence `TGMeasureHeader.getMarker()`,
  `isRepeatOpen()`, `getRepeatClose()`, `getRepeatAlternative()`). `PracticeSession` keeps
  `currentIndex`, a per-index `Outcome` map (ACCEPTED/SKIPPED/AUTO_PASSED), and a `Verdict`
  (CORRECT/WRONG, FR-087) set by `setVerdict`; `advance()` is the single place where practice moves
  on; `disarm()` implements the FR-086 reference point. There is no notion of range, loop or count.
- **Segmentation (QP1).** Derived purely from the sequence, so it needs no core change: walk the
  events, start a new passage when the played-measure count since the passage start reaches the
  passage length, when `measureIndex` of consecutive played measures is not +1 (a repeat or
  alternate-ending jump), when a measure has a marker, and at the start event. Passages whose events
  are all `AUTO_PASS` merge into the next (or previous, at the end). Measures are the unit because
  they are visible on the score, stable across tempo and independent of note density; 2 measures
  is a guess to be tuned with the owner. Markers and jumps are hard boundaries so that a retry
  never replays a different occurrence of a repeated section.
- **Mistake signal (QP2).** Reuse FR-087's confirmed WRONG verdict, already designed to fire only
  for a fresh, reliable, sustained wrong note or an incomplete strum, and never for silence, noise
  or ringing. Counting events rather than wrong verdicts keeps one troublesome chord from inflating
  the count. Skip does not force a retry because the detector still has known false waits (legato,
  Q4-L; chords on the built-in microphone, T094 pending): forcing retries on skips would punish the
  learner for the application's limits.
- **Retry decision (QP3, QP4).** Decided when `advance()` leaves the passage's last event:
  clean → next passage; not clean and attempts < limit → jump to the passage start; otherwise move
  on and record "not clean". Deciding at the end rather than at the first mistake keeps the end of a
  hard passage reachable. A limit of 2 retries (3 attempts) bounds frustration; it is a parameter.
- **Sketch for the plan stage.** `model/PracticePassages` (built from `PracticeSequence` plus the
  length) → `PracticeSession` gains passage index, attempt number and the set of mistake events;
  `setVerdict(WRONG)` records `currentIndex`; `advance()` applies the decision; a jump clears the
  passage's outcomes, calls `disarm()`/`resetMatch()`/`newEvent()` and re-runs
  `passAutomatically()`; new `PracticeCommand.REPEAT_PASSAGE`; `PracticeSnapshot` exposes passage
  and attempt data; `PracticePalette` renders it; messages in `share/lang/messages.properties`.
  Tests in the style of `PracticeSessionTest` with synthetic frames: clean passage advances; one
  confirmed wrong note → retry from the first event; silence/noise/ringing/skip → no retry; limit
  reached → moves on, marked not clean; repeat occurrences segmented separately; pause keeps the
  attempt; the last note ringing does not accept the first note after a jump; Off = current
  behaviour unchanged.
- **Risks.** A false WRONG verdict now costs a whole passage instead of a cross, so the WRONG
  verdict's reliability on real audio (V2, T094–T095) matters more; until measured, the retry
  limit keeps the damage bounded.
- **Owner policy and implementation (T100).** Approved with changes: Skip counts as an error;
  retry only when the error rate is above 50 % (configurable) instead of any mistake; markers,
  the preparation-window setting and the "Repeat passage" command were not approved and are not
  implemented. Implemented as described in the sketch above, with `PassagePolicy` as the
  parameter holder. The error-rate rule softens the risk noted above: one false WRONG verdict no
  longer repeats a passage unless most of its events already failed. Verified only with scripted
  analysis frames (`PassagePracticeTest`), not on the device.
- **Learner choice (T101).** The owner then made the policy a preparation-window choice (Off,
  1/2/4 measures, 25/50/75 %), kept for the application run in a `TGContext` singleton like
  `PracticeTunerContext`, never written to the TuxGuitar configuration. Off passes no policy to
  the session, which is the code path of the practice behaviour before passages existed.

### T102 — Audio-recognition settings: parameter inventory (before coding, 2026-09-28)

Exact current values from source, their consumers, and the proposed safe ranges. **Every default
is the current hard-coded value** (owner rule: changing musical behaviour is a product decision);
the ranges only bound what a learner may choose. Keys are stored in the module configuration
`tuxguitar-audio-recognition` (lib `TGConfigManager`, like the synth/jack plugins).

| Section | Key (`recognition.` +) | Type / unit | Current value (source) | Consumer(s) | Range proposed | Live? |
|---|---|---|---|---|---|---|
| Input | `input.level.min` | dBFS | −40 (`AudioEngine.DEFAULT_TOO_LOW_RMS_DBFS`) | `LevelAnalyzer` → level state TOO_LOW: tuner/practice status, practice "usable level" (FR-086) | −60 … −25 | analysis restarted cleanly |
| Input | `input.level.max` | dBFS | −6 (`AudioEngine.DEFAULT_TOO_HIGH_RMS_DBFS`) | `LevelAnalyzer` → TOO_HIGH | −20 … −1 | idem |
| Advanced | `advanced.level.clip-sample` | ratio of full scale | 0.99 (`DEFAULT_CLIPPED_SAMPLE_LEVEL`) | `LevelAnalyzer` clipped sample | 0.90 … 1.00 | idem |
| Advanced | `advanced.level.clip-share` | % of samples | 10 (`DEFAULT_CLIPPING_RATIO` 0.10) | `LevelAnalyzer` CLIPPING state | 1 … 50 | idem |
| Tuner | `tuner.in-tune` | cents | 5 (`TunerWindow.IN_TUNE_CENTS`, `PracticeWindow.TUNER_IN_TUNE_CENTS`) | tuner "in tune" state and needle colour; practice palette tuning state (`TolerancePolicy` first argument) | 1 … 20 | tuner: next frame; practice: next Start |
| Tuner | `tuner.hold` | on/off | on (`TunerHold`, FR-085) | `TunerWindow` | — | next frame |
| Advanced | `advanced.tuner.confidence` | 0–1 | 0.80 (`TunerWindow.CONFIDENCE_GATE`) | tuner `NoteInterpreter` | 0.50 … 0.95 | next tuner open |
| Notes | `note.accept` | cents | 35 (`PracticeWindow.PRACTICE_ACCEPT_CENTS`) | `TolerancePolicy` second argument (acceptance) | 10 … 45 (`TolerancePolicy` requires < 50) | next Start |
| Notes | `note.confidence` | 0–1 | 0.80 (`PracticeWindow.CONFIDENCE_GATE`) | practice `NoteInterpreter` | 0.50 … 0.95 | next Start |
| Notes | `note.stability` | ms | 20 hops = 106.7 ms (`PracticeWindow.STABILITY_HOPS` × 256/48000 s) | `PracticeSession` stability window, also wrong-note confirmation | 50 … 300 ms, converted to whole hops (107 ms → 20 hops, exact) | next Start |
| Notes | `note.attack` | dB | 4.5 (`ArticulationDetector.DEFAULT_GATE_DB`) | onset detector: level rise needed for a new attack (FR-086 protection) | 2 … 10 | analysis restarted cleanly |
| Notes | `note.legato` | choice | strict: a new attack for every note (FR-086) | `PracticeSession` | only value implemented; proposal Q4-L documented, not offered | — |
| Advanced | `advanced.attack.min-level` | dBFS | −50 (`ArticulationDetector.DEFAULT_MIN_LEVEL_DB`) | onset detector ignores quieter frames | −70 … −30 | analysis restarted cleanly |
| Chords | `chord.evidence` | % of unique partials | 33.3 (`ChordMatcher` `minClearFraction` 1/3) | `ChordMatcher` heard rule | 20 … 80 | next Start |
| Chords | `chord.window` | ms | 600 (`ChordMatcher` `windowNanos`) | strum window before "wrong chord" | 300 … 1500 | next Start |
| Chords | `chord.completeness` | choice | every verifiable pitch required (FR-079) | `ChordMatcher`/`PracticeSession` | only value implemented | — |
| Chords | `chord.foreign` | choice | ignored, not reported (Q2 provisional) | — | only value implemented; proposal Q2 documented, not offered | — |
| Advanced | `advanced.chord.min-partials` | count | 2 (`minClearPartials`) | `ChordMatcher` verifiability and heard rule | 1 … 4 | next Start |
| Advanced | `advanced.chord.confirm` | ms | 8 hops = 42.7 ms (`stableHops`) | `ChordMatcher` consecutive complete frames | 20 … 150 ms → whole hops (43 ms → 8, exact) | next Start |
| Passages | `passage.measures` | choice | 2 (`PassagePolicy.DEFAULT_MEASURES`; 0 = Off) | `PassageSettings` → `PassagePolicy` | 0 (Off), 1, 2, 4 | next Start |
| Passages | `passage.threshold` | % | 50 (`DEFAULT_MAX_ERROR_RATE`) | idem | 25, 50, 75 | next Start |
| Passages | `passage.retries` | count | 2 (`DEFAULT_MAX_RETRIES`) | idem | 0 … 5 | next Start |
| Engine | `engine.pitch` | choice | FastYin (TarsosDSP) — the only estimator in the code | `AudioEngine` estimator factory | "Automatic (FastYin) — recommended" only | next capture |

**Cross-parameter rules (repair, never crash):** `note.accept` ≥ `tuner.in-tune` (the accept
range is raised to the in-tune range when lower, since `TolerancePolicy` rejects the opposite);
`input.level.min` < `input.level.max` holds by the disjoint ranges; `passage.threshold` and
`passage.retries` are kept but unused while `passage.measures` = Off.

**Deliberately not exposed** (documented in the help): FastYin window 2048 / hop 256 samples
(every time-based setting and the benchmark depend on them), articulation flux constants and
refractory time, polyphonic analyzer internals and `ChordEvidence.PARTIAL_PRESENCE_RATIO`,
level-check (`InputAssessor`) shares, ring-buffer size, display timings (verdict 1 s, flash
250 ms).

**Input device:** stays the shared per-run selection (the rubric reuses the practice input
selector). Remembering it across launches is out of scope in spec.md; kept as is and proposed.

**Passages:** the Configuration values are the persistent defaults; the preparation-window
choice (FR-091) overrides them for the current run only, and is reset to them when the
Configuration is confirmed.

**Architecture:** the Configuration window (`TGSettingsEditor.initOptions`) has a fixed option
list and no plugin extension; the core cannot depend on the fork's plugin modules. A generic
extension point is therefore added to the core (`TGSettingsOptionRegistry`: plugins register an
option factory, the editor appends the registered options). The typed settings, validation and
persistence live in `TuxGuitar-audio-engine` (shared by the tuner and practice); the rubric UI
lives in `TuxGuitar-practice`, which already owns the input selector.
- **Implementation notes (T102).** Implemented as inventoried; no default changed. Two findings
  while testing: (1) the chord evidence default must stay the exact double 1/3 (a percent
  33.33 stored and divided by 100 differs in the last bit and would flip the boundary case of 1
  of 3 unique partials), so fractions are stored as fractions and only shown in %; (2) the
  stability time is stored in ms but converted to whole hops by rounding (107 ms → 20 hops),
  which keeps the former 106.67 ms window exactly, where a plain 107 ms would have needed one
  extra hop. Engine values restart analysis at once through the existing atomic configuration
  swap of `AnalysisService` (verified by `AudioEngineSettingsTest`: frames switch from old to new
  values once, never back, timestamps keep increasing, no dropped samples). Practice values apply
  at the next Start; tuner values at the next frame (UI thread). The rubric lives in the practice
  plugin, which owns the input selector; if the practice plugin is disabled the page is absent but
  the saved values still apply to the tuner.
- **P13 failure (2026-09-28) — root cause.** Not the settings model: the SWT binding. SWT 4.37 cocoa
  `Scale` (bytecode checked with `javap -c`): `setMinimum(v)` returns without effect when
  `v < 0` or `v >= maxValue`; `setMaximum(v)` when `v <= minValue`; the default range is 0…100.
  The page called `setMinimum(-60)` / `setMaximum(-25)` for "Minimum usable level" (and
  `-20…-1` for "High level warning"): both ignored, so the native range stayed 0…100. Dragging
  gave positions 0…100, read as 0…100 dBFS, then clamped by validation to −25 (or −1):
  the value text never changed. After each change the page refreshed every control; the other
  level slider received a negative position, which the native slider clamps to 0: far left.
  Other sliders were affected too (e.g. strum window 300…1500 ms: `setMinimum(300)` ignored
  while the maximum was still 100). Fix: 0-based positions (offset from the minimum, maximum
  set first), exact value reconstruction, and refresh limited to changed values. A headless
  toolkit reproducing that `Scale` contract (`SwtContractToolkit`) makes the page tests fail with
  the old code (5 failures) and pass with the fix; the native SWT test still has to be run in a
  graphical session.

