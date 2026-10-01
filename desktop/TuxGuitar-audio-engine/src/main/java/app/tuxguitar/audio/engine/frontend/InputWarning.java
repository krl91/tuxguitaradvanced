package app.tuxguitar.audio.engine.frontend;

/** A problem the learner should be told about when the input gain is raised (spec 002, FR-062). */
public enum InputWarning {
	NONE,
	/** The boost mostly amplifies background noise: raise the gain on the interface or use a direct input. */
	BOOST_AMPLIFIES_NOISE,
	/** The boost makes the signal clip: lower the gain. */
	BOOST_CAUSES_CLIPPING
}
