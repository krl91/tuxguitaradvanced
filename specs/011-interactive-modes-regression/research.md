# Research: functional non-regression tests of the interactive modes (spec 011)

Every claim cites a file that was opened on 2026-10-15. Paths are relative to `desktop/TuxGuitar-practice/` unless they start with another module name. `SRC` = `src/app/tuxguitar/app/tools/custom/practice`, `TST` = `test/app/tuxguitar/app/tools/custom/practice`.

## R1 - What `PracticeWindow` takes from the application (the seam, FR-014)

**Evidence** (`SRC/ui/PracticeWindow.java`, 1275 lines, grep of 2026-10-15):

| Access | Lines | Used for |
|---|---|---|
| `TGApplication.getInstance(ctx).getFactory()` | 173, 247, 847, 922 | the widget factory (the window, the Arcade window, the result window, the start window) |
| `TGWindow.getInstance(ctx).getWindow()` | 174, 248, 847, 922, 1249 | the main window: parent of every window, and the area for `WindowFit.refit` |
| `TuxGuitar.getInstance().getIconManager().getAppIcon()` | 253 | the window icon |
| `TuxGuitar.getInstance().getDocumentManager().getSong()` | 511 | the song of the preparation |
| `TuxGuitar.getInstance().getTablatureEditor().getTablature().getCaret()` | 512, 571 | the cursor track (`getTrack()`), the measure and the position (`getMeasure()`, `getPosition()`) that the prepared sequence starts from |
| `TGDialogUtil.openDialog(window, style)` | 326 | showing the preparation window centred and packed |
| `AudioEngine.getInstance(ctx)` | 245 | the engine; then only 8 methods: `getSelection`, `setSpectrumEnabled`, `setAttackCandidates`, `subscribe`, `unsubscribe`, `setExpectedPitches`, `acquire`, `release` |
| `new EngineFollowAdapter(ctx, engine)` | 246 | the `FollowStarter.Environment` (player, route, calibrations, assessment, capture clock): the existing seam of the runs |
| `TuxGuitar.getProperty(...)` | 34 sites | texts: **static**, through `TGMessagesManager`; see R2 |
| `TGSynchronizer.getInstance(ctx).executeLater(...)` | 13 sites | later on the user-interface thread: a **context singleton with `setController`** (`common/TuxGuitar-lib/.../util/TGSynchronizer.java`), so a test can give it its own controller without any change |
| `PracticeTunerContext`, `FollowSettings`, `PassageSettings`, `RecognitionSettingsStore` | many | context singletons: a fresh `new TGContext()` gives fresh ones (`PracticeTunerOpenerTest` already relies on it) |

The collaborators the window **receives**: `PracticePlugin` (`windowStateChanged`, `setCommandSink`).

**Other classes of `SRC/ui` that reach the application:** `PracticePalette` (factory, main window, `TGDialogUtil`, icon, ~39 texts), `AudioRecognitionOption` (toolbar item, icon, texts), `PracticeLauncherItem` (`getDocumentManager().getSong()`), `PracticePlugin` (action manager, plugin wiring). `FollowPanel`, `InputSelectorPanel`, `LevelPanel`, `GainControl`, `RecognitionSettingsPanel`, `ArcadeWindow` and `ArcadeResultWindow` take the factory and parent as **constructor arguments** already (they only use the static texts): nothing to change there. `PracticePlugin` and `PracticeLauncherItem` are the plugin's wiring to the application and are **out of the seam**: they are not on the journeys (the window is built by the plugin; the tests build it directly).

**Decision.** One package-private interface **`PracticeHost`** in `SRC/ui`, with the **seven** accesses that carry real behaviour: `factory()`, `mainWindow()`, `appIcon()`, `song()`, `caret()` (a small value: the track, the measure, the position), `openDialog(window, style)`, and `engine()` plus `environment(engine)` (the `FollowStarter.Environment`). Its default implementation `AppPracticeHost(TGContext)` does **exactly** what the lines above do today. `PracticeWindow(TGContext, PracticePlugin)` stays as the plugin calls it and delegates to a new package-private constructor `PracticeWindow(TGContext, PracticePlugin, PracticeHost)`. `PracticePalette` gets the same treatment **only if** a journey needs it (it is built by the window: its factory and parent are two of the seven accesses; the minimal change is to pass them in).

**Not in the seam, on purpose:** the 34 `TuxGuitar.getProperty` sites (R2) and `TGSynchronizer` (a controller can be set).

**Alternatives.** Driving the built application from outside (rejected by the owner, 2026-10-15). Making `PracticeWindow` take the whole `TuxGuitar` (rejected: it is the thing that cannot be built). A mocking library (rejected: none is a dependency of the module; the seam is seven methods).

## R2 - Texts stay static

