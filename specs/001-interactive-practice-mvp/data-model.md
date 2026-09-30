# Data Model: Interactive Practice MVP

**Plan**: [plan.md](./plan.md) | **Research**: [research.md](./research.md)

All types are in-memory and transient (FR-034, FR-068). Nothing here is persisted. Types marked
*(engine)* live in the `TuxGuitar-audio-engine` library module and have no UI dependency;
*(tuner)* types live in the evolved tuner plugin `TuxGuitar-tuner-next`; *(practice)* types live
in the practice plugin. Java 9 language level (research R1).

---

## 1. Score-side types *(practice)*

### PracticeTrackCandidate

One structurally compatible track offered in the preparation step (FR-003).

| Field | Meaning |
|---|---|
| `trackNumber`, `trackName` | identity/display |
| `guitarHint` | true when the track's channel program is in the GM guitar family (0-based 24–31); advisory only |
| `preselected` | the one candidate initially shown: the caret's track if compatible, else a `guitarHint` candidate, else none (learner picks) |

Excluded from candidates (reason shown when none remain): `PERCUSSION`, `NOT_SIX_STRINGS`,
`UNDEFINED_STRING_PITCH`, `NO_PRACTICE_EVENTS`.

### PracticeTrackSelection (per practice session, never saved into the song)

| Field | Meaning |
|---|---|
| `candidate` | the chosen `PracticeTrackCandidate` |
| `accepted` | set only by an explicit learner action accepting this candidate as the guitar part to practise (may be the clearly labelled start/accept action itself); preselection and `guitarHint` never set it; practice cannot start while false |

Fixed for the whole session: later track selection or caret moves in the score do not change it
(FR-003, FR-066).

### GuitarConfirmationMemory (per `TGContext`, application run only)

Set of (song document, track) pairs the learner has explicitly accepted as Practice Track in
this run (guitar-confirmed tracks). Used to skip re-confirmation (FR-003) and to allow track-guided tuning in the
standalone tuner (FR-052). Never written to the song or to settings. Song identity = the open
document's `TGSong` instance; reopening a file yields a new instance and may ask again.

### PracticeEvent

One step in the practice sequence, in playback order (FR-006, FR-009).

| Field | Meaning |
|---|---|
| `kind` | `EXPECTED_NOTE`, `MULTI_NOTE` (waits for Skip), `AUTO_PASS` |
| `autoPassReason` | for `AUTO_PASS`: `DEAD`, `DEAD_ONLY_CHORD`, `GRACE_WITHOUT_PITCH`, `BEND`, `HARMONIC`, `TREMOLO_BAR`, `TRILL` |
| `sequenceIndex` | position in the practice sequence (0-based, unique per session) |
| `playIndex` | index of the played measure occurrence from `MidiRepeatController` (distinguishes repeat passes) |
| `beat`, `measureIndex`, `voiceIndex` | score location; `beat` is passed to `showExternalBeat` for the practice highlight (research R18) |
| `stringNumber`, `fret` | tablature instruction (instructional only, FR-027) |
| `expectedMidi` | `offset + stringValue + fret` (or grace fret) — `EXPECTED_NOTE` only |
| `isGrace` | true for the grace pitch that precedes its principal note |
| `techniques` | set of display-only flags (hammer, pull-off, slide, vibrato, palm mute, accent, let ring, staccato, tremolo picking) — never "verified" |
| `sameAsPrevious` | true when `expectedMidi` equals the previous expected note's pitch (drives FR-022); since 2026-09-27 also when the previous expected event was a chord containing that pitch, and for a chord whose required pitches were all in the previous expected note or chord (let-ring must not validate it) |
| `notes` | the score notes the event attacks (framed in the score, FR-073) |
| `chordNotes` | `EXPECTED_CHORD` only (since 2026-09-27): the chord's new pitched notes, each `{stringNumber, fret, midi, note}` — display data; string/fret instructional only |
| `requiredPitches` | `EXPECTED_CHORD` only: distinct sounding MIDI pitches of `chordNotes`, ascending (duplicates on several strings counted once, FR-078) |
| `toleratedPitches` | `EXPECTED_CHORD` only: pitches still sounding from earlier beats or tied in; allowed, never required (FR-077) |

**Classification rules** (research R3), evaluated per beat of the selected track, both voices:

