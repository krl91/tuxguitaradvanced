# Contract: the calibration flow (spec 013)

The state machine is pure (like `PreRunCheck`): a total table, ignored pairs change nothing. Steps and events:

| State | Event | Next |
|---|---|---|
| (start) | press *Calibrate* on **any** device (calibrated, red or never calibrated) | `OFFER` (*Calibrate again* or, for a never calibrated device, *Calibrate* / *Use the default configuration*: the second saves the default as the profile, orange, and ends with nothing recorded) |
| `OFFER` | *Calibrate* / *Calibrate again* | `TUNING` |
| `TUNING` | answer yes | `ADVICE` |
| `TUNING` | answer no | `TUNER_OPEN` (the tuner opens; closing it returns to `TUNING`; never goes on by itself) |
| `ADVICE` | OK | `PIECE` |
| `PIECE` | OK | `LISTENING` |
| `LISTENING` | the expected number of sounds heard | `QUALITY` |
| `LISTENING` | timeout (nothing for 20 s, 3 min in all), a wrong number of sounds, or clipped / silent | `REFUSED` (the reason, with the numbers when the count is wrong; no analysis tried) |
| `REFUSED` | record again | `PIECE` |
| `REFUSED` | use the default configuration | the default saved as the profile (orange); `DONE` |
| `REFUSED` | stop | `CANCELLED` |
| `QUALITY` | yes, good enough | `ANALYSING` |
| `QUALITY` | no | `REFUSED` (reason: the player judged it not good enough; same three choices) |
| `ANALYSING` | search finished | `RESULT` |
| `RESULT` | result better than the default **and** total rate >= 60 % | saved as the profile; `DONE` |
| `RESULT` | **not better than the default, or total rate < 60 %** | `CHOICE` with the message "the default settings are the most optimal" and three choices |
| `CHOICE` | use the settings found anyway | saved (red if < 60 %); `DONE` |
| `CHOICE` | record again | `PIECE` |
| `CHOICE` | use the default | the default saved as the profile (orange); `DONE` |
| any | cancel, device lost | `CANCELLED`: nothing saved, the recording dropped, the previous profile intact |

**Invariants.** (1) Nothing is saved before `DONE` and only by a finished choice. (2) `TUNER_OPEN` never reaches `PIECE`. (3) The recording is dropped in `DONE` and `CANCELLED`. (4) A result **not better than the default** never becomes a profile without the player choosing it. (5) The texts of every step exist in English, French and Spanish. (6) A wrong number of sounds never reaches `ANALYSING`. (7) The window is modal during `ANALYSING` and closing it cancels. (8) Every `REFUSED` offers the three choices. (9) *Forget the calibration* is outside this flow: it asks for a confirmation and calls `ProfileLibrary.remove`.
