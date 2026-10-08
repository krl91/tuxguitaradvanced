package app.tuxguitar.app.tools.custom.practice.session;

import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.audio.engine.settings.RecognitionSettingsStore;
import app.tuxguitar.util.TGContext;
import app.tuxguitar.util.singleton.TGSingletonFactory;
import app.tuxguitar.util.singleton.TGSingletonUtil;

/**
 * What the learner chooses in the preparation window for the tempo-following mode (spec 002 T098, FR-051, FR-052): the mode, the percentage of the
 * written tempo and the timing tolerance. Kept in memory for the application run (one instance per {@link TGContext}), like the passage choice;
 * the tolerance starts from the one saved in the Configuration window ({@link RecognitionParameters#TEMPO_TOLERANCE}) and returns to it whenever
 * that one changes. The mode starts as the wait mode: nothing changes for a learner who never opens this choice.
 * <p>
 * Each value is its own: changing one never changes another.
 */
public final class FollowSettings {

	public enum Mode {
		/** The score waits for the right note (the mode that was there first). */
		WAIT,
		/** The score plays at its tempo and the notes are judged on time. */
		FOLLOW,
		/** Arcade (spec 003): the tempo-following run on the track the learner chose, with the other tracks as the backing and the isometric view. */
		ARCADE
	}

	public static final int MIN_PERCENT = 20;
	public static final int MAX_PERCENT = 150;
	public static final int DEFAULT_PERCENT = 100;

	private final RecognitionSettingsStore store;
	private Mode mode = Mode.WAIT;
	private int percent = DEFAULT_PERCENT;
	private TimingTolerance.Preset tolerance;

	FollowSettings(RecognitionSettingsStore store) {
		this.store = store;
		resetToleranceTo(store.get());
		store.addListener((previous, current) -> resetToleranceTo(current));
	}

	/** For the tests and for a window that has no store: the tolerance is the normal one until it is set. */
	FollowSettings() {
		this.store = null;
		this.tolerance = TimingTolerance.Preset.NORMAL;
	}

	public static FollowSettings getInstance(TGContext context) {
		return TGSingletonUtil.getInstance(context, FollowSettings.class.getName(), new TGSingletonFactory<FollowSettings>() {
			@Override
			public FollowSettings createInstance(TGContext singletonContext) {
				return new FollowSettings(RecognitionSettingsStore.getInstance(singletonContext));
			}
		});
	}

	private synchronized void resetToleranceTo(RecognitionSettings settings) {
		this.tolerance = presetOf(settings.get(RecognitionParameters.TEMPO_TOLERANCE));
	}

	/** The preset named by the saved setting ({@code strict}, {@code normal}, {@code lenient}); the normal one for anything else. */
	public static TimingTolerance.Preset presetOf(String name) {
		if (RecognitionParameters.TOLERANCE_STRICT.equals(name)) {
			return TimingTolerance.Preset.STRICT;
		}
		if (RecognitionParameters.TOLERANCE_LENIENT.equals(name)) {
			return TimingTolerance.Preset.LENIENT;
		}
		return TimingTolerance.Preset.NORMAL;
	}

	public synchronized Mode getMode() {
		return this.mode;
	}

	public synchronized void setMode(Mode mode) {
		this.mode = (mode != null ? mode : Mode.WAIT);
	}

	public synchronized int getPercent() {
		return this.percent;
	}

	/** The percentage, kept within the bounds. */
	public synchronized int setPercent(int percent) {
		this.percent = Math.max(MIN_PERCENT, Math.min(MAX_PERCENT, percent));
		return this.percent;
	}

	public synchronized TimingTolerance.Preset getTolerance() {
		return this.tolerance;
	}

	public synchronized void setTolerance(TimingTolerance.Preset preset) {
		this.tolerance = (preset != null ? preset : TimingTolerance.Preset.NORMAL);
	}

	public synchronized boolean isFollow() {
		return this.mode == Mode.FOLLOW;
	}

	public synchronized boolean isArcade() {
		return this.mode == Mode.ARCADE;
	}

	/** True for the modes that play the score at its tempo and judge on time: they share the percentage, the tolerance and the calibration. */
	public synchronized boolean isTimed() {
		return this.mode == Mode.FOLLOW || this.mode == Mode.ARCADE;
	}
}
