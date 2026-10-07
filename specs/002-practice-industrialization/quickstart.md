# Quickstart: validating Interactive Practice Industrialization

Nothing here has been run yet; each scenario is a check to execute when its phase is implemented. Report results exactly as run (Constitution XII).

## Prerequisites

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
cd desktop/build-scripts/tuxguitar-macosx-swt-cocoa
mvn -e clean verify -P native-modules          # build + all tests (macOS SWT variant, see the build notes in README.md)
```

Launch the generated `.app` (with the local JRE symlink workaround: link `Contents/MacOS/jre` to the JDK, see the build notes in README.md). Hardware: a guitar through an audio interface instrument input (recommended) and, separately, the built-in microphone.

## Phase A: launcher button (one click)

1. Open a song with a guitar track; in the toolbar, see the Practice button right of Play/Stop. Click once: the practice window opens for the selected track.
2. Tools → Practice opens the same single window; clicking the button again does not create a second one; its checked state follows the window.
3. Close the window with its own close button: the button unchecks. Open a song-less app: the button explains/disables.
4. Remove/disable the plugin jar: no button, no error (core registry empty).
5. Automated: toolbar default-layout regression test; registry unit tests (`desktop/TuxGuitar`'s test setup, if absent: pure-Java registry test in the build that already runs tests).

## Phase B: gain and noise

1. In the tuner and in practice, move the gain slider: meter and level state follow; reset returns to neutral.
2. Change the input or restart: gain is back to neutral.
3. Toggle noise reduction; run the benchmark with/without (command below); accept only if recognition is not worse.
4. With the suggestion switch on, click "suggest": a value is proposed and only applied on confirmation; switch off hides it.

## Phase C: fast recognition

```sh
cd desktop/build-scripts/tuxguitar-macosx-swt-cocoa
mvn test -pl ../../TuxGuitar-audio-engine -am -Dgroups=benchmark -DexcludedGroups= -Dsurefire.failIfNoSpecifiedTests=false
```
Compare the new report (latency p50/p95, fast passages, repeated notes) with the baseline captured first (task: S-C1). Manually play scale runs at rising speed in the app; watch for false crosses and missed notes.

## Phases D/F: techniques

Play each technique and its plain counterpart on the written notes; expect detected vs not detected, `uncertain` near the boundary, and `not verifiable` for techniques without a benchmark-backed detector. Check no technique result ever blocks progression.

## Phase E: tempo following

Needs the guitar (or the built-in microphone) and the application built by the checkpoint. Nothing here is ticked until it was done by a person.

1. Preparation window: choose **Follow the tempo**, tolerance **Normal**, **70 %**, track with the guitar. The start button reads "Follow the tempo of this track". Start: a count-in, the practised track is silent, the others play; notes are marked on time / early (with ms) / late / missed; a note not in the score is "extra".
2. A deliberate mistake (a wrong note, or a note skipped): it is marked, and the notes after it are still judged on their own time.
3. A chord and a written technique (a bend): the chord is judged as a chord; the technique line is apart and a bend not made does not stop anything.
4. Pause and resume: unplug the input or open the tuner; the run says it is paused, the notes that go by are "not judged", then Resume.
5. Raise to **100 %**: the summary at the end lists on time / early / late / wrong / missed / extra / not judged, and the techniques apart.
6. Calibration with the **built-in microphone**: "Calibrate…" announces 8 clicks and about 10 seconds; play one note on each click; Cancel stops the clicks at once. Without a calibration the window says "Reduced rhythmic precision" and still works; adjust the delay by hand to see the same. The tuner and the wait mode do not depend on it.
7. Play the score by hand during a run: the run stops with a message and the normal playback goes on.
8. Back to **Wait for the right note**: unchanged.

## Regression (every phase)

Full `mvn -e clean verify -P native-modules` green; existing practice/tuner/engine tests unchanged; MVP benchmark at or above its previous results.
