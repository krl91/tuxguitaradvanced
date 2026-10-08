package app.tuxguitar.app.tools.custom.practice.arcade.stress;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import app.tuxguitar.app.tools.custom.practice.model.PracticeEvent;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.app.tools.custom.practice.session.FollowTimeline;
import app.tuxguitar.audio.engine.testsupport.SyntheticSignals;
import app.tuxguitar.song.models.TGDuration;
import app.tuxguitar.song.models.TGTrack;

/**
 * The score and the recording of the long Arcade stress run (spec 003 T047): a passage of the given length for the guitar track, with a bass line on a
 * second track for the MIDI backing. The first half is dense single notes (the rate is a parameter, the neck wandering over the frets so that the camera moves),
 * the second half is chords of five or six strings (three a second). The recording is generated from the score and a seed, with a few milliseconds of
 * seeded jitter (plus or minus five) on each attack, so it is the same every time; it is played in real time into the live chain.
 */
public final class StressScore {

	public static final int RATE = 48000;
	private static final int[] OPEN = { 0, 64, 59, 55, 50, 45, 40 };
	private static final int[][] SHAPES = { { 0, 2, 2, 1, 0, 0 }, { 3, 2, 0, 0, 0, 3 }, { 0, 0, 2, 2, 2, 0 }, { 1, 3, 3, 2, 1, 1 }, { 2, 4, 4, 3, 2, 2 },
			{ 0, 2, 2, 0, 0, 0 } };

	public final SongFixture fixture;
	public final TGTrack guitar;
	public final long seed;
	/** The chords of the passage (events with more than one pitch). */
	public final int chordEvents;
	public final int noteEvents;

	/**
	 * @param seconds the length of the passage, half single notes, half chords
	 * @param noteRate single notes a second in the first half: one a quarter note, so the tempo is sixty times this, and the same for the whole passage (a change
	 *        of tempo is a moment where the clock of the player has to find its rate again, which would make two runs differ for a reason that is not the subject)
	 *        The chords of the second half fall on the first and the third beat, half as many a second.
	 */
	public StressScore(int seconds, long seed, double noteRate) {
		this.seed = seed;
		int denseMeasures = (int) Math.round(seconds / 2.0 * noteRate / 4.0);
		int chordMeasures = (int) Math.round(seconds / 2.0 * noteRate / 8.0);
		// measures 0 and 1 are empty and never played (the run begins at the first event); measure 2 holds one note on its fourth beat, one second after the
		// run begins, which gives the feeder the time to know where the player is; then the single notes, then the chords, then one empty measure so that
		// the last windows close before the player stops
		this.fixture = new SongFixture(3 + denseMeasures + chordMeasures + 1);
		this.guitar = this.fixture.track();
		TGTrack bass = this.fixture.addTrack();
		this.fixture.tempoChange(0, (int) Math.round(noteRate * 60));
		this.fixture.quarter(2, 3, 3, 5);
		int notes = 1;
		int chords = 0;
		for (int m = 0; m < denseMeasures; m++) {
			for (int q = 0; q < 4; q++) {
				int i = m * 4 + q;
				int base = (int) Math.round(8.0 + 6.0 * Math.sin(i / 23.0) + 3.0 * Math.sin(i / 5.0));
				this.fixture.quarter(3 + m, q, 1 + (i * 5) % 6, Math.max(0, Math.min(19, base + (i % 3))));
				notes++;
			}
			this.fixture.note(bass, 3 + m, 0, 0, 5, 3, TGDuration.QUARTER);
			this.fixture.note(bass, 3 + m, 2, 0, 6, 5, TGDuration.QUARTER);
		}
		for (int m = 0; m < chordMeasures; m++) {
			for (int q = 0; q < 4; q += 2) {
				int i = m * 2 + q / 2;
				int[] shape = SHAPES[i % SHAPES.length];
				int shift = (i / 6) % 5;
				int[] strings = new int[6];
				int[] frets = new int[6];
				int count = 0;
				for (int s = 0; s < 6; s++) {
					if (!(i % 7 == 3 && s == 0)) {
						strings[count] = 6 - s;
						frets[count] = shape[s] + (shape[s] > 0 || shift > 0 ? shift : 0);
						count++;
					}
				}
				this.fixture.chord(3 + denseMeasures + m, q, java.util.Arrays.copyOf(strings, count), java.util.Arrays.copyOf(frets, count));
				chords++;
			}
			this.fixture.note(bass, 3 + denseMeasures + m, 0, 0, 5, 3 + (m % 4), TGDuration.QUARTER);
		}
		this.noteEvents = notes;
		this.chordEvents = chords;
	}

