# The chord check levels on the data (spec 024, T036)

Run on 2026-11-05 with `ChordLeniencyLevelsTest` (tag `dataset`), once per engine, from the macOS SWT build directory:

```sh
mvn -o test -pl ../../TuxGuitar-practice -am -Dtuxguitar.recognition.engine=$e -Dtuxguitar.native.library.dir=$LIB \
  -Dtuxguitar.owner.chords=$OC3 -Dgroups=dataset -DexcludedGroups= -Dtest=ChordLeniencyLevelsTest -Dsurefire.failIfNoSpecifiedTests=false
```

The reports are `chord-leniency-levels-java.txt` and `chord-leniency-levels-native.txt` in this folder. **Both engines give the same figures.**

## The owner's reference series (owner-chords-3, 25 takes, Rocksmith cable)

Each take is judged against its own chord and against every other chord of the series, in the Wait session, with the level's values (data-model "Chord leniency level").

| Level | Values (rule, match, attack) | Right accepted | Voicings accepted (reported apart) | One name off accepted | Clearly wrong accepted |
|---|---|---|---|---|---|
| *Strict* | profile, 0.79, off | 23 of 25 | 7 of 10 | 0 of 138 | **0 of 408** |
| *Normal* (defaults) | profile, 0.79, on | **25 of 25** | 10 of 10 | 6 of 138 | **0 of 408** |
| *Lenient* | profile, 0.70, on | 25 of 25 | 10 of 10 | 69 of 138 | **0 of 408** |

The ordering of data-model and SC-004 holds:
- every level accepts 0 clearly wrong chords;
- *Lenient* accepts at least *Normal*'s right chords (25 ≥ 25);
- *Strict* wrongly accepts at most as many as *Normal* (0 ≤ 6, one name off and clearly wrong counted).

*Strict* refuses 2 right takes of 25: they are accepted by *Normal* on the attack of the strum only, which *Strict* never uses. Its line in the *Mode* tab says that some right chords may be refused.

## The incomplete chords of the public datasets (reported, not asserted)

These are the 44 cases of `DatasetBenchmarkTest` ("incomplete chord refused (one more note expected)": IDMT-SMT-Guitar and IDMT-SMT-Audio-Effects, sample 8, one note more expected than played).

| Level | Incomplete chords accepted |
|---|---|
| *Strict* | 24 of 44 |
| *Normal* | 24 of 44 |
| *Lenient* | 32 of 44 |

**The spec's edge case "Strict refuses it more often" is not met:** *Strict* refuses the same incomplete chords as *Normal*. An incomplete chord is accepted by the profile over the strum, not by the attack path, and the two levels have the same match. The owner corrected the edge case to this measure through `/speckit-clarify` on 2026-11-06 (option A, no code change); the guide and the release notes already said only what was measured.

## The native panel

`ChordLeniencyPanelSwtTest` was not run overnight (tasks.md, Phase 5 note). It ran on the owner's return on 2026-11-06, with the other SWT tests of the branch: 92 tests, 0 failures.
