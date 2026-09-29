package app.tuxguitar.app.tools.custom.practice.session;

/**
 * Feedback for the current expected chord (FR-081, FR-082). Immutable.
 */
public final class ChordFeedback {

	public enum Kind {
		/** No usable playing, or the chord is still being listened to after a strum. */
		WAITING,
		/** Signal present but too weak, clipping or unclear to judge. */
		UNCERTAIN,
		/** The expected pitches are heard but without a new strum (FR-086). */
		STRUM_AGAIN,
		/** A strum whose window ended with verifiable pitches missing: a confirmed wrong chord. */
		INCOMPLETE,
		CORRECT
	}

	static final ChordFeedback WAITING = new ChordFeedback(Kind.WAITING, new int[0], new int[0]);
	static final ChordFeedback UNCERTAIN = new ChordFeedback(Kind.UNCERTAIN, new int[0], new int[0]);

	private final Kind kind;
	private final int[] missing;
	private final int[] unverifiable;

	ChordFeedback(Kind kind, int[] missing, int[] unverifiable) {
		this.kind = kind;
		this.missing = missing.clone();
		this.unverifiable = unverifiable.clone();
	}

	public Kind getKind() {
		return this.kind;
	}

	/** For INCOMPLETE: the verifiable pitches that were not heard. */
	public int[] getMissing() {
		return this.missing.clone();
	}

	/** Pitches of the chord that cannot be verified separately (duplicates, harmonics of a lower pitch). */
	public int[] getUnverifiable() {
		return this.unverifiable.clone();
	}
}
