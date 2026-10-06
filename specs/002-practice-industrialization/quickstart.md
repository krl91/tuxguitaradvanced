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

1. Choose tempo-following, tolerance Normal, 70% speed: count-in, play along; notes are marked on time/early/late/missed; make a deliberate mistake and confirm the following notes are still judged.
2. Raise to 100%; verify the summary. Adjust/calibrate the latency offset with a click test; repeat with the built-in mic.
3. Switch back to wait mode: MVP behaviour unchanged.

## Regression (every phase)

Full `mvn -e clean verify -P native-modules` green; existing practice/tuner/engine tests unchanged; MVP benchmark at or above its previous results.
