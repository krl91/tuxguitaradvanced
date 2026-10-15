# Feature Specification: Per-device calibration of the note and chord recognition

**Feature Branch**: `feature/interactive-practice` (the spec directory is `013-device-recognition-calibration`)

**Created**: 2026-10-15

**Status**: Draft

**Input**: User description: "Calibrate the recognition of notes and chords for each input device: the player plays a short piece, it is recorded and analysed, the best settings are found and stored as a profile for that device; a coloured dot in the Input tab shows the state of the profile" (owner, 2026-10-15)

## Why this exists

The owner wants better **note-by-note recognition with the computer's built-in microphone**. A microphone does not hear a guitar like the Rocksmith adapter does, and today there is **one set of recognition settings for every device** (the note acceptance in cents, the minimum certainty, the time to confirm a note, the attack sensitivity, the legato mode, the chord settings, the level thresholds...). It is edited by hand in the Configuration window and applies to whatever input is selected. The owner's idea: instead of tuning by hand, **the player plays a short known piece once, it is recorded, and the system searches, offline and with as much time as it needs, for the settings that recognise that piece best on that device**, then keeps them as the **profile of the device**.

What exists today (inspected 2026-10-15): the settings are one global immutable set saved by the recognition store; the latency calibration of the audio route is already stored **per route**; the input is chosen in the *Input* tab of the preparation window; the start window of spec 010 asks "is your guitar in tune?"; the engine can analyse a recording offline with the real analyser, and a judge can say whether a session accepts a recording. The weaknesses with the built-in microphone and with chords are measured (specs 004 and 010).

## Clarifications

### Session 2026-10-15

- Q: Which piece is played to calibrate? → A: **`basic_test.tg`**, the owner's own short score ("perfect, to be reused and put on GitHub"): it becomes the committed **calibration piece for the guitar**. A **bass** piece is a separate later piece of work and is **set aside**; this feature is about the **normal guitar** only.
- Q: What happens to the recording? → A: It is **kept only until the analysis is done, then deleted**; it never leaves the computer. (A cancelled or failed calibration also deletes it.)
- Q: What happens when the settings found are not better than the default (the "red" case)? → A: The player is **shown a message**: the **default settings are the most optimal**; they are asked **whether they still want to use the settings found, which look worse, or record again, or use the default ones** (three choices).
- Q: Who is offered *Calibrate again* / *Use the default configuration*? → A: **Every device**, calibrated or not (the owner's wording: "when a device is to be calibrated"): pressing the button always first offers the two choices; *Calibrate again* (for an uncalibrated device: *Calibrate*) goes on to the tuning question, *Use the default configuration* saves the default as the profile of that device (orange) with nothing recorded.
- Q: What happens when the listening ends with a number of sounds different from the piece's (one too many, or some missing)? → A: The recording is **refused with the reason** ("14 sounds heard, 13 expected") and **no analysis is tried** (no guessing of an alignment). **At every refusal** (wrong number of sounds, silent, too short, too noisy, clipped, timeout) the player is always offered **three choices**: *record again*, *use the default configuration* (saved as the device's profile, orange, nothing recorded kept) or *stop the calibration* (nothing saved, the previous profile unchanged).
- Q: Can the player listen to the recording before saying it is good enough? → A: **No replay.** The quality question shows **measures** instead: the sounds heard out of the sounds expected, the highest level, the background noise, and whether the signal clipped. (A replay can be added later without changing the rest.)
- Q: During the offline search, may the player keep using the application? → A: **No**: the calibration window stays open and **blocks the rest of the application** (modal) with a progress bar and a *Cancel* button; closing the window cancels the search and saves nothing. The search lasts at most about 2 minutes.
- Q: What does the dot show when the noise reduction or the input gain is no longer what it was at the recording? → A: The dot **keeps its colour** (green or red) and a **warning sentence** is shown next to it (for example "calibrated with noise reduction on"); the profile **stays in use**. (A different sample rate is not the same case: the profile is not applied and the dot is orange with the reason.)
- Q: What does the dot show when no input is selected? → A: A **grey** dot with the text "No input selected", and the *Calibrate* button is **disabled**. (A fourth state, only when there is no input.)
- Q: What does the result screen show when the profile found is better than the default? → A: The recognition rate **with the default and with the profile**, **in total and separately for the notes and the chords** (for example "notes 62 % → 85 %, chords 40 % → 60 %, total 58 % → 77 %"), and the sentence "saved for this device". The list of the settings changed is **not** in the window (it is in the technical report of the work).
- Q: Can the player forget the profile of a device? → A: **Yes**: a button *Forget the calibration* next to *Calibrate the recognition*, enabled only when the selected device has a profile; after a yes/no confirmation it **deletes** the profile (back to never calibrated, orange). (The confirmation is the usual guard for a deletion; say so if it is not wanted.)
- Q: Does the profile apply everywhere the input is used, or only in the practice modes? → A: **Everywhere**: the profile of the selected device applies to the tuner window and to Follow, Arcade and Wait alike (they read the same settings). The tuner's own settings (tolerance, hold, confidence) are not part of a profile and do not change.
- Q: When does a new profile apply if the input changes during an exercise? → A: **No special handling**: a change of input takes effect as it does today (the exercise stops if the input disappears) and the profile of the new input applies from that input on.
- Q: One window or several for the steps of the calibration? → A: **One window** whose content changes from step to step (the tuning question, the advice, the piece, the listening, the quality question, the analysis, the result) instead of a new window at each step. The owner accepts it **on condition that it is tried**: the one-window design is judged in the owner's trial and, if it does not feel right, the steps go back to separate windows.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - The player calibrates the recognition for a device (Priority: P1)

