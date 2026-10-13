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
4. Chords and single notes: one translucent panel per chord with the rectangles of its notes and its name (FR-074); the next note is readable at about 90 BPM (SC-009, three guitarists).
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
3. When it lands, its string is lit; inside a chord the open string shows as a line across the panel and is lit with the chord.

## Manual check of the rounded rectangles (FR-070 to FR-072, owner, 2026-10-11)

1. A fretted note arrives as a small solid rounded rectangle in the colour of its string, raised above its lane, with a thin thread to a footprint on the lane and its fret number under it.
2. On the neck the target is a rounded rectangle filling the cell of the fret, on the string; the number of that fret is highlighted under the neck.
3. A translucent blue band lights the approach zone; the lane of the active fret is slightly lighter, and both go the way of the lanes down to the foot of the neck, where the next note or chord lands (Q42).

## Manual check of the chord panel (FR-074, owner, 2026-10-11)

1. A chord arrives as a translucent panel across the lane with the rectangles of its notes at their frets and strings, and its name above the left corner.
2. Several chords in a row come as panels in perspective; the active one lands on the neck, its strings are lit and its rectangles carry the fret numbers.
3. An open string of a chord is a line across the panel; no bar or diagram cartouche remains.

## Manual check of the landing, the count-in and the window (Q37 to Q42, FR-079 to FR-082, owner, 2026-10-13)

1. Chords, open-string bars and single notes all land at the foot of the neck at the same instant; the rectangles of a chord land on their targets at the onset (Q37).
2. Start Arcade from the cursor, and again from the start, several times in a row: during the clicks of the count-in the notes and the first chord come from the far end, never already on the neck; the first event does not jump at the end of the count-in (Q38).
3. Pause during the count-in and in the music, then Resume: the picture holds while paused, the metronome counts in again and the notes come from the far end again; the buttons work as often as they are pressed (Q38, Q41).
4. The streak, the speed and *Next* are fully visible at several window sizes and shapes, and after resizing the window (Q39).
5. Let a run reach its end without playing, and stop another one before its end: the result window is shown both times; *From the start* replays from the first measure of the track and *From measure* honours the chosen measure (Q40).
6. While the game runs, the note button of the transport bar is not framed and no preparation window comes over the game (Q41).

