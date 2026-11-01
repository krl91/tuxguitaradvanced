# Contract: `MonitorEngine` and the effects (AE `monitor/`)

## `Effect` (each pedal, the amp, the cabinet, the output stage)

```java
interface Effect {
    void prepare(float sampleRate, int maxBlock);   // allocates every buffer; called once before processing
    void set(PedalSettings settings);                // real-time safe: stores targets, no allocation
    void process(float[] in, float[] out, int n);   // in-place allowed; no allocation, no lock, no I/O
    void reset();                                    // clears delay lines and envelopes
}
```

Every effect MUST meet these rules:
- **Deterministic:** the same input and the same settings give the same output, sample for sample.
- **Smooth changes:** after `set`, each knob ramps to its target within one block (at most 10 ms), so a step of a knob never makes a jump bigger than the signal's own change (SC-003).
- **Clean bypass:** `enabled = false` crossfades to the dry signal over 10 ms.
- **Stable:** finite output for any finite input and any knob value, with no NaN and no denormals (a tiny DC offset or flush-to-zero).
- **Tails:** the echo and the reverb keep their tails after the input stops (US2/AC2).

## `MonitorEngine`

```java
final class MonitorEngine {
    MonitorEngine(MonitorDevice device, Clock clock);
    void setChain(ChainSettings chain);              // any thread; atomic snapshot (research R3)
    void setGuitarVolume(double zeroToOne);
    void start(String inputName, int inputChannel, String outputName); // OFF → STARTING; fails to FAILED with a reason, never throws
    void stop();                                     // → STOPPING → OFF
    MonitorState state();
    MonitorStats stats();                            // delays, counters (data-model)
    void addListener(Listener l);                    // state changes and messages, delivered on the caller's executor
}
```

- **The input** comes from the device itself (R1 addendum), never from `CaptureService`. A block read at or above −0.1 dBFS is counted as clipped, and told at most once per second.
- **The thread** runs, in order: device input, chain (before, amp, cabinet, after), output stage (volume, fade, limiter, clamp), then writes to the device.
- **The safe ceiling (FR-010):** no output sample exceeds 0 dBFS after the clamp, and the limiter holds peaks at −1 dBFS.
- **The feedback guard** (research R5) watches the output and moves to `MUTED_FEEDBACK`.
- **Overload:** when processing a block takes more than 50 % of the block time for 20 blocks in a row, the chain is lightened (reverb off, then chorus off, then oversampling 2×, then 1×, then the amp alone with the cabinet: every pedal bypassed), with a message each time. If it is still over, the state becomes `MUTED_OVERLOAD`.

## `MonitorDevice` (the only place that touches the hardware)

```java
interface MonitorDevice {
    Opened open(String inputName, int inputChannel, String outputName, int blockFrames) throws MonitorDeviceException;   // "" = the system's default; channel -1 = the first
    interface Opened extends AutoCloseable {
        float sampleRate();                                // the input device's current rate (the chain's rate; the output is resampled in the crate)
        int read(float[] in, int n);                       // waits for at most one block time; frames read, 0 on a gap (counted as silence)
        void write(float[] left, float[] right, int n);    // never blocks longer than one block time
        int estimatedLatencyFrames();                      // the devices' declared latencies plus both buffers (R8)
        long droppedBlocks();                              // input blocks dropped because Java was late (more than two waiting)
    }
}
```

- `NativeMonitorDevice` is the only implementation (R1 addendum): JNI over the Rust crate `tuxguitar-monitor-native` (`cpal`), with two lock-free single-producer, single-consumer rings between the devices' callbacks and Java. If the library cannot be loaded, `open` throws `MonitorDeviceException` with a clear reason (FR-013); there is no Java Sound fallback.
- Tests use a fake duplex device that is fed an input, records the output, and can be made late, lost or refused.

## `CaptureService`

Not touched (R1 addendum): the sound has its own input, so the recognition's capture is exactly as today whether the sound is on or off (FR-003).
