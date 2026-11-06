# Feature Specification: GuitarFX, a standalone amp and effect pedals for the guitar, without TuxGuitar

**Feature Branch**: `feature/interactive-practice` (no branch of its own; spec directory `specs/027-standalone-amp`)

**Created**: 2026-11-06

**Status**: Draft

**Input**: User description: "spec 027 : un outil autonome (hors TuxGuitar) qui transforme l'ordinateur en ampli et pédales d'effets pour guitare, option B : les effets de la spec 022 (noise gate, compresseur, overdrive, distorsion, ampli clean/crunch/high gain avec égaliseur 3 bandes, baffle, chorus, écho, réverb) réécrits en Rust et appliqués directement dans le traitement audio temps réel (cpal, déjà utilisé par le crate tuxguitar-monitor-native), sans Java ni JVM ; un petit exécutable par système, macOS, Linux et Windows à parité ; les sons prêts à l'emploi et les sons de l'utilisateur ; latence plus faible qu'aujourd'hui visée (à mesurer, pas promise) ; les effets Rust donnent la même sortie que les effets Java sur les mêmes signaux de test ; pas de code GPL ; pas d'allocation, de verrou ni d'attente dans le traitement temps réel ; paramètres externes, aucune valeur réglée en dur (règle du propriétaire)."

**Where it comes from**: spec 022 gave TuxGuitarAdvanced an amp and effect pedals inside the practice window. Today the sound only exists while TuxGuitarAdvanced runs with its practice window open, and each block of sound goes from the native audio device to the effects and back. On 2026-11-06 the owner chose option B among the two ways of a standalone tool: the same amp and pedals, rewritten in the native audio part and applied right where the sound is received and sent, in a small program of its own per system. The owner's wish is a "pocket amp": plug the guitar into the computer, open the tool, play.

**Note on the request's technical words**: the owner's request names the language and the audio library. They are recorded here as constraints of the request (see Assumptions); the requirements below say what the learner gets, and the plan says how.

## Clarifications

### Session 2026-11-06

- Q: In which music software must the plugin load? → A: **VST3, CLAP and Audio Unit** (option C): GarageBand and Logic on macOS as well as Reaper, Bitwig, Ableton, Cubase, Studio One…; and **SoundCraft** (github.com/storytold/soundcraft, an open-source music program in Rust that loads CLAP, VST3 and Audio Unit) is one of the programs the plugin is tested in.
- Q: What is the tool called, for the application, the plugin and the packages? → A: **GuitarFX**.
- Q: Where does the guitarist set the internal values of the effects that are not knobs? → A: **In an *Advanced* window of the application** (option B), by categories, each value with its bounds, its default and a tooltip, with a *Defaults* button; the settings file stays readable by hand; the plugin reads the same values and has no *Advanced* page of its own.
- Q: Does GuitarFX have a tuner in this spec? → A: **No**; a later spec adds it (the application and the plugin stay without a tuner here).
- Q: How is GuitarFX installed, the plugin included? → A: **One installer per system** (option A): a .pkg on macOS, an installer on Windows, a package or an archive with an install script on Linux; it places the application and the plugins (VST3, CLAP, and Audio Unit on macOS) in the system's standard folders, and a clean uninstall removes them.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - I plug my guitar in and hear it through an amp, without TuxGuitar (Priority: P1)

The guitarist plugs the guitar into the computer (an instrument cable such as the Rocksmith cable, or an audio interface), opens the standalone amp, picks the input and the output, and turns the sound on. They hear their guitar from the headphones or speakers, shaped by a virtual amp (clean, crunch or high gain) and its cabinet, with nothing else to install and without opening TuxGuitarAdvanced.

**Why this priority**: it is the whole point of the tool: a computer becomes an amp. Every other story builds on it.

**Independent Test**: on a computer without Java, install only the tool, plug a guitar, choose the devices, turn the sound on, play: the guitar is heard through the amp, with no noticeable delay and a comfortable level.

**Acceptance Scenarios**:

1. **Given** a computer with no Java installed and only the tool installed, **When** the guitarist opens it, **Then** it starts, lists the inputs and outputs, and the sound is off.
2. **Given** a guitar on an instrument input and the sound turned on, **When** a note is played, **Then** it is heard from the chosen output within the delay of SC-002, shaped by the chosen amp.
3. **Given** the sound is on, **When** the guitarist stops playing, **Then** nothing is heard but silence (FR-011).
4. **Given** the tool was used, **When** it is opened again, **Then** the same input, output, volume and sound are chosen, and the sound is off until turned on.

