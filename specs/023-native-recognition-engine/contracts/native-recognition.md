# Contract: native recognition (spec 023)

## 1. JNI: `AE/nativeengine/NativeRecognitionLibrary` ↔ crate `tuxguitar_monitor_native`

Loading:
- The library is loaded once, shared with `NativeMonitorLibrary`.
- `load()` returns null on success, or the failure text.

| Method | Thread | Contract |
|---|---|---|
| `long open(String inputName, int inputChannel, int blockFrames, long javaNanosAtOpen, float[] parameters)` | caller (run start) | `""` = the system's default input; channel −1 = the first. Opens at the device's **current** rate, never changing it. Starts the analysis thread, promoted as in spec 022. Throws `NativeRecognitionException(reason, detail)`. |
| `float sampleRate(long h)` | any | The rate in use. |
| `void configure(long h, float[] parameters)` | any | Front end, level, articulation and engine values (`NativeParameters`). Copied into a slot; the analysis thread swaps it at the top of its loop, discards its window and resets its state, as `AnalysisService.reconfigure` does. Never blocks the analysis thread. |
| `void setSpectrumEnabled(long h, boolean on)` | any | As `AnalysisService.setSpectrumEnabled`. |
| `void setExpectedPitches(long h, int[] midi)` | any | null or empty = none. |
| `void setExpectedChord(long h, int[] required, int[] tolerated)` | any | null = none. While set, the records carry the `ChordEvidence`. |
| `void setAttackCandidates(long h, int[] pitches)` | any | null or empty = off. |
| `int nextFrames(long h, float[] records, int timeoutMillis)` | the Java frame thread | Waits at most `timeoutMillis`, then copies whole records. Returns the number of records; 0 on timeout; −1 if the stream stopped (device lost); −2 after close. |
| `int nextHops(long h, float[] hops, int timeoutMillis)` | the Java hop thread | As `nextFrames`, for hop blocks. Hop blocks are only produced after `setHopsEnabled(h, true)`. |
| `void setHopsEnabled(long h, boolean on)` | any | |
| `long droppedBlocks(long h)` | any | Input blocks dropped because the analysis was late. Counted, never silent. |
| `void close(long h)` | caller | Stops the stream and the thread, and frees everything. Idempotent. |
| `float[] strumClip(long h)` / `void resetStrumClip(long h)` | any | The reading of the native strum clip measure (verdict, clipped share, RMS dBFS), and its restart. It replaces a Java `StrumClipMeter` on the native backend (research R2). |
| `float[] stats(long h)` | any | The dropped blocks, and the median and 99th percentile of one hop's analysis cost in nanoseconds since open (research R9). |
| `float[] frontEnd(float[] mono, int rate, float[] parameters)` | any | Test only: the hops after the native front end, for the parity test (offline `analyze` has no front end, as today). |
| `float[] analyze(float[] mono, int rate, float[] parameters, boolean spectrum, int[] attackCandidates, int[] expectedPitches, int[] chordRequired, int[] chordTolerated)` | any | Offline: the records of the whole recording. Deterministic. The window is 2048 and the hop 256, as `OfflineAnalysis`. `expectedPitches` (null or empty = none) adds the target evidence, for the parity test only: `OfflineAnalysis` passes null, as it has no target evidence today. |
| `float[] strumClipOffline(float[] mono, int rate, float[] parameters)` | any | Test only: the native strum clip measure fed the recording hop by hop (hop 256), giving its final reading (verdict, clipped share, RMS dBFS), for the parity test. |
| `long openAnalysis(int rate, float[] parameters)` | the Java analysis thread | Per-hop analysis of a stream captured by Java: the offline stages fed one hop at a time, no front end, no timestamps (Java dates the frames). Throws `NativeRecognitionException` on bad parameters or rate. |
| `void setAnalysisSpectrum(long h, boolean on)` / `void setAnalysisAttackCandidates(long h, int[] pitches)` / `void setAnalysisExpectedPitches(long h, int[] midi)` | the Java analysis thread | As the requests of `AnalysisService`: the spectrum starts afresh when switched on, each candidates call restarts the attack analysis. |
| `int analyzeHop(long h, float[] hop, float[] record)` | the Java analysis thread | One hop of 256 samples: the record's length, 0 while the window is not full, −1 when it does not fit. No allocation. |
| `void resetAnalysis(long h)` / `void closeAnalysis(long h)` | the Java analysis thread | A gap in the input: the window and every history forgotten. Close frees the handle, once. |

