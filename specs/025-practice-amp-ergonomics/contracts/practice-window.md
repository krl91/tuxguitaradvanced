# Contract: the practice window (spec 025)

## Layout

```
┌─ Interactive practice ──────────────────────────────────┐
│ [Track] [Input] [Mode] [Passages] [Advanced]            │
│ ┌──────────────────────────────────────────────────┐ ▲ │  each tab's content scrolls vertically
│ │  (the tab's controls)                            │ █ │  the folder's height: PracticeLayout.tabHeight
│ └──────────────────────────────────────────────────┘ ▼ │
│ (engine notice, spec 023, when there is one)            │
│─────────────────────────────────────────────────────────│  fixed bottom area, always in view
│ ⓘ <blocker line>                    [ Check again ]     │  shown only while the start is greyed
│      [ Amp and effects… ] [ Practise this track ] [Close]│
└─────────────────────────────────────────────────────────┘
```

## Behaviour

- The blocker line is `startLabel`; `updateStartButton` keeps calling `QuickStart.greyedReason(` and writes with `this.startLabel.setText(` (committed `GreyedStartExplainedTest`).
- **Check again** is visible only when `QuickStart.blocker(...)` says it applies; it calls `engine.runLevelCheck()`, which does nothing while a check runs.
- The line changes as soon as the assessment, the track choice or the check state changes; it is hidden when the start can be pressed.
- At opening and on every refit, the window's height fits the usable height of the screen holding it (768 px reference); kept bounds taller than the screen are brought within it.
- The tests of `PracticeWindowStartPathSourceTest` (start path), `QuickStartTest`, `WindowFitTest` stay as they are.
