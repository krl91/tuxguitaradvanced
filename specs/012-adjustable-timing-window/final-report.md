# Final report: an adjustable timing window (spec 012), 2026-10-15

## What was built
A slider in milliseconds (40 to 200 each side at 120 beats a minute, default 100) replaces the three presets; it is one saved setting (`recognition.tempo.window`) also shown in the Configuration window; an older saved choice is converted (strict 50, lenient 125, normal the new default 100); the window keeps the shape of the old Normal (a fraction of the beat between a floor and a ceiling) so that 75 ms is exactly the old Normal, to the nanosecond; texts and help pages in English, French and Spanish; documentation.

## What ran
* Default practice suite: **937 tests, 0 failures, 4 skipped** (the same four), 9 min 31 (baseline 923); audio-engine 295 (291 before); native 28 green; `clean verify -P native-modules,swt-gui-tests` BUILD SUCCESS.
* Recognition reports: 8 of 9 files identical to spec 010; the ninth differs only on the line that prints the settings (`tempo.tolerance=normal` became `tempo.window=100`). The owner's takes: 26 of 42, 13 wrong, as before.
* Tests failed first (compile errors) for the formula, the setting, the panel and the texts; the parity test was seen failing when a ratio of the formula was changed in a scratch copy.

## What went wrong on the way (kept visible)
* A first green run was misleading: an incremental compile had not recompiled `FollowPanel` after a method was removed; a `clean` showed the error. From then on the full suite is run after a clean compile.
* The default suite found four things: a missing row in the help page of the settings, my own parity test (the fake player adds 65 ms), the demo test, and the effect below.
* **A wider window has a measured cost**: on the demo score played by a synthetic guitar, a window of 85 ms or more makes one note (event 10 of 30) be judged as the note still ringing (one wrong pitch), where 75 ms gives none; the technique recording gains one (56 of 60 matched in both cases). The demo test is pinned at 75 ms with a comment, and the cost is written in the documentation and in `benchmark/after.txt`. It is not fixed (recognition is out of scope).

## The owner's trial (T020), 2026-10-15
1. The note button starts Wait for the right note, the pyramid button starts Arcade: **OK**.
2. The slider shows 100 ms, keeps its value across a restart and the Configuration window shows the same value: **OK**.
3. **"Is the default less hard?": no, the same.** SC-008 is **not met**. The owner then said: input = the computer's built-in microphone, the verdict that comes back most is **Missed**, and the slider at 200 ms was not tried.

## What this means
The difficulty the owner feels with the built-in microphone is mostly notes that are **not recognised** (Missed), not notes outside the timing window: widening the window cannot help with that, and the owner's feeling did not change. The slider stays (it is what was asked for, it works, and it is tested), but the real fix is the **recognition with that microphone**, which is the subject of spec 013 (per-device calibration). A useful check that was not done: with the slider at 200 ms, does anything change? If not, this confirms the diagnosis.

## Left open
* SC-008 (the feel) not met; the numbers of the window stay provisional until validated on real play.
* The cost of a wide window on ringing notes (above) is a possible follow-up.
