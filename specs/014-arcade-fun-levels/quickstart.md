# Quickstart: validating spec 014

Prerequisites: the macOS SWT build directory, JDK 21 (`CLAUDE.md`).

1. **Pure parts:** `mvn test -pl ../../TuxGuitar-practice -am -Dtest='ArcadeLevelFilterTest,ArcadeLeniencyTest,ArcadeChoicesTest' -Dsurefire.failIfNoSpecifiedTests=false`: counts at each level, the even spread, determinism, chords whole, never empty, the one-way overlay, the window factor and its cap, the store's fallbacks (FR-006 to FR-009, FR-012).
2. **Nothing changes elsewhere:** the whole default suite after a clean compile (practice 1066 and engine 373 today, plus the new tests); the recognition reports equal to the base; Follow, Wait and the tuner give the same parameters at every position (FR-004, FR-011, SC-003).
3. **Measure SC-001 and SC-002:** the gated replay test on the reference recording and the chord takes, at each position, printing the right notes accepted, the wrong notes accepted (of the 98 negative tests) and the median delay, with Follow's next to them; reported in `benchmark/validation.txt`, honestly.
4. **The start path:** the source-structure tests of the Arcade start unchanged and green; a new test that a run prepared at level 3 has the kept sounds only, and that a restart and a replay keep the level.
5. **The windows:** headless tests of the start window row and of the leniency line; with `-P swt-gui-tests`, `PracticeWindowSwtTest` unchanged and a new native check of the level row (FR-006, FR-010).
6. **Texts:** the language tests pass with the new keys in English, French and Spanish (FR-013).
7. **By hand (owner):** start Arcade, see level 1 proposed the first time, choose level 3, play, read the result naming the level and the leniency; restart the application and see level 3 proposed; move the leniency and feel the difference; **is it fun?** (SC-006).
8. **The removed sounds are heard in the backing (FR-015, SC-007):** `-Dtest='RemovedSoundsPlayerTest'` with a fake output: each removed event sent once at its written time and ended after its duration, a chord whole, nothing at level 5, nothing heard twice or lost at a pause, a stop and a restart; then the spike's measure of the offset against the existing backing; by hand (owner): at level 3 the music sounds complete and in time.
