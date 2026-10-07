# Feature Specification: Interactive Practice Industrialization

**Feature Branch**: `feature/interactive-practice` (continuation of `001-interactive-practice-mvp`)

**Created**: 2026-09-30

**Status**: Draft

**Input**: User description: "Interactive Practice « industrialisé » (suite du MVP, qui fonctionne bien). (1) Reconnaissance des notes nettement améliorée, plus robuste et plus rapide, pour jouer à un tempo plus élevé (faible latence, bonne détection des notes rapprochées). (2) Reconnaissance/évaluation des techniques de jeu : pull-off, hammer-on, bend, vibrato, slide, palm mute, harmonique naturelle, harmonique artificielle, pinch harmonic, tapping, tremolo picking, etc. (3) Meilleure intégration UI : un bouton lançable en un clic dans la barre de lancement/transport, à côté du bouton Play, en conservant l'entrée du menu Outils."


> **Amendments (2026-10-06, decided with the owner)**: (1) the tuner cannot be opened while Interactive Practice is open (it shared the input and the window displayed badly): the "Tune guitar" buttons are removed and the tuner refuses with a message; this replaces the pause-on-tuner-open flow for the case where practice is opened after the tuner only. (2) A one-click start from the Practice button shows no preparation window unless the learner has to act (input unusable or not ready after a short wait). (3) Measure-duration problems of an opened file are listed on demand, no dialog at open (T147). (4) Recognition of notes and chords over ringing strings is specified in spec 004.

## Clarifications

### Session 2026-09-30

- Q1: Technique mismatch → the application tries to detect every supported technique, never blocks progression on a technique mismatch, and uses a deliberately wider tolerance: a borderline result is reported as "uncertain" rather than "not detected". Research (see Assumptions) shows published detectors are mostly rule-based on pitch trajectory, energy envelope and spectrum, and the most robust ones use per-string pickups; with a single audio input, tolerant rules plus an explicit "uncertain" state are the simple, honest option.
- Q2: Tempo → the target experience is that the guitarist plays **at the tempo of the score** and the application follows the notes in time. This adds a tempo-following practice mode beside the existing wait-for-correct-note mode, with an adjustable timing tolerance. Rhythm is therefore no longer out of scope for this feature (see User Story 2b).
- Q3: Technique priorities confirmed. P1 = hammer-on, pull-off, slide, vibrato, bend (hammer-on and pull-off explicitly P1); P2 = palm mute, tremolo picking, natural harmonic; P3 = artificial harmonic, pinch harmonic, tapping.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Start practising in one click from the transport bar (Priority: P1)

A learner with a song open wants to start practising without digging through menus. A Practice button sits in the launch/transport bar right next to Play. One click opens the practice experience for the selected track; the Tools menu entry keeps working and opens the same thing.

**Why this priority**: It is the most visible, lowest-risk improvement and the entry point of everything else.

**Independent Test**: Open a song, click the Practice button next to Play, and confirm the practice window opens ready for the selected track; then do the same via Tools and confirm identical behaviour.

**Acceptance Scenarios**:

1. **Given** a song is open, **When** the learner clicks the Practice button beside Play, **Then** the practice experience opens for the currently selected track in one click.
2. **Given** practice is already open, **When** the learner clicks the button again, **Then** the existing practice window is brought forward (or closed, consistently with its toggle state) and no second instance appears.
3. **Given** the Tools menu, **When** the learner chooses the Practice entry, **Then** it behaves exactly like the button.
4. **Given** practice is active, **When** the learner looks at the button, **Then** it shows an active state that mirrors whether practice is running.
5. **Given** no song or no playable guitar track is available, **When** the learner looks at the button, **Then** it is disabled or explains why nothing can be practised, never failing silently.
6. **Given** the learner presses Play while practice is active, **When** both would drive the song position, **Then** the application resolves the conflict predictably and tells the learner what happened.
7. **Given** the application runs in a non-English language, **When** the button is displayed, **Then** its label and tooltip are localized (at least English and French).

---

### User Story 2 - Keep up when playing fast (Priority: P1)

A learner who is comfortable with a passage wants to play it at a higher speed (fast runs, scale sequences, repeated notes, fast alternate picking) and have practice recognize each note quickly and correctly, without falsely rejecting or missing notes.

**Why this priority**: Fast playing is the main limit of the MVP and the core of the request.

**Independent Test**: Play a recorded set of fast passages (increasing note rates, including repeated identical notes and close neighbouring pitches) and measure recognition rate, false accepts and latency against defined targets.

