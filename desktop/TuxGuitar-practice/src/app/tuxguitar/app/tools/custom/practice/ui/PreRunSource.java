package app.tuxguitar.app.tools.custom.practice.ui;

import app.tuxguitar.audio.engine.analysis.StrumClipMeter;

/** What the start window reads from the audio input (spec 010): the verdict of the strum, the live level, and the means to start again; a fake in the tests. */
interface PreRunSource {

	/** The last finished window of the strum meter (no strum until one is judged). */
	StrumClipMeter.Reading reading();

	/** The live level in dBFS for the bar, or NaN when nothing arrives. */
	double levelDbfs();

	/** True when the application's own gain is above 1: a clip may come from that boost and not from the interface. */
	boolean boostActive();

	/** Starts the strum meter again: the verdict is hidden at once and a strum heard before does not count. */
	void reset();

	/** Called, on the user-interface thread, when something changed (about thirty times a second at most); the window then refreshes. */
	default void setChangeListener(Runnable listener) {
	}

	/** Gives the listeners back. */
	void close();
}
