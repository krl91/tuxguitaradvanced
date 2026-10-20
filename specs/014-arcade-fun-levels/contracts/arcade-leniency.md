# Contract: the Arcade leniency (FR-001 to FR-005, FR-011, FR-014)

1. **Scope**: applied only when an **Arcade** run is prepared. Follow, Wait, the tuner window and the calibration read the store as before; a test shows their parameters equal with every position.
2. **One-way**: for each relaxed parameter the Arcade value is the more lenient of the effective value (default plus device profile) and the position's value; a stricter value is never produced.
3. **LIKE_PRACTICE** gives the same settings and the same window: with level 5, every verdict, score and result equals the base.
4. **Window**: `min(200, round(chosen × factor))` ms, the player's window of spec 012 staying the base; the existing range 40 to 200 is not changed.
5. **Within ranges**: every value stays inside the range of its parameter; no value is outside what the settings accept.
6. **Deterministic**: no randomness; the same performance, position and level give the same result.
7. **No silence accepted**: the leniency only relaxes thresholds on a sound that was heard; the attack detection level (`ATTACK_MIN_LEVEL`) and the input level minimum are **not** relaxed.
8. **The values** of the two permissive positions are fixed by the measure of research R6 (SC-001, SC-002) and written in this contract once measured.
