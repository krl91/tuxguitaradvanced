package app.tuxguitar.app.tools.custom.practice.session;

import app.tuxguitar.audio.engine.analysis.AttackEvidence;
import app.tuxguitar.audio.engine.pitch.TolerancePolicy;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;

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
			return new Parameters(0.30, 0.10, 0.7, 6, 0.0, 0.0, 220_000_000L);
		}

		/** The values of the settings (spec 026, section {@code ACCEPTANCE_1}); the defaults are {@link #defaults()}. */
		public static Parameters of(RecognitionSettings settings) {
			return new Parameters(settings.get(RecognitionParameters.ACCEPTANCE_MIN_SHARE), settings.get(RecognitionParameters.ACCEPTANCE_MIN_BALANCE),
					settings.get(RecognitionParameters.ACCEPTANCE_MIN_PEAK), (int) Math.round(settings.get(RecognitionParameters.ACCEPTANCE_HOPS)),
					settings.get(RecognitionParameters.ACCEPTANCE_NEIGHBOUR), settings.get(RecognitionParameters.ACCEPTANCE_ATTACK_STRENGTH),
					Math.round(settings.get(RecognitionParameters.ACCEPTANCE_REARM) * 1_000_000.0));
		}
	}

	private static final int[] NEIGHBOURS = { -2, -1, 1, 2 };
	/** The measured offset of the new partials is noisy at low frequencies (about 35 cents on a low E): it only has to rule out the semitone neighbour (100 cents), not to judge the tuning. */
	public static final double DEFAULT_OFFSET_MARGIN = 15.0;
	public static final int DEFAULT_DECAY = 1;

	private final Parameters parameters;
	/** The first part of a pluck is a broadband thump and body resonances: pitch evidence is read only after it. */
	public static final long DEFAULT_GUARD_NANOS = 60L * 1_000_000L;
	/** Below this the growth is room noise, not a note (amplitude as a share of a full-scale sine; quiet real notes measure 0.02 and above). */
	public static final double DEFAULT_MIN_TOTAL = 0.01;
	public static final int DEFAULT_FLOOR_HOPS = 10;
	public static final double DEFAULT_SILENCE_STRENGTH = 0.6;
	public static final double DEFAULT_MIN_ONSET_TOTAL = 0.002;
	/** An attack begins when the sound exceeds this many times the quiet level before it, and ends when it falls under this share of its peak. */
	public static final double DEFAULT_ONSET_FLOOR = 3.0;
	public static final double DEFAULT_RELEASE = 0.25;
	/** The values above, from the settings of the run (spec 026, sections {@code ACCEPTANCE_2} and {@code ACCEPTANCE_3}). */
	private final double offsetMargin;
	private final int decay;
	private final long guardNanos;
	private final double minTotal;
	private final int floorHops;
	private final double silenceStrength;
	private final double minOnsetTotal;
	private final double onsetFloor;
	private final double release;
	private final double[] recentTotals;
	private int recentCount;
	private boolean inAttack;
	private double attackPeak;
	private double attackStrengthPeak;
	private long onsetNanos;
	private long ignoreUntilNanos = Long.MIN_VALUE;
	private boolean rearmAtNextFrame;
	private int run;

	/** The settings of the run: the offset measure of the new partials (spec 026, section {@code ATTACK_2}). */
	private final RecognitionSettings settings;

	public PolyphonicAcceptance(Parameters parameters) {
		this(parameters, RecognitionSettings.defaults());
	}

	public PolyphonicAcceptance(Parameters parameters, RecognitionSettings settings) {
		this.parameters = parameters;
		this.settings = settings;
		this.offsetMargin = settings.get(RecognitionParameters.ACCEPTANCE_OFFSET_MARGIN);
		this.decay = (int) Math.round(settings.get(RecognitionParameters.ACCEPTANCE_DECAY));
		this.guardNanos = Math.round(settings.get(RecognitionParameters.ACCEPTANCE_GUARD) * 1_000_000.0);
		this.minTotal = settings.get(RecognitionParameters.ACCEPTANCE_MIN_TOTAL);
		this.floorHops = (int) Math.round(settings.get(RecognitionParameters.ACCEPTANCE_FLOOR_HOPS));
		this.silenceStrength = settings.get(RecognitionParameters.ACCEPTANCE_SILENCE);
		this.minOnsetTotal = settings.get(RecognitionParameters.ACCEPTANCE_MIN_ONSET);
		this.onsetFloor = settings.get(RecognitionParameters.ACCEPTANCE_ONSET_FLOOR);
		this.release = settings.get(RecognitionParameters.ACCEPTANCE_RELEASE);
		this.recentTotals = new double[this.floorHops];
	}

	public RecognitionSettings getSettings() {
		return this.settings;
	}

	public Parameters getParameters() {
		return this.parameters;
	}

	/** The stream time at which the attack the last {@link #offer} answered {@code true} for began to grow (not when it was recognised). */
	public long getOnsetNanos() {
		return this.onsetNanos;
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
				|| evidence.getAttackStrength() < this.parameters.minAttackStrength || evidence.getTotal() < this.minTotal ) {
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
		for( int i = 0 ; i < Math.min(this.recentCount, this.floorHops) ; i ++ ) {
			floor = Math.min(floor, this.recentTotals[i]);
		}
		if( floor == Double.MAX_VALUE ) {
			floor = 0.0;
		}
		double total = evidence.getTotal();
		if( !this.inAttack && total > Math.max(this.minOnsetTotal, this.onsetFloor * floor) ) {
			this.inAttack = true;
			this.onsetNanos = timestampNanos;
			this.attackPeak = total;
			this.attackStrengthPeak = 0.0;
		} else if( this.inAttack ) {
			this.attackPeak = Math.max(this.attackPeak, total);
			if( total < this.release * this.attackPeak || total < this.minOnsetTotal ) {
				this.inAttack = false;
			}
		}
		this.recentTotals[this.recentCount % this.floorHops] = total;
		this.recentCount ++;
		if( this.inAttack ) {
			this.attackStrengthPeak = Math.max(this.attackStrengthPeak, evidence.getAttackStrength());
		}
		// a note struck from near silence is mostly a broadband thump at first; a note added to a sounding texture is not
		boolean fromSilence = (this.attackStrengthPeak >= this.silenceStrength);
		boolean settled = !fromSilence || (this.inAttack && timestampNanos - this.onsetNanos >= this.guardNanos);
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
				double offset = evidence.offsetCents(expected[i], this.settings);
				int fret = (frets != null && i < frets.length ? frets[i] : 0);
				if( Double.isNaN(offset) || offset < -(tolerance.getPracticeAcceptCents() + this.offsetMargin) || offset > tolerance.sharpLimitCents(fret) + this.offsetMargin ) {
					inTune = false;
				}
			}
		}
		// Completeness of a chord (spec FR-003), measured on the public datasets (research R9): every note of the chord must
		// show some growth of its own, minBalance (10 %) of the strongest, shared among the notes. On the reference recording the
		// genuine chords show upper notes down to 6 % of the bass, hence the known-limit count in its replay test.
		// Only the notes audio can tell from a lower note of the chord are compared (an octave or a twelfth is
		// a partial of the lower note: spec FR-004); the more of them, the weaker the weakest even when all sound, so the threshold is shared among them.
		boolean balanced = true;
		int[] separable = separable(expected);
		if( separable.length > 1 ) {
			double weakestOwn = Double.MAX_VALUE;
			double strongestOwn = 0.0;
			for( int pitch : separable ) {
				double amplitude = evidence.amplitudeOf(pitch);
				weakestOwn = Math.min(weakestOwn, amplitude);
				strongestOwn = Math.max(strongestOwn, amplitude);
			}
			double balance = this.parameters.minBalance / separable.length;
			// the reference is the strongest of all the expected notes (an octave included), not only of the separable ones: when every separable
			// note carries nothing but leakage they are all equally weak, which must not pass as balanced (a chord heard as one octave only)
			balanced = (weakestOwn >= balance * Math.max(strongestOwn, strongest));
		}
		boolean heard = settled && inTune && dominant && balanced && (sum / evidence.getTotal() >= this.parameters.minShare)
				&& (strongest >= this.parameters.minPeakRatio * evidence.getMaximum());
		this.run = (heard ? this.run + 1 : (this.decay > 0 ? Math.max(0, this.run - this.decay) : 0));
		return (this.run >= this.parameters.hops);
	}

	private static final int[] HARMONIC_INTERVALS = { 12, 19, 24, 28, 31, 34, 36 };

	/** The expected pitches that are not a harmonic (octave, twelfth...) of a lower expected pitch: the only ones audio can tell from the other notes of the chord. */
	public static int[] separable(int[] expected) {
		int[] sorted = expected.clone();
		java.util.Arrays.sort(sorted);
		int[] kept = new int[sorted.length];
		int count = 0;
		for( int i = 0 ; i < sorted.length ; i ++ ) {
			boolean harmonic = false;
			for( int j = 0 ; j < i ; j ++ ) {
				int distance = sorted[i] - sorted[j];
				for( int interval : HARMONIC_INTERVALS ) {
					harmonic |= (distance == interval);
				}
				harmonic |= (distance == 0);
			}
			if( !harmonic ) {
				kept[count ++] = sorted[i];
			}
		}
		return java.util.Arrays.copyOf(kept, count);
	}

	/** The expected pitches audio cannot tell from the others (an octave or a harmonic of a lower expected pitch, or the same pitch): shown as "not checked separately", never as heard or missing (spec 004 FR-004). */
	public static int[] notSeparable(int[] expected) {
		int[] separable = separable(expected);
		int[] result = new int[expected.length];
		int count = 0;
		int[] sorted = expected.clone();
		java.util.Arrays.sort(sorted);
		for( int pitch : sorted ) {
			if( !contains(separable, pitch) && (count == 0 || result[count - 1] != pitch) ) {
				result[count ++] = pitch;
			}
		}
		return java.util.Arrays.copyOf(result, count);
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
