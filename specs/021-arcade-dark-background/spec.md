# Feature Specification: The Arcade view keeps its dark design in every system appearance

**Feature Branch**: `feature/interactive-practice` (spec directory `specs/021-arcade-dark-background`)

**Created**: 2026-10-31

**Status**: Draft

**Input**: User description: "spec 021 : la vue Arcade garde toujours son design sombre, quel que soit le mode d'apparence du système (choix du propriétaire, 2026-10-31, option A). Constat (spec 020, run 41246758808, image enregistrée) : la vue Arcade ne peint pas son propre fond ; sa scène translucide (manche à 10 % d'opacité, terrain translucide) se dessine sur le fond de la fenêtre fourni par le système, sombre en mode sombre, gris clair en mode clair ; en mode clair, toute la scène est délavée, les frettes deviennent foncées sur fond clair et les tests graphiques ArcadePixelAlignmentSwtTest et ArcadeCanvasSwtTest échouent sur la machine macos-15 de GitHub (mode clair, écran 1024x768), alors qu'ils passent sur le Mac du propriétaire (mode sombre). Le fond de référence du design sombre est 0x0B0E13 (Theme.background(), aujourd'hui utilisé seulement pour les images de référence) ; Theme.LIGHT existe mais n'est jamais sélectionné et ne doit pas l'être (hors périmètre). Attendu : en mode clair comme en mode sombre, la vue Arcade montre le même rendu que le design sombre ; sur macOS, Linux et Windows ; les tests existants ne sont jamais modifiés ; le workflow Practice GUI tests doit passer au vert sur macos-15 (cela ferme T026/T031 de la spec 020). Questions possibles : le feu de démo sur la partition (spec 019, ScoreFireScene en Theme.DARK) est-il concerné ; la fenêtre de résultat Arcade aussi."

## Context

- Spec 020 moved the graphical checks of the practice module to GitHub's macOS 15 machines. Two of them fail there and pass on the owner's Mac: the check that the target of a note sits between its two painted fret wires, and the check that counts the painted flames of the stage.
- The picture saved by the failing check (diagnostic run 41246758808) shows the cause. The GitHub machine is in **light** appearance; the owner's Mac is in **dark** appearance. The Arcade view does not paint a background of its own: its scene is mostly translucent (the neck at 10 % opacity, the field translucent too) and is laid over whatever background the system gives the window. In dark appearance that background is dark and the view looks as designed. In light appearance it is light grey, and the whole view comes out washed out, with dark fret wires on a pale neck.
- Every player whose computer is in light appearance sees that washed-out view today: on macOS, and very probably on Linux and Windows, whose default themes are light (not checked).
- The dark look is the design of the Arcade view (spec 003); its reference pictures are painted on the design's own dark background. A light variant of the colours exists but nothing selects it.
- Owner's decision (2026-10-31, option A): the Arcade view always shows its dark design, whatever the appearance of the system.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - The Arcade view looks the same in light and dark appearance (Priority: P1)

A player whose computer is in light appearance opens the Arcade view. The view shows the dark design: a dark background, the neck, the fret wires, the strings and the notes in the same colours as on a computer in dark appearance. Nothing of the system's light background shows through the scene.

**Why this priority**: today the view is washed out and hard to read for every player in light appearance; this is the defect itself.

**Independent Test**: paint the same moment of the same song in the Arcade view with the system in light appearance and in dark appearance; the two pictures are the same, and match the design's reference look.

**Acceptance Scenarios**:

1. **Given** a computer in light appearance, **When** the player opens the Arcade view and a song plays, **Then** the background of the scene is the design's dark background and the fret wires are lighter than the neck, as in the design.
2. **Given** a computer in dark appearance, **When** the player opens the Arcade view, **Then** the view looks as in the design (the same dark background as in light appearance).
3. **Given** the Arcade view is open, **When** the player changes the system appearance from dark to light or back, **Then** the scene keeps the dark design.
4. **Given** the Arcade view in any quality tier (full, balanced, performance) or on its simplified painting path, **When** a frame is painted, **Then** the dark background is there in every one of them.

