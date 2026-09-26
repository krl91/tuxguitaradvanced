# Feature Specification: Interactive Practice MVP

**Feature Branch**: `feature/interactive-practice`

**Created**: 2026-09-26

**Status**: Draft (revision 4, final product-owner decisions of 2026-09-26 applied)

**Input**: User description: "Interactive Practice MVP. A guitar learner can open an existing guitar tablature in TuxGuitar, connect a guitar through an audio input, and practice the selected track note by note. The application identifies the next expected playable note, shows expected note/string/fret, the detected note, pitch deviation, and correct / incorrect / uncertain feedback; the score waits until the expected pitch is detected reliably, then advances. Validation is based on detected pitch only; string and fret are instructional and must not be claimed as verified. Scope: macOS Apple Silicon, single guitar track, monophonic single notes, live guitar input, wait-for-correct-note, immediate visual feedback. Out of scope: chords, string/fret validation, articulation recognition, rhythm/timing scoring, effects, statistics, adaptive exercises, machine learning, cloud services."

## Clarifications

### Session 2026-09-26

- Q: Must the learner be able to Start, Skip, Resume and Stop Interactive Practice from the keyboard, without using the mouse? → A: Yes — these actions must be operable from the keyboard, following TuxGuitar's existing shortcut conventions.
- Q: May the learner's live audio ever be recorded or kept, or must it be processed only in memory and then thrown away? → A: Processed only in memory for immediate detection; never saved, kept after use, or sent anywhere.

## User Scenarios & Testing *(mandatory)*

The intended preparation flow is: select audio input → automatic input-level check → calibrate or
adjust gain if the level is not suitable (required only if it is unusable) → optionally tune the
guitar → start Interactive Practice.

### User Story 1 - Practise a melody note by note (Priority: P1)

Interactive Practice is a song-learning mode, and its defining interaction is
**wait-for-correct-note**: for each supported single note, the score waits at the current
expected note until the learner plays it correctly. A wrong note, silence, or uncertain detection
never advances practice; a valid correct note advances to the next expected note; and two
consecutive identical pitches need two separate articulations.

A guitar learner opens a tablature containing a single-note guitar part, selects that 6-string
guitar track, and chooses Interactive Practice, an explicit action for the open song. In the
preparation step they review or select the audio input, see its level state, calibrate if
required, optionally open the tuner, see the tuning the track expects, and see where practice
will start — the position of the normal score cursor, placed before choosing Interactive
Practice. Once practice is running, they can see at a glance which score note is expected, its
note name and octave, the string and fret written in the tablature, the note they are playing
and how in tune it is, the current feedback state, and the health of the input level — without
switching to the tuner. The learner works through the track at their own pace until its end.

**Why this priority**: This is the core learning loop and the reason the feature exists.

**Independent Test**: Open a song whose selected track contains only single pitched notes,
choose Interactive Practice, go through the preparation step with a usable input, start
practice, and play the first few notes correctly on a guitar tuned to the track: each note is
accepted in turn, while silence, uncertain input, wrong notes, and wrong octaves leave the
position unchanged, and the running view shows all the information listed above.

**Acceptance Scenarios**:

1. **Given** a supported tablature is open and a 6-string guitar track is selected, **When** the
   learner chooses Interactive Practice, **Then** a preparation step lets them review or select
   the audio input, see the input-level state, calibrate if required, optionally open the tuner,
   see the tuning expected by the selected track, see the resolved starting position (from the
   normal score cursor), and start practice.
2. **Given** practice is running on an expected note, **When** the learner looks at the practice
   view, **Then** they can see at a glance the highlighted score location, the expected note and
   octave, the tablature string and fret as instructions, the detected note and its tuning
   deviation, the feedback state (*waiting*, *uncertain*, *incorrect*, or *correct*), and the
   input-level health, without opening the tuner.
3. **Given** a song is open, a 6-string guitar track is selected, and the input is usable,
   **When** the learner starts Interactive Practice, **Then** the first practice event is
   highlighted in the score and, for an expected note, its note name with octave, string, and
   fret are displayed.
4. **Given** practice is on an expected note, **When** no usable playing is detected, **Then**
   the state is *waiting* and practice does not advance; **when** the detection is uncertain or
   a wrong note is played, **then** the state is *uncertain* or *incorrect* respectively and
   practice still does not advance.
5. **Given** practice is on an expected note, **When** the learner plays that pitch in the
   correct octave reliably, **Then** the note is marked correct and practice moves to the next
   practice event.
6. **Given** two consecutive expected notes have the same pitch, **When** the learner sustains
   the first note without playing it again, **Then** the second note is not accepted until the
   learner articulates it anew.
7. **Given** notes connected by a hammer-on, pull-off, or slide, **When** the learner plays the
   written sequence of pitches, **Then** each note can be accepted by its pitch, and nothing
   states that the technique was detected.
8. **Given** every remaining event of the track has been accepted, skipped, or passed
   automatically, **When** practice reaches the end of the track, **Then** the learner is told
   the session is complete and can restart or exit.
9. **Given** a session is complete, **When** the learner chooses Restart, **Then** practice
   starts again from that session's original starting position.
10. **Given** the next practice event is off-screen, **When** it becomes current, **Then** the
   score view shows it without the learner having to scroll.

---

### User Story 2 - Understand what went wrong (Priority: P1)

While practising, the learner plays a wrong note, the right note in the wrong octave, a
slightly out-of-tune note, or plays too quietly. The application shows, like a tuner, which note
it heard and how in tune that note is, and separately which note it expects, so the learner can
correct themselves. When the application cannot tell what was played, it says so instead of
calling it a mistake.

**Why this priority**: Feedback quality is what turns detection into learning (Constitution
XIV). Without it, learners cannot tell why practice will not advance.

**Independent Test**: With practice on a known expected note, play a neighbouring semitone, the
same note one octave away, the expected note slightly sharp, the expected note slightly flat,
and a very quiet sound; each produces the matching, distinct feedback, and only the slightly
sharp or flat expected note is accepted.

**Acceptance Scenarios**:

