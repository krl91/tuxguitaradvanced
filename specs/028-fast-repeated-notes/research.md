# Research: fast repeated notes recognised

**Spec**: [spec.md](spec.md) | **Date**: 2026-11-11

## R0. Sources read

- `desktop/TuxGuitar-audio-engine/src/main/java/app/tuxguitar/audio/engine/analysis/ArticulationDetector.java`: the attack detector. It needs a spectral-flux peak and a level gate: the mean level 4–13 hops after the transient, minus the **maximum** level 2–19 hops before it, must be at least `NOTE_ATTACK` (4.5 dB). Refractory: 12 hops.
- `AudioEngine.articulationDetector(settings)`: the only detector wired, in the live engine (`getInstance`, `rebuildAnalysis`), in `OfflineAnalysis` and in `SoundCounter`. `AudioEngine.fastArticulationDetector` exists (spec 002 T048: level before taken over hops 2–8, refractory 6) but nothing calls it outside tests. The native engine takes the same `ONSET_*` values through `NativeParameters`; `withFastOnset` is used by parity tests only.
- `specs/002-practice-industrialization/research.md` lines 320–321: "synthetic equal re-plucks on a ringing string rise only 2–4 dB, so the MVP gate (4.5 dB) finds 1–2 of 6 repeats at 6–12 notes/s"; the fast detector found 4 false attacks on 17 real single-pluck clips, so "the fast detector is NOT the default … identical fast repeats stay a documented limit". The recordings that were to settle it (T053) were deferred by the owner. The owner's DI recording is that recording.
- `desktop/TuxGuitar-practice/src/.../session/FollowSession.java`: `process` turns each `frame.isOnset()` into a `PendingOnset` (`registerOnset`) whose candidates are the open events whose window holds the compensated transient; the pitch is then judged on the frames after it. An event left without any attack in its window is `MISSED` with the reason `NO_ATTACK` (`updateReason`). Legato arrivals (`legatoArrivals`) match a new pitch without an attack, which is why the dyads and the pitch changes are found and the repeats are not.
- `arcade/core/ArcadeStarter.java`: Arcade runs on `FollowStarter.prepare` and so on `FollowSession`, with its own window width (`ArcadeLeniency`, spec 014). One change in `FollowSession` serves both modes.
- `audio/engine/analysis/SpectrumFrame.java`: each frame of both engines carries the level of every semitone band (`bandDb`), already used in Java by the chord profile of spec 024. Hop: 256 samples (5.33 ms at 48 kHz; 3,796 frames for the 20.28 s recording).
- `audio/engine/pitch/TolerancePolicy.java`, `RecognitionParameters.NOTE_ACCEPT` (35 cents, 10–45), `NOTE_FRET_SHARP` (0.7 cent per fret, up to `DEFAULT_MAX_SHARP_CENTS` 49), `FollowLeniency` (Permissive and Very permissive both at 35: "40 loses a right note"): the out-of-tune acceptance FR-007b keeps.
- `ui/PracticePalette.java` (the line of the per-note feedback, `practice.feedback.out-of-tune`) and `ui/FollowPanel.precisionText` ("never a block"): where a non-blocking line already lives. `arcade/ui/ArcadeSceneLabels.java`: Arcade's texts on the scene.
- `session/PracticeSession.java` (Wait mode): `isPitchChange` lets a new pitch in without an attack, but "the same pitch, the first note and anything after a skip … still need a fresh attack" (line 685): the same limit in Wait mode. `functional/FastRunFunctionalTest` (default suite) measures `REPEATED` runs (57, 57, 57, 57) at 3–12 notes per second through it, with floors only ("the floor that work … on identical repeats … must not go under").
- `arcade/core/ArcadeLevel.java`, `session/ArcadeChoices.java`: Arcade's default level is `LEVEL_1` (30 % of the notes, 0 % of the chords); `ArcadeLevelFilter` judges only the kept sounds, the others are played in the backing.
- `arcade/view/SceneLabels.java` (an interface; the committed `SceneFixture.ENGLISH` implements it anonymously) and `arcade/ui/ArcadeRenderer.java`: Arcade has no message area during the run (hence the window the owner chose). `ui/PracticePalette.showFollowNotice(String)`: Follow's non-blocking notice line.
- `FollowSession.FollowParameters(RecognitionSettings)`: where Follow reads its values; `PracticeParameters`: Wait's.
- Committed tests that constrain new settings: `RecognitionSectionsTest` (the first eight sections and their keys are pinned, `NOTES` already holds its 7, every section holds 1 to 7, a device profile never overlays a new section), `EngineDocumentationTest` and `EngineDocumentationFactsTest` (every key has a row in `docs/architecture/recognition-engines.md` under its section, with unit, default, bounds and when it takes effect), `RecognitionStringsAllLanguagesTest` and `RecognitionSettingsPanelTest` (name, tip and section name in three languages, translated), `RecognitionControlsTest.theHelpPageDocumentsEverySetting…` (a row per setting and an `<h2>` per section in `desktop/TuxGuitar/share/help/tools_audio_recognition.html` and `_fr.html`), `SessionConstantInventoryTest` (every numeric `static final` of `PR/session` and `PR/arcade/core` is a parameter or a fixed row of `specs/026-engine-external-parameters/inventory.md`).
- In `TuxGuitar-practice`, only `swt-gui` is excluded by default (`pom.xml`): the `dataset` tests run in every build and skip themselves without their data.
- Committed tests that pin the current detector: `OnsetParametersTest.theDefaultsAreTheFormerConstants` and `theDetectorsAndTheNativeArrayOfTheDefaultsAreThoseOfBefore` (every `ONSET_*` and `ONSET_FAST_*` default), `RecognitionSettingsTest` (`NOTE_ATTACK` = 4.5), `RecognitionSettingsPanelTest` ("recommended: 4.5 dB").
- The offset probe of 2026-11-11 (`OffsetReplayProbeTest`, untracked): 24 onsets for 100 events on the DI recording, the first ones at 0.384, 0.688, 1.152, 1.456 s while the targets fall every 0.153 s; same onsets with both engines.

