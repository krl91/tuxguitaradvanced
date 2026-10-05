# Feature Specification: Polyphonic recognition by attack-difference spectrum

**Feature Branch**: `feature/interactive-practice` (spec dir `004-attack-difference-recognition`)

**Created**: 2026-10-05

**Status**: In progress (2026-10-06): recognition delivered and measured (notes 99.8 %, chords with a note missing refused 96-98 %); learner messages for "not separately verifiable" and the speed limit, and the weak chord cases, are open (see tasks.md)

**Input**: User description: "Reconnaissance polyphonique par écart de spectre à l'attaque pour le mode Practice. Sur un enregistrement réel de guitare acoustique en fingerstyle (basse qui sonne sous la mélodie) joué avec la partition `tests/1/_TEST_.tg` (34 notes + 8 accords, 99 bpm), le Practice par défaut ne reconnaît que 2/34 notes et 5/8 accords, avec des fausses acceptations. L'écart de spectre à l'attaque classe la note attendue en 1er dans 27/34 cas et l'accord en 1er dans 5/8. Objectif : reconnaissance de 100 % des 42 événements de ce morceau avec 0 fausse acceptation, dans un test automatique rejouant l'audio (non redistribué, test ignoré s'il est absent), latence annoncée et mesurée, sans enregistrer d'audio, déterministe, honnête sur ce qui n'est pas vérifiable, textes en français et anglais, configuration par défaut déduite des mesures."

## Relation to other specifications

- **Builds on** `001-interactive-practice-mvp` (note and chord judging) and `002-practice-industrialization` (recognition work; research R3 and R6, benchmark `real-recording-1`). It replaces how a played note or chord is *verified*; it does not change the song model, the practice session states, the toolbar or the settings window layout.
- **Evidence**: `specs/002-practice-industrialization/benchmark/real-recording-1/` (event-by-event report of the defaults, rank of the expected note with the static and the attack-difference spectrum).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Recognise every note and chord of a fingerstyle piece (Priority: P1)

A learner practises a fingerstyle piece in which a bass note keeps ringing under the melody. When they play each written note or chord correctly, Practice recognises it; when they play another note or chord, it does not.

**Why this priority**: today most notes of such a piece are never recognised (2 of 34), so Practice is unusable for this common style of playing; this is the reference scenario of the whole feature.

**Independent Test**: replay the reference recording (`tests/1/audio_15ps.m4a`, an acoustic guitar playing `tests/1/_TEST_.tg` at about 99 bpm, 34 notes and 8 chords) through Practice, event by event: all 42 events are recognised and no event is recognised when the learner is expected to play a neighbouring one.

**Acceptance Scenarios**:

1. **Given** the reference recording and score, **When** each of the 42 events is replayed with Practice expecting that event, **Then** all 42 are recognised (100 %).
2. **Given** the same audio, **When** Practice expects a neighbouring event (the two before and the two after, with a different pitch set) instead of the one played, **Then** none is recognised (0 false acceptances).
3. **Given** a note played while a lower string still rings, **When** it is recognised, **Then** the recognition does not depend on the ringing note being absent.
4. **Given** a chord, **When** only part of it is played or a string of it is missing, **Then** it is not recognised, and Practice says which notes were not heard when it can tell.
5. **Given** a note or chord that cannot be told apart from another by audio (same pitch, or an octave of a lower string), **When** the event is judged, **Then** it is labelled as not separately verifiable, never as heard or missed (constitution XIII).

---

### User Story 2 - Keep monophonic playing as good as it is (Priority: P1)

A learner who plays one note at a time on a clean string, as in the existing benchmark clips, sees no regression: the notes recognised before are still recognised, wrong notes are still refused, and the delay before recognition does not grow.

**Why this priority**: most practice is note by note; a gain on fingerstyle must not break it.

**Independent Test**: the existing real-guitar benchmark clips and the practice session tests give the same recognition and no more false acceptances than before.

**Acceptance Scenarios**:

1. **Given** the existing built-in-microphone clips, **When** replayed, **Then** every clip recognised before is still recognised and no wrong note is accepted that was refused before.
2. **Given** a repeated identical note, **When** it is played again, **Then** it still needs a new attack (rule unchanged).
3. **Given** silence, noise or a note still ringing, **When** Practice waits for a note, **Then** nothing advances.

---

### User Story 3 - Honest delay and speed limits (Priority: P2)

The learner is told what the recognition can and cannot follow: the measured delay between playing a note and its recognition, and the speed at which successive notes can no longer be told apart (for example three notes 0.1 s apart).

**Why this priority**: Practice waits for the player, so a limit is acceptable, but it must be stated and measured, not assumed (constitution XII, XIII).

**Independent Test**: the delay (median and 90th percentile) and the shortest note spacing recognised are reported by the test on the reference recording.

**Acceptance Scenarios**:

