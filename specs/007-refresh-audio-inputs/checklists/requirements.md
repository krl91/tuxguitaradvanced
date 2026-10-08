# Specification Quality Checklist: Refresh audio inputs

**Created**: 2026-10-07

**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] The hot-plug user problem and button placement are explicit.
- [x] The main tuner window exposes input selection and Refresh directly, not only through Settings.
- [x] Refresh and explicit input selection are separate operations.
- [x] Existing shared capture/selection behaviour remains authoritative.
- [x] macOS SWT delivery and portable engine behaviour are distinguished.

## Requirement Completeness

- [x] Added, removed, unchanged, reordered and ambiguous devices are covered.
- [x] Multi-channel devices and duplicate names are covered.
- [x] Practice, tuner and other shared selectors are kept consistent.
- [x] Active sessions, permission, errors, concurrency and disposed UI are covered.
- [x] Automatic fallback/switching is explicitly forbidden.

## Testability

- [x] Selection and capture restart counts are measurable.
- [x] Stale asynchronous completion has a deterministic no-sleep test.
- [x] Shared UI update and disposed-widget behaviour are testable.
- [x] A real macOS USB hot-plug acceptance test is required.

## Readiness

- [x] Ready for `/speckit-plan`.
- [ ] Implementation plan and tasks not yet created.
- [ ] Stable endpoint identity capabilities of each Java Sound provider must be finalised during planning.
