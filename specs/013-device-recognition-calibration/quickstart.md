# Quickstart: running and checking spec 013

From `desktop/build-scripts/tuxguitar-macosx-swt-cocoa`, `export JAVA_HOME=$(/usr/libexec/java_home -v 21)`.

1. **The profile layer and the library:** `mvn test -pl ../../TuxGuitar-audio-engine -am -Dtest='RecognitionSettingsStoreProfileTest,ProfileLibraryTest,ProfileStateTest'`: with no profile `get()` equals `getDefault()`; a profile changes only its parameters; every writer leaves the default untouched; the library survives a "restart" (a new library over the same persistence).
2. **The pure parts:** `-Dtest='SoundCounterTest,SoundSegmenterTest,EventJudgeTest,CalibrationScorerTest,ProfileSearchTest,CalibrationFlowTest'`: sounds counted on synthetic signals, a candidate scored, the search deterministic with the default first.
3. **Independent measure:** the 22 built-in-microphone notes judged with the default and with a profile found on another recording: `-Dtest=BuiltinMicProfileTest -Dgroups=benchmark -DexcludedGroups=`; the figures go to `specs/013-.../benchmark/`.
4. **The owner's own recording** of `basic_test.tg` on the built-in microphone (outside git): the search on it, the rate with the default and with the profile, reported.
5. **Nothing changes without a profile:** the whole default suite `mvn test -pl ../../TuxGuitar-practice -am` equal to the baseline (937) plus the new tests, the recognition reports equal to `specs/010-pre-run-check/benchmark/after/`.
6. **By hand (owner):** in the Input tab press *Calibrate the recognition*, follow the windows, play the piece; the dot turns green (or the message with three choices appears); restart, select the device: the dot is green again; select another device: orange.
7. **Refusals and the three choices:** `-Dtest='CalibrationFlowTest,CalibrationControllerTest'`: a wrong number of sounds, a silent and a clipped recording are each refused with the reason, no analysis is tried, and *record again* / *use the default* / *stop* each do what the contract says.
8. **Activation and scope:** `-Dtest='ProfileActivatorTest,ProfileReuseTest'`: selecting a device with a profile changes the effective settings seen by the tuner and the practice readers (and not the tuner's own parameters), at start too, with no preparation window open; selecting another device restores the default.
9. **Forget:** `-Dtest='ProfileLibraryTest,InputSelectorPanelCalibrationSwtTest'`: the *Forget the calibration* button is enabled only with a profile, asks for a confirmation, and deletes only that device's profile.
10. **Dot states:** `-Dtest=ProfileDotTest`: grey with no input (buttons disabled), orange, green, red, the warning sentence for a changed noise reduction or gain, orange with the reason for a changed sample rate.
11. **Existing tests unchanged:** after each phase `git diff --name-status <phase start> | grep -v '^A'` lists no file under a `test` directory; the default suite is the baseline (937) plus the new tests, with the same failures as before (none).