1. **Given** the reference recording, **When** the test runs, **Then** the median and 90th-percentile delay are reported and stay within the figure announced in the specification's success criteria.
2. **Given** notes closer together than the recognition can separate, **When** they are played, **Then** Practice waits for the learner as usual and the limit is documented, never silently skipped.

---

### Edge Cases

- A note struck on a string that is already sounding the same pitch (repeat on a ringing string): needs a new attack as today; recognition must not accept the ringing note.
- A very soft note under a loud ringing bass: may not be recognised; it must wait, not be reported wrong.
- Effects on the recorded sound (reverb, chorus, delay): the reference recording has some; recognition must still hold.
- The beginning or end of a recording, silence, or a jingle after the music: nothing is recognised from it.
- Notes less than 0.15 s apart: reported as a speed limit, not as errors.
- Tempo different from the written one: recognition does not depend on the written tempo.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Practice MUST recognise a written note or chord from what the audio *adds at the attack* (partials that appear between just before and just after it), so that a note already ringing, such as a bass, does not hide or replace it.
- **FR-002**: On the reference recording, all 42 events MUST be recognised when expected, and none MUST be recognised when a neighbouring event (two before, two after, with a different pitch set) is expected instead.
- **FR-003**: A chord MUST be recognised only when every separately verifiable note of it is heard; a missing note MUST prevent recognition and be named when known.
- **FR-004**: Notes that audio cannot separate (same pitch, octave of a lower note of the chord) MUST be shown as not separately verifiable, never as heard or missing.
- **FR-005**: Recognition MUST keep the existing rules: a new attack is required for each note (including a repeat of the same pitch), nothing advances on silence, noise or a ringing note, and a wrong note is shown only when it is reliably heard.
- **FR-006**: Recognition MUST NOT make any previously recognised monophonic benchmark clip unrecognised, nor accept a note refused before.
- **FR-007**: The delay between a note and its recognition MUST be measured and reported (median and 90th percentile) on the reference recording and on the benchmark clips, and MUST NOT exceed the figures in SC-003.
- **FR-008**: Where recognition cannot follow the playing speed, the limit MUST be measured, documented and shown to the learner as a limit, never as an error of the learner.
- **FR-009**: The result MUST be deterministic: the same audio and settings give the same decisions.
- **FR-010**: No audio MUST be stored; only derived numbers live in memory.
- **FR-011**: The default recognition settings MUST be the ones the measurements select; each parameter that matters to this recognition MUST be a documented setting with a range and a recommended value, or a documented constant with its measured justification.
- **FR-012**: All new learner-facing text MUST exist in French and English.
- **FR-013**: The reference replay MUST be an automatic test that is skipped, with a visible reason, when the audio file is absent; the audio itself MUST NOT be committed.
- **FR-014**: The event times used by the replay MUST come from a documented fixture derived from the recording (method and tolerance stated), not from the recogniser under test.

### Key Entities

- **Attack**: the instant a string is struck, found from the audio; carries the audio just before and just after it.
- **Attack difference**: what the audio gained at an attack, per frequency, used to tell which notes were just played.
- **Reference recording**: the audio and score pair of the test, with its event-time fixture, expected pitches per event and the neighbouring events used as negative cases.
- **Verifiability**: per note of an event, whether audio can establish it separately (verifiable, or not separately verifiable and why).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 42 of 42 events of the reference recording are recognised when expected (100 %), with the default settings.
- **SC-002**: 0 recognitions in the negative tests of the reference recording (neighbouring events expected instead).
- **SC-003**: Median recognition delay of at most 200 ms and 90th percentile of at most 400 ms on the reference recording, reported by the test (provisional targets, revisited with measured figures, never silently changed).
- **SC-004**: No regression on the existing monophonic benchmark and practice tests (all pass; clips recognised before still recognised; no extra false acceptances).
- **SC-005**: The full build with tests passes, and the new replay test runs (not skipped) on the development machine.

## Assumptions

- The reference audio is a clean acoustic recording at about 99 bpm (the written tempo scaled by 1.215, first attack at 0.47 s); event times are fitted on the detected attacks and are approximate (to a few tens of ms).
- Verified is pitch (and chord notes), as before; string and fret are never claimed from audio.
- The single-note f0 detector stays for monophonic use (tuner, fast notes); this feature adds a second way to verify notes and chords and Practice chooses between them by what the audio allows.
- A recognition delay above the current ~170 ms is acceptable for polyphonic playing because Practice waits for the learner (no tempo following in this feature).
- Notes closer than 0.15 s apart are outside the 100 % target and are reported as a speed limit.
- Technique recognition, tempo-following and the Arcade mode are separate specifications.

## Out of Scope

- Playing techniques, timing/tempo following, scoring and the Arcade mode.
- Guitar sound effects or amp simulation.
- Recognition of string and fret from audio.
- Electric guitar with heavy distortion and other recordings; they are measured later with new recordings, not claimed here.
