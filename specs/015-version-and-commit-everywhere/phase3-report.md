# Phase 3 report (spec 015, User Story 1): what ran and what was seen

Date: 2026-10-22, macOS arm64, Temurin 21.

## Ran
- Tests written first and seen failing: `BuildIdentityTest`, `TitleLayoutDefaultTest` (compilation error, classes absent), `BuildInfoWiringTest` (5 assertion failures), `AboutBuildInfoSourceTest` (3 failures), `BuildInfoStringsTest` (1 failure, 2 errors: keys absent), `AboutClipboardSwtTest` (compilation error, `UITextClipboard` absent), `misc/test-stamp-build-info.sh` (all 16 cases failed, script absent).
- After the code: the 24 new unit tests pass; `AboutClipboardSwtTest` (profile `swt-gui-tests`, real SWT) passes: the SWT factory puts the line on the system clipboard and it is read back.
- Full build `mvn -e clean verify -P native-modules` on macOS: BUILD SUCCESS. Practice module: 1172 tests (1148 before + 24 new), 0 failures, 10 skipped (the same 10 as in `baseline.txt`). The `.app` package was built (the packaging step that failed in the baseline on a Finder `.DS_Store` race passed this time).
- The package contains `Contents/MacOS/share/build-info.properties`: `version=dev`, `numeric=0.0.0.0`, `commit=73e9d4ad2…` (HEAD), `modified=true` (the phase 3 files were not committed yet).
- `tuxguitar.sh --version` printed `TuxGuitarAdvanced dev (73e9d4a)`.

## Not seen (to be looked at by the owner, or in a later run)
- The About window, the main window title and the Copy button in the running application: the app was launched, but a screenshot came back entirely black (screen locked or no capture permission) and the window list was empty to System Events, so nothing was read from the screen. What is covered instead: the wiring by `BuildInfoWiringTest` and `AboutBuildInfoSourceTest` (source structure), the title resolution by `BuildInfoWiringTest` (the real resolver with the real variables), the clipboard by `AboutClipboardSwtTest`. The layout of the new line and button in the About window is NOT verified visually.
- The `.tg` header stamp (SC-005): not checked by saving a file. Checked by diff only: `TGVersion.java` and `TGSongWriterImpl.java` have 0 changed lines since commit 80e25247d, and the writer still writes `TGVersion.CURRENT` (2.1.1).
- The instance of the application I launched was stopped; no other instance was touched.

## Decisions found while implementing (recorded in `research.md` R14)
- The UI toolkit has no clipboard: a small interface `UITextClipboard` (1 method) was added to `TuxGuitar-ui-toolkit` and implemented by `SWTFactory` only; JavaFX and Qt are not changed (About then says the copy is not available).
- The settings window shows the layout that is really used (`MainOption` goes through `TitleLayoutDefault`).