Real time:
- In the cpal callback: copy the block and its arrival into a ring; no allocation, no lock and no system call apart from the clock read.
- In the analysis thread's loop: no allocation and no lock.
- The waits are on the Java side only: `nextFrames` and `nextHops`.

Amendment (owner, 2026-11-03, during T015): `analyze` takes the expected pitches and `strumClipOffline` is added, so the target evidence (T022) and the strum clip measure (T024) are compared with Java on the owner's recordings offline.

Amendment (owner, 2026-11-03, during T039): the per-hop analysis (`openAnalysis` … `closeAnalysis`) is added. With the native engine, `AnalysisService` runs it in place of its Java stages when the estimator is the catalog's FastYin (the test inputs, or a Java capture), so a Java capture analysed live gives the frames of `OfflineAnalysis` bit for bit, as `LiveEngineEquivalenceTest` requires in the native pass. The native YIN gives its f0 and confidence as floats, as the Java estimator does.

## 2. Java seam: `AE/backend/RecognitionBackend`

These are the operations `AudioEngine` calls today on `CaptureService` and `AnalysisService`:

```text
engineKind()                         // JAVA or NATIVE, for the diagnostics and the tests
acquire(consumerId) / release(consumerId) / getConsumerCount()
subscribe(AnalysisService.Listener) / unsubscribe
addHopListener(HopListener) / removeHopListener
addCaptureListener(CaptureService.Listener) / remove
setSpectrumEnabled(boolean)
setExpectedPitches(int[]) / setExpectedChord(int[], int[]) / setAttackCandidates(int[])
configure(float[] NativeParameters)  // front end, level, articulation, strum; ignored by JavaBackend
CaptureClock clock()
getDroppedSampleCount()
```

As written during T049–T051: the backends keep `CaptureService`'s acquire/release by consumer rather than a single `open`/`close`, so the Java
chain is used unchanged. `NativeBackend.open()` (throws `NativeRecognitionException`) is called by `AudioEngine` when it chooses the native
engine, and `close()` is idempotent. `RecognitionBackendFactory` (`resolveKind`, `createNative`) is the "backend factory" of the package
constructors. The native backend's capture events are `CaptureService.Event`s, made with the added factory `CaptureService.Event.of`.

Frames are delivered:
- on one thread per backend, never the UI thread;
- in order, with `timestampNanos` strictly increasing within a stream.

`JavaBackend` wraps today's `CaptureService` and `AnalysisService` without changing their code. Its `setExpectedChord` is a no-op, because `ChordMatcher` computes the evidence itself.

Choice and fallback:
- Only `AudioEngine.getInstance` chooses between the backends. The package constructors used by the tests keep the Java backend unless a backend factory is passed to them.
- On a failed native open, `AudioEngine` opens the `JavaBackend` and sends one `RecognitionEngineNotice(reason)`.
- PERMISSION_DENIED is the exception: no fallback is tried, and today's microphone-permission event reaches the practice.

### Fallback and notice (T065, as built)

