# Contract: the suggestion to retune

FR-009, research R5.

- **Input**: each single note judged on its pitch in Wait, Follow or Arcade (accepted, or refused as out of tune), with its signed cents offset and the string the tab writes it on (`PracticeEvent.getStringNumber()`). Wrong notes (another pitch), misses and chords are not inputs. A guitar more than 50 cents off reads as the neighbouring note: it is caught earlier, at 20 cents.
- **Trigger**: over the last `tuning.drift-notes` inputs, the mean |offset| is at least `tuning.drift-threshold` (default 20 cents, user setting 10–35) and at least `tuning.drift-agreement` of them have the sign of the mean.
- **Text** (English, French, Spanish, in the plugin's `share/lang`):
  - one string: "Your guitar sounds {flat|sharp} on string {n} (as written in the tab): retune it when you can."
  - several: "Your guitar sounds {flat|sharp}: retune it when you can."
- **Where, Wait and Follow**: `PracticePalette` (`showFollowNotice` in Follow, the feedback line in Wait). Nothing pauses.
- **Where, Arcade** (spec Clarifications, 2026-11-11): the run pauses (`ArcadeController.pause()`, as the pause button does) and a modal window over the Arcade window shows the text and three buttons:
  - **Yes**: the application's tuner opens (`PracticeTunerOpener.open()`, as `ui/PreRunStartGate` opens it); when it closes (`setClosedListener`), the run resumes where it paused (`ArcadeController.resume()`, with its count-in). The tuner refuses to open while a practice track is set (`tuner-next` `TunerWindow.open`, both listen to the same input): `PracticeTunerOpener.open()` lifts the track and gives it back on close, and `FollowEvents` pauses the session with `TUNER_OPEN` and resolves it on close. The run resumes only after both, so no pause reason is left; the track lifted for a moment must not end the run nor show the track mismatch (`PracticeWindow.updateMismatch`). The tuner comes in front of the Arcade window (`toFront()`). If the tuner cannot open (`available()` false, or it did not open), Yes acts as No and the window says so;
  - **No**: the run resumes the same way;
  - **More tolerant (+N %)**, N = `tuning.arcade-boost` (default 10, shown as set): the run's pitch acceptance (`NOTE_ACCEPT` as the run uses it) rises by `tuning.arcade-boost` (10 %) of its value at the start of the run, never above 45 cents, for this run only (several choices add up: 35 → 38.5 → 42 → 45); then the run resumes the same way. The button says what it risks: "a note close to the right one may be accepted" (constitution XIV; spec 014 measured that 40 cents loses a right note on its reference).
  - Closing the window by its frame is **No**. If the run ends while the window is open (Stop, the Arcade window closed), the window closes with no effect.
- **Arcade end**: the boost is never saved; the next run starts from the usual setting.
- **Never**: pausing the run, the playback or the judging in Wait and Follow; changing a verdict already given; appearing again within `tuning.drift-quiet` notes; appearing on a guitar in tune with notes missed or wrong (they are not inputs).
- **Setting**: the threshold is the first row of the new section *Tuning drift* (`Section.TUNING_DRIFT`) of the recognition settings panel, with its recommended value and tip in three languages (`NOTES` is full and pinned).
