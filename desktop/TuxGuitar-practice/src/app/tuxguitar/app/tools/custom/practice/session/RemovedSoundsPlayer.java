package app.tuxguitar.app.tools.custom.practice.session;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import app.tuxguitar.app.tools.custom.practice.model.PracticeEvent;
import app.tuxguitar.app.tools.custom.practice.model.PracticeSequence;
import app.tuxguitar.song.models.TGNote;
import app.tuxguitar.song.models.TGTrack;

/**
 * Plays the sounds that a level of Arcade removed from the player's track (spec 014, FR-015, research R10-bis), so that the music stays complete to the ear: the
 * player plays the kept sounds and hears the others. The track is muted in the player as a whole (a mute of the sequencer's track), so these sounds are sent by
 * hand, on the channel of the track (its instrument), straight to the output of the player, as the editor does to audition a beat; the sequencer's mute does not
 * reach them.
 * <p>
 * It is driven by the clock of the run: {@link #poll()} sends each sound whose tick has been reached since the last poll, once, a chord whole, and ends it after
 * its written duration at the tempo of the run. Nothing is sent before the clock is ready; while it is paused the notes that ring are ended and nothing is sent
 * (the run goes on from the clock, nothing is lost and nothing heard twice); a sound that is already in the past when it is looked at (a start in the middle of
 * the piece, a seek) is not played late. {@link #start()} polls from a thread of its own, never the analysis thread; {@link #stop()} silences and ends it.
 */
public final class RemovedSoundsPlayer {

	/** Where the notes go: the output of the player in the application, a list in a test. */
	public interface Output {
		void noteOn(int channel, int key, int velocity);

		void noteOff(int channel, int key);
	}

	/** One removed sound: the player's tick it is written at, its duration at the tempo of the run, and the notes to play on a channel. */
	public static final class Sound {
		private final double tick;
		private final long durationNanos;
		private final int channel;
		private final int[] keys;
		private final int velocity;

		public Sound(double tick, long durationNanos, int channel, int[] keys, int velocity) {
			this.tick = tick;
			this.durationNanos = durationNanos;
			this.channel = channel;
			this.keys = keys.clone();
			this.velocity = velocity;
		}

		public double tick() {
			return this.tick;
		}

		public long durationNanos() {
			return this.durationNanos;
		}

		public int channel() {
			return this.channel;
		}

		public int[] keys() {
			return this.keys.clone();
		}

		public int velocity() {
			return this.velocity;
		}
	}

	/** A sound already this far in the past when it is looked at is not played: late is worse than missing. */
	static final long LATE_TOLERANCE_NANOS = 250_000_000L;
	/** The polling step of the thread. */
	static final long POLL_MILLIS = 5L;
	private static final int DEFAULT_VELOCITY = 64;

	private static final class Ringing {
		final long offWall;
		final int channel;
		final int key;

		Ringing(long offWall, int channel, int key) {
			this.offWall = offWall;
			this.channel = channel;
			this.key = key;
		}
	}

	private final List<Sound> sounds;
	private final TempoClock clock;
	private final Output output;
	private final List<Ringing> ringing = new ArrayList<Ringing>();
	private int next;
	private boolean initialised;
	private boolean paused;
	private boolean stopped;
	private ScheduledExecutorService scheduler;

	RemovedSoundsPlayer(List<Sound> sounds, TempoClock clock, Output output) {
		List<Sound> ordered = new ArrayList<Sound>(sounds);
		Collections.sort(ordered, new Comparator<Sound>() {
			public int compare(Sound a, Sound b) {
				return Double.compare(a.tick, b.tick);
			}
		});
		this.sounds = ordered;
		this.clock = clock;
		this.output = output;
	}

	/** The player of these sounds, or null when there is nothing to play (level 5): nothing is created then. */
	public static RemovedSoundsPlayer create(List<Sound> sounds, TempoClock clock, Output output) {
		if (sounds == null || sounds.isEmpty()) {
			return null;
		}
		return new RemovedSoundsPlayer(sounds, clock, output);
	}

	/**
	 * The player of the sounds a level removed, for one run: their timeline is built on the sequence of the removed events at the percentage of the run; null when
	 * there is nothing to play or nowhere to play it (the output is null).
	 */
	public static RemovedSoundsPlayer forRun(app.tuxguitar.song.models.TGSong song, PracticeSequence removedSequence, int percent, TGTrack track, TempoClock clock,
			Output output) {
		if (removedSequence == null || removedSequence.isEmpty() || output == null) {
			return null;
		}
		FollowTimeline timeline = FollowTimeline.build(song, removedSequence, percent);
		return create(soundsOf(removedSequence.getEvents(), timeline, track), clock, output);
	}

