# Contract: the profile library and the store's layer (spec 013)

1. `RecognitionSettingsStore.getDefault()` = the default settings, what the Configuration window edits and what the existing file saves; `get()` = **effective**: `getDefault()` with, for each profile parameter, the value of the **active profile** when there is one; `setActiveProfile(profile or null)`; listeners are told when `get()` changes.
2. With **no active profile** `get()` equals `getDefault()` for every parameter (invariant 1): every figure of `specs/010-pre-run-check/benchmark/after/` is unchanged.
3. A profile never changes a non-profile parameter (the engine, the tuner, the passages, the timing window, the flames, the gain): `get()` takes those from the default (invariant 2).
4. **Every writer uses `getDefault()`**: writing any setting while a profile is active leaves the default's profile parameters exactly as they were (invariant 3, one test over every caller found by a grep of `update(`).
5. `ProfileLibrary.save(profile)` writes the library file only; a cancelled, refused or failed calibration never calls it (invariant 4); an unreadable entry gives an orange device and no error (invariant 5).
6. The profile of a device applies to every reader of the store (tuner window, Follow, Arcade, Wait) and nowhere else. The profile of a device is the one of its `DeviceKey`; selecting another device changes the active profile at once; the dot follows (invariant 6).
7. A profile whose sample rate differs from the capture rate does not apply (invariant 7).
8. After a restart `ProfileLibrary.load()` returns the profiles saved, equal to what was saved (invariant 8, SC-002).
9. `ProfileLibrary.remove(deviceKey)` deletes only that device's profile, saved at once; after a restart the device is orange (invariant 9).
10. **Activation reads the latest selection**: the activator takes `selection.snapshot()` under a lock and does not trust the order of the notifications (each is delivered on its own thread); a rapid A, B, A ends with A's profile. **Activation does not depend on a window**: `ProfileActivator` sets the active profile when the selection changes and at start, whether or not the preparation window is open (invariant 10). The tuner's own parameters never come from a profile.
11. The writers are `AudioRecognitionOption` (page built from `getDefault()`, `commit`, `restoreDefaults`), `FollowSettings.setWindowMillis` and the store's two `restoreDefaults`; none of them reads the effective settings to write them back (invariant 11, a source test).
