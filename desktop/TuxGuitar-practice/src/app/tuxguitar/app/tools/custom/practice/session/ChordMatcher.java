package app.tuxguitar.app.tools.custom.practice.session;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.analysis.ChordEvidence;
import app.tuxguitar.audio.engine.analysis.SpectrumFrame;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;

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

		/** The chord rule: {@link RecognitionParameters#COMPLETENESS_ALL} (every string on its own) or {@link RecognitionParameters#COMPLETENESS_PROFILE}. */
		public final String rule;
		/** Profile rule: the match from which a chord is complete. */
		public final double profileMatch;
		/** Profile rule: the share of the strongest required name under which a name is weak. */
		public final double profileNames;
		/** Profile rule: the highest pitch summed. */
		public final int profileTop;
		/** Profile rule: the time summed from the strum before a chord can be complete. */
		public final long profileSettleNanos;
		/** Whether a chord may be accepted on its attack evidence (guarded by the weak names under the profile rule). */
		public final boolean attack;

		/** The rule of every string on its own (spec 023), the attack path on. */
		public Parameters(int minClearPartials, double minClearFraction, int stableHops, long windowNanos) {
			this(minClearPartials, minClearFraction, stableHops, windowNanos, RecognitionParameters.COMPLETENESS_ALL, 0.79, 0.40, 84, 200_000_000L, true);
		}

		public Parameters(int minClearPartials, double minClearFraction, int stableHops, long windowNanos, String rule, double profileMatch, double profileNames,
				int profileTop, long profileSettleNanos, boolean attack) {
			this.minClearPartials = minClearPartials;
			this.minClearFraction = minClearFraction;
			this.stableHops = stableHops;
			this.windowNanos = windowNanos;
			this.rule = rule;
			this.profileMatch = profileMatch;
			this.profileNames = profileNames;
			this.profileTop = profileTop;
			this.profileSettleNanos = profileSettleNanos;
			this.attack = attack;
		}

		/** True when chords are judged by their pitch-class profile. */
		public boolean usesProfile() {
			return RecognitionParameters.COMPLETENESS_PROFILE.equals(this.rule);
		}

		/** Provisional: 2 clear unique partials and a third of them; 8 hops (≈ 43 ms); 600 ms after the attack. */
		public static Parameters defaults() {
			return new Parameters(2, 1.0 / 3.0, 8, 600_000_000L);
		}
	}

	/** The check of one frame. */
	public static final class Result {
		private static final int[] NONE = new int[0];

		private final int[] heard;
		private final int[] missing;
		private final int[] unverifiable;
		/** Profile rule, not complete: the profile and how many of its weakest names are missing (read when asked, on a refusal). */
		private final ChordProfile profile;
		private final int weak;

		Result(int[] heard, int[] missing, int[] unverifiable) {
			this.heard = heard;
			this.missing = missing;
			this.unverifiable = unverifiable;
			this.profile = null;
			this.weak = 0;
		}

		private Result(ChordProfile profile, int weak) {
			this.heard = NONE;
			this.missing = null;
			this.unverifiable = NONE;
			this.profile = profile;
			this.weak = weak;
		}

		public int[] getHeard() {
			return this.heard.clone();
		}

		/**
		 * Verifiable required pitches not heard in this frame. Under the profile rule, the required pitches of the weakest names (at least one), as the
		 * profile is when this is asked.
		 */
		public int[] getMissing() {
			return (this.profile != null ? this.profile.weakest(Math.max(1, this.weak)) : this.missing.clone());
		}

		/** Required pitches that cannot be verified separately (FR-078, FR-082). */
		public int[] getUnverifiable() {
			return this.unverifiable.clone();
		}

		/** Every verifiable pitch is heard, and at least one pitch is verifiable. */
		public boolean isComplete() {
			return (this.profile == null && this.missing.length == 0 && this.heard.length > 0);
		}
	}

	private final Parameters parameters;

	public ChordMatcher(Parameters parameters) {
		this.parameters = parameters;
	}

	public Parameters getParameters() {
		return this.parameters;
	}

	/**
	 * The profile rule (contracts/chord-profile-rule.md): complete when the match reaches {@link Parameters#profileMatch}, at most one required name
	 * is weak and {@link Parameters#profileSettleNanos} have been summed.
	 */
	public Result check(ChordProfile profile, long nowNanos) {
		int weak = profile.weakNames(this.parameters.profileNames);
		if( weak <= 1 && profile.match() >= this.parameters.profileMatch && profile.summedNanos(nowNanos) >= this.parameters.profileSettleNanos ) {
			return complete(profile);
		}
		return new Result(profile, weak);
	}

	/** Whether the attack path may accept the chord: never when it is off; under the profile rule, only with at least all but one required name (research R6). */
	public boolean attackMayAccept(ChordProfile profile) {
		return this.parameters.attack && (!this.parameters.usesProfile() || profile.weakNames(this.parameters.profileNames) <= 1);
	}

	private Result complete(ChordProfile profile) {
		return new Result(profile.requiredPitches(), Result.NONE, Result.NONE);
	}

	public Result check(SpectrumFrame spectrum, int[] required, int[] tolerated) {
		return judge(ChordEvidence.evaluate(spectrum, required, tolerated));
	}

	/**
	 * The check of one frame: with the native engine the frame carries the evidence of the expected chord, which is used when it is that of the chord
	 * matched (the same distinct pitches); otherwise it is computed from the frame's spectrum, as with the Java engine (spec 023, contract §4). The
	 * rule that follows is the same.
	 */
	public Result check(AnalysisFrame frame, int[] required, int[] tolerated) {
		ChordEvidence carried = frame.getChordEvidence();
		if( carried != null && isFor(carried, required) ) {
			return judge(carried);
		}
		return check(frame.getSpectrum(), required, tolerated);
	}

	private static boolean isFor(ChordEvidence evidence, int[] required) {
		int[] distinct = Arrays.stream(required).distinct().sorted().toArray();
		List<ChordEvidence.Pitch> pitches = evidence.getPitches();
		if( pitches.size() != distinct.length ) {
			return false;
		}
		for( int i = 0; i < distinct.length; i++ ) {
			if( pitches.get(i).getMidi() != distinct[i] ) {
				return false;
			}
		}
		return true;
	}

	private Result judge(ChordEvidence evidence) {
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
