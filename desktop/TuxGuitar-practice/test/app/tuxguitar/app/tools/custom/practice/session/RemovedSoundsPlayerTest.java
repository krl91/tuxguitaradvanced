package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.model.PracticeEvent;
import app.tuxguitar.app.tools.custom.practice.model.PracticeSequence;
import app.tuxguitar.app.tools.custom.practice.model.PracticeSequenceBuilder;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.app.tools.custom.practice.model.TrackTuning;
import app.tuxguitar.app.tools.custom.practice.testsupport.FakeTempoClock;
import app.tuxguitar.song.models.TGTrack;

/**
 * Spec 014 (T023, FR-015, SC-007): the sounds a level removes are played by the backing, at their written time. With a fake output and a fake clock: each sound
 * is sent once when its tick is reached, a chord whole, ended after its written duration; nothing is sent before the clock is ready, while it is paused, after a
 * stop, or for a sound that is already in the past (a start in the middle, a seek); nothing is created when there is nothing to play (level 5); nothing is heard
 * twice or lost over a pause.
 */
public class RemovedSoundsPlayerTest {

	private static final long MS = 1_000_000L;
	private static final long BASE = 960L;

	private static final class Output implements RemovedSoundsPlayer.Output {
		final List<String> calls = new ArrayList<String>();
		final List<String> threads = new ArrayList<String>();

		public synchronized void noteOn(int channel, int key, int velocity) {
			this.calls.add("on " + channel + " " + key + " " + velocity);
			this.threads.add(Thread.currentThread().getName());
		}

		public synchronized void noteOff(int channel, int key) {
			this.calls.add("off " + channel + " " + key);
			this.threads.add(Thread.currentThread().getName());
		}

		synchronized List<String> take() {
			List<String> taken = new ArrayList<String>(this.calls);
			this.calls.clear();
			return taken;
		}
	}

	/** one tick is one millisecond of the run, counted from the base tick */
	private static FakeTempoClock clock() {
		return new FakeTempoClock(ticks -> ticks * MS, BASE, 100_000L);
	}

	private static RemovedSoundsPlayer.Sound sound(long tickAfterBase, long durationMillis, int channel, int velocity, int... keys) {
		return new RemovedSoundsPlayer.Sound(BASE + tickAfterBase, durationMillis * MS, channel, keys, velocity);
	}

	@Test
	public void aSoundIsSentOnceWhenItsTickIsReachedAndEndedAfterItsDuration() {
		FakeTempoClock clock = clock();
		Output output = new Output();
		RemovedSoundsPlayer player = new RemovedSoundsPlayer(Arrays.asList(sound(1000, 300, 3, 90, 40)), clock, output);
		clock.setNow(0);
		clock.start(BASE);
		player.poll();
		assertEquals(List.of(), output.take(), "nothing before its time");
		clock.setNow(999 * MS);
		player.poll();
		assertEquals(List.of(), output.take());
		clock.setNow(1001 * MS);
		player.poll();
		assertEquals(List.of("on 3 40 90"), output.take(), "sent when its tick is reached");
		clock.setNow(1200 * MS);
		player.poll();
		assertEquals(List.of(), output.take(), "still ringing");
		clock.setNow(1302 * MS);
		player.poll();
		assertEquals(List.of("off 3 40"), output.take(), "ended after its written duration");
		clock.setNow(1500 * MS);
		player.poll();
		player.poll();
		assertEquals(List.of(), output.take(), "never heard twice");
	}

	@Test
	public void aChordIsSentWholeAndEndedWhole() {
		FakeTempoClock clock = clock();
		Output output = new Output();
		RemovedSoundsPlayer player = new RemovedSoundsPlayer(Arrays.asList(sound(500, 200, 0, 80, 40, 47, 52)), clock, output);
		clock.setNow(0);
		clock.start(BASE);
		player.poll();
		clock.setNow(501 * MS);
		player.poll();
		assertEquals(List.of("on 0 40 80", "on 0 47 80", "on 0 52 80"), output.take());
		clock.setNow(701 * MS);
		player.poll();
		assertEquals(List.of("off 0 40", "off 0 47", "off 0 52"), output.take());
	}

