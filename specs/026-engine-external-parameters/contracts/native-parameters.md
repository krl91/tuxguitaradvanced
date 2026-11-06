# Contract: the native parameter interface (spec 026, FR-004, FR-009)

Extends spec 023's contract §1 (`NativeRecognitionLibrary`, `jni_recognition.rs`). Approved by the owner on 2026-11-06 (Q3 A). Research R5.

## The two arrays

- **`parameters`** (`float[21]`, unchanged): the layout, indices and precision of today. Eight values that are fixed today are now taken from the settings: `ONSET_FLUX_FACTOR`, `ONSET_FLUX_OFFSET`, `ONSET_REFRACTORY`, the four windows (`ONSET_PRE_FIRST`, `ONSET_PRE_LAST`, `ONSET_POST_FIRST`, `ONSET_POST_LAST`) and `YIN_THRESHOLD`. `withFastOnset(float[])` keeps the default fast windows, and a new overload `withFastOnset(float[], settings)` reads them from the settings.
- **`tuning`** (`double[TUNING_LENGTH]`, new): every other value a native stage reads, at named indices `NativeParameters.T_…`, the same names in `CRATE/analysis/mod.rs`:
  - polyphonic, attack, chord evidence and strum;
  - front end, noise gate and subtraction;
  - pitch target.

  Counts and hops are whole numbers, rounded by the crate as today. `Tuning::from_slice` refuses any length other than `TUNING_LENGTH`.

## JNI functions

| Function | Status |
|---|---|
| `open`, `configure`, `analyze`, `frontEnd`, `strumClipOffline`, `openAnalysis` | unchanged; they use `Tuning::defaults()` |
| `openTuned(inputName, inputChannel, blockFrames, javaNanosAtOpen, float[] parameters, double[] tuning)` | new |
| `openAnalysisTuned(rate, float[] parameters, double[] tuning)` | new |
| `configureTuning(handle, double[] tuning)` | new: same lock-free mailbox as `configure` |
| `analyzeTuned(…, float[] parameters, double[] tuning, …)` | new: offline, for tests |
| `tuningLayout()` | new: returns `TUNING_LENGTH` |

## `NativeRecognitionLibrary.Api`

- New `open(…, float[] parameters, double[] tuning)`: the interface's default calls today's `open(…, parameters)`. The `JNI` implementation overrides it: it loads the library, checks the layout, then calls `openTuned`.
- New `configureTuning(handle, tuning)`: the interface's default does nothing; `JNI` overrides it with the JNI call.
- So a fake that implements only today's methods (the committed `FakeRecognitionApi`) keeps working unchanged.

## Version check

After loading, the `JNI` implementation calls `tuningLayout()`:
- if the symbol is missing (an older library) or the value differs from `NativeParameters.TUNING_LENGTH`, the library is not used;
- the reason is `LIBRARY_MISSING`, with the detail "native library tuning layout N, expected M" or "native library too old: no tuning layout";
- the run falls back to the Java engine with the existing notice. There is no new learner-visible string.

## Real time

- `configureTuning` replaces the values through the existing lock-free mailbox (`CRATE/recognition.rs`); it never changes a buffer size.
- Values that size a buffer are bounded, and the crate allocates for the bound when it opens (research R6).
- The crate does no validation: values come validated from `RecognitionSettings`, as today.

## Parity

The Java and native stages read the same parameter for the same value (FR-004). The parity tests of spec 023 stay as they are (defaults). New parity tests run one non-default value per family through `analyzeTuned` (SC-001).