- `AudioEngine.acquire` chooses the backend when no consumer holds the capture. When the settings resolve to native, it opens the native input on a thread of its own (`tuxguitar-native-open`) and waits at most 1.5 s (`NATIVE_OPEN_TIMEOUT_MILLIS`).
  - Success: the native backend runs the run.
  - LIBRARY_MISSING, DEVICE_NOT_FOUND, FORMAT_UNSUPPORTED, STREAM_FAILED, or no answer within 1.5 s (a STREAM_FAILED): the Java backend runs the run, and one `RecognitionEngineNotice(reason, detail)` goes to the `addRecognitionEngineNoticeListener` listeners, after the lock, on the acquiring thread.
  - An open abandoned after 1.5 s that returns later is closed at once, under the native backend's lock (`NativeBackend.openFor`), before another run can see it.
  - PERMISSION_DENIED: no fallback and no notice. The native backend stays chosen with no stream; the consumer holds it (`NativeBackend.holdRefused`) and its capture listeners get `CAPTURE_ERROR`, as with the Java capture.
- When the settings ask for native (a hand-made `native`, or Automatic with the property set to `native`) and the library does not load, `resolveKind` gives Java and `RecognitionBackendFactory.nativeLibraryFailure(settings)` gives the reason: the run sends a LIBRARY_MISSING notice. Automatic alone is Java without the library, with no notice.

## 3. `OfflineAnalysis.analyze` (existing signatures, unchanged)

- It resolves the engine from `settings.get(ENGINE_PITCH)`, then the system property `tuxguitar.recognition.engine` (`java` or `native`), which overrides *Automatic* only.
- **NATIVE:** `NativeRecognitionLibrary.analyze`, then `FrameRecords` → `List<AnalysisFrame>`. A `HopListener` given to it gets the hops of the same samples, offline, with no front end, as today.
- **JAVA:** today's loop, unchanged.
- If the native library cannot be loaded while `native` was asked: Java, and one line in the log.

## 4. `ChordMatcher` (practice)

- If `frame.getChordEvidence()` is present for the chord being matched, it uses that.
- Otherwise it calls `ChordEvidence.evaluate(spectrum, required, tolerated)` as today.
- The rule that follows (accept or refuse) is unchanged.

## 5. Settings panel (practice)

- The setting keeps its name, *Pitch detection engine* (`recognition.engine.pitch.name`).
- The choice `recognition.engine.pitch` lists *Automatic (Native)* or *Automatic (Java)*, then *Native (Rust)* when it loads, then *Java (FastYin)*.
- Each option has a tip in English, French and Spanish.
- New keys:
  - `recognition.engine.pitch.option.native`;
  - `recognition.engine.pitch.option.fastyin`;
  - `recognition.engine.pitch.tip.v2`;
  - `recognition.engine.notice.<reason>`, for the four reasons that fall back: LIBRARY_MISSING, DEVICE_NOT_FOUND, FORMAT_UNSUPPORTED, STREAM_FAILED.

As built (T064, T066, T067):

- `RecognitionParameters.ENGINE_PITCH.getOptions()` stays `[automatic]` (the committed `RecognitionSettingsStoreTest` checks it); the choice also accepts `native` and `fastyin` as stored values, so a hand-made choice is kept. The panel's list comes from `RecognitionSettingsPanel.menuOptions`: Automatic, then the ids of `PitchEngineCatalog.getRecognitionEngines()`.
- Automatic's text is `recognition.engine.pitch.option.automatic` with the start of the recommended engine's option text, before its bracket: *Automatic (Native)* / *Automatique (Natif)* / *Automático (Nativo)*, or *Automatic (Java)*.
- The engine's tip is `recognition.engine.pitch.tip.v2` (it tells each engine); the old `recognition.engine.pitch.tip` is kept.
- The notice keys are `recognition.engine.notice.library-missing`, `.device-not-found`, `.format-unsupported` and `.stream-failed` (`RecognitionEngineNotices.key`); each text says the practice goes on with Java.
- The practice window shows the notice in a line above its buttons (`RecognitionEngineNoticeView`), once per reason per session (`RecognitionEngineNoticeView.Session`, one per application context), on the user-interface thread through `host.later`; it gets the engine through `host.engine()`, so `PracticeHost` does not grow. Nothing opens over the window.
- The committed `PitchEngineKindTest` was changed with the owner's approval (2026-11-03): only its Automatic assertions, which follow `recommendedRecognitionEngine` instead of `getRecommended()`.

