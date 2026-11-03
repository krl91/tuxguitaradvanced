# Feature Specification: Recognise notes and chords natively, without Java, at least as well and with less delay

**Feature Branch**: `feature/interactive-practice` (no branch of its own; spec directory `specs/023-native-recognition-engine`)

**Created**: 2026-11-02

**Status**: Draft

**Input**: User description: "la reconnaissance doit aussi pouvoir se faire en rust (paramétrable dans le menu) c'est important et tu dois améliorer la reconnaissance !!!" — then: "le but est de ne plus passer par java pour reconnaître les notes et accords en gardant au moins la même qualité de reconnaissance pour les note et accord et en améliorant la latence" — and: "le mode arcade doit être très performant et temps réel".

**Where it comes from**: spec 022 moved the amp sound to a native device (Rust) because the Java path was too slow: Java Sound gave 37 ms at best and 86 to 126 ms in practice, while the native device measured 32.5 ms round trip at 128 frames through the USB microphone. Its research (R1 addendum) explicitly did **not** choose "one native input that also feeds the recognition", so the recognition still goes through Java: capture by Java Sound in bursts of about 11.6 ms, then pitch and chord analysis in Java. This spec does that step: the whole path from the input to "this note / this chord was played" leaves Java.

## Clarifications

### Session 2026-11-03

- Q: By how much must the native engine lower the live delay from the strike to the verdict on the owner's Mac? → A: The median at least 20 ms lower than the Java engine's, and the 90th percentile not higher.
- Q: Do the chord improvement targets (SC-004) block shipping the native engine? → A: No: they are aimed at, not blocking; the native engine ships once SC-001 to SC-003, SC-005 and SC-006 are met, the chord work follows in this spec, measured, and the final report says which targets were reached.
- Q: Which engine is used by default, after the update and on a new installation? → A: *Automatic*, which uses the native engine when it starts, else the Java engine with a message; a Java engine chosen by hand before the update is kept.
- Q: Must the native engine cover everything the Java recognition covers today (bass, lowered tunings, techniques such as bends, slides and harmonics)? → A: Yes, everything, as the Java engine: every current measure, including the bass and technique benchmarks, must be matched.
- Q: Which frame rate must Arcade hold while the native engine recognises live? → A: The screen's refresh rate (60 frames a second on a 60 Hz screen, 120 on the owner's ProMotion MacBook): 99 % of the frames on time, no frame held by the recognition, the current limits of the Arcade benchmarks kept.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - My notes and chords are recognised without Java, sooner (Priority: P1)

The learner practises (Wait, Follow, Arcade, the pre-run check) and what they play is captured and recognised by the native engine: the input is read by the native side, notes and chords are found there, and only the results (which note or chord, when, how sure) reach the application. The verdicts come sooner than with the Java path, and are at least as right; in Arcade the hits show sooner and the game stays fully real time.

**Why this priority**: it is the owner's goal; the delay between the string and the verdict is what makes Follow and Arcade feel right.

**Independent Test**: run the dataset benchmarks with the native engine and with the Java engine on the same recordings, and measure the live delay on the owner's Mac with both; the native engine is at least as good on every measure and faster.

**Acceptance Scenarios**:

1. **Given** the native engine is selected and loads, **When** the learner plays a written note in Wait mode, **Then** it is accepted as with the Java engine, and no Java Sound line is opened for the recognition.
2. **Given** the same recordings, **When** both engines analyse them, **Then** every dataset measure of SC-001 is at least the Java engine's figure.
3. **Given** the owner's Mac with the Rocksmith cable, **When** a note is struck, **Then** the median delay to the verdict is lower with the native engine than with the Java engine (SC-002).

---

### User Story 2 - I choose the recognition engine in the menu (Priority: P1)

In the recognition settings, the existing *Pitch detection engine* choice offers the native engine beside the Java one, plus *Automatic*. The choice is kept across restarts. If the native engine cannot start on this computer, the application says so and recognises with Java, the practice goes on.

**Why this priority**: the owner asked for the choice in the menu; it also lets the learner go back to Java if the native engine misbehaves on their machine.

**Independent Test**: switch the setting between the engines, restart, check the setting and the engine used; remove the native library and check the message and the fall back to Java.

**Acceptance Scenarios**:

