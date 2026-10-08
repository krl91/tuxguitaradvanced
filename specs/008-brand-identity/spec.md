# Feature Specification: TuxGuitarAdvanced brand identity

**Feature Branch**: `feature/interactive-practice` (spec dir `008-brand-identity`)

**Created**: 2026-10-08

**Status**: Primary logo selected by the product owner; application integration not planned or implemented

**Input**: Product-owner decision: "Use this image as the new TuxGuitarAdvanced logo."

## Authoritative asset and decision

The product owner's selected image is the official primary logo for TuxGuitarAdvanced:

![Official TuxGuitarAdvanced primary logo](assets/tuxguitaradvanced-logo-primary.png)

The exact authoritative raster asset is
`assets/tuxguitaradvanced-logo-primary.png` (PNG, 1254 x 1254, RGBA, SHA-256
`a9d369a2574396cd3208b0665648c57f398690499efb137d6781478991baf9e0`).

This logo represents the whole TuxGuitarAdvanced product, not only Arcade mode. It combines the
existing Tux penguin and guitar identity with the interactive-learning experience: a receding
isometric fretboard, six colour-coded strings and incoming playable notes. The wordmark is exactly
**TuxGuitarAdvanced**, with `TuxGuitar` in white/silver and `Advanced` in yellow/orange.

The selected file MUST remain the source of truth. Future production assets are derivatives of this
master; they MUST NOT be recreated from a text prompt or silently replaced by a visually similar
generation.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Recognise the official product (Priority: P1)

A user sees TuxGuitarAdvanced in the application, its installer, documentation or release page and
recognises the same product identity in every location.

**Independent Test**: Compare each branded surface with the authoritative asset and verify the
wordmark, mascot, guitar, note highway, colour order and proportions.

**Acceptance Scenarios**:

1. **Given** an official TuxGuitarAdvanced surface with enough room for the full logo, **When** its
   branding is displayed, **Then** it uses the authoritative primary logo or an approved derivative.
2. **Given** the logo appears on a light or dark background, **When** it is rendered, **Then** its
   transparency, silhouette, colours and text remain readable.
3. **Given** two official surfaces, **When** their branding is compared, **Then** neither changes the
   product name, string-colour order, mascot, guitar or note-highway concept.

---

### User Story 2 - Use a legible small icon (Priority: P1)

A user can identify the application in Finder, the Dock, task switchers, installers and window
chrome even when the full wordmark cannot be read.

**Independent Test**: Produce icon derivatives from the master, render them at all target platform
sizes and inspect them on light and dark backgrounds.

**Acceptance Scenarios**:

1. **Given** a target that cannot legibly display the full wordmark, **When** the branding is reduced,
   **Then** an approved icon derivative is used rather than squeezing or cropping the primary logo.
2. **Given** the smallest supported icon size, **When** it is rendered at native scale, **Then** the
   main silhouette remains identifiable and no required element becomes an accidental blur.
3. **Given** platform icon requirements, **When** assets are packaged, **Then** aspect ratio,
   transparency, safe margins and required sizes are preserved.

---

### User Story 3 - Derive additional brand formats safely (Priority: P2)

A maintainer can create horizontal, icon-only, monochrome or accessibility variants without losing
the identity selected by the product owner.

**Independent Test**: Review each proposed derivative against the invariant list and obtain explicit
product-owner approval before marking it as official.

**Acceptance Scenarios**:

1. **Given** a new derivative, **When** it is proposed, **Then** its purpose, source master, changes
   and supported uses are documented.
2. **Given** an unapproved redesign or AI regeneration, **When** official assets are assembled,
   **Then** it is rejected even if it looks similar to the master.

## Visual invariants

The following properties define the selected identity and MUST be preserved in full-logo uses:

- the Tux penguin is the main character and holds a red-to-orange semi-hollow electric guitar;
- the scene includes an isometric six-string/fretboard highway receding into the distance;
- the six strings/lanes and their playable notes use one stable rainbow colour per string, in the
  visible red, orange, yellow, green, blue and violet order;
- luminous circular notes approach on the matching coloured strings;
- the exact wordmark is `TuxGuitarAdvanced`, without a space or alternate capitalisation;
- `TuxGuitar` is rendered in white/silver and `Advanced` in yellow/orange;
- the asset has a transparent background and must retain its original proportions.

