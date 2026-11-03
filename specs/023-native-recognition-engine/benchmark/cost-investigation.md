# Per-hop cost of the live native loop (T057, research R9): what was wrong and the fix

Date: 2026-11-03. Bug protocol: reproducing measurement first, 2 attempts of 3 used.

## Symptom

`NativeRecognitionEnduranceBenchmarkTest` (10 minutes, spectrum on, 9 candidates): 0 dropped blocks, but one hop's analysis cost a median of
1.11–1.13 ms and a 99th percentile of 1.19–1.21 ms, against a limit of 0.58 ms (44.1 kHz) or 0.53 ms (48 kHz). It failed four times, with the
machine busy or idle, and with the low-power mode on or off.

## Measurements

| Measurement | Median / 99th percentile (ms) |
|---|---|
| Offline, `pipeline::cost` (sine and room noise) | — / 0.13–0.18 |
| Paced loop without a device, `recognition::live_cost`, promoted | 0.19 / 0.28, later 0.35 / 1.18 |
| Default input from Rust, `recognition::device_cost`, 10 minutes | 1.12 / 1.20 (windows of 10 s between 0.39 and 1.13 of median) |
| Java, 30 s | 0.18 / 0.49 |

The offline cost stayed fast while the live loop was slow: the analysis was not slower, the thread was. A thread that works a fifth of a
millisecond every few milliseconds on an idle machine is left on an efficiency core at its lowest frequency (about six times slower); the
paced test without a device kept the cores busy with its feeder, which hid it at first.

## Attempts

1. The user-interactive quality of service before the time-constraint policy: the paced test became fast (0.12 / 0.17–0.20), the device
   loop and the Java endurance did not (1.12 / 1.19). Removed.
2. The recognition thread joins the input device's audio workgroup (`kAudioDevicePropertyIOThreadOSWorkgroup`, `os_workgroup_join`, after
   its promotion; `src/workgroup.rs`, from the JNI open): the system schedules it with CoreAudio's I/O thread. Device loop from Rust, 2
   minutes: 0.162 / 0.188; Java endurance, 10 minutes, low-power mode on: **0.162 / 0.183**, 0 dropped blocks (`cost.md`, `endurance.md`).

Linux and Windows have no workgroup: nothing is joined there (the thread keeps its real-time policy).
