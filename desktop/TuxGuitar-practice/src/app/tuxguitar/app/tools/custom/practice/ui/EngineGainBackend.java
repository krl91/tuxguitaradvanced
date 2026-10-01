package app.tuxguitar.app.tools.custom.practice.ui;

import java.util.Optional;

import app.tuxguitar.audio.engine.AudioEngine;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.frontend.GainSuggestion;
import app.tuxguitar.audio.engine.frontend.InputWarning;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettingsStore;
import app.tuxguitar.util.TGContext;

/** The real engine and the saved settings behind the gain control. */
final class EngineGainBackend implements GainBackend {

	private final AudioEngine engine;
	private final RecognitionSettingsStore store;

	EngineGainBackend(TGContext context, AudioEngine engine) {
		this.engine = engine;
		this.store = RecognitionSettingsStore.getInstance(context);
	}

	public double getGain() {
		return this.engine.getInputGain();
	}

	public double setGain(double multiplier) {
		return this.engine.setInputGain(multiplier);
	}

	public void resetGain() {
		this.engine.resetInputGain();
	}

	public Optional<GainSuggestion> suggest() {
		return this.engine.suggestGain();
	}

	public InputWarning warning(AnalysisFrame latest) {
		return this.engine.getInputWarning(latest);
	}

	public boolean suggestionsEnabled() {
		return this.store.get().get(RecognitionParameters.INPUT_GAIN_SUGGESTION);
	}

	public Runnable onSettingsChanged(Runnable listener) {
		RecognitionSettingsStore.Listener wrapped = (previous, current) -> listener.run();
		this.store.addListener(wrapped);
		return () -> this.store.removeListener(wrapped);
	}
}
