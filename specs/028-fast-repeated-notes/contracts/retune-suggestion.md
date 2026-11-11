# Contract: the suggestion to retune

FR-009, research R5.

- **Input**: each single note judged on its pitch in Wait, Follow or Arcade (accepted, or refused as out of tune), with its signed cents offset and the string the tab writes it on (`PracticeEvent.getStringNumber()`). Wrong notes (another pitch), misses and chords are not inputs. A guitar more than 50 cents off reads as the neighbouring note: it is caught earlier, at 20 cents.
- **Trigger**: over the last `tuning.drift-notes` inputs, the mean |offset| is at least `tuning.drift-threshold` (default 20 cents, user setting 10–35) and at least `tuning.drift-agreement` of them have the sign of the mean.
- **Text** (English, French, Spanish, in the plugin's `share/lang`):
  - one string: "Your guitar sounds {flat|sharp} on string {n} (as written in the tab): retune it when you can."
  - several: "Your guitar sounds {flat|sharp}: retune it when you can."
- **Where**: Wait and Follow: `PracticePalette` (`showFollowNotice` in Follow, the feedback line in Wait). Arcade: a text drawn on the scene by `ArcadeRenderer`, its words from a new **default** method of `SceneLabels` implemented by `ArcadeSceneLabels`; its place: [owner's answer to spec FR-009]. Shown until the next suggestion or the end of the run, never over the notes' lanes.
- **Never**: pausing the run, the playback or the judging; changing a verdict; appearing again within `tuning.drift-quiet` notes; appearing on a guitar in tune with notes missed or wrong (they are not inputs).
- **Setting**: the threshold is the first row of the new section *Tuning drift* (`Section.TUNING_DRIFT`) of the recognition settings panel, with its recommended value and tip in three languages (`NOTES` is full and pinned).
