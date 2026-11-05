# Quickstart: validating spec 025

From `desktop/build-scripts/tuxguitar-macosx-swt-cocoa`, with JDK 21 (`export JAVA_HOME=$(/usr/libexec/java_home -v 21)`).

## 1. Unit tests

```sh
mvn -o test -pl ../../TuxGuitar-practice,../../TuxGuitar-audio-engine -am \
  -Dtest='LevelRecheckTest,QuickStartBlockerTest,PracticeLayoutTest,LevelCheckNotTwiceTest,GreyedStartExplainedTest,QuickStartTest,WindowFitTest' \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: green. `GreyedStartExplainedTest`, `QuickStartTest` and `WindowFitTest` are committed and unchanged.

## 2. Window tests (SWT, opens windows)

```sh
mvn -o test -P swt-gui-tests -pl ../../TuxGuitar-practice -am -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest='PracticeWindowFitsSwtTest,AmpWindowTabsSwtTest,AmpNamePromptSwtTest,AmpWindowSwtTest,AmpSafetySwtTest,AmpAutoStartWarningSwtTest,AmpSoundListSwtTest,AmpPedalRowsSwtTest'
```

Expected: green; the four tests of the owner's exception changed only in their R6 lines (`git diff` shows nothing else).

## 3. Full gate

`mvn -e clean verify -P native-modules`, then the swt-gui profile in full; then `git checkout -- specs/003-arcade-mode/benchmark/`.

## 4. The owner (sheet F14)

On the built app, guitar plugged in:
1. Open the practice window, wait 15 s without playing, then play: the start button becomes available within 5 s; the line under it said what to do meanwhile.
2. Unplug the interface: the line says so with **Check again**; plug it back, press it, play.
3. On the screen shrunk to 768 px (or the window made short): every tab scrolls; the start button is always visible.
4. Open *Amp and effects*: it fits; tick *Hear my guitar through the amp*; find *Speaker simulation*; save a sound with **Save…**, rename and delete it with **⋯**.
