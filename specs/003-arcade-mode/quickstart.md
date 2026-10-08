# Quickstart: validating Arcade

Automatic parts run in the normal build; human and hardware parts are marked and are never reported as done before they are.

## Automatic (each increment)

```sh
cd desktop/build-scripts/tuxguitar-macosx-swt-cocoa
export JAVA_HOME=$(/usr/libexec/java_home -v 21); export PATH="$JAVA_HOME/bin:$PATH"
mvn test -pl ../../TuxGuitar-practice -am -Dtest='Arcade*Test' -Dsurefire.failIfNoSpecifiedTests=false   # the increment's tests
mvn test -pl ../../TuxGuitar-practice -am                                                                  # the whole practice module
mvn test -P swt-gui-tests -pl ../../TuxGuitar-practice -am -Dsurefire.failIfNoSpecifiedTests=false        # native SWT, graphical session
mvn -e clean verify -P native-modules                                                                      # full build
```

## Manual (the owner)

1. A multi-track song: choose a guitar track, start Arcade at 70 %: count-in, the other tracks play, the chosen one is silent; play along; mistakes do not stop the song.
2. Leave Arcade: the song's mute and solo are exactly as before (look at the track controls).
3. A bass track (if supported after T003) and a seven-string guitar if available: the neck has the right strings.
4. Chords and single notes: one bar with one cartouche per chord; the next note is readable at about 90 BPM (SC-009, three guitarists).
5. Pause and resume: the backing and the judging stop and restart together.
6. The result at the end: score, counts, best streak; nothing during play; nothing kept after closing.
7. Monitoring (only if the spike allowed it): the physical loopback of `contracts/monitoring-latency-protocol.md`.
