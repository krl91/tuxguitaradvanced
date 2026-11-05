# Spec 023, final report: the native recognition engine

Written on 2026-11-04 (T086), before the owner's gate T087. The figures come from `benchmark/`; each line names its report.

## Success criteria

| SC | Target | Measured | Status |
|---|---|---|---|
| SC-001 | Native at least the Java baseline on every dataset line | Every line of `benchmark/comparison.md` passes; no test OK with Java is KO with the native engine; technique and bass benchmarks equal | **Reached** |
| SC-002, offline | GuitarSet chord delay median ≤ 103 ms summed, ≤ 149 ms microphone, not above Java | 103 ms and 149 ms, the same as Java (`after-native/guitarset-chords-report.txt`) | **Reached** |
| SC-002, live, built-in microphone | Median at least 20 ms lower than Java, p90 not higher | Java 138.5 to 144.5 ms, native 120.2 to 121.9 ms over three pairs of runs: 16.6 to 23.0 ms lower; p90 always lower (`recognition-delay.md`) | **Partly**: the gain is 20 ms or more in two pairs of three |
| SC-002, live, USB microphone | (not in the target; measured) | Java 114.4 ms, native 86.5 to 90.1 ms: about 27 ms lower; p90 lower | Measured |
| SC-002, live, Rocksmith cable | Native at least 20 ms ahead at the median, p90 not higher | The owner's 20 notes, both engines at once (2026-11-05): the native verdict arrives 21.5 ms sooner at the median, 34.4 ms at p90; from the sound to the verdict 1.6 / 1.8 ms native, 21.7 / 34.5 ms Java (`recognition-delay.md`, `DualEngineCableArrivalProbeTest`; T055's probe, dated by the capture clock, compares the sound's moment and came out level) | **Reached** |
| SC-003 | Fallback to Java with a message within 2 s, on the three systems | `RecognitionFallbackTest` (fake library, every reason, an open that never answers abandoned after 1.5 s): green. In the application: macOS at T087 (sheet F13 step 5); Linux and Windows by testers (F13 X3) | **Tested; the real-app checks wait for T087 and the testers** |
| SC-004 (aimed at, not blocking) | Owner's chords ≥ 34/42 and neighbours ≤ 6/42; GuitarSet ≥ 40/56 summed, ≥ 30/56 microphone; whole tone higher refused ≥ 85 % | 26/42 and 13/42; 33/56 and 23/56; 100 % (66.7 % before) | **Whole tone reached; the four others not reached** (carried to spec 024) |
| SC-005 | 10 minutes live without a dropped block; datasets identical with the amp on and off | 112,496 frames, 0 dropped (`endurance.md`); identical outcomes and frames with the amp on and off (`amp-identity.md`) | **Reached** |
| SC-006, frames | 99 % of the frames on time at the screen's rate (120 Hz here), no frame held | 99.83 % native, 99.81 % Java, 0 held, after T073; 98.94 % before it (`arcade-refresh.md`) | **Reached** |
| SC-006, spec 003 benchmarks | Within their current limits | Scene baseline: passed (p99 ≤ 0.43 ms against 4.2 ms). Stress run: frames far better than spec 003's record; its identity check failed by 2 lines of 1129, as spec 003's own record (13 lines): accepted by the owner as timing variation | **Reached, with that reservation** |
| SC-006, hit shown | Sooner than Java at the median and p90 | Native 204.0 / 211.9 ms, Java 233.0 / 255.2 ms (`recognition-delay.md`, T071) | **Reached** |

## Functional requirements

