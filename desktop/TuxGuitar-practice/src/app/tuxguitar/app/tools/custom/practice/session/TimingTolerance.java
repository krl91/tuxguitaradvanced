package app.tuxguitar.app.tools.custom.practice.session;

/**
 * The tolerance the learner chooses for the timing of the tempo-following mode (spec 002 T094, FR-052): three presets, each giving the windows
 * around the target time of an event as <b>a fraction of the local beat with a floor and a ceiling in milliseconds</b>, so that the same preset works
 * at 60 and at 180 beats a minute. The beat is the quarter note at the tempo of the measure and the chosen percentage.
 * <p>
 * The <b>tight window</b> gives ON_TIME, the <b>outer window</b> EARLY (before the tight one) or LATE (after it); beyond it nothing matches. The outer window
 * is cut at the middle of the gap to the neighbouring events, so that two windows never overlap and no nanosecond belongs to nobody (a fast passage of
 * eight notes a second leaves 125 ms between events).
 * <p>
 * This is only the learner's choice. The latency of the route ({@link LatencyCompensation}) and the uncertainty of the clocks ({@link ClockUncertainty})
 * are other things and never enter a window.
 * <p>
 * <b>The numbers are provisional</b> until the benchmark T101 and real validation: they were chosen so that at 120 beats a minute a normal tight window is
 * 75 ms each side, and are pinned by a test only so that changing one is a decision.
 */
public final class TimingTolerance {

	public enum Preset {
		//          tight: fraction floor ceil    outer early/late: fraction floor ceil   (milliseconds)
		STRICT(0.10, 40, 100, 0.25, 0.25, 100, 300),
		NORMAL(0.15, 60, 150, 0.40, 0.40, 150, 450),
		LENIENT(0.25, 90, 250, 0.60, 0.60, 220, 700);

		final double onTimeFraction;
		final long onTimeFloorMillis;
		final long onTimeCeilingMillis;
		final double earlyFraction;
		final double lateFraction;
		final long outerFloorMillis;
		final long outerCeilingMillis;

		Preset(double onTimeFraction, long onTimeFloorMillis, long onTimeCeilingMillis, double earlyFraction, double lateFraction,
				long outerFloorMillis, long outerCeilingMillis) {
			this.onTimeFraction = onTimeFraction;
			this.onTimeFloorMillis = onTimeFloorMillis;
			this.onTimeCeilingMillis = onTimeCeilingMillis;
			this.earlyFraction = earlyFraction;
			this.lateFraction = lateFraction;
			this.outerFloorMillis = outerFloorMillis;
			this.outerCeilingMillis = outerCeilingMillis;
		}
	}

	private static final long MS = 1_000_000L;

	private final Preset preset;

	public TimingTolerance(Preset preset) {
		if (preset == null) {
			throw new IllegalArgumentException("a preset is required");
		}
		this.preset = preset;
	}

	public Preset preset() {
		return this.preset;
	}

	/** The windows of the event {@code eventIndex} of the timeline. */
	public Window windowFor(int eventIndex, FollowTimeline timeline) {
		return windowFor(timeline.targetNanos(eventIndex), timeline.beatNanos(eventIndex), timeline.gapBeforeNanos(eventIndex), timeline.gapAfterNanos(eventIndex));
	}

	/**
	 * @param targetNanos the target time of the event
	 * @param beatNanos the local beat in nanoseconds (0 for a broken tempo: the floors apply)
	 * @param gapBeforeNanos the distance to the previous event of another time, {@link Long#MAX_VALUE} when there is none
	 * @param gapAfterNanos the distance to the next event of another time, {@link Long#MAX_VALUE} when there is none
	 */
	public Window windowFor(long targetNanos, long beatNanos, long gapBeforeNanos, long gapAfterNanos) {
		if (beatNanos < 0) {
			throw new IllegalArgumentException("a beat cannot be negative: " + beatNanos);
		}
		long on = clamp(Math.round(this.preset.onTimeFraction * beatNanos), this.preset.onTimeFloorMillis * MS, this.preset.onTimeCeilingMillis * MS);
		long early = clamp(Math.round(this.preset.earlyFraction * beatNanos), this.preset.outerFloorMillis * MS, this.preset.outerCeilingMillis * MS);
		long late = clamp(Math.round(this.preset.lateFraction * beatNanos), this.preset.outerFloorMillis * MS, this.preset.outerCeilingMillis * MS);
		early = Math.max(early, on);
		late = Math.max(late, on);
		// cut in the middle of the gap to the neighbours: the earlier window ends one nanosecond before the later one begins
		if (gapBeforeNanos != Long.MAX_VALUE) {
			early = Math.min(early, gapBeforeNanos / 2);
		}
		if (gapAfterNanos != Long.MAX_VALUE) {
			late = Math.min(late, (gapAfterNanos - 1) / 2);
		}
		return new Window(targetNanos, targetNanos - Math.min(on, early), targetNanos + Math.min(on, late), targetNanos - early, targetNanos + late);
	}

	private static long clamp(long value, long floor, long ceiling) {
		return Math.max(floor, Math.min(ceiling, value));
	}

	/** The windows of one event, in nanoseconds on the scale of its target time; the limits are inclusive. Immutable. */
	public static final class Window {
		private final long target;
		private final long onTimeFrom;
		private final long onTimeTo;
		private final long outerFrom;
		private final long outerTo;

		Window(long target, long onTimeFrom, long onTimeTo, long outerFrom, long outerTo) {
			this.target = target;
			this.onTimeFrom = onTimeFrom;
			this.onTimeTo = onTimeTo;
			this.outerFrom = outerFrom;
			this.outerTo = outerTo;
		}

		public long target() {
			return this.target;
		}

		public long onTimeFrom() {
			return this.onTimeFrom;
		}

		public long onTimeTo() {
			return this.onTimeTo;
		}

		public long outerFrom() {
			return this.outerFrom;
		}

		public long outerTo() {
			return this.outerTo;
		}

		/**
		 * ON_TIME inside the tight window, EARLY from the start of the outer window to the start of the tight one, LATE from the end of the tight one
		 * to the end of the outer one (all limits included); null outside the outer window. The time is the compensated time of the onset.
		 */
		public TimingVerdict classify(long adjustedNanos) {
			if (adjustedNanos < this.outerFrom || adjustedNanos > this.outerTo) {
				return null;
			}
			if (adjustedNanos < this.onTimeFrom) {
				return TimingVerdict.EARLY;
			}
			if (adjustedNanos > this.onTimeTo) {
				return TimingVerdict.LATE;
			}
			return TimingVerdict.ON_TIME;
		}

		/** Signed offset of a compensated onset from the target (negative: early). */
		public long offsetNanos(long adjustedNanos) {
			return adjustedNanos - this.target;
		}
	}
}