## R1. Why the repeats are missed

**Finding:** the attack detector does not report the repeated notes. Its gate compares the level after the transient with the *maximum* of the 100 ms before it, which, 153 ms after the previous pluck of the same palm-muted string, still holds that pluck's peak: the new pluck rises less than 4.5 dB above it. This is the limit spec 002 documented. With no onset, `FollowSession` has nothing to judge, and the note is `NO_ATTACK`.

**To confirm in the first phase** (probe, per event): the level rise of each missed repeat, its flux, and whether the band of the expected pitch rises. The probe also explains the loss with lateness (51 % on time, 30 % at 70 ms): an onset found late may fall in the window of two events and go to the wrong one.

## R2. Constraint: the current detector stays as it is

**Decision:** neither the defaults of the attack detector (`ONSET_*`, `ONSET_FAST_*`, `NOTE_ATTACK`) nor its rule change. New values are new parameters.

**Rationale:** committed tests pin those defaults (R0); the owner's rule forbids modifying them. Spec 002 also measured that a lower gate or the fast detector everywhere creates false attacks on real single plucks, which would turn a sustained note into a second one (FR-002, SC-004).

**Alternatives rejected:** making the fast detector the default, or lowering `NOTE_ATTACK` (breaks committed tests, false attacks on sustain); wiring `fastArticulationDetector` in the engine (same false attacks, and a change of the native frame records for a second onset flag).

## R3. Method: a re-attack expected by the score

**Decision:** in `FollowSession` (so in Follow and Arcade) and in `PracticeSession` (Wait), with both engines, when an event expects a pitch that is already sounding according to the full score (the previous written event, or a chord still ringing, holds it; in Arcade, before the level filter, since the removed sounds are still played), a **re-attack** of that pitch counts as its attack even without an onset from the detector. A re-attack is found on the frames both engines already give:

- the level of the expected pitch's semitone band (and of its octave above), taken from `SpectrumFrame.bandDb`, rises by at least `reattack.rise` dB above its **minimum** over the `reattack.before` hops before (the release-aware baseline of spec 002, measured on the note itself, not on the whole sound);
- that rise happens within the event's window (the same `Window` as an onset, so the timing and the lateness of FR-003 are judged the same way);
- no detector onset was matched to the event already;
- the event is then judged by its own existing rule: a note by the pitch rule (`NOTE_ACCEPT`, FR-007b), a chord by the chord rule of spec 024;
- a rise of every band at once (a scratch or a click: the overall level and most bands rise together) is not a re-attack of `p` (spec, Edge Cases); the probe measures the guard that tells them apart.

It is searched only where the score says a repeat is expected: a held note gives no rise of its band, so FR-002 holds; a guitar played in the clear (not a repeat) keeps the detector's onsets alone, so no other result moves (FR-006).

**Rationale:** it is score-informed like the chord profile of spec 024, deterministic (constitution VIII), Java-only on data both engines provide (no native or JNI change, the engines judge the same way), and leaves the detector, its tests and its false-attack figures untouched.

**Alternatives considered:**
- The overall level (`frame.getRmsDbfs()`) with a minimum-based baseline: simpler, but the other strings of a chord and the noise move it; kept as the probe's second candidate.
- A second detector in the engine: rejected in R2.

**Stop condition A:** if the probe finds no setting of the re-attack that gives 100 % at every offset of FR-003 with both engines, in Follow and in Arcade at each of its five levels (and in Wait at offset 0), without a new accept on the guard datasets (R6), the work stops and the figures go to the owner (spec, Assumptions).

## R4. Early and late (−70 to +70 ms)

**Decision:** the windows stay those of each mode's defaults (Follow: play time window 600 ms; Arcade: its leniency's width); what changes is **which event an attack goes to**, under the same switch `reattack.enabled` so that the verdicts of before stay reachable. When an attack (an onset or a re-attack) falls in the windows of several open events of the same pitch, it goes to the event whose target is nearest, not the first open one. The probe measures, per offset, which events an attack went to before and after.

