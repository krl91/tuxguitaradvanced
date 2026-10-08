# Research: current audio-input enumeration

**Date**: 2026-10-07

## Current implementation

- `InputDeviceCatalog.listInputs()` calls `AudioSystem.getMixerInfo()` on every invocation, so the engine can already see a newly enumerated Java Sound input without an application restart.
- `InputSelectorPanel.reload()` already rebuilds the Practice dropdown, preserves a selection by descriptor id and applies the first-use default policy. No user control calls it after panel creation.
- `TunerSettingsDialog` obtains `engine.getCatalog().listInputs()` only while constructing its own dropdown. It does not observe later changes.
- `CurrentInputSelection` is the shared selection used by tuner and Practice, so explicit selection already has the correct shared effect.
- `AudioInputDescriptor` documents its id as stable only "within the run" and currently incorporates mixer index, mixer name and channel. Hot-plug can reorder mixer indices, so raw id equality is insufficient to preserve a physical endpoint across refresh.

## Planning consequence

Introduce one engine-level refresh/catalog snapshot service with generation ordering and listeners. Keep the platform enumeration in `InputDeviceCatalog`, but compare endpoints using a stable identity abstraction rather than directly binding UI state to the current enumeration index. Both Practice and tuner selectors consume the same snapshot. The refresh button asks this service to enumerate asynchronously; only explicit user selection calls `CurrentInputSelection.select(...)`.

If Java Sound exposes no stable identity for two identical devices, do not pretend it does: preserve selection only for an unambiguous match, otherwise show the endpoints and require the user to choose.

