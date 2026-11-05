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
- `CHORD_PROFILE_NAMES`;
- `CHORD_PROFILE_TOP`;
- `CHORD_PROFILE_NOISE` and `CHORD_PROFILE_WEIGHT` (if kept);
- `CHORD_CONFIRM`;
- `CHORD_WINDOW`.

## Behaviour

1. **At each new strum** the profile is reset to the expected chord: at the arming in Wait (`PracticeSession`, `frame.isOnset()` while a chord is expected); with each new onset and for each of its chord candidates in Follow and Arcade (`FollowSession.registerOnset`).
2. **Each usable frame** adds its bands to the profile (the bands under the noise floor left out, if kept). A frame is complete when the match reaches `CHORD_PROFILE_MATCH`, at most one expected name is weak (`CHORD_PROFILE_NAMES`), and `CHORD_PROFILE_SETTLE` has passed. Two weak names refuse the frame whatever the match.
3. **Acceptance:** `stableHops` complete frames in a row, within `CHORD_WINDOW` of the arming (Wait) or of the onset (Follow, Arcade).
4. **Refusal:** the window ends first. In Wait, the feedback is `INCOMPLETE`, with the expected pitches whose names are weakest, and a new strum is needed. In Follow and Arcade, the wrong attempt carries the same missing pitches (`wrongAttempt`).
5. **The attack path** (`PolyphonicAcceptance`, in `PracticeSession` and in `FollowSession`) stays as it is, unless the sweep sets `CHORD_ATTACK` off for chords (R6), then off for chords in every mode. While it stays on with the profile rule, it accepts a chord only when the profile summed so far has at most one weak name. Single notes are never affected.

## Guarantees

- The same frames give the same decision with both engines, since the decision is one Java code path (FR-009).
- **No allocation per frame.** Nothing runs on the audio callback.
- **No JNI change:** `NativeParameters` (21 values), the frame record and `jni_recognition.rs` are unchanged.
- A change of any value above takes effect at the next run, with no rebuild of the application or the native library (FR-013).
- With `CHORD_COMPLETENESS = all-verifiable`, the spec 023 rule applies unchanged.
