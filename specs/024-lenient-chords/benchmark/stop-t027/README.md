# Stop at T027 (2026-11-05): the owner's choice A

- `idmt-triad-profile-{java,native}.txt`: `IdmtTriadProfileDiagnosticTest` (tag `dataset`), the 30 major and minor triads of IDMT-SMT-Audio-Effects NoFX, G2 to A3, under the profile rule. Only G2 minor is refused (low-register semitone leak).
- `chord-weak-name-probe-{java,native}.txt`: a probe of the weak names tolerated (always, from four names, never) and the match threshold, on the IDMT chords and incomplete chords and the owner's three series. Its code (a temporary `ChordMatcher` value and a probe test) was removed after the owner's choice B; the reports are kept.

Both engines gave identical figures. Decision: spec Clarifications, option B.