**Acceptance Scenarios**:

1. **Given** a passage of single notes, **When** the learner plays it at progressively higher speeds, **Then** every correctly played note is recognized and the score advances without the learner having to slow down for the tool.
2. **Given** two consecutive notes of the same pitch, **When** they are picked quickly, **Then** both are counted as separate notes.
3. **Given** two consecutive notes a semitone apart, **When** they are played quickly, **Then** the wrong neighbour is never accepted for the right one.
4. **Given** the learner plays a correct note, **When** recognition completes, **Then** the feedback appears fast enough to feel immediate, noticeably faster than the MVP.
5. **Given** a string still ringing from the previous note, **When** the next note is played, **Then** the previous note does not cause a false detection or block the next one.
6. **Given** noisy or weak input (built-in microphone, low level), **When** the learner plays, **Then** accuracy degrades gracefully and the learner is told when the input is too poor for reliable fast recognition.
7. **Given** chords in the score, **When** they are strummed, **Then** chord recognition keeps working at least as reliably as in the MVP.

---

### User Story 2b - Play at the tempo of the score (Priority: P1)

The learner wants to play along at the written tempo, as with a backing track, and have the application follow their notes in time instead of waiting for each note. A count-in and a tempo reference lead the learner in, the position in the score advances with the tempo, and each expected note is judged (right pitch, right moment within a tolerance) as the music goes by.

**Why this priority**: This is the stated ideal outcome ("jouer au tempo de la partition") and the reason fast recognition matters.

**Independent Test**: Play a passage at the score tempo on benchmark clips with known timing offsets; verify notes inside the tolerance are matched, notes outside are flagged early/late or missed, and the position never gets lost after a mistake.

**Acceptance Scenarios**:

1. **Given** practice in tempo-following mode, **When** the learner starts, **Then** a count-in is given and the score position then advances at the written tempo (or a learner-chosen percentage of it).
2. **Given** an expected note, **When** the learner plays the right pitch within the timing tolerance, **Then** it is marked correct and on time; outside the tolerance it is marked early or late.
3. **Given** a wrong or missing note, **When** the moment passes, **Then** it is marked wrong or missed and the following notes are still judged normally: the application resynchronizes instead of losing its place.
4. **Given** the tolerance setting (strict, normal, lenient, each with documented values), **When** the learner changes it, **Then** subsequent judgement uses it.
5. **Given** the learner wants a lower speed, **When** they set a tempo percentage, **Then** the same judging applies at that speed, so speed can be raised step by step toward 100%.
6. **Given** the learner prefers it, **When** they switch back to wait-for-correct-note mode, **Then** the MVP behaviour is unchanged.
7. **Given** a passage finished, **When** the learner reviews it, **Then** a summary shows correct, early, late, wrong and missed notes, and technique results.

---

### User Story 3 - Recognize legato: hammer-on, pull-off, slide (Priority: P1)

Notes played without a pick attack (hammer-ons, pull-offs, slides) must be recognized as the written notes, and the learner should see whether the technique itself was heard.

**Why this priority**: Legato is ubiquitous in guitar music and was an open question in the MVP (no fresh onset for these notes).

**Independent Test**: Play legato clips (ascending hammer-on, descending pull-off, slide up and down) and verify the notes are accepted and the technique result is reported; play the same notes picked individually and verify the technique is reported as not detected.

**Acceptance Scenarios**:

1. **Given** a written hammer-on, **When** the learner hammers onto the second note without picking, **Then** the second note is accepted and the technique is reported as detected.
2. **Given** a written pull-off, **When** the learner pulls off, **Then** the lower note is accepted and the technique is reported as detected.
3. **Given** a written slide, **When** the learner slides between the two pitches, **Then** the destination note is accepted and the slide is reported as detected; a picked jump between the notes is reported as a different articulation.
4. **Given** the learner picks each note of a written legato pair, **When** the notes are correct, **Then** progression is not blocked and the feedback states that the legato technique was not heard (or uncertain when borderline).

---

### User Story 4 - Recognize expressive pitch techniques: bend, vibrato (Priority: P1)

The learner plays written bends (including pre-bends and release) and vibrato and sees whether the pitch movement matches the score.

**Why this priority**: Bends and vibrato change pitch continuously; without dedicated handling they produce false "wrong note" feedback.

**Independent Test**: Play bend clips (half, whole, release, pre-bend) and vibrato clips (narrow, wide) and verify target pitch, amount and movement are reported and no false wrong-note feedback occurs.