---

### User Story 2 - The same pedals as in TuxGuitarAdvanced, sounding the same (Priority: P1)

Before and after the amp, the guitarist adds pedals from the same set as spec 022 (noise gate, compressor, overdrive, distortion, chorus, echo, reverb), switches them, reorders them and turns their knobs. Every change is heard at once, without a click. For the same settings and the same guitar signal, the tool sounds the same as the *Amp and effects* window of TuxGuitarAdvanced.

**Why this priority**: an amp without pedals is only a monitor; and the owner asked that the rewritten effects give the same output as the existing ones, so a sound learnt in one is the same sound in the other.

**Independent Test**: feed the same recorded and synthetic test signals to every element of the chain in the tool and in TuxGuitarAdvanced, with the same knob values: the outputs are the same within the tolerance of SC-003. Then play live and move every knob: each change is heard within 0.1 second without a click.

**Acceptance Scenarios**:

1. **Given** the sound is on, **When** the guitarist adds, removes, moves or switches a pedal, or moves a knob, **Then** the change is heard within 0.1 second, with no crackle, gap or jump in level.
2. **Given** each pedal kind, the amp in each character and the cabinet, **When** the test signals of spec 022 go through them with the same knob values, **Then** the tool's output equals TuxGuitarAdvanced's within SC-003.
3. **Given** an echo or a reverb, **When** the guitarist stops playing, **Then** the tail dies away naturally.
4. **Given** any knob position, **When** the guitarist plays, **Then** the output never exceeds the safe ceiling (FR-010).

---

### User Story 3 - Ready-made sounds and my own sounds (Priority: P2)

The guitarist finds the four ready-made sounds of spec 022 (clean, crunch, lead, clean with echo), picks one, changes it, restores it, and saves their own chains under a name, renames and deletes them. Everything survives a restart.

**Why this priority**: saving a sound avoids setting the knobs every time; it is comfort on top of US1 and US2.

**Independent Test**: save two sounds, quit, reopen, switch from one to the other: each sounds as saved; change a ready-made sound, restore it: it sounds as shipped.

**Acceptance Scenarios**:

1. **Given** a chain, **When** the guitarist saves it under a name, **Then** it is listed with the ready-made sounds and survives a restart.
2. **Given** a ready-made sound changed by the guitarist, **When** they restore it, **Then** it sounds as shipped.
3. **Given** saved sounds, **When** one is picked, **Then** it is heard at once, without a click.
4. **Given** a sound saved in the tool, **When** the guitarist exports it to a file and imports that file in TuxGuitarAdvanced's *Amp and effects* window, **Then** it is listed there and sounds the same (SC-003); the same works from TuxGuitarAdvanced to the tool.
5. **Given** an imported sound whose name already exists, **When** it is imported, **Then** the existing sound is not overwritten without the guitarist's confirmation.

---

### User Story 4 - Safe, and tunable without rebuilding (Priority: P2)

