# Feature Specification: Refresh audio inputs after hot-plug

**Feature Branch**: `feature/interactive-practice` (spec dir `007-refresh-audio-inputs`)

**Created**: 2026-10-07

**Status**: Specified; not planned or implemented

**Input**: User description: "Add an important button to refresh the input list when a new USB microphone is connected after TuxGuitarAdvanced has started."

## Scope and existing behaviour to preserve

The main tuner window exposes the shared recognition input directly: a device/channel selector and a **Refresh audio inputs** button are visible without opening a settings dialog. Practice setup/running input selectors and audio-recognition settings that expose an input provide the same shared selector behaviour.

Refreshing enumerates the operating system's current capture devices and channels. It updates all open selectors from one shared snapshot, preserves the selected device/channel when it still exists and never changes the active input merely because a new device appeared.

The initial delivery target is the packaged macOS SWT application and USB microphone/interface hot-plug. The engine and UI behaviour remain portable.

## Clarifications

- Q1: Does connecting a device select it automatically? → No. Refresh makes it available; the user explicitly selects it. Audio input must never change silently.
- Q2: What if the current input still exists? → Keep the same physical device and channel, with uninterrupted capture where the platform permits.
- Q3: What if the selected input disappeared? → Stop using it, retain an explicit unavailable/disconnected state and ask the user to select another input. Do not silently fall back to the built-in microphone.
- Q4: What if there was no previous selection? → Preserve the existing first-use policy: after refresh, the system default may be offered/selected exactly as it is on initial panel creation.
- Q5: Does refresh test or open every microphone? → No. Enumeration must not open capture lines. A selected new input starts the normal capture and level-check path.
- Q6: Is automatic polling required? → No. This feature is the explicit button. Platform notifications may be added later but must reuse the same refresh service.
- Q7: Must the user open tuner settings to choose an input? → No. Input choice and Refresh are first-class controls in the main tuner window; advanced audio-format settings may remain in a settings dialog.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Use a USB microphone connected after startup (Priority: P1)

A user starts TuxGuitarAdvanced with only the built-in microphone, then connects a USB microphone or audio interface. They press one button and can select the new input without restarting the application.

**Independent Test**: Launch the packaged macOS SWT application, open Practice and the tuner, connect a USB input, press Refresh and verify that the device and its usable channels appear in every selector.

**Acceptance Scenarios**:

1. **Given** TuxGuitarAdvanced is already running, **When** a new USB input is connected and Refresh is pressed, **Then** it appears once in the list with every usable channel.
2. **Given** the built-in microphone remains selected, **When** the new device appears, **Then** the built-in microphone remains selected and capture is not restarted solely by refresh.
3. **Given** the user selects the new USB input, **When** selection completes, **Then** tuner and Practice use that shared input and the normal level check restarts.
4. **Given** Practice and tuner selectors are both open, **When** either Refresh button is pressed, **Then** both show the same refreshed catalog and selection.
5. **Given** the tuner is open, **When** the user wants another microphone, **Then** they can select it directly in the tuner window without opening Settings or closing the tuner.

---

### User Story 2 - Recover when an input is disconnected (Priority: P1)

A user unplugs the selected USB input and refreshes. The application clearly says that the input is unavailable and lets them choose another source without crashing or pretending that capture continues.

**Independent Test**: Select a fake/hardware USB device, begin listening, remove it from the catalog, refresh and select the built-in microphone.

**Acceptance Scenarios**:

1. **Given** the selected USB device has disappeared, **When** Refresh completes, **Then** capture stops safely and the selector shows a localised disconnected/unavailable state.
2. **Given** another microphone is available, **When** the selected device disappears, **Then** the application does not select or open it automatically.
3. **Given** the user selects a remaining input, **When** capture starts, **Then** the disconnected state clears and a fresh level assessment is performed.

---

### User Story 3 - Refresh repeatedly without disrupting audio (Priority: P2)

The user may press Refresh multiple times while tuning or preparing Practice. Identical catalogs do not restart capture, reset settings or create duplicate entries.

**Independent Test**: Refresh an unchanged catalog repeatedly, inject reordered and duplicated provider results, and press Refresh rapidly while analysis subscribers are active.

**Acceptance Scenarios**:

1. **Given** the catalog has not changed, **When** Refresh is pressed, **Then** the displayed list and selection remain stable and the active capture generation is unchanged.
2. **Given** Refresh is already running, **When** it is pressed again, **Then** only one enumeration runs and the button communicates that work is in progress.
3. **Given** a provider returns duplicate display names, **When** refreshed, **Then** distinct devices/channels remain distinct and no duplicate descriptor for the same physical endpoint is shown.

### Edge Cases

- A USB device appears during enumeration or disappears immediately afterward.
- The operating system returns the same devices in a different order after hot-plug.
- Two devices expose the same human-readable name.
- A multi-channel interface adds one device but several selectable input channels.
- The same physical device's volatile mixer index changes after another device is attached.
- Enumeration throws, blocks unusually long or returns an empty list.
- Microphone permission is denied; enumeration remains separate from opening a capture line.
- Refresh occurs during level check, active tuner listening, Practice setup or a running Practice session.
- An old asynchronous refresh completes after a newer one; stale results must not overwrite the newer snapshot.
- The selector or its window is disposed while refresh is in progress.

## Requirements *(mandatory)*

### Catalog refresh

