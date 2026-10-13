# Specification Quality Checklist: TuxGuitarAdvanced brand identity

**Created**: 2026-10-08

**Last reviewed**: 2026-10-14 (finding I4 of `/speckit-analyze`; the two last lines were negative statements and are written as positive ones, their state re-evaluated)

**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] The exact product-owner-selected file is embedded and identified by dimensions and digest.
- [x] The logo is explicitly the product logo, not an Arcade-only visual.
- [x] The wordmark and visual invariants are unambiguous.
- [x] The primary master is distinguished from future production derivatives.

## Requirement Completeness

- [x] Full-logo, icon-only, platform package and documentation uses are covered.
- [x] Aspect ratio, transparency, safe margins, colour identity and exact naming are covered.
- [x] Light/dark backgrounds and small native icon sizes are covered.
- [x] Replacement governance and explicit owner approval are covered.
- [x] Existing notices and provenance are preserved without making unsupported ownership claims.

## Testability

- [x] The master has a byte-level digest check.
- [x] Required visual review sizes and backgrounds are enumerated.
- [x] Package presence, dimensions and formats can be checked automatically.
- [x] Platform launcher/window surfaces require manual packaged-build validation.

## Readiness

- [x] The brand decision and authoritative asset are complete.
- [x] Ready for `/speckit-plan` when application-wide integration is prioritised.
- [x] Production derivatives D1 to D4 are designed and approved by the owner (2026-10-13, dark background).
- [ ] The light-background review of the derivatives is recorded (T037, finding C1 of the analysis of 2026-10-14).
- [x] The integration plan and the tasks are created (plan, research, data model, contracts, quickstart, 37 tasks) and the integration is done for the beta 0.5.0-beta.1.
- [ ] The manual checks on Windows and Linux are done (T030, reported as not checked in the release notes).