**Acceptance Scenarios**:

1. **Given** a written bend to a target pitch, **When** the learner bends, **Then** the target is recognized, with feedback on whether the bend reached it (under, over or on pitch).
2. **Given** a written release or pre-bend, **When** played, **Then** the pitch path is evaluated accordingly.
3. **Given** a sustained note with vibrato, **When** the pitch oscillates around the target, **Then** the note is accepted as the written pitch, and vibrato presence (and rough width) is reported.
4. **Given** a note without written bend or vibrato, **When** the learner adds them, **Then** this is noted informatively and does not cause a rejection unless the pitch leaves tolerance.

---

### User Story 5 - Recognize muted and rhythmic techniques: palm mute, tremolo picking (Priority: P2)

The learner plays palm-muted notes and tremolo-picked (rapidly repeated) notes and receives feedback about them.

**Why this priority**: Very common in rock/metal practice, but less essential than pitch-based techniques.

**Independent Test**: Play palm-muted versus open notes and tremolo-picked versus single picked notes, and verify the technique is reported correctly.

**Acceptance Scenarios**:

1. **Given** a written palm mute, **When** the learner mutes with the palm, **Then** the pitch is accepted and the mute is reported as detected; open notes are reported as not muted.
2. **Given** a written tremolo-picked note, **When** the learner picks rapidly on it, **Then** the repeated attacks are treated as one note event with tremolo picking detected, not as many errors or many advances.

---

### User Story 6 - Recognize harmonics and tapping (Priority: P2/P3)

The learner plays natural harmonics (P2), artificial harmonics and pinch harmonics, and tapping (P3), and gets the correct expected pitch and technique feedback.

**Why this priority**: Harmonics sound at pitches different from the fretted position and are otherwise misjudged; they are valuable but harder and rarer.

**Independent Test**: Play natural, artificial and pinch harmonics and tapped passages; verify the sounding pitch written in the score is accepted and the technique result reported.

**Acceptance Scenarios**:

1. **Given** a written natural harmonic, **When** played at the harmonic node, **Then** the sounding pitch is accepted and the technique reported as detected; a normally fretted note at that position is reported as not a harmonic.
2. **Given** a written artificial or pinch harmonic, **When** played, **Then** the sounding pitch is accepted and the technique reported with a clearly stated confidence level.
3. **Given** a written tapped passage, **When** played, **Then** notes are recognized and the technique reported, including rapid taps without a pick attack.
4. **Given** a technique that cannot be reliably told from the audio alone, **When** feedback is shown, **Then** the screen says "not verifiable" rather than claiming it was checked.

---

### User Story 7 - Understand technique feedback honestly (Priority: P1)

For each event the learner sees, separately: the pitch result, and for each written technique one of detected / not detected / not verifiable, so nothing is claimed that was not actually checked.

**Why this priority**: The project rule is never to claim verification that did not happen; technique detection is inherently less certain than pitch.

**Independent Test**: Run practice on a score with every supported technique and confirm each technique shows exactly one of the three states, consistent with the audio.

**Acceptance Scenarios**:

1. **Given** a note with a written technique, **When** it is played, **Then** the pitch result and technique result are displayed independently.
2. **Given** a technique whose detector has low confidence, **When** shown, **Then** it is displayed as uncertain rather than as success or failure.
3. **Given** a technique the application cannot recognize at all, **When** it appears in the score, **Then** it is displayed as not verifiable.
4. **Given** a finished passage, **When** the learner reviews it, **Then** a summary lists per technique how many were detected, missed, or unverifiable.

---

### User Story 8 - Boost and clean the input signal (Priority: P1)

A learner whose guitar signal is weak or noisy (passive pickup on a low-gain input, built-in microphone, a humming room) wants to raise the level inside the application and cut background noise, in the tuner as well as in practice, without leaving the window or changing the operating-system settings.

**Why this priority**: recognition speed and accuracy depend on a usable signal; the MVP could only advise the learner to change the hardware gain.

**Independent Test**: with a deliberately weak and noisy recording (or a quiet real input), raise the gain and switch noise reduction on and off; the level indicator and recognition results change as described below, and gain returns to neutral after an input change or restart.

**Acceptance Scenarios**:

1. **Given** a weak input flagged as too low, **When** the learner raises the gain, **Then** the level indication follows the boosted signal and recognition uses it.
2. **Given** the tuner and the practice window, **When** the learner sets the gain in one of them, **Then** the same gain applies to both (one shared input).
3. **Given** a set gain, **When** the input device changes or the application restarts, **Then** the gain is back to neutral and nothing was saved.
4. **Given** background noise between notes, **When** noise reduction is on, **Then** the noise does not produce false notes or false attacks, and played notes and their decay are not cut.
5. **Given** noise reduction is switched off, **When** the learner plays, **Then** behaviour is identical to the MVP.
6. **Given** a boost that mostly amplifies noise or causes clipping, **When** the level is shown, **Then** the learner gets a warning and the advice to raise the gain on the interface or use a direct input.
7. **Given** the suggestion feature is on, **When** the learner asks for a suggested gain after playing a few notes, **Then** a value is proposed and applied only after confirmation; with the feature off, no suggestion is offered.

---

### Edge Cases

- Fast passages mixing different techniques (hammer-on followed by a slide, then a bend).
- Bend or vibrato that drifts past the tolerance of the neighbouring semitone.
- Harmonics whose sounding pitch is an octave or more above the written fret; very weak harmonic signal.
- Palm-muted notes that are quiet or have very short sustain; low-level input hiding the onset.
- Tremolo picking at rates where individual attacks blur into a continuous tone.
- Hammer-on or pull-off with no audible energy change, where the only evidence is a pitch change.
- Two identical notes at high speed while the first is still ringing.
- Sympathetic string ringing and open-string noise during fast play.
- Alternate tunings, capo and tremolo-bar (dive) pitch changes not written as bends.
- Practice button pressed while the song is playing, while the tuner is open, or with no audio input configured.
- Button state after closing the practice window by other means (window close, plugin unload, song change).
- A toolbar with limited width or a different UI toolkit/skin where the button may not fit.

## Requirements *(mandatory)*

### Functional Requirements

**Launch integration**

- **FR-001**: The application MUST provide a Practice button in the launch/transport bar immediately next to the Play button, openable in one click.
- **FR-002**: The existing Tools menu entry MUST remain and behave identically to the button.
- **FR-003**: The button MUST reflect the practice state (inactive, active, unavailable) and show a localized tooltip.
- **FR-004**: The button MUST be unavailable, with an explanation, when nothing can be practised; it MUST NOT fail silently.
- **FR-005**: Opening practice twice MUST never create two instances.
- **FR-006**: The application MUST resolve the interaction between normal playback and practice predictably and communicate it to the learner.
- **FR-007**: Adding the button MUST NOT change the behaviour of any existing transport control.

**Fast, robust note recognition**

- **FR-010**: Recognition MUST keep up with the learner's playing speed: every correctly played note in a passage played at the supported maximum note rate MUST be recognized (targets in Success Criteria).
- **FR-011**: Two successive notes of identical pitch MUST be separated as distinct events when played fast.
- **FR-012**: Residual sound from a previous note MUST NOT cause false accepts of the next expected note nor block it.
- **FR-013**: A wrong neighbouring pitch MUST NOT be accepted for the expected pitch at any supported speed.
- **FR-014**: Latency between a note starting and the feedback MUST be lower than in the MVP and within the target in Success Criteria.
- **FR-015**: The application MUST tell the learner when the input quality is too low for reliable fast recognition, and what to do.
- **FR-016**: Existing behaviour (chords, silence handling, level check, tuning/alternate tunings, skip/retry) MUST NOT regress.
- **FR-017**: A reproducible benchmark (synthetic signals as deterministic tests plus real-guitar clips as a separate benchmark, as in the MVP) MUST cover fast passages and every supported technique; acceptance targets are derived from the measured baseline before release.

**Technique recognition**

