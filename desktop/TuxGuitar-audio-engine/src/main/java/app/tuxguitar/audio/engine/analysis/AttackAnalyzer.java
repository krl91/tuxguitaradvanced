package app.tuxguitar.audio.engine.analysis;

import java.util.Arrays;

/**
 * Tells which pitches were just struck, also over strings that keep ringing (spec 004): compares
 * the spectrum of the newest window with the one {@code lagHops} hops earlier, keeps only the
 * growth, and explains that growth as a non-negative combination of harmonic templates of the
 * candidate pitches ({@link NnlsSolver}). Streaming, one {@link #analyze()} per hop, deterministic,
 * preallocated except for the small result per call. Not thread-safe: one instance per analysis
 * thread.
 */
public final class AttackAnalyzer {

	/** Values chosen on the replay of the reference recording (spec 004 research R8). */
	public static final class Parameters {
		public final int fftSize;
		public final int lagHops;
		public final double maxHz;
		public final int partials;
		/** Template partial h weighs h^-beta. */
		public final double beta;
		/** Gaussian width of a template partial, in bins. */
		public final double sigmaBins;

		public Parameters(int fftSize, int lagHops, double maxHz, int partials, double beta, double sigmaBins) {
			if( Integer.bitCount(fftSize) != 1 || lagHops < 1 || maxHz <= 0 || partials < 1 || beta < 0 || sigmaBins <= 0 ) {
				throw new IllegalArgumentException("invalid attack analyzer parameters");
			}
			this.fftSize = fftSize;
			this.lagHops = lagHops;
			this.maxHz = maxHz;
			this.partials = partials;
			this.beta = beta;
			this.sigmaBins = sigmaBins;
		}

		public static Parameters defaults() {
			return new Parameters(4096, 22, 5000.0, 14, 0.3, 1.2);
		}
	}

	private final Parameters parameters;
	private final int[] pitches;
	private final int hopSize;
	private final RealFft fft;
	private final double[] hann;
	private final float[] history;
	private final double[] input;
	private final double[] magnitudes;
	private final int length;
	private final double[][] ring;
	private final double[][] templates;
	private final double[] difference;
	private final double[] projection;
	private final NnlsSolver solver;
	private final double binHz;
	private int historyPosition;
	private int hops;

	/**
	 * @param pitches the candidate MIDI pitches (any order, duplicates ignored)
	 * @param hopSize samples between two {@link #analyze()} calls
	 */
	public AttackAnalyzer(Parameters parameters, int sampleRate, int hopSize, int[] pitches) {
		if( sampleRate <= 0 || hopSize <= 0 || pitches == null || pitches.length == 0 ) {
			throw new IllegalArgumentException("invalid attack analyzer arguments");
		}
		this.parameters = parameters;
		this.hopSize = hopSize;
		this.pitches = Arrays.stream(pitches).distinct().sorted().toArray();
		int size = parameters.fftSize;
		this.fft = new RealFft(size);
		this.hann = new double[size];
		for( int i = 0 ; i < size ; i ++ ) {
			this.hann[i] = 0.5 - 0.5 * Math.cos(2.0 * Math.PI * i / size);
		}
		this.history = new float[size];
		this.input = new double[size];
		this.magnitudes = new double[size / 2 + 1];
		double binHz = (double) sampleRate / size;
		this.binHz = binHz;
		this.length = Math.min(size / 2 - 1, (int) (parameters.maxHz / binHz));
		this.ring = new double[parameters.lagHops + 1][this.length];
		this.difference = new double[this.length];
		this.templates = buildTemplates(binHz);
		this.projection = new double[this.pitches.length];
		this.solver = new NnlsSolver(gram(this.templates));
	}

	public int[] getPitches() {
		return this.pitches.clone();
	}

	/** Appends one hop of samples ({@code length} must be the hop size). */
	public void push(float[] samples, int offset, int count) {
		for( int i = 0 ; i < count ; i ++ ) {
			this.history[this.historyPosition] = samples[offset + i];
			this.historyPosition = (this.historyPosition + 1) % this.history.length;
		}
	}

	/** The evidence of the newest hop, or null until the first {@code lagHops + 1} hops have been analysed. */
	public AttackEvidence analyze() {
		int size = this.parameters.fftSize;
		for( int i = 0 ; i < size ; i ++ ) {
			this.input[i] = this.history[(this.historyPosition + i) % size] * this.hann[i];
		}
		this.fft.magnitudes(this.input, this.magnitudes, 4.0 / size);
		double[] now = this.ring[this.hops % this.ring.length];
		System.arraycopy(this.magnitudes, 0, now, 0, this.length);
		this.hops ++;
		if( this.hops <= this.parameters.lagHops ) {
			return null;
		}
		double[] before = this.ring[(this.hops - 1 - this.parameters.lagHops) % this.ring.length];
		double grown = 0.0;
		double size2 = 0.0;
		for( int k = 0 ; k < this.length ; k ++ ) {
			double d = now[k] - before[k];
			this.difference[k] = (d > 0.0 ? d : 0.0);
			grown += this.difference[k];
			size2 += now[k];
		}
		for( int u = 0 ; u < this.pitches.length ; u ++ ) {
			double sum = 0.0;
			double[] template = this.templates[u];
			for( int k = 0 ; k < this.length ; k ++ ) {
				sum += template[k] * this.difference[k];
			}
			this.projection[u] = sum;
		}
		double[] solution = this.solver.solve(this.projection);
		return new AttackEvidence(this.pitches, solution.clone(), (size2 > 0.0 ? grown / size2 : 0.0), this.difference.clone(), this.binHz);
	}

	public void reset() {
		Arrays.fill(this.history, 0f);
		this.historyPosition = 0;
		this.hops = 0;
	}

	public int getHopSize() {
		return this.hopSize;
	}

	private double[][] buildTemplates(double binHz) {
		double[][] result = new double[this.pitches.length][this.length];
		for( int u = 0 ; u < this.pitches.length ; u ++ ) {
			double f0 = 440.0 * Math.pow(2.0, (this.pitches[u] - 69) / 12.0);
			for( int h = 1 ; h <= this.parameters.partials ; h ++ ) {
				double center = f0 * h / binHz;
				if( center >= this.length - 2 ) {
					break;
				}
				double weight = 1.0 / Math.pow(h, this.parameters.beta);
				int from = Math.max(1, (int) (center - 3 * this.parameters.sigmaBins - 1));
				int to = Math.min(this.length - 1, (int) (center + 3 * this.parameters.sigmaBins + 1));
				for( int k = from ; k <= to ; k ++ ) {
					double d = (k - center) / this.parameters.sigmaBins;
					result[u][k] += weight * Math.exp(-0.5 * d * d);
				}
			}
			double norm = 0.0;
			for( int k = 0 ; k < this.length ; k ++ ) {
				norm += result[u][k] * result[u][k];
			}
			norm = Math.sqrt(Math.max(norm, 1e-12));
			for( int k = 0 ; k < this.length ; k ++ ) {
				result[u][k] /= norm;
			}
		}
		return result;
	}

	private static double[][] gram(double[][] templates) {
		int n = templates.length;
		double[][] g = new double[n][n];
		for( int i = 0 ; i < n ; i ++ ) {
			for( int j = i ; j < n ; j ++ ) {
				double sum = 0.0;
				for( int k = 0 ; k < templates[i].length ; k ++ ) {
					sum += templates[i][k] * templates[j][k];
				}
				g[i][j] = sum;
				g[j][i] = sum;
			}
		}
		return g;
	}
}
