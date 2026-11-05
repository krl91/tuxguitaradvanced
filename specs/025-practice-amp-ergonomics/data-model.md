# Data model: spec 025

No stored format changes. The amp settings (`AmpSoundSettings`: enabled, output, guitar volume, current sound, kept window bounds) and the saved sounds load unchanged.

## Start blocker (in memory, `QuickStart.blocker`)

| Cause | When | Text key | Check again |
|---|---|---|---|
| NO_TRACK | no track chosen | `practice.start.blocker.no-track` | no |
| WAITING_GUITAR | one-click start waiting for the guitar | `practice.quickstart.waiting` (existing) | no |
| NOT_CHECKED | assessment `UNCHECKED` | `practice.start.blocker.not-checked` ("play a few notes") | yes |
| CHECKING | a check is running | `practice.start.blocker.checking` ("listening…") | no |
| NO_SIGNAL | `UNUSABLE` / `NO_SIGNAL` | `practice.start.blocker.no-signal` | yes |
| SATURATING | `UNUSABLE` / `CLIPPING` | `practice.start.blocker.saturating` ("lower the gain, then play") | yes |
| INPUT_LOST | `UNUSABLE` / `DEVICE_LOST` | `practice.start.blocker.input-lost` ("plug the interface back in, then Check again") | yes |
| ACCESS_REFUSED | `UNUSABLE` / `ACCESS_DENIED_SUSPECTED` | `practice.start.blocker.access-refused` ("allow the microphone in System Settings, then Check again") | yes |
| none | the start button can be pressed | — (no line) | no |

## Level re-check rule (`LevelRecheck`)

Inputs: the assessment (status and reason), whether a check is running, whether a run is going, the frame's pitch and confidence. Output: start a check now or not. True only for `UNUSABLE` with `NO_SIGNAL` or `CLIPPING`, no check running, no run going, and a playing frame (pitched, confidence ≥ the assessor's `playingConfidence`).

## Practice tab height (`PracticeLayout.tabHeight`)

Inputs: the tallest tab's packed height, the screen's usable height (768 when unknown or larger), the heights of the title bar, the notice and the bottom area. Output: the folder's height = min(tallest, usable − others), never below one row of controls.

## Amp window tabs

| Tab | Controls |
|---|---|
| Pedals before the amp | the pedal rows before the amp |
| Amp | character, gain, bass, middle, treble, volume, speaker simulation |
| Effects after the amp | the pedal rows after the amp |
| Output | output, guitar volume, score volume |

Above the tabs: the check box *Hear my guitar through the amp*, the sound list with **Save…** and **⋯** (Rename…, Delete, Restore), the delay. Under the tabs: the message line.

**Add pedal** stays a single list (as `AmpPedalRowsSwtTest` reaches it through `getAddPedal()`), under the tabs next to the message line: it offers the missing kinds as today, puts each pedal in its section, and shows that section's tab.
