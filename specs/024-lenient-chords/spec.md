# Feature Specification: Every chord the owner plays right is accepted, a clearly wrong chord refused, with a chord leniency the learner can set

**Feature Branch**: `feature/interactive-practice` (no branch of its own; spec directory `specs/024-lenient-chords`)

**Created**: 2026-11-05

**Status**: Draft

**Input**: User description: "spec 024 : les accords du propriétaire joués au câble Rocksmith doivent être acceptés au moins à 90 % (26/42 = 62 % à la fin de la spec 023), sans que les accords voisins acceptés à tort augmentent au-delà d'une limite à fixer (13/42 aujourd'hui) ; l'acceptation des accords doit être plus tolérante par défaut, et cette tolérance réglable dans les paramètres de pratique ; mesuré sur les jeux de données (~/tuxguitar-datasets : accords du propriétaire, GuitarSet, IDMT) avant et après, avec les deux moteurs (natif et Java), sans régression sur les notes seules et les fausses notes ; le plan commence par un diagnostic des 16 accords refusés du propriétaire (specs/023-native-recognition-engine/benchmark/chord-experiments.md)."

**Where it comes from**: spec 023 left chords as the weak point. At its end (2026-11-05, `specs/023-native-recognition-engine/final-report.md`, SC-004), with both engines giving the same figures (the first two series of the owner; since the clarifications of 2026-11-05 the reference is the owner's third series, Key Entities):

| Line | End of spec 023 |
|---|---|
| The owner's chords through the Rocksmith cable, accepted | 26 of 42 (62 %) |
| The owner's neighbouring chords (a chord close to the expected one) wrongly accepted | 13 of 42, of which 12 are the same chord in another voicing and 1 is a really different chord (diagnostic, `benchmark/diagnostic.md`) |
| GuitarSet complete chords accepted, six strings summed / microphone | 33 / 56, 23 / 56 |
| IDMT-SMT-Audio-Effects chord a whole tone higher refused | 100 % |

Its experiment B showed the trade-off this spec must handle: a looser rule raised the wrong acceptances as much as the right ones. The owner asked (2026-11-04) for chords more lenient by default and for the leniency to be set in the practice settings.

## Clarifications

### Session 2026-11-05

- Q: With the default setting, how many of the owner's 42 neighbouring chords may be wrongly accepted at most? → A: No more than today: 13; the *Strict* level must do better than 13. *(The way they are counted was changed after the diagnostic, see below.)*
- Q: How many chord leniency levels? → A: Three: *Strict*, *Normal* (the new default), *Lenient*.
- Q: Where is the chord leniency setting? → A: In the *Mode* tab of the practice window, next to the choice of Wait / Follow / Arcade.
- Q: How do the existing expert chord settings (chord evidence, strum window) relate to the levels? → A: Choosing a level sets the expert chord settings to its values; an expert chord setting changed by hand makes the level show *Custom*.
- Q: The diagnostic shows that 12 of the 13 neighbouring chords wrongly accepted are the same chord in another voicing (the same note names on more or fewer strings, or with an octave more or less), which audio cannot tell apart; how should they be counted? → A: Only a really different chord accepted counts as a wrong acceptance; the same chord in another voicing is reported apart and is not counted (owner's choice (a), 2026-11-05).
- Q: How many of the owner's 42 chords must be accepted with the default level (38 is out of reach without changing the engine)? → A: All of them, 42 of 42: a right chord must never be refused. A wrong chord accepted is preferred to a right chord refused, but a clearly wrong chord must still be refused; among the settings that accept every right chord, the default is the one that refuses the most wrong chords (owner, 2026-11-05).
- Q: From what difference is a chord clearly wrong, so that it must always be refused? → A: When at least two note names (the octave not counted) of the expected chord are missing from the chord played; it is always refused (100 %); a chord that differs by one note name only is refused as often as possible, with no obligation. If no setting accepts every right chord while refusing every clearly wrong one, fall back to: no threshold per chord, the dataset lines of FR-003 kept, and the rest refused as often as possible; the fallback is said in the final report (owner, 2026-11-05).
- Q: May the work change the engines, the committed owner-chords baseline and the chord defaults to reach every right chord? → A: Yes to all three (owner, 2026-11-05): (1) judging each string over the whole strum may be added to both engines (the native crate and the Java engine, identically); (2) the committed `owner-chords-baseline.properties` may stop failing on its old count of wrong acceptances (which counts voicings), its minimum of accepted chords kept, the new count being checked by a new test; (3) the chord defaults (chord evidence, confirmation) may change if needed, with only the lines of `RecognitionSettingsTest` and `RecognitionControlsTest` that pin them adapted. Constraint added by the owner: every engine must be set by external parameters, never needing a rebuild when a parameter changes, and the engines (how they work, their interface, their parameters) must be documented.
- Q: Must the external parameters cover every internal value of both engines now, or only the chord values this spec adds or tunes? → A: Only the chord and strum values this spec adds or tunes become external parameters in spec 024, and the documentation of the engines is written for everything that exists, listing the constants still fixed in the code; a dedicated spec 026 makes every other constant an external parameter (owner, 2026-11-05).
- Q: Which recordings of the owner are the reference for the targets of this spec? → A: The third series, `owner-chords-3` (25 takes of 8 chords chosen by the owner: Am, C, G, Em, D, A, Bm, Dm, through the Rocksmith adapter, names confirmed by the owner), in place of the first two series (42 takes), which the owner listened to and judged bad, with a contact problem (owner, 2026-11-05: "pars sur cela à la place des autres").
- Q: What becomes of the first two series (42 takes), judged bad? → A: A guard only: at least 8 and 18 of their takes accepted with the default settings, the minimum the committed `OwnerChordsBenchmarkTest` already holds; they are no target, some of their right chords are known to stay refused and 100 % is never expected on them, whereas the reference series must reach 100 % (owner, 2026-11-05).
- Q: Which expert chord settings does choosing a level set? → A: The chord rule, its match threshold and the values the measured sweep moves between levels; the strum window and the chord evidence are not level values (the strum window stays the learner's; the chord evidence belongs to the per-string rule) (recommended answer, applied on the owner's instruction "corrige tout", 2026-11-05).
- Q: How are strings that cannot be told apart (the same note twice, an octave of a lower string) treated now? → A: The default chord check judges the chord by its note names, the octave not counted, so such strings need no separate check and another voicing of the same names is never counted wrong; with the per-string rule, kept as an expert option, they are treated as before (recommended answer, applied on the owner's instruction "corrige tout", 2026-11-05).
- Q: Do chords on a bass track keep rules of their own? → A: No: the code has no chord rule of its own for a bass, so chords on any track are judged by the same chord check and level; the bass figures (single notes) stay a guard (recommended answer, applied on the owner's instruction "corrige tout", 2026-11-05).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - The chords I play are accepted (Priority: P1)

The owner plays the chords of a score through the Rocksmith cable, in Wait, Follow or Arcade. A chord played correctly is always accepted, where today 17 of the 25 takes of the reference series are; a chord that is not the one written is refused as often as that allows, and a clearly wrong chord is always refused.

**Why this priority**: a learner whose right chords are refused about 3 times in 10 (8 of 25 on the reference series) stops trusting the practice; it is the main complaint left after spec 023.

**Independent Test**: run the owner's reference series (Key Entities) through the judgement with the default settings and count the chords accepted, the clearly wrong chords accepted and the other really different chords accepted, with each engine.

**Acceptance Scenarios**:

1. **Given** the 25 takes of the owner's reference series and the default settings, **When** each is judged against its own chord, **Then** all 25 are accepted, with each engine.
2. **Given** each take of the reference series judged against the chord of every other take that is clearly wrong for it (Key Entities), **When** they are counted, **Then** none is accepted; the other really different chords accepted are counted and as few as possible; the same chord in another voicing is reported apart.
3. **Given** a chord played a whole tone higher than written (IDMT-SMT-Audio-Effects), **When** it is judged, **Then** it is still refused at least 85 % of the time.
4. **Given** single notes and wrong single notes of every dataset, **When** they are judged, **Then** none of their figures is worse than at the end of spec 023.

---

### User Story 2 - I set how strict the chord check is (Priority: P2)

A learner whose guitar, cable or playing does not suit the default can make the chord check more lenient or stricter from the practice settings, with a few clear levels, and sees what each level means.

**Why this priority**: one default cannot fit every guitar and input; the owner asked for the setting explicitly, but the default of story 1 already gives the main gain.

**Independent Test**: choose each level in the practice settings, judge the reference series with it, and check that a more lenient level accepts at least as many right chords and a stricter level wrongly accepts at most as many really different chords as the default.

**Acceptance Scenarios**:

1. **Given** the *Mode* tab of the practice window, **When** the learner opens it, **Then** a chord leniency setting is shown next to the choice of Wait / Follow / Arcade, with three levels *Strict*, *Normal*, *Lenient*, each with a tip saying what it accepts and what it risks.
2. **Given** a more lenient level, **When** the same chords are judged, **Then** at least as many right chords are accepted as with the default.
3. **Given** the *Strict* level, **When** the same chords are judged, **Then** at most as many really different chords are wrongly accepted as with *Normal*, and fewer when any setting allows it; *Strict* may refuse some right chords, and its tip says so.
4. **Given** a level is chosen, **When** the learner opens the expert chord settings (Recognition settings), **Then** they show the level's values; **When** one of them is changed by hand, **Then** the level shows *Custom*.
5. **Given** a chosen level, **When** the application is closed and opened again, **Then** the level is kept.
6. **Given** a level changed during a run, **When** the next chord is judged, **Then** the new level applies from the next run at the latest, and never makes the run stop.
7. **Given** a learner who has never touched the setting, **When** they practise, **Then** the default level is used, which is more lenient than the behaviour of spec 023.

---

### User Story 3 - The cause of each refused chord is known (Priority: P3)

Before any rule changes, the 16 chords of the owner refused at the end of spec 023 (the first two series) are examined one by one, and the cause of each refusal is recorded (which string was not heard, which evidence was missing, which path refused it), so that the change aims at the real causes.

**Why this priority**: it does not change what the learner sees, but every later choice depends on it; experiment B of spec 023 failed for lack of it.

**Independent Test**: a report lists the 16 refused chords with the cause of each refusal, and groups them by cause.

**Acceptance Scenarios**:

1. **Given** the 16 chords refused at the end of spec 023, **When** the diagnostic is run, **Then** each has a recorded cause, and the causes are grouped with their counts.
2. **Given** the neighbouring chords wrongly accepted, **When** the diagnostic is run, **Then** each has the evidence that made it accepted.

---

### Edge Cases

- A chord with strings that cannot be told apart (the same note twice, or an octave of a lower string): the default chord check judges note names, the octave not counted, so these strings need no separate check and another voicing of the same names is never counted wrong; with the per-string rule (expert option) they are treated as before.
- A strum slow enough to sound like an arpeggio: the leniency applies to the chord as a whole, the strum window is unchanged unless the learner changes it.
- A level input too low or saturating: the leniency does not hide an input problem; the input warnings stay.
- A bass track: chords on a bass are judged by the same chord check and level as on a guitar (no chord rule of its own exists); the bass figures (single notes) are a guard.
- The expert chord settings that exist today (chord evidence, strum window, the chord rule, formerly "Strings required"): they keep working; the new leniency setting and them must not contradict each other in a way the learner cannot understand.
- A song practised with a leniency level, then the default changed by an update: a level chosen by the learner is kept; only a learner who never chose one gets the new default.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: With the default settings, all 25 takes of the owner's reference series (Key Entities) MUST be accepted, with each engine (native and Java): a right chord is never refused.
- **FR-002**: With the default settings, no clearly wrong chord (Key Entities) MUST be accepted: each take of the reference series is judged against the chord of every other take that is clearly wrong for it, with each engine. The other really different chords wrongly accepted are counted and reported; the same chord in another voicing is reported apart and not counted. If FR-001 and this cannot both be met, the fallback of the clarification applies (the FR-003 lines kept, the wrong chords refused as often as possible) and the final report says so.
- **FR-003**: The single-note and wrong-note figures of every dataset (IDMT-SMT-Guitar, IDMT-SMT-Audio-Effects, GuitarSet, the owner's recordings) MUST NOT get worse than at the end of spec 023, with each engine; the chord a whole tone higher MUST stay refused at least 85 % of the time.
- **FR-003a**: The first two series of the owner (`owner-chords`, `owner-chords-2`, 42 takes, judged bad by the owner) are a guard, not a target: with the default settings and each engine, at least 8 and 18 of their takes MUST stay accepted (the committed minimum of `owner-chords-baseline.properties`); some of their right chords may stay refused.
- **FR-004**: The GuitarSet complete chord figures (summed and microphone) MUST NOT get worse than at the end of spec 023.
- **FR-005**: The *Mode* tab of the practice window MUST offer a chord leniency setting next to the choice of Wait / Follow / Arcade, with three levels *Strict*, *Normal* (default) and *Lenient* (French *Strict*, *Normal*, *Tolérant*), each with a tip in English, French and Spanish that says what it accepts and what it risks.
- **FR-006**: The default level MUST be more lenient than the behaviour at the end of spec 023; among the settings that meet FR-001, it MUST be the one that refuses the most wrong chords (wrong acceptances as defined in Key Entities), while meeting FR-002 and FR-003.
- **FR-007**: A more lenient level MUST accept at least as many right chords as the default, and a stricter level MUST wrongly accept at most as many really different chords, on the reference series.
- **FR-008**: The chosen level MUST be kept across restarts; a learner who never chose a level MUST get the default; a level chosen before an update MUST be kept.
- **FR-009**: The leniency MUST apply the same way with both engines and in every mode that checks chords (Wait, Follow, Arcade).
- **FR-010**: Before any rule changes, the 16 refused chords of the owner and the 13 neighbouring chords wrongly accepted MUST be examined, and the cause of each refusal or acceptance recorded in a report.
- **FR-011**: Every figure of this spec MUST be measured before and after, for each engine, on the datasets in `~/tuxguitar-datasets` and the owner's recordings, and kept in the spec's benchmark reports.
- **FR-012**: Choosing a level MUST set the level's expert chord settings (Recognition settings: the chord rule, its match threshold, and any value the measured sweep moves between levels) to that level's values, and MUST NOT change the strum window or the chord evidence; changing one of them by hand MUST make the level show *Custom*, and choosing a level again MUST restore its values. Their tips MUST say so.
- **FR-013**: Every engine value this spec adds or tunes (for the chord check and the strum) MUST be an external parameter of both engines, read at run time from the recognition settings, so that changing it never needs a rebuild of the application or of the native library; both engines MUST read the same value.
- **FR-014**: The engines MUST be documented in one place (the constants still fixed in the code listed as such, for spec 026): how each one works (the analysis of a frame, the attack evidence, the chord evidence, the decision in the practice session), their interface (what the application gives them and what they return, including the native interface), and every parameter (name, meaning, unit, default, bounds, where it is set).

### Key Entities

- **Chord leniency level**: *Strict*, *Normal* (default) or *Lenient*, or *Custom* when an expert chord setting was changed by hand; each named level is a set of values of the expert chord settings; the learner's choice is kept.
- **Clearly wrong chord**: an expected chord at least two of whose note names (the octave not counted) are missing from the chord played. Always refused.
- **Wrong acceptance**: a control (the chord of another take) accepted while the owner played a chord whose note names are not all among the control's, nor the control's among the played chord's: a really different chord. A control with the same note names (a subset, a superset, an octave more or less) is **another voicing**: it is reported apart, never counted as a wrong acceptance.
- **Reference series**: the owner's third series of chords through the Rocksmith adapter, `owner-chords-3`: 25 takes of 8 chords (Am, C, G, Em, D, A, Bm, Dm), each judged against its own chord (25), and against every other take's chord: 54 voicings, 138 one name off, 408 clearly wrong.
- **Chord diagnostic**: for each of the owner's refused chords and wrongly accepted neighbours, the cause found (the string or evidence concerned, the path that decided).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: All 25 takes of the owner's reference series through the Rocksmith cable are accepted with the default settings (100 %; 17 today), with each engine.
- **SC-002**: With *Normal*, no clearly wrong chord is accepted on the reference series (408 such judgements), with each engine (or, under the fallback, the FR-003 lines hold); the really different chords wrongly accepted are as few as the settings that accept every right chord allow; *Strict* accepts at most as many.
- **SC-003**: The first two series keep at least 8 and 18 takes accepted. No single-note or wrong-note figure of any dataset is worse than at the end of spec 023; the chord a whole tone higher stays refused at least 85 % of the time; GuitarSet complete chords are not worse.
- **SC-004**: Each leniency level orders as promised on the reference series: the more lenient, the more right chords accepted; the stricter, the fewer really different chords wrongly accepted.
- **SC-005**: The owner, playing chords of a score through the cable in Wait, Follow and Arcade with the default, judges that right chords are accepted and wrong ones refused (gate on a test sheet).
- **SC-006**: The diagnostic gives a recorded cause for each of the 16 refused chords and the 13 wrong acceptances of the first two series (done, `benchmark/diagnostic.md`).

## Assumptions

- The owner's reference series is `owner-chords-3` (Clarifications); the takes are the owner's playing and are not committed, their labels and figures are (`benchmark/owner-chords-3/`); "accepted" is counted as in `OwnerChordsBenchmarkTest`; wrong acceptances are counted as defined in Key Entities, each take being judged against every other take's chord (not only the next one), so that every clearly wrong chord of the recordings is tried.
- The targets (every right chord accepted, no clearly wrong chord accepted, the same chord in another voicing reported apart), the three levels, the place of the setting and its link with the expert settings were settled with the owner (Clarifications, 2026-11-05).
- Both engines keep giving the same judgements; a change of a shared rule is measured on both.
- Making every other internal constant of the engines an external parameter is out of scope: it is spec 026 (Clarifications, 2026-11-05).
- Linux and Windows get the same setting and the same rule (no line "only on macOS").
- Chords played through a microphone are measured (GuitarSet microphone) but the 100 % target is for the cable, as the owner asked.
