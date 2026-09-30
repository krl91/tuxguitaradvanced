package app.tuxguitar.audio.engine.recognition;

import java.util.Collections;
import java.util.List;

/**
 * How well each hypothesis of the expected-pitch verifier (the expected pitch, its semitone
 * neighbours, its octave) explains the latest audio (spec 002, research R3 lever L1). Immutable.
 */
public final class TargetEvidence {

	/** The evidence for one candidate pitch. */
	public static final class Hypothesis {
		private final int midi;
		private final double score;

		public Hypothesis(int midi, double score) {
			if( !(score >= 0.0 && score <= 1.0) ) {
				throw new IllegalArgumentException("score must be in [0, 1]: " + score);
			}
			this.midi = midi;
			this.score = score;
		}

		public int getMidi() {
			return this.midi;
		}

		/** Periodicity evidence from 0 (none) to 1 (certain). */
		public double getScore() {
			return this.score;
		}
	}

	private final List<Hypothesis> hypotheses;
	private final double windowMillis;

	public TargetEvidence(List<Hypothesis> hypotheses, double windowMillis) {
		if( hypotheses == null || hypotheses.isEmpty() ) {
			throw new IllegalArgumentException("at least one hypothesis is required");
		}
		this.hypotheses = Collections.unmodifiableList(new java.util.ArrayList<Hypothesis>(hypotheses));
		this.windowMillis = windowMillis;
	}

	public List<Hypothesis> getHypotheses() {
		return this.hypotheses;
	}

	/** Length of the audio window the evidence was computed on. */
	public double getWindowMillis() {
		return this.windowMillis;
	}

	/** The evidence of one pitch, or null when it is not among the hypotheses. */
	public Hypothesis of(int midi) {
		for( Hypothesis hypothesis : this.hypotheses ) {
			if( hypothesis.getMidi() == midi ) {
				return hypothesis;
			}
		}
		return null;
	}

	/** How much the given pitch beats the best other hypothesis (negative when another one is better). */
	public double marginOver(int midi) {
		Hypothesis own = of(midi);
		if( own == null ) {
			throw new IllegalArgumentException("not a hypothesis: " + midi);
		}
		double bestOther = 0.0;
		for( Hypothesis hypothesis : this.hypotheses ) {
			if( hypothesis.getMidi() != midi ) {
				bestOther = Math.max(bestOther, hypothesis.getScore());
			}
		}
		return own.getScore() - bestOther;
	}
}
