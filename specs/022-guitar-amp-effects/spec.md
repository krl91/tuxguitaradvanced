# Feature Specification: Hear my guitar through an amp and effect pedals while I practise

**Feature Branch**: `feature/interactive-practice` (no branch of its own; spec directory `specs/022-guitar-amp-effects`)

**Created**: 2026-11-01

**Status**: Clarified (2026-11-01)

**Input**: User description: "spec 022 : dans les modes de pratique, l'app doit restituer le son joué par le joueur à la guitare, traité par un DSP d'effets réglables (écho, saturation, distorsion, compresseur, autres…) : l'app agit comme des pédales d'effet et un ampli, comme dans Rocksmith. Référence possible : Rocksmith 2014 installé sur la machine du propriétaire."

**Reference looked at**: Rocksmith 2014 is installed on the owner's Mac. Its sound is the reference for the experience: the guitar plugged into the computer is heard through a virtual amp, with pedals before and after it, and the sound follows the song. Its game files are packed archives and were not opened; nothing of Rocksmith (names, models, presets, sounds) is copied. Its settings file shows a small output buffer setting (`LatencyBuffer=2`), a reminder that the delay between the string and the speaker is what makes such a feature playable or not.

## Clarifications

### Session 2026-11-01 (while writing this specification)

- Q: Where is the sound active? → A: **Whenever the practice window is open**: during Wait, Follow and Arcade runs and between them (to warm up and try sounds); closing the practice window stops it.
- Q: Which pedals in this spec? → A: **A first set**: compressor, overdrive, distortion, echo, reverb, chorus and noise gate, plus the amp (clean, crunch, high gain) and its cabinet. Other pedals (fuzz, flanger, phaser, tremolo, wah, octave, equalizer, pitch shifter…) come in a later spec.
- Q: Does the sound follow the score? → A: **Per score**: the last sound chosen for a score is remembered and comes back when that score is practised again; the learner can always change it.

### Session 2026-11-01 (/speckit-clarify)

- Q: If the delay from the string to the sound cannot reach 20 ms on the owner's Mac with the Rocksmith cable, what happens? → A: **20 ms stays the target; up to 30 ms the feature ships with the delay shown; above 30 ms it does not ship until improved.**
- Q: Where and in what form does the learner set the amp and the pedals? → A: **In a separate window, *Amp and effects***, opened by a button of the practice window and of the practice palette, usable during a run; the chain is shown as plain controls (a row per pedal with its switch and sliders), not as drawn pedals.
- Q: How is "the same score" recognised to give it back its sound? → A: **By the title and the artist written in the score** (by the file name when both are empty): the sound follows the score when it is moved or renamed, and two copies of the same song share it.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - I hear my guitar through an amp while I practise (Priority: P1)

The learner plugs the guitar into the computer (an instrument cable such as the Rocksmith cable, or an audio interface), opens the practice window, then the *Amp and effects* window, and turns on *Amp sound*. While practising, they hear their own guitar from the computer's speakers or headphones, shaped by a virtual amp (clean, crunch or lead), at the same time as the score plays in *Follow the tempo* and Arcade. Recognition and judging work exactly as before.

**Why this priority**: today a guitar plugged into the computer with a cable is silent: the learner hears nothing, or must add a real amp. Hearing the guitar is the base on which every effect is built.

**Independent Test**: plug a guitar, turn on *Amp sound*, play: the guitar is heard with no noticeable delay, the level is comfortable, and a recorded run gives the same verdicts with the sound on and off.

**Acceptance Scenarios**:

1. **Given** a guitar on an instrument input and *Amp sound* on, **When** the learner plays a note, **Then** it is heard from the chosen output within the delay of SC-001, shaped by the chosen amp.
2. **Given** *Amp sound* on, **When** a Wait, Follow or Arcade run goes, **Then** the verdicts, timings and score are exactly the same as with the sound off (the sound heard never feeds the recognition).
3. **Given** Follow or Arcade plays the score, **When** the guitar sounds too, **Then** both are heard together, and the learner can set the guitar's volume against the score's.
4. **Given** *Amp sound* on, **When** the learner stops playing, **Then** nothing is heard but silence (no hum or hiss above −60 dBFS at the output, FR-011).