In the *Input* tab, next to the device chooser, the player presses **Calibrate the recognition**. The application asks whether the guitar is in tune (no opens the tuner and goes no further), tells the player to play as well as possible and, for a microphone, to find the quietest place, shows the piece to play (with no tempo, to be played carefully because it is recorded), then listens: it detects that a note or a chord was played, without saying which, records, and stops by itself when the number of sounds of the piece seems to have been heard. The player is asked whether the recording is good enough; if yes, it is analysed offline and the best settings found are saved as the profile of that device.

**Why this priority**: it is the whole request, and the way the owner will improve the built-in microphone.

**Independent Test**: calibrate a device with a recorded performance of the piece (no person, no guitar) and check that a profile is saved for it and that its recognition rate on that recording is reported.

**Acceptance Scenarios**:

1. **Given** the Input tab with a device selected, **When** the player presses *Calibrate the recognition* and answers *No* to the tuning question, **Then** the tuner opens and the calibration does not go on.
2. **Given** the tuning answered *Yes*, **When** the windows of advice and of the piece are shown, **Then** the advice mentions a quiet place if the input is a microphone, and the piece is shown with no tempo.
3. **Given** the listening mode, **When** the number of sounds of the piece has been detected, **Then** it stops by itself and asks whether the recording is good enough.
4. **Given** the player says the recording is not good enough, **When** they answer, **Then** nothing is analysed or saved and they may record again.
5. **Given** the player says it is good enough, **When** the analysis ends, **Then** the best settings found are stored as the profile of the device and the recognition rate obtained on the recording is shown.

---

### User Story 2 - Each device has its profile, shown and reused (Priority: P1)

Each calibrated device keeps its **own** profile, in a library with one entry per device. The **dot** next to the device chooser shows the state of the selected device and **changes every time the input changes**: **green** (calibrated, its profile is loaded), **orange** (not calibrated: the default configuration is used), **red** (calibrated, but the recognition measured during the analysis was **below 60 %**). The profile is **really saved** and **really used**: after a restart, selecting the device loads it again.

**Why this priority**: without reuse the calibration is worthless; the owner explicitly asked for a visible proof that the profile is saved and loaded.

**Independent Test**: calibrate two devices, restart, switch between them and an uncalibrated one, and check the dot and the settings in use each time.

**Acceptance Scenarios**:

