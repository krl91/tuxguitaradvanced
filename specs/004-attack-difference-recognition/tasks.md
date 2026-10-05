# Tasks: Polyphonic recognition by attack-difference spectrum (spec 004)

- [x] T001 Spec, plan, research R6/R7/R8 (benchmark `real-recording-1`).
- [x] T002 `NnlsSolver`, `AttackAnalyzer`, `AttackEvidence` (+ offset in cents per pitch) in the engine, with unit tests (ringing bass, octave, semitone, two strikes, determinism).
- [x] T003 `AnalysisFrame.getAttackEvidence`, `AnalysisService.setAttackCandidates`, `AudioEngine.setAttackCandidates`, `OfflineAnalysis` (same stages offline).
- [x] T004 `PolyphonicAcceptance` (+ tests), `AttackCandidates`, `PracticeSession.setPolyphonicAcceptance` (hybrid with the existing rules), window wiring (on by default).
- [x] T005 `RecordedPracticeReplayTest` (skipped without `tests/1`), event-time fixture `test-data/real-recording-1/events.csv`.
- [x] T006 `MonophonicRegressionReplayTest` (SC-004).
- [ ] T007 FR-003: reject a chord with a note missing (4 of 12 still accepted, research R8).
- [ ] T008 FR-004/FR-008 learner messages (not separately verifiable; speed limit), fr + en.
- [ ] T009 SC-003 report of delay in the app (measured in the test: median 107 ms, p90 169 ms).
- [ ] T010 Triplets (events within 0.15 s): measure the real limit.
