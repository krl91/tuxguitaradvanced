# Feature Specification: The owner's chords accepted at least 9 times in 10, with a chord leniency the learner can set

**Feature Branch**: `feature/interactive-practice` (no branch of its own; spec directory `specs/024-lenient-chords`)

**Created**: 2026-11-05

**Status**: Draft

**Input**: User description: "spec 024 : les accords du propriétaire joués au câble Rocksmith doivent être acceptés au moins à 90 % (26/42 = 62 % à la fin de la spec 023), sans que les accords voisins acceptés à tort augmentent au-delà d'une limite à fixer (13/42 aujourd'hui) ; l'acceptation des accords doit être plus tolérante par défaut, et cette tolérance réglable dans les paramètres de pratique ; mesuré sur les jeux de données (~/tuxguitar-datasets : accords du propriétaire, GuitarSet, IDMT) avant et après, avec les deux moteurs (natif et Java), sans régression sur les notes seules et les fausses notes ; le plan commence par un diagnostic des 16 accords refusés du propriétaire (specs/023-native-recognition-engine/benchmark/chord-experiments.md)."

**Where it comes from**: spec 023 left chords as the weak point. At its end (2026-11-05, `specs/023-native-recognition-engine/final-report.md`, SC-004), with both engines giving the same figures:

| Line | End of spec 023 |
|---|---|
| The owner's chords through the Rocksmith cable, accepted | 26 of 42 (62 %) |
| The owner's neighbouring chords (a chord close to the expected one) wrongly accepted | 13 of 42 |
| GuitarSet complete chords accepted, six strings summed / microphone | 33 / 56, 23 / 56 |
| IDMT-SMT-Audio-Effects chord a whole tone higher refused | 100 % |

Its experiment B showed the trade-off this spec must handle: a looser rule raised the wrong acceptances as much as the right ones. The owner asked (2026-11-04) for chords more lenient by default and for the leniency to be set in the practice settings.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - The chords I play are accepted (Priority: P1)

The owner plays the chords of a score through the Rocksmith cable, in Wait, Follow or Arcade. A chord played correctly is accepted at least 9 times in 10, where today about 6 in 10 are; a chord that is not the one written (a neighbouring chord) is not accepted more often than today.

**Why this priority**: a learner whose right chords are refused 4 times in 10 stops trusting the practice; it is the main complaint left after spec 023.

**Independent Test**: run the owner's chord recordings through the judgement with the default settings and count the chords accepted and the neighbouring chords wrongly accepted, with each engine.

**Acceptance Scenarios**:

1. **Given** the owner's 42 recorded chords and the default settings, **When** each is judged against its own chord, **Then** at least 38 are accepted, with each engine.
2. **Given** the same recordings judged against their neighbouring chords, **When** they are counted, **Then** no more are wrongly accepted than the limit set for this spec (13 of 42 at most, see Assumptions).
3. **Given** a chord played a whole tone higher than written (IDMT-SMT-Audio-Effects), **When** it is judged, **Then** it is still refused at least 85 % of the time.
4. **Given** single notes and wrong single notes of every dataset, **When** they are judged, **Then** none of their figures is worse than at the end of spec 023.

---

### User Story 2 - I set how strict the chord check is (Priority: P2)

A learner whose guitar, cable or playing does not suit the default can make the chord check more lenient or stricter from the practice settings, with a few clear levels, and sees what each level means.

**Why this priority**: one default cannot fit every guitar and input; the owner asked for the setting explicitly, but the default of story 1 already gives the main gain.

**Independent Test**: choose each level in the practice settings, judge the owner's chords with it, and check that a more lenient level accepts at least as many right chords and a stricter level wrongly accepts at most as many neighbouring chords as the default.

**Acceptance Scenarios**:

1. **Given** the practice settings, **When** the learner opens them, **Then** a chord leniency setting is shown with a few named levels (for example *Strict*, *Normal*, *Lenient*), each with a tip saying what it accepts and what it risks.
2. **Given** a more lenient level, **When** the same chords are judged, **Then** at least as many right chords are accepted as with the default.
3. **Given** a stricter level, **When** the same chords are judged, **Then** at most as many neighbouring chords are wrongly accepted as with the default.
4. **Given** a chosen level, **When** the application is closed and opened again, **Then** the level is kept.
5. **Given** a level changed during a run, **When** the next chord is judged, **Then** the new level applies from the next run at the latest, and never makes the run stop.
6. **Given** a learner who has never touched the setting, **When** they practise, **Then** the default level is used, which is more lenient than the behaviour of spec 023.

---

### User Story 3 - The cause of each refused chord is known (Priority: P3)

Before any rule changes, the 16 chords of the owner refused at the end of spec 023 are examined one by one, and the cause of each refusal is recorded (which string was not heard, which evidence was missing, which path refused it), so that the change aims at the real causes.

**Why this priority**: it does not change what the learner sees, but every later choice depends on it; experiment B of spec 023 failed for lack of it.

**Independent Test**: a report lists the 16 refused chords with the cause of each refusal, and groups them by cause.

**Acceptance Scenarios**:

