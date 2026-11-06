# Contract: the native parameter interface (spec 026, FR-004, FR-009)

Extends spec 023's contract §1 (`NativeRecognitionLibrary`, `jni_recognition.rs`). Approved by the owner on 2026-11-06 (Q3 A).

## Layout

- Every parameter array between Java and the crate is a `double[]`: `open`, `configure`, `analyze`, `frontEnd`, `strumClipOffline`, `openAnalysis`.
- `NativeParameters` names each index (constants `0 … LENGTH − 1`); `CRATE/analysis/mod.rs` has the same names and indices. Indices 0–20 keep their meaning and order of today. New indices follow, one per parameter an engine stage reads (onset fast windows, pitch, polyphonic, attack, chord evidence, strum, front end, noise gate, subtraction).
- Indices 0–20 are written as `(double) (float) value`, the precision they have today (research R5). New indices carry the setting's `double` unchanged.
- Counts and hops are whole numbers in the array; the crate rounds them as today (`round() as i32`).

## Version check

- New function `int parameterLayout()` (no handle): returns the crate's `LENGTH`.
- Java calls it once after loading the library. If the symbol is missing (`UnsatisfiedLinkError`) or the value differs from `NativeParameters.LENGTH`, the library is not used: the failure reason is `LIBRARY_MISSING`, with the detail "native library parameter layout N, expected M" (or "too old"); the run falls back to the Java engine with the existing notice. No new learner-visible string.
- `Parameters::from_slice` refuses an array whose length is not exactly `LENGTH` (today: shorter only); `open` and `openAnalysis` then fail with the existing error.

## Real time

- `configure` replaces the values through the existing lock-free mailbox (`CRATE/recognition.rs`); it never changes a buffer size. Values that size a buffer are bounded; the crate allocates for the bound at `open` (research R6).
- No validation in the crate: values come validated from `RecognitionSettings` (as today).

## Parity

The Java and native stages read the same parameter for the same value (FR-004). The parity tests of spec 023 run with the defaults and with one non-default value per family (SC-001).
