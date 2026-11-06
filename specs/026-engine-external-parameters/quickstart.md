# Quickstart: validating spec 026

From `desktop/build-scripts/tuxguitar-macosx-swt-cocoa`, JDK 21 (`export JAVA_HOME=$(/usr/libexec/java_home -v 21)`), then `git checkout -- specs/003-arcade-mode/benchmark/ specs/023-native-recognition-engine/benchmark/recognition-delay.md` after each build.

1. **Before** (task T001): `mvn -o -e clean verify -P native-modules`, then the dataset reports with each engine:
   `mvn -o test -pl ../../TuxGuitar-practice -am -Dtuxguitar.recognition.engine=$e -Dtuxguitar.native.library.dir=$LIB -Dtuxguitar.owner.chords=$OC3 -Dgroups=dataset -DexcludedGroups= -Dsurefire.failIfNoSpecifiedTests=false`
   Copy `target/dataset-benchmark/` and the benchmark reports to `SPEC/benchmark/before-<engine>/`.
2. **After each phase**: the same commands, then `diff -r` against `before-<engine>/` (dates and durations masked). Expected: no difference (SC-002).
3. **A value changed without a rebuild** (SC-001): each family's `…TakesEffectTest` (one value per family, each engine for an engine stage) is green. By hand: set `recognition.onset.flux-factor=3.0` in the configuration file, start a Wait run: fewer attacks are detected; restore it.
4. **An older native library** (FR-009): `NativeLayoutCheckTest` with a layout supplier that is missing or gives another length: the library is refused (`LIBRARY_MISSING`) and the backend falls back to the Java engine with the notice.
5. **The window** (SC-007): `mvn -o test -P swt-gui-tests -pl ../../TuxGuitar-practice -am`, Mac unlocked: every category fits 768 pixels; `RecognitionControlsTest` and `RecognitionSettingsPanelTest` green.
6. **Documentation** (SC-003, SC-004): `EngineDocumentationTest`, `EngineConstantInventoryTest`, `SessionConstantInventoryTest` and the crate's inventory test green.
7. **Old configuration file** (SC-005): `RecognitionSettingsMigrationTest` reads a configuration file written by v0.8.0-beta.7 (the latest release) and one written at the end of spec 024 with every stored value kept.
