# Contract: the chord leniency setting

## In the *Mode* tab of the practice window

```
Mode   ( Wait | Follow | Arcade )          ← FollowPanel, unchanged
Chord check  [ Normal       ▾ ]            ← ChordLeniencyPanel (new)
  Accepts a chord when its notes are heard together over the strum,
  whatever the voicing. Recommended.       ← the chosen level's line (wraps)
```

- **The drop-down lists** *Strict*, *Normal*, *Lenient*. *Custom* is added at the end while the settings match no level, and is removed as soon as they match one.
- **Choosing *Custom* is not possible.** Choosing a named level writes its values (`ChordLeniency.apply`) into the store's default layer at once.
- **The line under the drop-down** says what the level accepts and what it risks. For *Custom*, it says that the expert chord settings were changed by hand and that choosing a level restores its values.
- **During a run:** the control stays enabled, the change applies from the next run, and the run never stops.
- **Texts:** keys `practice.chord-leniency`, `practice.chord-leniency.{strict,normal,lenient,custom}` and `….line`, in `TuxGuitar-practice/share/lang/messages{,_fr,_es}.properties`. The French level names are *Strict*, *Normal* and *Tolérant*.

## In the Recognition settings window (expert)

- The chord settings keep their rows. The rows of the level's values show the level's values.
- Their tips say: "Choosing a chord check level in the practice window sets this value; changing it here makes the level Custom." This is a text change of the existing `.tip` keys, in three languages.
- **Restore** in the chord section gives the defaults of its values, which are *Normal*'s. The level values (the rule, the threshold, and any value the sweep moves between levels) sit in the chords section (research R5). If the sweep moves a chord details value between levels, *Normal* needs that section restored too, and its line under the drop-down says so while the level shows *Custom*.

## The rule (both engines)

See [chord-profile-rule.md](chord-profile-rule.md).
- A level writes `CHORD_COMPLETENESS`, `CHORD_PROFILE_MATCH` and any profile value the sweep moves between levels (data model).
- No change to `ChordEvidence`, to the crate or to `NativeParameters`.
