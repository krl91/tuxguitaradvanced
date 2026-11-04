# Chord experiments (spec 023, US3, T076 to T080)

The targets of SC-004 are aimed at, not blocking (clarification of 2026-11-02). Each experiment is recorded here: what, why, the figures, kept or dropped.

## Protocol

1. A Rust test of the new behaviour first, seen red.
2. The experiment is behind a switch that is off by default, so the native engine stays at parity with Java while it is off. The switch is a Cargo feature of the crate (`chord-a`, `chord-b`, `chord-c`), not a value of `NativeParameters`. The parameter array is part of the JNI contract, and nothing is added to that contract without the owner. An experiment that is kept becomes the default behaviour of the crate, and its feature is removed.
3. The chord benchmarks (`OwnerChordsBenchmarkTest`, `GuitarSetChordBenchmarkTest`, the Audio-Effects lines of `DatasetBenchmarkTest`) and the comparison (T037) are run with the native library built with the experiment on.
4. The experiment is kept only if every SC-001 line still holds and at least one SC-004 line moves toward its target.
5. Only deterministic methods (constitution VIII). A change to a shared Java rule (`PolyphonicAcceptance`, the rule of `ChordMatcher`) is measured on both engines and recorded (FR-007).

### What the datasets can see

A chord is accepted on either of two paths of `PracticeSession`, and one of them is enough:

- **Attack.** `PolyphonicAcceptance` reads the attack evidence of each hop. The native engine computes that evidence offline too, from the attack candidates that `OfflineAnalysis` receives, so a change in `attack.rs` reaches the dataset figures.
- **Chord check.** `ChordMatcher` reads the chord evidence. Live, the native engine computes it for the expected chord (`setExpectedChord`). Offline, `OfflineAnalysis` does not pass the expected chord, so `ChordMatcher` computes the evidence in Java from the native spectrum. A change in `chord.rs` therefore changes the live judgement only, and the committed dataset benchmarks (`DatasetJudge`) cannot measure it. Measuring it needs a judge that passes the expected chord to the native offline analysis. Such a judge is new test code: `DatasetJudge` itself is committed and stays as it is.

## Starting figures (from `comparison.md`, native engine, identical to Java)

| SC-004 line | Start | Target |
|---|---|---|
| Owner's chords accepted (of 42) | 26 | ≥ 34 |
| Owner's neighbouring chords wrongly accepted (of 42) | 13 | ≤ 6 |
| GuitarSet complete chords, six string channels summed (of 56) | 33 | ≥ 40 |
| GuitarSet complete chords, microphone (of 56) | 23 | ≥ 30 |
| Audio-Effects chord a whole tone higher refused | 66.7 % | ≥ 85 % |

## Experiments

The instrument of these experiments is `NativeChordEvidenceBenchmarkTest` (excluded from the default run, `-Dtest=NativeChordEvidenceBenchmarkTest`). It judges as `DatasetJudge.accepts`, except that the offline analysis receives the expected chord, so the chord check reads the native evidence. With the native library built without any feature, it gives the figures of the committed benchmarks: owner's chords 26/42 accepted and 13/42 neighbours wrongly accepted; IDMT-SMT-Guitar recognised 15/16, incomplete refused 8/8, whole tone higher refused 16/16; Audio-Effects poly recognised 63/66 (95.5 %), incomplete refused 35/36 (97.2 %), whole tone higher refused 44/66 (66.7 %).

A diagnostic run with the attack path removed (chord check alone, native) showed that every one of the 22 wrong acceptances of the whole tone higher in Audio-Effects poly comes from the chord check.

### A: the chord a whole tone away (T077), feature `chord-a`

**What.** Before the residual check, `ChordEvaluator::evaluate` compares the harmonic sum (the comb salience already used by the polyphonic analysis) of the expected pitches with that of the same chord a whole tone lower and a whole tone higher. If either neighbour explains the partials more than 1.5 times better (`NEIGHBOUR_MARGIN`), every pitch of the expected chord is marked as not heard (no clear unique partial).

**Why.** The ninth partial of a note is 2.04 semitones above its eighth, so it coincides with the eighth partial of the note a whole tone higher. Through a distortion, which gives every partial, a chord played a whole tone lower than the one expected therefore shows two clear unique partials for each expected pitch, and the chord check accepts it. The harmonic sum is dominated by the fundamentals and the low partials, which are those of the chord really played. Diagnostic on the distorted G power chord of the Rust test: the played chord explains 6.54 times better than the expected one a whole tone higher; the other way round, the ratio is 0.1 to 0.2. A margin of 1.5 is far from both.