1. **Given** practice expects E4, **When** the learner plays F4 steadily and slightly flat,
   **Then** the feedback shows expected E4, detected F4 with its own flat deviation in cents,
   and the state *incorrect*; it does not show a deviation measured from E4.
2. **Given** practice expects E3, **When** the learner plays E4, **Then** the state is
   *incorrect* and the feedback explains, in learner terms, that the note name is right but the
   octave is not.
3. **Given** practice is on an expected note, **When** the learner plays it slightly out of
   tune, **Then** it may be accepted, and the feedback still shows whether it was sharp or flat.
4. **Given** practice is on an expected note, **When** the learner plays a neighbouring
   semitone, **Then** it is not accepted, even if played out of tune towards the expected note.
5. **Given** a signal is present but its pitch cannot be determined reliably (too weak, clipped,
   noisy, several strings ringing), **When** feedback is shown, **Then** the state is
   *uncertain*, it is not presented as a mistake, and practice does not advance.
6. **Given** the expected string and fret are displayed, **When** the learner reads them,
   **Then** they are presented as the tablature's instruction, and nothing states or implies that
   the string or fret actually used was checked.
7. **Given** an accepted note carried vibrato, palm mute, accent, let ring, staccato, or tremolo
   picking, **When** it is shown as correct, **Then** nothing states or implies that the
   technique or articulation was checked.

---

### User Story 3 - Set up and monitor the audio input (Priority: P1)

Before practising, the learner chooses the audio input carrying their guitar — an audio
interface's instrument input or a microphone, including the Mac's built-in one — and sees a live
indication of the signal level. As soon as an input is selected, a quick level check runs while
the learner plays. If the input is suitable, the learner simply continues. If it is usable but
suboptimal, the learner may still practise, but is warned that detection may be less reliable
and is recommended to calibrate or adjust the gain. If it is unusable — for example no
meaningful guitar signal, or clipping so severe that pitch cannot be detected reliably —
practice cannot start until the problem is corrected, and the application explains the problem
and guides the learner, including towards the hardware or system setting when the application
cannot change the gain itself. The learner can also ask to recalibrate at any time. Unless the input is
positively identified as a direct instrument input, the application recommends headphones. If
anything goes wrong with the input, the application explains what happened and how to recover,
without crashing or freezing.

**Why this priority**: Nothing in Stories 1–2 works without a usable input at a usable level.

**Independent Test**: Select an audio-interface input, then the built-in microphone: the level
indication responds to playing in both cases, the headphone recommendation appears for the
built-in microphone, and the automatic level check reports too-low, suitable, too-high, and
clipping levels correctly as the gain is changed. At a suitable level practice starts normally;
at a suboptimal level practice can start with a warning and a calibration recommendation; with
no meaningful signal or severe clipping practice cannot start. Then deny input access, remove the device mid-session, and start with
no input: each case produces a clear, actionable message and TuxGuitar keeps working.

**Acceptance Scenarios**:

1. **Given** several audio inputs are available, **When** the learner sets up practice, **Then**
   they can choose any of them from within TuxGuitar; an audio interface is not required.
2. **Given** an input is selected, **When** sound arrives, **Then** a live level indication
   shows whether the level is too low, suitable, too high, or clipping.
3. **Given** the selected input is the Mac's built-in microphone, is identifiable as a
   microphone, or cannot reliably be classified, **When** the setup is shown, **Then** a
   non-blocking recommendation explains in simple terms that headphones stop sound from the
   computer being picked up again and can improve detection, and the learner can continue
   without headphones. For an input positively identified as a direct instrument input, the
   recommendation is not shown.
4. **Given** an input has just been selected or changed, **When** the learner plays, **Then** a
   quick level check runs automatically; if the level is suitable, this is indicated and the
   learner can continue without a separate calibration step.
5. **Given** the level check finds the input usable but suboptimal (e.g. somewhat low, high, or
   occasionally clipping), **When** the result is shown, **Then** the learner is warned that
   detection may be less reliable, calibration or gain adjustment is recommended, and the learner
   may still start practice.
6. **Given** the level check finds the input unusable (e.g. no meaningful guitar signal, or
   clipping severe enough that pitch cannot be detected reliably), **When** the learner tries to
   start practice, **Then** practice does not start, the problem is explained, and the learner is
   guided towards correcting it; practice can start once the input becomes usable.
7. **Given** the application cannot control the input's gain, **When** the level is not
   suitable, **Then** the learner is guided to adjust the interface's gain control or the
   operating system's input setting.
8. **Given** the application can control the input's gain, **When** the level is not suitable,
   **Then** automatic adjustment may be offered, and manual adjustment remains possible.
9. **Given** a level check or calibration has already been done, **When** the learner asks to
   recalibrate, **Then** the calibration workflow runs again; **when** the learner changes the
   input from either the practice setup or the tuner, **then** it becomes the current input for
   both, the previous result no longer applies, and a new level check runs before reliable
   judgements are made.
10. **Given** practice is running, **When** the input becomes usable but suboptimal, **Then** the
   session continues, the learner is warned, and results that are not reliable are shown as
   *uncertain* rather than as mistakes; **when** the input becomes unusable, **then** practice
   pauses automatically at the current event, does not advance, explains the problem, and keeps
   monitoring for recovery or lets the learner select another input; once a usable input has been
   restored and checked, practice stays paused at the same event, indicates that it is ready to
   resume, and resumes only when the learner explicitly chooses Resume.
11. **Given** input access is denied, no usable input exists, the input format cannot be used,
   or the selected device disappears, **When** this happens before or during practice, **Then**
   the application explains the problem, remains usable, keeps the current practice position
   where reasonable, and lets the learner recover or select another input.

---

### User Story 4 - Tune the guitar and check detection (Priority: P2)

The learner uses TuxGuitar's tuner — the same single tuner experience whether it is opened on its
own or from the Interactive Practice preparation step (for example through a "Tune guitar"
entry point). It uses the currently selected input, shows the input-level status, and has two
modes:

- **Chromatic**: the learner plays any single note, without choosing a string first, and sees
  the detected note and octave, its deviation in cents, and a flat / in tune / sharp indication.
  It works even when no song is open.
