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

**Decision**: implement **YIN with parabolic interpolation**, difference function computed via
FFT-based autocorrelation (reusing the permissive `craigl` FFT or an equivalent in-module FFT),
analysis window sized for ≥ 2 periods of the lowest supported fundamental, hop ≈ 5–10 ms.
Implement from the published algorithm; **do not** add third-party DSP libraries (e.g.
TarsosDSP is GPL-3.0, incompatible with shipping inside an LGPL-2.1 application).
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
  run explicitly (`-Dgroups=benchmark`); results written to
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