---

### User Story 2 - Effect pedals I can choose and tune (Priority: P1)

Before and after the amp, the learner adds effect pedals from a list (a compressor, an overdrive, a distortion, a chorus, an echo, a reverb, a noise gate) and sets each one with a few plain knobs (drive, tone, level, time, mix…). Each pedal can be switched on and off; the order of the chain is shown. The change is heard at once while playing.

**Why this priority**: the owner asked for adjustable effects "like pedals"; with the amp alone the feature is a monitor, with pedals it becomes the Rocksmith experience.

**Independent Test**: add an echo and a distortion, play, move each knob and switch each pedal: each change is heard within a tenth of a second, without a click or a gap.

**Acceptance Scenarios**:

1. **Given** the sound is on, **When** the learner adds a pedal, switches it or moves a knob, **Then** the change is heard within 0.1 second, with no crackle, gap or jump in level.
2. **Given** an echo, **When** the learner stops playing, **Then** the repeats die away naturally (they are not cut).
3. **Given** a chain of pedals and an amp, **When** the learner reopens the application, **Then** the same chain and settings are found.
4. **Given** any knob position, **When** the learner plays, **Then** the output never exceeds the safe level of FR-010.

---

### User Story 3 - Sounds I can save and pick per song (Priority: P2)

The learner saves a chain as a named *sound* ("Clean", "Crunch", "Lead with echo"…), finds a few ready-made sounds, and picks one before a run. The last sound chosen for a score is remembered and comes back as soon as the practice window is open on that score again (before any run, for warming up), as Rocksmith keeps a tone per song; a score never practised starts with the last sound used.

**Why this priority**: saving a sound avoids setting the knobs again each time; it is comfort on top of US1 and US2.

**Independent Test**: save two sounds, restart, switch from one to the other before a run: each one sounds as saved.

**Acceptance Scenarios**:

1. **Given** a chain, **When** the learner saves it under a name, **Then** it is listed with the ready-made sounds and survives a restart.
2. **Given** saved sounds, **When** the learner picks one, **Then** it is heard at once.
3. **Given** a ready-made sound changed by the learner, **When** they choose to restore it, **Then** it sounds as shipped.
4. **Given** the learner picked *Lead* for score A and *Clean* for score B, **When** they practise A again, even after a restart, **Then** *Lead* is chosen; with B, *Clean*.
5. **Given** a score never practised, **When** its practice starts, **Then** the last sound used is chosen.
6. **Given** the sound remembered for a score was deleted, **When** that score is practised, **Then** the last sound used is chosen and nothing fails.

---

### User Story 4 - Safe by default (Priority: P2)

The sound is off until the learner turns it on. With the computer's built-in microphone as input, the application warns about howling (feedback) and suggests headphones; a howl, whatever the input, is cut at once.

**Why this priority**: a loud feedback loop can hurt the ears and the speakers; an unexpected sound at start-up surprises.

**Independent Test**: a fresh install starts silent; turning the sound on with the built-in microphone shows the warning; a howl played into the microphone is cut within the time of SC-005.

**Acceptance Scenarios**:

1. **Given** a fresh install, **When** the practice window opens, **Then** no guitar sound is played.
2. **Given** the input is the built-in microphone, **When** the learner turns the sound on, **Then** a warning about feedback is shown, with the advice to use headphones.
3. **Given** a feedback howl builds up, **When** it is detected, **Then** the sound is muted within the time of SC-005 and a message says why, and the learner can turn it on again.

---

### Edge Cases

