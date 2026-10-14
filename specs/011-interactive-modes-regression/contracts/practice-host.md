# Contract: the seam `PracticeHost` (spec 011, FR-014)

The only production change of this feature. Package-private, in `SRC/ui`. It isolates what `PracticeWindow` takes from the application, so that a test can build the real window. **Default implementation = exactly today's code.**

```java
interface PracticeHost {
    UIFactory factory();                              // was TGApplication.getInstance(ctx).getFactory()      (l.173, 247, 847, 922)
    UIWindow mainWindow();                            // was TGWindow.getInstance(ctx).getWindow()            (l.174, 248, 847, 922, 1249)
    UIImage appIcon();                                // was TuxGuitar.getInstance().getIconManager().getAppIcon()      (l.253)
    TGSong song();                                    // was TuxGuitar.getInstance().getDocumentManager().getSong()     (l.511)
    CaretView caret();                                // was ...getTablatureEditor().getTablature().getCaret()          (l.512, 571): track, measure, position
    void openDialog(UIWindow window, int style);      // was TGDialogUtil.openDialog(window, style)                     (l.326)
    AudioEngine engine();                             // was AudioEngine.getInstance(ctx)                               (l.245)
    FollowStarter.Environment environment(AudioEngine engine);   // was new EngineFollowAdapter(ctx, engine)             (l.246)

    // time (declared by the analysis of 2026-10-15: two threads of PracticeWindow really sleep, FR-011 forbids waiting in a test)
    void later(String name, long millis, Runnable task);   // was the daemon thread that sleeps then TGSynchronizer.executeLater(task): l.336-348 (quick-start patience 2.5 s) and l.981-992 (Arcade restart retry 50 ms)
    long nanoTime();                                  // was System::nanoTime given to PreRunWindow by PreRunStartGate (the ten seconds before *Continue anyway*)

    // the plugin (PracticePlugin.windowStateChanged runs TGMainToolBar.getInstance(ctx).updateItems() through the synchronizer: with a queued controller a test would run it)
    void windowStateChanged();                        // was plugin.windowStateChanged(context)                         (l.286, 313, 322, 872)
    void commandSink(java.util.function.Consumer<PracticeCommand> sink);   // was plugin.setCommandSink(...)          (l.721, 828, 1038, 1125, 1146)
}
```

**Size, declared:** **twelve** methods in three groups: the application's (eight: `factory`, `mainWindow`, `appIcon`, `song`, `caret`, `openDialog`, `engine`, `environment`), time (two: `later`, `nanoTime`) and the plugin (two: `windowStateChanged`, `commandSink`). The first version of this contract had eight and the plan said seven; the analysis of 2026-10-15 showed that the last four are needed (the quick-start patience and the restart retry are real sleeping threads; the plugin's `windowStateChanged` reaches the main toolbar).

`CaretView` is a three-field value (`TGTrack track`, `TGMeasure measure`, `long position`) so that a test does not need the desktop `Caret` class.

## Rules

1. `AppPracticeHost(TGContext)` is the **only** implementation in production and reproduces each line above one for one.
2. `PracticeWindow(TGContext, PracticePlugin)` keeps its signature and calls `this(context, plugin, new AppPracticeHost(context, plugin))`; the plugin's two construction sites are **not touched**. `later` is **exactly** today's code moved: a daemon thread named `name` that sleeps `millis` then runs `TGSynchronizer.getInstance(ctx).executeLater(task)`; in tests it records the task and the harness runs it when the fake clock says so.
3. The seam does not carry texts (static `TGMessagesManager`, R2) nor the synchronizer (a controller can be set, R1).
4. **Proof of no change of behaviour** (P1 task): the existing suites equal before and after (counts and recognition reports), the built application opened by the owner shows the same start path; plus a test that the default host answers what the application's classes answer.
5. The seam is listed in the report with the `git diff --stat` of the phase; it does not grow without a declared reason in the task that needs it.
6. The plugin calls (`windowStateChanged`, `commandSink`) are part of the seam from the start (analysis finding), so a test never needs a real `PracticePlugin` or the main toolbar. Any further growth is declared in the task that needs it and approved by the owner.

## Test double

`FakeHost` (test code): a factory from `SwtContractToolkit`, a main window of that toolkit, no icon, a song and a caret given by the journey, an `openDialog` that packs and opens, **a real device-less engine** built with `FakeAudioInput.newEngine(context, settings)` (public static, in the audio-engine test-jar: `desktop/TuxGuitar-audio-engine/src/test/java/app/tuxguitar/audio/engine/FakeAudioInput.java:29`), an environment built on `FakeMidiPlayer`/`FakeTempoClock` (`FollowStarter.Environment`, `SRC/session/FollowStarter.java:23-37`), a `later` that **records** the task with its delay and a `nanoTime` that is the fake clock, and recorders for `windowStateChanged` and `commandSink`.
