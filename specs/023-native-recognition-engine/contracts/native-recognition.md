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
| `float[] analyze(float[] mono, int rate, float[] parameters, boolean spectrum, int[] attackCandidates, int[] chordRequired, int[] chordTolerated)` | any | Offline: the records of the whole recording. Deterministic. The window is 2048 and the hop 256, as `OfflineAnalysis`. |

Real time:
- In the cpal callback: copy the block and its arrival into a ring; no allocation, no lock and no system call apart from the clock read.
- In the analysis thread's loop: no allocation and no lock.
- The waits are on the Java side only: `nextFrames` and `nextHops`.

## 2. Java seam: `AE/backend/RecognitionBackend`

These are the operations `AudioEngine` calls today on `CaptureService` and `AnalysisService`:

```text
open(selection snapshot)             // throws RecognitionBackendException(reason) for NativeBackend
close()
subscribe(AnalysisService.Listener) / unsubscribe
addHopListener(HopListener) / removeHopListener
addCaptureListener(CaptureService.Listener) / remove
setSpectrumEnabled(boolean)
setExpectedPitches(int[]) / setExpectedChord(int[], int[]) / setAttackCandidates(int[])
applySettings(RecognitionSettings)  // front end, level, articulation
CaptureClock clock()
engineKind()                         // JAVA or NATIVE, for the diagnostics and the tests
```

Frames are delivered:
- on one thread per backend, never the UI thread;
- in order, with `timestampNanos` strictly increasing within a stream.

`JavaBackend` wraps today's `CaptureService` and `AnalysisService` without changing their code. Its `setExpectedChord` is a no-op, because `ChordMatcher` computes the evidence itself.

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

- The choice `recognition.engine.pitch` lists *Automatic (Native)* or *Automatic (Java)*, then *Native (Rust)* when it loads, then *Java (FastYin)*.
- Each option has a tip in English, French and Spanish.
- New keys:
  - `recognition.engine.pitch.option.native`;
  - `recognition.engine.pitch.option.fastyin`;
  - `recognition.engine.pitch.tip.v2`;
  - `recognition.engine.notice.<reason>` (five reasons).
