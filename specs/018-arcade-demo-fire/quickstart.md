# Quickstart: validate spec 018

Prerequisites: JDK 21, build directory `desktop/build-scripts/tuxguitar-macosx-swt-cocoa`.

1. New tests: `mvn -o -q test -pl ../../TuxGuitar-practice -am -Dtest='FireFieldTest,FireColoursTest,FlameSparksTest' -Dsurefire.failIfNoSpecifiedTests=false` — the rules of the contract hold.
2. Spec 009's rules: `-Dtest='FlameSceneTest,FlameContrastTest,FlameParityTest,ArcadeReferenceSceneTest,ArcadeSceneQualityTest,ArcadeFlamesSettingTest'` — green (with the exception edits of research R8 only).
3. Full gate: `mvn -o -e clean verify -P native-modules`, then the `swt-gui-tests` run.
4. Manual (gate, owner): relaunch the application, start an Arcade run on `tests/demo-arcade-morceau.tg` with the flames on *full*, then *discreet*, then *off*; check the big-pixel fire, the game visible through it, no black band, sparks on right notes, smooth picture (see `specs/test-sheets/F06-flammes-arcade.md`).
