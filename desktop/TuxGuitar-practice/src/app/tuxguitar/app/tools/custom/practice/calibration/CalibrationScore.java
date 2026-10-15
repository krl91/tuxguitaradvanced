package app.tuxguitar.app.tools.custom.practice.calibration;

import app.tuxguitar.audio.engine.calibration.RecognitionRate;

/** What one candidate settings scored on the recording (spec 013, contract scoring-search). */
public class CalibrationScore {

	private final int correct;
	private final int wrong;
	private final RecognitionRate rate;

	CalibrationScore(int correct, int wrong, RecognitionRate rate) {
		this.correct = correct;
		this.wrong = wrong;
		this.rate = rate;
	}

	/** The sounds accepted for their own event. */
	public int getCorrect() {
		return this.correct;
	}

	/** The sounds accepted for the next event, which they must not match (the negative control). */
	public int getWrong() {
		return this.wrong;
	}

	/** The events recognised over the events of the piece, for the notes, the chords and together. */
	public RecognitionRate getRate() {
		return this.rate;
	}

	/** Higher is better: the sounds recognised, minus {@link CalibrationScorer#WRONG_WEIGHT} points for each sound accepted for a wrong event. */
	public double getObjective() {
		return this.correct - CalibrationScorer.WRONG_WEIGHT * this.wrong;
	}
}
