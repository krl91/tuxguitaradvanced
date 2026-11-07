# Contract: the GuitarFX application

## Main window (fits 1024×768; remembers its place and size)

```text
┌ GuitarFX ─────────────────────────────────────────────────────────────────┐
│ Input [Rocksmith Guitar Adapter ▾] Channel [1 ▾]   Output [Speakers ▾] [⟳] │
│ [ Sound on ]   Volume ━━━━●━━━━   Input ▮▮▮▮▯▯ (clip)   Delay ≈ 9.8 ms (estimated) │
│ Sound [Crunch (changed) ▾] [Save…] [⋯ Rename… Delete Restore Export… Import…] │
│ ┌ Before the amp ┬ Amp ┬ After the amp ┐                                   │
│ │ ☑ Gate        threshold ━━●━━ -60 dB   release ━●━━ 150 ms   [↑][↓][✕]  │
│ │ ☑ Compressor  …                                                          │
│ │ [+ Add a pedal ▾]                                                        │
│ └──────────────────────────────────────────────────────────────────────────┘
│ [Advanced…]                                         message line           │
└───────────────────────────────────────────────────────────────────────────┘
```

- **The devices**: the lists show the system's devices; ⟳ refreshes them (FR-003).
- **The sound control**:
  - *Sound on* is off at every start (FR-009).
  - Turning it on fades in over the fade-in parameter (FR-010).
  - With a built-in microphone as input, it first shows the feedback warning (FR-012).
- **The chain**:
  - Three tabs, as TuxGuitarAdvanced's *Amp and effects* window: before the amp, the amp (character, five knobs, cabinet switch), after the amp.
  - A row per element: switch, knobs as sliders with value and unit, move up, move down, remove.
  - Moves stay within the section.
- **The sound list**:
  - Ready-made sounds first, then own sounds in alphabetical order.
  - "(changed)" after a changed sound.
  - *Restore* only on a changed ready-made sound; *Rename…* and *Delete* only on own sounds.
- **Tooltips**: every control has one in three languages, saying what it changes, its unit and its default (FR-018).

## *Advanced…* window (FR-025)

- **Categories** in a drop-down list, the same pattern as TuxGuitarAdvanced's recognition settings (spec 026), at most 8 rows each:
  - *Output and safety* (ceiling, fade in and out, release);
  - *Feedback guard*;
  - *Devices* (block size, input ring);
  - *Smooth changes* (crossfade, glide);
  - *Overload* (blocks before lightening);
  - then one category per element whose internal values are parameters (*Gate*, *Compressor*, *Drive*, *Amp*, *Cabinet*, *Chorus*, *Echo*, *Reverb*).
  - The exact list comes from `inventory.md`.
- **A row**: name, slider and value, unit, "default X", and a tooltip.
- **Buttons**: *Defaults* (asks for confirmation) and *Close*.
- **A line at the bottom**: "Changes take effect the next time the sound is turned on." This is shown while the sound is on.

## Messages (message line, and a dialog for the first three)

| Event | Message (English; French and Spanish in `lang/`) | Sound |
|---|---|---|
| feedback detected | "Howling detected: the sound was turned off. Use headphones, or move away from the speakers." | muted (SC-005) |
| built-in microphone | "With the built-in microphone, the speakers can howl. Headphones are advised." [Turn on anyway] [Cancel] | off until confirmed |
| permission denied | "GuitarFX may not use the microphone. Allow it in the system's privacy settings, then turn the sound on again." | off |
| device lost | "The input (or output) device is gone. Choose a device to go on." | off |
| unsupported format | "This device's format is not supported: choose another device." | off |
| overload | "Your computer cannot keep up: the reverb was turned off to lighten the sound." (then the chorus, the echo…, as `MonitorEngine.LIGHTER`) | lighter |
| input clipping | "The input clips: lower the input gain of your interface or of the system." | unchanged |
| a second instance | "GuitarFX is already open." | the second instance quits |
| a newer or damaged settings file | contracts/settings-files.md | defaults |
| always (no detection; clarification 2026-11-07) | "Bluetooth headphones add their own delay, not counted here." beside the delay | unchanged |

## Headless start (CI)

`guitarfx --check` starts without opening a window or a device. It loads the settings, the texts and the registry, builds the chain at 48 kHz, processes one second of silence, prints `ok`, and exits with 0. Every CI system runs it after installing the package.
