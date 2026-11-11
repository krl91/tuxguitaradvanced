# Contract: the re-attack expected by the score

Applies to `FollowSession` (Follow and Arcade) and to `PracticeSession` (Wait), with both engines (research R3, R4). Items 4 and 5 concern Follow and Arcade only (Wait has one awaited event).

1. An event has an **expected repeat** on pitch `p` when `p` is one of its pitches and `p` already sounds at its target according to the full written sequence (in Arcade, before the level filter): the previous written event holds `p`, or a chord whose duration covers the target holds it.
2. For such an event, while its window is open and no attack is matched to it, each frame's band level of `p` (and of `p + 12` when `reattack.octave` is on, the larger of the two) is compared with the minimum of the same band over the `reattack.before` frames before. A rise of at least `reattack.rise` dB is a **re-attack** of `p`, at the time of the frame where the rise began.
3. A re-attack is handled as a detector onset: its candidates are the open events whose window holds its compensated time and that expect a repeat of `p`; the event is judged on the frames after it by its own existing rule: a note by the pitch rule (`NOTE_ACCEPT`, `NOTE_FRET_SHARP`), a chord by the chord rule of spec 024.
4. An attack (onset or re-attack) inside the windows of several open events of the same pitch goes to the event with the nearest target; ties go to the earlier event.
5. A detector onset and a re-attack within `ONSET_REFRACTORY` hops of each other are one attack (the onset's time is kept).
6. With `reattack.enabled` off, every verdict is that of before this spec: no re-attack, and item 4 is off (the first open event takes the attack, as today).
6a. A rise of the band of `p` that comes with a rise of most other bands in the same frame (a scratch, a click) is not a re-attack; the share of bands is `reattack.spread` (data-model.md), its default set by the probe.
7. Never: a re-attack for an event that does not expect a repeat; a re-attack from a band that did not rise (a held note, FR-002); a change of a verdict already given.
8. Nothing allocated per frame; nothing on the audio threads (constitution VI).