**Test first.** `a_chord_a_whole_tone_lower_than_expected_is_not_its_evidence` (G power chord and A major through a strong soft clip, C major with almost none): no pitch missing when the chord played is expected, one missing when the chord a whole tone higher is expected. Seen red on the G power chord before the change, green after.

**Figures (instrument, native, `--features chord-a`).**

| Line | Without | With A |
|---|---|---|
| Owner's chords accepted (of 42) | 26 | 26 |
| Owner's neighbouring chords wrongly accepted (of 42) | 13 | 13 |
| IDMT-SMT-Guitar recognised / incomplete refused / whole tone higher refused | 15/16, 8/8, 16/16 | 15/16, 8/8, 16/16 |
| Audio-Effects poly recognised | 63/66 (95.5 %) | 63/66 (95.5 %) |
| Audio-Effects poly incomplete refused | 35/36 (97.2 %) | 35/36 (97.2 %) |
| Audio-Effects poly whole tone higher refused | 44/66 (66.7 %) | **66/66 (100 %)** |

The SC-004 whole-tone line passes its target (≥ 85 %) live, with no loss on the other chord lines. The owner's neighbours are not a whole tone away from the chord played, so A does not touch them.

The GuitarSet complete chords were added to the instrument after A (judged as `GuitarSetChordBenchmarkTest`: accepted within the excerpt, not more than 50 ms ahead of the strum). Without any feature and with A they give the committed figures, 33/56 summed and 23/56 microphone.

### B: the strum spread (T078), feature `chord-b`

**What.** `ChordEvaluator` holds each partial at its highest over the last 15 hops (`STRUM_HOPS`, 80 ms of 256-sample hops at 48 kHz) before it evaluates the chord. The strings of a strum start one after another, so the evidence of the whole strum counts, not only that of the last hop.

**Why.** On a strum, the first strings fade while the last ones start, and no single hop may carry every pitch.

**Test first.** `the_pitches_of_a_strum_count_over_its_hops`, where each pitch sounds in four hops of its own, was seen red: two pitches were missing in the last hop. It is green with B. `what_sounded_more_than_a_strum_ago_does_not_count` guards the length of the hold.

**Figures (instrument, native).**

| Line | Without | With B |
|---|---|---|
| Owner's chords accepted (of 42) | 26 | 29 |
| Owner's neighbouring chords wrongly accepted (of 42) | 13 | **19** |
| IDMT-SMT-Guitar | 15/16, 8/8, 16/16 | 15/16, 8/8, 16/16 |
| Audio-Effects poly recognised / incomplete refused | 63/66, 35/36 | 64/66, 35/36 |
| Audio-Effects poly whole tone higher refused | 44/66 (66.7 %) | **38/66 (57.6 %)** |
| GuitarSet complete chords, summed / microphone | 33/56, 23/56 | 33/56, 27/56 |

With A and C as well, the neighbours wrongly accepted stay at 19/42.

**Dropped.** B accepts more played chords, but it accepts more wrong ones in the same proportion. The held partials of what rang just before count as evidence for the chord that follows. Two SC-004 lines move away from their targets, and a wrong chord accepted is a wrong note accepted.

### C: the weak fundamental of a low string (T079), feature `chord-c`

**What.** A required pitch whose fundamental is under the presence ratio (5 % of the strongest partial of the chord), while its second and third partials are both above it, is heard. Neither of those two partials may be a partial of a lower expected pitch (within the 25 cents of the polyphonic analysis). Its clear unique partials count as at least two, and their fraction as at least a third, which is what the rule of `ChordMatcher` asks. A, when on, is applied after C and overrides it.

**Why.** A low string picked up by a microphone gives little of its fundamental and of its high partials. Next to a loud treble note, only its second and third partials pass the presence ratio. That is two clear partials out of eleven unique ones, under the third the rule asks.

**Test first.** `a_low_string_with_a_weak_fundamental_is_heard_by_its_second_and_third_partials` (E2 with weak fundamental and high partials, under a loud G3) was seen red: E2 had 2 clear partials of 11, a fraction of 0.18. It is green with C. `partials_a_lower_expected_pitch_explains_are_not_evidence` (E2 played, B2 expected as well) guards the condition on the lower pitch.

