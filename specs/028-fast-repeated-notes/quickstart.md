# Quickstart: validating spec 028

From `desktop/build-scripts/tuxguitar-macosx-swt-cocoa`, with JDK 21:

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 21); export PATH="$JAVA_HOME/bin:$PATH"
NATIVE=$PWD/../native-modules/tuxguitar-monitor-native/target
```

1. **The offset test on the owner's DI recording** (SC-001), owner's machine only:

   ```sh
   ../../../misc/owner-offset-check.sh /tmp/offset.md ../../../tests/tiktok/1
   ```

   Expected: the Wait row and every Follow row and Arcade row (levels 1 to 5), Java and native, from −70 to +70 ms at 100 / 100; the script exits 0. Then `misc/verify-offset-table.sh` on release notes holding that table exits 0.

2. **Repeats and held notes** (SC-004, SC-005), in every build:

   ```sh
   mvn -o -q test -pl ../../TuxGuitar-practice -am -Dtest='FollowSessionReattackTest,PracticeSessionReattackTest,ArcadeReattackTest,FollowSessionNearestTargetTest,FastRunFunctionalTest,TuningDriftTest,FollowSessionTuningDriftTest,PracticeSessionTuningDriftTest,TuningDriftTextTest,ArcadeRetuneOfferTest,FollowSessionAcceptBoostTest,ArcadeRetuneFlowTest' -Dsurefire.failIfNoSpecifiedTests=false
   ```

   Expected: two plucks 100 ms apart are two notes; a held note expected twice is one note; a scratch over a ringing note is not a second one; with `reattack.enabled` off, the verdicts are those of before; `FastRunFunctionalTest` keeps its floors and prints the `REPEATED` rates.

3. **No regression** (SC-002): the dataset benchmarks with both engines, compared with `benchmark/before-*`:

   ```sh
   mvn -o test -pl ../../TuxGuitar-practice -am -Dgroups=dataset,benchmark -DexcludedGroups= -Dtuxguitar.recognition.engine=java   -Dsurefire.failIfNoSpecifiedTests=false
   mvn -o test -pl ../../TuxGuitar-practice -am -Dgroups=dataset,benchmark -DexcludedGroups= -Dtuxguitar.recognition.engine=native -Dtuxguitar.native.library.dir=$NATIVE -Dsurefire.failIfNoSpecifiedTests=false
   ```

   Expected: no figure lower than its `before` value.

4. **The suggestion to retune** (SC-006): `TuningDriftReplayTest` replays a clean recording shifted 25 then 40 cents flat; the suggestion appears and the run goes on; on the dataset recordings within 10 cents on average it never appears. `ArcadeRetuneOfferTest` and `FollowSessionAcceptBoostTest`: Arcade's three choices, the boost of 10 % per choice, capped at 45 cents, reset at the next run.

5. **The owner's sheet** F18 (`specs/test-sheets/F18-notes-repetees.md`): the riff played live in Wait, Follow and Arcade, early and late, and a guitar detuned on purpose (in Arcade: the pause and its three choices).

After each Maven run: `git checkout -- specs/003-arcade-mode/benchmark/ specs/023-native-recognition-engine/benchmark/recognition-delay.md`.
