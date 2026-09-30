package app.tuxguitar.audio.engine.technique;

/**
 * Per-hop measurements the technique detectors work from (spec 002, data-model.md
 * "TechniqueFeatures"). Immutable; NaN means "not available for this hop".
 */
public final class TechniqueFeatures {

	private final double f0Hz;
	private final double f0Cents;
	private final double levelDb;
	private final double levelSlopeDbPerMs;
	private final double spectralCentroidHz;
	private final double highFrequencyRatio;
	private final double flatness;
	private final double flux;
	private final boolean provisionalOnset;

	/**
	 * @param f0Hz estimated fundamental, or NaN
	 * @param f0Cents the fundamental in cents relative to the nearest equal-tempered note, or NaN
	 * @param levelDb level of the hop in dBFS
	 * @param levelSlopeDbPerMs level change per millisecond over the last hops
	 * @param spectralCentroidHz spectral centroid
	 * @param highFrequencyRatio share of the spectrum energy above the high-frequency split, 0–1
	 * @param flatness spectral flatness, 0 (tonal) to 1 (noise-like)
	 * @param flux spectral flux of the hop
	 * @param provisionalOnset true when a flux-only onset (before the gate confirmation) was seen in this hop
	 */
	public TechniqueFeatures(double f0Hz, double f0Cents, double levelDb, double levelSlopeDbPerMs,
			double spectralCentroidHz, double highFrequencyRatio, double flatness, double flux, boolean provisionalOnset) {
		this.f0Hz = f0Hz;
		this.f0Cents = f0Cents;
		this.levelDb = levelDb;
		this.levelSlopeDbPerMs = levelSlopeDbPerMs;
		this.spectralCentroidHz = spectralCentroidHz;
		this.highFrequencyRatio = highFrequencyRatio;
		this.flatness = flatness;
		this.flux = flux;
		this.provisionalOnset = provisionalOnset;
	}

	public double getF0Hz() {
		return this.f0Hz;
	}

	public double getF0Cents() {
		return this.f0Cents;
	}

	public double getLevelDb() {
		return this.levelDb;
	}

	public double getLevelSlopeDbPerMs() {
		return this.levelSlopeDbPerMs;
	}

	public double getSpectralCentroidHz() {
		return this.spectralCentroidHz;
	}

	public double getHighFrequencyRatio() {
		return this.highFrequencyRatio;
	}

	public double getFlatness() {
		return this.flatness;
	}

	public double getFlux() {
		return this.flux;
	}

	public boolean isProvisionalOnset() {
		return this.provisionalOnset;
	}
}