The sound starts off and rises quietly when turned on. With a built-in microphone the tool warns about feedback and suggests headphones; a howl is cut at once. When the computer cannot keep up, the sound gets simpler rather than crackling. Every internal value of the effects that is not a knob (a filter's corner, a time constant, a safety threshold…) can be changed in the application's *Advanced* window (or its settings file) and is documented, so tuning never needs a rebuild.

**Why this priority**: ears and speakers must be protected; and the owner's standing rule says an engine is tuned by its parameters, never by changing its code.

**Independent Test**: a fresh install is silent; a howl made on purpose with the built-in microphone and speakers is muted within SC-005; changing an internal value in the settings file changes the sound at the next start without rebuilding; every such value is in the documentation.

**Acceptance Scenarios**:

1. **Given** a fresh install, **When** the tool opens, **Then** no sound is played.
2. **Given** the input is a built-in microphone, **When** the sound is turned on, **Then** a warning about feedback is shown with the advice to use headphones.
3. **Given** a feedback howl builds up, **When** it is detected, **Then** the sound is muted within SC-005, a message says why, and the guitarist can turn it on again.
4. **Given** an internal value changed in the *Advanced* window, or in the settings file by hand, within its bounds, **When** the sound is next turned on (the application) or the plugin is next loaded, **Then** it uses that value; a value out of its bounds is brought back into them and the tool starts normally.

---

### User Story 5 - The same amp as a plugin in music software (Priority: P3)

The guitarist who records with music software loads the amp as a plugin on a guitar track, picks a sound or builds a chain, and hears the same amp as in the standalone tool; the project remembers the settings.

**Why this priority**: the owner wants the amp usable while recording, not only to practise; it comes after the standalone tool, which it reuses.

**Independent Test**: load the plugin in a music program on each system, pick a ready-made sound, play through it, save and reopen the project: the same sound is heard; render a test signal through it: it equals the standalone tool's output (SC-003).

**Acceptance Scenarios**:

1. **Given** music software that loads the plugin's format, **When** the guitarist adds it to a track, **Then** it shows the chain, the sounds and the knobs, and the track is heard through the amp.
2. **Given** a project with the plugin set, **When** the project is saved and reopened, **Then** the plugin has the same chain and knobs.
3. **Given** the host runs at any common rate and block size, **When** a test signal is rendered, **Then** the output equals the standalone tool's at that rate within SC-003.

---

### Edge Cases

- **No instrument input, only a built-in microphone**: the tool works but warns (US4).
- **The input or output device disappears** (cable or headphones unplugged): the sound stops without a crash or a hang, a message says so, and the guitarist can pick a device again; the tool never switches by itself to another device (no surprise sound from the speakers).
- **The microphone permission is denied** (macOS and Windows ask for it): a message says how to allow it; the tool stays open.
- **Input and output at different rates or on different devices**: the sound works; the delay shown takes it into account.
- **The input level is too high**: the tool shows clipping and advises lowering the input gain.
- **The computer cannot keep up**: the sound gets simpler (pedals dropped from the end of the chain, then the amp alone) or mutes with a message, rather than crackling.
- **A settings or sounds file that is damaged or from a newer version**: the tool starts with the defaults for what it cannot read, says so, and does not overwrite the damaged file without keeping a copy.
- **Two instances of the tool**: the second one tells that the tool is already open and does not open the audio devices a second time.
- **The tool runs while TuxGuitarAdvanced's amp sound is on**: both may try to play the guitar; the tool does not prevent it, and its guide says to use one at a time.
- **A sound file imported from a newer version, or damaged**: refused with a message (FR-023); nothing already saved is changed.
- **The plugin in a host running offline (faster than real time)**: it renders the same output as in real time.
- **Bluetooth headphones**: they add their own delay; the tool says so beside the delay shown.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001** (standalone): The tool, named **GuitarFX** (the application, the plugin as music software lists it, the packages and the guides), MUST run on its own, without TuxGuitarAdvanced and without Java installed, from one installer per system (macOS, Linux, Windows).
- **FR-026** (installation): Each installer MUST place the application and the plugins (VST3 and CLAP; Audio Unit on macOS) in the system's standard folders, where music software finds them without the guitarist copying anything; a clean uninstall MUST remove everything it installed and keep the guitarist's sounds and settings unless asked to remove them. Installing a newer version over an older one MUST keep the sounds and settings.
- **FR-002** (form): The tool MUST come in two forms built from the same effects: a desktop application with a window of its own, where the guitarist sets the devices, the chain, the sounds and the volumes; and a plugin that music software loads on a guitar track, showing the chain, the sounds and the knobs (the host software owns the devices and the volume).
- **FR-003** (devices): The guitarist MUST be able to choose the input, its channel, and the output among the devices of the system, and refresh the list after plugging a device; the choice MUST survive a restart.
- **FR-004** (amp): The chain MUST contain one amp with a clean, a crunch and a high-gain character, with gain, bass, middle, treble and volume knobs, and a cabinet that can be switched off, as in spec 022.
- **FR-005** (pedals): The guitarist MUST be able to place pedals before and after the amp, from the set of spec 022 (noise gate, compressor, overdrive, distortion, chorus, echo, reverb; each kind at most once), each with its switch and the same knobs, units, ranges and defaults as spec 022.
- **FR-006** (live changes): Any change (a pedal added, removed, moved or switched, a knob moved, a sound picked) MUST be heard within 0.1 second, without a click, a gap or a jump in level.
- **FR-007** (same sound as TuxGuitarAdvanced): For the same settings and the same input signal, every element and the whole chain MUST give the same output as TuxGuitarAdvanced's spec 022 effects within the tolerance of SC-003, checked by an automated comparison on the test signals of spec 022.
- **FR-008** (sounds): The tool MUST ship the four ready-made sounds of spec 022; the guitarist MUST be able to save the chain as a named sound, rename and delete it, pick it, and restore a ready-made sound; a changed ready-made sound shows as changed until restored.
- **FR-009** (kept): The devices, the volume, the current chain and the saved sounds MUST survive a restart; the sound MUST start off at every start.
- **FR-010** (safe level): The output MUST be limited so that no knob combination exceeds a safe ceiling; turning the sound on MUST start quietly and rise in about half a second.
- **FR-011** (silence): With the guitar not played, the noise at the output MUST stay below −60 dBFS at the default volume, for every ready-made sound.
- **FR-012** (feedback): With a built-in microphone, turning the sound on MUST show a feedback warning and the advice to use headphones; a feedback howl MUST be detected and muted with a message (SC-005).
- **FR-013** (devices failing): Losing a device, a denied permission or an unsupported format MUST stop the sound with a clear message, never crash or hang the tool, and never switch to another device by itself.
- **FR-014** (cost): When the computer cannot process the chain in time, the sound MUST get simpler or mute with a message rather than crackle.
- **FR-015** (real-time safety): The processing of each block of sound MUST NOT allocate memory, take a lock or wait; changes made by the guitarist MUST reach it without blocking it (owner's rule, constitution VI).
- **FR-016** (external parameters): Every internal value of the effects, the safety guards and the device handling that changes what is heard (corner frequencies, time constants, thresholds, ceilings, ramp times, block size…) MUST be an external parameter with a default equal to spec 022's value, bounds, a unit and a description, read from the tool's settings at start; no such value is fixed in the code (owner's rule of 2026-11-05). A value out of bounds MUST be brought back into them.
- **FR-025** (*Advanced* window): The application MUST show every external parameter in an *Advanced* window, grouped in categories, each with its name, value, unit, bounds, default and a tooltip in the three languages saying what it changes; a *Defaults* button MUST bring every value back to its default after a confirmation. The window MUST fit a 768-pixel-high screen. A change MUST take effect the next time the sound is turned on, and the window MUST say so. The plugin MUST read the same values from the application's settings on the same computer when it is loaded (defaults when the application was never used) and MUST NOT offer an *Advanced* page of its own.
- **FR-017** (delay shown): The tool MUST show the delay estimated from the devices in use (input, output, block size and processing), named as an estimate.
- **FR-018** (languages): Every label, knob, message and tooltip MUST exist in English, French and Spanish, the tool following the system's language (English otherwise); each tooltip says what the control changes, its unit and its default.
- **FR-019** (documentation): A guide in the three languages MUST describe the installation, the devices, the chain, the sounds, the delay, headphones and feedback; a reference MUST list every external parameter (name, meaning, unit, default, bounds).
- **FR-020** (original and licences): The amp, the pedals and the ready-made sounds MUST be the project's own (as spec 022, FR-018); the tool MUST contain no GPL code, and every third-party component MUST have a licence compatible with the project's distribution.
- **FR-021** (parity): The tool MUST offer the same features on macOS, Linux and Windows and be published for all three in the same release (owner's standing rule); the delay reached on each system is reported, Linux and Windows by testers or stated as unknown.
- **FR-022** (TuxGuitarAdvanced): TuxGuitarAdvanced's amp sound MUST keep its features and sound of spec 022 and its own effects; making it use the rewritten effects, so that only one copy remains, is a later spec. Its only change in this spec is the export and import of a sound (FR-023).
- **FR-023** (sounds as files): The tool, its plugin and TuxGuitarAdvanced's *Amp and effects* window MUST export a sound to a file and import such a file; the file holds the chain and its knob values in a documented format; a file from a newer version or damaged is refused with a message, never half-read.
- **FR-024** (plugin): The plugin MUST be offered in three formats: VST3 and CLAP on the three systems, and Audio Unit on macOS (the only system that has it; the application and the two other formats keep the three systems at parity). It MUST load in music software on the three systems, save its chain and knobs with the host's project and restore them when the project is reopened, and follow the host's rate and block size; it MUST obey FR-005 to FR-008, FR-010, FR-014 to FR-016 and FR-020 like the application.

### Key Entities

- **Device choice**: the input, its channel, the output; kept between sessions.
- **Chain**: the ordered pedals before the amp, the amp with its cabinet, the pedals after it; each element has a kind, an on/off state and its knob values (same meaning as spec 022).
- **Sound**: a named chain, ready-made (restorable, may show as changed) or the guitarist's own.
- **Sound file**: a sound exported to a file, readable by the tool, its plugin and TuxGuitarAdvanced; it carries its format version.
- **Plugin state**: the chain and knobs the host saves with its project.
- **External parameter**: an internal value of the processing with its name, meaning, unit, default (spec 022's value), bounds and the place it is set.
- **Estimated delay**: the delay from input to output on the current devices, shown as an estimate.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: On each of the three systems, the tool installs from its installer, its plugins are found by music software without any copy by hand, it plays the guitar on a computer with no Java installed, and the uninstall leaves none of its files but the guitarist's sounds and settings (macOS checked by the owner; Linux and Windows by CI start-up checks and testers, or stated as unchecked).
- **SC-002**: The delay from a string struck to its sound heard, measured by the loopback probe of spec 022 on the owner's Mac with the same devices and block size, MUST NOT be longer than TuxGuitarAdvanced's measured with the same probe (spec 022: 32.5 ms with the USB microphone into the built-in speakers at 128 frames); a shorter delay is the aim, reported as measured, not promised. The 30 ms ceiling of spec 022 with the Rocksmith cable stays the shipping condition.
- **SC-003**: On the test signals of spec 022 (synthetic and recorded), every element and every ready-made sound gives an output whose difference from TuxGuitarAdvanced's stays below −90 dBFS (inaudible) on every sample.
- **SC-004**: Every change of a knob, a switch or a sound is heard within 0.1 second, and a recording of the output while moving every knob end to end shows no click, gap or level jump above the limit used in spec 022.
- **SC-005**: A feedback howl made on purpose (built-in microphone and speakers) is muted within 0.5 second.
- **SC-006**: During one hour of play with a chain of six active elements, the owner hears no crackle or dropout, and the endurance test reports no dropped block.
- **SC-007**: With every knob at its loudest the output stays under the safe ceiling; a fresh install is silent; the devices, chain and sounds survive a restart.
- **SC-008**: Every internal value listed by the parameter reference is shown in the *Advanced* window, can be changed there or in the settings file, and takes effect the next time the sound is turned on (the plugin: the next time it is loaded) without rebuilding; an automated check finds no value that changes the sound left fixed in the code outside the reference's list of fixed values (each with its reason).
- **SC-009**: The package of the tool (application and plugin) is at most a tenth of the size of TuxGuitarAdvanced's package on the same system.
- **SC-010**: Every label, knob and message has its text and tooltip in the three languages.
- **SC-011**: A sound exported from the tool and imported in TuxGuitarAdvanced, and the reverse, sounds the same within SC-003 (automated round trip on the four ready-made sounds and one sound using every pedal).
- **SC-012**: The plugin loads, plays and restores its settings with a project in SoundCraft on each of the three systems, in each of its formats there (VST3, CLAP, and Audio Unit on macOS), and on macOS also in GarageBand or Logic (Audio Unit) and in one other program loading VST3 or CLAP (macOS checked by the owner; Linux and Windows by testers or stated as unchecked).

## Assumptions

- **Constraints of the request** (owner, 2026-11-06, option B): the effects are rewritten in the language of the native audio part of TuxGuitarAdvanced (Rust) and run in the audio library it already uses (cpal), with no Java and no JVM in the tool. The plan will say how they relate to the existing crate `tuxguitar-monitor-native`.
- The reference for "the same output" is the spec 022 effects as they are in the repository when this spec is planned; the comparison runs the existing Java effects and the new ones on the same signals.
- The usual setup is the one of spec 022: an instrument cable or an audio interface, headphones or speakers.
- The tool lives in this repository, is built by the project's CI for the three systems and published with the TuxGuitarAdvanced releases as separate packages; its name is GuitarFX (owner, 2026-11-06).
- While two copies of the effects exist, a change to one must be made to the other; the comparison of FR-007 catches a difference.
- The development kits of the three plugin formats must have licences compatible with FR-020 (no GPL); the plan checks each one's licence. SoundCraft is pre-alpha: a failure caused by SoundCraft itself is reported, not counted against the plugin, when the plugin works in another program with the same format.
- Out of scope: a tuner (owner, 2026-11-06: a later spec), recording to a file, a looper, the later pedals (fuzz, flanger, phaser, tremolo, wah, octave, equalizer, pitch shifter), impulse responses of real cabinets, MIDI control, the practice features of TuxGuitarAdvanced (recognition, scores, sound per score), Android.
- The microphone permission on macOS and Windows is asked by the system the first time the tool opens the input.
