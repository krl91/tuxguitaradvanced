# Spec 026 benchmark: the figures before any change (T002)

Taken on 2026-11-07 at HEAD bd19f8126 (code), from `desktop/build-scripts/tuxguitar-macosx-swt-cocoa`, once per engine:

```sh
mvn -o -fae test -pl ../../TuxGuitar-audio-engine,../../TuxGuitar-practice -am \
  -Dtuxguitar.recognition.engine=$e -Dtuxguitar.native.library.dir=$LIB -Dtuxguitar.owner.chords=$OC3 \
  -Dgroups=dataset,benchmark -DexcludedGroups= -Dsurefire.excludesFile=<the list below> -Dsurefire.failIfNoSpecifiedTests=false
```

Both runs: BUILD SUCCESS (practice: 40 tests, 9 skipped for missing local files; engine: 6).

Left out, because they measure the devices or the clock rather than the recognition, and must not play a sound unannounced:
- they open the sound card or the microphone: `MonitorLatencyProbeTest`, `NativeMonitorProbeTest`, `RealtimeEnduranceBenchmarkTest`, `DualEngineCableArrivalProbeTest`, `DualEngineCableProbeTest`, `ArcadeHitDelayProbeTest`, `RecognitionDelayProbeTest`;
- it opens a window: `ArcadeRefreshRateBenchmarkTest`;
- ten-minute wall-clock endurance runs: `MonitorEnduranceBenchmarkTest`, `NativeRecognitionEnduranceBenchmarkTest`. A first run stopped on `MonitorEnduranceBenchmarkTest` (17 and 34 dropped blocks on a normal thread, which spec 022 measured as 20 to 30); it has nothing to do with recognition and was not changed.

`before-java/` and `before-native/` hold the reports of `TuxGuitar-practice/target/dataset-benchmark/`; the large `.tsv` probes are gzipped.
`compare.sh <engine> <new reports folder>` masks dates and durations, then runs `diff -r`; it was checked to give "identical" on each engine's own reports and a difference across engines.
