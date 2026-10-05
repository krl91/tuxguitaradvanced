package app.tuxguitar.app.tools.custom.practice.session;

import app.tuxguitar.audio.engine.analysis.AttackEvidence;
import app.tuxguitar.audio.engine.pitch.TolerancePolicy;

/**
 * Decides, from the attack evidence of successive hops, that the expected notes were just played
 * (spec 004): they must explain at least {@link Parameters#minShare} of what the attack added to the
 * sound, the strongest of them at least {@link Parameters#minPeakRatio} of the strongest pitch of all,
 * every note of a chord at least {@link Parameters#minBalance} of the strongest note of that chord,
 * and this for {@link Parameters#hops} hops in a row. Evidence that still contains the attack of a
 * note accepted (or of a start) is ignored for {@link Parameters#rearmNanos}: a note that rings
 * must never be taken for the next one. Pure logic, no audio.
 */
public final class PolyphonicAcceptance {

	public static final class Parameters {
		public final double minShare;
		public final double minBalance;
		public final double minPeakRatio;
		public final int hops;
		/** Each expected note must be at least this many times as strong as its strongest semitone or whole-tone neighbour (0: not checked). */
		public final double neighbourRatio;
		public final double minAttackStrength;
		public final long rearmNanos;

		public Parameters(double minShare, double minBalance, double minPeakRatio, int hops, double minAttackStrength, long rearmNanos) {
			this(minShare, minBalance, minPeakRatio, hops, 0.0, minAttackStrength, rearmNanos);
		}

		public Parameters(double minShare, double minBalance, double minPeakRatio, int hops, double neighbourRatio, double minAttackStrength, long rearmNanos) {
			if( minShare < 0 || minShare > 1 || minBalance < 0 || minBalance > 1 || minPeakRatio < 0 || minPeakRatio > 1 || hops < 1
					|| neighbourRatio < 0 || minAttackStrength < 0 || rearmNanos < 0 ) {
				throw new IllegalArgumentException("invalid polyphonic acceptance parameters");
			}
			this.minShare = minShare;
			this.minBalance = minBalance;
			this.minPeakRatio = minPeakRatio;
			this.hops = hops;
			this.neighbourRatio = neighbourRatio;
			this.minAttackStrength = minAttackStrength;
			this.rearmNanos = rearmNanos;
		}

		/** Values chosen on the replay of the reference recording (spec 004 research R8). */
		public static Parameters defaults() {
			return new Parameters(0.30, 0.0, 0.7, 6, 0.0, 0.0, 220_000_000L);
		}
	}

	private static final int[] NEIGHBOURS = { -2, -1, 1, 2 };
	/** The measured offset of the new partials is noisy at low frequencies (about 35 cents on a low E): it only has to rule out the semitone neighbour (100 cents), not to judge the tuning. */
	private static final double OFFSET_MARGIN = 15.0;
	private static final int DECAY = 1;

	private final Parameters parameters;
	/** The first part of a pluck is a broadband thump and body resonances: pitch evidence is read only after it. */
	private static final long GUARD_NANOS = 60L * 1_000_000L;
	/** Below this the growth is room noise, not a note (amplitude as a share of a full-scale sine; quiet real notes measure 0.02 and above). */
	private static final double MIN_TOTAL = 0.01;
	private static final int FLOOR_HOPS = 10;
	private static final double SILENCE_STRENGTH = 0.6;
	private static final double MIN_ONSET_TOTAL = 0.002;
	private final double[] recentTotals = new double[FLOOR_HOPS];
	private int recentCount;
	private boolean inAttack;
	private double attackPeak;
	private double attackStrengthPeak;
	private long onsetNanos;
	private long ignoreUntilNanos = Long.MIN_VALUE;
	private boolean rearmAtNextFrame;
	private int run;

	public PolyphonicAcceptance(Parameters parameters) {
		this.parameters = parameters;
	}

	public Parameters getParameters() {
		return this.parameters;
	}

	/** Evidence older than the attack of what has just been accepted, started or skipped must not count. */
	public void rearm(long nowNanos) {
		this.ignoreUntilNanos = nowNanos + this.parameters.rearmNanos;
		this.rearmAtNextFrame = false;
		this.run = 0;
		this.inAttack = false;
		this.recentCount = 0;
	}

	/** Like {@link #rearm(long)} from the time of the next frame offered (a start, resume or skip: the time is not known yet). */
	public void rearmAtNextFrame() {
		this.rearmAtNextFrame = true;
		this.run = 0;
	}

	/** @return true when the expected pitches have now been heard for long enough, without a pitch check */
	public boolean offer(AttackEvidence evidence, long timestampNanos, int[] expected) {
		return offer(evidence, timestampNanos, expected, null, null);
	}

