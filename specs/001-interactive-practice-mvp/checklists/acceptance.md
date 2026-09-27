# Manual Acceptance Checklist: Interactive Practice MVP

Results are recorded exactly as executed (constitution XII). "Deferred" means not yet run.
Validation target: macOS Apple Silicon, `tuxguitar-macosx-swt-cocoa` build, Temurin 21.

## US1 — Practise a melody note by note (T049)

Run 2026-09-27 on the build of commit `2f0e0b91c`, launched from the packaged `.app`, checked by
the supervising agent with computer use (no guitar input).

| Check | Result | Observation |
|---|---|---|
| Tools › Interactive Practice opens one non-modal window | Pass | |
| Blank song: no practicable track reported | Pass | |
| `misc/tuxguitar.tg`: track 1 "Guitar" preselected, standard tuning shown (E2 A2 D3 G3 B3 E4) | Pass | |
| Input list shows the default input | Pass | "Default Audio Device" |
| Input level check concludes suitable; Start enabled only then | Pass | |
| Running view: expected note, tablature instruction, detected reading | Pass | "Play E2", string 6 fret 0, "Heard -" |
| Stop returns to the preparation view | Pass | |
| F2 opens / brings the window to front; ⌘⇧S/K/R/X while focused | Deferred | Not reliably testable with computer use |
| Correct guitar note advances the highlight; wrong note, wrong octave, silence do not | Deferred | Needs guitar and microphone |
| Same-pitch notes: sustain does not validate, re-pluck does | Deferred | Needs guitar and microphone |
| Practice highlight separate from the cursor; cursor still movable | Deferred | Needs a running session with playing |
| MIDI playback pauses practice; Resume after stopping playback | Deferred | |