**Evidence.** `TuxGuitar.getProperty` is `TGMessagesManager.getProperty` (`desktop/TuxGuitar/src/app/tuxguitar/app/TuxGuitar.java:365`); `TGMessagesManager` is a static manager with `setResources(TGResourceBundle)` (`common/TuxGuitar-lib/.../util/TGMessagesManager.java:36`) and returns the **key** when a text is missing (the loop at l.41 compares the answer with the key).

**Decision.** Texts are **not** part of the seam. A test that needs real words (the layout with the longest texts of the three languages, SC-005) loads the language files into `TGMessagesManager` through `setResources`; the others see the keys, which is enough to know which text was shown. To verify in the harness phase: that `setResources` accepts the practice `share/lang/messages*.properties` (the language test `FollowStringsTest` already reads them as plain properties).

## R3 - Driving the real window in a test: two toolkits, two jobs

**Evidence.** `TST/ui/SwtContractToolkit.java` is a headless toolkit built on dynamic proxies (the `invoke(Object proxy, Method method, Object[] args)` of l.54): text kept as the property `Text`, progress bar as `Value`, visibility as `Visible`, `add*Listener` ignored except selection; it is what `PreRunWindowTest` uses. The real SWT toolkit runs only in the profile `swt-gui-tests` (`pom.xml` l.141-172: `groups=swt-gui`, `-XstartOnFirstThread`) and opens windows on the maintainer's screen: `PracticeWindowSwtTest` (2 tests, about 1 s) does it for the start window.

**Decision.**
* **Default build:** the whole `PracticeWindow` on `SwtContractToolkit` (rules, order, what is shown or hidden, what is enabled, what started): fast. The contract toolkit is **extended where a journey needs it** (a `Selected` value for drop-downs and tabs, `moveToTop`, `isDisposed` after `dispose`, window bounds): the extension is test code only.
* **Tagged `swt-gui`:** the same journeys' **layout** checks on the real toolkit (every visible part has a size and lies inside the window, the lesson of the empty start window), in the three languages.
* The two are different jobs: the first is what the learner can **do**, the second is what the learner can **see**.

## R4 - A whole run in seconds, deterministically

**Evidence.** `TST/session/FakePlayerRun.java` and `FollowHarness.java` run a whole Follow or Arcade run on a fake player and a fake clock with no waiting (`FollowFunctionalTest`, `ArcadeRunFunctionalTest`, `FlameParityTest`); `TST/testsupport/FakeMidiPlayer.java` implements `FollowPlayer` on a `FakeTempoClock`; `FollowStarter.Environment` (`SRC/session/FollowStarter.java:23-37`) is what a run needs of the application (player, capture clock, route, calibrations, assessment); `TST/arcade/stress/StressRun.java` already builds a **real** engine chain on a seeded input (`StressInput` from the audio-engine test-jar) with a real `MidiFollowPlayer` on silent MIDI, in about 30 s per run (a tagged `swt-gui` test).

**Decision.** The seam's `environment(engine)` returns, in a test, a fake `FollowStarter.Environment` built on `FakeMidiPlayer`; the engine is either a **small fake with the 8 methods** of R1 (the default: frames are pushed by the test, as `FollowHarness` does) or the real `AudioEngine` fed by `StressInput` (tagged, for the 20-run stability and for one real-chain run per mode). A test gives `TGSynchronizer` a controller that **queues** tasks and runs them when the test says so (no thread, no waiting): the quick-start patience (2.5 s), the ten seconds before *Continue anyway* and the count-in are driven by the fake clock, never waited for (FR-011).

**To verify in the harness phase:** that `AudioEngine` has a constructor or a factory a test can use without a device (`AudioEngine.getInstance(ctx)` is a context singleton); if not, the 8-method fake is the only engine of the default build, and the real-chain tests stay in `StressRun`'s tagged style.

## R5 - The contract and the matrix (FR-001, FR-013, SC-001)

**Decision.** The contract is **one file, `contracts/journeys.md`** (committed with the spec): a table whose rows are journeys with an id (`SP-01`, `WR-02`, ...), the behaviour that must hold, the **test that protects it** (`Class#method`) or a **declared gap** with its reason. A test, `JourneyMatrixTest`, reads that file and checks: every row has a test or a gap; every `Class#method` named exists in `TST` (by a scan of the test sources, no classloading of every test); every journey id used in a `@Tag`/name comment of a new journey test is a row of the file. A journey added to a mode without a row is **not** found by this test (the code cannot say what a journey is): that limit is written in the file's header, and the owner's review of the matrix is the control (spec Edge Cases).

**Existing tests are mapped by name**, not by assumption: the first task of the matrix phase opens each and confirms it covers the row (a recorded list "confirmed / not confirmed").

## R6 - Checking the tests themselves (FR-010, SC-002, SC-003)

**Evidence.** The repository has **no mutation-testing tool** (no PIT or similar in any pom). The three bugs of spec 010 are fixed by identifiable lines: the watchdog guard (`QuickStart.showAfterPatience` called from `watchQuickStart`), the practice-track lifting in `PracticeTunerOpener.open`, and the refit in `PreRunWindow.refresh`.

