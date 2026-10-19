# Data model: per-device recognition calibration (spec 013)

## DeviceKey
`deviceName + "#" + ("mono" | channelIndex) + "#" + ordinal`, where `ordinal` is the rank of the input among the catalog's inputs with the same name and channel (0 for a single device). Rule: two devices never share a key at a given time; a device with two channels has two keys. **Not the descriptor's `getId()`**: it starts with the position of the mixer in the system's list, which may change between sessions (T003, research R3).

## RecognitionProfile
The values of the **profile parameters** only (`NOTE_ACCEPT`, `NOTE_FRET_SHARP`, `NOTE_CONFIDENCE`, `NOTE_STABILITY`, `NOTE_ATTACK`, `CHORD_EVIDENCE`, `CHORD_WINDOW`, `CHORD_MIN_PARTIALS`, `CHORD_CONFIRM`, `ATTACK_MIN_LEVEL`, `INPUT_LEVEL_MIN`), each within its bounds, plus: the **device key**, the **sample rate** of the recording, the **date**, the **recognition rate found** (events recognised, notes, chords, total) and the **rate of the default on the same recording**, the **front-end state** at recording time (gain, noise reduction: stored and shown, not enforced), the **origin** (`SEARCHED` or `DEFAULT_CHOSEN`) and the layout version. Rule: a profile applies only when the capture rate equals its rate or is unknown.

## ProfileState (the dot)
`NONE` (grey: no input selected; the *Calibrate* button disabled), then:
`ORANGE` (no profile, or the default chosen, or the profile does not apply at the current rate), `GREEN` (profile searched, total rate >= 60 %), `RED` (profile searched and kept, total rate < 60 %). A pure function of the profile and the current rate. A different noise-reduction state or gain from the profile's does not change the state: it adds a **warning sentence** next to the dot.

## ProfileLibrary
A map device key to profile, saved in its own file. Rules: unreadable entries are skipped (orange); `save` writes the whole library; `remove(deviceKey)` deletes only that device's profile and saves at once; nothing is written by a cancelled or failed calibration.

## CalibrationPiece
The shipped score `basic_test.tg` read as a practice sequence: an ordered list of **events** (a note, or a chord with its notes). Rule: the number of events is the number of sounds the listening mode expects.

## CalibrationRecording
The contiguous hops of the live analysis, in memory (never a file), with the sample rate and the onsets found while listening. Rule: dropped when the calibration ends or is cancelled.

## Candidate, SearchResult
A candidate is a set of values of the profile parameters; the result lists the best one, the rate and the objective of the default and of the best, and **one line per parameter** (default, found, what it changed). Rules: the default is always the first candidate; leaving it needs a gain of at least one event; the search is deterministic.

## CalibrationFlow (state machine)
`OFFER`, `TUNING`, `TUNER_OPEN`, `ADVICE`, `PIECE`, `LISTENING`, `QUALITY`, `REFUSED`, `ANALYSING`, `RESULT`, `CHOICE`, `DONE`, `CANCELLED`, with a total table of events (see `contracts/calibration-flow.md`).

## Result messages
Two, with the same three choices: *not better than the default* ("the default settings are the most optimal") and *better than the default but under 60 %* ("better than the default, but only N %"). The button *Use the default configuration* reads "(replaces the current calibration)" when the device already has a profile.

## SearchReport (what the result screen shows)
The rates with the default and with the profile: **total, notes, chords**, each as events accepted over events of the piece, and the sentence "saved for this device". The per-parameter list (default, found) is **not** on the screen; it goes to the technical report.

## ProfileActivator
Listens to the current input selection (each notification arrives on its own thread, possibly out of order), so it ignores the event's content: under one lock it reads the **latest** `selection.snapshot()`, looks up the profile of that device in the library and calls `RecognitionSettingsStore.setActiveProfile` (or `null`); also reads the current selection when created. Lives with the engine, not with the Input tab.

## DiagnosticSink and DiagnosticCopy (FR-020)
`DiagnosticSink.write(float[] samples, int sampleRate)` returns the path written or null. `DiagnosticCopy` (window package) is the only implementation: 16-bit mono WAV, folder `tuxguitar-calibration-takes` of the user's home, name `calibration-<yyyy-MM-dd-HHmmss>.wav`. The `Snapshot` has `diagnosticPath` (null when none was asked for or it failed) and `diagnosticFailed`. Nothing of it is ever read back.

## TabDiagram.Mark (FR-021)
One note of the sound shown: a string 1 (thinnest) to 6 and a fret 0 or more (0 is open). A sound is the list of the marks of one event of the shipped piece (several for a chord). The layout of a picture is a function of its size and its marks: six horizontal lines, each cut in two around its number.
