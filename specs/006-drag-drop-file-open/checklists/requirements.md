# Specification Quality Checklist: Open scores by drag and drop

**Created**: 2026-10-07

**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] User outcome and supported surface are explicit.
- [x] Existing File → Open behaviour is authoritative.
- [x] No second parser/document lifecycle is introduced.
- [x] macOS SWT delivery and toolkit-neutral architecture are distinguished.

## Requirement Completeness

- [x] Single and multiple files are covered.
- [x] Supported, unsupported, corrupt, inaccessible and duplicate files are covered.
- [x] Unsaved documents and already-open documents are covered.
- [x] Unicode paths, modal windows, playback and UI-thread safety are covered.
- [x] Directories, URLs and non-file payloads are explicitly out of scope.

## Testability

- [x] Unit, integration, native-adapter and manual packaged-app tests are required.
- [x] Equivalence with File → Open is measurable.
- [x] Batch ordering, deduplication and failure isolation are measurable.
- [x] No UI-thread parsing and no unsaved-document mutation are measurable.

## Readiness

- [x] Ready for `/speckit-plan`.
- [ ] Implementation plan and tasks not yet created.
- [ ] Native drag-and-drop support is absent from the current shared UI contract and must be investigated in planning.

