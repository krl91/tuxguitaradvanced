package app.tuxguitar.app.tools.custom.practice.ui;

import java.util.Optional;

import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.frontend.GainSuggestion;
import app.tuxguitar.audio.engine.frontend.InputWarning;

/**
 * What the gain control needs from the audio engine and the settings. A seam: the application plugs
 * in the real engine ({@link EngineGainBackend}), the tests a stub.
 */
interface GainBackend {

	double getGain();

	/** Sets the gain (limited to the allowed range) and returns the value that was set. */
	double setGain(double multiplier);

	void resetGain();

	Optional<GainSuggestion> suggest();

	InputWarning warning(AnalysisFrame latest);

	/** Whether the "suggest a gain" setting is on. */
	boolean suggestionsEnabled();

	/** Tells {@code listener} when settings change; returns the action that stops it. */
	Runnable onSettingsChanged(Runnable listener);
}
