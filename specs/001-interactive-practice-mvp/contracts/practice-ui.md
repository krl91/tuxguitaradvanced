# Contract: Practice and tuner user interface (behavioural)

Built with `app.tuxguitar.ui.*` only (research R17). Layout details belong to implementation;
this contract fixes what must be visible and when. All text comes from the plugins'
`share/lang/messages.properties` (FR-069); states are shown with text/symbols, not colour alone
(FR-070).

## Practice window — preparation (FR-002)

Shows, top to bottom: Practice Track selector listing only compatible candidates, preselecting
the caret's track if compatible (else a guitar-program candidate as an advisory hint), with a
clear "this track will be practised" label; a
an explicit acceptance of the displayed candidate as "the guitar part to practise" (may be the
clearly labelled start/accept control itself; required even for a preselected or
guitar-program track; no extra step for a track already accepted in this run); a clear reason when no candidate exists (FR-003); track open-string tuning, numeric offset and required realization — "play in this tuning",
"use a capo at fret k (capo-equivalent)" or "tune every string down |k| semitone(s)" — worded as
what Practice needs, not as the file author's intent; non-zero offsets always show the raw
value and the resulting physical open-string targets; a non-blocking "unusual setup" notice
(tuning outside TuxGuitar's built-in 6-string guitar presets — please verify the track setup)
only for negative offsets whose targets fall below those presets (FR-008); current input + selector listing one entry per mono device and per usable channel of
multi-channel devices, e.g. "Scarlett 2i2 — Input 2" (FR-035); a "try another input channel"
hint when the selected channel has no signal (never an automatic switch); live level meter with state (FR-037); assessment
result and, if needed, calibration/gain guidance (FR-038–FR-041); headphone recommendation when
applicable (FR-036); "Tune guitar" button (opens the tuner); tuner/track tuning mismatch warning
(FR-062); resolved start position (FR-004); Start (disabled while input is unusable).

## Practice window — running (FR-028, FR-029)

Always visible: expected note + octave; string/fret labelled as the tablature's instruction;
detected note + octave + cents with sharp/flat; feedback state (waiting / uncertain / incorrect /
correct, with the octave hint for octave-only errors); input-level health; buttons Skip, Stop;
Resume when ready to resume. Multi-note events: "not supported in this version" + Skip.
Auto-passed events: brief non-blocking indication. Completion: message + Restart / Close.
The score location is shown with a practice highlight separate from the normal cursor, which the
learner may move freely; the view scrolls to the event when it changes (FR-014, research R18).

*Amended 2026-09-27 (User Story 7, FR-073–FR-076)*: while practice runs the window above is
hidden and replaced by the **practice palette** — a compact, movable, non-modal window with: the
detected note (or chord candidates) with cents and confidence; one status line (feedback naming
the expected note, or for a chord the missing/foreign pitches); input-level health; Skip, Stop,
Resume, Restart, Tune guitar. Practice shortcuts work while it has focus. Stop closes it and shows
the preparation window again. The expected event is shown in the score itself: a red frame around
the note, or around the whole chord, on the score staff and the tablature; the accepted event is
framed green for a short provisional time; the view scrolls to the framed event.

## Tuner window (evolved existing tuner, FR-046–FR-060)

Preserved from the existing tuner (FR-059 regression baseline): Tools-menu entry, same tuner
window, live pitch feedback with Hz readout, fine and rough needle widgets, clickable string
targets. Settings: device/channel, level status, level check/recalibration only — no FFT size,
buffer, sample rate/size, noise-gate or other detector-internal controls.

Mode switch Chromatic / Guided; input selector + level status (available without any practice
session); note, octave, cents, flat / in tune / sharp; guided: six targets, tuning source label
("from Practice Track", "from track <name>" for a guitar-confirmed track — showing the physical
open-string targets and realization, e.g. "tuned down 1 semitone" — or preset/custom name;
Standard E by default); a guitar MIDI program may be shown as a hint but never selects the track
tuning by itself; preset list (at least Standard E and Drop D from `TuningManager`), custom editor (one
pitch per string, limits from the TuxGuitar tuning model). Too-low/clipping → input problem
shown instead of a confident reading.

## Update cadence

UI receives coalesced snapshots at most ~30 per second via `TGSynchronizer.executeLater`;
the UI thread never waits on audio or analysis threads.
