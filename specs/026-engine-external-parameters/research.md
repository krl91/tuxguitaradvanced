# Research: spec 026, every recognition engine value is an external parameter

Each decision cites the source it rests on (constitution III). Paths are relative to the repository root; `AE` = `desktop/TuxGuitar-audio-engine/src/main/java/app/tuxguitar/audio/engine`, `PR` = `desktop/TuxGuitar-practice/src/app/tuxguitar/app/tools/custom/practice`, `CRATE` = `desktop/build-scripts/native-modules/tuxguitar-monitor-native/src`.

## R1. The inventory: what becomes a parameter, what stays fixed

**Decision.** The starting list is section 6 of `docs/architecture/recognition-engines.md`, completed by a scan of the source made on 2026-11-06 (every `static final` number, every `Parameters.defaults()` body, every public default field of a `Parameters` class) in `AE`, `PR/session`, `PR/arcade/core` and `CRATE/analysis`. The scan found values section 6 missed:
- `AE/analysis/AttackEvidence.java` (`PARTIALS` 12, `SEARCH_CENTS` 60, `MIN_PEAK_SHARE` 0.03);
- the technique detectors' `Parameters` fields in `AE/technique/` (`BendDetector`, `SlideDetector`, `VibratoDetector`, `LegatoDetector`) and `PitchMotion` (`OUTLIER_CENTS` 400, `MIN_VOICED` 8);
- the calibration thresholds (`AE/calibration/RecognitionRate`, `RecordingQuality`, `SoundDetection` refractory and lead; `PR/session/CalibrationRun`, `CalibrationTaps`);
- `PR/session/InputQuality`, `PlayTimeWindow` (advance, gap), `TimingTolerance` (reference beat), `ClockUncertainty`, `AttackCandidates` (MIDI range);
- the relaxed values of `PR/session/FollowLeniency` (6 rows × 2 positions) and `PR/arcade/core/ArcadeLeniency` (5 rows × 2 positions).

The final per-key inventory is written by task T002 into `inventory.md` and kept complete by a source-scan test (R9).

**Stays fixed (FR-002, clarifications of 2026-11-06), each listed in the documentation with its reason:**

| Kind | Values | Reason |
|---|---|---|
| Structural sizes | sample rate 48,000; window 2,048; hop 256 (`TarsosFastYinEstimator`, `OfflineAnalysis`, `pipeline.rs`, `NativeBackend.HOP_SIZE`, `RecognitionParameters.HOP_MILLIS`); FFT 8,192 (polyphonic), 4,096 (attack), 1,024 (articulation); the spectrum band range MIDI 24–112 (it is the layout of the frame record, `FrameRecords`, `frame.rs`); `SpectralSubtractor.FRAME`/`HOP`; `ExpectedPitchVerifier.MIN_WINDOW` | Owner, clarification 1 |
| Buffers and layouts | ring and block sizes (`CAPTURE_BLOCKS`, `FRAME_RING`, `HOP_RING`, `CAPACITY`, `CHUNK`, `RECENT_FRAMES`, `BEND_FRAMES_MAX`, `MAX_PITCHES`, `MAX_HYPOTHESES`, `COST_BINS`), record indices, `VERSION`, `LAYOUT` | Not tuning values |
| Numerical safety | `EPSILON`, `RIDGE`, `GAIN_TOLERANCE`, `FLOOR_DBFS` −120 | Not tuning values |
| Plumbing | `NATIVE_OPEN_TIMEOUT_MILLIS`, `NativeBackend.WAIT_MILLIS`/`JOIN_MILLIS`, `SoundDetection` silence and total timeouts, `CaptureClock`, `SessionLoop.MIN_UI_INTERVAL_NANOS` | Owner, clarification 2 |
| Clock | `TickInterpolator`, `PlayerTempoClock`, `LatencyCompensation.LIMIT_MILLIS`, `CRATE/resampler.rs` | Owner, clarification 2 (clock drift correction); the player clock is the same kind |
| Score and display | `ArcadeScoring` steps, `PracticeSession.SPEED_SHOW_NANOS`, `FollowSession.REASON_SPAN_NANOS`, `FollowSettings` display percents (already user settings of the Follow window) | Change no recognition decision |
| Calibration protocol | `CalibrationRun.LEAD_NANOS`, `PERIOD_NANOS`, `CalibrationTaps.CLICKS` | The exercise played to the learner, not a threshold |
| Already settings | `PassagePolicy` defaults (`recognition.passage.*`), `ChordMatcher.Parameters.defaults()` (mirrors the chord settings), the play-time bounds of `PlayTimeWindow` | Already parameters |

