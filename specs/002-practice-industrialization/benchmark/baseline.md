# Baseline (2026-10-01)

Measured on the working tree at commit `0c3255a20` plus the uncommitted Phase 1–2 work (types, settings, test signals); none of that work changes recognition behaviour.

## Build and tests (T001)

`mvn -e clean verify -P native-modules` (macOS SWT, Temurin 21): **BUILD SUCCESS**. Tests run per Maven module with tests, in reactor order: 67, 1, 1, 3, 2, 142, 17, 142; 0 failures, 0 errors, 0 skipped. (The two 142 are the audio engine and the practice plugin; the 3 are `TestGTPTempo` of `tuxguitar-gtp`.) The benchmark tests are excluded from this run.

## MVP benchmark (T005)

Command (from `desktop/build-scripts/tuxguitar-macosx-swt-cocoa`): `mvn test -pl ../../TuxGuitar-audio-engine -am -Dgroups=benchmark -DexcludedGroups= -Dsurefire.failIfNoSpecifiedTests=false`. Result: 2 benchmark tests pass. CSV reports: `baseline-2026-10-01/` (copied from `specs/001-interactive-practice-mvp/benchmark/`, where the run writes them).

Built-in microphone clips (`builtin-mic.csv`, 22 clips): 19 reach a stable first note; the 3 others are the silence clip and the two known-issue clips (E4 open string 1, E5 fret 12 on string 1).

Time from the start of the clip's energy onset to the first stable note, over the 19 clips:

| min | median | p95 (approx., 19 clips) | max |
|---|---|---|---|
| 144 ms | **171 ms** | 288 ms | 421 ms |

This reproduces the MVP figures (median 171 ms, previously recorded max 288 ms; this run shows a 421 ms maximum on one clip). It is a **stable-note** latency on recorded single notes, not the full note-to-feedback latency of the application, which also includes the attack confirmation; task T004 adds p50/p95 reporting per clip to the benchmark.

Attack detection on the 17 reference clips at the default gate (4.5 dB): 17 expected, 17 reported, 0 extra, 0 missed.

## What this means for the targets

The provisional SC-003 target (median ≤ 150 ms) is below the current median of 171 ms for stable-note detection alone, on this microphone and these clips. It remains a stretch to be earned by the Phase C levers, as plan.md R3 says; no target is changed yet.
