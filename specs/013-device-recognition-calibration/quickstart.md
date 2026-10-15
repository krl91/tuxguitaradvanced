# Quickstart: running and checking spec 013

From `desktop/build-scripts/tuxguitar-macosx-swt-cocoa`, `export JAVA_HOME=$(/usr/libexec/java_home -v 21)`.

1. **The profile layer and the library:** `mvn test -pl ../../TuxGuitar-audio-engine -am -Dtest='RecognitionSettingsStoreProfileTest,ProfileLibraryTest,ProfileStateTest'`: with no profile `get()` equals `getDefault()`; a profile changes only its parameters; every writer leaves the default untouched; the library survives a "restart" (a new library over the same persistence).
2. **The pure parts:** `-Dtest='SoundCounterTest,SoundSegmenterTest,EventJudgeTest,CalibrationScorerTest,ProfileSearchTest,CalibrationFlowTest'`: sounds counted on synthetic signals, a candidate scored, the search deterministic with the default first.
3. **Independent measure:** the 22 built-in-microphone notes judged with the default and with a profile found on another recording: `-Dtest=BuiltinMicProfileTest -Dgroups=benchmark -DexcludedGroups=`; the figures go to `specs/013-.../benchmark/`.
4. **The owner's own recording** of `basic_test.tg` on the built-in microphone (outside git): the search on it, the rate with the default and with the profile, reported.
5. **Nothing changes without a profile:** the whole default suite `mvn test -pl ../../TuxGuitar-practice -am` equal to the baseline (937) plus the new tests, the recognition reports equal to `specs/010-pre-run-check/benchmark/after/`.
6. **By hand (owner):** in the Input tab press *Calibrate the recognition*, follow the windows, play the piece; the dot turns green (or the message with three choices appears); restart, select the device: the dot is green again; select another device: orange.
