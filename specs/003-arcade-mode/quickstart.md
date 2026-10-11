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

### Visual lane gate before corpus approval

Before reviewing the 32 real-score screenshots, inspect the minimal lane fixtures required by T066. For every visible fret, its approach lane must meet the midpoint of the fret cell's upper edge. Notes on different strings but the same fret must travel on the same lane while keeping different string colours. At onset the moving marker must be centred on that endpoint; no approach line or connector may continue into or across the physical neck. Reject T044 if any note floats beside a lane, lands on a fret wire, uses a string-specific trajectory or draws a diagonal through the neck.

## Manual check of the palette and the lit string (FR-067, FR-068, owner, 2026-10-11)

1. Start an Arcade run on a six-string track. The strings, from the top to the bottom of the neck, are red, yellow, light blue, orange, green, violet; violet is the highest string.
2. While no event is active the strings are dim. When a note is the active event, its string is bright with a glow and the other strings stay dim; for a chord, every written string is bright together. The light goes out at the judgement.
3. In the monochrome look and at `PERFORMANCE`, the targeted string is still visibly brighter or thicker.

## Manual check of the open string (FR-069, owner, 2026-10-11)

1. Use a score with open-string notes (fret 0) and start Arcade.
2. An open note arrives as a thin horizontal bar in the colour of its string, as wide as the lanes, with the word *open*; there is no disk and no fret number.
3. When it lands, its string is lit; inside a chord the open string shows as `O` in the diagram and is lit with the chord.