	/**
	 * A much denser score for the picture alone (spec 003 T047): twenty single notes a second over the first half, the neck wandering over all the frets,
	 * then six five- or six-string chords a second. It is never played or judged (no recognition could follow it): it stands for the heaviest passage a
	 * learner could face, drawn on the same instants as the judged one, so that the cost of the picture is measured at its worst while the judgements stay
	 * reproducible. Its time is that of the run: its first event falls at the start of the timeline.
	 */
	public static SongFixture visualLoad(int seconds) {
		int noteMeasures = (int) Math.round(seconds / 2.0 * 20.0 / 4.0);
		int chordMeasures = (int) Math.round(seconds / 2.0 * 6.0 / 4.0);
		SongFixture load = new SongFixture(noteMeasures + chordMeasures + 1);
		load.tempoChange(0, 1200);
		load.tempoChange(noteMeasures, 360);
		for (int m = 0; m < noteMeasures; m++) {
			for (int q = 0; q < 4; q++) {
				int i = m * 4 + q;
				int base = (int) Math.round(10.0 + 9.0 * Math.sin(i / 31.0) + 4.0 * Math.sin(i / 3.0));
				load.quarter(m, q, 1 + (i * 5) % 6, Math.max(0, Math.min(22, base)));
			}
		}
		for (int m = 0; m < chordMeasures; m++) {
			for (int q = 0; q < 4; q++) {
				int i = m * 4 + q;
				int[] shape = SHAPES[i % SHAPES.length];
				int shift = (i / 4) % 8;
				int[] strings = new int[6];
				int[] frets = new int[6];
				int count = 0;
				for (int s = 0; s < 6; s++) {
					if (!(i % 5 == 2 && s == 0)) {
						strings[count] = 6 - s;
						frets[count] = shape[s] + shift;
						count++;
					}
				}
				load.chord(noteMeasures + m, q, java.util.Arrays.copyOf(strings, count), java.util.Arrays.copyOf(frets, count));
			}
		}
		return load;
	}

	public int backingNotes() {
		int count = 0;
		for (java.util.Iterator<app.tuxguitar.song.models.TGMeasure> measures = this.fixture.song.getTrack(1).getMeasures(); measures.hasNext();) {
			app.tuxguitar.song.models.TGMeasure measure = measures.next();
			for (int b = 0; b < measure.countBeats(); b++) {
				count += measure.getBeat(b).getVoice(0).countNotes();
			}
		}
		return count;
	}

	/** One thing played: when (nanoseconds from the start of the timeline, jitter included) and which pitches, each struck after the previous one by the stagger. */
	public static final class Strike {
		public final long nanos;
		public final int[] midis;
		public final boolean strum;

		Strike(long nanos, int[] midis, boolean strum) {
			this.nanos = nanos;
			this.midis = midis;
			this.strum = strum;
		}
	}

	/** The strikes of the recording, one for each event of the sequence, in order. */
	public List<Strike> strikes(List<PracticeEvent> events, FollowTimeline timeline) {
		Random random = new Random(this.seed);
		List<Strike> strikes = new ArrayList<Strike>();
		for (int i = 0; i < events.size(); i++) {
			PracticeEvent event = events.get(i);
			long jitter = (long) ((random.nextDouble() * 2.0 - 1.0) * 5_000_000.0);
			int[] midis = event.getRequiredPitches();
			boolean strum = event.getKind() == PracticeEvent.Kind.EXPECTED_CHORD;
			if (!strum) {
				midis = new int[] { event.getExpectedMidi() };
			}
			strikes.add(new Strike(timeline.targetNanos(i) + jitter, midis, strum));
		}
		return strikes;
	}

	/** The sound of one pitch, as the benchmarks of specification 002 make it (a plucked string), cached by the caller. */
	public static float[] pluck(int midi, boolean strum, long seed) {
		return SyntheticSignals.karplusStrong(SyntheticSignals.midiFrequency(midi), RATE, (int) ((strum ? 1.3 : 0.9) * RATE), seed + midi);
	}

	public static int openMidi(int string) {
		return OPEN[string];
	}
}