	/**
	 * @param frets the fret of each expected pitch (for the sharp allowance), or null
	 * @param tolerance when given, every expected pitch must also sound within it: the partials that
	 *        just appeared must lie within the accepted range of the exact positions (this is what
	 *        separates a note from its semitone neighbour)
	 * @return true when the expected pitches have now been heard for long enough
	 */
	public boolean offer(AttackEvidence evidence, long timestampNanos, int[] expected, int[] frets, TolerancePolicy tolerance) {
		if( this.rearmAtNextFrame ) {
			this.ignoreUntilNanos = timestampNanos + this.parameters.rearmNanos;
			this.rearmAtNextFrame = false;
		}
		if( evidence == null || expected == null || expected.length == 0 || timestampNanos < this.ignoreUntilNanos
				|| evidence.getAttackStrength() < this.parameters.minAttackStrength || evidence.getTotal() < MIN_TOTAL ) {
			this.run = 0;
			return false;
		}
		double sum = 0.0;
		double strongest = 0.0;
		double weakest = Double.MAX_VALUE;
		for( int pitch : expected ) {
			double amplitude = evidence.amplitudeOf(pitch);
			sum += amplitude;
			strongest = Math.max(strongest, amplitude);
			weakest = Math.min(weakest, amplitude);
		}
		// where the sound begins growing: the attack's own onset, from the quiet level before it
		double floor = Double.MAX_VALUE;
		for( int i = 0 ; i < Math.min(this.recentCount, FLOOR_HOPS) ; i ++ ) {
			floor = Math.min(floor, this.recentTotals[i]);
		}
		if( floor == Double.MAX_VALUE ) {
			floor = 0.0;
		}
		double total = evidence.getTotal();
		if( !this.inAttack && total > Math.max(MIN_ONSET_TOTAL, 3.0 * floor) ) {
			this.inAttack = true;
			this.onsetNanos = timestampNanos;
			this.attackPeak = total;
			this.attackStrengthPeak = 0.0;
		} else if( this.inAttack ) {
			this.attackPeak = Math.max(this.attackPeak, total);
			if( total < 0.25 * this.attackPeak || total < MIN_ONSET_TOTAL ) {
				this.inAttack = false;
			}
		}
		this.recentTotals[this.recentCount % FLOOR_HOPS] = total;
		this.recentCount ++;
		if( this.inAttack ) {
			this.attackStrengthPeak = Math.max(this.attackStrengthPeak, evidence.getAttackStrength());
		}
		// a note struck from near silence is mostly a broadband thump at first; a note added to a sounding texture is not
		boolean fromSilence = (this.attackStrengthPeak >= SILENCE_STRENGTH);
		boolean settled = !fromSilence || (this.inAttack && timestampNanos - this.onsetNanos >= GUARD_NANOS);
		boolean dominant = true;
		if( this.parameters.neighbourRatio > 0.0 ) {
			for( int pitch : expected ) {
				double neighbours = 0.0;
				for( int delta : NEIGHBOURS ) {
					int other = pitch + delta;
					if( !contains(expected, other) ) {
						neighbours = Math.max(neighbours, evidence.amplitudeOf(other));
					}
				}
				if( evidence.amplitudeOf(pitch) < this.parameters.neighbourRatio * neighbours ) {
					dominant = false;
				}
			}
		}
		boolean inTune = true;
		if( tolerance != null ) {
			for( int i = 0 ; i < expected.length ; i ++ ) {
				double offset = evidence.offsetCents(expected[i]);
				int fret = (frets != null && i < frets.length ? frets[i] : 0);
				if( Double.isNaN(offset) || offset < -(tolerance.getPracticeAcceptCents() + OFFSET_MARGIN) || offset > tolerance.sharpLimitCents(fret) + OFFSET_MARGIN ) {
					inTune = false;
				}
			}
		}
		// Known limit (spec 004, research R8): a chord with one note missing cannot be told from the whole chord when
		// that note is partly explained by the others' partials (G2 + B3: B3's partials 2 and 4 are G2's 5th and 10th),
		// and the genuine chords of the reference recording show weak upper notes down to 6 % of the bass, so no
		// balance threshold separates them: minBalance stays 0 and the replay test reports these cases.
		boolean balanced = (weakest >= this.parameters.minBalance * strongest);
		boolean heard = settled && inTune && dominant && balanced && (sum / evidence.getTotal() >= this.parameters.minShare)
				&& (strongest >= this.parameters.minPeakRatio * evidence.getMaximum());
		this.run = (heard ? this.run + 1 : (DECAY > 0 ? Math.max(0, this.run - DECAY) : 0));
		return (this.run >= this.parameters.hops);
	}

	private static boolean contains(int[] pitches, int pitch) {
		for( int value : pitches ) {
			if( value == pitch ) {
				return true;
			}
		}
		return false;
	}
}
