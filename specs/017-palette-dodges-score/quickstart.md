# Quickstart: validate spec 017

Prerequisites: JDK 21 (`export JAVA_HOME=$(/usr/libexec/java_home -v 21)`), build directory `desktop/build-scripts/tuxguitar-macosx-swt-cocoa`.

1. Unit tests of the decision:
   `mvn -o -q test -pl ../../TuxGuitar-practice -am -Dtest='PaletteDodgeTest' -Dsurefire.failIfNoSpecifiedTests=false`
   Expected: every scenario of US1–US3 and the edge cases of the contract pass.
2. Focus (graphical session):
   `mvn -o test -P swt-gui-tests -pl ../../TuxGuitar-practice -am -Dtest='PaletteMoveSwtTest' -Dsurefire.failIfNoSpecifiedTests=false`
   Expected: the focused control keeps the focus after a move.
3. Full gate: `mvn -o -e clean verify -P native-modules`, then the `swt-gui-tests` run; counts equal to the baseline plus the new tests.
4. Manual (gate, owner): follow `specs/test-sheets/F01-suivre-le-tempo.md` (and a Wait run on `tests/basic_test.tg`) with the palette in its default place; the palette jumps to another corner before the notes reach it, never hides the red frame (or, in Follow, the highlighted beat), keeps the shortcuts working, does not jump back and forth; drag it beside the window: it stays there.
