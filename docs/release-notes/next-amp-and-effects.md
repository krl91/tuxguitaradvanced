<!-- Draft for the next version (spec 022, T057). The version number is chosen at release time; nothing is released before the owner's gate (T060, sheet F12). Rename this file to v<version>.md then. -->

## 🎸 TuxGuitarAdvanced <version> — hear your guitar through an amp and pedals while you practise

TuxGuitarAdvanced adds **practice modes** on top of [TuxGuitar](https://www.tuxguitar.app/), the free multitrack tablature editor: it listens to your real guitar, checks the **pitch of the notes and the chords** you play against the tablature, and lets you practise note by note, in time (Follow, Arcade) or waiting for the right note.

### 🔖 About this release

Every feature is in all three packages (macOS, Linux and Windows), with the same behaviour. The macOS package still needs **macOS 15 Sequoia or later**.

> 🙏 **Windows and Linux users: your feedback is needed.** The author has no Windows or Linux computer at hand, so these packages are built by the CI but **have never been run**. This release plays your guitar back through the application with its own native audio code on each system, which has never been tried on a real Linux or Windows machine. If you try a package, please open an [issue](https://github.com/krl91/tuxguitaradvanced/issues) with your system, its version and your audio interface, and tell:
>
> 1. **The delay** — open *Amp and effects…* in the practice window, turn on *Amp sound* with your guitar plugged in and headphones on, and write down the value of the **Delay** line. Does the delay between the string and the sound bother you?
> 2. **No clicks** — pick the four sounds and move the knobs while playing: each change should be heard at once, without a click.
> 3. **A howl** — with a microphone and speakers (no headphones), turn the sound on and raise the volume: the howl should be cut within half a second, with a message.
> 4. If you can, start the application from a terminal and copy the line that begins with `TuxGuitar amp sound:` (it says whether the audio thread got real-time priority).
>
> The full checklist is the sheet [F12](https://github.com/krl91/tuxguitaradvanced/blob/master/specs/test-sheets/F12-ampli-effets.md), rows **X1** to **X4**.

### ✨ What is new

* **Your guitar through an amp, with the score.** While the practice window is open, the new **Amp and effects** window plays your guitar through a virtual amp (*Clean*, *Crunch* or *High gain*, with gain, bass, middle, treble and volume) and its cabinet, mixed with the score. It is **off by default**.
* **Effect pedals:** noise gate, compressor, overdrive and distortion before the amp; chorus, echo and reverb after it. Add, remove, switch and reorder them; every change is heard at once, without a click.
* **Sounds:** four ready-made sounds (*Clean*, *Crunch*, *Lead*, *Clean echo*) and your own, saved, renamed and deleted. A changed ready-made sound shows *(changed)* until *Restore*.
* **A sound per score:** the sound you pick comes back each time you practise that score, before any run.
* **Safe by default:** a warning before the sound starts with the built-in microphone, a howl cut within half a second, a lost device that stops the sound while the practice goes on, a chain made lighter step by step if the computer cannot keep up, and an output never above −1 dBFS.
* **The judging never changes:** the amp sound has its own input; the recognition hears exactly the same thing with the sound on or off.
* Texts and tooltips in English, French and Spanish; the user guide has a new section.

### ✅ What was checked, and by whom

* **Tests on the author's Mac:** the full macOS build is green: 1,420 practice tests and 438 audio-engine tests, 0 failures; all 75 tests on the real SWT toolkit pass; the recognition gives exactly the same results on the datasets with the sound on and off.
* **The delay on the author's Mac**, measured with clicks through the native audio code: about **33 ms** with a USB microphone into the built-in speakers (128-frame blocks), **61 ms** with the built-in microphone (its own hardware adds most of it). With the Rocksmith cable and headphones: <T060>.
* **Ten minutes** of the heaviest chain on a real-time audio thread without a single dropped block; the chain uses about 4 % of each block.
* **By the author, on macOS:** <T060, sheet F12>.
* **Not checked by anyone yet:** the delay, the clicks and a howl on real **Linux and Windows** machines; their packages were never run (built only).

### 🚧 What is **not** fixed

* Notes and chords whose **attack is not heard** (a soft note, a low input level) are still refused.
* A chord with **one string missing** can still be accepted.
* The amp sound is offered only while the practice window is open; it is not recorded, and the bass uses the same chain as the guitar.
* **Bluetooth headphones** add their own delay: use wired ones.
* The macOS package is **not notarised**: the first launch needs *Open Anyway* (System Settings → Privacy & Security). The Windows package needs Java 17 or later.
