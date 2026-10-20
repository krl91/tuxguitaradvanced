# Contract: the level filter (FR-006 to FR-009)

`ArcadeLevelFilter.apply(PracticeSequence full, ArcadeLevel level) -> PracticeSequence`

1. **Pure and deterministic**: the same sequence and level give the same events, always.
2. **Counts**: for a kind (notes, chords) with *n* events and share *s* (not 0): *k* = max(1, round(*s* n*)) kept; level 5 keeps all; share 0 keeps none of that kind.
3. **Even spread**: the ranks kept are `floor((i + 0.5) n / k)`; no gap between two kept ranks differs from another by more than one.
4. **A chord is whole**: a kept chord keeps every note of its event; a removed one is removed whole.
5. **Never empty**: if the piece has at least one sound, at least one is kept at every level; at level 1 a piece with only chords keeps one chord.
6. **Order, times and play indices are those of the song**: the kept events are the same objects as in `full`.
7. **Start**: the new start index is the first kept event at or after the original start index.
8. **AUTO_PASS** events are kept as they are (they are not sounds and are not judged).
9. **A removed legato origin**: a kept note whose origin was removed is judged as a plain note (as with `startingAt`).
10. **Level 5** returns a sequence equal to `full` event for event.

The run, the scene, the score and the result use the returned sequence only; a sound not in it is neither shown, expected nor judged.
