# Quickstart: validate spec 019

**Prerequisites**: JDK 21; build directory `desktop/build-scripts/tuxguitar-macosx-swt-cocoa`; `export JAVA_HOME=$(/usr/libexec/java_home -v 21)`.

1. **Tuning identity** (FR-017, SC-010):
   ```sh
   mvn -o -q test -pl ../../TuxGuitar-practice -am -Dtest='FireParameterDefaultsTest,FireTuningTest,FireTuningIdentityTest' -Dsurefire.failIfNoSpecifiedTests=false
   ```
   The defaults equal the constants, and the reference scene is byte-identical.
2. **Spec 009 and 018 rules unchanged**:
   ```sh
   -Dtest='FlameSceneTest,FlameContrastTest,FlameParityTest,ArcadeReferenceSceneTest,FireFieldTest,FireColoursTest,FlameSparksTest,FlameCellsPaintTest,ReducedMotionTest'
   ```
   All green, none edited.
3. **Score fire logic** (FR-003, FR-005, FR-006, SC-002, SC-003, SC-005):
   ```sh
   -Dtest='ScoreStreakTest,ScoreFireSceneTest,ScoreFireLevelTest,FireExtremesTest'
   ```
   The streak follows the replayed Wait and Follow runs; nothing above 12 % meets a guard box, at the defaults and at every extreme.
4. **Store and Reduce motion** (FR-013, FR-019, SC-007, SC-008):
   ```sh
   -Dtest='FireTuningStoreTest,SystemReducedMotionTest'
   ```
   Round trip, clamping, never throws; GNOME, KDE, Windows and macOS answers parsed with a fake runner.
5. **Texts** (FR-018): `-Dtest='FireTextsTest'`. Every key exists in EN, FR and ES.
6. **Real toolkit**: `mvn -o test -P swt-gui-tests -pl ../../TuxGuitar-practice -am`. The overlay listener paints after the score; with Off, no listener; the Advanced tab builds and its preview stops when it is hidden.
7. **Full gate**: `mvn -o -e clean verify -P native-modules`, then restore `specs/003-arcade-mode/benchmark/`.
8. **Manual, the owner's gate** (sheet `specs/test-sheets/F11-feu-partition.md`):
   - relaunch the application;
   - open `tests/demo-arcade-morceau.tg`;
   - set *Score fire* to Full in the Mode tab;
   - in Wait, play right notes and watch the fire grow, then a wrong note and watch it fall;
   - in Follow, do the same and check that the scrolling stays smooth;
   - in the Advanced tab, move the opacity, the sparks and the length while watching the preview, use *Default values*, then restart and check that the values are kept;
   - with the system's Reduce motion on, check that the fire stands still and throws no spark.
