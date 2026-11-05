# Contract: the chord profile rule (both engines)

## Input

The `AnalysisFrame`s of a run, each with a `SpectrumFrame`, whose semitone band levels `getBandDb(midi)` run from MIDI 24 to 112:
- the Java engine computes them with `PolyphonicAnalyzer`;
- the native engine computes them with `polyphonic.rs` and sends them in the spectrum part of the frame record (`frame_layout.txt`).

The spectrum is on while the score has chords (`PracticeWindow`).

The values come from the recognition settings through `ChordMatcher.Parameters`, read when a run starts:
- `CHORD_COMPLETENESS`;
- `CHORD_PROFILE_MATCH`;
- `CHORD_PROFILE_SETTLE`;
- `CHORD_PROFILE_TOP`;
- `CHORD_PROFILE_WEIGHT` (if kept);
- `CHORD_CONFIRM`;
- `CHORD_WINDOW`.

## Behaviour

1. **At each arming of a strum** (`frame.isOnset()` while a chord is expected, as today), the profile is reset to the expected chord.
2. **Each usable frame** adds its bands to the profile. A frame is complete when the match reaches `CHORD_PROFILE_MATCH` after `CHORD_PROFILE_SETTLE`.
3. **Acceptance:** `stableHops` complete frames in a row, within `CHORD_WINDOW` of the arming.
4. **Refusal:** the window ends first. The feedback is `INCOMPLETE`, with the expected pitches whose names are weakest, and a new strum is needed.
5. **The attack path** stays as it is, unless the sweep sets `CHORD_ATTACK` off for chords (R6). Single notes are never affected.

## Guarantees

- The same frames give the same decision with both engines, since the decision is one Java code path (FR-009).
- **No allocation per frame.** Nothing runs on the audio callback.
- **No JNI change:** `NativeParameters` (21 values), the frame record and `jni_recognition.rs` are unchanged.
- A change of any value above takes effect at the next run, with no rebuild of the application or the native library (FR-013).
- With `CHORD_COMPLETENESS = all-verifiable`, the spec 023 rule applies unchanged.