- **Guided 6-string guitar**: the tuner shows the six target open-string pitches of a guitar
  tuning, and the learner tunes each string to them. When a supported 6-string guitar track is
  selected, its tuning — for example Drop D — is offered automatically as the default and is
  labelled as coming from the current track, so the learner never re-enters it. When no suitable
  track provides a tuning, the learner chooses one: at least Standard E, Drop D, or a custom
  tuning in which each open-string target can be chosen.

Because the tuner and Interactive Practice interpret pitch the same way, the tuner also shows the
learner that input and detection work. Tuner settings never change the song: choosing a tuning in
the tuner only changes the tuner's guidance, and Interactive Practice always takes its expected
pitches from the score and selected track.

**Why this priority**: Playing in tune with the track is a precondition for useful practice
feedback, and the tuner is the easiest way to confirm the input works; but a learner with an
already tuned guitar can practise without it.

**Independent Test**: With no song open, open the tuner, use chromatic mode, and use guided mode
with Standard E, Drop D, and a custom tuning chosen manually. Then open a song with a Drop D
6-string guitar track and open the tuner from the practice preparation step: it is the same tuner
experience, and Drop D is offered by default and labelled as the track's tuning. Play each open
string: each is identified with note and octave, and flat/in-tune/sharp is shown. Lower the input
level until it is too low, then raise it until it clips: the tuner reports the input problem
instead of confident readings. Change the tuner's tuning and confirm the song's track tuning and
the practice expectations are unchanged. Open the tuner during a practice session: practice
pauses; closing the tuner returns to the same paused event, and practice continues only after the
learner chooses Resume.

**Acceptance Scenarios**:

1. **Given** TuxGuitar is running, with or without a song open, **When** the learner opens the
   tuner without starting Interactive Practice, **Then** the tuner is usable on its own, and
   chromatic mode works without choosing a string.
2. **Given** the learner is in the Interactive Practice preparation step, **When** they choose to
   tune the guitar, **Then** the same tuner experience opens; the learner is never presented with
   two competing tuners.
3. **Given** an input is selected, **When** the tuner is open, **Then** it uses that input.
4. **Given** the tuner is in chromatic mode, **When** the learner plays any single note, **Then**
   it shows the detected note, its octave, its deviation in cents, a flat / in tune / sharp
   indication, and the input-level status.
5. **Given** a supported 6-string guitar track is selected and the learner has not chosen a tuner
   tuning in this application session, **When** they use guided mode, **Then** the track's
   open-string tuning is used, its six target pitches are shown, and it is clearly identified as
   coming from the current track.
6. **Given** no suitable track provides a tuning, **When** the learner uses guided mode, **Then**
   they can choose Standard E, Drop D, or a custom tuning in which each open-string target can be
   chosen.
7. **Given** a track tuning is offered, **When** the learner explicitly chooses another guided or
   custom tuning, **Then** that choice stays the current tuner tuning for the rest of the
   application session — including after the tuner is closed and reopened — until the learner
   changes it again; it is not kept after the application is restarted.
8. **Given** the learner changes the tuner's tuning, **When** they return to the song or to
   Interactive Practice, **Then** the song and the track's tuning are unchanged, and practice's
   expected pitches still come from the score and selected track.
9. **Given** the level is too low or clipping, **When** the learner plays, **Then** the tuner
   indicates the input problem clearly and does not present confident tuning feedback.
10. **Given** the same input conditions, **When** a note is identified by the tuner, **Then**
    Interactive Practice does not interpret it as a different note.
11. **Given** a practice session is active, **When** the learner opens the tuner, **Then**
    practice pauses at its current event; **when** the tuner is closed, **then** the learner
    returns to the same paused event, and practice continues only after an explicit Resume.
12. **Given** the learner used TuxGuitar's tuner before this feature existed, **When** they use it
    now, **Then** its existing behaviour has not regressed.

---

### User Story 5 - Get past events the MVP cannot validate (Priority: P2)

Real guitar tracks contain chords, bends, harmonics, dead notes, and other events this MVP
cannot validate, and a learner can also get stuck on a note. Dead notes, bends, harmonics,
tremolo-bar pitch changes, and trills are passed automatically, with a brief indication where
practical. Chords and other simultaneous multi-note events are significant musical events, so
they stay visible: practice stops on them, explains that they are not supported, and waits for
the learner to Skip. The learner can Skip any current event; skipped events are marked as
skipped and never shown as correct.

**Why this priority**: Needed to practise most real-world tracks end to end, but the core loop is
testable on a single-note melody without it.

**Independent Test**: Practise a track containing a chord, a bent note, a harmonic, and a run of
dead notes: the dead notes, the bend, and the harmonic are passed automatically, briefly
indicated where practical, and never shown as correct; at the chord, practice explains that it
is unsupported and moves on only when Skip is chosen, and the chord is shown as skipped. Skip an
ordinary note and confirm it is shown as skipped, not correct.

**Acceptance Scenarios**:

1. **Given** the current event is a chord, double stop, or other simultaneous multi-note event,
   **When** it becomes current, **Then** the application states that it is unsupported in this
   version, makes Skip clearly available, does not ask the learner to play any single note from
   it, and gives no correct/incorrect judgement.
2. **Given** practice reaches a dead/muted note, **When** it arrives there, **Then** the note is
   passed automatically, briefly indicated where practical, and the learner is not asked to Skip.
3. **Given** practice reaches a bend, harmonic, tremolo-bar pitch change, or trill, **When** it
   arrives there, **Then** the event is passed automatically, briefly indicated as unsupported
   where practical, never shown as correct, and the learner is not asked to Skip.
4. **Given** a multi-note event is current, **When** the learner plays, **Then** the live level
   and detected pitch may remain visible, but no correct/incorrect judgement is given.
5. **Given** any event is current, **When** the learner chooses Skip, **Then** practice moves to
   the next practice event and the skipped event is identified as skipped, never as correct.

---

### User Story 6 - Start from a chosen position (Priority: P3)

