package app.tuxguitar.audio.engine.analysis;

/**
 * Detects new articulations (plucks) for FR-022, as validated in research spike S4: a
 * spectral-flux transient AND a level gate (mean level 4–13 hops after the transient minus the
 * maximum level 2–19 hops before it ≥ {@code gateDb}). Biased toward missing weak re-plucks
 * rather than reporting sustain, vibrato, clicks or dropouts.
 *
 * <p>Streaming, one call per hop, preallocated (no allocation per call). The gate needs the
 * level after the transient, so a detection is reported {@link #DECISION_DELAY_HOPS} hops after
 * the transient hop. All thresholds are provisional parameters, not product constants.</p>
 */
public final class ArticulationDetector {

	/** Hops between the transient and the hop on which it is reported. */
	public static final int DECISION_DELAY_HOPS = 13;

	/**
	 * Provisional defaults: flux and level thresholds from the synthetic S4 experiment; the gate
	 * raised from +1.5 to +4.5 dB on the real-guitar clips (T076: no false articulation left, no
	 * additional miss; research.md "T076").
	 */
	public static final double DEFAULT_FLUX_MEDIAN_FACTOR = 1.5;
	public static final double DEFAULT_FLUX_OFFSET = 5.0;
	public static final double DEFAULT_MIN_LEVEL_DB = -50.0;
	public static final int DEFAULT_REFRACTORY_HOPS = 12;
	public static final double DEFAULT_GATE_DB = 4.5;

	private static final int FFT_SIZE = 1024;
	private static final int MEDIAN_HOPS = 10;
	/** Hops before/after the transient whose levels form the gate (MVP: before 2-19, after 4-13). */
	private final int preFirst;
	private final int preLast;
	private final int postFirst;
	private final int postLast;
	private final int history;

	private final double fluxMedianFactor;
	private final double fluxOffset;
	private final double minLevelDb;
	private final int refractoryHops;
	private final double gateDb;

	private final double[] hann = new double[FFT_SIZE];
	private final double[] real = new double[FFT_SIZE];
	private final double[] imaginary = new double[FFT_SIZE];
	private final double[] previousMagnitude = new double[FFT_SIZE / 2];
	private final double[] levelDb;
	private final double[] flux;
	private final double[] medianScratch = new double[MEDIAN_HOPS];
	private long hop;
	private long lastOnsetHop;

	public ArticulationDetector() {
		this(DEFAULT_FLUX_MEDIAN_FACTOR, DEFAULT_FLUX_OFFSET, DEFAULT_MIN_LEVEL_DB, DEFAULT_REFRACTORY_HOPS, DEFAULT_GATE_DB);
	}

	public ArticulationDetector(double fluxMedianFactor, double fluxOffset, double minLevelDb, int refractoryHops, double gateDb) {
		this(fluxMedianFactor, fluxOffset, minLevelDb, refractoryHops, gateDb, 19, 2, 4, DECISION_DELAY_HOPS);
	}

	/**
	 * The fast-run detector (spec 002 T048): the level before the attack is taken over the few hops
	 * just before it instead of the maximum over 19 (a previous note that is already decaying no
	 * longer hides an equal re-pluck), the level after it over a shorter window (earlier decision),
	 * and the refractory is shorter. The MVP detector stays the default; the thresholds are
	 * provisional until the fast-passage benchmark has measured them.
	 */
	public static ArticulationDetector fast(double minLevelDb, double gateDb) {
		return new ArticulationDetector(DEFAULT_FLUX_MEDIAN_FACTOR, DEFAULT_FLUX_OFFSET, minLevelDb, FAST_REFRACTORY_HOPS, gateDb, 8, 2, 3, FAST_DECISION_DELAY_HOPS);
	}

	public static final int FAST_REFRACTORY_HOPS = 6;
	public static final int FAST_DECISION_DELAY_HOPS = 8;

