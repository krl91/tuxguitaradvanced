# Contract: Actions, menu entries and keyboard shortcuts

Registered through existing extension points only (research R2, R14).

| Action id | Registered by | Menu | Key-bindable | Sync thread | Default shortcut |
|---|---|---|---|---|---|
| `tuner.tuner` (existing id, via `TGCustomTool`) | macOS SWT: evolved tuner `TuxGuitar-tuner-next` (module id `tuxguitar-tuner`); other builds: legacy tuner, unchanged | Tools › tuner item | yes (existing) | yes | none (unchanged); a user's existing binding carries over (research R19) |
| `practice.open` | practice plugin (`TGToolItemPlugin`) | Tools › "Interactive Practice…" | yes | yes | added in Phase 1 |
| `action.practice.start` | practice plugin (`mapAction`) | practice window button | yes | yes | added in Phase 1 |
| `action.practice.skip` | practice plugin | practice window button | yes | yes | added in Phase 1 |
| `action.practice.resume` | practice plugin | practice window button | yes | yes | added in Phase 1 |
| `action.practice.stop` | practice plugin | practice window button | yes | yes | added in Phase 1 |

Rules

- Registration: `TuxGuitar.getInstance().getActionManager().mapAction(id, action)`,
  `TGActionAdapterManager.getKeyBindingActionIds().addActionId(id)`,
  `getSyncThreadInterceptor().addActionId(id)`; undone in `disconnect`.
- Actions only post commands to the session queue; they never mutate session state directly.
- The practice window calls `KeyBindingActionManager.appendListenersTo(control)` so shortcuts
  work while it has focus (as `TGFretBoard`/`TGPiano` do).
- Default combinations are added to the shared `desktop/TuxGuitar/dist/tuxguitar-shortcuts.xml`
  and, because macOS builds replace that file with their own copy, to
  `desktop/build-scripts/common-resources/common-macosx/Contents/MacOS/dist/tuxguitar-shortcuts.xml`
  (`Command` instead of `Ctrl`); both are inert on builds without the plugin (research R14 and its
  T028 correction). Approved defaults: `F2` for `practice.open`; `Ctrl Shift` (macOS: `Command Shift`)
  + `s` / `k` / `r` / `x` for start / skip / resume / stop. They must not collide with existing defaults (digits, `Space`, `F9`,
  `Ctrl t`, …); collisions are checked in Phase 1. Users can reassign them in Tools › Shortcuts
  (existing editor).
- The practice plugin opens the tuner by processing the tuner's existing action id — no class
  coupling to tuner UI classes, one tuner experience (FR-046).
- None of these actions is a document modifier; the song is never marked modified (FR-064).
- The practice highlight uses the existing external-beat API
  (`TGExternalBeatViewerManager.showExternalBeat(beat)` / `hideExternalBeat()`), which the
  tablature is extended to honour (research R18). The practice plugin never moves the normal
  cursor; `hideExternalBeat()` is called whenever the session stops.