The learner wants to work on a later part of the song without playing everything before it. They
place the normal TuxGuitar score cursor where they want to begin, then choose Interactive
Practice; the preparation step shows where practice will start. There is no separate position
picker in this MVP.

**Why this priority**: Convenient, but practising from the start of the track already delivers
the core value.

**Independent Test**: Place the score cursor mid-track, including once inside a repeated
section, start practice, and confirm it begins exactly there and then follows TuxGuitar's
playback order to the end of the track.

**Acceptance Scenarios**:

1. **Given** the score cursor is on a practice event of the selected track, **When** the learner
   chooses Interactive Practice, **Then** the preparation step shows that event as the starting
   position and practice begins there.
2. **Given** the score cursor is on the selected track but not on a practice event, **When** the
   learner chooses Interactive Practice, **Then** the resolved start is the first practice event
   at or after the cursor.
3. **Given** there is no usable cursor position on the selected track, **When** the learner
   chooses Interactive Practice, **Then** the resolved start is the first practice event of the
   track.
4. **Given** the selected position is inside a repeated section, **When** practice starts,
   **Then** it begins exactly there without rewinding to the start of the section, and from
   there follows the order TuxGuitar playback follows.
5. **Given** practice is running, **When** the learner stops practice, **Then** normal editing and
   playback behaviour returns, with the song unchanged.

---

### Edge Cases

- **Rests** and **tied continuations** are not practice events; practice passes over them
  automatically.
- **Dead/muted notes**, and simultaneous events made up only of dead notes (e.g. a muted strum),
  are passed automatically; the learner is never required to Skip them.
- **Simultaneous multi-note events** with at least one pitched note (chords, double stops, notes
  overlapping across voices) are unsupported and wait for Skip.
- **Bends, harmonics, tremolo-bar pitch changes, and trills** are unsupported and passed
  automatically, briefly indicated where practical; they are never validated, and a note carrying
  a bend is never accepted as if its unbent pitch proved the bend.
- **Grace notes** with a usable pitch are normal expected notes; a grace note without a usable
  pitch follows the dead-note rule.
- **Hammer-ons, pull-offs, and slides**: the written sequence of pitches is expected; a legato
  note does not need to be picked again to be accepted, because its pitch differs from the
  previous note.
- **Vibrato**: the underlying expected pitch is validated; the vibrato is not.
- **Tremolo picking**: the written pitch may be validated; the number of attacks, picking speed,
  and rhythm are not evaluated.
- **Repeats and alternate endings** after the starting position follow TuxGuitar's musical
  playback order; starting inside a repeated section does not rewind to its beginning.
- **Tunings and capo**: the expected pitch follows the tuning represented by the selected track
  and any other score information affecting the sounding pitch, such as a capo where
  applicable. Alternate tunings (e.g. Drop D) are supported, not only standard tuning.
- **No song open, or no supported 6-string guitar track selected**: the tuner's chromatic mode
  still works, and guided mode requires the learner to choose a tuning manually.
- **Tuner tuning differs from the track's tuning** (e.g. tuner on Standard E, track in Drop D):
  before practice starts the learner is warned that practice will use the track's tuning (Drop
  D); the warning does not block practice.
- **Learner's guitar out of tune** relative to the track: the tuner and tuner-style deviation make
  the offset visible; the application does not retune or transpose.
- **Signal too weak, clipped, or otherwise unreliable** during practice: warnings appear and
  results are *uncertain*, never definite mistakes.
- **Silence** never validates a note.
- **Other pitched sounds** — speech, humming, singing, other instruments, sound from the computer
  — may occasionally be matched by pitch alone, particularly through a microphone; this is a
  known limitation, not something the feature claims to prevent. Headphones reduce the risk for
  computer sound.
- **Selected track cannot be determined to be a supported 6-string guitar track** (e.g. a 4- or
  5-string bass, a 7-string guitar, a percussion track, a track without a defined pitch for each
  string, or a six-string track that the available information does not identify as a guitar
  with sufficient confidence), **or has no practice events**: practice cannot start and the learner is told why.
  There is no unvalidated mode for other instruments.
- **Song edited, or another song or track selected, during practice**: the session stops.
- **Playback started during practice**: practice pauses at its current event and playback proceeds
  as TuxGuitar normally behaves; playback and practice never run at the same time. When playback
  stops, practice stays paused at the same event, indicates that it is ready to resume, and
  continues only after an explicit Resume.
- **Notes near the ends of the practical guitar range** that cannot be detected reliably give
  *uncertain*, never a false *correct*.

## Requirements *(mandatory)*

### Functional Requirements

**Session and progression**

- **FR-001**: Interactive Practice MUST be available as an explicit user-facing action for an
  open song. Learners MUST be able to start it for one selected track of the open song, and stop
  it at any time. The exact menu, toolbar, panel, or dialog placement is a design/planning
  decision.
- **FR-002**: Choosing Interactive Practice MUST lead the learner through a preparation step in
  which they can review or select the audio input, see the input-level state, calibrate if
  required (FR-038), optionally open the tuner, see the tuning expected by the selected track,
  see the resolved starting position (FR-004), and start practice. The preparation step MUST NOT
  include a separate score-position picker.
- **FR-003**: Interactive Practice is intended for conventional 6-string guitar. A track MUST only
  be able to start practice when the application can determine, from available TuxGuitar
  score/track information and with sufficient confidence, that it is a supported 6-string guitar
  track: at minimum a pitched, fretted tablature track with exactly six strings and a defined
  open-string pitch for each string, in any guitar tuning, containing at least one practice
  event. Having six strings alone MUST NOT be sufficient. Percussion tracks are unsupported.
  Otherwise practice MUST NOT start and the system MUST explain why. There MUST NOT be an
  unvalidated mode for bass or other instruments. Which existing signals establish eligibility
  is determined during technical planning from the real TuxGuitar model; if they cannot
  reliably distinguish a supported 6-string guitar from another six-string fretted instrument,
  six-string tracks MUST NOT silently be treated as supported, and the limitation MUST be
  returned as a product question before implementation.
