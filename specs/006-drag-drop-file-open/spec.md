# Feature Specification: Open scores by drag and drop

**Feature Branch**: `feature/interactive-practice` (spec dir `006-drag-drop-file-open`)

**Created**: 2026-10-07

**Status**: Specified; not planned or implemented

**Input**: User description: "A missing feature is dragging and dropping a file into TuxGuitarAdvanced to open it."

## Scope and existing behaviour to preserve

The user drags one or more local score files from Finder or another file manager and drops them on the main TuxGuitarAdvanced window. Each accepted file opens exactly as if selected through **File → Open**.

The existing open path is authoritative: `TGOpenFileAction` converts a selected local file to a URL and `TGReadURLAction` performs format selection, reading, error handling, document creation and already-open-document selection. Drag and drop MUST enter this path after file selection; it MUST NOT introduce another song reader or document lifecycle.

The first delivery target is the supported macOS SWT application. The shared UI contract MUST remain toolkit-neutral so JFX and Qt implementations can expose the same feature without application-layer forks.

## Clarifications

- Q1: Where may the file be dropped? → Anywhere in the main application window, including the score, empty canvas and tab area, except over a blocking modal dialog.
- Q2: Does a drop replace the current song? → No. It follows normal Open behaviour and opens/selects a document tab. Existing unsaved documents remain untouched.
- Q3: What file types are accepted? → Exactly the readable formats registered in the running application, the same dynamic set as File → Open; no duplicated extension list.
- Q4: What happens with several files? → Supported files open in drop order, one document per file. One failure does not prevent later files from being tried.
- Q5: What happens when the file is already open? → Its existing tab is selected; no duplicate document is created.
- Q6: Are folders and web links accepted? → Not in this feature. It accepts local regular files only.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Open a score directly from Finder (Priority: P1)

A user sees a `.tg`, `.gp3`, `.gp4`, `.gp5`, `.gpx` or any other currently registered readable score in Finder and drops it on TuxGuitarAdvanced instead of navigating through a file chooser.

**Independent Test**: Drop one valid native TuxGuitar file and one valid Guitar Pro fixture onto the macOS SWT main window and compare the resulting documents with File → Open.

**Acceptance Scenarios**:

1. **Given** a supported local score, **When** it is dragged over the main window, **Then** the window shows an unambiguous accepted-drop indication without opening it yet.
2. **Given** the supported score is released, **When** loading completes, **Then** it appears in a document tab with the same parsed song, URI, title and state as File → Open.
3. **Given** another document has unsaved edits, **When** a score is dropped, **Then** the edited document remains open and unchanged and the dropped score opens in its own tab.
4. **Given** the score is already open, **When** it is dropped again, **Then** the existing tab becomes active and no duplicate tab appears.

---

### User Story 2 - Drop several scores safely (Priority: P2)

A user selects several scores in Finder and drops them together. Valid scores open predictably and failures are explained without losing successful files.

**Independent Test**: Drop an ordered batch containing two valid supported files, one unsupported file and one unreadable/missing file.

**Acceptance Scenarios**:

1. **Given** several supported files, **When** dropped together, **Then** they open in drop order and the last successfully opened file becomes active.
2. **Given** a mixed batch, **When** one item cannot be opened, **Then** the remaining supported files are still attempted and a localised summary identifies each failed item and reason.
3. **Given** the same file appears twice in the batch, **When** processed, **Then** only one document exists for that canonical local file.

---

### User Story 3 - Understand whether a drop will work (Priority: P2)

Before releasing the mouse, the user can tell whether TuxGuitarAdvanced accepts the dragged content and receives a useful message when it cannot.

**Independent Test**: Drag supported files, unsupported files, a directory, plain text and a URL over the main window without dropping, then drop each case.

**Acceptance Scenarios**:

1. **Given** all dragged items are supported local files, **When** hovering over the window, **Then** the accepted cursor/overlay is shown.
2. **Given** no dragged item is an eligible local file, **When** hovering, **Then** the rejected cursor/overlay is shown and dropping does not alter any document.
3. **Given** a file has a supported-looking extension but invalid content, **When** dropped, **Then** the normal reader rejects it with the same safe error behaviour as File → Open.

### Edge Cases

- Paths containing spaces, apostrophes, accents, emoji or non-Latin characters.
- A file reached through a symbolic link or alternate path while the canonical file is already open.
- A file that is deleted, moved or loses permission between drag-enter and drop.
- A zero-byte, truncated, corrupt or newer-version file.
- A directory, package, URL, text selection or application-specific drag payload.
- Drop while playback is running: behaviour is exactly the existing Open/load behaviour and must not leave playback attached to the wrong document.
- Drop while a blocking modal dialog is open: reject the drop rather than open behind the dialog.
- A large batch: process in order without blocking the UI event thread during parsing.
- Application window disposed while background loading is in progress: stop UI delivery safely and never crash.

