package app.tuxguitar.app.tools.custom.practice.model;

/** Test access to the package-private sequence constructor: the same events, starting elsewhere. */
public final class TestSequences {

	private TestSequences() {
	}

	public static PracticeSequence startingAt(PracticeSequence sequence, int startIndex) {
		return new PracticeSequence(sequence.getEvents(), startIndex, sequence.getTrackTuning());
	}
}