1. Rests, empty voices, tied continuations (`TGNote.isTiedNote()`) → no event.
2. Notes sounding at the same time (same beat, or overlapping across voices by start + duration):
   all dead → `AUTO_PASS/DEAD_ONLY_CHORD`; otherwise → `MULTI_NOTE`.
   *Since 2026-09-27 (FR-077)*: if no new note carries a bend, harmonic, tremolo bar or trill →
   `EXPECTED_CHORD` with the new non-dead notes as `chordNotes` (none left → `AUTO_PASS/DEAD`);
   otherwise `MULTI_NOTE`.
3. Single note: dead → `AUTO_PASS/DEAD`; bend → `BEND`; harmonic → `HARMONIC`; tremolo bar →
   `TREMOLO_BAR`; trill → `TRILL`; otherwise `EXPECTED_NOTE`.
4. Grace effect on a single note: emits a preceding event — dead grace → `AUTO_PASS/GRACE_WITHOUT_PITCH`,
   else `EXPECTED_NOTE` with `isGrace=true`.

Validation: `sequenceIndex` strictly increasing; every `EXPECTED_NOTE` has `expectedMidi` in
0–127; `MULTI_NOTE` never carries `expectedMidi`.

### PracticeSequence

Ordered, immutable list of `PracticeEvent` for one track, built once per session start from
`MidiRepeatController(song, -1, -1)`.

| Field | Meaning |
|---|---|
| `events` | the ordered events |
| `startIndex` | resolved start (FR-004): first event at/after the caret in the first played occurrence of the caret's measure; else 0 |
| `trackTuning` | `TrackTuning` snapshot used for display (FR-008) |

### TrackTuning

| Field | Meaning |
|---|---|
| `openStringMidi[6]` | `TGString.getValue()` per string |
| `offset` | `TGTrack.getOffset()` (−24…+24), used unchanged in expected pitches |
| `realization` | `NONE` (0), `CAPO_EQUIVALENT(k)` (k > 0), `TUNE_DOWN(k)` (k < 0) — display/guidance only (FR-008) |
| `physicalTargetMidi[6]` | `openStringMidi[i]` if `offset ≥ 0`, else `openStringMidi[i] + offset` — tuner guidance only (FR-052) |
| `unusualSetup` | true only when `offset < 0` and some `physicalTargetMidi[i]` is below the per-string minimum of TuxGuitar's built-in Guitar › 6-String presets; display-only notice (FR-008) |

Invariant: `expectedMidi` of every event is computed from `offset` + `openStringMidi` + fret and
never from `physicalTargetMidi`.

---

## 2. Audio engine types *(engine)*

### AudioInputDescriptor

| Field | Meaning |
|---|---|
| `id` | stable key within the run (mixer name + index + channel) |
| `deviceName` | mixer name from Java Sound |
| `channelIndex` | 0-based channel within the device stream; absent for mono devices |
| `channelCount` | channels the device offers (1 for mono) |
| `displayName` | device name, plus "— Input k" for multi-channel devices |
| `kind` | `BUILT_IN_MICROPHONE`, `MICROPHONE`, `INSTRUMENT`, `UNKNOWN` (only built-in mic expected to be positively identified; research R11) |
| `isSystemDefault` | offered first when available |

### CurrentInputSelection

One per `TGContext` (shared by tuner and practice, FR-039/FR-048). Exactly one logical input
(device + channel). Independent of song-track selection: no song track is ever mapped to an
input.

| Field | Meaning |
|---|---|
| `input` | selected `AudioInputDescriptor` or none |
| `format` | negotiated PCM format |
| `assessment` | latest `InputAssessment` (reset to `UNCHECKED` on every change — FR-039) |
| `revision` | increments on every change; listeners use it to detect invalidation |

Transitions: `NONE → SELECTED(UNCHECKED) → CHECKED(assessment)`; any input change →
`SELECTED(UNCHECKED)`; device loss → `LOST`.

### AnalysisFrame (immutable, one per hop)

| Field | Meaning |
|---|---|
| `timestampNanos` | capture time of the hop end |
| `rmsDbfs`, `peakDbfs`, `clippedRatio` | level metrics |
| `levelState` | `TOO_LOW`, `SUITABLE`, `TOO_HIGH`, `CLIPPING` (live meter, FR-037) |
| `f0Hz` | estimated fundamental or NaN |
| `confidence` | 0–1 from the estimator's aperiodicity/clarity |
| `onset` | true when a new articulation (attack) is detected in this hop |

### InputAssessment

`UNCHECKED`, `SUITABLE`, `SUBOPTIMAL`, `UNUSABLE` + reason (`NO_SIGNAL`, `TOO_LOW`, `TOO_HIGH`,
`CLIPPING`, `DEVICE_LOST`, `ACCESS_DENIED_SUSPECTED`). Derived from a window of frames; thresholds
are parameters set from the benchmark (FR-038).

