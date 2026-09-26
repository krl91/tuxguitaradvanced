# Contract: Shared audio input and pitch engine

**Lives in**: `desktop/TuxGuitar-audio-engine` (library module `tuxguitar-audio-engine`, packages
`app.tuxguitar.audio.engine.input`, `.analysis`, `.pitch`; packaged in `lib/` of the macOS SWT
build only for the MVP). **Consumers**: evolved tuner plugin (`TuxGuitar-tuner-next`), practice
plugin. The legacy tuner does not use it.
**Constraints**: Java 9; no `app.tuxguitar.ui.*` or `app.tuxguitar.app.*` imports in `analysis`
and `pitch`; `input` may import only `javax.sound.sampled` and `app.tuxguitar.util.*`.
Names below are indicative; signatures are fixed in tasks, behaviour here is binding.

## Input service (per `TGContext`)

| Operation | Behaviour |
|---|---|
| `listInputs()` | Enumerates Java Sound mixers that support `TargetDataLine`; returns one logical `AudioInputDescriptor` per mono device and one per usable channel of a multi-channel device ("<device> — Input k"); system default first. |
| `select(input)` | Makes that single device/channel the current input for tuner **and** practice; increments `revision`; sets assessment `UNCHECKED`; restarts capture. Callable from either UI. Never called automatically in response to signal levels. |
| Channel handling | Capture reads the device at its native channel count and copies only the selected channel into the mono ring buffer (de-interleave). No mixing, no implicit channel 1, no automatic channel switching. Level check and analysis see only the selected channel. |
| `current()` | Returns `CurrentInputSelection` snapshot. |
| `addListener(l)` / `removeListener(l)` | Notified (off the audio threads) of: selection changed, assessment changed, device lost, capture error. |
| `acquire(consumerId)` / `release(consumerId)` | Capture runs only while ≥ 1 consumer holds it (tuner open, practice preparation or session active). With zero consumers the line is closed (FR-067). |
| `runLevelCheck()` | Starts the quick assessment over a short window of learner playing; result via listener. |

Failure reporting: `LineUnavailableException`, `IllegalArgumentException` (format), device
disappearance, and suspected permission denial (research spike S1) are reported as
`InputAssessment.UNUSABLE` with a reason — never thrown into UI code, never crash.

## Analysis stream

| Item | Behaviour |
|---|---|
| `subscribe(listener)` | Delivers `AnalysisFrame`s on the analysis thread; listeners must return quickly and must not touch UI. |
| Frame rate | one per hop (≈ 5–10 ms, parameter) |
| Estimator | the single estimator selected by spike S3 (YIN default); range at least 60 Hz – 1.4 kHz |
| Determinism | given the same sample sequence and parameters, frames are identical (tested) |

## Interpretation

| Function | Behaviour |
|---|---|
| `NoteInterpreter.read(f0Hz, confidence, assessment)` | → `PitchReading` (MIDI, name, octave, cents vs detected note's centre, reliable flag). A4 = 440 Hz; sounding pitch; E2 = MIDI 40. |
| `TolerancePolicy(tunerInTune, practiceAccept)` | Rejects construction unless `practiceAccept ≥ tunerInTune` and `practiceAccept < 50` cents. |
| `TolerancePolicy.tunerState(reading)` | `FLAT`, `IN_TUNE`, `SHARP`, or `NO_READING` when not reliable or input too low/clipping (FR-056). |
| `TolerancePolicy.matches(reading, expectedMidi)` | true only if reliable, same MIDI note (octave included) and `|cents| ≤ practiceAccept`. |

## Privacy guarantees (FR-068)

- No public or package API writes samples to files, streams, network or logs.
- Sample buffers are preallocated and overwritten; nothing retains audio after `release`.
- Test fixtures are synthesised in tests or read from the static, owner-recorded CC0 benchmark
  resources; runtime learner audio is never saved or turned into benchmark material.

## Tests that pin this contract

- Synthetic signals (sine+harmonics, Karplus–Strong, ±cents detuning) across E2–E6 and
  Drop D / B tunings → correct MIDI note and cents within tolerance.
- Octave-error suite (strong 2nd/3rd harmonics) → correct octave.
- Silence and noise → `reliable=false`; silence never yields a reading (SC-004).
- Clipped and very low inputs → assessment `UNUSABLE`/`SUBOPTIMAL` per parameters.
- Interleaved 2-channel test streams (signal on one channel, silence/noise on the other) →
  only the selected channel is analysed; selecting the silent channel reports no signal and
  never switches.
- Repeated identical plucks → one `onset` per pluck; sustained note → no extra onset.
- `TolerancePolicy` invariant and adjacent-semitone rejection.
