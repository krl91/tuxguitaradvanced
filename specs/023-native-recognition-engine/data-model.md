# Data model: native recognition engine (spec 023)

`AE/` = `desktop/TuxGuitar-audio-engine/src/main/java/app/tuxguitar/audio/engine/`.

## Recognition engine (setting)

- **Key:** `recognition.engine.pitch` (`RecognitionParameters.ENGINE_PITCH`). The key is unchanged.
- **Values:**

  | Value | Meaning |
  |---|---|
  | `automatic` | The default, after the update and on a new installation. It means `native` if the native library loads, else `fastyin`. |
  | `native` | The native engine. If it cannot start, the Java engine is used, with a notice. |
  | `fastyin` | The Java engine, today's. A stored `fastyin` (a hand-made choice) is kept as is. |
  | any other value | Read as `automatic`, as today (`PitchEngineCatalog.resolve`). |

- **Change rule:** a new value takes effect at the next `acquire` when no consumer holds the capture, that is, from the next run (FR-004).

## Engine (catalog entry, `PitchEngineCatalog.Engine`)

| Field | Java | Native |
|---|---|---|
| `getId()` | `fastyin` | `native` |
| `getName()` | "Java (FastYin)" | "Native (Rust)" |
| `kind()` | JAVA | NATIVE |
| `create(sampleRate)` | `TarsosFastYinEstimator` | not used for the analysis; returns the Java estimator, for callers that only need a `PitchEstimator` |

- `getEngines()` lists the native engine first when it loads, then Java. When the native library does not load, only Java is listed.
- `getRecommended()` gives the first one listed.

## Backend (`AE/backend/RecognitionBackend`)

- **States:** CLOSED → OPENING → RUNNING → CLOSED, or OPENING → FAILED(reason) → the Java backend is opened instead.
- The backend is chosen when the first consumer acquires, from the setting (resolved).
- It holds what the consumers set, and re-applies it to a backend that opens later:
  - the subscribers;
  - the hop listeners;
  - the capture listeners;
  - the spectrum flag;
  - the expected pitches, the expected chord and the attack candidates;
  - the front-end settings.

## Recognition engine notice (`AE/backend/RecognitionEngineNotice`)

| Field | Type | Values |
|---|---|---|
| `reason` | enum | `LIBRARY_MISSING`, `DEVICE_NOT_FOUND`, `PERMISSION_DENIED`, `FORMAT_UNSUPPORTED`, `STREAM_FAILED` |
| `detail` | String | the native text, for the log only |

- It is sent once per run in which the native engine was requested but the Java engine is used.
- The practice window shows it once per reason per session (texts in three languages).

## Frame record (native → Java)

One record per analysis window, packed into a `float[]` that Java turns into an `AnalysisFrame` (`AE/nativeengine/FrameRecords`). The fields are those of `AnalysisFrame`, so the consumers do not change.

| Field | Source stage | Note |
|---|---|---|
| `timestampNanos` | sample count | same rule as `AnalysisService`: the processed samples over the rate |
| `captureSampleEnd` | sample count | as `ringBuffer.getReadSampleCount()` today; 0 offline |
| `arrivalNanos` | capture callback | for `CaptureClock` (R4), in Java's `System.nanoTime` base |
| `rmsDbfs`, `peakDbfs`, `clippedRatio`, `levelState` | level | |
| `f0Hz` (NaN if unpitched), `confidence` | YIN | |
| `onset` | articulation | |
| `spectrum` (optional) | polyphonic | the band dB, the salience, the candidates and the peaks, as `SpectrumFrame` |
| `targetEvidence` (optional) | target | the hypotheses (midi, score) and the window in ms |
| `attackEvidence` (optional) | attack | the pitches, amplitudes, total, maximum, strength, growth, maximum growth and bin Hz |
| `chordEvidence` (optional, new) | chord | the pitches (midi, salience, independent salience, harmonic-of-lower, mean and unique amplitude, unique partials) and the residual candidates |
| `techniqueFeatures` | none (Java) | null from the native engine, as from `AnalysisService` today |

- Optional parts are present only while they are enabled: spectrum on, pitches expected, candidates set, chord expected.
- **Sizes:** a record has a fixed header plus variable parts with their counts. The largest record fits a buffer of 4096 floats, preallocated on both sides.

## Hop block (native → Java, only while a hop listener is registered)

`hop[256]` after the front end. On a reconfiguration, a `configure(sampleRate)` marker comes first, the same contract as `HopListener`.

## Dataset report (per engine)

- **Files:**
  - `benchmark/before/`: the Java figures of 2026-11-02;
  - `benchmark/after-java/` and `benchmark/after-native/`, written by the dataset benchmarks run with `-Dtuxguitar.recognition.engine=`;
  - `benchmark/comparison.md`: every SC-001 line with its before, Java-after and native-after figures and a pass/fail status, and the SC-004 lines with "reached" or "not reached".
- **Rule:** a line of SC-001 that is lower with the native engine fails the comparison test.

## Live delay report

- **File:** `benchmark/recognition-delay.md`.
- **Content:** per engine and per input, the tones played, the tones found, the median and 90th percentile from tone to pitched frame and from tone to onset, and, for Arcade, from tone to the hit painted.