1. **Given** the recognition settings, **When** the learner opens the engine choice, **Then** it offers *Automatic*, the native engine and the Java engine, with a tip saying what each does.
2. **Given** the learner picks an engine, **When** the application restarts, **Then** the same engine is used.
3. **Given** the native library cannot be loaded or the device cannot be opened natively, **When** a run starts with *Automatic* or the native engine, **Then** a message says the native engine is unavailable and why, and the Java engine recognises instead; nothing crashes or hangs.
4. **Given** the engine is changed between runs, **When** the next run starts, **Then** it uses the new engine without restarting the application.

---

### User Story 3 - Chords are recognised better (Priority: P2)

Beyond keeping the quality, chords are where the recognition is weakest today; the native engine is the occasion to improve them: more of the owner's chords accepted, fewer neighbouring chords accepted by mistake, more complete GuitarSet chords found.

**Why this priority**: asked by the owner ("tu dois améliorer la reconnaissance"), but the P1 goal (no Java, same quality, less delay) can ship without it.

**Independent Test**: the chord benchmarks (owner's chords, GuitarSet, IDMT a whole tone higher) before and after, for each engine.

**Acceptance Scenarios**:

1. **Given** the owner's 42 chords through the Rocksmith cable, **When** the native engine judges them, **Then** more are accepted and fewer neighbouring chords are wrongly accepted than today (SC-004).
2. **Given** an improvement of the chord judgement, **When** it is measured, **Then** no single-note or wrong-note figure gets worse.

---

### Edge Cases

- The native library is missing, of the wrong architecture or refuses to load: Java engine, message, practice goes on.
- The input device disappears during a run (cable pulled): the run reacts as it does today with the Java capture (input lost message), never a crash or a frozen window.
- The microphone permission is denied (macOS): same message and behaviour as today.
- The amp sound of spec 022 is on: both use the practice input; the recognition's verdicts are identical with the amp sound on and off (as spec 022 FR-003).
- The device runs at 44.1, 48, 88.2 or 96 kHz: the native engine works at the device's current rate and never changes it (standing rule: opening a device must not change its system rate).
- The computer is busy: the native engine must not drop input silently; dropped blocks are counted and reported like the Java capture's.
- The calibration of the input (spec 013), the input meter and the pre-run check use the same engine as the recognition, so their numbers match what the recognition hears.
- An engine switch while the practice window is open but no run is going: the next run uses the new engine.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001** (native path): With the native engine, the practice input MUST be captured and analysed outside Java (pitch of single notes, onsets, chord evidence, the same information the Java path gives today), for everything the Java engine covers: guitar and bass, every tuning the practice supports, and the techniques it recognises (bends, slides, harmonics and the others); only the results MUST reach the application, which keeps the rules (accepted, early, late, wrong) and the scores unchanged.
- **FR-002** (same quality): On the datasets (`~/tuxguitar-datasets`: IDMT, GuitarSet, the owner's chords, the technique and effect sets), every measure listed in SC-001 MUST be at least as good with the native engine as the Java engine's figure measured before the change.
- **FR-003** (less delay): The delay from the strike to the verdict MUST be lower with the native engine than with the Java engine, measured live on the owner's Mac and offline on the datasets (SC-002).
- **FR-004** (choice in the menu): The existing *Pitch detection engine* setting MUST offer *Automatic*, the native engine and the Java engine, with tips in English, French and Spanish; the choice MUST survive a restart and apply from the next run.
- **FR-005** (Automatic, the default): *Automatic* MUST be the default, after the update and on a new installation; it MUST use the native engine when it can start, else the Java engine; the setting MUST show which engine *Automatic* stands for. A Java engine chosen by hand before the update MUST be kept.
- **FR-006** (fallback): When the native engine cannot start (library, device, permission, format), the application MUST say why once, recognise with the Java engine, and never crash or hang.
- **FR-007** (Java engine kept): The Java engine MUST keep working exactly as today when selected: its dataset outcome files MUST be identical before and after this spec, except where an improvement of US3 is deliberately applied to both engines and measured.
- **FR-008** (same consumers): The calibration, the input meter, the pre-run check and the three practice modes MUST use the selected engine; the standalone tuner is out of scope.
- **FR-009** (devices): The native engine MUST open the input chosen in the practice settings (device and channel) at its current rate, never change the device's rate, and report lost devices and dropped blocks as the Java capture does.
- **FR-010** (amp sound): The amp sound of spec 022 and the native recognition MUST be able to run together on the same input; the recognition's results MUST be identical with the amp sound on and off.
- **FR-011** (real time): The native engine's audio thread MUST NOT block, allocate in the loop or wait on the user interface; it MUST NOT slow the score's display or playback.
- **FR-012** (chords better, US3): The chord judgement SHOULD reach the targets of SC-004 with the native engine without breaking FR-002; these targets do not block shipping, and the final report MUST state for each one whether it was reached.
- **FR-013** (measured): Each engine MUST be measured on the datasets before and after, with the reports kept in the spec's benchmark folder, so the comparison can be repeated.
- **FR-015** (Arcade in real time): In Arcade, the native engine MUST keep the game fully real time: the hit or miss of a note MUST show on screen within the delay of SC-002 plus one frame, Arcade MUST hold the screen's refresh rate (60 frames a second at 60 Hz, 120 at 120 Hz) and no frame MUST be held by the recognition (SC-006).
- **FR-014** (parity): The native engine MUST ship on macOS, Linux and Windows with the same features (standing rule); the live delay MUST be reported for macOS by the owner's Mac and for Linux and Windows by testers.

### Key Entities

- **Recognition engine**: Java or native; selected by the setting, or by *Automatic*; reports which one is in use and, if the native one failed, why.
- **Recognition result**: what the engine hands to the application for a moment of the input: the notes heard with their pitch and strength, the onsets, the chord evidence, and the time of the input they belong to.
- **Dataset report**: per engine, per dataset measure, the figure before and after, and the delays.

## Success Criteria *(mandatory)*

### Measurable Outcomes

Baseline = the Java engine on 2026-11-02 (`desktop/TuxGuitar-practice/target/dataset-benchmark/`, commit 0ffd99c89).

- **SC-001** (quality, native ≥ baseline on each line):
  - IDMT single notes recognised: 100 %.
  - Wrong notes ±1 and ±2 semitones refused: 100 %.
  - IDMT chords recognised: ≥ 95.5 %.
  - Incomplete chord refused: ≥ 97.2 %.
  - Chord a whole tone higher refused: ≥ 66.7 % (Audio-Effects), 100 % (IDMT-Guitar).
  - By effect: distortion ≥ 86.5 %, chorus ≥ 90.4 %, and every other effect line at least its baseline.
  - GuitarSet complete chords accepted: ≥ 33/56 (strings summed), ≥ 23/56 (microphone).
  - Owner's chords: ≥ 26/42 accepted, ≤ 13/42 neighbouring chords wrongly accepted.
  - The 942 dataset tests: no test OK with Java becomes KO with the native engine.
  - The technique benchmark and the bass benchmark (spec 003): every line at least its Java figure.
- **SC-002** (delay): with the native engine,
  - offline, the GuitarSet chord delay median is ≤ 103 ms (summed) and ≤ 149 ms (microphone), and lower than the Java engine's on the same run;
  - live on the owner's Mac (Rocksmith cable and built-in microphone), the median delay from the strike to the verdict is at least 20 ms lower than the Java engine's, and its 90th percentile is not higher.
- **SC-003** (fallback): with the native library removed, a run starts with the Java engine and a message within 2 seconds, on the three systems.
- **SC-004** (chords better, US3, native engine; aimed at, not blocking): owner's chords ≥ 34/42 accepted and ≤ 6/42 neighbours wrongly accepted; GuitarSet complete chords ≥ 40/56 summed and ≥ 30/56 microphone; chord a whole tone higher refused ≥ 85 % (Audio-Effects); all with SC-001 still met.
- **SC-006** (Arcade): with the native engine running live, 99 % of the Arcade frames are on time at the screen's refresh rate (60 or 120 frames a second; on the owner's Mac, 120), the frame times measured by the existing Arcade benchmarks of spec 003 stay within their current limits, no frame waits on the recognition over a 10-minute run, and the delay from the strike to the hit shown on screen is lower than with the Java engine (median and 90th percentile).
- **SC-005** (stability): 10 minutes of live recognition with the native engine without a dropped block on the owner's Mac, and the dataset results identical with the amp sound on and off.

## Assumptions

- "Without Java" means the input capture and the signal analysis (pitch, onsets, chord evidence); the rules of the practice modes, the scores, the user interface and the settings stay in the Java application, which receives the results.
- The native engine is written in Rust and extends the native crate of spec 022 (one code for the three systems, already built by the three CI jobs); the plan decides whether it shares the amp's input stream or opens its own.
- The analysis is deterministic and explainable signal processing (constitution VIII), like today's; no machine learning.
- The live delay is measured with the probes of specs 013 and 022 on the owner's Mac; probe clicks are audible and the owner is warned before each one.
- Out of scope: the standalone tuner plugin, the score's playback (MIDI synthesizers), Android, new practice modes, machine-learning recognition.