1. **Given** the 16 chords refused at the end of spec 023, **When** the diagnostic is run, **Then** each has a recorded cause, and the causes are grouped with their counts.
2. **Given** the neighbouring chords wrongly accepted, **When** the diagnostic is run, **Then** each has the evidence that made it accepted.

---

### Edge Cases

- A chord with strings that cannot be told apart (the same note twice, or an octave of a lower string): the leniency does not make such strings count as heard or missing differently than today.
- A strum slow enough to sound like an arpeggio: the leniency applies to the chord as a whole, the strum window is unchanged unless the learner changes it.
- A level input too low or saturating: the leniency does not hide an input problem; the input warnings stay.
- A bass track: chords on a bass keep their own rules; the setting is shown and applies only where chords are checked.
- The expert chord settings that exist today (chord evidence, strum window, required strings): they keep working; the new leniency setting and them must not contradict each other in a way the learner cannot understand.
- A song practised with a leniency level, then the default changed by an update: a level chosen by the learner is kept; only a learner who never chose one gets the new default.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: With the default settings, at least 38 of the owner's 42 recorded chords MUST be accepted, with each engine (native and Java).
- **FR-002**: With the default settings, the owner's neighbouring chords wrongly accepted MUST NOT exceed the limit of this spec (13 of 42 at most until `/speckit-clarify` sets it), with each engine.
- **FR-003**: The single-note and wrong-note figures of every dataset (IDMT-SMT-Guitar, IDMT-SMT-Audio-Effects, GuitarSet, the owner's recordings) MUST NOT get worse than at the end of spec 023, with each engine; the chord a whole tone higher MUST stay refused at least 85 % of the time.
- **FR-004**: The GuitarSet complete chord figures (summed and microphone) MUST NOT get worse than at the end of spec 023.
- **FR-005**: The practice settings MUST offer a chord leniency setting with a few named levels, each with a tip in English, French and Spanish that says what it accepts and what it risks.
- **FR-006**: The default level MUST be more lenient than the behaviour at the end of spec 023, and MUST be the one that meets FR-001 and FR-002.
- **FR-007**: A more lenient level MUST accept at least as many right chords as the default, and a stricter level MUST wrongly accept at most as many neighbouring chords, on the owner's recordings.
- **FR-008**: The chosen level MUST be kept across restarts; a learner who never chose a level MUST get the default; a level chosen before an update MUST be kept.
- **FR-009**: The leniency MUST apply the same way with both engines and in every mode that checks chords (Wait, Follow, Arcade).
- **FR-010**: Before any rule changes, the 16 refused chords of the owner and the 13 neighbouring chords wrongly accepted MUST be examined, and the cause of each refusal or acceptance recorded in a report.
- **FR-011**: Every figure of this spec MUST be measured before and after, for each engine, on the datasets in `~/tuxguitar-datasets` and the owner's recordings, and kept in the spec's benchmark reports.
- **FR-012**: The expert chord settings that exist today MUST keep working, and their relation to the new leniency setting MUST be stated in their tips.

### Key Entities

- **Chord leniency level**: a named level of the chord check (for example strict, normal, lenient); one is the default; the learner's choice is kept.
- **Chord diagnostic**: for each of the owner's refused chords and wrongly accepted neighbours, the cause found (the string or evidence concerned, the path that decided).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: At least 38 of the owner's 42 chords through the Rocksmith cable are accepted with the default settings (90 %; 26 today), with each engine.
- **SC-002**: The owner's neighbouring chords wrongly accepted stay within the limit of this spec (13 of 42 at most until clarified), with each engine.
- **SC-003**: No single-note or wrong-note figure of any dataset is worse than at the end of spec 023; the chord a whole tone higher stays refused at least 85 % of the time; GuitarSet complete chords are not worse.
- **SC-004**: Each leniency level orders as promised on the owner's chords: the more lenient, the more right chords accepted; the stricter, the fewer neighbours wrongly accepted.
- **SC-005**: The owner, playing chords of a score through the cable in Wait, Follow and Arcade with the default, judges that right chords are accepted and wrong ones refused (gate on a test sheet).
- **SC-006**: The diagnostic gives a recorded cause for each of the 16 refused chords and the 13 wrong acceptances.

## Assumptions

- The owner's 42 chords and their neighbours are the recordings used in spec 023 (`OwnerChordsBenchmarkTest`); "accepted" and "wrongly accepted" are counted as there.
- The limit on neighbouring chords wrongly accepted is provisionally "no more than today" (13 of 42); `/speckit-clarify` settles it with the owner, since more leniency tends to raise it (experiment B of spec 023).
- The number and names of the leniency levels (for example three) are a proposal; `/speckit-clarify` may settle them.
- The setting goes where the learner sets the practice (the practice window's settings), next to or replacing nothing of the expert chord settings, which stay.
- Both engines keep giving the same judgements; a change of a shared rule is measured on both.
- Linux and Windows get the same setting and the same rule (no line "only on macOS").
- Chords played through a microphone are measured (GuitarSet microphone) but the 90 % target is for the cable, as the owner asked.
