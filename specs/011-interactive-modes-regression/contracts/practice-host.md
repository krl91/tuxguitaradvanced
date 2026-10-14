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
}
```

`CaretView` is a three-field value (`TGTrack track`, `TGMeasure measure`, `long position`) so that a test does not need the desktop `Caret` class.

## Rules

1. `AppPracticeHost(TGContext)` is the **only** implementation in production and reproduces each line above one for one.
2. `PracticeWindow(TGContext, PracticePlugin)` keeps its signature and calls `this(context, plugin, new AppPracticeHost(context))`; the plugin's two construction sites are **not touched**.
3. The seam does not carry texts (static `TGMessagesManager`, R2) nor the synchronizer (a controller can be set, R1).
4. **Proof of no change of behaviour** (P1 task): the existing suites equal before and after (counts and recognition reports), the built application opened by the owner shows the same start path; plus a test that the default host answers what the application's classes answer.
5. The seam is listed in the report with the `git diff --stat` of the phase; it does not grow without a declared reason in the task that needs it.
6. If `PracticePlugin` cannot be constructed in a test (R10.1), the plugin calls the window makes on it (`windowStateChanged`, `setCommandSink`) become a ninth, declared method group of the seam (`PluginPort`), nothing more.

## Test double

`FakeHost` (test code): a factory from `SwtContractToolkit`, a main window of that toolkit, no icon, a song and a caret given by the journey, an `openDialog` that packs and opens, an engine (`FakeEngine`), and an environment built on `FakeMidiPlayer`/`FakeTempoClock` (`FollowStarter.Environment`, `SRC/session/FollowStarter.java:23-37`).