1. **Given** a calibrated device, **When** it is selected, **Then** the dot is green and the recognition uses its profile.
2. **Given** a device never calibrated, **When** it is selected, **Then** the dot is orange and the recognition uses the default configuration exactly as today.
3. **Given** a device whose calibration measured under 60 %, **When** it is selected, **Then** the dot is red.
4. **Given** the application is closed and opened again, **When** a calibrated device is selected, **Then** its profile is loaded again, identical to what was saved.
5. **Given** the input changes during practice preparation, **When** another device is selected, **Then** the dot and the settings in use change with it, at once.

---

### User Story 3 - Calibrate again, or keep the default (Priority: P2)

When a device is to be calibrated, the player is offered either to **calibrate again** or to **use the default configuration**. Choosing the default makes **no recording**, and the default configuration becomes the profile of that device (the dot shows it).

**Why this priority**: a calibration that does not help must not trap the player; they need a clean way out.

**Independent Test**: for a device with a red dot, choose "use the default configuration" and check that nothing was recorded and that the device now uses the default.

**Acceptance Scenarios**:

1. **Given** a device with a profile (or a red dot), **When** the player presses *Calibrate*, **Then** they are offered *Calibrate again* or *Use the default configuration*.
2. **Given** *Use the default configuration*, **When** it is chosen, **Then** no recording is made and the device's profile is the default.
3. **Given** *Calibrate again*, **When** it is chosen, **Then** the calibration of Story 1 runs and replaces the profile only if it finishes.

---

### User Story 4 - Nothing changes without a calibration (Priority: P2)

A player who never calibrates sees **no change**: the default configuration is used exactly as today, the Configuration window still edits the default configuration, and the recognition figures recorded before this feature stay equal. A profile replaces the default **only for its device**.

**Why this priority**: the recognition is the core of the product; a feature that could degrade it for everyone must be provably inert for those who do not use it.

**Independent Test**: run the recorded recognition suites with no profile and compare with the figures of spec 010: identical.

**Acceptance Scenarios**:

1. **Given** no profile exists, **When** the recorded suites run, **Then** every figure equals the one before this feature.
2. **Given** a profile for device A, **When** device B (uncalibrated) is used, **Then** B uses the default configuration, not A's profile.
3. **Given** the default configuration edited in the Configuration window, **When** an uncalibrated device is used, **Then** it uses the edited default.

---

### User Story 5 - Stopping, failing and trying again lose nothing (Priority: P3)

The player can cancel at every step; a recording that is too short, too noisy, silent or clipped is refused with a reason; an analysis that cannot improve on the default says so and keeps the default; the profile that already existed is never lost by a calibration that did not finish.

**Why this priority**: calibration asks effort of the player; a failure must be recoverable.

**Independent Test**: cancel at each step and feed a silent, a noisy and a clipped recording; check the previous profile is intact and the reason is shown.

**Acceptance Scenarios**:

1. **Given** any step, **When** the player cancels, **Then** the previous profile of the device is unchanged.
2. **Given** a silent, too short, too noisy or clipped recording, or a wrong number of sounds, **When** the listening ends or times out, **Then** the reason is shown and the player may **record again, use the default configuration, or stop the calibration**.
3. **Given** an analysis whose best result is not better than the default, **When** it ends, **Then** the player is told and the default configuration is kept for the device.

---

### Edge Cases