	/**
	 * The sounds of the removed events, in their order: their pitches (a chord whole, the tied and the dead notes left out), their velocity, the channel of the track
	 * and their written duration at the tempo of the run. {@code timeline} is the timeline of the sequence of those events, in the same order.
	 */
	public static List<Sound> soundsOf(List<PracticeEvent> removed, FollowTimeline timeline, TGTrack track) {
		List<Sound> sounds = new ArrayList<Sound>();
		for (int i = 0; i < removed.size(); i++) {
			PracticeEvent event = removed.get(i);
			TreeSet<Integer> keys = new TreeSet<Integer>();
			int velocity = DEFAULT_VELOCITY;
			boolean first = true;
			for (TGNote note : event.getNotes()) {
				if (note.isTiedNote() || note.getEffect().isDeadNote()) {
					continue;
				}
				keys.add(track.getOffset() + note.getValue() + track.getStrings().get(note.getString() - 1).getValue());
				if (first) {
					velocity = note.getVelocity();
					first = false;
				}
			}
			if (keys.isEmpty()) {
				continue;
			}
			long tick = timeline.playStartTicks(i);
			long written = event.getBeat().getVoice(event.getVoiceIndex()).getDuration().getTime();
			long duration = Math.max(0L, timeline.nanosAtTick(tick + written) - timeline.nanosAtTick(tick));
			int[] array = new int[keys.size()];
			int at = 0;
			for (int key : keys) {
				array[at++] = key;
			}
			sounds.add(new Sound(tick, duration, track.getChannelId(), array, velocity));
		}
		return sounds;
	}

	/** One step: what is due since the last one is sent, what has rung long enough is ended. Called by the thread, and by a test with its own clock. */
	public synchronized void poll() {
		if (this.stopped || !this.clock.isReady()) {
			return;
		}
		long now = this.clock.nowNanos();
		if (this.clock.isStalled(now)) {
			if (!this.paused) {
				this.paused = true;
				silence();
			}
			return;
		}
		this.paused = false;
		double tick = this.clock.tickAt(now);
		if (!this.initialised) {
			// the first look: what is before the place the run starts at is never played
			this.initialised = true;
			while (this.next < this.sounds.size() && this.sounds.get(this.next).tick <= tick) {
				this.next++;
			}
		}
		while (this.next < this.sounds.size() && this.sounds.get(this.next).tick <= tick) {
			Sound sound = this.sounds.get(this.next++);
			long late = now - this.clock.wallNanosAt(sound.tick);
			if (late <= LATE_TOLERANCE_NANOS) {
				for (int key : sound.keys) {
					this.output.noteOn(sound.channel, key, sound.velocity);
					this.ringing.add(new Ringing(now + sound.durationNanos, sound.channel, key));
				}
			}
		}
		// ended in the order they were sent
		java.util.Iterator<Ringing> notes = this.ringing.iterator();
		while (notes.hasNext()) {
			Ringing note = notes.next();
			if (note.offWall <= now) {
				this.output.noteOff(note.channel, note.key);
				notes.remove();
			}
		}
	}

	/** Ends every note that rings. */
	public synchronized void silence() {
		for (Ringing note : this.ringing) {
			this.output.noteOff(note.channel, note.key);
		}
		this.ringing.clear();
	}

	/** Polls from a thread of its own, every few milliseconds. */
	public synchronized void start() {
		if (this.scheduler != null || this.stopped) {
			return;
		}
		this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
			Thread thread = new Thread(runnable, "practice-removed-sounds");
			thread.setDaemon(true);
			return thread;
		});
		this.scheduler.scheduleWithFixedDelay(() -> {
			try {
				poll();
			} catch (RuntimeException exception) {
				// a failed note never stops the polling
			}
		}, 0L, POLL_MILLIS, TimeUnit.MILLISECONDS);
	}

	/** Silences, and ends the thread: nothing is sent afterwards. */
	public void stop() {
		ScheduledExecutorService toEnd;
		synchronized (this) {
			this.stopped = true;
			silence();
			toEnd = this.scheduler;
			this.scheduler = null;
		}
		if (toEnd != null) {
			toEnd.shutdownNow();
		}
	}
}