**Rationale.** The owner's rule covers what changes what is heard or accepted; the clarifications exclude sizes, plumbing, clock and score.

**Alternatives.** Take section 6 as it is: rejected, it misses about 40 tuning values.

## R2. One parameter list, one store

**Decision.** Every new value is a `RecognitionParameter` (`AE/settings/RecognitionParameter.java`: `Number`, `Flag`, `Choice`) in `RecognitionParameters.ALL`, stored by `RecognitionSettingsStore` in the configuration module `tuxguitar-audio-recognition`, read and repaired by `RecognitionSettings.fromStrings`, restored by section. Keys: `recognition.<family>.<name>` with the families of data-model.md (for example `recognition.onset.flux-factor`, `recognition.follow.ringing`). No key of an existing parameter changes.

**Rationale.** The store already repairs missing, unreadable and out-of-bounds values (FR-003), drops unknown keys, has a *Restore* per section, and an older file without the new keys reads them as defaults (FR-007). `RecognitionParameters.VERSION` stays 1: no key is renamed or reinterpreted.

**Alternatives.** A second file for expert values: rejected, two places to set one engine (FR-005).

## R3. Defaults equal today's values, bit for bit

**Decision.** Each default is the literal of the code it replaces, written the same way (for example `1.0 / 3.0`, not `0.333`). Durations kept in nanoseconds in the code are stored in milliseconds when the source value is whole milliseconds (all are), and converted with the same arithmetic the code uses today (`ms * 1_000_000L`). Values in hops stay in hops (unit "hops"), so that `RecognitionParameters.toHops` rounding never moves a default.

**Rationale.** SC-002 (identical figures). A stored `Number` is a `double`; every literal listed is exactly the double the code uses.

## R4. How each stage reads its values

**Decision.** Each stage that has fixed values gets them from a value object built once from `RecognitionSettings` when a run (or an offline analysis) opens, following the pattern already in place: `StrumClipMeter.Parameters.of(settings)` (`AE/analysis/StrumClipMeter.java`), `PolyphonicAnalyzer.Parameters`, `AttackAnalyzer.Parameters`, `PolyphonicAcceptance.Parameters`, `PracticeParameters` (built once per run). Static constants become fields of those objects; the existing no-argument constructors and `defaults()` keep giving today's values (tests build stages without settings).

**Rationale.** The analysis loop and the cpal callback never read the store: they read final fields of an object built before the run (FR-010, constitution VI).

**Alternatives.** Read `RecognitionSettings` from each stage at each hop: rejected (lookups in the loop).

## R5. The native interface: a `double[]`, and a layout check

**Decision.**
1. Every parameter array between Java and the crate becomes a `double[]` (`open`, `configure`, `analyze`, `frontEnd`, `strumClipOffline`, `openAnalysis` in `AE/nativeengine/NativeRecognitionLibrary.java` and `CRATE/jni_recognition.rs`). `NativeParameters.LENGTH` grows to carry every value an engine stage reads; `CRATE/analysis/mod.rs` `Parameters::from_slice` reads the same indices and refuses any other length.
2. The 21 values carried today keep their present precision: Java writes them rounded through `float` (`(double) (float) value`), so the native engine computes exactly what it computes today. A later spec may drop that rounding, with its own before/after.
3. A new JNI function `parameterLayout()` returns the crate's `LENGTH`. After loading the library, `NativeRecognitionLibrary` calls it: a missing symbol (`UnsatisfiedLinkError`, an older library) or another length makes the library unusable with the reason `LIBRARY_MISSING` and a detail naming the version mismatch; the run falls back to the Java engine with the existing notice (`RecognitionEngineNotice`).

