# Contract: refusal reasons of a Follow run (FR-001, FR-002, SC-001)

Applies to `FollowSession` when an event closes. The verdict (status, timing, offset) is decided exactly as today **before** the reason is chosen; the reason never changes a verdict.

| Status at close | Condition (checked in this order) | Reason |
|---|---|---|
| `MATCHED` | — | `NONE` |
| `AUTO_PASSED` | — | `NONE` |
| `NOT_JUDGED` | — | `NOT_JUDGED` (cause as today) |
| `WRONG_PITCH` | — | `OTHER_PITCH`, detail = the decisive pitch heard most often in the window (ties: the first heard) |
| `MISSED` | an attack with the **right pitch** was decided outside the window, within the search span | `EARLY_OUTSIDE` / `LATE_OUTSIDE`, detail = its compensated offset in ms |
| `MISSED` | an attack lay inside the window, no decisive pitch followed | `UNCLEAR`, detail = best confidence ×100 |
| `MISSED` | otherwise | `NO_ATTACK` |

- **Search span**: from the target minus 1 s (but not before the previous event's target) to the target plus 1 s (but not after the next event's target).
- An attack counted for another event (matched there) is not used for this one.
- The summary counts each reason; the counts add up to the events not accepted on time.
- Texts: en, fr, es; a reason with a detail always shows it (FR-001, constitution XIV).

## Tests (written first)

Synthetic frames through the existing `FollowHarness`: one case per row; a run containing one of each gives one count of each in the summary; the reference recording replayed in Follow gives a reason for 100 % of its non-matched events (SC-001); with the reasons on, every status, timing and offset of `FollowBenchmarkTest`'s recorded run equals today's.