	@Test
	public void severalSoundsAreSentInOrderEachOnceWhateverTheStepOfThePoll() {
		FakeTempoClock clock = clock();
		Output output = new Output();
		List<RemovedSoundsPlayer.Sound> sounds = Arrays.asList(sound(200, 50, 1, 70, 40), sound(400, 50, 1, 70, 41), sound(600, 50, 1, 70, 42));
		RemovedSoundsPlayer player = new RemovedSoundsPlayer(sounds, clock, output);
		clock.setNow(0);
		clock.start(BASE);
		player.poll();
		for (long ms = 0; ms <= 1000; ms += 100) {
			clock.setNow(ms * MS);
			player.poll();
		}
		List<String> heard = new ArrayList<String>();
		for (String call : output.take()) {
			if (call.startsWith("on")) {
				heard.add(call);
			}
		}
		assertEquals(List.of("on 1 40 70", "on 1 41 70", "on 1 42 70"), heard, "a coarse step still plays each sound once, in order");
	}

	@Test
	public void nothingIsSentBeforeTheClockIsReadyOrAfterAStop() {
		FakeTempoClock clock = clock();
		Output output = new Output();
		RemovedSoundsPlayer player = new RemovedSoundsPlayer(Arrays.asList(sound(10, 100, 2, 90, 50), sound(500, 100, 2, 90, 51)), clock, output);
		clock.setNow(0);
		clock.setReady(false);
		clock.start(BASE);
		clock.setNow(2000 * MS);
		player.poll();
		assertEquals(List.of(), output.take(), "the clock is not ready");
		clock.setReady(true);
		clock.seek(BASE + 400);
		clock.setNow(2010 * MS);
		player.poll();
		clock.setNow(2200 * MS);
		player.poll();
		assertEquals(List.of("on 2 51 90"), output.take(), "after a seek to 400 the sound at 500 plays, the one at 10 is in the past");
		player.stop();
		assertEquals(List.of("off 2 51"), output.take(), "a stop ends the note that rings");
		clock.setNow(5000 * MS);
		player.poll();
		assertEquals(List.of(), output.take(), "after a stop nothing more is sent");
	}

	@Test
	public void aStartInTheMiddleDoesNotPlayWhatIsBeforeIt() {
		FakeTempoClock clock = clock();
		Output output = new Output();
		RemovedSoundsPlayer player = new RemovedSoundsPlayer(Arrays.asList(sound(100, 100, 4, 90, 40), sound(900, 100, 4, 90, 41), sound(1500, 100, 4, 90, 42)), clock, output);
		clock.setNow(0);
		clock.start(BASE + 800);
		player.poll();
		clock.setNow(50 * MS);
		player.poll();
		assertEquals(List.of(), output.take(), "the sound at 100 is before the start");
		clock.setNow(150 * MS);
		player.poll();
		assertEquals(List.of("on 4 41 90"), output.take(), "the sound at 900 plays at its time, 100 ms after the start");
	}

	@Test
	public void aPauseEndsWhatRingsSendsNothingAndTheRunGoesOnFromTheClock() {
		FakeTempoClock clock = clock();
		Output output = new Output();
		RemovedSoundsPlayer player = new RemovedSoundsPlayer(Arrays.asList(sound(200, 400, 5, 90, 40), sound(1000, 100, 5, 90, 41)), clock, output);
		clock.setNow(0);
		clock.start(BASE);
		player.poll();
		clock.setNow(250 * MS);
		player.poll();
		assertEquals(List.of("on 5 40 90"), output.take());
		clock.pause();
		clock.setNow(300 * MS);
		player.poll();
		assertEquals(List.of("off 5 40"), output.take(), "a pause ends the note that rings");
		clock.setNow(5000 * MS);
		player.poll();
		assertEquals(List.of(), output.take(), "nothing is sent while paused, however long");
		clock.resume();
		clock.setNow(5100 * MS);
		player.poll();
		assertEquals(List.of(), output.take(), "the clock is at 350: the next sound is not due");
		clock.setNow(5800 * MS);
		player.poll();
		assertEquals(List.of("on 5 41 90"), output.take(), "after the resume the sound at 1000 plays once, at its time: nothing lost, nothing twice");
	}

