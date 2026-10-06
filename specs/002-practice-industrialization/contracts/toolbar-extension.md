# Contract: main toolbar extension point (core, Phase A)

Class (new, `desktop/TuxGuitar/src/app/tuxguitar/app/view/toolbar/main/TGMainToolBarItemRegistry.java`), one instance per `TGContext` via `TGSingletonUtil` like the other managers.

```java
public final class TGMainToolBarItemRegistry {
    /** Adds (or replaces, by name) an item definition; callable from a plugin's connect(). */
    void register(TGMainToolBarPluginItem item);
    /** Removes it (plugin disconnect). No-op if unknown. */
    void unregister(String name);
    List<TGMainToolBarPluginItem> items();
}

public final class TGMainToolBarPluginItem {
    String name;            // unique, e.g. "transport.practice"; also the key shown in the toolbar settings dialog
    String group;           // settings-dialog group, e.g. "transport"
    String textKey;         // tooltip/label property key
    String actionName;      // action id, e.g. "practice.open"
    String iconFileName;    // resolved like core icons: skins/<skin>/<name>
    boolean checkable;      // true => CHECKABLE_ITEM (checked = feature active)
    TGMainToolBarItemUpdater updater; // enabled/checked/icon/text, same interface as core items
    String defaultAfter;    // optional anchor in the default centre area, e.g. "transport.start"
}
```

## Behaviour (testable)

1. Registered items appear in `TGMainToolBarConfigMap` (so the toolbar-settings dialog lists them) in `group`.
2. The **default** layout gets the item inserted right after `defaultAfter` (centre area) when registered; a user's saved layout is never modified.
3. `unregister` removes the item from the map; a saved layout that still names it renders without it (no exception).
4. The item's click executes `actionName` through the existing action mechanism (`TGMainToolBarSection.createActionProcessor`); no new action type.
5. With no plugin registered, the toolbar is identical to today (regression test on the default layout).
6. The registry only holds data; it has no reference to any fork class (core stays unaware of the practice plugin).

## Practice plugin side

`PracticePlugin.connect` registers `transport.practice` (checkable, `practice.open`, updater = `LauncherState`, `defaultAfter = "transport.start"`); `disconnect` unregisters. Updater rules: checked iff the practice window is open; enabled iff a song is open (a missing input does not disable it); tooltip key `practice.toolbar.open` (and `…unavailable` with the reason). Clicking while open toggles per `PracticeWindow` (single instance, FR-005). Strings: `practice.toolbar.*` in the plugin's `share/lang/` (en, fr).
