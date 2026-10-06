# Contract: technique detection (engine + practice, Phases D and F)

```java
// engine: app.tuxguitar.audio.engine.technique
interface TechniqueDetector {
    Technique technique();
    /** Streaming: called per hop with the features of the current note segment; preallocated. */
    void push(TechniqueFeatures f);
    /** Verdict for the segment that just ended or is being judged; never null. */
    TechniqueEvidence evaluate(TechniqueTarget target, NoteSegment segment);
    void reset();
}

// practice: session.TechniqueJudge
TechniqueResult judge(TechniqueTarget target, List<TechniqueEvidence> evidence, TechniqueSettings settings);
```

## Verdict rules (testable)

1. Detectors emit only `DETECTED`, `NOT_DETECTED`, `UNCERTAIN`. A band between the "clearly plain" and "clearly technique" thresholds yields `UNCERTAIN` (the widened tolerance the owner asked for, FR-030).
2. The judge returns `NOT_VERIFIABLE` when: no detector is registered for the technique; detection is switched off (FR-032); or the technique's `verified` flag is false. The `verified` flags live in one table (`TechniqueCatalog`) with the benchmark result that justifies each.
3. A technique verdict never affects the pitch verdict or progression (FR-030).
4. Legato (`HAMMER_ON`, `PULL_OFF`, `SLIDE`): the destination pitch is accepted as the expected note from a stable pitch change without a confirmed attack (research R3, L3); the verdict reflects whether an attack was heard.
5. Bend: pitch acceptance follows the `PitchPath` within a widened cents tolerance; result detail gives reached/target semitones.
6. Vibrato: does not reject the static pitch; depth/rate reported.
7. Tremolo picking: a single event; attacks counted inside it.
8. Thresholds are parameters (defaults in `RecognitionParameters`, `ADVANCED` section), never product constants.
9. Every detector has synthetic tests (plain versus technique versions of the same Karplus-Strong pluck) and a benchmark entry before its `verified` flag is set.

## Learner-facing feedback

Per event: pitch line (unchanged) + one technique chip per written technique: detected / not detected / uncertain / not verifiable, each with a short localized explanation (`practice.technique.<name>.<verdict>`). The end-of-passage summary counts verdicts per technique.
