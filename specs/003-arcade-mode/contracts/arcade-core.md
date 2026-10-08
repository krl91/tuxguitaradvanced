# Contract: Arcade core (increment A)

Package `app.tuxguitar.app.tools.custom.practice.arcade.core`. Pure Java, no widget, no clock of its own.

```java
enum ArcadeVerdict { WAITING, ON_TIME, EARLY, LATE, WRONG, INCOMPLETE, MISSED, NOT_JUDGED, AUTO_PASSED;
    static ArcadeVerdict of(FollowSession.EventResult result, PracticeEvent event); }
final class ArcadeScoring { static ArcadeScoring standard(); int points(ArcadeVerdict v); double multiplier(int streak); boolean extendsStreak(ArcadeVerdict v); boolean resetsStreak(ArcadeVerdict v); }
final class ArcadeRun { ArcadeRun(PracticeSequence sequence, ArcadeScoring scoring);
    ArcadeRun.View update(FollowSession.Snapshot snapshot);   // idempotent: the same snapshot gives the same view
    ArcadeResult result(FollowSession.Snapshot last); }
final class ArcadeResult { int score(); int maxScore(); int percent(); FollowSummary summary(); int bestStreak(); }
```

## Behaviour (each line is tested)

1. **Total mapping.** Every `FollowSession.Status` x timing maps to exactly one verdict; a chord with non-empty `missingPitches` and status WRONG_PITCH is INCOMPLETE; a note is WRONG. PENDING is WAITING.
2. **Never waits.** The core has no state that blocks the session: a wrong, missed or extra event changes only score and streak; the following events are judged on their own windows (guaranteed by `FollowSession`, re-asserted by a whole-run test with an injected error).
3. **Score** is a pure function of the ordered resolved events and the table. The same snapshots give the same score in any order of calls (idempotence), and two runs of the same recorded play give the same result (SC-004).
4. **Streak.** ON_TIME, EARLY, LATE extend; WRONG, INCOMPLETE, MISSED reset; NOT_JUDGED, AUTO_PASSED, WAITING and extra notes leave it unchanged. The best streak never decreases.
5. **Multiplier** applies to the points of an event using the streak *before* the event is added, so the first event of a run scores its base value.
6. **Techniques** never change the verdict, the points or the streak.
7. **Result** counts come from `FollowSummary` (every event counted once; the sum is the number of events); `percent` is `score / maxScore` where `maxScore` is the score of the same run with every judged event ON_TIME.
8. **Memory only.** No I/O of any kind; a test with a recording stub of `TGConfigManager` and the file system proves nothing is written.
9. **Honest claims.** The core never states string, fret or fingering as heard; positions come from the sequence and are labelled instructions.