- **FR-004**: The learner chooses where practice starts by positioning the normal TuxGuitar score
  cursor before choosing Interactive Practice. If the cursor is on a practice event of the
  selected track, practice MUST begin exactly there; if it is on the selected track but not on a
  practice event, at the first practice event at or after the cursor; if there is no usable
  cursor position on the selected track, at the first practice event of the track. The
  preparation step MUST display the resolved starting position. Practice MUST continue to the
  end of the track.
- **FR-005**: Starting inside a repeated section MUST NOT rewind to the beginning of that section.
- **FR-006**: From the starting position onward, practice events MUST follow TuxGuitar's normal
  musical playback order, including repeats and alternate endings encountered after that point.
- **FR-007**: For an expected note, practice MUST NOT advance automatically unless that note has
  been accepted; silence, *uncertain* detection, and *incorrect* pitches MUST keep practice on the
  current note.
- **FR-008**: During preparation and at practice start, the system MUST show the tuning (and capo,
  if any) expected by the selected track.

**Practice events**

- **FR-009**: Practice events — the events practice stops on — are expected notes (FR-010) and
  simultaneous multi-note events (FR-012). Rests and tied continuations MUST NOT be practice
  events and MUST be passed over.
- **FR-010**: A single pitched note MUST be an expected note, including grace notes with a usable
  pitch and notes carrying hammer-on, pull-off, slide, vibrato, palm mute, accent, let ring,
  staccato, or tremolo picking. Its expected pitch is the sounding pitch specified by the score
  for that string and fret — including octave — given the track's tuning and any capo where
  applicable.
- **FR-011**: Dead/muted notes, grace notes without a usable pitch, and simultaneous events made
  up only of dead notes MUST be passed automatically without requiring Skip; the system MAY
  briefly indicate that an unpitched event was passed.
- **FR-012**: Simultaneous multi-note events containing at least one pitched note (including
  events mixing pitched and dead notes) MUST be unsupported practice events that remain visible
  and wait for the learner to Skip. The system MUST NOT select one note from such an event for
  the learner to play.
- **FR-013**: Notes carrying a bend, a harmonic, a tremolo-bar pitch change, or a trill MUST be
  treated as unsupported, MUST be passed automatically without requiring Skip, and MUST never be
  validated or shown as correct. The system SHOULD briefly indicate, where practical, that an
  unsupported event was passed automatically.
- **FR-014**: The current practice event MUST be highlighted in the score and kept visible.
- **FR-015**: For an expected note, the system MUST display its note name with octave and the
  string and fret written in the tablature, labelled as the tablature's instruction.
- **FR-016**: For a multi-note practice event, the system MUST clearly state that the score
  event is unsupported in this version, say what kind of event it is, and make Skip clearly
  available.

**Detection, acceptance, and feedback**

- **FR-017**: The system MUST display, tuner-style, the currently detected note with its octave,
  whether it is flat or sharp, and its deviation in cents from that detected note's own
  equal-tempered centre. The expected note MUST be displayed separately. The system MUST NOT
  display a deviation measured from an expected note of a different semitone.
- **FR-018**: For an expected note, the system MUST present exactly one feedback state at a time:
  *waiting* (no usable playing detected), *uncertain* (signal present but pitch not determined
  reliably enough), *incorrect* (a reliable pitch that does not match), or *correct* (the
  expected pitch reliably detected and accepted).
- **FR-019**: A played pitch MUST match the expected note only in the expected octave; a note
  with the right pitch class in the wrong octave MUST NOT be accepted, and the feedback MUST make
  the octave difference understandable.
- **FR-020**: Pitch acceptance MUST use one fixed tolerance that is not learner-adjustable. A
  slightly out-of-tune performance of the expected note MAY be accepted, with its sharp/flat
  deviation still shown; a neighbouring semitone MUST NOT be accepted. The tolerance MUST be at
  least as permissive as the tuner's "in tune" range: when the detected note and octave match the
  expected note and the tuner would show that pitch as in tune, practice MUST NOT reject the note
  on pitch tolerance alone. Practice MAY still decline to accept it for an independent specified
  reason (wrong octave, uncertain detection, insufficient stability, a required new articulation
  not yet played, or another condition in this specification). The numeric tolerance is
  determined through technical testing and is not part of this specification.
- **FR-021**: A note MUST only be accepted when its pitch is detected reliably, not from a
  momentary reading.
- **FR-022**: One sustained note MUST NOT validate two consecutive expected notes of the same
  pitch; the second requires a new articulation by the learner. How articulation is detected is
  not part of this specification.
- **FR-023**: *Incorrect* feedback MUST show both the expected and the detected note.
- **FR-024**: On acceptance the system MUST show the note as correct and advance to the next
  practice event.
- **FR-025**: For a multi-note practice event, the system MUST NOT give a correct or incorrect
  judgement; the live level and detected pitch MAY remain visible.
- **FR-026**: When the signal is too weak, clipping, or otherwise insufficient for a reliable
  pitch, the system MUST show *uncertain* rather than *incorrect*.
- **FR-027**: The system MUST NOT state or imply that the physical string or fret used, or any
  playing technique or articulation, was verified.

**Running view**

- **FR-028**: While practice is active, the learner MUST be able to see at a glance: the
  currently expected score event, highlighted in the tablature; the expected note and octave;
  the tablature string and fret as instructional information; the detected note; its tuning
  deviation; the current feedback state (*waiting*, *uncertain*, *incorrect*, or *correct*); and
  the current input-level health.
- **FR-029**: The learner MUST NOT need to open or switch to the tuner to see which pitch is being
  detected during practice.

**Skip, completion, and session state**

- **FR-030**: Learners MUST be able to Skip the current practice event explicitly, whether it is
  an expected note or a multi-note event.
- **FR-031**: A skipped event MUST advance practice to the next practice event, MUST be
  identified as skipped for the rest of the session, and MUST NOT be presented as correct.
- **FR-032**: When practice reaches the end of the track — every remaining event having been
  accepted, skipped, or passed automatically — the system MUST tell the learner the session is
  complete and offer Restart or exit.
- **FR-033**: Restart MUST return to the original starting position of that session.
- **FR-034**: Session state (current event, correct and skipped results, completion) MUST exist
  only for the current session and MUST NOT be saved as statistics or history.