| FR | Status and evidence |
|---|---|
| FR-001 native path | Done: capture, front end, level, pitch (YIN from the paper), spectrum, attacks, target and chord evidence in the crate; guitar, bass and every tuning covered by the parity tests. |
| FR-002 same quality | Done: SC-001. |
| FR-003 less delay | Done offline (equal) and live: both microphones 17 to 28 ms lower, the Rocksmith cable 21.5 ms sooner. |
| FR-004 choice in the menu | Done: *Automatic*, *Native (Rust)*, *Java (FastYin)*, tips in English, French and Spanish; kept across a restart (tests; F13 steps 1 and 2 at T087). |
| FR-005 Automatic by default | Done: native where the library loads, else Java; the option shows which; a hand-chosen Java kept (`PitchEngineKindTest`, `RecognitionSettingsStoreTest`). |
| FR-006 fallback | Done: one notice per reason, never a crash or a hang; no fallback for a refused permission (`RecognitionFallbackTest`). |
| FR-007 Java engine kept | Done, with the owner's approval of 2026-11-03: the outcome files are identical except 23 lines KO before and OK on both engines, from US3's experiments A and C applied to both engines. |
| FR-008 same consumers | Done: calibration, meter, pre-run check and the three modes go through the selected backend; the standalone tuner unchanged. |
| FR-009 devices | Done: the chosen device and channel at its current rate; lost devices and dropped blocks reported as by the Java capture. |
| FR-010 amp sound | Done: SC-005's identity tests; F13 step 6 at T087. |
| FR-011 real time | Done: no allocation, lock or wait in the callback and the loop; cost 0.162 ms per 5.33 ms hop at the median (`cost.md`); Arcade's drift and paint no worse with the native engine. |
| FR-012 chords better | Partly: see SC-004. |
| FR-013 measured | Done: `benchmark/before`, `after-java`, `after-native`, `comparison.md`. |
| FR-014 parity | Built for macOS, Linux and Windows by the Release workflow of 0.8.0-beta.6; Linux and Windows never run: the live delay waits for testers (sheet F13). |
| FR-015 Arcade in real time | Screen rate held (SC-006). The hit limit as derived in T071 (T054's pitch frame plus one frame, 128.5 ms) is **not met as written**: the hit also waits for the note confirmation time (107 ms by default, the same for both engines), which T054 does not include. Without it the native hit is about 97 ms. The owner asked for this investigation; no engine fault was found. |

## Parity tolerances reached, per stage (all parity tests green)

| Stage | Tolerance |
|---|---|
| FFT | inverse within 1e-6; the same bin layout as `RealFft` |
| Front end (gain, high-pass, gate, spectral subtraction) | 1e-4 relative, hop by hop |
| Level | rms and peak within 0.01 dB, clipped ratio within 1e-4, the same level state |
| Pitch (YIN) | within 1 cent of FastYin; the same pitched decision on ≥ 99.9 % of the frames; confidence within 1e-4 |
| Spectrum (polyphonic, comb, NNLS) | 1e-4 relative; the same candidate set on every frame |
| Attack evidence | 1e-4 relative |
| Target evidence | 1e-4; the same window in ms |
| Chord evidence | every field within 1e-4 relative |

## Chord experiments (US3, `benchmark/chord-experiments.md`)

- **A, kept** (both engines): a chord a whole tone away that explains the partials 1.5 times better is not the expected chord's evidence. The whole tone higher is refused 100 % instead of 66.7 %.
- **B, dropped**: the strum spread raised the wrong acceptances as much as the right ones.
- **C, kept, corrected** (both engines): a low string heard by its second and third partials, provided neither is a partial of another sounding pitch. Its gains on the owner's chords and GuitarSet disappeared with the correction.

## Risks left

- **Linux, raw ALSA `hw:` device**: it opens only once; with the recognition holding it, the amp sound cannot open. It is reported to testers (F13 X5) and in the release notes.
- **Linux and Windows have never been run**: built by the CI only.
- `comparison.md`'s SC-002 table comes out empty (the report writer does not find the delay lines); the figures above are read from the GuitarSet reports directly.
- The stress run's identity check is not reproducible over ten minutes (see SC-006).

## What waits for the owner and the testers

- **T087**: the owner's gate on sheet F13. The owner's answer (felt quicker or not) will be written here.
- **Testers**: sheet F13, rows X1 to X5, on Linux and Windows.
- **Spec 024**: the chords at 90 % on the cable, more lenient by default, with the setting in the practice window.

## The owner's answer at T087

On 2026-11-05, with the relaunched app, the Rocksmith cable and headphones (sheet F13):

| Step | Result | The owner's remarks |
|---|---|---|
| 1, the engine choice | OK | |
| 2, kept across a restart | OK | |
| 3, Wait, native against Java | OK | **Bug**: *Practise this track* stayed greyed; the level became good only after unselecting and selecting the input again, then playing. What blocks the start is not told clearly enough: the window should guide the learner (a tip saying what blocks). |
| 4, Follow and Arcade | OK | The practice window is taller than a standard screen: it must scroll so the start button can be reached. |
| 5, the fallback in the app | OK, checked by Claude at the owner's request | On the built app's own jars and Java, with the library left out of `java.library.path` (as renamed): the menu shows Java only, *Automatic* runs Java, a hand-chosen native engine runs Java with its line in the log ("the native engine was asked but its library could not be loaded … the Java engine is used"), all within 9 ms; with the library, the menu shows native then Java and *Automatic* runs native. The notice in the window and the start within 2 s are `RecognitionFallbackTest`'s (green); not seen by eye. |
| 6, the amp with the recognition | OK as a function | The *Amp and effects* window does not fit the screen (not every control is visible, even enlarged); the names are unclear (*Amp sound* should read as switching it on, *Baffle* is not understood): to be redesigned with tabs and clearer names. |
| 7, felt quicker | **Yes** | |
