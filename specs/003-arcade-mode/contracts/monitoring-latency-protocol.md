# Contract: monitoring latency protocol (increment E, spike first)

**What is measured**: the delay from a sound reaching the **input** of the audio interface to the same sound leaving its **output**, through the application's monitoring path. It is a gate: monitoring is allowed only when the **worst credible value** is at most **22 ms** (FR-041, FR-047).

**What it is not**: the 64 to 66 ms of the rhythmic calibration of specification 002 (an acoustic loop from the speakers to the built-in microphone, used to correct the judging of notes, research R6c of 002). Different quantity, different path, different use. The two are never added, compared or substituted.

## 1. Deterministic accounting (software budget)

Named terms, each measured or read, none estimated from a nominal buffer size:

| Term | How obtained |
|---|---|
| Capture block | frames per read of the `TargetDataLine` / sample rate (today 1024 / 48000 = 21.3 ms; the spike tries 128, 256, 512) |
| Capture queue | frames waiting in the line when read (from `available()` at each read) |
| Processing | time between the read returning and the write call, measured per block (median, p95, max) |
| Output queue | frames queued in the `SourceDataLine` at each write (buffer size minus `available()`) / sample rate |
| Device terms | what the OS/driver reports (not available through Java Sound; recorded as unknown, and covered by the loopback) |

The accounting gives a lower bound and finds where the budget goes; it **cannot** validate the claim (FR-047).

## 2. Physical wired loopback (reference)

Needs an audio interface with at least two inputs and two outputs, and two short cables.

1. Output A -> Input 1 (the test source). Output B <- the monitoring output. Output B -> Input 2.
2. A test thread writes a click (an impulse with a sharp, known shape) to Output A and logs the sample index at which it was handed to the device.
3. The application monitors Input 1 to Output B.
4. A recorder captures Input 1 and Input 2 **on the same device clock**; the click is found in both by cross-correlation.
5. `t(Input 2) - t(Input 1)` is the input-to-output latency of the monitoring path (it contains the interface's input and output latency once, as it should, plus the cable, negligible).
6. At least 50 trials per configuration at random intervals; report **median, 95th percentile, maximum**; at least two sample rates and the buffer sizes tried.

Pass for a configuration: the **maximum** (or, for a stable distribution, the upper bound established with the observed jitter) is <= 22 ms. Passing on an average while any repeated trial exceeds 22 ms is a failure.

## 3. Acoustic loopback (indicative)

For the built-in microphone and speakers or an independent microphone, when no interface is available. A reference click is emitted from a small speaker at a measured distance `d` from the microphone; the monitoring path takes the microphone to the speakers; the recording microphone hears both the click and its monitored copy. Report the delay between them, **subtract `d / 343 m/s` for each acoustic leg** (or bound it), state `d`, the room, the number of trials, and treat reflections as uncertainty (+- the observed spread). This result is reported **separately** and never used to claim the gate for a wired configuration.

## 4. Drift of the output timeline (FR-036)

Run the MIDI backing and the monitoring for at least 30 minutes; log the difference between the two lines' frame counters against the wall clock; report the drift rate. A drift that would desynchronise the backing from the monitored guitar beyond 5 ms over a song is recorded as a finding.

## 5. Report

A versioned file under `specs/003-arcade-mode/benchmark/`: date, machine, OS, JDK, interface, sample rate, buffer sizes, method (1, 2, 3), trial count, median, p95, maximum, accounting table, uncertainty, and the decision (offer monitoring on this configuration: yes/no, with the reason). A "no" is a valid and complete outcome.

## 6. Safety tests of the path (if it is built)

Click-free on enable/disable and on volume change (SC-006); the analysis reads the original captured samples, never the monitored ones (FR-033); no audio is recorded or stored (FR-034); a feedback warning for microphone + speakers with a low starting volume (FR-043); the lost device does not hang (FR-044).