**Rationale.** The new values (for example the polyphonic relative salience 0.10, today an `f64` literal in `CRATE/analysis/polyphonic.rs`) would change bits if sent as `float` (SC-002). An older library given a longer `float[]` would accept it silently (`from_slice` checks only `len < LENGTH`), so a layout check is needed (FR-009). The owner approved extending the interface (2026-11-06, Q3 A).

**Alternatives.** A second `double[]` beside the `float[]`: rejected, two layouts to keep in step. Keep `float[]`: rejected (precision).

## R6. Values that size buffers in the native engine and in Java

**Decision.** A tuning value that sets how much a stage keeps (the attack lag in hops, the number of polyphonic candidates, the number of harmonics, the technique windows) has an upper bound, and the stage allocates for that bound when it is built (at `open`, never at `configure`). A `configure` changes only the value used, never a size.

**Rationale.** `CRATE/recognition.rs` swaps parameters through a lock-free mailbox and its loop "never allocates, locks or waits on Java"; resizing there would break it.

## R7. The settings window

**Decision.** New `RecognitionParameter.Section` values, each a category of the existing drop-down (`PR/ui/RecognitionSettingsPanel.java`), placed after `ADVANCED`, named "Advanced: <family>" (French "Avancé : …", Spanish "Avanzado: …"). Each holds at most 7 parameters, the budget `RecognitionControls.MAX_SECTION_ROWS` = 8 rows (7 values and the *Restore* button) that `RecognitionControlsTest.everySectionFitsWithoutScrolling` already enforces for every section; a family with more values is split ("Advanced: Follow (1/3)"). Existing categories keep their place and content (FR-005a). Every parameter has a name and a tip in the three message files (`common/resources/lang/messages{,_fr,_es}.properties`), checked by the existing `RecognitionControlsTest.everyTextOfTheRubricIsInTheTranslationFile`.

**Rationale.** The existing tests already loop over `Section.values()` (`RecognitionSettingsPanelTest`, `RecognitionSettingsSwtTest` "inside the window" for every control and *Restore* button), so the new categories are checked by them unchanged. A new SWT test adds the 768-pixel screen (SC-007), as spec 025 did for the practice window.

**Alternatives.** One scrolled "Expert" category: rejected, the owner asked for more Advanced categories rather than overflow. A second drop-down (group then family): rejected, it changes the existing panel's layout.

## R8. Device profiles and leniency

**Decision.** No new parameter is added to `ProfileParameters.LIST` (`AE/settings/ProfileParameters.java`): a device profile does not overlay them. The relaxed values of `FollowLeniency` and `ArcadeLeniency` become parameters (`recognition.follow-leniency.*`, `recognition.arcade-leniency.*`, one per row and position) read by those classes instead of their literals; the rule "the more lenient of the effective value and the position's" is unchanged.

## R9. Keeping the inventory complete

**Decision.** A new test `EngineConstantInventoryTest` (audio-engine) and `SessionConstantInventoryTest` (practice) scan the source for numeric `static final` fields, `Parameters.defaults()` bodies and public numeric default fields, and fail on any that is neither read from a parameter nor listed in the fixed list of `docs/architecture/recognition-engines.md` section 6 (with its reason). A `CRATE` test does the same with `const` items of `analysis/`. `EngineDocumentationTest` (spec 024) already fails when a key of `RecognitionParameters.ALL` is not documented; it is extended (new test, not an edit) to require the six facts of FR-008 in the table row.

## R10. Proving that nothing changed (SC-002)

**Decision.** Before any code change (task T001), run and keep the reports of every dataset and benchmark test of the branch with each engine: `DatasetBenchmarkTest`, `OwnerChordsProfileTest`, `ChordLeniencyLevelsTest`, `OwnerChordsBenchmarkTest`, `SyntheticChordGuardTest`, `RecordedPracticeReplayTest` (if its local files are there), the Java/native parity tests, and the `benchmark` tag reports. After each phase, the same reports are compared byte for byte (`diff`), times and dates excepted. Any difference stops the phase (bug protocol).

## R11. Platforms

**Decision.** Validated on macOS (local gate, SWT); Linux and Windows through the CI builds (the crate is built for each by `.github/workflows/release.yml` and `ubuntu-maven.yml`). The CI timing tests that fail on runners (memory `ci-timing-tests-flaky`, 2026-11-06) are not touched by this spec and are reported apart.
