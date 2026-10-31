# Contract: the Advanced tab and the Mode tab choice (spec 019)

## Mode tab

- `FollowPanel` shows a *Score fire* drop-down (`practice.fire.score.setting`), with *Off*, *Discreet* and *Full*, beside the other Wait and Follow settings.
- Its tooltip says that the fire is decoration only and off by default.
- The choice is saved at once through `FireTuningStore.setScoreSetting` and applies at the next run.

## Advanced tab (`practice.tab.advanced`, after *Passages*)

- **Groups**: two, *Arcade fire* (`practice.fire.group.arcade`) and *Score fire* (`practice.fire.group.score`). Each holds, from the top:
  1. the preview (about 360×120);
  2. *Sparks* (`practice.fire.preview.sparks`) and the growth slider (`practice.fire.preview.growth`: the streak for the score, the share of the maximum for Arcade);
  3. *Default values* (`practice.fire.defaults`, with a tooltip);
  4. one row per catalogue parameter of the group, by section (Look, Flames, Motion, Embers, Growth, Sparks, Colours).
- **Rows**:
  - number and integer rows are a label, a horizontal scale and a value with its unit;
  - colour rows are a label and a swatch button opening the colour chooser;
  - the label and the control both carry the tooltip `practice.fire.<key>.tip`, followed by the unit and "default: X".
- **Changing a value**:
  - the group's tuning is rebuilt (clamped) and saved;
  - the preview shows it within 0.1 s;
  - the running game is not changed; the value is used at the next run of that mode.
- ***Default values***: every row of the group returns to its default, and the stored keys are removed.
- **Preview animation**:
  - it animates only while the Advanced tab is selected and the window is shown;
  - with Reduce motion, it is drawn still and without sparks;
  - it uses the same scene code as the real fire.
- **Scrolling**: the tab scrolls when the window is small; it adds nothing to the other tabs.

## Texts

Every new key exists in EN, FR and ES (`desktop/TuxGuitar-practice/share/lang/messages*.properties`). This is checked by a test over the catalogue keys and the fixed keys above.