**Audio input and level**

- **FR-035**: Learners MUST be able to choose among the available audio inputs from within
  TuxGuitar, including audio-interface inputs and microphones such as the Mac's built-in
  microphone. An audio interface MUST NOT be required. Remembering the choice across
  application launches is not required.
- **FR-036**: The system MUST show a non-blocking recommendation to use headphones when the
  selected input is the Mac's built-in microphone, is identifiable as a microphone, or cannot
  reliably be classified as either a microphone or a direct instrument input. It SHOULD NOT show
  the recommendation for an input positively identified as a direct instrument input. The
  recommendation MUST explain in simple terms that headphones prevent sound from the computer
  being picked up by the microphone and can improve detection, and the learner MUST be able to
  continue without headphones. (What input-type identification is technically possible is a
  planning constraint, not assumed here.)
- **FR-037**: The system MUST show a live input-level indication that distinguishes at least:
  no useful signal / too low, suitable, too high, and clipping.
- **FR-038**: Whenever an audio input is selected or changed, the system MUST automatically
  perform or begin a quick level check while the learner plays, and classify the input as:
  - *suitable* — practice MAY start normally, without a separate calibration step;
  - *usable but suboptimal* — the learner MAY start practice; the system MUST clearly warn that
    detection reliability may be reduced and MUST recommend calibration or gain adjustment;
  - *unusable* (e.g. no meaningful guitar signal, or clipping severe enough that reliable pitch
    detection cannot be performed) — Interactive Practice MUST NOT start until the input becomes
    usable; the system MUST explain the problem and guide the learner towards correcting it.

  A full calibration MUST NOT be required before every practice session. The criteria separating
  suitable, suboptimal, and unusable input are established during technical testing and are not
  part of this specification.
- **FR-039**: Learners MUST be able to request recalibration explicitly. There is one current
  audio-input selection, shared by the tuner and Interactive Practice. Changing it from either
  the tuner or the practice setup MUST make the new input current for both, MUST invalidate the
  previous level-check and calibration result, and MUST cause the new input to be checked again
  before reliable judgements are made. How the shared selection is stored is not part of this
  specification.
- **FR-040**: When the system cannot control the input gain, it MUST still evaluate the level,
  report too low, suitable, too high, or clipping, and guide the learner to the interface's gain
  control or the operating system's input setting.
- **FR-041**: When the system can control the input gain, it MAY offer automatic adjustment;
  manual adjustment MUST remain possible where applicable.
- **FR-042**: The system MUST NOT continuously change input gain while the learner is playing;
  after the level check or calibration, the input level MUST remain stable during note
  evaluation.
- **FR-043**: During practice, the system MUST keep monitoring the input. If it becomes usable but
  suboptimal, the session MUST continue with a warning, and unreliable results MUST be shown as
  *uncertain* rather than *incorrect*. If it becomes unusable for pitch detection, practice MUST
  pause automatically at the current event without advancing, explain the problem, and keep
  monitoring for recovery or let the learner select another input (FR-044 governs resuming).
- **FR-044**: Practice MUST NOT resume automatically after a pause caused by unusable input,
  opening the tuner, changing the audio input, normal playback, or any other condition requiring
  input revalidation. When the condition is resolved (input usable again and, where required,
  checked; tuner closed; playback stopped), practice MUST stay paused at the same current event,
  indicate that it is ready to resume, and continue only when the learner explicitly chooses
  Resume. Changing the audio input during an active session MUST pause practice, invalidate the
  previous level-check/calibration result (FR-039), and require the new input to be checked
  before the learner can Resume.
- **FR-045**: The system MUST NOT imply that microphone input has the same detection reliability
  as direct instrument input unless measurements demonstrate it.

**Tuner**

- **FR-046**: The system MUST present one coherent tuner experience. Learners MUST NOT be
  presented with two competing tuners as a result of this feature. Whether this is achieved by
  extending, reusing, or replacing TuxGuitar's existing tuner is decided during technical
  planning.
- **FR-047**: The tuner MUST be reachable independently of Interactive Practice, so it can be
  used without starting a practice session, and from the Interactive Practice preparation flow
  (for example through a "Tune guitar" entry point).
- **FR-048**: The tuner MUST use the shared current audio input (FR-039) and MUST give access to
  audio-input selection itself, including when no practice session exists.
- **FR-049**: The tuner MUST display the detected note, its octave, its deviation in cents, a
  flat / in tune / sharp indication, and the live input-level status.
- **FR-050**: The tuner MUST provide a chromatic mode: the learner plays any single note without
  selecting a string first and sees the detected note and octave, the deviation in cents, and a
  flat / in tune / sharp indication. Chromatic mode MUST remain usable when no song is open.
- **FR-051**: The tuner MUST provide a guided 6-string guitar mode that shows the six target
  open-string pitches of the selected tuner tuning, for the learner to tune each string.
- **FR-052**: When a supported 6-string guitar track is selected and the learner has not
  explicitly chosen a tuner tuning in the current application session, guided mode MUST use that
  track's open-string tuning and clearly identify it as coming from the current track; the
  learner MUST NOT have to re-enter it. If the selected supported track changes, the default
  guidance MUST follow the newly selected track's tuning. A capo does not change the open-string
  targets.
- **FR-053**: When no suitable track provides a tuning, the learner MUST be able to choose a
  guitar tuning manually, supporting at least Standard E, Drop D, and a custom 6-string tuning in
  which the learner chooses one target pitch for each of the six open strings. Custom tunings
  need not be kept across application launches. The allowed pitch range for custom targets is
  set during technical planning from what TuxGuitar's tuning model represents. Further presets
  MAY be added. How tunings and presets are represented is not part of this specification.
- **FR-054**: Whether the tuner is used independently or opened from the practice preparation
  step, the learner MUST be able to switch to chromatic mode and to choose another guided or
  custom tuning manually, even when a track tuning is offered.
- **FR-055**: A tuning the learner explicitly chooses in the tuner MUST remain the current tuner
  tuning for the rest of the application session, until the learner changes it again; it MUST
  NOT be reset merely because the tuner is closed and reopened, and MUST NOT be kept across
  application launches. It never modifies the song or the pitches Interactive Practice expects.
