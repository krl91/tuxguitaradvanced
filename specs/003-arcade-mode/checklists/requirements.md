# Specification Quality Checklist: Arcade Mode

**Created**: 2026-10-01

**Last reviewed**: 2026-10-08 after approval of selected-track MIDI backing, dry monitoring, the 22 ms latency gate and the performance-over-decoration policy
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain (FR-025 to FR-029 reconciled with the approved visual direction on 2026-10-07)
- [x] Requirements are testable and unambiguous
- [x] Success criteria are measurable
- [x] Success criteria are technology-agnostic
- [x] All acceptance scenarios are defined
- [x] Edge cases are identified
- [x] Scope is clearly bounded
- [x] Dependencies and assumptions identified
- [x] The player-selected track is excluded from MIDI while all other playable tracks form the backing.
- [x] Initial dry monitoring and deferred reverb/distortion are explicitly separated; future effects are independently adjustable, bypassable and combinable.
- [x] The 22 ms limit is an enablement gate, not merely an informational warning.
- [x] Software timing budgets and physical loopback validation are both required.
- [x] Future effects require a source-cited free/open-source library survey and equal-condition live benchmarks before selection.
- [x] Licence, portability, real-time safety, CPU, allocation, dropout and remaining latency headroom are selection gates.
- [x] Audio continuity and unchanged judging take priority over animation, which takes priority over decorative quality.
- [x] Adaptive quality has a deterministic drop order and explicitly protects every gameplay-critical visual.
- [x] Apple Silicon frame pacing is measured under concurrent MIDI and recognition, not from an isolated paint loop.
- [x] Hardware acceleration is vendor-neutral at the scene boundary, benchmark-gated and optional; a functional fallback is mandatory.
- [x] Frame queues, audio-thread rendering work and render-held audio locks are forbidden and testable.
- [x] The physical neck exposes targets for exactly one event, the active event (or its complete chord), with an atomic transition; camera look-ahead never exposes or admits another event.
- [x] Every approaching chord carries its exact written name when available; otherwise a tested deterministic name is derived from written pitches/bass, with an honest pitch-list fallback and no use of detected audio.
- [x] One visual set — every event due within five seconds, notes and chords alike, whatever their number — is the only thing drawn; every later event is absent, no compact or preview form exists, each note comes from the horizon for five seconds on its fret lane, a fret number is dropped where it would overlap, completed events keep only their verdict badge, and musical data remains unchanged.
- [x] Chords of the set share one centred temporal lane; only one has a full in-bounds diagram, the others of the set are bars without diagram, chords due later are absent, and promotion follows onset order.
- [x] Visual acceptance includes a deterministic, diverse external GP3/GP4 corpus; normal builds skip without it and copyrighted score bytes/private paths are never committed.
- [x] The actionable neck target is a large opaque filled string-coloured disk with a contrasting outline; quality reduction may remove decoration but never weaken this core cue.
- [x] Every visible fret has one predefined arrival lane ending at the midpoint of its painted cell top; all strings reuse it, markers remain on it, and neither the lane nor a connector crosses the physical neck.

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
- [x] User scenarios cover primary flows
- [x] Feature meets measurable outcomes defined in Success Criteria
- [x] No implementation details leak into specification

## Notes

- Clarifications resolved; ready for `/speckit-plan` (after the tempo-following mode of spec 002 exists). The approved visual references and their score-to-render contract are preserved in [visual-design.md](../visual-design.md). Planning must include its deterministic string mapping, fret geometry, event timing, chord cartouche, metadata fallback, stroke, feedback, look-ahead camera, accessibility, resize and performance tests, in addition to SC-010 to SC-012.
- Recognition targets remain provisional; the monitoring limit is fixed by the owner at a hard 22 ms maximum (SC-003).
- Planning must define the latency fixture and protocol before claiming monitoring support. Electrical loopback is preferred; an acoustic built-in-microphone/speaker test is useful only with propagation and room uncertainty documented.
- The later effects plan must preserve the candidate matrix and raw benchmark results. It may select no third-party library when none meets the licence, integration and 22 ms constraints.
- The approved concept art is a composition/readability reference, not a resolution target. The simplest renderer that passes SC-014 to SC-016 wins; retaining the existing toolkit renderer is a valid and preferred result when it passes.
- [x] The string palette and order of 2026-10-11 (FR-067) and the lit string (FR-068) are specified and testable (SC-012, SC-025).
- [ ] The owner confirms on the built application the palette, the order (lowest string at the top) and the lit string (T100, T101).
