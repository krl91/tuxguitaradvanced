package app.tuxguitar.audio.engine.analysis;

/**
 * What the newest hop added to the sound, explained by pitches (spec 004): the amplitude, per
 * candidate pitch, of the non-negative combination of harmonic templates that best reproduces the
 * growth of the spectrum between {@link AttackAnalyzer.Parameters#lagHops} hops ago and now. A note
 * that already rings contributes nothing; a note just struck does. Immutable.
 */
public final class AttackEvidence {

	private final int[] pitches;
	private final double[] amplitudes;
	private final double total;
	private final double maximum;
	private final double attackStrength;
	private final double[] growth;
	private final double maxGrowth;
	private final double binHz;

	/** @param pitches ascending candidate pitches; {@code amplitudes} aligned with them */
	public AttackEvidence(int[] pitches, double[] amplitudes, double attackStrength) {
		this(pitches, amplitudes, attackStrength, null, 0.0);
	}

	/**
	 * @param growth the positive spectral growth (what appeared), per bin of {@code binHz}; null when
	 *        the tuning of the new notes cannot be measured
	 */
	public AttackEvidence(int[] pitches, double[] amplitudes, double attackStrength, double[] growth, double binHz) {
		if( pitches.length != amplitudes.length ) {
			throw new IllegalArgumentException("one amplitude per pitch");
		}
		this.growth = growth;
		this.binHz = binHz;
		double top = 0.0;
		if( growth != null ) {
			for( double value : growth ) {
				top = Math.max(top, value);
			}
		}
		this.maxGrowth = top;
		this.pitches = pitches;
		this.amplitudes = amplitudes;
		double sum = 0.0;
		double max = 0.0;
		for( double amplitude : amplitudes ) {
			sum += amplitude;
			max = Math.max(max, amplitude);
		}
		this.total = sum;
		this.maximum = max;
		this.attackStrength = attackStrength;
	}

	/** The candidate pitches, ascending; the same array for every frame of an analyzer (not to be modified). */
	public int[] getPitches() {
		return this.pitches;
	}

	/** Amplitude of the candidate at {@code index} (same order as {@link #getPitches()}). */
	public double getAmplitude(int index) {
		return this.amplitudes[index];
	}

	/** Amplitude of a pitch, or 0 when it is not a candidate. */
	public double amplitudeOf(int midi) {
		int index = java.util.Arrays.binarySearch(this.pitches, midi);
		return (index >= 0 ? this.amplitudes[index] : 0.0);
	}

	/** Sum of all the candidates' amplitudes. */
	public double getTotal() {
		return this.total;
	}

	/** Largest candidate amplitude. */
	public double getMaximum() {
		return this.maximum;
	}

	/**
	 * How much the spectrum grew, relative to its own size (0 for a steady sound, towards 1 for a
	 * sudden one): the guard against reading noise as an attack.
	 */
	public double getAttackStrength() {
		return this.attackStrength;
	}

	/**
	 * How far, in cents, the partials that just appeared near the harmonics of a pitch lie from the
	 * exact positions (positive: sharp): the median over the partials with a clear peak, each peak
	 * refined between bins. This separates a pitch from its semitone neighbours, which the template
	 * amplitudes alone cannot at low frequencies. NaN when fewer than two partials are measurable.
	 */
	public double offsetCents(int midi) {
		if( this.growth == null ) {
			return Double.NaN;
		}
		double f0 = 440.0 * Math.pow(2.0, (midi - 69) / 12.0);
		double strongest = this.maxGrowth;
		double[] offsets = new double[PARTIALS];
		double[] weights = new double[PARTIALS];
		int found = 0;
		for( int h = 1 ; h <= PARTIALS ; h ++ ) {
			double hz = f0 * h;
			double center = hz / this.binHz;
			int half = (int) Math.max(1.0, Math.ceil(hz * (Math.pow(2.0, SEARCH_CENTS / 1200.0) - 1.0) / this.binHz));
			int from = Math.max(1, (int) Math.round(center) - half);
			int to = Math.min(this.growth.length - 2, (int) Math.round(center) + half);
			if( to <= from ) {
				break;
			}
			int peak = from;
			for( int k = from ; k <= to ; k ++ ) {
				if( this.growth[k] > this.growth[peak] ) {
					peak = k;
				}
			}
			double left = this.growth[peak - 1];
			double middle = this.growth[peak];
			double right = this.growth[peak + 1];
			if( middle <= 0.0 || middle < left || middle < right ) {
				continue;
			}
			double denominator = left - 2.0 * middle + right;
			double shift = (denominator < 0.0 ? 0.5 * (left - right) / denominator : 0.0);
			double measured = (peak + Math.max(-0.5, Math.min(0.5, shift))) * this.binHz;
			offsets[found] = 1200.0 * Math.log(measured / hz) / Math.log(2.0);
			weights[found] = middle;
			found ++;
		}
		int kept = 0;
		double[] values = new double[found];
		for( int i = 0 ; i < found ; i ++ ) {
			if( weights[i] >= MIN_PEAK_SHARE * strongest ) {
				values[kept ++] = offsets[i];
			}
		}
		if( kept < 2 ) {
			return Double.NaN;
		}
		java.util.Arrays.sort(values, 0, kept);
		return (kept % 2 == 1 ? values[kept / 2] : 0.5 * (values[kept / 2 - 1] + values[kept / 2]));
	}

	private static final int PARTIALS = 12;
	private static final double SEARCH_CENTS = 60.0;
	private static final double MIN_PEAK_SHARE = 0.03;

	/** Share of the explained growth that belongs to the given pitches (0 when nothing grew). */
	public double shareOf(int... midi) {
		if( this.total <= 0.0 ) {
			return 0.0;
		}
		double sum = 0.0;
		for( int pitch : midi ) {
			sum += amplitudeOf(pitch);
		}
		return sum / this.total;
	}
}
