# Contract: the *Amp and effects* window and the sounds (PR `ui/AmpWindow`, PR `session/AmpSoundStore`)

## Opening and placement (FR-020)

- **Opening:** the window opens from the *Amp and effects…* button in the practice window, or from the amp button of the palette. It is one window, so a second click brings it to the front.
- **Placement:**
  - At its kept bounds, if they are on a screen.
  - Otherwise, during a run, on the side of the main window away from the framed note and the next note (boxes of `PaletteGuard`/`ScoreGuardBoxes`).
  - Otherwise, centred over the practice window.
- **Focus and closing:** opening it does not take the focus from the run (the palette's keys keep working). It closes when the practice window closes, and its bounds are kept.

## Layout

1. **Top block:**
   - *Amp sound* switch (off by default);
   - a sound list, with *Save as…*, *Rename…*, *Delete* (own sounds only) and *Restore* (changed ready-made sounds only);
   - output device;
   - *Guitar volume*;
   - *Score volume* (TuxGuitar's volume);
   - the delay line, e.g. "Delay: about 14 ms (estimate)".
2. **The chain, in order:**
   - **Before the amp:** one row per pedal: switch, name, sliders with values, up, down, remove.
   - **Amp:** character (clean, crunch, high gain), gain, bass, middle, treble, volume.
   - **Cabinet:** switch.
   - **After the amp:** rows as before.
   - An *Add pedal…* drop-down lists the kinds not yet in the chain and puts each one in its own section.
3. **A message line** for feedback, overload, a lost device, input clipping (with the gain advice) or the microphone warning.

## Behaviour

- **Live changes:** every change goes to `MonitorEngine.setChain` at once (FR-006).
  - It changes the current sound in memory, and the sound list shows "(changed)".
  - *Save as…* keeps it (under a new name, or under the name of the learner's own sound to replace it). When the window closes with changes, they are kept as the current sound, so nothing is lost silently.
- **The sound per score (FR-019):**
  - when the practice window opens on a score, and whenever the score in front of it changes, the current sound becomes `soundFor(ScoreKey)`;
  - when the learner picks a sound while a score is open, `remember(ScoreKey, name)` is called.
- **The microphone warning:** turning the sound on with the built-in microphone as input shows the warning (FR-012). The sound starts only after *Turn on anyway*.
- **Tooltips:** every control has a tooltip in English, French and Spanish: what it changes, its unit and its default (FR-016).

## `AmpSoundStore`

```java
final class AmpSoundStore {
    AmpSoundStore(ArcadeChoices.Persistence persistence);
    static AmpSoundStore of(TGContext context);              // module "tuxguitar-practice-amp"
    AmpSoundSettings settings();  void save(AmpSoundSettings s);
    List<Sound> sounds();                                    // ready-made first, then own, by name
    ChainSettings currentChain();  void saveCurrentChain(ChainSettings chain);   // "chain.current", the settings part (US1)
    Sound current();  void setCurrent(String name);                           // also sets the current chain
    void saveAs(String name, ChainSettings chain);  void rename(String from, String to);  void delete(String name);
    void restore(String readyMadeName);
    Sound soundFor(Optional<ScoreKey> key);                  // remembered, else last, else "Clean"
    void remember(Optional<ScoreKey> key, String name);      // also sets "last"
}
```

- **Names:** they are trimmed, from 1 to 40 characters, and unique regardless of case. A clash with a ready-made sound is refused with a message.
- **Deleted sounds:** deleting a sound that a score remembers leaves that score's key in place. `soundFor` then falls back to the last sound (US3/AC6).