	@Test
	public void nothingIsCreatedWhenThereIsNothingToPlay() {
		assertNull(RemovedSoundsPlayer.create(new ArrayList<RemovedSoundsPlayer.Sound>(), clock(), new Output()), "level 5: no player");
		assertNull(RemovedSoundsPlayer.create(null, clock(), new Output()));
	}

	@Test
	public void itPollsOnAThreadOfItsOwnNotOnTheCallerNorTheAnalysis() throws InterruptedException {
		FakeTempoClock clock = clock();
		Output output = new Output();
		RemovedSoundsPlayer player = RemovedSoundsPlayer.create(Arrays.asList(sound(10, 20, 1, 90, 40)), clock, output);
		clock.setNow(0);
		clock.start(BASE);
		player.start();
		try {
			Thread.sleep(60); // the thread has looked at the clock at the start of the run before it moves
			clock.setNow(50 * MS);
			long deadline = System.currentTimeMillis() + 3000;
			while (output.threads.isEmpty() && System.currentTimeMillis() < deadline) {
				Thread.sleep(10);
			}
			assertTrue(!output.threads.isEmpty(), "the sound was sent by the scheduler");
			assertEquals("practice-removed-sounds", output.threads.get(0));
			assertTrue(!Thread.currentThread().getName().equals(output.threads.get(0)));
		} finally {
			player.stop();
		}
	}

	@Test
	public void theSoundsAreMadeFromTheRemovedEventsOnlyWithTheirPitchesAndTheirDuration() {
		SongFixture fixture = new SongFixture(1);
		fixture.quarter(0, 0, 1, 0);
		fixture.chord(0, 1, new int[] { 6, 5 }, new int[] { 0, 2 });
		fixture.quarter(0, 2, 1, 5);
		TGTrack track = fixture.song.getTrack(0);
		PracticeSequence whole = PracticeSequenceBuilder.build(fixture.song, track, TrackTuning.ofAnyStrings(track), null, 0L);
		boolean[] keepOnlyTheFirstAndTheLast = { true, false, true };
		PracticeSequence kept = whole.keeping(keepOnlyTheFirstAndTheLast);
		List<PracticeEvent> removed = new ArrayList<PracticeEvent>();
		removed.add(whole.getEvents().get(1));
		PracticeSequence removedSequence = whole.keeping(new boolean[] { false, true, false });
		FollowTimeline timeline = FollowTimeline.build(fixture.song, removedSequence, 100);
		List<RemovedSoundsPlayer.Sound> sounds = RemovedSoundsPlayer.soundsOf(removed, timeline, track);
		assertEquals(1, kept.getEvents().size() - 1, "the kept sequence has the other two");
		assertEquals(1, sounds.size(), "one sound per removed event: the kept ones are never sent");
		RemovedSoundsPlayer.Sound chord = sounds.get(0);
		assertEquals(2, chord.keys().length, "a chord whole");
		assertEquals(Arrays.asList(40, 47), Arrays.asList(chord.keys()[0], chord.keys()[1]), "string 6 open and string 5 at fret 2 on a guitar in standard tuning, sorted");
		assertEquals(timeline.playStartTicks(0), chord.tick(), 1e-9);
		assertTrue(chord.durationNanos() > 0, "its written duration at the tempo of the run");
	}
}
