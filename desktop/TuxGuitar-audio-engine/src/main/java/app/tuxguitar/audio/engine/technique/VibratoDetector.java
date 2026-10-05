package app.tuxguitar.audio.engine.technique;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Vibrato: the pitch oscillates around its trend at about 3–9 Hz with a depth of tens of cents. The
 * thresholds are provisional until the benchmark of spec 002 (research R4) has fixed them.
 */
public final class VibratoDetector implements TechniqueDetector {

	/** Thresholds, in cents and Hz; {@link #defaults()} are the provisional values. */
	public static final class Parameters {
		public double minDepthCents = 25.0;
		public double plainDepthCents = 10.0;
		public double minRateHz = 2.5;
		public double maxRateHz = 9.0;
		public double minDurationSeconds = 0.35;
		public double minVoicedShare = 0.7;

		public static Parameters defaults() {
			return new Parameters();
		}
	}

	private final Parameters parameters;

	public VibratoDetector(Parameters parameters) {
		this.parameters = parameters;
	}

	@Override
	public DetectableTechnique technique() {
		return DetectableTechnique.VIBRATO;
	}

	@Override
	public TechniqueEvidence evaluate(NoteSegment segment) {
		PitchMotion motion = new PitchMotion(segment);
		Map<String, Double> m = new LinkedHashMap<String, Double>();
		m.put("depthCents", motion.wobbleDepth);
		m.put("rateHz", motion.wobbleRate);
		double voicedShare = motion.voicedShare(segment.size());
		m.put("voicedShare", voicedShare);
		if( segment.getDurationSeconds() < this.parameters.minDurationSeconds || voicedShare < this.parameters.minVoicedShare || motion.voiced < PitchMotion.MIN_VOICED ) {
			return new TechniqueEvidence(DetectableTechnique.VIBRATO, TechniqueEvidence.Verdict.UNCERTAIN, 0.0, m);
		}
		boolean rateOk = motion.wobbleRate >= this.parameters.minRateHz && motion.wobbleRate <= this.parameters.maxRateHz;
		if( motion.wobbleDepth >= this.parameters.minDepthCents && rateOk ) {
			return new TechniqueEvidence(DetectableTechnique.VIBRATO, TechniqueEvidence.Verdict.DETECTED, Math.min(1.0, motion.wobbleDepth / (2.0 * this.parameters.minDepthCents)), m);
		}
		if( motion.wobbleDepth < this.parameters.plainDepthCents ) {
			return new TechniqueEvidence(DetectableTechnique.VIBRATO, TechniqueEvidence.Verdict.NOT_DETECTED, Math.min(1.0, 1.5 - motion.wobbleDepth / this.parameters.plainDepthCents), m);
		}
		return new TechniqueEvidence(DetectableTechnique.VIBRATO, TechniqueEvidence.Verdict.UNCERTAIN, 0.3, m);
	}
}
