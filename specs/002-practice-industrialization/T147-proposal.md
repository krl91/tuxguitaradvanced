# T147 — Proposed behaviour for imported files with measure errors (for owner review)

Status: DRAFT, not implemented. Rule given by the owner: behave like Guitar Pro.

## Behaviour
1. File read without any problem: nothing is shown.
2. File that cannot be read: the read error is shown (existing behaviour, unchanged).
3. File read but with measure problems (too long / too short / overlap / early start / discontinuous / tied-note):
   no dialog at opening. A menu tool lists every problem, non-blocking.

## Inspected code (current behaviour)
- `desktop/TuxGuitar/src/app/tuxguitar/app/action/listener/cache/controller/TGUpdateReadSongController.java`:
  after a read, if `TGSongManager.hasMajorError(song)` it runs `TGOpenMeasureErrorsDialogAction` (the dialog the owner sees).
- `.../action/listener/save/TGInvalidSongInterceptor.java`: with a major error, listed actions (saving) are intercepted.
- `.../view/dialog/errors/TGMeasureErrorDialog.java`: the dialog; `TGSongManager.getMeasureErrors(song)` gives
  (track, measure, voice, error code or tied-note type).

## Planned changes
- A. Remove the automatic call in `TGUpdateReadSongController` (core change, justified: it is the only trigger at open).
- B. Add the Tools-menu entry "Analyse the score" that opens the existing error list, non-modal, one row per problem
  (track, measure, voice, kind in plain words); selecting a row moves the cursor to that measure.
  Reuse `TGMeasureErrorDialog` if it can run non-modal; otherwise a new dialog.
- C. Texts in `common/resources/lang/messages*.properties` (EN + FR).
- D. Tests: unit test of the problem list (stub song with a too-long measure, as in file "(4).gp4": track 8, measure 215,
  voice 0, TOO_LONG); open-without-dialog check on that file.

## Questions for the owner
1. Saving: today a file with major errors is blocked by `TGInvalidSongInterceptor` (save intercepted). Keep that block,
   or allow saving and only warn? (Guitar Pro saves.)
2. Should a discreet hint appear (e.g. status line "1 problem found — Tools > Analyse") or really nothing?
3. Does the analysis tool also offer a one-click "repair" (trim/pad the measure), or only list? (Proposal: list only.)
