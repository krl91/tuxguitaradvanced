# Contract: the amp window (spec 025; replaces the layout of spec 022's contracts/amp-window.md)

## Layout

```
┌─ Amp and effects ───────────────────────────────────────┐
│ ☑ Hear my guitar through the amp        Delay ≈ 20 ms   │
│ Sound: [ Lead            ▾ ]  [ Save… ] [ ⋯ ]           │  ⋯ = Rename…, Delete, Restore
│ [Pedals before the amp] [Amp] [Effects after the amp] [Output]
│ ┌──────────────────────────────────────────────────┐ ▲ │  each tab scrolls vertically
│ │ (the tab's controls)                             │ █ │
│ └──────────────────────────────────────────────────┘ ▼ │
│ [ Add a pedal ▾ ]   <message line>                      │
└─────────────────────────────────────────────────────────┘
```

At the first opening (no kept bounds) the window is at most 600 px high; kept bounds are brought within the screen (768 px reference).

## Accessors used by the tests

| Accessor | Type | Note |
|---|---|---|
| `getSwitch()` | `UICheckBox` | was `UIToggleButton` (owner's exception, research R6) |
| `getRows().getCabinet()` | `UIToggleButton` | unchanged type, text "Speaker simulation" |
| `getSaveAs()` | `UIButton` | text "Save…"; opens the name prompt |
| `getMore()` | `UIButton` | "⋯"; opens the menu |
| `getRename()`, `getDelete()`, `getRestore()` | `UIMenuActionItem` | were buttons (owner's exception) |
| `getNamePrompt()` | `AmpNamePrompt` | its text field and OK; the old `getSoundName()` field is gone (owner's exception) |
| `getTabs()` | `UITabFolder` | four tabs in the order of the layout |
| everything else (`getOutputs()`, `getDelay()`, `getMessage()`, `getWarning()`, `getRows()...`) | unchanged | |

## Rules kept from spec 022

Every enable rule of Save as / Rename / Delete / Restore, the name rules (`AmpSoundStore.NameProblem`: 1 to 40 characters, not a ready-made name), the built-in microphone warning, the message line's events, the placement beside the notes during a run.