These invariants describe the selected artwork. They do not redefine fretboard geometry or note
rendering rules for Arcade gameplay; those remain governed by spec 003.

## Requirements *(mandatory)*

### Source asset and governance

- **FR-001**: The PNG identified in **Authoritative asset and decision** MUST be stored in the
  repository and treated as the official primary TuxGuitarAdvanced logo.
- **FR-002**: Official full-logo uses MUST use the authoritative asset without regeneration,
  redrawing, recolouring, retouching or content-aware cropping.
- **FR-003**: The logo MUST represent the entire TuxGuitarAdvanced application and MUST NOT be
  labelled or presented as an Arcade-only logo.
- **FR-004**: Any replacement of the primary logo or any derivative that changes a visual invariant
  MUST require explicit product-owner approval recorded in this specification.
- **FR-005**: Every production derivative MUST record its source, intended surfaces, dimensions and
  approval status in this specification or its implementation plan.

### Rendering and derivatives

- **FR-006**: Logo rendering MUST preserve the source aspect ratio, alpha channel and safe margins;
  it MUST NOT stretch, squash or cut the wordmark.
- **FR-007**: Surfaces with enough space MUST prefer the complete logo. Surfaces too small for a
  readable wordmark MUST use a separately prepared and approved icon derivative.
- **FR-008**: Application integration MUST produce and verify the platform-specific icon sizes and
  formats required by packaged macOS, Windows and Linux builds rather than relying on runtime
  scaling of the 1254-pixel master.
- **FR-009**: At minimum, visual checks MUST cover 16, 32, 64, 128, 256, 512 and 1024 pixels on light
  and dark backgrounds. The implementation plan MAY exclude a size that a target platform never
  consumes, but MUST state why.
- **FR-010**: A derivative MUST NOT be described as official until it has passed visual review and
  received explicit product-owner approval.
- **FR-011**: Monochrome, high-contrast, horizontal and icon-only treatments are permitted future
  derivatives, not implicit licences to alter the primary master.

### Integration and regression safety

- **FR-012**: Replacing packaged branding MUST cover the application icon, bundle/installer icon,
  about/startup branding and documentation/release surfaces that currently expose a product logo,
  as applicable to each platform and as enumerated during planning.
- **FR-013**: Branding work MUST NOT change application behaviour, score compatibility, playback,
  audio recognition or Practice/Arcade rules.
- **FR-014**: Automated packaging tests MUST verify that every declared icon asset exists, is
  readable, has the expected dimensions/format and is included in its package.
- **FR-015**: Manual packaged-build checks MUST verify Finder/Dock on macOS and equivalent launcher,
  window and installer surfaces on Windows and Linux before cross-platform integration is complete.
- **FR-016**: Existing upstream notices, licences and attributions MUST remain intact. The selected
  logo's provenance MUST be recorded as an AI-assisted redesign selected by the product owner; this
  specification does not assert ownership of third-party marks or waive a later release review.

## Success Criteria *(mandatory)*

- **SC-001**: The repository contains one byte-identical authoritative master with the documented
  dimensions and SHA-256 digest.
- **SC-002**: Every official full-logo use matches all visual invariants and uses the exact
  `TuxGuitarAdvanced` wordmark.
- **SC-003**: All packaged platform icons pass the required native-size checks without distortion,
  clipping or unreadable full wordmarks.
- **SC-004**: Light- and dark-background reviews find no lost silhouette, unintended opaque square
  or illegible wordmark at the sizes where the full logo is specified.
- **SC-005**: The product owner explicitly approves every production derivative before it replaces
  an existing application or release asset.
- **SC-006**: Branding-only integration leaves the full platform builds and existing automated tests
  unchanged in outcome.

## Out of Scope

- Implementing Arcade mode or changing its approved gameplay visuals (spec 003).
- Replacing application/package icons in this specification-only step.
- Treating automatic downscales of the full square logo as approved small icons.
- Creating animation, splash-screen motion or merchandising assets.
- Renaming the application; the canonical product name is already TuxGuitarAdvanced.

