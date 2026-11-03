# Live recognition delay (spec 023, research R1)

## 2026-11-03 12:47, engine java

- Java 21.0.13, Mac OS X 27.2 aarch64
- 20 plucked tones (A2, D3, G3, B3, E4 in turn), 120 ms, 1.5 s apart, through the default output (Java Sound, 48 kHz).
- Received: the frame reached the listener. Dated: the frame's last sample on the wall clock (CaptureClock). From the tone's first sample at the output.

| Input | Tones found (pitch) | Pitch received median / p90 (ms) | Pitch dated median / p90 (ms) | Onsets found | Onset received median / p90 (ms) | Onset dated median / p90 (ms) | Dropped samples |
|---|---|---|---|---|---|---|---|
| Micro MacBook Pro | 20 / 20 | 141.7 / 155.5 | 119.5 / 125.3 | 20 / 20 | 176.5 / 183.4 | 144.5 / 148.8 | 0 |