- **FR-056**: When the level is too low or clipping, the tuner MUST indicate the input problem
  and MUST NOT present confident tuning feedback.
- **FR-057**: Under the same input conditions, a pitch identified as a given note by the tuner
  MUST NOT be interpreted as a different note by Interactive Practice.
- **FR-058**: Opening the tuner while a practice session is active MUST pause practice at its
  current event. Closing the tuner MUST return the learner to the same paused event; practice
  continues only after an explicit Resume (FR-044).
- **FR-059**: Existing TuxGuitar tuner behaviour MUST NOT regress.
- **FR-060**: Selecting or changing a tuning in the tuner MUST change tuner guidance only; the
  tuner MUST NOT modify the song or any track's tuning. The song's tuning changes only through
  TuxGuitar's existing, explicit editing functions outside this feature.
- **FR-061**: Interactive Practice MUST always derive its expected pitches from the score and the
  selected track, never from a tuning chosen in the tuner.
- **FR-062**: If a supported track is selected and the tuner's current guided tuning differs from
  that track's tuning, the difference MUST be made visible before Interactive Practice starts,
  making clear that the tuner is guiding a different tuning and that practice will still use the
  tuning and pitches from the score. This is a warning, not a blocker.

**Input failures**

- **FR-063**: If input access is denied, no usable input is available, the selected device
  disappears, the input format cannot be used, or the signal becomes unusable, the system MUST
  explain the problem in learner terms, MUST NOT crash or freeze, MUST preserve the current
  practice position where reasonable, and MUST let the learner recover or select another input.

**Preservation of existing behaviour**

- **FR-064**: Practice MUST NOT modify the song or mark it as modified.
- **FR-065**: Playback and practice MUST NOT run at the same time. Starting normal playback while
  practice is active MUST pause practice at its current event and let playback proceed according
  to existing TuxGuitar behaviour; when playback stops, practice MUST return to the same paused
  event and resume only after an explicit Resume (FR-044).
- **FR-066**: Editing the song, or changing the active song or track, MUST stop the practice
  session.
- **FR-067**: With practice, input setup, and the tuner all inactive, existing TuxGuitar behaviour
  MUST be unchanged and the feature MUST NOT capture audio.
- **FR-068**: Live audio from the selected input MUST be processed only in memory for immediate
  detection and level assessment, and MUST NOT be saved, kept after use, or sent anywhere. The
  development benchmark recordings (SC-003) are separate development material, not learner
  audio.

**Language and accessibility**

- **FR-069**: All new user-visible text MUST be translatable, with English provided.
- **FR-070**: Feedback and level states MUST be understandable without relying on colour alone.
- **FR-071**: Messages about headphones, low level, clipping, uncertain detection, unsupported
  events, and input failures MUST be written for a learner, without unexplained technical audio
  terminology.
- **FR-072**: Starting, skipping, resuming, and stopping Interactive Practice MUST be operable from
  the keyboard, without using the mouse, following TuxGuitar's existing shortcut conventions, so
  the learner does not have to let go of the guitar. How shortcuts are provided is decided during
  technical planning.

### Key Entities *(include if feature involves data)*

- **Practice Session**: one run of practice on a track — selected track, original starting
  position, current event, state (running, paused, complete), and which events were accepted or
  skipped. Exists only for the session.
- **Practice Event**: an event practice stops on, in playback order — an expected note or a
  simultaneous multi-note event awaiting Skip.
- **Automatically Passed Event**: a dead note, dead-only simultaneous event, grace note without a
  usable pitch, bend, harmonic, tremolo-bar pitch change, or trill — passed without learner
  action, briefly indicated where practical, never validated.
- **Expected Note**: a single pitched note — its score position, expected sounding pitch and
  octave, and instructional string and fret.
- **Unsupported Event**: any event the MVP does not validate — multi-note events (which wait for
  Skip) and automatically passed events — with its position and kind, used to explain why it is
  not validated.
- **Detected Pitch**: the note currently heard — name, octave, deviation in cents from that
  note's centre, and whether it is reliable enough to judge.
- **Feedback State**: waiting, uncertain, incorrect, or correct.
- **Input Selection**: the one current input for the application run, shared by the tuner and
  Interactive Practice, its kind where it can be determined (direct instrument input, microphone, or unknown), and its level-check/calibration
  result.
- **Input Level Status**: the live level (too low, suitable, too high, or clipping) and the
  resulting input assessment (suitable, usable but suboptimal, or unusable).
- **Track Tuning**: the selected track's string tuning (and capo where applicable), stored in the
  song; the only source of practice's expected pitches.
- **Tuner Tuning**: the six open-string targets guided mode currently uses — the selected track's
  tuning until the learner explicitly chooses otherwise, or a chosen preset (at least Standard E
  and Drop D) or custom tuning with one pitch per string, kept for the application session only. It never changes the song or practice's expected
  pitches.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001** *(provisional)*: Starting with a song already open and a guitar already connected
  (or available through a microphone), a learner can attempt the first practice note in under
  1 minute. This includes all setup required before that attempt — selecting an input if
  necessary, the automatic level check, and any correction actually required because the input
  is unusable — but not optional calibration of a suboptimal input, and not optional tuning. Provisional until measured with the
  real application.
- **SC-002** *(provisional)*: When the learner plays the expected note, recognition and
  progression to the next event feel immediate: within 300 ms of the note starting, as perceived
  by the learner. This expresses the intended experience and is not an architecture or
  algorithm requirement.
- **SC-003**: A reproducible detection benchmark is maintained during development. It covers each
  open string; fretted notes across the practical 6-string range; correct notes; neighbouring
  wrong semitones; slightly sharp and slightly flat notes; repeated notes of identical pitch;
  silence; weak input; clipping input; direct audio-interface input; built-in microphone input;
  standard tuning; and at least one alternate tuning such as Drop D. Results are recorded
  separately wherever input type materially changes performance, and quantitative acceptance
  targets are proposed from the measured baseline before release.
