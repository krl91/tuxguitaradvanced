# TuxGuitarAdvanced Constitution

TuxGuitarAdvanced is a fork of TuxGuitar (upstream: helge17/tuxguitar) that adds interactive
guitar-learning capabilities while preserving TuxGuitar's strengths as a multitrack tablature
editor and player. This constitution governs how the fork evolves; it does not prescribe the
design of any individual feature.

## Core Principles

### I. Preserve TuxGuitar Compatibility

- Existing TuxGuitar behaviour (editing, playback, import/export, settings, plugins) MUST keep
  working unchanged unless a ratified specification explicitly calls for a change.
- Songs readable or writable by upstream TuxGuitar MUST remain readable and writable with the
  same results. Changes to persisted formats (e.g. `.tg`) or user configuration MUST be
  backward compatible or explicitly specified with a migration path.
- New capabilities MUST be additive and, where they add runtime cost, possible to leave inactive.

Rationale: users adopt the fork for TuxGuitar first; learning features must not degrade it, and
staying close to upstream keeps future upstream merges tractable.

### II. Extend Before Modifying Core

- New functionality MUST use existing extension mechanisms (plugins, separate modules, the action
  system, event listeners) when the repository architecture supports it.
- A change to core modules (e.g. `common/TuxGuitar-lib`, `desktop/TuxGuitar`) MUST be justified
  in the technical plan by a concrete limitation found in inspected source, and MUST be the
  smallest change that removes that limitation.

Rationale: invasive core edits raise regression risk and conflict with upstream changes.

### III. Evidence-Based Architecture

- Architectural claims and decisions MUST be grounded in inspected source code and MUST cite the
  real files, classes, or build configuration they rely on.
- TuxGuitar APIs, classes, or extension points MUST NOT be invented or assumed. When behaviour
  cannot be confirmed from source, it MUST be recorded as an open question or verified
  experimentally.

Rationale: TuxGuitar is a large, long-lived codebase; plausible-but-wrong assumptions are the
most expensive kind of design error.

### IV. Spec-Driven Feature Development

- Significant feature work MUST follow the Spec Kit flow: specification (WHAT and WHY), then
  technical plan (HOW), then tasks, then implementation.
- Substantial implementation MUST NOT begin from informal discussion alone.
- Material ambiguities in product behaviour MUST be resolved explicitly with the product owner
  and recorded in the specification, not silently decided during implementation.
- Small fixes and upstream-sync work MAY skip the full flow but still follow all other principles.

Rationale: learning features involve product judgement (what counts as "correct", how feedback
is shown) that must be decided deliberately and traceably.

### V. Incremental, Independently Testable Delivery

- Features MUST be delivered in small phases, each independently reviewable, testable, and
  leaving the application usable.
- Work belonging to a later milestone MUST NOT be implemented opportunistically in an earlier one.

Rationale: small phases keep review feasible and let each step be validated on real hardware
before the next builds on it.

### VI. Real-Time Audio Correctness and Responsiveness

- Code on real-time audio paths (capture, playback, synthesis) MUST NOT perform blocking I/O,
  unbounded allocation, lock contention with the UI, or other work with unbounded latency.
- Audio processing MUST NOT degrade existing playback or UI responsiveness.
- Latency and timing expectations for any real-time feature MUST be stated in its specification
  and verified, not assumed.
- Audio device unavailability, permission denial, or format mismatch MUST degrade gracefully
  with a clear message, never crash or hang the application.

Rationale: in a practice tool, late or wrong timing is indistinguishable from wrong feedback.

### VII. Separable, Testable Audio Logic

- Signal-processing and evaluation logic MUST be separable from UI toolkits and from live audio
  hardware where practical, so that it can be exercised by automated tests with recorded or
  synthesized input.
- Where separation is impractical, the plan MUST say why and describe how the behaviour will be
  verified instead.

Rationale: live-hardware-only behaviour cannot be regression-tested and is hard to debug.

### VIII. Deterministic, Explainable Signal Processing First

- Deterministic, explainable signal-processing techniques MUST be preferred.
- Machine-learning approaches MAY be introduced only when a specification documents a need that
  deterministic methods demonstrably fail to meet, and the plan addresses model size, licensing,
  platform support, and explainability of results.

Rationale: explainable behaviour is easier to test, debug, tune, and justify to a learner.

### IX. Cross-Platform Conscious, macOS-First

- macOS on Apple Silicon is the initial development and validation target.
- Designs MUST NOT needlessly preclude TuxGuitar's other platforms (Linux, Windows, FreeBSD,
  Android) or UI toolkits. Platform-specific code MUST be isolated behind an abstraction or in a
  platform-specific module, following existing patterns (e.g. `desktop/build-scripts/*` variants,
  native modules).
- Code placed in `common/` MUST remain compatible with its current Java release level and with
  Android.
- A plan MUST state which platforms a phase is validated on; untested platforms MUST NOT be
  claimed as supported.

Rationale: TuxGuitar's value includes portability; early shortcuts are costly to undo.

