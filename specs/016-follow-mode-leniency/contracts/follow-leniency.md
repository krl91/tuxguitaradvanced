# Contract: the pitch permissiveness of Follow (FR-005, FR-006, SC-003, SC-004)

`FollowLeniency.apply(position, effective)` returns the recognition settings of a Follow run, **one way** (for each row: the more lenient of the effective value and the position's), like `ArcadeLeniency.apply`. `STRICT` returns `effective` itself.

| Row (existing parameter) | Strict | Permissive | Very permissive | Direction |
|---|---|---|---|---|
| `NOTE_ACCEPT` (cents) | effective | *measured* (≤ 45) | *measured* (≤ 45) | higher is more lenient |
| `NOTE_CONFIDENCE` | effective | *measured* | *measured* | lower |
| `NOTE_STABILITY` (ms) | effective | *measured* | *measured* | lower |
| `CHORD_EVIDENCE` | effective | *measured* | *measured* | lower |
| `CHORD_MIN_PARTIALS` | effective | *measured* | *measured* | lower |
| `CHORD_CONFIRM` (ms) | effective | *measured* | *measured* | lower |

*Measured*: chosen during implementation as the most lenient values for which, on the 98 negative tests of the reference recording, **at most 4** are accepted at Permissive (SC-004), no right note of the reference recording is lost, and (once the owner's record exists) the share of SC-003 is reached; the chosen values and the figures of each position are written into research.md before the values are committed. Very permissive is reported with its figure (no limit), as Arcade's.

Invariants (tested): the semitone is never accepted (`NOTE_ACCEPT` stays ≤ 45 cents, below the 50 cents halfway); silence is never accepted (no level threshold is a row); `STRICT` gives byte-equal settings; a device profile (spec 013) applies first and the position on top; Arcade's leniency, Wait and the tuner read none of this.

Persisted: its own key `practice.follow.leniency` (Arcade's is `arcade.leniency`, `session/ArcadeChoices.java`), `PERMISSIVE` for a missing or unknown value; applies to the next run.
