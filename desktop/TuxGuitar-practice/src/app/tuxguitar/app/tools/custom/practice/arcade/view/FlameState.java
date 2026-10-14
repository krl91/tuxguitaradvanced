package app.tuxguitar.app.tools.custom.practice.arcade.view;

import app.tuxguitar.app.tools.custom.practice.session.FlameSetting;

/**
 * What the scene needs to draw the stage flames of one picture (spec 009 FR-001, FR-013): the setting, the level 0..1 of the flames, the flame clock
 * that moves their sway, and whether they may move at all. Immutable; it never comes from the judging.
 */
public final class FlameState {

	private final FlameSetting setting;
	private final double level;
	private final long flameClockNanos;
	private final boolean motion;

	/**
	 * @param setting the setting of the run; null counts as off
	 * @param level the level of the flames; outside 0..1 it is clamped
	 * @param flameClockNanos the monotonic flame clock, not the instant of the picture
	 * @param motion false when the system asks to reduce motion: the sway is then frozen and only the height follows the play
	 */
	public FlameState(FlameSetting setting, double level, long flameClockNanos, boolean motion) {
		this.setting = (setting != null ? setting : FlameSetting.OFF);
		this.level = (Double.isNaN(level) ? 0.0 : Math.max(0.0, Math.min(1.0, level)));
		this.flameClockNanos = flameClockNanos;
		this.motion = motion;
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
}
