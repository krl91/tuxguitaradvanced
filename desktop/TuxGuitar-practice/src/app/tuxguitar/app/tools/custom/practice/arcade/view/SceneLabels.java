package app.tuxguitar.app.tools.custom.practice.arcade.view;

import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeVerdict;

/** The words of a scene, in the learner's language: the scene asks, it never holds text of its own (the texts live in the language files). */
public interface SceneLabels {

	/** The word of a verdict ("On time", "Not judged" ...). */
	String verdict(ArcadeVerdict verdict);

	/** The notes of a chord that no conventional name describes: "Notes: A–C–F♯" for the list {@code A–C–F♯}. */
	String pitchSet(String notes);

	/** An event the mode shows as automatic (it is passed without being judged). */
	String automatic();

	/** An event the mode cannot present as a recognised chord. */
	String unsupported();

	/** "Missing: E2 G2" for the names given. */
	String missing(String names);

	/** The note of a chord two notes of which share a pitch. */
	String notCheckedSeparately();

	String streak(int streak);

	String speed(int percent);
}
