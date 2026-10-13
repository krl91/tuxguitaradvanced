# Quickstart: validating the Arcade flames (spec 009)

Automatic parts run in the normal build; the human parts are marked and are never reported as done before they are.

## Automatic

```sh
cd desktop/build-scripts/tuxguitar-macosx-swt-cocoa
export JAVA_HOME=$(/usr/libexec/java_home -v 21); export PATH="$JAVA_HOME/bin:$PATH"
mvn test -pl ../../TuxGuitar-practice -am -Dtest='Flame*Test' -Dsurefire.failIfNoSpecifiedTests=false        # the flames' tests
mvn test -pl ../../TuxGuitar-practice -am                                                                     # the whole module (reference scenes included)
mvn -e clean verify -P native-modules,swt-gui-tests                                                           # full build and native paint tests
mvn test -pl ../../TuxGuitar-audio-engine -am -pl ../../TuxGuitar-practice -Dgroups=benchmark -DexcludedGroups= -Dtest=ArcadeStressTest -Dsurefire.failIfNoSpecifiedTests=false   # cost (SC-006), as in spec 003
```

## Manual (the owner)

1. **G1, before any code:** open `mockups/A2-full.png`, `A2-discreet.png` and `A2-mouvement-coin-gauche.png`: do the shape, the colours and the difference between discreet and full suit you? (Static images; the motion is judged on the built application.)
2. **G2, on the built application:** play the demo score at a high level of success, then miss on purpose: the flames rise to full at 100 %, stay as embers at 50 % or less, and fall back over about 3 seconds.
3. Reading: with the flames at full level, the string names, the fret numbers, the verdict badge and the top texts stay easy to read; resize the window to several shapes.
4. The setting in the *Mode* tab: off draws nothing, discreet is lower and fainter than full, the choice holds until the application is closed.
5. Pause and resume: the flames hold their level and their motion while paused.
