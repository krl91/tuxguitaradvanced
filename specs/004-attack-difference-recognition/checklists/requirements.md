# Specification Quality Checklist: Polyphonic recognition by attack-difference spectrum

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-10-05
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs) — the method (what the audio adds at the attack) is the requirement itself, stated at the level of behaviour
- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
- [x] Requirements are testable and unambiguous
- [x] Success criteria are measurable
- [x] Success criteria are technology-agnostic
- [x] All acceptance scenarios are defined
- [x] Edge cases are identified
- [x] Scope is clearly bounded
- [x] Dependencies and assumptions identified

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
- [x] User scenarios cover primary flows
- [ ] Feature meets measurable outcomes defined in Success Criteria — NOT met: on the reference recording 35 of 38 judged events are recognised and 4 events are outside the measured speed limit (SC-001 asks 42 of 42), and 2 of 12 chords with a note missing are still accepted (FR-003); SC-002 to SC-005 are met. The replay test still contains these explicit exceptions.
- [x] No implementation details leak into specification

## Notes

- SC-003 (delay) is a provisional target; it is reported and revisited with the measured figure.
- Open risk recorded, not a clarification: 100 % on the reference recording may not be reachable without trading the 0 false-acceptance target; if so the measured trade-off is reported to the owner, never hidden.