- **FR-020**: For each note carrying a written technique from the supported list, the application MUST evaluate the technique separately from the pitch and report exactly one of: detected, not detected, uncertain, not verifiable.
- **FR-021**: Hammer-ons, pull-offs and slides MUST be accepted as the written pitch without requiring a new pick attack, and their technique MUST be evaluated.
- **FR-022**: Bends (including pre-bend and release) MUST be evaluated against the written target pitch path and MUST NOT produce false wrong-note results while the pitch is moving toward a written target.
- **FR-023**: Vibrato MUST be recognized (presence and approximate width) and MUST NOT make the sustained written pitch be rejected.
- **FR-024**: Palm mute MUST be evaluated from the sound character of the note and reported independently of pitch.
- **FR-025**: Tremolo picking MUST be treated as one note event with a rapid repeated attack, not as repeated separate events.
- **FR-026**: Natural harmonics, artificial harmonics and pinch harmonics MUST be validated against the written sounding pitch, and the technique reported with its confidence.
- **FR-027**: Tapping MUST be recognized and its notes validated, including notes without a pick attack.
- **FR-028**: The technique set MUST be extensible so that further techniques (e.g. trill, let ring, ghost note) can be added without redesigning the feedback model.
- **FR-029**: A technique MUST be reported as "not verifiable" when it cannot be reliably detected from audio; the application MUST NOT claim it was checked (per project rule on honest verification).
- **FR-030**: A technique mismatch MUST NOT block progression; pitch alone advances. Technique verdict thresholds MUST be tolerant: borderline evidence yields "uncertain", and only clear contrary evidence yields "not detected".
- **FR-031**: Technique results MUST be shown in the practice feedback and in the per-passage summary, and MUST be visually distinguishable from the pitch result.
- **FR-032**: Technique detection MUST be individually switchable off (e.g. for a poor input), falling back to pitch-only behaviour identical to the MVP.

**Input gain and noise reduction**

- **FR-060**: The application MUST let the learner adjust a light software input gain (boost and, slightly, reduction) with a visible value and a reset to neutral. The control is reachable both from the practice level panel and from the tuner, so it can be adjusted while tuning; it acts on the shared audio input, so one setting serves both.
- **FR-061**: The existing level indication (shown in the tuner and the practice level panel), the clipping indication and the level check MUST reflect the signal after the gain, and recognition MUST use the boosted signal. The gain range and step are to be fixed from trials.
- **FR-062**: The application MUST warn when the boost mostly amplifies noise or causes clipping, and suggest raising the gain on the interface or using a direct input.
- **FR-063**: The gain is NOT remembered: it returns to neutral when the input device changes and at each application launch, and is never saved. (Decided 2026-09-30.)
- **FR-064**: The application MUST reduce input noise before analysis using a noise gate driven by an estimate of the background noise, with subtraction of that estimated noise. The learner MUST be able to switch it off easily. The processing MUST NOT make played notes, attacks or technique cues harder to recognize, which the benchmark MUST demonstrate (with versus without) before it is enabled by default. Kalman-type filtering is dropped from this feature. The noise estimate is learned from the level check (silence) and updated when the input is quiet.
- **FR-066**: The application MUST offer a one-click suggested gain, computed from a short sample of the learner playing and proposed for the learner to confirm; it never changes the gain on its own and never adjusts it continuously. The suggestion feature MUST be easy to disable (one switch, off leaving manual gain untouched), and its default state is decided in planning.
- **FR-065**: The added latency of any gain or filtering stage MUST be counted in the latency targets (SC-003, FR-054).

**Tempo-following mode**

- **FR-050**: The application MUST offer a tempo-following practice mode in addition to wait-for-correct-note mode; the learner chooses the mode and the choice is visible.
- **FR-051**: In tempo-following mode the application MUST give a count-in and provide an audible or visual tempo reference, and the position MUST advance at the score tempo scaled by a learner-chosen percentage.
- **FR-052**: Each expected event MUST be judged as correct-on-time, early, late, wrong or missed, using a timing tolerance the learner can select (strict / normal / lenient) with documented values that scale with tempo.
- **FR-053**: After a wrong or missed event the application MUST resynchronize to the score and keep judging subsequent events; it MUST NOT get stuck or lose its place.
- **FR-054**: Latency of the audio chain MUST be accounted for so that a learner playing exactly on the beat is not judged late; the learner MUST be able to calibrate or adjust this offset.
- **FR-057**: Audio inputs MUST be accepted, shown, refreshable and selectable whatever their name or transport (built-in, USB, **Bluetooth**, virtual...), as long as Java Sound exposes a usable capture format. The application MUST NOT filter or refuse an input by its name, its type or a presumed latency. (Decided by the owner on 2026-10-07. "Bluetooth excluded" in the research describes only the conditions of one acoustic benchmark, never a functional restriction.)
- **FR-058**: The timing calibration (FR-054) MUST belong to the pair input + output and to the audio format/profile in use (at least the sample rate and, for Bluetooth, its profile), and MUST be invalidated or asked again when the input, the output, the sample rate or the Bluetooth profile changes. A high or unstable latency MUST give an understandable warning, never a block. The tuner and the wait-for-the-right-note mode MUST stay usable without a precise timing calibration. Every mode that judges rhythm MUST show "reduced rhythmic precision" as long as the route is not correctly calibrated.
- **FR-059**: Calibration sounds MUST be announced clearly before they play, with an approximate duration and a Cancel button, and MUST stop at once on Cancel.
- **FR-055**: Tempo-following mode MUST work with chords and with the techniques of this spec, and with repeats and tempo changes of the score.
- **FR-056**: A per-passage summary MUST show counts of correct, early, late, wrong and missed events and of technique results; it stays in memory only unless persistence is decided later.

