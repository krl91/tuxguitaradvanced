# Contract: palette dodge (spec 017)

## Behaviour (UI contract)
| Situation | Result |
|---|---|
| Wait or Follow run, current or next box under the palette (8 px margin) | jump to a free corner of the score area (FR-002) |
| Several free corners | not the corner just left while the notes are near it; not the side the notes head to on the same line; then the nearest (FR-003) |
| No free corner | the corner covering the least of the current box; stay (FR-005) |
| Same current event, already moved | stay, unless the score area or the palette changed size (FR-004) |
| Nothing under the palette | stay (US1/AC4) |
| Run paused | only the current box counts; the next is not looked at (Edge Cases) |
| Check period | on each event change and every `PaletteDodge.CHECK_MILLIS` (100 ms) while a Wait or Follow run goes (SC-001) |
| Learner moved the palette | that place is kept until it is in the way (FR-007) |
| Palette outside the main window | never moved (FR-007) |
| Arcade, editor, end of run, toolkit without screen mapping | nothing happens (FR-009) |

Every move: position only, same size, no focus change, no command posted, no verdict changed (FR-006).

## Hooks
- `UIControl.toDisplay(UIPosition)`: screen position of a control-relative point, or null when the toolkit cannot tell (default).
- `TGExternalNoteFramePainter.frameBounds(TGLayout, List<TGNote>, int)`: canvas box of the frame the painter draws around these notes (score and tablature union), or null; `paint` draws exactly this box.