**Figures (instrument, native).**

| Line | Without | With C | With A and C |
|---|---|---|---|
| Owner's chords accepted (of 42) | 26 | 27 | 27 |
| Owner's neighbouring chords wrongly accepted (of 42) | 13 | 13 | 13 |
| IDMT-SMT-Guitar | 15/16, 8/8, 16/16 | 15/16, 8/8, 16/16 | 15/16, 8/8, 16/16 |
| Audio-Effects poly recognised | 63/66 | 64/66 | 64/66 |
| Audio-Effects poly incomplete refused | 35/36 | 35/36 | 35/36 |
| Audio-Effects poly whole tone higher refused | 44/66 (66.7 %) | 44/66 | **66/66 (100 %)** |
| GuitarSet complete chords, summed / microphone | 33/56, 23/56 | 34/56, 24/56 | 34/56, 24/56 |

C moves four lines a little toward their targets and loses nothing on these lines.

**Corrected.** In the gates, the committed `PolyphonicAnalyzerTest.uniquePartialsSeparateSoundingFromMissingChordTones` failed with C. In E major without its low E, the second and third partials of E2 are E3 and B3, which other strings of the chord play, so C heard the missing E2. The condition only excluded partials of a *lower* expected pitch. A Rust test reproducing the case (`partials_another_expected_pitch_plays_are_not_evidence`) was seen red. C was then corrected on the first attempt: neither partial may be a partial of any other pitch that sounds, expected or tolerated. With the correction, the gains on the owner's chords and on GuitarSet were those of the flaw, and they are gone. What remains is one more Audio-Effects poly chord recognised (63 → 64 of 66).

## Kept: A and C, in both engines (T080)

The protocol planned an experiment, once kept, to become the default of the native engine only. A native-only change makes the committed parity test `ChordEvidenceParityTest` fail, since the native chord evidence would no longer be the Java one. The owner chose on 2026-11-03 to apply A and C to both engines (`ChordEvidence.evaluate` in Java, `ChordEvaluator::evaluate` in the crate), as FR-007 allows for "an improvement of US3 deliberately applied to both engines and measured". The features are removed and B's code is deleted (its record stays above). The parity test passes unchanged.

The Java outcome files then differ from `before/`, so the committed FR-007 check of `EngineComparisonReportTest` failed. The owner approved, on 2026-11-03, relaxing that check: a Java outcome file may differ from `before/` only by lines that are KO before and OK after, and both engines must give the same lines. Any line going from OK to KO still fails.

**Figures after T080** (the five dataset benchmarks on both engines, `after-java/`, `after-native/`, `comparison.md`; the two engines give identical outcome files):

- `outcomes.csv`: 23 lines go from KO to OK and none from OK to KO. 22 of them are "chord a whole tone higher refused" in Audio-Effects poly and one is "recognised when played", a distorted major triad. GuitarSet's outcomes are identical to `before/`.
- Audio-Effects poly: whole tone higher refused 66.7 % → 100 %, recognised 95.5 % → 97 %, incomplete refused 97.2 % (unchanged).
- Every SC-001 line passes on both engines.

The instrument, which also passes the expected chord to the offline analysis, gives the same figures on both engines: owner's chords 26/42 accepted and 13/42 neighbours wrongly accepted, GuitarSet 33/56 and 23/56, Audio-Effects poly 64/66 recognised, 35/36 incomplete refused and 66/66 whole tone higher refused.

## SC-004: reached or not

| Target | Start | After | Status |
|---|---|---|---|
| Owner's chords accepted ≥ 34/42 | 26 | 26 | not reached |
| Owner's neighbouring chords wrongly accepted ≤ 6/42 | 13 | 13 | not reached |
| GuitarSet complete chords ≥ 40/56, six string channels summed | 33 | 33 | not reached |
| GuitarSet complete chords ≥ 30/56, microphone | 23 | 23 | not reached |
| Audio-Effects chord a whole tone higher refused ≥ 85 % | 66.7 % | 100 % | **reached** |

The owner's neighbours are other chords, not the same chord a whole tone away, so A does not reach them. The only experiment that raised the owner's and GuitarSet's acceptance, B, raised the wrong acceptances as much. These targets stay open for later work.