- A device unplugged or replaced during the calibration, or during the analysis: the calibration stops, nothing is saved, the previous profile stays.
- The same device offered with several channels or sample rates: the profile must not be applied to a different configuration of the device by mistake.
- Two devices with the same name (two identical adapters): they must not share a profile by mistake.
- A profile saved by a later version, a corrupted profile, a missing file: the device falls back to the default (orange) without an error.
- The player plays too few or too many sounds: the recording is refused with the numbers heard and expected, and the three choices of FR-004 are offered; no analysis is attempted.
- The analysis takes long: progress is shown and it can be cancelled.
- The first start of the application with no library: every device is orange.
- The player changes the default configuration in the Configuration window after calibrating: the profiles of calibrated devices stay as they are; the uncalibrated ones follow the new default.
- The microphone of a laptop used in a noisy room: the advice to find a quiet place is shown before, and a noisy recording is refused.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The *Input* tab MUST offer a **Calibrate the recognition** button next to the device chooser.
- **FR-002**: Pressing it MUST first ask whether the guitar is in tune, reusing the question of the start window: *Yes* goes on, *No* opens the tuner and does not go on.
- **FR-003**: It MUST then show a window telling the player to play as well as possible and, when the input is a microphone, to be in the quietest place possible, and a window showing the piece to play, saying it is played **without tempo** and **carefully because it is recorded**. The steps of the calibration (FR-002 to FR-006) MUST be shown in **one window** whose content changes at each step, showing which step the player is at; the owner's trial decides whether it stays so (see Clarifications).
- **FR-004**: It MUST then run a **listening mode** that detects that a note or a chord was played **without distinguishing which**, records the sound, and **stops by itself** when the number of sounds of the piece seems to have been heard; the player MUST be able to cancel it, and it MUST end on a timeout when nothing or too little is played, with a reason. When the number of sounds heard differs from the piece's, or the recording is silent, too short, too noisy or clipped, the recording MUST be refused with the reason and **no analysis tried**; at **every** refusal the player MUST be offered *record again*, *use the default configuration* and *stop the calibration*.
- **FR-005**: It MUST then ask whether the recording is good enough for the analysis, **showing the measures** (sounds heard out of expected, highest level, background noise, clipping) and **without a replay**; a *No* ends the calibration with nothing saved and allows a new recording.
- **FR-006**: On *Yes*, the recording MUST be **analysed offline**: the system looks for the settings that recognise the notes and the chords of the piece best, using recognisers that may be slower and heavier than in real time, shows progress, can be cancelled, and reports the **recognition rate** obtained: with the default and with the profile, in total and separately for the notes and the chords. While it runs, the calibration window MUST stay open and **block the rest of the application** (modal); closing it cancels the search and saves nothing.
- **FR-007**: The settings found MUST be stored as the **profile of that device**, in a library with **one profile per device**; the profile MUST be saved so that it is **loaded again after a restart** when the device is selected.
- **FR-008**: A **dot** next to the device chooser MUST show the state of the selected device and MUST update **every time the input changes**: **green** (calibrated and its profile loaded), **orange** (not calibrated: the default configuration is used), **red** (calibrated, with a recognition below **60 %** during the analysis). When **no input is selected** the dot is **grey** with the text "No input selected" and the *Calibrate* button is disabled. When the noise reduction or the input gain differs from what they were at the recording, the colour stays and a **warning sentence** is shown next to the dot; the profile stays in use.
- **FR-009**: When a device is to be calibrated (**whether or not it already has a profile**), the player MUST be offered **Calibrate again** or **Use the default configuration**; the second makes **no recording** and sets the default configuration as the device's profile.
- **FR-010**: A profile MUST apply **only to its device**, and **everywhere the input is used** (tuner window, Follow, Arcade, Wait); an uncalibrated device MUST use the default configuration, **exactly as today**; the Configuration window MUST keep editing the default configuration.
- **FR-011**: With **no profile**, every recognition figure recorded before this feature (the reports of spec 010) MUST be unchanged.
- **FR-012**: A calibration that is cancelled, fails or does not finish MUST NOT change or delete the device's existing profile.
- **FR-013**: A missing, unreadable or incompatible profile MUST fall back to the default configuration (orange) without an error.
- **FR-014**: The search MUST include the **default configuration** among its candidates. When the best settings found are **not better than the default** on the calibration recording, or the recognition is **below 60 %**, the application MUST **never decide for the player**: it shows a message saying that **the default settings are the most optimal** and offers **three choices**: **use the settings found anyway** (they are saved as the profile, and the dot is red if the rate is under 60 %), **record again**, or **use the default configuration** (saved as the profile of the device, and nothing else kept).
- **FR-015**: The texts MUST exist in English, French and Spanish and the feature MUST be described in the documentation.
- **FR-016**: The calibration piece for the guitar is the owner's score **`basic_test.tg`**, committed with the application; the way the recognition rate is computed, the list of settings the search may change and their bounds, the time the analysis may take and the key of a profile are written in the plan.
- **FR-017**: The **calibration recording MUST be deleted once the analysis is done**, and also when the calibration is cancelled, refused or fails; it never leaves the computer and no audio of it remains on the disk afterwards.
- **FR-018**: The calibration is for the **guitar**; a bass calibration (with its own piece) is **not part of this feature**.
- **FR-019**: Next to the *Calibrate the recognition* button, a **Forget the calibration** button MUST be enabled only when the selected device has a profile; after a yes/no confirmation it MUST delete that device's profile from the library (the device is then never calibrated, orange, using the default configuration), and no other profile.