### PitchReading (shared interpretation)

| Field | Meaning |
|---|---|
| `midi` | nearest equal-tempered MIDI note (A4 = 440 Hz) |
| `noteName`, `octave` | e.g. `E`, `2` (sounding pitch) |
| `cents` | deviation from that note's centre, −50…+50 (FR-017, FR-049) |
| `reliable` | confidence above threshold **and** input not unusable |

### TolerancePolicy

`tunerInTuneCents` and `practiceAcceptCents`, both parameters. Construction fails unless
`practiceAcceptCents ≥ tunerInTuneCents` and `practiceAcceptCents < 50` (FR-020, adjacent
semitone never accepted).

---

## 3. Tuner types *(tuner — `TuxGuitar-tuner-next`)*

### TunerTuning

| Field | Meaning |
|---|---|
| `source` | `TRACK`, `PRESET`, `CUSTOM` |
| `openStringMidi[6]` | targets; for `TRACK` = the track's `physicalTargetMidi` |
| `realization` | for `TRACK`: shown with the targets (e.g. "capo at fret 2", "tuned down 1 semitone") |
| `presetName` | for presets (from `TuningManager` "Guitar / 6-String": at least `E Tuning`, `Dropped D`) |

### TunerSessionState (per application run)

| Field | Meaning |
|---|---|
| `mode` | `CHROMATIC` or `GUIDED` |
| `explicitChoice` | `TunerTuning` chosen by the learner, or none (FR-055) |
| `effectiveTuning` | explicit choice if any; else the Practice Track (in practice context) or a guitar-confirmed selected track (standalone); else Standard E (FR-052) |

Not persisted across launches.

---

## 4. Practice session *(practice)*

### PracticeSession

| Field | Meaning |
|---|---|
| `song`, `track` | the song and the Practice Track (compared on update events) |
| `trackSelection` | `PracticeTrackSelection` (FR-003) |
| `sequence` | `PracticeSequence` |
| `startIndex` | original start (Restart target, FR-033) |
| `currentIndex` | current event |
| `state` | see state machine |
| `pauseReasons` | set: `INPUT_UNUSABLE`, `TUNER_OPEN`, `INPUT_CHANGED`, `PLAYBACK` |
| `outcomes` | map `sequenceIndex → ACCEPTED | SKIPPED | AUTO_PASSED` (session only) |
| `feedback` | current `FeedbackState` for an expected note |
| `articulationArmed` | false after an acceptance until a new onset is seen (FR-022) |

### FeedbackState

`WAITING`, `UNCERTAIN`, `INCORRECT(detected, octaveOnly)`, `CORRECT` (FR-018, FR-019, FR-023).

### State machine

```text
            start (eligible, input usable)
 PREPARING ───────────────────────────────▶ RUNNING
     ▲  │ input unusable: stay (FR-038)       │ │ │
     │  └─ cancel ──▶ (closed)               │ │ └─ last event done ──▶ COMPLETE ──Restart──▶ RUNNING(startIndex)
     │                                        │ │
     │     pause cause (input unusable,       │ └─ song edit / other document ──▶ STOPPED (FR-066)
     │     tuner opened, input changed,       │
     │     playback started)                  ▼
     │                                     PAUSED ── all causes resolved ──▶ READY_TO_RESUME
     │                                        ▲                                  │ explicit Resume
     └──────────── Stop (any state) ──────────┘                                  ▼
                                                                              RUNNING
```

Rules in `RUNNING`:

- `EXPECTED_NOTE`: accept only when `PitchReading.reliable`, `midi == expectedMidi`,
  `|cents| ≤ practiceAcceptCents`, stable for the stability window, and (if `sameAsPrevious`)
  `articulationArmed`. Then outcome `ACCEPTED`, advance.
- `MULTI_NOTE`: no judgement; only Skip advances (FR-025, FR-012).
- `EXPECTED_CHORD`: until chord matching exists (plan stage E), as `MULTI_NOTE`; then accepted
  per FR-079–FR-081 (chord feedback `WAITING`, `UNCERTAIN`, `INCOMPLETE(missing)`,
  `FOREIGN(pitch)`, `CORRECT`).
- `AUTO_PASS`: advance immediately with a brief indication (FR-011, FR-013).
- Skip: outcome `SKIPPED`, advance (FR-030, FR-031).
- Suboptimal input: continue, unreliable readings → `UNCERTAIN` (FR-026, FR-043).
- Unusable input → `PAUSED(INPUT_UNUSABLE)` (FR-043); resolution needs an input check; resume
  only by explicit Resume (FR-044).
