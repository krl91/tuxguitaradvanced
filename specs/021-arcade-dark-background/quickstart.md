# Quickstart: checking spec 021

Paths: `CB` = `desktop/build-scripts/tuxguitar-macosx-swt-cocoa`. JDK 21 (`export JAVA_HOME=$(/usr/libexec/java_home -v 21); export PATH="$JAVA_HOME/bin:$PATH"`). After each Maven build: `git checkout -- specs/003-arcade-mode/benchmark/`.

1. **The new test, red then green** (owner's Mac, dark appearance), from `CB`:
   `mvn -o test -P swt-gui-tests -pl ../../TuxGuitar-practice -am -Dtest=ArcadeDarkBackgroundSwtTest -Dsurefire.failIfNoSpecifiedTests=false`.
   - Before the fix: it fails, and the read pixel is the system background, not `b0e13`.
   - After the fix: every case passes.
2. **All the native tests locally**: the same command with `-Dtest='*SwtTest'`. Every one passes.
3. **The full gate**: `mvn -o -e clean verify -P native-modules`. It gives BUILD SUCCESS with the counts of spec 020 for the existing tests: 73, 1, 1, 10 (1 skipped), 2, 378, 24, 9, 1371 (12 skipped). The native tests are not part of the gate.
4. **On GitHub (light appearance)**: `gh workflow run practice-gui-tests.yml -R krl91/tuxguitaradvanced --ref master`. Expected: green, including `ArcadePixelAlignmentSwtTest` and `ArcadeCanvasSwtTest`. If it fails, the artifact `practice-gui-diagnostics` holds the pictures.
5. **The release**: `gh workflow run release.yml -R krl91/tuxguitaradvanced --ref master -f tag=v0.8.0-beta.4`. Expected: published as a pre-release with its 4 files, and OK [1]–[6] on the macOS package.
6. **By hand (owner)**: put the Mac in light appearance (System Settings > Appearance > Light), open the Arcade view of v0.8.0-beta.4. The scene is dark as in dark appearance, and the buttons under it and the other windows are light.
