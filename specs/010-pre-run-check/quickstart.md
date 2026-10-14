# Quickstart: Pre-run check (spec 010)

Prerequisites: JDK 21, the macOS SWT build directory, the owner's recordings in `specs/004-attack-difference-recognition/benchmark/owner-chords` and `owner-chords-2` (not committed) and GuitarSet in `~/tuxguitar-datasets/guitarset` (not committed).

```sh
cd desktop/build-scripts/tuxguitar-macosx-swt-cocoa
export JAVA_HOME=$(/usr/libexec/java_home -v 21); export PATH="$JAVA_HOME/bin:$PATH"
mvn -e clean verify -P native-modules,swt-gui-tests          # the build and the SWT tests (the app must be closed)
mvn test -pl ../../TuxGuitar-practice -am                    # unit tests
# the meter on the data, and the recognition figures (the dataset tests also run in the default build and skip themselves without data; these flags run them alone)
mvn test -pl ../../TuxGuitar-practice -am -Dgroups=dataset -DexcludedGroups= -Dtest='StrumClipDatasetTest,OwnerChordsBenchmarkTest,GuitarSetChordBenchmarkTest' -Dsurefire.failIfNoSpecifiedTests=false
```

Manual steps (gate G, the owner, built application, Rocksmith adapter):

1. Set the input volume of the Rocksmith adapter to the high value that clipped (0.99), open a score, start *Wait*: the start window opens; answer *Yes, in tune*; strum a chord loudly: the window says the signal clips and tells to lower the gain; the mode does not begin; *Continue anyway* begins it for this start only; start again: the window asks again.
2. Lower the gain (about 0.6), strum: the window says the level is good and the mode begins after the strum.
3. Start *Follow* and *Arcade* from the preparation window, then *Arcade* from the transport-bar button: each opens the same start window.
4. Answer *No, open the tuner*: the tuner opens and the mode does not begin; close the tuner: the start window is back at the question.
5. Strum nothing for 10 seconds: the window keeps asking for a strum and never says the level is good.
6. Play again from the result window and use Restart: no new question (R4); the owner says if they want one.
