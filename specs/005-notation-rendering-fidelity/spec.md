# Feature Specification: Professional score and tablature rendering

**Feature Branch**: `feature/interactive-practice` (spec dir `005-notation-rendering-fidelity`)

**Created**: 2026-10-07

**Status**: Specified from the owner's side-by-side bend fixture; not planned or implemented

**Input**: User description: "For the same score, TuxGuitarAdvanced must provide notation and tablature of professional, Guitar-Pro-class readability. A whole-tone bend must be labelled `full`, not `1`; the tablature must show `TAB` at the left; and the renderer must preserve the musical meaning and produce balanced, readable engraving."

## Scope and reference

This specification improves the shared score/tablature renderer. It is independent of Practice recognition and Arcade, although both benefit from a more readable score.

The initial acceptance fixture is the same musical file opened in both applications: local score `tests/2/1-bend-comme-ecrit.tg` (and its GP4 source when available). The two screenshots and the detailed comparison are preserved in [visual-comparison.md](visual-comparison.md). The Guitar Pro image is a quality and semantic reference, not a pixel template and not an asset to copy into the product.

### Notation evidence for `full`

- The official [Guitar Pro 7 user guide](https://blog.guitar-pro.com/wp-content/uploads/2018/10/GuitarPro7-user-guide.pdf) states that `Full` equals a whole tone and controls the tablature display.
- Yamaha's official [electric-guitar notation guide](https://www.yamaha.com/en/musical_instrument_guide/electric_guitar/play/) defines a full bend as raising the pitch by one tone, while a half bend raises it by one semitone.
- Finale's [guitar-bend notation options](https://usermanuals.finalemusic.com/Finale2011Win/Content/Finale/IDD_GUITARBEND_OPTIONS.htm) explicitly describe displaying a whole-step bend as `full` instead of `1`.
- In the approved local fixture, every bend rises from model value 0 to 4. The project's validated conversion is 0.5 semitone per model value, so 4 means two semitones: exactly one whole tone / one `full` bend.

## Clarifications

- Q1: What does `full` mean? → In conventional guitar bend notation, `full` means one whole tone, i.e. two semitones. In this TuxGuitar model that fixture is represented by a bend movement value of 4 quarter-tone units. Rendering changes the label only; it MUST NOT change the stored bend or playback.
- Q2: Where is `TAB` shown? → At the left of every system containing a tablature staff, stacked vertically and aligned with that staff.
- Q3: Must the screen copy Guitar Pro exactly? → No. It must reach equivalent semantic completeness, balance and readability using original TuxGuitar artwork and theme resources.
- Q4: Is page composition the same as continuous horizontal editing? → No. Semantic marks are mandatory in every layout. Compact title/header composition and non-stretched measure layout are acceptance requirements for page/vertical/print presentation; continuous horizontal mode may use the viewport but must remain readable.
- Q5: What is authoritative? → The parsed TuxGuitar song model. A reference screenshot may reveal a missing mark but may never override the score data.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Read bends without decoding internal numbers (Priority: P1)

A guitarist opens a score containing bends and immediately understands the requested pitch movement from standard guitar notation.

**Independent Test**: Render the approved bend fixture and programmatic fixtures for quarter-, half-, three-quarter-, full-, one-and-a-half- and two-tone bends, releases and pre-bends; inspect the semantic render tree and screenshots.

**Acceptance Scenarios**:

1. **Given** a whole-tone bend, **When** the tablature is rendered, **Then** its arrow is labelled `full`, never `1`.
2. **Given** another supported bend amplitude, **When** rendered, **Then** the label follows conventional guitar notation and is derived from the bend value without changing it.
3. **Given** a bend, release or pre-bend, **When** score and tablature are both visible, **Then** both staves communicate the pitch path and remain horizontally aligned to the same musical event.
4. **Given** a bend target requiring an accidental, **When** the standard staff is rendered, **Then** the target cue carries the correct accidental and is not presented as a separately attacked note.

---

### User Story 2 - Recognise the tablature and instrument at a glance (Priority: P1)

A guitarist can immediately distinguish the standard staff from the tablature, identify the instrument/tuning when supplied by the score, and follow each system without ambiguity.

**Independent Test**: Render 4-, 6- and 7-string pitched tracks in screen and print/page layouts, with and without tuning/name metadata.

**Acceptance Scenarios**:

1. **Given** a system containing tablature, **When** it starts, **Then** a clearly legible vertical `TAB` identifier appears to its left without crossing strings, barlines or instrument labels.
2. **Given** tuning metadata, **When** the first system/page header is rendered, **Then** a localised tuning label is shown in low-to-high order; absent metadata is not invented.
3. **Given** an available short instrument or track name, **When** there is room in page/print presentation, **Then** it is shown to the left of the system without displacing `TAB`; absent metadata remains absent.
4. **Given** score and tablature together, **When** measures wrap or the window is resized, **Then** both staves retain the same barlines and horizontal event alignment.

---

### User Story 3 - Obtain balanced professional engraving (Priority: P1)

The score remains compact, high-contrast and readable rather than looking like oversized symbols placed in a mostly empty canvas.

**Independent Test**: Golden-image and geometry tests render the bend fixture at supported zoom levels, light/dark themes, normal and compact layout, and print/page layout.

**Acceptance Scenarios**:

1. **Given** the bend fixture, **When** rendered at 100%, **Then** clef, time signature, tempo, noteheads, fret numbers, bend arrows and labels have a coherent typographic hierarchy and no collision.
2. **Given** a short two-measure score in page/vertical/print presentation, **When** laid out, **Then** measures use musically proportional compact spacing and are not stretched merely to fill the window width.
3. **Given** any supported zoom or theme, **When** rendered, **Then** staff lines, notation, labels and selection remain distinguishable with sufficient contrast.
4. **Given** repeated rendering of the same score, layout, theme and scale, **When** screenshots and semantic geometry are captured, **Then** the output is deterministic.

### Edge Cases

- Bends whose amplitude has no conventional named label: use a documented unambiguous fractional/numeric fallback, never a misleading rounded label.
- A bend followed by a barline or another effect: reserve its actual glyph bounds so the arrow and label do not collide or get clipped.
- Multiple simultaneous bends: preserve each distinct written path or use an explicit documented collision strategy; never silently omit an effect.
- Bend and release in the same note, pre-bend, pre-bend release and hold: direction and start/target state remain distinguishable.
- Very narrow windows and high zoom: horizontal scrolling is preferable to overlapping or dropping notation.
- One-string/percussion tracks: do not show a guitar `TAB` identifier where tablature is not displayed.
- Missing title, tuning or short-name metadata: do not invent text or display empty placeholders.
- Practice highlight: its frame must not cover bend labels, arrows, target cues, fret numbers or the `TAB` identifier.

## Requirements *(mandatory)*

### Semantic fidelity

- **FR-001**: Rendering MUST be a lossless visual projection of the parsed score for every supported notation item; layout MUST NOT alter stored music, playback, import or export.
- **FR-002**: A whole-tone bend (two semitones; movement value 4 in the current quarter-tone bend representation) MUST display the conventional label `full`, never the internal/numeric label `1`.
- **FR-003**: Bend amplitude labels MUST be produced by one tested semantic formatter shared by every screen/print layout. At minimum, the conventional quarter-, half-, three-quarter-, full-, one-and-a-half- and two-tone cases MUST be covered; localisation MUST NOT change their musical meaning.
- **FR-004**: The tablature MUST render an intelligible bend curve from its written fret toward a clear arrowhead, with the amplitude label associated with that curve. Bend, hold, release and pre-bend paths MUST remain visually distinct.
- **FR-005**: When standard notation is displayed, a bend MUST also have a standard-staff representation of its source-to-target pitch path. The target MUST be a non-attacked cue and MUST include any required accidental.
- **FR-006**: Standard staff, tablature fret, bend path and target cue MUST refer to the same underlying note/effect and remain horizontally aligned.

### System identity and metadata

- **FR-007**: Every rendered system containing tablature MUST show `TAB` vertically at the left of the tablature staff, aligned with its string lines and separated from barlines and other labels.
- **FR-008**: Page/vertical/print presentation MUST show available title, track/instrument identity and tuning using the score metadata. Text MUST be localised where applicable and MUST NOT be invented when absent.
- **FR-009**: Tuning MUST be displayed in documented physical low-to-high order and support the actual number of track strings rather than assuming six.

### Engraving and layout quality

- **FR-010**: Clef, time signature, tempo, measure number, noteheads, stems, accidentals, fret numbers, barlines, effect glyphs and text MUST use a coherent scale and baseline hierarchy. No item may be disproportionately enlarged relative to the staff at the same zoom.
- **FR-011**: Layout calculations MUST reserve measured bounds for every glyph and label. No bend arrow/label, accidental, measure number, note, fret number, selection or practice highlight may collide, be clipped or erase another semantic mark.
- **FR-012**: Page/vertical/print layout MUST use compact, duration-aware horizontal spacing. A short score MUST NOT be expanded across the full available width solely to fill the viewport. Continuous horizontal mode may expand its timeline but MUST preserve semantic alignment and minimum/maximum readable spacing.
- **FR-013**: Score and tablature barlines and events MUST share the same horizontal time mapping. Visual refinement MUST NOT create apparent rhythmic disagreement between the two staves.
- **FR-014**: Foreground notation and text MUST meet WCAG AA text/essential-graphic contrast against the score background in supplied light and dark themes; staff lines may be lighter but MUST remain continuously readable.
- **FR-015**: All new shapes and labels MUST be drawn from original TuxGuitar primitives/resources. The implementation MUST NOT copy Guitar Pro fonts, glyphs, code or image assets.

### Regression protection

- **FR-016**: The test suite MUST include semantic-layout assertions in addition to screenshots: bend label, path kind, source/target pitch, accidental, `TAB` identifier, metadata presence, event/barline alignment and non-overlapping bounds.
- **FR-017**: Golden rendering tests MUST cover the approved fixture at multiple scales and themes with explicit tolerances. They MUST detect missing marks and collisions without requiring unstable pixel-for-pixel equality across operating systems.
- **FR-018**: Tests MUST cover screen horizontal, screen vertical/page and print rendering wherever they use distinct paths; a fix in one path MUST NOT leave another semantically incomplete.
- **FR-019**: Existing file-format round trips and playback MUST remain unchanged, including the original bend point values.

## Success Criteria *(mandatory)*

- **SC-001**: The approved `1-bend-comme-ecrit` fixture displays four `full` labels, four readable bend curves on tablature and four corresponding source-to-target bend indications on the standard staff; it displays no amplitude label `1`.
- **SC-002**: Every tablature system in the acceptance fixtures has exactly one visible, non-overlapping `TAB` identifier at its left.
- **SC-003**: Automated geometry checks report zero intersections between semantic glyph bounds for the acceptance fixtures at 50%, 75%, 100%, 150% and 200% scale in light and dark themes.
- **SC-004**: Score and tablature event anchors and barlines differ by at most one device pixel after final scaling in every acceptance screenshot.
- **SC-005**: A 4-, 6- and 7-string fixture displays the correct string count and low-to-high tuning order without hard-coded guitar assumptions.
- **SC-006**: A reviewer can identify, without consulting the source model, the tablature staff, each bend direction and target amplitude, the target accidental where required, and the onset shared by score and tablature in every approved screenshot.
- **SC-007**: The full macOS SWT build and renderer tests pass, with no changes to parsed song data, saved files or playback event values.

## Out of Scope

- Replacing the entire rendering engine or cloning Guitar Pro's proprietary visual identity.
- Changing bend recognition, Practice tolerances or Arcade presentation.
- Changing the meaning or file representation of existing bend points.
- Completing every possible notation symbol in this first increment; newly discovered fidelity gaps are added as explicit fixtures and requirements rather than silently bundled.
