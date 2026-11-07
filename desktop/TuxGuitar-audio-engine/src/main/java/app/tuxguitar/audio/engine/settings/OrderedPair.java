package app.tuxguitar.audio.engine.settings;

/**
 * Two settings that must stay ordered, lower ≤ upper (spec 026, data-model "Validation"): when they are not, the lower one is brought down to the
 * upper one, on reading (the repair reported) and on every change, as {@code note.accept} ≥ {@code tuner.in-tune} is.
 */
public final class OrderedPair {

	private final RecognitionParameter.Number lower;
	private final RecognitionParameter.Number upper;

	public OrderedPair(RecognitionParameter.Number lower, RecognitionParameter.Number upper) {
		this.lower = lower;
		this.upper = upper;
	}

	public RecognitionParameter.Number getLower() {
		return this.lower;
	}

	public RecognitionParameter.Number getUpper() {
		return this.upper;
	}

	@Override
	public String toString() {
		return this.lower.getKey() + " <= " + this.upper.getKey();
	}
}