**Localization and quality**

- **FR-040**: All new user-facing strings MUST be localized at least in English and French.
- **FR-041**: Everything new MUST keep the desktop application buildable and existing tests passing; new behaviour MUST be covered by automated tests using synthetic signals where feasible.
- **FR-042**: The feature MUST not record or persist the learner's audio.

### Key Entities

- **Technique**: a playing technique attached to a written note (hammer-on, pull-off, slide, bend, vibrato, palm mute, tremolo picking, natural/artificial/pinch harmonic, tapping, extensible).
- **Technique Result**: the verdict for one technique on one event: detected, not detected, uncertain, not verifiable, with a confidence.
- **Note Event Result**: pitch verdict plus zero or more technique results for one expected event.
- **Practice Launcher State**: inactive, active, unavailable (with reason), shown by the transport-bar button.
- **Recognition Benchmark Clip**: a recorded or synthetic signal with its expected notes and techniques, used to measure accuracy and latency.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A learner can open practice for the selected track in one click from the transport bar, and a first-time user finds the button without instructions in a usability check.
- **SC-002**: On the benchmark of single-note fast passages, at least 95% of correctly played notes are recognized at 8 notes per second and at least 90% at 12 notes per second, with false accepts under 1% (targets to be confirmed from measured baseline).
- **SC-003**: From a note starting to feedback displayed is under 150 ms for the median note, improving on the MVP's 300 ms goal, and under 250 ms for the 95th percentile (targets to be confirmed from measured baseline).
- **SC-009**: At the score tempo (for a passage of up to 8 notes per second), at least 95% of correctly played notes are matched to the right score event within the normal tolerance, and the application stays synchronized for the whole passage after a deliberate mistake in at least 95% of benchmark runs (targets provisional).
- **SC-004**: Repeated identical notes at 8 notes per second are separated correctly in at least 95% of cases on the benchmark.
- **SC-005**: For P1 techniques (hammer-on, pull-off, slide, bend, vibrato), at least 85% of benchmark clips receive the correct technique verdict, with a false "detected" rate on plain notes under 10% (targets to be confirmed from measured baseline).
- **SC-006**: For P2/P3 techniques, each is either meeting its documented target or is displayed as "not verifiable"; no technique is ever shown as verified without a benchmark backing it.
- **SC-007**: No regression: all MVP benchmark and test results remain at or above their previous values.
- **SC-008**: Learners running a practice session with techniques enabled report that feedback is clearer than pitch-only feedback in a qualitative check with at least 3 guitarists.

## Assumptions

- Target platform remains macOS on Apple Silicon with the SWT desktop build; other platforms and toolkits must not break but are not validated.
- Wait-for-correct-note remains available; tempo-following mode (new) adds timing judgement, which the MVP excluded.
- Technique detection is audio-only (no extra sensors, hexaphonic pickups or MIDI guitars); techniques that audio cannot separate are reported as not verifiable.
- Research basis (web survey, 2026-09-30): published guitar-technique detectors (e.g. NIME 2012 "Left and right-hand guitar playing techniques detection", Stefani/Turchet technique classifiers, ISMIR tablature transcription work) mostly use rules or classifiers on pitch trajectory, energy envelope and spectral features, and often rely on hexaphonic pickups for robustness. Planning must evaluate rule-based detectors on the single audio input before considering anything heavier; machine learning stays out of scope unless the user approves it.
- Direct electric-guitar input through an audio interface (instrument/Hi-Z input) is the recommended source: cleaner and louder than a room microphone, so it improves note and technique recognition; it is still one mixed signal for all six strings, not one per string.
- Techniques are taken from the effects already stored in the TuxGuitar song model; techniques not representable in the score are out of scope.
- The 6-string guitar is the only supported instrument; machine learning and cloud services stay out of scope unless planning justifies otherwise with the user.
- Real-guitar benchmark clips for the new techniques will be recorded by the project owner under the same CC0 conditions as in the MVP.
- Only the project-owner's setup is assumed for accuracy targets; numbers in Success Criteria are provisional until measured.
- Dependencies: the MVP audio engine, tuner and practice modules (spec `001-interactive-practice-mvp`).
