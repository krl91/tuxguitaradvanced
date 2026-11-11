# Contract: the offset test, replayed before every version

FR-004, FR-005, research R7.

- **Class**: `desktop/TuxGuitar-practice/test/.../dataset/OwnerOffsetTest.java`, JUnit tag `owner-offset`. Not excluded in the pom (only `swt-gui` is, in this module): it skips itself without its data, like the `dataset` tests.
- **Inputs** (system properties):
  - `tuxguitar.owner.offset`: one folder or several separated by commas, each holding one `.gp5` (or `.gp4`, `.gp3`) and one `.wav`; absent: the test is skipped, never failed. A folder holding a file `report-only` is reported but never fails the test (the owner's further recordings); every other folder is gated;
  - `tuxguitar.owner.offset.wav`, `tuxguitar.owner.offset.track`: optional, when the folder holds several;
  - `tuxguitar.owner.offset.report`: the Markdown file the table is written to;
  - `tuxguitar.owner.offset.reattack`: `off` replays the verdicts of before (`reattack.enabled` off in the settings the test builds), to prove that the test fails;
  - `tuxguitar.native.library.dir`: as for the other native dataset tests; without the library, the native rows say "not available" and the test fails (both engines are required).
- **Alignment**: offset 0 lays the tablature's attacks on the rises of the sound's energy (5 ms RMS every ms, summed at each target ±3 ms, leads −500 to +1000 ms), never on an engine's detection.
- **Runs**: Wait (offset 0 only, no tempo), Follow (`FollowStarter.prepareFollow` defaults) and Arcade (`ArcadeStarter` defaults, leniency `ArcadeLeniencyPosition.DEFAULT`, level: [owner's answer to spec FR-003]), Java and native, offsets −100, −70, −50, −40, −30, −20, 0, 20, 30, 40, 50, 70, 100 ms (negative: early; the sound padded with silence).
- **Table**: `| Mode | Engine | Offset (ms) | Recognised | Rate | Refusals |`, refusals as counts per `RefusalReason`; then, per failing row, the events missed (index, time, pitch, reason).
- **Verdict**: fails when any row between −70 and +70 ms is below 100 %; the rows at ±100 ms never fail.
- **Script**: `misc/owner-offset-check.sh <report> <folder>…` sets JDK 21, runs the test from the macOS build directory and restores the benchmark files Maven rewrites.
- **Release**: `docs/VERSIONING.md` "Before a release": run the script on the owner's machine; the table goes into `docs/release-notes/<tag>.md` under the heading `## Offset test`. `misc/verify-offset-table.sh <notes>` (with its self-test `misc/test-verify-offset-table.sh`, both listed in `.github/workflows/release.yml`) fails the release when the heading is missing or a gated row between −70 and +70 ms is under 100 %.