---

### User Story 2 - The graphical checks pass on the build machines (Priority: P1)

The owner starts the graphical checks of the practice module on GitHub's macOS 15 machine, which is in light appearance. They pass, as they pass on the owner's Mac, without any change to the checks themselves.

**Why this priority**: these checks guard the Arcade view on every change; while they fail for a reason unrelated to the change being checked, they protect nothing. It also closes the open point of spec 020.

**Independent Test**: one run of the *Practice GUI tests* workflow on macOS 15 ends green.

**Acceptance Scenarios**:

1. **Given** the change is pushed, **When** the owner runs *Practice GUI tests* on GitHub, **Then** every check passes, including the fret wire alignment check and the stage flames check, and no existing check was changed, skipped or removed.
2. **Given** the owner's Mac in dark appearance, **When** the full local build runs, **Then** it passes with the same test counts as before, plus the new tests of this spec.

### Edge Cases

- The window is resized, made smaller than the screen or larger than it (the GitHub machine's screen is 1024×768, smaller than some checked sizes): the dark background covers the whole visible scene at every size, with no light strip at an edge.
- The first frame, before the song starts, and the frames during the countdown or a pause: the background is already dark.
- The simplified painting path used when the normal one fails: it also paints the dark background.
- A system with high-contrast or reduced-transparency settings: the scene still shows the dark design (only the Arcade scene is concerned; the system's settings keep applying to the ordinary windows).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The Arcade scene MUST paint, on every frame, the dark design's own background over the whole surface of the view before anything else, so that nothing of the system's window background shows through the translucent parts of the scene.
- **FR-002**: That background MUST be the one the design's reference pictures use (the dark reference background of spec 003), so that the live view and the reference pictures look the same.
- **FR-003**: The Arcade scene MUST look the same whether the system is in light or dark appearance, on macOS, Linux and Windows, including after the appearance changes while the view is open.
- **FR-004**: FR-001 MUST hold in every quality tier and on the simplified painting path, at every window size.
- **FR-005**: The light variant of the scene's colours MUST stay unselected: this spec does not add a light Arcade view.
- **FR-006**: The ordinary windows around the Arcade view (the Arcade result window, the practice windows, the menus and dialogs) MUST keep following the system appearance, unchanged.
- **FR-007**: The demo fire painted over the score (spec 019) MUST stay unchanged: it is laid over the score on purpose and gets no background.
- **FR-008**: Each new behaviour MUST have a new automated check, written first and seen failing; no existing check is changed, skipped or removed.
- **FR-009**: After the change, the *Practice GUI tests* workflow MUST pass on GitHub's macOS 15 machine. Recording that run closes tasks T026 and T031 of spec 020.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: With the system in light appearance, a painted Arcade frame shows the design's dark background in 100 % of the scene area not covered by a scene element (checked on the picture of the frame), and its fret wires are lighter than the neck.
- **SC-002**: The same frame painted in light and in dark appearance gives the same picture, within the small colour shifts the system may apply.
- **SC-003**: One run of *Practice GUI tests* on GitHub's macOS 15 machine ends with every check passing (0 failures), with no existing check changed.
- **SC-004**: The full local build passes with the same counts as before for every existing test, plus only new tests.
- **SC-005**: The ordinary windows of the application in light appearance look as before the change (checked by the owner on the release).

## Assumptions

- On the owner's Mac in dark appearance, the system's window background is close to, but not exactly, the design's background; the Arcade view may therefore become very slightly darker there. This is accepted: it then matches the reference pictures.
- The Arcade result window and the other practice windows are made of ordinary system controls and are not part of this change.
- Linux and Windows are covered by the same change of the shared Arcade painting; they are checked only by the build and its tests, not by hand, since the owner tests on macOS.
- The change ships with the next pre-release after v0.8.0-beta.3.

## Dependencies

- Spec 003 (the Arcade view's design and its reference pictures), spec 020 (the graphical checks on macOS 15, its open tasks T026 and T031), spec 019 (the demo fire over the score, left unchanged).