**Decision.** A **recorded procedure**, `contracts/break-on-purpose.md`: for each P1 journey (and for the three bugs) the exact change to make (which line to revert or invert), the test that must fail, and the command; the change is made on a scratch copy of the file (`git stash` or `git checkout -- <file>` to restore), the failure is pasted in the report, and the file is restored. No framework is added for a list of about 15 changes. SC-003's 100 % of P1 and 80 % of P2 are counted from that list.

## R7 - Coverage (FR-016, SC-010)

**Evidence.** No coverage tool is configured (`grep jacoco` in the poms and in `~/.m2/repository/org` found nothing on 2026-10-15). The practice module compiles at Java release 11, the engine at 11, older modules at 9 (`CLAUDE.md`); the Maven 3 / JDK 21 build is `desktop/build-scripts/tuxguitar-macosx-swt-cocoa`.

**Decision.** **JaCoCo** (the `jacoco-maven-plugin`, a version that reads class files of release 9 to 21) in a **profile `coverage`** of `TuxGuitar-practice/pom.xml` (and the audio-engine if its packages are measured), **never active by default**; it writes `target/site/jacoco/` and a small CSV. The packages measured: `ui` (the window and its start path), `session` (Wait and Follow), `arcade.core`, `arcade.ui`, `arcade.view` (Arcade and the flames). The **before** figure is taken in phase P0 on the build as it is today (the tooling is the only change then); the **after** at the end; both are kept as text in `specs/011-.../benchmark/coverage-before.txt` and `coverage-after.txt` (figures only). The plugin has to be downloaded the first time: that is verified in P0 and, if it cannot be fetched, reported (the coverage figure is then a declared gap, not invented).

**Alternatives.** Other coverage tools (rejected: JaCoCo is the standard for Maven, nothing else is already present). Measuring in the default build (rejected by SC-007: it slows every build).

## R8 - Data

**Decision.** Per journey: **synthetic performances** (the `SongFixture` and `FakePlayerRun` ones: right, wrong, late, missing notes, chords) for every journey that is about behaviour; the **committed recordings** (`TST/replay`: the owner's `tests/1` and `tests/2` with `test-data/real-recording-1/events.csv`) for the recorded baselines of Wait and Follow; the **owner's takes** (`OwnerChordsBenchmarkTest`, `owner-chords-baseline.properties`) and the **datasets** (GuitarSet, IDMT) only through the tests that exist, which skip when the files are absent (FR-012). The kinds of event with no recorded-audio test (really played hammer-on, pull-off, palm mute, tapping, tremolo picking) and the chord weaknesses (43 of 78 synthetic chords with a string missing accepted, the D major takes) are **rows of the matrix declared as gaps** with their baseline figures (`specs/010-pre-run-check/benchmark/before-after.txt`), not tested again here.

## R9 - Default build against tagged suites (SC-007)

**Evidence.** The practice suite takes **9 min 14 s** today in the default build (`mvn test -pl ../../TuxGuitar-practice -am`, 2026-10-14, 911 tests); the longest are the data benchmarks (`FollowBenchmarkTest` 123 s, `WeakInputBenchmarkTest` 74 s, `TechniqueDatasetBenchmarkTest` 58 s, `ArcadeRenderBenchmarkTest` 58 s); a native test of one window takes about 1 s.

**Decision.**

| Suite | What | How it runs | Budget |
|---|---|---|---|
| default | every journey on the contract toolkit and the fake engine; the matrix test; the baselines that exist | `mvn test -pl ../../TuxGuitar-practice -am` | **+ under 2 minutes** (measured before and after) |
| `swt-gui` | the layout and window checks on the real toolkit; the real-window flow smoke | `mvn test -P swt-gui-tests ...` | on request and before a release |
| `stability` (new tag) | the whole new set 20 times in a row | `mvn test -Dgroups=stability -DexcludedGroups=` | on request and before a release |
| `coverage` (profile) | the JaCoCo measure | `mvn test -P coverage ...` | on request |

## R10 - Risks found by reading, to be settled in the first phases

1. **`PracticePlugin` has to be constructible in a test** (the window calls `windowStateChanged` and `setCommandSink` on it). If its construction touches the application, the seam grows by a tiny port for these two calls. Verified in P1 by constructing it.
2. **`TGDialogUtil.openDialog` is a static of the desktop module**, reading the parent's bounds to centre: the seam's `openDialog` hides it; the real toolkit still goes through it in the tagged tests.
3. **`PracticeWindow` holds many private fields and callbacks** (lines 100-165): tests drive it through its **public behaviour** (the buttons, the transport-bar entry points `open`, `openAndStart`, `openAndStartArcade`, the engine events, the player events), never by reflection into private state. Where a journey cannot be driven that way, it is reported as a gap or as a reason for another seam method, not forced by reflection.
4. **The first run of JaCoCo on the Java 11 and 9 modules** may need a configuration (the `argLine` of surefire is already used by `swt-gui-tests`): handled in P0.
