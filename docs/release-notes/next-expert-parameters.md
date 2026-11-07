<!-- Draft (spec 026, T039): to be merged into the notes of the next version. -->

### ✨ Every recognition value can be tuned without a rebuild (expert settings)

* **192 values that were fixed in the code are now settings**, in 35 new **Advanced** categories of *Configuration › Audio recognition*: the onset detector (normal and fast runs), the pitch and chord spectrum analysers, the struck-note evidence, the input clean-up and the noise gate, the gain suggestion, the Wait and Follow decisions, the playback clock and latency, the timing windows, the Follow and Arcade relaxations, the playing techniques (bend, slide, vibrato, legato), the delay and input calibrations, the level check and the weak-input warning.
* **Nothing changes until you change a value.** Each default is the value the code used before, and the reports of every dataset are identical, with the Java engine and with the native engine.
* **Expert settings.** Their recommended values were measured. Each tooltip says what the value does and which way makes the recognition more lenient or more sensitive. The button *Restore the recommended values of this category* brings them back.
* **When a change applies.** A value the analysis reads (onsets, chord spectrum, input clean-up, noise gate) restarts the listening for a moment. The others apply at the next start.
* **Both engines read the same values.** The native engine receives them in a new tuning array, at the same indices as the Java side.
* **An older configuration file is read as it was.** Every stored value is kept and every new value takes its default, whether it was written by 0.8.0-beta.7 or by a later development build with the chord check levels.
* **The documentation lists everything.** [The engines' documentation](https://github.com/krl91/tuxguitaradvanced/blob/master/docs/architecture/recognition-engines.md) gives the unit, default, range and when it applies for every setting (226 in all). It also lists the constants that stay fixed, with their reason: sizes, buffers, numerical safety, timeouts, clock, score and display, the calibration exercise. Tests check this list against the code.

### 🖥️ macOS, Linux and Windows alike

* The same settings and the same behaviour in all three packages.
* **Each package carries its own native library.** An older library is never mixed in. If a native library without the tuning array is found anyway, it is refused when it loads and the Java engine is used, with the usual notice.
* Windows is built by the release workflow only, without tests. It is verified when that job runs for this release.

### 🙏 For testers

The sheet [F16](https://github.com/krl91/tuxguitaradvanced/blob/master/specs/test-sheets/F16-parametres-experts.md) checks one value with each engine and that every Advanced category fits the screen. On Linux and Windows, an [issue](https://github.com/krl91/tuxguitaradvanced/issues) with your system, its version and your screen height is welcome.
