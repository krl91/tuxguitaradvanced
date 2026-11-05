# Spec 025: the lines of the owner's exception (research R6, task T023)

The owner's exception of 2026-11-05: in `AmpWindowSwtTest`, `AmpSafetySwtTest`, `AmpAutoStartWarningSwtTest` and `AmpSoundListSwtTest`, only the lines that reach the amp switch as `UIToggleButton`, `getSoundName()` and the Rename / Delete / Restore buttons may change, so that they reach the check box, the name prompt and the menu items; what they check stays the same.

All four files are under `desktop/TuxGuitar-practice/test/app/tuxguitar/app/tools/custom/practice/ui/`. Line numbers are those of the file before the change.

## Lines changed

### `AmpWindowSwtTest.java`, line 366

```java
// before
private static void userToggles(Object display, UIToggleButton button, boolean selected) throws Exception {
// after
private static void userToggles(Object display, Object button, boolean selected) throws Exception {
```

Why: the helper took the switch as a toggle button; it now takes the check box too (its body only reaches the native control). The cabinet, still a toggle button, goes through it unchanged.

### `AmpSafetySwtTest.java`, line 229

```java
// before
private static void userToggles(Object display, UIToggleButton button, boolean selected) throws Exception {
// after
private static void userToggles(Object display, Object button, boolean selected) throws Exception {
```

Why: the same helper, the same change.

### `AmpSoundListSwtTest.java`, line 135

```java
// before
this.amp.getSoundName().setText(name);
// after
this.amp.getNamePrompt().getName().setText(name);
```

Why: the name is typed in the name prompt instead of the Name field that left the window.

### `AmpSoundListSwtTest.java`, line 196

```java
// before
assertFalse(harness.amp.getSaveAs().isEnabled(), "no name typed");
// after
harness.press(harness.amp.getSaveAs()); assertFalse(harness.amp.getNamePrompt().getOk().isEnabled(), "no name typed");
```

Why: Save… opens the prompt; "no name typed" is checked on its OK, as it was on Save as.

### `AmpSoundListSwtTest.java`, line 198

```java
// before
assertFalse(harness.amp.getSaveAs().isEnabled(), "a ready-made name");
// after
assertFalse(harness.amp.getNamePrompt().getOk().isEnabled(), "a ready-made name");
```

Why: "a ready-made name" checked on the prompt's OK, as it was on Save as.

### `AmpSoundListSwtTest.java`, line 200

```java
// before
harness.press(harness.amp.getSaveAs());
// after
harness.press(harness.amp.getNamePrompt().getOk());
```

Why: saving is the prompt's OK, as it was Save as.

### `AmpSoundListSwtTest.java`, line 205

```java
// before
assertFalse(harness.amp.getRename().isEnabled(), "no new name typed");
// after
harness.press(harness.amp.getRename()); assertFalse(harness.amp.getNamePrompt().getOk().isEnabled(), "no new name typed");
```

Why: Rename… opens the prompt with the current name; "no new name typed" is checked on its OK, as it was on Rename.

### `AmpSoundListSwtTest.java`, line 207

```java
// before
harness.press(harness.amp.getRename());
// after
harness.press(harness.amp.getNamePrompt().getOk());
```

Why: renaming is the prompt's OK, as it was Rename.

## Lines added

`AmpSoundListSwtTest.java`: one method of the harness, right after `press(UIButton)`, so that the menu items Rename…, Delete and Restore are pressed as the buttons were (enabled checked, then a native selection). With it, the lines `harness.press(harness.amp.getRestore())` and `harness.press(harness.amp.getDelete())` stay as they were and reach the menu items.

```java
		void press(app.tuxguitar.ui.menu.UIMenuActionItem item) throws Exception {
			assertTrue(item.isEnabled(), "the menu item is enabled");
			notifySelection(this.display, item.getClass().getMethod("getControl").invoke(item));
		}
```

## Lines not changed

- `AmpAutoStartWarningSwtTest.java`: nothing changed. Its lines read `getSwitch().isSelected()`, which the check box answers as the toggle button did; it needed a clean compile only.
- `AmpSafetySwtTest.java` keeps its import of `UIToggleButton`, now unused, so that no other line changes.
- Every other line of the four files, and every other test, is unchanged; `AmpPedalRowsSwtTest` is unchanged.

## Not in the exception

- `UICheckBox` (the UI toolkit) declares `getToolTipText` and `setToolTipText`. The SWT, JavaFX and Qt check boxes already had both methods through their base classes; the interface did not declare them, and the check box needs its tooltip (every control has one, spec 022 FR-016). No test changed for it.
