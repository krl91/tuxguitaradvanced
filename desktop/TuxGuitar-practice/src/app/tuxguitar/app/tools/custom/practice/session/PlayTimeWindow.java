package app.tuxguitar.app.tools.custom.practice.session;

/**
 * The time to play a note in Follow (spec 016 FR-011, contracts/play-time-window.md, the owner's rule): the window opens when the note turns red (its
 * target) and the note may be played up to the time to play after it, and up to 50 ms before it (then early). When the next note turns red sooner, the
 * window ends 20 ms before it, and the next note's advance is what that leaves, so two windows never overlap. On time for the first half of the time to
 * play. The beat changes nothing.
 */
public final class PlayTimeWindow implements WindowPolicy {

	public static final int MIN_MILLIS = 100;
	public static final int MAX_MILLIS = 1200;
	public static final int DEFAULT_MILLIS = 600;
	/** How long before its red a note may be played. */
	static final long ADVANCE_NANOS = 50_000_000L;
	/** What is left between the end of a window and the next red. */
	static final long GAP_NANOS = 20_000_000L;

	private final int playTimeMillis;
	private final long playTimeNanos;

	public PlayTimeWindow(int playTimeMillis) {
		if (playTimeMillis < MIN_MILLIS || playTimeMillis > MAX_MILLIS) {
			throw new IllegalArgumentException("a time to play is from " + MIN_MILLIS + " to " + MAX_MILLIS + " ms: " + playTimeMillis);
		}
		this.playTimeMillis = playTimeMillis;
		this.playTimeNanos = playTimeMillis * 1_000_000L;
	}

	public int playTimeMillis() {
		return this.playTimeMillis;
	}

	/** The late limit of an event whose next event is {@code gapAfterNanos} away. */
	private long late(long gapAfterNanos) {
		return gapAfterNanos == Long.MAX_VALUE ? this.playTimeNanos : Math.max(0L, Math.min(this.playTimeNanos, gapAfterNanos - GAP_NANOS));
	}

	@Override
	public TimingTolerance.Window windowFor(long targetNanos, long beatNanos, long gapBeforeNanos, long gapAfterNanos) {
		long late = late(gapAfterNanos);
		long early = ADVANCE_NANOS;
		if (gapBeforeNanos != Long.MAX_VALUE) {
			long previousLate = late(gapBeforeNanos);
			early = Math.max(0L, Math.min(ADVANCE_NANOS, gapBeforeNanos - previousLate - 1));
		}
		long onTimeTo = targetNanos + Math.min(late, this.playTimeNanos / 2);
		return new TimingTolerance.Window(targetNanos, targetNanos, onTimeTo, targetNanos - early, targetNanos + late);
	}
}
