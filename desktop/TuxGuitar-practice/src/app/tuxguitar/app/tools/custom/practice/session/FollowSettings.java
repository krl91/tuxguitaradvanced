package app.tuxguitar.app.tools.custom.practice.session;

import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettingsStore;
import app.tuxguitar.util.TGContext;
import app.tuxguitar.util.singleton.TGSingletonFactory;
import app.tuxguitar.util.singleton.TGSingletonUtil;

/**
 * What the learner chooses in the preparation window for the tempo-following mode (spec 002 T098, FR-051, FR-052): the mode, the percentage of the
 * written tempo and the timing window. Kept in memory for the application run (one instance per {@link TGContext}), like the passage choice, except the
 * <b>window</b>, which is one saved setting (spec 012): it lives in the recognition store ({@link RecognitionParameters#TEMPO_WINDOW}), what the learner sets here is
 * saved at once and is what the Configuration window shows. The mode starts as the wait mode: nothing changes for a learner who never opens this choice.
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

	/** The default window, in milliseconds each side at 120 beats a minute: a third more lenient than the old Normal (75 ms). */
	public static final int DEFAULT_WINDOW_MILLIS = 100;
	public static final int MIN_PERCENT = 20;
	public static final int MAX_PERCENT = 150;
	public static final int DEFAULT_PERCENT = 100;

	/** The size of the discs of the incoming notes in Arcade, in percent of the normal size. */
	public static final int MIN_MARKER_PERCENT = 0;
	public static final int MAX_MARKER_PERCENT = 150;
	public static final int DEFAULT_MARKER_PERCENT = 100;

	/** The tilt of the Arcade plane (owner, 2026-10-11): the scale at the horizon and the height of the horizon, in percent. The defaults are the owner's choice. */
	public static final int MIN_FAR_SCALE_PERCENT = 20;
	public static final int MAX_FAR_SCALE_PERCENT = 100;
	public static final int DEFAULT_FAR_SCALE_PERCENT = 35;
	public static final int MIN_HORIZON_PERCENT = 0;
	public static final int MAX_HORIZON_PERCENT = 40;
	public static final int DEFAULT_HORIZON_PERCENT = 18;

	private final RecognitionSettingsStore store;
	private int farScalePercent = DEFAULT_FAR_SCALE_PERCENT;
	private int horizonPercent = DEFAULT_HORIZON_PERCENT;
	private int markerPercent = DEFAULT_MARKER_PERCENT;
	private FlameSetting flames = FlameSetting.DISCREET;
	private Mode mode = Mode.WAIT;
	private int percent = DEFAULT_PERCENT;
	/** The window of a settings object that has no store (tests, a window without one). */
	private int ownWindowMillis = DEFAULT_WINDOW_MILLIS;

	FollowSettings(RecognitionSettingsStore store) {
		this.store = store;
	}

	/** For the tests and for a window that has no store: the window is the default one until it is set. */
	FollowSettings() {
		this.store = null;
	}

	public static FollowSettings getInstance(TGContext context) {
		return TGSingletonUtil.getInstance(context, FollowSettings.class.getName(), new TGSingletonFactory<FollowSettings>() {
			@Override
			public FollowSettings createInstance(TGContext singletonContext) {
				return new FollowSettings(RecognitionSettingsStore.getInstance(singletonContext));
			}
		});
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

	public synchronized int getMarkerPercent() {
		return this.markerPercent;
	}

	/** The size of the Arcade discs in percent, kept within the bounds. */
	public synchronized int setMarkerPercent(int percent) {
		this.markerPercent = Math.max(MIN_MARKER_PERCENT, Math.min(MAX_MARKER_PERCENT, percent));
		return this.markerPercent;
	}

	public synchronized int getFarScalePercent() {
		return this.farScalePercent;
	}

	/** The scale at the horizon in percent (the lower, the more the plane is tilted), kept within the bounds. */
	public synchronized int setFarScalePercent(int percent) {
		this.farScalePercent = Math.max(MIN_FAR_SCALE_PERCENT, Math.min(MAX_FAR_SCALE_PERCENT, percent));
		return this.farScalePercent;
	}

	public synchronized FlameSetting getFlames() {
		return this.flames;
	}

	/** The stage flames of the Arcade picture (spec 009 FR-008); read when a run starts. A missing value is refused. */
	public synchronized void setFlames(FlameSetting flames) {
		if (flames == null) {
			throw new IllegalArgumentException("flames");
		}
		this.flames = flames;
	}

	public synchronized int getHorizonPercent() {
		return this.horizonPercent;
	}

	/** The height of the horizon in percent of the height of the picture, kept within the bounds. */
	public synchronized int setHorizonPercent(int percent) {
		this.horizonPercent = Math.max(MIN_HORIZON_PERCENT, Math.min(MAX_HORIZON_PERCENT, percent));
		return this.horizonPercent;
	}

	/** The half-width of the on-time window in milliseconds at 120 beats a minute (spec 012), the saved one; it is read once when a run is prepared. */
	public int getWindowMillis() {
		if (this.store == null) {
			synchronized (this) {
				return this.ownWindowMillis;
			}
		}
		return (int) Math.round(this.store.get().get(RecognitionParameters.TEMPO_WINDOW));
	}

	/** Sets the window, brought into its range, and saves it at once as the one setting that the Configuration window shows too. */
	public void setWindowMillis(int millis) {
		int bounded = Math.max(TimingTolerance.MIN_WIDTH_MILLIS, Math.min(TimingTolerance.MAX_WIDTH_MILLIS, millis));
		if (this.store == null) {
			synchronized (this) {
				this.ownWindowMillis = bounded;
			}
			return;
		}
		// the default layer: with a device profile in force, get() holds the profile's values, which must not be written into the default (spec 013)
		this.store.update(this.store.getDefault().with(RecognitionParameters.TEMPO_WINDOW, (double) bounded));
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
