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
    void start(String outputName);                   // OFF → STARTING; fails to FAILED with a reason, never throws
    void stop();                                     // → STOPPING → OFF
    void offer(float[] block, int n);                // called by the capture thread; never blocks
    MonitorState state();
    MonitorStats stats();                            // delays, counters (data-model)
    void addListener(Listener l);                    // state changes and messages, delivered on the caller's executor
}
```

- **`offer`** copies at most one block into the single-producer, single-consumer queue. When the queue is full, the oldest block is dropped and counted. It never waits.
- **The output thread** runs, in order: queue, chain (before, amp, cabinet, after), output stage (volume, fade, limiter, clamp), then writes to the device.
- **The safe ceiling (FR-010):** no output sample exceeds 0 dBFS after the clamp, and the limiter holds peaks at −1 dBFS.
- **The feedback guard** (research R5) watches the output and moves to `MUTED_FEEDBACK`.
- **Overload:** when processing a block takes more than 50 % of the block time for 20 blocks in a row, the chain is lightened (reverb off, then chorus off, then oversampling 2×, then 1×), with a message each time. If it is still over, the state becomes `MUTED_OVERLOAD`.

## `MonitorDevice` (the only place that touches the hardware)

```java
interface MonitorDevice {
    Opened open(String outputName, float sampleRate, int blockFrames) throws MonitorDeviceException;
    interface Opened extends AutoCloseable {
        void write(float[] left, float[] right, int n);   // blocks for at most one block time
        int estimatedLatencyFrames();
    }
}
```

- `JavaSoundMonitorDevice` is a `SourceDataLine`, 16-bit stereo.
- A native implementation is added only if research R1 requires it.
- Tests use a fake device that records the output and can be made late or lost.

## `CaptureService` change (research R2, additive)

- `setMonitor(MonitorEngine monitor)`:
  - **null** (the default): the capture is exactly as today.
  - **non-null:** each block read is also offered to the monitor, and the read size is the monitor's block size (R1).
  - **Changing it while the capture runs** takes effect at the next read, without reopening the input line or resetting the ring buffer.
- The samples written to `SampleRingBuffer` are the same in both cases.