- **FR-001**: The main tuner window MUST directly expose the shared device/channel selector and an adjacent keyboard-focusable **Refresh audio inputs** control, without requiring the settings dialog. Every other UI surface that exposes the shared recognition input MUST provide the same Refresh control. The control MUST have an icon plus localised accessible name and tooltip.
- **FR-002**: Activating Refresh MUST enumerate the current operating-system capture devices and usable channels asynchronously and publish one immutable shared catalog snapshot.
- **FR-003**: All open input selectors MUST observe the shared catalog snapshot; they MUST NOT each perform independent enumeration or show divergent lists.
- **FR-004**: Refresh MUST preserve the selected physical device and channel when that endpoint still exists, even if provider enumeration order or volatile mixer index changed.
- **FR-005**: A newly discovered device MUST NOT become active automatically when a valid input is already selected.
- **FR-006**: If the selected endpoint disappears, capture MUST stop safely, selection MUST enter an explicit unavailable state, and the UI MUST require an explicit replacement choice. It MUST NOT silently switch to another microphone.
- **FR-007**: If no input has ever been selected, the existing first-use system-default policy MAY run after refresh.
- **FR-008**: An unchanged catalog MUST be a no-op for selection and capture: no capture restart, analysis reset, level-check reset or selection revision solely because Refresh was pressed.
- **FR-009**: Enumeration MUST NOT open, probe by recording from or request permission for every listed input. Capture/permission behaviour begins only through the normal selected-input path.

### Identity, concurrency and error behaviour

- **FR-010**: Device identity MUST be separate from display text and MUST NOT rely solely on enumeration index. It MUST include the strongest stable provider identity available plus channel identity.
- **FR-011**: When the platform cannot distinguish two same-named endpoints reliably, refresh MUST preserve selection only if the match is unambiguous; otherwise it MUST request explicit selection rather than choosing incorrectly.
- **FR-012**: Catalog results MUST be deduplicated by endpoint identity while preserving every independently selectable channel of a multi-channel device.
- **FR-013**: Concurrent/repeated refresh requests MUST be coalesced or serialised. A stale completion MUST never replace a newer catalog.
- **FR-014**: Enumeration MUST NOT run on the UI event thread or the real-time capture/analysis threads. Publishing the final snapshot and changing widgets MUST follow existing UI synchronisation rules.
- **FR-015**: During refresh, the button MUST show a busy/disabled state without clearing the current list. On failure, the previous valid list and selection remain visible and a localised non-crashing error is shown.
- **FR-016**: If a selector/window is disposed before completion, the result MUST update the shared engine state safely but MUST NOT access disposed widgets.

### Shared selection behaviour

- **FR-017**: Selecting a newly discovered input MUST use `CurrentInputSelection` and the existing capture restart/level-assessment path so tuner and Practice change together.
- **FR-018**: Refresh MUST NOT change recognition settings, tuner state, Practice progress or the selected track. Only an explicit input change may restart input-dependent assessment.
- **FR-019**: If the current input is physically lost during active Practice, the session MUST pause/wait with an input-lost explanation and MUST NOT advance, mark notes wrong or fall back to ambient audio from another device.
- **FR-020**: All button, busy, disconnected, empty-list and error text MUST exist in French and English.

### Regression and test requirements

- **FR-021**: Catalog tests MUST simulate unchanged, added, removed, reordered, duplicate-name, multi-channel and identity-ambiguous device sets.
- **FR-022**: Tests MUST assert selection and capture revisions: unchanged refresh causes neither to change; explicit selection changes input once; device loss stops capture once without selecting a replacement.
- **FR-023**: UI tests MUST press Refresh and prove that every open selector updates, values remain selected when appropriate, and disposed widgets are never touched.
- **FR-024**: Concurrency tests MUST control two refresh completions deterministically and prove that the older result cannot overwrite the newer one, without sleeps.
- **FR-025**: A packaged macOS SWT acceptance test MUST cover connecting and disconnecting a real USB microphone/interface after application startup, refreshing from Practice and tuner, selecting the new endpoint and receiving live level/pitch data.
- **FR-026**: Existing input selection, tuner, Practice, capture lifecycle and settings persistence tests MUST remain green.

## Success Criteria *(mandatory)*

- **SC-001**: A USB input connected after startup appears in every open selector after one Refresh action without application restart.
- **SC-002**: Across 100 deterministic unchanged/reordered refresh cycles, the selected endpoint is preserved, zero duplicate descriptors appear and capture restarts zero times.
- **SC-003**: Across added-device tests, a new endpoint is listed in 100% of cases and automatically selected in 0% of cases when a valid selection exists.
- **SC-004**: Across removed-device tests, capture stops safely, an unavailable state is shown and another device is automatically selected in 0% of cases.
- **SC-005**: Controlled concurrent-refresh tests always retain the newest generation and perform no disposed-widget access.
- **SC-006**: Manual macOS validation confirms hot-plug discovery, explicit USB selection, shared tuner/Practice input and live signal without restarting TuxGuitarAdvanced.
- **SC-007**: The full macOS SWT build and all audio-engine, tuner and Practice tests pass.
- **SC-008**: In the packaged tuner window, selecting and refreshing inputs requires zero navigation into Settings and at most one direct selection action after the desired endpoint appears.

## Out of Scope

- Automatic periodic polling for devices.
- Automatically switching to a newly connected or fallback microphone.
- Changing macOS microphone permissions or aggregate-device configuration.
- Managing output/MIDI devices; this feature concerns audio capture inputs only.
- Persisting an unreliable operating-system mixer index as durable hardware identity.
