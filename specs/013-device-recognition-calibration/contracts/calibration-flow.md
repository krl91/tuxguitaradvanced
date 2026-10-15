# Contract: the calibration flow (spec 013)

The state machine is pure (like `PreRunCheck`): a total table, ignored pairs change nothing. Steps and events:

| State | Event | Next |
|---|---|---|
| (start) | press *Calibrate* on a device **with a profile or a red dot** | `OFFER` (*Calibrate again* / *Use the default configuration*: the second saves the default as the profile, orange, and ends with nothing recorded) |
| (start) | press *Calibrate* on an uncalibrated device | `TUNING` |
| `TUNING` | answer yes | `ADVICE` |
| `TUNING` | answer no | `TUNER_OPEN` (the tuner opens; closing it returns to `TUNING`; never goes on by itself) |
| `ADVICE` | OK | `PIECE` |
| `PIECE` | OK | `LISTENING` |
| `LISTENING` | the expected number of sounds heard | `QUALITY` |
| `LISTENING` | timeout (nothing for 20 s, 3 min in all) or too noisy / clipped / silent | `QUALITY` refused with the reason, then back to `PIECE` |
| `QUALITY` | yes, good enough | `ANALYSING` |
| `QUALITY` | no | `PIECE` (nothing kept) |
| `ANALYSING` | search finished | `RESULT` |
| `RESULT` | result better than the default **and** total rate >= 60 % | saved as the profile; `DONE` |
| `RESULT` | **not better than the default, or total rate < 60 %** | `CHOICE` with the message "the default settings are the most optimal" and three choices |
| `CHOICE` | use the settings found anyway | saved (red if < 60 %); `DONE` |
| `CHOICE` | record again | `PIECE` |
| `CHOICE` | use the default | the default saved as the profile (orange); `DONE` |
| any | cancel, device lost | `CANCELLED`: nothing saved, the recording dropped, the previous profile intact |

**Invariants.** (1) Nothing is saved before `DONE` and only by a finished choice. (2) `TUNER_OPEN` never reaches `PIECE`. (3) The recording is dropped in `DONE` and `CANCELLED`. (4) A result **not better than the default** never becomes a profile without the player choosing it. (5) The texts of every step exist in English, French and Spanish.