## Requirements *(mandatory)*

### Functional requirements

- **FR-001**: The main window MUST accept operating-system drag payloads containing one or more local regular files.
- **FR-002**: A dropped file MUST be opened through the same application action and reader/document pipeline used by File → Open. Drag and drop MUST NOT duplicate format detection, parsing, warning, document or recent-file logic.
- **FR-003**: The eligible format set MUST be obtained dynamically from the registered readable file formats. It MUST NOT be a hard-coded list in the drop handler.
- **FR-004**: A valid dropped file MUST produce the same song model, document URI, tab title, warnings and errors as opening that file normally.
- **FR-005**: Dropping a file MUST NOT close, replace, save, discard or otherwise modify any currently open document. Existing unsaved edits MUST remain intact.
- **FR-006**: If the canonical dropped URI already belongs to an open document, the existing document MUST be selected and no duplicate created.
- **FR-007**: Multiple dropped files MUST be processed in payload order. A failure MUST be isolated to its item and MUST NOT cancel later eligible files.
- **FR-008**: Directories, remote URLs, non-file payloads and unsupported formats MUST NOT be passed to a song reader.
- **FR-009**: Parsing MUST NOT block the native UI event thread. All UI/document mutations after reading MUST use the application's existing thread and locking rules.
- **FR-010**: Drag-over MUST expose accepted/rejected feedback using the platform cursor and, where available, a lightweight localised overlay. Hover alone MUST never open a file.
- **FR-011**: Failures MUST be localised and identify the affected filename without exposing stack traces. A multi-file drop SHOULD combine failures into one summary after the batch.
- **FR-012**: Path handling MUST preserve spaces and Unicode and MUST not execute, evaluate or interpolate any part of a filename.
- **FR-013**: The feature MUST work when the drop lands on the score canvas, tab strip or another child surface of the main window; child widgets MUST NOT silently consume eligible file drops.
- **FR-014**: A blocking modal window MUST reject or defer main-window drops explicitly; files MUST NOT open invisibly behind it.
- **FR-015**: The initial implementation MUST work in the packaged macOS SWT application. The application-facing drop contract MUST be toolkit-neutral, with native adapters isolated in UI-toolkit modules.

### Regression and test requirements

- **FR-016**: Unit tests MUST cover payload filtering, dynamic format eligibility, order preservation, duplicate/canonical URI handling, Unicode paths and per-item failure isolation.
- **FR-017**: An integration test MUST prove that a dropped file and File → Open invoke the same open service and yield equivalent document state.
- **FR-018**: UI adapter tests MUST prove that native drag-enter/over/drop data becomes the toolkit-neutral local-file event and that no song parsing occurs inside the native callback.
- **FR-019**: A macOS SWT acceptance test MUST drop real `.tg` and Guitar Pro fixtures from Finder onto the packaged application, including onto the score and tab strip.
- **FR-020**: Existing File → Open, command-line/OS file association opening, recent-file behaviour, multi-document tabs, unsaved indicators and close/save prompts MUST remain unchanged.

## Success Criteria *(mandatory)*

- **SC-001**: Every readable fixture used by the existing File → Open regression set opens successfully by drag and drop with an equivalent document model and URI.
- **SC-002**: In a batch of at least ten items containing valid, corrupt, unsupported, duplicate and Unicode-path files, every eligible unique file opens exactly once in order and every failure is reported against the correct item.
- **SC-003**: Dropping an already-open file produces zero additional tabs and activates the original tab in 100% of automated repetitions.
- **SC-004**: An unsaved document's song content, undo buffer, unsaved marker and tab remain byte-for-byte/logically unchanged after another file is dropped.
- **SC-005**: Automated UI tests confirm that drag callbacks return promptly and parsing never runs on the native UI event thread.
- **SC-006**: Manual macOS SWT validation passes for Finder drops on the canvas and tab strip, paths with spaces/accents, multiple files, unsupported files and a file already open.
- **SC-007**: The full macOS SWT build and existing file-opening/import regression tests pass.

## Out of Scope

- Importing folders recursively.
- Dropping web URLs or downloading remote scores.
- Reordering document tabs by drag and drop.
- Dragging notes, measures or tracks between documents.
- Changing supported music formats or their parsers.

