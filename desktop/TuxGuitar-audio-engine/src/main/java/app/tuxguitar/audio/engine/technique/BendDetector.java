package app.tuxguitar.audio.engine.technique;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bend (and release, pre-bend): the pitch of the note moves by tens of cents without oscillating. The
 * amount it reaches is reported (cents); comparing it with the written bend is the judge's business.
 */
public final class BendDetector implements TechniqueDetector {

	public static final class Parameters {
		public double minRangeCents = 60.0;
		public double plainRangeCents = 30.0;
		public double minDurationSeconds = 0.2;
		public double minVoicedShare = 0.6;

		public static Parameters defaults() {
			return new Parameters();
		}
	}

	private final Parameters parameters;

	public BendDetector(Parameters parameters) {
		this.parameters = parameters;
	}

	@Override
	public DetectableTechnique technique() {
		return DetectableTechnique.BEND;
	}

	@Override
	public TechniqueEvidence evaluate(NoteSegment segment) {
		PitchMotion motion = new PitchMotion(segment);
		Map<String, Double> m = new LinkedHashMap<String, Double>();
		m.put("rangeCents", motion.range);
		m.put("driftCents", motion.drift);
		double voicedShare = motion.voicedShare(segment.size());
		m.put("voicedShare", voicedShare);
		if( segment.getDurationSeconds() < this.parameters.minDurationSeconds || voicedShare < this.parameters.minVoicedShare || motion.voiced < PitchMotion.MIN_VOICED ) {
			return new TechniqueEvidence(DetectableTechnique.BEND, TechniqueEvidence.Verdict.UNCERTAIN, 0.0, m);
		}
		if( motion.range >= this.parameters.minRangeCents ) {
			return new TechniqueEvidence(DetectableTechnique.BEND, TechniqueEvidence.Verdict.DETECTED, Math.min(1.0, motion.range / (2.0 * this.parameters.minRangeCents)), m);
		}
		if( motion.range < this.parameters.plainRangeCents ) {
			return new TechniqueEvidence(DetectableTechnique.BEND, TechniqueEvidence.Verdict.NOT_DETECTED, Math.min(1.0, 1.5 - motion.range / this.parameters.plainRangeCents), m);
		}
		return new TechniqueEvidence(DetectableTechnique.BEND, TechniqueEvidence.Verdict.UNCERTAIN, 0.3, m);
	}
}
