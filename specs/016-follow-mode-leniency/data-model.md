# Data model: Follow the tempo, made playable (spec 016)

## RefusalReason (new, enum)

Why an event of a Follow run was not accepted on time (FR-001). Kept on the event result; `NONE` for an event accepted on time, early or late inside its window, and for auto-passed events.

| Value | Detail kept | Shown as (en) |
|---|---|---|
| `NONE` | — | — |
| `NO_ATTACK` | — | "No attack heard" |
| `UNCLEAR` | best confidence seen (×100) | "A sound was heard but not clearly enough to tell the note" |
| `OTHER_PITCH` | heard pitch (MIDI) | "Heard {note} instead of {expected}" |
| `EARLY_OUTSIDE` | offset (ms, negative) | "Right note, {n} ms too early" |
| `LATE_OUTSIDE` | offset (ms) | "Right note, {n} ms too late" |
| `NOT_JUDGED` | cause (pause, clocks, input) | as today |

Derivation rules: [contracts/refusal-reasons.md](contracts/refusal-reasons.md). Deterministic: the same frames give the same reason (FR-009).

## EventResult (existing, `FollowSession.EventResult`, extended additively)

+ `reason(): RefusalReason`, + `reasonDetail(): long` (MIDI or ms, by reason). Every existing accessor and value unchanged.

## FollowSummary (existing, extended)

+ count per `RefusalReason` (FR-002), + pitch position and time to play used (FR-007).

## PlayTimeWindow (new, implements WindowPolicy)

| Field | Value |
|---|---|
| `playTimeMillis` | 100–1200, default 600 (FR-011, since gate G2; was 100–600, 300) |
| `advanceMillis` | 50 (constant) |
| `gapToNextMillis` | 20 (constant) |
| `onTimeShare` | 0.5 of the time to play (provisional, research R3) |

Produces the existing `TimingTolerance.Window` (target, onTimeFrom, onTimeTo, outerFrom, outerTo). Rules: [contracts/play-time-window.md](contracts/play-time-window.md).

## FollowLeniencyPosition (new, enum)

`STRICT` (today's pitch recognition settings) · `PERMISSIVE` (default) · `VERY_PERMISSIVE`. Unknown or missing saved value → `PERMISSIVE`. Applied one way by `FollowLeniency` (never stricter than the effective settings). Rows and values: [contracts/follow-leniency.md](contracts/follow-leniency.md).

## FollowSettings (existing, extended)

+ `playTimeMillis` (stored as `RecognitionParameters.FOLLOW_PLAY_TIME` in the default layer; out of range → brought to 100 or 1200; absent → 600; the learner's choice, never in a device profile, like `TEMPO_WINDOW`).
+ `leniencyPosition` (stored like Arcade's leniency, `ArcadeChoices.LENIENCY_KEY = "arcade.leniency"`, under its own key `practice.follow.leniency`).
+ `recordRun`, `recordAudio`: booleans, memory only, false at each launch.

## FollowRunRecord (new, value + writer)

The file of one run: [contracts/run-record.md](contracts/run-record.md). Written at the end of a run only when `recordRun` is set; the WAV beside it only when `recordAudio` is set too.

## Declined calibration routes (new, launch-scoped)

A set of `AudioRoute` (existing value: input, output, format) the learner chose *Go on without* for; memory only (FR-004, clarification Q of 2026-10-29). Rules: [contracts/calibration-offer.md](contracts/calibration-offer.md).

## State: PreRunCheck (existing, one state added)

`TUNING → (TUNER_OPEN ↔ TUNING) → STRUM ↔ LEVEL_BAD → ` **`RHYTHM`** ` → READY`, where `RHYTHM` is entered only for Follow and Arcade on an uncalibrated, not-declined route; from `RHYTHM`: calibrate (stays in `RHYTHM` while the clicks run; success → READY; cancel or failure → `RHYTHM` again) or go on without (→ READY).
