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

## Cross-cutting — build without the practice plugin (T081)

Run 2026-09-27 by the product owner on a temporary copy of the macOS SWT `.app` built from commit
`b7639d4d2` with only `share/plugins/tuxguitar-practice.jar` removed (distinct bundle identifier
`app.tuxguitar.swt-validation`; `lib/` and `dist/tuxguitar-shortcuts.xml` unchanged, so the five
practice defaults were present but unregistered). This replaces the macOS JFX route, which stayed
blocked at launch (tasks.md T081).

| Check | Result | Observation |
|---|---|---|
| Starts without error; normal toolbar and left controls | Pass | |
| Tools menu has no Interactive Practice | Pass | |
| Evolved tuner opens and works | Pass | |
| Tools › Shortcuts: searching "practice" lists no rows | Pass | |
| F2 and ⌘⇧S/K/R/X do nothing | Pass | |
| Saved user `shortcuts.xml` contains no practice ids | Deferred | Not run. Source inspection predicts a pass: `TGKeyBindingEditor` lists only registered key-binding action ids and `save()` writes that list |
| `misc/tuxguitar.tg` renders normally | Pass | |
| Playback with the red played-beat highlight, stops cleanly | Pass | |

## Full SWT build — owner's GP3 file (2026-09-27)

| Check | Result | Observation |
|---|---|---|
| `knockin_on_heaven_s_door_5.gp3` opens | Pass | Tracks Guitar 1, Guitar 2, Guitar 3, Bass, Drums |
| Interactive Practice opens and preselects Guitar 1 | Pass | |

## Pending — 2026-09-28 changes (T097, T098, T099, T092/T093, T100/T101)

To run on the macOS SWT build (`desktop/build-scripts/tuxguitar-macosx-swt-cocoa/target/...app`).
Visual rows (V) need a display; guitar rows (G) need the guitar and the input. Result column is
empty until run. Nothing here has been executed yet.

| # | Kind | Check | Expected | Result |
|---|---|---|---|---|
| P1 | V | Select the microphone, then another input, then back (T097, FR-084) | Start / Tune guitar / Close stay visible without resizing; long lines wrap; window stays inside the main window | |
| P2 | V | Preparation window, passage controls (FR-091) | "Repeat passages" (Off / Every measure / Every 2 measures / Every 4 measures, default 2) and "Repeat when errors exceed" (25 / 50 / 75 %, default 50) plus the hint fit without resizing; the threshold greys out on Off | |
| P3 | V | Change both passage controls, close and reopen Interactive Practice; then relaunch TuxGuitar | Choice kept while TuxGuitar runs; back to 2 measures / 50 % after relaunch | |
| P4 | V | Start with passages on | Palette shows "Passage n of N (bars a–b) · attempt 1 of 3 · errors 0 of j"; all buttons visible | |
| P5 | V | Start with passages Off | No passage line; practice as before | |
| P6 | V | Tuner: play a string and let it decay; then change mode (T098, FR-085) | Grey, still needle and "⏸ Last reading (held)"; cleared on mode change | |
| P7 | G | Single notes: pluck each note once; hold a note while the next is expected; silence; tap the guitar body (T099, FR-086) | Each clean pluck accepted with green ✓; sustained note, silence and tapping never advance; report false waits | |
| P8 | G | Chords open E, A, D, G, C, Am, Em strummed correctly; E major without the G#3 string; Am played on an E chord (T092/T093, FR-087) | Correct → ✓ and advance; missing G#3 → ✗ naming G#3; Am → ✗; silence → nothing; report false waits/accepts | |
| P9 | G+V | Passages 2 measures / 50 %: play a passage with more than half the notes wrong or skipped (T100) | At its last note the red frame and view jump back to its first note; "↻ Again … (over 50 %)"; the last note ringing does not accept the first; the score flashes green again on re-accepted notes | |
| P10 | G+V | Same passage with exactly half wrong/skipped; then fail it three times | Half → moves on ("Last passage passed"); third failure → "» Moving on after 3 attempts" | |
| P11 | G+V | Repeat 25 % and 75 % once each; finish the song | Retry decisions follow the chosen threshold; completion shows the summary (first time / after retries / not passed with bars / retries); Restart clears it | |
| P12 | G | A section inside repeat signs: fail its second occurrence only | Only the second occurrence is repeated | |
| P13 | V | Tools › Settings › Audio recognition (T102, FR-092): open, go through every category | Page present and in the interface language (French: « Reconnaissance audio », « Catégorie de réglages »); each category fits without resizing and without controls outside the window; every setting appears exactly once; each slider updates its own value text while it moves and never moves another slider; tooltips on names and sliders show effect, range and recommended value; restore buttons « Rétablir les valeurs recommandées de cette catégorie » / « Rétablir tous les réglages de reconnaissance audio » distinct from « Valeurs par défaut » | **Échec (2026-09-28, owner, macOS SWT) — en attente d'un nouveau test.** Observed: page entirely in English in a French TuxGuitar; ambiguous « Section »; restore buttons hard to tell apart; slider handles moved but the value text stayed frozen, and moving one slider sent the other to the far left (Minimum usable level / High level warning). Fixed in T103 (not yet re-tested visually). |
| P14 | V | Keyboard only: Tab through the page, change a slider with the arrow keys, a list, a check box | Every control reachable and changeable from the keyboard | |
| P15 | V | Change values, Cancel; reopen | Previous values shown (nothing saved) | |
| P16 | V | Change values, OK; relaunch TuxGuitar; reopen | Values kept after relaunch | |
| P17 | V | "Restore recommended values of this section", then "Restore all recommended values", OK | Section / all values back to recommended; the Settings dialog's Defaults button also restores them | |
| P18 | V | Open and close the Settings dialog 10 times, switching sections | No error, no growth of the window beyond the screen | |
| P19 | V+G | With the tuner open, change "In-tune range" and "Keep the last note", OK | Tuner follows at once | |
| P20 | G | Raise "Minimum usable level" to −25 dBFS while the tuner listens | Brief restart, then quiet playing shows "too low"; restore → normal | |
| P21 | V | Change passage values in Settings with the practice window open | Its passage lists show the new values; Help › Audio recognition page opens from the sidebar | |
