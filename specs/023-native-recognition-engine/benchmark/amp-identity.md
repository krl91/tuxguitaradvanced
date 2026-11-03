# Amp sound and recognition, native engine (T058, FR-010, SC-005)

Date: 2026-11-03. `-Dtuxguitar.recognition.engine=native`, native library built with `cargo build --release --locked`.

| Test | Amp sound | Result |
|---|---|---|
| `DatasetRecognitionIdentityTest` (spec 022 T061: the four dataset benchmarks alone, then with four amp engines playing the recordings) | off, then on | same outcome lines, 1 / 1 passed (120.9 s) |
| `RecognitionUntouchedTest` (frames with and without a monitor engine playing the same recording) | off, then on | same frames, 1 / 1 passed (18.5 s) |

Both tests play through fake duplex devices: nothing is heard. No second open failed on this system (R6): nothing to note for sheet F13.
