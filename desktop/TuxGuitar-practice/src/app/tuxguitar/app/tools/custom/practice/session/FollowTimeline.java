package app.tuxguitar.app.tools.custom.practice.session;

import java.util.ArrayList;
import java.util.List;

import app.tuxguitar.app.tools.custom.practice.model.PracticeEvent;
import app.tuxguitar.app.tools.custom.practice.model.PracticeSequence;
import app.tuxguitar.player.base.MidiRepeatController;
import app.tuxguitar.song.models.TGDuration;
import app.tuxguitar.song.models.TGMeasureHeader;
import app.tuxguitar.song.models.TGSong;

/**
 * When each event of a {@link PracticeSequence} is expected, for a tempo percentage (spec 002 T094, T087): the target times from the start of the first
 * played measure, built from the tempo of each measure (a measure has one tempo in the model), in the order of the player's repeats and alternate endings
 * ({@link MidiRepeatController}, as the sequence itself), every measure taking its time, the empty ones too. The local beat is the quarter note at the tempo of
 * the event's measure and the percentage.
 * <p>
 * Besides the time, each event has its <b>tick</b>: the expanded position the player will reach (its repeats are expanded: the ticks go on, they do not
 * rewind), relative to the first measure. The player clock turns it into wall time; the ticks do not depend on the percentage.
 * <p>
 * Immutable: rebuilt when the percentage changes.
 */
public final class FollowTimeline {

	private final int percent;
	private final long[] targetNanos;
	private final long[] targetTicks;
	private final long[] beatNanos;
	private final long durationNanos;

	private FollowTimeline(int percent, long[] targetNanos, long[] targetTicks, long[] beatNanos, long durationNanos) {
		this.percent = percent;
		this.targetNanos = targetNanos;
		this.targetTicks = targetTicks;
		this.beatNanos = beatNanos;
		this.durationNanos = durationNanos;
	}

	/** One played occurrence of a measure. */
	private static final class Occurrence {
		final long startNanos;
		final long startTicks;
		final long measureStartTick;
		final double quarterNanos;

		Occurrence(long startNanos, long startTicks, long measureStartTick, double quarterNanos) {
			this.startNanos = startNanos;
			this.startTicks = startTicks;
			this.measureStartTick = measureStartTick;
			this.quarterNanos = quarterNanos;
		}
	}

	/**
	 * @param song the song whose tempo and repeats the player follows
	 * @param sequence the sequence built from it (its events carry the index of the played measure)
	 * @param percent the tempo percentage chosen by the learner, above 0
	 */
	public static FollowTimeline build(TGSong song, PracticeSequence sequence, int percent) {
		if (percent <= 0) {
			throw new IllegalArgumentException("the tempo percentage must be above 0: " + percent);
		}
		List<Occurrence> played = new ArrayList<Occurrence>();
		MidiRepeatController controller = new MidiRepeatController(song, -1, -1);
		long nanos = 0;
		long ticks = 0;
		while (!controller.finished()) {
			int measureIndex = controller.getIndex();
			controller.process();
			if (controller.shouldPlay()) {
				TGMeasureHeader header = song.getMeasureHeader(measureIndex);
				double quarterNanos = 60e9 / (header.getTempo().getQuarterValue() * percent / 100.0);
				played.add(new Occurrence(nanos, ticks, header.getStart(), quarterNanos));
				nanos += Math.round(header.getLength() * quarterNanos / TGDuration.QUARTER_TIME);
				ticks += header.getLength();
			}
		}
		List<PracticeEvent> events = sequence.getEvents();
		long[] targetNanos = new long[events.size()];
		long[] targetTicks = new long[events.size()];
		long[] beatNanos = new long[events.size()];
		for (int i = 0; i < events.size(); i++) {
			PracticeEvent event = events.get(i);
			Occurrence occurrence = played.get(event.getPlayIndex());
			long offsetTicks = event.getBeat().getStart() - occurrence.measureStartTick;
			targetNanos[i] = occurrence.startNanos + Math.round(offsetTicks * occurrence.quarterNanos / TGDuration.QUARTER_TIME);
			targetTicks[i] = occurrence.startTicks + offsetTicks;
			beatNanos[i] = Math.round(occurrence.quarterNanos);
		}
		return new FollowTimeline(percent, targetNanos, targetTicks, beatNanos, nanos);
	}

	public int percent() {
		return this.percent;
	}

	/** The number of events: the same as the sequence it was built from, with the same indexes. */
	public int size() {
		return this.targetNanos.length;
	}

	public long targetNanos(int eventIndex) {
		return this.targetNanos[eventIndex];
	}

	/** The expanded tick of the event, from the first played measure (the player's own offset of its first measure is not included). */
	public long targetTicks(int eventIndex) {
		return this.targetTicks[eventIndex];
	}

	/** The local beat (a quarter note) in nanoseconds, at the tempo of the event's measure and the percentage. */
	public long beatNanos(int eventIndex) {
		return this.beatNanos[eventIndex];
	}

	/** The whole played length, the empty measures included. */
	public long durationNanos() {
		return this.durationNanos;
	}

	/** The distance to the nearest previous event of another time, or {@link Long#MAX_VALUE} when there is none. */
	public long gapBeforeNanos(int eventIndex) {
		long time = this.targetNanos[eventIndex];
		for (int i = eventIndex - 1; i >= 0; i--) {
			if (this.targetNanos[i] < time) {
				return time - this.targetNanos[i];
			}
		}
		return Long.MAX_VALUE;
	}

	/** The distance to the nearest next event of another time, or {@link Long#MAX_VALUE} when there is none. */
	public long gapAfterNanos(int eventIndex) {
		long time = this.targetNanos[eventIndex];
		for (int i = eventIndex + 1; i < this.targetNanos.length; i++) {
			if (this.targetNanos[i] > time) {
				return this.targetNanos[i] - time;
			}
		}
		return Long.MAX_VALUE;
	}
}
