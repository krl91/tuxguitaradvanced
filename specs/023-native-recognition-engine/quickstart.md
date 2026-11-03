# Quickstart: validating the native recognition engine (spec 023)

Prerequisites:
- JDK 21 (`export JAVA_HOME=$(/usr/libexec/java_home -v 21); export PATH="$JAVA_HOME/bin:$PATH"`).
- Rust 1.93.0.
- The datasets in `~/tuxguitar-datasets` (GuitarSet, IDMT, the owner's chords).
- Every command runs from `desktop/build-scripts/tuxguitar-macosx-swt-cocoa`.
- After each Maven build: `git checkout -- specs/003-arcade-mode/benchmark/`.

## 1. Before (Java baseline)

1. Copy the Java dataset reports of 2026-11-02 into `specs/023-native-recognition-engine/benchmark/before/`.
2. Live delay of the Java engine (**audible tones: warn the owner first**):

   ```sh
   mvn test -pl ../../TuxGuitar-practice -am -Dgroups=benchmark -DexcludedGroups= \
     -Dtest=RecognitionDelayProbeTest -Dtuxguitar.recognition.engine=java -Dsurefire.failIfNoSpecifiedTests=false
   ```

   Expected: `benchmark/recognition-delay.md` has the Java median and 90th percentile, built-in microphone (USB microphone if plugged in).

## 2. Native analysis offline

```sh
(cd ../native-modules/tuxguitar-monitor-native && cargo test --release)
mvn test -pl ../../TuxGuitar-audio-engine -am -Dtest='*ParityTest' -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: every stage's parity within the tolerances of research R3.

## 3. Datasets, both engines

```sh
for e in java native; do
  mvn test -pl ../../TuxGuitar-practice -am -Dgroups=benchmark -DexcludedGroups= -Dtuxguitar.recognition.engine=$e \
    -Dtest='DatasetBenchmarkTest,GuitarSetChordBenchmarkTest,TechniqueDatasetBenchmarkTest,OwnerChordsBenchmarkTest,BassRecognitionBenchmarkTest' \
    -Dsurefire.failIfNoSpecifiedTests=false
  # copy target/dataset-benchmark/ into benchmark/after-$e/
done
mvn test -pl ../../TuxGuitar-practice -am -Dgroups=benchmark -DexcludedGroups= -Dtest=EngineComparisonReportTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: `benchmark/comparison.md` has every SC-001 line at "pass", and SC-004 reported as reached or not.

## 4. Full suite, both engines

```sh
mvn -e clean verify -P native-modules                                    # Automatic → native
mvn test -pl ../../TuxGuitar-audio-engine,../../TuxGuitar-practice,../../TuxGuitar-tuner-next -am -Dtuxguitar.recognition.engine=java
```

Expected: both green, with no committed test changed.

## 5. Live (owner's Mac)

Each item below plays audible tones, so warn the owner first.

- `RecognitionDelayProbeTest` with `-Dtuxguitar.recognition.engine=native`: the median at least 20 ms below Java's, and the 90th percentile not higher (SC-002).
- `DualEngineCableProbeTest`, with the owner playing 20 notes through the Rocksmith cable (both engines listen at once): the native frame at least 20 ms ahead of the Java frame at the median, and the native 90th percentile not higher than Java's (SC-002, cable).
- `NativeRecognitionEnduranceBenchmarkTest`: 10 minutes, 0 dropped blocks (SC-005), and one hop's analysis under 10 % of a hop (0.53 ms at 48 kHz) at the 99th percentile (`cost.md`).
- `ArcadeRefreshRateBenchmarkTest`: ≥ 99 % of the frames on time at 120 Hz, and no frame waiting on the recognition (SC-006).
- The dataset identity with the amp sound on and off, with the native engine (FR-010).

## 6. In the application (sheet F13, owner)

1. *Recognition settings → Pitch detection engine*: it shows *Automatic (Native)*, *Native (Rust)* and *Java (FastYin)*, with tips. Choose each, restart, and check that the choice is kept.
2. Wait, Follow and Arcade with the Rocksmith cable, on the native engine: the verdicts are felt as quicker than on Java.
3. Rename the native library in the `.app`, then start a run: a notice says the native engine is unavailable, the run works on Java, and it starts within 2 s.
4. Turn the amp sound on: the recognition is unchanged.
