package app.tuxguitar.audio.engine.technique;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Slide: the pitch glides in one direction through the pitches between two frets, as opposed to a
 * direct jump (picked) or a wobble (vibrato).
 */
public final class SlideDetector implements TechniqueDetector {

	public static final class Parameters {
		public double minRangeCents = 100.0;
		public double plainRangeCents = 40.0;
		public double minMonotony = 0.75;
		/** A glide passes through intermediate pitches: a jump has one or two moving steps only. */
		public int minMovingSteps = 6;
		public double minDurationSeconds = 0.15;
		public double minVoicedShare = 0.5;

		public static Parameters defaults() {
			return new Parameters();
		}
	}

	private final Parameters parameters;

	public SlideDetector(Parameters parameters) {
		this.parameters = parameters;
	}

	@Override
	public DetectableTechnique technique() {
		return DetectableTechnique.SLIDE;
	}

	@Override
	public TechniqueEvidence evaluate(NoteSegment segment) {
		PitchMotion motion = new PitchMotion(segment);
		Map<String, Double> m = new LinkedHashMap<String, Double>();
		m.put("rangeCents", motion.range);
		m.put("driftCents", motion.drift);
		m.put("monotony", motion.monotony);
		m.put("movingSteps", (double) motion.movingSteps);
		double voicedShare = motion.voicedShare(segment.size());
		m.put("voicedShare", voicedShare);
		if( segment.getDurationSeconds() < this.parameters.minDurationSeconds || voicedShare < this.parameters.minVoicedShare || motion.voiced < PitchMotion.MIN_VOICED ) {
			return new TechniqueEvidence(DetectableTechnique.SLIDE, TechniqueEvidence.Verdict.UNCERTAIN, 0.0, m);
		}
		if( motion.range >= this.parameters.minRangeCents && motion.monotony >= this.parameters.minMonotony && motion.movingSteps >= this.parameters.minMovingSteps ) {
			return new TechniqueEvidence(DetectableTechnique.SLIDE, TechniqueEvidence.Verdict.DETECTED, Math.min(1.0, motion.range / (2.0 * this.parameters.minRangeCents)), m);
		}
		if( motion.range < this.parameters.plainRangeCents ) {
			return new TechniqueEvidence(DetectableTechnique.SLIDE, TechniqueEvidence.Verdict.NOT_DETECTED, Math.min(1.0, 1.5 - motion.range / this.parameters.plainRangeCents), m);
		}
		return new TechniqueEvidence(DetectableTechnique.SLIDE, TechniqueEvidence.Verdict.UNCERTAIN, 0.3, m);
	}
}
