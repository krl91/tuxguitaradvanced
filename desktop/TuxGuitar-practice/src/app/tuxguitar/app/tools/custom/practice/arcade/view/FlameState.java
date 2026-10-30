package app.tuxguitar.app.tools.custom.practice.arcade.view;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import app.tuxguitar.app.tools.custom.practice.session.FlameSetting;

/**
 * What the scene needs to draw the stage flames of one picture (spec 009 FR-001, FR-013): the setting, the level 0..1 of the flames, the flame clock
 * that moves their sway, and whether they may move at all; and the bursts of sparks of the right notes of the last second (spec 018). Immutable; it never
 * comes from the judging.
 */
public final class FlameState {

	/** A right note, for the sparks (spec 018 FR-005): the flame clock when it was judged right, and the streak then. */
	public static final class Burst {
		private final long clockNanos;
		private final int streak;

		public Burst(long clockNanos, int streak) {
			this.clockNanos = clockNanos;
			this.streak = Math.max(1, streak);
		}

		public long clockNanos() {
			return this.clockNanos;
		}

		public int streak() {
			return this.streak;
		}
	}

	private final FlameSetting setting;
	private final double level;
	private final long flameClockNanos;
	private final boolean motion;
	private final List<Burst> bursts;

	/**
	 * @param setting the setting of the run; null counts as off
	 * @param level the level of the flames; outside 0..1 it is clamped
	 * @param flameClockNanos the monotonic flame clock, not the instant of the picture
	 * @param motion false when the system asks to reduce motion: the sway is then frozen and only the height follows the play
	 */
	public FlameState(FlameSetting setting, double level, long flameClockNanos, boolean motion) {
		this(setting, level, flameClockNanos, motion, null);
	}

	/**
	 * @param bursts the right notes of the last second, oldest first; null for none
	 */
	public FlameState(FlameSetting setting, double level, long flameClockNanos, boolean motion, List<Burst> bursts) {
		this.setting = (setting != null ? setting : FlameSetting.OFF);
		this.level = (Double.isNaN(level) ? 0.0 : Math.max(0.0, Math.min(1.0, level)));
		this.flameClockNanos = flameClockNanos;
		this.motion = motion;
		this.bursts = (bursts != null && !bursts.isEmpty() ? Collections.unmodifiableList(new ArrayList<Burst>(bursts)) : Collections.<Burst>emptyList());
	}

	public FlameSetting setting() {
		return this.setting;
	}

	public double level() {
		return this.level;
	}

	public long flameClockNanos() {
		return this.flameClockNanos;
	}

	public boolean motion() {
		return this.motion;
	}

	/** The bursts of sparks, oldest first; never null. */
	public List<Burst> bursts() {
		return this.bursts;
	}
}
