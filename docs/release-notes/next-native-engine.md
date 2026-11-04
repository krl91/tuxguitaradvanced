## 🎸 TuxGuitarAdvanced (next version) — the native recognition engine, checked, and Arcade at your screen's rate

<!-- Draft (spec 023, T084). The version is chosen at release time; rename this file to docs/release-notes/<tag>.md then. Nothing is released before the owner's gate T087. -->

TuxGuitarAdvanced adds **practice modes** on top of [TuxGuitar](https://www.tuxguitar.app/), the free multitrack tablature editor: it listens to your real guitar, checks the **pitch of the notes and the chords** you play against the tablature, and lets you practise note by note, in time (Follow, Arcade) or waiting for the right note.

### 🔖 About this release

It completes the **native recognition engine** (spec 023) that 0.8.0-beta.6 brought as a pre-release: its last measurements are done and the author has checked it with the Rocksmith cable. Every feature is in all three packages (macOS, Linux and Windows), with the same behaviour. The macOS package still needs **macOS 15 Sequoia or later**.

> 🙏 **Windows and Linux users: your feedback is needed.** The author has no Windows or Linux computer at hand: these packages are built by the CI but **have never been run**, and the native engine is native audio code on each system. If you try a package, please open an [issue](https://github.com/krl91/tuxguitaradvanced/issues) with your system, its version and your audio interface, and tell:
>
> 1. **The engine** — in the practice settings, *Recognition engine › Pitch detection engine* should show *Automatic (Native)*. If it shows *Automatic (Java)*, or a notice says the Java engine is used, copy it.
> 2. **The delay you feel** — practise in *Wait* once with *Native (Rust)* and once with *Java (FastYin)*: does the verdict come sooner with the native engine?
> 3. **The fallback** — with the application closed, rename the native library in its `lib` folder (add `.off`), start a practice: a notice should say so, and the practice should work on Java and start within 2 s. Then give the library its name back.
> 4. **The amp together with the recognition** — turn *Amp sound* on (*Amp and effects…*) while you practise: both should work at once.
> 5. **Linux:** if your interface is opened as a raw ALSA `hw:` device (without PipeWire or PulseAudio), the amp sound may not open while the recognition holds the device. Tell us if that happens.
>
> The full checklist is the sheet [F13](https://github.com/krl91/tuxguitaradvanced/blob/master/specs/test-sheets/F13-moteur-natif.md), rows **X1** to **X5**.

### ✨ What is new since 0.8.0-beta.6

* **Arcade follows your screen's refresh rate.** The picture is asked for at the display's own rate (120 Hz on a ProMotion MacBook, 60 Hz when the rate cannot be read), so it no longer misses display cycles: on the author's Mac, 99.8 % of the frames now come on time instead of 96.8 to 98.9 %.
* **The native engine, checked** with the Rocksmith cable by the author: the delay figures below. <!-- fill in from T056/T087 -->

### 📏 Measured on the author's Mac

* **The recognition delay**, from a tone to its verdict (median):

  | Input | Java engine | Native engine |
  |---|---|---|
  | Built-in microphone | 139 to 144 ms | about 122 ms |
  | USB microphone | 114 ms | 87 ms |
  | Rocksmith cable | <!-- T056 --> | <!-- T056 --> |

* **Arcade at 120 Hz**, ten minutes of the stress score with the recognition live: 99.83 % of the frames on time with the native engine (99.81 % with Java), no frame held, no drift of the song.
* **The hit shown in Arcade**, from a tone in the speakers to the hit on screen (median / 90th percentile): 204 / 212 ms with the native engine, 233 / 255 ms with Java. Most of it is the time a note must hold to be confirmed (107 ms by default), the same for both engines.
* **Chords** (the same with both engines):
  * a chord played a whole tone higher than written is refused 100 % of the time (66.7 % before 0.8.0-beta.6), in IDMT-SMT-Audio-Effects;
  * chords played through effects are recognised 97 % of the time;
  * the author's chords through the Rocksmith cable: 26 of 42 accepted, 13 of 42 neighbouring chords wrongly accepted;
  * GuitarSet complete chords: 33 of 56 on the summed strings, 23 of 56 on the microphone.

### 🚧 What is **not** fixed

* Many **chords are still refused** (about 6 in 10 of the author's chords accepted), and some **neighbouring chords are still accepted**. Making chords more lenient, with a setting to adjust it, is the next piece of work.
* Notes and chords whose **attack is not heard** (a soft note, a low input level) are still refused.
* **Bluetooth headphones** add their own delay: use wired ones.
* The macOS package is **not notarised**: the first launch needs *Open Anyway* (System Settings → Privacy & Security). The Windows package needs Java 17 or later.
