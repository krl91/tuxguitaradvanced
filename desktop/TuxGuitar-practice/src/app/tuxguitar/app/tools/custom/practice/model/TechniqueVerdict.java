package app.tuxguitar.app.tools.custom.practice.model;

/**
 * What practice says about one written technique (spec 002, FR-020, FR-029). A detector may only
 * produce the first three; {@link #NOT_VERIFIABLE} is set when no detector exists, when technique
 * detection is switched off, or when no benchmark justifies "verified" for that technique.
 */
public enum TechniqueVerdict {
	DETECTED,
	NOT_DETECTED,
	UNCERTAIN,
	NOT_VERIFIABLE;

	/** True for the verdicts a detector is allowed to produce. */
	public boolean isDetectorVerdict() {
		return this != NOT_VERIFIABLE;
	}
}
