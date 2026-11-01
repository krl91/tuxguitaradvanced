# Quickstart: validating spec 022 (the guitar heard through an amp and effect pedals)

The JDK is pinned as in `CLAUDE.md`. `BUILD` = `desktop/build-scripts/tuxguitar-macosx-swt-cocoa`.

## 1. The delay probe (Phase 1, research R1)

```sh
cd BUILD
mvn test -pl ../../TuxGuitar-audio-engine -am -Dgroups=benchmark -DexcludedGroups= \
    -Dtest=MonitorLatencyProbeTest -Dsurefire.failIfNoSpecifiedTests=false
```

- **Setup:** speakers on, built-in microphone, a quiet room.
- **Report:** `specs/022-guitar-amp-effects/benchmark/latency-probe.md`. It gives the round-trip median and 95th percentile for each block size, and the underruns over 60 s.
- **Expected:** a block size whose median is at most 20 ms (target), or at most 30 ms (shipping ceiling). Otherwise the native branch is planned (research R1).

## 2. Automated checks (every phase)

```sh
cd BUILD && mvn -o -e clean verify -P native-modules
```

The suites cover:
- the pure effects on synthetic signals (sines, impulses, steps, silence): determinism, ranges, no NaN, tails, smooth knob changes, safe ceiling;
- the chain text, `ChainSettings` validation, `ReadyMadeChains`, `ScoreKey`, `AmpSoundStore` (settings kept, sounds, restore, sound per score, deleted sound);
- every ready-made sound below −60 dBFS at the output with a −70 dBFS noise as input (FR-011);
- `MonitorEngine` on a fake device: queue never blocks, dropped and silent blocks counted, fade in and out, feedback guard on a synthetic howl (muted within 0.5 s), overload lightening;
- **recognition untouched (SC-002):** the same recorded inputs, sound off and on with the four ready-made sounds, give identical hops, verdicts and summaries;
- every text key of the window has its label and tooltip in English, French and Spanish (`AmpTextsTest`);
- `swt-gui-tests`: the *Amp and effects* window, its rows, tooltips and placement away from the framed note.

Benchmark reports (excluded by default, run like §1 with `-Dtest=ChainCostBenchmarkTest` or `-Dtest=MonitorEnduranceBenchmarkTest`): the cost of the full chain per block (goal under 25 %) and 10 minutes without dropped or silent blocks.

After each build, restore `specs/003-arcade-mode/benchmark/`.

## 3. On the built application (the owner, macOS)

1. Plug the guitar with the Rocksmith cable and wear headphones. Open a score, then the practice window, then *Amp and effects…*.
2. Turn on *Amp sound*: it rises in about half a second, and the guitar is heard through *Clean*. Read the delay line.
3. Pick *Crunch*, *Lead* and *Clean echo*, and move each knob while playing: no click, and the change is heard at once. Add, remove and move a pedal, and switch sounds while playing: no click either.
4. Add and remove pedals, and reorder them within their section.
5. Start Wait, Follow and Arcade runs: the guitar is heard with the score, and the verdicts behave as usual. Set *Guitar volume* and *Score volume*.
6. Pick *Lead* for this score, open another score and pick *Clean*, restart the application, and reopen both: each one gets its own sound back.
7. Choose the built-in microphone as input and turn the sound on: the warning shows. Remove the headphones, and a howl is cut within half a second with a message.
8. Unplug the headphones or the interface: the sound stops with a message, and the practice goes on.

## 4. Linux and Windows

These checks are done by testers, through the release notes and the test sheet F12: the delay line's value, steps 2 to 5, and a howl.
