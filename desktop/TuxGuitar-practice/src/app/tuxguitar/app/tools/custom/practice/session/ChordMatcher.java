package app.tuxguitar.app.tools.custom.practice.session;

import java.util.ArrayList;
import java.util.List;

import app.tuxguitar.audio.engine.analysis.ChordEvidence;
import app.tuxguitar.audio.engine.analysis.SpectrumFrame;

/**
 * Score-conditioned chord check for one analysis frame (FR-079, FR-082; plan stage E), on the
 * unique partials of each required pitch — the partials no other expected or still-sounding
 * pitch shares, so the other strings cannot make a missing one look present:
 * <ul>
 * <li>a pitch with fewer than {@link Parameters#minClearPartials} unique partials cannot be
 * verified separately (a duplicate, or a pitch on a lower chord pitch's harmonic, e.g. E3 over
 * E2; spec Q1, recommended answer A) and is reported as such, never as heard;</li>
 * <li>a verifiable pitch is heard when at least {@link Parameters#minClearPartials} of its unique
 * partials, and at least {@link Parameters#minClearFraction} of them, clearly sound
 * ({@link ChordEvidence#PARTIAL_PRESENCE_RATIO}).</li>
 * </ul>
 * On the synthetic strums of research "Requirements B" every sounding pitch had at least two
 * clear unique partials and every missing one at most one. Values are provisional engineering
 * parameters, not learner settings, to be checked on real chords (T094–T095).
 */
public final class ChordMatcher {

	public static final class Parameters {
		public final int minClearPartials;
		public final double minClearFraction;
		/** Consecutive frames with every verifiable pitch heard before acceptance. */
		public final int stableHops;
		/** Time after the strum's attack within which the chord must be complete; later needs a new strum. */
		public final long windowNanos;

		public Parameters(int minClearPartials, double minClearFraction, int stableHops, long windowNanos) {
			this.minClearPartials = minClearPartials;
			this.minClearFraction = minClearFraction;
			this.stableHops = stableHops;
			this.windowNanos = windowNanos;
		}

		/** Provisional: 2 clear unique partials and a third of them; 8 hops (≈ 43 ms); 600 ms after the attack. */
		public static Parameters defaults() {
			return new Parameters(2, 1.0 / 3.0, 8, 600_000_000L);
		}
	}

	/** The check of one frame. */
	public static final class Result {
		private final int[] heard;
		private final int[] missing;
		private final int[] unverifiable;

		Result(int[] heard, int[] missing, int[] unverifiable) {
			this.heard = heard;
			this.missing = missing;
			this.unverifiable = unverifiable;
		}

		public int[] getHeard() {
			return this.heard.clone();
		}

		/** Verifiable required pitches not heard in this frame. */
		public int[] getMissing() {
			return this.missing.clone();
		}

		/** Required pitches that cannot be verified separately (FR-078, FR-082). */
		public int[] getUnverifiable() {
			return this.unverifiable.clone();
		}

		/** Every verifiable pitch is heard, and at least one pitch is verifiable. */
		public boolean isComplete() {
			return (this.missing.length == 0 && this.heard.length > 0);
		}
	}

	private final Parameters parameters;

	public ChordMatcher(Parameters parameters) {
		this.parameters = parameters;
	}

	public Parameters getParameters() {
		return this.parameters;
	}

	public Result check(SpectrumFrame spectrum, int[] required, int[] tolerated) {
		ChordEvidence evidence = ChordEvidence.evaluate(spectrum, required, tolerated);
		List<Integer> heard = new ArrayList<Integer>();
		List<Integer> missing = new ArrayList<Integer>();
		List<Integer> unverifiable = new ArrayList<Integer>();
		for( ChordEvidence.Pitch pitch : evidence.getPitches() ) {
			if( pitch.getUniquePartials() < this.parameters.minClearPartials ) {
				unverifiable.add(pitch.getMidi());
			} else if( pitch.getUniqueClearPartials() >= this.parameters.minClearPartials
					&& pitch.getUniquePresentFraction() >= this.parameters.minClearFraction ) {
				heard.add(pitch.getMidi());
			} else {
				missing.add(pitch.getMidi());
			}
		}
		return new Result(toArray(heard), toArray(missing), toArray(unverifiable));
	}

	private static int[] toArray(List<Integer> values) {
		return values.stream().mapToInt(Integer::intValue).toArray();
	}
}
