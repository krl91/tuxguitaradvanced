# Probe of the pitch-class profile (T006–T007)

**Date**: 2026-11-05. **Test**: `OwnerChordsProfileProbeTest` (tag `dataset`), run with `-Dtuxguitar.recognition.engine=java` and `native`, on `owner-chords-3` (reference) and the first two series (guard).

**Reports**:
- `owner-chords-profile-probe-java.txt` and `owner-chords-profile-probe-native.txt`: the summary and every right chord's detail;
- `probe-qualifying-without-leak.tsv`: the 3,992 configurations that meet every criterion but the leak's, on both engines and both arming references.

The full tables (`.tsv`, 10 MB each) stay in `desktop/TuxGuitar-practice/target/dataset-benchmark/`.

## Verdict: stop condition A, caused by the leak criterion only

No configuration qualifies as research R6 defines it. Every configuration fails on the same criterion: the weak-name share must be at least 0.05 above the leak.

- **The leak.** In the reference takes judged against their own chord, a note name that is not played reaches **0.61 to 0.85** of the strongest expected name, depending on the configuration (both engines).
  - The cause is the guitar's harmonics and the open strings' sympathetic ringing. For example, the fifth harmonic of a root is a major third, which is not in a minor chord.
  - So a share above the leak (0.66 to 0.90) would also refuse right chords, and the sweep stops at 0.50.
- **Without the leak criterion, 3,992 configurations qualify on both engines and both arming references (Wait's and Follow's):**
  - 25/25 right accepted;
  - 0/408 clearly wrong accepted;
  - at least 8 and 18 accepted on the first two series. This is exactly the guard's minimum, so the margin is nil.
- **The two engines give the same figures** (their summaries differ only in the engine name).

## The figures behind the verdict

| | right (of 25) | clearly wrong (of 408) | one name off (of 138) | first two series |
|---|---|---|---|---|
| Today (per-string rule, `benchmark/owner-chords-3/`) | 17 | 0 | — | 8 and 18 (committed) |
| Profile, attack path off, best configuration | 23 | 0 | — | — |
| Profile, attack path guarded, e.g. top 84, no noise floor, linear, settle 200 ms, share 0.05–0.50, confirm 8 or 4, threshold 0.79 | **25** | **0** | 6 | 8 and 18 |

- **Margin, that example configuration.** The weakest right acceptance is at 0.84, or 0.80 with a share of 0.50. The strongest clearly wrong one is at 0.71, or 0.66 with a share of 0.40 or more. This holds with both engines and both references.
- **Takes 10 and 11 (G, Em) have no onset detected by either engine.** Neither Wait nor Follow arms a strum for them, so the profile rule never judges them. Only the attack path accepts them. That is why the attack path cannot be turned off.
  - When no strum is armed, its guard reads the profile of the attack's own frame. This detail is not in the plan yet; it must be added if the attack path stays guarded.
- **The D takes (14–16) reach a best match of about 0.80** (top 88), lower than the other chords (0.85–0.90). This is why the threshold sits at 0.79 or under.
- **The weight, the noise floor and the settle time hardly change the figures.** The plain choices (linear, no floor) qualify. Neither would need to become a setting.

## What the owner decides (stop condition A)

The leak criterion came from the analysis of 2026-11-05 (finding U3, margin 0.05), not from the spec. Its goal was to make sure that a four- or five-name chord missing two names is refused, which the reference series cannot show. The probe shows the criterion cannot be met with real guitar spectra.

1. **Drop the leak criterion** and keep the weak-name rule at a low share (0.05–0.30), which every qualifying configuration allows.
   - FR-002 is met on the reference series (0/408 with both engines).
   - The four- and five-name case stays covered by unit tests with silent missing names only. On a real seventh chord with two names missing, the harmonics may still fill those names, and the risk is written in the final report.
2. **Record seventh chords** (four names) to measure this case before choosing.
3. **The spec's fallback** (Clarifications): not needed, since the spec's own targets are met without the leak criterion.