**Rationale:** at 153 ms between notes, an attack 70 ms early or late sits inside the windows of two events; given to the earliest open one, every later note of the run shifts by one and ends missed. FR-007a asks to lean towards accepting; the nearest target does so without widening any window.

**To confirm in the first phase:** the cause of the loss with lateness (R1); if it is not the assignment, the decision is revised in the plan before the code.

## R5. The out-of-tune margin and the suggestion to retune

**Decision:** the acceptance in cents stays as it is (FR-007b). A new pure class, `TuningDrift`, receives the cents offset of each single note judged on its pitch (accepted, or refused as out of tune) in Wait, Follow and Arcade, with its written string (`PracticeEvent.getStringNumber()`); chords are not inputs (their judge has no per-note offset), and says when to suggest retuning: the mean offset of the last `tuning.drift-notes` notes is beyond `tuning.drift-threshold` cents (default 20, a **user setting**, bounds 10–35) and at least `tuning.drift-agreement` of them are off the same way. It names the string when those notes are all written on one string. After a suggestion it is silent for `tuning.drift-quiet` notes.

**Display:** Wait and Follow: a new line of `PracticePalette` (`showFollowNotice` holds a single notice, the record not written of spec 016, and Wait's feedback line is rewritten at every event, so neither can hold it). Arcade (owner's answers, 2026-11-11): the run pauses on a modal window with Yes (the application's tuner, `PracticeTunerOpener`, then resume), No (resume), More tolerant (+10 % of the run's pitch acceptance, for this run only, cumulative, never above 45 cents, then resume). `ArcadeController.pause()` and `resume()` (with its count-in) already exist, owned by `ui/PracticeWindow` (line 1021), and `ui/PreRunStartGate` already opens the tuner through `PracticeTunerOpener`; `ConfirmWindow` is the model of a small modal window. Spec 014 kept the acceptance at 35 in Arcade because 40 lost a right note on its reference: the button says what it risks, and the boost is the learner's choice, never a default. The tuner refuses to open while a practice track is set (`tuner-next` `TunerWindow.open`): `PracticeTunerOpener.open()` lifts the track for the time the tuner is open, and `FollowEvents` pauses the session with `TUNER_OPEN` until it closes, so the run resumes only after both (a new `ui/ArcadeRetuneFlow`, tested on fakes). A pure `ArcadeRetuneOffer` holds the states; `FollowSession` gains a setter for the run's acceptance, applied from the next frame. In Wait and Follow nothing pauses (FR-009).

**Settings:** the five drift values (with `tuning.arcade-boost`) go in the new section `TUNING_DRIFT` (the threshold first): `NOTES` is full and pinned by `RecognitionSectionsTest`. Every section is reachable in the settings panel, so the threshold is a user setting there.

**Rationale:** the cents offset is already measured for every judged note; the string comes from the score, so the text says "the string you play it on in the tab", never a string identified from the sound (constitution XIII).

## R6. The guards

- Before any change, the figures of every dataset benchmark (both engines), of the committed recognition tests and of the offset test in both modes, early and late, are written to `benchmark/before-*` (SC-002 baseline, and SC-001's Arcade and early figures, which were never measured).
- After: the same, and a comparison; a guard figure lower than before stops the work (stop condition B).
- A held note expected twice (SC-004) and two plucks 100 ms apart (SC-005) are synthetic unit tests with `FakeAnalysis`-style frames, plus the probe on the DI recording.

## R7. The offset test kept for every version

**Decision:** `OwnerOffsetTest` (new, committed, tagged `owner-offset`, not excluded: like the `dataset` tests it skips itself without its data) is the cleaned probe: it reads `-Dtuxguitar.owner.offset=<folder>[,<folder>…]` (a GP5 tablature and a WAV, the track chosen by a property), and is skipped (`Assumptions.assumeTrue`) when the property or the files are absent, so CI and other builds skip it. It aligns on the sound's own energy rises (as the probe does), replays Follow and Arcade with their defaults, both engines, offsets −100 to +100 ms, writes the table (mode, engine, offset, recognised, refusals by reason) to `-Dtuxguitar.owner.offset.report`, and fails when an event is missed between −70 and +70 ms.

The switch `-Dtuxguitar.owner.offset.reattack=off` (read by the test itself, which builds the settings) replays the verdicts of before, to prove the test fails. `misc/owner-offset-check.sh <report> <folder>…` runs it from the macOS build directory; `docs/VERSIONING.md` gains a "Before a release" step: run it on the owner's machine and copy the table into the release notes. The recording itself is never committed. The release workflow (`.github/workflows/release.yml`) refuses a tag whose `docs/release-notes/<tag>.md` lacks the offset table or holds a row under 100 % between −70 and +70 ms, so that FR-005's "MUST NOT be published" does not rest on memory.