- **No instrument input, only the built-in microphone**: the feature works but warns (US4); the default remains off.
- **The output device changes or disappears** (headphones unplugged, interface switched off): the sound stops without a crash or a hang, a message says so, and it comes back when the learner chooses an output again.
- **The input level is too high**: the sound shows clipping like the input meter of the practice window does, and the advice of the gain control applies.
- **The computer cannot keep up** (too much processing for the delay asked): the sound gets simpler (fewer pedals active, then the amp alone) or is muted with a message, rather than crackling or delaying the recognition.
- **Pause, stop, end of a run**: the sound keeps playing between runs while the practice window is open; closing the practice window stops it.
- **The score's playback in Follow and Arcade**: the guitar sound is mixed with it, never replaces it; the guitar never slows or shifts the score's timing.
- **The tuner and the calibration**: the sound does not change what they measure.
- **Two outputs** (score through the system's synthesizer, guitar through an audio interface): both are allowed; the learner picks the guitar's output.
- **Two different songs with the same title and artist, or a score with neither**: they share the remembered sound (title and artist), or fall back to the file name; the learner can always change it.
- **A value typed out of range**: brought back into the range, never an error.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001** (sound of the guitar): When *Amp sound* is on, the guitar's signal from the practice input MUST be played on the chosen output, processed by the learner's chain, whenever the practice window is open: during Wait, Follow and Arcade runs and between them; closing the practice window MUST stop it. Outside the practice window, nothing is played.
- **FR-002** (delay): The delay between a string being struck and the sound heard MUST stay within SC-001 on the owner's computer with an instrument input; the delay estimated from the devices in use (input block, output buffer and processing) MUST be shown to the learner in the *Amp and effects* window, named as an estimate; the delay measured end to end is a development check (SC-001), not shown in the application.
- **FR-003** (recognition untouched): The processed sound MUST NOT feed the recognition, the tuner, the calibration or the input meter; verdicts, timings, scores and recorded runs MUST be identical with the sound on and off.
- **FR-004** (amp): The chain MUST contain one amp with at least a clean, a crunch and a high-gain character, with gain, bass, middle, treble and volume knobs, and a speaker cabinet that can be switched off (for learners using a real amp or a cabinet simulator).
- **FR-005** (pedals): The learner MUST be able to place pedals before and after the amp, chosen from this first set: compressor, overdrive, distortion, echo, reverb, chorus and noise gate (each kind at most once in the chain); each pedal MUST have an on/off switch and a few knobs with plain names, units and defaults.
- **FR-006** (live changes): Any change (a pedal added, removed, moved, switched, a knob moved, a sound picked) MUST be heard within 0.1 second, without a click, a gap or a jump in level.
- **FR-007** (mix): The learner MUST be able to set the guitar's volume and, in Follow and Arcade, its balance with the score's playback; the score's timing MUST NOT change.
- **FR-008** (sounds): The learner MUST be able to save the chain as a named sound, rename and delete it, pick it before a run, and restore a ready-made sound; the application MUST ship at least four ready-made sounds (clean, crunch, lead, clean with echo).
- **FR-019** (sound per score): The last sound chosen for a score MUST be remembered for that score, a score being recognised by its title and artist (by its file name when both are empty), so that moving or renaming the file keeps it, and chosen again whenever the practice window opens on that score or the score in front of it changes, across restarts; a score never practised, or whose remembered sound was deleted, MUST start with the last sound used.
- **FR-009** (kept): The on/off choice, the output, the volumes, the current chain and the saved sounds MUST survive a restart.
- **FR-010** (safe level): The output MUST be limited so that no knob combination makes it louder than a fixed safe ceiling; turning the sound on MUST start quietly and rise in about half a second.
- **FR-011** (silence): With the guitar not played, the noise at the output MUST stay below −60 dBFS with the guitar's volume at its default, for every ready-made sound (a noise gate is available and on in the ready-made high-gain sounds).
- **FR-012** (feedback): The sound MUST be off by default; with the built-in microphone, turning it on MUST show a warning about feedback and the advice to use headphones; a feedback howl MUST be detected and muted with a message (SC-005).
- **FR-013** (devices): Losing the output, a denied permission or an unsupported format MUST stop the sound with a clear message, never crash or hang the application, and the practice MUST go on without the sound.
- **FR-014** (cost): When the computer cannot process the chain within the delay, the sound MUST get simpler or mute with a message rather than crackle; it MUST NOT slow the recognition, the score's display or the score's playback.
- **FR-015** (parity): The feature MUST be the same on macOS, Linux and Windows (owner's standing rule); the delay reached on each system MUST be reported, Linux and Windows by testers.
- **FR-020** (the *Amp and effects* window): The amp, the pedals, the choice of the sound, the volumes, the output and the measured delay MUST be set in a separate window, *Amp and effects*, opened by a button of the practice window and by a button of the practice palette, and usable while a run goes (the run goes on). It shows the chain in its order, a row per element with its switch and its knobs as sliders with their values. It MUST NOT cover the framed note or the next note when opened during a run (it opens beside the score or where the learner last put it), and it closes with the practice window.
- **FR-016** (tooltips and languages): Every pedal, knob, amp setting and message MUST have a label and a tooltip in English, French and Spanish saying what it changes, its unit and its default.
- **FR-017** (documentation): The practice guides in the three languages MUST describe the sound, the chain, the sounds, the delay, headphones and feedback, and that the sound never changes the judging.
- **FR-018** (original): The amp, the pedals and the ready-made sounds MUST be the project's own: no name, picture, preset or recorded sound taken from Rocksmith or from a pedal or amp maker.

### Key Entities

- **Amp sound setting**: on/off (default off), the chosen output, the guitar's volume and its balance with the score.
- **Chain**: the ordered pedals before the amp, the amp with its cabinet, the pedals after it; each element has a kind, an on/off state and its knob values.
- **Pedal**: a kind (compressor, overdrive, echo…), its knobs (each with a name, a unit, a range and a default) and its state.
- **Sound**: a named, saved chain; ready-made (restorable) or the learner's own.
- **Sound per score**: the score's title and artist (or its file name when both are empty) and the name of the sound last chosen for it.
- ***Amp and effects* window**: its place on the screen, kept between sessions.
- **Estimated delay**: the delay between the input and the output on the current devices, computed from their formats and buffers, shown to the learner as an estimate.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: On the owner's Mac with an instrument cable (Rocksmith cable), the delay from a string struck to its sound heard is measured by a loopback test: the target is at most 20 ms; the feature may ship up to 30 ms, with the delay shown to the learner; above 30 ms it MUST NOT ship. The owner says it feels like playing through an amp.
- **SC-002**: The same recorded runs (Wait, Follow, Arcade) give identical verdicts, timings, scores and summaries with the sound off and on, whatever the chain.
- **SC-003**: Every change of a knob, a switch or a sound is heard within 0.1 second, and a recording of the output while moving every knob end to end shows no click, gap or level jump above a fixed limit.
- **SC-004**: With every knob at its loudest, the output stays under the safe ceiling; turning the sound on reaches the set level after about half a second.
- **SC-005**: A feedback howl created on purpose (built-in microphone and speakers) is muted within 0.5 second.
- **SC-006**: During one hour of practice with a chain of six active elements, the owner hears no crackle or dropout, and the score keeps scrolling and playing as smoothly as with the sound off.
- **SC-007**: A fresh install is silent; the chain, the sounds, the sound of each score and the settings survive a restart.
- **SC-008**: Every pedal, knob and message has its label and tooltip in the three languages.
- **SC-009**: On Linux and Windows the packages contain the same feature; the delays reached there are reported by testers (or stated as unknown).

## Assumptions

- The usual setup is a guitar plugged in with an instrument cable (the Rocksmith Real Tone Cable or an audio interface) and headphones or speakers on the computer; the built-in microphone is supported but with a warning.
- The sound is heard through the computer's output that the learner picks; it can be the same device as the score's playback or another one.
- The sound keeps playing between runs while the practice window is open, so the learner can warm up and try sounds (owner's answer).
- The amp and pedals are built from deterministic, explainable signal processing (constitution VIII), not from recorded impulse responses of commercial gear or from machine learning.
- The delay target of 20 ms (ceiling 30 ms for shipping) is for the owner's Mac with an instrument cable; Bluetooth headphones add their own delay and are out of the target (the application says so).
- Out of scope: recording the processed sound to a file, a looper, sharing sounds online, a sound for the bass different from the guitar's (same chain), MIDI control of the pedals, effects on the score's own playback, the pedals of the later set (fuzz, flanger, phaser, tremolo, wah, octave, equalizer, pitch shifter), the sound outside the practice window.
