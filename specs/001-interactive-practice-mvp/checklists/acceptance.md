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

## US4 — Tune the guitar and check detection (T069)

Run 2026-09-27 on the macOS SWT build with the evolved tuner packaged (after the T005 swap and the
three tuner fixes, uncommitted at the time), French UI, checked by the supervising agent with
computer use. A first run found three defects (reopening raised "Index 6 out of bounds for
length 6" then repeated "Widget is disposed"; the first open showed "Input: none"; tuner labels
were English in the French UI); they were fixed with regression tests and this run repeats the
checks.

| Check | Result | Observation |
|---|---|---|
| Tools menu shows one tuner entry, translated ("Accordeur"); tuner labels in French | Pass | |
| First open selects the default input | Pass | "Entrée : Default Audio Device" |
| Choosing Dropped D changes string 6 to D2 | Pass | |
| Close and reopen the tuner: no error | Pass | |
| Explicit choice persists after reopening (FR-055) | Pass | Dropped D still selected |
| Needle dial, rough scale and level status rendered | Pass | |
| Settings dialog limited to input, level status and level re-check (FR-059) | Deferred | Not reported |
| Custom tuning | Deferred | Not reported |
| "Tune guitar" from practice, tuning-mismatch note, practice paused while the tuner is open | Deferred | Not reported |
| Needle moves left when flat, up when in tune, right when sharp, with note, text state and Hz | Deferred | Needs guitar or sound |
