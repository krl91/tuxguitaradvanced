# Research: every chord the owner plays right is accepted, a clearly wrong chord refused

**Spec**: [spec.md](spec.md) · **Date**: 2026-11-05 · **Version 2** (re-plan after the owner's third series; version 1 is in git history, commit `13b3d6bd6`)

Every decision cites the source read. The paths are under `desktop/` unless said otherwise.

## R0. What decides that a chord is accepted today (source read)

A chord is accepted on either of two paths of `PracticeSession` (`TuxGuitar-practice/src/.../practice/session/PracticeSession.java`). One path is enough.

- **The attack path** (`PolyphonicAcceptance.offer`): the expected pitches must explain `minShare` (0.30) of what the attack added, the strongest must reach `minPeakRatio` (0.7), every separable pitch `minBalance` (0.10), for `hops` (6) hops in a row. Clipping removes the evidence. Its parameters are `PolyphonicAcceptance.Parameters.defaults()`, not settings.
- **The chord check** (`onChordFrame`, lines 707–744): `ChordMatcher.check` reads `ChordEvidence` (per required pitch: unique partials, clear unique partials, clear fraction). Every verifiable pitch must be heard in `stableHops` (8) frames in a row, within `windowNanos` (600 ms) of the strum's arming (`armTransientNanos`). When the window ends first, the feedback lists the missing pitches (`ChordFeedback.Kind.INCOMPLETE`) and a new strum is needed.
- **Follow and Arcade judge chords in their own loop.** `FollowSession` (lines 939–960), which Arcade runs through `ArcadeRun`, keeps for each detected onset (`PendingOnset`, created in `registerOnset`) the open events of its timing window as candidates. For each chord candidate and each usable frame it calls `ChordMatcher.check` and counts its own run (`onset.chordRun[c]`). It matches after `stableHops`, or records a wrong attempt when the frame's age since the onset (`seen.wall - onset.transient_`) passes `windowNanos`. It never goes through `PracticeSession.onChordFrame`. The calibration's `EventJudge` builds a `PracticeSession` (line 73).
- **The values** come from `RecognitionSettings` through `PracticeParameters.chordParameters()`: `CHORD_MIN_PARTIALS` (2), `CHORD_EVIDENCE` (1/3), `CHORD_CONFIRM` (43 ms), `CHORD_WINDOW` (600 ms), declared in `TuxGuitar-audio-engine/src/main/java/.../settings/RecognitionParameters.java` (lines 67–98). `CHORD_COMPLETENESS` (`recognition.chord.completeness`, one option `all-verifiable`) is declared and read nowhere.
- **Both engines feed the same Java decision.** The native engine writes the chord evidence into its frame record; with the Java engine `ChordEvidence.evaluate` computes it. `ChordEvidenceParityTest` holds them equal.

## R1. What the diagnostic and the probes found (US3, done)

- **The first two series** (42 takes, `owner-chords` and `owner-chords-2`): the diagnostic (`benchmark/diagnostic.md`, T003–T004) gave identical lines on both engines. 12 of the 16 refusals had every string heard, but not in the same frames; clipping was not a cause; 12 of the 13 wrong acceptances were voicings. The best lever (one string short) reached at most 34/42.
- **The strum hold probe** (`OwnerChordsStrumHoldProbeTest`, committed at `7901efe96`) simulated judging each string over the whole strum: it adds almost nothing (`benchmark/owner-chords-strum-hold-java.txt`).
- **The owner then listened to the first two series and judged them bad** (a contact problem), and recorded a third (R2).
- **On the third series, the per-string method keeps the same ceiling** (`benchmark/owner-chords-3/`, identical on both engines): 17/25 right accepted and 9/408 clearly wrong (the three G takes accepted as Dm); one string short gives 24/25 and 25/408. Holding the strings over the strum adds nothing.

**Conclusion.** No setting of the per-string chord check meets FR-001 and FR-002 together. The spec allows changing the method (Clarifications: the engines may change; the owner: "peut-être qu'il faut changer de méthode de reconnaissance").

## R2. The reference recordings are the owner's third series

**Decision.** `owner-chords-3` (`specs/004-attack-difference-recognition/benchmark/owner-chords-3/`, git-ignored, labels and figures committed in `benchmark/owner-chords-3/`) is the reference series of FR-001, FR-002, FR-006, FR-007 and SC-001 to SC-004:
- 25 takes, 8 chords chosen by the owner (Am, C, G, Em, D, A, Bm, Dm), three strums each (four for G), through the Rocksmith adapter, no clipping (peaks −14 to −21 dBFS), noise floor about −80 dBFS;
- names measured by two local tools that agree on all 25 takes (chroma template matching, Basic Pitch) and confirmed by the owner; the labels keep the notes Basic Pitch heard that belong to the chord's open shape;
- every take judged against its own chord (25 right) and against every other take's chord: 54 voicings (the same note names), 138 one name off, 408 clearly wrong.

**Rationale.** The owner chose it (2026-11-05: "ces enregistrements sont bien meilleurs, pars sur cela à la place des autres").

**What this changes in the spec.** The spec still counts "42 chords". Its FR-001, FR-002, SC-001, SC-006 and the first assumption name the first two series. **The spec needs an amendment through `/speckit-clarify`** (the owner's decision recorded, 42 → the 25 takes of `owner-chords-3`) before `/speckit-tasks`. The plan is written for the third series.

**The first two series stay a guard.** The committed `OwnerChordsBenchmarkTest` reads them by default and fails under its per-series minimum of accepted chords (`owner-chords-baseline.properties`: 8 and 18). Its count of wrong acceptances may stop failing (approved exception, Clarifications). So the new method must still accept at least 8 and 18 of them (R8).

## R3. The method: the chord's pitch-class profile matched against the expected chord

**Decision.** A new chord rule, *profile*, replaces the per-string chord check as the default:
1. **The profile.** For each frame after the strum's arming, the 12 pitch classes get the sum, over the pitches from one semitone below the expected chord's lowest pitch up to an upper bound, of the linear magnitude of the semitone band (`SpectrumFrame.getBandDb(midi)`, converted from dB). The frame profiles are summed from the arming on.
2. **The template.** The names of the chord's required pitches (the octave not counted), each weighted 1: the *expected names* everywhere below. The names of tolerated pitches (notes still sounding from an earlier beat, `PracticeSequenceBuilder`, that are not required names) are left out of the profile: their bands are not summed. A tolerated note may have faded or still ring; counted in the template it would lower the match of a right chord (three required names and one silent tolerated name match at most 0.87), counted as foreign it would lower it too.
3. **The match.** The cosine between the summed profile and the template.
4. **The names present.** Each expected name must hold at least a minimum share of the profile (`CHORD_PROFILE_NAME_FLOOR`, a fraction of the strongest expected name's sum). A name under it is weak. **Two weak names or more make the frame incomplete, whatever the match**, which is the spec's clearly wrong chord.
5. **The decision.** A frame is complete when the match reaches its threshold, at most one expected name is weak, and the settle time has passed. The chord is accepted after `stableHops` complete frames in a row, within the strum window.
6. **The feedback when refused.** The expected note names whose share of the profile is weakest are listed as missing, as the `INCOMPLETE` feedback does today (constitution XIV). Unverifiable pitches no longer apply to this rule (they are reported empty).

**Rationale.**
- **Measured.** The offline probe (`misc/owner-chords-chroma-probe.py`, librosa CQT chroma, first second after the strum, cosine) on the third series: at 0.85, 25/25 right, 0/138 one name off, 0/408 clearly wrong. The weakest right match is 0.851; the strongest clearly wrong one is 0.66 (`benchmark/owner-chords-3/chroma-probe.txt`).
- **It answers the diagnostic's cause.** The strings of a strum are never clear in the same frames; a profile summed over the strum does not need them to be.
- **It treats voicings alike** (the same names give the same template), as the spec asks (voicings reported apart, never counted), and it refuses a chord missing two names, whose template shares at most one name with the profile.
- **Deterministic and explainable** (constitution VIII): a sum, a cosine and a threshold, no learned model. Pitch-class profiles matched against chord templates are the classic chord recognition method (Fujishima 1999, "Realtime chord recognition of musical sound").

**Why the names present are checked, not only the match.** The cosine alone guarantees a refusal only for three-name chords. A played chord holding only two names of a four-name expected chord (a seventh) can reach a cosine of √(2/4) ≈ 0.71, and with five names √(3/5) ≈ 0.77, above the lower thresholds of the sweep. The reference series has only three-name chords, so it would not show it. Counting the weak names makes FR-002 hold for any chord size: two missing names are two weak names.

**Risks, each measured before the rule changes (R6, a stop condition):**
- the live window: the probe read 0.2–1.2 s after the strum, the strum window is 600 ms;
- the semitone bands are not a CQT: `getBandDb` is the strongest peak within ±50 cents of an FFT of 8192 samples;
- the bands are not floor-removed (`polyphonic.rs` takes `band_db` from the raw magnitudes, before `remove_floor`): the noise adds the same level to every name and, as a strum fades, draws every profile closer to every template. The probe sweeps a noise floor: a band more than `CHORD_PROFILE_NOISE` dB under the frame's strongest summed band (the tolerated names' bands not counted) is not summed;
- the partials: the root's fifth harmonic is a major third, which may pull a minor chord towards major;
- generalisation: 25 takes of one player and one guitar; GuitarSet and IDMT are the guards.

**Alternatives rejected.**
- **Keep tuning the per-string rule:** its ceiling is measured (R1).
- **Hold each string's evidence over the strum** (spec 023 experiment B, approved by the owner): measured, adds nothing (R1).
- **A learned model** (Basic Pitch or another network in the product): heavy, not explainable (constitution VIII), and the two engines would need it identically.

## R4. Where the profile is computed: in Java, on the spectrum both engines already give

**Decision.** The profile and the match are computed in Java from `AnalysisFrame.getSpectrum()`. No change to the native crate, to the frame record or to the JNI contract.
- **One rule, two callers.** `ChordMatcher` decides from a `ChordProfile` the caller holds (`check(profile, spectrum, required, tolerated)` gives the same `Result` as today, complete or not, with the missing pitches).
- `PracticeSession` (Wait) holds one profile, reset at each arming.
- `FollowSession` (Follow and Arcade) holds one profile per chord candidate of a `PendingOnset`, made with it in `registerOnset` (once per onset, as its other per-candidate arrays are) and summed per frame without allocation.
- So the rule is the same in every mode (FR-009), and the strum window keeps each mode's own reference: the arming in Wait, the onset in Follow and Arcade.

**Rationale (source read).**
- With chords in the score, `PracticeWindow` switches the spectrum on (`engine.setSpectrumEnabled(hasChords(...))`, lines 885, 1007, 1213).
- The native engine then writes the spectrum part of every frame (`pipeline.rs`, `PART_SPECTRUM` when `spectrum_on`), with the semitone band levels from MIDI 24 to 112 (`polyphonic.rs`, `band_db`, the strongest peak around each semitone of an 8192-sample Hann FFT). `FrameRecords` reads it into a `SpectrumFrame` (lines 173–182).
- The Java engine builds the same `SpectrumFrame` (`PolyphonicAnalyzer`, of which `polyphonic.rs` is the port; spec 023 parity tests).
- So one Java rule judges both engines' spectra: FR-009 holds by construction, and nothing is added to the JNI contract (the owner's rule).
- The calibration's `EventJudge` builds a `PracticeSession` (line 73), so the device calibration (spec 013) judges chords with the same rule; its committed tests are part of the gate.
- **Cost:** about 50 bands per frame, a sum per pitch class and a cosine of 12 values; arrays allocated once per event, none per frame (constitution VI). It runs in the session's frame handling, not on the audio callback.

**Alternative rejected.** Computing the profile in Rust and adding it to the frame record: a JNI change for no gain, since the bands are already there.

## R5. The new values are external parameters (FR-013)

**Decision.** Each value of the profile rule is a `RecognitionParameters` entry, read at run time from the recognition settings file (`tuxguitar-audio-recognition`), never a constant:

| Key | Meaning | Unit | Default | Bounds | Section |
|---|---|---|---|---|---|
| `recognition.chord.completeness` (existing) | the chord rule: `all-verifiable` (per string) or `profile` (new option) | choice | `profile` | — | chords |
| `recognition.chord.profile.match` (new) | the minimum cosine between the chord's profile and the expected chord | % | from the sweep (R6), about 85 | 50–99 | chords |
| `recognition.chord.profile.settle` (new) | the minimum time of profile summed before a judgement | ms | from the sweep | 0–500 | chords |
| `recognition.chord.profile.names` (new) | the minimum share of the strongest expected name that a name needs not to be weak; two weak names refuse the chord | % | from the sweep | 5–60 | chords |
| `recognition.chord.detail.top` (new) | the highest pitch summed into the profile | MIDI | from the sweep, about 88 | 60–112 | chord details (new section) |
| `recognition.chord.detail.noise` (new, only if the sweep keeps it) | a band more than this under the frame's strongest band is not summed | dB | from the sweep | 20–60 | chord details |
| `recognition.chord.detail.weight` (new, only if the sweep keeps it variable) | the magnitude summed: linear or compressed (square root) | choice | from the sweep | — | chord details |

The existing `CHORD_CONFIRM` (frames in a row) and `CHORD_WINDOW` keep their meaning for the profile rule.

**Where they sit in the Recognition settings window (source read).** The committed `RecognitionControlsTest.everySectionFitsWithoutScrolling` holds every section to `RecognitionControls.MAX_SECTION_ROWS` (8) rows, a section's rows being its parameters plus one (`RecognitionControls.rows`).
- The advanced section already has 7 parameters, so it is full.
- The chords section has 4 (`CHORD_EVIDENCE`, `CHORD_WINDOW`, `CHORD_COMPLETENESS`, `CHORD_FOREIGN`), so it takes the three values that decide: `CHORD_PROFILE_MATCH`, `CHORD_PROFILE_SETTLE` and `CHORD_PROFILE_NAMES`, reaching 7 parameters and 8 rows.
- The values that shape the profile go in a new section, `Section.CHORD_DETAILS` ("Chord details"), with its texts in three languages: `CHORD_PROFILE_TOP` always, and `CHORD_PROFILE_NOISE`, `CHORD_PROFILE_WEIGHT` and `CHORD_ATTACK` if the sweep keeps them. The committed tests iterate `Section.values()` generically (`RecognitionControlsTest`, `RecognitionSettingsPanelTest`, `RecognitionSettingsTest` line 68), so a section added with its texts and at least one parameter satisfies them.
- **The values of the per-string rule** (`CHORD_EVIDENCE`, `CHORD_MIN_PARTIALS`) stay, and their tips say that they apply to the rule "Every string separately" only, so the expert settings do not contradict the level (spec edge case).

- They reach the rule through `ChordMatcher.Parameters` (built by `PracticeParameters.chordParameters()`), read when a run starts, as today.
- Changing one in the Recognition settings window or in the file takes effect at the next run, with no rebuild of the application or of the native library.
- Both engines read the same values, because the rule is the same Java code (R4).
- New keys have their defaults when an old file lacks them; an old file keeps `all-verifiable` unless migrated (R7).

## R6. The sweep, the levels and the stop condition (FR-006, FR-007, SC-004)

**Decision.**
1. **A Java probe first, before any production change:** `OwnerChordsProfileProbeTest` (tag `dataset`, new) computes the profile exactly as R3 says, from the session's own `SpectrumFrame`s and arming, with each engine. It sweeps the threshold (0.70 to 0.95), the weak-name share, the settle time, the upper bound, the noise floor, the weighting, the confirmation (`CHORD_CONFIRM` at its default of 8 frames, and 4), and with or without the attack path. It reports, on the third series: right, voicings, one name off, clearly wrong; on the first two series: right accepted. It runs each configuration with both arming references: Wait's (the arming, `articulationLatencyNanos`) and Follow's (the onset, the age of each frame since it). A configuration qualifies only when both give the figures below.
2. **Stop condition A.** If no configuration gives 25/25 right and 0 clearly wrong on both engines and both arming references, with a weak-name share at least 0.05 above the leak (§3), while keeping at least 8 and 18 accepted on the first two series, the work stops and reports to the owner, with the fallback of the spec (Clarifications: no threshold per chord, the FR-003 lines kept, the rest refused as often as possible) proposed.
3. ***Normal*** is the configuration that, on both engines: accepts 25/25; accepts 0 clearly wrong; has a weak-name share at least 0.05 above the leak (the highest share that a name not played reaches in the reference takes, reported by the probe, with each engine), the margin written in `probe.md`, so that the synthetic clearly wrong four- and five-name chords of the unit tests, built with that leak, are refused (data model); keeps at least 8 and 18 accepted on the first two series; passes the guards (R8); and, among those, accepts the fewest one-name-off chords (ties: the highest threshold, then the shortest settle time).
4. ***Strict*** is *Normal* with a higher threshold, the highest that keeps 0 clearly wrong and at most *Normal*'s wrong acceptances, fewer when any setting allows; its tip says it may refuse some right chords.
5. ***Lenient*** is *Normal* with a lower threshold, the lowest that still accepts 0 clearly wrong; it accepts at least as many right chords. The weak-name rule is the same in every level, so two missing names are refused at every level.
6. The ordering of SC-004 is checked by a test on the recorded figures.

**The attack path.** It accepted 6 one-name-off chords and 0 clearly wrong on the third series (`owner-chords-strum-hold-java.txt`). The probe measures the profile with and without it. If it adds wrong acceptances without adding right ones, a chord flag `recognition.chord.detail.attack` (on/off, a level value, in the chord details section) turns it off for chords, in `PracticeSession` and in `FollowSession` alike (FR-009). It stays on for single notes in every case. **When it stays on for chords, it accepts a chord only if the profile summed so far has at most one weak name**, so that FR-002 holds whatever path accepts: the attack path judges strings, and the reference series, with only three-name chords, cannot show a four- or five-name chord accepted with two names missing.

## R7. Settings, migration and the approved test exceptions

**Decision.**
- **A level is a set of values** of the chord settings: `CHORD_COMPLETENESS`, `CHORD_PROFILE_MATCH`, and the other profile values the sweep moves between levels. The new pure class `ChordLeniency` (`TuxGuitar-audio-engine/src/main/java/.../settings/`) holds them.
- **The level shown is derived, not stored:** the named level whose values the default layer of `RecognitionSettingsStore` holds, otherwise *Custom*. Choosing a level writes its values with `RecognitionSettingsStore.update`, so the expert panel shows them (FR-012).
- ***Normal* is exactly the default values** (`RecognitionSettings.defaults()`): a fresh learner, "Restore defaults" and the committed benchmarks get *Normal*.
- **Migration** (FR-008): `RecognitionSettings.fromStrings` reads an old file whose chord values are those of spec 023 (`all-verifiable`, `CHORD_EVIDENCE` 1/3, `CHORD_CONFIRM` 43 ms, no profile key) with *Normal*'s values, like the legacy tempo key already there (lines 96–101). A file changed by hand keeps its values and shows *Custom*. `VERSION` stays 1 (`RecognitionSettingsStoreTest.valuesSurviveARestart` pins it).
- **`CHORD_EVIDENCE` and `CHORD_CONFIRM` defaults** stay as they are unless the sweep needs `CHORD_CONFIRM` to move; then only `RecognitionSettingsTest` lines 48 and 50 and `RecognitionControlsTest` lines 81–84 change (approved exception).
- **`OwnerChordsBenchmarkTest`**: its count of wrong acceptances counts voicings; with the profile rule it may exceed `wrong.max`. Only the two `wrong.max` lines of `owner-chords-baseline.properties` change, to the new figures, with a comment (approved exception); `accepted.min` stays. The new counts are checked by a new test (R8).
- **The expert name of `CHORD_COMPLETENESS`** becomes "Chord rule" ("Règle des accords", "Regla de acordes"), with the options "Note names over the strum" (`profile`) and "Every string separately" (`all-verifiable`). "Chord check" is the name of the level in the *Mode* tab only.
- **Follow and Arcade** keep relaxing the chord values one way (`FollowLeniency`, `ArcadeLeniency`). They relax `CHORD_EVIDENCE` and `CHORD_CONFIRM`; they must also lower the profile threshold by the same rule only if the sweep shows a need, otherwise the profile threshold is not relaxed. A test checks that Follow and Arcade keep the chord rule and its threshold (FR-009).
- **A device profile (spec 013)** may still overlay `CHORD_EVIDENCE` and `CHORD_CONFIRM`; the new values are not profile parameters.

## R8. The guards (FR-003, FR-004, FR-011)

**Decision.** Measured after, with each engine, against the figures before (`benchmark/before-{java,native}/`, measured in phase 1 at spec 023's code):
- `DatasetBenchmarkTest`, `TechniqueDatasetBenchmarkTest`: every single-note and wrong-note line not lower; Audio-Effects "chord a whole tone higher refused" at least 85 %;
- `GuitarSetChordBenchmarkTest`: complete chords not lower (33 and 23 of 56), and its controls reported;
- `OwnerChordsBenchmarkTest` on the first two series: at least 8 and 18 accepted;
- `BassRecognitionBenchmarkTest`: unchanged. It judges single bass notes only (`functional/BassRecognitionBenchmarkTest`), which the chord rule does not touch. A chord on a bass track is judged by the same rule as on a guitar (spec Clarifications: no bass rule of its own exists); the profile starts one semitone below the chord's lowest expected pitch, so it covers the bass range.
- **New:** `OwnerChordsProfileTest` (tag `dataset`): on the third series with the defaults, 25/25 right and 0 clearly wrong, with each engine; voicings and one name off reported.
- **New:** `ChordLeniencyComparisonTest` writes `benchmark/comparison.md` (before against after, both engines, each line pass or fail) and fails on any guard line lower.

The "incomplete chord refused" lines (IDMT-SMT-Guitar 8 of 8, Audio-Effects poly 35 of 36) are reported; any drop is told to the owner.

**Stop condition C.** If a guard line drops and no qualifying configuration of the probe avoids it, the work stops and reports to the owner.

## R9. The setting in the *Mode* tab (FR-005, FR-012)

Unchanged from version 1:
- `ChordLeniencyPanel` (new, `TuxGuitar-practice/src/.../practice/ui/`): a label "Chord check" and a drop-down *Strict*, *Normal*, *Lenient* (*Custom* added only while the values match no level), in the *Mode* tab of `PracticeWindow`, the row after `FollowPanel`.
- `UIDropDownSelect` has no tooltip (spec 025 finding): the chosen level's tip is a wrapped line under the drop-down. Texts in English, French and Spanish in `TuxGuitar-practice/share/lang/messages*.properties`.
- During a run the panel stays enabled; the change applies from the next run (the session's `ChordMatcher` is built at the start, `PracticeWindow` line 865) and never stops the run.

## R10. The engine documentation (FR-014)

**Decision.** One document, `docs/architecture/recognition-engines.md`, linked from `docs/architecture/README.md`:
1. **How each engine works:** the frame analysis (level, pitch, onset, spectrum), the attack evidence, the chord evidence, the profile; and the decision in the practice session (the two acceptance paths, the arming, the window).
2. **Their interface:** what the application gives (samples, expected pitches, expected chord, requests such as the spectrum) and what they return (the frame record, `frame_layout.txt`), including the native JNI functions (`jni_recognition.rs`) and `NativeParameters` (21 values).
3. **Every parameter:** name, key, meaning, unit, default, bounds, where it is set (settings window, file, level, device profile), for both engines.
4. **The constants still fixed in the code**, listed by file (for example `chord.rs` `PARTIAL_PRESENCE_RATIO` 0.05 and `NEIGHBOUR_MARGIN` 1.5, `polyphonic.rs` `FLOOR_HALF_WIDTH_HZ` 25 and `FLOOR_FACTOR` 2, the defaults of `PolyphonicAcceptance.Parameters`), marked as spec 026's.

## R11. Platforms

- No native change (R4): the native library of spec 023 is used as built.
- Validated: macOS (Apple Silicon): unit tests, the full gate and the benchmarks with both engines.
- Built by CI only: Linux and Windows, with the same Java code, so the same rule and setting (no "only on macOS" line).
- The owner's gate: sheet F15, played through the cable in Wait, Follow and Arcade (SC-005).