	public ArticulationDetector(double fluxMedianFactor, double fluxOffset, double minLevelDb, int refractoryHops, double gateDb,
			int preFirst, int preLast, int postFirst, int postLast) {
		if( preFirst < preLast || preLast < 1 || postFirst < 1 || postLast < postFirst || preFirst > 19 || postLast > DECISION_DELAY_HOPS ) {
			throw new IllegalArgumentException("invalid gate windows");
		}
		this.preFirst = preFirst;
		this.preLast = preLast;
		this.postFirst = postFirst;
		this.postLast = postLast;
		this.history = preFirst + postLast + 2;
		this.levelDb = new double[this.history];
		this.flux = new double[this.history];
		this.fluxMedianFactor = fluxMedianFactor;
		this.fluxOffset = fluxOffset;
		this.minLevelDb = minLevelDb;
		this.refractoryHops = refractoryHops;
		this.gateDb = gateDb;
		for( int i = 0 ; i < FFT_SIZE ; i ++ ) {
			this.hann[i] = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / FFT_SIZE);
		}
		reset();
	}

	/** Hops between a transient and the hop on which it is reported by this detector. */
	public int getDecisionDelayHops() {
		return this.postLast;
	}

	/** Level rise needed for a new attack (the "pick attack sensitivity" setting). */
	public double getGateDb() {
		return this.gateDb;
	}

	/** Quieter frames are never an attack (the "attack detection floor" setting). */
	public double getMinLevelDb() {
		return this.minLevelDb;
	}

	/** Forgets all history, e.g. after a gap in the input. */
	public void reset() {
		this.hop = 0;
		this.lastOnsetHop = Long.MIN_VALUE / 2;
		java.util.Arrays.fill(this.previousMagnitude, 0.0);
	}

	/**
	 * Processes the newest hop.
	 *
	 * @param window the analysis window; its last 1024 samples are used
	 * @return true when an articulation is confirmed whose transient was
	 *         {@link #DECISION_DELAY_HOPS} hops before this one
	 */
	public boolean process(float[] window) {
		int offset = window.length - FFT_SIZE;
		double energy = 0;
		for( int i = 0 ; i < FFT_SIZE ; i ++ ) {
			double sample = window[offset + i];
			energy += sample * sample;
			this.real[i] = sample * this.hann[i];
			this.imaginary[i] = 0;
		}
		fft(this.real, this.imaginary);
		double currentFlux = 0;
		for( int k = 1 ; k < FFT_SIZE / 2 ; k ++ ) {
			double magnitude = Math.log1p(100 * Math.hypot(this.real[k], this.imaginary[k]));
			double rise = magnitude - this.previousMagnitude[k];
			if( rise > 0 ) {
				currentFlux += rise;
			}
			this.previousMagnitude[k] = magnitude;
		}
		int slot = slot(this.hop);
		this.levelDb[slot] = 10 * Math.log10(energy / FFT_SIZE + 1e-12);
		this.flux[slot] = (this.hop == 0 ? 0 : currentFlux);
		boolean onset = decide(this.hop - this.postLast);
		this.hop ++;
		return onset;
	}

	private boolean decide(long candidate) {
		if( candidate < MEDIAN_HOPS || candidate - this.lastOnsetHop <= this.refractoryHops ) {
			return false;
		}
		double value = this.flux[slot(candidate)];
		if( value < this.flux[slot(candidate - 1)] || value < this.flux[slot(candidate + 1)] ) {
			return false;
		}
		if( this.levelDb[slot(candidate)] <= this.minLevelDb || value <= this.fluxMedianFactor * medianBefore(candidate) + this.fluxOffset ) {
			return false;
		}
		if( candidate >= this.preFirst && !passesGate(candidate) ) {
			return false;
		}
		this.lastOnsetHop = candidate;
		return true;
	}

	private boolean passesGate(long candidate) {
		double pre = -Double.MAX_VALUE;
		for( long k = candidate - this.preFirst ; k <= candidate - this.preLast ; k ++ ) {
			pre = Math.max(pre, this.levelDb[slot(k)]);
		}
		double post = 0;
		for( long k = candidate + this.postFirst ; k <= candidate + this.postLast ; k ++ ) {
			post += this.levelDb[slot(k)];
		}
		post /= (this.postLast - this.postFirst + 1);
		return (post - pre >= this.gateDb);
	}

	private double medianBefore(long candidate) {
		for( int i = 0 ; i < MEDIAN_HOPS ; i ++ ) {
			this.medianScratch[i] = this.flux[slot(candidate - MEDIAN_HOPS + i)];
		}
		java.util.Arrays.sort(this.medianScratch);
		return this.medianScratch[MEDIAN_HOPS / 2];
	}

	private int slot(long hop) {
		return (int) (hop % this.history);
	}

	private static void fft(double[] re, double[] im) {
		int n = re.length;
		for( int i = 1, j = 0 ; i < n ; i ++ ) {
			int bit = n >> 1;
			for( ; (j & bit) != 0 ; bit >>= 1 ) {
				j ^= bit;
			}
			j ^= bit;
			if( i < j ) {
				double t = re[i]; re[i] = re[j]; re[j] = t;
				t = im[i]; im[i] = im[j]; im[j] = t;
			}
		}
		for( int length = 2 ; length <= n ; length <<= 1 ) {
			double angle = -2 * Math.PI / length;
			double wr = Math.cos(angle);
			double wi = Math.sin(angle);
			for( int i = 0 ; i < n ; i += length ) {
				double cr = 1;
				double ci = 0;
				for( int k = 0 ; k < length / 2 ; k ++ ) {
					int a = i + k;
					int b = a + length / 2;
					double tr = re[b] * cr - im[b] * ci;
					double ti = re[b] * ci + im[b] * cr;
					re[b] = re[a] - tr;
					im[b] = im[a] - ti;
					re[a] += tr;
					im[a] += ti;
					double next = cr * wr - ci * wi;
					ci = cr * wi + ci * wr;
					cr = next;
				}
			}
		}
	}
}
