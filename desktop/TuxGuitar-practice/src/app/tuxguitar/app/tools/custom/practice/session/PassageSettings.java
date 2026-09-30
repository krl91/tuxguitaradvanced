package app.tuxguitar.app.tools.custom.practice.session;

import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.audio.engine.settings.RecognitionSettingsStore;
import app.tuxguitar.util.TGContext;
import app.tuxguitar.util.singleton.TGSingletonFactory;
import app.tuxguitar.util.singleton.TGSingletonUtil;

/**
 * The learner's passage-retry choice from the preparation window (owner decision 2026-09-28,
 * FR-091): Off, or a passage length of 1, 2 or 4 measures with an error threshold of 25, 50 or
 * 75 %. Kept in memory for the application run only (one instance per {@link TGContext}); never
 * saved. Off practises straight through, as before passages existed.
 *
 * <p>Since T102 the Configuration window holds the persistent values ({@link RecognitionSettingsStore}):
 * this run choice starts from them, is reset to them whenever they change, and takes the retry
 * limit from them.</p>
 */
public final class PassageSettings {

	/** 0 = Off. */
	public static final int[] MEASURE_OPTIONS = { 0, 1, 2, 4 };
	public static final int[] THRESHOLD_PERCENT_OPTIONS = { 25, 50, 75 };
	public static final int OFF = 0;

	private final RecognitionSettingsStore store;
	private int measures;
	private int thresholdPercent;

	PassageSettings(RecognitionSettingsStore store) {
		this.store = store;
		resetTo(store.get());
		store.addListener((previous, current) -> resetTo(current));
	}

	/** The persistent Configuration values replace the run choice. */
	private synchronized void resetTo(RecognitionSettings settings) {
		this.measures = settings.getPassageMeasures();
		this.thresholdPercent = settings.getPassageThresholdPercent();
	}

	public static PassageSettings getInstance(TGContext context) {
		return TGSingletonUtil.getInstance(context, PassageSettings.class.getName(), new TGSingletonFactory<PassageSettings>() {
			@Override
			public PassageSettings createInstance(TGContext singletonContext) {
				return new PassageSettings(RecognitionSettingsStore.getInstance(singletonContext));
			}
		});
	}

	/** Passage length in measures, or {@link #OFF}. */
	public synchronized int getMeasures() {
		return this.measures;
	}

	public synchronized void setMeasures(int measures) {
		this.measures = checked(measures, MEASURE_OPTIONS);
	}

	public synchronized int getThresholdPercent() {
		return this.thresholdPercent;
	}

	public synchronized void setThresholdPercent(int thresholdPercent) {
		this.thresholdPercent = checked(thresholdPercent, THRESHOLD_PERCENT_OPTIONS);
	}

	public synchronized boolean isOff() {
		return this.measures == OFF;
	}

	/** The policy for a new session, or null when Off (no passages, no retries, no summary). */
	public synchronized PassagePolicy toPolicy() {
		if( this.measures == OFF ) {
			return null;
		}
		return new PassagePolicy(this.measures, this.thresholdPercent / 100.0, this.store.get().getPassageRetries());
	}

	private static int checked(int value, int[] options) {
		for( int option : options ) {
			if( option == value ) {
				return value;
			}
		}
		throw new IllegalArgumentException("unsupported value " + value);
	}
}
