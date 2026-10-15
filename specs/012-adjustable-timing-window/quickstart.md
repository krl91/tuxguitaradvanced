# Quickstart: running and checking spec 012

From `desktop/build-scripts/tuxguitar-macosx-swt-cocoa`, `export JAVA_HOME=$(/usr/libexec/java_home -v 21)`.

1. **The formula and the equality at 75:** `mvn test -pl ../../TuxGuitar-practice -am -Dtest=TimingToleranceTest -Dsurefire.failIfNoSpecifiedTests=false`: the windows at W = 75 equal Normal's, W = 100 judges 90 ms late on time.
2. **The setting and the migration:** `-Dtest='RecognitionSettingsTest,FollowSettingsTest'`: an old saved `strict`/`lenient` loads as 50/125 and `normal` as the default 100, an absent or invalid value gives 100, an out-of-range value is brought to 40 or 200.
3. **The slider:** `-Dtest='FollowPanelTest,FollowStringsTest'` and, natively, `mvn test -P swt-gui-tests ... -Dtest=FollowPanelSwtTest`.
4. **Nothing else changed:** the whole default suite `mvn test -pl ../../TuxGuitar-practice -am` equal to `specs/011-interactive-modes-regression/benchmark/build-times.txt` plus the new tests; the recognition reports equal to `specs/010-pre-run-check/benchmark/after/`.
5. **By hand (owner):** in the Mode tab of the preparation window, choose Follow or Arcade: a slider "Timing window" shows 100 ms; move it, close and reopen the application: the value is back; the Configuration window shows the same value; replay a passage with the slider at 40 and at 200 and say whether the default feels less hard (SC-008).
