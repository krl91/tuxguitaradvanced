# Specification Quality Checklist: Professional score and tablature rendering

**Created**: 2026-10-07

**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] User-visible problem and intended outcome are stated.
- [x] Guitar Pro is treated as a quality reference, not something to clone.
- [x] Screenshot observations are separated from model facts and layout-mode differences.
- [x] Scope does not alter recognition, playback or file semantics.

## Requirement Completeness

- [x] `full` is defined unambiguously as a whole tone / two semitones.
- [x] `TAB` placement is defined for every tablature system.
- [x] Standard-notation and tablature bend semantics are both covered.
- [x] Metadata, compact/page layout, contrast and collision behaviour are covered.
- [x] Missing metadata and uncommon bend amplitudes have fallback rules.
- [x] 4-, 6- and 7-string tracks are covered without a six-string assumption.

## Testability

- [x] Requirements include semantic layout assertions, not screenshots alone.
- [x] Golden tests tolerate platform rasterisation differences.
- [x] Zoom, theme, horizontal, vertical/page and print paths are covered.
- [x] Non-overlap and score/tab alignment have measurable criteria.
- [x] File-format and playback non-regression are explicit.

## Readiness

- [x] Ready for `/speckit-plan`.
- [ ] Implementation plan and tasks not yet created.
- [ ] Baseline semantic render tree and golden images not yet captured by automated tests.

