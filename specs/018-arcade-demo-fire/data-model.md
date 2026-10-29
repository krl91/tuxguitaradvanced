# Data Model: spec 018

## FlameState (extended, immutable)
- `setting`, `level` (0..1), `flameClockNanos`, `motion` — unchanged (spec 009).
- `bursts`: list of Burst, the right notes of the last second; empty by default (the existing constructor keeps working).

## Burst
- `clockNanos`: the flame clock when the note was judged right.
- `streak`: the streak at that moment (≥ 1).
Rule: kept while `flameClockNanos − clockNanos < 1 s`.

## FireField (pure)
Inputs: viewport, tier, FlameState. Output: for each cell (col, row) a heat step 0..15.
- cell size: R3; height(col): level × factor × 0.55 × H × weight(col / cols); heat 0 above it; R2.

## FireColours (pure)
heat step → (colour, opacity); step 0 → none; opacity ≤ 0.60 (× 0.6 discreet); R4.

## Spark
From a Burst and an index: start x near a corner of the fire, rising path, age 0..1 s, opacity fading to 0; one cell square.
