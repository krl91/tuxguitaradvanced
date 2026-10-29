# Contract: the record of a Follow run (FR-003, SC-002, SC-003)

Folder `~/tuxguitar-follow-takes/`, outside the project, created when needed. Name `follow-<yyyy-MM-dd-HHmmss>.csv`, and `.wav` with the same stem when the audio box is ticked too. Written at the end of a run (complete, stopped by the learner, or ended by a manual play); **not** written when the run was cancelled before its count-in ended. A failed write is told once in the palette and changes nothing else.

CSV, UTF-8, comma separated, dot decimals:

```text
# follow-run-record 1
# score=<file name>,track=<n>,percent=<p>,play_time_ms=<P>,pitch_position=<STRICT|PERMISSIVE|VERY_PERMISSIVE>
# route=<input>|<output>|<format>,calibration=<NONE|CALIBRATED|MANUAL>,offset_ms=<o>
# audio=<file name or none>,sample_rate=<hz>,first_sample_ms=<ms of the first recorded sample on the timeline>,audio_truncated=<true|false>
index,kind,expected,target_ms,status,timing,offset_ms,reason,detail,attack_ms,heard,confidence,level_dbfs
```

- One line per event, in order; empty fields for what does not apply.
- `first_sample_ms` aligns the WAV on the timeline so the replay test can lay the score on it exactly (no least-squares fit needed).
- The WAV holds the hops the analysis saw (after the front end), mono, at the run's sample rate, at most 10 minutes (`audio_truncated=true` beyond).

Replay (test, tag `dataset`, skipped without a record): reads the newest record (or `-Dfollow.take=<file>`), lays the score with `percent` and `first_sample_ms`, replays the WAV with `FakePlayerRun` at each pitch position and time to play asked, and prints the accepted share of the events that the record's own run judged right-pitch within the time to play (SC-003) and the reasons (SC-002).
