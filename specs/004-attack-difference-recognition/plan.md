# Implementation Plan: Polyphonic recognition by attack-difference spectrum

**Spec**: [spec.md](spec.md) · **Evidence**: `specs/002-practice-industrialization/benchmark/real-recording-1/`, research R6, R7 · **Date**: 2026-10-05

## Technical context

- Modules: `desktop/TuxGuitar-audio-engine` (new package `app.tuxguitar.audio.engine.polyphonic`), `desktop/TuxGuitar-practice` (session acceptance + settings), tests in both. No core TuxGuitar change (constitution II). Java release 11 for these modules (TarsosDSP constraint).
- Real-time: the analysis thread (`AnalysisService`, hop 256) already does one FFT per hop for the chord spectrum; this adds one 4096-point FFT per hop and a small non-negative least squares (NNLS) solve on precomputed normal equations (about 35 templates), only while expected pitches are set (constitution VI, VIII: deterministic, no allocation per hop after warm-up).
- Reference data (not redistributable): `tests/1/audio_15ps.m4a` + `tests/1/_TEST_verifie.tg` (score checked against the audio by the owner: events 26 and 31 corrected, event 17 kept as E2). Event times: fitted fixture committed as a small CSV (`desktop/TuxGuitar-practice/test-data/real-recording-1/events.csv`), derived from the audio's attacks, not from the recogniser (FR-014).

## Method (what the measurements support)

1. **Attack-difference spectrum**: magnitude spectrum (Hann, 4096 samples) of the newest window minus the one `lag` earlier, square-root compressed, negative parts clipped (what appeared at the attack, not what already rings).
2. **Explain it with pitch templates**: harmonic templates (14 partials, weight h^-0.5, Gaussian width about one bin) for the candidate pitches (the pitches of the Practice track plus semitone, whole-tone and octave neighbours of every expected pitch), solved by NNLS. This lets the octave and the ringing bass be told apart where a plain comb score cannot.
3. **Decision for the expected event** (score-conditioned): the expected notes must explain at least a share θ of the attack's energy, with every note of a chord present (balance λ), for M consecutive hops; a new attack is still required (existing rule). Related pairs (subset/superset, octave) cannot be told apart by audio and are reported as not separately verifiable (spec FR-004).
4. Parameters θ, λ, M, lag, partial count, template shape come from the replay grid (research R8) and are constants with their measured justification, not user settings (FR-011).

Open point to settle with measurement: how this rule combines with the existing f0 and chord rules in the session (replace in polyphonic mode vs require both), decided by the replay and the monophonic benchmark (SC-004).

## Structure

- `engine/analysis/AttackAnalyzer` (package `analysis`, to reach the package-private FFT) (rolling spectra, difference), `PitchTemplates`, `NnlsSolver`, `AttackExplanation` (amplitude per candidate), `AttackEvidence` on `AnalysisFrame` (null unless candidates are set, same pattern as `TargetEvidence`).
- `AudioEngine.setAttackCandidates(int[])`, `AnalysisService` hook.
- `practice/session/PolyphonicAcceptance` (pure, tested without audio) used by `PracticeSession`.
- Tests: unit (NNLS, templates, tracker on synthetic chords with a ringing bass), session tests on scripted evidence, and the replay test `RecordedPracticeReplayTest` (tag `recording`, skipped with a visible reason when the audio is absent; decodes the m4a with macOS `afconvert` when no .wav is next to it).

## Constitution check

II extend not modify (new package; the session gains one acceptance path), VI real-time (no waiting, bounded work), VII separable logic (pure classes), VIII deterministic, XII/XIII truthful and honest semantics (not-separately-verifiable stated), XIV learner-centred (named missing notes, limits shown).