- **SC-004**: Silence never, by itself, causes a note to be accepted.
- **SC-005**: For every benchmark recording on which both give a reliable result, the tuner and
  Interactive Practice identify the same note.
- **SC-006**: After any practice session, the song is unchanged and not marked as modified.
- **SC-007** *(provisional)*: A 30-minute practice session remains responsive and does not crash.
- **SC-008**: With the feature never used, existing TuxGuitar behaviour and existing automated
  tests are unaffected.
- **SC-009**: A documented product-owner manual acceptance checklist, covering this
  specification's acceptance scenarios, is completed and passed on the validation target before
  the MVP is accepted. It verifies at least that:
  audio-interface input works; built-in microphone input works; the headphone recommendation
  appears for microphone use; too-low input is detected; a suitable level is recognised;
  clipping is detected; calibration guides the learner to a usable level; a suitable level lets
  the learner continue without a separate calibration step; a suboptimal level allows practice
  with a reliability warning and a calibration recommendation; an unusable input prevents
  practice from starting until corrected; the tuner is usable without starting
  practice and is the same tuner reached from the practice preparation step; chromatic mode works
  with no song open; guided mode offers the current track's tuning labelled as such, and offers
  Standard E, Drop D, and a custom tuning when no suitable track is available; changing the
  tuner's tuning changes neither the song nor practice's expected pitches; a tuner/track tuning
  mismatch is warned about before practice without blocking it; custom tuning lets one pitch be
  chosen per string; the input can be selected from the standalone tuner and a change there
  applies to practice too and triggers a new level check; the preparation step shows the start
  resolved from the score cursor; an input becoming unusable mid-session pauses practice at the
  current event, and after recovery practice waits there for an explicit Resume; closing the
  tuner during a session returns to the paused event and requires Resume; starting playback
  pauses practice and, after playback stops, practice waits at the same event for Resume; Interactive Practice
  can be reached as an explicit action and its preparation step offers every listed step; the
  running view shows the expected event, expected note and octave, string and fret, detected
  note, tuning deviation, feedback state, and input-level health without opening the tuner; the tuner detects all
  six open strings; flat / in tune / sharp feedback is understandable; alternate-tuning guidance
  works for at least one alternate tuning; the tuner and Interactive Practice interpret pitch
  consistently; no learner audio is saved or sent anywhere; Start, Skip, Resume, and Stop work from the keyboard without the mouse; a note the tuner shows as in tune, in the expected octave, is never rejected by
  practice on pitch tolerance alone; silence alone never validates a note; an incorrect note does not advance
  practice; a correct note advances practice; the wrong octave is rejected; two consecutive
  identical notes require two articulations; Skip advances without marking the event correct;
  unsupported multi-note events are not judged and wait for Skip; dead notes pass automatically;
  bends, harmonics, tremolo-bar pitch changes, and trills pass automatically and are not
  presented as validated; no screen claims physical string/fret
  verification; no screen claims technique verification when only pitch was detected; the song
  remains unchanged by practice; stopping Interactive Practice restores normal TuxGuitar
  behaviour.

## Assumptions

Confirmed by the product owner (2026-09-26):

- Validation target is macOS on Apple Silicon (Constitution IX). Other platforms are not claimed
  as supported until validated.
- Supported instrument is 6-string guitar in any tuning represented by the track, validated at
  least in standard tuning and one alternate tuning such as Drop D.
- Supported inputs are an audio interface's instrument input (the reference configuration for
  the best, most reproducible detection) and microphones, including the Mac's built-in
  microphone.
- Note names and octaves use sounding pitch, with A4 = 440 Hz; the low E string of a guitar in
  standard tuning is E2. This applies equally to the tuner and to practice.
- Tuner guidance refers to the guitar's open-string tuning; a capo does not change how the open
  strings are tuned.
- An event made up entirely of dead/muted notes passes automatically; an event mixing pitched
  and dead notes is an unsupported multi-note event awaiting Skip.
- A grace note without a usable pitch follows the automatic-pass rule for unpitched events.
- Hammer-ons, pull-offs, and slides do not require a new pick attack merely because they are
  separate expected pitches; their pitches are validated, not their technique.
- If the cursor is not exactly on a practice event, practice starts at the first practice event
  at or after the cursor position.
- The operating system's default input may be offered first when it is available.
- Opening the tuner during an active practice session pauses practice; any pause needing input
  revalidation, and any pause for normal playback, ends only with an explicit learner Resume.
- Until the learner explicitly chooses another tuner tuning, the guided tuner follows the
  currently selected supported 6-string guitar track's tuning; if the selected supported track
  changes, the default guidance follows that track.
- Changing the audio input during an active practice session pauses practice, invalidates the
  previous input check/calibration result, and requires the new input to be checked before the
  learner can explicitly Resume.
- Changing the selected input, from either the tuner or the practice setup, invalidates the
  previous level-check/calibration result.
- Numeric thresholds for "in tune", practice pitch acceptance, and the suitable / suboptimal /
  unusable input criteria are decided through technical testing, subject to FR-020 (practice
  acceptance at least as permissive as the tuner's "in tune" range).
- The relationship between TuxGuitar's existing tuner implementation and these requirements is
  a technical-planning question; how TuxGuitar represents tuning, capo, and playback order is
  also verified from source during planning.

Other assumptions:

- Feedback is visual only; no audible click, tone, or guide playback is produced.
- Pitch-only validation cannot prove which string, fret, or technique was used, nor that a
  matched pitch came from the guitar (Constitution XIII).
- Out of scope: validating chords or any multi-note event, validating the physical string or
  fret, evaluating playing techniques or articulations, bends, harmonics, tremolo-bar pitch
  changes, trills, rhythm scoring and timing grades, attack counting, amp simulation or effects,
  practice statistics or history, adaptive exercises, machine learning, cloud services,
  instruments other than 6-string guitar (including any unvalidated mode for them),
  learner-adjustable tolerance, continuous automatic gain control, hands-free control (e.g. foot
  pedals), recording or saving the learner's audio, remembering the input device
  across launches, selectable practice ranges, looping, and practising several tracks at once.