### Key Entities

- **Device**: an audio input as the application knows it (its identity, its channel, its sample rate).
- **Recognition profile**: the set of recognition settings chosen for one device, with the date of the calibration and the recognition rate measured when it was made.
- **Profile library**: the saved collection of profiles, one per device.
- **Calibration piece**: the short score the player plays to calibrate.
- **Calibration recording**: the sound recorded during the calibration, used for the analysis.
- **Profile state**: orange (none, default used), green (calibrated, at least 60 %), red (calibrated, below 60 %).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A calibration, from pressing the button to the saved profile, takes the player **under 5 minutes** in the normal case (playing the piece once), excluding the analysis time that is shown and cancellable.
- **SC-002**: **100 % of the profiles saved are loaded again after a restart** and used when their device is selected (checked on several devices).
- **SC-003**: The dot is **correct in 100 % of the checks** for the three states and the no-input case, and **changes at once** when the input changes (green for a calibrated device with at least 60 %, orange for none, red below 60 %).
- **SC-004**: With **no profile**, **all recorded recognition figures are equal** to those of spec 010 (0 differences) and an uncalibrated device uses the default configuration.
- **SC-005**: On the calibration recording itself, the recognition rate with the profile is **never lower than with the default** (FR-014), and the rate obtained is **shown** to the player.
- **SC-006**: On **the owner's own recordings made with the built-in microphone** (not used for the calibration), the recognition rate with a calibrated profile is **measured and reported next to the default's**; **no improvement figure is promised in advance** (constitution XII): the owner's trial decides whether it helped.
- **SC-007**: A cancelled, failed or interrupted calibration leaves the existing profile **unchanged in 100 % of the cases** tried (cancel at each step, silent, noisy and clipped recordings, a device unplugged).
- **SC-008**: Two devices never share a profile by mistake in the cases tried (two devices of the same name, one device with two channels or two rates).
- **SC-009**: The texts exist in **3 of 3 languages**.

## Assumptions

- The three questions of the first draft are answered (see Clarifications): the piece is `basic_test.tg` for the guitar (a bass piece later), the recording is deleted after the analysis, and a result that is not better than the default is shown to the player with three choices.
- The number of sounds of the calibration piece is the number of events of `basic_test.tg` (counted in the plan from the score itself); the listening mode ends when about that many sounds were heard.
- When the player chooses **Use the default configuration**, the profile of the device is the default and its dot is **orange** (the default configuration is in use); a red dot only exists for a profile the player kept although the recognition was under 60 %.
- A profile is keyed by the **device identity and its channel**, and it records the **sample rate** and the front-end state it was made with: it applies only when the capture rate is the same (decided in the plan from the inspected source); two devices with the same name must be told apart if the engine can.
- The **recognition rate** is the share of the events of the piece recognised, for notes and chords together, with the two figures also shown separately.
- The settings the search may change are the **note and chord recognition settings** (acceptance, certainty, confirmation time, attack sensitivity, legato mode, the chord settings, the level thresholds of the input); the **pitch engine**, the tuner settings and the learner's own settings (passages, flames, timing window) **never change**; the list with bounds is in the plan.
- The analysis may take **up to a few minutes**, with progress and a cancel button; no real-time constraint.
- The calibration piece is played **without tempo**: only the order of the sounds matters, not their time.
- The latency calibration of the route (rhythm) is a different thing and is not touched.
- A first validation uses **a recording the owner plays with the built-in microphone** at the start of the work, and the result is reported honestly; the owner's own trial is the final check.
- Out of scope: a shared (cloud) library of profiles, calibration without the player, changing the recognition algorithms, the tuner window itself.
- The work follows Spec Kit in order and is delivered in small phases; tests are written first; the build stays green after each.
