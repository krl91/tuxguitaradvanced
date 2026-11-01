# Final report: spec 019, the demo fire on the score and the Advanced tab

Date: 2026-11-01. Status: every task done (50/50); released in v0.8.0-beta.5 (2026-11-01). The owner's gate T048 passed on 2026-11-01 (sheet F11, "ça me semble bon", no value to tune).

## What was built

- **The fire on the score (US1, P1).** In Wait and Follow, with *Score fire* (Mode tab) on Discreet or Full, the Amiga fire of spec 018 burns at the bottom of the visible score. It is painted by a second paint listener of the score's canvas, after the score, on the same painter (`ScoreFire`, `ScoreFireScene`). It grows with the streak of right notes (`ScoreStreak`, full height at 16 by default, at most a third of the score area) and falls back within a second on a mistake. A pause holds it. It fades near the framed note, the next note and the palette (`ScoreGuardBoxes`, asked at each picture). On a slow computer the quality tiers step it down, then off. It is off by default.
- **R2 decision (T002): hook needed.** A full Wait repaint of the score measured a median of 3.2 ms, above the 3 ms threshold. The core gained an overlay-only repaint (`TGBufferedPainterLocked.requestOverlayOnly`, `TGControl.redrawOverlay`), so the fire's frames do not redraw the score. `redraw()` still asks for a full picture, which wins over an overlay request.
- **The sparks (US2).** Each right note throws a burst rising from the bottom of the fire, more with the streak, gone within `sparks.life`, and never over a guard box. A replay throws exactly one burst per right note.
- **The Advanced tab (US4).** Two groups, *Arcade fire* and *Score fire*:
  - 47 and 48 settings (`FireParameter`, `FireTuning`), kept by `FireTuningStore`;
  - a live preview per group (`FirePreview`), a *Sparks* button, a growth slider and *Default values*;
  - a tooltip for every setting, in EN, FR and ES (129 texts per language);
  - one value written per move; a run keeps the tuning it read at its start.
  With the defaults, Arcade's pictures are byte-identical to before (`FireTuningIdentityTest`).
- **Kept, and the same everywhere (US3).** The choice and the values survive a restart. Reduce motion is read on macOS (as before), Linux (GNOME `gsettings`, KDE `kreadconfig6` or `kreadconfig5`, reduced if any says so) and Windows (PowerShell, "Show animations in Windows"), for both fires and the previews (`SystemReducedMotion`). On Linux and Windows it is fetched in the background, so a run never waits.
- **The judging never changes** (FR-008): the same scripted Wait and Follow runs give the same verdicts, timings and summary with the fire off, discreet, full, and at every setting's minimum and maximum.

## Tests added

23 new test files. No existing test was changed, skipped or removed: `git diff --name-status 955d7a051 HEAD` on the test folders shows 24 additions and nothing else, the 24th being `ArcadeDarkBackgroundSwtTest` of spec 021.

| Phase | Tests |
|---|---|
| 2 | `FireParameterDefaultsTest`, `FireParameterLevelDefaultsTest`, `FireTuningTest`, `FireTuningIdentityTest`, `FireParameterFlameLevelIdentityTest`, `FireTuningStoreTest`, `FireSourceScanTest`, `ScoreFireSettingKeptTest` |
| 3 | `ScoreStreakTest`, `ScoreFireSceneTest`, `ScoreFireLevelTest`, `ScoreFireOverlaySwtTest`, `ScoreGuardBoxesTest`, `ScoreFireVerdictsTest`, `OverlayOnlyRepaintSwtTest` |
| 4 | `ScoreSparksTest`, `ScoreBurstsTest` |
| 5 | `FireTextsTest`, `FireExtremesTest`, `FireExtremesVerdictsTest`, `FirePreviewTest`, `FireSettingsPanelSwtTest` |
| 6 | `SystemReducedMotionTest` |

Every test was seen red first: it failed to compile, it failed on the missing behaviour, or it was failed by a named one-run mutation that was never committed (T018, T030, T034).

Counts (T046): see `baseline.txt`.

## Measured

The fire's own painting at FULL, 1280×800, 100 frames (T029):

| Mode | Median | 95th percentile |
|---|---|---|
| Wait | 3.3 ms | 4.4 ms |
| Follow | 2.8 ms | 4.0 ms |

The medians are below the plan's 4 ms goal; the 95th percentiles are slightly above it. **The owner accepted these times on 2026-11-01 (T049)**: the gap is at most 0.4 ms on the slowest 5 % of frames, and the fire's own quality controller (8 ms budget) still makes it cheaper on a computer that cannot keep up. The code is unchanged.

## Not verified

- **The real Linux and Windows commands of R7** (`gsettings`, `kreadconfig6`/`kreadconfig5`, the PowerShell line) have only been run against a fake system. Other Linux desktops (Cinnamon, XFCE, …) are not read. Testers' feedback is asked in sheet F11 and must be asked in the release notes.
- **The screenshots of T044** were taken on a throwaway build fed with a scripted streak (no audio, no real run), so the score-fire picture has no palette or red frame.
- **Everything seen on screen**: judged by the owner at gate T048 on macOS (2026-11-01, overall answer "ça me semble bon"); Linux and Windows only through the testers.
