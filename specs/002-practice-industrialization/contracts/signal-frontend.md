# Contract: input front end (engine, Phase B)

Package `app.tuxguitar.audio.engine.frontend`. Applied by `AnalysisService` on each 256-sample hop **before** level, pitch, onset and spectrum analysis. Pure Java, no allocation per hop after construction.

```java
interface FrontEndStage { void process(float[] hop, int length); void reset(); }

final class InputGain implements FrontEndStage {      // volatile multiplier, 1.0 neutral
    void setMultiplier(double m); double getMultiplier(); void resetToNeutral();
}
// HighPass, NoiseGate, SpectralSubtractor implement FrontEndStage; NoiseEstimator feeds gate and subtractor.
```

`AudioEngine` API additions (all thread-safe, non-blocking):

| Method | Meaning |
|---|---|
| `double getInputGain()` / `void setInputGain(double)` | in-memory gain; clamped to the allowed range |
| `void resetInputGain()` | called on input-revision change and at start |
| `boolean isNoiseReductionEnabled()` / setter | backed by a persisted `RecognitionParameters.Flag` |
| `GainSuggestion suggestGain()` | result of the last level check: `suggestedMultiplier`, `reason` (TOO_LOW, TOO_HIGH, NOISE_DOMINATED, OK) or empty when the suggestion switch is off |

## Guarantees (testable)

1. Neutral settings (gain 1.0, reduction off) leave frames bit-identical to the MVP output (regression test).
2. The level meter, clipping indicator, `InputAssessor` level check and pitch/onset analysis all see the processed signal.
3. Gain returns to 1.0 after `CurrentInputSelection` changes input and on a new engine; nothing is written to settings.
4. The gate never mutes the decay of a note still above the noise floor + margin (synthetic decaying pluck test); it never attenuates a pluck's first 50 ms.
5. Added latency per stage is reported by the stage (`latencyHops()`) and summed into the engine's reported chain latency (FR-065).
6. `suggestGain()` never changes the gain itself.

UI: a compact slider with value and reset in `LevelPanel` (practice) and `TunerWindow` (tuner) bound to the same engine gain; a "suggest" button shown only when the suggestion switch is on; a noise-reduction checkbox in the "Audio recognition" options.
