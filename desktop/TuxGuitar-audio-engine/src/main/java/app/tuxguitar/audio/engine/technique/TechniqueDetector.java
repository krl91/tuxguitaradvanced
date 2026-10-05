package app.tuxguitar.audio.engine.technique;

/** A stateless detector of one technique over a finished (or growing) note segment. */
public interface TechniqueDetector {

	DetectableTechnique technique();

	/** Never null; says "uncertain" when the segment is too short or too unvoiced to tell. */
	TechniqueEvidence evaluate(NoteSegment segment);
}
