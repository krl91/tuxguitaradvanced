# GuitarFX guide

GuitarFX turns your computer into a guitar amp with effect pedals: the noise gate, compressor, overdrive, distortion, the amp (clean, crunch or high gain, with bass, middle and treble), the cabinet, chorus, echo and reverb. It is the sound of TuxGuitarAdvanced's *Amp and effects* window, in a small program of its own that needs neither TuxGuitarAdvanced nor Java. It comes as an application and as a plugin for music software (CLAP, VST3, and Audio Unit on macOS), on macOS, Windows and Linux.

French: [guide_fr.md](guide_fr.md). Spanish: [guide_es.md](guide_es.md). Every internal value: [parameters.md](parameters.md).

## Install

The files are on the release page, next to TuxGuitarAdvanced's.

- **macOS** (Apple Silicon, macOS 15 or later): open `GuitarFX-<version>-macos-arm64.pkg`. GuitarFX is not signed by an Apple developer, so macOS refuses it the first time: open *System Settings > Privacy & Security* and choose *Open Anyway*. The installer places `GuitarFX.app` in *Applications* and the plugins in `/Library/Audio/Plug-Ins` (`VST3`, `CLAP`, `Components`). The first time the sound is turned on, macOS asks to let GuitarFX use the microphone: allow it.
- **Windows** (64-bit): run `GuitarFX-<version>-windows-x86_64-setup.exe`. Windows may warn that the publisher is unknown: choose *More info*, then *Run anyway*. GuitarFX goes to `Program Files\GuitarFX`, with a Start menu entry; the plugins go to `Common Files\VST3` and `Common Files\CLAP`.
- **Linux** (x86-64):
  - Debian, Ubuntu and their relatives: `sudo apt install ./guitarfx_<version>_amd64.deb`. The program is `/usr/bin/guitarfx` (also in the applications menu); the plugins go to `/usr/lib/vst3` and `/usr/lib/clap`.
  - Any other system, for your user only: unpack `GuitarFX-<version>-linux-x86_64.tar.gz` and run `GuitarFX/install.sh`. The program goes to `~/.local/bin/guitarfx`, the plugins to `~/.vst3` and `~/.clap`.

Installing a newer version over an older one keeps your settings and your sounds.

## The devices

At the top of the window:

- **Input**: the device your guitar is plugged into (an audio interface is best), and its **Channel** (1 for the first input).
- **Output**: where you hear the sound. **Headphones are advised.**
- **⟳** looks for the devices again after you plug one in.

GuitarFX remembers the devices by name. If one is missing at the next start, it is shown as *(missing)* and the sound stays off until you connect it or choose another one.

**Sound on** plays your guitar through the amp. It is off at every start, and turning it on fades the sound in. **Volume** sets the output level; the **Input** meter shows your guitar's level, and *clip* means the input is too loud: lower the input gain of your interface or of the system.

## The chain

The chain is in three tabs, in the order the sound goes through them:

1. **Before the amp**: the pedals that shape the guitar's signal, such as the noise gate, the compressor, the overdrive or the distortion.
2. **Amp**: its **Character** (clean, crunch or high gain), its knobs (gain, bass, middle, treble, volume) and the **Cabinet**, the speaker's colour (off, the amp sounds bright and raw, as through a line output).
3. **After the amp**: the pedals that colour the amp's sound, such as the chorus, the echo or the reverb.

Each pedal has a row: its switch (off, the sound passes through it unchanged), its knobs, and buttons to move it earlier or later within the tab, or to take it out. **+ Add a pedal** adds one at the end of the tab; each pedal can be in the chain once. Leave the pointer over a knob to read what it does, its range and its default. Every change is heard at once, without a click.

## Sounds

The **Sound** list holds the four ready-made sounds (Clean, Crunch, Lead, Clean echo), then yours. *(changed)* means the chain differs from the saved sound.

- **Save…** keeps the chain as one of your sounds, under a name you choose.
- **⋯** gives **Rename…**, **Delete** (the chain in use stays as it is), **Restore** (a ready-made sound back as it was shipped), **Export…** and **Import…**.

A sound can be exported to a `.guitarfx` file and imported in GuitarFX or in TuxGuitarAdvanced's *Amp and effects*, both ways. If you already have a sound of that name, you choose to replace it or to keep both.

## The delay

Under the devices, GuitarFX shows the estimated delay between your playing and the sound: the devices' blocks and their declared latencies, added up. The delay you hear can be a little longer. An audio interface with small blocks gives the shortest delay; Bluetooth headphones add their own delay, which is not counted, and is usually too long to play with.

## Headphones and feedback

With speakers, and above all with the computer's built-in microphone, the sound can loop back into the microphone and howl. GuitarFX advises headphones when the input is a built-in microphone, and asks before turning the sound on. If a howl starts, it turns the sound off and tells you so: use headphones, or move away from the speakers, then turn the sound on again.

If your computer cannot keep up, GuitarFX lightens the sound step by step (the reverb off, the chorus off, less detail in the amp, then only the amp and the cabinet) and tells you each step. If even the amp alone is too much, it turns the sound off: close other programs, then turn the sound on again.

## The Advanced window

**Advanced…** shows every internal value of the sound (103, in 19 categories): the limiter's ceiling, the fades, the feedback guard, the devices' block, each element's inner values. The defaults are the tested ones; **Defaults** puts them all back, after asking. A change takes effect the next time the sound is turned on. [parameters.md](parameters.md) lists them all, with their ranges.

## The plugin

The plugin is the same chain, output stage and feedback guard, inside your music software (a DAW): add **GuitarFX** as an effect on a track with your guitar. Its window has the same three tabs and the same sound list, and the project keeps the chain.

- Formats: **CLAP** and **VST3** on the three systems, and **Audio Unit** on macOS.
- It takes a mono or a stereo track (a stereo input is mixed to mono) and gives stereo.
- It tells the host its delay (45 samples), so the host can line it up with the other tracks.
- It has no volume of its own: use the host's fader.
- If it detects feedback, or if the computer cannot keep up even with the amp alone, it mutes itself and says so in its window. Fix the cause, then remove GuitarFX and add it again, or reopen the project.
- **Its internal values come from the computer's settings**: the plugin reads the values of the *Advanced* window (the file `settings.properties` of GuitarFX's settings folder) each time the host starts it. A project opened on another computer uses that computer's values; the chain itself travels with the project.

## Uninstall

Uninstalling removes the program and the plugins; **it keeps your settings and your sounds** unless you ask.

- **macOS**: in Terminal, `/Applications/GuitarFX.app/Contents/Resources/guitarfx-uninstall` (add `--remove-settings` to remove `~/Library/Application Support/GuitarFX` too). It asks for your password.
- **Windows**: *Settings > Apps*, GuitarFX, *Uninstall*. The uninstaller asks whether to remove your settings and sounds (`%APPDATA%\GuitarFX`).
- **Linux**: `sudo apt remove guitarfx` for the .deb; `GuitarFX/install.sh --uninstall` for the tar.gz (add `--remove-settings` to remove `~/.config/guitarfx` too).
