<!-- Draft (spec 027, T064): the GuitarFX section of the next release's notes. To be merged into docs/release-notes/<tag>.md when the
     version is chosen, after the owner's sheet F17. -->

### 🎛️ New: GuitarFX, the amp and pedals on their own

**GuitarFX** turns your computer into a guitar amp with effect pedals, **without TuxGuitarAdvanced and without Java**. It is the sound of the *Amp and effects* window (noise gate, compressor, overdrive, distortion, the amp in *Clean*, *Crunch* or *High gain* with its cabinet, chorus, echo and reverb), ported to Rust and checked against it sample by sample (the difference stays under −90 dBFS on spec 022's test signals).

* **An application**: choose your input and output, turn the sound on, play. The four ready-made sounds and your own, the chain in three tabs (before the amp, the amp, after the amp), the estimated delay, the howl guard and the warning with the built-in microphone, the output held under −1 dBFS.
* **A plugin** for your music software: **CLAP** and **VST3** on macOS, Linux and Windows, and **Audio Unit** on macOS (GarageBand, Logic). The project keeps its sound.
* **Sounds shared with TuxGuitarAdvanced**: export a sound to a `.guitarfx` file in one, import it in the other, both ways.
* **Every internal value can be tuned** in an *Advanced* window (103 values), documented in [parameters.md](https://github.com/krl91/tuxguitaradvanced/blob/master/docs/guitarfx/parameters.md); the plugin reads the same values.
* **Installers** that place the application and the plugins where music software finds them, and an uninstall that keeps your sounds and settings: `GuitarFX-<version>-macos-arm64.pkg`, `GuitarFX-<version>-windows-x86_64-setup.exe`, `guitarfx_<version>_amd64.deb` and `GuitarFX-<version>-linux-x86_64.tar.gz`. Each is a small fraction of TuxGuitarAdvanced's package.

The [guide](https://github.com/krl91/tuxguitaradvanced/blob/master/docs/guitarfx/guide.md) ([français](https://github.com/krl91/tuxguitaradvanced/blob/master/docs/guitarfx/guide_fr.md), [español](https://github.com/krl91/tuxguitaradvanced/blob/master/docs/guitarfx/guide_es.md)) covers installing, playing, the plugin and uninstalling.

> ⚠️ GuitarFX is **not signed by Apple or Microsoft**: macOS asks you to allow it in *System Settings › Privacy & Security › Open Anyway*, and Windows may show *More info › Run anyway*.

> 🙏 **Windows and Linux users**: the GuitarFX packages are installed, started without Java and uninstalled by the CI, but nobody has played through them yet. Sheet [F17](https://github.com/krl91/tuxguitaradvanced/blob/master/specs/test-sheets/F17-guitarfx.md) says what to check; please open an [issue](https://github.com/krl91/tuxguitaradvanced/issues) with your system, your audio interface and your music software.

<!-- Before publishing: replace with what the owner's sheet F17 found (SoundCraft, GarageBand or Logic), and the delay measured by
     guitarfx-probe if it was run (specs/027-standalone-amp/benchmark/latency.md), or say it was not measured. -->
