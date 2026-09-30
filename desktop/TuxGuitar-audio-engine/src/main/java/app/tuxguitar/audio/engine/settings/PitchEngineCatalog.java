package app.tuxguitar.audio.engine.settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import app.tuxguitar.audio.engine.analysis.PitchEstimator;
import app.tuxguitar.audio.engine.analysis.TarsosFastYinEstimator;

/**
 * The pitch-detection engines that really exist (task T102): today only FastYin (TarsosDSP).
 * The setting offers "Automatic", which resolves to the recommended engine; a future engine is
 * added here as another {@link Engine} and becomes an extra choice. Nothing is listed that the
 * code cannot run.
 */
public final class PitchEngineCatalog {

	/** The only choice while a single engine exists: the recommended engine. */
	public static final String AUTOMATIC = "automatic";
	public static final String FASTYIN = "fastyin";

	/** One available engine. */
	public interface Engine {
		/** Stable identifier stored in the configuration. */
		String getId();

		/** Display name (not translated: a product name). */
		String getName();

		/** A new estimator for this sample rate, with the standard analysis window and hop. */
		PitchEstimator create(int sampleRate);
	}

	private static final Engine FASTYIN_ENGINE = new Engine() {
		public String getId() {
			return FASTYIN;
		}

		public String getName() {
			return "FastYin";
		}

		public PitchEstimator create(int sampleRate) {
			return new TarsosFastYinEstimator(sampleRate, TarsosFastYinEstimator.DEFAULT_WINDOW_SIZE, TarsosFastYinEstimator.DEFAULT_HOP_SIZE);
		}
	};

	private static final List<Engine> ENGINES = Collections.unmodifiableList(new ArrayList<Engine>(Collections.singletonList(FASTYIN_ENGINE)));

	private PitchEngineCatalog() {
	}

	/** Every engine the code can run, the recommended one first. */
	public static List<Engine> getEngines() {
		return ENGINES;
	}

	public static Engine getRecommended() {
		return ENGINES.get(0);
	}

	/** The engine for a stored choice; {@link #AUTOMATIC} and unknown identifiers give the recommended one. */
	public static Engine resolve(String id) {
		for( Engine engine : ENGINES ) {
			if( engine.getId().equals(id) ) {
				return engine;
			}
		}
		return getRecommended();
	}
}