### X. No Unrelated Refactoring

- Changes MUST be limited to what the current task requires.
- Reformatting, renaming, or restructuring code unrelated to the task MUST NOT be mixed into
  feature changes. If needed, it MUST be a separate, explicitly approved, style-only change.

Rationale: TuxGuitar formatting is heterogeneous; mixed changes make review and upstream merges
painful.

### XI. Build and Test Integrity

- The repository MUST build and existing tests MUST pass after every implementation phase, using
  the validated build for the target platform.
- Tests MUST NOT be disabled, skipped, or weakened to make a build pass without explicit approval
  and a recorded reason.
- New logic that can be unit-tested MUST come with tests.

Rationale: a fork that drifts into a broken state cannot absorb upstream fixes safely.

### XII. Observable Acceptance and Truthful Reporting

- Every specified requirement MUST have acceptance criteria that can be observed by a test or a
  described manual check.
- Test, build, and manual verification results MUST be reported exactly as executed. Work MUST
  NOT be described as verified, passing, or working unless that verification actually ran;
  skipped or failed checks MUST be stated.

Rationale: trust in the project's state depends on reports matching reality.

### XIII. Honest Guitar-Audio Semantics

- Features MUST respect what the input can actually establish. Conventional mono guitar audio can
  validate pitch and timing, but it cannot always prove which physical string and fret produced a
  note, because the same pitch is playable at several positions.
- Specifications MUST state which properties are validated from audio and which are assumed or
  unverified, and the product MUST NOT present unverifiable properties as verified.
- Capabilities beyond this (e.g. positional validation) require input that can support them and
  MUST be specified as such.

Rationale: overclaiming detection accuracy misleads learners and erodes trust in all feedback.

### XIV. Learner-Centred Feedback

- Feedback MUST be understandable and actionable for a guitar learner: it MUST communicate what
  was expected, what was detected, and how they differ, in musical terms the learner understands.
- Uncertain detection MUST be presented as uncertain, not as a definite error.
- Specifications for learning features MUST define how feedback helps the learner improve, not
  only what is technically detected.

Rationale: a technically correct detector that confuses or discourages the learner has failed
its purpose.

## Technical & Platform Constraints

- Build: Maven per platform/toolkit variant under `desktop/build-scripts/`; Gradle for Android.
  The validated local baseline (Temurin JDK 21, macOS Apple Silicon, `tuxguitar-macosx-swt-cocoa`
  with `-P native-modules`) is documented in `README.md` ("Build from source").
- New user-facing strings MUST go through TuxGuitar's existing translation files, with English
  provided.
- New third-party dependencies MUST have an identified open-source licence that is compatible
  with the intended licence of the combined TuxGuitarAdvanced distribution, MUST be available on
  the targeted platforms, and MUST be justified in the technical plan. The project intends to
  distribute the combined work under GPLv3 before public release; GPL-3.0 dependencies are
  therefore permitted, and TarsosDSP is permitted for the current implementation.
- The licence and provenance of every third-party dependency (name, version, licence, source
  repository) MUST be documented in the technical plan or research notes and kept current.
- Full distribution and relicensing compliance (licence files, headers, notices, combined-work
  terms) is a **release gate**, not an implementation blocker: no public release or distribution
  may occur until it is complete.
- Because upstream TuxGuitar is LGPL, code that depends on GPL-3.0 components MUST stay isolated
  in fork-specific modules, so that upstream-compatible changes remain separable (Principle I).
- Local development workarounds (e.g. linking the development `.app`'s embedded JRE to a local
  JDK) MUST NOT be treated as architectural decisions or committed as source fixes.

## Development Workflow & Quality Gates

- Upstream TuxGuitar changes MUST be kept separate from fork feature changes where practical
  (sync upstream via `master`; develop on feature branches).
- Every technical plan MUST include a Constitution Check listing each applicable principle and
  any justified deviation.
- Pushing, force-pushing, rewriting published history, hard resets, and branch deletion MUST NOT
  happen without explicit approval from the repository owner. Commits are made only when requested.
- Review of each phase MUST confirm: build passes, existing tests pass, new tests exist where
  practical, acceptance criteria are demonstrated, and no unrelated changes are included.

## Governance

- This constitution supersedes informal practice for work in this fork. The operational guidance
  (the build notes of `README.md`) MUST remain consistent with it; where they conflict, this
  constitution governs and the guidance MUST be corrected.
- Amendments are made by the repository owner through the `/speckit-constitution` workflow, with
  a Sync Impact Report reviewed before commit.
- Versioning follows semantic versioning: MAJOR for removing or redefining a principle, MINOR for
  adding a principle or materially expanding guidance, PATCH for clarifications and wording.
- Compliance is checked at planning time (Constitution Check) and at review of each
  implementation phase. Deviations MUST be documented with justification in the relevant plan.

**Version**: 1.1.0 | **Ratified**: 2026-09-26 | **Last Amended**: 2026-09-26
